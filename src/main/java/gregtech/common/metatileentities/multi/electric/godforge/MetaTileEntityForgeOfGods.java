package gregtech.common.metatileentities.multi.electric.godforge;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IGodforgeModule;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.metatileentity.multiblock.MultiblockWorldData;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.OffsetMode;
import gregtech.api.pattern.PatternError;
import gregtech.api.pattern.StructureCondition;
import gregtech.api.pattern.StructureExternalDependencies;
import gregtech.api.pattern.StructureFailureTrace;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.element.Elements;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureDefinition;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 *   <li>Formation, validation, previews, and builds route through the canonical
 *       StructureDefinition backed by the multi-piece pattern.</li>
 *   <li>Ring pieces remain split, and their active set is selected from the desired ring tier.</li>
 * </ul>
 */
public class MetaTileEntityForgeOfGods extends MultiblockWithDisplayBase {

    // Core tick interval: every 100 ticks (5 seconds) just like GT5
    private static final int TICK_INTERVAL = 100;
    private static final int RING_REPLACEMENT_BLOCK_BUDGET = 1024;
    // Piece offsets use OffsetMode.RELATIVE coordinates (right, up, back).
    // The source templates advance toward FRONT after the beam shaft, so their
    // positive forward distances are negative values on the BACK axis here.
    // Derived from GT5 structurelib checkPiece offsets:
    //   beam_shaft: controller is at template [63, 14, 1] → offset (0, 0, 0)
    //   first_ring: controller maps to template [63, 14, 0] → 59 FRONT aisles after controller
    //   second_ring: controller maps to template [55, 11, 0] → 67 FRONT aisles after controller
    //   third_ring: controller maps to template [47, 13, 0] → 76 FRONT aisles after controller
    private static final Vec3i BEAM_SHAFT_OFFSET = Vec3i.NULL_VECTOR;
    private static final Vec3i FIRST_RING_OFFSET = new Vec3i(0, 0, -59);
    private static final Vec3i SECOND_RING_OFFSET = new Vec3i(0, 0, -67);
    private static final Vec3i THIRD_RING_OFFSET = new Vec3i(0, 0, -76);
    private static final RelativeDirection[] GODFORGE_STRUCTURE_DIRECTIONS = { RIGHT, UP, FRONT };
    // External center offsets [x, y, z, minZ, maxZ] for sub-piece templates without selfPredicate
    private static final int[] FIRST_RING_CENTER = { 63, 14, 0, 0, 0 };
    private static final int[] SECOND_RING_CENTER = { 55, 11, 0, 0, 0 };
    private static final int[] THIRD_RING_CENTER = { 47, 13, 0, 0, 0 };
    private static final StructureDefinition<MetaTileEntityForgeOfGods>
            STRUCTURE_DEFINITION = buildGodforgeStructureDefinition();
    /**
     * Offset from controller to render position along the structure's back axis. In GT5, the star is at the center of
     * the ring structure, 122 blocks behind the controller.
     */
    private static final int RENDER_OFFSET = 122;
    private final ForgeOfGodsData data = new ForgeOfGodsData();
    private final List<MTEBaseModule> moduleHatches = new ArrayList<>();
    private SyncHypervisor syncHypervisor;
    // Start at TICK_INTERVAL-1 so the first updateFormedValid tick immediately
    // runs the full logic (milestone recalculation, module connections, etc.)
    private long ticker = TICK_INTERVAL - 1;
    private int lastKnownRingAmount = 1;
    private int lastKnownClearedRingAmount = 0;

    // ==================== Multi-Piece Pattern (Canonical Structure) ====================
    private long lastStructureFailureLogTime = -1;
    private long lastModuleConnectionLogTime = -1;
    private long lastRingStateLogTime = -1;
    private long lastRenderedRingOwnershipLogTime = -1;
    private boolean pendingStructureRefresh = false;
    private boolean recoveringRenderedStructure = false;
    /**
     * Dirty flag for ring block replacement. Set when ring state changes (e.g., renderer created/destroyed, ring
     * unlocked/respec). Cleared after replaceRenderedRings() is executed. This avoids scanning ~1M block positions
     * every 100 ticks when nothing has changed.
     */
    private boolean ringsDirty = false;
    private RingReplacementTask ringReplacementTask;

    public MetaTileEntityForgeOfGods(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
        this.data.setStructureStateChangeListener(this::notifyGodforgeStructureStateChanged);
    }

