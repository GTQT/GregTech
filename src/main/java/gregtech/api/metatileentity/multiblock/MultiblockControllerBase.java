package gregtech.api.metatileentity.multiblock;

import gregtech.api.GregTechAPI;
import gregtech.api.block.VariantActiveBlock;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IMultiblockController;
import gregtech.api.capability.IMultipleRecipeMaps;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.BlockWorldState;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.MultiblockState;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.pattern.casing.StructureChannelValues;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.unification.material.Material;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gregtech.api.util.RelativeDirection;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.api.util.world.DummyWorld;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.handler.MultiblockPreviewRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOrientedCubeRenderer;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.MetaBlocks;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static gregtech.api.capability.GregtechDataCodes.*;

public abstract class MultiblockControllerBase extends MetaTileEntity implements IMultiblockController {

    private final Map<MultiblockAbility<Object>, AbilityInstances> multiblockAbilities = new HashMap<>();
    private final List<IMultiblockPart> multiblockParts = new ArrayList<>();

    /**
     * @deprecated Use {@link #patternTemplate} + {@link #multiblockState} for new code.
     *             Retained for backward compatibility during migration.
     */
    @Deprecated
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

    /** Channel values collected from the formed structure (populated in formStructure) */
    @NotNull
    private StructureChannelValues formedChannelValues = new StructureChannelValues();

    /** Tick counter for async check fallback — counts ticks since registering for async check */
    private int asyncCheckFallbackTicks = 0;

    /** Interval (in ticks) before falling back to a main-thread check when async check has not formed */
    private static final int ASYNC_FALLBACK_INTERVAL = 100;

