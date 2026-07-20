package gregtech.integration.bbw.recipes;

import gregtech.api.recipes.ModHandler;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.MaterialToolProperty;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.UnificationEntry;
import gregtech.api.util.Mods;
import gregtech.integration.bbw.tools.BBWToolItems;

import static gregtech.api.unification.material.info.MaterialFlags.GENERATE_PLATE;
import static gregtech.loaders.recipe.handlers.ToolRecipeHandler.addToolRecipe;

public class BBWToolsRecipe {

    public static void init() {
        ModHandler.removeRecipeByName(Mods.BetterBuildersWands.getResource("recipewandstone"));
        ModHandler.removeRecipeByName(Mods.BetterBuildersWands.getResource("recipewandiron"));
        ModHandler.removeRecipeByName(Mods.BetterBuildersWands.getResource("recipewanddiamond"));

        OrePrefix.plate.addProcessingHandler(PropertyKey.TOOL, BBWToolsRecipe::processTool);
        processFlintWandRecipe();
    }

    private static void processTool(OrePrefix orePrefix, Material material, MaterialToolProperty materialToolProperty) {
        UnificationEntry plate = new UnificationEntry(OrePrefix.plate, material);
        if (material.hasFlag(GENERATE_PLATE)) {
            addToolRecipe(material, BBWToolItems.WAND, false,
                    " fP", " Sh", "S  ",
                    'P', plate,
                    'S', new UnificationEntry(OrePrefix.stick, Materials.Wood));
        }
    }

    private static void processFlintWandRecipe() {
        ModHandler.addShapedRecipe("wand_flint",
                BBWToolItems.WAND.get(Materials.Flint),
                " fF", " Sh", "S  ",
                'F', new UnificationEntry(OrePrefix.gem, Materials.Flint),
                'S', new UnificationEntry(OrePrefix.stick, Materials.Wood));
    }
}
