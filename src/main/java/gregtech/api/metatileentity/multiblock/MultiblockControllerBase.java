package gregtech.api.metatileentity.multiblock;

import gregtech.api.GregTechAPI;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IMultiblockController;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.PieceTemplate;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.MultiPiecePreviewAssembler;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.PatternError;
import gregtech.api.pattern.PieceRuntime;
import gregtech.api.pattern.PieceRuntimeState;
import gregtech.api.pattern.PieceRuntimes;
import gregtech.api.pattern.RepeatGroupPiece;
import gregtech.api.pattern.StructureCheckResult;
import gregtech.api.pattern.StructureElementPreviewEntry;
import gregtech.api.pattern.StructureExternalDependencies;
import gregtech.api.pattern.StructureFailureTrace;
import gregtech.api.pattern.StructureHintResult;
import gregtech.api.pattern.StructureLifecycleState;
import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.StructureTrace;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.pattern.casing.StructureChannelValues;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gregtech.api.util.RelativeDirection;
import gregtech.api.util.world.DummyWorld;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOrientedCubeRenderer;
import gregtech.common.ConfigHolder;
import gregtech.common.creativetab.GTCreativeTabs;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import codechicken.lib.vec.Rotation;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static gregtech.api.capability.GregtechDataCodes.*;

public abstract class MultiblockControllerBase extends MetaTileEntity implements IMultiblockController {

    // Move multiblock controllers from the generic machines tab into their own tab
    {
        creativeTabs.add(GTCreativeTabs.TAB_GREGTECH_MULTIBLOCKS);
        creativeTabs.remove(GTCreativeTabs.TAB_GREGTECH_MACHINES);
    }

    private final Map<MultiblockAbility<Object>, AbilityInstances> multiblockAbilities = new HashMap<>();
    private final List<IMultiblockPart> multiblockParts = new ArrayList<>();
    private final MultiblockStructureCheckScheduler structureCheckScheduler = new MultiblockStructureCheckScheduler();

    /** Shared immutable structure template (new architecture) */
    @Nullable
    protected PieceTemplate patternTemplate;
    /** Canonical single-template matcher/cache state. Null for multi-piece-only structures. */
    @Nullable
    protected PieceRuntimeState runtimeState;
    /** Multi-piece pattern for super-large structures (P3, opt-in) */
    @Nullable
    protected MultiPiecePattern multiPiecePattern;
    /**
     * Per-controller state for the multi-piece pattern.
     * Built in {@link #reinitializeStructurePattern()} and rebuilt whenever the
     * pattern itself is rebuilt. Null if the controller has no multi-piece pattern.
     *
     * <p>This is the canonical place for per-instance state (the {@link PieceRuntimeState}
     * per piece, plus dirty/validated flags, formed-position set, and the
     * repeatable-piece search cache). The {@link MultiPiecePattern} itself is
     * stateless and safe to share across controllers of the same multiblock type.
     */
    @Nullable
    protected PieceRuntimes pieceRuntimes;
    /** Canonical structure definition. */
    @Nullable
    private StructureDefinition<?> structureDefinition;
    /** V3 per-controller structure runtime. */
    @Nullable
    private StructureRuntime structureRuntime;
    /** Invalidates detached async work whenever compiled runtime objects are rebuilt. */
    private volatile long structureRuntimeGeneration;
    private volatile long structureControllerModeGeneration;
    private volatile long structureChannelDependencyGeneration;
    private volatile long structureConfigDependencyGeneration;
    private volatile long structureUpgradeDependencyGeneration;
    protected EnumFacing upwardsFacing = EnumFacing.NORTH;
    protected boolean isFlipped;
    /**
     * 判断是否应该延迟检查
     *
     * @return boolean 返回是否应该延迟检查的标识， 当前实现固定返回false表示不延迟
     */
    boolean delayCheck = false;
    private boolean structureFormed;
    private int delayStructureCheckStandby = 20;
    private int delayStructureCheckWork = 20;
    /** Tick counter for async check fallback — counts ticks since registering for async check */

