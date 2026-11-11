package gregtech.api.pattern;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

/**
 * Optional external item source consulted by survival placement when the player inventory cannot supply a block.
 *
 * <p>Sources are registered through {@link StructureItemSourceRegistry}. Implementations must honour
 * {@code simulate}: they may check availability, but must not extract anything until the real build consumes it.
 */
public interface StructureItemSource {

    /**
     * Attempts to supply one block from this source.
     *
     * @param player    player the build belongs to
     * @param candidate the exact stack the structure wants to place
     * @param simulate  {@code true} to only check availability
     * @return whether the source can supply (or supplied) the stack
     */
    boolean extract(@NotNull EntityPlayer player, @NotNull ItemStack candidate, boolean simulate);

    /**
     * @param player player the build belongs to
     * @return whether this source is currently usable by the player (linked terminal, network in range, ...);
     * used for status hints, never for placement decisions
     */
    default boolean isLinked(@NotNull EntityPlayer player) {
        return false;
    }
}
