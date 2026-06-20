package gregtech.api.pattern;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

/** Optional item source used by survival structure placement integrations. */
public interface StructureItemSource {

    boolean extract(@NotNull EntityPlayer player, @NotNull ItemStack candidate, boolean simulate);
}
