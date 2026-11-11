package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Extension point for turning a server-computed material bill into a mod-specific pattern item.
 *
 * <p>Handlers are consulted in registration order and the first one that succeeds wins. The base mod never
 * depends on a concrete pattern system; addons opt in by registering a {@link Handler}.
 */
public final class StructureEncodeHandlers {

    private static final List<Handler> HANDLERS = new CopyOnWriteArrayList<>();

    private StructureEncodeHandlers() {}

    /**
     * Registers a pattern writer.
     *
     * @param handler writer consulted for every encode request
     */
    public static void register(@NotNull Handler handler) {
        HANDLERS.add(handler);
    }

    /** @return whether at least one writer is available, so the build dialog can hide the encode action */
    public static boolean hasHandler() {
        return !HANDLERS.isEmpty();
    }

    /**
     * Offers the bill to every registered writer until one succeeds.
     *
     * @param player        the player requesting the pattern
     * @param controller    the controller whose formed result becomes the pattern output
     * @param requiredItems one stack per required block type, with the per-type total as stack size
     * @return whether a pattern was created and handed to the player
     */
    public static boolean encode(@NotNull EntityPlayer player, @NotNull MultiblockControllerBase controller,
                                 @NotNull List<ItemStack> requiredItems) {
        for (Handler handler : HANDLERS) {
            try {
                if (handler.encode(player, controller, requiredItems)) {
                    return true;
                }
            } catch (RuntimeException ignored) {}
        }
        return false;
    }

    /**
     * A writer for one pattern system.
     */
    public interface Handler {

        /**
         * @param player        the player requesting the pattern
         * @param controller    the controller whose formed result becomes the pattern output
         * @param requiredItems one stack per required block type, with the per-type total as stack size
         * @return whether a pattern was created and handed to the player
         */
        boolean encode(@NotNull EntityPlayer player, @NotNull MultiblockControllerBase controller,
                       @NotNull List<ItemStack> requiredItems);
    }
}
