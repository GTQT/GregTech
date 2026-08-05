package gregtech.loaders.recipe;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.recipes.ModHandler;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.material.MarkerMaterials;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.UnificationEntry;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockFusionCasing;
import gregtech.common.blocks.BlockGlassCasing;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.BlockUniqueCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.MetaTileEntities;

import org.apache.commons.lang3.ArrayUtils;

import static gregtech.api.GTValues.*;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.ore.OrePrefix.*;
import static gregtech.common.items.MetaItems.*;
import static gregtech.common.metatileentities.MetaTileEntities.*;
import static gregtech.loaders.recipe.CraftingComponent.*;
import static gregtech.loaders.recipe.CraftingComponent.HULL;
import static gregtech.loaders.recipe.MetaTileEntityLoader.registerMachineRecipe;

public final class MultiblockLoader {

    public static void init() {
        CasingLoader();
        ControllerLoader();
    }

    public static void CasingLoader() {
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

    public static void ControllerLoader() {
        ModHandler.addShapedRecipe(true, "large_macerator", MetaTileEntities.LARGE_MACERATOR.getStackForm(),
                "TCT", "PSP", "MWM",
                'T', new UnificationEntry(plate, TungstenCarbide),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'P', MetaItems.ELECTRIC_PISTON_IV.getStackForm(),
                'S', MetaTileEntities.MACERATOR[IV].getStackForm(),
                'M', MetaItems.ELECTRIC_MOTOR_IV.getStackForm(),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "alloy_blast_smelter", MetaTileEntities.ALLOY_BLAST_SMELTER.getStackForm(),
                "TCT", "WSW", "TCT",
                'T', new UnificationEntry(plate, TantalumCarbide),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.EV),
                'S', MetaTileEntities.ALLOY_SMELTER[EV].getStackForm(),
                'W', new UnificationEntry(cableGtSingle, Aluminium));

        ModHandler.addShapedRecipe(true, "large_arc_furnace", MetaTileEntities.LARGE_ARC_FURNACE.getStackForm(),
                "WGW", "CSC", "TTT",
                'T', new UnificationEntry(plate, TantalumCarbide),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'G', new UnificationEntry(dust, Graphite),
                'S', MetaTileEntities.ARC_FURNACE[IV].getStackForm(),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_assembler", MetaTileEntities.LARGE_ASSEMBLER.getStackForm(),
                "RWR", "CSC", "PWP",
                'R', MetaItems.ROBOT_ARM_IV.getStackForm(),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'P', MetaItems.CONVEYOR_MODULE_IV.getStackForm(),
                'S', MetaTileEntities.ASSEMBLER[IV].getStackForm(),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_autoclave", MetaTileEntities.LARGE_AUTOCLAVE.getStackForm(),
                "ACA", "ASA", "PWP",
                'A', new UnificationEntry(plateDouble, HSLASteel),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'P', MetaItems.ELECTRIC_PUMP_IV.getStackForm(),
                'S', MetaTileEntities.AUTOCLAVE[IV].getStackForm(),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_bender", MetaTileEntities.LARGE_BENDER.getStackForm(),
                "PWP", "BCS", "FWH",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'P', MetaItems.ELECTRIC_PISTON_IV.getStackForm(),
                'B', MetaTileEntities.BENDER[IV].getStackForm(),
                'S', MetaTileEntities.COMPRESSOR[IV].getStackForm(),
                'F', MetaTileEntities.FORMING_PRESS[IV].getStackForm(),
                'H', MetaTileEntities.FORGE_HAMMER[IV].getStackForm(),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_brewer", MetaTileEntities.LARGE_BREWERY.getStackForm(),
                "SCS", "BFH", "PWP",
                'S', new UnificationEntry(spring, MolybdenumDisilicide),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'P', MetaItems.ELECTRIC_PUMP_IV.getStackForm(),
                'B', MetaTileEntities.BREWERY[IV].getStackForm(),
                'F', MetaTileEntities.FERMENTER[IV].getStackForm(),
                'H', MetaTileEntities.FLUID_HEATER[IV].getStackForm(),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_centrifuge", MetaTileEntities.LARGE_CENTRIFUGE.getStackForm(),
        "ACA", "ASA", "PWP",
                'A', new UnificationEntry(pipeHugeFluid, StainlessSteel),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'P', MetaItems.ELECTRIC_MOTOR_IV.getStackForm(),
                'S', MetaTileEntities.CENTRIFUGE[IV].getStackForm(),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_thermal_centrifuge", MetaTileEntities.LARGE_THERMAL_CENTRIFUGE.getStackForm(),
                "ACA", "ASA", "PWP",
                'A', new UnificationEntry(spring, MolybdenumDisilicide),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'P', MetaItems.ELECTRIC_MOTOR_IV.getStackForm(),
                'S', MetaTileEntities.THERMAL_CENTRIFUGE[IV].getStackForm(),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_sonicator", MetaTileEntities.LARGE_SONICATOR.getStackForm(),
                "LFL", "PHP", "CPC",
                'L', new UnificationEntry(pipeLargeFluid, Naquadah),
                'F', FIELD_GENERATOR_UV.getStackForm(),
                'P', ELECTRIC_PUMP_UV.getStackForm(),
                'H', MetaTileEntities.HULL[UV].getStackForm(),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.UV));

