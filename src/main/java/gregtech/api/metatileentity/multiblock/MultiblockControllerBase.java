package gregtech.api.metatileentity.multiblock;

import gregtech.api.GregTechAPI;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IMultiblockController;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.BlockWorldState;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.MultiPiecePreviewAssembler;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.MultiblockState;
import gregtech.api.pattern.PatternError;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.PieceRuntimes;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.StructureFailureTrace;
import gregtech.api.pattern.StructureTrace;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.pattern.casing.StructureChannelValues;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.StructureCheckState;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.unification.material.Material;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gregtech.api.util.RelativeDirection;
import gregtech.api.util.world.DummyWorld;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOrientedCubeRenderer;
import gregtech.common.ConfigHolder;

import net.minecraft.block.Block;
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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static gregtech.api.capability.GregtechDataCodes.*;

public abstract class MultiblockControllerBase extends MetaTileEntity implements IMultiblockController {

    private final Map<MultiblockAbility<Object>, AbilityInstances> multiblockAbilities = new HashMap<>();
    private final List<IMultiblockPart> multiblockParts = new ArrayList<>();
    private final MultiblockStructureCheckScheduler structureCheckScheduler = new MultiblockStructureCheckScheduler();
    /**
     * @deprecated Use {@link #patternTemplate} + {@link #multiblockState} for new code. Retained for backward
     * compatibility during migration. Will be removed in version 2.10.
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2.10")
    @Nullable
    public BlockPattern structurePattern;
    /** Shared immutable structure template (new architecture) */
    @Nullable
    protected BlockPatternTemplate patternTemplate;
    /** Per-instance mutable state for pattern checking (new architecture) */
    @Nullable
    protected MultiblockState multiblockState;
    /** Multi-piece pattern for super-large structures (P3, opt-in) */
    @Nullable
    protected MultiPiecePattern multiPiecePattern;
    /**
     * Per-controller state for the multi-piece pattern.
     * Built in {@link #reinitializeStructurePattern()} and rebuilt whenever the
     * pattern itself is rebuilt. Null if the controller has no multi-piece pattern.
     *
     * <p>This is the canonical place for per-instance state (the {@link MultiblockState}
     * per piece, plus dirty/validated flags, formed-position set, and the
     * repeatable-piece search cache). The {@link MultiPiecePattern} itself is
     * stateless and safe to share across controllers of the same multiblock type.
     */
    @Nullable
    protected PieceRuntimes pieceRuntimes;
    /** Canonical structure definition. Legacy hooks are adapted into this model. */
    @Nullable
    private StructureDefinition<?> structureDefinition;
    /** V3 per-controller structure runtime. */
    @Nullable
    private StructureRuntime structureRuntime;
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

    public static TraceabilityPredicate tilePredicate(
            @NotNull BiFunction<BlockWorldState, MetaTileEntity, Boolean> predicate,
            @Nullable Supplier<BlockInfo[]> candidates) {
        return MultiblockPredicates.tilePredicate(predicate, candidates);
    }

    public static TraceabilityPredicate metaTileEntities(MetaTileEntity... metaTileEntities) {
        return MultiblockPredicates.metaTileEntities(metaTileEntities);
    }

    public static TraceabilityPredicate abilities(MultiblockAbility<?>... allowedAbilities) {
        return MultiblockPredicates.abilities(allowedAbilities);
    }

    public static TraceabilityPredicate states(IBlockState... allowedStates) {
        return MultiblockPredicates.states(allowedStates);
    }

    @NotNull
    protected static TraceabilityPredicate energyOutput(int tier, boolean isMinTier) {
        return MultiblockPredicates.energyOutput(tier, isMinTier);
    }

    @NotNull
    protected static TraceabilityPredicate energyInput(int tier, boolean isMinTier) {
        return MultiblockPredicates.energyInput(tier, isMinTier);
    }

    @NotNull
    protected static TraceabilityPredicate laserOutput(int tier, boolean isMinTier) {
        return MultiblockPredicates.laserOutput(tier, isMinTier);
    }

