package gregtech.integration.chisel.recipes;

import gregtech.api.recipes.ModHandler;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.MaterialToolProperty;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.UnificationEntry;
import gregtech.api.util.Mods;
import gregtech.integration.chisel.tools.ChiselToolItems;

import static gregtech.api.unification.material.info.MaterialFlags.GENERATE_PLATE;

public class ChiselToolsRecipe {

    public static void init() {
        ModHandler.removeRecipeByName(Mods.Chisel.getResource("chisel_iron"));
        ModHandler.removeRecipeByName(Mods.Chisel.getResource("chisel_diamond"));
        ModHandler.removeRecipeByName(Mods.Chisel.getResource("chisel_hitech"));

        OrePrefix.plate.addProcessingHandler(PropertyKey.TOOL, ChiselToolsRecipe::processTool);
        processFlintWandRecipe();
    }

    private static void processTool(OrePrefix orePrefix, Material material, MaterialToolProperty materialToolProperty) {
        UnificationEntry plate = new UnificationEntry(OrePrefix.plate, material);
        if (material.hasFlag(GENERATE_PLATE)) {
            ModHandler.addShapedRecipe(String.format("chisel_%s", material.getName()),
                    ChiselToolItems.CHISEL.get(material),
                    "fP", "Sh",
                    'P', plate,
                    'S', new UnificationEntry(OrePrefix.stick, Materials.Wood));
        }
    }

    private static void processFlintWandRecipe() {
        ModHandler.addShapedRecipe("chisel_flint",
                ChiselToolItems.CHISEL.get(Materials.Flint),
                "fF", "Sh",
                'F', new UnificationEntry(OrePrefix.gem, Materials.Flint),
                'S', new UnificationEntry(OrePrefix.stick, Materials.Wood));
    }

}