    public MultiblockControllerBase(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    /**
     * Collect all unique channels referenced by predicates in the given template.
     */
    @NotNull
    protected static List<StructureChannel> collectChannelsFromTemplate(
            @NotNull PieceTemplate template) {
        return MultiblockStructureChannels.collectChannelsFromTemplate(template);
    }

    @Override
    public boolean isStructureFormed() {
        if (getWorld() == null || !getWorld().isRemote) {
            StructureRuntime runtime = structureRuntime;
            if (runtime != null) {
                return runtime.getLifecycleState().isFormed();
            }
        }
        return structureFormed;
    }

    @Override
    public void onPlacement(EntityLivingBase placer) {
        super.onPlacement(placer);
        reinitializeStructurePattern();
    }

    @SuppressWarnings("deprecation")
    public void reinitializeStructurePattern() {
        StructureRuntime previousRuntime = this.structureRuntime;
        this.structureDefinition = resolveStructureDefinition();
        this.multiPiecePattern = this.structureDefinition.getCompiledPattern();
        if (this.structureDefinition.supportsSingleTemplatePath()) {
            this.patternTemplate = this.multiPiecePattern.getPrimaryPiece().getTemplate();
            this.runtimeState = new PieceRuntimeState(this.patternTemplate);
        } else {
            this.patternTemplate = null;
            this.runtimeState = null;
        }
        // Per-controller state for the compiled pattern. Single-template
        // runtimes use PieceRuntimeState directly.
        this.pieceRuntimes = this.runtimeState == null
                ? new PieceRuntimes(this.multiPiecePattern)
                : PieceRuntimes.singleWithState(this.multiPiecePattern, this.runtimeState);
        this.structureRuntime = new StructureRuntime(this.structureDefinition, this.patternTemplate,
                this.runtimeState, this.multiPiecePattern, this.pieceRuntimes);
        this.structureRuntimeGeneration++;
        this.structureRuntime.copyFormedStateFrom(previousRuntime);
        StructureTrace.debug(this, "runtime-reinitialized", this.structureRuntime.describeShape());
    }

    @NotNull
    @SuppressWarnings("deprecation")
    private StructureDefinition<?> resolveStructureDefinition() {
        StructureDefinition<?> definition = createStructureDefinition();
        if (definition == null) {
            throw new UnsupportedOperationException(
                    "Override createStructureDefinition() to provide a StructureDefinition");
        }
        return definition;
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote) {
            doStructureCheck();
            // DummyWorld is the world for the JEI preview. We do not want to update the Multi in this world,
            // besides initially forming it in checkStructurePattern
            if (isStructureFormed() && !(getWorld() instanceof DummyWorld)) {
                updateFormedValid();
            }
        }
    }

    public void doStructureCheck() {
        structureCheckScheduler.doStructureCheck(this);
    }

    /**
     * Called when the multiblock is formed and validation predicate is matched
     */
    protected abstract void updateFormedValid();

    /**
     * Create a StructureDefinition for this multiblock.
     *
     * <p>Must return an idempotent instance — use
     * {@link StructureDefinition#getOrBuild(String, java.util.function.Supplier)}
     * to ensure this.
     *
     * @return the structure definition, must not be null
     */
    @NotNull
    protected abstract StructureDefinition<?> createStructureDefinition();

    /**
     * Get the per-controller state for the multi-piece pattern.
     * Each controller of a given multiblock type has its own independent
     * {@link PieceRuntimes} so that per-instance state (the
     * {@link PieceRuntimeState} per piece, dirty/validated flags, etc.) is not
     * shared between independent controllers. See {@link PieceRuntime} for
     * the underlying per-piece state holder.
     *
     * @return the controller's per-piece state, or null if this controller has
     *         no multi-piece pattern.
     */
    @Nullable
    public PieceRuntimes getPieceRuntimes() {
        return pieceRuntimes;
    }

    public EnumFacing getUpwardsFacing() {
        return upwardsFacing;
    }

    public void setUpwardsFacing(EnumFacing upwardsFacing) {
        if (!allowsExtendedFacing()) return;
        if (upwardsFacing == null || upwardsFacing == EnumFacing.UP || upwardsFacing == EnumFacing.DOWN) {
            GTLog.logger.error("Tried to set upwards facing to invalid facing {}! Skipping", upwardsFacing);
            return;
        }
        if (this.upwardsFacing != upwardsFacing) {
            this.upwardsFacing = upwardsFacing;
            if (getWorld() != null && !getWorld().isRemote) {
                notifyBlockUpdate();
                markDirty();
                writeCustomData(UPDATE_UPWARDS_FACING, buf -> buf.writeByte(upwardsFacing.getIndex()));
                if (runtimeState != null) {
                    // Unregister before clearing cache so positions can be properly cleaned up
                    MultiblockWorldData.get(getWorld()).unregisterMultiblock(this);
                    runtimeState.clearCache();
                    checkStructurePattern();
                }
            }
            refreshPreviewOnClient();
        }
    }

    public boolean isFlipped() {
        return isFlipped;
    }

    /** <strong>Should not be called outside of structure formation logic!</strong> */
    @ApiStatus.Internal
    protected void setFlipped(boolean isFlipped) {
        if (this.isFlipped != isFlipped) {
            this.isFlipped = isFlipped;
            notifyBlockUpdate();
            markDirty();
            writeCustomData(UPDATE_FLIP, buf -> buf.writeBoolean(isFlipped));
        }
    }

    @SideOnly(Side.CLIENT)
    public abstract ICubeRenderer getBaseTexture(IMultiblockPart sourcePart);

    public boolean shouldRenderOverlay(IMultiblockPart sourcePart) {
        return true;
    }

    /**
     * Override this method to change the Controller overlay
     *
     * @return The overlay to render on the Multiblock Controller
     */
    @SideOnly(Side.CLIENT)
    @NotNull
    protected ICubeRenderer getFrontOverlay() {
        return Textures.MULTIBLOCK_WORKABLE_OVERLAY;
    }

