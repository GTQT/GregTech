package gregtech.common.metatileentities.registration;

import gregtech.api.GTValues;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.GTUtility;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.metatileentities.electric.MetaTileEntityGasCollector;
import gregtech.common.metatileentities.electric.MetaTileEntityMiner;
import gregtech.common.metatileentities.electric.MetaTileEntityRockBreaker;
import gregtech.common.metatileentities.electric.MetaTileEntitySingleCombustion;
import gregtech.common.metatileentities.electric.MetaTileEntitySingleTurbine;
import gregtech.common.metatileentities.electric.SimpleMachineMetaTileEntityResizable;
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

import static gregtech.api.GTValues.EV;
import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.common.metatileentities.MetaTileEntities.*;

public final class MachineRegistration {

    private MachineRegistration() {}

    public static void init() {
        registerSteamMachines();
        registerElectricMachines();
        registerGenerators();
    }

    private static void registerSteamMachines() {
        STEAM_BOILER_COAL_BRONZE = registerMetaTileEntity(1,
                new SteamCoalBoiler(gregtechId("steam_boiler_coal_bronze"), false));
        STEAM_BOILER_COAL_STEEL = registerMetaTileEntity(2,
                new SteamCoalBoiler(gregtechId("steam_boiler_coal_steel"), true));

        STEAM_BOILER_SOLAR_BRONZE = registerMetaTileEntity(3,
                new SteamSolarBoiler(gregtechId("steam_boiler_solar_bronze"), false));
        STEAM_BOILER_SOLAR_STEEL = registerMetaTileEntity(4,
                new SteamSolarBoiler(gregtechId("steam_boiler_solar_steel"), true));

        STEAM_BOILER_LAVA_BRONZE = registerMetaTileEntity(5,
                new SteamLavaBoiler(gregtechId("steam_boiler_lava_bronze"), false));
        STEAM_BOILER_LAVA_STEEL = registerMetaTileEntity(6,
                new SteamLavaBoiler(gregtechId("steam_boiler_lava_steel"), true));

        STEAM_EXTRACTOR_BRONZE = registerMetaTileEntity(7,
                new SteamExtractor(gregtechId("steam_extractor_bronze"), false));
        STEAM_EXTRACTOR_STEEL = registerMetaTileEntity(8,
                new SteamExtractor(gregtechId("steam_extractor_steel"), true));

        STEAM_MACERATOR_BRONZE = registerMetaTileEntity(9,
                new SteamMacerator(gregtechId("steam_macerator_bronze"), false));
        STEAM_MACERATOR_STEEL = registerMetaTileEntity(10,
                new SteamMacerator(gregtechId("steam_macerator_steel"), true));

        STEAM_COMPRESSOR_BRONZE = registerMetaTileEntity(11,
                new SteamCompressor(gregtechId("steam_compressor_bronze"), false));
        STEAM_COMPRESSOR_STEEL = registerMetaTileEntity(12,
                new SteamCompressor(gregtechId("steam_compressor_steel"), true));

        STEAM_HAMMER_BRONZE = registerMetaTileEntity(13, new SteamHammer(gregtechId("steam_hammer_bronze"), false));
        STEAM_HAMMER_STEEL = registerMetaTileEntity(14, new SteamHammer(gregtechId("steam_hammer_steel"), true));

        STEAM_FURNACE_BRONZE = registerMetaTileEntity(15, new SteamFurnace(gregtechId("steam_furnace_bronze"), false));
        STEAM_FURNACE_STEEL = registerMetaTileEntity(16, new SteamFurnace(gregtechId("steam_furnace_steel"), true));

        STEAM_ALLOY_SMELTER_BRONZE = registerMetaTileEntity(17,
                new SteamAlloySmelter(gregtechId("steam_alloy_smelter_bronze"), false));
        STEAM_ALLOY_SMELTER_STEEL = registerMetaTileEntity(18,
                new SteamAlloySmelter(gregtechId("steam_alloy_smelter_steel"), true));

        STEAM_ROCK_BREAKER_BRONZE = registerMetaTileEntity(19,
                new SteamRockBreaker(gregtechId("steam_rock_breaker_bronze"), false));
        STEAM_ROCK_BREAKER_STEEL = registerMetaTileEntity(20,
                new SteamRockBreaker(gregtechId("steam_rock_breaker_steel"), true));

        STEAM_VULCANIZING_PRESS_BRONZE = registerMetaTileEntity(21,
                new SteamVulcanizingPress(gregtechId("steam_vulcanizing_press_bronze"), false));
        STEAM_VULCANIZING_PRESS_STEEL = registerMetaTileEntity(22,
                new SteamVulcanizingPress(gregtechId("steam_vulcanizing_press_steel"), true));

        STEAM_ROASTER_BRONZE = registerMetaTileEntity(23,
                new SteamRoaster(gregtechId("steam_roaster_bronze"), false));
        STEAM_ROASTER_STEEL = registerMetaTileEntity(24,
                new SteamRoaster(gregtechId("steam_roaster_steel"), true));

        STEAM_MINER = registerMetaTileEntity(40, new SteamMiner(gregtechId("steam_miner"), 320, 4, 0));
    }

