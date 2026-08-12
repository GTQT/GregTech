package gregtech.common.metatileentities.registration;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.common.metatileentities.electric.MetaTileEntityDustCollector;
import gregtech.common.metatileentities.electric.MetaTileEntityElectricHeater;
import gregtech.common.metatileentities.multi.electric.generator.nuclearReactor.MetaTileEntityNuclearExtend;
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
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntityHugeSteamFluidHatch;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntityHugeSteamHatch;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntityHugeSteamItemBus;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntitySteamFluidHatch;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntitySteamHatch;
import gregtech.common.metatileentities.steam.multiblockpart.MetaTileEntitySteamItemBus;

import static gregtech.api.GTValues.EV;
import static gregtech.api.GTValues.IV;
import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.common.metatileentities.MetaTileEntities.*;

public final class MultiblockPartRegistration {

    private MultiblockPartRegistration() {}

    public static void init() {
        registerIOHatches();
        registerSteamHatches();
        registerEnergyHatches();
        registerLaserHatches();
        registerWirelessHatches();
        registerMaintenanceHatches();
        registerSpecialHatches();
    }

    // ---- Item/Fluid IO Buses and Hatches ----

    private static void registerIOHatches() {
        // Import/Export Buses/Hatches
        int endPos = GregTechAPI.isHighTier() ? ITEM_IMPORT_BUS.length : GTValues.UHV + 1;
        for (int i = 0; i < endPos; i++) {
            String voltageName = GTValues.VN[i].toLowerCase();
            ITEM_IMPORT_BUS[i] = registerMetaTileEntity(2000 + i,
                    new MetaTileEntityItemBus(gregtechId("item_bus.import." + voltageName), i, false));
            ITEM_EXPORT_BUS[i] = registerMetaTileEntity(2015 + i,
                    new MetaTileEntityItemBus(gregtechId("item_bus.export." + voltageName), i, true));
            FLUID_IMPORT_HATCH[i] = registerMetaTileEntity(2030 + i,
                    new MetaTileEntityFluidHatch(gregtechId("fluid_hatch.import." + voltageName), i, false));
            FLUID_EXPORT_HATCH[i] = registerMetaTileEntity(2045 + i,
                    new MetaTileEntityFluidHatch(gregtechId("fluid_hatch.export." + voltageName), i, true));

            QUADRUPLE_IMPORT_HATCH[i] = registerMetaTileEntity(2060 + i,
                    new MetaTileEntityMultiFluidHatch(gregtechId("fluid_hatch.import_4x." + voltageName), i, 4, false));
            NONUPLE_IMPORT_HATCH[i] = registerMetaTileEntity(2075 + i,
                    new MetaTileEntityMultiFluidHatch(gregtechId("fluid_hatch.import_9x." + voltageName), i, 9, false));
            SIXTEEN_IMPORT_HATCH[i] = registerMetaTileEntity(2090 + i,
                    new MetaTileEntityMultiFluidHatch(gregtechId("fluid_hatch.import_16x." + voltageName), i, 16,
                            false));
            QUADRUPLE_EXPORT_HATCH[i] = registerMetaTileEntity(2105 + i,
                    new MetaTileEntityMultiFluidHatch(gregtechId("fluid_hatch.export_4x." + voltageName), i, 4, true));
            NONUPLE_EXPORT_HATCH[i] = registerMetaTileEntity(2120 + i,
                    new MetaTileEntityMultiFluidHatch(gregtechId("fluid_hatch.export_9x." + voltageName), i, 9, true));
            SIXTEEN_EXPORT_HATCH[i] = registerMetaTileEntity(2135 + i,
                    new MetaTileEntityMultiFluidHatch(gregtechId("fluid_hatch.export_16x." + voltageName), i, 16,
                            true));

            DUAL_IMPORT_HATCH[i] = registerMetaTileEntity(2150 + i,
                    new MetaTileEntityDualHatch(gregtechId("dual_hatch.import." + voltageName), i, false));
            DUAL_EXPORT_HATCH[i] = registerMetaTileEntity(2165 + i,
                    new MetaTileEntityDualHatch(gregtechId("dual_hatch.export." + voltageName), i, true));
            COMPLEX_DUAL_HATCH[i] = registerMetaTileEntity(2180 + i,
                    new MetaTileEntityComplexDualHatch(gregtechId("complex_dual_hatch." + voltageName), i));
            MOLD_ITEM_BUS[i] = registerMetaTileEntity(2195 + i,
                    new MetaTileEntityMoldItemBus(gregtechId("mold_bus." + voltageName), i));

        }

        // 巨型 IO 仓 (LV-IV, 每等级4变体: 1/4/9/16槽)
        int[] HUGE_SLOT_VARIANTS = { 1, 4, 9, 16 };
        for (int i = 0; i < GTValues.IV; i++) {
            int tier = i + 1;
            String voltageName = GTValues.VN[tier].toLowerCase();
            for (int v = 0; v < 4; v++) {
                int slotCount = HUGE_SLOT_VARIANTS[v];
                String slotKey = slotCount + "." + voltageName;
                HUGE_ITEM_IMPORT_BUS[i][v] = registerMetaTileEntity(2210 + (i * 4 + v),
                        new MetaTileEntityHugeItemBus(gregtechId("huge_item_bus.import." + slotKey), tier, false,
                                slotCount));
                HUGE_ITEM_EXPORT_BUS[i][v] = registerMetaTileEntity(2230 + (i * 4 + v),
                        new MetaTileEntityHugeItemBus(gregtechId("huge_item_bus.export." + slotKey), tier, true,
                                slotCount));
                HUGE_DUAL_IMPORT_HATCH[i][v] = registerMetaTileEntity(2250 + (i * 4 + v),
                        new MetaTileEntityHugeDualHatch(gregtechId("huge_dual_hatch.import." + slotKey), tier, false,
                                slotCount));
                HUGE_DUAL_EXPORT_HATCH[i][v] = registerMetaTileEntity(2270 + (i * 4 + v),
                        new MetaTileEntityHugeDualHatch(gregtechId("huge_dual_hatch.export." + slotKey), tier, true,
                                slotCount));
            }
        }
    }