    @NotNull
    protected static TraceabilityPredicate laserInput(int tier, boolean isMinTier) {
        return MultiblockPredicates.laserInput(tier, isMinTier);
    }


    /**
     * Use this predicate for Frames in your Multiblock. Allows for Framed Pipes as well as normal Frame blocks.
     */
    public static TraceabilityPredicate frames(Material... frameMaterials) {
        return MultiblockPredicates.frames(frameMaterials);
    }

    public static TraceabilityPredicate blocks(Block... block) {
        return MultiblockPredicates.blocks(block);
    }

    public static TraceabilityPredicate air() {
        return TraceabilityPredicate.AIR;
    }

    public static TraceabilityPredicate any() {
        return TraceabilityPredicate.ANY;
    }

    @Deprecated
    public static TraceabilityPredicate heatingCoils() {
        return TraceabilityPredicate.HEATING_COILS.get();
    }

    /**
     * Static version of {@link #selfPredicate()} for multi-variant controllers. Creates a center predicate that matches
     * any controller instance whose class equals or extends the given class. Suitable for machines that register
     * multiple IDs with the same class (e.g., LargeTurbine, LargeBoiler, LargeMiner).
     *
     * <p>Usage:
     * <pre>{@code
     * private static final SoftTemplate TEMPLATE = TemplatePool.getInstance().register(
     *     "gregtech:large_turbine/steam", () ->
     *     DeclarativePatternBuilder.start()
     *         .where('S', selfPredicateByClass(MetaTileEntityLargeTurbine.class))
     *         ...
     *         .buildTemplate()
     * );
     * }</pre>
     *
     * @param controllerClass the exact controller class to match
     * @return a center predicate matching all instances of that class
     */
    @NotNull
    public static TraceabilityPredicate selfPredicate(
            @NotNull Class<? extends MultiblockControllerBase> controllerClass) {
        return MultiblockPredicates.selfPredicate(controllerClass);
    }

    /**
     * Collect all unique channels referenced by predicates in the given template.
     */
    @NotNull
    protected static List<StructureChannel> collectChannelsFromTemplate(
            @NotNull BlockPatternTemplate template) {
        return MultiblockStructureChannels.collectChannelsFromTemplate(template);
    }

    @Override
    public boolean isStructureFormed() {
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
        if (this.structureDefinition.isSinglePiece()) {
            this.patternTemplate = this.multiPiecePattern.getPrimaryPiece().getTemplate();
            this.multiblockState = this.patternTemplate.createState();
        } else {
            this.patternTemplate = null;
            this.multiblockState = null;
        }
        // Per-controller state for the multi-piece pattern. Built every time the
        // pattern is rebuilt (including first construction).
        this.pieceRuntimes = new PieceRuntimes(this.multiPiecePattern);
        this.structureRuntime = new StructureRuntime(this.structureDefinition, this.patternTemplate,
                this.multiblockState, this.multiPiecePattern, this.pieceRuntimes);
        this.structureRuntime.copyFormedStateFrom(previousRuntime);
        this.structurePattern = (this.patternTemplate != null)
                ? new BlockPattern(this.patternTemplate, this.multiblockState)
                : null;
        StructureTrace.debug(this, "runtime-reinitialized", this.structureRuntime.describeShape());
    }