    @SideOnly(Side.CLIENT)
    public TextureAtlasSprite getFrontDefaultTexture() {
        return getFrontOverlay().getParticleSprite();
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        ICubeRenderer baseTexture = getBaseTexture(null);
        pipeline = ArrayUtils.add(pipeline,
                new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(getPaintingColorForRendering())));
        if (baseTexture instanceof SimpleOrientedCubeRenderer) {
            baseTexture.renderOriented(renderState, translation, pipeline, getFrontFacing());
        } else {
            baseTexture.render(renderState, translation, pipeline);
        }

        if (allowsExtendedFacing()) {
            double degree = Math.PI / 2 * (upwardsFacing == EnumFacing.EAST ? -1 :
                    upwardsFacing == EnumFacing.SOUTH ? 2 : upwardsFacing == EnumFacing.WEST ? 1 : 0);
            Rotation rotation = new Rotation(degree, frontFacing.getXOffset(), frontFacing.getYOffset(),
                    frontFacing.getZOffset());
            translation.translate(0.5, 0.5, 0.5);
            if (frontFacing == EnumFacing.DOWN && upwardsFacing.getAxis() == EnumFacing.Axis.Z) {
                translation.apply(new Rotation(Math.PI, 0, 1, 0));
            }
            translation.apply(rotation);
            translation.scale(1.0000f);
            translation.translate(-0.5, -0.5, -0.5);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(getBaseTexture(null).getParticleSprite(), getPaintingColorForRendering());
    }

    /**
     * Override to disable Multiblock pattern from being added to Jei
     */
    public boolean shouldShowInJei() {
        return true;
    }

    /**
     * Used if MultiblockPart Abilities need to be sorted a certain way, like Distillation Tower and Assembly Line.
     */
    protected Function<BlockPos, Integer> multiblockPartSorter() {
        return BlockPos::hashCode;
    }

    public boolean isDelayCheck() {
        return delayCheck;
    }

    public void setDelayCheck(boolean delay) {
        if (delayCheck != delay) {
            delayCheck = delay;
            notifyStructureConfigChanged();
        }
    }

    /**
     * Returns whether this multiblock is currently "working" for structure check interval purposes. Subclasses override
     * to provide machine-specific working state (e.g., recipe active). When working, the fallback polling uses
     * {@link #getStructureCheckIntervalWorking()}.
     *
     * @return true if the multiblock is actively working
     */
    protected boolean isWorkingForStructureCheck() {
        return false;
    }

    /**
     * Large or piece-sharded structures may be more expensive to snapshot than to check normally.
     */
    protected boolean allowsAsyncStructureCheck() {
        return true;
    }

    /**
     * Structure validation scheduling policy for this controller.
     * Override to force a structure onto polling-only, event-driven-only, or
     * async-capable behavior without changing the world dirty index storage.
     */
    @NotNull
    protected StructureSchedulerPolicy getStructureSchedulerPolicy() {
        return StructureSchedulerPolicy.defaultPolicy();
    }

    /**
     * Returns the structure check polling interval (in ticks) when the multiblock is idle/standby. Used in fallback
     * polling mode when event-driven or async checking is unavailable.
     *
     * @return polling interval in ticks (minimum 20)
     */
    protected int getStructureCheckIntervalStandby() {
        if (isDelayCheck()) {
            return getDelayStructureCheckStandby();
        }
        return 20;
    }

    /**
     * Returns the structure check polling interval (in ticks) when the multiblock is working. Used in fallback polling
     * mode when event-driven or async checking is unavailable.
     *
     * @return polling interval in ticks (minimum 20)
     */
    protected int getStructureCheckIntervalWorking() {
        if (isDelayCheck()) {
            return getDelayStructureCheckWork();
        }
        return 20;
    }

    public int getDelayStructureCheckStandby() {
        return Math.max(delayStructureCheckStandby, 20);
    }

    public void setDelayStructureCheckStandby(int delay) {
        int clamped = Math.max(Math.min(1200, delay), 20);
        if (delayStructureCheckStandby != clamped) {
            delayStructureCheckStandby = clamped;
            notifyStructureConfigChanged();
        }
    }

    public int getDelayStructureCheckWork() {
        return Math.max(delayStructureCheckWork, 20);
    }

    public void setDelayStructureCheckWork(int delay) {
        int clamped = Math.max(Math.min(1200, delay), 20);
        if (delayStructureCheckWork != clamped) {
            delayStructureCheckWork = clamped;
            notifyStructureConfigChanged();
        }
    }

    /**
     * Snapshot for controller mode dependencies. Subclasses with additional
     * mode switches can override {@link #getStructureControllerModeValue()} and
     * call {@link #notifyStructureControllerModeChanged()} when the value changes.
     */
    @NotNull
    public StructureExternalDependencies.ControllerModeSnapshot getStructureControllerModeSnapshot() {
        IControllable controllable = this instanceof IControllable ? (IControllable) this : null;
        return new StructureExternalDependencies.ControllerModeSnapshot(
                getStructureControllerModeValue(),
                controllable != null,
                controllable != null && controllable.isWorkingEnabled(),
                structureControllerModeGeneration);
    }

    @Nullable
    protected Object getStructureControllerModeValue() {
        return null;
    }

    @NotNull
    public StructureExternalDependencies.VersionedSnapshot getStructureChannelDependencySnapshot() {
        return StructureExternalDependencies.VersionedSnapshot.of(
                structureChannelDependencyGeneration,
                getStructureChannelDependencyValue());
    }

