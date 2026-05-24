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
 * Supports lookup by canonical name or legacy alias, and manages indicator items
 * that represent specific channel tiers in GUIs and tooltips.
 *
 * <p>All built-in channels from {@link GTStructureChannels} are auto-registered at class load time.
 * Addons should register their custom channels during FML preInit or init phase via
 * {@link #register(StructureChannel)}.
 *
 * <p>Legacy aliases can be registered via {@link #registerAlias(String, StructureChannel)}
 * for backward compatibility with external mods that use different channel key names.
 */
public final class StructureChannelRegistry {

    // Canonical name -> channel
    private static final Map<String, StructureChannel> BY_NAME = new HashMap<>();

    // Legacy alias -> channel (for GT5 compat)
    private static final Map<String, StructureChannel> BY_ALIAS = new HashMap<>();

    // (channelName + ":" + tier) -> indicator ItemStack
    private static final Map<String, ItemStack> INDICATORS = new HashMap<>();

    private StructureChannelRegistry() {}

    static {
        // Auto-register all built-in channels
        for (GTStructureChannels channel : GTStructureChannels.values()) {
            register(channel);
        }
        // Legacy alias: NO_HATCH was previously named "gt_no_hatch"
        registerAlias("gt_no_hatch", GTStructureChannels.NO_HATCH);
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
     * Register a legacy alias that maps to an existing channel.
     * When resolving by alias, the alias is checked only if the canonical name lookup fails.
     * Addons can use this to support alternative key names for their channels.
     *
     * @param alias   the alternative key name
     * @param channel the target channel
     */
    public static void registerAlias(@NotNull String alias, @NotNull StructureChannel channel) {
        BY_ALIAS.put(alias, channel);
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
     * Look up a channel by a legacy alias.
     *
     * @param alias the legacy alias
     * @return the channel, or null if no alias matches
     */
    @Nullable
    public static StructureChannel getByAlias(@NotNull String alias) {
        return BY_ALIAS.get(alias);
    }

    /**
     * Resolve a channel by trying canonical name first, then legacy alias.
     * This is the recommended lookup method for user-facing code.
     *
     * @param key the canonical name or legacy alias
     * @return the channel, or null if not found
     */
    @Nullable
    public static StructureChannel resolve(@NotNull String key) {
        StructureChannel channel = BY_NAME.get(key);
        if (channel != null) return channel;
        return BY_ALIAS.get(key);
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
