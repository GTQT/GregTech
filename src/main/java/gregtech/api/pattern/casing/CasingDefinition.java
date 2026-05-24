package gregtech.api.pattern.casing;

import gregtech.api.util.BlockInfo;

import net.minecraft.block.state.IBlockState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry and factory for casing definitions.
 * Provides methods to create simple casings, tiered casings, and casing groups.
 *
 * <p>Usage example:
 * <pre>{@code
 * // Simple casing (no tier)
 * ICasing solidSteel = CasingDefinition.simple(
 *     MetaBlocks.METAL_CASING.getState(MetalCasingType.STEEL_SOLID),
 *     "gregtech.machine.casing.solid_steel");
 *
 * // Tiered casing group from a registry map
 * ICasingGroup heatingCoils = CasingDefinition.fromMap("heating_coils", true,
 *     GTCasingGroups.heatingCoils().channel(),
 *     GregTechAPI.HEATING_COILS,
 *     IHeatingCoilBlockStats::getTier,
 *     IHeatingCoilBlockStats::getName);
 *
 * // Tiered casing group from manually constructed list
 * ICasingGroup myGroup = CasingDefinition.fromEntries("my_group", true,
 *     GTStructureChannels.MY_CHANNEL,
 *     casingList);
 * }</pre>
 */
public final class CasingDefinition {

    private static final String CASING_GROUP_PREFIX = "gregtech.casing_group.";

    private static final Map<String, ICasingGroup> GROUPS = new HashMap<>();

    private CasingDefinition() {}

    // --- Factory methods for ICasing ---

    /**
     * Create a simple (non-tiered) casing definition.
     *
     * @param state          the block state
     * @param translationKey the translation key for the name
     * @return a new ICasing instance
     */
    public static ICasing simple(@NotNull IBlockState state, @NotNull String translationKey) {
        return new SimpleCasing(state, translationKey);
    }

    public static ICasing simple(@NotNull IBlockState state) {
        return new SimpleCasing(state, state.getBlock().getTranslationKey());
    }

    /**
     * Create a tiered casing definition.
     *
     * @param state          the block state
     * @param translationKey the translation key for the name
     * @param tier           the tier level
     * @return a new ICasing instance
     */
    public static ICasing tiered(@NotNull IBlockState state, @NotNull String translationKey, int tier) {
        return new TieredCasing(state, translationKey, tier, null);
    }

    /**
     * Create a tiered casing definition with a payload object.
     * The payload can be retrieved later via {@link ICasing#getPayload()} or
     * {@link ICasing#getPayloadAs(Class)} without downcasting.
     *
     * @param state          the block state
     * @param translationKey the translation key for the name
     * @param tier           the tier level
     * @param payload        optional payload object carried by this casing
     * @return a new ICasing instance
     */
    public static ICasing tiered(@NotNull IBlockState state, @NotNull String translationKey, int tier,
                                  @Nullable Object payload) {
        return new TieredCasing(state, translationKey, tier, payload);
    }

    // --- Factory methods for ICasingGroup ---

    /**
     * Create and register a tiered casing group (varargs).
     * Translation key is auto-derived as {@code "gregtech.casing_group." + groupId}.
     *
     * @param groupId         unique group identifier
     * @param requiresUniform true if all casings must be the same tier
     * @param casings         the casings in this group (will be sorted by tier)
     * @return the registered casing group
     */
    public static ICasingGroup tieredGroup(@NotNull String groupId, boolean requiresUniform,
                                            @NotNull ICasing... casings) {
        return tieredGroup(groupId, null, requiresUniform, casings);
    }

