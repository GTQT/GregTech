package gregtech.loaders.recipe.handlers;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.AlloyBlastProperty;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AlloyBlastRecipeHandler {

    private AlloyBlastRecipeHandler() {}

    public static void register() {
        OrePrefix.ingot.addProcessingHandler(PropertyKey.ALLOY_BLAST, AlloyBlastRecipeHandler::generateAlloyBlastRecipes);
    }

    /**
     * Generates alloy blast recipes for a material
     *
     * @param material the material to generate for
     * @param property the blast property of the material
     */
    public static void generateAlloyBlastRecipes(@Nullable OrePrefix unused, @NotNull Material material,
                                                 @NotNull AlloyBlastProperty property) {
        if (material.hasProperty(PropertyKey.BLAST)) {
            property.getRecipeProducer().produce(material, material.getProperty(PropertyKey.BLAST));
        }
    }
}
