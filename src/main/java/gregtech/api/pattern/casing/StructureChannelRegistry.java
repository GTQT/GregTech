package gregtech.api.pattern.casing;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Central registry for all {@link StructureChannel} instances.
 * Supports lookup by canonical name and manages indicator items that represent
 * specific channel tiers in GUIs and tooltips.
 *
 * <p>All built-in channels from {@link GTStructureChannels} are auto-registered at class load time.
 * Addons should register their custom channels during FML preInit or init phase via
 * {@link #register(StructureChannel)}.
 */
public final class StructureChannelRegistry {

    // Canonical name -> channel
    private static final Map<String, StructureChannel> BY_NAME = new HashMap<>();

    // (channelName + ":" + tier) -> indicator ItemStack
    private static final Map<String, ItemStack> INDICATORS = new HashMap<>();

    private StructureChannelRegistry() {}

    static {
        // Auto-register all built-in channels
        for (GTStructureChannels channel : GTStructureChannels.values()) {
            register(channel);
        }
    }

    /**
     * Register a channel. The channel's {@link StructureChannel#getName()} is used as the canonical key.
     *
     * @param channel the channel to register
     * @throws IllegalArgumentException if a channel with the same name is already registered
     */
    public static void register(@NotNull StructureChannel channel) {
        String name = channel.getName();
        if (BY_NAME.containsKey(name)) {
            throw new IllegalArgumentException("StructureChannel already registered: " + name);
        }
        BY_NAME.put(name, channel);
    }

    /**
     * Look up a channel by its canonical name.
     *
     * @param name the channel name
     * @return the channel, or null if not found
     */
    @Nullable
    public static StructureChannel getByName(@NotNull String name) {
        return BY_NAME.get(name);
    }

    /**
     * @return unmodifiable collection of all registered channels
     */
    @NotNull
    public static Collection<StructureChannel> getAll() {
        return Collections.unmodifiableCollection(BY_NAME.values());
    }

    /**
     * Register an indicator item for a specific channel tier.
     * Indicator items are used in GUIs and tooltips to visually represent a tier level.
     *
     * @param channel the channel
     * @param tier    the tier value
     * @param stack   the indicator item (will be copied)
     */
    public static void registerIndicator(@NotNull StructureChannel channel, int tier, @NotNull ItemStack stack) {
        String key = indicatorKey(channel, tier);
        INDICATORS.put(key, stack.copy());
    }

    /**
     * Get the indicator item for a specific channel tier.
     *
     * @param channel the channel
     * @param tier    the tier value
     * @return the indicator ItemStack (copy), or {@link ItemStack#EMPTY} if not registered
     */
    @NotNull
    public static ItemStack getIndicator(@NotNull StructureChannel channel, int tier) {
        String key = indicatorKey(channel, tier);
        ItemStack stack = INDICATORS.get(key);
        return stack != null ? stack.copy() : ItemStack.EMPTY;
    }

    /**
     * Check if an indicator is registered for the given channel and tier.
     */
    public static boolean hasIndicator(@NotNull StructureChannel channel, int tier) {
        return INDICATORS.containsKey(indicatorKey(channel, tier));
    }

    private static String indicatorKey(@NotNull StructureChannel channel, int tier) {
        return channel.getName() + ":" + tier;
    }

    /**
     * Register indicator items for channels that have associated casing groups.
     * Should be called after block registration is complete (e.g. during FML init).
     * This populates indicators from the casings in each group.
     *
     * @param group   the casing group
     * @param channel the channel associated with this group
     */
    public static void registerIndicatorsFromGroup(@NotNull ICasingGroup group, @NotNull StructureChannel channel) {
        for (ICasing casing : group.getCasings()) {
            ItemStack stack = casing.getItemStack();
            if (!stack.isEmpty()) {
                registerIndicator(channel, casing.getTier(), stack);
            }
        }
    }
}
