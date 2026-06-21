package gregtech.loaders.recipe;

import gregtech.api.GTValues;
import gregtech.api.recipes.ModHandler;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.material.MarkerMaterials;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.UnificationEntry;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.BlockUniqueCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.items.MetaItems;

public final class GCYMCasingLoader {

    private GCYMCasingLoader() {}

    public static void init() {
        final int numCasings = ConfigHolder.recipes.casingsPerCraft;

        // Multiblock Casings
        ModHandler.addShapedRecipe(true, "casing_large_macerator",
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.MACERATOR_CASING, numCasings),
                "PhP", "PFP", "PwP", 'P', new UnificationEntry(OrePrefix.plate, Materials.Zeron100), 'F',
                new UnificationEntry(OrePrefix.frameGt, Materials.Titanium));
        ModHandler.addShapedRecipe(true, "casing_high_temperature",
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.HIGH_TEMPERATURE_CASING, numCasings),
                "DhD", "PFP", "DwD", 'P', new UnificationEntry(OrePrefix.plate, Materials.TitaniumCarbide), 'D',
                new UnificationEntry(OrePrefix.plate, Materials.HSLASteel), 'F',
                new UnificationEntry(OrePrefix.frameGt, Materials.TungstenCarbide));
        ModHandler.addShapedRecipe(true, "casing_large_assembler",
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.ASSEMBLING_CASING, numCasings),
                "PhP", "PFP", "PwP", 'P', new UnificationEntry(OrePrefix.plate, Materials.Stellite), 'F',
                new UnificationEntry(OrePrefix.frameGt, Materials.Tungsten));
        ModHandler.addShapedRecipe(true, "casing_stress_proof",
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.STRESS_PROOF_CASING, numCasings),
                "PhP", "PFP", "PwP", 'P', new UnificationEntry(OrePrefix.plate, Materials.MaragingSteel300), 'F',
                new UnificationEntry(OrePrefix.frameGt, Materials.StainlessSteel));
        ModHandler.addShapedRecipe(true, "casing_corrosion_proof",
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.CORROSION_PROOF_CASING, numCasings),
                "PhP", "PFP", "PwP", 'P', new UnificationEntry(OrePrefix.plate, Materials.CobaltBrass), 'F',
                new UnificationEntry(OrePrefix.frameGt, Materials.HSLASteel));
        ModHandler.addShapedRecipe(true, "casing_vibration_safe",
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.VIBRATION_SAFE_CASING, numCasings),
                "PhP", "PFP", "PwP", 'P', new UnificationEntry(OrePrefix.plate, Materials.IncoloyMA956), 'F',
                new UnificationEntry(OrePrefix.frameGt, Materials.IncoloyMA956));
        ModHandler.addShapedRecipe(true, "casing_watertight",
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.WATERTIGHT_CASING, numCasings),
                "PhP", "PFP", "PwP", 'P', new UnificationEntry(OrePrefix.plate, Materials.WatertightSteel), 'F',
                new UnificationEntry(OrePrefix.frameGt, Materials.WatertightSteel));
        ModHandler.addShapedRecipe(true, "casing_large_cutter",
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.CUTTER_CASING, numCasings),
                "PhP", "PFP", "PwP", 'P', new UnificationEntry(OrePrefix.plate, Materials.HastelloyC276), 'F',
                new UnificationEntry(OrePrefix.frameGt, Materials.HastelloyC276));
        ModHandler.addShapedRecipe(true, "casing_nonconducting",
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.NONCONDUCTING_CASING, numCasings),
                "PhP", "PFP", "PwP", 'P', new UnificationEntry(OrePrefix.plate, Materials.HSLASteel), 'F',
                new UnificationEntry(OrePrefix.frameGt, Materials.HSLASteel));
        ModHandler.addShapedRecipe(true, "casing_large_mixer",
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.MIXER_CASING, numCasings),
                "PhP", "PFP", "PwP", 'P', new UnificationEntry(OrePrefix.plate, Materials.HastelloyX), 'F',
                new UnificationEntry(OrePrefix.frameGt, Materials.MaragingSteel300));
        ModHandler.addShapedRecipe(true, "casing_large_thermal_centrifuge",
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.THERMAL_PROCESSING_CASING, numCasings),
                "PhP", "PFP", "PwP", 'P', new UnificationEntry(OrePrefix.plate, Materials.RedSteel), 'F',
                new UnificationEntry(OrePrefix.frameGt, Materials.BlackSteel));
        ModHandler.addShapedRecipe(true, "casing_large_engraver",
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.ENGRAVER_CASING, numCasings),
                "PhP", "PFP", "PwP", 'P', new UnificationEntry(OrePrefix.plate, Materials.TitaniumTungstenCarbide),
                'F', new UnificationEntry(OrePrefix.frameGt, Materials.Titanium));
        ModHandler.addShapedRecipe(true, "casing_atomic",
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.ATOMIC_CASING, numCasings),
                "PhP", "PFP", "PwP", 'P', new UnificationEntry(OrePrefix.plateDouble, Materials.Trinaquadalloy),
                'F', new UnificationEntry(OrePrefix.frameGt, Materials.NaquadahAlloy));

        ModHandler.addShapedRecipe(true, "casing_naquadah_reinforced",
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.NAQUADAH_REINFORCED_CASING, numCasings),
                "PhP", "PFP", "PwP", 'P', new UnificationEntry(OrePrefix.plateDouble, Materials.Tritanium),
                'F', new UnificationEntry(OrePrefix.frameGt, Materials.Naquadria));
        
        ModHandler.addShapedRecipe(true, "casing_steam",
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.STEAM_CASING, numCasings),
                "PhP", "PFP", "PwP", 'P', new UnificationEntry(OrePrefix.plate, Materials.Brass), 'F',
                new UnificationEntry(OrePrefix.frameGt, Materials.Brass));

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plate, Materials.Zeron100, 6)
                .input(OrePrefix.frameGt, Materials.Titanium)
                .circuitMeta(6)
                .outputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.MACERATOR_CASING, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plate, Materials.HSLASteel, 4)
                .input(OrePrefix.plate, Materials.TitaniumCarbide, 2)
                .input(OrePrefix.frameGt, Materials.TungstenCarbide)
                .circuitMeta(6)
                .outputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.HIGH_TEMPERATURE_CASING, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plate, Materials.Stellite, 6)
                .input(OrePrefix.frameGt, Materials.Tungsten)
                .circuitMeta(6)
                .outputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.ASSEMBLING_CASING, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plate, Materials.MaragingSteel300, 6)
                .input(OrePrefix.frameGt, Materials.StainlessSteel)
                .circuitMeta(6)
                .outputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.STRESS_PROOF_CASING, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plate, Materials.CobaltBrass, 6)
                .input(OrePrefix.frameGt, Materials.HSLASteel)
                .circuitMeta(6)
                .outputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.CORROSION_PROOF_CASING, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plate, Materials.IncoloyMA956, 6)
                .input(OrePrefix.frameGt, Materials.IncoloyMA956)
                .circuitMeta(6)
                .outputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.VIBRATION_SAFE_CASING, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plate, Materials.WatertightSteel, 6)
                .input(OrePrefix.frameGt, Materials.WatertightSteel)
                .circuitMeta(6)
                .outputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.WATERTIGHT_CASING, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plate, Materials.HastelloyC276, 6)
                .input(OrePrefix.frameGt, Materials.HastelloyC276)
                .circuitMeta(6)
                .outputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.CUTTER_CASING, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plate, Materials.HSLASteel, 6)
                .input(OrePrefix.frameGt, Materials.HSLASteel)
                .circuitMeta(6)
                .outputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.NONCONDUCTING_CASING, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plate, Materials.HastelloyX, 6)
                .input(OrePrefix.frameGt, Materials.MaragingSteel300)
                .circuitMeta(6)
                .outputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.MIXER_CASING, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plate, Materials.RedSteel, 6)
                .input(OrePrefix.frameGt, Materials.BlackSteel)
                .circuitMeta(6)
                .outputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.THERMAL_PROCESSING_CASING, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plate, Materials.TitaniumTungstenCarbide, 6)
                .input(OrePrefix.frameGt, Materials.Titanium)
                .circuitMeta(6)
                .outputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.ENGRAVER_CASING, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plateDouble, Materials.Trinaquadalloy, 6)
                .input(OrePrefix.frameGt, Materials.NaquadahAlloy)
                .circuitMeta(6)
                .outputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.ATOMIC_CASING, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plateDouble, Materials.Naquadria, 6)
                .input(OrePrefix.frameGt, Materials.Tritanium)
                .circuitMeta(6)
                .outputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.NAQUADAH_REINFORCED_CASING, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plate, Materials.Brass, 6)
                .input(OrePrefix.frameGt, Materials.Brass)
                .circuitMeta(6)
                .outputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.STEAM_CASING, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        // Unique Casings
        ModHandler.addShapedRecipe(true, "casing_crushing_wheels",
                MetaBlocks.UNIQUE_CASING.getItemVariant(BlockUniqueCasing.UniqueCasingType.CRUSHING_WHEELS,
                        numCasings),
                "SSS", "GCG", "GMG", 'S', new UnificationEntry(OrePrefix.gearSmall, Materials.TungstenCarbide), 'G',
                new UnificationEntry(OrePrefix.gear, Materials.Ultimet), 'C',
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.MACERATOR_CASING),
                'M', MetaItems.ELECTRIC_MOTOR_IV.getStackForm());
        ModHandler.addShapedRecipe(true, "casing_slicing_blades",
                MetaBlocks.UNIQUE_CASING.getItemVariant(BlockUniqueCasing.UniqueCasingType.SLICING_BLADES,
                        numCasings),
                "SSS", "GCG", "GMG", 'S', new UnificationEntry(OrePrefix.plate, Materials.TungstenCarbide), 'G',
                new UnificationEntry(OrePrefix.gear, Materials.Ultimet), 'C',
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.CUTTER_CASING),
                'M', MetaItems.ELECTRIC_MOTOR_IV.getStackForm());
        ModHandler.addShapedRecipe(true, "casing_electrolytic_cell",
                MetaBlocks.UNIQUE_CASING.getItemVariant(BlockUniqueCasing.UniqueCasingType.ELECTROLYTIC_CELL,
                        numCasings),
                "WWW", "WCW", "KAK", 'W', new UnificationEntry(OrePrefix.wireGtDouble, Materials.Platinum), 'C',
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.NONCONDUCTING_CASING),
                'K', new UnificationEntry(OrePrefix.circuit, MarkerMaterials.Tier.IV), 'A',
                new UnificationEntry(OrePrefix.cableGtSingle, Materials.Tungsten));
        ModHandler.addShapedRecipe(true, "casing_heat_vent",
                MetaBlocks.UNIQUE_CASING.getItemVariant(BlockUniqueCasing.UniqueCasingType.HEAT_VENT, numCasings),
                "PDP",
                "RLR", "PDP", 'P', new UnificationEntry(OrePrefix.plate, Materials.TantalumCarbide), 'D',
                new UnificationEntry(OrePrefix.plateDouble, Materials.MolybdenumDisilicide), 'R',
                new UnificationEntry(OrePrefix.rotor, Materials.Titanium), 'L',
                new UnificationEntry(OrePrefix.stickLong, Materials.MolybdenumDisilicide));

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.gearSmall, Materials.TungstenCarbide, 3)
                .input(OrePrefix.gear, Materials.Ultimet, 4)
                .inputs(MetaItems.ELECTRIC_MOTOR_IV.getStackForm())
                .inputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.MACERATOR_CASING))
                .outputs(MetaBlocks.UNIQUE_CASING.getItemVariant(BlockUniqueCasing.UniqueCasingType.CRUSHING_WHEELS,
                        numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plate, Materials.TungstenCarbide, 3)
                .input(OrePrefix.gear, Materials.Ultimet, 4)
                .inputs(MetaItems.ELECTRIC_MOTOR_IV.getStackForm())
                .inputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.CUTTER_CASING))
                .outputs(MetaBlocks.UNIQUE_CASING.getItemVariant(BlockUniqueCasing.UniqueCasingType.SLICING_BLADES,
                        numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.wireGtDouble, Materials.Platinum, 5)
                .input(OrePrefix.circuit, MarkerMaterials.Tier.IV, 2)
                .input(OrePrefix.cableGtSingle, Materials.Tungsten)
                .inputs(MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.NONCONDUCTING_CASING))
                .outputs(MetaBlocks.UNIQUE_CASING
                        .getItemVariant(BlockUniqueCasing.UniqueCasingType.ELECTROLYTIC_CELL, numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.plate, Materials.TantalumCarbide, 4)
                .input(OrePrefix.rotor, Materials.Titanium, 2)
                .input(OrePrefix.plateDouble, Materials.MolybdenumDisilicide, 2)
                .input(OrePrefix.stickLong, Materials.MolybdenumDisilicide)
                .outputs(MetaBlocks.UNIQUE_CASING.getItemVariant(BlockUniqueCasing.UniqueCasingType.HEAT_VENT,
                        numCasings))
                .duration(50).EUt(16).buildAndRegister();

        RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                .input(OrePrefix.ring, Materials.MolybdenumDisilicide, 32)
                .input(OrePrefix.foil, Materials.Graphene, 16)
                .fluidInputs(Materials.HSLASteel.getFluid(GTValues.L))
                .outputs(MetaBlocks.UNIQUE_CASING
                        .getItemVariant(BlockUniqueCasing.UniqueCasingType.MOLYBDENUM_DISILICIDE_COIL))
                .duration(500).EUt(GTValues.VA[GTValues.EV]).buildAndRegister();
    }
}