    private static void registerElectricMachines() {
        // Electric Furnace, IDs 50-64
        registerSimpleMetaTileEntity(ELECTRIC_FURNACE, 50, "electric_furnace", RecipeMaps.FURNACE_RECIPES,
                Textures.ELECTRIC_FURNACE_OVERLAY, true);

        // Macerator, IDs 65-79
        registerMetaTileEntities(MACERATOR, 65, "macerator",
                (tier, voltageName) -> new SimpleMachineMetaTileEntityResizable(
                        gregtechId(String.format("%s.%s", "macerator", voltageName)),
                        RecipeMaps.MACERATOR_RECIPES,
                        -1,
                        switch (tier) {
                            case 1, 2 -> 1;
                            case 3 -> 3;
                            default -> 4;
                        },
                        tier <= GTValues.MV ? Textures.MACERATOR_OVERLAY : Textures.PULVERIZER_OVERLAY,
                        tier,
                        true,
                        GTUtility.defaultTankSizeFunction,
                        VanillaParticleEffects.TOP_SMOKE_SMALL, null));

        // Alloy Smelter, IDs 80-94
        registerSimpleMetaTileEntity(ALLOY_SMELTER, 80, "alloy_smelter", RecipeMaps.ALLOY_SMELTER_RECIPES,
                Textures.ALLOY_SMELTER_OVERLAY, true);

        // Arc Furnace, IDs 95-109
        registerMetaTileEntities(ARC_FURNACE, 95, "arc_furnace",
                (tier, voltageName) -> new SimpleMachineMetaTileEntityResizable(
                        gregtechId(String.format("%s.%s", "arc_furnace", voltageName)),
                        RecipeMaps.ARC_FURNACE_RECIPES,
                        -1,
                        tier >= EV ? 9 : 4,
                        Textures.ARC_FURNACE_OVERLAY,
                        tier,
                        false,
                        GTUtility.hvCappedTankSizeFunction));

        // Assembler, IDs 110-124
        registerSimpleMetaTileEntity(ASSEMBLER, 110, "assembler", RecipeMaps.ASSEMBLER_RECIPES,
                Textures.ASSEMBLER_OVERLAY, true, GTUtility.hvCappedTankSizeFunction);

        // Autoclave, IDs 125-139
        registerSimpleMetaTileEntity(AUTOCLAVE, 125, "autoclave", RecipeMaps.AUTOCLAVE_RECIPES,
                Textures.AUTOCLAVE_OVERLAY, false, GTUtility.hvCappedTankSizeFunction);

        // Bender, IDs 140-154
        registerSimpleMetaTileEntity(BENDER, 140, "bender", RecipeMaps.BENDER_RECIPES, Textures.BENDER_OVERLAY, true);

        // Brewery, IDs 155-169
        registerSimpleMetaTileEntity(BREWERY, 155, "brewery", RecipeMaps.BREWING_RECIPES, Textures.BREWERY_OVERLAY,
                true, GTUtility.hvCappedTankSizeFunction);

        // Canner, IDs 170-184
        registerSimpleMetaTileEntity(CANNER, 170, "canner", RecipeMaps.CANNER_RECIPES, Textures.CANNER_OVERLAY, true);

        // Centrifuge, IDs 185-199
        registerSimpleMetaTileEntity(CENTRIFUGE, 185, "centrifuge", RecipeMaps.CENTRIFUGE_RECIPES,
                Textures.CENTRIFUGE_OVERLAY, false, GTUtility.largeTankSizeFunction);

        // Chemical Bath, IDs 200-214
        registerSimpleMetaTileEntity(CHEMICAL_BATH, 200, "chemical_bath", RecipeMaps.CHEMICAL_BATH_RECIPES,
                Textures.CHEMICAL_BATH_OVERLAY, true, GTUtility.hvCappedTankSizeFunction);

        // Chemical Reactor, IDs 215-229
        registerSimpleMetaTileEntity(CHEMICAL_REACTOR, 215, "chemical_reactor", RecipeMaps.CHEMICAL_RECIPES,
                Textures.CHEMICAL_REACTOR_OVERLAY, true, tier -> 16000);

        // Compressor, IDs 230-244
        registerSimpleMetaTileEntity(COMPRESSOR, 230, "compressor", RecipeMaps.COMPRESSOR_RECIPES,
                Textures.COMPRESSOR_OVERLAY, true);

        // Cutter, IDs 245-259
        registerSimpleMetaTileEntity(CUTTER, 245, "cutter", RecipeMaps.CUTTER_RECIPES, Textures.CUTTER_OVERLAY, true);

        // Distillery, IDs 260-274
        registerSimpleMetaTileEntity(DISTILLERY, 260, "distillery", RecipeMaps.DISTILLERY_RECIPES,
                Textures.DISTILLERY_OVERLAY, true, GTUtility.hvCappedTankSizeFunction);

        // Electrolyzer, IDs 275-289
        registerSimpleMetaTileEntity(ELECTROLYZER, 275, "electrolyzer", RecipeMaps.ELECTROLYZER_RECIPES,
                Textures.ELECTROLYZER_OVERLAY, false, GTUtility.largeTankSizeFunction);

        // Electromagnetic Separator, IDs 290-304
        registerSimpleMetaTileEntity(ELECTROMAGNETIC_SEPARATOR, 290, "electromagnetic_separator",
                RecipeMaps.ELECTROMAGNETIC_SEPARATOR_RECIPES, Textures.ELECTROMAGNETIC_SEPARATOR_OVERLAY, true);

        // Extractor, IDs 305-319
        registerSimpleMetaTileEntity(EXTRACTOR, 305, "extractor", RecipeMaps.EXTRACTOR_RECIPES,
                Textures.EXTRACTOR_OVERLAY, true);

        // Extruder, IDs 320-334
        registerSimpleMetaTileEntity(EXTRUDER, 320, "extruder", RecipeMaps.EXTRUDER_RECIPES, Textures.EXTRUDER_OVERLAY,
                true);

        // Fermenter, IDs 335-349
        registerSimpleMetaTileEntity(FERMENTER, 335, "fermenter", RecipeMaps.FERMENTING_RECIPES,
                Textures.FERMENTER_OVERLAY, true, GTUtility.hvCappedTankSizeFunction);

        // Mass Fabricator, IDs 350-364
        registerSimpleMetaTileEntity(MASS_FABRICATOR, 350, "mass_fabricator", RecipeMaps.MASS_FABRICATOR_RECIPES,
                Textures.MASS_FABRICATOR_OVERLAY, true);

        // Replicator, IDs 365-379
        registerSimpleMetaTileEntity(REPLICATOR, 365, "replicator", RecipeMaps.REPLICATOR_RECIPES,
                Textures.REPLICATOR_OVERLAY, true);

        // Fluid Heater, IDs 380-394
        registerSimpleMetaTileEntity(FLUID_HEATER, 380, "fluid_heater", RecipeMaps.FLUID_HEATER_RECIPES,
                Textures.FLUID_HEATER_OVERLAY, true, GTUtility.hvCappedTankSizeFunction);

        // Fluid Solidifier, IDs 395-409
        registerSimpleMetaTileEntity(FLUID_SOLIDIFIER, 395, "fluid_solidifier", RecipeMaps.FLUID_SOLIDFICATION_RECIPES,
                Textures.FLUID_SOLIDIFIER_OVERLAY, true, GTUtility.hvCappedTankSizeFunction);

        // Forge Hammer, IDs 410-424
        registerSimpleMetaTileEntity(FORGE_HAMMER, 410, "forge_hammer", RecipeMaps.FORGE_HAMMER_RECIPES,
                Textures.FORGE_HAMMER_OVERLAY, true);

        // Forming Press, IDs 425-439
        registerSimpleMetaTileEntity(FORMING_PRESS, 425, "forming_press", RecipeMaps.FORMING_PRESS_RECIPES,
                Textures.FORMING_PRESS_OVERLAY, true);

        // Lathe, IDs 440-454
        registerSimpleMetaTileEntity(LATHE, 440, "lathe", RecipeMaps.LATHE_RECIPES, Textures.LATHE_OVERLAY, true);

        // Scanner, IDs 455-469
        registerSimpleMetaTileEntity(SCANNER, 455, "scanner", RecipeMaps.SCANNER_RECIPES, Textures.SCANNER_OVERLAY,
                true);

        // Mixer, IDs 470-484
        registerSimpleMetaTileEntity(MIXER, 470, "mixer", RecipeMaps.MIXER_RECIPES, Textures.MIXER_OVERLAY, false,
                GTUtility.hvCappedTankSizeFunction);

        // Ore Washer, IDs 485-499
        registerSimpleMetaTileEntity(ORE_WASHER, 485, "ore_washer", RecipeMaps.ORE_WASHER_RECIPES,
                Textures.ORE_WASHER_OVERLAY, true);

        // Packer, IDs 500-514
        registerSimpleMetaTileEntity(PACKER, 500, "packer", RecipeMaps.PACKER_RECIPES, Textures.PACKER_OVERLAY, true);

        // UnPacker IDs 515-529
        registerSimpleMetaTileEntity(UNPACKER, 515, "unpacker", RecipeMaps.UNPACKER_RECIPES, Textures.UNPACKER_OVERLAY,
                true);

        // Gas Collectors, IDs 530-544
        registerMetaTileEntities(GAS_COLLECTOR, 530, "gas_collector",
                (tier, voltageName) -> new MetaTileEntityGasCollector(
                        gregtechId(String.format("%s.%s", "gas_collector", voltageName)),
                        RecipeMaps.GAS_COLLECTOR_RECIPES, Textures.GAS_COLLECTOR_OVERLAY, tier, false,
                        GTUtility.largeTankSizeFunction));

        // Polarizer, IDs 545-559
        registerSimpleMetaTileEntity(POLARIZER, 545, "polarizer", RecipeMaps.POLARIZER_RECIPES,
                Textures.POLARIZER_OVERLAY, true);

        // Laser Engraver, IDs 560-574
        registerSimpleMetaTileEntity(LASER_ENGRAVER, 560, "laser_engraver", RecipeMaps.LASER_ENGRAVER_RECIPES,
                Textures.LASER_ENGRAVER_OVERLAY, true);

        // Sifter, IDs 575-589
        registerSimpleMetaTileEntity(SIFTER, 575, "sifter", RecipeMaps.SIFTER_RECIPES, Textures.SIFTER_OVERLAY, true);

        // Polisher IDs 590-604
        registerSimpleMetaTileEntity(POLISHER, 590, "polisher", RecipeMaps.POLISHER_RECIPES, Textures.POLISHER_OVERLAY,
                true, GTUtility.hvCappedTankSizeFunction);

        // Thermal Centrifuge, IDs 605-619
        registerSimpleMetaTileEntity(THERMAL_CENTRIFUGE, 605, "thermal_centrifuge",
                RecipeMaps.THERMAL_CENTRIFUGE_RECIPES, Textures.THERMAL_CENTRIFUGE_OVERLAY, true);

        // Wire Mill, IDs 620-634
        registerSimpleMetaTileEntity(WIREMILL, 620, "wiremill", RecipeMaps.WIREMILL_RECIPES, Textures.WIREMILL_OVERLAY,
                true);

        // Circuit Assembler, IDs 635-664
        registerSimpleMetaTileEntity(CIRCUIT_ASSEMBLER, 635, "circuit_assembler", RecipeMaps.CIRCUIT_ASSEMBLER_RECIPES,
                Textures.CIRCUIT_ASSEMBLER_OVERLAY, true, GTUtility.hvCappedTankSizeFunction);

        // Rock Breaker, IDs 650-664
        registerMetaTileEntities(ROCK_BREAKER, 650, "rock_breaker",
                (tier, voltageName) -> new MetaTileEntityRockBreaker(
                        gregtechId(String.format("%s.%s", "rock_breaker", voltageName)),
                        RecipeMaps.ROCK_BREAKER_RECIPES, Textures.ROCK_BREAKER_OVERLAY, tier));

        // Laminator IDs 755-770
        registerSimpleMetaTileEntity(LAMINATOR, 755, "laminator", RecipeMaps.LAMINATOR_RECIPES,
                Textures.LAMINATOR_OVERLAY, true);

        // Polymerization Tank IDs 770-785
        registerSimpleMetaTileEntity(POLYMERIZATION_TANK, 770, "polymerization_tank", RecipeMaps.POLYMERIZATION_RECIPES,
                Textures.POLYMERIZATION_TANK_OVERLAY, true, GTUtility.hvCappedTankSizeFunction);

        // Desulfurizer IDs 785-800
        registerSimpleMetaTileEntity(DESULFURIZER, 785, "desulfurizer", RecipeMaps.DESULFURIZATION_RECIPES,
                Textures.DESULFURIZER_OVERLAY, true);

        // Bio Reactor IDs 800-815
        registerSimpleMetaTileEntity(BIO_REACTOR, 800, "bio_reactor", RecipeMaps.BIO_REACTOR_RECIPES,
                Textures.BIO_REACTOR_OVERLAY, true, GTUtility.defaultTankSizeFunction);

        // Component Assembler IDs 815-830
        registerSimpleMetaTileEntity(COMPONENT_ASSEMBLER, 815, "component_assembler",
                RecipeMaps.COMPONENT_ASSEMBLER_RECIPES, Textures.ASSEMBLER_OVERLAY, true,
                GTUtility.hvCappedTankSizeFunction);

        // Loom IDs 830-845
        registerSimpleMetaTileEntity(LOOM, 830, "loom", RecipeMaps.LOOM_RECIPES, Textures.LOOM_OVERLAY, true,
                GTUtility.defaultTankSizeFunction);

        // Roaster IDs 845-860
        registerSimpleMetaTileEntity(ROASTER, 845, "roaster", RecipeMaps.ROASTER_RECIPES, Textures.ROASTER_OVERLAY,
                true, GTUtility.defaultTankSizeFunction);

        // Chemical Dehydrator IDs 860-875
        registerSimpleMetaTileEntity(CHEMICAL_DEHYDRATOR, 860, "chemical_dehydrator",
                RecipeMaps.CHEMICAL_DEHYDRATOR_RECIPES, Textures.CHEMICAL_DEHYDRATOR_OVERLAY, true,
                GTUtility.defaultTankSizeFunction);

        // Lightning Processor IDs 875-890
        registerSimpleMetaTileEntity(LIGHTNING_PROCESSOR, 875, "lightning_processor",
                RecipeMaps.LIGHTNING_PROCESSOR_RECIPES, Textures.LIGHTNING_PROCESSOR_OVERLAY, true,
                GTUtility.defaultTankSizeFunction);

        // Recycler IDs 890-905
        registerSimpleMetaTileEntity(RECYCLER, 890, "recycler", RecipeMaps.RECYCLER_RECIPES, Textures.RECYCLER_OVERLAY,
                true);

        // Vulcanizing Press IDs 905-920
        registerSimpleMetaTileEntity(VULCANIZING_PRESS, 905, "vulcanizing_press", RecipeMaps.VULCANIZING_PRESS_RECIPES,
                Textures.VULCANIZING_PRESS_OVERLAY, true, GTUtility.defaultTankSizeFunction);

        // Chunk Miner, IDs 920-922
        MINER[0] = registerMetaTileEntity(920, new MetaTileEntityMiner(gregtechId("miner.lv"), 1, 160, 8, 1));
        MINER[1] = registerMetaTileEntity(921, new MetaTileEntityMiner(gregtechId("miner.mv"), 2, 80, 16, 2));
        MINER[2] = registerMetaTileEntity(922, new MetaTileEntityMiner(gregtechId("miner.hv"), 3, 40, 24, 3));
    }

