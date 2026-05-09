package gregtech.api.recipes.logic;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.IVoidable;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.ParallelLogicType;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;

import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IParallelableRecipeLogic {

    /**
     * Method which applies bonuses or penalties to the recipe based on the parallelization factor,
     * such as EU consumption or processing speed.
     *
     * @param builder the recipe builder
     */
    default void applyParallelBonus(@NotNull RecipeBuilder<?> builder) {}

    /**
     * @deprecated Use {@link ParallelLogicType#CROSS_RECIPE} mode with CrossRecipeParallelScheduler instead.
     * This method is only kept for backward compatibility with legacy parallel modes.
     */
    @Deprecated
    default RecipeBuilder<?> findMultipliedParallelRecipe(@NotNull RecipeMap<?> recipeMap,
                                                          @NotNull Recipe currentRecipe,
                                                          @NotNull IItemHandlerModifiable inputs,
                                                          @NotNull IMultipleTankHandler fluidInputs,
                                                          @NotNull IItemHandlerModifiable outputs,
                                                          @NotNull IMultipleTankHandler fluidOutputs, int parallelLimit,
                                                          long maxVoltage, @NotNull IVoidable voidable) {
        return ParallelLogic.doParallelRecipes(
                currentRecipe,
                recipeMap,
                inputs,
                fluidInputs,
                outputs,
                fluidOutputs,
                parallelLimit,
                maxVoltage,
                voidable);
    }

    /**
     * @deprecated Use {@link ParallelLogicType#CROSS_RECIPE} mode with CrossRecipeParallelScheduler instead.
     * This method is only kept for backward compatibility with legacy parallel modes.
     */
    @Deprecated
    default RecipeBuilder<?> findAppendedParallelItemRecipe(@NotNull RecipeMap<?> recipeMap,
                                                            @NotNull IItemHandlerModifiable inputs,
                                                            @NotNull IItemHandlerModifiable outputs, int parallelLimit,
                                                            long maxVoltage, @NotNull IVoidable voidable) {
        return ParallelLogic.appendItemRecipes(
                recipeMap,
                inputs,
                outputs,
                parallelLimit,
                maxVoltage,
                voidable);
    }

    /**
     * @deprecated Use {@link ParallelLogicType#CROSS_RECIPE} mode with CrossRecipeParallelScheduler instead.
     * This method is only kept for backward compatibility with legacy parallel modes.
     */
    @Deprecated
    default RecipeBuilder<?> findAppendedParallelFluidRecipe(@NotNull RecipeMap<?> recipeMap,
                                                             @NotNull IMultipleTankHandler fluidInputs,
                                                             @NotNull IMultipleTankHandler fluidOutputs,
                                                             int parallelLimit,
                                                             long maxVoltage, @NotNull IVoidable voidable) {
        return ParallelLogic.appendFluidRecipes(
                recipeMap,
                fluidInputs,
                fluidOutputs,
                parallelLimit,
                maxVoltage,
                voidable);
    }

    /**
     * @deprecated Use {@link ParallelLogicType#CROSS_RECIPE} mode with CrossRecipeParallelScheduler instead.
     * This method is only kept for backward compatibility with legacy parallel modes.
     */
    @Deprecated
    default RecipeBuilder<?> findAppendedParallelRecipe(@NotNull RecipeMap<?> recipeMap,
                                                        @NotNull IItemHandlerModifiable inputs,
                                                        @NotNull IMultipleTankHandler fluidInputs,
                                                        @NotNull IItemHandlerModifiable outputs,
                                                        @NotNull IMultipleTankHandler fluidOutputs, int parallelLimit,
                                                        long maxVoltage, @NotNull IVoidable voidable) {
        return ParallelLogic.appendParallelRecipes(
                recipeMap,
                inputs,
                fluidInputs,
                outputs,
                fluidOutputs,
                parallelLimit,
                maxVoltage,
                voidable);
    }

    // Recipes passed in here should be already trimmed, if desired
    @SuppressWarnings("deprecation")
    default Recipe findParallelRecipe(@NotNull Recipe currentRecipe, @NotNull IItemHandlerModifiable inputs,
                                      @NotNull IMultipleTankHandler fluidInputs,
                                      @NotNull IItemHandlerModifiable outputs,
                                      @NotNull IMultipleTankHandler fluidOutputs, long maxVoltage, int parallelLimit) {
        if (parallelLimit > 1 && getRecipeMap() != null) {
            // CROSS_RECIPE mode bypasses traditional parallel building - it uses the scheduler directly.
            // Return the currentRecipe as-is; the owning RecipeLogic handles slot dispatch separately.
            if (getParallelLogicType() == ParallelLogicType.CROSS_RECIPE) {
                return currentRecipe;
            }

            RecipeBuilder<?> parallelBuilder = switch (getParallelLogicType()) {
                case MULTIPLY -> findMultipliedParallelRecipe(getRecipeMap(), currentRecipe, inputs, fluidInputs,
                        outputs, fluidOutputs, parallelLimit, maxVoltage, getMetaTileEntity());
                case APPEND_ITEMS -> findAppendedParallelItemRecipe(getRecipeMap(), inputs, outputs, parallelLimit,
                        maxVoltage, getMetaTileEntity());
                case APPEND_FLUIDS -> findAppendedParallelFluidRecipe(getRecipeMap(), fluidInputs, fluidOutputs,
                        parallelLimit, maxVoltage, getMetaTileEntity());
                case APPEND_ALL -> findAppendedParallelRecipe(getRecipeMap(), inputs, fluidInputs, outputs, fluidOutputs,
                        parallelLimit, maxVoltage, getMetaTileEntity());
                case CROSS_RECIPE -> null; // Handled above, unreachable
            };

            // if the builder returned is null, no recipe was found.
            if (parallelBuilder == null) {
                invalidateInputs();
                return null;
            } else {
                // if the builder returned does not parallel, its outputs are full
                if (parallelBuilder.getParallel() == 0) {
                    invalidateOutputs();
                    return null;
                } else {
                    setParallelRecipesPerformed(parallelBuilder.getParallel());
                    // apply any parallel bonus
                    applyParallelBonus(parallelBuilder);
                    return parallelBuilder.build().getResult();
                }
            }
        }
        return currentRecipe;
    }

    @NotNull
    MetaTileEntity getMetaTileEntity();

    @Nullable
    RecipeMap<?> getRecipeMap();

    @NotNull
    ParallelLogicType getParallelLogicType();

    /**
     * Set the amount of parallel recipes currently being performed
     *
     * @param amount the amount to set
     */
    void setParallelRecipesPerformed(int amount);

    /**
     * Invalidate the current state of input inventory contents
     */
    void invalidateInputs();

    /**
     * Invalidate the current state of output inventory contents
     */
    void invalidateOutputs();
}
