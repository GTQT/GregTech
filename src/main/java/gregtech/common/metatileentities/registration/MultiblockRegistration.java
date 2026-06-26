package gregtech.common.metatileentities.registration;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.BlockSteamCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.multi.BoilerType;
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
import gregtech.common.metatileentities.multi.electric.FluidDrillType;
import gregtech.common.metatileentities.multi.electric.FusionReactorType;
import gregtech.common.metatileentities.multi.electric.LargeMinerType;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityActiveTransformer;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityAlloyBlastSmelter;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityAssemblyLine;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityCleanroom;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityCrackingUnit;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityDataBank;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityDistillationTower;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityElectricBlastFurnace;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityElectricImplosionCompressor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityFluidDrill;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityFusionReactor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityHPCA;
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
import gregtech.common.metatileentities.multi.electric.MetaTileEntityNetworkSwitch;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityPowerSubstation;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityProcessingArray;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityPyrolyseOven;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityResearchStation;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityVacuumFreezer;
import gregtech.common.metatileentities.multi.electric.generator.LargeTurbineType;
import gregtech.common.metatileentities.multi.electric.generator.MetaTileEntityLargeCombustionEngine;
import gregtech.common.metatileentities.multi.electric.generator.MetaTileEntityLargeTurbine;
import gregtech.common.metatileentities.multi.electric.generator.MetaTileEntitySteamEngine;
import gregtech.common.metatileentities.multi.electric.godforge.MetaTileEntityForgeOfGods;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEExoticModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEMoltenModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEPlasmaModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTESmeltingModule;
import gregtech.common.metatileentities.multi.steam.MetaTileEntitySteamGrinder;
import gregtech.common.metatileentities.multi.steam.MetaTileEntitySteamOven;

import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;

import static gregtech.api.GTValues.EV;
import static gregtech.api.GTValues.IV;
import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.common.metatileentities.MetaTileEntities.*;

public final class MultiblockRegistration {

    private MultiblockRegistration() {}

    private static MetaTileEntityTankValve registerTankValve(int id, String name, Material material,
                                                             ICubeRenderer texture, SoundType soundType) {
        return registerMetaTileEntity(id, new MetaTileEntityTankValve(gregtechId("tank_valve." + name),
                material, texture, soundType));
    }

    private static MetaTileEntityMultiblockTank registerTank(int id, String name, boolean isWood, int capacity,
                                                             IBlockState casingState, MetaTileEntityTankValve valve,
                                                             ICubeRenderer texture, SoundType soundType) {
        MetaTileEntityMultiblockTank.registerTankStructure(name, casingState, valve);
        return registerMetaTileEntity(id, new MetaTileEntityMultiblockTank(gregtechId("tank." + name), name, isWood,
                capacity, casingState, valve, texture, soundType));
    }

    public static void init() {
        registerPrimitiveMultiblocks();
        registerSteamMultiblocks();
        registerGeneratorMultiblocks();
        registerElectricMultiblocks();
        registerGodforge();
    }

    private static void registerPrimitiveMultiblocks() {
        PRIMITIVE_BLAST_FURNACE = registerMetaTileEntity(1000,
                new MetaTileEntityPrimitiveBlastFurnace(gregtechId("primitive_blast_furnace.bronze")));
        PRIMITIVE_BLAST_FURNACE_HATCH = registerMetaTileEntity(1001,
                new MetaTileEntityPrimitiveBlastFurnaceHatch(gregtechId("primitive_blast_furnace_hatch")));

        COKE_OVEN = registerMetaTileEntity(1002, new MetaTileEntityCokeOven(gregtechId("coke_oven")));
        COKE_OVEN_HATCH = registerMetaTileEntity(1003, new MetaTileEntityCokeOvenHatch(gregtechId("coke_oven_hatch")));

        PRIMITIVE_WATER_PUMP = registerMetaTileEntity(1004,
                new MetaTileEntityPrimitiveWaterPump(gregtechId("primitive_water_pump")));
        PUMP_OUTPUT_HATCH = registerMetaTileEntity(1005, new MetaTileEntityPumpHatch(gregtechId("pump_hatch")));

        SAW_MILL = registerMetaTileEntity(1007, new MetaTileEntitySawMill(gregtechId("saw_mill")));

        WOODEN_TANK_VALVE = registerTankValve(1010, "wood", Materials.Wood, Textures.WOOD_WALL, SoundType.WOOD);
        WOODEN_TANK = registerTank(1011, "wood", true, 250 * 1000,
                MetaBlocks.STEAM_CASING.getState(BlockSteamCasing.SteamCasingType.WOOD_WALL), WOODEN_TANK_VALVE,
                Textures.WOOD_WALL, SoundType.WOOD);

        BRONZE_TANK_VALVE = registerTankValve(1012, "bronze", Materials.Bronze, Textures.BRONZE_PLATED_BRICKS,
                SoundType.METAL);
        BRONZE_TANK = registerTank(1013, "bronze", false, 1000 * 1000,
                MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.BRONZE_BRICKS), BRONZE_TANK_VALVE,
                Textures.BRONZE_PLATED_BRICKS, SoundType.METAL);

