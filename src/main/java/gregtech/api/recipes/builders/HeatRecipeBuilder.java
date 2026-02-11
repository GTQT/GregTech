package gregtech.api.recipes.builders;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.properties.impl.HeatProperty;
import gregtech.api.recipes.properties.impl.TemperatureProperty;
import gregtech.api.util.ValidationResult;

public class HeatRecipeBuilder extends RecipeBuilder<HeatRecipeBuilder> {

    public HeatRecipeBuilder() {
    }

    public HeatRecipeBuilder(Recipe recipe, RecipeMap<HeatRecipeBuilder> recipeMap) {
        super(recipe, recipeMap);
    }

    public HeatRecipeBuilder(RecipeBuilder<HeatRecipeBuilder> recipeBuilder) {
        super(recipeBuilder);
    }

    public HeatRecipeBuilder copy() {
        return new HeatRecipeBuilder(this);
    }

    @Override
    public boolean applyPropertyCT(String key, Object value) {
        if (key.equals(HeatProperty.KEY)) {
            this.Heat(((Number) value).intValue());
            return true;
        }
        if (key.equals(TemperatureProperty.KEY)) {
            this.Temperature(((Number) value).intValue());
            return true;
        }
        return super.applyPropertyCT(key, value);
    }

    public HeatRecipeBuilder Heat(int Heat) {
        this.applyProperty(HeatProperty.getInstance(), Math.max(Heat, 0));
        return this;
    }

    public HeatRecipeBuilder Temperature(int Temperature) {
        this.applyProperty(TemperatureProperty.getInstance(), Math.max(Temperature, 473));
        return this;
    }

    @Override
    public ValidationResult<Recipe> build() {
        this.EUt(1);
        this.applyProperty(HeatProperty.getInstance(), true);
        this.applyProperty(TemperatureProperty.getInstance(), true);
        return super.build();
    }
}