    // ---- Steam Hatches/Buses ----

    private static void registerSteamHatches() {
        STEAM_HATCH = registerMetaTileEntity(2290, new MetaTileEntitySteamHatch(gregtechId("steam_hatch")));
        HUGE_STEAM_HATCH = registerMetaTileEntity(2291,
                new MetaTileEntityHugeSteamHatch(gregtechId("huge_steam_hatch")));

        STEAM_EXPORT_BUS = registerMetaTileEntity(2292,
                new MetaTileEntitySteamItemBus(gregtechId("steam_export_bus"), true));
        STEAM_IMPORT_BUS = registerMetaTileEntity(2293,
                new MetaTileEntitySteamItemBus(gregtechId("steam_import_bus"), false));

        STEAM_EXPORT_HATCH = registerMetaTileEntity(2294,
                new MetaTileEntitySteamFluidHatch(gregtechId("steam_export_hatch"), true));
        STEAM_IMPORT_HATCH = registerMetaTileEntity(2295,
                new MetaTileEntitySteamFluidHatch(gregtechId("steam_import_hatch"), false));

        HUGE_STEAM_EXPORT_BUS = registerMetaTileEntity(2296,
                new MetaTileEntityHugeSteamItemBus(gregtechId("huge_steam_export_bus"), true));
        HUGE_STEAM_IMPORT_BUS = registerMetaTileEntity(2297,
                new MetaTileEntityHugeSteamItemBus(gregtechId("huge_steam_import_bus"), false));

        HUGE_STEAM_EXPORT_HATCH = registerMetaTileEntity(2298,
                new MetaTileEntityHugeSteamFluidHatch(gregtechId("huge_steam_export_hatch"), true));
        HUGE_STEAM_IMPORT_HATCH = registerMetaTileEntity(2299,
                new MetaTileEntityHugeSteamFluidHatch(gregtechId("huge_steam_import_hatch"), false));
    }

