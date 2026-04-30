package gregtech.mixins.ae2;

import gregtech.integration.ae2.GTCircuitHelper;
import gregtech.integration.jei.utils.render.ItemStackTextRenderer;
import gregtech.mixins.jei.GuiIngredientAccessor;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.container.implementations.ContainerPatternEncoder;
import appeng.helpers.ItemStackHelper;
import appeng.integration.modules.gregtech.CircuitHelper;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(targets = "appeng.integration.modules.jei.RecipeTransferHandler", remap = false)
public abstract class MixinRecipeTransferHandler {

    @Unique
    private static final Logger gregtech$logger = LogManager.getLogger("GT-MixinRecipeTransferHandler");

    @Unique
    private boolean gregtech$programmableCircuitTransferEnabled;
    @Unique
    private boolean gregtech$currentJeiIngredientNotConsumed;

    @Inject(method = "transferRecipe", at = @At("HEAD"), remap = false)
    private void gregtech$clearTrackedNotConsumedIngredientAtStart(Container container, IRecipeLayout recipeLayout,
                                                                  EntityPlayer player, boolean maxTransfer,
                                                                  boolean doTransfer,
                                                                  CallbackInfoReturnable<IRecipeTransferError> cir) {
        GTCircuitHelper.clearCurrentJeiIngredientNotConsumable();
        gregtech$currentJeiIngredientNotConsumed = false;

        CircuitHelper circuitHelper = CircuitHelper.getInstance();
        gregtech$programmableCircuitTransferEnabled = doTransfer &&
                container instanceof ContainerPatternEncoder &&
                !((ContainerPatternEncoder) container).isCraftingMode() &&
                circuitHelper.hasToolkitInInventory(player) &&
                circuitHelper.isProgrammableCircuitAvailable();

        gregtech$logger.info("[HEAD] doTransfer={}, containerClass={}, isPatternEncoder={}, isCraftingMode={}, " +
                        "hasToolkit={}, pcAvailable={}, enabled={}",
                doTransfer,
                container.getClass().getSimpleName(),
                container instanceof ContainerPatternEncoder,
                container instanceof ContainerPatternEncoder ?
                        ((ContainerPatternEncoder) container).isCraftingMode() : "N/A",
                circuitHelper.hasToolkitInInventory(player),
                circuitHelper.isProgrammableCircuitAvailable(),
                gregtech$programmableCircuitTransferEnabled);
    }

    @Redirect(
            method = "transferRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/api/gui/IGuiIngredient;getAllIngredients()Ljava/util/List;"),
            remap = false)
    private List<ItemStack> gregtech$trackNotConsumedIngredient(IGuiIngredient<ItemStack> ingredient) {
        boolean notConsumed = false;
        if (ingredient instanceof GuiIngredientAccessor) {
            IIngredientRenderer<?> renderer = ((GuiIngredientAccessor<?>) ingredient).getIngredientRenderer();
            notConsumed = renderer instanceof ItemStackTextRenderer
                    && ((ItemStackTextRenderer) renderer).isNotConsumed();
        }

        gregtech$currentJeiIngredientNotConsumed = notConsumed;
        GTCircuitHelper.setCurrentJeiIngredientNotConsumable(notConsumed);

        List<ItemStack> allIngredients = ingredient.getAllIngredients();
        gregtech$logger.info("[getAllIngredients] notConsumed={}, ingredientClass={}, ingredientCount={}",
                notConsumed, ingredient.getClass().getSimpleName(), allIngredients.size());
        return allIngredients;
    }

    @Redirect(
            method = "transferRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/helpers/ItemStackHelper;stackToNBT(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/nbt/NBTTagCompound;",
                    ordinal = 1),
            remap = false)
    private NBTTagCompound gregtech$wrapNotConsumedInputStack(ItemStack stack) {
        gregtech$logger.info("[stackToNBT] enabled={}, notConsumed={}, stack={}, isEmpty={}",
                gregtech$programmableCircuitTransferEnabled,
                gregtech$currentJeiIngredientNotConsumed,
                stack != null ? stack.getDisplayName() : "null",
                stack != null ? stack.isEmpty() : "null");
        if (gregtech$programmableCircuitTransferEnabled &&
                gregtech$currentJeiIngredientNotConsumed &&
                stack != null &&
                !stack.isEmpty()) {
            CircuitHelper circuitHelper = CircuitHelper.getInstance();
            boolean isProgrammable = circuitHelper.isProgrammableCircuit(stack);
            gregtech$logger.info("[stackToNBT] isProgrammable={}", isProgrammable);
            if (!isProgrammable) {
                ItemStack wrappedStack = circuitHelper.wrapItemAsProgrammableStack(stack);
                gregtech$logger.info("[stackToNBT] wrappedStack={}", wrappedStack != null ? wrappedStack.getDisplayName() : "null");
                if (wrappedStack != null && !wrappedStack.isEmpty()) {
                    return ItemStackHelper.stackToNBT(wrappedStack);
                }
            }
        }
        return ItemStackHelper.stackToNBT(stack);
    }

    @Inject(method = "transferRecipe", at = @At("RETURN"), remap = false)
    private void gregtech$clearTrackedNotConsumedIngredientAtReturn(Container container, IRecipeLayout recipeLayout,
                                                                   EntityPlayer player, boolean maxTransfer,
                                                                   boolean doTransfer,
                                                                   CallbackInfoReturnable<IRecipeTransferError> cir) {
        GTCircuitHelper.clearCurrentJeiIngredientNotConsumable();
        gregtech$currentJeiIngredientNotConsumed = false;
        gregtech$programmableCircuitTransferEnabled = false;
    }
}