    @Nullable
    protected Object getStructureChannelDependencyValue() {
        return null;
    }

    @NotNull
    public StructureExternalDependencies.VersionedSnapshot getStructureConfigDependencySnapshot() {
        return StructureExternalDependencies.VersionedSnapshot.of(
                structureConfigDependencyGeneration,
                getStructureConfigDependencyValue());
    }

    @Nullable
    protected Object getStructureConfigDependencyValue() {
        Map<String, Object> values = new HashMap<>();
        values.put("delayCheck", delayCheck);
        values.put("standbyInterval", getDelayStructureCheckStandby());
        values.put("workInterval", getDelayStructureCheckWork());
        return values;
    }

    @NotNull
    public StructureExternalDependencies.VersionedSnapshot getStructureUpgradeDependencySnapshot() {
        return StructureExternalDependencies.VersionedSnapshot.of(
                structureUpgradeDependencyGeneration,
                getStructureUpgradeDependencyValue());
    }

    @Nullable
    protected Object getStructureUpgradeDependencyValue() {
        return null;
    }

    protected final void notifyStructureControllerModeChanged() {
        structureControllerModeGeneration++;
        enqueueChangedStructureExternalDependency();
    }

    protected final void notifyStructureChannelsChanged() {
        structureChannelDependencyGeneration++;
        enqueueChangedStructureExternalDependency();
    }

    protected final void notifyStructureConfigChanged() {
        structureConfigDependencyGeneration++;
        enqueueChangedStructureExternalDependency();
    }

    protected final void notifyStructureUpgradesChanged() {
        structureUpgradeDependencyGeneration++;
        enqueueChangedStructureExternalDependency();
    }

    boolean enqueueChangedStructureExternalDependencies() {
        World world = getWorld();
        StructureRuntime runtime = structureRuntime;
        if (world == null || world.isRemote || runtime == null || !isStructureFormed()) {
            return false;
        }
        Set<String> roots = runtime.rootsForChangedExternalDependencies(this);
        if (roots.isEmpty()) {
            return false;
        }
        boolean enqueued = MultiblockWorldData.get(world)
                .enqueueDirtyRoots(this, roots, world.getTotalWorldTime());
        if (enqueued && ConfigHolder.machines.debugStructureCheck) {
            GTLog.logger.debug("[StructureCheck] External dependency changed for {} roots={}",
                    getMetaName(), roots);
        }
        return enqueued;
    }

    private void enqueueChangedStructureExternalDependency() {
        enqueueChangedStructureExternalDependencies();
    }

    public void checkStructurePattern() {
        MultiblockStructureOperations.checkStructurePattern(this);
    }

    /**
     * Checks if a multiblock ability at a given block pos should be added to the ability instances
     *
     * @return true if the ability should be added to this multiblocks ability instances
     */
    protected <T> boolean checkAbilityPart(MultiblockAbility<T> ability, BlockPos pos) {
        return true;
    }

    /**
     * Typed formation callback for new controllers.
     * New code should override this method and consume {@link FormedStructureView}
     * directly.
     */
    protected void formStructure(@NotNull FormedStructureView formed) {}

    /**
     * Re-validates the complete active piece graph after an indexed block change.
     */
    protected void checkActiveStructureGraph() {
        MultiblockStructureOperations.checkActiveGraph(this);
    }

    /**
     * Re-validates the dependency closure affected by event-driven dirty roots.
     */
    protected void checkIncrementalStructureGraph() {
        MultiblockStructureOperations.checkIncrementalGraph(this);
    }

    /**
     * Register a multi-piece pattern with the event-driven system. Collects all positions from all active, validated
     * pieces and registers them.
     */
    protected void registerMultiPiecePattern() {
        MultiblockStructureRegistration.registerMultiPiecePattern(this, multiPiecePattern, pieceRuntimes);
    }

    /**
     * Get the structure directions for preview coordinate transforms.
     * Falls back to the structure definition's directions if the pattern template is null.
     */
    @NotNull
    public RelativeDirection[] getStructureDirForPreview() {
        if (patternTemplate != null) {
            return patternTemplate.getStructureDir();
        }
        if (structureDefinition != null) {
            return structureDefinition.getStructureDir();
        }
        // Default fallback: RIGHT, UP, FRONT
        return new RelativeDirection[]{
                RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.FRONT
        };
    }

    /**
     * Compute the {@code frontFacing} argument to pass into
     * {@link gregtech.api.util.RelativeDirection#setActualRelativeOffset} so that the
     * template's first aisle (aisle 0) ends up <em>behind</em> the controller.
     *
     * <p>The structure's Z axis is the controller's actual front for a
     * {@link RelativeDirection#FRONT} template, and the opposite for a
     * {@link RelativeDirection#BACK} template. To place the structure behind the
     * controller in both cases, callers should pass the value returned here rather
     * than {@code getFrontFacing()} or {@code getFrontFacing().getOpposite()}
     * directly.</p>
     *
     * <p>Replaces the old "into-structure" convention where the parameter was
     * unconditionally {@code getFrontFacing().getOpposite()}. With the old
     * convention, FRONT templates placed aisle 0 in front of the controller
     * (where the player stands) instead of behind it.</p>
     */
    public EnumFacing getFrontFacingForStructure() {
        RelativeDirection[] structureDir = getStructureDirForPreview();
        return structureDir[2] == RelativeDirection.BACK
                ? getFrontFacing().getOpposite()
                : getFrontFacing();
    }

