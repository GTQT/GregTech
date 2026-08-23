package gregtech.api.worldgen;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.worldgen.config.BedrockFluidDepositBuilder;
import gregtech.api.worldgen.config.OreDepositBuilder;
import gregtech.api.worldgen.config.OreDepositDefinition;
import gregtech.api.worldgen.config.WorldGenRegistry;
import gregtech.common.blocks.StoneVariantBlock.StoneType;

import com.google.common.collect.ImmutableMap;

/**
 * 生成内容与原 JSON 完全一致。
 */
public final class WorldgenDefinitions {

    private WorldgenDefinitions() {}

    public static void registerAll(WorldGenRegistry registry) {
        registerOverworldVeins(registry);
        registerNetherVeins(registry);
        registerEndVeins(registry);
        registerBedrockFluidVeins(registry);
    }

    private static void registerVein(WorldGenRegistry registry, OreDepositDefinition definition) {
        registry.addVeinDefinitions(definition);
    }

    /** 标准分层矿脉：surface_rock populator + layered generator + 4 材料 layered filler */
    private static OreDepositBuilder layeredVein(String name, String translationKey, int weight, float density,
                                                 int minHeight, int maxHeight, Material surfaceRock,
                                                 Material primary, Material secondary, Material between,
                                                 Material sporadic, int radiusMin, int radiusMax) {
        return OreDepositBuilder.definitionBuilder(name)
                .translationKey(translationKey)
                .weight(weight)
                .density(density)
                .minHeight(minHeight)
                .maxHeight(maxHeight)
                .surfaceRock(surfaceRock)
                .layeredGeneration(radiusMin, radiusMax)
                .layeredFill(primary, secondary, between, sporadic);
    }

