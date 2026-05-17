package gregtech.common.metatileentities.registration;

import gregtech.api.GTValues;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.material.Materials;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockTurbineCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.multi.BoilerType;
import gregtech.common.metatileentities.multi.MetaTileEntityCokeOven;
import gregtech.common.metatileentities.multi.MetaTileEntityCokeOvenHatch;
import gregtech.common.metatileentities.multi.MetaTileEntityLargeBoiler;
import gregtech.common.metatileentities.multi.MetaTileEntityMultiblockTank;
import gregtech.common.metatileentities.multi.MetaTileEntityPrimitiveBlastFurnace;
import gregtech.common.metatileentities.multi.MetaTileEntityPrimitiveBlastFurnaceHatch;
import gregtech.common.metatileentities.multi.MetaTileEntityPrimitiveWaterPump;
import gregtech.common.metatileentities.multi.MetaTileEntityPumpHatch;
import gregtech.common.metatileentities.multi.MetaTileEntitySawMill;
import gregtech.common.metatileentities.multi.MetaTileEntityTankValve;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityActiveTransformer;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityAssemblyLine;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityCleanroom;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityCrackingUnit;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityDataBank;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityDistillationTower;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityElectricBlastFurnace;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityFluidDrill;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityFusionReactor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityHPCA;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityImplosionCompressor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeChemicalReactor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeMiner;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityMultiAlloyFurnace;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityMultiSmelter;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityNetworkSwitch;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityPowerSubstation;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityProcessingArray;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityPyrolyseOven;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityResearchStation;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityVacuumFreezer;
import gregtech.common.metatileentities.multi.electric.generator.MetaTileEntityLargeCombustionEngine;
import gregtech.common.metatileentities.multi.electric.generator.MetaTileEntityLargeTurbine;
import gregtech.common.metatileentities.multi.electric.godforge.MetaTileEntityForgeOfGods;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEExoticModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEMoltenModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEPlasmaModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTESmeltingModule;
import gregtech.common.metatileentities.multi.steam.MetaTileEntitySteamGrinder;
import gregtech.common.metatileentities.multi.steam.MetaTileEntitySteamOven;
import gregtech.common.metatileentities.primitive.MetaTileEntityCharcoalPileIgniter;

import static gregtech.api.GTValues.*;
import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.common.metatileentities.MetaTileEntities.*;

/**
 * Registration for all multiblock controllers:
 * <ul>
 *   <li>Primitive multiblocks (IDs 1000, 1013-1018, 1036, 2001-2002, 2050-2051)</li>
 *   <li>Electric multiblock controllers (IDs 1001-1042)</li>
 *   <li>Steam multiblocks (IDs 1024-1025)</li>
 *   <li>Forge of the Gods (IDs 2100-2104)</li>
 * </ul>
 */
public final class MultiblockRegistration {

    private MultiblockRegistration() {}

    public static void init() {
        registerPrimitiveMultiblocks();
        registerElectricMultiblocks();
        registerSteamMultiblocks();
        registerGodforge();
    }

    // ---- Primitive multiblocks ----

    private static void registerPrimitiveMultiblocks() {
        PRIMITIVE_BLAST_FURNACE = registerMetaTileEntity(1000,
                new MetaTileEntityPrimitiveBlastFurnace(gregtechId("primitive_blast_furnace.bronze")));
        PRIMITIVE_BLAST_FURNACE_HATCH = registerMetaTileEntity(1059,
                new MetaTileEntityPrimitiveBlastFurnaceHatch(gregtechId("primitive_blast_furnace_hatch")));

        COKE_OVEN = registerMetaTileEntity(1017, new MetaTileEntityCokeOven(gregtechId("coke_oven")));
        COKE_OVEN_HATCH = registerMetaTileEntity(1018, new MetaTileEntityCokeOvenHatch(gregtechId("coke_oven_hatch")));

        CHARCOAL_PILE_IGNITER = registerMetaTileEntity(1036,
                new MetaTileEntityCharcoalPileIgniter(gregtechId("charcoal_pile")));

        PRIMITIVE_WATER_PUMP = registerMetaTileEntity(2001,
                new MetaTileEntityPrimitiveWaterPump(gregtechId("primitive_water_pump")));
        PUMP_OUTPUT_HATCH = registerMetaTileEntity(2002, new MetaTileEntityPumpHatch(gregtechId("pump_hatch")));

        // Tanks, IDs 2050-
        WOODEN_TANK_VALVE = registerMetaTileEntity(2050,
                new MetaTileEntityTankValve(gregtechId("tank_valve.wood"), false));
        WOODEN_TANK = registerMetaTileEntity(2051,
                new MetaTileEntityMultiblockTank(gregtechId("tank.wood"), false, 250 * 1000));

        STEEL_TANK_VALVE = registerMetaTileEntity(2052,
                new MetaTileEntityTankValve(gregtechId("tank_valve.steel"), true));
        STEEL_TANK = registerMetaTileEntity(2053,
                new MetaTileEntityMultiblockTank(gregtechId("tank.steel"), true, 1000 * 1000));

    }

