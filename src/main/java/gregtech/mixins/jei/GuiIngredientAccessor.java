package gregtech.mixins.jei;

import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.gui.ingredients.GuiIngredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiIngredient.class, remap = false)
public interface GuiIngredientAccessor<T> {

    @Accessor("ingredientRenderer")
    IIngredientRenderer<T> getIngredientRenderer();
}