    private static void registerEnergyHatches() {
        int endPos = GregTechAPI.isHighTier() ? ENERGY_INPUT_HATCH.length - 1 :
                Math.min(ENERGY_INPUT_HATCH.length - 1, GTValues.UV + 2);
        for (int i = 0; i < endPos; i++) {
            String voltageName = GTValues.VN[i].toLowerCase();
            ENERGY_INPUT_HATCH[i] = registerMetaTileEntity(2300 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.input." + voltageName), i, 2, false));
            ENERGY_OUTPUT_HATCH[i] = registerMetaTileEntity(2315 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.output." + voltageName), i, 2, true));
            ENERGY_INPUT_HATCH_4A[i] = registerMetaTileEntity(2330 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.input_4a." + voltageName), i, 4, false));
            ENERGY_OUTPUT_HATCH_4A[i] = registerMetaTileEntity(2345 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.output_4a." + voltageName), i, 4, true));
            ENERGY_INPUT_HATCH_16A[i] = registerMetaTileEntity(2360 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.input_16a." + voltageName), i, 16, false));
            ENERGY_OUTPUT_HATCH_16A[i] = registerMetaTileEntity(2375 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.output_16a." + voltageName), i, 16, true));
            ENERGY_INPUT_HATCH_64A[i] = registerMetaTileEntity(2390 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.input_64a." + voltageName), i, 64, false));
            ENERGY_OUTPUT_HATCH_64A[i] = registerMetaTileEntity(2405 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.output_64a." + voltageName), i, 64, true));
            ENERGY_INPUT_HATCH_256A[i] = registerMetaTileEntity(2420 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.input_256a." + voltageName), i, 256, false));
            ENERGY_OUTPUT_HATCH_256A[i] = registerMetaTileEntity(2435 + i,
                    new MetaTileEntityEnergyHatch(gregtechId("energy_hatch.output_256a." + voltageName), i, 256, true));
            SUBSTATION_ENERGY_INPUT_HATCH[i] = registerMetaTileEntity(2450 + i,
                    new MetaTileEntitySubstationEnergyHatch(gregtechId("substation_hatch.input_64a." + voltageName), i,
                            64, false));
            SUBSTATION_ENERGY_INPUT_HATCH_256A[i] = registerMetaTileEntity(2465 + i,
                    new MetaTileEntitySubstationEnergyHatch(gregtechId("substation_hatch.input_256a." + voltageName), i,
                            256, false));
            SUBSTATION_ENERGY_OUTPUT_HATCH[i] = registerMetaTileEntity(2480 + i,
                    new MetaTileEntitySubstationEnergyHatch(gregtechId("substation_hatch.output_64a." + voltageName), i,
                            64, true));
            SUBSTATION_ENERGY_OUTPUT_HATCH_256A[i] = registerMetaTileEntity(2495 + i,
                    new MetaTileEntitySubstationEnergyHatch(gregtechId("substation_hatch.output_256a." + voltageName),
                            i,
                            256, true));
        }
    }

    private static void registerLaserHatches() {
        int endPos = GregTechAPI.isHighTier() ? LASER_INPUT_HATCH_256.length - 1 :
                Math.min(LASER_INPUT_HATCH_256.length - 1, GTValues.UHV - IV);
        for (int i = 0; i < endPos; i++) {
            int v = i + IV;
            String voltageName = GTValues.VN[v].toLowerCase();
            LASER_INPUT_HATCH_256[i] = registerMetaTileEntity(2600 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.target_256a." + voltageName), false, v, 256));
            LASER_OUTPUT_HATCH_256[i] = registerMetaTileEntity(2610 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.source_256a." + voltageName), true, v, 256));
            LASER_INPUT_HATCH_1024[i] = registerMetaTileEntity(2620 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.target_1024a." + voltageName), false, v,
                            1024));
            LASER_OUTPUT_HATCH_1024[i] = registerMetaTileEntity(2630 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.source_1024a." + voltageName), true, v, 1024));
            LASER_INPUT_HATCH_4096[i] = registerMetaTileEntity(2640 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.target_4096a." + voltageName), false, v,
                            4096));
            LASER_OUTPUT_HATCH_4096[i] = registerMetaTileEntity(2650 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.source_4096a." + voltageName), true, v, 4096));
            LASER_INPUT_HATCH_16384[i] = registerMetaTileEntity(2660 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.target_16384a." + voltageName), false, v,
                            16384));
            LASER_OUTPUT_HATCH_16384[i] = registerMetaTileEntity(2670 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.source_16384a." + voltageName), true, v,
                            16384));
            LASER_INPUT_HATCH_65536[i] = registerMetaTileEntity(2680 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.target_65536a." + voltageName), false, v,
                            65536));
            LASER_OUTPUT_HATCH_65536[i] = registerMetaTileEntity(2690 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.source_65536a." + voltageName), true, v,
                            65536));
            LASER_INPUT_HATCH_262144[i] = registerMetaTileEntity(2700 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.target_262144a." + voltageName), false, v,
                            262144));
            LASER_OUTPUT_HATCH_262144[i] = registerMetaTileEntity(2710 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.source_262144a." + voltageName), true, v,
                            262144));
            LASER_INPUT_HATCH_1048576[i] = registerMetaTileEntity(2720 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.target_1048576a." + voltageName), false, v,
                            1048576));
            LASER_OUTPUT_HATCH_1048576[i] = registerMetaTileEntity(2730 + i,
                    new MetaTileEntityLaserHatch(gregtechId("laser_hatch.source_1048576a." + voltageName), true, v,
                            1048576));
        }
    }

    private static void registerWirelessHatches() {
        int endPos = GregTechAPI.isHighTier() ? WIRELESS_INPUT_ENERGY_HATCH.length : GTValues.UHV + 1;
        for (int i = 0; i < endPos; i++) {
            String voltageName = GTValues.VN[i].toLowerCase();

            WIRELESS_CONTROLLERS[i] = registerMetaTileEntity(2800 + i,
                    new MetaTileEntityWirelessController(gregtechId("wireless_controller." + voltageName), i));

            WIRELESS_INPUT_ENERGY_HATCH[i] = registerMetaTileEntity(2815 + i,
                    new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input." + voltageName), i,
                            2,
                            false));
            WIRELESS_INPUT_ENERGY_HATCH_4A[i] = registerMetaTileEntity(2830 + i,
                    new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input_4a." + voltageName),
                            i, 4,
                            false));
            WIRELESS_INPUT_ENERGY_HATCH_16A[i] = registerMetaTileEntity(2845 + i,
                    new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input_16a." + voltageName),
                            i, 16,
                            false));
            WIRELESS_INPUT_ENERGY_HATCH_64A[i] = registerMetaTileEntity(2860 + i,
                    new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input_64a." + voltageName),
                            i, 64,
                            false));
            WIRELESS_INPUT_ENERGY_HATCH_256A[i] = registerMetaTileEntity(2875 + i,
                    new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input_256a." + voltageName),
                            i,
                            256, false));
            WIRELESS_INPUT_ENERGY_HATCH_1024A[i] = registerMetaTileEntity(2890 + i,
                    new MetaTileEntityWirelessEnergyHatch(
                            gregtechId("wireless_energy_hatch.input_1024a." + voltageName), i,
                            1024, false));
            WIRELESS_INPUT_ENERGY_HATCH_4096A[i] = registerMetaTileEntity(2905 + i,
                    new MetaTileEntityWirelessEnergyHatch(
                            gregtechId("wireless_energy_hatch.input_4096a." + voltageName), i,
                            4096, false));
            WIRELESS_INPUT_ENERGY_HATCH_16384A[i] = registerMetaTileEntity(2920 + i,
                    new MetaTileEntityWirelessEnergyHatch(
                            gregtechId("wireless_energy_hatch.input_16384a." + voltageName), i,
                            16384, false));
            WIRELESS_INPUT_ENERGY_HATCH_65536A[i] = registerMetaTileEntity(2935 + i,
                    new MetaTileEntityWirelessEnergyHatch(
                            gregtechId("wireless_energy_hatch.input_65536a." + voltageName), i,
                            65536, false));
            WIRELESS_INPUT_ENERGY_HATCH_262144A[i] = registerMetaTileEntity(2950 + i,
                    new MetaTileEntityWirelessEnergyHatch(
                            gregtechId("wireless_energy_hatch.input_262144a." + voltageName), i,
                            262144, false));
            WIRELESS_INPUT_ENERGY_HATCH_1048576A[i] = registerMetaTileEntity(2965 + i,
                    new MetaTileEntityWirelessEnergyHatch(
                            gregtechId("wireless_energy_hatch.input_1048576a." + voltageName), i,
                            1048576, false));
            WIRELESS_OUTPUT_ENERGY_HATCH[i] = registerMetaTileEntity(2980 + i,
                    new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.output." + voltageName), i,
                            2,
                            true));
            WIRELESS_OUTPUT_ENERGY_HATCH_4A[i] = registerMetaTileEntity(2995 + i,
                    new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.output_4a." + voltageName),
                            i, 4,
                            true));
            WIRELESS_OUTPUT_ENERGY_HATCH_16A[i] = registerMetaTileEntity(3010 + i,
                    new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.output_16a." + voltageName),
                            i, 16,
                            true));
            WIRELESS_OUTPUT_ENERGY_HATCH_64A[i] = registerMetaTileEntity(3025 + i,
                    new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.output_64a." + voltageName),
                            i, 64,
                            true));
            WIRELESS_OUTPUT_ENERGY_HATCH_256A[i] = registerMetaTileEntity(3040 + i,
                    new MetaTileEntityWirelessEnergyHatch(
                            gregtechId("wireless_energy_hatch.output_256a." + voltageName), i,
                            256, true));
            WIRELESS_OUTPUT_ENERGY_HATCH_1024A[i] = registerMetaTileEntity(3055 + i,
                    new MetaTileEntityWirelessEnergyHatch(
                            gregtechId("wireless_energy_hatch.output_1024a." + voltageName), i,
                            1024, true));
            WIRELESS_OUTPUT_ENERGY_HATCH_4096A[i] = registerMetaTileEntity(3070 + i,
                    new MetaTileEntityWirelessEnergyHatch(
                            gregtechId("wireless_energy_hatch.output_4096a." + voltageName), i,
                            4096, true));
            WIRELESS_OUTPUT_ENERGY_HATCH_16384A[i] = registerMetaTileEntity(3085 + i,
                    new MetaTileEntityWirelessEnergyHatch(
                            gregtechId("wireless_energy_hatch.output_16384a." + voltageName), i,
                            16384, true));
            WIRELESS_OUTPUT_ENERGY_HATCH_65536A[i] = registerMetaTileEntity(3100 + i,
                    new MetaTileEntityWirelessEnergyHatch(
                            gregtechId("wireless_energy_hatch.output_65536a." + voltageName), i,
                            65536, true));
            WIRELESS_OUTPUT_ENERGY_HATCH_262144A[i] = registerMetaTileEntity(3115 + i,
                    new MetaTileEntityWirelessEnergyHatch(
                            gregtechId("wireless_energy_hatch.output_262144a." + voltageName), i,
                            262144, true));
            WIRELESS_OUTPUT_ENERGY_HATCH_1048576A[i] = registerMetaTileEntity(3130 + i,
                    new MetaTileEntityWirelessEnergyHatch(
                            gregtechId("wireless_energy_hatch.output_1048576a." + voltageName),
                            i, 1048576, true));
        }
    }

    private static void registerMaintenanceHatches() {
        MAINTENANCE_HATCH = registerMetaTileEntity(3200,
                new MetaTileEntityMaintenanceHatch(gregtechId("maintenance_hatch"), false));
        CONFIGURABLE_MAINTENANCE_HATCH = registerMetaTileEntity(3201,
                new MetaTileEntityMaintenanceHatch(gregtechId("maintenance_hatch_configurable"), true));
        AUTO_MAINTENANCE_HATCH = registerMetaTileEntity(3202,
                new MetaTileEntityAutoMaintenanceHatch(gregtechId("maintenance_hatch_full_auto")));
        CLEANING_MAINTENANCE_HATCH = registerMetaTileEntity(3203,
                new MetaTileEntityCleaningMaintenanceHatch(gregtechId("maintenance_hatch_cleanroom_auto")));
        STERILE_CLEANING_MAINTENANCE_HATCH = registerMetaTileEntity(3204,
                new MetaTileEntitySterileCleaningMaintenanceHatch(
                        gregtechId("maintenance_hatch_sterile_cleanroom_auto")));
        ISO3_CLEANING_MAINTENANCE_HATCH = registerMetaTileEntity(3205,
                new MetaTileEntityISO3CleaningMaintenanceHatch(gregtechId("maintenance_hatch_iso_3_cleanroom_auto")));
        ISO2_CLEANING_MAINTENANCE_HATCH = registerMetaTileEntity(3206,
                new MetaTileEntityISO2CleaningMaintenanceHatch(gregtechId("maintenance_hatch_iso_2_cleanroom_auto")));
        ISO1_CLEANING_MAINTENANCE_HATCH = registerMetaTileEntity(3207,
                new MetaTileEntityISO1CleaningMaintenanceHatch(gregtechId("maintenance_hatch_iso_1_cleanroom_auto")));
    }

    private static void registerSpecialHatches() {
        PASSTHROUGH_HATCH_ITEM = registerMetaTileEntity(3210,
                new MetaTileEntityPassthroughHatchItem(gregtechId("passthrough_hatch_item"), 3));
        PASSTHROUGH_HATCH_FLUID = registerMetaTileEntity(3211,
                new MetaTileEntityPassthroughHatchFluid(gregtechId("passthrough_hatch_fluid"), 3));
        PASSTHROUGH_HATCH_LASER = registerMetaTileEntity(3212,
                new MetaTileEntityPassthroughHatchLaser(gregtechId("passthrough_hatch_laser"), 5));
        PASSTHROUGH_HATCH_COMPUTATION = registerMetaTileEntity(3213,
                new MetaTileEntityPassthroughHatchComputation(gregtechId("passthrough_hatch_computation"), 5));

        OPTICAL_DATA_HATCH_RECEIVER = registerMetaTileEntity(3215,
                new MetaTileEntityOpticalDataHatch(gregtechId("data_access_hatch.optical.receiver"), false));
        OPTICAL_DATA_HATCH_TRANSMITTER = registerMetaTileEntity(3216,
                new MetaTileEntityOpticalDataHatch(gregtechId("data_access_hatch.optical.transmitter"), true));
        OBJECT_HOLDER = registerMetaTileEntity(3217,
                new MetaTileEntityObjectHolder(gregtechId("research_station.object_holder")));

        HPCA_EMPTY_COMPONENT = registerMetaTileEntity(3220,
                new MetaTileEntityHPCAEmpty(gregtechId("hpca.empty_component")));
        HPCA_COMPUTATION_COMPONENT = registerMetaTileEntity(3221,
                new MetaTileEntityHPCAComputation(gregtechId("hpca.computation_component"), false));
        HPCA_ADVANCED_COMPUTATION_COMPONENT = registerMetaTileEntity(3222,
                new MetaTileEntityHPCAComputation(gregtechId("hpca.advanced_computation_component"), true));
        HPCA_HEAT_SINK_COMPONENT = registerMetaTileEntity(3223,
                new MetaTileEntityHPCACooler(gregtechId("hpca.heat_sink_component"), false));
        HPCA_ACTIVE_COOLER_COMPONENT = registerMetaTileEntity(3224,
                new MetaTileEntityHPCACooler(gregtechId("hpca.active_cooler_component"), true));
        HPCA_SUPER_COMPUTATION_COMPONENT = registerMetaTileEntity(3225,
                new MetaTileEntityHPCAAdvancedComputation(gregtechId("hpca.super_computation_component"), false));
        HPCA_ULTIMATE_COMPUTATION_COMPONENT = registerMetaTileEntity(3226,
                new MetaTileEntityHPCAAdvancedComputation(gregtechId("hpca.ultimate_computation_component"), true));
        HPCA_SUPER_COOLER_COMPONENT = registerMetaTileEntity(3227,
                new MetaTileEntityHPCAAdvancedCooler(gregtechId("hpca.super_cooler_component"), true, false));
        HPCA_ULTIMATE_COOLER_COMPONENT = registerMetaTileEntity(3228,
                new MetaTileEntityHPCAAdvancedCooler(gregtechId("hpca.ultimate_cooler_component"), false, true));
        HPCA_BRIDGE_COMPONENT = registerMetaTileEntity(3229,
                new MetaTileEntityHPCABridge(gregtechId("hpca.bridge_component")));

        DATA_ACCESS_HATCH[0] = registerMetaTileEntity(3230,
                new MetaTileEntityDataAccessHatch(gregtechId("data_access_hatch.i"), GTValues.MV, false));
        DATA_ACCESS_HATCH[1] = registerMetaTileEntity(3231,
                new MetaTileEntityDataAccessHatch(gregtechId("data_access_hatch.ii"), EV, false));
        DATA_ACCESS_HATCH[2] = registerMetaTileEntity(3232,
                new MetaTileEntityDataAccessHatch(gregtechId("data_access_hatch.iii"), GTValues.LuV, false));
        DATA_ACCESS_HATCH[3] = registerMetaTileEntity(3233,
                new MetaTileEntityDataAccessHatch(gregtechId("data_access_hatch.iv"), GTValues.UV, false));
        DATA_ACCESS_HATCH[4] = registerMetaTileEntity(3234,
                new MetaTileEntityDataAccessHatch(gregtechId("data_access_hatch.creative"), GTValues.MAX, true));

        for (int i = 0; i < COMPUTATION_HATCH_RECEIVER.length - 1; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            COMPUTATION_HATCH_RECEIVER[i] = registerMetaTileEntity(3240 + i,
                    new MetaTileEntityComputationHatch(gregtechId("computation_hatch.receiver." + voltageName), i + 1, false));
            COMPUTATION_HATCH_TRANSMITTER[i] = registerMetaTileEntity(3255 + i,
                    new MetaTileEntityComputationHatch(gregtechId("computation_hatch.transmitter." + voltageName), i + 1, true));
        }
        for (int i = 0; i < CLOUD_COMPUTATION_HATCH_UPLINK.length; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            CLOUD_COMPUTATION_HATCH_UPLINK[i] = registerMetaTileEntity(3270 + i,
                    new MetaTileEntityCloudComputationHatch(gregtechId("cloud_computation_hatch.uplink." + voltageName), i + 1, true));
            CLOUD_COMPUTATION_HATCH_DOWNLINK[i] = registerMetaTileEntity(3285 + i,
                    new MetaTileEntityCloudComputationHatch(gregtechId("cloud_computation_hatch.downlink." + voltageName), i + 1, false));
        }
        for (int i = 0; i < ROTOR_HOLDER.length; i++) {
            String voltageName = GTValues.VN[i + 3].toLowerCase();
            ROTOR_HOLDER[i] = registerMetaTileEntity(3300 + i,
                    new MetaTileEntityRotorHolder(gregtechId("rotor_holder." + voltageName), i + 3));
        }
        for (int i = 0; i < MUFFLER_HATCH.length - 1; i++) {
            int tier = i + 1;
            String voltageName = GTValues.VN[tier].toLowerCase();
            MUFFLER_HATCH[i] = registerMetaTileEntity(3315 + i,
                    new MetaTileEntityMufflerHatch(gregtechId("muffler_hatch." + voltageName), tier));
        }
        for (int i = 0; i < PARALLEL_HATCH.length - 1; i++) {
            int tier = i + 1;
            String voltageName = GTValues.VN[tier].toLowerCase();
            PARALLEL_HATCH[i] = registerMetaTileEntity(3330 + i,
                    new MetaTileEntityParallelHatch(gregtechId(String.format("parallel_hatch.%s", voltageName)), tier));
        }
        for (int i = 0; i < TIERED_HATCH.length - 1; i++) {
            int tier = i + 1;
            String voltageName = GTValues.VN[tier].toLowerCase();
            TIERED_HATCH[i] = registerMetaTileEntity(3345 + i,
                    new MetaTileEntityTieredHatch(gregtechId(String.format("tiered_hatch.%s", voltageName)), i));
        }
        for (int i = 0; i < GAS_HATCH.length - 1; i++) {
            int tier = i + 1;
            String voltageName = GTValues.VN[tier].toLowerCase();
            GAS_HATCH[i] = registerMetaTileEntity(3360 + i,
                    new MetaTileEntityGasHatch(gregtechId("gas_hatch." + voltageName), tier));
        }
        for (int i = 0; i < DUST_COLLECTOR.length - 1; i++) {
            int tier = i + 1;
            String voltageName = GTValues.VN[tier].toLowerCase();
            DUST_COLLECTOR[i] = registerMetaTileEntity(3375 + i,
                    new MetaTileEntityDustCollector(gregtechId("dust_collector." + voltageName), tier));
        }
        for (int i = 0; i < THREAD_HATCH.length - 1; i++) {
            int tier = i + 1;
            String voltageName = GTValues.VN[tier].toLowerCase();
            THREAD_HATCH[i] = registerMetaTileEntity(3390 + i, new MetaTileEntityThreadHatch(
                    gregtechId(String.format("thread_hatch.%s", voltageName)), tier));
        }
        for (int i = 0; i < OVERCLOCK_HATCH.length - 1; i++) {
            int tier = i + 1;
            if (tier < GTValues.UV) continue;
            String voltageName = GTValues.VN[tier].toLowerCase();
            OVERCLOCK_HATCH[i] = registerMetaTileEntity(3405 + tier - 8,
                    new MetaTileEntityOverclockHatch(gregtechId(String.format("overclock_hatch.%s", voltageName)), tier));
        }
        for (int i = 0; i < ACCELERATE_HATCH.length - 1; i++) {
            int tier = i + 1;
            String voltageName = GTValues.VN[tier].toLowerCase();
            ACCELERATE_HATCH[i] = registerMetaTileEntity(3415 + tier - 1,
                    new MetaTileEntityAccelerateHatch(gregtechId(String.format("accelerate_hatch.%s", voltageName)), tier));
        }

        for (int i = 0; i < HEAT_INPUT_HATCH.length - 1; i++) {
            String voltageName = GTValues.VN[i].toLowerCase();
            HEAT_INPUT_HATCH[i] = registerMetaTileEntity(3530 + i,
                    new MetaTileEntityHeatHatch(gregtechId("heat_input_hatch." + voltageName), i, false));
            HEAT_OUTPUT_HATCH[i] = registerMetaTileEntity(3545 + i,
                    new MetaTileEntityHeatHatch(gregtechId("heat_output_hatch." + voltageName), i, true));
            ELECTRIC_HEATER[i] = registerMetaTileEntity(3560 + i,
                    new MetaTileEntityElectricHeater(gregtechId("electric_heater." + voltageName), i));
        }

        HEAT_SENSOR = registerMetaTileEntity(3580, new MetaTileEntityHeatSensor(gregtechId("heat_sensor")));

        RESERVOIR_HATCH = registerMetaTileEntity(3585, new MetaTileEntityReservoirHatch(gregtechId("reservoir_hatch")));
        MACHINE_HATCH = registerMetaTileEntity(3586, new MetaTileEntityMachineHatch(gregtechId("machine_hatch"), 2));

        //

        FUEL_ROD_INPUT = registerMetaTileEntity(3600,
                new MetaTileEntityFuelRodImportBus(gregtechId("fuel_rod_input")));
        FUEL_ROD_OUTPUT = registerMetaTileEntity(3601,
                new MetaTileEntityFuelRodExportBus(gregtechId("fuel_rod_output")));
        COOLANT_INPUT = registerMetaTileEntity(3602,
                new MetaTileEntityCoolantImportHatch(gregtechId("coolant_input")));
        COOLANT_OUTPUT = registerMetaTileEntity(3603,
                new MetaTileEntityCoolantExportHatch(gregtechId("coolant_output")));

        CONTROL_ROD = registerMetaTileEntity(3605,
                new MetaTileEntityControlRodPort(gregtechId("control_rod"), false));
        CONTROL_ROD_MODERATED = registerMetaTileEntity(3606,
                new MetaTileEntityControlRodPort(gregtechId("control_rod_moderated"), true));
        MODERATOR_PORT = registerMetaTileEntity(3607,
                new MetaTileEntityModeratorPort(gregtechId("moderator_port")));
        NUCLEAR_EXTEND_HATCH = registerMetaTileEntity(3608,
                new MetaTileEntityNuclearExtend(gregtechId("nuclear_extend_hatch")));

        CREATIVE_PARALLEL_HATCH = registerMetaTileEntity(3900, new MetaTileEntityParallelHatch(
                gregtechId("creative_parallel_hatch"), GTValues.MAX, Integer.MAX_VALUE));
        CREATIVE_OVERCLOCK_HATCH = registerMetaTileEntity(3901, new MetaTileEntityOverclockHatch(
                gregtechId("creative_overclock_hatch"), GTValues.MAX, Integer.MAX_VALUE));
        CREATIVE_ACCELERATE_HATCH = registerMetaTileEntity(3902, new MetaTileEntityAccelerateHatch(
                gregtechId("creative_accelerate_hatch"), GTValues.MAX, 1));
        CREATIVE_THREAD_HATCH = registerMetaTileEntity(3903, new MetaTileEntityThreadHatch(
                gregtechId("creative_thread_hatch"), GTValues.MAX, Integer.MAX_VALUE));
        CREATIVE_INPUT_BUS = registerMetaTileEntity(3924,
                new MetaTileEntityCreativeInputBus(gregtechId("creative_input_bus")));
        CREATIVE_INPUT_HATCH = registerMetaTileEntity(3905,
                new MetaTileEntityCreativeInputHatch(gregtechId("creative_input_hatch")));
    }
}