    private static void registerGenerators() {
        // Diesel Generator, IDs 935-939
        registerSimpleGeneratorMetaTileEntity(COMBUSTION_GENERATOR, 935, "combustion_generator",
                RecipeMaps.COMBUSTION_GENERATOR_FUELS, Textures.COMBUSTION_GENERATOR_OVERLAY,
                MetaTileEntitySingleCombustion.class, GTUtility.genericGeneratorTankSizeFunction,
                GTUtility.genericGeneratorEfficiencyFunction);

        // Steam Turbine, IDs 940-944
        registerSimpleGeneratorMetaTileEntity(STEAM_TURBINE, 940, "steam_turbine",
                RecipeMaps.STEAM_TURBINE_FUELS, Textures.STEAM_TURBINE_OVERLAY,
                MetaTileEntitySingleTurbine.class, GTUtility.steamGeneratorTankSizeFunction,
                GTUtility.genericGeneratorEfficiencyFunction);

        // Gas Turbine, IDs 945-949
        registerSimpleGeneratorMetaTileEntity(GAS_TURBINE, 945, "gas_turbine",
                RecipeMaps.GAS_TURBINE_FUELS, Textures.GAS_TURBINE_OVERLAY,
                MetaTileEntitySingleTurbine.class, GTUtility.genericGeneratorTankSizeFunction,
                GTUtility.genericGeneratorEfficiencyFunction);

        // Semi-Fluid Generator, IDs 950-954
        registerSimpleGeneratorMetaTileEntity(SEMI_FLUID_GENERATOR, 950, "semi_fluid_generator",
                RecipeMaps.SEMI_FLUID_GENERATOR_FUELS, Textures.SEMI_FLUID_OVERLAY,
                MetaTileEntitySingleCombustion.class, GTUtility.genericGeneratorTankSizeFunction,
                GTUtility.genericGeneratorEfficiencyFunction);

        // Plasma Generator, IDs 955-959
        PLASMA_GENERATOR[0] = registerMetaTileEntity(955,
                new MetaTileEntitySingleTurbine(gregtechId("plasma_generator.ev"), RecipeMaps.PLASMA_GENERATOR_FUELS,
                        Textures.PLASMA_TURBINE_OVERLAY, 4, GTUtility.genericGeneratorTankSizeFunction, 1));
        PLASMA_GENERATOR[1] = registerMetaTileEntity(956,
                new MetaTileEntitySingleTurbine(gregtechId("plasma_generator.iv"), RecipeMaps.PLASMA_GENERATOR_FUELS,
                        Textures.PLASMA_TURBINE_OVERLAY, 5, GTUtility.genericGeneratorTankSizeFunction, 1));
        PLASMA_GENERATOR[2] = registerMetaTileEntity(957,
                new MetaTileEntitySingleTurbine(gregtechId("plasma_generator.luv"), RecipeMaps.PLASMA_GENERATOR_FUELS,
                        Textures.PLASMA_TURBINE_OVERLAY, 6, GTUtility.genericGeneratorTankSizeFunction, 1));
        PLASMA_GENERATOR[3] = registerMetaTileEntity(958,
                new MetaTileEntitySingleTurbine(gregtechId("plasma_generator.zpm"), RecipeMaps.PLASMA_GENERATOR_FUELS,
                        Textures.PLASMA_TURBINE_OVERLAY, 7, GTUtility.genericGeneratorTankSizeFunction, 1));
        PLASMA_GENERATOR[4] = registerMetaTileEntity(959,
                new MetaTileEntitySingleTurbine(gregtechId("plasma_generator.uv"), RecipeMaps.PLASMA_GENERATOR_FUELS,
                        Textures.PLASMA_TURBINE_OVERLAY, 8, GTUtility.genericGeneratorTankSizeFunction, 1));
    }
}
