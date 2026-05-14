package gregtech.api.recipes.machines;

import gregtech.api.recipes.ModHandler;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeIterator;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.ui.RecipeMapUIFunction;
import gregtech.api.util.GTUtility;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@ApiStatus.Internal
public class RecipeMapFurnace extends RecipeMap<SimpleRecipeBuilder> {

    public static final int RECIPE_EUT = 4;
    public static final int RECIPE_DURATION = 128;

    public RecipeMapFurnace(@NotNull String unlocalizedName, @NotNull SimpleRecipeBuilder defaultRecipeBuilder,
                            @NotNull RecipeMapUIFunction recipeMapUI) {
        super(unlocalizedName, defaultRecipeBuilder, recipeMapUI, 1, 1, 0, 0);
        setSound(GTSoundEvents.FURNACE);
    }

    // ==================== Recipe Lookup ====================

    @Override
    @Nullable
    public Recipe findRecipe(long voltage, List<ItemStack> inputs, List<FluidStack> fluidInputs, boolean exactVoltage) {
        Recipe normalRecipe = super.findRecipe(voltage, inputs, fluidInputs, exactVoltage);
        if (normalRecipe != null || inputs.isEmpty())
            return normalRecipe;

        return findVanillaFurnaceRecipe(inputs, null);
    }

    // ==================== Vanilla Furnace Fallback ====================
    // Vanilla furnace recipes (FurnaceRecipes) are not registered in the GT RecipeMap tree.
    // RecipeMapFurnace.findRecipe() handles this with a fallback, but findRecipeIterator()
    // and find() did not, causing cross-recipe parallel mode to miss furnace recipes entirely.
    // The overrides below ensure all recipe search paths include the vanilla fallback.

    @Override
    @Nullable
    public Recipe find(@NotNull Collection<ItemStack> items, @NotNull Collection<FluidStack> fluids,
                       @NotNull Predicate<Recipe> canHandle) {
        Recipe gtRecipe = super.find(items, fluids, canHandle);
        if (gtRecipe != null || items.isEmpty()) {
            return gtRecipe;
        }

        Recipe vanillaRecipe = findVanillaFurnaceRecipe(items, null);
        if (vanillaRecipe != null && canHandle.test(vanillaRecipe)) {
            return vanillaRecipe;
        }
        return null;
    }

    @Override
    @Nullable
    public RecipeIterator findRecipeIterator(@NotNull Collection<ItemStack> items,
                                            @NotNull Collection<FluidStack> fluids,
                                            @NotNull Predicate<Recipe> canHandle) {
        RecipeIterator baseIterator = super.findRecipeIterator(items, fluids, canHandle);
        List<ItemStack> inputSnapshot = new ArrayList<>(items);
        return new FurnaceFallbackIterator(this, baseIterator, inputSnapshot, canHandle);
    }

    /**
     * Searches vanilla FurnaceRecipes for a matching smelting recipe.
     *
     * @param inputs       the item inputs to search
     * @param excludeItems item stacks to skip (already matched by GT tree), or null to skip none
     * @return a dynamically built Recipe, or null if no match found
     */
    @Nullable
    private Recipe findVanillaFurnaceRecipe(@NotNull Collection<ItemStack> inputs,
                                            @Nullable Set<ItemStack> excludeItems) {
        for (ItemStack input : inputs) {
            if (input.isEmpty()) continue;
            if (excludeItems != null && excludeItems.contains(input)) continue;

            ItemStack output = ModHandler.getSmeltingOutput(input);
            if (!output.isEmpty()) {
                return this.recipeBuilder()
                        .inputs(GTUtility.copy(1, input))
                        .outputs(output)
                        .duration(RECIPE_DURATION).EUt(RECIPE_EUT)
                        .build().getResult();
            }
        }
        return null;
    }

    // ==================== Furnace Fallback Iterator ====================
    // Wraps a base RecipeIterator and appends vanilla furnace recipes after the GT tree
    // is exhausted. This allows cross-recipe parallel schedulers to discover furnace recipes.

    /**
     * Iterator that delegates to a base GT RecipeIterator, then falls back to vanilla
     * FurnaceRecipes for any remaining input items. Supports the exclusion set contract
     * required by cross-recipe parallel scheduling.
     */
    private static class FurnaceFallbackIterator extends RecipeIterator {

        @Nullable
        private final RecipeIterator baseIterator;
        @NotNull
        private final List<ItemStack> inputs;
        @NotNull
        private final Predicate<Recipe> canHandle;
        @NotNull
        private final RecipeMapFurnace furnaceMap;
        private boolean baseDone;
        private int vanillaIndex;
        @Nullable
        private Recipe vanillaCached;
        @NotNull
        private final Set<Recipe> vanillaExclusions;

        FurnaceFallbackIterator(@NotNull RecipeMapFurnace furnaceMap,
                                @Nullable RecipeIterator baseIterator,
                                @NotNull List<ItemStack> inputs,
                                @NotNull Predicate<Recipe> canHandle) {
            super(furnaceMap, null, canHandle);
            this.furnaceMap = furnaceMap;
            this.baseIterator = baseIterator;
            this.inputs = inputs;
            this.canHandle = canHandle;
            this.baseDone = (baseIterator == null);
            this.vanillaIndex = 0;
            this.vanillaExclusions = new HashSet<>();
        }

        @Override
        public void exclude(@NotNull Recipe recipe) {
            if (baseIterator != null) {
                baseIterator.exclude(recipe);
            }
            vanillaExclusions.add(recipe);
        }

        @Override
        public void reset(boolean clearExclusions) {
            if (baseIterator != null) {
                baseIterator.reset(clearExclusions);
            }
            baseDone = (baseIterator == null);
            vanillaIndex = 0;
            vanillaCached = null;
            if (clearExclusions) {
                vanillaExclusions.clear();
            }
        }

        @Override
        public boolean hasNext() {
            if (!baseDone) {
                if (baseIterator != null && baseIterator.hasNext()) {
                    return true;
                }
                baseDone = true;
            }
            if (vanillaCached != null) {
                return true;
            }
            vanillaCached = findNextVanilla();
            return vanillaCached != null;
        }

        @Override
        @Nullable
        public Recipe next() {
            if (!baseDone) {
                if (baseIterator != null && baseIterator.hasNext()) {
                    return baseIterator.next();
                }
                baseDone = true;
            }
            if (vanillaCached != null) {
                Recipe r = vanillaCached;
                vanillaCached = null;
                return r;
            }
            return findNextVanilla();
        }

        @Nullable
        private Recipe findNextVanilla() {
            while (vanillaIndex < inputs.size()) {
                ItemStack input = inputs.get(vanillaIndex++);
                if (input.isEmpty()) continue;

                ItemStack output = ModHandler.getSmeltingOutput(input);
                if (output.isEmpty()) continue;

                Recipe recipe = furnaceMap.recipeBuilder()
                        .inputs(GTUtility.copy(1, input))
                        .outputs(output)
                        .duration(RECIPE_DURATION).EUt(RECIPE_EUT)
                        .build().getResult();

                if (recipe == null) continue;
                if (vanillaExclusions.contains(recipe)) continue;
                if (!canHandle.test(recipe)) continue;
                return recipe;
            }
            return null;
        }
    }
}
