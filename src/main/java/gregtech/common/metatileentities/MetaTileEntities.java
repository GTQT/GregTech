package gregtech.common.metatileentities;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.block.machines.MachineItemBlock;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.SimpleGeneratorMetaTileEntity;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gregtech.api.util.Mods;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.common.creativetab.GTCreativeTabs;
import gregtech.common.metatileentities.converter.MetaTileEntityConverter;
import gregtech.common.metatileentities.electric.MetaTileEntityAlarm;
import gregtech.common.metatileentities.electric.MetaTileEntityBatteryBuffer;
import gregtech.common.metatileentities.electric.MetaTileEntityBlockBreaker;
import gregtech.common.metatileentities.electric.MetaTileEntityCharger;
import gregtech.common.metatileentities.electric.MetaTileEntityDiode;
import gregtech.common.metatileentities.electric.MetaTileEntityDisposableBatteryBase;
import gregtech.common.metatileentities.electric.MetaTileEntityDustCollector;
import gregtech.common.metatileentities.electric.MetaTileEntityElectricHeater;
import gregtech.common.metatileentities.electric.MetaTileEntityEnergyDistributor;
import gregtech.common.metatileentities.electric.MetaTileEntityFisher;
import gregtech.common.metatileentities.electric.MetaTileEntityGasCollector;
import gregtech.common.metatileentities.electric.MetaTileEntityHull;
import gregtech.common.metatileentities.electric.MetaTileEntityItemCollector;
import gregtech.common.metatileentities.electric.MetaTileEntityMagicEnergyAbsorber;
import gregtech.common.metatileentities.electric.MetaTileEntityMiner;
import gregtech.common.metatileentities.electric.MetaTileEntityProgrammingProvider;
import gregtech.common.metatileentities.electric.MetaTileEntityPump;
import gregtech.common.metatileentities.electric.MetaTileEntityRockBreaker;
import gregtech.common.metatileentities.electric.MetaTileEntitySingleCombustion;
import gregtech.common.metatileentities.electric.MetaTileEntitySingleTurbine;
import gregtech.common.metatileentities.electric.MetaTileEntityTeleporter;
import gregtech.common.metatileentities.electric.MetaTileEntityTransformer;
import gregtech.common.metatileentities.electric.MetaTileEntityWorldAccelerator;
import gregtech.common.metatileentities.multi.MetaTileEntityCokeOven;
import gregtech.common.metatileentities.multi.MetaTileEntityCokeOvenHatch;
import gregtech.common.metatileentities.multi.MetaTileEntityHugeTransformer;
import gregtech.common.metatileentities.multi.MetaTileEntityLargeBoiler;
import gregtech.common.metatileentities.multi.MetaTileEntityLogisticsMaterialDistributor;
import gregtech.common.metatileentities.multi.MetaTileEntityMultiblockTank;
import gregtech.common.metatileentities.multi.MetaTileEntityPrimitiveBlastFurnace;
import gregtech.common.metatileentities.multi.MetaTileEntityPrimitiveBlastFurnaceHatch;
import gregtech.common.metatileentities.multi.MetaTileEntityPrimitiveWaterPump;
import gregtech.common.metatileentities.multi.MetaTileEntityPumpHatch;
import gregtech.common.metatileentities.multi.MetaTileEntitySawMill;
import gregtech.common.metatileentities.multi.MetaTileEntityTankValve;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityActiveTransformer;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityAlloyBlastSmelter;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityAssemblyLine;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityBatteryAccumulator;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityCleanroom;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityCrackingUnit;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityDataBank;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityDistillationTower;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityElectricBlastFurnace;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityElectricImplosionCompressor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityFluidDrill;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityFusionReactor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityGasCentrifuge;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityHPCA;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityHeatExchanger;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityImplosionCompressor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeArcFurnace;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeAssembler;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeAutoclave;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeBender;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeBrewery;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeCentrifuge;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeChemicalBath;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeChemicalComplex;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeChemicalReactor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeCircuitAssembler;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeCutter;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeDesulfurization;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeDistillery;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeElectrolyzer;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeEngraver;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeExtractor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeExtruder;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeGasCollector;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeMacerator;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeMassFabricator;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeMiner;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeMixer;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargePackager;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargePolarizer;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargePolymerization;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargePyrolyser;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeReplicator;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeRockBreaker;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeSifter;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeSolidifier;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeSonicator;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeThermalCentrifuge;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeWiremill;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityMegaAlloyBlastSmelter;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityMegaBlastFurnace;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityMegaChemicalReactor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityMegaCrackingUnit;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityMegaVacuumFreezer;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityMultiAlloyFurnace;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityMultiSmelter;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityNaturalDraftCoolingTower;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityNetworkSwitch;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityPowerSubstation;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityProcessingArray;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityPyrolyseOven;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityQuantumComputer;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityResearchStation;
import gregtech.common.metatileentities.multi.electric.MetaTileEntitySpentFuelPool;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityVacuumFreezer;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityCentralMonitor;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityMonitorScreen;
import gregtech.common.metatileentities.multi.electric.generator.MetaTileEntityLargeCombustionEngine;
import gregtech.common.metatileentities.multi.electric.generator.MetaTileEntityLargeTurbine;
import gregtech.common.metatileentities.multi.electric.generator.MetaTileEntitySteamEngine;
import gregtech.common.metatileentities.multi.electric.generator.nuclearReactor.MetaTileEntityFissionReactor;
import gregtech.common.metatileentities.multi.electric.generator.nuclearReactor.MetaTileEntityNuclearExtend;
import gregtech.common.metatileentities.multi.electric.generator.nuclearReactor.MetaTileEntityNuclearReactor;
import gregtech.common.metatileentities.multi.electric.godforge.MetaTileEntityForgeOfGods;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEExoticModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEMoltenModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEPlasmaModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTESmeltingModule;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityAccelerateHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityAutoMaintenanceHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityCleaningMaintenanceHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityCloudComputationHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityComplexDualHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityComputationHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityControlRodPort;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityCoolantExportHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityCoolantImportHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityCreativeInputBus;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityCreativeInputHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityDataAccessHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityDualHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityEnergyHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityFluidHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityFuelRodExportBus;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityFuelRodImportBus;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityGasHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityHeatHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityHeatSensor;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityHugeDualHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityHugeItemBus;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityISO1CleaningMaintenanceHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityISO2CleaningMaintenanceHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityISO3CleaningMaintenanceHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityLaserHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMachineHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMaintenanceHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityModeratorPort;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMoldItemBus;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMufflerHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiFluidHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityObjectHolder;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityOpticalDataHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityOverclockHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityParallelHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityPassthroughHatchComputation;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityPassthroughHatchFluid;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityPassthroughHatchItem;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityPassthroughHatchLaser;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityReservoirHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityRotorHolder;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntitySterileCleaningMaintenanceHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntitySubstationEnergyHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityThreadHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityTieredHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityWirelessController;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityWirelessEnergyHatch;
import gregtech.common.metatileentities.multi.multiblockpart.hpca.MetaTileEntityHPCAAdvancedComputation;
import gregtech.common.metatileentities.multi.multiblockpart.hpca.MetaTileEntityHPCAAdvancedCooler;
import gregtech.common.metatileentities.multi.multiblockpart.hpca.MetaTileEntityHPCABridge;
import gregtech.common.metatileentities.multi.multiblockpart.hpca.MetaTileEntityHPCAComputation;
import gregtech.common.metatileentities.multi.multiblockpart.hpca.MetaTileEntityHPCACooler;
import gregtech.common.metatileentities.multi.multiblockpart.hpca.MetaTileEntityHPCAEmpty;
import gregtech.common.metatileentities.multi.multiblockpart.qc.MetaTileEntityQCComponentRack;
import gregtech.common.metatileentities.multi.multiblockpart.qc.MetaTileEntityQCCreativeUncertaintyHatch;
import gregtech.common.metatileentities.multi.multiblockpart.qc.MetaTileEntityQCUncertaintyHatch;
import gregtech.common.metatileentities.multi.steam.MetaTileEntitySteamGrinder;
import gregtech.common.metatileentities.multi.steam.MetaTileEntitySteamOven;
import gregtech.common.metatileentities.primitive.MetaTileEntityCharcoalPileIgniter;
import gregtech.common.metatileentities.registration.InfrastructureRegistration;
import gregtech.common.metatileentities.registration.MachineRegistration;
import gregtech.common.metatileentities.registration.MultiblockPartRegistration;
import gregtech.common.metatileentities.registration.MultiblockRegistration;
import gregtech.common.metatileentities.steam.SteamAlloySmelter;
import gregtech.common.metatileentities.steam.SteamCompressor;
import gregtech.common.metatileentities.steam.SteamExtractor;
import gregtech.common.metatileentities.steam.SteamFurnace;
import gregtech.common.metatileentities.steam.SteamHammer;
import gregtech.common.metatileentities.steam.SteamMacerator;
import gregtech.common.metatileentities.steam.SteamMiner;
import gregtech.common.metatileentities.steam.SteamRoaster;
import gregtech.common.metatileentities.steam.SteamRockBreaker;
import gregtech.common.metatileentities.steam.SteamVulcanizingPress;
import gregtech.common.metatileentities.steam.boiler.SteamCoalBoiler;
import gregtech.common.metatileentities.steam.boiler.SteamLavaBoiler;
import gregtech.common.metatileentities.steam.boiler.SteamSolarBoiler;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntityHugeSteamFluidHatch;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntityHugeSteamHatch;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntityHugeSteamItemBus;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntitySteamFluidHatch;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntitySteamHatch;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntitySteamItemBus;
import gregtech.common.metatileentities.storage.MetaTileEntityBuffer;
import gregtech.common.metatileentities.storage.MetaTileEntityCrate;
import gregtech.common.metatileentities.storage.MetaTileEntityCreativeChest;
import gregtech.common.metatileentities.storage.MetaTileEntityCreativeEnergy;
import gregtech.common.metatileentities.storage.MetaTileEntityCreativeTank;
import gregtech.common.metatileentities.storage.MetaTileEntityDrum;
import gregtech.common.metatileentities.storage.MetaTileEntityLockedSafe;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumChest;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumExtender;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumMultiTank;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumProxy;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumStorageController;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumTank;
import gregtech.common.metatileentities.store.MetaTileEntityHugeBuffer;
import gregtech.common.metatileentities.workbench.MetaTileEntityWorkbench;
import gregtech.common.pipelike.fluidpipe.longdistance.MetaTileEntityLDFluidEndpoint;
import gregtech.common.pipelike.itempipe.longdistance.MetaTileEntityLDItemEndpoint;
import gregtech.integration.jei.multiblock.MultiblockInfoCategory;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static gregtech.api.util.GTUtility.gregtechId;

