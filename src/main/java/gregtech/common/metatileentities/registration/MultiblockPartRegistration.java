package gregtech.common.metatileentities.registration;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.util.Mods;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityAutoMaintenanceHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityCleaningMaintenanceHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityComputationHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityDataAccessHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityEnergyHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityFluidHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityLaserHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMachineHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMaintenanceHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMufflerHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiFluidHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityObjectHolder;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityOpticalDataHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityPassthroughHatchComputation;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityPassthroughHatchFluid;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityPassthroughHatchItem;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityPassthroughHatchLaser;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityReservoirHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityRotorHolder;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntitySterileCleaningMaintenanceHatch;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntitySubstationEnergyHatch;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEInputBus;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEInputHatch;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEOutputBus;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEOutputHatch;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEStockingBus;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEStockingHatch;
import gregtech.common.metatileentities.multi.multiblockpart.hpca.MetaTileEntityHPCAAdvancedComputation;
import gregtech.common.metatileentities.multi.multiblockpart.hpca.MetaTileEntityHPCAAdvancedCooler;
import gregtech.common.metatileentities.multi.multiblockpart.hpca.MetaTileEntityHPCABridge;
import gregtech.common.metatileentities.multi.multiblockpart.hpca.MetaTileEntityHPCAComputation;
import gregtech.common.metatileentities.multi.multiblockpart.hpca.MetaTileEntityHPCACooler;
import gregtech.common.metatileentities.multi.multiblockpart.hpca.MetaTileEntityHPCAEmpty;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntityHugeSteamFluidHatch;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntityHugeSteamHatch;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntityHugeSteamItemBus;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntitySteamFluidHatch;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntitySteamHatch;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntitySteamItemBus;

import static gregtech.api.GTValues.*;
import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.common.metatileentities.MetaTileEntities.*;

/**
 * Registration for all multiblock parts (hatches and buses):
 * <ul>
 *   <li>Item/Fluid IO Buses and Hatches (IDs 1150-1300)</li>
 *   <li>Energy Input/Output Hatches (IDs 1300-1420)</li>
 *   <li>Laser Hatches (IDs 1530-1660)</li>
 *   <li>Maintenance Hatches (IDs 1700-1704)</li>
 *   <li>Passthrough Hatches (IDs 1710-1713)</li>
 *   <li>Data/Optical/HPCA Hatches (IDs 1720-1732)</li>
 *   <li>Reservoir/Machine Hatches (IDs 1740-1741)</li>
 *   <li>Rotor Holders (IDs 1750-1764)</li>
 *   <li>Muffler Hatches (IDs 1775-1789)</li>
 *   <li>ME Hatches (IDs 1900-1905)</li>
 *   <li>Data Access Hatches (IDs 1910-1914)</li>
 *   <li>Computation Hatches (IDs 1920-1950)</li>
 *   <li>Steam Hatches/Buses (IDs 2004-2013)</li>
 * </ul>
 */
public final class MultiblockPartRegistration {

    private MultiblockPartRegistration() {}

    public static void init() {
        registerIOHatches();
        registerEnergyHatches();
        registerLaserHatches();
        registerMaintenanceHatches();
        registerSpecialHatches();
        registerMEHatches();
        registerDataHatches();
        registerComputationHatches();
        registerSteamHatches();
    }

    // ---- Item/Fluid IO Buses and Hatches ----