        ModHandler.addShapedRecipe(true, "large_rock_breaker", LARGE_ROCK_BREAKER.getStackForm(),
                "ICI", "PXP", "WCW",
                'X', ROCK_BREAKER[IV].getStackForm(),
                'C', COMPONENT_GRINDER_TUNGSTEN,
                'W', new UnificationEntry(cableGtSingle, Platinum),
                'P', ELECTRIC_PISTON_IV,
                'I', new UnificationEntry(pipeLargeItem, SterlingSilver));

        ModHandler.addShapedRecipe(true, "large_gas_collector", LARGE_GAS_COLLECTOR.getStackForm(),
                "ARA", "PHP", "WFW",
                'H', GAS_COLLECTOR[IV].getStackForm(),
                'P', new UnificationEntry(pipeNormalFluid, TungstenSteel),
                'W', new UnificationEntry(cableGtQuadruple, Platinum),
                'R', new UnificationEntry(rotor, StainlessSteel),
                'A', new UnificationEntry(plate, BlackSteel),
                'F', FIELD_GENERATOR_IV);

        ModHandler.addShapedRecipe(true, "large_chemical_bath", MetaTileEntities.LARGE_CHEMICAL_BATH.getStackForm(),
                "PGP", "BCO", "MWM",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'B', MetaTileEntities.CHEMICAL_BATH[IV].getStackForm(),
                'O', MetaTileEntities.ORE_WASHER[IV].getStackForm(),
                'G', MetaBlocks.TRANSPARENT_CASING.getItemVariant(BlockGlassCasing.CasingType.TEMPERED_GLASS),
                'P', MetaItems.ELECTRIC_PUMP_IV.getStackForm(),
                'M', MetaItems.CONVEYOR_MODULE_IV.getStackForm(),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_extractor", MetaTileEntities.LARGE_EXTRACTOR.getStackForm(),
                "PGP", "BCO", "MWM",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'B', MetaTileEntities.EXTRACTOR[IV].getStackForm(),
                'O', MetaTileEntities.CANNER[IV].getStackForm(),
                'G', MetaBlocks.TRANSPARENT_CASING.getItemVariant(BlockGlassCasing.CasingType.TEMPERED_GLASS),
                'P', MetaItems.ELECTRIC_PUMP_IV.getStackForm(),
                'M', MetaItems.ELECTRIC_PISTON_IV.getStackForm(),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_cutter", MetaTileEntities.LARGE_CUTTER.getStackForm(),
                "SPS", "BCO", "MWM",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'B', MetaTileEntities.CUTTER[IV].getStackForm(),
                'O', MetaTileEntities.LATHE[IV].getStackForm(),
                'S', new UnificationEntry(toolHeadBuzzSaw, TungstenCarbide),
                'P', MetaItems.CONVEYOR_MODULE_IV.getStackForm(),
                'M', MetaItems.ELECTRIC_MOTOR_IV.getStackForm(),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_distillery", MetaTileEntities.LARGE_DISTILLERY.getStackForm(),
                "LCL", "PSP", "LCL",
                'L', new UnificationEntry(pipeLargeFluid, Iridium),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'P', MetaItems.ELECTRIC_PUMP_IV.getStackForm(),
                'S', MetaTileEntities.DISTILLATION_TOWER.getStackForm());

