package gregtech.api.items.itemhandlers;

import gregtech.common.covers.filter.ItemFilterContainer;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;

/**
 * 输出物品过滤包装 Handler —— 在 insertItem 时检查过滤器，不匹配的物品将被拒绝。
 * extractItem / getStackInSlot 不受过滤影响，玩家可正常取出所有已放入的物品。
 */
public class FilteredExportItemHandler implements IItemHandlerModifiable {

    private final IItemHandlerModifiable delegate;
    private final ItemFilterContainer filter;

    public FilteredExportItemHandler(IItemHandlerModifiable delegate, ItemFilterContainer filter) {
        this.delegate = delegate;
        this.filter = filter;
    }

    @Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(slot);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (!filter.test(stack)) return stack;
        return delegate.insertItem(slot, stack, simulate);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        return delegate.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return delegate.getSlotLimit(slot);
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        delegate.setStackInSlot(slot, stack);
    }
}