    private static void registerIOHatches() {
        // Import/Export Buses/Hatches, IDs 1150-1300
        int endPos = GregTechAPI.isHighTier() ? ITEM_IMPORT_BUS.length : GTValues.UHV + 1;
        for (int i = 0; i < endPos; i++) {
            String voltageName = GTValues.VN[i].toLowerCase();
            ITEM_IMPORT_BUS[i] = new MetaTileEntityItemBus(gregtechId("item_bus.import." + voltageName), i, false);
            ITEM_EXPORT_BUS[i] = new MetaTileEntityItemBus(gregtechId("item_bus.export." + voltageName), i, true);
            FLUID_IMPORT_HATCH[i] = new MetaTileEntityFluidHatch(gregtechId("fluid_hatch.import." + voltageName), i,
                    false);
            FLUID_EXPORT_HATCH[i] = new MetaTileEntityFluidHatch(gregtechId("fluid_hatch.export." + voltageName), i,
                    true);

            registerMetaTileEntity(1150 + i, ITEM_IMPORT_BUS[i]);
            registerMetaTileEntity(1165 + i, ITEM_EXPORT_BUS[i]);
            registerMetaTileEntity(1180 + i, FLUID_IMPORT_HATCH[i]);
            registerMetaTileEntity(1195 + i, FLUID_EXPORT_HATCH[i]);

            QUADRUPLE_IMPORT_HATCH[i] = registerMetaTileEntity(1210 + i,
                    new MetaTileEntityMultiFluidHatch(gregtechId("fluid_hatch.import_4x." + voltageName), i, 4, false));
            NONUPLE_IMPORT_HATCH[i] = registerMetaTileEntity(1225 + i,
                    new MetaTileEntityMultiFluidHatch(gregtechId("fluid_hatch.import_9x." + voltageName), i, 9, false));
            SIXTEEN_IMPORT_HATCH[i] = registerMetaTileEntity(1240 + i,
                    new MetaTileEntityMultiFluidHatch(gregtechId("fluid_hatch.import_16x." + voltageName), i, 16,
                            false));
            QUADRUPLE_EXPORT_HATCH[i] = registerMetaTileEntity(1255 + i,
                    new MetaTileEntityMultiFluidHatch(gregtechId("fluid_hatch.export_4x." + voltageName), i, 4, true));
            NONUPLE_EXPORT_HATCH[i] = registerMetaTileEntity(1270 + i,
                    new MetaTileEntityMultiFluidHatch(gregtechId("fluid_hatch.export_9x." + voltageName), i, 9, true));
            SIXTEEN_EXPORT_HATCH[i] = registerMetaTileEntity(1285 + i,
                    new MetaTileEntityMultiFluidHatch(gregtechId("fluid_hatch.export_16x." + voltageName), i, 16,
                            true));
        }
    }

    // ---- Energy Input/Output Hatches ----

    private static void registerEnergyHatches() {
        // Energy Input/Output Hatches, IDs 1300-1420
        int endPos = GregTechAPI.isHighTier() ? ENERGY_INPUT_HATCH.length - 1 :
                Math.min(ENERGY_INPUT_HATCH.length - 1, GTValues.UV + 2);
        for (int i = 0; i < endPos; i++) {
            String voltageName = GTValues.VN[i].toLowerCase();
            ENERGY_INPUT_HATCH[i] = registerMetaTileEntity(1300 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.input." + voltageName), i, 2, false));
            ENERGY_OUTPUT_HATCH[i] = registerMetaTileEntity(1315 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.output." + voltageName), i, 2, true));
            ENERGY_INPUT_HATCH_4A[i] = registerMetaTileEntity(1330 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.input_4a." + voltageName), i, 4, false));
            ENERGY_OUTPUT_HATCH_4A[i] = registerMetaTileEntity(1345 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.output_4a." + voltageName), i, 4, true));
            ENERGY_INPUT_HATCH_16A[i] = registerMetaTileEntity(1360 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.input_16a." + voltageName), i, 16, false));
            ENERGY_OUTPUT_HATCH_16A[i] = registerMetaTileEntity(1375 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.output_16a." + voltageName), i, 16, true));
            SUBSTATION_ENERGY_INPUT_HATCH[i] = registerMetaTileEntity(1390 + i,
                    new MetaTileEntitySubstationEnergyHatch(gregtechId("substation_hatch.input_64a." + voltageName), i,
                            64, false));
            SUBSTATION_ENERGY_OUTPUT_HATCH[i] = registerMetaTileEntity(1405 + i,
                    new MetaTileEntitySubstationEnergyHatch(gregtechId("substation_hatch.output_64a." + voltageName), i,
                            64, true));
        }
    }

    // ---- Laser Hatches ----

    private static void registerLaserHatches() {
        // Laser Hatches, IDs 1530-1660
        int endPos = GregTechAPI.isHighTier() ? LASER_INPUT_HATCH_256.length - 1 :
                Math.min(LASER_INPUT_HATCH_256.length - 1, GTValues.UHV - IV);
        for (int i = 0; i < endPos; i++) {
            int v = i + IV;
            String voltageName = GTValues.VN[v].toLowerCase();
            LASER_INPUT_HATCH_256[i] = registerMetaTileEntity(1530 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.target_256a." + voltageName), false, v, 256));
            LASER_OUTPUT_HATCH_256[i] = registerMetaTileEntity(1540 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.source_256a." + voltageName), true, v, 256));
            LASER_INPUT_HATCH_1024[i] = registerMetaTileEntity(1550 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.target_1024a." + voltageName), false, v,
                            1024));
            LASER_OUTPUT_HATCH_1024[i] = registerMetaTileEntity(1560 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.source_1024a." + voltageName), true, v, 1024));
            LASER_INPUT_HATCH_4096[i] = registerMetaTileEntity(1570 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.target_4096a." + voltageName), false, v,
                            4096));
            LASER_OUTPUT_HATCH_4096[i] = registerMetaTileEntity(1580 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.source_4096a." + voltageName), true, v, 4096));
            LASER_INPUT_HATCH_16384[i] = registerMetaTileEntity(1590 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.target_16384a." + voltageName), false, v,
                            16384));
            LASER_OUTPUT_HATCH_16384[i] = registerMetaTileEntity(1600 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.source_16384a." + voltageName), true, v,
                            16384));
            LASER_INPUT_HATCH_65536[i] = registerMetaTileEntity(1610 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.target_65536a." + voltageName), false, v,
                            65536));
            LASER_OUTPUT_HATCH_65536[i] = registerMetaTileEntity(1620 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.source_65536a." + voltageName), true, v,
                            65536));
            LASER_INPUT_HATCH_262144[i] = registerMetaTileEntity(1630 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.target_262144a." + voltageName), false, v,
                            262144));
            LASER_OUTPUT_HATCH_262144[i] = registerMetaTileEntity(1640 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.source_262144a." + voltageName), true, v,
                            262144));
            LASER_INPUT_HATCH_1048576[i] = registerMetaTileEntity(1650 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.target_1048576a." + voltageName), false, v,
                            1048576));
            LASER_OUTPUT_HATCH_1048576[i] = registerMetaTileEntity(1660 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.source_1048576a." + voltageName), true, v,
                            1048576));
        }
    }

