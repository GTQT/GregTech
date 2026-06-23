package gregtech.api.items.itemhandlers;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;

public class GTItemStackHandler extends ItemStackHandler {

    final private MetaTileEntity metaTileEntity;
    private boolean allowSameItemInsert = true;

    public GTItemStackHandler(MetaTileEntity metaTileEntity) {
        super();
        this.metaTileEntity = metaTileEntity;
    }

    public GTItemStackHandler(MetaTileEntity metaTileEntity, int size) {
        super(size);
        this.metaTileEntity = metaTileEntity;
    }

    public GTItemStackHandler(MetaTileEntity metaTileEntity, NonNullList<ItemStack> stacks) {
        super(stacks);
        this.metaTileEntity = metaTileEntity;
    }

    public boolean getAllowSameItemInsert() {
        return allowSameItemInsert;
    }

    public void setAllowSameItemInsert(boolean allowSameItemInsert) {
        this.allowSameItemInsert = allowSameItemInsert;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (ItemStack.areItemStacksEqual(stack, getStackInSlot(slot)))
            return;

        super.setStackInSlot(slot, stack);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (!allowSameItemInsert && !stack.isEmpty()) {
            for (int i = 0; i < getSlots(); i++) {
                if (i != slot && stack.isItemEqual(getStackInSlot(i))) {
                    return stack;
                }
            }
        }
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    public void onContentsChanged(int slot) {
        if (metaTileEntity != null) {
            metaTileEntity.markDirty();
        }
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = super.serializeNBT();
        nbt.setBoolean("AllowSameItemInsert", allowSameItemInsert);
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        if (nbt.hasKey("AllowSameItemInsert")) {
            allowSameItemInsert = nbt.getBoolean("AllowSameItemInsert");
        }
        super.deserializeNBT(nbt);
    }
}
