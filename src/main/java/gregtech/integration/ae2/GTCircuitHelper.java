package gregtech.integration.ae2;

import gregtech.api.recipes.ingredients.IntCircuitIngredient;
import gregtech.api.util.GTLog;

import gtqt.common.items.GTQTMetaItems;
import gtqt.common.items.behaviors.ProgrammableCircuit;

import org.jetbrains.annotations.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEStack;
import appeng.integration.modules.gregtech.CircuitHelper;
import appeng.util.item.AEItemStack;

/**
 * GT 侧的 CircuitHelper 实现，提供可编程电路相关的实际逻辑。
 * 在 GT preInit 阶段通过 CircuitHelper.setInstance() 注册。
 */
public class GTCircuitHelper extends CircuitHelper {

    private static final ThreadLocal<Boolean> CURRENT_JEI_INGREDIENT_NOT_CONSUMABLE =
            ThreadLocal.withInitial(() -> false);

    public static void setCurrentJeiIngredientNotConsumable(boolean notConsumable) {
        CURRENT_JEI_INGREDIENT_NOT_CONSUMABLE.set(notConsumable);
    }

    public static void clearCurrentJeiIngredientNotConsumable() {
        CURRENT_JEI_INGREDIENT_NOT_CONSUMABLE.remove();
    }

    @Override
    public boolean isProgrammableCircuit(ItemStack stack) {
        return GTQTMetaItems.PROGRAMMABLE_CIRCUIT != null
                && stack != null
                && !stack.isEmpty()
                && GTQTMetaItems.PROGRAMMABLE_CIRCUIT.isItemEqual(stack);
    }

    @Override
    public boolean isIntegratedCircuit(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        boolean isCircuit = IntCircuitIngredient.isIntegratedCircuit(stack);
        boolean notConsumable = Boolean.TRUE.equals(CURRENT_JEI_INGREDIENT_NOT_CONSUMABLE.get());
        boolean result = isCircuit
                || notConsumable && !stack.isEmpty() && !isProgrammableCircuit(stack);
        GTLog.logger.info("[GTCircuitHelper] isIntegratedCircuit: stack={}, isCircuit={}, notConsumable={}, result={}",
                stack.getDisplayName(), isCircuit, notConsumable, result);
        return result;
    }

    @Nullable
    @Override
    public IAEStack<?> wrapItemAsProgrammable(ItemStack sourceItem) {
        ItemStack wrapped = wrapItemAsProgrammableStack(sourceItem);
        return wrapped == null ? null : toAEStack(wrapped);
    }

    @Nullable
    @Override
    public ItemStack wrapItemAsProgrammableStack(ItemStack sourceItem) {
        if (sourceItem.isEmpty()) {
            GTLog.logger.info("[GTCircuitHelper] wrapItemAsProgrammableStack: sourceItem is empty, returning null");
            return null;
        }

        if (GTQTMetaItems.PROGRAMMABLE_CIRCUIT == null) {
            GTLog.logger.info("[GTCircuitHelper] wrapItemAsProgrammableStack: PROGRAMMABLE_CIRCUIT is null, returning copy");
            return sourceItem.copy();
        }

        final ItemStack wrappedItem;
        if (IntCircuitIngredient.isIntegratedCircuit(sourceItem)) {
            final int config = IntCircuitIngredient.getCircuitConfiguration(sourceItem);
            wrappedItem = IntCircuitIngredient.getIntegratedCircuit(config);
        } else {
            wrappedItem = sourceItem.copy();
            wrappedItem.setCount(1);
        }

        final ItemStack programmable = GTQTMetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(1);
        ProgrammableCircuit.wrap(wrappedItem, programmable);
        GTLog.logger.info("[GTCircuitHelper] wrapItemAsProgrammableStack: source={}, wrapped={}, hasNBT={}",
                sourceItem.getDisplayName(), programmable.getDisplayName(),
                programmable.hasTagCompound() ? programmable.getTagCompound() : "null");
        return programmable;
    }

    @Override
    public boolean hasToolkitInInventory(@Nullable EntityPlayer player) {
        if (player == null || GTQTMetaItems.PROGRAMMING_TOOLKIT == null) {
            return false;
        }

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            final ItemStack invStack = player.inventory.getStackInSlot(i);
            if (!invStack.isEmpty() && GTQTMetaItems.PROGRAMMING_TOOLKIT.isItemEqual(invStack)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @Override
    public ItemStack getProgrammableCircuitStack() {
        if (GTQTMetaItems.PROGRAMMABLE_CIRCUIT == null) {
            return null;
        }
        return GTQTMetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(1);
    }

    @Override
    public boolean isProgrammableCircuitAvailable() {
        return GTQTMetaItems.PROGRAMMABLE_CIRCUIT != null;
    }

    @Nullable
    private static IAEStack<?> toAEStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return AEItemStack.fromItemStack(stack);
    }
}
