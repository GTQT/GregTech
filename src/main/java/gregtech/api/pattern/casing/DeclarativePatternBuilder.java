package gregtech.api.pattern.casing;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.BlockWorldState;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.PatternStringError;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.BlockInfo;

import net.minecraft.block.state.IBlockState;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A declarative builder for multiblock structure patterns.
 * Provides a higher-level API compared to raw {@link FactoryBlockPattern},
 * with automatic minimum casing count calculation, declarative hatch placement,
 * and tiered casing tracking.
 *
 * <p>This builder co-exists with FactoryBlockPattern — existing multiblocks
 * do not need to migrate. New multiblocks can use either approach.
 *
 * <p>Usage example:
 * <pre>{@code
 * DeclarativePatternBuilder.start(RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.FRONT)
 *     .aisle("XXX", "XCX", "XXX")
 *     .aisle("XXX", "X#X", "XXX")
 *     .aisle("XXX", "XSX", "XXX")
 *     .where('S', selfPredicate())
 *     .where('#', air())
 *     .casing('X', casingDef)
 *         .withHatches(MultiblockAbility.IMPORT_ITEMS, 1, 4)
 *         .withHatches(MultiblockAbility.EXPORT_ITEMS, 1, 4)
 *         .withHatches(MultiblockAbility.INPUT_ENERGY, 1, 3)
 *     .tieredCasing('C', coilGroup)
 *     .build();
 * }</pre>
 *
 * @see FactoryBlockPattern for the traditional builder
 * @see ICasing for casing definitions
 * @see ICasingGroup for tiered casing groups
 */
public class DeclarativePatternBuilder {

    private final FactoryBlockPattern factoryBuilder;
    private final Map<Character, CasingSlotInfo> casingSlots = new HashMap<>();
    private final Map<Character, TieredSlotInfo> tieredSlots = new HashMap<>();
    private final List<String[]> aisles = new ArrayList<>();

    private DeclarativePatternBuilder(FactoryBlockPattern factoryBuilder) {
        this.factoryBuilder = factoryBuilder;
    }

    /**
     * Start building a declarative pattern with default directions (RIGHT, UP, FRONT).
     */
    public static DeclarativePatternBuilder start() {
        return new DeclarativePatternBuilder(FactoryBlockPattern.start());
    }

    /**
     * Start building a declarative pattern with specified directions.
     */
    public static DeclarativePatternBuilder start(
            gregtech.api.util.RelativeDirection charDir,
            gregtech.api.util.RelativeDirection stringDir,
            gregtech.api.util.RelativeDirection aisleDir) {
        return new DeclarativePatternBuilder(FactoryBlockPattern.start(charDir, stringDir, aisleDir));
    }

    // --- Aisle methods (delegated to FactoryBlockPattern) ---

    /**
     * Define an aisle (a layer of the structure).
     */
    public DeclarativePatternBuilder aisle(String... aisle) {
        factoryBuilder.aisle(aisle);
        aisles.add(aisle);
        return this;
    }

    /**
     * Define a repeatable aisle.
     */
    public DeclarativePatternBuilder aisleRepeatable(int minRepeat, int maxRepeat, String... aisle) {
        factoryBuilder.aisleRepeatable(minRepeat, maxRepeat, aisle);
        aisles.add(aisle);
        return this;
    }

    // --- Standard where (pass-through to FactoryBlockPattern) ---

    /**
     * Define a character mapping using raw TraceabilityPredicate (for non-casing blocks).
     */
    public DeclarativePatternBuilder where(char symbol, TraceabilityPredicate predicate) {
        factoryBuilder.where(symbol, predicate);
        return this;
    }

    // --- Declarative casing methods ---

    /**
     * Define a casing slot. The minimum required count will be automatically calculated as:
     * (total occurrences of this char in all aisles) - (sum of all max hatch counts for this slot).
     *
     * @param symbol the character representing this casing in the aisle definition
     * @param casing the casing definition
     * @return a {@link CasingSlot} for chaining hatch declarations
     */
    public CasingSlot casing(char symbol, @NotNull ICasing casing) {
        CasingSlotInfo info = new CasingSlotInfo(symbol, casing);
        casingSlots.put(symbol, info);
        return new CasingSlot(this, info);
    }

