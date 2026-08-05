package gregtech.api.items.armor;

import net.minecraft.item.ItemStack;

/**
 * Interface for chest armor pieces that store oxygen for breathing masks.
 * Any chest armor implementing this can supply oxygen to any breathing mask.
 */
public interface IOxygenTank {

    /**
     * @return current oxygen stored, in ticks
     */
    double getOxygen(ItemStack stack);

    /**
     * @return maximum oxygen capacity, in ticks
     */
    double getMaxOxygen(ItemStack stack);

    /**
     * Change the stored oxygen by the given amount (negative to consume).
     */
    void changeOxygen(ItemStack stack, double delta);
}