        ModHandler.addShapedRecipe(true, "large_electrolyzer", MetaTileEntities.LARGE_ELECTROLYZER.getStackForm(),
                "PCP", "LSL", "PWP",
                'L', new UnificationEntry(wireGtQuadruple, Osmium),
                'P', new UnificationEntry(plate, BlackSteel),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'S', MetaTileEntities.ELECTROLYZER[IV].getStackForm(),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_polarizer", MetaTileEntities.LARGE_POLARIZER.getStackForm(),
                "PSP", "BCO", "WSW",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'B', MetaTileEntities.POLARIZER[IV].getStackForm(),
                'O', MetaTileEntities.ELECTROMAGNETIC_SEPARATOR[IV].getStackForm(),
                'S', new UnificationEntry(wireGtQuadruple, Osmium),
                'P', new UnificationEntry(plate, BlackSteel),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_extruder", MetaTileEntities.LARGE_EXTRUDER.getStackForm(),
                "LCL", "PSP", "OWO",
                'L', new UnificationEntry(pipeLargeItem, Ultimet),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'S', MetaTileEntities.EXTRUDER[IV].getStackForm(),
                'P', MetaItems.ELECTRIC_PISTON_IV.getStackForm(),
                'O', new UnificationEntry(spring, MolybdenumDisilicide),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_chemical_complex", MetaTileEntities.LARGE_CHEMICAL_COMPLEX.getStackForm(),
                "LCL", "PSP", "OWO",
                'L', new UnificationEntry(pipeLargeItem, Ultimet),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'S', MetaTileEntities.CHEMICAL_REACTOR[IV].getStackForm(),
                'P', MetaItems.ELECTRIC_PUMP_IV.getStackForm(),
                'O', new UnificationEntry(spring, MolybdenumDisilicide),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_desulfurizer", MetaTileEntities.LARGE_DESULFURIZER.getStackForm(),
                "LCL", "PSP", "OWO",
                'L', new UnificationEntry(pipeLargeItem, Ultimet),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'S', MetaTileEntities.DESULFURIZER[IV].getStackForm(),
                'P', MetaItems.ELECTRIC_PUMP_IV.getStackForm(),
                'O', new UnificationEntry(spring, MolybdenumDisilicide),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_polymerization", MetaTileEntities.LARGE_POLYMERIZATION.getStackForm(),
                "LCL", "PSP", "OWO",
                'L', new UnificationEntry(pipeLargeItem, Ultimet),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'S', MetaTileEntities.POLYMERIZATION_TANK[IV].getStackForm(),
                'P', MetaItems.ELECTRIC_PUMP_IV.getStackForm(),
                'O', new UnificationEntry(spring, MolybdenumDisilicide),
                'W', new UnificationEntry(cableGtSingle, Palladium));

