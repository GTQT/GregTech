package gregtech.api.recipes;

import gregtech.api.recipes.map.AbstractMapIngredient;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Iterator that yields all matching recipes from a RecipeMap's ingredient tree.
 * Supports an exclusion set to skip already-processed recipes, avoiding redundant hits.
 *
 * <p>Usage:
 * <pre>{@code
 * RecipeIterator iter = recipeMap.findRecipeIterator(items, fluids, canHandle);
 * if (iter != null) {
 *     while (iter.hasNext()) {
 *         Recipe recipe = iter.next();
 *         // process recipe...
 *         iter.exclude(recipe); // prevent re-finding this recipe
 *     }
 * }
 * }</pre>
 */
public class RecipeIterator implements Iterator<Recipe> {

    private int index;
    @Nullable
    private final List<List<AbstractMapIngredient>> ingredients;
    @NotNull
    private final RecipeMap<?> recipeMap;
    @NotNull
    private final Predicate<Recipe> canHandle;
    @Nullable
    private Recipe cachedNext;
    @NotNull
    private final Set<Recipe> exclusionSet;

    public RecipeIterator(@NotNull RecipeMap<?> recipeMap,
                          @Nullable List<List<AbstractMapIngredient>> ingredients,
                          @NotNull Predicate<Recipe> canHandle) {
        this.ingredients = ingredients;
        this.recipeMap = recipeMap;
        this.canHandle = canHandle;
        this.exclusionSet = new HashSet<>();
    }

    /**
     * Add a recipe to the exclusion set. Future iterations will skip this recipe.
     */
    public void exclude(@NotNull Recipe recipe) {
        exclusionSet.add(recipe);
    }

    /**
     * Reset the iterator to the beginning, optionally clearing the exclusion set.
     */
    public void reset(boolean clearExclusions) {
        this.index = 0;
        this.cachedNext = null;
        if (clearExclusions) {
            this.exclusionSet.clear();
        }
    }

    @Override
    public boolean hasNext() {
        if (cachedNext != null) return true;
        if (ingredients == null || this.index >= this.ingredients.size()) return false;

        // Wrap canHandle with exclusion filter
        Predicate<Recipe> filteredCanHandle = recipe ->
                !exclusionSet.contains(recipe) && canHandle.test(recipe);

        while (index < ingredients.size()) {
            Recipe r = recipeMap.recurseIngredientTreeFindRecipe(
                    ingredients, recipeMap.getLookup(), filteredCanHandle, index, 0, (1L << index));
            ++index;
            if (r != null) {
                cachedNext = r;
                return true;
            }
        }
        return false;
    }

    @Override
    @Nullable
    public Recipe next() {
        if (cachedNext != null) {
            Recipe r = cachedNext;
            cachedNext = null;
            return r;
        }
        if (ingredients == null) return null;

        Predicate<Recipe> filteredCanHandle = recipe ->
                !exclusionSet.contains(recipe) && canHandle.test(recipe);

        while (index < ingredients.size()) {
            Recipe r = recipeMap.recurseIngredientTreeFindRecipe(
                    ingredients, recipeMap.getLookup(), filteredCanHandle, index, 0, (1L << index));
            ++index;
            if (r != null) return r;
        }
        return null;
    }
}
