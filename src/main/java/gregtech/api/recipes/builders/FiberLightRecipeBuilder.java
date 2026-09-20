package gregtech.api.recipes.builders;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.common.pipelike.fiber.FiberColor;
import gregtech.common.pipelike.fiber.recipe.FiberLightProperty;
import gregtech.common.pipelike.fiber.recipe.FiberLightProperty.FiberLightRequirement;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Recipe builder for recipes driven by a fiber optic light signal.
 * <p>
 * The optical requirement lives here rather than on the base {@link RecipeBuilder}, because only
 * recipe maps that actually read light should be able to declare it.
 *
 * @see FiberLightProperty
 */
public class FiberLightRecipeBuilder extends RecipeBuilder<FiberLightRecipeBuilder> {

    public FiberLightRecipeBuilder() {}

    public FiberLightRecipeBuilder(Recipe recipe, RecipeMap<FiberLightRecipeBuilder> recipeMap) {
        super(recipe, recipeMap);
    }

    public FiberLightRecipeBuilder(RecipeBuilder<FiberLightRecipeBuilder> recipeBuilder) {
        super(recipeBuilder);
    }

    @Override
    public FiberLightRecipeBuilder copy() {
        return new FiberLightRecipeBuilder(this);
    }

    @Override
    public boolean applyPropertyCT(@NotNull String key, @NotNull Object value) {
        if (key.equals(FiberLightProperty.KEY) && value instanceof FiberLightRequirement requirement) {
            this.fiberLight(requirement);
            return true;
        }
        return super.applyPropertyCT(key, value);
    }

    /** Sets the full optical requirement: colour plus intensity range. */
    public FiberLightRecipeBuilder fiberLight(@NotNull FiberLightRequirement requirement) {
        this.applyProperty(FiberLightProperty.getInstance(), requirement);
        return this;
    }

    /** Requires a specific colour, with no intensity constraint. */
    public FiberLightRecipeBuilder lightColor(@NotNull FiberColor color) {
        return fiberLight(new FiberLightRequirement().color(color));
    }

    /** Requires at least {@code min} flux, in any colour. */
    public FiberLightRecipeBuilder lightAtLeast(long min) {
        return fiberLight(new FiberLightRequirement().atLeast(min));
    }

    /** Requires strictly more than {@code min} flux, in any colour. */
    public FiberLightRecipeBuilder lightAbove(long min) {
        return fiberLight(new FiberLightRequirement().above(min));
    }

    /** Requires at most {@code max} flux, in any colour. */
    public FiberLightRecipeBuilder lightAtMost(long max) {
        return fiberLight(new FiberLightRequirement().atMost(max));
    }

    /** Requires strictly less than {@code max} flux, in any colour. */
    public FiberLightRecipeBuilder lightBelow(long max) {
        return fiberLight(new FiberLightRequirement().below(max));
    }

    /** Requires the flux to fall inside {@code [min, max]}. */
    public FiberLightRecipeBuilder lightBetween(long min, long max) {
        return fiberLight(new FiberLightRequirement().between(min, max));
    }

    /** The optical requirement of this recipe, or {@code null} when it has none. */
    @Nullable
    public FiberLightRequirement getFiberLight() {
        return this.recipePropertyStorage.get(FiberLightProperty.getInstance(), null);
    }
}
