package gregtech.common.items.battery;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The four battery slots of a battery case.
 * <p>
 * The contents are stored in the plain NBT tag of the owning battery case stack (key {@link #NBT_KEY}) instead of in
 * Forge's capability data, because {@code Item#getNBTShareTag} of 1.12.2 only returns {@code stack.getTagCompound()}
 * and {@link gregtech.api.items.metaitem.MetaItem} does not override it: anything living in {@code ForgeCaps} is lost
 * whenever a stack travels over the share tag path, e.g. after toggling the discharge mode. With the contents in the
 * plain tag they are carried by every path (share tag, {@code ItemStack#writeToNBT} and {@code ItemStack#copy}).
 * <p>
 * Note that the batteries themselves are charged and discharged in place, which does not call
 * {@link #onContentsChanged(int)}, so {@link BatteryCaseEnergyStorage} persists the contents explicitly.
 */
public class BatteryCaseInventory extends ItemStackHandler {

    public static final int SIZE = 4;

    /** NBT key holding the contents inside the battery case stack. */
    public static final String NBT_KEY = "BatteryCaseInv";

    /** The battery case stack the contents belong to; the NBT is always read through it, never cached. */
    private final ItemStack owner;

    public BatteryCaseInventory(ItemStack owner) {
        super(SIZE);
        this.owner = owner;
        load();
    }

    private void load() {
        NBTTagCompound tag = owner.getTagCompound();
        if (tag != null && tag.hasKey(NBT_KEY, 10)) {
            deserializeNBT(tag.getCompoundTag(NBT_KEY));
        }
    }

    /**
     * Writes the contents back into the battery case stack. The tag is looked up again on every call so that replacing
     * the whole {@link NBTTagCompound} instance from the outside cannot lose the contents.
     */
    public void save() {
        NBTTagCompound tag = owner.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            owner.setTagCompound(tag);
        }
        tag.setTag(NBT_KEY, serializeNBT());
    }

    @Override
    protected void onContentsChanged(int slot) {
        save();
    }

    /** @return the voltage tier of the batteries inside, or {@code -1} when the case is empty. */
    public int getTier() {
        for (int i = 0; i < getSlots(); i++) {
            IElectricItem item = getBattery(getStackInSlot(i));
            if (item != null) {
                return item.getTier();
            }
        }
        return -1;
    }

    /** @return the summed charge of the four slots. */
    public long getTotalCharge() {
        long total = 0L;
        for (int i = 0; i < getSlots(); i++) {
            IElectricItem item = getBattery(getStackInSlot(i));
            if (item != null) {
                total += item.getCharge();
            }
        }
        return total;
    }

    /** @return the summed capacity of the four slots. */
    public long getTotalMaxCharge() {
        long total = 0L;
        for (int i = 0; i < getSlots(); i++) {
            IElectricItem item = getBattery(getStackInSlot(i));
            if (item != null) {
                total += item.getMaxCharge();
            }
        }
        return total;
    }

    /** @return the number of occupied slots. */
    public int getBatteryCount() {
        int count = 0;
        for (int i = 0; i < getSlots(); i++) {
            if (!getStackInSlot(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * @return the battery capability of the given stack, requiring it to be able to provide its charge externally so
     *         that chargeable tools are not mistaken for batteries
     */
    @Nullable
    public static IElectricItem getBattery(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        IElectricItem item = stack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (item == null || !item.canProvideChargeExternally()) {
            return null;
        }
        return item;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        // Nesting a battery case inside another one would be self referencing nonsense, so refuse it.
        if (stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null) instanceof BatteryCaseInventory) {
            return false;
        }
        IElectricItem candidate = stack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (candidate == null || !candidate.canProvideChargeExternally()) {
            return false;
        }
        int tier = getTier();
        return tier < 0 || tier == candidate.getTier();
    }
}
