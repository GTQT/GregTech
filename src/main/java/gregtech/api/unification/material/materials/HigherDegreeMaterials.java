package gregtech.api.unification.material.materials;

import gregtech.api.GTValues;
import gregtech.api.fluids.FluidBuilder;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.BlastProperty.GasTier;
import gregtech.api.unification.material.properties.MaterialToolProperty;

import static gregtech.api.GTValues.*;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.material.info.MaterialFlags.*;
import static gregtech.api.unification.material.info.MaterialIconSet.*;
import static gregtech.api.util.GTUtility.gregtechId;

public class HigherDegreeMaterials {

    public static void register() {
        Electrotine = Material.builder(2507, gregtechId("electrotine"))
                .dust().ore(5, 1, true)
                .color(0x3CB4C8).iconSet(SHINY)
                .flags(DISABLE_DECOMPOSITION)
                .components(Redstone, 1, Electrum, 1)
                .build();

        EnderEye = Material.builder(2508, gregtechId("ender_eye"))
                .gem(1)
                .color(0x66FF66)
                .flags(GENERATE_PLATE, NO_SMASHING, NO_SMELTING, DECOMPOSITION_BY_CENTRIFUGING)
                .build();

        Diatomite = Material.builder(2509, gregtechId("diatomite"))
                .dust(1).ore()
                .color(0xE1E1E1)
                .components(Flint, 8, BandedIron, 1, Sapphire, 1)
                .build();

        RedSteel = Material.builder(2510, gregtechId("red_steel"))
                .ingot(3).fluid()
                .color(0x8C6464).iconSet(METALLIC)
                .flags(EXT_METAL)
                .components(RoseGold, 1, Brass, 1, Steel, 2, BlackSteel, 4)
                .toolStats(MaterialToolProperty.Builder.of(7.0F, 6.0F, 2560, 3)
                        .attackSpeed(0.1F).enchantability(21).build())
                .blast(b -> b.temp(1813, GasTier.LOW).blastStats(VA[HV], 1000))
                .build();

        BlueSteel = Material.builder(2511, gregtechId("blue_steel"))
                .ingot(3).fluid()
                .color(0x64648C).iconSet(METALLIC)
                .flags(EXT_METAL)
                .components(SterlingSilver, 1, BismuthBronze, 1, Steel, 2, BlackSteel, 4)
                .toolStats(MaterialToolProperty.Builder.of(15.0F, 6.0F, 1024, 3)
                        .attackSpeed(0.1F).enchantability(33).build())
                .blast(b -> b.temp(1813, GasTier.LOW).blastStats(VA[HV], 1000))
                .build();

        Basalt = Material.builder(2512, gregtechId("basalt"))
                .dust(1)
                .color(0x3C3232).iconSet(ROUGH)
                .flags(NO_SMASHING, DECOMPOSITION_BY_CENTRIFUGING)
                .components(Olivine, 1, Calcite, 3, Flint, 8, DarkAsh, 4)
                .build();

        GraniticMineralSand = Material.builder(2513, gregtechId("granitic_mineral_sand"))
                .dust(1).ore()
                .color(0x283C3C).iconSet(SAND)
                .components(Magnetite, 1, GraniteBlack, 1)
                .build();

        Redrock = Material.builder(2514, gregtechId("redrock"))
                .dust(1)
                .color(0xFF5032).iconSet(ROUGH)
                .flags(NO_SMASHING, DECOMPOSITION_BY_CENTRIFUGING)
                .components(Calcite, 2, Flint, 1)
                .build();

        GarnetSand = Material.builder(2515, gregtechId("garnet_sand"))
                .dust(1).ore()
                .color(0xC86400).iconSet(SAND)
                .flags(DECOMPOSITION_BY_CENTRIFUGING)
                .components(Almandine, 1, Andradite, 1, Grossular, 1, Pyrope, 1, Spessartine, 1, Uvarovite, 1)
                .build();

        HSSG = Material.builder(2516, gregtechId("hssg"))
                .ingot(3).fluid()
                .color(0x999900).iconSet(METALLIC)
                .flags(EXT2_METAL, GENERATE_SMALL_GEAR, GENERATE_FRAME, GENERATE_SPRING, GENERATE_FINE_WIRE,
                        GENERATE_FOIL, GENERATE_GEAR)
                .components(TungstenSteel, 5, Chrome, 1, Molybdenum, 2, Vanadium, 1)
                .rotorStats(10.0f, 5.5f, 4000)
                .cableProperties(V[LuV], 4, 2)
                .blast(b -> b
                        .temp(4200, GasTier.MID)
                        .blastStats(VA[EV], 1300)
                        .vacuumStats(VA[HV]))
                .build();

        RedAlloy = Material.builder(2517, gregtechId("red_alloy"))
                .ingot(0)
                .liquid(new FluidBuilder().temperature(1400))
                .color(0xC80000)
                .flags(STD_METAL,DISABLE_DECOMPOSITION)
                .components(Copper, 1, Redstone, 4)
                .cableProperties(V[ULV], 1, 0)
                .build();

        BasalticMineralSand = Material.builder(2518, gregtechId("basaltic_mineral_sand"))
                .dust(1).ore()
                .color(0x283228).iconSet(SAND)
                .components(Magnetite, 1, Basalt, 1)
                .build();

        HSSE = Material.builder(2519, gregtechId("hsse"))
                .ingot(4).fluid()
                .color(0x336600).iconSet(METALLIC)
                .flags(EXT2_METAL)
                .components(HSSG, 6, Cobalt, 1, Manganese, 1, Silicon, 1)
                .toolStats(MaterialToolProperty.Builder.of(5.0F, 10.0F, 3072, 4)
                        .attackSpeed(0.3F).enchantability(33).build())
                .rotorStats(10.0f, 8.0f, 5120)
                .blast(b -> b
                        .temp(5000, GasTier.HIGH)
                        .blastStats(VA[EV], 1400)
                        .vacuumStats(VA[HV]))
                .build();

        HSSS = Material.builder(2520, gregtechId("hsss"))
                .ingot(4).fluid()
                .color(0x660033).iconSet(METALLIC)
                .flags(EXT2_METAL)
                .components(HSSG, 6, Iridium, 2, Osmium, 1)
                .rotorStats(15.0f, 7.0f, 3000)
                .blast(b -> b
                        .temp(5000, GasTier.HIGH)
                        .blastStats(VA[EV], 1500)
                        .vacuumStats(VA[EV], 200))
                .build();

        // FREE ID: 2521

        IridiumMetalResidue = Material.builder(2522, gregtechId("iridium_metal_residue"))
                .dust()
                .color(0x5C5D68).iconSet(METALLIC)
                .flags(DISABLE_DECOMPOSITION)
                .components(Iridium, 1, Chlorine, 3, PlatinumSludgeResidue, 1)
                .build();
        IridiumMetalResidue.setFormula("Ir2O4(SiO2)2Au3");

        Granite = Material.builder(2523, gregtechId("granite"))
                .dust()
                .color(0xCFA18C).iconSet(ROUGH)
                .flags(DECOMPOSITION_BY_CENTRIFUGING)
                .components(SiliconDioxide, 4, Redrock, 1)
                .build();

        Brick = Material.builder(2524, gregtechId("brick"))
                .dust()
                .color(0x9B5643).iconSet(ROUGH)
                .flags(EXCLUDE_BLOCK_CRAFTING_RECIPES, NO_SMELTING, DECOMPOSITION_BY_CENTRIFUGING)
                .components(Clay, 1)
                .build();

        Fireclay = Material.builder(2525, gregtechId("fireclay"))
                .dust()
                .color(0xADA09B).iconSet(ROUGH)
                .flags(DECOMPOSITION_BY_CENTRIFUGING, NO_SMELTING)
                .components(Clay, 1, Brick, 1)
                .build();

        Diorite = Material.builder(2526, gregtechId("diorite"))
                .dust()
                .iconSet(ROUGH)
                .flags(DECOMPOSITION_BY_CENTRIFUGING)
                .components(Mirabilite, 2, Clay, 7)
                .build();

        BlueAlloy = Material.builder(2527, gregtechId("blue_alloy"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1400))
                .color(0x64B4FF).iconSet(DULL)
                .flags(DISABLE_DECOMPOSITION)
                .components(Electrotine, 4, Silver, 1)
                .cableProperties(GTValues.V[GTValues.HV], 2, 1)
                .build();

        //冷却液 水+9青金石
        WaterCoolant = Material.builder(2528, gregtechId("water_coolant"))
                .fluid()
                .color(0x0000FF)
                .flags(DISABLE_DECOMPOSITION)
                .components(Water, 1, Lapis, 9)
                .build();

        //热冷却液
        HotWaterCoolant = Material.builder(2529, gregtechId("hot_water_coolant"))
                .fluid()
                .color(0xFF0000)
                .flags(DISABLE_DECOMPOSITION)
                .components(Water, 1, Lapis, 9)
                .build();

        // ==================== Forge of the Gods Materials ====================

        // Dimensionally Transcendent Residue (DTR) - primary godforge fuel
        DimensionallyTranscendentResidue = Material.builder(2530, gregtechId("dimensionally_transcendent_residue"))
                .fluid()
                .color(0x1A0033).iconSet(DULL)
                .flags(DISABLE_DECOMPOSITION)
                .build();

        // Raw Star Matter - secondary godforge fuel
        RawStarMatter = Material.builder(2531, gregtechId("raw_star_matter"))
                .fluid()
                .color(0xFFFF99).iconSet(SHINY)
                .flags(DISABLE_DECOMPOSITION)
                .build();

        // Magneto-Hydrodynamically Constrained Star Matter (MHDCSM) - tertiary godforge fuel (molten)
        MagnetoHydrodynamicallyConstrainedStarMatter = Material.builder(2532,
                        gregtechId("magneto_hydrodynamically_constrained_star_matter"))
                .ingot().fluid()
                .color(0xE6E6FF).iconSet(METALLIC)
                .flags(DISABLE_DECOMPOSITION, NO_SMELTING)
                .build();

        // Graviton Shard - godforge upgrade currency
        GravitonShard = Material.builder(2533, gregtechId("graviton_shard"))
                .gem(1)
                .color(0x9933FF).iconSet(DIAMOND)
                .flags(DISABLE_DECOMPOSITION, NO_SMELTING)
                .build();
    }
}