    // ---- Maintenance Hatches ----

    private static void registerMaintenanceHatches() {
        // Maintenance Hatches, IDs 1700-1704
        MAINTENANCE_HATCH = registerMetaTileEntity(1700,
                new MetaTileEntityMaintenanceHatch(gregtechId("maintenance_hatch"), false));
        CONFIGURABLE_MAINTENANCE_HATCH = registerMetaTileEntity(1701,
                new MetaTileEntityMaintenanceHatch(gregtechId("maintenance_hatch_configurable"), true));
        AUTO_MAINTENANCE_HATCH = registerMetaTileEntity(1702,
                new MetaTileEntityAutoMaintenanceHatch(gregtechId("maintenance_hatch_full_auto")));
        CLEANING_MAINTENANCE_HATCH = registerMetaTileEntity(1703,
                new MetaTileEntityCleaningMaintenanceHatch(gregtechId("maintenance_hatch_cleanroom_auto")));
        STERILE_CLEANING_MAINTENANCE_HATCH = registerMetaTileEntity(1704,
                new MetaTileEntitySterileCleaningMaintenanceHatch(gregtechId("maintenance_hatch_sterile_cleanroom_auto")));
    }

    // ---- Passthrough, Optical, HPCA, Rotor Holder, Muffler, Reservoir, Machine Hatches ----

