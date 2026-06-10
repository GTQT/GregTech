package gregtech.common.metatileentities.multi.electric.godforge;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IGodforgeModule;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.metatileentity.multiblock.MultiblockWorldData;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.OffsetMode;
import gregtech.api.pattern.PieceRuntime;
import gregtech.api.pattern.PieceRuntimes;
import gregtech.api.pattern.PatternError;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.SoftTemplate;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.StructureActivationContext;
import gregtech.api.pattern.TemplatePool;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.util.GTLog;
import gregtech.api.util.RelativeDirection;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.godforge.GodforgeRenderTileEntity;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockGodforgeCasing;
import gregtech.common.blocks.BlockGodforgeGlass;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.common.metatileentities.multi.electric.godforge.data.Fuels;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEBaseModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEExoticModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEMoltenModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEPlasmaModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTESmeltingModule;
import gregtech.common.metatileentities.multi.electric.godforge.upgrade.ForgeOfGodsUpgrade;
import gregtech.common.metatileentities.multi.electric.godforge.util.ForgeOfGodsData;
import gregtech.common.metatileentities.multi.electric.godforge.util.GodforgeMath;
import gregtech.common.mui.multiblock.godforge.sync.Panels;
import gregtech.common.mui.multiblock.godforge.sync.SyncHypervisor;
import gregtech.common.mui.multiblock.godforge.sync.SyncValues;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static gregtech.api.util.RelativeDirection.*;

/**
 * Forge of the Gods — the largest multiblock structure in the mod.
 * <p>
 * Structure overview:
 * <ul>
 *   <li>beam_shaft (60 layers) — contains the controller, hatches, modules</li>
 *   <li>first_ring (127 layers) — always required (replaced with air when renderer active)</li>
 *   <li>second_ring (111 layers) — conditional, unlocked by CD upgrade</li>
 *   <li>third_ring (94 layers) — conditional, unlocked by END upgrade</li>
 * </ul>
 * <p>
 * Architecture:
 * <ul>
 *   <li>Initial formation uses a standard BlockPattern (beam_shaft + first_ring merged).</li>
 *   <li>After formation, a MultiPiecePattern will provide event-driven partial re-validation
 *       (pending multiblock structure system refactoring — see docs/multiblock-structure-refactoring-plan.md).</li>
 * </ul>
 */
public class MetaTileEntityForgeOfGods extends MultiblockWithDisplayBase {

    // Core tick interval: every 100 ticks (5 seconds) just like GT5
    private static final int TICK_INTERVAL = 100;
    private static final int RING_REPLACEMENT_BLOCK_BUDGET = 1024;

    private final ForgeOfGodsData data = new ForgeOfGodsData();
    private SyncHypervisor syncHypervisor;
    private final List<MTEBaseModule> moduleHatches = new ArrayList<>();
    // Start at TICK_INTERVAL-1 so the first updateFormedValid tick immediately
    // runs the full logic (milestone recalculation, module connections, etc.)
    private long ticker = TICK_INTERVAL - 1;
    private int lastKnownRingAmount = 1;
    private int lastKnownClearedRingAmount = 0;
    private long lastStructureFailureLogTime = -1;
    private long lastModuleConnectionLogTime = -1;
    private boolean patternBuiltForRenderActive = false;
    private int patternBuiltForClearedRingAmount = 0;
    private boolean pendingStructureRefresh = false;

    /**
     * Dirty flag for ring block replacement. Set when ring state changes
     * (e.g., renderer created/destroyed, ring unlocked/respec). Cleared after
     * replaceRenderedRings() is executed. This avoids scanning ~1M block positions
     * every 100 ticks when nothing has changed.
     */
    private boolean ringsDirty = false;
    private RingReplacementTask ringReplacementTask;

    public MetaTileEntityForgeOfGods(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityForgeOfGods(metaTileEntityId);
    }

    // ==================== Structure Pattern (Initial Formation + JEI) ====================

    @NotNull
    @Override
    protected BlockPattern createStructurePattern() {
        // The main BlockPattern handles initial structure formation and JEI preview.
        // It contains beam_shaft + first_ring merged into a single pattern.
        // When the star renderer owns a ring, that physical ring is intentionally replaced with air.
        String[][] beamShaft = ForgeOfGodsStructureString.BEAM_SHAFT;
        String[][] firstRing = data.isRenderActive() && data.isRingCleared(1) ?
                ForgeOfGodsStructureString.FIRST_RING_AIR :
                ForgeOfGodsStructureString.FIRST_RING;
        patternBuiltForRenderActive = data.isRenderActive();
        patternBuiltForClearedRingAmount = data.getClearedRingAmount();

        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : beamShaft) {
            builder.aisle(layer);
        }
        for (String[] layer : firstRing) {
            builder.aisle(layer);
        }