    /**
     * Create and register a tiered casing group (varargs) with explicit translation key.
     *
     * @param groupId         unique group identifier
     * @param translationKey  translation key for the group name, or null to auto-derive
     * @param requiresUniform true if all casings must be the same tier
     * @param casings         the casings in this group (will be sorted by tier)
     * @return the registered casing group
     */
    public static ICasingGroup tieredGroup(@NotNull String groupId, @Nullable String translationKey,
                                            boolean requiresUniform, @NotNull ICasing... casings) {
        List<ICasing> sorted = new ArrayList<>();
        Collections.addAll(sorted, casings);
        return registerGroup(groupId, translationKey, requiresUniform, null, sorted);
    }

    /**
     * Create and register a tiered casing group with a custom tier channel name (varargs).
     * Translation key is auto-derived as {@code "gregtech.casing_group." + groupId}.
     *
     * @param groupId         unique group identifier
     * @param requiresUniform true if all casings must be the same tier
     * @param tierChannel     the tier channel name
     * @param casings         the casings in this group (will be sorted by tier)
     * @return the registered casing group
     */
    public static ICasingGroup tieredGroup(@NotNull String groupId, boolean requiresUniform,
                                            @NotNull String tierChannel, @NotNull ICasing... casings) {
        List<ICasing> sorted = new ArrayList<>();
        Collections.addAll(sorted, casings);
        return registerGroup(groupId, null, requiresUniform, tierChannel, sorted);
    }

    /**
     * Create and register a tiered casing group with a custom tier channel name (List).
     * Translation key is auto-derived as {@code "gregtech.casing_group." + groupId}.
     *
     * @param groupId         unique group identifier
     * @param requiresUniform true if all casings must be the same tier
     * @param tierChannel     the tier channel name
     * @param casings         the casings in this group (will be sorted by tier)
     * @return the registered casing group
     */
    public static ICasingGroup tieredGroup(@NotNull String groupId, boolean requiresUniform,
                                            @NotNull String tierChannel, @NotNull List<ICasing> casings) {
        return registerGroup(groupId, null, requiresUniform, tierChannel, casings);
    }

    /**
     * Create and register a tiered casing group with a custom tier channel name (List)
     * and explicit translation key.
     *
     * @param groupId         unique group identifier
     * @param translationKey  translation key for the group name, or null to auto-derive
     * @param requiresUniform true if all casings must be the same tier
     * @param tierChannel     the tier channel name
     * @param casings         the casings in this group (will be sorted by tier)
     * @return the registered casing group
     */
    public static ICasingGroup tieredGroup(@NotNull String groupId, @Nullable String translationKey,
                                            boolean requiresUniform, @NotNull String tierChannel,
                                            @NotNull List<ICasing> casings) {
        return registerGroup(groupId, translationKey, requiresUniform, tierChannel, casings);
    }

    // --- High-level factory methods ---

    /**
     * Create and register a tiered casing group from a {@link Map} of block states to value objects,
     * with an explicit structure channel.
     *
     * <p>The map values are stored as payloads in the resulting {@link ICasing} instances,
     * and can be retrieved via {@link ICasing#getPayload()} or {@link ICasing#getPayloadAs(Class)}.
     *
     * <p>Automatically sorts casings by tier, registers indicator items for the channel,
     * and auto-derives the translation key as {@code "gregtech.casing_group." + groupId}.
     *
     * @param groupId         unique group identifier
     * @param requiresUniform true if all casings must be the same tier
     * @param channel         the structure channel (indicator items are auto-registered)
     * @param map             map from block state to value object
     * @param tierExtractor   function to extract tier from value
     * @param nameExtractor   function to extract translation key from value
     * @return the registered casing group and channel
     */
    public static <V> CasingRegistration fromMap(@NotNull String groupId, boolean requiresUniform,
                                                  @NotNull StructureChannel channel,
                                                  @NotNull Map<IBlockState, V> map,
                                                  @NotNull Function<V, Integer> tierExtractor,
                                                  @NotNull Function<V, String> nameExtractor) {
        List<ICasing> casings = new ArrayList<>();
        for (Map.Entry<IBlockState, V> entry : map.entrySet()) {
            V value = entry.getValue();
            casings.add(new TieredCasing(
                    entry.getKey(),
                    nameExtractor.apply(value),
                    tierExtractor.apply(value),
                    value));
        }
        ICasingGroup group = registerGroup(groupId, null, requiresUniform, channel.getName(), casings);
        StructureChannelRegistry.registerIndicatorsFromGroup(group, channel);
        return new CasingRegistration(group, channel);
    }