    private static void registerOverworldVeins(WorldGenRegistry registry) {
        registerVein(registry, layeredVein("overworld/apatite_vein", "gregtech.veins.ore.apatite",
                40, 0.25f, 40, 60, Materials.Apatite,
                Materials.Apatite, Materials.Apatite, Materials.TricalciumPhosphate, Materials.Pyrochlore, 14, 16).build());

        registerVein(registry, layeredVein("overworld/cassiterite_vein", "gregtech.veins.ore.cassiterite",
                80, 0.2f, 40, 60, Materials.Cassiterite,
                Materials.Tin, Materials.Tin, Materials.Cassiterite, Materials.Tin, 17, 24).build());

        registerVein(registry, layeredVein("overworld/coal_vein", "gregtech.veins.ore.coal",
                80, 0.25f, 30, 80, Materials.Coal,
                Materials.Coal, Materials.Coal, Materials.Coal, Materials.Coal, 20, 32).build());

        registerVein(registry, layeredVein("overworld/copper_tin_vein", "gregtech.veins.ore.copper_tin",
                50, 0.2f, 40, 160, Materials.Realgar,
                Materials.Chalcopyrite, Materials.Zeolite, Materials.Cassiterite, Materials.Realgar, 17, 24).build());

        registerVein(registry, layeredVein("overworld/copper_vein", "gregtech.veins.ore.copper",
                80, 0.2f, 5, 60, Materials.Copper,
                Materials.Chalcopyrite, Materials.Iron, Materials.Pyrite, Materials.Copper, 17, 24).build());

        registerVein(registry, layeredVein("overworld/diamond_vein", "gregtech.veins.ore.diamond",
                40, 0.25f, 5, 20, Materials.Diamond,
                Materials.Graphite, Materials.Graphite, Materials.Diamond, Materials.Coal, 14, 16).build());

        registerVein(registry, layeredVein("overworld/galena_vein", "gregtech.veins.ore.galena",
                40, 0.25f, 5, 45, Materials.Galena,
                Materials.Galena, Materials.Galena, Materials.Silver, Materials.Lead, 14, 16).build());

        registerVein(registry, layeredVein("overworld/garnet_tin_vein", "gregtech.veins.ore.garnet_tin",
                80, 0.2f, 50, 60, Materials.Asbestos,
                Materials.CassiteriteSand, Materials.GarnetSand, Materials.Asbestos, Materials.Diatomite, 17, 24).build());

        registerVein(registry, layeredVein("overworld/garnet_vein", "gregtech.veins.ore.garnet",
                40, 0.25f, 10, 30, Materials.GarnetRed,
                Materials.GarnetRed, Materials.GarnetYellow, Materials.Amethyst, Materials.Opal, 14, 16).build());

        registerVein(registry, layeredVein("overworld/iron_vein", "gregtech.veins.ore.iron",
                120, 0.2f, 10, 40, Materials.BandedIron,
                Materials.BrownLimonite, Materials.YellowLimonite, Materials.BandedIron, Materials.Malachite, 17, 24).build());

        registerVein(registry, layeredVein("overworld/lapis_vein", "gregtech.veins.ore.lapis",
                40, 0.25f, 20, 50, Materials.Lapis,
                Materials.Lazurite, Materials.Sodalite, Materials.Lapis, Materials.Calcite, 14, 16).build());

        registerVein(registry, layeredVein("overworld/lubricant_vein", "gregtech.veins.ore.lubricant",
                40, 0.25f, 20, 50, Materials.Soapstone,
                Materials.Soapstone, Materials.Talc, Materials.GlauconiteSand, Materials.Pentlandite, 14, 16).build());

        registerVein(registry, layeredVein("overworld/magnetite_vein", "gregtech.veins.ore.magnetite",
                80, 0.15f, 30, 60, Materials.Magnetite,
                Materials.Magnetite, Materials.Magnetite, Materials.VanadiumMagnetite, Materials.Gold, 20, 32).build());

        registerVein(registry, layeredVein("overworld/manganese_vein", "gregtech.veins.ore.manganese",
                20, 0.25f, 20, 30, Materials.Pyrolusite,
                Materials.Grossular, Materials.Spessartine, Materials.Pyrolusite, Materials.Tantalite, 14, 16).build());

        registerVein(registry, layeredVein("overworld/mica_vein", "gregtech.veins.ore.mica",
                20, 0.25f, 20, 40, Materials.Mica,
                Materials.Kyanite, Materials.Mica, Materials.Bauxite, Materials.Pollucite, 14, 16).build());

        registerVein(registry, layeredVein("overworld/mineral_sand_vein", "gregtech.veins.ore.mineral_sand",
                80, 0.2f, 35, 60, Materials.BasalticMineralSand,
                Materials.BasalticMineralSand, Materials.GraniticMineralSand, Materials.FullersEarth,
                Materials.Gypsum, 17, 24).build());

        registerVein(registry, layeredVein("overworld/nickel_vein", "gregtech.veins.ore.nickel",
                40, 0.25f, 10, 40, Materials.Nickel,
                Materials.Garnierite, Materials.Nickel, Materials.Cobaltite, Materials.Pentlandite, 14, 16).build());

        registerVein(registry, layeredVein("overworld/oilsands_vein", "gregtech.veins.ore.oilsands",
                40, 0.3f, 50, 80, Materials.Oilsands,
                Materials.Oilsands, Materials.Oilsands, Materials.Oilsands, Materials.Oilsands, 20, 32).build());

        registerVein(registry, layeredVein("overworld/olivine_vein", "gregtech.veins.ore.olivine",
                20, 0.25f, 10, 40, Materials.Olivine,
                Materials.Bentonite, Materials.Magnesite, Materials.Olivine, Materials.GlauconiteSand, 14, 16).build());

        registerVein(registry, layeredVein("overworld/redstone_vein", "gregtech.veins.ore.redstone",
                60, 0.2f, 5, 40, Materials.Redstone,
                Materials.Redstone, Materials.Redstone, Materials.Ruby, Materials.Cinnabar, 17, 24).build());

        registerVein(registry, layeredVein("overworld/salts_vein", "gregtech.veins.ore.salts",
                50, 0.2f, 50, 70, Materials.Salt,
                Materials.RockSalt, Materials.Salt, Materials.Lepidolite, Materials.Spodumene, 17, 24).build());

        registerVein(registry, layeredVein("overworld/sapphire_vein", "gregtech.veins.ore.sapphire",
                60, 0.25f, 10, 40, Materials.Sapphire,
                Materials.Almandine, Materials.Pyrope, Materials.Sapphire, Materials.GreenSapphire, 14, 16).build());

        // 石材 sphere（装饰性，不占用矿脉位）
        registerVein(registry, stoneSphere("overworld/basalt_sphere", "gregtech.veins.ore.basalt_sphere",
                120, StoneType.BASALT));

        registerVein(registry, stoneSphere("overworld/black_granite_sphere", "gregtech.veins.ore.black_granite_sphere",
                90, StoneType.BLACK_GRANITE));

        registerVein(registry, stoneSphere("overworld/marble_sphere", "gregtech.veins.ore.marble_sphere",
                120, StoneType.MARBLE));

        registerVein(registry, stoneSphere("overworld/red_granite_sphere", "gregtech.veins.ore.red_granite_sphere",
                90, StoneType.RED_GRANITE));

        // 原油球：流体 sphere + fluid_spring populator
        registerVein(registry, OreDepositBuilder.definitionBuilder("overworld/raw_oil_sphere")
                .translationKey("gregtech.veins.ore.raw_oil_sphere")
                .weight(50)
                .density(1.0f)
                .minHeight(10)
                .maxHeight(40)
                .priority(-100)
                .countAsVein(false)
                .generationPredicateAny()
                .biomeWeightModifierDictionary(ImmutableMap.of("sandy", 5))
                .sphereGeneration(9, 13)
                .fluidSpring(Materials.RawOil.getFluid().getBlock().getDefaultState(), 0.40f)
                .fluidFill(Materials.RawOil.getFluid())
                .build());
    }

