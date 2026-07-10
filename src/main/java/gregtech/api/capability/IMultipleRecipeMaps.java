package gregtech.api.capability;

import gregtech.api.recipes.RecipeMap;

public interface IMultipleRecipeMaps {

    /**
     * Whether this controller permits ME pattern buffers to select a recipe map
     * independently of the map selected in the controller UI.
     *
     * <p>It is deliberately opt-in. Most multi-map multiblocks are designed to
     * run exactly one selected map and must keep this disabled.</p>
     */
    default boolean supportsRecipeMapPatternRouting() {
        return false;
    }

    /**
     * Used to get all possible RecipeMaps a Multiblock can run
     * 
     * @return array of RecipeMaps
     */
    RecipeMap<?>[] getAvailableRecipeMaps();

    /**
     *
     * @return the currently selected RecipeMap
     */
    RecipeMap<?> getCurrentRecipeMap();

    /** @return the index of the currently selected RecipeMap. Used for UI. */
    int getRecipeMapIndex();

    /** Set the current RecipeMap by index. Used for UI. */
    void setRecipeMapIndex(int index);
}
