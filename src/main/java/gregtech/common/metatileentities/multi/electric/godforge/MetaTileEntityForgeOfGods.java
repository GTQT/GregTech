package gregtech.common.metatileentities.multi.electric.godforge;

import gregtech.api.util.GTLog;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.LazyTemplate;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.OffsetMode;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
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

import net.minecraft.block.state.IBlockState;
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

import static gregtech.api.util.RelativeDirection.FRONT;
import static gregtech.api.util.RelativeDirection.RIGHT;
import static gregtech.api.util.RelativeDirection.UP;

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

    private final ForgeOfGodsData data = new ForgeOfGodsData();
    private final List<MTEBaseModule> moduleHatches = new ArrayList<>();
    private long ticker = 0;
    private int lastKnownRingAmount = 1;

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
        String[][] beamShaft = ForgeOfGodsStructureString.BEAM_SHAFT;
        String[][] firstRing = data.isRenderActive() ?
                ForgeOfGodsStructureString.FIRST_RING_AIR :
                ForgeOfGodsStructureString.FIRST_RING;

        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : beamShaft) {
            builder.aisle(layer);
        }
        for (String[] layer : firstRing) {
            builder.aisle(layer);
        }

        builder.where('S', selfPredicate());
        applySharedPredicates(builder);
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

    // External center offsets [x, y, z, minZ, maxZ] for sub-piece templates without selfPredicate
    private static final int[] FIRST_RING_CENTER = { 63, 14, 0, 0, 0 };
    private static final int[] SECOND_RING_CENTER = { 55, 11, 0, 0, 0 };
    private static final int[] THIRD_RING_CENTER = { 47, 13, 0, 0, 0 };

    // Static template cache using LazyTemplate (thread-safe, zero-lock after init)
    private static final LazyTemplate BEAM_SHAFT_TEMPLATE = LazyTemplate.of(
            MetaTileEntityForgeOfGods::buildBeamShaftTemplate);
    private static final LazyTemplate FIRST_RING_TEMPLATE = LazyTemplate.of(
            MetaTileEntityForgeOfGods::buildFirstRingTemplate);
    private static final LazyTemplate SECOND_RING_TEMPLATE = LazyTemplate.of(
            MetaTileEntityForgeOfGods::buildSecondRingTemplate);
    private static final LazyTemplate THIRD_RING_TEMPLATE = LazyTemplate.of(
            MetaTileEntityForgeOfGods::buildThirdRingTemplate);

    @Nullable
    @Override
    protected MultiPiecePattern createMultiPiecePattern() {
        return MultiPiecePattern.builder()
                .piece("beam_shaft", BEAM_SHAFT_TEMPLATE.get(), BEAM_SHAFT_OFFSET, OffsetMode.RELATIVE)
                .piece("first_ring", FIRST_RING_TEMPLATE.get(), FIRST_RING_OFFSET, OffsetMode.RELATIVE)
                .conditionalPiece("second_ring", SECOND_RING_TEMPLATE.get(), SECOND_RING_OFFSET,
                        OffsetMode.RELATIVE, () -> data.isUpgradeActive(ForgeOfGodsUpgrade.CD))
                .conditionalPiece("third_ring", THIRD_RING_TEMPLATE.get(), THIRD_RING_OFFSET,
                        OffsetMode.RELATIVE, () -> data.isUpgradeActive(ForgeOfGodsUpgrade.END))
                .build();
    }

    private static BlockPatternTemplate buildBeamShaftTemplate() {
        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : ForgeOfGodsStructureString.BEAM_SHAFT) {
            builder.aisle(layer);
        }
        applyAllPredicates(builder, true);
        return builder.buildTemplate();
    }

    private static BlockPatternTemplate buildFirstRingTemplate() {
        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : ForgeOfGodsStructureString.FIRST_RING) {
            builder.aisle(layer);
        }
        applyAllPredicates(builder, false);
        return builder.buildTemplate(FIRST_RING_CENTER);
    }

    private static BlockPatternTemplate buildSecondRingTemplate() {
        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : ForgeOfGodsStructureString.SECOND_RING) {
            builder.aisle(layer);
        }
        applyAllPredicates(builder, false);
        return builder.buildTemplate(SECOND_RING_CENTER);
    }

    private static BlockPatternTemplate buildThirdRingTemplate() {
        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : ForgeOfGodsStructureString.THIRD_RING) {
            builder.aisle(layer);
        }
        applyAllPredicates(builder, false);
        return builder.buildTemplate(THIRD_RING_CENTER);
    }

    /**
     * Apply all known character -> predicate mappings to a builder.
     * Includes all characters used across all pieces.
     *
     * @param builder          the factory block pattern builder
     * @param includeController true to include 'S' -> selfPredicate() (only for beam_shaft)
     */
    private static void applyAllPredicates(FactoryBlockPattern builder, boolean includeController) {
        if (includeController) {
            builder.where('S', new TraceabilityPredicate(
                    blockWorldState -> true,
                    () -> new gregtech.api.util.BlockInfo[] {
                            new gregtech.api.util.BlockInfo(
                                    getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING))
                    }).setCenter());
        }
        applySharedPredicates(builder);
    }

    // ==================== Block State Helpers ====================

    private static void applySharedPredicates(FactoryBlockPattern builder) {
        builder.where('A', hatches())
                .where('B', states(getCasingState(BlockGodforgeCasing.CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING)))
                .where('C', states(getCasingState(BlockGodforgeCasing.CasingType.CELESTIAL_MATTER_GUIDANCE_CASING)))
                .where('D', states(getCasingState(BlockGodforgeCasing.CasingType.BOUNDLESS_GRAVITATIONALLY_SEVERED_STRUCTURE_CASING)))
                .where('E', states(getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING)))
                .where('F', states(getCasingState(BlockGodforgeCasing.CasingType.STELLAR_ENERGY_SIPHON_CASING)))
                .where('G', states(getCasingState(BlockGodforgeCasing.CasingType.REMOTE_GRAVITON_FLOW_MODULATOR)))
                .where('H', states(getGlassState()))
                .where('J', godforgeModules()
                        .or(states(getCasingState(BlockGodforgeCasing.CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING))))
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
                .or(states(getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING)));
    }

    private static TraceabilityPredicate godforgeModules() {
        return metaTileEntities(
                MetaTileEntities.GODFORGE_SMELTING_MODULE,
                MetaTileEntities.GODFORGE_MOLTEN_MODULE,
                MetaTileEntities.GODFORGE_PLASMA_MODULE,
                MetaTileEntities.GODFORGE_EXOTIC_MODULE);
    }

    // ==================== Structure Lifecycle ====================

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        updateRingAmount();
        discoverModules();

        // Restore renderer if battery was active before structure broke
        if (data.getInternalBattery() != 0 && !data.isRenderActive() && !data.isRendererDisabled()) {
            createRenderer();
        }
    }

    @Override
    public void invalidateStructure() {
        disconnectAllModules();
        moduleHatches.clear();
        if (data.isRenderActive()) {
            destroyRenderer();
        }
        super.invalidateStructure();
    }

    /**
     * Scans all multiblock parts to discover connected godforge modules.
     * Modules are sub-multiblocks attached to the beam_shaft at 'J' positions.
     */
    private void discoverModules() {
        moduleHatches.clear();
        for (IMultiblockPart part : getMultiblockParts()) {
            if (part instanceof MTEBaseModule) {
                moduleHatches.add((MTEBaseModule) part);
            }
        }
    }

    /**
     * Disconnects all currently connected modules.
     */
    private void disconnectAllModules() {
        for (MTEBaseModule module : moduleHatches) {
            module.disconnect();
        }
    }

    private void updateRingAmount() {
        int rings = 1;
        if (data.isUpgradeActive(ForgeOfGodsUpgrade.CD)) {
            rings = 2;
        }
        if (data.isUpgradeActive(ForgeOfGodsUpgrade.END)) {
            rings = 3;
        }
        data.setRingAmount(rings);
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
    protected void updateFormedValid() {
        if (getWorld().isRemote) return;

        ticker++;

        if (ticker % TICK_INTERVAL != 0) return;

        // Calculate max allowed module count based on ring upgrades
        int maxModuleCount = 8;
        if (data.isUpgradeActive(ForgeOfGodsUpgrade.CD)) {
            maxModuleCount += 4;
        }
        if (data.isUpgradeActive(ForgeOfGodsUpgrade.END)) {
            maxModuleCount += 4;
        }

        // === Fuel absorption and battery startup ===
        absorbFuelOrShards();

        // === Fuel consumption (drain fluid + maintain battery) ===
        if (data.getInternalBattery() != 0) {
            drainFuel();
        }

        ensureRendererState();

        // === Milestone calculations ===
        determineCompositionMilestoneLevel();
        determineMilestoneProgress();
        checkInversionStatus();
        determineGravitonShardAmount();

        // === Graviton shard ejection (if END upgrade active and ejection enabled) ===
        if (data.isUpgradeActive(ForgeOfGodsUpgrade.END) && data.isGravitonShardEjection()) {
            ejectGravitonShards();
        }

        // === Module parameter calculation and connection management ===
        if (!moduleHatches.isEmpty() && data.getInternalBattery() > 0
                && moduleHatches.size() <= maxModuleCount) {
            for (MTEBaseModule module : moduleHatches) {
                if (GodforgeMath.allowModuleConnection(module, data)) {
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
                        module.disconnect();
                    }
                } else {
                    module.disconnect();
                }
            }
        } else if (moduleHatches.size() > maxModuleCount) {
            disconnectAllModules();
        }

        // === Ring unlock/respec detection → update renderer and structure ===
        if (data.getRingAmount() != lastKnownRingAmount) {
            lastKnownRingAmount = data.getRingAmount();
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

    private void ensureRendererState() {
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
        return false;
    }

    @Override
    public boolean isValidFrontFacing(EnumFacing facing) {
        return facing != null && (!hasFrontFacing() || getFrontFacing() != facing);
    }

    // ==================== Data Access ====================

    public ForgeOfGodsData getData() {
        return data;
    }

    public List<MTEBaseModule> getModuleHatches() {
        return moduleHatches;
    }

    public void refreshStructureFromGui() {
        if (getWorld() == null || getWorld().isRemote) return;

        if (isStructureFormed()) {
            invalidateStructure();
        }
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

        BlockPos renderPos = getRenderPos();
        if (renderPos == null) {
            GTLog.logger.warn("[FOG] createRenderer: getRenderPos() returned null");
            return;
        }

        GTLog.logger.info("[FOG] createRenderer: attempting setBlockState at {}, chunk loaded: {}",
                renderPos, getWorld().isBlockLoaded(renderPos));

        if (!getWorld().setBlockState(renderPos, MetaBlocks.GODFORGE_RENDER.getDefaultState(), 3)) {
            GTLog.logger.warn("[FOG] createRenderer: setBlockState FAILED at {}", renderPos);
            data.setRenderActive(false);
            return;
        }
        TileEntity te = getWorld().getTileEntity(renderPos);
        if (te instanceof GodforgeRenderTileEntity) {
            GodforgeRenderTileEntity renderTE = (GodforgeRenderTileEntity) te;
            renderTE.setRenderRotation(getFrontFacing());
            data.setRenderActive(true);
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

        IBlockState state = getWorld().getBlockState(renderPos);
        if (state.getBlock() == MetaBlocks.GODFORGE_RENDER) {
            getWorld().setBlockToAir(renderPos);
        }

        data.setRenderActive(false);
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
        renderTE.setRingCount(data.getRingAmount());
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
        reinitializeStructurePattern();
    }
}