    /**
     * Define a tiered casing slot. Automatically tracks tier uniformity through PatternMatchContext.
     * All blocks matching this character must be from the same tier in the given group.
     *
     * @param symbol the character representing this tiered casing in the aisle definition
     * @param group  the casing group (contains all valid tier options)
     * @return this builder
     */
    public DeclarativePatternBuilder tieredCasing(char symbol, @NotNull ICasingGroup group) {
        TieredSlotInfo info = new TieredSlotInfo(symbol, group);
        tieredSlots.put(symbol, info);
        return this;
    }

    // --- Build methods ---

    /**
     * Build the pattern. Automatically calculates minimum casing counts and
     * generates TraceabilityPredicates for all declared casing/tiered slots.
     *
     * @return the built BlockPattern (with template + state)
     */
    @SuppressWarnings("deprecation")
    public BlockPattern build() {
        // Process casing slots
        for (Map.Entry<Character, CasingSlotInfo> entry : casingSlots.entrySet()) {
            char symbol = entry.getKey();
            CasingSlotInfo info = entry.getValue();

            int totalCount = countCharInAisles(symbol);
            int maxHatches = info.hatches.stream().mapToInt(h -> h.maxCount).sum();
            int minCasings = Math.max(0, totalCount - maxHatches);

            TraceabilityPredicate predicate = createCasingPredicate(info.casing)
                    .setMinGlobalLimited(minCasings);

            // Add hatch predicates
            for (HatchInfo hatch : info.hatches) {
                predicate = predicate.or(
                        MultiblockControllerBase.abilities(hatch.ability)
                                .setMinGlobalLimited(hatch.minCount)
                                .setMaxGlobalLimited(hatch.maxCount));
            }

            factoryBuilder.where(symbol, predicate);
        }

        // Process tiered casing slots
        for (Map.Entry<Character, TieredSlotInfo> entry : tieredSlots.entrySet()) {
            char symbol = entry.getKey();
            TieredSlotInfo info = entry.getValue();
            factoryBuilder.where(symbol, createTieredPredicate(info.group));
        }

        return factoryBuilder.build();
    }

    /**
     * Build only the immutable template. Use for shared templates (P1 architecture).
     *
     * @return the built BlockPatternTemplate
     */
    public BlockPatternTemplate buildTemplate() {
        // Process casing slots
        for (Map.Entry<Character, CasingSlotInfo> entry : casingSlots.entrySet()) {
            char symbol = entry.getKey();
            CasingSlotInfo info = entry.getValue();

            int totalCount = countCharInAisles(symbol);
            int maxHatches = info.hatches.stream().mapToInt(h -> h.maxCount).sum();
            int minCasings = Math.max(0, totalCount - maxHatches);

            TraceabilityPredicate predicate = createCasingPredicate(info.casing)
                    .setMinGlobalLimited(minCasings);

            for (HatchInfo hatch : info.hatches) {
                predicate = predicate.or(
                        MultiblockControllerBase.abilities(hatch.ability)
                                .setMinGlobalLimited(hatch.minCount)
                                .setMaxGlobalLimited(hatch.maxCount));
            }

            factoryBuilder.where(symbol, predicate);
        }

        for (Map.Entry<Character, TieredSlotInfo> entry : tieredSlots.entrySet()) {
            char symbol = entry.getKey();
            TieredSlotInfo info = entry.getValue();
            factoryBuilder.where(symbol, createTieredPredicate(info.group));
        }

        return factoryBuilder.buildTemplate();
    }

    // --- Internal helpers ---

