package gregtech.api.pattern.casing;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single casing type used in multiblock structures.
 * A casing is a specific block state that can appear in a structure definition,
 * optionally with a tier for tiered casing groups (e.g. coils, casings of different material tiers).
 *
 * @see ICasingGroup for groups of casings that share a role (e.g. all heating coils)
 */
public interface ICasing {

    /**
     * @return the block state representing this casing
     */
    IBlockState getBlockState();

    /**
     * @return the unlocalized name for display in tooltips
     */
    String getTranslationKey();

    /**
     * @return true if this casing has a tier level (e.g. coils, multi-tier casings)
     */
    boolean isTiered();

    /**
     * @return the tier level of this casing (only meaningful if {@link #isTiered()} is true)
     */
    int getTier();

    /**
     * Get the ItemStack representation of this casing.
     * Used for indicator display in GUIs and tooltips.
     *
     * @return the item stack for this casing block state
     */
    @NotNull
    default ItemStack getItemStack() {
        IBlockState state = getBlockState();
        Block block = state.getBlock();
        int meta = block.getMetaFromState(state);
        return new ItemStack(block, 1, meta);
    }

    /**
     * Get the optional payload object carried by this casing.
     * Used to retrieve additional data (e.g. coil stats, casing type) without downcasting.
     *
     * @return the payload object, or null if none
     */
    @Nullable
    default Object getPayload() {
        return null;
    }

    /**
     * Get the payload as a specific type.
     * Convenience method that combines {@link #getPayload()} with a type check.
     *
     * @param type the expected payload type
     * @return the payload cast to the requested type, or null if absent or wrong type
     */
    @Nullable
    default <T> T getPayloadAs(@NotNull Class<T> type) {
        Object p = getPayload();
        return type.isInstance(p) ? type.cast(p) : null;
    }
}
