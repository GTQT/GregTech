package gregtech.api.pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;

/**
 * Per-cell candidate overrides.
 *
 * <p>A cell may accept alternative blocks; those are usually hatch or casing slots, and the items involved are
 * meta items whose identity is not fully described by {@code itemId + damage} (tier, assembled NBT, ...). The
 * override is therefore a serialized {@link ItemStack} carried next to the channel map, not a value squeezed into
 * an int channel.
 *
 * <p>Both {@link MultiPiecePreviewAssembler} and {@link PieceRuntimeState} read the same map, so preview and build
 * always agree on which cell was replaced.
 */
public final class StructureCellOverrides {

    /** Guards the packet and the preview rebuild against absurd override counts. */
    public static final int MAX_OVERRIDES = 4096;
    /** Overrides are controller-relative; anything beyond this cannot belong to a structure. */
    public static final int MAX_OFFSET = 512;

    private StructureCellOverrides() {}

    @Nullable
    public static ItemStack find(@Nullable Map<BlockPos, ItemStack> overrides, @NotNull BlockPos pos) {
        if (overrides == null || overrides.isEmpty()) {
            return null;
        }
        ItemStack stack = overrides.get(pos);
        return isUsable(stack) ? stack : null;
    }

    public static boolean isUsable(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() > 0 && stack.getItem() != null;
    }

    /**
     * Copies the stack with count {@code 1}; the structure build always places a single block per cell.
     */
    @NotNull
    public static ItemStack normalize(@NotNull ItemStack stack) {
        if (!isUsable(stack)) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    /**
     * Whether {@code candidate} is the block described by {@code override}. Item identity includes damage and NBT so
     * tiered hatch items resolve to the exact candidate the player clicked.
     */
    public static boolean matches(@Nullable ItemStack candidate, @NotNull ItemStack override) {
        if (!isUsable(candidate) || !isUsable(override)) {
            return false;
        }
        if (candidate.getItem() != override.getItem()) {
            return false;
        }
        if (candidate.getMetadata() != override.getMetadata()) {
            return false;
        }
        return ItemStack.areItemStackTagsEqual(candidate, override);
    }

    /**
     * Drops out-of-range, empty and surplus entries in place. Packet handlers run this on untrusted input.
     */
    public static void sanitize(@NotNull Map<BlockPos, ItemStack> overrides) {
        Iterator<Map.Entry<BlockPos, ItemStack>> iterator = overrides.entrySet().iterator();
        int kept = 0;
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, ItemStack> entry = iterator.next();
            BlockPos pos = entry.getKey();
            if (pos == null || !isUsable(entry.getValue()) ||
                    Math.abs(pos.getX()) > MAX_OFFSET ||
                    Math.abs(pos.getY()) > MAX_OFFSET ||
                    Math.abs(pos.getZ()) > MAX_OFFSET ||
                    kept++ >= MAX_OVERRIDES) {
                iterator.remove();
                continue;
            }
            ItemStack stack = entry.getValue().copy();
            stack.setCount(1);
            entry.setValue(stack);
        }
    }
}
