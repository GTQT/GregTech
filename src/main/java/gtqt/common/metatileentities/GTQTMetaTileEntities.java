package gtqt.common.metatileentities;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.texture.Textures;

import gtqt.common.metatileentities.electric.MetaTileEntityDustCollector;
import gtqt.common.metatileentities.heat.MetaTileEntityElectricHeater;
import gtqt.common.metatileentities.heat.MetaTileEntityHeatHatch;
import gtqt.common.metatileentities.multi.MetaTileEntityLogisticsMaterialDistributor;
import gtqt.common.metatileentities.multi.multiblockpart.MetaTileEntityComplexDualHatch;
import gtqt.common.metatileentities.multi.multiblockpart.MetaTileEntityDualHatch;
import gtqt.common.metatileentities.multi.multiblockpart.MetaTileEntityHugeComplexDualHatch;
import gtqt.common.metatileentities.multi.multiblockpart.MetaTileEntityHugeDualHatch;
import gtqt.common.metatileentities.multi.multiblockpart.MetaTileEntityHugeItemBus;
import gtqt.common.metatileentities.multi.multiblockpart.MetaTileEntityMoldItemBus;
import gtqt.common.metatileentities.multi.multiblockpart.MetaTileEntityThreadHatch;
import gtqt.common.metatileentities.multi.multiblockpart.MetaTileEntityWirelessController;
import gtqt.common.metatileentities.multi.multiblockpart.MetaTileEntityWirelessEnergyHatch;
import gtqt.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityHugeMEOrePrefixPatternProvider;
import gtqt.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityHugeMEOrePrefixPatternProviderProxy;
import gtqt.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityHugeMEPatternProvider;
import gtqt.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityHugeMEPatternProviderProxy;
import gtqt.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEDualExportHatch;
import gtqt.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEDualInputHatch;
import gtqt.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEMufflerHatch;
import gtqt.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEOreDictBus;
import gtqt.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEOrePrefixPatternProvider;
import gtqt.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEOrePrefixPatternProviderProxy;
import gtqt.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEPatternManager;
import gtqt.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEPatternProvider;
import gtqt.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEPatternProviderProxy;

import static gregtech.api.GTValues.VN;
import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.api.util.Mods.Names.FORESTRY;
import static gregtech.api.util.Mods.Names.FTB_LIB;
import static gregtech.common.metatileentities.MetaTileEntities.registerMetaTileEntity;
import static net.minecraftforge.fml.common.Loader.isModLoaded;

public class GTQTMetaTileEntities {

    public static final MetaTileEntityDualHatch[] DUAL_IMPORT_HATCH = new MetaTileEntityDualHatch[GTValues.V.length - 2]; // All tiers but MAX
    public static final MetaTileEntityDualHatch[] DUAL_EXPORT_HATCH = new MetaTileEntityDualHatch[GTValues.V.length - 2];
    public static final MetaTileEntityComplexDualHatch[] COMPLEX_DUAL_HATCH = new MetaTileEntityComplexDualHatch[GTValues.V.length - 2];

    public static final MetaTileEntityHugeDualHatch[] HUGE_DUAL_IMPORT_HATCH = new MetaTileEntityHugeDualHatch[GTValues.V.length - 2]; // All tiers but MAX
    public static final MetaTileEntityHugeDualHatch[] HUGE_DUAL_EXPORT_HATCH = new MetaTileEntityHugeDualHatch[GTValues.V.length - 2];
    public static final MetaTileEntityHugeComplexDualHatch[] HUGE_COMPLEX_DUAL_HATCH = new MetaTileEntityHugeComplexDualHatch[GTValues.V.length - 2];

    public static final MetaTileEntityMEPatternProvider[] ME_PATTERN_PROVIDER = new MetaTileEntityMEPatternProvider[GTValues.V.length - 2];
    public static final MetaTileEntityHugeMEPatternProvider[] HUGE_ME_PATTERN_PROVIDER = new MetaTileEntityHugeMEPatternProvider[GTValues.V.length - 2];
    public static MetaTileEntityMEOrePrefixPatternProvider[] ME_ORE_PREFIX_PATTERN_PROVIDER = new MetaTileEntityMEOrePrefixPatternProvider[GTValues.V.length - 2];
    public static MetaTileEntityHugeMEOrePrefixPatternProvider[] HUGE_ME_ORE_PREFIX_PATTERN_PROVIDER = new MetaTileEntityHugeMEOrePrefixPatternProvider[GTValues.V.length - 2];