        builder.where('S', selfPredicate());
        applySharedPredicates(builder, false);
        return builder.build();
    }

    // ==================== Multi-Piece Pattern (P3: Sharded Structure Check) ====================

    // Piece offsets in structure-relative coordinates (right, up, back from controller)
    // Derived from GT5 structurelib checkPiece offsets:
    //   beam_shaft: controller is at template [63, 14, 1] → offset (0, 0, 0)
    //   first_ring: controller maps to template [63, 14, 0] → 59 aisles behind controller
    //   second_ring: controller maps to template [55, 11, 0] → 67 aisles behind controller
    //   third_ring: controller maps to template [47, 13, 0] → 76 aisles behind controller
    private static final Vec3i BEAM_SHAFT_OFFSET = Vec3i.NULL_VECTOR;
    private static final Vec3i FIRST_RING_OFFSET = new Vec3i(0, 0, 59);
    private static final Vec3i SECOND_RING_OFFSET = new Vec3i(0, 0, 67);
    private static final Vec3i THIRD_RING_OFFSET = new Vec3i(0, 0, 76);
    private static final RelativeDirection[] GODFORGE_STRUCTURE_DIRECTIONS = { RIGHT, UP, FRONT };

    // External center offsets [x, y, z, minZ, maxZ] for sub-piece templates without selfPredicate
    private static final int[] FIRST_RING_CENTER = { 63, 14, 0, 0, 0 };
    private static final int[] SECOND_RING_CENTER = { 55, 11, 0, 0, 0 };
    private static final int[] THIRD_RING_CENTER = { 47, 13, 0, 0, 0 };

    // Static template cache using SoftTemplate (thread-safe, reclaimable under memory pressure)
    private static final SoftTemplate BEAM_SHAFT_TEMPLATE = TemplatePool.getInstance().register(
            "gregtech:forge_of_gods/beam_shaft", MetaTileEntityForgeOfGods::buildBeamShaftTemplate);
    private static final SoftTemplate FIRST_RING_TEMPLATE = TemplatePool.getInstance().register(
            "gregtech:forge_of_gods/first_ring", MetaTileEntityForgeOfGods::buildFirstRingTemplate);
    private static final SoftTemplate FIRST_RING_AIR_TEMPLATE = TemplatePool.getInstance().register(
            "gregtech:forge_of_gods/first_ring_air", MetaTileEntityForgeOfGods::buildFirstRingAirTemplate);
    private static final SoftTemplate SECOND_RING_TEMPLATE = TemplatePool.getInstance().register(
            "gregtech:forge_of_gods/second_ring", MetaTileEntityForgeOfGods::buildSecondRingTemplate);
    private static final SoftTemplate SECOND_RING_AIR_TEMPLATE = TemplatePool.getInstance().register(
            "gregtech:forge_of_gods/second_ring_air", MetaTileEntityForgeOfGods::buildSecondRingAirTemplate);
    private static final SoftTemplate THIRD_RING_TEMPLATE = TemplatePool.getInstance().register(
            "gregtech:forge_of_gods/third_ring", MetaTileEntityForgeOfGods::buildThirdRingTemplate);
    private static final SoftTemplate THIRD_RING_AIR_TEMPLATE = TemplatePool.getInstance().register(
            "gregtech:forge_of_gods/third_ring_air", MetaTileEntityForgeOfGods::buildThirdRingAirTemplate);

    @Nullable
    @Override
    protected MultiPiecePattern createMultiPiecePattern() {
        return MultiPiecePattern.builder()
                .piece("beam_shaft", BEAM_SHAFT_TEMPLATE.get(), BEAM_SHAFT_OFFSET, OffsetMode.RELATIVE)
                .piece("first_ring", getRingTemplate(1, FIRST_RING_TEMPLATE, FIRST_RING_AIR_TEMPLATE),
                        FIRST_RING_OFFSET, OffsetMode.RELATIVE)
                .conditionalPieceContextual("second_ring",
                        getRingTemplate(2, SECOND_RING_TEMPLATE, SECOND_RING_AIR_TEMPLATE),
                        SECOND_RING_OFFSET,
                        OffsetMode.RELATIVE,
                        (StructureActivationContext<MetaTileEntityForgeOfGods> context) ->
                                context.getController() != null
                                && context.getController().data.getRingAmount() >= 2)
                .conditionalPieceContextual("third_ring",
                        getRingTemplate(3, THIRD_RING_TEMPLATE, THIRD_RING_AIR_TEMPLATE),
                        THIRD_RING_OFFSET,
                        OffsetMode.RELATIVE,
                        (StructureActivationContext<MetaTileEntityForgeOfGods> context) ->
                                context.getController() != null
                                && context.getController().data.getRingAmount() >= 3)
                .build();
    }

    private BlockPatternTemplate getRingTemplate(int ringIndex, SoftTemplate normalTemplate, SoftTemplate airTemplate) {
        return data.isRenderActive() && data.isRingCleared(ringIndex) ? airTemplate.get() : normalTemplate.get();
    }

    private static BlockPatternTemplate buildBeamShaftTemplate() {
        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : ForgeOfGodsStructureString.BEAM_SHAFT) {
            builder.aisle(layer);
        }
        applyAllPredicates(builder, true, true);
        return builder.buildTemplate();
    }

    private static BlockPatternTemplate buildFirstRingTemplate() {
        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : ForgeOfGodsStructureString.FIRST_RING) {
            builder.aisle(layer);
        }
        applyAllPredicates(builder, false, false);
        return builder.buildTemplate(FIRST_RING_CENTER);
    }

    private static BlockPatternTemplate buildFirstRingAirTemplate() {
        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : ForgeOfGodsStructureString.FIRST_RING_AIR) {
            builder.aisle(layer);
        }
        applyAllPredicates(builder, false, false);
        return builder.buildTemplate(FIRST_RING_CENTER);
    }

    private static BlockPatternTemplate buildSecondRingTemplate() {
        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : ForgeOfGodsStructureString.SECOND_RING) {
            builder.aisle(layer);
        }
        applyAllPredicates(builder, false, false);
        return builder.buildTemplate(SECOND_RING_CENTER);
    }

    private static BlockPatternTemplate buildSecondRingAirTemplate() {
        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : ForgeOfGodsStructureString.SECOND_RING_AIR) {
            builder.aisle(layer);
        }
        applyAllPredicates(builder, false, false);
        return builder.buildTemplate(SECOND_RING_CENTER);
    }

    private static BlockPatternTemplate buildThirdRingTemplate() {
        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : ForgeOfGodsStructureString.THIRD_RING) {
            builder.aisle(layer);
        }
        applyAllPredicates(builder, false, false);
        return builder.buildTemplate(THIRD_RING_CENTER);
    }

    private static BlockPatternTemplate buildThirdRingAirTemplate() {
        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : ForgeOfGodsStructureString.THIRD_RING_AIR) {
            builder.aisle(layer);
        }
        applyAllPredicates(builder, false, false);
        return builder.buildTemplate(THIRD_RING_CENTER);
    }

    /**
     * Apply all known character -> predicate mappings to a builder.
     * Includes all characters used across all pieces.
     *
     * @param builder          the factory block pattern builder
     * @param includeController     true to include 'S' -> selfPredicate() (only for beam_shaft)
     * @param allowEmptyModuleSlots true when validating an already formed beam shaft, so module hotswaps
     *                              do not invalidate the whole Forge of Gods while a slot is briefly empty
     */
    private static void applyAllPredicates(FactoryBlockPattern builder, boolean includeController,
                                           boolean allowEmptyModuleSlots) {
        if (includeController) {
            builder.where('S', godforgeController());
        }
        applySharedPredicates(builder, allowEmptyModuleSlots);
    }

    // ==================== Block State Helpers ====================

    private static void applySharedPredicates(FactoryBlockPattern builder, boolean allowEmptyModuleSlots) {
        builder.where('A', hatches())
                .where('B', states(getCasingState(BlockGodforgeCasing.CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING)))
                .where('C', states(getCasingState(BlockGodforgeCasing.CasingType.CELESTIAL_MATTER_GUIDANCE_CASING)))
                .where('D', states(getCasingState(BlockGodforgeCasing.CasingType.BOUNDLESS_GRAVITATIONALLY_SEVERED_STRUCTURE_CASING)))
                .where('E', states(getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING)))
                .where('F', states(getCasingState(BlockGodforgeCasing.CasingType.STELLAR_ENERGY_SIPHON_CASING)))
                .where('G', states(getCasingState(BlockGodforgeCasing.CasingType.REMOTE_GRAVITON_FLOW_MODULATOR)))
                .where('H', states(getGlassState()))
                .where('J', godforgeModuleSlot(allowEmptyModuleSlots))
                .where('I', states(getCasingState(BlockGodforgeCasing.CasingType.MEDIAL_GRAVITON_FLOW_MODULATOR)))
                .where('K', states(getCasingState(BlockGodforgeCasing.CasingType.CENTRAL_GRAVITON_FLOW_MODULATOR)))
                .where('L', air());
    }

    private static IBlockState getCasingState(BlockGodforgeCasing.CasingType type) {
        return MetaBlocks.GODFORGE_CASING.getState(type);
    }

    private static IBlockState getGlassState() {
        return MetaBlocks.GODFORGE_GLASS.getState(BlockGodforgeGlass.GlassType.SPATIALLY_TRANSCENDENT_GRAVITATIONAL_LENS);
    }

    private static TraceabilityPredicate hatches() {
        return abilities(MultiblockAbility.IMPORT_ITEMS)
                .or(abilities(MultiblockAbility.IMPORT_FLUIDS))
                .or(abilities(MultiblockAbility.EXPORT_ITEMS))
                .or(abilities(MultiblockAbility.EXPORT_FLUIDS))
                .or(states(getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING)));
    }

    private static TraceabilityPredicate godforgeModules() {
        return metaTileEntities(
                MetaTileEntities.GODFORGE_SMELTING_MODULE,
                MetaTileEntities.GODFORGE_MOLTEN_MODULE,
                MetaTileEntities.GODFORGE_PLASMA_MODULE,
                MetaTileEntities.GODFORGE_EXOTIC_MODULE);
    }

    private static TraceabilityPredicate godforgeModuleSlot(boolean allowEmptyModuleSlots) {
        TraceabilityPredicate predicate = godforgeModules()
                .or(states(getCasingState(BlockGodforgeCasing.CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING)));
        return allowEmptyModuleSlots ? predicate.or(air()) : predicate;
    }

    private static TraceabilityPredicate godforgeController() {
        return selfPredicate(MetaTileEntityForgeOfGods.class);
    }

    // ==================== Structure Lifecycle ====================

    @Override
    public void checkStructurePattern() {
        ensurePatternMatchesRenderState();
        super.checkStructurePattern();
        if (!isStructureFormed() && tryRecoverRenderedStructure()) {
            ensurePatternMatchesRenderState();
            super.checkStructurePattern();
            if (isStructureFormed()) {
                ensureRendererState();
            }
        }
        if (!isStructureFormed()) {
            logStructureFailure();
        }
    }

    private void ensurePatternMatchesRenderState() {
        if (patternBuiltForRenderActive != data.isRenderActive() ||
                patternBuiltForClearedRingAmount != data.getClearedRingAmount()) {
            reinitializeStructurePattern();
        }
    }

    private boolean tryRecoverRenderedStructure() {
        if (data.isRenderActive()) return false;
        if (getWorld() == null || getWorld().isRemote || getPos() == null) return false;
        if (data.getInternalBattery() <= 0) return false;
        if (data.getClearedRingAmount() <= 0) return false;
        if (!isBeamShaftStillFormed()) return false;
        if (!isRingTemplateFormed(FIRST_RING_AIR_TEMPLATE, FIRST_RING_OFFSET)) return false;
        if (data.getClearedRingAmount() >= 2 &&
                !isRingTemplateFormed(SECOND_RING_AIR_TEMPLATE, SECOND_RING_OFFSET)) {
            return false;
        }
        if (data.getClearedRingAmount() >= 3 &&
                !isRingTemplateFormed(THIRD_RING_AIR_TEMPLATE, THIRD_RING_OFFSET)) {
            return false;
        }

        GTLog.logger.info("[FOG] recovering persisted rendered structure at {}; clearedRings={} and battery={}",
                getPos(), data.getClearedRingAmount(), data.getInternalBattery());
        data.setRenderActive(true);
        data.setRingAmount(Math.max(data.getRingAmount(), data.getClearedRingAmount()));
        markDirty();
        return true;
    }

    @Override
    public void doStructureCheck() {
        // When the renderer is active, rings are replaced with air. The event-driven system
        // (MultiblockWorldData) monitors beam_shaft positions and will trigger a recheck if
        // any block in the registered structure changes. We delegate to super which handles
        // event-driven, async, and fallback polling modes automatically.
        super.doStructureCheck();
    }

    @Override
    protected void checkMultiPieceStructure() {
        if (multiPiecePattern == null) return;

        StructurePiece beamShaft = multiPiecePattern.getPiece("beam_shaft");
        PieceRuntimes runtimes = getPieceRuntimes();
        PieceRuntime beamShaftRuntime = (beamShaft != null) ? runtimes.get(beamShaft) : null;
        boolean beamShaftDirty = beamShaftRuntime != null
                && beamShaft.isActive()
                && beamShaftRuntime.isDirty();

        boolean allValid = multiPiecePattern.checkDirtyPieces(
                getWorld(), getPos(), getFrontFacingForStructure(),
                getUpwardsFacing(), isFlipped(), runtimes, this);

        if (!allValid && isStructureFormed()) {
            invalidateStructure();
            return;
        }

        if (beamShaftDirty && beamShaftRuntime != null && beamShaftRuntime.isValidated()) {
            boolean reassembled = reassembleStructure(beamShaftRuntime.getState().getMatchContext());
            if (reassembled && getWorld() != null && !getWorld().isRemote) {
                MultiblockWorldData.get(getWorld()).unregisterMultiblock(this);
                registerMultiPiecePattern();
            }
        }
    }

    private boolean isBeamShaftStillFormed() {
        if (getWorld() == null || getPos() == null) return false;
        return BEAM_SHAFT_TEMPLATE.get()
                .createState()
                .checkPatternFastAt(getWorld(), getPos(), getFrontFacingForStructure(),
                        getUpwardsFacing(), allowsFlip(), false) != null;
    }

    private boolean isRingTemplateFormed(SoftTemplate template, Vec3i pieceOffset) {
        BlockPos pieceOrigin = OffsetMode.RELATIVE.apply(getPos(),
                new int[] { pieceOffset.getX(), pieceOffset.getY(), pieceOffset.getZ() },
                getFrontFacingForStructure(), getUpwardsFacing(), isFlipped());
        return template.get()
                .createState()
                .checkPatternFastAt(getWorld(), pieceOrigin, getFrontFacingForStructure(),
                        getUpwardsFacing(), allowsFlip(), false) != null;
    }

    private void logStructureFailure() {
        if (getWorld() == null || getWorld().isRemote || multiblockState == null) return;

        long worldTime = getWorld().getTotalWorldTime();
        if (lastStructureFailureLogTime >= 0 && worldTime - lastStructureFailureLogTime < TICK_INTERVAL) return;
        lastStructureFailureLogTime = worldTime;

        PatternError error = multiblockState.getError();
        BlockPos renderPos = getRenderPos();
        String renderState = "null";
        if (renderPos != null) {
            renderState = getWorld().isBlockLoaded(renderPos) ?
                    String.valueOf(getWorld().getBlockState(renderPos)) :
                    "unloaded";
        }

        if (error == null) {
            GTLog.logger.warn("[FOG] structure check failed at controller={}, front={}, up={}, renderActive={}, " +
                            "rendererDisabled={}, battery={}, rings={}, renderPos={}, renderState={}, no pattern error",
                    getPos(), getFrontFacing(), getUpwardsFacing(), data.isRenderActive(), data.isRendererDisabled(),
                    data.getInternalBattery(), data.getRingAmount(), renderPos, renderState);
            return;
        }

        BlockPos errorPos = error.getPos();
        IBlockState actualState = getWorld().isBlockLoaded(errorPos) ?
                getWorld().getBlockState(errorPos) :
                null;
        TileEntity actualTile = actualState != null ? getWorld().getTileEntity(errorPos) : null;

        GTLog.logger.warn("[FOG] structure check failed at controller={}, front={}, up={}, renderActive={}, " +
                        "rendererDisabled={}, battery={}, rings={}, renderPos={}, renderState={}, errorType={}, " +
                        "errorPos={}, actualState={}, actualTile={}, candidates={}",
                getPos(), getFrontFacing(), getUpwardsFacing(), data.isRenderActive(), data.isRendererDisabled(),
                data.getInternalBattery(), data.getRingAmount(), renderPos, renderState,
                error.getClass().getSimpleName(), errorPos,
                actualState != null ? actualState : "unloaded",
                describeTileEntity(actualTile), describeCandidates(error));
    }

    private static String describeTileEntity(@Nullable TileEntity tileEntity) {
        if (tileEntity == null) return "null";
        String description = tileEntity.getClass().getName();
        if (tileEntity instanceof IGregTechTileEntity) {
            MetaTileEntity metaTileEntity = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
            description += ", mte=" + (metaTileEntity == null ? "null" :
                    metaTileEntity.metaTileEntityId + "/" + metaTileEntity.getClass().getName());
        }
        return description;
    }

    private static String describeCandidates(PatternError error) {
        StringBuilder builder = new StringBuilder();
        for (List<ItemStack> group : error.getCandidates()) {
            if (builder.length() > 0) builder.append(" | ");
            builder.append('[');
            int written = 0;
            for (ItemStack stack : group) {
                if (stack.isEmpty()) continue;
                if (written > 0) builder.append(", ");
                builder.append(stack.getItem().getRegistryName()).append(':').append(stack.getMetadata());
                written++;
                if (written >= 3) {
                    builder.append(", ...");
                    break;
                }
            }
            builder.append(']');
        }
        return builder.length() == 0 ? "[]" : builder.toString();
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        updateRingAmount();
        discoverModules();

        // Ensure milestone percentages are up-to-date when structure forms,
        // so the GUI shows correct progress immediately.
        determineMilestoneProgress();

        // Restore renderer if battery was active before structure broke
        if (data.getInternalBattery() != 0 && !data.isRenderActive() && !data.isRendererDisabled()) {
            createRenderer();
        }
    }

    @Override
    public void invalidateStructure() {
        disconnectAllModules();
        moduleHatches.clear();
        destroyRenderer();
        cleanupPossibleRendererBlocks();
        super.invalidateStructure();
    }

    @Override
    public void onRemoval() {
        if (getWorld() != null && !getWorld().isRemote) {
            pendingStructureRefresh = false;
            destroyRenderer();
            completeRingReplacement();
            cleanupPossibleRendererBlocks();
            disconnectAllModules();
            moduleHatches.clear();
        }
        if (syncHypervisor != null) {
            syncHypervisor.clearMultiblock();
        }
        super.onRemoval();
    }

    /**
     * Scans all multiblock parts to discover connected godforge modules.
     * Modules are sub-multiblocks attached to the beam_shaft at 'J' positions.
     */
    private void discoverModules() {
        moduleHatches.clear();

        for (IGodforgeModule module : getAbilities(MultiblockAbility.GODFORGE_MODULE)) {
            if (module instanceof MTEBaseModule && !moduleHatches.contains(module)) {
                moduleHatches.add((MTEBaseModule) module);
            }
        }

        for (IMultiblockPart part : getMultiblockParts()) {
            if (part instanceof MTEBaseModule && !moduleHatches.contains(part)) {
                moduleHatches.add((MTEBaseModule) part);
            }
        }

        logModuleDiscovery();
    }

    /**
     * Disconnects all currently connected modules.
     */
    private void disconnectAllModules() {
        for (MTEBaseModule module : moduleHatches) {
            module.disconnect();
        }
    }

    private void logModuleDiscovery() {
        if (getWorld() == null || getWorld().isRemote) return;

        StringBuilder modules = new StringBuilder();
        for (MTEBaseModule module : moduleHatches) {
            if (modules.length() > 0) modules.append(" | ");
            modules.append(describeModule(module));
        }

        GTLog.logger.info("[FOG] discoverModules: controller={}, parts={}, abilityModules={}, moduleHatches={}, " +
                        "battery={}, rings={}, modules={}",
                getPos(), getMultiblockParts().size(), getAbilities(MultiblockAbility.GODFORGE_MODULE).size(),
                moduleHatches.size(), data.getInternalBattery(), data.getRingAmount(),
                modules.length() == 0 ? "[]" : modules);
    }

    /**
     * Determines the ring amount from structure template checks.
     * Only called during formStructure() and GUI-triggered refresh, NOT during tick loops.
     * When the renderer is active (rings replaced with air), uses the persisted clearedRingAmount
     * instead of doing expensive template checks against physical blocks.
     */
    private void updateRingAmount() {
        int rings;
        if (data.isRenderActive()) {
            // Rings are air — trust the persisted cleared ring amount
            rings = Math.max(1, data.getClearedRingAmount());
        } else {
            // Rings are physical blocks — check templates
            rings = Math.max(1, data.getClearedRingAmount());
            if (getWorld() != null && !getWorld().isRemote && getPos() != null) {
                if (data.isUpgradeActive(ForgeOfGodsUpgrade.CD) &&
                        isRingTemplateFormed(SECOND_RING_TEMPLATE, SECOND_RING_OFFSET)) {
                    rings = Math.max(rings, 2);
                }
                if (rings >= 2 && data.isUpgradeActive(ForgeOfGodsUpgrade.END) &&
                        isRingTemplateFormed(THIRD_RING_TEMPLATE, THIRD_RING_OFFSET)) {
                    rings = Math.max(rings, 3);
                }
            }
        }
        if (data.getRingAmount() != rings) {
            data.setRingAmount(rings);
            markDirty();
        }
    }

    // ==================== Rendering ====================

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.GODFORGE_INNER_CASING;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.GODFORGE_CONTROLLER_OVERLAY;
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(), true, true);
    }

    // ==================== Tick Logic ====================

    @Override
    public void update() {
        super.update();
        if (getWorld() == null || getWorld().isRemote) return;

        if (isStructureFormed() && ringsDirty) {
            ringsDirty = false;
            replaceRenderedRings(false);
        }
        processRingReplacement();
        if (isStructureFormed()) return;

        if (data.isRenderActive()) {
            destroyRenderer();
            return;
        }
        if (getOffsetTimer() % TICK_INTERVAL == 0) {
            cleanupPossibleRendererBlocks();
        }
    }

    @Override
    protected void updateFormedValid() {
        if (getWorld().isRemote) return;

        ticker++;

        if (ticker % TICK_INTERVAL != 0) return;

        // Ring amount is determined at formStructure() time, not in the tick loop.
        // During runtime, ring count only changes through GUI operations (upgrade/respec)
        // which call refreshStructureFromGui() → formStructure() → updateRingAmount().
        int maxModuleCount = 8 + (data.getRingAmount() - 1) * 4;

        // === Fuel absorption and battery startup ===
        absorbFuelOrShards();

        // === Fuel consumption (drain fluid + maintain battery) ===
        if (data.getInternalBattery() != 0) {
            drainFuel();
        }

        // === Renderer integrity check (lightweight: single getBlockState call) ===
        ensureRendererState();

        // === Ring block replacement (only when state has actually changed) ===
        if (ringsDirty) {
            ringsDirty = false;
            replaceRenderedRings(false);
        }

        // === Module parameter calculation and connection management ===
        logModuleConnectionSummary(maxModuleCount);
        if (!moduleHatches.isEmpty() && data.getInternalBattery() > 0
                && moduleHatches.size() <= maxModuleCount) {
            for (MTEBaseModule module : moduleHatches) {
                if (!module.isStructureFormed()) {
                    module.checkStructurePattern();
                }
                boolean allowConnection = GodforgeMath.allowModuleConnection(module, data);
                boolean antiCheeseDisconnect = false;
                boolean wasConnected = module.isConnected();
                if (allowConnection) {
                    module.connect();
                    GodforgeMath.calculateMaxHeatForModules(module, data);
                    GodforgeMath.calculateSpeedBonusForModules(module, data);
                    GodforgeMath.calculateMaxParallelForModules(module, data);
                    GodforgeMath.calculateEnergyDiscountForModules(module, data);
                    GodforgeMath.setMiscModuleParameters(module, data);
                    GodforgeMath.queryMilestoneStats(module, data);
                    if (!data.isUpgradeActive(ForgeOfGodsUpgrade.TBF)) {
                        GodforgeMath.calculateProcessingVoltageForModules(module, data);
                    }
                    if (GodforgeMath.factorChangeDuringRecipeAntiCheese(module)) {
                        antiCheeseDisconnect = true;
                        module.disconnect();
                    }
                } else {
                    module.disconnect();
                }
                logModuleConnectionDecision(module, wasConnected, allowConnection, antiCheeseDisconnect);
            }
        } else if (moduleHatches.size() > maxModuleCount) {
            disconnectAllModules();
        }

        // === Ring unlock/respec detection → update renderer and structure ===
        // === Milestone calculations ===
        determineCompositionMilestoneLevel();
        determineMilestoneProgress();
        pushMilestoneProgress();
        checkInversionStatus();
        determineGravitonShardAmount();

        // === Graviton shard ejection (if END upgrade active and ejection enabled) ===
        if (data.isUpgradeActive(ForgeOfGodsUpgrade.END) && data.isGravitonShardEjection()) {
            ejectGravitonShards();
        }

        if (data.getRingAmount() != lastKnownRingAmount ||
                data.getClearedRingAmount() != lastKnownClearedRingAmount) {
            lastKnownRingAmount = data.getRingAmount();
            lastKnownClearedRingAmount = data.getClearedRingAmount();
            if (data.isRenderActive() && !data.isRendererDisabled()) {
                updateRenderer();
            }
            reinitializeStructurePattern();
        }
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    // ==================== Fuel System ====================

    /**
     * Absorbs stellar fuel from input bus for battery startup,
     * or graviton shards if battery is already running and END upgrade is active.
     */
    private void absorbFuelOrShards() {
        List<IItemHandlerModifiable> itemInputs = getAbilities(MultiblockAbility.IMPORT_ITEMS);
        if (itemInputs.isEmpty()) return;

        if (data.getInternalBattery() == 0 || data.isUpgradeActive(ForgeOfGodsUpgrade.END)) {
            ItemStack itemToAbsorb;
            boolean absorbingShards = data.isUpgradeActive(ForgeOfGodsUpgrade.END) && data.getInternalBattery() != 0;

            if (absorbingShards) {
                itemToAbsorb = OrePrefix.gem.getItemForm(Materials.GravitonShard, 1);
            } else {
                itemToAbsorb = getStellarFuelItem();
            }

            if (itemToAbsorb == null) return;

            for (IItemHandlerModifiable handler : itemInputs) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    ItemStack itemStack = handler.getStackInSlot(i);
                    if (itemStack.isEmpty()) continue;
                    if (!itemStack.isItemEqual(itemToAbsorb)) continue;

                    int stackSize = Math.min(itemStack.getCount(),
                            Integer.MAX_VALUE - data.getStellarFuelAmount());
                    handler.extractItem(i, stackSize, false);

                    if (!absorbingShards) {
                        data.setStellarFuelAmount(data.getStellarFuelAmount() + stackSize);
                    } else {
                        data.setGravitonShardsAvailable(data.getGravitonShardsAvailable() + stackSize);
                        data.setGravitonShardsSpent(data.getGravitonShardsSpent() - stackSize);
                    }
                }
            }

            // Attempt battery startup
            if (data.getInternalBattery() == 0) {
                data.setNeededStartupFuel(GodforgeMath.calculateStartupFuelConsumption(data));
                if (data.getStellarFuelAmount() >= data.getNeededStartupFuel()) {
                    data.setStellarFuelAmount(data.getStellarFuelAmount() - data.getNeededStartupFuel());
                    increaseBattery(data.getNeededStartupFuel());
                    if (!data.isRendererDisabled()) {
                        createRenderer();
                    }
                }
            }
        }
    }

    /**
     * Returns the item used as stellar fuel for battery startup.
     * In GT5 this was Avaritia's Infinity Catalyst; here it's a dedicated MetaItem.
     */
    private ItemStack getStellarFuelItem() {
        return MetaItems.STELLAR_FUEL.getStackForm();
    }

    /**
     * Drains fuel fluid from input hatches and manages battery charge.
     * Port of GT5 MTEForgeOfGods#drainFuel().
     */
    private void drainFuel() {
        int fuelConsumptionFactor = data.getFuelConsumptionFactor();

        // Clamp fuel factor based on fuel type and upgrades
        if (data.getSelectedFuelType() == 0) {
            if (data.isUpgradeActive(ForgeOfGodsUpgrade.STEM)) {
                if (fuelConsumptionFactor > ForgeOfGodsData.MAX_RESIDUE_FACTOR_DISCOUNTED) {
                    data.setFuelConsumptionFactor(ForgeOfGodsData.MAX_RESIDUE_FACTOR_DISCOUNTED);
                }
            } else if (fuelConsumptionFactor > ForgeOfGodsData.MAX_RESIDUE_FACTOR) {
                data.setFuelConsumptionFactor(ForgeOfGodsData.MAX_RESIDUE_FACTOR);
            }
        } else if (data.getSelectedFuelType() == 1) {
            if (data.isUpgradeActive(ForgeOfGodsUpgrade.STEM)) {
                if (fuelConsumptionFactor > ForgeOfGodsData.MAX_STELLAR_PLASMA_FACTOR_DISCOUNTED) {
                    data.setFuelConsumptionFactor(ForgeOfGodsData.MAX_STELLAR_PLASMA_FACTOR_DISCOUNTED);
                }
            } else if (fuelConsumptionFactor > ForgeOfGodsData.MAX_STELLAR_PLASMA_FACTOR) {
                data.setFuelConsumptionFactor(ForgeOfGodsData.MAX_STELLAR_PLASMA_FACTOR);
            }
        }

        int updatedFuelConsumptionFactor = data.getFuelConsumptionFactor();
        data.setFuelConsumption(
                (long) Math.max(GodforgeMath.calculateFuelConsumption(data)
                        * 5 * (data.isBatteryCharging() ? 2 : 1), 1));

        if (data.getFuelConsumption() >= Integer.MAX_VALUE) {
            reduceBattery(updatedFuelConsumptionFactor);
            return;
        }

        Fuels selectedFuel = Fuels.getFromData(data);
        FluidStack fuelToDrain = selectedFuel.getFluid((int) data.getFuelConsumption());
        if (fuelToDrain == null) {
            reduceBattery(updatedFuelConsumptionFactor);
            return;
        }

        List<IFluidTank> fluidInputs = getAbilities(MultiblockAbility.IMPORT_FLUIDS);
        int remaining = fuelToDrain.amount;

        for (IFluidTank tank : fluidInputs) {
            if (remaining <= 0) break;
            if (!(tank instanceof IFluidHandler)) continue;

            FluidStack drained = ((IFluidHandler) tank).drain(
                    new FluidStack(fuelToDrain, remaining), true);
            if (drained != null) {
                remaining -= drained.amount;
            }
        }

        if (remaining <= 0) {
            // Successfully drained all required fuel
            data.setTotalFuelConsumed(data.getTotalFuelConsumed() + updatedFuelConsumptionFactor);
            if (data.isBatteryCharging()) {
                increaseBattery(updatedFuelConsumptionFactor);
            }
        } else {
            // Not enough fuel — reduce battery
            reduceBattery(updatedFuelConsumptionFactor);
        }
    }

    // ==================== Battery Management ====================

    private void increaseBattery(int amount) {
        long newCharge = (long) data.getInternalBattery() + amount;
        if (newCharge <= data.getMaxBatteryCharge()) {
            data.setInternalBattery((int) newCharge);
        } else {
            data.setInternalBattery(data.getMaxBatteryCharge());
            data.setBatteryCharging(false);
        }
    }

    private void reduceBattery(int amount) {
        if (data.getInternalBattery() - amount <= 0) {
            data.setInternalBattery(0);
            disconnectAllModules();
            destroyRenderer();
        } else {
            data.setInternalBattery(data.getInternalBattery() - amount);
            data.setTotalFuelConsumed(data.getTotalFuelConsumed() + amount);
        }
    }

    /**
     * Lightweight renderer integrity check. Only verifies that the render block exists
     * at the expected position (single getBlockState call). If missing, recreates it.
     * Ring block replacement is handled separately by the ringsDirty flag.
     */
    private void ensureRendererState() {
        if (!isStructureFormed()) {
            if (data.isRenderActive()) {
                destroyRenderer();
            }
            cleanupPossibleRendererBlocks();
            return;
        }

        if (data.getInternalBattery() <= 0 || data.isRendererDisabled()) {
            if (data.isRenderActive()) {
                destroyRenderer();
            }
            return;
        }

        if (data.isRenderActive()) {
            BlockPos renderPos = getRenderPos();
            if (renderPos == null || getWorld().getBlockState(renderPos).getBlock() != MetaBlocks.GODFORGE_RENDER) {
                GTLog.logger.info("[FOG] ensureRendererState: render block missing, recreating. isRenderActive={}", data.isRenderActive());
                data.setRenderActive(false);
                createRenderer();
            }
            return;
        }

        GTLog.logger.info("[FOG] ensureRendererState: renderer not active, battery={}, rendererDisabled={}",
                data.getInternalBattery(), data.isRendererDisabled());
        createRenderer();
    }

    private void logModuleConnectionSummary(int maxModuleCount) {
        if (getWorld() == null || getWorld().isRemote) return;
        if (!GTLog.logger.isDebugEnabled()) return;

        long worldTime = getWorld().getTotalWorldTime();
        if (lastModuleConnectionLogTime >= 0 && worldTime - lastModuleConnectionLogTime < TICK_INTERVAL) return;
        lastModuleConnectionLogTime = worldTime;

        GTLog.logger.debug("[FOG] module connection tick: controller={}, formed={}, battery={}, modules={}, " +
                        "maxModules={}, ringAmount={}, fuelType={}, fuelFactor={}, upgrades={}, shouldProcess={}",
                getPos(), isStructureFormed(), data.getInternalBattery(), moduleHatches.size(), maxModuleCount,
                data.getRingAmount(), data.getSelectedFuelType(), data.getFuelConsumptionFactor(),
                data.getUpgrades().getTotalActiveUpgrades(),
                !moduleHatches.isEmpty() && data.getInternalBattery() > 0 && moduleHatches.size() <= maxModuleCount);
    }

    private void logModuleConnectionDecision(MTEBaseModule module, boolean wasConnected, boolean allowConnection,
                                             boolean antiCheeseDisconnect) {
        if (getWorld() == null || getWorld().isRemote) return;
        if (!GTLog.logger.isDebugEnabled()) return;

        GTLog.logger.debug("[FOG] module connection decision: controller={}, module={}, wasConnected={}, " +
                        "allowConnection={}, antiCheeseDisconnect={}, nowConnected={}, heat={}, ocHeat={}, " +
                        "maxParallel={}, voltage={}, currentRecipeHeat={}",
                getPos(), describeModule(module), wasConnected, allowConnection, antiCheeseDisconnect,
                module.isConnected(), module.getHeat(), module.getHeatForOC(), module.getCalculatedMaxParallel(),
                module.getProcessingVoltage(), module.getCurrentRecipeHeat());
    }

    private static String describeModule(MTEBaseModule module) {
        if (module == null) return "null";
        return module.metaTileEntityId + "@" + module.getPos() +
                "{formed=" + module.isStructureFormed() +
                ", connected=" + module.isConnected() +
                ", type=" + module.getClass().getSimpleName() + "}";
    }

    // ==================== Milestone Tracking ====================

    /**
     * Determines the composition milestone level based on active module types.
     * Port of GT5 MTEForgeOfGods#determineCompositionMilestoneLevel().
     */
    private void determineCompositionMilestoneLevel() {
        int[] uniqueModuleCount = new int[5];
        int smelting = 0;
        int molten = 0;
        int plasma = 0;
        int exotic = 0;
        int exoticMagmatter = 0;

        for (MTEBaseModule module : moduleHatches) {
            if (module instanceof MTESmeltingModule) {
                uniqueModuleCount[0] = 1;
                smelting++;
            } else if (module instanceof MTEMoltenModule) {
                uniqueModuleCount[1] = 1;
                molten++;
            } else if (module instanceof MTEPlasmaModule) {
                uniqueModuleCount[2] = 1;
                plasma++;
            } else if (module instanceof MTEExoticModule) {
                if (!((MTEExoticModule) module).isMagmatterModeOn()) {
                    uniqueModuleCount[3] = 1;
                    exotic++;
                } else {
                    uniqueModuleCount[4] = 1;
                    exoticMagmatter++;
                }
            }
        }

        data.setTotalExtensionsBuilt(
                Arrays.stream(uniqueModuleCount).sum() + data.getRingAmount() - 1);

        if (data.isInversion()) {
            float toAdd = (smelting - 1
                    + (molten - 1) * 2
                    + (plasma - 1) * 3
                    + (exotic - 1) * 4
                    + (exoticMagmatter - 1) * 5) / 5f;
            data.setTotalExtensionsBuilt(data.getTotalExtensionsBuilt() + toAdd);
        }

        data.setMilestoneProgress(3, (int) Math.floor(data.getTotalExtensionsBuilt()));
    }

    /**
     * Calculates all four milestone percentages.
     */
    private void determineMilestoneProgress() {
        GodforgeMath.determineChargeMilestone(data);
        GodforgeMath.determineConversionMilestone(data);
        GodforgeMath.determineCatalystMilestone(data);
        GodforgeMath.determineCompositionMilestone(data);
   }

    private void pushMilestoneProgress() {
        if (syncHypervisor == null) return;
        // DEBUG: Log sync hypervisor state
        GTLog.logger.info("[FOG Milestone DEBUG] pushMilestoneProgress - syncHypervisor present, " +
            "MILESTONE PSM={}", syncHypervisor.getSyncManager(Panels.MILESTONE));
        SyncValues.MILESTONE_CHARGE_PROGRESS.notifyUpdateFrom(Panels.MILESTONE, syncHypervisor);
        SyncValues.MILESTONE_CHARGE_PROGRESS_INVERTED.notifyUpdateFrom(Panels.MILESTONE, syncHypervisor);
        SyncValues.MILESTONE_CONVERSION_PROGRESS.notifyUpdateFrom(Panels.MILESTONE, syncHypervisor);
        SyncValues.MILESTONE_CONVERSION_PROGRESS_INVERTED.notifyUpdateFrom(Panels.MILESTONE, syncHypervisor);
        SyncValues.MILESTONE_CATALYST_PROGRESS.notifyUpdateFrom(Panels.MILESTONE, syncHypervisor);
        SyncValues.MILESTONE_CATALYST_PROGRESS_INVERTED.notifyUpdateFrom(Panels.MILESTONE, syncHypervisor);
        SyncValues.MILESTONE_COMPOSITION_PROGRESS.notifyUpdateFrom(Panels.MILESTONE, syncHypervisor);
        SyncValues.MILESTONE_COMPOSITION_PROGRESS_INVERTED.notifyUpdateFrom(Panels.MILESTONE, syncHypervisor);
    }

    /**
     * Checks if all milestones have reached tier 7 to enable inversion.
     */
    private void checkInversionStatus() {
        int inversionChecker = 0;
        for (int progress : data.getAllMilestoneProgress()) {
            if (progress < 7) {
                break;
            }
            inversionChecker++;
        }
        data.setInversion(inversionChecker == 4);
    }

    /**
     * Calculates the total graviton shards available based on milestone progress.
     */
    private void determineGravitonShardAmount() {
        int sum = 0;
        for (int progress : data.getAllMilestoneProgress()) {
            if (!data.isInversion()) {
                progress = Math.min(progress, 7);
            }
            sum += progress * (progress + 1) / 2;
        }
        data.setGravitonShardsAvailable(sum - data.getGravitonShardsSpent());
    }

    /**
     * Ejects graviton shards into the output bus.
     */
    private void ejectGravitonShards() {
        List<IItemHandlerModifiable> itemOutputs = getAbilities(MultiblockAbility.EXPORT_ITEMS);
        if (itemOutputs.isEmpty()) return;

        int shardsToEject = data.getGravitonShardsAvailable();
        if (shardsToEject <= 0) return;

        ItemStack shardStack = OrePrefix.gem.getItemForm(Materials.GravitonShard, shardsToEject);
        if (shardStack.isEmpty()) return;

        int ejected = 0;
        for (IItemHandlerModifiable handler : itemOutputs) {
            for (int i = 0; i < handler.getSlots(); i++) {
                if (shardStack.isEmpty()) break;
                ItemStack remainder = handler.insertItem(i, shardStack, false);
                int inserted = shardStack.getCount() - (remainder.isEmpty() ? 0 : remainder.getCount());
                ejected += inserted;
                if (remainder.isEmpty()) {
                    shardStack = ItemStack.EMPTY;
                    break;
                }
                shardStack = remainder;
            }
            if (shardStack.isEmpty()) break;
        }

        if (ejected > 0) {
            data.setGravitonShardsAvailable(data.getGravitonShardsAvailable() - ejected);
            data.setGravitonShardsSpent(data.getGravitonShardsSpent() + ejected);
        }
    }

    // ==================== Facing ====================

    @Override
    public boolean allowsExtendedFacing() {
        return true;
    }

    @Override
    protected boolean allowsAsyncStructureCheck() {
        return false;
    }

    // ==================== Structure Channels ====================

    @Override
    @NotNull
    public List<StructureChannel> getSupportedChannels() {
        List<StructureChannel> channels = new ArrayList<>(super.getSupportedChannels());
        channels.add(GTStructureChannels.STRUCTURE_PIECE);
        return channels;
    }

    @Override
    @NotNull
    public int[] getChannelRange(@NotNull StructureChannel channel) {
        if (channel == GTStructureChannels.STRUCTURE_PIECE) {
            // 0=main only, 1=beam_shaft, 2=first_ring, 3=second_ring, 4=third_ring
            int pieceCount = multiPiecePattern != null ? multiPiecePattern.getPieceCount() : 0;
            return new int[] { 0, pieceCount };
        }
        return super.getChannelRange(channel);
    }

    /**
     * Checks whether rotation/flipping is currently locked.
     * Rotation is disabled when the structure is formed and the star renderer is active,
     * since rotating would desync the renderer position from the structure.
     */
    private boolean isRotationLocked() {
        return isStructureFormed() && data.isRenderActive();
    }

    @Override
    public void setFrontFacing(EnumFacing frontFacing) {
        if (frontFacing == null) return;
        // Block rotation while renderer is active to avoid desync
        if (isRotationLocked() && getFrontFacing() != frontFacing) return;

        if (getWorld() != null && !getWorld().isRemote && getFrontFacing() != frontFacing) {
            cleanupPossibleRendererBlocks();
        }
        super.setFrontFacing(frontFacing);
    }

    @Override
    public void setUpwardsFacing(EnumFacing upwardsFacing) {
        // Block upward rotation while renderer is active
        if (isRotationLocked()) return;
        super.setUpwardsFacing(upwardsFacing);
    }

    // ==================== Data Access ====================

    public ForgeOfGodsData getData() {
        return data;
    }

    public void setSyncHypervisor(SyncHypervisor hypervisor) {
        this.syncHypervisor = hypervisor;
    }

    public void clearSyncHypervisor(SyncHypervisor hypervisor) {
        if (this.syncHypervisor == hypervisor) {
            this.syncHypervisor = null;
        }
    }

    public List<MTEBaseModule> getModuleHatches() {
        return moduleHatches;
    }

    public void refreshStructureFromGui() {
        if (getWorld() == null || getWorld().isRemote) return;

        if (isStructureFormed()) {
            invalidateStructure();
        }
        if (ringReplacementTask != null) {
            pendingStructureRefresh = true;
            markDirty();
            return;
        }
        refreshStructureNow();
    }

    private void refreshStructureNow() {
        reinitializeStructurePattern();
        checkStructurePattern();
        markDirty();
    }

    // ==================== Renderer Management ====================

    /**
     * Offset from controller to render position along the structure's back axis.
     * In GT5, the star is at the center of the ring structure, 122 blocks behind the controller.
     */
    private static final int RENDER_OFFSET = 122;

    /**
     * Creates the render TileEntity at the structure center.
     * Places an invisible block with GodforgeRenderTileEntity at the correct position.
     */
    public void createRenderer() {
        if (getWorld() == null || getWorld().isRemote) return;
        if (!isStructureFormed()) {
            cleanupPossibleRendererBlocks();
            return;
        }

        BlockPos renderPos = getRenderPos();
        if (renderPos == null) {
            GTLog.logger.warn("[FOG] createRenderer: getRenderPos() returned null");
            return;
        }

        GTLog.logger.info("[FOG] createRenderer: attempting setBlockState at {}, chunk loaded: {}",
                renderPos, getWorld().isBlockLoaded(renderPos));

        IBlockState renderState = MetaBlocks.GODFORGE_RENDER.getDefaultState();
        IBlockState currentState = getWorld().getBlockState(renderPos);
        if (currentState.getBlock() != MetaBlocks.GODFORGE_RENDER &&
                !getWorld().setBlockState(renderPos, renderState, 3)) {
            getWorld().setBlockToAir(renderPos);
            if (!getWorld().setBlockState(renderPos, renderState, 3)) {
                GTLog.logger.warn("[FOG] createRenderer: setBlockState FAILED at {}", renderPos);
                data.setRenderActive(false);
                return;
            }
        }
        TileEntity te = getWorld().getTileEntity(renderPos);
        if (currentState.getBlock() == MetaBlocks.GODFORGE_RENDER && te == null) {
            getWorld().setBlockToAir(renderPos);
            if (!getWorld().setBlockState(renderPos, renderState, 3)) {
                GTLog.logger.warn("[FOG] createRenderer: failed to restore missing render TileEntity at {}", renderPos);
                data.setRenderActive(false);
                return;
            }
            te = getWorld().getTileEntity(renderPos);
        }
        if (te instanceof GodforgeRenderTileEntity) {
            GodforgeRenderTileEntity renderTE = (GodforgeRenderTileEntity) te;
            renderTE.setOwnerPos(getPos());
            renderTE.setRenderRotation(getFrontFacing());
            data.setRenderActive(true);
            ringsDirty = true;
            updateRenderer();
            GTLog.logger.info("[FOG] createRenderer: SUCCESS at {}", renderPos);
        } else {
            GTLog.logger.warn("[FOG] createRenderer: TileEntity mismatch at {}, got: {}",
                    renderPos, te != null ? te.getClass().getName() : "null");
            data.setRenderActive(false);
        }
    }

    /**
     * Removes the render block and marks renderer as inactive.
     */
    public void destroyRenderer() {
        if (getWorld() == null || getWorld().isRemote) return;

        BlockPos renderPos = getRenderPos();
        if (renderPos == null) return;

        destroyRendererAt(renderPos);
        replaceRenderedRings(true);

        data.setRenderActive(false);
        markDirty();
    }

    private void replaceRenderedRings(boolean restoreBlocks) {
        if (getWorld() == null || getWorld().isRemote || getPos() == null) return;

        int ringAmount = restoreBlocks ? getRestorableRingAmount() : getReplaceableRingAmount();
        if (ringAmount <= 0) return;

        ringReplacementTask = new RingReplacementTask(restoreBlocks, ringAmount);
    }

    private void processRingReplacement() {
        if (ringReplacementTask == null) return;
        if (getWorld() == null || getWorld().isRemote || getPos() == null) {
            ringReplacementTask = null;
            return;
        }

        // Suppress event-driven recheck during ring replacement to prevent cascading
        // structure invalidation from our own intentional block modifications.
        MultiblockWorldData worldData = MultiblockWorldData.get(getWorld());
        worldData.suppressRecheck(this);
        try {
            if (ringReplacementTask.process(RING_REPLACEMENT_BLOCK_BUDGET)) {
                finishRingReplacement(ringReplacementTask);
                ringReplacementTask = null;
            }
        } finally {
            worldData.unsuppressRecheck(this);
        }
    }

    private void completeRingReplacement() {
        while (ringReplacementTask != null) {
            processRingReplacement();
        }
    }

    private void finishRingReplacement(RingReplacementTask task) {
        if (task.restoreBlocks) {
            data.setClearedRingAmount(0);
            data.setRingAmount(task.ringAmount);
        } else {
            data.setClearedRingAmount(task.ringAmount);
            data.setRingAmount(Math.max(data.getRingAmount(), task.ringAmount));
        }
        markDirty();

        if (task.restoreBlocks || task.changedBlocks > 0) {
            GTLog.logger.info(
                    "[FOG] replaceRenderedRings: restore={}, rings={}, clearedRings={}, changedBlocks={}",
                    task.restoreBlocks, data.getRingAmount(), data.getClearedRingAmount(), task.changedBlocks);
        }

        if (task.restoreBlocks && pendingStructureRefresh) {
            pendingStructureRefresh = false;
            refreshStructureNow();
        }
    }

    /**
     * Returns the number of rings that can be replaced with air during rendering.
     * Uses the already-determined ring amount from formStructure() and persisted
     * cleared ring state, avoiding expensive runtime template checks.
     */
    private int getReplaceableRingAmount() {
        int rings = 1;
        if (data.isRingCleared(2) || data.getRingAmount() >= 2) {
            rings = 2;
        }
        if (rings >= 2 && (data.isRingCleared(3) || data.getRingAmount() >= 3)) {
            rings = 3;
        }
        return rings;
    }

    private int getRestorableRingAmount() {
        int rings = data.getClearedRingAmount();
        if (ringReplacementTask != null && !ringReplacementTask.restoreBlocks) {
            rings = Math.max(rings, ringReplacementTask.ringAmount);
        }
        return rings;
    }

    private static String[][] getRingShape(int ringIndex) {
        switch (ringIndex) {
            case 1:
                return ForgeOfGodsStructureString.FIRST_RING;
            case 2:
                return ForgeOfGodsStructureString.SECOND_RING;
            case 3:
                return ForgeOfGodsStructureString.THIRD_RING;
            default:
                throw new IllegalArgumentException("Invalid Godforge ring index: " + ringIndex);
        }
    }

    private static Vec3i getRingOffset(int ringIndex) {
        switch (ringIndex) {
            case 1:
                return FIRST_RING_OFFSET;
            case 2:
                return SECOND_RING_OFFSET;
            case 3:
                return THIRD_RING_OFFSET;
            default:
                throw new IllegalArgumentException("Invalid Godforge ring index: " + ringIndex);
        }
    }

    private static int[] getRingCenter(int ringIndex) {
        switch (ringIndex) {
            case 1:
                return FIRST_RING_CENTER;
            case 2:
                return SECOND_RING_CENTER;
            case 3:
                return THIRD_RING_CENTER;
            default:
                throw new IllegalArgumentException("Invalid Godforge ring index: " + ringIndex);
        }
    }

    private int replaceRingBlocks(String[][] shape, Vec3i pieceOffset, int[] centerOffset, boolean restoreBlocks) {
        BlockPos pieceOrigin = OffsetMode.RELATIVE.apply(getPos(),
                new int[] { pieceOffset.getX(), pieceOffset.getY(), pieceOffset.getZ() },
                getFrontFacingForStructure(), getUpwardsFacing(), isFlipped());
        int changed = 0;

        for (int z = 0; z < shape.length; z++) {
            String[] layer = shape[z];
            for (int y = 0; y < layer.length; y++) {
                String row = layer[y];
                for (int x = 0; x < row.length(); x++) {
                    char marker = row.charAt(x);
                    IBlockState state = restoreBlocks ? getRingBlockState(marker) : getAirReplacement(marker);
                    if (state == null) continue;

                    BlockPos relativePos = RelativeDirection.setActualRelativeOffset(
                            x - centerOffset[0],
                            y - centerOffset[1],
                            z - centerOffset[2],
                            getFrontFacingForStructure(),
                            getUpwardsFacing(),
                            isFlipped(),
                            GODFORGE_STRUCTURE_DIRECTIONS);
                    BlockPos worldPos = pieceOrigin.add(relativePos);
                    if (!getWorld().isBlockLoaded(worldPos)) continue;

                    if (!getWorld().getBlockState(worldPos).equals(state)) {
                        // flag=2: send to client only, no neighbor notifications.
                        // Ring blocks are structural and do not need neighborChanged callbacks.
                        // This avoids ~6M neighborChanged calls when replacing three rings.
                        getWorld().setBlockState(worldPos, state, 2);
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    private final class RingReplacementTask {

        private final boolean restoreBlocks;
        private final int ringAmount;
        private int ringIndex = 1;
        private int x;
        private int y;
        private int z;
        private int changedBlocks;

        private RingReplacementTask(boolean restoreBlocks, int ringAmount) {
            this.restoreBlocks = restoreBlocks;
            this.ringAmount = ringAmount;
        }

        private boolean process(int blockBudget) {
            int processedBlocks = 0;
            while (ringIndex <= ringAmount && processedBlocks < blockBudget) {
                String[][] shape = getRingShape(ringIndex);
                Vec3i pieceOffset = getRingOffset(ringIndex);
                int[] centerOffset = getRingCenter(ringIndex);
                BlockPos pieceOrigin = OffsetMode.RELATIVE.apply(getPos(),
                        new int[] { pieceOffset.getX(), pieceOffset.getY(), pieceOffset.getZ() },
                        getFrontFacingForStructure(), getUpwardsFacing(), isFlipped());

                while (z < shape.length && processedBlocks < blockBudget) {
                    String[] layer = shape[z];
                    while (y < layer.length && processedBlocks < blockBudget) {
                        String row = layer[y];
                        while (x < row.length() && processedBlocks < blockBudget) {
                            int currentX = x++;
                            char marker = row.charAt(currentX);
                            IBlockState state = restoreBlocks ? getRingBlockState(marker) : getAirReplacement(marker);
                            if (state == null) continue;

                            processedBlocks++;
                            BlockPos relativePos = RelativeDirection.setActualRelativeOffset(
                                    currentX - centerOffset[0],
                                    y - centerOffset[1],
                                    z - centerOffset[2],
                                    getFrontFacingForStructure(),
                                    getUpwardsFacing(),
                                    isFlipped(),
                                    GODFORGE_STRUCTURE_DIRECTIONS);
                            BlockPos worldPos = pieceOrigin.add(relativePos);
                            if (!getWorld().isBlockLoaded(worldPos)) continue;

                            if (!getWorld().getBlockState(worldPos).equals(state)) {
                                getWorld().setBlockState(worldPos, state, 2);
                                changedBlocks++;
                            }
                        }
                        if (x >= row.length()) {
                            x = 0;
                            y++;
                        }
                    }
                    if (y >= layer.length) {
                        y = 0;
                        z++;
                    }
                }
                if (z >= shape.length) {
                    z = 0;
                    ringIndex++;
                }
            }
            return ringIndex > ringAmount;
        }
    }

    @Nullable
    private static IBlockState getAirReplacement(char marker) {
        return marker >= 'A' && marker <= 'Z' ? Blocks.AIR.getDefaultState() : null;
    }

    @Nullable
    private static IBlockState getRingBlockState(char marker) {
        switch (marker) {
            case 'B':
                return getCasingState(BlockGodforgeCasing.CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING);
            case 'C':
                return getCasingState(BlockGodforgeCasing.CasingType.CELESTIAL_MATTER_GUIDANCE_CASING);
            case 'D':
                return getCasingState(BlockGodforgeCasing.CasingType.BOUNDLESS_GRAVITATIONALLY_SEVERED_STRUCTURE_CASING);
            case 'E':
                return getCasingState(
                        BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING);
            case 'F':
                return getCasingState(BlockGodforgeCasing.CasingType.STELLAR_ENERGY_SIPHON_CASING);
            case 'G':
                return getCasingState(BlockGodforgeCasing.CasingType.REMOTE_GRAVITON_FLOW_MODULATOR);
            case 'H':
                return getGlassState();
            case 'I':
                return getCasingState(BlockGodforgeCasing.CasingType.MEDIAL_GRAVITON_FLOW_MODULATOR);
            case 'K':
                return getCasingState(BlockGodforgeCasing.CasingType.CENTRAL_GRAVITON_FLOW_MODULATOR);
            default:
                return null;
        }
    }

    private void cleanupPossibleRendererBlocks() {
        BlockPos controllerPos = getPos();
        if (controllerPos == null) return;

        for (EnumFacing facing : EnumFacing.VALUES) {
            destroyRendererAt(controllerPos.offset(facing, RENDER_OFFSET));
        }
        data.setRenderActive(false);
        data.setClearedRingAmount(0);
        markDirty();
    }

    private void destroyRendererAt(BlockPos renderPos) {
        if (!getWorld().isBlockLoaded(renderPos)) return;

        IBlockState state = getWorld().getBlockState(renderPos);
        if (state.getBlock() == MetaBlocks.GODFORGE_RENDER) {
            getWorld().setBlockToAir(renderPos);
        }
    }

    /**
     * Syncs current star parameters to the render TileEntity.
     */
    public void updateRenderer() {
        if (getWorld() == null || getWorld().isRemote) return;

        BlockPos renderPos = getRenderPos();
        if (renderPos == null) return;

        TileEntity te = getWorld().getTileEntity(renderPos);
        if (!(te instanceof GodforgeRenderTileEntity)) return;

        GodforgeRenderTileEntity renderTE = (GodforgeRenderTileEntity) te;
        renderTE.setOwnerPos(getPos());
        renderTE.setRingCount(Math.max(1, data.getClearedRingAmount()));
        renderTE.setStarRadius(data.getStarSize());
        renderTE.setRotationSpeed(data.getRotationSpeed());
        renderTE.setColor(
                data.getStarColors()
                        .getByName(data.getSelectedStarColor()));
        renderTE.updateToClient();
    }

    /**
     * Calculates the world position where the render TE should be placed.
     * The star is at the center of the ring structure, behind the controller.
     */
    @Nullable
    private BlockPos getRenderPos() {
        BlockPos controllerPos = getPos();
        if (controllerPos == null) return null;

        EnumFacing back = getFrontFacing().getOpposite();
        return controllerPos.offset(back, RENDER_OFFSET);
    }

    // ==================== GUI ====================

    @Override
    protected MultiblockUIFactory createUIFactory() {
        return new GodforgeUIFactory(this);
    }

    // ==================== NBT ====================

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        NBTTagCompound tag = super.writeToNBT(data);
        this.data.writeToNBT(tag);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.data.readFromNBT(data);
        // Recalculate milestone percentages immediately after loading,
        // since they are not persisted in NBT but derived from totals.
        determineMilestoneProgress();
        reinitializeStructurePattern();
    }
}
