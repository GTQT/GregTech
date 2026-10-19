package gregtech.mixins.jei;

import gregtech.integration.jei.multiblock.CompactPreviewLayout;
import gregtech.integration.jei.multiblock.MultiblockInfoRecipeWrapper;

import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.gui.recipes.RecipeLayout;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Include external multiblock panels in JEI's focus, tooltip and mouse-event routing. */
@Mixin(value = RecipeLayout.class, remap = false)
public class MultiblockRecipeLayoutMixin {
    @Shadow @Final private IRecipeWrapper recipeWrapper;
    @Shadow private int posX;
    @Shadow private int posY;

    @Inject(method = "isMouseOver", at = @At("HEAD"), cancellable = true)
    private void gregtech$includePreviewPanels(int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (recipeWrapper instanceof MultiblockInfoRecipeWrapper wrapper &&
                CompactPreviewLayout.containsExtensions(mouseX - posX, mouseY - posY,
                        wrapper.getCompactPartCount(), wrapper.getCompactControlsHeight())) {
            cir.setReturnValue(true);
        }
    }
}