    @Nullable
    public PatternError getLastStructureError() {
        StructureFailureTrace failure = structureRuntime == null ? null : structureRuntime.getLastFailure();
        return failure == null ? null : failure.getError();
    }

    /**
     * Get the canonical structure definition.
     */
    @NotNull
    public StructureDefinition<?> getStructureDefinition() {
        if (structureDefinition == null) {
            reinitializeStructurePattern();
        }
        return structureDefinition;
    }

    @NotNull
    public Map<String, Integer> getMissingStructureAbilities() {
        return structureRuntime == null ? Collections.emptyMap() : structureRuntime.getMissingAbilities();
    }

    /**
     * Get the channel tier values determined when the structure was formed. Empty if the structure is not currently
     * formed.
     *
     * @return the formed channel values (never null)
     */
    @NotNull
    public StructureChannelValues getFormedChannelValues() {
        return structureRuntime == null
                ? new StructureChannelValues()
                : structureRuntime.getChannelValues();
    }

    /**
     * Get the formed structure metadata (piece repeat counts + channel values).
     * Only available when the structure is formed and using the new system.
     *
     * @return the formed metadata, or null if not formed or using old system
     */
    @Nullable
    public FormedStructureMetadata getFormedMetadata() {
        return structureRuntime == null ? null : structureRuntime.getFormedMetadata();
    }

    @Nullable
    public StructureRuntime getStructureRuntime() {
        return structureRuntime;
    }

    @NotNull
    public StructureRuntime getOrCreateStructureRuntime() {
        if (structureRuntime == null) {
            reinitializeStructurePattern();
        }
        return structureRuntime;
    }

    long getStructureRuntimeGeneration() {
        return structureRuntimeGeneration;
    }

    long getStructureLifecycleGeneration() {
        return structureRuntime == null ? 0 : structureRuntime.getLifecycleGeneration();
    }

    /**
     * Hook for multiblocks whose preview/build dimensions are controlled by channels
     * outside the canonical runtime template. Returning {@code true} means the
     * structure build was handled by the controller.
     */
    public boolean autoBuildStructure(@NotNull StructureOperationRequest request) {
        request.requireBuildKind();
        return false;
    }

    /**
     * Spawns structure hints for this controller. Dynamic structures can override this
     * to build a disposable definition from the requested channel values.
     */
    public void spawnStructureHints(@NotNull StructureOperationRequest request) {
        MultiblockStructureOperations.spawnStructureHints(this, request);
    }

    /**
     * Spawns structure hints and returns a traversal summary.
     */
    @NotNull
    public StructureHintResult hintStructure(@NotNull StructureOperationRequest request) {
        return MultiblockStructureOperations.hintStructure(this, request);
    }

    /**
     * Creates a disposable runtime for dynamic build definitions. The returned runtime
     * is not published as this controller's canonical runtime and must only be used for
     * the current tool operation.
     */
    @NotNull
    protected StructureRuntime createDynamicStructureRuntime(@NotNull StructureDefinition<?> definition) {
        return MultiblockStructureOperations.createDynamicStructureRuntime(definition);
    }

    /**
     * Creates a disposable runtime for a dynamic single-template operation.
     *
     * <p>The runtime is intentionally not published as this controller's canonical
     * runtime. Dynamic templates derived from channels or transient controller
     * state must stay scoped to one operation request so they cannot leak formed
     * metadata or per-piece caches across sizes.
     */
    @NotNull
    protected StructureRuntime createDynamicStructureRuntime(@NotNull String pieceName,
                                                             @NotNull PieceTemplate template) {
        return MultiblockStructureOperations.createDynamicStructureRuntime(pieceName, template);
    }

    @NotNull
    protected StructureCheckResult checkDynamicStructure(@NotNull StructureOperationRequest request,
                                                        @NotNull String pieceName,
                                                        @NotNull PieceTemplate template) {
        return MultiblockStructureOperations.checkDynamicStructure(this, request, pieceName, template);
    }

    @NotNull
    protected BlockInfo[][][] previewDynamicStructure(@NotNull StructureOperationRequest request,
                                                      @NotNull String pieceName,
                                                      @NotNull PieceTemplate template) {
        return MultiblockStructureOperations.previewDynamicStructure(this, request, pieceName, template);
    }

    /**
     * Executes a dynamic single-template build through the request/runtime boundary.
     */
    protected boolean autoBuildDynamicStructure(@NotNull StructureOperationRequest request,
                                                @NotNull String pieceName,
                                                @NotNull PieceTemplate template) {
        return MultiblockStructureOperations.autoBuildDynamicStructure(this, request, pieceName, template);
    }