    private static void registerSpecialHatches() {
        PASSTHROUGH_HATCH_ITEM = registerMetaTileEntity(1710,
                new MetaTileEntityPassthroughHatchItem(gregtechId("passthrough_hatch_item"), 3));
        PASSTHROUGH_HATCH_FLUID = registerMetaTileEntity(1711,
                new MetaTileEntityPassthroughHatchFluid(gregtechId("passthrough_hatch_fluid"), 3));
        PASSTHROUGH_HATCH_LASER = registerMetaTileEntity(1712,
                new MetaTileEntityPassthroughHatchLaser(gregtechId("passthrough_hatch_laser"), 5));
        PASSTHROUGH_HATCH_COMPUTATION = registerMetaTileEntity(1713,
                new MetaTileEntityPassthroughHatchComputation(gregtechId("passthrough_hatch_computation"), 5));

        OPTICAL_DATA_HATCH_RECEIVER = registerMetaTileEntity(1720,
                new MetaTileEntityOpticalDataHatch(gregtechId("data_access_hatch.optical.receiver"), false));
        OPTICAL_DATA_HATCH_TRANSMITTER = registerMetaTileEntity(1721,
                new MetaTileEntityOpticalDataHatch(gregtechId("data_access_hatch.optical.transmitter"), true));
        OBJECT_HOLDER = registerMetaTileEntity(1722,
                new MetaTileEntityObjectHolder(gregtechId("research_station.object_holder")));
        HPCA_EMPTY_COMPONENT = registerMetaTileEntity(1723,
                new MetaTileEntityHPCAEmpty(gregtechId("hpca.empty_component")));
        HPCA_COMPUTATION_COMPONENT = registerMetaTileEntity(1724,
                new MetaTileEntityHPCAComputation(gregtechId("hpca.computation_component"), false));
        HPCA_ADVANCED_COMPUTATION_COMPONENT = registerMetaTileEntity(1725,
                new MetaTileEntityHPCAComputation(gregtechId("hpca.advanced_computation_component"), true));
        HPCA_HEAT_SINK_COMPONENT = registerMetaTileEntity(1726,
                new MetaTileEntityHPCACooler(gregtechId("hpca.heat_sink_component"), false));
        HPCA_ACTIVE_COOLER_COMPONENT = registerMetaTileEntity(1727,
                new MetaTileEntityHPCACooler(gregtechId("hpca.active_cooler_component"), true));
        HPCA_SUPER_COMPUTATION_COMPONENT = registerMetaTileEntity(1728,
                new MetaTileEntityHPCAAdvancedComputation(gregtechId("hpca.super_computation_component"), false));
        HPCA_ULTIMATE_COMPUTATION_COMPONENT = registerMetaTileEntity(1729,
                new MetaTileEntityHPCAAdvancedComputation(gregtechId("hpca.ultimate_computation_component"), true));
        HPCA_SUPER_COOLER_COMPONENT = registerMetaTileEntity(1730,
                new MetaTileEntityHPCAAdvancedCooler(gregtechId("hpca.super_cooler_component"), true, false));
        HPCA_ULTIMATE_COOLER_COMPONENT = registerMetaTileEntity(1731,
                new MetaTileEntityHPCAAdvancedCooler(gregtechId("hpca.ultimate_cooler_component"), false, true));
        HPCA_BRIDGE_COMPONENT = registerMetaTileEntity(1732,
                new MetaTileEntityHPCABridge(gregtechId("hpca.bridge_component")));

        RESERVOIR_HATCH = registerMetaTileEntity(1740, new MetaTileEntityReservoirHatch(gregtechId("reservoir_hatch")));
        MACHINE_HATCH = registerMetaTileEntity(1741, new MetaTileEntityMachineHatch(gregtechId("machine_hatch"), 2));

        // Rotor Holder, IDs 1750-1764
        for (int i = 0; i < ROTOR_HOLDER.length; i++) {
            String voltageName = GTValues.VN[i + 3].toLowerCase();
            ROTOR_HOLDER[i] = registerMetaTileEntity(1750 + i,
                    new MetaTileEntityRotorHolder(gregtechId("rotor_holder." + voltageName), i + 3));
        }

        // Muffler Hatches, IDs 1775-1789
        for (int i = 0; i < MUFFLER_HATCH.length - 1; i++) {
            int tier = i+1;
            String voltageName = GTValues.VN[tier].toLowerCase();
            MUFFLER_HATCH[i] = new MetaTileEntityMufflerHatch(gregtechId("muffler_hatch." + voltageName), tier);
            registerMetaTileEntity(1775 + i, MUFFLER_HATCH[i]);
        }
    }

    // ---- ME Hatches (AE2 integration) ----

    private static void registerMEHatches() {
        // ME Hatches, IDs 1900-
        if (Mods.AppliedEnergistics2.isModLoaded()) {
            FLUID_EXPORT_HATCH_ME = registerMetaTileEntity(1900,
                    new MetaTileEntityMEOutputHatch(gregtechId("me_export_fluid_hatch")));
            ITEM_EXPORT_BUS_ME = registerMetaTileEntity(1901,
                    new MetaTileEntityMEOutputBus(gregtechId("me_export_item_bus")));
            FLUID_IMPORT_HATCH_ME = registerMetaTileEntity(1902,
                    new MetaTileEntityMEInputHatch(gregtechId("me_import_fluid_hatch"), GTValues.EV));
            ITEM_IMPORT_BUS_ME = registerMetaTileEntity(1903,
                    new MetaTileEntityMEInputBus(gregtechId("me_import_item_bus"), GTValues.EV));
            STOCKING_BUS_ME = registerMetaTileEntity(1904,
                    new MetaTileEntityMEStockingBus(gregtechId("me_stocking_item_bus"), GTValues.IV));
            STOCKING_HATCH_ME = registerMetaTileEntity(1905,
                    new MetaTileEntityMEStockingHatch(gregtechId("me_stocking_fluid_hatch"), GTValues.IV));
        }
    }

