package gregtech.api.unification.material.materials;

import gregtech.api.GTValues;
import gregtech.api.fluids.FluidBuilder;
import gregtech.api.fluids.store.FluidStorageKeys;
import gregtech.api.unification.Elements;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.BlastProperty;
import gregtech.api.unification.material.properties.BlastProperty.GasTier;
import gregtech.api.unification.material.properties.MaterialToolProperty;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.material.properties.RadioactiveProperty;
import gregtech.api.unification.material.properties.ToxicProperty;

import static gregtech.api.GTValues.*;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.material.info.MaterialFlags.*;
import static gregtech.api.unification.material.info.MaterialIconSet.*;
import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.api.util.Mods.Names.GTQT_CORE;
import static net.minecraftforge.fml.common.Loader.isModLoaded;

public class ElementMaterials {

    public static void register() {
        Actinium = Material.builder(1, gregtechId("actinium"))
                .ingot()
                .fluid()
                .color(0xC3D1FF).iconSet(METALLIC)
                .element(Elements.Ac)
                .build();
        Actinium.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.0f));

        Aluminium = Material.builder(2, gregtechId("aluminium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(933))
                .color(0x80C8F0)
                .element(Elements.Al)
                .toolStats(MaterialToolProperty.Builder.of(6.0F, 7.5F, 768, 2)
                        .enchantability(14).build())
                .rotorStats(10.0f, 2.0f, 128)
                .cableProperties(V[EV], 1, 1)
                .fluidPipeProperties(1166, 100, true)
                .blast(b -> b
                        .temp(isModLoaded(GTQT_CORE) ? 2054 : 1700, BlastProperty.GasTier.LOW)
                        .blastStats(isModLoaded(GTQT_CORE) ? VA[EV] : VA[MV], 1200))
                .build();

        Americium = Material.builder(3, gregtechId("americium"))
                .ingot(3)
                .liquid(new FluidBuilder().temperature(1449))
                .plasma()
                .color(0x287869).iconSet(METALLIC)
                .element(Elements.Am)
                .itemPipeProperties(64, 64)
                .build();
        Americium.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.0f));

        Antimony = Material.builder(4, gregtechId("antimony"))
                .ingot()
                .liquid(new FluidBuilder().temperature(904))
                .color(0xDCDCF0).iconSet(SHINY)
                .flags(MORTAR_GRINDABLE)
                .element(Elements.Sb)
                .build();

        Argon = Material.builder(5, gregtechId("argon"))
                .gas()
                .plasma()
                .liquid(new FluidBuilder()
                        .temperature(87)
                        .color(0x8080FF)
                        .name("liquid_argon")
                        .translation("gregtech.fluid.liquid_generic"))
                .color(0x00FF00)
                .element(Elements.Ar)
                .build();
        Argon.getProperty(PropertyKey.FLUID).setPrimaryKey(FluidStorageKeys.GAS);

        Arsenic = Material.builder(6, gregtechId("arsenic"))
                .ingot()
                .fluid()
                .gas(new FluidBuilder().temperature(887))
                .color(0x676756)
                .element(Elements.As)
                .build();
        Arsenic.setProperty(PropertyKey.TOXIC, new ToxicProperty(0.5f));

        Astatine = Material.builder(7, gregtechId("astatine"))
                .ingot()
                .liquid(new FluidBuilder().temperature(302))
                .plasma()
                .color(0x241A24)
                .element(Elements.At)
                .build();
        Astatine.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(3.0f));

        Barium = Material.builder(8, gregtechId("barium"))
                .ingot()
                .fluid()
                .color(0x83824C).iconSet(METALLIC)
                .element(Elements.Ba)
                .build();

        Berkelium = Material.builder(9, gregtechId("berkelium"))
                .ingot()
                .fluid()
                .color(0x645A88).iconSet(METALLIC)
                .element(Elements.Bk)
                .build();
        Berkelium.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(3.0f));

        Beryllium = Material.builder(10, gregtechId("beryllium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1560))

                .color(0x64B464).iconSet(METALLIC)
                .element(Elements.Be)
                .build();

        Bismuth = Material.builder(11, gregtechId("bismuth"))
                .ingot(1)
                .liquid(new FluidBuilder().temperature(545))
                .plasma()
                .color(0x64A0A0).iconSet(METALLIC)
                .element(Elements.Bi)
                .build();

        Bohrium = Material.builder(12, gregtechId("bohrium"))
                .ingot()
                .fluid()
                .color(0xDC57FF).iconSet(SHINY)
                .element(Elements.Bh)
                .build();

        Boron = Material.builder(13, gregtechId("boron"))
                .dust()
                .liquid(new FluidBuilder().temperature(2076))
                .plasma()
                .color(0xD2FAD2)
                .element(Elements.B)
                .build();

        Bromine = Material.builder(14, gregtechId("bromine"))
                .ingot()
                .fluid()
                .color(0x500A0A).iconSet(SHINY)
                .element(Elements.Br)
                .build();

        Caesium = Material.builder(15, gregtechId("caesium"))
                .ingot()
                .fluid()
                .color(0x80620B).iconSet(METALLIC)
                .element(Elements.Cs)
                .build();

        Calcium = Material.builder(16, gregtechId("calcium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(839))
                .plasma()
                .color(0xFFF5DE).iconSet(METALLIC)
                .element(Elements.Ca)
                .build();

        Californium = Material.builder(17, gregtechId("californium"))
                .ingot()
                .fluid()
                .color(0xA85A12).iconSet(METALLIC)
                .element(Elements.Cf)
                .build();
        Californium.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(4.0f));

        Carbon = Material.builder(18, gregtechId("carbon"))
                .ingot()
                .liquid(new FluidBuilder().temperature(4600))
                .color(0x141414)
                .element(Elements.C)
                .build();

        Cadmium = Material.builder(19, gregtechId("cadmium"))
                .ingot()
                .fluid()
                .color(0x32323C).iconSet(SHINY)
                .element(Elements.Cd)
                .build();

        Cerium = Material.builder(20, gregtechId("cerium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1068))
                .color(0x87917D).iconSet(METALLIC)
                .element(Elements.Ce)
                .build();

        Chlorine = Material.builder(21, gregtechId("chlorine"))
                .gas(new FluidBuilder().customStill())
                .color(0x2D8C8C)
                .element(Elements.Cl)
                .build();

        Chrome = Material.builder(22, gregtechId("chrome"))
                .ingot(3)
                .liquid(new FluidBuilder().temperature(2180))
                .plasma()
                .color(0xEAC4D8).iconSet(SHINY)
                .flags(GENERATE_CURVED_PLATE)
                .element(Elements.Cr)
                .rotorStats(12.0f, 3.0f, 512)
                .fluidPipeProperties(2180, 35, true, true, false, false)
                .blast(1700, GasTier.LOW)
                .build();

        Cobalt = Material.builder(23, gregtechId("cobalt"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1768))
                // leave for TiCon ore processing
                .color(0x5050FA).iconSet(METALLIC)
                .element(Elements.Co)
                .cableProperties(V[LV], 2, 2)
                .itemPipeProperties(2560, 2.0f)
                .build();

        Copernicium = Material.builder(24, gregtechId("copernicium"))
                .ingot()
                .fluid()
                .color(0xFFFEFF)
                .element(Elements.Cn)
                .build();

        Copper = Material.builder(25, gregtechId("copper"))
                .ingot(1)
                .liquid(new FluidBuilder().temperature(1358))
                .color(0xFF6400).iconSet(SHINY)
                .flags(MORTAR_GRINDABLE, GENERATE_CURVED_PLATE, GENERATE_EXTRA)
                .element(Elements.Cu)
                .cableProperties(V[MV], 1, 2)
                .fluidPipeProperties(1696, 6, true)
                .heatConductorProperties(1000, 100, 0f)
                .build();

        Curium = Material.builder(26, gregtechId("curium"))
                .ingot()
                .fluid()
                .color(0x7B544E).iconSet(METALLIC)
                .element(Elements.Cm)
                .build();
        Curium.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(3.0f));

        Darmstadtium = Material.builder(27, gregtechId("darmstadtium"))
                .ingot().fluid()
                .color(0x578062)
                .flags(GENERATE_CURVED_PLATE)
                .fluidPipeProperties(9600, 300, true, true, false, false)
                .element(Elements.Ds)
                .build();

        Deuterium = Material.builder(28, gregtechId("deuterium"))
                .gas(new FluidBuilder().customStill())
                .color(0xFCFC84)
                .element(Elements.D)
                .build();

        Dubnium = Material.builder(29, gregtechId("dubnium"))
                .ingot()
                .fluid()
                .color(0xD3FDFF).iconSet(SHINY)
                .element(Elements.Db)
                .build();

        Dysprosium = Material.builder(30, gregtechId("dysprosium"))
                .ingot()
                .fluid()
                .iconSet(METALLIC)
                .element(Elements.Dy)
                .build();

        Einsteinium = Material.builder(31, gregtechId("einsteinium"))
                .ingot()
                .fluid()
                .color(0xCE9F00).iconSet(METALLIC)
                .element(Elements.Es)
                .build();

        Erbium = Material.builder(32, gregtechId("erbium"))
                .ingot()
                .fluid()
                .iconSet(METALLIC)
                .element(Elements.Er)
                .build();

        Europium = Material.builder(33, gregtechId("europium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1099))
                .color(0x20FFFF).iconSet(METALLIC)
                .element(Elements.Eu)
                .cableProperties(GTValues.V[GTValues.UHV], 2, 32)
                .fluidPipeProperties(7750, 300, true)
                .blast(b -> b
                        .temp(6000, GasTier.MID)
                        .blastStats(VA[IV], 180)
                        .vacuumStats(VA[HV]))
                .build();

        Fermium = Material.builder(34, gregtechId("fermium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1500))
                .plasma()
                .color(0x984ACF).iconSet(METALLIC)
                .element(Elements.Fm)
                .build();

        Flerovium = Material.builder(35, gregtechId("flerovium"))
                .ingot()
                .fluid()
                .iconSet(SHINY)
                .element(Elements.Fl)
                .build();

        Fluorine = Material.builder(36, gregtechId("fluorine"))
                .gas(new FluidBuilder().customStill())
                .color(0x6EA7DC)
                .element(Elements.F)
                .build();

        Francium = Material.builder(37, gregtechId("francium"))
                .ingot()
                .fluid()
                .color(0xAAAAAA).iconSet(SHINY)
                .element(Elements.Fr)
                .build();

        Gadolinium = Material.builder(38, gregtechId("gadolinium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1313))
                .plasma()
                .color(0xDDDDFF).iconSet(METALLIC)
                .element(Elements.Gd)
                .build();

        Gallium = Material.builder(39, gregtechId("gallium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(303))
                .color(0xDCDCFF).iconSet(SHINY)
                .flags(GENERATE_FOIL)
                .element(Elements.Ga)
                .build();

        Germanium = Material.builder(40, gregtechId("germanium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1086))
                .plasma()
                .color(0x434343).iconSet(SHINY)
                .element(Elements.Ge)
                .blast(b -> b.temp(1211, GasTier.LOW))
                .build();

        Gold = Material.builder(41, gregtechId("gold"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1337))
                .color(0xFFE650).iconSet(SHINY)
                .flags(MORTAR_GRINDABLE, EXCLUDE_BLOCK_CRAFTING_BY_HAND_RECIPES, GENERATE_CURVED_PLATE)
                .element(Elements.Au)
                .cableProperties(V[HV], 3, 2)
                .fluidPipeProperties(1671, 25, true, true, false, false)
                .build();

        Hafnium = Material.builder(42, gregtechId("hafnium"))
                .ingot()
                .fluid()
                .color(0x99999A).iconSet(SHINY)
                .element(Elements.Hf)
                .blast(b -> b.temp(2227, GasTier.HIGH)
                        .blastStats(GTValues.VA[GTValues.EV], 2000))
                .build();

        Hassium = Material.builder(43, gregtechId("hassium"))
                .ingot()
                .fluid()
                .color(0xDDDDDD)
                .element(Elements.Hs)
                .build();

        Holmium = Material.builder(44, gregtechId("holmium"))
                .ingot()
                .fluid()
                .iconSet(METALLIC)
                .element(Elements.Ho)
                .build();

        Hydrogen = Material.builder(45, gregtechId("hydrogen"))
                .gas()
                .plasma()
                .liquid(new FluidBuilder()
                        .temperature(20)
                        .color(0xE0FFFF)
                        .name("liquid_hydrogen")
                        .translation("gregtech.fluid.liquid_generic"))
                .color(0x0000B5)
                .element(Elements.H)
                .build();
        Hydrogen.getProperty(PropertyKey.FLUID).setPrimaryKey(FluidStorageKeys.GAS);

        Helium = Material.builder(46, gregtechId("helium"))
                .gas()
                .plasma()
                .liquid(new FluidBuilder()
                        .temperature(4)
                        .color(0xFCFF90)
                        .name("liquid_helium")
                        .translation("gregtech.fluid.liquid_generic"))
                .color(0xFCFC94)
                .element(Elements.He)
                .build();
        Helium.getProperty(PropertyKey.FLUID).setPrimaryKey(FluidStorageKeys.GAS);

        Helium3 = Material.builder(47, gregtechId("helium_3"))
                .gas(new FluidBuilder()
                        .customStill()
                        .translation("gregtech.fluid.generic"))
                .color(0xFCFCCC)
                .element(Elements.He3)
                .build();

        Indium = Material.builder(48, gregtechId("indium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(430))
                .color(0x400080).iconSet(SHINY)
                .element(Elements.In)
                .build();

        Iodine = Material.builder(49, gregtechId("iodine"))
                .ingot()
                .fluid()
                .color(0x2C344F).iconSet(SHINY)
                .element(Elements.I)
                .build();

        Iridium = Material.builder(50, gregtechId("iridium"))
                .ingot(3)
                .liquid(new FluidBuilder().temperature(2719))
                .color(0xA1E4E4).iconSet(METALLIC)
                .flags(GENERATE_SHEET)
                .element(Elements.Ir)
                .rotorStats(7.0f, 3.0f, 2560)
                .fluidPipeProperties(3398, 250, true, false, true, false)
                .blast(b -> b
                        .temp(4500, GasTier.HIGH)
                        .blastStats(VA[IV], 1100)
                        .vacuumStats(VA[EV], 250))
                .build();

        Iron = Material.builder(51, gregtechId("iron"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1811))
                .plasma()
                .color(0xC8C8C8).iconSet(METALLIC)
                .flags(MORTAR_GRINDABLE, EXCLUDE_BLOCK_CRAFTING_BY_HAND_RECIPES, GENERATE_CURVED_PLATE,
                        GENERATE_EXTRA)
                .element(Elements.Fe)
                .toolStats(MaterialToolProperty.Builder.of(2.0F, 2.0F, 256, 2)
                        .enchantability(14).build())
                .rotorStats(7.0f, 2.5f, 256)
                .cableProperties(V[MV], 2, 3)
                .fluidPipeProperties(800, 24, true)
                .build()
                .setTooltips("我是铁");

        Krypton = Material.builder(52, gregtechId("krypton"))
                .gas()
                .plasma()
                .liquid(new FluidBuilder()
                        .temperature(119)
                        .color(0x8080FF)
                        .name("liquid_krypton")
                        .translation("gregtech.fluid.liquid_generic"))
                .color(0x80FF80)
                .element(Elements.Kr)
                .build();
        Krypton.getProperty(PropertyKey.FLUID).setPrimaryKey(FluidStorageKeys.GAS);

        Lanthanum = Material.builder(53, gregtechId("lanthanum"))
                .dust()
                .liquid(new FluidBuilder().temperature(1193))
                .color(0x5D7575).iconSet(METALLIC)
                .element(Elements.La)
                .build();

        Lawrencium = Material.builder(54, gregtechId("lawrencium"))
                .ingot()
                .fluid()
                .iconSet(METALLIC)
                .element(Elements.Lr)
                .build();

        Lead = Material.builder(55, gregtechId("lead"))
                .ingot(1)
                .liquid(new FluidBuilder().temperature(600))
                .plasma()
                .color(0x8C648C)
                .flags(MORTAR_GRINDABLE)
                .element(Elements.Pb)
                .cableProperties(V[ULV], 2, 2)
                .fluidPipeProperties(1200, 32, true)
                .build();

        Lithium = Material.builder(56, gregtechId("lithium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(454))

                .color(0xBDC7DB)
                .element(Elements.Li)
                .build();

        Livermorium = Material.builder(57, gregtechId("livermorium"))
                .ingot()
                .fluid()
                .color(0xAAAAAA).iconSet(SHINY)
                .element(Elements.Lv)
                .build();

        Lutetium = Material.builder(58, gregtechId("lutetium"))
                .dust()
                .ingot()
                .liquid(new FluidBuilder().temperature(1925))
                .color(0x00AAFF).iconSet(METALLIC)
                .element(Elements.Lu)
                .build();

        Magnesium = Material.builder(59, gregtechId("magnesium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(923))
                .color(0xFFC8C8).iconSet(METALLIC)
                .element(Elements.Mg)
                .build();

        Mendelevium = Material.builder(60, gregtechId("mendelevium"))
                .ingot()
                .fluid()
                .color(0x1D4ACF).iconSet(METALLIC)
                .element(Elements.Md)
                .build();

        Manganese = Material.builder(61, gregtechId("manganese"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1519))
                .color(0xCDE1B9)
                .element(Elements.Mn)
                .rotorStats(7.0f, 2.0f, 512)
                .build();

        Meitnerium = Material.builder(62, gregtechId("meitnerium"))
                .ingot()
                .fluid()
                .color(0x2246BE).iconSet(SHINY)
                .element(Elements.Mt)
                .build();

        Mercury = Material.builder(63, gregtechId("mercury"))
                .fluid()
                .color(0xE6DCDC).iconSet(DULL)
                .element(Elements.Hg)
                .build();

        Molybdenum = Material.builder(64, gregtechId("molybdenum"))
                .ingot()
                .liquid(new FluidBuilder().temperature(2896))
                .color(0xB4B4DC).iconSet(SHINY)
                .element(Elements.Mo)
                .rotorStats(7.0f, 2.0f, 512)
                .build();

        Moscovium = Material.builder(65, gregtechId("moscovium"))
                .ingot()
                .fluid()
                .color(0x7854AD).iconSet(SHINY)
                .element(Elements.Mc)
                .build();

        Neodymium = Material.builder(66, gregtechId("neodymium"))
                .ingot().fluid()
                .color(0x646464).iconSet(METALLIC)
                .element(Elements.Nd)
                .rotorStats(7.0f, 2.0f, 512)
                .blast(1297, GasTier.MID)
                .build();

        Neon = Material.builder(67, gregtechId("neon"))
                .gas()
                .plasma()
                .liquid(new FluidBuilder()
                        .temperature(27)
                        .color(0xFF4500)
                        .name("liquid_neon")
                        .translation("gregtech.fluid.liquid_generic"))
                .color(0xFAB4B4)
                .element(Elements.Ne)
                .build();
        Neon.getProperty(PropertyKey.FLUID).setPrimaryKey(FluidStorageKeys.GAS);

        Neptunium = Material.builder(68, gregtechId("neptunium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(3902))
                .plasma()
                .color(0x284D7B).iconSet(METALLIC)
                .element(Elements.Np)
                .build();
        Neptunium.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(1.5f));

        Nickel = Material.builder(69, gregtechId("nickel"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1728))
                .plasma()
                .color(0xC8C8FA).iconSet(METALLIC)
                .flags(MORTAR_GRINDABLE)
                .element(Elements.Ni)
                .cableProperties(GTValues.V[GTValues.LV], 3, 3)
                .itemPipeProperties(2048, 1.0f)
                .build();

        Nihonium = Material.builder(70, gregtechId("nihonium"))
                .ingot()
                .fluid()
                .color(0x08269E).iconSet(SHINY)
                .element(Elements.Nh)
                .build();

        Niobium = Material.builder(71, gregtechId("niobium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(2468))
                .plasma()
                .color(0xBEB4C8).iconSet(METALLIC)
                .element(Elements.Nb)
                .blast(b -> b
                        .temp(2750, GasTier.MID)
                        .blastStats(VA[HV], 900))
                .build();

        Nitrogen = Material.builder(72, gregtechId("nitrogen"))
                .gas()
                .plasma()
                .liquid(new FluidBuilder()
                        .temperature(77)
                        .color(0x008D8F)
                        .name("liquid_nitrogen")
                        .translation("gregtech.fluid.liquid_generic"))
                .color(0x00BFC1)
                .element(Elements.N)
                .build();
        Nitrogen.getProperty(PropertyKey.FLUID).setPrimaryKey(FluidStorageKeys.GAS);

        Nobelium = Material.builder(73, gregtechId("nobelium"))
                .ingot()
                .fluid()
                .iconSet(SHINY)
                .element(Elements.No)
                .build();

        Oganesson = Material.builder(74, gregtechId("oganesson"))
                .ingot()
                .fluid()
                .color(0x142D64).iconSet(METALLIC)
                .element(Elements.Og)
                .build();

        Osmium = Material.builder(75, gregtechId("osmium"))
                .ingot(4)
                .liquid(new FluidBuilder().temperature(3306))
                .color(0x3232FF).iconSet(METALLIC)
                .element(Elements.Os)
                .rotorStats(16.0f, 4.0f, 1280)
                .cableProperties(V[LuV], 4, 2)
                .itemPipeProperties(256, 8.0f)
                .blast(b -> b
                        .temp(4500, GasTier.HIGH)
                        .blastStats(VA[LuV], 1000)
                        .vacuumStats(VA[EV], 300))
                .build();

        Oxygen = Material.builder(76, gregtechId("oxygen"))
                .gas()
                .liquid(new FluidBuilder()
                        .temperature(85)
                        .color(0x6688DD)
                        .name("liquid_oxygen")
                        .translation("gregtech.fluid.liquid_generic"))
                .plasma()
                .color(0x4CC3FF)
                .element(Elements.O)
                .build();
        Oxygen.getProperty(PropertyKey.FLUID).setPrimaryKey(FluidStorageKeys.GAS);

        Palladium = Material.builder(77, gregtechId("palladium"))
                .ingot().fluid()
                .color(0x808080).iconSet(SHINY)
                .element(Elements.Pd)
                .blast(b -> b
                        .temp(1828, GasTier.LOW)
                        .blastStats(VA[HV], 900)
                        .vacuumStats(VA[HV], 150))
                .build();

        Phosphorus = Material.builder(78, gregtechId("phosphorus"))
                .ingot()
                .fluid()
                .dust()
                .color(0xFFFF00)
                .element(Elements.P)
                .build();

        Polonium = Material.builder(79, gregtechId("polonium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(254))
                .plasma()
                .color(0xC9D47E)
                .element(Elements.Po)
                .build();
        Polonium.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(5.0f));

        Platinum = Material.builder(80, gregtechId("platinum"))
                .ingot()
                .liquid(new FluidBuilder().temperature(2041))
                .color(0xFFFFC8).iconSet(SHINY)
                .element(Elements.Pt)
                .cableProperties(V[IV], 2, 1)
                .itemPipeProperties(512, 4.0f)
                .build();

        Plutonium239 = Material.builder(81, gregtechId("plutonium_239"))
                .ingot(3)
                .liquid(new FluidBuilder().temperature(913))
                .color(0xF03232).iconSet(METALLIC)
                .element(Elements.Pu239)
                .build();
        Plutonium239.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(1.0f));

        Plutonium241 = Material.builder(82, gregtechId("plutonium_241"))
                .ingot(3)
                .liquid(new FluidBuilder().temperature(913))
                .plasma()
                .color(0xFA4646).iconSet(SHINY)
                .element(Elements.Pu241)
                .build();
        Plutonium241.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(1.5f));

        Potassium = Material.builder(83, gregtechId("potassium"))
                .dust(1)
                .ingot()
                .liquid(new FluidBuilder().temperature(337))
                .color(0xBEDCFF).iconSet(METALLIC)
                .element(Elements.K)
                .build();

        Praseodymium = Material.builder(84, gregtechId("praseodymium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(254))
                .plasma()
                .color(0xCECECE).iconSet(METALLIC)
                .element(Elements.Pr)
                .build();

        Promethium = Material.builder(85, gregtechId("promethium"))
                .dust().ingot().fluid()
                .color(0x74E0A0).iconSet(SHINY)
                .element(Elements.Pm)
                .build();

        Protactinium = Material.builder(86, gregtechId("protactinium"))
                .ingot()
                .fluid()
                .color(0xA78B6D).iconSet(METALLIC)
                .element(Elements.Pa)
                .build();

        Radon = Material.builder(87, gregtechId("radon"))
                .gas()
                .plasma()
                .liquid(new FluidBuilder()
                        .temperature(211)
                        .color(0x9400D3)
                        .name("liquid_radon")
                        .translation("gregtech.fluid.liquid_generic"))
                .color(0xFF39FF)
                .element(Elements.Rn)
                .build();
        Radon.getProperty(PropertyKey.FLUID).setPrimaryKey(FluidStorageKeys.GAS);
        Radon.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.0f));

        Radium = Material.builder(88, gregtechId("radium"))
                .ingot()
                .fluid()
                .color(0xFFFFCD).iconSet(SHINY)
                .element(Elements.Ra)
                .build();
        Radium.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.5f));

        Rhenium = Material.builder(89, gregtechId("rhenium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(3186))
                .plasma()
                .color(0xB6BAC3).iconSet(SHINY)
                .element(Elements.Re)
                .build();

        Rhodium = Material.builder(90, gregtechId("rhodium"))
                .ingot().fluid()
                .color(0xDC0C58).iconSet(BRIGHT)
                .element(Elements.Rh)
                .blast(b -> b
                        .temp(2237, GasTier.MID)
                        .blastStats(VA[EV], 1200)
                        .vacuumStats(VA[HV]))
                .build();

        Roentgenium = Material.builder(91, gregtechId("roentgenium"))
                .ingot()
                .fluid()
                .color(0xE3FDEC).iconSet(SHINY)
                .element(Elements.Rg)
                .build();

        Rubidium = Material.builder(92, gregtechId("rubidium"))
                .ingot()
                .fluid()
                .color(0xF01E1E).iconSet(SHINY)
                .element(Elements.Rb)
                .build();

        Ruthenium = Material.builder(93, gregtechId("ruthenium"))
                .ingot().fluid()
                .color(0x50ACCD).iconSet(SHINY)
                .element(Elements.Ru)
                .blast(b -> b
                        .temp(2607, GasTier.MID)
                        .blastStats(VA[EV], 900)
                        .vacuumStats(VA[HV], 200))
                .build();

        Rutherfordium = Material.builder(94, gregtechId("rutherfordium"))
                .ingot()
                .fluid()
                .color(0xFFF6A1).iconSet(SHINY)
                .element(Elements.Rf)
                .build();

        Samarium = Material.builder(95, gregtechId("samarium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1345))
                .color(0xFFFFCC).iconSet(METALLIC)
                .element(Elements.Sm)
                .blast(b -> b
                        .temp(5400, GasTier.HIGH)
                        .blastStats(VA[EV], 1500)
                        .vacuumStats(VA[HV], 200))
                .build();

        Scandium = Material.builder(96, gregtechId("scandium"))
                .ingot()
                .fluid()
                .iconSet(METALLIC)
                .element(Elements.Sc)
                .build();

        Seaborgium = Material.builder(97, gregtechId("seaborgium"))
                .ingot()
                .fluid()
                .color(0x19C5FF).iconSet(SHINY)
                .element(Elements.Sg)
                .cableProperties(V[UEV], 32, 32)
                .build();

        Selenium = Material.builder(98, gregtechId("selenium"))
                .ingot()
                .fluid()
                .color(0xB6BA6B).iconSet(SHINY)
                .element(Elements.Se)
                .build();

        Silicon = Material.builder(99, gregtechId("silicon"))
                .ingot().fluid()
                .color(0x3C3C50).iconSet(METALLIC)
                .flags(GENERATE_FOIL)
                .element(Elements.Si)
                .blast(2273) // no gas tier for silicon
                .build();

        Silver = Material.builder(100, gregtechId("silver"))
                .ingot()
                .liquid(new FluidBuilder().temperature(1235))
                .plasma()
                .color(0xDCDCFF).iconSet(SHINY)
                .element(Elements.Ag)
                .cableProperties(V[HV], 1, 1)
                .build();

        Sodium = Material.builder(101, gregtechId("sodium"))
                .dust()
                .liquid(new FluidBuilder().temperature(97))
                .plasma()
                .color(0x000096).iconSet(METALLIC)
                .element(Elements.Na)
                .build();

        Strontium = Material.builder(102, gregtechId("strontium"))
                .ingot()
                .fluid()
                .color(0xC8C8C8).iconSet(METALLIC)
                .element(Elements.Sr)
                .build();

        Sulfur = Material.builder(103, gregtechId("sulfur"))
                .dust()
                .liquid(new FluidBuilder().temperature(388))
                .plasma()
                .color(0xC8C800)
                .flags(FLAMMABLE)
                .element(Elements.S)
                .build();

        Tantalum = Material.builder(104, gregtechId("tantalum"))
                .ingot()
                .liquid(new FluidBuilder().temperature(3290))
                .color(0x69B7FF).iconSet(METALLIC)
                .element(Elements.Ta)
                .build();

        Technetium = Material.builder(105, gregtechId("technetium"))
                .ingot()
                .fluid()
                .color(0x545455).iconSet(SHINY)
                .element(Elements.Tc)
                .build();

        Tellurium = Material.builder(106, gregtechId("tellurium"))
                .dust().ingot().fluid()
                .color(0xEFDDED).iconSet(METALLIC)
                .element(Elements.Te)
                .build();

        Tennessine = Material.builder(107, gregtechId("tennessine"))
                .ingot()
                .fluid()
                .color(0x977FD6).iconSet(SHINY)
                .element(Elements.Ts)
                .build();

        Terbium = Material.builder(108, gregtechId("terbium"))
                .ingot()
                .fluid()
                .iconSet(METALLIC)
                .element(Elements.Tb)
                .build();

        Thorium = Material.builder(109, gregtechId("thorium"))
                .ingot()
                .liquid(new FluidBuilder().temperature(2023))
                .plasma()
                .color(0x001E00).iconSet(SHINY)
                .element(Elements.Th)
                .build();
        Thorium.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(0.15f));

        Thallium = Material.builder(110, gregtechId("thallium"))
                .ingot()
                .fluid()
                .dust()
                .color(0xC1C1DE).iconSet(SHINY)
                .element(Elements.Tl)
                .build();

        Thulium = Material.builder(111, gregtechId("thulium"))
                .ingot()
                .fluid()
                .dust()
                .iconSet(METALLIC)
                .element(Elements.Tm)
                .build();

        Tin = Material.builder(112, gregtechId("tin"))
                .ingot(1)
                .liquid(new FluidBuilder().temperature(505))
                .plasma()
                .color(0xDCDCDC)
                .flags(MORTAR_GRINDABLE, GENERATE_CURVED_PLATE)
                .element(Elements.Sn)
                .cableProperties(V[LV], 1, 1)
                .itemPipeProperties(4096, 0.5f)
                .build();

        Titanium = Material.builder(113, gregtechId("titanium")) // todo Ore? Look at EBF recipe here if we do Ti
                // ores
                .ingot(3)
                .liquid(new FluidBuilder().temperature(1668))
                .plasma()
                .color(0xDCA0F0).iconSet(METALLIC)
                .flags(GENERATE_SHEET)
                .element(Elements.Ti)
                .toolStats(MaterialToolProperty.Builder.of(8.0F, 6.0F, 1536, 3)
                        .enchantability(14).build())
                .rotorStats(7.0f, 3.0f, 1600)
                .fluidPipeProperties(2426, 150, true, true, false, false)
                .blast(b -> b
                        .temp(1941, GasTier.MID)
                        .blastStats(VA[HV], 1500)
                        .vacuumStats(VA[HV]))
                .build();

        Tritium = Material.builder(114, gregtechId("tritium"))
                .gas(new FluidBuilder().customStill())
                .color(0xFC5C5C)
                .iconSet(METALLIC)
                .element(Elements.T)
                .build();

        Tungsten = Material.builder(115, gregtechId("tungsten"))
                .ingot(3)
                .liquid(new FluidBuilder().temperature(3695))
                .color(0x323232).iconSet(METALLIC)

                .element(Elements.W)
                .rotorStats(7.0f, 3.0f, 2560)
                .cableProperties(V[IV], 2, 2)
                .fluidPipeProperties(4618, 50, true, true, false, true)
                .blast(b -> b
                        .temp(3600, GasTier.MID)
                        .blastStats(VA[EV], 1800)
                        .vacuumStats(VA[HV], 300))
                .build();

        Uranium = Material.builder(116, gregtechId("uranium"))
                .dust(3)
                .liquid(new FluidBuilder().temperature(1405))
                .color(0x32F032).iconSet(METALLIC)
                .element(Elements.U)
                .build();
        Uranium.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(0.2f));

        Uranium235 = Material.builder(117, gregtechId("uranium_235"))
                .dust(3)
                .liquid(new FluidBuilder().temperature(1405))
                .color(0x46FA46).iconSet(SHINY)
                .element(Elements.U235)
                .build();
        Uranium235.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(0.8f));

        Vanadium = Material.builder(118, gregtechId("vanadium"))
                .ingot().fluid()
                .color(0x323232).iconSet(METALLIC)
                .element(Elements.V)
                .blast(2183, GasTier.MID)
                .build();

        Xenon = Material.builder(119, gregtechId("xenon"))
                .gas()
                .plasma()
                .liquid(new FluidBuilder()
                        .temperature(165)
                        .color(0x00FFFF)
                        .name("liquid_xenon")
                        .translation("gregtech.fluid.liquid_generic"))
                .color(0x00FFFF)
                .element(Elements.Xe)
                .build();
        Xenon.getProperty(PropertyKey.FLUID).setPrimaryKey(FluidStorageKeys.GAS);

        Ytterbium = Material.builder(120, gregtechId("ytterbium"))
                .ingot()
                .fluid()
                .color(0xA7A7A7).iconSet(METALLIC)
                .element(Elements.Yb)
                .build();

        Yttrium = Material.builder(121, gregtechId("yttrium"))
                .ingot().fluid()
                .color(0x76524C).iconSet(METALLIC)
                .element(Elements.Y)
                .blast(1799)
                .build();

        Zinc = Material.builder(122, gregtechId("zinc"))
                .ingot(1)
                .liquid(new FluidBuilder().temperature(693))
                .plasma()
                .color(0xEBEBFA).iconSet(METALLIC)
                .flags(MORTAR_GRINDABLE)
                .element(Elements.Zn)
                .build();

        Zirconium = Material.builder(123, gregtechId("zirconium"))
                .ingot()
                .fluid()
                .color(0xC8FFFF).iconSet(METALLIC)
                .element(Elements.Zr)
                .blast(b -> b.temp(2125, GasTier.MID)
                        .blastStats(GTValues.VA[GTValues.EV], 1200))
                .build();

        Naquadah = Material.builder(124, gregtechId("naquadah"))
                .ingot(4)
                .liquid(new FluidBuilder().customStill())
                .color(0x323232).iconSet(METALLIC)
                .flags(GENERATE_SHEET)
                .element(Elements.Nq)
                .rotorStats(6.0f, 4.0f, 1280)
                .cableProperties(V[ZPM], 2, 2)
                .fluidPipeProperties(3776, 200, true, false, true, true)
                .blast(b -> b
                        .temp(5000, GasTier.HIGH)
                        .blastStats(VA[IV], 600)
                        .vacuumStats(VA[EV], 150))
                .build();

        NaquadahEnriched = Material.builder(125, gregtechId("naquadah_enriched"))
                .ingot(4)
                .liquid(new FluidBuilder().customStill())
                .color(0x3C3C3C).iconSet(METALLIC)
                .element(Elements.Nq1)
                .blast(b -> b
                        .temp(7000, GasTier.HIGH)
                        .blastStats(VA[IV], 1000)
                        .vacuumStats(VA[EV], 150))
                .build();

        Naquadria = Material.builder(126, gregtechId("naquadria"))
                .ingot(3)
                .liquid(new FluidBuilder().customStill())
                .color(0x1E1E1E).iconSet(SHINY)
                .element(Elements.Nq2)
                .blast(b -> b
                        .temp(9000, GasTier.HIGH)
                        .blastStats(VA[ZPM], 1200)
                        .vacuumStats(VA[LuV], 200))
                .build();

        Neutronium = Material.builder(127, gregtechId("neutronium"))
                .ingot(6)
                .liquid(new FluidBuilder().temperature(100_000))
                .color(0xFAFAFA)
                .element(Elements.Nt)
                .toolStats(MaterialToolProperty.Builder.of(180.0F, 100.0F, 65535, 6)
                        .attackSpeed(0.5F).enchantability(33).magnetic().unbreakable().build())
                .rotorStats(24.0f, 12.0f, 655360)
                .fluidPipeProperties(100_000, 5000, true, true, true, true)
                .build();

        Tritanium = Material.builder(128, gregtechId("tritanium"))
                .ingot(6)
                .liquid(new FluidBuilder().temperature(25_000))
                .color(0x600000).iconSet(METALLIC)
                .element(Elements.Tr)
                .cableProperties(V[UV], 1, 8)
                .rotorStats(20.0f, 6.0f, 10240)
                .build();

        Duranium = Material.builder(129, gregtechId("duranium"))
                .ingot(5)
                .liquid(new FluidBuilder().temperature(7500))
                .color(0x4BAFAF).iconSet(BRIGHT)
                .element(Elements.Dr)
                .toolStats(MaterialToolProperty.Builder.of(14.0F, 12.0F, 8192, 5)
                        .attackSpeed(0.3F).enchantability(33).magnetic().build())
                .fluidPipeProperties(9625, 500, true, true, true, true)
                .build();

        Trinium = Material.builder(130, gregtechId("trinium"))
                .ingot(7).fluid()
                .color(0x9973BD).iconSet(SHINY)
                .element(Elements.Ke)
                .cableProperties(V[ZPM], 6, 4)
                .blast(b -> b
                        .temp(7200, GasTier.HIGH)
                        .blastStats(VA[LuV], 1500)
                        .vacuumStats(VA[IV], 300))
                .build();

        Uranium238 = Material.builder(131, gregtechId("uranium_238"))
                .ingot(3)
                .fluid()
                .color(0x46FA46).iconSet(ROUGH)
                .element(Elements.U238)
                .build();
        Uranium238.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(0.1f));

        Plutonium = Material.builder(132, gregtechId("plutonium"))
                .ingot()
                .fluid()
                .color(0xF03232).iconSet(ROUGH)
                .element(Elements.Pu)
                .build();
        Plutonium.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(1.5f));

        // Nuclear isotopes — all radioactive
        Radium225 = Material.builder(133, gregtechId("radium_225"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0xE0E0E0).element(Elements.Ra225).build();
        Radium225.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(3.0f));
        Radium226 = Material.builder(134, gregtechId("radium_226"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0xD3D3D3).element(Elements.Ra226).build();
        Radium226.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.0f));
        Protactinium231 = Material.builder(135, gregtechId("protactinium_231"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x708090).element(Elements.Pa231).build();
        Protactinium231.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.0f));
        Protactinium233 = Material.builder(136, gregtechId("protactinium_233"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x778899).element(Elements.Pa233).build();
        Protactinium233.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.5f));
        Uranium232 = Material.builder(137, gregtechId("uranium_232"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x2F4F4F).element(Elements.U232).build();
        Uranium232.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(3.0f));
        Uranium233 = Material.builder(138, gregtechId("uranium_233"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x3B3B3B).element(Elements.U233).build();
        Uranium233.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.0f));
        Uranium234 = Material.builder(139, gregtechId("uranium_234"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x90EE90).element(Elements.U234).build();
        Uranium234.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(1.0f));
        Uranium236 = Material.builder(140, gregtechId("uranium_236"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x8FBC8F).element(Elements.U236).build();
        Uranium236.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(1.0f));
        Uranium237 = Material.builder(141, gregtechId("uranium_237"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x7CFC00).element(Elements.U237).build();
        Uranium237.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.5f));
        Uranium239 = Material.builder(142, gregtechId("uranium_239"))
                .ingot().dust().fluid().flags(GENERATE_PELLETS).color(0x46FA46).iconSet(SHINY).element(Elements.U239)
                .build();
        Uranium239.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.0f));
        Neptunium235 = Material.builder(143, gregtechId("neptunium_235"))
                .ingot().dust().fluid().flags(GENERATE_PELLETS).color(0x284D7B).iconSet(METALLIC)
                .element(Elements.Np235).build();
        Neptunium235.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(1.5f));
        Neptunium236 = Material.builder(144, gregtechId("neptunium_236"))
                .ingot().dust().fluid().flags(GENERATE_PELLETS).color(0x284D7B).iconSet(METALLIC)
                .element(Elements.Np236).build();
        Neptunium236.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.0f));
        Neptunium237 = Material.builder(145, gregtechId("neptunium_237"))
                .ingot().dust().fluid().flags(GENERATE_PELLETS).color(0x284D7B).iconSet(METALLIC)
                .element(Elements.Np237).build();
        Neptunium237.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(1.5f));
        Neptunium238 = Material.builder(146, gregtechId("neptunium_238"))
                .ingot().dust().fluid().flags(GENERATE_PELLETS).color(0x284D7B).iconSet(METALLIC)
                .element(Elements.Np238).build();
        Neptunium238.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.5f));
        Neptunium239 = Material.builder(147, gregtechId("neptunium_239"))
                .ingot().dust().fluid().flags(GENERATE_PELLETS).color(0x284D7B).iconSet(METALLIC)
                .element(Elements.Np239).build();
        Neptunium239.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.0f));
        Plutonium236 = Material.builder(148, gregtechId("plutonium_236"))
                .ingot().dust().liquid(new FluidBuilder().temperature(913)).flags(GENERATE_PELLETS).color(0xF03232)
                .iconSet(METALLIC).element(Elements.Pu236).build();
        Plutonium236.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(3.0f));
        Plutonium237 = Material.builder(149, gregtechId("plutonium_237"))
                .ingot().dust().liquid(new FluidBuilder().temperature(913)).flags(GENERATE_PELLETS).color(0xF03232)
                .iconSet(METALLIC).element(Elements.Pu237).build();
        Plutonium237.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.5f));
        Plutonium238 = Material.builder(150, gregtechId("plutonium_238"))
                .ingot().dust().liquid(new FluidBuilder().temperature(913)).flags(GENERATE_PELLETS).color(0xF03232)
                .iconSet(METALLIC).element(Elements.Pu238).build();
        Plutonium238.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.0f));
        Plutonium240 = Material.builder(151, gregtechId("plutonium_240"))
                .ingot().dust().liquid(new FluidBuilder().temperature(913)).flags(GENERATE_PELLETS).color(0xF03232)
                .iconSet(METALLIC).element(Elements.Pu240).build();
        Plutonium240.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(1.5f));
        Plutonium242 = Material.builder(152, gregtechId("plutonium_242"))
                .ingot().dust().liquid(new FluidBuilder().temperature(913)).flags(GENERATE_PELLETS).color(0xF03232)
                .iconSet(METALLIC).element(Elements.Pu242).build();
        Plutonium242.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(1.0f));
        Plutonium243 = Material.builder(153, gregtechId("plutonium_243"))
                .ingot().dust().liquid(new FluidBuilder().temperature(913)).flags(GENERATE_PELLETS).color(0xF03232)
                .iconSet(METALLIC).element(Elements.Pu243).build();
        Plutonium243.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(1.5f));
        Plutonium244 = Material.builder(154, gregtechId("plutonium_244"))
                .ingot().dust().liquid(new FluidBuilder().temperature(913)).flags(GENERATE_PELLETS).color(0xF03232)
                .iconSet(METALLIC).element(Elements.Pu244).build();
        Plutonium244.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(0.8f));
        Thorium228 = Material.builder(155, gregtechId("thorium_228"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0xFF8C00).element(Elements.Th228).build();
        Thorium228.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.0f));
        Thorium229 = Material.builder(156, gregtechId("thorium_229"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0xFFD700).element(Elements.Th229).build();
        Thorium229.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(1.5f));
        Thorium230 = Material.builder(157, gregtechId("thorium_230"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0xFFA500).element(Elements.Th230).build();
        Thorium230.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(1.5f));
        Thorium232 = Material.builder(158, gregtechId("thorium_232"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0xB8860B).element(Elements.Th232).build();
        Thorium232.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(0.1f));
        Thorium233 = Material.builder(159, gregtechId("thorium_233"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0xCD853F).element(Elements.Th233).build();
        Thorium233.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.0f));
        Americium240 = Material.builder(160, gregtechId("americium_240"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x008B8B).element(Elements.Am240).build();
        Americium240.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.5f));
        Americium241 = Material.builder(161, gregtechId("americium_241"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x00868B).element(Elements.Am241).build();
        Americium241.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.0f));
        Americium242 = Material.builder(162, gregtechId("americium_242"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x008B45).element(Elements.Am242).build();
        Americium242.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.5f));
        Americium243 = Material.builder(163, gregtechId("americium_243"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x006400).element(Elements.Am243).build();
        Americium243.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.0f));
        Curium242 = Material.builder(164, gregtechId("curium_242"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x4169E1).element(Elements.Cm242).build();
        Curium242.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(3.0f));
        Curium243 = Material.builder(165, gregtechId("curium_243"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x0000FF).element(Elements.Cm243).build();
        Curium243.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(3.5f));
        Curium244 = Material.builder(166, gregtechId("curium_244"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x1E90FF).element(Elements.Cm244).build();
        Curium244.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(3.5f));
        Curium245 = Material.builder(167, gregtechId("curium_245"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x0000EE).element(Elements.Cm245).build();
        Curium245.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(3.0f));
        Curium246 = Material.builder(168, gregtechId("curium_246"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x0000CD).element(Elements.Cm246).build();
        Curium246.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.5f));
        Curium247 = Material.builder(169, gregtechId("curium_247"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x0000AA).element(Elements.Cm247).build();
        Curium247.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(2.0f));
        Curium248 = Material.builder(170, gregtechId("curium_248"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x000080).element(Elements.Cm248).build();
        Curium248.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(1.5f));
        Curium250 = Material.builder(171, gregtechId("curium_250"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x191970).element(Elements.Cm250).build();
        Curium250.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(1.0f));
        Berkelium249 = Material.builder(172, gregtechId("berkelium_249"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x696969).element(Elements.Bk249).build();
        Berkelium249.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(3.0f));
        Californium249 = Material.builder(173, gregtechId("californium_249"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x708090).element(Elements.Cf249).build();
        Californium249.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(3.5f));
        Californium252 = Material.builder(174, gregtechId("californium_252"))
                .ingot().fluid().dust().flags(GENERATE_PELLETS).color(0x2F4F4F).element(Elements.Cf252).build();
        Californium252.setProperty(PropertyKey.RADIOACTIVE, new RadioactiveProperty(4.0f));
    }
}