    /**
     * Executes dynamic single-template hint generation through a disposable runtime.
     */
    protected void spawnDynamicStructureHints(@NotNull StructureOperationRequest request,
                                              @NotNull String pieceName,
                                              @NotNull PieceTemplate template) {
        MultiblockStructureOperations.spawnDynamicStructureHints(this, request, pieceName, template);
    }

    /**
     * Executes dynamic single-template hint generation and returns a traversal summary.
     */
    @NotNull
    protected StructureHintResult hintDynamicStructure(@NotNull StructureOperationRequest request,
                                                       @NotNull String pieceName,
                                                       @NotNull PieceTemplate template) {
        return MultiblockStructureOperations.hintDynamicStructure(this, request, pieceName, template);
    }

    public void invalidateStructure() {
        StructureTrace.debug(this, "invalidate", structureRuntime == null ? null : "lastFailure=" +
                structureRuntime.getLastFailure());
        // Unregister from event-driven structure checking system
        if (getWorld() != null && !getWorld().isRemote) {
            MultiblockWorldData.get(getWorld()).unregisterMultiblock(this);
        }

        // Reset multi-piece pattern state if present (P3)
        if (multiPiecePattern != null) {
            multiPiecePattern.resetAll(pieceRuntimes);
        }

        this.multiblockParts.forEach(part -> part.removeFromMultiBlock(this));
        projectStructureLifecycle(StructureLifecycleState.empty());
        if (structureRuntime != null) {
            structureRuntime.clearFormedState();
        }
        this.structureCheckScheduler.resetAsyncFallbackTicks();
        this.setFlipped(false);
        writeCustomData(STRUCTURE_FORMED, buf -> buf.writeBoolean(false));
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        if (!getWorld().isRemote) {
            if (structureFormed) {
                invalidateStructure();
            }
            // Unregister from async checker (P2)
            AsyncStructureChecker.getInstance().unregister(this);
        }
    }