    private static StructureDefinition<MetaTileEntityForgeOfGods> buildGodforgeStructureDefinition() {
        StructureDefinition.Builder<MetaTileEntityForgeOfGods> builder = StructureDefinition
                .builder(RIGHT, UP, FRONT);

        applyAllElements(builder.piece("beam_shaft", ForgeOfGodsStructureString.BEAM_SHAFT, BEAM_SHAFT_OFFSET),
                true, true).end();
        applyAllElements(builder.conditionalPiece(
                "first_ring",
                ForgeOfGodsStructureString.FIRST_RING,
                FIRST_RING_OFFSET,
                ringTemplateCondition(1, false)), false, false)
                .offsetMode(OffsetMode.RELATIVE)
                .centerOffset(FIRST_RING_CENTER[0], FIRST_RING_CENTER[1], FIRST_RING_CENTER[2])
                .end();
        applyAllElements(builder.conditionalPiece(
                "first_ring_air",
                ForgeOfGodsStructureString.FIRST_RING_AIR,
                FIRST_RING_OFFSET,
                ringTemplateCondition(1, true)), false, false)
                .offsetMode(OffsetMode.RELATIVE)
                .centerOffset(FIRST_RING_CENTER[0], FIRST_RING_CENTER[1], FIRST_RING_CENTER[2])
                .runtimeOnly()
                .end();
        applyAllElements(builder.conditionalPiece(
                "second_ring",
                ForgeOfGodsStructureString.SECOND_RING,
                SECOND_RING_OFFSET,
                ringTemplateCondition(2, false)), false, false)
                .offsetMode(OffsetMode.RELATIVE)
                .centerOffset(SECOND_RING_CENTER[0], SECOND_RING_CENTER[1], SECOND_RING_CENTER[2])
                .end();
        applyAllElements(builder.conditionalPiece(
                "second_ring_air",
                ForgeOfGodsStructureString.SECOND_RING_AIR,
                SECOND_RING_OFFSET,
                ringTemplateCondition(2, true)), false, false)
                .offsetMode(OffsetMode.RELATIVE)
                .centerOffset(SECOND_RING_CENTER[0], SECOND_RING_CENTER[1], SECOND_RING_CENTER[2])
                .runtimeOnly()
                .end();
        applyAllElements(builder.conditionalPiece(
                "third_ring",
                ForgeOfGodsStructureString.THIRD_RING,
                THIRD_RING_OFFSET,
                ringTemplateCondition(3, false)), false, false)
                .offsetMode(OffsetMode.RELATIVE)
                .centerOffset(THIRD_RING_CENTER[0], THIRD_RING_CENTER[1], THIRD_RING_CENTER[2])
                .end();
        applyAllElements(builder.conditionalPiece(
                "third_ring_air",
                ForgeOfGodsStructureString.THIRD_RING_AIR,
                THIRD_RING_OFFSET,
                ringTemplateCondition(3, true)), false, false)
                .offsetMode(OffsetMode.RELATIVE)
                .centerOffset(THIRD_RING_CENTER[0], THIRD_RING_CENTER[1], THIRD_RING_CENTER[2])
                .runtimeOnly()
                .end();

        return builder.build();
    }

    @NotNull
    private static StructureCondition<MetaTileEntityForgeOfGods>
    ringTemplateCondition(int ringIndex, boolean rendererOwned) {
        return StructureCondition.withDependencies(
                context -> {
                    MetaTileEntityForgeOfGods controller =
                            context.getController();
                    if (controller == null) {
                        return ringIndex == 1 && !rendererOwned;
                    }
                    boolean active = ringIndex == 1
                            || controller.getStructureRingTargetAmount()
                            >= ringIndex;
                    return active
                            && controller.canUseRenderedRingTemplate(
                            ringIndex, rendererOwned) == rendererOwned;
                },
                StructureExternalDependencies.upgrades(),
                StructureExternalDependencies.configuration());
    }

    private static StructureDefinition.PieceBuilder<MetaTileEntityForgeOfGods> applyAllElements(
            StructureDefinition.PieceBuilder<MetaTileEntityForgeOfGods> builder,
            boolean includeController,
            boolean allowEmptyModuleSlots) {
        if (includeController) {
            builder.where('S', godforgeController());
        }
        applySharedElements(builder, allowEmptyModuleSlots);
        return builder;
    }

    private static void applySharedElements(
            StructureDefinition.PieceBuilder<MetaTileEntityForgeOfGods> builder,
            boolean allowEmptyModuleSlots) {
        builder.where('A', hatches())
                .where('B', Elements.block(getCasingState(
                        BlockGodforgeCasing.CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING)))
                .where('C', Elements.block(getCasingState(
                        BlockGodforgeCasing.CasingType.CELESTIAL_MATTER_GUIDANCE_CASING)))
                .where('D', Elements.block(getCasingState(
                        BlockGodforgeCasing.CasingType.BOUNDLESS_GRAVITATIONALLY_SEVERED_STRUCTURE_CASING)))
                .where('E', Elements.block(getCasingState(
                        BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING)))
                .where('F', Elements.block(getCasingState(
                        BlockGodforgeCasing.CasingType.STELLAR_ENERGY_SIPHON_CASING)))
                .where('G', Elements.block(getCasingState(
                        BlockGodforgeCasing.CasingType.REMOTE_GRAVITON_FLOW_MODULATOR)))
                .where('H', Elements.block(getGlassState()))
                .where('J', godforgeModuleSlot(allowEmptyModuleSlots))
                .where('I', Elements.block(getCasingState(
                        BlockGodforgeCasing.CasingType.MEDIAL_GRAVITON_FLOW_MODULATOR)))
                .where('K', Elements.block(getCasingState(
                        BlockGodforgeCasing.CasingType.CENTRAL_GRAVITON_FLOW_MODULATOR)))
                .where('L', Elements.air());
    }

