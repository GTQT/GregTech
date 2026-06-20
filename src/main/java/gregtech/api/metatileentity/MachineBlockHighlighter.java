package gregtech.api.metatileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class MachineBlockHighlighter {

    @FunctionalInterface
    public interface Handler {

        void highlight(@NotNull EntityPlayer player, @NotNull BlockPos pos);
    }

    private static Handler handler = (player, pos) -> {};

    private MachineBlockHighlighter() {}

    public static void setHandler(@NotNull Handler newHandler) {
        handler = Objects.requireNonNull(newHandler);
    }

    public static void highlight(@NotNull EntityPlayer player, @NotNull BlockPos pos) {
        handler.highlight(player, pos);
    }
}