    private int countCharInAisles(char symbol) {
        int count = 0;
        for (String[] aisle : aisles) {
            for (String row : aisle) {
                for (int i = 0; i < row.length(); i++) {
                    if (row.charAt(i) == symbol) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private TraceabilityPredicate createCasingPredicate(@NotNull ICasing casing) {
        IBlockState state = casing.getBlockState();
        return new TraceabilityPredicate(
                blockWorldState -> blockWorldState.getBlockState().equals(state),
                () -> new BlockInfo[] { new BlockInfo(state, null) });
    }

    private TraceabilityPredicate createTieredPredicate(@NotNull ICasingGroup group) {
        String channel = group.getTierChannel();
        boolean requiresUniform = group.requiresUniformTier();
        List<ICasing> casings = group.getCasings();

        // Create a map for quick lookup
        Map<IBlockState, ICasing> stateMap = new HashMap<>();
        for (ICasing c : casings) {
            stateMap.put(c.getBlockState(), c);
        }

        return new TraceabilityPredicate(blockWorldState -> {
            IBlockState blockState = blockWorldState.getBlockState();
            ICasing matched = stateMap.get(blockState);
            if (matched == null) return false;

            if (requiresUniform) {
                Object existing = blockWorldState.getMatchContext().getOrPut(channel, matched);
                if (!existing.equals(matched)) {
                    blockWorldState.setError(new PatternStringError(
                            "gregtech.multiblock.pattern.error.casing_tier_mismatch"));
                    return false;
                }
            } else {
                blockWorldState.getMatchContext().getOrPut(channel, matched);
            }
            return true;
        }, () -> casings.stream()
                .sorted(Comparator.comparingInt(ICasing::getTier))
                .map(c -> new BlockInfo(c.getBlockState(), null))
                .toArray(BlockInfo[]::new))
                .addTooltips("gregtech.multiblock.pattern.error.casing_tier_mismatch");
    }

    // --- CasingSlot fluent API ---

    /**
     * Fluent API for declaring hatches on a casing slot.
     */
    public static class CasingSlot {

        private final DeclarativePatternBuilder builder;
        private final CasingSlotInfo info;

        CasingSlot(DeclarativePatternBuilder builder, CasingSlotInfo info) {
            this.builder = builder;
            this.info = info;
        }

        /**
         * Add a hatch type to this casing slot.
         *
         * @param ability  the multiblock ability for this hatch
         * @param minCount minimum number of this hatch type
         * @param maxCount maximum number of this hatch type
         * @return this CasingSlot for chaining
         */
        public CasingSlot withHatches(@NotNull MultiblockAbility<?> ability, int minCount, int maxCount) {
            info.hatches.add(new HatchInfo(ability, minCount, maxCount));
            return this;
        }

        /**
         * Add optional hatches (min=0).
         */
        public CasingSlot withOptionalHatches(@NotNull MultiblockAbility<?> ability, int maxCount) {
            return withHatches(ability, 0, maxCount);
        }

        /**
         * Finish declaring hatches for this slot and return to the main builder.
         * (Also allows chaining back to builder methods directly through the CasingSlot.)
         */
        public DeclarativePatternBuilder done() {
            return builder;
        }

        // --- Pass-through methods for seamless chaining ---

        public DeclarativePatternBuilder aisle(String... aisle) {
            return builder.aisle(aisle);
        }

        public DeclarativePatternBuilder where(char symbol, TraceabilityPredicate predicate) {
            return builder.where(symbol, predicate);
        }

        public CasingSlot casing(char symbol, @NotNull ICasing casing) {
            return builder.casing(symbol, casing);
        }

        public DeclarativePatternBuilder tieredCasing(char symbol, @NotNull ICasingGroup group) {
            return builder.tieredCasing(symbol, group);
        }

        public BlockPattern build() {
            return builder.build();
        }

        public BlockPatternTemplate buildTemplate() {
            return builder.buildTemplate();
        }
    }

    // --- Internal data classes ---

    private static class CasingSlotInfo {

        final char symbol;
        final ICasing casing;
        final List<HatchInfo> hatches = new ArrayList<>();

        CasingSlotInfo(char symbol, ICasing casing) {
            this.symbol = symbol;
            this.casing = casing;
        }
    }

    private static class TieredSlotInfo {

        final char symbol;
        final ICasingGroup group;

        TieredSlotInfo(char symbol, ICasingGroup group) {
            this.symbol = symbol;
            this.group = group;
        }
    }

    private static class HatchInfo {

        final MultiblockAbility<?> ability;
        final int minCount;
        final int maxCount;

        HatchInfo(MultiblockAbility<?> ability, int minCount, int maxCount) {
            this.ability = ability;
            this.minCount = minCount;
            this.maxCount = maxCount;
        }
    }
}
