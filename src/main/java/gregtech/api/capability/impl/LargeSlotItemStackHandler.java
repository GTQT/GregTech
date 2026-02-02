package gregtech.api.capability.impl;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.items.ItemHandlerHelper;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class LargeSlotItemStackHandler extends NotifiableItemStackHandler {

    Supplier<Integer> slotCapacity;
    private static final String ITEM_LIST_TAG_KEY = "Items";
    private static final String ITEM_COUNT_TAG_KEY = "Count";
    private static final String BIG_STACK_SIZE_TAG_KEY = "BigStackSize";
    private static final Byte FAKE_STACK_SIZE = new Byte("1");

    public LargeSlotItemStackHandler(MetaTileEntity metaTileEntity, int slots, MetaTileEntity entityToNotify,
                                     boolean isExport) {
        this(metaTileEntity, slots, entityToNotify, isExport, () -> Integer.MAX_VALUE);
    }

    public LargeSlotItemStackHandler(MetaTileEntity metaTileEntity, int slots, MetaTileEntity entityToNotify,
                                     boolean isExport, Supplier<Integer> slotCapacity) {
        super(metaTileEntity, slots, entityToNotify, isExport);

        this.slotCapacity = slotCapacity;
    }

    @Override
    public int getSlotLimit(int slot) {
        return slotCapacity.get();
    }

    @Override
    protected int getStackLimit(int slot, ItemStack stack) {
        return getSlotLimit(slot);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount == 0) return ItemStack.EMPTY;

        validateSlotIndex(slot);

        ItemStack existing = this.stacks.get(slot);

        if (existing.isEmpty()) return ItemStack.EMPTY;

        if (existing.getCount() <= amount) {
            if (!simulate) {
                this.stacks.set(slot, ItemStack.EMPTY);
                onContentsChanged(slot);
            }

            return existing;
        } else {
            if (!simulate) {
                this.stacks.set(slot, ItemHandlerHelper.copyStackWithSize(
                        existing, existing.getCount() - amount));
                onContentsChanged(slot);
            }

            return ItemHandlerHelper.copyStackWithSize(existing, amount);
        }
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound tagCompound = super.serializeNBT();

        if (stacks.stream().anyMatch(x -> x.getCount() > Byte.MAX_VALUE)) {
            NBTTagCompound stackSizes = new NBTTagCompound();
            NBTTagList items = tagCompound.getTagList(ITEM_LIST_TAG_KEY, 10);

            // save big stack size data
            for (int i = 0; i < stacks.size(); i++) {
                ItemStack itemStack = stacks.get(i);

                if (itemStack != ItemStack.EMPTY && itemStack.getCount() > Byte.MAX_VALUE) {
                    stackSizes.setInteger(String.valueOf(i), itemStack.getCount());
                }
            }
            tagCompound.setTag(BIG_STACK_SIZE_TAG_KEY, stackSizes);

            // fix size overflow of existing item tags
            for (NBTBase itemBase : items) {
                NBTTagCompound item = (NBTTagCompound) itemBase;

                byte size = item.getByte(ITEM_COUNT_TAG_KEY);
                if (size < 0)
                    item.setByte(ITEM_COUNT_TAG_KEY, FAKE_STACK_SIZE);
            }
        }

        return tagCompound;
    }

    @Override
    public void deserializeNBT(NBTTagCompound tagCompound) {
        super.deserializeNBT(tagCompound);

        if (tagCompound.hasKey(BIG_STACK_SIZE_TAG_KEY)) {
            NBTTagCompound stackSizes = tagCompound.getCompoundTag(BIG_STACK_SIZE_TAG_KEY);

            for (String tagKey : stackSizes.getKeySet()) {
                int size = stackSizes.getInteger(tagKey);
                int slot = Integer.parseInt(tagKey);
                stacks.get(slot).setCount(size);
            }
        }
    }
}