    private static void registerNetherVeins(WorldGenRegistry registry) {
        registerVein(registry, layeredVein("nether/banded_iron_vein", "gregtech.veins.ore.banded_iron",
                30, 0.2f, 20, 40, Materials.BandedIron,
                Materials.BrownLimonite, Materials.YellowLimonite, Materials.BandedIron, Materials.Gold, 17, 24)
                .netherOnly().build());

        registerVein(registry, layeredVein("nether/beryllium_vein", "gregtech.veins.ore.beryllium",
                30, 0.25f, 5, 30, Materials.Beryllium,
                Materials.Beryllium, Materials.Beryllium, Materials.Emerald, Materials.Thorium, 14, 16)
                .netherOnly().build());

        registerVein(registry, layeredVein("nether/certus_quartz_vein", "gregtech.veins.ore.certus_quartz",
                40, 0.25f, 80, 120, Materials.Barite,
                Materials.Quartzite, Materials.CertusQuartz, Materials.CertusQuartz, Materials.Barite, 14, 16)
                .netherOnly().build());

        registerVein(registry, layeredVein("nether/manganese_vein", "gregtech.veins.ore.manganese",
                20, 0.25f, 20, 30, Materials.Grossular,
                Materials.Grossular, Materials.Pyrolusite, Materials.Pyrochlore, Materials.Tantalite, 14, 16)
                .netherOnly().build());

        registerVein(registry, layeredVein("nether/molybdenum_vein", "gregtech.veins.ore.molybdenum",
                5, 0.25f, 20, 50, Materials.Molybdenite,
                Materials.Wulfenite, Materials.Molybdenite, Materials.Molybdenum, Materials.Powellite, 14, 16)
                .netherOnly().build());

        registerVein(registry, layeredVein("nether/monazite_vein", "gregtech.veins.ore.monazite",
                30, 0.25f, 20, 40, Materials.Monazite,
                Materials.Bastnasite, Materials.Bastnasite, Materials.Monazite, Materials.Neodymium, 14, 16)
                .netherOnly().build());

        registerVein(registry, layeredVein("nether/nether_quartz_vein", "gregtech.veins.ore.nether_quartz",
                80, 0.2f, 40, 80, Materials.Quartzite,
                Materials.NetherQuartz, Materials.NetherQuartz, Materials.NetherQuartz, Materials.Quartzite, 17, 24)
                .netherOnly().build());

        registerVein(registry, layeredVein("nether/redstone_vein", "gregtech.veins.ore.redstone",
                60, 0.2f, 5, 40, Materials.Redstone,
                Materials.Redstone, Materials.Redstone, Materials.Ruby, Materials.Cinnabar, 17, 24)
                .netherOnly().build());

        registerVein(registry, layeredVein("nether/saltpeter_vein", "gregtech.veins.ore.saltpeter",
                40, 0.25f, 5, 45, Materials.Saltpeter,
                Materials.Saltpeter, Materials.Diatomite, Materials.Electrotine, Materials.Alunite, 14, 16)
                .netherOnly().build());

        registerVein(registry, layeredVein("nether/sulfur_vein", "gregtech.veins.ore.sulfur",
                100, 0.2f, 10, 30, Materials.Sphalerite,
                Materials.Sulfur, Materials.Sulfur, Materials.Pyrite, Materials.Sphalerite, 17, 24)
                .netherOnly().build());

        registerVein(registry, layeredVein("nether/tetrahedrite_vein", "gregtech.veins.ore.tetrahedrite",
                70, 0.2f, 80, 120, Materials.Tetrahedrite,
                Materials.Tetrahedrite, Materials.Tetrahedrite, Materials.Copper, Materials.Stibnite, 17, 24)
                .netherOnly().build());

        registerVein(registry, layeredVein("nether/topaz_vein", "gregtech.veins.ore.topaz",
                40, 0.25f, 80, 120, Materials.Chalcocite,
                Materials.BlueTopaz, Materials.Topaz, Materials.Chalcocite, Materials.Bornite, 14, 16)
                .netherOnly().build());
    }