    /**
     * Create and register a tiered casing group from a {@link Map} of block states to value objects,
     * auto-creating a {@link SimpleStructureChannel} with the same name as the group.
     *
     * <p>This is the simplest way to register a new casing group — no need to create
     * a {@link StructureChannel} in advance. The channel name will equal the groupId,
     * and the tooltip key will be auto-derived as {@code "gregtech.structure_channel." + groupId}.
     *
     * @param groupId         unique group identifier (also used as channel name)
     * @param requiresUniform true if all casings must be the same tier
     * @param map             map from block state to value object
     * @param tierExtractor   function to extract tier from value
     * @param nameExtractor   function to extract translation key from value
     * @return the registration result containing the group and auto-created channel
     */
    public static <V> CasingRegistration fromMap(@NotNull String groupId, boolean requiresUniform,
                                                  @NotNull Map<IBlockState, V> map,
                                                  @NotNull Function<V, Integer> tierExtractor,
                                                  @NotNull Function<V, String> nameExtractor) {
        StructureChannel channel = getOrCreateChannel(groupId);
        return fromMap(groupId, requiresUniform, channel, map, tierExtractor, nameExtractor);
    }

    /**
     * Create and register a tiered casing group from an iterable of items, extracting
     * block state, tier, name, and optional payload via functions, with an explicit channel.
     *
     * <p>Each item's payload (if provided) is stored in the resulting {@link ICasing}
     * and can be retrieved via {@link ICasing#getPayload()} or {@link ICasing#getPayloadAs(Class)}.
     *
     * <p>Automatically sorts casings by tier, registers indicator items for the channel,
     * and auto-derives the translation key as {@code "gregtech.casing_group." + groupId}.
     *
     * @param groupId          unique group identifier
     * @param requiresUniform   true if all casings must be the same tier
     * @param channel           the structure channel (indicator items are auto-registered)
     * @param items             the items to create casings from
     * @param stateExtractor    function to extract block state from an item
     * @param tierExtractor     function to extract tier from an item
     * @param nameExtractor     function to extract translation key from an item
     * @param payloadExtractor  function to extract payload from an item (may return null)
     * @return the registered casing group and channel
     */
    public static <V> CasingRegistration fromIterable(@NotNull String groupId, boolean requiresUniform,
                                                       @NotNull StructureChannel channel,
                                                       @NotNull Iterable<V> items,
                                                       @NotNull Function<V, IBlockState> stateExtractor,
                                                       @NotNull Function<V, Integer> tierExtractor,
                                                       @NotNull Function<V, String> nameExtractor,
                                                       @NotNull Function<V, Object> payloadExtractor) {
        List<ICasing> casings = new ArrayList<>();
        for (V item : items) {
            Object payload = payloadExtractor.apply(item);
            casings.add(new TieredCasing(
                    stateExtractor.apply(item),
                    nameExtractor.apply(item),
                    tierExtractor.apply(item),
                    payload));
        }
        ICasingGroup group = registerGroup(groupId, null, requiresUniform, channel.getName(), casings);
        StructureChannelRegistry.registerIndicatorsFromGroup(group, channel);
        return new CasingRegistration(group, channel);
    }