    // ---- Electric multiblock controllers ----

    private static void registerElectricMultiblocks() {
        // MULTIBLOCK START: IDs 1000-1149
        ELECTRIC_BLAST_FURNACE = registerMetaTileEntity(1001,
                new MetaTileEntityElectricBlastFurnace(gregtechId("electric_blast_furnace")));
        VACUUM_FREEZER = registerMetaTileEntity(1002, new MetaTileEntityVacuumFreezer(gregtechId("vacuum_freezer")));
        IMPLOSION_COMPRESSOR = registerMetaTileEntity(1003,
                new MetaTileEntityImplosionCompressor(gregtechId("implosion_compressor")));
        PYROLYSE_OVEN = registerMetaTileEntity(1004, new MetaTileEntityPyrolyseOven(gregtechId("pyrolyse_oven")));
        DISTILLATION_TOWER = registerMetaTileEntity(1005,
                new MetaTileEntityDistillationTower(gregtechId("distillation_tower"), true));
        MULTI_FURNACE = registerMetaTileEntity(1006, new MetaTileEntityMultiSmelter(gregtechId("multi_furnace")));

        // Large Combustion Engines - Single ID with NBT variants (ID 1007)
        LARGE_COMBUSTION_ENGINE = registerMetaTileEntity(1007,
                new MetaTileEntityLargeCombustionEngine(gregtechId("large_combustion_engine"), EV));
        EXTREME_COMBUSTION_ENGINE = registerMetaTileEntity(1008,
                new MetaTileEntityLargeCombustionEngine(gregtechId("extreme_combustion_engine"), IV));

        CRACKER = registerMetaTileEntity(1009, new MetaTileEntityCrackingUnit(gregtechId("cracker")));

        LARGE_STEAM_TURBINE = registerMetaTileEntity(1010,
                new MetaTileEntityLargeTurbine(gregtechId("large_turbine.steam"), RecipeMaps.STEAM_TURBINE_FUELS, HV,
                        MetaBlocks.TURBINE_CASING.getState(BlockTurbineCasing.TurbineCasingType.STEEL_TURBINE_CASING),
                        MetaBlocks.TURBINE_CASING.getState(BlockTurbineCasing.TurbineCasingType.STEEL_GEARBOX),
                        Textures.TURBINE_STEEL_CASING, false, Textures.LARGE_STEAM_TURBINE_OVERLAY));

        LARGE_GAS_TURBINE = registerMetaTileEntity(1011,
                new MetaTileEntityLargeTurbine(gregtechId("large_turbine.gas"), RecipeMaps.GAS_TURBINE_FUELS, EV,
                        MetaBlocks.TURBINE_CASING.getState(BlockTurbineCasing.TurbineCasingType.STAINLESS_TURBINE_CASING),
                        MetaBlocks.TURBINE_CASING.getState(BlockTurbineCasing.TurbineCasingType.STAINLESS_STEEL_GEARBOX),
                        Textures.TURBINE_STAINLESS_STEEL_CASING, true, Textures.LARGE_GAS_TURBINE_OVERLAY));

        LARGE_PLASMA_TURBINE = registerMetaTileEntity(1012,
                new MetaTileEntityLargeTurbine(gregtechId("large_turbine.plasma"), RecipeMaps.PLASMA_GENERATOR_FUELS, IV,
                        MetaBlocks.TURBINE_CASING.getState(BlockTurbineCasing.TurbineCasingType.TUNGSTENSTEEL_TURBINE_CASING),
                        MetaBlocks.TURBINE_CASING.getState(BlockTurbineCasing.TurbineCasingType.TUNGSTENSTEEL_GEARBOX),
                        Textures.TURBINE_TUNGSTENSTEEL_CASING, false, Textures.LARGE_PLASMA_TURBINE_OVERLAY));

        LARGE_BRONZE_BOILER = registerMetaTileEntity(1013,
                new MetaTileEntityLargeBoiler(gregtechId("large_boiler.bronze"), BoilerType.BRONZE));
        LARGE_STEEL_BOILER = registerMetaTileEntity(1014,
                new MetaTileEntityLargeBoiler(gregtechId("large_boiler.steel"), BoilerType.STEEL));
        LARGE_TITANIUM_BOILER = registerMetaTileEntity(1015,
                new MetaTileEntityLargeBoiler(gregtechId("large_boiler.titanium"), BoilerType.TITANIUM));
        LARGE_TUNGSTENSTEEL_BOILER = registerMetaTileEntity(1016,
                new MetaTileEntityLargeBoiler(gregtechId("large_boiler.tungstensteel"), BoilerType.TUNGSTENSTEEL));

        ASSEMBLY_LINE = registerMetaTileEntity(1019, new MetaTileEntityAssemblyLine(gregtechId("assembly_line")));

        FUSION_REACTOR[0] = registerMetaTileEntity(1020,
                new MetaTileEntityFusionReactor(gregtechId("fusion_reactor.luv"), GTValues.LuV));
        FUSION_REACTOR[1] = registerMetaTileEntity(1021,
                new MetaTileEntityFusionReactor(gregtechId("fusion_reactor.zpm"), GTValues.ZPM));
        FUSION_REACTOR[2] = registerMetaTileEntity(1022,
                new MetaTileEntityFusionReactor(gregtechId("fusion_reactor.uv"), GTValues.UV));

        LARGE_CHEMICAL_REACTOR = registerMetaTileEntity(1023,
                new MetaTileEntityLargeChemicalReactor(gregtechId("large_chemical_reactor")));

        BASIC_LARGE_MINER = registerMetaTileEntity(1026, new MetaTileEntityLargeMiner(gregtechId("large_miner.ev"), EV, 16, 3, 4, Materials.Steel, 8));
        LARGE_MINER = registerMetaTileEntity(1027, new MetaTileEntityLargeMiner(gregtechId("large_miner.iv"), IV, 4, 5, 5, Materials.Titanium, 16));
        ADVANCED_LARGE_MINER = registerMetaTileEntity(1028, new MetaTileEntityLargeMiner(gregtechId("large_miner.luv"), GTValues.LuV, 1, 7, 6, Materials.TungstenSteel, 32));

        PROCESSING_ARRAY = registerMetaTileEntity(1030,
                new MetaTileEntityProcessingArray(gregtechId("processing_array"), 0));
        ADVANCED_PROCESSING_ARRAY = registerMetaTileEntity(1031,
                new MetaTileEntityProcessingArray(gregtechId("advanced_processing_array"), 1));

        BASIC_FLUID_DRILLING_RIG = registerMetaTileEntity(1032,
                new MetaTileEntityFluidDrill(gregtechId("fluid_drilling_rig.mv"), 2));
        FLUID_DRILLING_RIG = registerMetaTileEntity(1033,
                new MetaTileEntityFluidDrill(gregtechId("fluid_drilling_rig.hv"), 3));
        ADVANCED_FLUID_DRILLING_RIG = registerMetaTileEntity(1034,
                new MetaTileEntityFluidDrill(gregtechId("fluid_drilling_rig.ev"), 4));

        CLEANROOM = registerMetaTileEntity(1035, new MetaTileEntityCleanroom(gregtechId("cleanroom")));

        DATA_BANK = registerMetaTileEntity(1037, new MetaTileEntityDataBank(gregtechId("data_bank")));
        RESEARCH_STATION = registerMetaTileEntity(1038,
                new MetaTileEntityResearchStation(gregtechId("research_station")));
        HIGH_PERFORMANCE_COMPUTING_ARRAY = registerMetaTileEntity(1039,
                new MetaTileEntityHPCA(gregtechId("high_performance_computing_array")));
        NETWORK_SWITCH = registerMetaTileEntity(1040, new MetaTileEntityNetworkSwitch(gregtechId("network_switch")));

        POWER_SUBSTATION = registerMetaTileEntity(1041,
                new MetaTileEntityPowerSubstation(gregtechId("power_substation")));
        ACTIVE_TRANSFORMER = registerMetaTileEntity(1042,
                new MetaTileEntityActiveTransformer(gregtechId("active_transformer")));

        MULTI_ALLOY_FURNACE = registerMetaTileEntity(1050, new MetaTileEntityMultiAlloyFurnace(gregtechId("multi_alloy_furnace")));

        SAW_MILL = registerMetaTileEntity(1060, new MetaTileEntitySawMill(gregtechId("saw_mill")));
    }

    // ---- Steam multiblocks ----

    private static void registerSteamMultiblocks() {
        STEAM_OVEN = registerMetaTileEntity(1024, new MetaTileEntitySteamOven(gregtechId("steam_oven")));
        STEAM_GRINDER = registerMetaTileEntity(1025, new MetaTileEntitySteamGrinder(gregtechId("steam_grinder")));
    }

    // ---- Forge of the Gods ----

    private static void registerGodforge() {
        FORGE_OF_GODS = registerMetaTileEntity(2100,
                new MetaTileEntityForgeOfGods(gregtechId("forge_of_gods")));
        GODFORGE_SMELTING_MODULE = registerMetaTileEntity(2101,
                new MTESmeltingModule(gregtechId("godforge_smelting_module")));
        GODFORGE_MOLTEN_MODULE = registerMetaTileEntity(2102,
                new MTEMoltenModule(gregtechId("godforge_molten_module")));
        GODFORGE_PLASMA_MODULE = registerMetaTileEntity(2103,
                new MTEPlasmaModule(gregtechId("godforge_plasma_module")));
        GODFORGE_EXOTIC_MODULE = registerMetaTileEntity(2104,
                new MTEExoticModule(gregtechId("godforge_exotic_module")));
    }
}
