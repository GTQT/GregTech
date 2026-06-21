package gregtech.api.unification.material.materials;

import gregtech.api.fluids.FluidBuilder;
import gregtech.api.fluids.attribute.FluidAttributes;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.info.MaterialIconSet;
import gregtech.api.unification.material.properties.BlastProperty;
import gregtech.api.unification.material.properties.BlastProperty.GasTier;
import gregtech.api.unification.material.properties.MaterialToolProperty;
import gregtech.api.unification.material.properties.PropertyKey;

import net.minecraft.init.Enchantments;

import static gregtech.api.GTValues.*;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.material.info.MaterialFlags.*;
import static gregtech.api.unification.material.info.MaterialIconSet.*;
import static gregtech.api.util.GCYMUtil.gcymId;
import static gregtech.api.util.GTUtility.gregtechId;

public class SecondDegreeMaterials {

    public static void register() {
        Glass = Material.builder(2000, gregtechId("glass"))
                .gem(0)
                .liquid(new FluidBuilder().temperature(1200).customStill())
                .color(0xFAFAFA).iconSet(GLASS)
                .flags(GENERATE_LENS, NO_SMASHING, EXCLUDE_BLOCK_CRAFTING_RECIPES, DECOMPOSITION_BY_CENTRIFUGING)
                .components(SiliconDioxide, 1)
                .build();

        Perlite = Material.builder(2001, gregtechId("perlite"))
                .dust(1)
                .color(0x1E141E)
                .components(Obsidian, 2, Water, 1)
                .build();

        Borax = Material.builder(2002, gregtechId("borax"))
                .dust(1)
                .color(0xFAFAFA).iconSet(FINE)
                .components(Sodium, 2, Boron, 4, Water, 10, Oxygen, 7)
                .build();

        SaltWater = Material.builder(2003, gregtechId("salt_water"))
                .fluid()
                .color(0x0000C8)
                .flags(DISABLE_DECOMPOSITION)
                .components(Salt, 1, Water, 1)
                .build();

        Olivine = Material.builder(2004, gregtechId("olivine"))
                .gem().ore(2, 1)
                .color(0x96FF96).iconSet(RUBY)
                .flags(NO_SMASHING, NO_SMELTING, HIGH_SIFTER_OUTPUT)
                .components(Magnesium, 2, Iron, 1, SiliconDioxide, 2)
                .build();

        Opal = Material.builder(2005, gregtechId("opal"))
                .gem().ore()
                .color(0x0000FF).iconSet(OPAL)
                .flags(NO_SMASHING, NO_SMELTING, HIGH_SIFTER_OUTPUT, DECOMPOSITION_BY_CENTRIFUGING)
                .components(SiliconDioxide, 1)
                .build();

        Amethyst = Material.builder(2006, gregtechId("amethyst"))
                .gem(3).ore()
                .color(0xD232D2).iconSet(RUBY)
                .flags(NO_SMASHING, NO_SMELTING, HIGH_SIFTER_OUTPUT)
                .components(SiliconDioxide, 4, Iron, 1)
                .build();

        Lapis = Material.builder(2007, gregtechId("lapis"))
                .gem(1).ore(6, 4)
                .color(0x4646DC).iconSet(LAPIS)
                .flags(NO_SMASHING, NO_SMELTING, CRYSTALLIZABLE, NO_WORKING, DECOMPOSITION_BY_ELECTROLYZING,
                        EXCLUDE_BLOCK_CRAFTING_BY_HAND_RECIPES,
                        GENERATE_PLATE, GENERATE_ROD)
                .components(Lazurite, 12, Sodalite, 2, Pyrite, 1, Calcite, 1)
                .build();

        Blaze = Material.builder(2008, gregtechId("blaze"))
                .dust(1)
                .liquid(new FluidBuilder().temperature(4000).customStill())
                .color(0xFFC800).iconSet(FINE)
                .flags(NO_SMELTING, MORTAR_GRINDABLE, DECOMPOSITION_BY_CENTRIFUGING) // todo burning flag
                .components(DarkAsh, 1, Sulfur, 1)
                .build();

        // Free ID 2009

        Apatite = Material.builder(2010, gregtechId("apatite"))
                .gem(1).ore(2, 2)
                .color(0xC8C8FF).iconSet(DIAMOND)
                .flags(NO_SMASHING, NO_SMELTING, CRYSTALLIZABLE, GENERATE_BOLT_SCREW)
                .components(Calcium, 5, Phosphorus, 3, Oxygen, 12, Chlorine, 1)
                .build()
                .setFormula("Ca5(PO4)3Cl", true);

        BlackSteel = Material.builder(2011, gregtechId("black_steel"))
                .ingot().fluid()
                .color(0x646464).iconSet(METALLIC)
                .components(Nickel, 1, BlackBronze, 1, Steel, 3)
                .cableProperties(V[EV], 3, 2)
                .blast(1758, GasTier.LOW)
                .build();

        DamascusSteel = Material.builder(2012, gregtechId("damascus_steel"))
                .ingot(3).fluid()
                .color(0x6E6E6E).iconSet(METALLIC)
                .components(Steel, 1)
                .toolStats(MaterialToolProperty.Builder.of(6.0F, 4.0F, 1024, 3)
                        .attackSpeed(0.3F).enchantability(33)
                        .enchantment(Enchantments.LOOTING, 3)
                        .enchantment(Enchantments.FORTUNE, 3).build())
                .blast(1500, GasTier.LOW)
                .build();

        TungstenSteel = Material.builder(2013, gregtechId("tungsten_steel"))
                .ingot(4).fluid()
                .color(0x6464A0).iconSet(METALLIC)
                .flags(GENERATE_SHEET, GENERATE_CURVED_PLATE)
                .components(Steel, 1, Tungsten, 1)
                .toolStats(MaterialToolProperty.Builder.of(9.0F, 7.0F, 2048, 4)
                        .enchantability(14).build())
                .rotorStats(8.0f, 4.0f, 2560)
                .fluidPipeProperties(3587, 225, true, true, false, false)
                .cableProperties(V[IV], 3, 2)
                .blast(b -> b
                        .temp(4000, GasTier.MID)
                        .blastStats(VA[EV], 1000)
                        .vacuumStats(VA[HV]))
                .build();

        CobaltBrass = Material.builder(2014, gregtechId("cobalt_brass"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1202))
                .color(0xB4B4A0).iconSet(METALLIC)
                .components(Brass, 7, Aluminium, 1, Cobalt, 1)
                .toolStats(MaterialToolProperty.Builder.of(2.5F, 2.0F, 1024, 2)
                        .attackSpeed(-0.2F).enchantability(5).build())
                .rotorStats(8.0f, 2.0f, 256)
                .itemPipeProperties(2048, 1)
                .build();

        TricalciumPhosphate = Material.builder(2015, gregtechId("tricalcium_phosphate"))
                .dust().ore(3, 1)
                .color(0xFFFF00).iconSet(FLINT)
                .flags(NO_SMASHING, NO_SMELTING, FLAMMABLE, EXPLOSIVE)
                .components(Calcium, 3, Phosphorus, 2, Oxygen, 8)
                .build()
                .setFormula("Ca3(PO4)2", true);

        GarnetRed = Material.builder(2016, gregtechId("garnet_red"))
                .gem().ore(4, 1)
                .color(0xC85050).iconSet(RUBY)
                .flags(NO_SMASHING, NO_SMELTING, HIGH_SIFTER_OUTPUT, DECOMPOSITION_BY_CENTRIFUGING)
                .components(Pyrope, 3, Almandine, 5, Spessartine, 8)
                .build();

        GarnetYellow = Material.builder(2017, gregtechId("garnet_yellow"))
                .gem().ore(4, 1)
                .color(0xC8C850).iconSet(RUBY)
                .flags(NO_SMASHING, NO_SMELTING, HIGH_SIFTER_OUTPUT, DECOMPOSITION_BY_CENTRIFUGING)
                .components(Andradite, 5, Grossular, 8, Uvarovite, 3)
                .build();

        Marble = Material.builder(2018, gregtechId("marble"))
                .dust()
                .color(0xC8C8C8).iconSet(ROUGH)
                .flags(NO_SMASHING, DECOMPOSITION_BY_CENTRIFUGING)
                .components(Magnesium, 1, Calcite, 7)
                .build();

        GraniteBlack = Material.builder(2019, gregtechId("granite_black"))
                .dust()
                .color(0x0A0A0A).iconSet(ROUGH)
                .flags(NO_SMASHING, DECOMPOSITION_BY_CENTRIFUGING)
                .components(SiliconDioxide, 4, Biotite, 1)
                .build();

        GraniteRed = Material.builder(2020, gregtechId("granite_red"))
                .dust()
                .color(0xFF0080).iconSet(ROUGH)
                .flags(NO_SMASHING)
                .components(Aluminium, 2, PotassiumFeldspar, 1, Oxygen, 3)
                .build();

        // Free ID 2021

        VanadiumMagnetite = Material.builder(2022, gregtechId("vanadium_magnetite"))
                .dust().ore()
                .color(0x23233C).iconSet(METALLIC)
                .flags(DECOMPOSITION_BY_CENTRIFUGING)
                .components(Magnetite, 1, Vanadium, 1)
                .build();

        QuartzSand = Material.builder(2023, gregtechId("quartz_sand"))
                .dust(1)
                .color(0xC8C8C8).iconSet(SAND)
                .flags(DISABLE_DECOMPOSITION)
                .components(CertusQuartz, 1, Quartzite, 1)
                .build();

        Pollucite = Material.builder(2024, gregtechId("pollucite"))
                .dust().ore()
                .color(0xF0D2D2)
                .components(Caesium, 2, Aluminium, 2, Silicon, 4, Water, 2, Oxygen, 12)
                .build();

        // Free ID 2025

        Bentonite = Material.builder(2026, gregtechId("bentonite"))
                .dust().ore(3, 1)
                .color(0xF5D7D2).iconSet(ROUGH)
                .flags(DISABLE_DECOMPOSITION)
                .components(Sodium, 1, Magnesium, 6, Silicon, 12, Hydrogen, 4, Water, 5, Oxygen, 36)
                .build();

        FullersEarth = Material.builder(2027, gregtechId("fullers_earth"))
                .dust().ore(2, 1)
                .color(0xA0A078).iconSet(FINE)
                .components(Magnesium, 1, Silicon, 4, Hydrogen, 1, Water, 4, Oxygen, 11)
                .build();

        Pitchblende = Material.builder(2028, gregtechId("pitchblende"))
                .dust(3).ore(true)
                .color(0xC8D200)
                .flags(DECOMPOSITION_BY_CENTRIFUGING)
                .components(Uraninite, 3, Thorium, 1, Lead, 1)
                .build()
                .setFormula("(UO2)3ThPb", true);

        Monazite = Material.builder(2029, gregtechId("monazite"))
                .gem(1).ore(4, 2, true)
                .color(0x324632).iconSet(DIAMOND)
                .flags(NO_SMASHING, NO_SMELTING, CRYSTALLIZABLE, DECOMPOSITION_BY_CENTRIFUGING)
                .components(RareEarth, 1, Phosphorus, 1)
                .build();

        Mirabilite = Material.builder(2030, gregtechId("mirabilite"))
                .dust()
                .color(0xF0FAD2)
                .components(Sodium, 2, Sulfur, 1, Water, 10, Oxygen, 4)
                .build();

        Trona = Material.builder(2031, gregtechId("trona"))
                .dust(1).ore(2, 1)
                .color(0x87875F).iconSet(METALLIC)
                .flags(DISABLE_DECOMPOSITION)
                .components(Sodium, 3, Carbon, 2, Hydrogen, 1, Water, 2, Oxygen, 6)
                .build();

        Gypsum = Material.builder(2032, gregtechId("gypsum"))
                .dust(1).ore()
                .color(0xE6E6FA)
                .components(Calcium, 1, Sulfur, 1, Water, 2, Oxygen, 4)
                .build();

        Zeolite = Material.builder(2033, gregtechId("zeolite"))
                .dust().ore(3, 1)
                .color(0xF0E6E6)
                .flags(DISABLE_DECOMPOSITION)
                .components(Sodium, 1, Calcium, 4, Silicon, 27, Aluminium, 9, Water, 28, Oxygen, 72)
                .build();

        Concrete = Material.builder(2034, gregtechId("concrete"))
                .dust()
                .liquid(new FluidBuilder().temperature(286))
                .color(0x646464).iconSet(ROUGH)
                .flags(NO_SMASHING, EXCLUDE_BLOCK_CRAFTING_BY_HAND_RECIPES)
                .components(Stone, 1)
                .build();

        SteelMagnetic = Material.builder(2035, gregtechId("steel_magnetic"))
                .ingot()
                .color(0x808080).iconSet(MAGNETIC)
                .flags(IS_MAGNETIC)
                .components(Steel, 1)
                .ingotSmeltInto(Steel)
                .arcSmeltInto(Steel)
                .macerateInto(Steel)
                .build();
        Steel.getProperty(PropertyKey.INGOT).setMagneticMaterial(SteelMagnetic);

        VanadiumSteel = Material.builder(2036, gregtechId("vanadium_steel"))
                .ingot(3)
                .liquid(new FluidBuilder().temperature(2073))
                .color(0xc0c0c0).iconSet(METALLIC)
                .components(Vanadium, 1, Chrome, 1, Steel, 7)
                .toolStats(MaterialToolProperty.Builder.of(3.0F, 3.0F, 1536, 3)
                        .attackSpeed(-0.2F).enchantability(5).build())
                .rotorStats(7.0f, 3.0f, 1920)
                .fluidPipeProperties(2073, 50, true, true, false, false)
                .blast(1453, GasTier.LOW)
                .build();

        Potin = Material.builder(2037, gregtechId("potin"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1084))
                .color(0xc99781).iconSet(METALLIC)
                .components(Copper, 6, Tin, 2, Lead, 1)
                .fluidPipeProperties(1456, 40, true)
                .build();

        BorosilicateGlass = Material.builder(2038, gregtechId("borosilicate_glass"))
                .ingot(1)
                .liquid(new FluidBuilder().temperature(1921))
                .color(0xE6F3E6).iconSet(SHINY)
                .components(Boron, 1, SiliconDioxide, 7)
                .build();

        Andesite = Material.builder(2039, gregtechId("andesite"))
                .dust()
                .color(0xBEBEBE).iconSet(ROUGH)
                .flags(DECOMPOSITION_BY_CENTRIFUGING)
                .components(Asbestos, 4, Saltpeter, 1)
                .build();

        // FREE ID 2040

        // FREE ID 2041

        NaquadahAlloy = Material.builder(2042, gregtechId("naquadah_alloy"))
                .ingot(5).fluid()
                .color(0x282828).iconSet(METALLIC)
                .flags(GENERATE_CURVED_PLATE)
                .components(Naquadah, 2, Osmiridium, 1, Trinium, 1)
                .toolStats(MaterialToolProperty.Builder.of(40.0F, 12.0F, 3072, 5)
                        .attackSpeed(0.3F).enchantability(33).magnetic().build())
                .rotorStats(8.0f, 5.0f, 5120)
                .cableProperties(V[UV], 2, 4)
                .fluidPipeProperties(8000, 250, true, true, false, false)
                .blast(b -> b
                        .temp(7200, GasTier.HIGH)
                        .blastStats(VA[LuV], 1000)
                        .vacuumStats(VA[IV], 300))
                .build();

        SulfuricNickelSolution = Material.builder(2043, gregtechId("sulfuric_nickel_solution"))
                .liquid(new FluidBuilder().attribute(FluidAttributes.ACID))
                .color(0x3EB640)
                .components(Nickel, 1, Oxygen, 1, SulfuricAcid, 1)
                .build();

        SulfuricCopperSolution = Material.builder(2044, gregtechId("sulfuric_copper_solution"))
                .liquid(new FluidBuilder().attribute(FluidAttributes.ACID))
                .color(0x48A5C0)
                .components(Copper, 1, Oxygen, 1, SulfuricAcid, 1)
                .build();

        LeadZincSolution = Material.builder(2045, gregtechId("lead_zinc_solution"))
                .liquid(new FluidBuilder().customStill())
                .color(0x3C0404)
                .flags(DECOMPOSITION_BY_CENTRIFUGING)
                .components(Lead, 1, Silver, 1, Zinc, 1, Sulfur, 3, Water, 1)
                .build();

        NitrationMixture = Material.builder(2046, gregtechId("nitration_mixture"))
                .liquid(new FluidBuilder().attribute(FluidAttributes.ACID))
                .color(0xE6E2AB)
                .flags(DISABLE_DECOMPOSITION)
                .components(NitricAcid, 1, SulfuricAcid, 1)
                .build();

        DilutedSulfuricAcid = Material.builder(2047, gregtechId("diluted_sulfuric_acid"))
                .liquid(new FluidBuilder().attribute(FluidAttributes.ACID))
                .color(0xC07820)
                .flags(DISABLE_DECOMPOSITION)
                .components(SulfuricAcid, 2, Water, 1)
                .build();

        DilutedHydrochloricAcid = Material.builder(2048, gregtechId("diluted_hydrochloric_acid"))
                .liquid(new FluidBuilder().attribute(FluidAttributes.ACID))
                .color(0x99A7A3)
                .flags(DISABLE_DECOMPOSITION)
                .components(HydrochloricAcid, 1, Water, 1)
                .build();

        Flint = Material.builder(2049, gregtechId("flint"))
                .gem(1)
                .color(0x002040).iconSet(FLINT)
                .flags(NO_SMASHING, MORTAR_GRINDABLE, DECOMPOSITION_BY_CENTRIFUGING)
                .components(SiliconDioxide, 1)
                .toolStats(MaterialToolProperty.Builder.of(0.0F, 1.0F, 64, 1)
                        .enchantability(5).ignoreCraftingTools()
                        .enchantment(Enchantments.FIRE_ASPECT, 2).build())
                .build();

        Air = Material.builder(2050, gregtechId("air"))
                .gas(new FluidBuilder().customStill())
                .color(0xA9D0F5)
                .flags(DISABLE_DECOMPOSITION)
                .components(Nitrogen, 78, Oxygen, 21, Argon, 9)
                .build();

        LiquidAir = Material.builder(2051, gregtechId("liquid_air"))
                .liquid(new FluidBuilder()
                        .temperature(79)
                        .customStill())
                .color(0x84BCFC)
                .flags(DISABLE_DECOMPOSITION)
                .components(Nitrogen, 70, Oxygen, 22, CarbonDioxide, 5, Helium, 2, Argon, 1, Ice, 1)
                .build();

        NetherAir = Material.builder(2052, gregtechId("nether_air"))
                .gas()
                .color(0x4C3434)
                .flags(DISABLE_DECOMPOSITION)
                .components(CarbonMonoxide, 78, HydrogenSulfide, 21, Neon, 9)
                .build();

        LiquidNetherAir = Material.builder(2053, gregtechId("liquid_nether_air"))
                .liquid(new FluidBuilder().temperature(58))
                .color(0x4C3434)
                .flags(DISABLE_DECOMPOSITION)
                .components(CarbonMonoxide, 144, CoalGas, 20, HydrogenSulfide, 15, SulfurDioxide, 15, Helium3, 5, Neon, 1, Ash, 1)
                .build();

        EnderAir = Material.builder(2054, gregtechId("ender_air"))
                .gas()
                .color(0x283454)
                .flags(DISABLE_DECOMPOSITION)
                .components(NitrogenDioxide, 78, Deuterium, 21, Xenon, 9)
                .build();

        LiquidEnderAir = Material.builder(2055, gregtechId("liquid_ender_air"))
                .liquid(new FluidBuilder().temperature(36))
                .color(0x283454)
                .flags(DISABLE_DECOMPOSITION)
                .components(NitrogenDioxide, 122, Deuterium, 50, Helium, 15, Tritium, 10, Krypton, 1, Xenon, 1, Radon,
                        1, EnderPearl, 1)
                .build();

        AquaRegia = Material.builder(2056, gregtechId("aqua_regia"))
                .liquid(new FluidBuilder().attribute(FluidAttributes.ACID))
                .color(0xFFB132)
                .flags(DISABLE_DECOMPOSITION)
                .components(NitricAcid, 1, HydrochloricAcid, 2)
                .build();

        PlatinumSludgeResidue = Material.builder(2057, gregtechId("platinum_sludge_residue"))
                .dust()
                .color(0x827951)
                .flags(DECOMPOSITION_BY_CENTRIFUGING)
                .components(SiliconDioxide, 2, Gold, 3)
                .build();

        PalladiumRaw = Material.builder(2058, gregtechId("palladium_raw"))
                .dust()
                .color(Palladium.getMaterialRGB()).iconSet(METALLIC)
                .flags(DISABLE_DECOMPOSITION)
                .components(Palladium, 1, Ammonia, 1)
                .build();
        PalladiumRaw.setFormula("PdCl2?");

        RarestMetalMixture = Material.builder(2059, gregtechId("rarest_metal_mixture"))
                .dust()
                .color(0x832E11).iconSet(SHINY)
                .flags(DISABLE_DECOMPOSITION)
                .components(Iridium, 1, Osmium, 1, Oxygen, 4, Water, 1)
                .build();
        RarestMetalMixture.setFormula("Ir2O2(SiO2)2Au3?");

        AmmoniumChloride = Material.builder(2060, gregtechId("ammonium_chloride"))
                .dust()
                .color(0x9711A6)
                .components(Ammonia, 1, HydrochloricAcid, 1)
                .build()
                .setFormula("NH4Cl", true);

        AcidicOsmiumSolution = Material.builder(2061, gregtechId("acidic_osmium_solution"))
                .liquid(new FluidBuilder().attribute(FluidAttributes.ACID))
                .color(0xA3AA8A)
                .flags(DISABLE_DECOMPOSITION)
                .components(Osmium, 1, Oxygen, 4, Water, 1, HydrochloricAcid, 1)
                .build();
        AcidicOsmiumSolution.setFormula("OsO4(H2O)(HCl)");

        RhodiumPlatedPalladium = Material.builder(2062, gregtechId("rhodium_plated_palladium"))
                .ingot().fluid()
                .color(0xDAC5C5).iconSet(SHINY)
                .flags(GENERATE_CURVED_PLATE)
                .components(Palladium, 3, Rhodium, 1)
                .rotorStats(12.0f, 3.0f, 1024)
                .fluidPipeProperties(6200, 200, true, true, false, false)
                .blast(b -> b
                        .temp(4500, GasTier.HIGH)
                        .blastStats(VA[IV], 1200)
                        .vacuumStats(VA[EV], 300))
                .build();

        Clay = Material.builder(2063, gregtechId("clay"))
                .dust(1)
                .ingot()
                .color(0xC8C8DC).iconSet(ROUGH)
                .flags(MORTAR_GRINDABLE, EXCLUDE_BLOCK_CRAFTING_BY_HAND_RECIPES)
                .components(Sodium, 2, Lithium, 1, Aluminium, 2, Silicon, 2, Water, 6)
                .build();

        Redstone = Material.builder(2064, gregtechId("redstone"))
                .dust().ore(5, 1, true)
                .liquid(new FluidBuilder().temperature(500))
                .color(0xC80000).iconSet(ROUGH)
                .flags(GENERATE_PLATE, NO_SMASHING, NO_SMELTING, EXCLUDE_BLOCK_CRAFTING_BY_HAND_RECIPES,
                        EXCLUDE_PLATE_COMPRESSOR_RECIPE, DECOMPOSITION_BY_CENTRIFUGING)
                .components(Silicon, 1, Pyrite, 5, Ruby, 1, Mercury, 3)
                .build();


        ChromiumDopedMolybdenite = Material.builder(2065, gregtechId("chromium_doped_molybdenite"))
                .dust().color(0x9C5fB5)
                .flags(DISABLE_DECOMPOSITION)
                .components(Chrome, 1, Molybdenite, 1)
                .build();

        //尾气
        // ExhaustGas 普通尾气
        // 普通燃油(燃油 极限燃油) 1tick 5mb
        ExhaustGas = Material.builder(2067, gregtechId("exhaust_gas"))
                .gas(new FluidBuilder().temperature(773))
                .color(0x8A8A8A)
                .flags(DISABLE_DECOMPOSITION)
                .components(Nitrogen, 72, CarbonDioxide, 11, Oxygen, 5, Argon, 1, Water, 8, CarbonMonoxide, 1, NitricOxide, 1, NitrogenDioxide, 1)
                .build();

        // HighPressureExhaustGas 高压尾气
        // 促燃机(通化) 1tick 5mb
        HighPressureExhaustGas = Material.builder(2068, gregtechId("high_pressure_exhaust_gas"))
                .gas(new FluidBuilder().temperature(973))
                .color(0x8A8A8A)
                .flags(DISABLE_DECOMPOSITION)
                .components(Nitrogen, 70, CarbonDioxide, 12, Oxygen, 5, Argon, 1, Water, 9, CarbonMonoxide, 1, NitricOxide, 1, NitrogenDioxide, 1)
                .build();

        // SupercriticalExhaustGas 超临界尾气
        // 火箭发动机(火箭) 1tick 5mb
        SupercriticalExhaustGas = Material.builder(2069, gregtechId("supercritical_exhaust_gas"))
                .gas(new FluidBuilder().temperature(1473))
                .color(0x8A8A8A)
                .flags(DISABLE_DECOMPOSITION)
                .components(Nitrogen, 65, CarbonDioxide, 18, Oxygen, 2, Argon, 1, Water, 10, CarbonMonoxide, 1, NitricOxide, 2, NitrogenDioxide, 1)
                .build();

        // LowTemperatureExhaustGas 低温尾气
        // 经多级涡轮利用后的废气
        LowTemperatureExhaustGas = Material.builder(2070, gregtechId("low_temperature_exhaust_gas"))
                .gas(new FluidBuilder().temperature(323))
                .color(0x6A6A6A)
                .flags(DISABLE_DECOMPOSITION)
                .components(Nitrogen, 78, CarbonDioxide, 12, Oxygen, 4, Argon, 2, Water, 1, CarbonMonoxide, 1, NitricOxide, 1, NitrogenDioxide, 1)
                .build();

        Stellite = new Material.Builder(2071, gcymId("stellite"))
                .ingot().fluid()
                .color(0xDEDEFF).iconSet(MaterialIconSet.METALLIC)
                .components(Materials.Cobalt, 7, Materials.Chrome, 7, Materials.Manganese, 4, Materials.Titanium, 2)
                .blast(b -> b.temp(4585, BlastProperty.GasTier.HIGH).blastStats(VA[EV]))
                .mix(VA[EV], 80)
                .build();

        WatertightSteel = new Material.Builder(2072, gcymId("watertight_steel"))
                .ingot().fluid()
                .color(0x355D6A).iconSet(MaterialIconSet.METALLIC)
                .components(Steel, 12, Carbon, 2, Manganese, 1, Silicon, 2, Phosphorus, 1, Sulfur, 1, Aluminium, 4)
                .blast(b -> b.temp(3850, BlastProperty.GasTier.MID).blastStats(VA[EV], 800))
                .build();

        MaragingSteel250 = new Material.Builder(2073, gcymId("maraging_steel_250"))
                .ingot().fluid()
                .color(0x9083C0).iconSet(MaterialIconSet.METALLIC)
                .components(Steel, 16, Molybdenum, 1, Titanium, 1, Nickel, 4, Cobalt, 2)
                .mix(VA[EV], 1200)
                .blast(2685)
                .build();

        MaragingSteel300 = new Material.Builder(2074, gcymId("maraging_steel_300"))
                .ingot().fluid()
                .color(0x637087).iconSet(MaterialIconSet.METALLIC)
                .components(Steel, 16, Titanium, 1, Aluminium, 1, Nickel, 4, Cobalt, 2)
                .blast(b -> b.temp(4000, BlastProperty.GasTier.HIGH)
                        .blastStats(VA[EV], 1000))
                .build();

        MaragingSteel350 = new Material.Builder(2075, gcymId("maraging_steel_350"))
                .ingot().fluid()
                .color(0x7985B7).iconSet(MaterialIconSet.METALLIC)
                .components(Steel, 16, Aluminium, 1, Molybdenum, 1, Nickel, 4, Cobalt, 2)
                .fluidPipeProperties(2500, 32000, true)
                .blast(b -> b.temp(4000, BlastProperty.GasTier.HIGH)
                        .blastStats(VA[EV], 1000))
                .build();

        HastelloyC276 = new Material.Builder(2076, gcymId("hastelloy_c_276"))
                .ingot().fluid()
                .color(0xCF3939).iconSet(MaterialIconSet.METALLIC)
                .components(Nickel, 32, Molybdenum, 8, Chrome, 7, Tungsten, 1, Cobalt, 1, Copper, 1)
                .blast(b -> b.temp(4625, BlastProperty.GasTier.MID))
                .build();

        HastelloyX = new Material.Builder(2077, gcymId("hastelloy_x"))
                .ingot().fluid()
                .color(0x6BA3E3).iconSet(MaterialIconSet.METALLIC)
                .components(Nickel, 24, Chrome, 11, Iron, 9, Molybdenum, 4, Manganese, 1, Silicon, 1)
                .blast(b -> b.temp(4200, BlastProperty.GasTier.HIGH)
                        .blastStats(VA[EV], 900))
                .build();

        HastelloyN = new Material.Builder(2078, gcymId("hastelloy_n"))
                .ingot().fluid()
                .color(0x9C97C4).iconSet(MaterialIconSet.METALLIC)
                .components(Yttrium, 4, Molybdenum, 4, Chrome, 2, Titanium, 2, Nickel, 15)
                .blast(b -> b.temp(4000, BlastProperty.GasTier.HIGH)
                        .blastStats(VA[EV], 800))
                .build();

        HastelloyW = new Material.Builder(2079, gcymId("hastelloy_w"))
                .ingot().fluid()
                .color(0xC0B6D0).iconSet(MaterialIconSet.METALLIC)
                .components(Iron, 3, Cobalt, 1, Molybdenum, 12, Chrome, 3, Nickel, 31)
                .blast(b -> b.temp(3800, BlastProperty.GasTier.MID)
                        .blastStats(VA[HV], 600))
                .build();

        Trinaquadalloy = new Material.Builder(2080, gcymId("trinaquadalloy"))
                .ingot().fluid()
                .color(0x281832).iconSet(MaterialIconSet.BRIGHT)
                .components(Trinium, 6, Naquadah, 2, Carbon, 1)
                .blast(b -> b.temp(8747, BlastProperty.GasTier.HIGHER)
                        .blastStats(VA[ZPM], 1200))
                .build();

        Zeron100 = new Material.Builder(2081, gcymId("zeron_100"))
                .ingot().fluid()
                .color(0x325A8C).iconSet(MaterialIconSet.METALLIC)
                .components(Chrome, 13, Nickel, 3, Molybdenum, 2, Copper, 10, Tungsten, 2, Steel, 20)
                .blast(b -> b.temp(3693, BlastProperty.GasTier.MID)
                        .blastStats(VA[LuV]))
                .build();

        TitaniumCarbide = new Material.Builder(2082, gcymId("titanium_carbide"))
                .ingot().fluid()
                .color(0xB20B3A).iconSet(MaterialIconSet.METALLIC)
                .components(Titanium, 1, Carbon, 1)
                .blast(b -> b.temp(3430, BlastProperty.GasTier.MID)
                        .blastStats(VA[EV], 1000))
                .build();

        TantalumCarbide = new Material.Builder(2083, gcymId("tantalum_carbide"))
                .ingot().fluid()
                .color(0x56566A).iconSet(MaterialIconSet.METALLIC)
                .components(Tantalum, 1, Carbon, 1)
                .blast(b -> b.temp(4120, BlastProperty.GasTier.MID)
                        .blastStats(VA[EV], 1200))
                .build();

        MolybdenumDisilicide = new Material.Builder(2084, gcymId("molybdenum_disilicide"))
                .ingot().fluid()
                .color(0x6A5BA3).iconSet(MaterialIconSet.METALLIC)
                .components(Molybdenum, 1, Silicon, 2)
                .blast(b -> b.temp(2300, BlastProperty.GasTier.MID)
                        .blastStats(VA[EV], 800))
                .build();

        HSLASteel = new Material.Builder(2085, gcymId("hsla_steel"))
                .ingot().fluid()
                .color(0x808080).iconSet(MaterialIconSet.METALLIC)
                .components(Invar, 2, Vanadium, 1, Titanium, 1, Molybdenum, 1)
                .toolStats(MaterialToolProperty.Builder.of(60F, 6.0F, 2048, 4)
                        .enchantability(14).build())
                .rotorStats(6.0f, 5.0f, 2400)
                .fluidPipeProperties(1711, 280, true, true, false, false)
                .blast(b -> b.temp(1711, BlastProperty.GasTier.LOW).blastStats(VA[HV], 1000))
                .build();

        TitaniumTungstenCarbide = new Material.Builder(2086, gcymId("titanium_tungsten_carbide"))
                .ingot().fluid()
                .color(0x800D0D).iconSet(MaterialIconSet.METALLIC)
                .components(TungstenCarbide, 1, TitaniumCarbide, 2)
                .toolStats(MaterialToolProperty.Builder.of(80F, 8.0F, 2048, 4)
                        .enchantability(14).build())
                .rotorStats(8.0f, 5.0f, 3200)
                .fluidPipeProperties(3800, 400, true, true, false, false)
                .blast(b -> b.temp(3800, BlastProperty.GasTier.HIGH).blastStats(VA[EV], 1000))
                .build();

        IncoloyMA956 = new Material.Builder(2087, gcymId("incoloy_ma_956"))
                .ingot().fluid()
                .color(0x37BF7E).iconSet(MaterialIconSet.METALLIC)
                .components(VanadiumSteel, 4, Manganese, 2, Aluminium, 5, Yttrium, 2)
                .toolStats(MaterialToolProperty.Builder.of(80F, 6.0F, 2048, 4)
                        .enchantability(14).build())
                .rotorStats(6.0f, 5.0f, 2400)
                .fluidPipeProperties(3625, 400, true, true, false, false)
                .blast(b -> b.temp(3625, BlastProperty.GasTier.MID).blastStats(VA[EV], 800))
                .build();
    }
}