    /**
     * Create and register a tiered casing group from an iterable of items with payload,
     * auto-creating a {@link SimpleStructureChannel}.
     *
     * @see #fromIterable(String, boolean, StructureChannel, Iterable, Function, Function, Function, Function)
     */
    public static <V> CasingRegistration fromIterable(@NotNull String groupId, boolean requiresUniform,
                                                       @NotNull Iterable<V> items,
                                                       @NotNull Function<V, IBlockState> stateExtractor,
                                                       @NotNull Function<V, Integer> tierExtractor,
@NotNull Function<V, String> nameExtractor,
                                                        @NotNull Function<V, Object> payloadExtractor) {
        StructureChannel channel = getOrCreateChannel(groupId);
        return fromIterable(groupId, requiresUniform, channel, items, stateExtractor, tierExtractor, nameExtractor, payloadExtractor);
    }

    /**
     * Create and register a tiered casing group from an iterable of items (without payload),
     * with an explicit channel.
     *
     * @see #fromIterable(String, boolean, StructureChannel, Iterable, Function, Function, Function, Function)
     */
    public static <V> CasingRegistration fromIterable(@NotNull String groupId, boolean requiresUniform,
                                                       @NotNull StructureChannel channel,
                                                       @NotNull Iterable<V> items,
                                                       @NotNull Function<V, IBlockState> stateExtractor,
                                                       @NotNull Function<V, Integer> tierExtractor,
                                                       @NotNull Function<V, String> nameExtractor) {
        return fromIterable(groupId, requiresUniform, channel, items, stateExtractor, tierExtractor, nameExtractor, item -> null);
    }

    /**
     * Create and register a tiered casing group from an iterable of items (without payload),
     * auto-creating a {@link SimpleStructureChannel}.
     *
     * @see #fromIterable(String, boolean, StructureChannel, Iterable, Function, Function, Function, Function)
     */
    public static <V> CasingRegistration fromIterable(@NotNull String groupId, boolean requiresUniform,
                                                       @NotNull Iterable<V> items,
                                                       @NotNull Function<V, IBlockState> stateExtractor,
@NotNull Function<V, Integer> tierExtractor,
                                                        @NotNull Function<V, String> nameExtractor) {
        StructureChannel channel = getOrCreateChannel(groupId);
        return fromIterable(groupId, requiresUniform, channel, items, stateExtractor, tierExtractor, nameExtractor, item -> null);
    }

    /**
     * Create and register a tiered casing group from an iterable of pre-constructed {@link ICasing} instances,
     * with an explicit channel.
     *
     * <p>Automatically sorts casings by tier, registers indicator items for the channel,
     * and auto-derives the translation key as {@code "gregtech.casing_group." + groupId}.
     *
     * @param groupId         unique group identifier
     * @param requiresUniform true if all casings must be the same tier
     * @param channel         the structure channel (indicator items are auto-registered)
     * @param casings          the casings in this group (will be sorted by tier)
     * @return the registered casing group and channel
     */
    public static CasingRegistration fromEntries(@NotNull String groupId, boolean requiresUniform,
                                                  @NotNull StructureChannel channel,
                                                  @NotNull Iterable<? extends ICasing> casings) {
        List<ICasing> list = new ArrayList<>();
        for (ICasing casing : casings) {
            list.add(casing);
        }
        ICasingGroup group = registerGroup(groupId, null, requiresUniform, channel.getName(), list);
        StructureChannelRegistry.registerIndicatorsFromGroup(group, channel);
        return new CasingRegistration(group, channel);
    }

    /**
     * Create and register a tiered casing group from an iterable of pre-constructed {@link ICasing} instances,
     * auto-creating a {@link SimpleStructureChannel}.
     *
     * @see #fromEntries(String, boolean, StructureChannel, Iterable)
     */
    public static CasingRegistration fromEntries(@NotNull String groupId, boolean requiresUniform,
                                                  @NotNull Iterable<? extends ICasing> casings) {
        StructureChannel channel = getOrCreateChannel(groupId);
        return fromEntries(groupId, requiresUniform, channel, casings);
    }

    /**
     * @param groupId the group ID
     * @return the registered group, or null if not found
     */
    public static ICasingGroup getGroup(String groupId) {
        return GROUPS.get(groupId);
    }