        ModHandler.addShapedRecipe(true, "large_pyrolyser", MetaTileEntities.LARGE_PYROLYSER.getStackForm(),
                "LCL", "PSP", "OWO",
                'L', new UnificationEntry(pipeNormalFluid, Polyethylene),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.EV),
                'S', MetaTileEntities.PYROLYSE_OVEN.getStackForm(),
                'P', MetaItems.ELECTRIC_PUMP_EV.getStackForm(),
                'O', new UnificationEntry(spring, MolybdenumDisilicide),
                'W', new UnificationEntry(cableGtSingle, Gold));

        ModHandler.addShapedRecipe(true, "large_solidifier", MetaTileEntities.LARGE_SOLIDIFIER.getStackForm(),
                "LCL", "PSP", "LWL",
                'L', new UnificationEntry(pipeNormalFluid, Polyethylene),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'S', MetaTileEntities.FLUID_SOLIDIFIER[IV].getStackForm(),
                'P', MetaItems.ELECTRIC_PUMP_IV.getStackForm(),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_mixer", MetaTileEntities.LARGE_MIXER.getStackForm(),
                "LCL", "RSR", "MWM",
                'L', new UnificationEntry(pipeNormalFluid, Polybenzimidazole),
                'R', new UnificationEntry(rotor, Iridium),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'S', MetaTileEntities.MIXER[IV].getStackForm(),
                'M', MetaItems.ELECTRIC_MOTOR_IV.getStackForm(),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_packager", MetaTileEntities.LARGE_PACKAGER.getStackForm(),
                "RCR", "PSP", "MPM",
                'P', new UnificationEntry(plate, HSLASteel),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.EV),
                'S', MetaTileEntities.PACKER[HV].getStackForm(),
                'R', MetaItems.ROBOT_ARM_HV.getStackForm(),
                'M', MetaItems.CONVEYOR_MODULE_HV.getStackForm());

        ModHandler.addShapedRecipe(true, "large_engraver", MetaTileEntities.LARGE_ENGRAVER.getStackForm(),
                "ECE", "PSP", "DWD",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'S', MetaTileEntities.LASER_ENGRAVER[IV].getStackForm(),
                'E', MetaItems.EMITTER_IV.getStackForm(),
                'P', MetaItems.ELECTRIC_PISTON_IV.getStackForm(),
                'D', new UnificationEntry(plateDense, TantalumCarbide),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_sifter", MetaTileEntities.LARGE_SIFTER.getStackForm(),
                "ACA", "PSP", "AWA",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'S', MetaTileEntities.SIFTER[IV].getStackForm(),
                'P', MetaItems.ELECTRIC_PISTON_IV.getStackForm(),
                'A', new UnificationEntry(plate, HSLASteel),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "large_wiremill", MetaTileEntities.LARGE_WIREMILL.getStackForm(),
                "ACA", "RSR", "MWM",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.IV),
                'S', MetaTileEntities.WIREMILL[IV].getStackForm(),
                'M', MetaItems.ELECTRIC_MOTOR_IV.getStackForm(),
                'R', new UnificationEntry(spring, HSLASteel),
                'A', new UnificationEntry(plate, HSLASteel),
                'W', new UnificationEntry(cableGtSingle, Platinum));

        ModHandler.addShapedRecipe(true, "electric_implosion_compressor",
                MetaTileEntities.ELECTRIC_IMPLOSION_COMPRESSOR.getStackForm(),
                "PCP", "FSF", "PCP",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.ZPM),
                'S', MetaTileEntities.IMPLOSION_COMPRESSOR.getStackForm(),
                'P', MetaItems.ELECTRIC_PISTON_IV.getStackForm(),
                'F', MetaItems.FIELD_GENERATOR_IV.getStackForm());

        ModHandler.addShapedRecipe(true, "large_mass_fabricator",
                MetaTileEntities.LARGE_MASS_FABRICATOR.getStackForm(),
                "FCF", "ESE", "FWF",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.UHV),
                'S', MetaTileEntities.MASS_FABRICATOR[ZPM].getStackForm(), //todo mid tier configs
                'F', MetaItems.FIELD_GENERATOR_ZPM.getStackForm(),
                'E', MetaItems.EMITTER_ZPM.getStackForm(),
                'W', new UnificationEntry(cableGtDouble, VanadiumGallium));


        ModHandler.addShapedRecipe(true, "large_replicator", MetaTileEntities.LARGE_REPLICATOR.getStackForm(),
                "FCF", "ESE", "FWF",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.UHV),
                'S', MetaTileEntities.REPLICATOR[ZPM].getStackForm(), //todo mid tier configs
                'F', MetaItems.FIELD_GENERATOR_ZPM.getStackForm(),
                'E', MetaBlocks.FUSION_CASING.getItemVariant(BlockFusionCasing.CasingType.FUSION_COIL),
                'W', new UnificationEntry(cableGtDouble, VanadiumGallium));


        ModHandler.addShapedRecipe(true, "mega_blast_furnace",
                MetaTileEntities.MEGA_BLAST_FURNACE.getStackForm(),
                "PCP", "FSF", "DWD",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.UHV),
                'S', MetaTileEntities.ELECTRIC_BLAST_FURNACE.getStackForm(),
                'F', FIELD_GENERATOR_UV.getStackForm(),
                'P', new UnificationEntry(spring, Neutronium),
                'D', new UnificationEntry(plateDense, Neutronium),
                'W', new UnificationEntry(wireGtQuadruple, RutheniumTriniumAmericiumNeutronate));

        ModHandler.addShapedRecipe(true, "mega_vacuum_freezer",
                MetaTileEntities.MEGA_VACUUM_FREEZER.getStackForm(),
                "PCP", "FSF", "DWD",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.UHV),
                'S', MetaTileEntities.VACUUM_FREEZER.getStackForm(),
                'F', FIELD_GENERATOR_UV.getStackForm(),
                'P', new UnificationEntry(pipeNormalFluid, Neutronium),
                'D', new UnificationEntry(plateDense, Neutronium),
                'W', new UnificationEntry(wireGtQuadruple, RutheniumTriniumAmericiumNeutronate));

        ModHandler.addShapedRecipe(true, "mega_alloy_blast_smelter",
                MetaTileEntities.MEGA_ALLOY_BLAST_SMELTER.getStackForm(),
                "PCP", "FSF", "DWD",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.UHV),
                'S', MetaTileEntities.ALLOY_BLAST_SMELTER.getStackForm(),
                'F', FIELD_GENERATOR_UV.getStackForm(),
                'P', new UnificationEntry(pipeNormalFluid, Neutronium),
                'D', new UnificationEntry(plateDense, Neutronium),
                'W', new UnificationEntry(wireGtQuadruple, RutheniumTriniumAmericiumNeutronate));

        ModHandler.addShapedRecipe(true, "mega_chemical_reactor",
                MetaTileEntities.MEGA_CHEMICAL_REACTOR.getStackForm(),
                "PCP", "FSF", "DWD",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.UHV),
                'S', MetaTileEntities.LARGE_CHEMICAL_REACTOR.getStackForm(),
                'F', ELECTRIC_PUMP_UV.getStackForm(),
                'P', new UnificationEntry(pipeNormalFluid, Neutronium),
                'D', new UnificationEntry(plateDense, Neutronium),
                'W', new UnificationEntry(wireGtQuadruple, RutheniumTriniumAmericiumNeutronate));

        ModHandler.addShapedRecipe(true, "mega_cracking_unit",
                MetaTileEntities.MEGA_CRACKING_UNIT.getStackForm(),
                "PCP", "FSF", "DWD",
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.UHV),
                'S', MetaTileEntities.CRACKER.getStackForm(),
                'F', ELECTRIC_PUMP_UV.getStackForm(),
                'P', new UnificationEntry(pipeNormalFluid, Neutronium),
                'D', new UnificationEntry(plateDense, Neutronium),
                'W', new UnificationEntry(wireGtQuadruple, RutheniumTriniumAmericiumNeutronate));

        ModHandler.addShapedRecipe(true, "steam_engine", MetaTileEntities.STEAM_ENGINE.getStackForm(),
                "FPF", "PCP", "SGS",
                'C',
                MetaBlocks.LARGE_MULTIBLOCK_CASING
                        .getItemVariant(BlockLargeMultiblockCasing.CasingType.STEAM_CASING),
                'S', new UnificationEntry(gearSmall, Bronze),
                'G', new UnificationEntry(gear, Steel),
                'F', new UnificationEntry(pipeSmallFluid, Potin),
                'P', new UnificationEntry(plate, Brass));

        ModHandler.addShapedRecipe(true, "large_circuit_assembler",
                MetaTileEntities.LARGE_CIRCUIT_ASSEMBLER.getStackForm(),
                "RER", "CSC", "WPW",
                'R', MetaItems.ROBOT_ARM_LuV.getStackForm(),
                'E', MetaItems.EMITTER_LuV.getStackForm(),
                'C', new UnificationEntry(circuit, MarkerMaterials.Tier.UV),
                'P', MetaItems.CONVEYOR_MODULE_LuV.getStackForm(),
                'S', MetaTileEntities.CIRCUIT_ASSEMBLER[LuV].getStackForm(),
                'W', new UnificationEntry(cableGtSingle, NiobiumTitanium));

        // Parallel Hatches
        registerMachineRecipe(MetaTileEntities.PARALLEL_HATCH,
                "SCE", "CHC", "WCW",
                'C', CIRCUIT,
                'H', HULL,
                'S', SENSOR,
                'E', EMITTER,
                'W', WIRE_QUAD);

        // Tiered Hatches
        MetaTileEntityLoader.registerMachineRecipe(
                ArrayUtils.subarray(MetaTileEntities.TIERED_HATCH, 0, GregTechAPI.isHighTier() ? UHV : UV), "PPP",
                "PCP", "PPP", 'P', CraftingComponent.PLATE, 'C', CraftingComponent.BETTER_CIRCUIT);

        if (!GregTechAPI.isHighTier()) {
            ModHandler.addShapedRecipe(true, "gcym.machine.tiered_hatch.uhv",
                    MetaTileEntities.TIERED_HATCH[UHV].getStackForm(),
                    "PPP", "PCP", "PPP",
                    'P', CraftingComponent.PLATE.getIngredient(UHV),
                    'C', CraftingComponent.CIRCUIT.getIngredient(UHV));
        }
    }
}
