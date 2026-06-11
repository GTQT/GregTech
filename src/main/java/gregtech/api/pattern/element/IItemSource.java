package gregtech.api.pattern.element;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Item drain used by survival structure construction.
 */
public interface IItemSource {

    @NotNull
    Map<ItemStack, Integer> take(@NotNull Predicate<ItemStack> predicate, boolean simulate, int count);

    @NotNull
    default ItemStack takeOne(@NotNull Predicate<ItemStack> predicate, boolean simulate) {
        Map<ItemStack, Integer> taken = take(predicate, simulate, 1);
        return taken.isEmpty() ? ItemStack.EMPTY : taken.keySet().iterator().next();
    }

    default boolean takeAll(@NotNull Predicate<ItemStack> predicate, boolean simulate, int count) {
        if (count <= 0) {
            return true;
        }
        if (count == 1) {
            return !takeOne(predicate, simulate).isEmpty();
        }
        Map<ItemStack, Integer> available = take(predicate, true, count);
        int total = available.values().stream().mapToInt(Integer::intValue).sum();
        if (total < count) {
            return false;
        }
        if (!simulate) {
            take(predicate, false, count);
        }
        return true;
    }

    default boolean takeOne(@NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot take an empty item stack");
        }
        ItemStack one = stack.copy();
        one.setCount(1);
        return !takeOne(candidate -> itemStacksEqual(one, candidate), simulate).isEmpty();
    }

    default boolean takeAll(@NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot take an empty item stack");
        }
        return takeAll(candidate -> itemStacksEqual(stack, candidate), simulate, stack.getCount());
    }

    @NotNull
    static IItemSource empty() {
        return (predicate, simulate, count) -> Collections.emptyMap();
    }

    @NotNull
    static IItemSource fromPlayer(@NotNull EntityPlayer player) {
        return new IItemSource() {
            @NotNull
            @Override
            public Map<ItemStack, Integer> take(@NotNull Predicate<ItemStack> predicate, boolean simulate, int count) {
                if (count <= 0) {
                    return Collections.emptyMap();
                }
                Map<ItemStack, Integer> result = new LinkedHashMap<>();
                int remaining = count;
                for (int i = 0; i < player.inventory.mainInventory.size() && remaining > 0; i++) {
                    ItemStack slot = player.inventory.mainInventory.get(i);
                    if (slot.isEmpty() || !predicate.test(slot)) {
                        continue;
                    }
                    int taken = Math.min(remaining, slot.getCount());
                    ItemStack key = slot.copy();
                    key.setCount(1);
                    result.merge(key, taken, Integer::sum);
                    if (!simulate) {
                        slot.shrink(taken);
                        if (slot.isEmpty()) {
                            player.inventory.mainInventory.set(i, ItemStack.EMPTY);
                        }
                    }
                    remaining -= taken;
                }
                return result;
            }
        };
    }

    static boolean itemStacksEqual(@NotNull ItemStack expected, @NotNull ItemStack actual) {
        return !expected.isEmpty()
                && !actual.isEmpty()
                && ItemStack.areItemsEqual(expected, actual)
                && ItemStack.areItemStackTagsEqual(expected, actual);
    }
}
