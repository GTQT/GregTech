package gregtech.api.pattern.casing;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

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
}
