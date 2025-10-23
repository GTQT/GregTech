package gregtech.api.capability;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class InaccessibleInfiniteSlot implements IItemHandlerModifiable, INotifiableHandler {

    private final IItemList<IAEItemStack> internalBuffer;
    private final List<MetaTileEntity> notifiableEntities = new ArrayList<>();
    private final MetaTileEntity holder;

    public InaccessibleInfiniteSlot(MetaTileEntity holder, IItemList<IAEItemStack> internalBuffer,
                                    MetaTileEntity mte) {
        this.holder = holder;
        this.internalBuffer = internalBuffer;
        this.notifiableEntities.add(mte);
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        this.internalBuffer.add(AEItemStack.fromItemStack(stack));
        this.holder.markDirty();
        this.trigger();
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            this.internalBuffer.add(AEItemStack.fromItemStack(stack));
            this.holder.markDirty();
        }
        this.trigger();
        return ItemStack.EMPTY;
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE - 1;
    }

    @Override
    public void addNotifiableMetaTileEntity(MetaTileEntity metaTileEntity) {
        this.notifiableEntities.add(metaTileEntity);
    }

    @Override
    public void removeNotifiableMetaTileEntity(MetaTileEntity metaTileEntity) {
        this.notifiableEntities.remove(metaTileEntity);
    }

    private void trigger() {
        for (MetaTileEntity metaTileEntity : this.notifiableEntities) {
            if (metaTileEntity != null && metaTileEntity.isValid()) {
                this.addToNotifiedList(metaTileEntity, this, true);
            }
        }
    }
}