    private static IBlockState getCasingState(BlockGodforgeCasing.CasingType type) {
        return MetaBlocks.GODFORGE_CASING.getState(type);
    }

    private static IBlockState getGlassState() {
        return MetaBlocks.GODFORGE_GLASS.getState(
                BlockGodforgeGlass.GlassType.SPATIALLY_TRANSCENDENT_GRAVITATIONAL_LENS);
    }

    private static IStructureElement hatches() {
        return Elements.chain(
                Elements.abilities(MultiblockAbility.IMPORT_ITEMS),
                Elements.abilities(MultiblockAbility.IMPORT_FLUIDS),
                Elements.abilities(MultiblockAbility.EXPORT_ITEMS),
                Elements.abilities(MultiblockAbility.EXPORT_FLUIDS),
                Elements.block(getCasingState(
                        BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING)));
    }

    private static IStructureElement godforgeModules() {
        return Elements.metaTileEntities(
                MetaTileEntities.GODFORGE_SMELTING_MODULE,
                MetaTileEntities.GODFORGE_MOLTEN_MODULE,
                MetaTileEntities.GODFORGE_PLASMA_MODULE,
                MetaTileEntities.GODFORGE_EXOTIC_MODULE);
    }

    private static IStructureElement godforgeModuleSlot(boolean allowEmptyModuleSlots) {
        IStructureElement predicate = Elements.chain(
                godforgeModules(),
                Elements.block(getCasingState(
                        BlockGodforgeCasing.CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING)));
        return allowEmptyModuleSlots ? Elements.chain(predicate, Elements.air()) : predicate;
    }

    private static IStructureElement godforgeController() {
        return Elements.self(MetaTileEntityForgeOfGods.class);
    }

    private static String describeFailurePath(@Nullable StructureFailureTrace failure) {
        return failure == null ? "none" : failure.getPath();
    }

    private static String describeFailureOperation(@Nullable StructureFailureTrace failure) {
        return failure == null ? "none" : failure.getOperation();
    }

    private static String describeFailureResult(@Nullable StructureFailureTrace failure) {
        return failure == null ? "none" : failure.getResult();
    }

    private static String describeFailureExpected(@Nullable StructureFailureTrace failure) {
        return failure == null || failure.getExpected() == null ? "none" : failure.getExpected();
    }

    private static String describeFailureActual(@Nullable StructureFailureTrace failure) {
        return failure == null || failure.getActual() == null ? "none" : failure.getActual();
    }

    private static String describeFailureMissingAbilities(@Nullable StructureFailureTrace failure) {
        return failure == null ? "none" : failure.getMissingAbilities();
    }

    @Nullable
    private static BlockPos describeFailureErrorPos(@Nullable StructureFailureTrace failure) {
        return failure == null ? null : failure.getErrorPos();
    }