public class MetaTileEntities {

    // HULLS
    public static final MetaTileEntityHull[] HULL = new MetaTileEntityHull[GTValues.V.length];
    public static final MetaTileEntityTransformer[] TRANSFORMER = new MetaTileEntityTransformer[GTValues.V.length -
            1]; // no MAX
    public static final MetaTileEntityTransformer[] HI_AMP_TRANSFORMER = new MetaTileEntityTransformer[
            GTValues.V.length - 1];
    /// no MAX
    public static final MetaTileEntityTransformer[] POWER_TRANSFORMER = new MetaTileEntityTransformer[
            GTValues.V.length - 1]; // no MAX
    public static final MetaTileEntityDiode[] DIODES = new MetaTileEntityDiode[GTValues.V.length];
    public static final MetaTileEntityBatteryBuffer[][] BATTERY_BUFFER = new MetaTileEntityBatteryBuffer[3][GTValues.V.length];
    public static final MetaTileEntityCharger[] CHARGER = new MetaTileEntityCharger[GTValues.V.length];
    // SIMPLE MACHINES SECTION
    public static final SimpleMachineMetaTileEntity[] ELECTRIC_FURNACE = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] MACERATOR = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] ALLOY_SMELTER = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] ARC_FURNACE = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] ASSEMBLER = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] AUTOCLAVE = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] BENDER = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] BREWERY = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] CANNER = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] CENTRIFUGE = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] CHEMICAL_BATH = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] CHEMICAL_REACTOR = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] COMPRESSOR = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] CUTTER = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] DISTILLERY = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] ELECTROLYZER = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] ELECTROMAGNETIC_SEPARATOR = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] EXTRACTOR = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] EXTRUDER = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] FERMENTER = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] FLUID_HEATER = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] FLUID_SOLIDIFIER = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] FORGE_HAMMER = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] FORMING_PRESS = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] LATHE = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] MIXER = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] ORE_WASHER = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] PACKER = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] UNPACKER = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] POLARIZER = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] LASER_ENGRAVER = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] SIFTER = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] THERMAL_CENTRIFUGE = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] WIREMILL = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] CIRCUIT_ASSEMBLER = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] MASS_FABRICATOR = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] REPLICATOR = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] SCANNER = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] GAS_COLLECTOR = new MetaTileEntityGasCollector[GTValues.V.length -
            1];
    public static final MetaTileEntityRockBreaker[] ROCK_BREAKER = new MetaTileEntityRockBreaker[GTValues.V.length - 1];
    public static final MetaTileEntityMiner[] MINER = new MetaTileEntityMiner[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] TOOL_CASTER = new SimpleMachineMetaTileEntity[5];
    public static final SimpleMachineMetaTileEntity[] BATH_CONDENSER = new SimpleMachineMetaTileEntity[5];
    public static final SimpleMachineMetaTileEntity[] POLISHER = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] LAMINATOR = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] POLYMERIZATION_TANK = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] DESULFURIZER = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] BIO_REACTOR = new SimpleMachineMetaTileEntity[GTValues.V.length -
            1];
    public static final SimpleMachineMetaTileEntity[] COMPONENT_ASSEMBLER = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] LOOM = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] ROASTER = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] CHEMICAL_DEHYDRATOR = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] LIGHTNING_PROCESSOR = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] RECYCLER = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];
    public static final SimpleMachineMetaTileEntity[] VULCANIZING_PRESS = new SimpleMachineMetaTileEntity[
            GTValues.V.length - 1];

    // GENERATORS SECTION
    public static final SimpleGeneratorMetaTileEntity[] COMBUSTION_GENERATOR = new SimpleGeneratorMetaTileEntity[5];
    public static final SimpleGeneratorMetaTileEntity[] STEAM_TURBINE = new SimpleGeneratorMetaTileEntity[5];
    public static final SimpleGeneratorMetaTileEntity[] GAS_TURBINE = new SimpleGeneratorMetaTileEntity[5];
    public static final SimpleGeneratorMetaTileEntity[] SEMI_FLUID_GENERATOR = new SimpleGeneratorMetaTileEntity[5];
    public static final SimpleGeneratorMetaTileEntity[] PLASMA_GENERATOR = new SimpleGeneratorMetaTileEntity[5];
    public static final SimpleGeneratorMetaTileEntity[] RTG = new SimpleGeneratorMetaTileEntity[3];

    // MULTIBLOCK PARTS SECTION
    public static final MetaTileEntityItemBus[] ITEM_IMPORT_BUS = new MetaTileEntityItemBus[GTValues.V.length -
            1]; // All tiers but MAX
    public static final MetaTileEntityItemBus[] ITEM_EXPORT_BUS = new MetaTileEntityItemBus[GTValues.V.length - 1];
    public static final MetaTileEntityFluidHatch[] FLUID_IMPORT_HATCH = new MetaTileEntityFluidHatch[GTValues.V.length -
            1];
    public static final MetaTileEntityFluidHatch[] FLUID_EXPORT_HATCH = new MetaTileEntityFluidHatch[GTValues.V.length -
            1];
    public static final MetaTileEntityMultiFluidHatch[] QUADRUPLE_IMPORT_HATCH = new MetaTileEntityMultiFluidHatch[
            GTValues.V.length - 1];
    public static final MetaTileEntityMultiFluidHatch[] NONUPLE_IMPORT_HATCH = new MetaTileEntityMultiFluidHatch[
            GTValues.V.length - 1];
    public static final MetaTileEntityMultiFluidHatch[] SIXTEEN_IMPORT_HATCH = new MetaTileEntityMultiFluidHatch[
            GTValues.V.length - 1];
    public static final MetaTileEntityMultiFluidHatch[] QUADRUPLE_EXPORT_HATCH = new MetaTileEntityMultiFluidHatch[
            GTValues.V.length - 1];
    public static final MetaTileEntityMultiFluidHatch[] NONUPLE_EXPORT_HATCH = new MetaTileEntityMultiFluidHatch[
            GTValues.V.length - 1];
    public static final MetaTileEntityMultiFluidHatch[] SIXTEEN_EXPORT_HATCH = new MetaTileEntityMultiFluidHatch[
            GTValues.V.length - 1];
    public static final MetaTileEntityDualHatch[] DUAL_IMPORT_HATCH = new MetaTileEntityDualHatch[GTValues.V.length -
            1];
    public static final MetaTileEntityDualHatch[] DUAL_EXPORT_HATCH = new MetaTileEntityDualHatch[GTValues.V.length -
            1];
    public static final MetaTileEntityComplexDualHatch[] COMPLEX_DUAL_HATCH = new MetaTileEntityComplexDualHatch[
            GTValues.V.length - 1];
    public static final MetaTileEntityMoldItemBus[] MOLD_ITEM_BUS = new MetaTileEntityMoldItemBus[GTValues.V.length -
            1];

    public static final MetaTileEntityHugeItemBus[][] HUGE_ITEM_IMPORT_BUS = new MetaTileEntityHugeItemBus[GTValues.IV][4];
    public static final MetaTileEntityHugeItemBus[][] HUGE_ITEM_EXPORT_BUS = new MetaTileEntityHugeItemBus[GTValues.IV][4];
    public static final MetaTileEntityHugeDualHatch[][] HUGE_DUAL_IMPORT_HATCH = new MetaTileEntityHugeDualHatch[GTValues.IV][4];
    public static final MetaTileEntityHugeDualHatch[][] HUGE_DUAL_EXPORT_HATCH = new MetaTileEntityHugeDualHatch[GTValues.IV][4];

    public static final MetaTileEntityEnergyHatch[] ENERGY_INPUT_HATCH = new MetaTileEntityEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityEnergyHatch[] ENERGY_INPUT_HATCH_4A = new MetaTileEntityEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityEnergyHatch[] ENERGY_INPUT_HATCH_16A = new MetaTileEntityEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityEnergyHatch[] ENERGY_OUTPUT_HATCH = new MetaTileEntityEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityEnergyHatch[] ENERGY_OUTPUT_HATCH_4A = new MetaTileEntityEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityEnergyHatch[] ENERGY_OUTPUT_HATCH_16A = new MetaTileEntityEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityEnergyHatch[] ENERGY_INPUT_HATCH_64A = new MetaTileEntityEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityEnergyHatch[] ENERGY_INPUT_HATCH_256A = new MetaTileEntityEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityEnergyHatch[] ENERGY_OUTPUT_HATCH_64A = new MetaTileEntityEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityEnergyHatch[] ENERGY_OUTPUT_HATCH_256A = new MetaTileEntityEnergyHatch[GTValues.V.length];

    public static final MetaTileEntitySubstationEnergyHatch[] SUBSTATION_ENERGY_INPUT_HATCH = new MetaTileEntitySubstationEnergyHatch[GTValues.V.length];
    public static final MetaTileEntitySubstationEnergyHatch[] SUBSTATION_ENERGY_OUTPUT_HATCH = new MetaTileEntitySubstationEnergyHatch[GTValues.V.length];
    public static final MetaTileEntitySubstationEnergyHatch[] SUBSTATION_ENERGY_INPUT_HATCH_256A = new MetaTileEntitySubstationEnergyHatch[GTValues.V.length];
    public static final MetaTileEntitySubstationEnergyHatch[] SUBSTATION_ENERGY_OUTPUT_HATCH_256A = new MetaTileEntitySubstationEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessController[] WIRELESS_CONTROLLERS = new MetaTileEntityWirelessController[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_4A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_4A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_16A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_16A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_64A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_64A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_256A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_256A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_1024A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_1024A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_4096A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_4096A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_16384A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_16384A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_65536A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_65536A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_262144A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_262144A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_1048576A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_1048576A = new MetaTileEntityWirelessEnergyHatch[GTValues.V.length];
    public static final MetaTileEntityRotorHolder[] ROTOR_HOLDER = new MetaTileEntityRotorHolder[12]; // HV, EV, IV, LuV, ZPM, UV, UHV, UEV, UIV, UXV, OPV,MAX
    public static final MetaTileEntityMufflerHatch[] MUFFLER_HATCH = new MetaTileEntityMufflerHatch[GTValues.UHV +
            1]; // LV-UHV
    public static final MetaTileEntityFusionReactor[] FUSION_REACTOR = new MetaTileEntityFusionReactor[3];
    public static final MetaTileEntityQuantumChest[] QUANTUM_CHEST = new MetaTileEntityQuantumChest[10];
    public static final MetaTileEntityQuantumTank[] QUANTUM_TANK = new MetaTileEntityQuantumTank[10];
    public static final MetaTileEntityBuffer[] BUFFER = new MetaTileEntityBuffer[5];
    public static final MetaTileEntityEnergyDistributor[] ENERGY_DISTRIBUTOR = new MetaTileEntityEnergyDistributor[GTValues.V.length]; // ULV-MAX
    public static final MetaTileEntityPump[] PUMP = new MetaTileEntityPump[9];
    public static final MetaTileEntityBlockBreaker[] BLOCK_BREAKER = new MetaTileEntityBlockBreaker[4];
    public static final MetaTileEntityMagicEnergyAbsorber[] MAGIC_ENERGY_ABSORBER = new MetaTileEntityMagicEnergyAbsorber[5];
    public static final MetaTileEntityItemCollector[] ITEM_COLLECTOR = new MetaTileEntityItemCollector[5];
    public static final MetaTileEntityFisher[] FISHER = new MetaTileEntityFisher[4];
    public static final MetaTileEntityWorldAccelerator[] WORLD_ACCELERATOR = new MetaTileEntityWorldAccelerator[
            GTValues.V.length - 1];
    public static final MetaTileEntityTeleporter[] TELEPORTER = new MetaTileEntityTeleporter[GTValues.V.length - 1];
    public static final MetaTileEntityGasHatch[] GAS_HATCH = new MetaTileEntityGasHatch[GTValues.UHV + 1];
    public static final MetaTileEntityDustCollector[] DUST_COLLECTOR = new MetaTileEntityDustCollector[
            GTValues.V.length - 1];
    public static final MetaTileEntityHeatHatch[] HEAT_INPUT_HATCH = new MetaTileEntityHeatHatch[10];
    public static final MetaTileEntityHeatHatch[] HEAT_OUTPUT_HATCH = new MetaTileEntityHeatHatch[10];
    public static final MetaTileEntityElectricHeater[] ELECTRIC_HEATER = new MetaTileEntityElectricHeater[10];
    public static final MetaTileEntityQuantumMultiTank[] MULTI_QUANTUM_TANK = new MetaTileEntityQuantumMultiTank[10];
    public static final MetaTileEntityHugeBuffer[] HUGE_BUFFER = new MetaTileEntityHugeBuffer[5];

    public static MetaTileEntityFuelRodImportBus FUEL_ROD_INPUT;
    public static MetaTileEntityFuelRodExportBus FUEL_ROD_OUTPUT;
    public static MetaTileEntityCoolantImportHatch COOLANT_INPUT;
    public static MetaTileEntityCoolantExportHatch COOLANT_OUTPUT;
    public static MetaTileEntityControlRodPort CONTROL_ROD;
    public static MetaTileEntityControlRodPort CONTROL_ROD_MODERATED;
    public static MetaTileEntityModeratorPort MODERATOR_PORT;

    public static MetaTileEntityNuclearExtend NUCLEAR_EXTEND_HATCH;


    // Used for addons if they wish to disable certain tiers of machines
    private static final Map<String, Boolean> MID_TIER = new HashMap<>();
    private static final Map<String, Boolean> HIGH_TIER = new HashMap<>();
    public static MetaTileEntityLaserHatch[] LASER_INPUT_HATCH_256 = new MetaTileEntityLaserHatch[10]; // IV+
    public static MetaTileEntityLaserHatch[] LASER_INPUT_HATCH_1024 = new MetaTileEntityLaserHatch[10]; // IV+
    public static MetaTileEntityLaserHatch[] LASER_INPUT_HATCH_4096 = new MetaTileEntityLaserHatch[10]; // IV+
    public static MetaTileEntityLaserHatch[] LASER_INPUT_HATCH_16384 = new MetaTileEntityLaserHatch[10]; // IV+
    public static MetaTileEntityLaserHatch[] LASER_INPUT_HATCH_65536 = new MetaTileEntityLaserHatch[10]; // IV+
    public static MetaTileEntityLaserHatch[] LASER_INPUT_HATCH_262144 = new MetaTileEntityLaserHatch[10]; // IV+
    public static MetaTileEntityLaserHatch[] LASER_INPUT_HATCH_1048576 = new MetaTileEntityLaserHatch[10]; // IV+
    public static MetaTileEntityLaserHatch[] LASER_OUTPUT_HATCH_256 = new MetaTileEntityLaserHatch[10]; // IV+
    public static MetaTileEntityLaserHatch[] LASER_OUTPUT_HATCH_1024 = new MetaTileEntityLaserHatch[10]; // IV+
    public static MetaTileEntityLaserHatch[] LASER_OUTPUT_HATCH_4096 = new MetaTileEntityLaserHatch[10]; // IV+
    public static MetaTileEntityLaserHatch[] LASER_OUTPUT_HATCH_16384 = new MetaTileEntityLaserHatch[10]; // IV+
    public static MetaTileEntityLaserHatch[] LASER_OUTPUT_HATCH_65536 = new MetaTileEntityLaserHatch[10]; // IV+
    public static MetaTileEntityLaserHatch[] LASER_OUTPUT_HATCH_262144 = new MetaTileEntityLaserHatch[10]; // IV+
    public static MetaTileEntityLaserHatch[] LASER_OUTPUT_HATCH_1048576 = new MetaTileEntityLaserHatch[10]; // IV+
    // spotless:off
    public static MetaTileEntityLockedSafe LOCKED_SAFE;
    public static MetaTileEntityQuantumStorageController QUANTUM_STORAGE_CONTROLLER;
    public static MetaTileEntityQuantumProxy QUANTUM_STORAGE_PROXY;
    public static MetaTileEntityQuantumExtender QUANTUM_STORAGE_EXTENDER;
    public static MetaTileEntityMachineHatch MACHINE_HATCH;
    public static MetaTileEntityPassthroughHatchItem PASSTHROUGH_HATCH_ITEM;
    public static MetaTileEntityPassthroughHatchFluid PASSTHROUGH_HATCH_FLUID;
    public static MetaTileEntityReservoirHatch RESERVOIR_HATCH;
    public static MetaTileEntityDataAccessHatch[] DATA_ACCESS_HATCH = new MetaTileEntityDataAccessHatch[5];
    public static MetaTileEntityOpticalDataHatch OPTICAL_DATA_HATCH_RECEIVER;
    public static MetaTileEntityOpticalDataHatch OPTICAL_DATA_HATCH_TRANSMITTER;

    public static MetaTileEntityPassthroughHatchComputation PASSTHROUGH_HATCH_COMPUTATION;
    public static MetaTileEntityPassthroughHatchLaser PASSTHROUGH_HATCH_LASER;
    public static MetaTileEntityComputationHatch[] COMPUTATION_HATCH_RECEIVER = new MetaTileEntityComputationHatch[GTValues.V.length - 1];
    public static MetaTileEntityComputationHatch[] COMPUTATION_HATCH_TRANSMITTER = new MetaTileEntityComputationHatch[GTValues.V.length - 1];
    public static MetaTileEntityCloudComputationHatch[] CLOUD_COMPUTATION_HATCH_UPLINK = new MetaTileEntityCloudComputationHatch[GTValues.V.length - 1];
    public static MetaTileEntityCloudComputationHatch[] CLOUD_COMPUTATION_HATCH_DOWNLINK = new MetaTileEntityCloudComputationHatch[GTValues.V.length - 1];
    public static MetaTileEntityObjectHolder OBJECT_HOLDER;
    public static MetaTileEntityHPCAEmpty HPCA_EMPTY_COMPONENT;
    public static MetaTileEntityHPCAComputation HPCA_COMPUTATION_COMPONENT;
    public static MetaTileEntityHPCAComputation HPCA_ADVANCED_COMPUTATION_COMPONENT;
    public static MetaTileEntityHPCACooler HPCA_HEAT_SINK_COMPONENT;
    public static MetaTileEntityHPCACooler HPCA_ACTIVE_COOLER_COMPONENT;
    public static MetaTileEntityHPCABridge HPCA_BRIDGE_COMPONENT;
    public static MetaTileEntityHPCAAdvancedComputation HPCA_SUPER_COMPUTATION_COMPONENT;
    public static MetaTileEntityHPCAAdvancedComputation HPCA_ULTIMATE_COMPUTATION_COMPONENT;
    public static MetaTileEntityHPCAAdvancedCooler HPCA_SUPER_COOLER_COMPONENT;
    public static MetaTileEntityHPCAAdvancedCooler HPCA_ULTIMATE_COOLER_COMPONENT;
    // Quantum Computer
    public static MetaTileEntityQuantumComputer QUANTUM_COMPUTER;
    public static MetaTileEntityQCComponentRack QC_RACK;
    public static MetaTileEntityQCUncertaintyHatch QC_UNCERTAINTY_HATCH;
    // Components
    public static MetaTileEntityParallelHatch[] PARALLEL_HATCH = new MetaTileEntityParallelHatch[GTValues.V.length - 1];
    public static MetaTileEntityTieredHatch[] TIERED_HATCH = new MetaTileEntityTieredHatch[GTValues.V.length];
    public static MetaTileEntityThreadHatch[] THREAD_HATCH = new MetaTileEntityThreadHatch[GTValues.V.length - 1];
    public static MetaTileEntityOverclockHatch[] OVERCLOCK_HATCH = new MetaTileEntityOverclockHatch[GTValues.V.length - 1];
    public static MetaTileEntityAccelerateHatch[] ACCELERATE_HATCH = new MetaTileEntityAccelerateHatch[GTValues.V.length - 1];
    public static MetaTileEntityHeatSensor HEAT_SENSOR;
    public static MetaTileEntityCreativeInputBus CREATIVE_INPUT_BUS;
    public static MetaTileEntityCreativeInputHatch CREATIVE_INPUT_HATCH;
    public static MetaTileEntityParallelHatch CREATIVE_PARALLEL_HATCH;
    public static MetaTileEntityOverclockHatch CREATIVE_OVERCLOCK_HATCH;
    public static MetaTileEntityAccelerateHatch CREATIVE_ACCELERATE_HATCH;
    public static MetaTileEntityThreadHatch CREATIVE_THREAD_HATCH;
    public static MetaTileEntityQCCreativeUncertaintyHatch QC_CREATIVE_UNCERTAINTY_HATCH;
    // STEAM AGE SECTION
    public static SteamCoalBoiler STEAM_BOILER_COAL_BRONZE;
    public static SteamCoalBoiler STEAM_BOILER_COAL_STEEL;
    public static SteamSolarBoiler STEAM_BOILER_SOLAR_BRONZE;
    public static SteamSolarBoiler STEAM_BOILER_SOLAR_STEEL;
    public static SteamLavaBoiler STEAM_BOILER_LAVA_BRONZE;
    public static SteamLavaBoiler STEAM_BOILER_LAVA_STEEL;
    public static SteamExtractor STEAM_EXTRACTOR_BRONZE;
    public static SteamExtractor STEAM_EXTRACTOR_STEEL;
    public static SteamMacerator STEAM_MACERATOR_BRONZE;
    public static SteamMacerator STEAM_MACERATOR_STEEL;
    public static SteamCompressor STEAM_COMPRESSOR_BRONZE;
    public static SteamCompressor STEAM_COMPRESSOR_STEEL;
    public static SteamHammer STEAM_HAMMER_BRONZE;
    public static SteamHammer STEAM_HAMMER_STEEL;
    public static SteamFurnace STEAM_FURNACE_BRONZE;
    public static SteamFurnace STEAM_FURNACE_STEEL;
    public static SteamAlloySmelter STEAM_ALLOY_SMELTER_BRONZE;
    public static SteamAlloySmelter STEAM_ALLOY_SMELTER_STEEL;
    public static SteamRockBreaker STEAM_ROCK_BREAKER_BRONZE;
    public static SteamRockBreaker STEAM_ROCK_BREAKER_STEEL;
    public static SteamVulcanizingPress STEAM_VULCANIZING_PRESS_BRONZE;
    public static SteamVulcanizingPress STEAM_VULCANIZING_PRESS_STEEL;
    public static SteamRoaster STEAM_ROASTER_BRONZE;
    public static SteamRoaster STEAM_ROASTER_STEEL;
    public static SteamMiner STEAM_MINER;
    public static MetaTileEntityPumpHatch PUMP_OUTPUT_HATCH;
    public static MetaTileEntityPrimitiveWaterPump PRIMITIVE_WATER_PUMP;
    public static MetaTileEntityCokeOvenHatch COKE_OVEN_HATCH;
    public static MetaTileEntityPrimitiveBlastFurnaceHatch PRIMITIVE_BLAST_FURNACE_HATCH;
    public static MetaTileEntitySteamHatch STEAM_HATCH;
    public static MetaTileEntityHugeSteamHatch HUGE_STEAM_HATCH;
    public static MetaTileEntitySteamItemBus STEAM_EXPORT_BUS;
    public static MetaTileEntitySteamItemBus STEAM_IMPORT_BUS;
    public static MetaTileEntitySteamFluidHatch STEAM_EXPORT_HATCH;
    public static MetaTileEntitySteamFluidHatch STEAM_IMPORT_HATCH;
    public static MetaTileEntityHugeSteamItemBus HUGE_STEAM_EXPORT_BUS;
    public static MetaTileEntityHugeSteamItemBus HUGE_STEAM_IMPORT_BUS;
    public static MetaTileEntityHugeSteamFluidHatch HUGE_STEAM_EXPORT_HATCH;
    public static MetaTileEntityHugeSteamFluidHatch HUGE_STEAM_IMPORT_HATCH;
    public static MetaTileEntityMaintenanceHatch MAINTENANCE_HATCH;
    public static MetaTileEntityMaintenanceHatch CONFIGURABLE_MAINTENANCE_HATCH;
    public static MetaTileEntityAutoMaintenanceHatch AUTO_MAINTENANCE_HATCH;
    public static MetaTileEntityCleaningMaintenanceHatch CLEANING_MAINTENANCE_HATCH;
    public static MetaTileEntitySterileCleaningMaintenanceHatch STERILE_CLEANING_MAINTENANCE_HATCH;
    public static MetaTileEntityISO3CleaningMaintenanceHatch ISO3_CLEANING_MAINTENANCE_HATCH;
    public static MetaTileEntityISO2CleaningMaintenanceHatch ISO2_CLEANING_MAINTENANCE_HATCH;
    public static MetaTileEntityISO1CleaningMaintenanceHatch ISO1_CLEANING_MAINTENANCE_HATCH;

    // MULTIBLOCKS SECTION
    public static MetaTileEntityPrimitiveBlastFurnace PRIMITIVE_BLAST_FURNACE;
    public static MetaTileEntityCokeOven COKE_OVEN;
    public static MetaTileEntityElectricBlastFurnace ELECTRIC_BLAST_FURNACE;
    public static MetaTileEntityVacuumFreezer VACUUM_FREEZER;
    public static MetaTileEntityImplosionCompressor IMPLOSION_COMPRESSOR;
    public static MetaTileEntityPyrolyseOven PYROLYSE_OVEN;
    public static MetaTileEntityDistillationTower DISTILLATION_TOWER;
    public static MetaTileEntityCrackingUnit CRACKER;
    public static MetaTileEntityMultiSmelter MULTI_FURNACE;
    public static MetaTileEntityMultiAlloyFurnace MULTI_ALLOY_FURNACE;
    public static MetaTileEntitySawMill SAW_MILL;
    public static MetaTileEntityLargeCombustionEngine LARGE_COMBUSTION_ENGINE;
    public static MetaTileEntityLargeCombustionEngine EXTREME_COMBUSTION_ENGINE;
    public static MetaTileEntityFissionReactor FISSION_REACTOR;
    public static MetaTileEntityNuclearReactor NUCLEAR_REACTOR;

    public static MetaTileEntityLargeTurbine LARGE_STEAM_TURBINE;
    public static MetaTileEntityLargeTurbine LARGE_GAS_TURBINE;
    public static MetaTileEntityLargeTurbine LARGE_PLASMA_TURBINE;
    public static MetaTileEntityLargeBoiler LARGE_BRONZE_BOILER;
    public static MetaTileEntityLargeBoiler LARGE_STEEL_BOILER;
    public static MetaTileEntityLargeBoiler LARGE_TITANIUM_BOILER;
    public static MetaTileEntityLargeBoiler LARGE_TUNGSTENSTEEL_BOILER;
    public static MetaTileEntityAssemblyLine ASSEMBLY_LINE;
    public static MetaTileEntityLargeChemicalReactor LARGE_CHEMICAL_REACTOR;
    public static MetaTileEntitySteamOven STEAM_OVEN;
    public static MetaTileEntitySteamGrinder STEAM_GRINDER;
    public static MetaTileEntityLargeMiner BASIC_LARGE_MINER;
    public static MetaTileEntityLargeMiner LARGE_MINER;
    public static MetaTileEntityLargeMiner ADVANCED_LARGE_MINER;
    public static MetaTileEntityProcessingArray PROCESSING_ARRAY;
    public static MetaTileEntityProcessingArray ADVANCED_PROCESSING_ARRAY;
    public static MetaTileEntityFluidDrill BASIC_FLUID_DRILLING_RIG;
    public static MetaTileEntityFluidDrill FLUID_DRILLING_RIG;
    public static MetaTileEntityFluidDrill ADVANCED_FLUID_DRILLING_RIG;
    public static MetaTileEntityCleanroom CLEANROOM;
    public static MetaTileEntityCharcoalPileIgniter CHARCOAL_PILE_IGNITER;
    public static MetaTileEntityDataBank DATA_BANK;
    public static MetaTileEntityResearchStation RESEARCH_STATION;
    public static MetaTileEntityHPCA HIGH_PERFORMANCE_COMPUTING_ARRAY;
    public static MetaTileEntityNetworkSwitch NETWORK_SWITCH;
    public static MetaTileEntityPowerSubstation POWER_SUBSTATION;
    public static MetaTileEntityActiveTransformer ACTIVE_TRANSFORMER;
    public static MetaTileEntityBatteryAccumulator BATTERY_ACCUMULATOR;
    public static MetaTileEntityHeatExchanger HEAT_EXCHANGER;

    public static MetaTileEntitySpentFuelPool SPENT_FUEL_POOL;
    public static MetaTileEntityGasCentrifuge GAS_CENTRIFUGE;
    public static MetaTileEntityNaturalDraftCoolingTower NATURAL_DRAFT_COOLING_TOWER;

    public static MetaTileEntityLargeMacerator LARGE_MACERATOR;
    public static MetaTileEntityAlloyBlastSmelter ALLOY_BLAST_SMELTER;
    public static MetaTileEntityLargeArcFurnace LARGE_ARC_FURNACE;
    public static MetaTileEntityLargeAssembler LARGE_ASSEMBLER;
    public static MetaTileEntityLargeAutoclave LARGE_AUTOCLAVE;
    public static MetaTileEntityLargeBender LARGE_BENDER;
    public static MetaTileEntityLargeBrewery LARGE_BREWERY;
    public static MetaTileEntityLargeCentrifuge LARGE_CENTRIFUGE;
    public static MetaTileEntityLargeChemicalBath LARGE_CHEMICAL_BATH;
    public static MetaTileEntityLargeExtractor LARGE_EXTRACTOR;
    public static MetaTileEntityLargeCutter LARGE_CUTTER;
    public static MetaTileEntityLargeDistillery LARGE_DISTILLERY;
    public static MetaTileEntityLargeElectrolyzer LARGE_ELECTROLYZER;
    public static MetaTileEntityLargePolarizer LARGE_POLARIZER;
    public static MetaTileEntityLargeExtruder LARGE_EXTRUDER;
    public static MetaTileEntityLargeSolidifier LARGE_SOLIDIFIER;
    public static MetaTileEntityLargeMixer LARGE_MIXER;
    public static MetaTileEntityLargePackager LARGE_PACKAGER;
    public static MetaTileEntityLargeEngraver LARGE_ENGRAVER;
    public static MetaTileEntityLargeSifter LARGE_SIFTER;
    public static MetaTileEntityLargeWiremill LARGE_WIREMILL;
    public static MetaTileEntityElectricImplosionCompressor ELECTRIC_IMPLOSION_COMPRESSOR;
    public static MetaTileEntityLargeMassFabricator LARGE_MASS_FABRICATOR;
    public static MetaTileEntityLargeReplicator LARGE_REPLICATOR;
    public static MetaTileEntityLargeCircuitAssembler LARGE_CIRCUIT_ASSEMBLER;
    public static MetaTileEntityLargeChemicalComplex LARGE_CHEMICAL_COMPLEX;
    public static MetaTileEntityLargePyrolyser LARGE_PYROLYSER;
    public static MetaTileEntityLargeThermalCentrifuge LARGE_THERMAL_CENTRIFUGE;
    public static MetaTileEntityLargeSonicator LARGE_SONICATOR;
    public static MetaTileEntityLargeDesulfurization LARGE_DESULFURIZER;
    public static MetaTileEntityLargePolymerization LARGE_POLYMERIZATION;
    public static MetaTileEntityLargeRockBreaker LARGE_ROCK_BREAKER;
    public static MetaTileEntityLargeGasCollector LARGE_GAS_COLLECTOR;
    public static MetaTileEntityMegaBlastFurnace MEGA_BLAST_FURNACE;
    public static MetaTileEntityMegaVacuumFreezer MEGA_VACUUM_FREEZER;
    public static MetaTileEntityMegaAlloyBlastSmelter MEGA_ALLOY_BLAST_SMELTER;
    public static MetaTileEntityMegaChemicalReactor MEGA_CHEMICAL_REACTOR;
    public static MetaTileEntityMegaCrackingUnit MEGA_CRACKING_UNIT;
    public static MetaTileEntitySteamEngine STEAM_ENGINE;
    public static MetaTileEntityLogisticsMaterialDistributor LOGISTICS_MATERIAL_DISTRIBUTOR;
    public static MetaTileEntityHugeTransformer HUGE_TRANSFORMER;
    // FORGE OF THE GODS
    public static MetaTileEntityForgeOfGods FORGE_OF_GODS;
    public static MTESmeltingModule GODFORGE_SMELTING_MODULE;
    public static MTEMoltenModule GODFORGE_MOLTEN_MODULE;
    public static MTEPlasmaModule GODFORGE_PLASMA_MODULE;
    public static MTEExoticModule GODFORGE_EXOTIC_MODULE;
    // DISPOSABLE BATTERY BLOCKS (A-series), IDs 2105+
    public static MetaTileEntityDisposableBatteryBase ZINC_MANGANESE_CELL;
    public static MetaTileEntityDisposableBatteryBase LITHIUM_MANGANESE_CELL;
    public static MetaTileEntityDisposableBatteryBase NICKEL_CADMIUM_CELL;
    public static MetaTileEntityDisposableBatteryBase LEAD_ACID_BATTERY;
    public static MetaTileEntityDisposableBatteryBase VANADIUM_FLOW_CELL;
    public static MetaTileEntityDisposableBatteryBase LFP_BATTERY;
    public static MetaTileEntityDisposableBatteryBase LCO_BATTERY;
    public static MetaTileEntityDisposableBatteryBase NMC_BATTERY;
    // STORAGE SECTION
    public static MetaTileEntityTankValve WOODEN_TANK_VALVE;
    public static MetaTileEntityTankValve BRONZE_TANK_VALVE;
    public static MetaTileEntityTankValve STEEL_TANK_VALVE;
    public static MetaTileEntityMultiblockTank WOODEN_TANK;
    public static MetaTileEntityMultiblockTank BRONZE_TANK;
    public static MetaTileEntityMultiblockTank STEEL_TANK;
    public static MetaTileEntityDrum WOODEN_DRUM;
    public static MetaTileEntityDrum BRONZE_DRUM;
    public static MetaTileEntityDrum ALUMINIUM_DRUM;
    public static MetaTileEntityDrum STEEL_DRUM;
    public static MetaTileEntityDrum CHROME_DRUM;
    public static MetaTileEntityDrum STAINLESS_STEEL_DRUM;
    public static MetaTileEntityDrum TITANIUM_DRUM;
    public static MetaTileEntityDrum TUNGSTEN_DRUM;
    public static MetaTileEntityDrum TUNGSTENSTEEL_DRUM;
    public static MetaTileEntityDrum IRIDIUM_DRUM;
    public static MetaTileEntityDrum GOLD_DRUM;
    public static MetaTileEntityDrum LEAD_DRUM;
    public static MetaTileEntityDrum IRON_DRUM;
    public static MetaTileEntityDrum COPPER_DRUM;
    public static MetaTileEntityDrum RHODIUM_PLATED_PALLADIUM_DRUM;
    public static MetaTileEntityDrum NAQUADAH_ALLOY_DRUM;
    public static MetaTileEntityDrum DARMSTADTIUM_DRUM;
    public static MetaTileEntityDrum NEUTRONIUM_DRUM;
    public static MetaTileEntityCrate WOODEN_CRATE;
    public static MetaTileEntityCrate COPPER_CRATE;
    public static MetaTileEntityCrate IRON_CRATE;
    public static MetaTileEntityCrate BRONZE_CRATE;
    public static MetaTileEntityCrate SILVER_CRATE;
    public static MetaTileEntityCrate STEEL_CRATE;
    public static MetaTileEntityCrate GOLD_CRATE;
    public static MetaTileEntityCrate ALUMINIUM_CRATE;
    public static MetaTileEntityCrate DIAMOND_CRATE;
    public static MetaTileEntityCrate STAINLESS_STEEL_CRATE;
    public static MetaTileEntityCrate TITANIUM_CRATE;
    public static MetaTileEntityCrate TUNGSTENSTEEL_CRATE;
    public static MetaTileEntityCrate RHODIUM_PLATED_PALLADIUM_CRATE;
    public static MetaTileEntityCrate NAQUADAH_ALLOY_CRATE;
    public static MetaTileEntityCrate DARMSTADTIUM_CRATE;
    public static MetaTileEntityCrate NEUTRONIUM_CRATE;
    // MISC MACHINES SECTION
    public static MetaTileEntityWorkbench WORKBENCH;
    public static MetaTileEntityCreativeEnergy CREATIVE_ENERGY;
    public static MetaTileEntityCreativeTank CREATIVE_TANK;
    public static MetaTileEntityCreativeChest CREATIVE_CHEST;
    public static MetaTileEntityClipboard CLIPBOARD_TILE;
    public static MetaTileEntityMonitorScreen MONITOR_SCREEN;
    public static MetaTileEntityCentralMonitor CENTRAL_MONITOR;
    public static MetaTileEntityLDItemEndpoint LONG_DIST_ITEM_ENDPOINT;
    public static MetaTileEntityLDFluidEndpoint LONG_DIST_FLUID_ENDPOINT;
    public static MetaTileEntityAlarm ALARM;
    public static MetaTileEntityProgrammingProvider PROGRAMMING_PROVIDER;


    public static final SimpleMachineMetaTileEntity[] DECAY_CHAMBER = new SimpleMachineMetaTileEntity[GTValues.V.length - 1];



    // todo
    public static MetaTileEntityConverter[][] ENERGY_CONVERTER = new MetaTileEntityConverter[4][GTValues.V.length];

    public static void init() {
        GTLog.logger.info("Registering MetaTileEntities");

        // Register the multiblock creative tab so multiblock controllers appear in their own tab
        MachineItemBlock.addCreativeTab(GTCreativeTabs.TAB_GREGTECH_MULTIBLOCKS);
        MachineItemBlock.addCreativeTab(GTCreativeTabs.TAB_GREGTECH_MULTIBLOCK_PARTS);

        // 单方块机器 1-1000
        MachineRegistration.init();
        // 多方块机器 1000-2000
        MultiblockRegistration.init();
        // 多方块仓口 2000-4000
        MultiblockPartRegistration.init();
        // 杂项 4000-
        InfrastructureRegistration.init();
    }

    public static void registerSimpleMetaTileEntity(SimpleMachineMetaTileEntity[] machines,
                                                    int startId,
                                                    String name,
                                                    RecipeMap<?> map,
                                                    ICubeRenderer texture,
                                                    boolean hasFrontFacing,
                                                    Function<Integer, Integer> tankScalingFunction) {
        registerSimpleMetaTileEntity(machines, startId, name, map, texture, hasFrontFacing, GTUtility::gregtechId,
                tankScalingFunction);
    }

    public static void registerSimpleMetaTileEntity(SimpleMachineMetaTileEntity[] machines,
                                                    int startId,
                                                    String name,
                                                    RecipeMap<?> map,
                                                    ICubeRenderer texture,
                                                    boolean hasFrontFacing) {
        registerSimpleMetaTileEntity(machines, startId, name, map, texture, hasFrontFacing,
                GTUtility.defaultTankSizeFunction);
    }

    public static void registerSimpleMetaTileEntity(SimpleMachineMetaTileEntity[] machines,
                                                    int startId,
                                                    String name,
                                                    RecipeMap<?> map,
                                                    ICubeRenderer texture,
                                                    boolean hasFrontFacing,
                                                    Function<String, ResourceLocation> resourceId,
                                                    Function<Integer, Integer> tankScalingFunction) {
        registerMetaTileEntities(machines, startId, name,
                (tier, voltageName) -> new SimpleMachineMetaTileEntity(
                        resourceId.apply(String.format("%s.%s", name, voltageName)), map, texture, tier, hasFrontFacing,
                        tankScalingFunction));
    }

    /**
     * 注册单方块发电机的基础方法
     *
     * @param generators          发电机数组
     * @param startId             起始ID
     * @param name                发电机名称
     * @param recipeMap           配方地图
     * @param texture             纹理
     * @param generatorClass      发电机类（Combustion或Turbine）
     * @param tankScalingFunction 流体槽大小函数
     * @param efficiencyFunction  效率函数
     */
    public static void registerSimpleGeneratorMetaTileEntity(
            MetaTileEntity[] generators,
            int startId,
            String name,
            RecipeMap<?> recipeMap,
            ICubeRenderer texture,
            Class<?> generatorClass,
            Function<Integer, Integer> tankScalingFunction,
            Function<Integer, Double> efficiencyFunction) {

        registerGeneratorMetaTileEntities(generators, startId, name,
                (tier, voltageName) -> {
                    double efficiency = efficiencyFunction.apply(tier);
                    if (generatorClass == MetaTileEntitySingleCombustion.class) {
                        return new MetaTileEntitySingleCombustion(
                                gregtechId(String.format("%s.%s", name, voltageName)),
                                recipeMap, texture, tier, tankScalingFunction, efficiency);
                    } else if (generatorClass == MetaTileEntitySingleTurbine.class) {
                        return new MetaTileEntitySingleTurbine(
                                gregtechId(String.format("%s.%s", name, voltageName)),
                                recipeMap, texture, tier, tankScalingFunction, efficiency);
                    } else {
                        throw new IllegalArgumentException("Unknown generator class: " + generatorClass);
                    }
                });
    }

    /**
     * 注册发电机的核心方法
     */
    public static void registerGeneratorMetaTileEntities(
            MetaTileEntity[] generators,
            int startId,
            String name,
            BiFunction<Integer, String, MetaTileEntity> mteCreator) {

        for (int i = 0; i < generators.length; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            generators[i] = registerMetaTileEntity(startId + i, mteCreator.apply(i + 1, voltageName));
        }
    }

    /**
     * @param mteCreator Takes tier and voltage name for the machine, and outputs MTE to register
     */
    public static void registerMetaTileEntities(
            MetaTileEntity[] machines,
            int startId,
            String name,
            BiFunction<Integer, String, MetaTileEntity> mteCreator) {
        for (int i = 0; i < machines.length - 1; i++) {
            if (i > 4 && !getMidTier(name)) continue;
            if (i > 7 && !getHighTier(name)) break;

            String voltageName = GTValues.VN[i + 1].toLowerCase();
            machines[i + 1] = registerMetaTileEntity(startId + i, mteCreator.apply(i + 1, voltageName));
        }
    }

    /**
     * Register a MetaTileEntity
     *
     * @param id  the numeric ID to use as item metadata
     * @param mte the MTE to register
     * @param <T> the MTE class
     * @return the MTE
     */
    public static <T extends MetaTileEntity> @NotNull T registerMetaTileEntity(int id, @NotNull T mte) {
        if (mte instanceof IMultiblockAbilityPart<?> abilityPart) {
            for (var ability : abilityPart.getAbilities())
                MultiblockAbility.registerMultiblockAbility(ability, mte);
        }

        if (Mods.JustEnoughItems.isModLoaded() && mte instanceof MultiblockControllerBase controller &&
                controller.shouldShowInJei()) {
            registerMultiblockForJei(controller);
        }

        mte.getRegistry().register(id, mte.metaTileEntityId, mte);
        return mte;
    }

    private static void registerMultiblockForJei(MultiblockControllerBase controller) {
        MultiblockInfoCategory.registerMultiblock(controller);
    }

    @SuppressWarnings("unused")
    public static void setMidTier(String key, boolean enabled) {
        MID_TIER.put(key, enabled);
    }

    @SuppressWarnings("unused")
    public static void setHighTier(String key, boolean enabled) {
        HIGH_TIER.put(key, enabled);
        if (!GregTechAPI.isHighTier()) {
            throw new IllegalArgumentException(
                    "Cannot set High-Tier machine without high tier being enabled in GregTechAPI.");
        }
    }

    public static boolean getMidTier(String key) {
        return MID_TIER.getOrDefault(key, true);
    }

    public static boolean getHighTier(String key) {
        return HIGH_TIER.getOrDefault(key, GregTechAPI.isHighTier());
    }
}
