package gregtech.common.metatileentities.registration;

import gregtech.api.GTValues;
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
import gregtech.common.metatileentities.multi.electric.FluidDrillType;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityFusionReactor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityHPCA;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityImplosionCompressor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeChemicalReactor;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityLargeMiner;
import gregtech.common.metatileentities.multi.electric.LargeMinerType;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityMultiAlloyFurnace;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityMultiSmelter;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityNetworkSwitch;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityPowerSubstation;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityProcessingArray;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityPyrolyseOven;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityResearchStation;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityVacuumFreezer;
import gregtech.common.metatileentities.multi.electric.generator.LargeCombustionEngineType;
import gregtech.common.metatileentities.multi.electric.generator.LargeTurbineVariant;
import gregtech.common.metatileentities.multi.electric.generator.LargeTurbineType;
import gregtech.common.metatileentities.multi.electric.generator.LargeTurbineVariants;
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

        // Multiblock Tank - Single ID with NBT variants (IDs 2050-2051)
        MULTIBLOCK_TANK_VALVE = registerMetaTileEntity(2050,
                new MetaTileEntityTankValve(gregtechId("multiblock_tank_valve")));
        MULTIBLOCK_TANK = registerMetaTileEntity(2051,
                new MetaTileEntityMultiblockTank(gregtechId("multiblock_tank")));

        // Legacy references for backward compatibility
        WOODEN_TANK_VALVE = MULTIBLOCK_TANK_VALVE;
        STEEL_TANK_VALVE = MULTIBLOCK_TANK_VALVE;
        WOODEN_TANK = MULTIBLOCK_TANK;
        STEEL_TANK = new MetaTileEntityMultiblockTank(gregtechId("multiblock_tank"));
        STEEL_TANK.setVariant(MetaTileEntityMultiblockTank.TankMaterial.STEEL);
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
        MetaTileEntityLargeCombustionEngine engine = registerMetaTileEntity(1007,
                new MetaTileEntityLargeCombustionEngine(gregtechId("large_combustion_engine")));
        // Legacy references for backward compatibility (addons may use these)
        LARGE_COMBUSTION_ENGINE = engine;
        EXTREME_COMBUSTION_ENGINE = new MetaTileEntityLargeCombustionEngine(gregtechId("large_combustion_engine"),
                LargeCombustionEngineType.EXTREME);
        LARGE_COMBUSTION_ENGINES.put(LargeCombustionEngineType.REGULAR, LARGE_COMBUSTION_ENGINE);
        LARGE_COMBUSTION_ENGINES.put(LargeCombustionEngineType.EXTREME, EXTREME_COMBUSTION_ENGINE);

        CRACKER = registerMetaTileEntity(1009, new MetaTileEntityCrackingUnit(gregtechId("cracker")));

        // Large Turbines - Single ID with NBT variants (ID 1010)
        MetaTileEntityLargeTurbine turbine = registerMetaTileEntity(1010,
                new MetaTileEntityLargeTurbine(gregtechId("large_turbine")));
        for (LargeTurbineVariant variant : LargeTurbineVariants.registry().getVariants()) {
            LARGE_TURBINE_VARIANTS.put(variant.getId(),
                    variant == LargeTurbineVariants.STEAM ? turbine :
                            new MetaTileEntityLargeTurbine(gregtechId("large_turbine"), variant));
        }
        // Legacy references for backward compatibility (addons may use these)
        LARGE_STEAM_TURBINE = LARGE_TURBINE_VARIANTS.get(LargeTurbineVariants.STEAM.getId());
        LARGE_GAS_TURBINE = LARGE_TURBINE_VARIANTS.get(LargeTurbineVariants.GAS.getId());
        LARGE_PLASMA_TURBINE = LARGE_TURBINE_VARIANTS.get(LargeTurbineVariants.PLASMA.getId());
        LARGE_TURBINES.put(LargeTurbineType.STEAM, LARGE_STEAM_TURBINE);
        LARGE_TURBINES.put(LargeTurbineType.GAS, LARGE_GAS_TURBINE);
        LARGE_TURBINES.put(LargeTurbineType.PLASMA, LARGE_PLASMA_TURBINE);

        // Large Boilers - Single ID with NBT variants (ID 1013)
        MetaTileEntityLargeBoiler boiler = registerMetaTileEntity(1013,
                new MetaTileEntityLargeBoiler(gregtechId("large_boiler")));
        // Legacy references for backward compatibility (addons may use these)
        LARGE_BRONZE_BOILER = boiler;
        LARGE_STEEL_BOILER = new MetaTileEntityLargeBoiler(gregtechId("large_boiler"), BoilerType.STEEL);
        LARGE_TITANIUM_BOILER = new MetaTileEntityLargeBoiler(gregtechId("large_boiler"), BoilerType.TITANIUM);
        LARGE_TUNGSTENSTEEL_BOILER = new MetaTileEntityLargeBoiler(gregtechId("large_boiler"),
                BoilerType.TUNGSTENSTEEL);
        LARGE_BOILERS.put(BoilerType.BRONZE, LARGE_BRONZE_BOILER);
        LARGE_BOILERS.put(BoilerType.STEEL, LARGE_STEEL_BOILER);
        LARGE_BOILERS.put(BoilerType.TITANIUM, LARGE_TITANIUM_BOILER);
        LARGE_BOILERS.put(BoilerType.TUNGSTENSTEEL, LARGE_TUNGSTENSTEEL_BOILER);

        ASSEMBLY_LINE = registerMetaTileEntity(1019, new MetaTileEntityAssemblyLine(gregtechId("assembly_line")));
        FUSION_REACTOR[0] = registerMetaTileEntity(1020,
                new MetaTileEntityFusionReactor(gregtechId("fusion_reactor.luv"), GTValues.LuV));
        FUSION_REACTOR[1] = registerMetaTileEntity(1021,
                new MetaTileEntityFusionReactor(gregtechId("fusion_reactor.zpm"), GTValues.ZPM));
        FUSION_REACTOR[2] = registerMetaTileEntity(1022,
                new MetaTileEntityFusionReactor(gregtechId("fusion_reactor.uv"), GTValues.UV));

        LARGE_CHEMICAL_REACTOR = registerMetaTileEntity(1023,
                new MetaTileEntityLargeChemicalReactor(gregtechId("large_chemical_reactor")));

        // Large Miners registration via EnumMap
        int largeMinerStartId = 1026;
        for (LargeMinerType type : LargeMinerType.values()) {
            MetaTileEntityLargeMiner miner = registerMetaTileEntity(largeMinerStartId++,
                    new MetaTileEntityLargeMiner(gregtechId("large_miner." + type.getName()), type));
            LARGE_MINERS.put(type, miner);
        }
        BASIC_LARGE_MINER = LARGE_MINERS.get(LargeMinerType.BASIC);
        LARGE_MINER = LARGE_MINERS.get(LargeMinerType.NORMAL);
        ADVANCED_LARGE_MINER = LARGE_MINERS.get(LargeMinerType.ADVANCED);

        PROCESSING_ARRAY = registerMetaTileEntity(1030,
                new MetaTileEntityProcessingArray(gregtechId("processing_array"), 0));
        ADVANCED_PROCESSING_ARRAY = registerMetaTileEntity(1031,
                new MetaTileEntityProcessingArray(gregtechId("advanced_processing_array"), 1));

        // Fluid Drilling Rigs registration via EnumMap
        int fluidDrillStartId = 1032;
        for (FluidDrillType type : FluidDrillType.values()) {
            MetaTileEntityFluidDrill drill = registerMetaTileEntity(fluidDrillStartId++,
                    new MetaTileEntityFluidDrill(gregtechId("fluid_drilling_rig." + type.getName()), type));
            FLUID_DRILLS.put(type, drill);
        }
        BASIC_FLUID_DRILLING_RIG = FLUID_DRILLS.get(FluidDrillType.BASIC);
        FLUID_DRILLING_RIG = FLUID_DRILLS.get(FluidDrillType.NORMAL);
        ADVANCED_FLUID_DRILLING_RIG = FLUID_DRILLS.get(FluidDrillType.ADVANCED);

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