    private static void registerEndVeins(WorldGenRegistry registry) {
        registerVein(registry, layeredVein("end/bauxite_vein", "gregtech.veins.ore.bauxite",
                40, 0.25f, 10, 80, Materials.Aluminium,
                Materials.Bauxite, Materials.Ilmenite, Materials.Aluminium, Materials.Ilmenite, 14, 16)
                .dimensionName("the_end").build());

        registerVein(registry, layeredVein("end/magnetite_vein", "gregtech.veins.ore.magnetite",
                30, 0.15f, 20, 80, Materials.Chromite,
                Materials.Magnetite, Materials.VanadiumMagnetite, Materials.Chromite, Materials.Gold, 20, 32)
                .dimensionName("the_end").build());

        registerVein(registry, layeredVein("end/naquadah_vein", "gregtech.veins.ore.naquadah",
                30, 0.15f, 10, 90, Materials.Naquadah,
                Materials.Naquadah, Materials.Naquadah, Materials.Naquadah, Materials.Zircon, 20, 32)
                .dimensionName("the_end").build());

        registerVein(registry, layeredVein("end/pitchblende_vein", "gregtech.veins.ore.pitchblende",
                20, 0.25f, 30, 60, Materials.Uraninite,
                Materials.Pitchblende, Materials.Pitchblende, Materials.Uraninite, Materials.Uraninite, 14, 16)
                .dimensionName("the_end").build());

        registerVein(registry, layeredVein("end/scheelite_vein", "gregtech.veins.ore.scheelite",
                20, 0.2f, 20, 60, Materials.Lithium,
                Materials.Scheelite, Materials.Scheelite, Materials.Tungstate, Materials.Lithium, 17, 24)
                .dimensionName("the_end").build());

        registerVein(registry, layeredVein("end/sheldonite_vein", "gregtech.veins.ore.sheldonite",
                10, 0.2f, 5, 50, Materials.Platinum,
                Materials.Bornite, Materials.Cooperite, Materials.Platinum, Materials.Palladium, 14, 16)
                .dimensionName("the_end").build());
    }

