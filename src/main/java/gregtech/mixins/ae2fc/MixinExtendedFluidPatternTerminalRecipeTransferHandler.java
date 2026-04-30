package gregtech.mixins.ae2fc;

import gregtech.integration.ae2.GTCircuitHelper;

import net.minecraft.entity.player.EntityPlayer;

import com.glodblock.github.client.container.ContainerExtendedFluidPatternTerminal;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for AE2FC's ExtendedFluidPatternTerminalRecipeTransferHandler to set up
 * the programmable circuit transfer context (player + enabled flag) before
 * RecipeTransferBuilder is constructed, and clean it up afterwards.
 */
@Mixin(targets = "com.glodblock.github.integration.jei.ExtendedFluidPatternTerminalRecipeTransferHandler",
        remap = false)
public abstract class MixinExtendedFluidPatternTerminalRecipeTransferHandler {

    @Inject(method = "transferRecipe", at = @At("HEAD"), remap = false)
    private void gregtech$beginTransfer(ContainerExtendedFluidPatternTerminal container,
                                        IRecipeLayout recipeLayout,
                                        EntityPlayer player, boolean maxTransfer,
                                        boolean doTransfer,
                                        CallbackInfoReturnable<IRecipeTransferError> cir) {
        if (doTransfer) {
            GTCircuitHelper.beginAe2fcTransfer(player);
        }
    }

    @Inject(method = "transferRecipe", at = @At("RETURN"), remap = false)
    private void gregtech$endTransfer(ContainerExtendedFluidPatternTerminal container,
                                      IRecipeLayout recipeLayout,
                                      EntityPlayer player, boolean maxTransfer,
                                      boolean doTransfer,
                                      CallbackInfoReturnable<IRecipeTransferError> cir) {
        GTCircuitHelper.endAe2fcTransfer();
    }
}