    @Nullable
    private static BlockPos getFailureErrorPos(@Nullable StructureFailureTrace failure, @NotNull PatternError error) {
        if (failure != null && failure.getErrorPos() != null) {
            return failure.getErrorPos();
        }
        try {
            return error.getPos();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    // ==================== Block State Helpers ====================

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

    private static String describeModule(MTEBaseModule module) {
        if (module == null) return "null";
        return module.metaTileEntityId + "@" + module.getPos() +
                "{formed=" + module.isStructureFormed() +
                ", connected=" + module.isConnected() +
                ", type=" + module.getClass().getSimpleName() + "}";
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

    @Nullable
    private static IBlockState getAirReplacement(char marker) {
        return marker >= 'A' && marker <= 'Z' ? Blocks.AIR.getDefaultState() : null;
    }

    // ==================== Structure Lifecycle ====================

    @Nullable
    private static IBlockState getRingBlockState(char marker) {
        switch (marker) {
            case 'B':
                return getCasingState(BlockGodforgeCasing.CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING);
            case 'C':
                return getCasingState(BlockGodforgeCasing.CasingType.CELESTIAL_MATTER_GUIDANCE_CASING);
            case 'D':
                return getCasingState(
                        BlockGodforgeCasing.CasingType.BOUNDLESS_GRAVITATIONALLY_SEVERED_STRUCTURE_CASING);
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

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityForgeOfGods(metaTileEntityId);
    }

    @Override
    protected StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    private void notifyGodforgeStructureStateChanged() {
        notifyStructureUpgradesChanged();
        notifyStructureConfigChanged();
    }

    private int getRenderedRingTemplateMask() {
        int mask = 0;
        for (int ringIndex = 1; ringIndex <= ForgeOfGodsData.MAX_RING_AMOUNT; ringIndex++) {
            if (canUseRenderedRingTemplate(ringIndex, false)) {
                mask |= 1 << (ringIndex - 1);
            }
        }
        return mask;
    }

    private boolean canUseRenderedRingTemplate(int ringIndex, boolean logSkipped) {
        boolean ringCleared = data.isRingCleared(ringIndex);
        boolean rendererOwned = isRendererOwnedByThisController();
        boolean foreignRenderer = isForeignRendererLoadedAtRenderPos();
        boolean allowed = GodforgeRenderedRingPolicy.canUseRenderedRingTemplate(
                ringCleared,
                data.isRenderActive(),
                recoveringRenderedStructure,
                rendererOwned,
                foreignRenderer,
                data.getInternalBattery());

        if (!allowed && logSkipped && ringCleared && (data.isRenderActive() || recoveringRenderedStructure)) {
            logRenderedRingTemplateSkipped(ringIndex, rendererOwned, foreignRenderer);
        }
        return allowed;
    }

    private boolean isRendererOwnedByThisController() {
        if (getWorld() == null || getPos() == null) return false;

        BlockPos renderPos = getRenderPos();
        if (renderPos == null || !getWorld().isBlockLoaded(renderPos)) return false;
        if (getWorld().getBlockState(renderPos).getBlock() != MetaBlocks.GODFORGE_RENDER) return false;

        TileEntity te = getWorld().getTileEntity(renderPos);
        if (!(te instanceof GodforgeRenderTileEntity)) return false;

        BlockPos ownerPos = ((GodforgeRenderTileEntity) te).getOwnerPosForDebug();
        return getPos().equals(ownerPos);
    }

    private boolean isForeignRendererLoadedAtRenderPos() {
        if (getWorld() == null || getPos() == null) return false;

        BlockPos renderPos = getRenderPos();
        if (renderPos == null || !getWorld().isBlockLoaded(renderPos)) return false;
        if (getWorld().getBlockState(renderPos).getBlock() != MetaBlocks.GODFORGE_RENDER) return false;

        TileEntity te = getWorld().getTileEntity(renderPos);
        if (!(te instanceof GodforgeRenderTileEntity)) return false;

        BlockPos ownerPos = ((GodforgeRenderTileEntity) te).getOwnerPosForDebug();
        return ownerPos != null && !getPos().equals(ownerPos);
    }

    private void logRenderedRingTemplateSkipped(int ringIndex, boolean rendererOwned, boolean foreignRenderer) {
        if (getWorld() == null || getWorld().isRemote) return;
        if (!GTLog.logger.isDebugEnabled()) return;

        long worldTime = getWorld().getTotalWorldTime();
        if (lastRenderedRingOwnershipLogTime >= 0 &&
                worldTime - lastRenderedRingOwnershipLogTime < TICK_INTERVAL) {
            return;
        }
        lastRenderedRingOwnershipLogTime = worldTime;

        GTLog.logger.debug("[FOG] rendered ring template skipped: controller={}, ring={}, renderActive={}, " +
                        "recovering={}, rendererOwned={}, foreignRenderer={}, clearedRings={}, battery={}, owner={}",
                getPos(), ringIndex, data.isRenderActive(), recoveringRenderedStructure,
                rendererOwned, foreignRenderer, data.getClearedRingAmount(), data.getInternalBattery(),
                describeRendererOwnershipForLog());
    }

    private String describeRendererOwnershipForLog() {
        if (getWorld() == null) return "world=null";

        BlockPos renderPos = getRenderPos();
        if (renderPos == null) return "renderPos=null";
        if (!getWorld().isBlockLoaded(renderPos)) return "renderPos=" + renderPos + ", loaded=false";

        IBlockState state = getWorld().getBlockState(renderPos);
        TileEntity te = getWorld().getTileEntity(renderPos);
        String teName = te == null ? "null" : te.getClass().getName();
        if (!(te instanceof GodforgeRenderTileEntity renderTE)) {
            return "renderPos=" + renderPos + ", loaded=true, block=" + state + ", te=" + teName +
                    ", ownedByThis=false";
        }

        BlockPos ownerPos = renderTE.getOwnerPosForDebug();
        boolean ownedByThis = getPos() != null && getPos().equals(ownerPos);
        return "renderPos=" + renderPos + ", loaded=true, block=" + state + ", te=" + teName +
                ", owner=" + ownerPos + ", ownedByThis=" + ownedByThis;
    }

    private int getDesiredRingAmount() {
        return data.getDesiredRingAmount();
    }

    private int getStructureRingTargetAmount() {
        int renderedRings = (data.isRenderActive() || recoveringRenderedStructure) ? data.getClearedRingAmount() : 0;
        return Math.max(getDesiredRingAmount(), renderedRings);
    }

    private int getFormedRingAmount() {
        return data.getFormedRingAmount();
    }

    private void setFormedRingAmount(int formedRingAmount) {
        data.setFormedRingAmount(formedRingAmount);
    }

    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    protected Object getStructureConfigDependencyValue() {
        Map<String, Object> values = new LinkedHashMap<>(
                (Map<String, Object>) super.getStructureConfigDependencyValue());
        values.put("godforgeRenderActive", data.isRenderActive());
        values.put("godforgeRendererDisabled", data.isRendererDisabled());
        values.put("godforgeClearedRings", data.getClearedRingAmount());
        values.put("godforgeRenderedRingMask", getRenderedRingTemplateMask());
        values.put("godforgeRecoveringRenderedStructure", recoveringRenderedStructure);
        values.put("godforgeStructureRingTarget", getStructureRingTargetAmount());
        return values;
    }

    @NotNull
    @Override
    protected Object getStructureUpgradeDependencyValue() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("activeUpgrades", data.getStructureUpgradeSnapshotValue());
        values.put("desiredRings", getDesiredRingAmount());
        values.put("formedRings", getFormedRingAmount());
        return values;
    }

    private void logRingState(String phase, boolean force) {
        if (getWorld() == null || getWorld().isRemote) return;
        if (!force && !GTLog.logger.isDebugEnabled()) return;

        long worldTime = getWorld().getTotalWorldTime();
        if (!force && lastRingStateLogTime >= 0 && worldTime - lastRingStateLogTime < TICK_INTERVAL) return;
        lastRingStateLogTime = worldTime;

        GTLog.logger.debug("[FOG] ring state: phase={}, controller={}, formed={}, desired={}, cleared={}, " +
                        "structureTarget={}, renderActive={}, rendererDisabled={}, battery={}, pendingRefresh={}, " +
                        "replacement={}, renderOwner={}",
                phase, getPos(), getFormedRingAmount(), getDesiredRingAmount(), data.getClearedRingAmount(),
                getStructureRingTargetAmount(),
                data.isRenderActive(), data.isRendererDisabled(), data.getInternalBattery(), pendingStructureRefresh,
                ringReplacementTask == null ? "none" : ringReplacementTask.describe(),
                describeRendererOwnershipForLog());
    }

    @Override
    public void checkStructurePattern() {
        super.checkStructurePattern();
        if (!isStructureFormed()) {
            tryRecoverRenderedStructure();
        }
        if (!isStructureFormed()) {
            logStructureFailure();
        }
    }

    private boolean tryRecoverRenderedStructure() {
        if (getWorld() == null || getWorld().isRemote || getPos() == null) return false;
        if (data.getInternalBattery() <= 0) return false;
        if (data.getClearedRingAmount() <= 0) return false;

        recoveringRenderedStructure = true;
        try {
            super.checkStructurePattern();
        } finally {
            recoveringRenderedStructure = false;
        }

        if (!isStructureFormed()) {
            GTLog.logger.info("[FOG] persisted rendered structure recovery failed at {}; clearedRings={}, " +
                            "battery={}, ownedByThis={}, owner={}",
                    getPos(), data.getClearedRingAmount(), data.getInternalBattery(),
                    isRendererOwnedByThisController(), describeRendererOwnershipForLog());
            return false;
        }

        GTLog.logger.info("[FOG] recovering persisted rendered structure at {}; clearedRings={}, battery={}, " +
                        "ownedByThis={}, owner={}",
                getPos(), data.getClearedRingAmount(), data.getInternalBattery(),
                isRendererOwnedByThisController(), describeRendererOwnershipForLog());
        data.setRenderActive(true);
        logRingState("recover-rendered-structure", true);
        ensureRendererState();
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

    private void logStructureFailure() {
        if (getWorld() == null || getWorld().isRemote) return;

        long worldTime = getWorld().getTotalWorldTime();
        if (lastStructureFailureLogTime >= 0 && worldTime - lastStructureFailureLogTime < TICK_INTERVAL) return;
        lastStructureFailureLogTime = worldTime;

        StructureFailureTrace failure = getLastFailureTrace();
        PatternError error = failure == null ? getLastStructureError() : failure.getError();
        BlockPos renderPos = getRenderPos();
        String renderState = "null";
        if (renderPos != null) {
            renderState = getWorld().isBlockLoaded(renderPos) ?
                    String.valueOf(getWorld().getBlockState(renderPos)) :
                    "unloaded";
        }

        if (error == null) {
            GTLog.logger.warn("[FOG] structure check failed at controller={}, front={}, structureFront={}, up={}, " +
                            "renderActive={}, " +
                            "rendererDisabled={}, battery={}, formedRings={}, desiredRings={}, clearedRings={}, " +
                            "structureTarget={}, renderedMask={}, renderOwner={}, tracePath={}, traceOperation={}, " +
                            "traceResult={}, traceErrorPos={}, traceExpected={}, traceActual={}, missingAbilities={}, " +
                            "renderPos={}, renderState={}, no pattern error",
                    getPos(), getFrontFacing(), getFrontFacingForStructure(), getUpwardsFacing(),
                    data.isRenderActive(), data.isRendererDisabled(),
                    data.getInternalBattery(), getFormedRingAmount(), getDesiredRingAmount(),
                    data.getClearedRingAmount(), getStructureRingTargetAmount(), getRenderedRingTemplateMask(),
                    describeRendererOwnershipForLog(), describeFailurePath(failure), describeFailureOperation(failure),
                    describeFailureResult(failure), describeFailureErrorPos(failure), describeFailureExpected(failure),
                    describeFailureActual(failure), describeFailureMissingAbilities(failure), renderPos, renderState);
            return;
        }

        BlockPos errorPos = getFailureErrorPos(failure, error);
        IBlockState actualState = errorPos != null && getWorld().isBlockLoaded(errorPos) ?
                getWorld().getBlockState(errorPos) :
                null;
        TileEntity actualTile = actualState != null ? getWorld().getTileEntity(errorPos) : null;

        GTLog.logger.warn("[FOG] structure check failed at controller={}, front={}, structureFront={}, up={}, " +
                        "renderActive={}, " +
                        "rendererDisabled={}, battery={}, formedRings={}, desiredRings={}, clearedRings={}, " +
                        "structureTarget={}, renderedMask={}, renderOwner={}, tracePath={}, traceOperation={}, " +
                        "traceResult={}, traceExpected={}, traceActual={}, missingAbilities={}, renderPos={}, " +
                        "renderState={}, errorType={}, errorPos={}, actualState={}, actualTile={}, candidates={}",
                getPos(), getFrontFacing(), getFrontFacingForStructure(), getUpwardsFacing(),
                data.isRenderActive(), data.isRendererDisabled(),
                data.getInternalBattery(), getFormedRingAmount(), getDesiredRingAmount(),
                data.getClearedRingAmount(), getStructureRingTargetAmount(), getRenderedRingTemplateMask(),
                describeRendererOwnershipForLog(), describeFailurePath(failure), describeFailureOperation(failure),
                describeFailureResult(failure), describeFailureExpected(failure), describeFailureActual(failure),
                describeFailureMissingAbilities(failure), renderPos, renderState,
                error.getClass().getSimpleName(), errorPos,
                actualState != null ? actualState : "unloaded",
                describeTileEntity(actualTile), describeCandidates(error));
    }

    @Nullable
    private StructureFailureTrace getLastFailureTrace() {
        return getStructureRuntime() == null ? null : getStructureRuntime().getLastFailure();
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formStructureWithDisplay(formed);
        formGodforgeStructure(formed);
    }

    private void formGodforgeStructure(@NotNull FormedStructureView formed) {
        logRingState("form-before-ring-commit", true);
        commitFormedRingAmountFromStructure(formed);
        logRingState("form-after-ring-commit", true);
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

    // ==================== Rendering ====================

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
     * Discovers Godforge modules from the committed structure ability map. Modules are sub-multiblocks attached to the
     * beam_shaft at 'J' positions.
     */
    private void discoverModules() {
        moduleHatches.clear();

        for (IGodforgeModule module : getAbilities(MultiblockAbility.GODFORGE_MODULE)) {
            if (module instanceof MTEBaseModule && !moduleHatches.contains(module)) {
                moduleHatches.add((MTEBaseModule) module);
            }
        }

        logModuleDiscovery();
    }

    private int countCommittedModuleParts() {
        int moduleParts = 0;
        for (IMultiblockPart part : getMultiblockParts()) {
            if (part instanceof MTEBaseModule) {
                moduleParts++;
            }
        }
        return moduleParts;
    }

    // ==================== Tick Logic ====================

    private String describeCommittedModulePartsMissingFromAbilities() {
        StringBuilder missing = new StringBuilder();
        for (IMultiblockPart part : getMultiblockParts()) {
            if (part instanceof MTEBaseModule && !moduleHatches.contains(part)) {
                if (missing.length() > 0) missing.append(" | ");
                missing.append(describeModule((MTEBaseModule) part));
            }
        }
        return missing.length() == 0 ? "[]" : missing.toString();
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

        int committedModuleParts = countCommittedModuleParts();
        if (committedModuleParts != moduleHatches.size()) {
            GTLog.logger.warn("[FOG] module discovery mismatch: controller={}, parts={}, committedModuleParts={}, " +
                            "abilityModules={}, missingAbilityModules={}, formedRings={}, desiredRings={}",
                    getPos(), getMultiblockParts().size(), committedModuleParts,
                    getAbilities(MultiblockAbility.GODFORGE_MODULE).size(),
                    describeCommittedModulePartsMissingFromAbilities(), getFormedRingAmount(), getDesiredRingAmount());
        }

        GTLog.logger.info(
                "[FOG] discoverModules: controller={}, parts={}, committedModuleParts={}, abilityModules={}, " +
                        "moduleHatches={}, battery={}, formedRings={}, desiredRings={}, modules={}",
                getPos(), getMultiblockParts().size(), committedModuleParts,
                getAbilities(MultiblockAbility.GODFORGE_MODULE).size(), moduleHatches.size(),
                data.getInternalBattery(), getFormedRingAmount(), getDesiredRingAmount(),
                modules.length() == 0 ? "[]" : modules);
    }

    // ==================== Fuel System ====================

    /**
     * Commits the ring tier from the successful multi-piece structure definition. The active piece set was already
     * selected from desiredRingAmount and validated by the structure check, so this must not rescan ring templates as a
     * second source.
     */
    private void commitFormedRingAmountFromStructure(@NotNull FormedStructureView formed) {
        int rings = GodforgeRingMatchPolicy.getFormedRingAmount(formed);
        int previousRings = getFormedRingAmount();
        if (previousRings != rings) {
            setFormedRingAmount(rings);
            GTLog.logger.info("[FOG] formed ring amount changed at {}: {} -> {}, desired={}, cleared={}, " +
                            "renderActive={}, battery={}, source=multi-piece",
                    getPos(), previousRings, rings, getDesiredRingAmount(), data.getClearedRingAmount(),
                    data.isRenderActive(), data.getInternalBattery());
            markDirty();
        }
    }

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

    // ==================== Battery Management ====================

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(), true, true);
    }

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

        // Ring amount is committed from the successful multi-piece structure at formStructure() time,
        // not from tick-loop template scans.
        // During runtime, ring count only changes through GUI operations (upgrade/respec)
        // which call refreshStructureFromGui() -> formStructure() -> commitFormedRingAmountFromStructure().
        int maxModuleCount = 8 + (getFormedRingAmount() - 1) * 4;

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

        if (getFormedRingAmount() != lastKnownRingAmount ||
                data.getClearedRingAmount() != lastKnownClearedRingAmount) {
            logRingState("tick-ring-state-change", true);
            lastKnownRingAmount = getFormedRingAmount();
            lastKnownClearedRingAmount = data.getClearedRingAmount();
            if (data.isRenderActive() && !data.isRendererDisabled()) {
                updateRenderer();
            }
            notifyGodforgeStructureStateChanged();
        }
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    /**
     * Absorbs stellar fuel from input bus for battery startup, or graviton shards if battery is already running and END
     * upgrade is active.
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
     * Returns the item used as stellar fuel for battery startup. In GT5 this was Avaritia's Infinity Catalyst; here
     * it's a dedicated MetaItem.
     */
    private ItemStack getStellarFuelItem() {
        return MetaItems.STELLAR_FUEL.getStackForm();
    }

    // ==================== Milestone Tracking ====================

    /**
     * Drains fuel fluid from input hatches and manages battery charge. Port of GT5 MTEForgeOfGods#drainFuel().
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
     * Lightweight renderer integrity check. Only verifies that the render block exists at the expected position (single
     * getBlockState call). If missing, recreates it. Ring block replacement is handled separately by the ringsDirty
     * flag.
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
                GTLog.logger.info("[FOG] ensureRendererState: render block missing, recreating. isRenderActive={}",
                        data.isRenderActive());
                data.setRenderActive(false);
                createRenderer();
            } else if (!isRendererOwnedByThisController()) {
                if (isForeignRendererLoadedAtRenderPos()) {
                    GTLog.logger.warn(
                            "[FOG] ensureRendererState: foreign render block at {}; disabling renderer. owner={}",
                            renderPos, describeRendererOwnershipForLog());
                    data.setRenderActive(false);
                    notifyGodforgeStructureStateChanged();
                    return;
                }
                GTLog.logger.info("[FOG] ensureRendererState: render owner missing or invalid, repairing. owner={}",
                        describeRendererOwnershipForLog());
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
                        "maxModules={}, formedRings={}, desiredRings={}, clearedRings={}, fuelType={}, " +
                        "fuelFactor={}, upgrades={}, shouldProcess={}",
                getPos(), isStructureFormed(), data.getInternalBattery(), moduleHatches.size(), maxModuleCount,
                getFormedRingAmount(), getDesiredRingAmount(), data.getClearedRingAmount(),
                data.getSelectedFuelType(), data.getFuelConsumptionFactor(),
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

    // ==================== Facing ====================

    /**
     * Determines the composition milestone level based on active module types. Port of GT5
     * MTEForgeOfGods#determineCompositionMilestoneLevel().
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
                Arrays.stream(uniqueModuleCount).sum() + getFormedRingAmount() - 1);

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

    // ==================== Structure Channels ====================

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

    @Override
    public boolean allowsExtendedFacing() {
        return true;
    }

    /** FOG templates advance from the controller toward the star at its physical back. */
    @Override
    public EnumFacing getFrontFacingForStructure() {
        return getFrontFacing().getOpposite();
    }

    // ==================== Data Access ====================

    @Override
    protected boolean allowsAsyncStructureCheck() {
        return false;
    }

    /**
     * Checks whether rotation/flipping is currently locked. Rotation is disabled when the structure is formed and the
     * star renderer is active, since rotating would desync the renderer position from the structure.
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

    // ==================== Renderer Management ====================

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

        logRingState("refresh-from-gui-start", true);
        if (isStructureFormed()) {
            invalidateStructure();
        }
        if (ringReplacementTask != null) {
            pendingStructureRefresh = true;
            logRingState("refresh-from-gui-deferred", true);
            markDirty();
            return;
        }
        refreshStructureNow();
    }

    private void refreshStructureNow() {
        logRingState("refresh-now-before-check", true);
        checkStructurePattern();
        logRingState("refresh-now-after-check", true);
        markDirty();
    }

    /**
     * Creates the render TileEntity at the structure center. Places an invisible block with GodforgeRenderTileEntity at
     * the correct position.
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
        if (te instanceof GodforgeRenderTileEntity renderTE) {
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
        } else {
            data.setClearedRingAmount(task.ringAmount);
        }
        markDirty();
        notifyGodforgeStructureStateChanged();

        if (task.restoreBlocks || task.changedBlocks > 0) {
            GTLog.logger.info(
                    "[FOG] replaceRenderedRings: restore={}, formedRings={}, desiredRings={}, clearedRings={}, " +
                            "taskRings={}, changedBlocks={}",
                    task.restoreBlocks, getFormedRingAmount(), getDesiredRingAmount(), data.getClearedRingAmount(),
                    task.ringAmount, task.changedBlocks);
        }
        logRingState("ring-replacement-finished", true);

        if (task.restoreBlocks && pendingStructureRefresh) {
            pendingStructureRefresh = false;
            refreshStructureNow();
        }
    }

    /**
     * Returns the number of rings that can be replaced with air during rendering. Uses only the ring amount committed
     * by the successful multi-piece structure. The renderer may clear or restore blocks, but it must not become a
     * ring-tier source.
     */
    private int getReplaceableRingAmount() {
        return getFormedRingAmount();
    }

    private int getRestorableRingAmount() {
        int rings = data.getClearedRingAmount();
        if (ringReplacementTask != null && !ringReplacementTask.restoreBlocks) {
            rings = Math.max(rings, ringReplacementTask.ringAmount);
        }
        return rings;
    }

    private int replaceRingBlocks(String[][] shape, Vec3i pieceOffset, int[] centerOffset, boolean restoreBlocks) {
        BlockPos pieceOrigin = OffsetMode.RELATIVE.apply(getPos(),
                new int[] { pieceOffset.getX(), pieceOffset.getY(), pieceOffset.getZ() },
                StructureOrientation.fromController(this));
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
        if (!(te instanceof GodforgeRenderTileEntity renderTE)) return;

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
     * Calculates the world position where the render TE should be placed. The star is at the center of the ring
     * structure, behind the controller.
     */
    @Nullable
    private BlockPos getRenderPos() {
        BlockPos controllerPos = getPos();
        if (controllerPos == null) return null;

        EnumFacing back = getFrontFacing().getOpposite();
        return controllerPos.offset(back, RENDER_OFFSET);
    }

    @Override
    protected MultiblockUIFactory createUIFactory() {
        return new GodforgeUIFactory(this);
    }

    // ==================== GUI ====================

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        NBTTagCompound tag = super.writeToNBT(data);
        this.data.writeToNBT(tag);
        return tag;
    }

    // ==================== NBT ====================

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.data.readFromNBT(data);
        // Recalculate milestone percentages immediately after loading,
        // since they are not persisted in NBT but derived from totals.
        determineMilestoneProgress();
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
                        StructureOrientation.fromController(MetaTileEntityForgeOfGods.this));

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

        private String describe() {
            return "restore=" + restoreBlocks +
                    ", target=" + ringAmount +
                    ", currentRing=" + ringIndex +
                    ", x=" + x +
                    ", y=" + y +
                    ", z=" + z +
                    ", changed=" + changedBlocks;
        }
    }
}