    private static void registerBedrockFluidVeins(WorldGenRegistry registry) {
        registry.addVeinDefinitions(BedrockFluidDepositBuilder.definitionBuilder("overworld/heavy_oil_deposit")
                .translationKey("gregtech.veins.fluid.heavy_oil")
                .weight(15)
                .yields(100, 200)
                .depletion(1, 100, 20)
                .biomeWeightModifierDictionary(ImmutableMap.of("ocean", 5, "sandy", 10))
                .fluid(Materials.OilHeavy.getFluid())
                .build());

        registry.addVeinDefinitions(BedrockFluidDepositBuilder.definitionBuilder("overworld/light_oil_deposit")
                .translationKey("gregtech.veins.fluid.light_oil")
                .weight(25)
                .yields(175, 300)
                .depletion(1, 100, 25)
                .fluid(Materials.OilLight.getFluid())
                .build());

        registry.addVeinDefinitions(BedrockFluidDepositBuilder.definitionBuilder("overworld/natural_gas_deposit")
                .translationKey("gregtech.veins.fluid.natural_gas_overworld")
                .weight(15)
                .yields(100, 175)
                .depletion(1, 100, 20)
                .fluid(Materials.NaturalGas.getFluid())
                .build());

        registry.addVeinDefinitions(BedrockFluidDepositBuilder.definitionBuilder("overworld/oil_deposit")
                .translationKey("gregtech.veins.fluid.oil")
                .weight(20)
                .yields(175, 300)
                .depletion(1, 100, 25)
                .biomeWeightModifierDictionary(ImmutableMap.of("ocean", 5, "sandy", 5))
                .fluid(Materials.Oil.getFluid())
                .build());

        registry.addVeinDefinitions(BedrockFluidDepositBuilder.definitionBuilder("overworld/raw_oil_deposit")
                .translationKey("gregtech.veins.fluid.raw_oil")
                .weight(20)
                .yields(200, 300)
                .depletion(1, 100, 25)
                .fluid(Materials.RawOil.getFluid())
                .build());

        registry.addVeinDefinitions(BedrockFluidDepositBuilder.definitionBuilder("overworld/salt_water_deposit")
                .translationKey("gregtech.veins.fluid.salt_water")
                .weight(0)
                .yields(50, 100)
                .depletion(1, 100, 15)
                .biomeWeightModifierMap(ImmutableMap.of(
                        "minecraft:ocean", 150,
                        "minecraft:frozen_ocean", 150,
                        "minecraft:deep_ocean", 200))
                .fluid(Materials.SaltWater.getFluid())
                .build());

        registry.addVeinDefinitions(BedrockFluidDepositBuilder.definitionBuilder("nether/lava_deposit")
                .translationKey("gregtech.veins.fluid.lava")
                .weight(65)
                .yields(125, 250)
                .depletion(1, 100, 30)
                .dimensionName("the_nether")
                .fluid(Materials.Lava.getFluid())
                .build());

        registry.addVeinDefinitions(BedrockFluidDepositBuilder.definitionBuilder("nether/natural_gas_nether_deposit")
                .translationKey("gregtech.veins.fluid.natural_gas_nether")
                .weight(35)
                .yields(150, 300)
                .depletion(1, 100, 40)
                .dimensionName("the_nether")
                .fluid(Materials.NaturalGas.getFluid())
                .build());
    }

    /** 石材 sphere：sphere generator + ignore_bedrock + weight_random(stone_smooth 变体)，不占用矿脉位 */
    private static OreDepositDefinition stoneSphere(String name, String translationKey, int weight,
                                                    StoneType stoneType) {
        return OreDepositBuilder.definitionBuilder(name)
                .translationKey(translationKey)
                .weight(weight)
                .priority(100)
                .density(1.0f)
                .minHeight(10)
                .countAsVein(false)
                .sphereGeneration(10, 20)
                .stoneSmoothSphereFill(stoneType)
                .build();
    }
}
