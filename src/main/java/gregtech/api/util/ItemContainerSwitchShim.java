package gregtech.api.util;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;


public class ItemContainerSwitchShim  implements IItemHandlerModifiable {

    IItemHandlerModifiable container;
    public ItemContainerSwitchShim(IItemHandlerModifiable container) {
        changeInventory(container);
    }

    public void changeInventory(IItemHandlerModifiable container) {
        this.container = container;
    }

    @Override
    public int getSlots() {
        return this.container.getSlots();
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.container.getStackInSlot(slot);
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack itemStack, boolean simulate) {
        return this.container.insertItem(slot, itemStack, simulate);
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return this.container.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return this.container.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return IItemHandlerModifiable.super.isItemValid(slot, stack);
    }

    @Override
    public void setStackInSlot(int i, @NotNull ItemStack itemStack) {
        this.container.setStackInSlot(i, itemStack);
    }
}