    public MultiblockControllerBase(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    @Override
    public boolean isStructureFormed() {
        return structureFormed;
    }

    public static TraceabilityPredicate tilePredicate(
            @NotNull BiFunction<BlockWorldState, MetaTileEntity, Boolean> predicate,
            @Nullable Supplier<BlockInfo[]> candidates) {
        return new TraceabilityPredicate(blockWorldState -> {
            TileEntity tileEntity = blockWorldState.getTileEntity();
            if (!(tileEntity instanceof IGregTechTileEntity))
                return false;
            MetaTileEntity metaTileEntity = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
            if (predicate.apply(blockWorldState, metaTileEntity)) {
                if (metaTileEntity instanceof IMultiblockPart) {
                    Set<IMultiblockPart> partsFound = blockWorldState.getMatchContext().getOrCreate("MultiblockParts",
                            HashSet::new);
                    partsFound.add((IMultiblockPart) metaTileEntity);
                }
                return true;
            }
            return false;
        }, candidates);
    }

    public static TraceabilityPredicate metaTileEntities(MetaTileEntity... metaTileEntities) {
        ResourceLocation[] ids = Arrays.stream(metaTileEntities).filter(Objects::nonNull)
                .map(tile -> tile.metaTileEntityId).toArray(ResourceLocation[]::new);
        return tilePredicate((state, tile) -> ArrayUtils.contains(ids, tile.metaTileEntityId),
                getCandidates(metaTileEntities));
    }

    private static Supplier<BlockInfo[]> getCandidates(MetaTileEntity... metaTileEntities) {
        return () -> Arrays.stream(metaTileEntities).filter(Objects::nonNull).map(tile -> {
            // TODO
            MetaTileEntityHolder holder = new MetaTileEntityHolder();
            holder.setMetaTileEntity(tile);
            holder.getMetaTileEntity().onPlacement();
            holder.getMetaTileEntity().setFrontFacing(EnumFacing.SOUTH);
            return new BlockInfo(tile.getBlock().getDefaultState(), holder);
        }).toArray(BlockInfo[]::new);
    }

    private static Supplier<BlockInfo[]> getCandidates(IBlockState... allowedStates) {
        return () -> Arrays.stream(allowedStates).map(state -> new BlockInfo(state, null)).toArray(BlockInfo[]::new);
    }

    public static TraceabilityPredicate abilities(MultiblockAbility<?>... allowedAbilities) {
        return tilePredicate((state, tile) -> {
            if (tile instanceof IMultiblockAbilityPart<?> abilityPart) {
                for (var ability : abilityPart.getAbilities()) {
                    if (ArrayUtils.contains(allowedAbilities, ability))
                        return true;
                }
            }
            return false;
        }, getCandidates(Arrays.stream(allowedAbilities)
                .flatMap(ability -> MultiblockAbility.REGISTRY.get(ability).stream())
                .toArray(MetaTileEntity[]::new)));
    }

    public static TraceabilityPredicate states(IBlockState... allowedStates) {
        return new TraceabilityPredicate(blockWorldState -> {
            IBlockState state = blockWorldState.getBlockState();
            if (state.getBlock() instanceof VariantActiveBlock) {
                blockWorldState.getMatchContext().getOrPut("VABlock", new LinkedList<>()).add(blockWorldState.getPos());
            }
            return ArrayUtils.contains(allowedStates, state);
        }, getCandidates(allowedStates));
    }

    /**
     * Use this predicate for Frames in your Multiblock. Allows for Framed Pipes as well as normal Frame blocks.
     */
    public static TraceabilityPredicate frames(Material... frameMaterials) {
        return states(Arrays.stream(frameMaterials).map(m -> MetaBlocks.FRAMES.get(m).getBlock(m))
                .toArray(IBlockState[]::new))
                .or(new TraceabilityPredicate(blockWorldState -> {
                    TileEntity tileEntity = blockWorldState.getTileEntity();
                    if (!(tileEntity instanceof IPipeTile<?, ?> pipeTile)) {
                        return false;
                    }
                    return ArrayUtils.contains(frameMaterials, pipeTile.getFrameMaterial());
                }));
    }

    public static TraceabilityPredicate blocks(Block... block) {
        return new TraceabilityPredicate(
                blockWorldState -> ArrayUtils.contains(block, blockWorldState.getBlockState().getBlock()),
                getCandidates(Arrays.stream(block).map(Block::getDefaultState).toArray(IBlockState[]::new)));
    }

    public static TraceabilityPredicate air() {
        return TraceabilityPredicate.AIR;
    }

    public static TraceabilityPredicate any() {
        return TraceabilityPredicate.ANY;
    }

    public static TraceabilityPredicate heatingCoils() {
        return TraceabilityPredicate.HEATING_COILS.get();
    }

    @Override
    public void onPlacement(EntityLivingBase placer) {
        super.onPlacement(placer);
        reinitializeStructurePattern();
    }

    public void reinitializeStructurePattern() {
        // Template-first approach: create shared template, then per-instance state
        this.patternTemplate = createStructureTemplate();
        this.multiblockState = this.patternTemplate.createState();
        // Maintain backward-compatible structurePattern sharing the same template and state
        this.structurePattern = new BlockPattern(this.patternTemplate, this.multiblockState);
        // Initialize multi-piece pattern if the subclass provides one (P3)
        this.multiPiecePattern = createMultiPiecePattern();
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
        // First tick always performs a full structure check on main thread
        if (isFirstTick()) {
            checkStructurePattern();
            return;
        }

        // Event-driven mode: formed multiblocks only re-check when a block change is detected
        if (ConfigHolder.machines.enableEventDrivenStructureCheck
                && structureFormed && getWorld() != null && !(getWorld() instanceof DummyWorld)) {
            MultiblockWorldData worldData = MultiblockWorldData.get(getWorld());
            if (worldData.isRegistered(this)) {
                if (worldData.hasPendingRecheck(this, getWorld().getTotalWorldTime())) {
                    if (ConfigHolder.machines.debugStructureCheck) {
                        GTLog.logger.debug("[StructureCheck] Event-driven recheck triggered for {}",
                                getMetaName());
                    }
                    // Multi-piece mode (P3): only check dirty pieces instead of full pattern
                    if (multiPiecePattern != null) {
                        checkMultiPieceStructure();
                    } else {
                        checkStructurePattern();
                    }
                }
                return;
            }
        }

        // Unformed multiblocks: register for async checking (P2)
        if (ConfigHolder.machines.enableAsyncStructureCheck && allowsAsyncStructureCheck()
                && !structureFormed && getWorld() != null && !getWorld().isRemote
                && !(getWorld() instanceof DummyWorld)) {
            AsyncStructureChecker checker = AsyncStructureChecker.getInstance();
            if (checker.isRunning()) {
                checker.registerForAsyncCheck(this);
                asyncCheckFallbackTicks++;
                // Fallback: if async check has not formed after ASYNC_FALLBACK_INTERVAL ticks,
                // perform a full main-thread check to handle edge cases (e.g. snapshot coverage issues)
                if (asyncCheckFallbackTicks >= ASYNC_FALLBACK_INTERVAL) {
                    asyncCheckFallbackTicks = 0;
                    if (ConfigHolder.machines.debugStructureCheck) {
                        GTLog.logger.debug("[StructureCheck] Async fallback triggered for {}", getMetaName());
                    }
                    checkStructurePattern();
                    if (structureFormed) {
                        checker.unregister(this);
                    }
                }
                return;
            }
        }

        // Fallback: periodic polling (when async checker is not running or DummyWorld)
        int interval = isWorkingForStructureCheck()
                ? getStructureCheckIntervalWorking()
                : getStructureCheckIntervalStandby();
        if (getOffsetTimer() % interval == 0) {
            checkStructurePattern();
        }
    }

    /**
     * Called when the multiblock is formed and validation predicate is matched
     */
    protected abstract void updateFormedValid();

    /**
     * Creates the structure pattern for this multiblock.
     *
     * @return structure pattern of this multiblock
     * @deprecated Override {@link #createStructureTemplate()} instead for new code.
     *             This method is retained for backward compatibility with existing subclasses.
     *             The default implementation of {@link #createStructureTemplate()} delegates to this method.
     */
    @Deprecated
    @NotNull
    protected BlockPattern createStructurePattern() {
        throw new UnsupportedOperationException(
                "Override createStructureTemplate() instead of createStructurePattern()");
    }

    /**
     * Override this method to provide a shared immutable structure template.
     * The template is shared across all instances of the same machine type,
     * while each instance holds its own mutable {@link MultiblockState}.
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
    protected BlockPatternTemplate createStructureTemplate() {
        return createStructurePattern().getTemplate();
    }

    /**
     * Override this method to provide a multi-piece pattern for super-large structures.
     * When this returns non-null, the structure is checked piece-by-piece:
     * only dirty pieces are re-validated when a block change occurs.
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
     * @return the multi-piece pattern if this controller uses one, or null
     */
    @Nullable
    public MultiPiecePattern getMultiPiecePattern() {
        return multiPiecePattern;
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

    /**
     * Static version of {@link #selfPredicate()} for use in static template builders.
     * Creates a center predicate that matches a controller by its registry ID.
     *
     * <p>Usage in static context:
     * <pre>{@code
     * private static final SoftTemplate TEMPLATE = TemplatePool.getInstance().register(
     *     "gregtech:electric_blast_furnace", () ->
     *     DeclarativePatternBuilder.start()
     *         .where('S', selfPredicate(gregtechId("electric_blast_furnace")))
     *         ...
     *         .buildTemplate()
     * );
     * }</pre>
     *
     * @param metaTileEntityId the registry ID of the controller MetaTileEntity
     * @return a center predicate matching that controller type
     */
    @NotNull
    public static TraceabilityPredicate selfPredicate(@NotNull ResourceLocation metaTileEntityId) {
        MetaTileEntity mte = GregTechAPI.mteManager.getRegistry(metaTileEntityId.getNamespace())
                .getObject(metaTileEntityId);
        if (mte == null) {
            throw new IllegalArgumentException("No MetaTileEntity registered with id: " + metaTileEntityId);
        }
        return metaTileEntities(mte).setCenter();
    }

    /**
     * Static version of {@link #selfPredicate()} for multi-variant controllers.
     * Creates a center predicate that matches any controller instance whose class equals or
     * extends the given class. Suitable for machines that register multiple IDs with the same class
     * (e.g., LargeTurbine, LargeBoiler, LargeMiner).
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
    public static TraceabilityPredicate selfPredicateByClass(@NotNull Class<? extends MultiblockControllerBase> controllerClass) {
        return tilePredicate((state, tile) -> controllerClass.isInstance(tile),
                getCandidatesByClass(controllerClass)).setCenter();
    }

    /**
     * Collect all registered MetaTileEntities whose class matches the given controller class
     * and return them as candidate BlockInfo array. Used by {@link #selfPredicateByClass} so that
     * the auto-generated JEI preview can render the controller block.
     */
    @NotNull
    private static Supplier<BlockInfo[]> getCandidatesByClass(
            @NotNull Class<? extends MultiblockControllerBase> controllerClass) {
        return () -> {
            List<MetaTileEntity> matches = new ArrayList<>();
            for (var registry : GregTechAPI.mteManager.getRegistries()) {
                for (MetaTileEntity mte : registry) {
                    if (controllerClass.isInstance(mte)) {
                        matches.add(mte);
                    }
                }
            }
            if (matches.isEmpty()) {
                return new BlockInfo[] { BlockInfo.EMPTY };
            }
            return matches.stream().map(tile -> {
                MetaTileEntityHolder holder = new MetaTileEntityHolder();
                holder.setMetaTileEntity(tile);
                holder.getMetaTileEntity().onPlacement();
                holder.getMetaTileEntity().setFrontFacing(EnumFacing.SOUTH);
                return new BlockInfo(tile.getBlock().getDefaultState(), holder);
            }).toArray(BlockInfo[]::new);
        };
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
     * Returns whether this multiblock is currently "working" for structure check interval purposes.
     * Subclasses override to provide machine-specific working state (e.g., recipe active).
     * When working, the fallback polling uses {@link #getStructureCheckIntervalWorking()}.
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
     * Returns the structure check polling interval (in ticks) when the multiblock is idle/standby.
     * Used in fallback polling mode when event-driven or async checking is unavailable.
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
     * Returns the structure check polling interval (in ticks) when the multiblock is working.
     * Used in fallback polling mode when event-driven or async checking is unavailable.
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void checkStructurePattern() {
        if (multiblockState == null) return;
        PatternMatchContext context = multiblockState.checkPatternFastAt(getWorld(), getPos(),
                getFrontFacing().getOpposite(), getUpwardsFacing(), allowsFlip(),
                isDelayCheck() && ConfigHolder.machines.enableStructureCheckSample);
        if (context != null && !structureFormed) {
            Set<IMultiblockPart> rawPartsSet = context.getOrCreate("MultiblockParts", HashSet::new);
            ArrayList<IMultiblockPart> parts = new ArrayList<>(rawPartsSet);
            for (IMultiblockPart part : parts) {
                if (part.isAttachedToMultiBlock()) {
                    if (!part.canPartShare()) {
                        return;
                    }
                }
            }
            this.setFlipped(context.neededFlip());
            parts.sort(Comparator.comparing(it -> multiblockPartSorter().apply(((MetaTileEntity) it).getPos())));
            Map<MultiblockAbility<Object>, AbilityInstances> abilities = new HashMap<>();
            for (IMultiblockPart part : parts) {
                if (part instanceof IMultiblockAbilityPart abilityPart) {
                    List<MultiblockAbility> abilityList = abilityPart.getAbilities();
                    for (MultiblockAbility ability : abilityList) {
                        if (!checkAbilityPart(ability, ((MetaTileEntity) abilityPart).getPos()))
                            continue;

                        AbilityInstances instances = abilities.computeIfAbsent(ability,
                                AbilityInstances::new);
                        abilityPart.registerAbilities(instances);
                    }
                }
            }
            this.multiblockParts.addAll(parts);
            this.multiblockAbilities.putAll(abilities);
            parts.forEach(part -> part.addToMultiBlock(this));
            this.structureFormed = true;
            this.formedChannelValues = StructureChannelValues.fromContext(context);
            writeCustomData(STRUCTURE_FORMED, buf -> buf.writeBoolean(true));
            formStructure(context);

            // Unregister from async checker since we're now formed (P2)
            AsyncStructureChecker.getInstance().unregister(this);

            // Register with event-driven structure checking system
            if (!(getWorld() instanceof DummyWorld)) {
                if (multiPiecePattern != null) {
                    // Multi-piece mode: do a full check of all pieces after initial form
                    multiPiecePattern.checkAllPieces(getWorld(), getPos(),
                            getFrontFacing().getOpposite(), getUpwardsFacing(), allowsFlip());
                    registerMultiPiecePattern();
                } else if (multiblockState != null && !multiblockState.cache.isEmpty()) {
                    LongSet positions = new LongOpenHashSet(multiblockState.cache.keySet());
                    MultiblockWorldData.get(getWorld()).registerMultiblock(this, positions);
                }
            }
        } else if (context == null && structureFormed) {
            invalidateStructure();
        } else if (context != null) {
            // ensure flip is ok, possibly not necessary but good to check just in case
            if (context.neededFlip() != isFlipped()) {
                setFlipped(context.neededFlip());
            }

            // Re-register if cache was refreshed (e.g. after facing change + clearCache)
            if (multiblockState != null && !multiblockState.cache.isEmpty()
                    && !(getWorld() instanceof DummyWorld)) {
                MultiblockWorldData worldData = MultiblockWorldData.get(getWorld());
                if (!worldData.isRegistered(this)) {
                    LongSet positions = new LongOpenHashSet(multiblockState.cache.keySet());
                    worldData.registerMultiblock(this, positions);
                }
            }
        }
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
     * Multi-piece structure check (P3).
     * Only re-validates dirty pieces instead of the entire pattern.
     * If any piece becomes invalid, the entire structure is invalidated.
     */
    protected void checkMultiPieceStructure() {
        if (multiPiecePattern == null) return;

        boolean allValid = multiPiecePattern.checkDirtyPieces(
                getWorld(), getPos(), getFrontFacing().getOpposite(),
                getUpwardsFacing(), allowsFlip());

        if (!allValid && structureFormed) {
            invalidateStructure();
        }
    }

    /**
     * Register a multi-piece pattern with the event-driven system.
     * Collects all positions from all active, validated pieces and registers them.
     */
    protected void registerMultiPiecePattern() {
        if (multiPiecePattern == null || getWorld() == null || getWorld() instanceof DummyWorld) return;

        LongSet allPositions = multiPiecePattern.getAllPositions();
        if (!allPositions.isEmpty()) {
            MultiblockWorldData.get(getWorld()).registerMultiblock(this, allPositions, multiPiecePattern);
        }
    }

    /**
     * @return the immutable pattern template, or null if not initialized
     */
    @Nullable
    public BlockPatternTemplate getPatternTemplate() {
        return patternTemplate;
    }

    /**
     * @return the per-instance mutable state, or null if not initialized
     */
    @Nullable
    public MultiblockState getMultiblockState() {
        return multiblockState;
    }

    /**
     * Get the channel tier values determined when the structure was formed.
     * Empty if the structure is not currently formed.
     *
     * @return the formed channel values (never null)
     */
    @NotNull
    public StructureChannelValues getFormedChannelValues() {
        return formedChannelValues;
    }

    public void invalidateStructure() {
        // Unregister from event-driven structure checking system
        if (getWorld() != null && !getWorld().isRemote) {
            MultiblockWorldData.get(getWorld()).unregisterMultiblock(this);
        }

        // Reset multi-piece pattern state if present (P3)
        if (multiPiecePattern != null) {
            multiPiecePattern.resetAll();
        }

        this.multiblockParts.forEach(part -> part.removeFromMultiBlock(this));
        this.multiblockAbilities.clear();
        this.multiblockParts.clear();
        this.structureFormed = false;
        this.formedChannelValues = new StructureChannelValues();
        this.asyncCheckFallbackTicks = 0;
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
        if (data.hasKey("UpwardsFacing")) {
            this.upwardsFacing = EnumFacing.VALUES[data.getByte("UpwardsFacing")];
        }
        if (data.hasKey("IsFlipped")) {
            this.isFlipped = data.getBoolean("IsFlipped");
        }
        delayCheck = data.getBoolean("delayCheck");
        delayStructureCheckStandby = data.getInteger("delayStructureCheckStandby");
        delayStructureCheckWork = data.getInteger("delayStructureCheckWork");
        this.reinitializeStructurePattern();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setByte("UpwardsFacing", (byte) upwardsFacing.getIndex());
        data.setBoolean("IsFlipped", isFlipped);
        data.setBoolean("delayCheck", delayCheck);
        data.setInteger("delayStructureCheckStandby", delayStructureCheckStandby);
        data.setInteger("delayStructureCheckWork", delayStructureCheckWork);
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
        TooltipBuilder.createDefault().build(this, tooltip);
        TooltipBuilder.create().addPollution(getPollutionAmount(), getPollutionTicks()).build(this, tooltip);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addStructureInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                                        boolean advanced) {
        // Structure size and tier info
        TooltipBuilder.create().addStructure().build(this, tooltip);

        // Auto-generated structure description from DeclarativePatternBuilder
        if (patternTemplate != null) {
            List<String> structureDesc = patternTemplate.getStructureDescription();
            if (!structureDesc.isEmpty()) {
                tooltip.add("");
                for (String line : structureDesc) {
                    tooltip.add(formatStructureDescriptionLine(line));
                }
            }
        }
    }

    /**
     * Format a raw structure description line for tooltip display.
     * Lines are stored as "type:param1:param2:..." for server-safe storage.
     */
    @SideOnly(Side.CLIENT)
    private String formatStructureDescriptionLine(@NotNull String rawLine) {
        String[] parts = rawLine.split(":", 4);
        if (parts.length < 2) return rawLine;

        switch (parts[0]) {
            case "casing": {
                // "casing:<translationKey>:<minCount>:<maxCount>"
                String name = I18n.format(parts[1]);
                int min = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                int max = parts.length > 3 ? Integer.parseInt(parts[3]) : min;
                if (min == max) {
                    return String.format("  %dx %s", max, name);
                } else {
                    return String.format("  %dx %s (%s %d)", max, name,
                            I18n.format("gregtech.multiblock.tooltip.at_least"), min);
                }
            }
            case "hatch": {
                // "hatch:<abilityName>:<minCount>:<maxCount>"
                String name = I18n.format("gregtech.multiblock.ability." + parts[1]);
                int min = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                int max = parts.length > 3 ? Integer.parseInt(parts[3]) : 1;
                if (min == 0) {
                    return String.format("  0-%dx %s (%s)", max, name,
                            I18n.format("gregtech.multiblock.tooltip.optional"));
                } else if (min == max) {
                    return String.format("  %dx %s", max, name);
                } else {
                    return String.format("  %d-%dx %s", min, max, name);
                }
            }
            case "tiered": {
                // "tiered:<translationKey>:<requiresUniform>"
                String name = I18n.format(parts[1]);
                boolean uniform = parts.length > 2 && Boolean.parseBoolean(parts[2]);
                if (uniform) {
                    return String.format("  %s (%s)", name,
                            I18n.format("gregtech.multiblock.tooltip.same_tier"));
                } else {
                    return "  " + name;
                }
            }
            case "channel": {
                // "channel:<tooltipKey>"
                return String.format("  %s", I18n.format("gregtech.multiblock.tooltip.sub_channel",
                        I18n.format(parts[1])));
            }
            default:
                return rawLine;
        }
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        if (this instanceof IMultipleRecipeMaps) {
            tooltip.add(I18n.format("gregtech.tool_action.screwdriver.toggle_mode_covers"));
        } else {
            tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        }
        if (allowsExtendedFacing()) {
            tooltip.add(I18n.format("gregtech.tool_action.wrench.extended_facing"));
        } else {
            tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        }
        super.addToolUsages(stack, world, tooltip, advanced);
    }

    @Override
    public boolean onRightClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                CuboidRayTraceResult hitResult) {
        if (super.onRightClick(playerIn, hand, facing, hitResult))
            return true;

        if (this.getWorld().isRemote && !this.isStructureFormed() && playerIn.isSneaking() &&
                playerIn.getHeldItem(hand).isEmpty()) {
            MultiblockPreviewRenderer.renderMultiBlockPreview(this, 60000);
            return true;
        }
        return false;
    }

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
        if (getWorld() != null && getWorld().isRemote) {
            MultiblockPreviewRenderer.refreshCurrentPreview(this);
        }
    }

    @Override
    public boolean isValidFrontFacing(EnumFacing facing) {
        return allowsExtendedFacing() || super.isValidFrontFacing(facing);
    }

    // todo tooltip on multis saying if this is enabled or disabled?

    /** Whether this multi can be rotated or face upwards. */
    public boolean allowsExtendedFacing() {
        return true;
    }

    /** Set this to false only if your multiblock is set up such that it could have a wall-shared controller. */
    public boolean allowsFlip() {
        return true;
    }

    /**
     * Returns the list of structure channels supported by this multiblock.
     * Default implementation auto-collects channels from the pattern template's predicates.
     * Subclasses can override for custom behavior or to add channels not in the pattern.
     *
     * @return list of supported StructureChannel instances
     */
    @NotNull
    public List<StructureChannel> getSupportedChannels() {
        if (patternTemplate == null) {
            reinitializeStructurePattern();
            if (patternTemplate == null) {
                return Collections.emptyList();
            }
        }
        return collectChannelsFromTemplate(patternTemplate);
    }

    /**
     * Collect all unique channels referenced by predicates in the given template.
     */
    @NotNull
    protected static List<StructureChannel> collectChannelsFromTemplate(
            @NotNull BlockPatternTemplate template) {
        Set<String> seen = new java.util.LinkedHashSet<>();
        TraceabilityPredicate[][][] matches = template.getBlockMatches();
        for (TraceabilityPredicate[][] layer : matches) {
            for (TraceabilityPredicate[] row : layer) {
                for (TraceabilityPredicate predicate : row) {
                    if (predicate == null) continue;
                    collectChannelNames(predicate.common, seen);
                    collectChannelNames(predicate.limited, seen);
                }
            }
        }
        List<StructureChannel> result = new ArrayList<>();
        String[] aisleChannelNames = template.getAisleChannelNames();
        if (aisleChannelNames != null) {
            for (String name : aisleChannelNames) {
                if (name != null && !name.isEmpty()) {
                    seen.add(name);
                }
            }
        }
        for (String name : seen) {
            StructureChannel channel =
                    gregtech.api.pattern.casing.StructureChannelRegistry.resolve(name);
            if (channel != null) {
                result.add(channel);
            }
        }
        return result;
    }

    private static void collectChannelNames(
            @NotNull List<TraceabilityPredicate.SimplePredicate> predicates,
            @NotNull Set<String> out) {
        for (TraceabilityPredicate.SimplePredicate sp : predicates) {
            if (sp.channelName != null && !sp.channelName.isEmpty()) {
                out.add(sp.channelName);
            }
        }
    }

    /**
     * Get the valid value range for a given channel in this multiblock's pattern.
     * For tiered casing channels, the range is [0, maxCandidateIndex].
     * For repeatable aisle channels, the range is [aisleMin, aisleMax].
     *
     * @param channel the channel to query
     * @return int[2] with [min, max], or [0, 0] if channel not found in pattern
     */
    @NotNull
    public int[] getChannelRange(@NotNull StructureChannel channel) {
        if (patternTemplate == null) {
            reinitializeStructurePattern();
            if (patternTemplate == null) return new int[] { 0, 0 };
        }
        String channelName = channel.getName();

        // Check repeatable aisle channels first
        String[] aisleChannelNames = patternTemplate.getAisleChannelNames();
        int[][] aisleRepetitions = patternTemplate.getAisleRepetitions();
        if (aisleChannelNames != null) {
            for (int i = 0; i < aisleChannelNames.length; i++) {
                if (channelName.equals(aisleChannelNames[i])) {
                    return new int[] { aisleRepetitions[i][0], aisleRepetitions[i][1] };
                }
            }
        }

        // Check tiered casing channels: count max candidates in predicates with this channelName
        int maxCandidates = 0;
        TraceabilityPredicate[][][] matches = patternTemplate.getBlockMatches();
        for (TraceabilityPredicate[][] layer : matches) {
            for (TraceabilityPredicate[] row : layer) {
                for (TraceabilityPredicate predicate : row) {
                    if (predicate == null) continue;
                    maxCandidates = Math.max(maxCandidates,
                            countChannelCandidates(predicate.common, channelName));
                    maxCandidates = Math.max(maxCandidates,
                            countChannelCandidates(predicate.limited, channelName));
                }
            }
        }
        if (maxCandidates > 0) {
            // Channel value semantics: 0 = auto, 1..N = specific candidate (1-based).
            // getChannelCandidateIndex uses (cv - 1) as 0-based index into candidates array.
            return new int[] { 0, maxCandidates };
        }
        return new int[] { 0, 0 };
    }

    private static int countChannelCandidates(
            @NotNull List<TraceabilityPredicate.SimplePredicate> predicates,
            @NotNull String channelName) {
        for (TraceabilityPredicate.SimplePredicate sp : predicates) {
            if (channelName.equals(sp.channelName) && sp.candidates != null) {
                return sp.candidates.get().length;
            }
        }
        return 0;
    }

    public List<MultiblockShapeInfo> getMatchingShapes() {
        if (this.patternTemplate == null) {
            this.reinitializeStructurePattern();
            if (this.patternTemplate == null) {
                return Collections.emptyList();
            }
        }
        int[][] aisleRepetitions = this.patternTemplate.getAisleRepetitions();
        return repetitionDFS(new ArrayList<>(), aisleRepetitions, new Stack<>(), null);
    }

    public List<MultiblockShapeInfo> getMatchingShapes(@Nullable Map<String, Integer> channelValues) {
        if (channelValues == null || channelValues.isEmpty()) {
            return getMatchingShapes();
        }
        if (this.patternTemplate == null) {
            this.reinitializeStructurePattern();
            if (this.patternTemplate == null) {
                return Collections.emptyList();
            }
        }
        int[][] aisleRepetitions = this.patternTemplate.getAisleRepetitions();
        return repetitionDFS(new ArrayList<>(), aisleRepetitions, new Stack<>(), channelValues);
    }

    private List<MultiblockShapeInfo> repetitionDFS(List<MultiblockShapeInfo> pages, int[][] aisleRepetitions,
                                                    Stack<Integer> repetitionStack,
                                                    @Nullable Map<String, Integer> channelValues) {
        if (repetitionStack.size() == aisleRepetitions.length) {
            int[] repetition = new int[repetitionStack.size()];
            for (int i = 0; i < repetitionStack.size(); i++) {
                repetition[i] = repetitionStack.get(i);
            }
            BlockInfo[][][] preview = channelValues != null
                    ? Objects.requireNonNull(this.multiblockState).getPreview(repetition, channelValues)
                    : Objects.requireNonNull(this.multiblockState).getPreview(repetition);
            pages.add(new MultiblockShapeInfo(preview));
        } else {
            int aisleIdx = repetitionStack.size();
            String[] channelNames = this.patternTemplate.getAisleChannelNames();
            String channelName = (channelNames != null && aisleIdx < channelNames.length)
                    ? channelNames[aisleIdx] : null;

            // If this aisle is controlled by a channel and a value is provided, use it directly
            if (channelName != null && channelValues != null && channelValues.containsKey(channelName)) {
                int channelValue = channelValues.get(channelName);
                // Clamp to valid range
                int min = aisleRepetitions[aisleIdx][0];
                int max = aisleRepetitions[aisleIdx][1];
                int clamped = Math.max(min, Math.min(max, channelValue));
                repetitionStack.push(clamped);
                repetitionDFS(pages, aisleRepetitions, repetitionStack, channelValues);
                repetitionStack.pop();
            } else {
                for (int i = aisleRepetitions[aisleIdx][0]; i <= aisleRepetitions[aisleIdx][1]; i++) {
                    repetitionStack.push(i);
                    repetitionDFS(pages, aisleRepetitions, repetitionStack, channelValues);
                    repetitionStack.pop();
                }
            }
        }
        return pages;
    }

    /**
     * Get the preview shape for a specific piece from the MultiPiecePattern.
     * Used by the structure projector to preview individual pieces (e.g. second_ring, third_ring).
     *
     * @param pieceIndex    1-based index into the piece list
     * @param channelValues channel values for tier selection (nullable)
     * @return the preview shape info, or null if the piece index is invalid or no MultiPiecePattern exists
     */
    @Nullable
    public MultiblockShapeInfo getMatchingShapeForPiece(int pieceIndex,
                                                        @Nullable Map<String, Integer> channelValues) {
        if (multiPiecePattern == null) return null;
        List<StructurePiece> pieces = multiPiecePattern.getPieceList();
        if (pieceIndex < 1 || pieceIndex > pieces.size()) return null;

        StructurePiece piece = pieces.get(pieceIndex - 1);
        BlockPatternTemplate pieceTemplate = piece.getTemplate();
        int[][] aisleRepetitions = pieceTemplate.getAisleRepetitions();
        int[] repetition = new int[aisleRepetitions.length];
        for (int i = 0; i < aisleRepetitions.length; i++) {
            repetition[i] = aisleRepetitions[i][0];
        }
        BlockInfo[][][] preview = piece.getState().getPreview(repetition, channelValues);
        return new MultiblockShapeInfo(preview);
    }

    @SideOnly(Side.CLIENT)
    public String[] getDescription() {
        String key = String.format("%s.multiblock.%s.description", metaTileEntityId.getNamespace(),
                metaTileEntityId.getPath());
        return I18n.hasKey(key) ? new String[] { I18n.format(key) } : new String[0];
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
        Map<BlockPos, BlockInfo> blocks = state.getAllStructureBlocks(
                world, getPos(), getFrontFacing().getOpposite(), getUpwardsFacing(), isFlipped());

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