    // ---- Data Access Hatches ----

    private static void registerDataHatches() {
        // Data Access Hatches, IDs 1910-
        DATA_ACCESS_HATCH[0] = registerMetaTileEntity(1910,
                new MetaTileEntityDataAccessHatch(gregtechId("data_access_hatch.i"), GTValues.MV, false));
        DATA_ACCESS_HATCH[1] = registerMetaTileEntity(1911,
                new MetaTileEntityDataAccessHatch(gregtechId("data_access_hatch.ii"), EV, false));
        DATA_ACCESS_HATCH[2] = registerMetaTileEntity(1912,
                new MetaTileEntityDataAccessHatch(gregtechId("data_access_hatch.iii"), GTValues.LuV, false));
        DATA_ACCESS_HATCH[3] = registerMetaTileEntity(1913,
                new MetaTileEntityDataAccessHatch(gregtechId("data_access_hatch.iv"), GTValues.UV, false));
        DATA_ACCESS_HATCH[4] = registerMetaTileEntity(1914,
                new MetaTileEntityDataAccessHatch(gregtechId("data_access_hatch.creative"), GTValues.MAX, true));
    }

    // ---- Computation Hatches ----

    private static void registerComputationHatches() {
        // Computation Hatches, IDs 1920-
        for (int i = 0; i < COMPUTATION_HATCH_RECEIVER.length - 1; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            COMPUTATION_HATCH_RECEIVER[i] = registerMetaTileEntity(1920 + i,
                    new MetaTileEntityComputationHatch(gregtechId("computation_hatch.receiver." + voltageName), i + 1,
                            false));
            COMPUTATION_HATCH_TRANSMITTER[i] = registerMetaTileEntity(1935 + i,
                    new MetaTileEntityComputationHatch(gregtechId("computation_hatch.transmitter." + voltageName),
                            i + 1, true));
        }
    }

    // ---- Steam Hatches/Buses ----

    private static void registerSteamHatches() {
        STEAM_HATCH = registerMetaTileEntity(2004, new MetaTileEntitySteamHatch(gregtechId("steam_hatch")));
        HUGE_STEAM_HATCH = registerMetaTileEntity(2005, new MetaTileEntityHugeSteamHatch(gregtechId("huge_steam_hatch")));

        STEAM_EXPORT_BUS = registerMetaTileEntity(2006,
                new MetaTileEntitySteamItemBus(gregtechId("steam_export_bus"), true));
        STEAM_IMPORT_BUS = registerMetaTileEntity(2007,
                new MetaTileEntitySteamItemBus(gregtechId("steam_import_bus"), false));

        STEAM_EXPORT_HATCH = registerMetaTileEntity(2008,
                new MetaTileEntitySteamFluidHatch(gregtechId("steam_export_hatch"), true));
        STEAM_IMPORT_HATCH = registerMetaTileEntity(2009,
                new MetaTileEntitySteamFluidHatch(gregtechId("steam_import_hatch"), false));

        HUGE_STEAM_EXPORT_BUS = registerMetaTileEntity(2010,
                new MetaTileEntityHugeSteamItemBus(gregtechId("huge_steam_export_bus"), true));
        HUGE_STEAM_IMPORT_BUS = registerMetaTileEntity(2011,
                new MetaTileEntityHugeSteamItemBus(gregtechId("huge_steam_import_bus"), false));

        HUGE_STEAM_EXPORT_HATCH=registerMetaTileEntity(2012,
                new MetaTileEntityHugeSteamFluidHatch(gregtechId("huge_steam_export_hatch"), true));
        HUGE_STEAM_IMPORT_HATCH=registerMetaTileEntity(2013,
                new MetaTileEntityHugeSteamFluidHatch(gregtechId("huge_steam_import_hatch"), false));
    }
}
