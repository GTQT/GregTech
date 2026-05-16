package gregtech.api.pattern.casing;

import gregtech.api.util.BlockInfo;

import net.minecraft.block.state.IBlockState;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * // Tiered casing group
 * ICasingGroup heatingCoils = CasingDefinition.tieredGroup("heating_coils",
 *     "gregtech.casing_group.heating_coils", true,
 *     CasingDefinition.tiered(coilState1, "gregtech.coil.cupronickel", 1),
 *     CasingDefinition.tiered(coilState2, "gregtech.coil.kanthal", 2),
 *     ...);
 * }</pre>
 */
public final class CasingDefinition {

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
        return new TieredCasing(state, translationKey, tier);
    }

    // --- Factory methods for ICasingGroup ---

    /**
     * Create and register a tiered casing group.
     *
     * @param groupId         unique group identifier
     * @param translationKey  translation key for the group name
     * @param requiresUniform true if all casings must be the same tier
     * @param casings         the casings in this group (will be sorted by tier)
     * @return the registered casing group
     */
    public static ICasingGroup tieredGroup(@NotNull String groupId, @NotNull String translationKey,
                                           boolean requiresUniform, @NotNull ICasing... casings) {
        List<ICasing> sorted = new ArrayList<>();
        Collections.addAll(sorted, casings);
        sorted.sort(Comparator.comparingInt(ICasing::getTier));
        ICasingGroup group = new SimpleCasingGroup(groupId, translationKey, sorted, requiresUniform);
        GROUPS.put(groupId, group);
        return group;
    }

    /**
     * Create and register a tiered casing group with a custom tier channel name.
     *
     * @param groupId         unique group identifier
     * @param translationKey  translation key for the group name
     * @param requiresUniform true if all casings must be the same tier
     * @param tierChannel     the tier channel name (used as key in PatternMatchContext)
     * @param casings         the casings in this group (will be sorted by tier)
     * @return the registered casing group
     */
    public static ICasingGroup tieredGroup(@NotNull String groupId, @NotNull String translationKey,
                                           boolean requiresUniform, @NotNull String tierChannel,
                                           @NotNull List<ICasing> casings) {
        List<ICasing> sorted = new ArrayList<>(casings);
        sorted.sort(Comparator.comparingInt(ICasing::getTier));
        ICasingGroup group = new SimpleCasingGroup(groupId, translationKey, sorted, requiresUniform, tierChannel);
        GROUPS.put(groupId, group);
        return group;
    }

    /**
     * @param groupId the group ID
     * @return the registered group, or null if not found
     */
    public static ICasingGroup getGroup(String groupId) {
        return GROUPS.get(groupId);
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

        TieredCasing(IBlockState state, String translationKey, int tier) {
            this.state = state;
            this.translationKey = translationKey;
            this.tier = tier;
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
