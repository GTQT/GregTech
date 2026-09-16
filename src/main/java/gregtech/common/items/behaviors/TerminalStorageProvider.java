package gregtech.common.items.behaviors;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Backs the terminal's portable storage app: a plain 6x9 inventory living in the item's NBT, so each
 * terminal carries its own contents and they travel with the item.
 * <p>
 * {@code CombinedCapabilityProvider} persists this through {@link #serializeNBT()}/{@link #deserializeNBT}, so the
 * contents are written out whenever the stack is saved.
 */
public class TerminalStorageProvider implements ICapabilityProvider, INBTSerializable<NBTTagCompound> {

    /** 6 rows of 9, the size of a single chest. */
    public static final int ROW_SIZE = 9;
    public static final int ROWS = 6;
    public static final int SLOT_COUNT = ROW_SIZE * ROWS;

    private final ItemStack stack;
    private ItemStackHandler handler;

    public TerminalStorageProvider(ItemStack stack) {
        this.stack = stack;
    }

    /** Returns the storage of the given terminal stack, or null if the stack has no such capability. */
    @Nullable
    public static ItemStackHandler getHandler(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var handler = stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        return handler instanceof ItemStackHandler itemStackHandler ? itemStackHandler : null;
    }

    @Override
    public boolean hasCapability(@NotNull Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;
    }

    @Nullable
    @Override
    public <T> T getCapability(@NotNull Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability != CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return null;
        return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(getOrCreateHandler());
    }

    private ItemStackHandler getOrCreateHandler() {
        if (handler == null) {
            // Contents are restored by deserializeNBT, so a fresh handler starts empty.
            handler = new ItemStackHandler(SLOT_COUNT);
        }
        return handler;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return getOrCreateHandler().serializeNBT();
    }

    @Override
    public void deserializeNBT(@Nullable NBTTagCompound nbt) {
        if (nbt == null) {
            handler = new ItemStackHandler(SLOT_COUNT);
            return;
        }
        getOrCreateHandler().deserializeNBT(nbt);
    }
}