    public static MetaTileEntityThreadHatch[] THREAD_HATCH = new MetaTileEntityThreadHatch[GTValues.V.length-1];
    public static final MetaTileEntityMEMufflerHatch[] ME_MUFFLER_HATCH = new MetaTileEntityMEMufflerHatch[GTValues.UHV + 1]; // LV-UHV
    public static final MetaTileEntityDustCollector[] DUST_COLLECTOR = new MetaTileEntityDustCollector[GTValues.V.length - 1];
    public static final MetaTileEntityHeatHatch[] HEAT_INPUT_HATCH = new MetaTileEntityHeatHatch[5];
    public static final MetaTileEntityHeatHatch[] HEAT_OUTPUT_HATCH = new MetaTileEntityHeatHatch[5];
    public static final MetaTileEntityElectricHeater[] ELECTRIC_HEATER = new MetaTileEntityElectricHeater[5];

    public static MetaTileEntityMEDualInputHatch ME_DUAL_IMPORT_HATCH;
    public static MetaTileEntityMEDualExportHatch ME_DUAL_EXPORT_HATCH;
    public static MetaTileEntityMEPatternProviderProxy ME_PATTERN_PROVIDER_PROXY;
    public static MetaTileEntityHugeMEPatternProviderProxy HUGE_ME_PATTERN_PROVIDER_PROXY;
    public static MetaTileEntityMEOrePrefixPatternProviderProxy ME_ORE_PREFIX_PATTERN_PROVIDER_PROXY;
    public static MetaTileEntityHugeMEOrePrefixPatternProviderProxy HUGE_ME_ORE_PREFIX_PATTERN_PROVIDER_PROXY;
    public static MetaTileEntityMEOreDictBus ME_ORE_DICT_BUS;
    public static MetaTileEntityMEPatternManager ME_PATTERN_MANAGER;

    public static final MetaTileEntityHugeItemBus[] HUGE_ITEM_IMPORT_BUS = new MetaTileEntityHugeItemBus[GTValues.V.length - 1]; // All tiers but MAX
    public static final MetaTileEntityHugeItemBus[] HUGE_ITEM_EXPORT_BUS = new MetaTileEntityHugeItemBus[GTValues.V.length - 1]; // All tiers but MAX
    public static final MetaTileEntityMoldItemBus[] MOLD_ITEM_BUS = new MetaTileEntityMoldItemBus[GTValues.V.length - 1];

    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_4A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_4A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_16A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_16A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_64A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_64A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_256A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_256A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_1024A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_1024A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_4096A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_4096A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_16384A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_16384A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_65536A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_65536A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_262144A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_262144A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_INPUT_ENERGY_HATCH_1048576A = new MetaTileEntityWirelessEnergyHatch[15];
    public static final MetaTileEntityWirelessEnergyHatch[] WIRELESS_OUTPUT_ENERGY_HATCH_1048576A = new MetaTileEntityWirelessEnergyHatch[15];

    public static final MetaTileEntityWirelessController[] WIRELESS_CONTROLLERS = new MetaTileEntityWirelessController[15];
    public static final SimpleMachineMetaTileEntity[] BEE_ATTRACTORS = new SimpleMachineMetaTileEntity[15];

    public static MetaTileEntityLogisticsMaterialDistributor LOGISTICS_MATERIAL_DISTRIBUTOR;