    /**
     * Get or create a {@link SimpleStructureChannel} for the given name.
     * If a channel with this name is already registered, returns it.
     * Otherwise, creates a new one and registers it.
     * This prevents duplicate registration errors when casing groups are rebuilt after cache invalidation.
     */
    private static StructureChannel getOrCreateChannel(String name) {
        StructureChannel existing = StructureChannelRegistry.getByName(name);
        if (existing != null) {
            return existing;
        }
        SimpleStructureChannel channel = new SimpleStructureChannel(name);
        StructureChannelRegistry.register(channel);
        return channel;
    }

    /**
     * @return all registered casing groups
     */
    public static Map<String, ICasingGroup> getAllGroups() {
        return Collections.unmodifiableMap(GROUPS);
    }

    /**
     * Get block info array for JEI preview from a casing group (sorted by tier).
     */
    public static BlockInfo[] getPreviewBlocks(@NotNull ICasingGroup group) {
        return group.getCasings().stream()
                .map(c -> new BlockInfo(c.getBlockState(), null))
                .toArray(BlockInfo[]::new);
    }

    // --- Internal ---

    private static ICasingGroup registerGroup(@NotNull String groupId, @Nullable String translationKey,
                                               boolean requiresUniform, @Nullable String tierChannel,
                                               @NotNull List<ICasing> casings) {
        List<ICasing> sorted = new ArrayList<>(casings);
        sorted.sort(Comparator.comparingInt(ICasing::getTier));
        String key = translationKey != null ? translationKey : CASING_GROUP_PREFIX + groupId;
        ICasingGroup group = new SimpleCasingGroup(groupId, key, sorted, requiresUniform, tierChannel);
        GROUPS.put(groupId, group);
        return group;
    }

    // --- Internal implementations ---

    private static class SimpleCasing implements ICasing {

        private final IBlockState state;
        private final String translationKey;

        SimpleCasing(IBlockState state, String translationKey) {
            this.state = state;
            this.translationKey = translationKey;
        }

        @Override
        public IBlockState getBlockState() {
            return state;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }

        @Override
        public boolean isTiered() {
            return false;
        }

        @Override
        public int getTier() {
            return 0;
        }
    }

    private static class TieredCasing implements ICasing {

        private final IBlockState state;
        private final String translationKey;
        private final int tier;
        @Nullable
        private final Object payload;

        TieredCasing(IBlockState state, String translationKey, int tier, @Nullable Object payload) {
            this.state = state;
            this.translationKey = translationKey;
            this.tier = tier;
            this.payload = payload;
        }

        @Override
        public IBlockState getBlockState() {
            return state;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }

        @Override
        public boolean isTiered() {
            return true;
        }

        @Override
        public int getTier() {
            return tier;
        }

        @Nullable
        @Override
        public Object getPayload() {
            return payload;
        }
    }

    private static class SimpleCasingGroup implements ICasingGroup {

        private final String groupId;
        private final String translationKey;
        private final List<ICasing> casings;
        private final boolean requiresUniform;
        private final String tierChannel;

        SimpleCasingGroup(String groupId, String translationKey, List<ICasing> casings, boolean requiresUniform) {
            this(groupId, translationKey, casings, requiresUniform, null);
        }

        SimpleCasingGroup(String groupId, String translationKey, List<ICasing> casings, boolean requiresUniform,
                          String tierChannel) {
            this.groupId = groupId;
            this.translationKey = translationKey;
            this.casings = Collections.unmodifiableList(casings);
            this.requiresUniform = requiresUniform;
            this.tierChannel = tierChannel;
        }

        @Override
        public String getGroupId() {
            return groupId;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }

        @Override
        public List<ICasing> getCasings() {
            return casings;
        }

        @Override
        public boolean requiresUniformTier() {
            return requiresUniform;
        }

        @Override
        public String getTierChannel() {
            return tierChannel != null ? tierChannel : groupId;
        }
    }
}