    public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
        return Collections.unmodifiableList(multiblockAbilities.getOrDefault(ability, AbilityInstances.EMPTY).cast());
    }

    public List<IMultiblockPart> getMultiblockParts() {
        return Collections.unmodifiableList(multiblockParts);
    }

    @NotNull
    List<IMultiblockPart> mutableMultiblockParts() {
        return multiblockParts;
    }

    @NotNull
    Map<MultiblockAbility<Object>, AbilityInstances> mutableMultiblockAbilities() {
        return multiblockAbilities;
    }

    void projectStructureLifecycle(@NotNull StructureLifecycleState lifecycleState) {
        this.multiblockParts.clear();
        this.multiblockParts.addAll(lifecycleState.getParts());
        this.multiblockAbilities.clear();
        this.multiblockAbilities.putAll(lifecycleState.getAbilities());
        if (this.structureFormed != lifecycleState.isFormed()) {
            this.structureFormed = lifecycleState.isFormed();
            writeCustomData(STRUCTURE_FORMED, buf -> buf.writeBoolean(lifecycleState.isFormed()));
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        FormedStructureMetadata loadedMetadata = null;
        if (data.hasKey("UpwardsFacing")) {
            this.upwardsFacing = EnumFacing.VALUES[data.getByte("UpwardsFacing")];
        }
        if (data.hasKey("IsFlipped")) {
            this.isFlipped = data.getBoolean("IsFlipped");
        }
        delayCheck = data.getBoolean("delayCheck");
        delayStructureCheckStandby = data.getInteger("delayStructureCheckStandby");
        delayStructureCheckWork = data.getInteger("delayStructureCheckWork");
        if (data.hasKey("FormedMetadata")) {
            loadedMetadata = FormedStructureMetadata.readFromNBT(data.getCompoundTag("FormedMetadata"));
        }
        this.reinitializeStructurePattern();
        if (structureRuntime != null) {
            structureRuntime.setFormedMetadata(loadedMetadata);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setByte("UpwardsFacing", (byte) upwardsFacing.getIndex());
        data.setBoolean("IsFlipped", isFlipped);
        data.setBoolean("delayCheck", delayCheck);
        data.setInteger("delayStructureCheckStandby", delayStructureCheckStandby);
        data.setInteger("delayStructureCheckWork", delayStructureCheckWork);
        FormedStructureMetadata metadata = getFormedMetadata();
        if (metadata != null) {
            data.setTag("FormedMetadata", metadata.writeToNBT());
        }
        return data;
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(structureFormed);
        buf.writeByte(upwardsFacing.getIndex());
        buf.writeBoolean(isFlipped);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.structureFormed = buf.readBoolean();
        this.upwardsFacing = EnumFacing.VALUES[buf.readByte()];
        this.isFlipped = buf.readBoolean();
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == STRUCTURE_FORMED) {
            this.structureFormed = buf.readBoolean();
            if (!structureFormed) {
                GregTechAPI.soundManager.stopTileSound(getPos());
            }
        } else if (dataId == UPDATE_UPWARDS_FACING) {
            this.upwardsFacing = EnumFacing.VALUES[buf.readByte()];
            scheduleRenderUpdate();
        } else if (dataId == UPDATE_FLIP) {
            this.isFlipped = buf.readBoolean();
            scheduleRenderUpdate();
        }

        if (dataId == UPDATE_FRONT_FACING || dataId == UPDATE_UPWARDS_FACING || dataId == UPDATE_FLIP) {
            refreshPreviewOnClient();
        }
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        T result = super.getCapability(capability, side);
        if (result != null)
            return result;
        if (capability == GregtechCapabilities.CAPABILITY_MULTIBLOCK_CONTROLLER) {
            return GregtechCapabilities.CAPABILITY_MULTIBLOCK_CONTROLLER.cast(this);
        }
        return null;
    }

    @Override
    public void setFrontFacing(EnumFacing frontFacing) {
        EnumFacing oldFrontFacing = getFrontFacing();
        super.setFrontFacing(frontFacing);

        // Set the upwards facing in a way that makes it "look like" the upwards facing wasn't changed
        if (allowsExtendedFacing()) {
            EnumFacing newUpwardsFacing = RelativeDirection.simulateAxisRotation(frontFacing, oldFrontFacing,
                    getUpwardsFacing());
            setUpwardsFacing(newUpwardsFacing);
        }

        if (getWorld() != null && !getWorld().isRemote && runtimeState != null) {
            // Unregister before clearing cache so positions can be properly cleaned up
            MultiblockWorldData.get(getWorld()).unregisterMultiblock(this);
            // clear cache since the cache has no concept of pre-existing facing
            // for the controller block (or any block) in the structure
            runtimeState.clearCache();
            // recheck structure pattern immediately to avoid a slight "lag"
            // on deforming when rotating a multiblock controller
            checkStructurePattern();
        }
        if (oldFrontFacing != frontFacing) {
            refreshPreviewOnClient();
        }
    }

    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        MultiblockControllerClientHooks.addInformation(this, tooltip);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addStructureInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                                        boolean advanced) {
        MultiblockControllerClientHooks.addStructureInformation(this, patternTemplate, tooltip);
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        MultiblockControllerClientHooks.addToolUsages(this, tooltip);
        super.addToolUsages(stack, world, tooltip, advanced);
    }

    @Override
    public boolean onRightClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                CuboidRayTraceResult hitResult) {
        if (super.onRightClick(playerIn, hand, facing, hitResult))
            return true;

        return MultiblockControllerClientHooks.onRightClickPreview(this, playerIn, hand);
    }

    // todo tooltip on multis saying if this is enabled or disabled?

    @Override
    public boolean onWrenchClick(EntityPlayer playerIn, EnumHand hand, EnumFacing wrenchSide,
                                 CuboidRayTraceResult hitResult) {
        if (wrenchSide == getFrontFacing() && allowsExtendedFacing()) {
            setUpwardsFacing(playerIn.isSneaking() ? upwardsFacing.rotateYCCW() : upwardsFacing.rotateY());
            scheduleRenderUpdate();
            return true;
        }

        return super.onWrenchClick(playerIn, hand, wrenchSide, hitResult);
    }

    private void refreshPreviewOnClient() {
        MultiblockControllerClientHooks.refreshPreviewOnClient(this);
    }

    @Override
    public boolean isValidFrontFacing(EnumFacing facing) {
        return allowsExtendedFacing() || super.isValidFrontFacing(facing);
    }

    /** Whether this multi can be rotated or face upwards. */
    public boolean allowsExtendedFacing() {
        return true;
    }

    /** Set this to false only if your multiblock is set up such that it could have a wall-shared controller. */
    public boolean allowsFlip() {
        return true;
    }

    /**
     * Returns the list of structure channels supported by this multiblock. Default implementation auto-collects
     * channels from the pattern template's predicates. Subclasses can override for custom behavior or to add channels
     * not in the pattern.
     *
     * @return list of supported StructureChannel instances
     */
    @NotNull
    public List<StructureChannel> getSupportedChannels() {
        return MultiblockStructureOperations.getSupportedChannels(this);
    }

    /**
     * Get the valid value range for a given channel in this multiblock's pattern. For tiered casing channels, the range
     * is [0, maxCandidateIndex]. For repeatable aisle channels, the range is [aisleMin, aisleMax].
     *
     * @param channel the channel to query
     * @return int[2] with [min, max], or [0, 0] if channel not found in pattern
     */
    public int [] getChannelRange(@NotNull StructureChannel channel) {
        return MultiblockStructureOperations.getChannelRange(this, channel);
    }

    public List<MultiblockShapeInfo> getMatchingShapes() {
        return MultiblockStructureOperations.getMatchingShapes(this);
    }

    public List<MultiblockShapeInfo> getMatchingShapes(@Nullable Map<String, Integer> channelValues) {
        return MultiblockStructureOperations.getMatchingShapes(this, channelValues);
    }

    /**
     * Build preview shapes for multi-piece structures (StructureDefinition with multiple pieces).
     * Merges all pieces' previews into a single combined shape by offsetting each piece
     * along the aisle direction (the repeat axis for repeatable pieces).
     *
     * <p>The preview array is indexed as [worldX][worldY][worldZ].
     * For structure dir [RIGHT, BACK, UP] with NORTH-facing:
     * worldX = char index, worldY = repeat/aisle index, worldZ = -row index.
     * So merging along the aisle direction offsets worldY (the second array dimension).
     */
    List<MultiblockShapeInfo> buildMultiPieceShapes(@Nullable Map<String, Integer> channelValues) {
        return MultiblockStructureOperations.buildMultiPieceShapes(this, channelValues);
    }

    /**
     * Build direct preview metadata for multi-piece structures.
     *
     * <p>This is the typed JEI/tooling surface: it reads
     * {@link gregtech.api.pattern.element.StructureElementPreview} and element
     * preview tooltips directly.
     */
    @NotNull
    public Map<BlockPos, StructureElementPreviewEntry> buildMultiPiecePreviewEntries(
            @Nullable Map<String, Integer> channelValues) {
        return MultiblockStructureOperations.buildMultiPiecePreviewEntries(this, channelValues);
    }

    /**
     * Build typed preview metadata for JEI, preview renderers and client tools.
     *
     * <p>This is the tooling-facing canonical path for candidate blocks and
     * preview tooltips.
     */
    @NotNull
    public Map<BlockPos, StructureElementPreviewEntry> buildStructurePreviewEntries(
            @Nullable Map<String, Integer> channelValues) {
        return MultiblockStructureOperations.buildStructurePreviewEntries(this, channelValues);
    }

    /**
     * Get the preview shape for a specific piece from the MultiPiecePattern. Used by the structure projector to preview
     * individual pieces (e.g. second_ring, third_ring).
     *
     * @param pieceIndex    1-based index into the piece list
     * @param channelValues channel values for tier selection (nullable)
     * @return the preview shape info, or null if the piece index is invalid or no MultiPiecePattern exists
     */
    @Nullable
    public MultiblockShapeInfo getMatchingShapeForPiece(int pieceIndex,
                                                         @Nullable Map<String, Integer> channelValues) {
        return MultiblockStructureOperations.getMatchingShapeForPiece(this, pieceIndex, channelValues);
    }

    @Nullable
    public MultiPiecePreviewAssembler.PieceResult getMatchingPreviewPiece(
            int pieceIndex, @Nullable Map<String, Integer> channelValues) {
        return MultiblockStructureOperations.getMatchingPreviewPiece(this, pieceIndex, channelValues);
    }

    /** Returns the active tooling pieces merged around the controller origin. */
    @Nullable
    public MultiPiecePreviewAssembler.Result getMatchingMultiPiecePreview(
            @Nullable Map<String, Integer> channelValues) {
        return MultiblockStructureOperations.getMatchingMultiPiecePreview(this, channelValues);
    }

    @SideOnly(Side.CLIENT)
    public String[] getDescription() {
        return MultiblockControllerClientHooks.getDescription(this);
    }

    @Override
    public int getDefaultPaintingColor() {
        return 0xFFFFFF;
    }

    public void explodeMultiblock(float explosionPower) {
        List<IMultiblockPart> parts = new ArrayList<>(getMultiblockParts());
        for (IMultiblockPart part : parts) {
            part.removeFromMultiBlock(this);
            ((MetaTileEntity) part).doExplosion(explosionPower);
        }
        doExplosion(explosionPower);
    }

    public void dismantleStructure(EntityPlayer player) {
        PieceRuntimeState state = this.runtimeState;

        if (!structureFormed || state == null) {
            return;
        }

        // First invalidate structure, removing all part associations
        invalidateStructure();

        World world = getWorld();

        // Get all block positions in the structure
        StructureOrientation orientation = StructureOrientation.fromController(this);
        Map<BlockPos, BlockInfo> blocks = structureRuntime == null
                ? state.getAllStructureBlocks(world, getPos(), orientation)
                : structureRuntime.iterateSingle(
                        StructureOperationRequest.iterate(world, getPos(), orientation));

        ArrayList<ItemStack> drops = new ArrayList<>();

        // 将所有部件转为掉落物
        for (Map.Entry<BlockPos, BlockInfo> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            TileEntity tileEntity = entry.getValue().getTileEntity();

            world.setBlockToAir(pos); // 先清除方块

            // 如果是MetaTileEntity，尝试获取其物品形式
            if (tileEntity instanceof IGregTechTileEntity) {
                MetaTileEntity metaTileEntity = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
                ItemStack itemStack = metaTileEntity.getStackForm();
                drops.add(itemStack);

            } else {
                // 普通方块掉落
                IBlockState blockState = entry.getValue().getBlockState();
                ItemStack stack = new ItemStack(blockState.getBlock(), 1,
                        blockState.getBlock().getMetaFromState(blockState));
                drops.add(stack);
            }
        }

        ItemStack controllerStack = getStackForm();
        drops.add(controllerStack);

        GTUtility.spawnDropsAtPlayer(drops, player, world, player.getRNG());

        // 最后拆除控制器自身
        onRemoval();
        invalidate();
        getWorld().setBlockToAir(getPos());
    }

    public String recipeMapsToString() {
        return "";
    }

    /**
     * @param part the part to check
     * @return if the multiblock part is terrain and weather resistant
     */
    public boolean isMultiblockPartWeatherResistant(@NotNull IMultiblockPart part) {
        return false;
    }
}