    //从2500开始写 与gtceu本体共用一个注册表
    //任务：GTQT内不方便写的内容转移到这里来写
    //例如 高等级的能源仓 激光仓等等
    public static void initialization() {
        //总成类 maxTier-2
        for(int i=0;i<DUAL_IMPORT_HATCH.length;i++)
        {
            String voltageName = GTValues.VN[i+1].toLowerCase();
            DUAL_IMPORT_HATCH[i] = new MetaTileEntityDualHatch(gregtechId("dual_hatch.import." + voltageName), i+1, false);
            DUAL_EXPORT_HATCH[i] = new MetaTileEntityDualHatch(gregtechId("dual_hatch.export." + voltageName), i+1, true);
            ME_PATTERN_PROVIDER[i] = new MetaTileEntityMEPatternProvider(gregtechId("me_pattern_provider." + voltageName), i+1);
            ME_ORE_PREFIX_PATTERN_PROVIDER[i] = new MetaTileEntityMEOrePrefixPatternProvider(gregtechId("me_ore_prefix_pattern_provider." + voltageName), i+1);

            HUGE_DUAL_IMPORT_HATCH[i] = new MetaTileEntityHugeDualHatch(gregtechId("huge_dual_hatch.import." + voltageName), i+1, false);
            HUGE_DUAL_EXPORT_HATCH[i] = new MetaTileEntityHugeDualHatch(gregtechId("huge_dual_hatch.export." + voltageName), i+1, true);
            HUGE_ME_PATTERN_PROVIDER[i] = new MetaTileEntityHugeMEPatternProvider(gregtechId("huge_me_pattern_provider." + voltageName), i+1);
            HUGE_ME_ORE_PREFIX_PATTERN_PROVIDER[i] = new MetaTileEntityHugeMEOrePrefixPatternProvider(gregtechId("huge_me_ore_prefix_pattern_provider." + voltageName), i+1);

            COMPLEX_DUAL_HATCH[i]=new MetaTileEntityComplexDualHatch(gregtechId("complex_dual_hatch." + voltageName), i+1);
            HUGE_COMPLEX_DUAL_HATCH[i] = new MetaTileEntityHugeComplexDualHatch(gregtechId("huge_complex_dual_hatch." + voltageName), i+1);

            registerMetaTileEntity(2500 + i, DUAL_IMPORT_HATCH[i]);
            registerMetaTileEntity(2515 + i, DUAL_EXPORT_HATCH[i]);
            registerMetaTileEntity(2530 + i, ME_PATTERN_PROVIDER[i]);
            registerMetaTileEntity(2545 + i, ME_ORE_PREFIX_PATTERN_PROVIDER[i]);
            registerMetaTileEntity(2560 + i, COMPLEX_DUAL_HATCH[i]);

            registerMetaTileEntity(2600 + i, HUGE_DUAL_IMPORT_HATCH[i]);
            registerMetaTileEntity(2615 + i, HUGE_DUAL_EXPORT_HATCH[i]);
            registerMetaTileEntity(2630 + i, HUGE_ME_PATTERN_PROVIDER[i]);
            registerMetaTileEntity(2645 + i, HUGE_ME_ORE_PREFIX_PATTERN_PROVIDER[i]);
            registerMetaTileEntity(2660 + i, HUGE_COMPLEX_DUAL_HATCH[i]);
        }

        ME_DUAL_IMPORT_HATCH = new MetaTileEntityMEDualInputHatch(gregtechId("me_dual_hatch.import"));
        ME_DUAL_EXPORT_HATCH = new MetaTileEntityMEDualExportHatch(gregtechId("me_dual_hatch.export"));
        ME_PATTERN_PROVIDER_PROXY= new MetaTileEntityMEPatternProviderProxy(gregtechId("me_pattern_provider_proxy"));
        HUGE_ME_PATTERN_PROVIDER_PROXY = new MetaTileEntityHugeMEPatternProviderProxy(gregtechId("huge_me_pattern_provider_proxy"));

        ME_ORE_PREFIX_PATTERN_PROVIDER_PROXY = new MetaTileEntityMEOrePrefixPatternProviderProxy(gregtechId("me_ore_prefix_pattern_provider_proxy"));
        HUGE_ME_ORE_PREFIX_PATTERN_PROVIDER_PROXY = new MetaTileEntityHugeMEOrePrefixPatternProviderProxy(gregtechId("huge_me_ore_prefix_pattern_provider_proxy"));

        ME_ORE_DICT_BUS = new MetaTileEntityMEOreDictBus(gregtechId("me_ore_dict_bus"));
        ME_PATTERN_MANAGER = new MetaTileEntityMEPatternManager(gregtechId("me_pattern_manager"),6,false);

        registerMetaTileEntity(2700, ME_DUAL_IMPORT_HATCH);
        registerMetaTileEntity(2701, ME_DUAL_EXPORT_HATCH);
        registerMetaTileEntity(2702, ME_PATTERN_PROVIDER_PROXY);
        registerMetaTileEntity(2703, HUGE_ME_PATTERN_PROVIDER_PROXY);
        registerMetaTileEntity(2704, ME_ORE_PREFIX_PATTERN_PROVIDER_PROXY);
        registerMetaTileEntity(2705, HUGE_ME_ORE_PREFIX_PATTERN_PROVIDER_PROXY);

        registerMetaTileEntity(2750, ME_ORE_DICT_BUS);
        registerMetaTileEntity(2751, ME_PATTERN_MANAGER);

        //巨型总线
        for (int i = 0; i < 14; i++) {
            String voltageName = GTValues.VN[i].toLowerCase();
            HUGE_ITEM_IMPORT_BUS[i] = new MetaTileEntityHugeItemBus(
                    gregtechId("huge_item_bus.import." + voltageName), i, false);
            HUGE_ITEM_EXPORT_BUS[i] = new MetaTileEntityHugeItemBus(
                    gregtechId("huge_item_bus.export." + voltageName), i, true);
            MOLD_ITEM_BUS[i] = new MetaTileEntityMoldItemBus(gregtechId("mold_item_bus." + voltageName), i+1);


            registerMetaTileEntity(2800 + i, HUGE_ITEM_IMPORT_BUS[i]);
            registerMetaTileEntity(2815 + i, HUGE_ITEM_EXPORT_BUS[i]);
            registerMetaTileEntity(2830 + i, MOLD_ITEM_BUS[i]);
        }

        //线程仓
        for (int i = 0; i < THREAD_HATCH.length; i++) {
            int tier = i+1;
            THREAD_HATCH[i] = registerMetaTileEntity(2860 + i, new MetaTileEntityThreadHatch(
                    gregtechId(String.format("thread_hatch.%s", GTValues.VN[tier])), tier));
        }

        // ME Muffler Hatches, IDs 2875-2890
        for (int i = 0; i < ME_MUFFLER_HATCH.length; i++) {
            int tier = i+1;
            String voltageName = GTValues.VN[tier].toLowerCase();
            ME_MUFFLER_HATCH[i] = new MetaTileEntityMEMufflerHatch(gregtechId("me_muffler_hatch." + voltageName), tier);
            registerMetaTileEntity(2875 + i, ME_MUFFLER_HATCH[i]);
        }

        // Dust Collector, IDs 2890-2905
        for (int i = 0; i < DUST_COLLECTOR.length; i++) {
            String voltageName = GTValues.VN[i].toLowerCase();
            DUST_COLLECTOR[i] = new MetaTileEntityDustCollector(gregtechId("dust_collector." + voltageName), i);
            registerMetaTileEntity(2890 + i, DUST_COLLECTOR[i]);
        }

        // 热力输入输出仓
        for (int i = 0; i < HEAT_INPUT_HATCH.length; i++) {
            String voltageName = GTValues.VN[i].toLowerCase();
            HEAT_INPUT_HATCH[i] = new MetaTileEntityHeatHatch(gregtechId("heat_input_hatch." + voltageName), i, false);
            registerMetaTileEntity(2910 + i, HEAT_INPUT_HATCH[i]);
            HEAT_OUTPUT_HATCH[i] = new MetaTileEntityHeatHatch(gregtechId("heat_output_hatch." + voltageName), i, true);
            registerMetaTileEntity(2915 + i, HEAT_OUTPUT_HATCH[i]);
            ELECTRIC_HEATER[i] = new MetaTileEntityElectricHeater(gregtechId("electric_heater." + voltageName), i);
            registerMetaTileEntity(2920 + i, ELECTRIC_HEATER[i]);
        }

        if(isModLoaded(FORESTRY))
        {
            //引蜂器
            for (int i = 0; i < 15; i++) {
                String tier = VN[i].toLowerCase();
                BEE_ATTRACTORS[i] = registerMetaTileEntity(2930 + i,
                        new SimpleMachineMetaTileEntity(gregtechId("bee_attractor." + tier),
                                RecipeMaps.ATTRACTOR_RECIPES,
                                Textures.BEE_ATTRACTOR_OVERLAY, i, false));
            }
        }


        //无线能源仓注册 ID 3000+
        for (int i = 0; i < 15; i++) {
            String tier = VN[i].toLowerCase();
            //管理单元
            WIRELESS_CONTROLLERS[i] = registerMetaTileEntity(2980 + i, new MetaTileEntityWirelessController(gregtechId("wireless_controller." + tier), i));

            if(isModLoaded(FTB_LIB)) {
                WIRELESS_INPUT_ENERGY_HATCH[i] = registerMetaTileEntity(3000 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input." + tier), i, 2,
                                false));
                WIRELESS_INPUT_ENERGY_HATCH_4A[i] = registerMetaTileEntity(3000 + 15 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input_4a." + tier), i,
                                4, false));
                WIRELESS_INPUT_ENERGY_HATCH_16A[i] = registerMetaTileEntity(3000 + 30 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input_16a." + tier), i,
                                16, false));
                WIRELESS_INPUT_ENERGY_HATCH_64A[i] = registerMetaTileEntity(3000 + 45 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input_64a." + tier), i,
                                64, false));
                WIRELESS_INPUT_ENERGY_HATCH_256A[i] = registerMetaTileEntity(3000 + 60 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input_256a." + tier), i,
                                256, false));
                WIRELESS_INPUT_ENERGY_HATCH_1024A[i] = registerMetaTileEntity(3000 + 75 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input_1024a." + tier),
                                i, 1024, false));
                WIRELESS_INPUT_ENERGY_HATCH_4096A[i] = registerMetaTileEntity(3000 + 90 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input_4096a." + tier),
                                i, 4096, false));
                WIRELESS_INPUT_ENERGY_HATCH_16384A[i] = registerMetaTileEntity(3000 + 105 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input_16384a." + tier),
                                i, 16384, false));
                WIRELESS_INPUT_ENERGY_HATCH_65536A[i] = registerMetaTileEntity(3000 + 120 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input_65536a." + tier),
                                i, 65536, false));
                WIRELESS_INPUT_ENERGY_HATCH_262144A[i] = registerMetaTileEntity(3000 + 135 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input_262144a." + tier),
                                i, 262144, false));
                WIRELESS_INPUT_ENERGY_HATCH_1048576A[i] = registerMetaTileEntity(3000 + 150 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.input_1048576a." + tier),
                                i, 1048576, false));
                WIRELESS_OUTPUT_ENERGY_HATCH[i] = registerMetaTileEntity(3000 + 165 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.output." + tier), i, 2,
                                true));
                WIRELESS_OUTPUT_ENERGY_HATCH_4A[i] = registerMetaTileEntity(3000 + 180 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.output_4a." + tier), i,
                                4, true));
                WIRELESS_OUTPUT_ENERGY_HATCH_16A[i] = registerMetaTileEntity(3000 + 195 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.output_16a." + tier), i,
                                16, true));
                WIRELESS_OUTPUT_ENERGY_HATCH_64A[i] = registerMetaTileEntity(3000 + 210 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.output_64a." + tier), i,
                                64, true));
                WIRELESS_OUTPUT_ENERGY_HATCH_256A[i] = registerMetaTileEntity(3000 + 225 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.output_256a." + tier),
                                i, 256, true));
                WIRELESS_OUTPUT_ENERGY_HATCH_1024A[i] = registerMetaTileEntity(3000 + 240 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.output_1024a." + tier),
                                i, 1024, true));
                WIRELESS_OUTPUT_ENERGY_HATCH_4096A[i] = registerMetaTileEntity(3000 + 255 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.output_4096a." + tier),
                                i, 4096, true));
                WIRELESS_OUTPUT_ENERGY_HATCH_16384A[i] = registerMetaTileEntity(3000 + 270 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.output_16384a." + tier),
                                i, 16384, true));
                WIRELESS_OUTPUT_ENERGY_HATCH_65536A[i] = registerMetaTileEntity(3000 + 285 + i,
                        new MetaTileEntityWirelessEnergyHatch(gregtechId("wireless_energy_hatch.output_65536a." + tier),
                                i, 65536, true));
                WIRELESS_OUTPUT_ENERGY_HATCH_262144A[i] = registerMetaTileEntity(3000 + 300 + i,
                        new MetaTileEntityWirelessEnergyHatch(
                                gregtechId("wireless_energy_hatch.output_262144a." + tier), i, 262144, true));
                WIRELESS_OUTPUT_ENERGY_HATCH_1048576A[i] = registerMetaTileEntity(3000 + 315 + i,
                        new MetaTileEntityWirelessEnergyHatch(
                                gregtechId("wireless_energy_hatch.output_1048576a." + tier), i, 1048576, true));
            }
        }

        //5000+
        LOGISTICS_MATERIAL_DISTRIBUTOR = registerMetaTileEntity(5000,
                new MetaTileEntityLogisticsMaterialDistributor(gregtechId("logistics_material_distributor")));
    }
}