    @NotNull
    @SuppressWarnings("deprecation")
    private StructureDefinition<?> resolveStructureDefinition() {
        StructureDefinition<?> definition = createStructureDefinition();
        if (definition != null) {
            return definition;
        }

        MultiPiecePattern legacyMultiPiece = createMultiPiecePattern();
        if (legacyMultiPiece != null) {
            RelativeDirection[] dirs = legacyMultiPiece.getPrimaryPiece().getTemplate().getStructureDir();
            StructureTrace.debug(this, "legacy-adapter",
                    "source=createMultiPiecePattern, pieces=" + legacyMultiPiece.getPieceList().size());
            return StructureDefinition.fromMultiPiecePattern(dirs, legacyMultiPiece);
        }

        BlockPatternTemplate legacyTemplate = createStructureTemplate();
        StructureTrace.debug(this, "legacy-adapter", "source=createStructureTemplate, pieces=1");
        return StructureDefinition.fromTemplate(legacyTemplate);
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
     * Creates the structure pattern for this multiblock.
     *
     * @return structure pattern of this multiblock
     * @deprecated Override {@link #createStructureTemplate()} instead for new code. This method is retained for
     * backward compatibility with existing subclasses. The default implementation of {@link #createStructureTemplate()}
     * delegates to this method. Will be removed in version 2.10.
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2.10")
    @NotNull
    protected BlockPattern createStructurePattern() {
        throw new UnsupportedOperationException(
                "Override createStructureTemplate() instead of createStructurePattern()");
    }

    /**
     * Override this method to provide a shared immutable structure template. The template is shared across all
     * instances of the same machine type, while each instance holds its own mutable {@link MultiblockState}.
     *
     * <p>Default implementation delegates to the deprecated {@link #createStructurePattern()}
     * for backward compatibility.
     *
     * <p>For optimal memory usage, subclasses should override this method and return
     * a statically cached {@link BlockPatternTemplate} instance.
     *
     * @return the immutable structure template
     * @see FactoryBlockPattern#buildTemplate()
     */
    @NotNull
    @SuppressWarnings("deprecation")
    protected BlockPatternTemplate createStructureTemplate() {
        return createStructurePattern().getTemplate();
    }

    /**
     * Override this method to provide a multi-piece pattern for super-large structures. When this returns non-null, the
     * structure is checked piece-by-piece: only dirty pieces are re-validated when a block change occurs.
     *
     * <p>Standard multiblocks should NOT override this method. It is only useful for
     * structures with thousands of blocks that benefit from partial re-checking.
     *
     * @return the multi-piece pattern, or null to use the standard single-pattern mode
     */
    @Nullable
    protected MultiPiecePattern createMultiPiecePattern() {
        return null;
    }

    /**
     * Create a StructureDefinition for this multiblock.
     * Override this for new structures; legacy {@link #createStructureTemplate()}
     * and {@link #createMultiPiecePattern()} implementations are adapted into a
     * definition by {@link #resolveStructureDefinition()}.
     *
     * <p>Must return an idempotent instance — use
     * {@link StructureDefinition#getOrBuild(String, java.util.function.Supplier)}
     * to ensure this.
     *
     * @return the structure definition, or null to use legacy adapters
     */
    @Nullable
    protected StructureDefinition<?> createStructureDefinition() {
        return null;
    }

    /**
     * @return the multi-piece pattern if this controller uses one, or null
     * @deprecated Prefer {@link #getStructureDefinition()} and its compiled
     *             {@link gregtech.api.pattern.MultiPiecePattern} via
     *             {@link StructureDefinition#getCompiledPattern()}. This accessor is
     *             retained for runtime structure checking internals and external mods.
     */
    @Nullable
    @Deprecated
    public MultiPiecePattern getMultiPiecePattern() {
        return multiPiecePattern;
    }

    /**
     * Get the per-controller state for the multi-piece pattern.
     * Each controller of a given multiblock type has its own independent
     * {@link PieceRuntimes} so that per-instance state (the
     * {@link MultiblockState} per piece, dirty/validated flags, etc.) is not
     * shared between independent controllers. See {@link PieceRuntime} for
     * the underlying per-piece state holder.
     *
     * @return the controller's per-piece state, or null if this controller
     *         has no multi-piece pattern (legacy single-piece path)
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
                if (multiblockState != null) {
                    // Unregister before clearing cache so positions can be properly cleaned up
                    MultiblockWorldData.get(getWorld()).unregisterMultiblock(this);
                    multiblockState.clearCache();
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

    public TraceabilityPredicate selfPredicate() {
        return metaTileEntities(this).setCenter();
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
        delayCheck = delay;
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
        delayStructureCheckStandby = Math.max(Math.min(1200, delay), 20);
    }

    public int getDelayStructureCheckWork() {
        return Math.max(delayStructureCheckWork, 20);
    }

    public void setDelayStructureCheckWork(int delay) {
        delayStructureCheckWork = Math.max(Math.min(1200, delay), 20);
    }

    public void checkStructurePattern() {
        StructureTrace.debug(this, "check-start", structureRuntime == null ? null : structureRuntime.describeShape());
        if (this.structureDefinition != null) {
            checkDefinitionStructure();
            return;
        }
        checkLegacyStructure();
    }

    private void checkDefinitionStructure() {
        StructureCheckState.Result result = structureRuntime == null
                ? this.structureDefinition.createState().check(
                        getWorld(), getPos(), getFrontFacingForStructure(),
                        getUpwardsFacing(), allowsFlip(), null, this)
                : structureRuntime.getEvaluator().checkDefinition(
                        getWorld(), getPos(), getFrontFacingForStructure(),
                        getUpwardsFacing(), allowsFlip(), null, this);
        if (!result.success) {
            if (structureRuntime != null) {
                StructureFailureTrace failure = StructureTrace.failure(
                        this, "definition", "CHECK", result.error, result.missingAbilities);
                structureRuntime.recordCheckFailure(failure, result.missingAbilities);
            }
            StructureTrace.debug(this, "check-failed", "path=definition, missingAbilities=" +
                    StructureTrace.describeMissingAbilities(result.missingAbilities));
            if (this.structureFormed) {
                invalidateStructure();
            }
            return;
        }

        PatternMatchContext context = result.context;
        if (context == null) {
            recordAssemblyRejection("definition", "Successful definition check returned no match context");
            return;
        }

        StructureChannelValues channelValues = StructureChannelValues.fromContext(context);
        if (!structureFormed) {
            MultiblockStructureAssembler.Assembly assembly =
                    MultiblockStructureAssembler.assemble(this, context);
            if (!assembly.successful) {
                recordAssemblyRejection("definition", assembly.failureMessage);
                return;
            }

            formNewStructure(context, assembly, result.metadata, channelValues, result.flipped, "definition");
            MultiblockStructureRegistration.registerFormedDefinition(this, multiPiecePattern, pieceRuntimes);
            return;
        }

        MultiblockStructureAssembler.Reassembly reassembly =
                MultiblockStructureAssembler.reassemble(this, context, this.multiblockParts);
        if (!reassembly.successful) {
            recordAssemblyRejection("definition", reassembly.failureMessage);
            return;
        }

        setFlipped(result.flipped);
        commitReassembly(context, reassembly, result.metadata, channelValues);
        StructureTrace.debug(this, "still-valid", "path=definition, metadata=" + getFormedMetadata());
    }

    private void checkLegacyStructure() {
        if (multiblockState == null) return;

        PatternMatchContext context = structureRuntime == null
                ? multiblockState.checkPatternFastAt(
                        getWorld(), getPos(), getFrontFacingForStructure(), getUpwardsFacing(), allowsFlip(),
                        isDelayCheck() && ConfigHolder.machines.enableStructureCheckSample)
                : structureRuntime.getEvaluator().checkSingle(
                        getWorld(), getPos(), getFrontFacingForStructure(), getUpwardsFacing(), allowsFlip(),
                        isDelayCheck() && ConfigHolder.machines.enableStructureCheckSample);
        Map<MultiblockAbility<?>, Integer> legacyMissingAbilities = context == null
                ? multiblockState.getMissingAbilities()
                : Collections.emptyMap();
        if (context == null && structureRuntime != null) {
            StructureFailureTrace failure = StructureTrace.failure(this, "legacy-template", "CHECK",
                    multiblockState.getError(), legacyMissingAbilities);
            structureRuntime.recordCheckFailure(failure, legacyMissingAbilities);
            StructureTrace.debug(this, "check-failed", "path=legacy-template, error=" +
                    structureRuntime.getLastFailure());
        }
        if (context != null && !structureFormed) {
            MultiblockStructureAssembler.Assembly assembly =
                    MultiblockStructureAssembler.assemble(this, context);
            if (!assembly.successful) {
                recordAssemblyRejection("legacy-template", assembly.failureMessage);
                return;
            }

            StructureChannelValues channelValues = StructureChannelValues.fromContext(context);
            formNewStructure(context, assembly, null, channelValues, context.neededFlip(), "legacy-template");

            // Unregister from async checker since we're now formed (P2)
            AsyncStructureChecker.getInstance().unregister(this);

            MultiblockStructureRegistration.registerFormedLegacy(this, multiPiecePattern, pieceRuntimes,
                    multiblockState);
        } else if (context == null && structureFormed) {
            invalidateStructure();
        } else if (context != null) {
            MultiblockStructureAssembler.Reassembly reassembly =
                    MultiblockStructureAssembler.reassemble(this, context, this.multiblockParts);
            if (!reassembly.successful) {
                recordAssemblyRejection("legacy-template", reassembly.failureMessage);
                return;
            }

            setFlipped(context.neededFlip());
            commitReassembly(context, reassembly, getFormedMetadata(),
                    StructureChannelValues.fromContext(context));
            StructureTrace.debug(this, "still-valid", "path=legacy-template");

            MultiblockStructureRegistration.reregisterLegacyCache(this, multiblockState);
        }
    }

    private void formNewStructure(@NotNull PatternMatchContext context,
                                  @NotNull MultiblockStructureAssembler.Assembly assembly,
                                  @Nullable FormedStructureMetadata metadata,
                                  @NotNull StructureChannelValues channelValues,
                                  boolean flipped,
                                  @NotNull String path) {
        setFlipped(flipped);
        this.multiblockParts.addAll(assembly.parts);
        this.multiblockAbilities.putAll(assembly.abilities);
        assembly.parts.forEach(part -> part.addToMultiBlock(this));
        this.structureFormed = true;
        if (structureRuntime != null) {
            structureRuntime.commitSuccessfulCheck(metadata, channelValues);
        }
        writeCustomData(STRUCTURE_FORMED, buf -> buf.writeBoolean(true));
        formStructure(context);
        StructureTrace.debug(this, "formed", "path=" + path + ", metadata=" + metadata +
                ", channels=" + channelValues);
    }

    private boolean commitReassembly(@NotNull PatternMatchContext context,
                                     @NotNull MultiblockStructureAssembler.Reassembly reassembly,
                                     @Nullable FormedStructureMetadata metadata,
                                     @NotNull StructureChannelValues channelValues) {
        if (reassembly.changed) {
            reassembly.removedParts.forEach(part -> part.removeFromMultiBlock(this));
            this.multiblockParts.clear();
            this.multiblockParts.addAll(reassembly.parts);
            this.multiblockAbilities.clear();
            this.multiblockAbilities.putAll(reassembly.abilities);
            reassembly.addedParts.forEach(part -> part.addToMultiBlock(this));
        }

        if (structureRuntime != null) {
            structureRuntime.commitSuccessfulCheck(metadata, channelValues);
        }
        if (reassembly.changed) {
            formStructure(context);
            StructureTrace.debug(this, "reassembled", "channels=" + channelValues);
        }
        return reassembly.changed;
    }

    private void recordAssemblyRejection(@NotNull String path, @Nullable String detail) {
        String message = detail == null ? "Structure assembly was rejected without a reason" : detail;
        StructureTrace.debug(this, "commit-rejected", "path=" + path + ", reason=" + message);
        if (structureRuntime != null) {
            StructureFailureTrace failure = StructureTrace.commitFailure(this, path, message);
            structureRuntime.recordCheckFailure(failure, Collections.emptyMap());
        }
    }

    /**
     * Re-collects structure parts and abilities from a successful pattern match without invalidating the whole
     * multiblock. This is used by normal cached checks and by large multi-piece structures when only one piece
     * changed.
     *
     * @return true if the part/ability set changed and subclass form logic was re-run
     */
    protected boolean reassembleStructure(@NotNull PatternMatchContext context) {
        MultiblockStructureAssembler.Reassembly reassembly =
                MultiblockStructureAssembler.reassemble(this, context, this.multiblockParts);
        if (!reassembly.successful) {
            recordAssemblyRejection("runtime", reassembly.failureMessage);
            return false;
        }

        setFlipped(context.neededFlip());
        return commitReassembly(context, reassembly, getFormedMetadata(),
                StructureChannelValues.fromContext(context));
    }

    /**
     * Checks if a multiblock ability at a given block pos should be added to the ability instances
     *
     * @return true if the ability should be added to this multiblocks ability instances
     */
    protected <T> boolean checkAbilityPart(MultiblockAbility<T> ability, BlockPos pos) {
        return true;
    }

    protected void formStructure(PatternMatchContext context) {}

    /**
     * Multi-piece structure check (P3). Only re-validates dirty pieces instead of the entire pattern. If any piece
     * becomes invalid, the entire structure is invalidated.
     */
    protected void checkMultiPieceStructure() {
        checkStructurePattern();
        MultiblockStructureRegistration.refreshMultiPieceRegistration(this, multiPiecePattern, pieceRuntimes);
    }

    /**
     * Register a multi-piece pattern with the event-driven system. Collects all positions from all active, validated
     * pieces and registers them.
     */
    protected void registerMultiPiecePattern() {
        MultiblockStructureRegistration.registerMultiPiecePattern(this, multiPiecePattern, pieceRuntimes);
    }

    /**
     * @return the immutable pattern template, or null if not initialized
     * @deprecated Prefer {@link #getStructureDefinition()} and (for 1-piece views)
     *             {@link StructureDefinition#getPrimaryTemplate()}. This accessor only
     *             returns a non-null value when the definition is a single piece;
     *             multi-piece structures must use {@link #getStructureDefinition()}.
     */
    @Nullable
    @Deprecated
    public BlockPatternTemplate getPatternTemplate() {
        return patternTemplate;
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

    /**
     * @return the per-instance mutable state, or null if not initialized
     * @deprecated Prefer {@link #getStructureDefinition()}. Runtime structure checking
     *             should use the SD's compiled products; this accessor is retained for
     *             internal main-thread state and a small number of legacy call sites.
     */
    @Nullable
    @Deprecated
    public MultiblockState getMultiblockState() {
        return multiblockState;
    }

    @Nullable
    public PatternError getLastStructureError() {
        StructureFailureTrace failure = structureRuntime == null ? null : structureRuntime.getLastFailure();
        return failure == null ? null : failure.getError();
    }

    /**
     * Get the canonical structure definition. Legacy templates are adapted into
     * this model during {@link #reinitializeStructurePattern()}.
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

    /**
     * Hook for multiblocks whose preview/build dimensions are controlled by channels
     * outside the canonical runtime template. Returning {@code true} means the
     * structure build was handled by the controller.
     */
    public boolean autoBuildStructure(@NotNull EntityPlayer player,
                                      @Nullable Map<String, Integer> channelValues,
                                      boolean skipHatches) {
        return false;
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
        this.multiblockAbilities.clear();
        this.multiblockParts.clear();
        this.structureFormed = false;
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

        if (getWorld() != null && !getWorld().isRemote && multiblockState != null) {
            // Unregister before clearing cache so positions can be properly cleaned up
            MultiblockWorldData.get(getWorld()).unregisterMultiblock(this);
            // clear cache since the cache has no concept of pre-existing facing
            // for the controller block (or any block) in the structure
            multiblockState.clearCache();
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
        if (patternTemplate == null) {
            reinitializeStructurePattern();
            if (patternTemplate == null) {
                return MultiblockStructureChannels.collectChannelsFromMultiPiece(multiPiecePattern);
            }
        }
        return collectChannelsFromTemplate(patternTemplate);
    }

    /**
     * Get the valid value range for a given channel in this multiblock's pattern. For tiered casing channels, the range
     * is [0, maxCandidateIndex]. For repeatable aisle channels, the range is [aisleMin, aisleMax].
     *
     * @param channel the channel to query
     * @return int[2] with [min, max], or [0, 0] if channel not found in pattern
     */
    @NotNull
    public int[] getChannelRange(@NotNull StructureChannel channel) {
        if (patternTemplate == null) {
            reinitializeStructurePattern();
            if (patternTemplate == null) {
                return MultiblockStructureChannels.getChannelRangeFromMultiPiece(multiPiecePattern, channel);
            }
        }
        String channelName = channel.getName();
        return MultiblockStructureChannels.getTemplateChannelRange(patternTemplate, channelName);
    }

    public List<MultiblockShapeInfo> getMatchingShapes() {
        if (this.patternTemplate == null) {
            this.reinitializeStructurePattern();
            if (this.patternTemplate == null) {
                return MultiblockStructurePreviews.buildMultiPieceShapes(this, multiPiecePattern,
                        pieceRuntimes, structureRuntime, null);
            }
        }
        return MultiblockStructurePreviews.getMatchingShapes(this, this.patternTemplate,
                this.multiblockState, this.structureRuntime, null);
    }

    public List<MultiblockShapeInfo> getMatchingShapes(@Nullable Map<String, Integer> channelValues) {
        if (channelValues == null || channelValues.isEmpty()) {
            return getMatchingShapes();
        }
        if (this.patternTemplate == null) {
            this.reinitializeStructurePattern();
            if (this.patternTemplate == null) {
                return MultiblockStructurePreviews.buildMultiPieceShapes(this, multiPiecePattern,
                        pieceRuntimes, structureRuntime, channelValues);
            }
        }
        return MultiblockStructurePreviews.getMatchingShapes(this, this.patternTemplate,
                this.multiblockState, this.structureRuntime, channelValues);
    }

    /**
     * Build preview shapes for multi-piece structures (StructureDefinition with multiple pieces).
     * Merges all pieces' previews into a single combined shape by offsetting each piece
     * along the aisle direction (the repeat axis for repeatable pieces).
     *
     * <p>The preview array from getPreview() is indexed as [worldX][worldY][worldZ].
     * For structure dir [RIGHT, BACK, UP] with NORTH-facing:
     * worldX = char index, worldY = repeat/aisle index, worldZ = -row index.
     * So merging along the aisle direction offsets worldY (the second array dimension).
     */
    List<MultiblockShapeInfo> buildMultiPieceShapes(@Nullable Map<String, Integer> channelValues) {
        return MultiblockStructurePreviews.buildMultiPieceShapes(this, multiPiecePattern,
                pieceRuntimes, structureRuntime, channelValues);
    }

    /**
     * Build a predicate map for multi-piece structures.
     * Maps block positions (in the merged preview array's 0-based coordinate system)
     * to their TraceabilityPredicate for right-click block cycling in JEI.
     *
     * <p>Iteration matches {@link MultiblockState#getPreview(int[], Map)} so that
     * every block rendered in the JEI preview (including all repeated slices of a
     * {@link RepeatGroupPiece}) gets a corresponding predicate entry. The previous
     * implementation only walked the base template, leaving repeated slices with
     * no predicate, which made right-click cycling silent for those positions.
     */
    @NotNull
    public Map<BlockPos, TraceabilityPredicate> buildMultiPiecePredicateMap() {
        return MultiblockStructurePreviews.buildMultiPiecePredicateMap(this, multiPiecePattern,
                pieceRuntimes, structureRuntime);
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
        return MultiblockStructurePreviews.getMatchingShapeForPiece(this, multiPiecePattern,
                pieceRuntimes, structureRuntime, pieceIndex, channelValues);
    }

    @Nullable
    public MultiPiecePreviewAssembler.PieceResult getMatchingPreviewPiece(
            int pieceIndex, @Nullable Map<String, Integer> channelValues) {
        return MultiblockStructurePreviews.getMatchingPreviewPiece(this, multiPiecePattern,
                pieceRuntimes, structureRuntime, pieceIndex, channelValues);
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
        MultiblockState state = this.multiblockState;

        if (!structureFormed || state == null) {
            return;
        }

        // First invalidate structure, removing all part associations
        invalidateStructure();

        World world = getWorld();

        // Get all block positions in the structure
        Map<BlockPos, BlockInfo> blocks = structureRuntime == null
                ? state.getAllStructureBlocks(
                        world, getPos(), getFrontFacingForStructure(), getUpwardsFacing(), isFlipped())
                : structureRuntime.getEvaluator().iterateSingle(
                        world, getPos(), getFrontFacingForStructure(), getUpwardsFacing(), isFlipped());

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