        STEEL_TANK_VALVE = registerTankValve(1014, "steel", Materials.Steel, Textures.SOLID_STEEL_CASING,
                SoundType.METAL);
        STEEL_TANK = registerTank(1015, "steel", false, 4 * 1000 * 1000,
                MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.STEEL_SOLID), STEEL_TANK_VALVE,
                Textures.SOLID_STEEL_CASING, SoundType.METAL);
    }

    private static void registerSteamMultiblocks() {
        STEAM_OVEN = registerMetaTileEntity(1050, new MetaTileEntitySteamOven(gregtechId("steam_oven")));
        STEAM_GRINDER = registerMetaTileEntity(1051, new MetaTileEntitySteamGrinder(gregtechId("steam_grinder")));
    }

    private static void registerGeneratorMultiblocks() {
        LARGE_BRONZE_BOILER = registerMetaTileEntity(1100,
                new MetaTileEntityLargeBoiler(gregtechId("large_boiler.bronze"), BoilerType.BRONZE));
        LARGE_STEEL_BOILER = registerMetaTileEntity(1101,
                new MetaTileEntityLargeBoiler(gregtechId("large_boiler.steel"), BoilerType.STEEL));
        LARGE_TITANIUM_BOILER = registerMetaTileEntity(1102,
                new MetaTileEntityLargeBoiler(gregtechId("large_boiler.titanium"), BoilerType.TITANIUM));
        LARGE_TUNGSTENSTEEL_BOILER = registerMetaTileEntity(1103,
                new MetaTileEntityLargeBoiler(gregtechId("large_boiler.tungstensteel"), BoilerType.TUNGSTENSTEEL));

        STEAM_ENGINE = registerMetaTileEntity(1104, new MetaTileEntitySteamEngine(gregtechId("steam_engine")));

        LARGE_STEAM_TURBINE = registerMetaTileEntity(1105,
                new MetaTileEntityLargeTurbine(gregtechId("large_turbine.steam"), LargeTurbineType.STEAM));

        LARGE_GAS_TURBINE = registerMetaTileEntity(1106,
                new MetaTileEntityLargeTurbine(gregtechId("large_turbine.gas"), LargeTurbineType.GAS));

        LARGE_PLASMA_TURBINE = registerMetaTileEntity(1107,
                new MetaTileEntityLargeTurbine(gregtechId("large_turbine.plasma"), LargeTurbineType.PLASMA));

        LARGE_COMBUSTION_ENGINE = registerMetaTileEntity(1108,
                new MetaTileEntityLargeCombustionEngine(gregtechId("large_combustion_engine"), EV));
        EXTREME_COMBUSTION_ENGINE = registerMetaTileEntity(1109,
                new MetaTileEntityLargeCombustionEngine(gregtechId("extreme_combustion_engine"), IV));
    }

    private static void registerElectricMultiblocks() {
        // 多方块
        ELECTRIC_BLAST_FURNACE = registerMetaTileEntity(1150,
                new MetaTileEntityElectricBlastFurnace(gregtechId("electric_blast_furnace")));

        MULTI_FURNACE = registerMetaTileEntity(1151, new MetaTileEntityMultiSmelter(gregtechId("multi_furnace")));

        MULTI_ALLOY_FURNACE = registerMetaTileEntity(1152,
                new MetaTileEntityMultiAlloyFurnace(gregtechId("multi_alloy_furnace")));

        PYROLYSE_OVEN = registerMetaTileEntity(1153, new MetaTileEntityPyrolyseOven(gregtechId("pyrolyse_oven")));

        VACUUM_FREEZER = registerMetaTileEntity(1154, new MetaTileEntityVacuumFreezer(gregtechId("vacuum_freezer")));

        IMPLOSION_COMPRESSOR = registerMetaTileEntity(1155,
                new MetaTileEntityImplosionCompressor(gregtechId("implosion_compressor")));

        DISTILLATION_TOWER = registerMetaTileEntity(1156,
                new MetaTileEntityDistillationTower(gregtechId("distillation_tower"), true));

        CRACKER = registerMetaTileEntity(1157, new MetaTileEntityCrackingUnit(gregtechId("cracker")));

        LARGE_CHEMICAL_REACTOR = registerMetaTileEntity(1158,
                new MetaTileEntityLargeChemicalReactor(gregtechId("large_chemical_reactor")));

        ASSEMBLY_LINE = registerMetaTileEntity(1159, new MetaTileEntityAssemblyLine(gregtechId("assembly_line")));

        FUSION_REACTOR[0] = registerMetaTileEntity(1160,
                new MetaTileEntityFusionReactor(gregtechId("fusion_reactor.luv"), FusionReactorType.MK1));
        FUSION_REACTOR[1] = registerMetaTileEntity(1161,
                new MetaTileEntityFusionReactor(gregtechId("fusion_reactor.zpm"), FusionReactorType.MK2));
        FUSION_REACTOR[2] = registerMetaTileEntity(1162,
                new MetaTileEntityFusionReactor(gregtechId("fusion_reactor.uv"), FusionReactorType.MK3));

        // GCYM
        LARGE_MACERATOR = registerMetaTileEntity(1200,
                new MetaTileEntityLargeMacerator(gregtechId("large_macerator")));
        ALLOY_BLAST_SMELTER = registerMetaTileEntity(1201,
                new MetaTileEntityAlloyBlastSmelter(gregtechId("alloy_blast_smelter")));
        LARGE_ARC_FURNACE = registerMetaTileEntity(1202,
                new MetaTileEntityLargeArcFurnace(gregtechId("large_arc_furnace")));
        LARGE_ASSEMBLER = registerMetaTileEntity(1203,
                new MetaTileEntityLargeAssembler(gregtechId("large_assembler")));
        LARGE_AUTOCLAVE = registerMetaTileEntity(1204,
                new MetaTileEntityLargeAutoclave(gregtechId("large_autoclave")));
        LARGE_BENDER = registerMetaTileEntity(1205, new MetaTileEntityLargeBender(gregtechId("large_bender")));
        LARGE_BREWERY = registerMetaTileEntity(1206, new MetaTileEntityLargeBrewery(gregtechId("large_brewer")));
        LARGE_CENTRIFUGE = registerMetaTileEntity(1207,
                new MetaTileEntityLargeCentrifuge(gregtechId("large_centrifuge")));
        LARGE_CHEMICAL_BATH = registerMetaTileEntity(1208,
                new MetaTileEntityLargeChemicalBath(gregtechId("large_chemical_bath")));
        LARGE_EXTRACTOR = registerMetaTileEntity(1209,
                new MetaTileEntityLargeExtractor(gregtechId("large_extractor")));
        LARGE_CUTTER = registerMetaTileEntity(1210, new MetaTileEntityLargeCutter(gregtechId("large_cutter")));
        LARGE_DISTILLERY = registerMetaTileEntity(1211,
                new MetaTileEntityLargeDistillery(gregtechId("large_distillery")));
        LARGE_ELECTROLYZER = registerMetaTileEntity(1212,
                new MetaTileEntityLargeElectrolyzer(gregtechId("large_electrolyzer")));
        LARGE_POLARIZER = registerMetaTileEntity(1213,
                new MetaTileEntityLargePolarizer(gregtechId("large_polarizer")));
        LARGE_EXTRUDER = registerMetaTileEntity(1214, new MetaTileEntityLargeExtruder(gregtechId("large_extruder")));
        LARGE_SOLIDIFIER = registerMetaTileEntity(1215,
                new MetaTileEntityLargeSolidifier(gregtechId("large_solidifier")));
        LARGE_MIXER = registerMetaTileEntity(1216, new MetaTileEntityLargeMixer(gregtechId("large_mixer")));
        LARGE_PACKAGER = registerMetaTileEntity(1217, new MetaTileEntityLargePackager(gregtechId("large_packager")));
        LARGE_ENGRAVER = registerMetaTileEntity(1218, new MetaTileEntityLargeEngraver(gregtechId("large_engraver")));
        LARGE_SIFTER = registerMetaTileEntity(1219, new MetaTileEntityLargeSifter(gregtechId("large_sifter")));
        LARGE_WIREMILL = registerMetaTileEntity(1220, new MetaTileEntityLargeWiremill(gregtechId("large_wiremill")));
        ELECTRIC_IMPLOSION_COMPRESSOR = registerMetaTileEntity(1221,
                new MetaTileEntityElectricImplosionCompressor(gregtechId("electric_implosion_compressor")));
        LARGE_MASS_FABRICATOR = registerMetaTileEntity(1222,
                new MetaTileEntityLargeMassFabricator(gregtechId("large_mass_fabricator")));
        LARGE_REPLICATOR = registerMetaTileEntity(1223,
                new MetaTileEntityLargeReplicator(gregtechId("large_replicator")));
        LARGE_CIRCUIT_ASSEMBLER = registerMetaTileEntity(1224,
                new MetaTileEntityLargeCircuitAssembler(gregtechId("large_circuit_assembler")));
        LARGE_CHEMICAL_COMPLEX = registerMetaTileEntity(1225,
                new MetaTileEntityLargeChemicalComplex(gregtechId("large_chemical_complex")));
        LARGE_PYROLYSER = registerMetaTileEntity(1226,
                new MetaTileEntityLargePyrolyser(gregtechId("large_pyrolyser")));
        LARGE_THERMAL_CENTRIFUGE = registerMetaTileEntity(1227,
                new MetaTileEntityLargeThermalCentrifuge(gregtechId("large_thermal_centrifuge")));
        LARGE_SONICATOR = registerMetaTileEntity(1228,
                new MetaTileEntityLargeSonicator(gregtechId("large_sonicator")));
        LARGE_DESULFURIZER = registerMetaTileEntity(1229,
                new MetaTileEntityLargeDesulfurization(gregtechId("large_desulfurizer")));
        LARGE_POLYMERIZATION = registerMetaTileEntity(1230,
                new MetaTileEntityLargePolymerization(gregtechId("large_polymerization")));
        LARGE_ROCK_BREAKER = registerMetaTileEntity(1231,
                new MetaTileEntityLargeRockBreaker(gregtechId("large_rock_breaker")));
        LARGE_GAS_COLLECTOR = registerMetaTileEntity(1232,
                new MetaTileEntityLargeGasCollector(gregtechId("large_gas_collector")));
        MEGA_BLAST_FURNACE = registerMetaTileEntity(1233,
                new MetaTileEntityMegaBlastFurnace(gregtechId("mega_blast_furnace")));
        MEGA_VACUUM_FREEZER = registerMetaTileEntity(1234,
                new MetaTileEntityMegaVacuumFreezer(gregtechId("mega_vacuum_freezer")));
        MEGA_ALLOY_BLAST_SMELTER = registerMetaTileEntity(1235,
                new MetaTileEntityMegaAlloyBlastSmelter(gregtechId("mega_alloy_blast_smelter")));
        MEGA_CHEMICAL_REACTOR = registerMetaTileEntity(1236,
                new MetaTileEntityMegaChemicalReactor(gregtechId("mega_chemical_reactor")));
        MEGA_CRACKING_UNIT = registerMetaTileEntity(1237,
                new MetaTileEntityMegaCrackingUnit(gregtechId("mega_cracking_unit")));

        // 资源采集
        BASIC_LARGE_MINER = registerMetaTileEntity(1250,
                new MetaTileEntityLargeMiner(gregtechId("large_miner.ev"), LargeMinerType.BASIC));
        LARGE_MINER = registerMetaTileEntity(1251,
                new MetaTileEntityLargeMiner(gregtechId("large_miner.iv"), LargeMinerType.NORMAL));
        ADVANCED_LARGE_MINER = registerMetaTileEntity(1252,
                new MetaTileEntityLargeMiner(gregtechId("large_miner.luv"), LargeMinerType.ADVANCED));

        BASIC_FLUID_DRILLING_RIG = registerMetaTileEntity(1253,
                new MetaTileEntityFluidDrill(gregtechId("fluid_drilling_rig.mv"), FluidDrillType.BASIC));
        FLUID_DRILLING_RIG = registerMetaTileEntity(1254,
                new MetaTileEntityFluidDrill(gregtechId("fluid_drilling_rig.hv"), FluidDrillType.NORMAL));
        ADVANCED_FLUID_DRILLING_RIG = registerMetaTileEntity(1255,
                new MetaTileEntityFluidDrill(gregtechId("fluid_drilling_rig.ev"), FluidDrillType.ADVANCED));

        // 杂项多方块

        PROCESSING_ARRAY = registerMetaTileEntity(1260,
                new MetaTileEntityProcessingArray(gregtechId("processing_array"), 0));

        ADVANCED_PROCESSING_ARRAY = registerMetaTileEntity(1261,
                new MetaTileEntityProcessingArray(gregtechId("advanced_processing_array"), 1));

        CLEANROOM = registerMetaTileEntity(1262, new MetaTileEntityCleanroom(gregtechId("cleanroom")));

        LOGISTICS_MATERIAL_DISTRIBUTOR = registerMetaTileEntity(1263,
                new MetaTileEntityLogisticsMaterialDistributor(gregtechId("logistics_material_distributor")));

        // 算力
        DATA_BANK = registerMetaTileEntity(1265, new MetaTileEntityDataBank(gregtechId("data_bank")));

        RESEARCH_STATION = registerMetaTileEntity(1266,
                new MetaTileEntityResearchStation(gregtechId("research_station")));

        HIGH_PERFORMANCE_COMPUTING_ARRAY = registerMetaTileEntity(1267,
                new MetaTileEntityHPCA(gregtechId("high_performance_computing_array")));

        NETWORK_SWITCH = registerMetaTileEntity(1268, new MetaTileEntityNetworkSwitch(gregtechId("network_switch")));

        // 电网系统

        HUGE_TRANSFORMER = registerMetaTileEntity(1270,
                new MetaTileEntityHugeTransformer(gregtechId("huge_transformer")));

        POWER_SUBSTATION = registerMetaTileEntity(1271,
                new MetaTileEntityPowerSubstation(gregtechId("power_substation")));

        ACTIVE_TRANSFORMER = registerMetaTileEntity(1272,
                new MetaTileEntityActiveTransformer(gregtechId("active_transformer")));

        //BATTERY_ACCUMULATOR = registerMetaTileEntity(1273,
        //        new MetaTileEntityBatteryAccumulator(gregtechId("battery_accumulator")));

    }

    private static void registerGodforge() {
        FORGE_OF_GODS = registerMetaTileEntity(1280,
                new MetaTileEntityForgeOfGods(gregtechId("forge_of_gods")));
        GODFORGE_SMELTING_MODULE = registerMetaTileEntity(1281,
                new MTESmeltingModule(gregtechId("godforge_smelting_module")));
        GODFORGE_MOLTEN_MODULE = registerMetaTileEntity(1282,
                new MTEMoltenModule(gregtechId("godforge_molten_module")));
        GODFORGE_PLASMA_MODULE = registerMetaTileEntity(1283,
                new MTEPlasmaModule(gregtechId("godforge_plasma_module")));
        GODFORGE_EXOTIC_MODULE = registerMetaTileEntity(1284,
                new MTEExoticModule(gregtechId("godforge_exotic_module")));
    }
}
