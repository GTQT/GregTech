package gregtech.integration.ae2;

import gregtech.api.recipes.ingredients.IntCircuitIngredient;

import gregtech.common.items.MetaItems;
import gregtech.common.items.behaviors.ProgrammableCircuit;

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

    // ThreadLocal for AE2FC recipe transfer programmable circuit injection
    private static final ThreadLocal<EntityPlayer> AE2FC_TRANSFER_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> AE2FC_TRANSFER_ENABLED = ThreadLocal.withInitial(() -> false);

    public static void setCurrentJeiIngredientNotConsumable(boolean notConsumable) {
        CURRENT_JEI_INGREDIENT_NOT_CONSUMABLE.set(notConsumable);
    }

    public static void clearCurrentJeiIngredientNotConsumable() {
        CURRENT_JEI_INGREDIENT_NOT_CONSUMABLE.remove();
    }

    /**
     * Begin an AE2FC recipe transfer session. Called from Mixin before
     * RecipeTransferBuilder is constructed.
     */
    public static void beginAe2fcTransfer(EntityPlayer player) {
        CircuitHelper circuitHelper = CircuitHelper.getInstance();
        boolean enabled = circuitHelper.hasToolkitInInventory(player) &&
                circuitHelper.isProgrammableCircuitAvailable();
        AE2FC_TRANSFER_ENABLED.set(enabled);
        AE2FC_TRANSFER_PLAYER.set(player);
    }

    /**
     * End an AE2FC recipe transfer session. Called from Mixin after
     * the transfer packet has been sent.
     */
    public static void endAe2fcTransfer() {
        AE2FC_TRANSFER_ENABLED.remove();
        AE2FC_TRANSFER_PLAYER.remove();
    }

    /**
     * Check if AE2FC recipe transfer programmable circuit injection is enabled.
     */
    public static boolean isAe2fcTransferEnabled() {
        return Boolean.TRUE.equals(AE2FC_TRANSFER_ENABLED.get());
    }

    @Override
    public boolean isProgrammableCircuit(ItemStack stack) {
        return MetaItems.PROGRAMMABLE_CIRCUIT != null
                && stack != null
                && !stack.isEmpty()
                && MetaItems.PROGRAMMABLE_CIRCUIT.isItemEqual(stack);
    }

    @Override
    public boolean isIntegratedCircuit(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        return IntCircuitIngredient.isIntegratedCircuit(stack)
                || Boolean.TRUE.equals(CURRENT_JEI_INGREDIENT_NOT_CONSUMABLE.get())
                        && !stack.isEmpty()
                        && !isProgrammableCircuit(stack);
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
            return null;
        }

        if (MetaItems.PROGRAMMABLE_CIRCUIT == null) {
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

        final ItemStack programmable = MetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(1);
        ProgrammableCircuit.wrap(wrappedItem, programmable);
        return programmable;
    }

    @Override
    public boolean hasToolkitInInventory(@Nullable EntityPlayer player) {
        if (player == null || MetaItems.PROGRAMMING_TOOLKIT == null) {
            return false;
        }

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            final ItemStack invStack = player.inventory.getStackInSlot(i);
            if (!invStack.isEmpty() && MetaItems.PROGRAMMING_TOOLKIT.isItemEqual(invStack)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @Override
    public ItemStack getProgrammableCircuitStack() {
        if (MetaItems.PROGRAMMABLE_CIRCUIT == null) {
            return null;
        }
        return MetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(1);
    }

    @Override
    public boolean isProgrammableCircuitAvailable() {
        return MetaItems.PROGRAMMABLE_CIRCUIT != null;
    }

    @Nullable
    private static IAEStack<?> toAEStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return AEItemStack.fromItemStack(stack);
    }
}
