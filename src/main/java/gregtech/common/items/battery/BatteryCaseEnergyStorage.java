package gregtech.common.items.battery;

import gregtech.api.GTValues;
import gregtech.api.capability.IElectricItem;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * The energy view of a battery case, exposing the four slots as a single {@link IElectricItem}:
 * <ul>
 * <li>charge and capacity are the plain sum over the slots,</li>
 * <li>the tier is the one of the batteries inside (an empty case is treated as ULV with no energy),</li>
 * <li>charging and discharging are forwarded slot by slot, taking from slot 1 to 4 in order.</li>
 * </ul>
 * <p>
 * Nothing is cached here: the aggregation is done on every call. Charging and discharging modify the batteries in
 * place, which does not notify the inventory, so the contents are persisted explicitly afterwards.
 */
public class BatteryCaseEnergyStorage implements IElectricItem {

    private final BatteryCaseInventory inventory;

    public BatteryCaseEnergyStorage(BatteryCaseInventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public boolean canProvideChargeExternally() {
        return true;
    }

    @Override
    public boolean chargeable() {
        return true;
    }

    @Override
    public void addChargeListener(@NotNull BiConsumer<ItemStack, Long> chargeListener) {
        // The charge is aggregated on demand, so there is nothing to listen to.
    }

    @Override
    public long charge(long amount, int chargerTier, boolean ignoreTransferLimit, boolean simulate) {
        if (amount <= 0L) {
            return 0L;
        }
        int tier = inventory.getTier();
        if (tier >= 0 && chargerTier < tier) {
            return 0L;
        }
        long remaining = amount;
        long charged = 0L;
        for (int i = 0; i < inventory.getSlots() && remaining > 0L; i++) {
            IElectricItem battery = BatteryCaseInventory.getBattery(inventory.getStackInSlot(i));
            if (battery == null) {
                continue;
            }
            long accepted = battery.charge(remaining, chargerTier, ignoreTransferLimit, simulate);
            charged += accepted;
            remaining -= accepted;
        }
        if (!simulate && charged > 0L) {
            inventory.save();
        }
        return charged;
    }

    @Override
    public long discharge(long amount, int dischargerTier, boolean ignoreTransferLimit, boolean externally,
                          boolean simulate) {
        if (amount <= 0L) {
            return 0L;
        }
        int tier = inventory.getTier();
        if (tier >= 0 && dischargerTier < tier) {
            return 0L;
        }
        long remaining = amount;
        long discharged = 0L;
        // Drain from slot 1 to 4 so that the same battery is not switched between all the time.
        for (int i = 0; i < inventory.getSlots() && remaining > 0L; i++) {
            IElectricItem battery = BatteryCaseInventory.getBattery(inventory.getStackInSlot(i));
            if (battery == null) {
                continue;
            }
            long taken = battery.discharge(remaining, dischargerTier, ignoreTransferLimit, externally, simulate);
            discharged += taken;
            remaining -= taken;
        }
        if (!simulate && discharged > 0L) {
            inventory.save();
        }
        return discharged;
    }

    @Override
    public long getTransferLimit() {
        return GTValues.V[getTier()];
    }

    @Override
    public long getMaxCharge() {
        return inventory.getTotalMaxCharge();
    }

    @Override
    public long getCharge() {
        return inventory.getTotalCharge();
    }

    @Override
    public int getTier() {
        int tier = inventory.getTier();
        return tier < 0 ? GTValues.ULV : tier;
    }
}
