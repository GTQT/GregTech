package gregtech.mixins.ae2fc;

import gregtech.integration.ae2.GTCircuitHelper;
import gregtech.integration.jei.utils.render.ItemStackTextRenderer;
import gregtech.mixins.jei.GuiIngredientAccessor;

import net.minecraft.item.ItemStack;

import appeng.integration.modules.gregtech.CircuitHelper;
import com.glodblock.github.integration.jei.RecipeTransferBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredientRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Mixin for AE2FC's RecipeTransferBuilder to inject programmable circuits for
 * GT notConsumed inputs that are normally skipped by split().
 * After build() completes, this mixin scans the recipe layout for skipped
 * notConsumed ingredients and appends them as programmable circuit entries
 * into the input map.
 */
@Mixin(value = RecipeTransferBuilder.class, remap = false)
public abstract class MixinRecipeTransferBuilder {

    @Shadow
    @Final
    private IRecipeLayout recipe;

    @Shadow
    @Final
    private Int2ObjectArrayMap<ItemStack[]> in;

    @Inject(method = "build", at = @At("RETURN"), remap = false)
    private void gregtech$injectProgrammableCircuits(CallbackInfoReturnable<RecipeTransferBuilder> cir) {
        if (!GTCircuitHelper.isAe2fcTransferEnabled()) {
            return;
        }

        CircuitHelper circuitHelper = CircuitHelper.getInstance();
        Map<Integer, ? extends IGuiIngredient<ItemStack>> ingredients =
                this.recipe.getItemStacks().getGuiIngredients();

        // Find the next available slot index after existing entries
        int nextSlotIndex = 0;
        for (int key : this.in.keySet()) {
            if (key >= nextSlotIndex) {
                nextSlotIndex = key + 1;
            }
        }

        boolean wrappedCircuitAdded = false;
        boolean hasProgrammableCircuitInput = false;

        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> entry : ingredients.entrySet()) {
            IGuiIngredient<ItemStack> ingredient = entry.getValue();
            if (!ingredient.isInput()) {
                continue;
            }

            ItemStack displayedItem = ingredient.getDisplayedIngredient();
            if (displayedItem == null || displayedItem.isEmpty()) {
                continue;
            }

            // Check if any input already is a programmable circuit
            if (circuitHelper.isProgrammableCircuit(displayedItem)) {
                hasProgrammableCircuitInput = true;
                continue;
            }

            // Check if this ingredient is notConsumed via renderer
            boolean notConsumed = false;
            if (ingredient instanceof GuiIngredientAccessor) {
                IIngredientRenderer<?> renderer =
                        ((GuiIngredientAccessor<?>) ingredient).getIngredientRenderer();
                notConsumed = renderer instanceof ItemStackTextRenderer
                        && ((ItemStackTextRenderer) renderer).isNotConsumed();
            }

            if (!notConsumed) {
                continue;
            }

            // This is a notConsumed ingredient that was skipped by split(),
            // wrap it as a programmable circuit
            ItemStack wrappedStack = circuitHelper.wrapItemAsProgrammableStack(displayedItem);
            if (wrappedStack != null && !wrappedStack.isEmpty()) {
                this.in.put(nextSlotIndex, new ItemStack[]{ wrappedStack });
                nextSlotIndex++;
                wrappedCircuitAdded = true;
                hasProgrammableCircuitInput = true;
            }
        }

        // If no circuit was wrapped and no programmable circuit was already present,
        // add an empty programmable circuit card as a placeholder
        if (!wrappedCircuitAdded && !hasProgrammableCircuitInput) {
            ItemStack pcStack = circuitHelper.getProgrammableCircuitStack();
            if (pcStack != null && !pcStack.isEmpty()) {
                this.in.put(nextSlotIndex, new ItemStack[]{ pcStack });
            }
        }
    }
}
