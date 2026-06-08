package gregtech.api.pattern.casing;

import gregtech.api.block.VariantActiveBlock;
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

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
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

    /**
     * Set a channel name on the last declared repeatable aisle.
     * The channel value controls the repetition count in previews and autoBuild.
     *
     * @param channelName the channel name controlling this aisle's repetition
     */
    public DeclarativePatternBuilder withAisleChannel(@NotNull String channelName) {
        factoryBuilder.setRepeatable(
                factoryBuilder.getLastAisleRepetition()[0],
                factoryBuilder.getLastAisleRepetition()[1],
                channelName);
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
     * @return a {@link TieredCasingSlot} for optional channel configuration
     */
    public TieredCasingSlot tieredCasing(char symbol, @NotNull ICasingGroup group) {
        TieredSlotInfo info = new TieredSlotInfo(symbol, group);
        tieredSlots.put(symbol, info);
        return new TieredCasingSlot(this, info);
    }

    // --- Build methods ---

    /**
     * Build the pattern. Automatically calculates minimum casing counts and
     * generates TraceabilityPredicates for all declared casing/tiered slots.
     * Also generates and attaches structure description to the template for tooltip display.
     *
     * @return the built BlockPattern (with template + state)
     * @deprecated Use {@link #buildTemplate()} for the new template-based architecture.
     *             Will be removed in version 2.10.
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2.10")
    @SuppressWarnings("deprecation")
    public BlockPattern build() {
        processSlots();
        BlockPattern pattern = factoryBuilder.build();
        attachStructureDescription(pattern.getTemplate());
        return pattern;
    }

    /**
     * Build only the immutable template. Use for shared templates (P1 architecture).
     * Also generates and attaches structure description.
     *
     * @return the built BlockPatternTemplate
     */
    public BlockPatternTemplate buildTemplate() {
        processSlots();
        BlockPatternTemplate template = factoryBuilder.buildTemplate();
        attachStructureDescription(template);
        return template;
    }

    /**
     * Process all declared casing and tiered slots into FactoryBlockPattern predicates.
     */
    private void processSlots() {
        // Process casing slots
        for (Map.Entry<Character, CasingSlotInfo> entry : casingSlots.entrySet()) {
            char symbol = entry.getKey();
            CasingSlotInfo info = entry.getValue();

            int totalCount = countCharInAisles(symbol);
            int maxHatches = info.hatches.stream().mapToInt(h -> h.maxCount).sum()
                    + info.customHatches.stream().mapToInt(h -> h.maxCount).sum();
            int minCasings = Math.max(0, totalCount - maxHatches);

            TraceabilityPredicate predicate = createCasingPredicate(info.casing)
                    .setMinGlobalLimited(minCasings);

            // Add ability-based hatch predicates with JEI preview count
            for (HatchInfo hatch : info.hatches) {
                predicate = predicate.or(
                        MultiblockControllerBase.abilities(hatch.ability)
                                .setMinGlobalLimited(hatch.minCount)
                                .setMaxGlobalLimited(hatch.maxCount)
                                .setPreviewCount(Math.max(1, hatch.minCount)));
            }

            // Add custom hatch predicates
            for (CustomHatchInfo customHatch : info.customHatches) {
                predicate = predicate.or(customHatch.predicate);
            }

            factoryBuilder.where(symbol, predicate);
        }

        // Process tiered casing slots
        for (Map.Entry<Character, TieredSlotInfo> entry : tieredSlots.entrySet()) {
            char symbol = entry.getKey();
            TieredSlotInfo info = entry.getValue();
            String channelName = info.channel != null ? info.channel.getName() : info.group.getTierChannel();
            factoryBuilder.where(symbol, createTieredPredicate(info.group, channelName));
        }
    }

    /**
     * Generate structure tooltip description from declared slots and attach to template.
     * Stores raw translation keys (not formatted strings) for server-safe operation.
     * Actual formatting happens at tooltip display time (client-side).
     */
    private void attachStructureDescription(@NotNull BlockPatternTemplate template) {
        List<String> lines = new ArrayList<>();

        // Add casing requirements as raw format patterns
        for (CasingSlotInfo info : casingSlots.values()) {
            char symbol = info.symbol;
            int totalCount = countCharInAisles(symbol);
            int maxHatches = info.hatches.stream().mapToInt(h -> h.maxCount).sum()
                    + info.customHatches.stream().mapToInt(h -> h.maxCount).sum();
            int minCasings = Math.max(0, totalCount - maxHatches);

            // Format: "casing:<translationKey>:<minCount>:<maxCount>"
            lines.add("casing:" + info.casing.getItemStack().getTranslationKey() + ":" + minCasings + ":" + totalCount);

            // Add hatch lines: "hatch:<abilityName>:<minCount>:<maxCount>"
            for (HatchInfo hatch : info.hatches) {
                lines.add("hatch:" + hatch.ability.toString() + ":" + hatch.minCount + ":" + hatch.maxCount);
            }
        }

        // Add tiered casing requirements: "tiered:<translationKey>:<requiresUniform>"
        for (TieredSlotInfo info : tieredSlots.values()) {
            lines.add("tiered:" + info.group.getTranslationKey() + ":" + info.group.requiresUniformTier());
            if (info.channel != null) {
                lines.add("channel:" + info.channel.getDefaultTooltip());
            }
        }

        if (!lines.isEmpty()) {
            template.setStructureDescription(lines);
        }
    }

    /**
     * Get the translation key for a MultiblockAbility's display name.
     */
    private static String getAbilityTranslationKey(MultiblockAbility<?> ability) {
        return "gregtech.multiblock.ability." + ability.toString();
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
                blockWorldState -> {
                    if (!blockWorldState.getBlockState().equals(state)) return false;
                    trackVariantActiveBlock(blockWorldState);
                    return true;
                },
                () -> new BlockInfo[] { new BlockInfo(state, null) });
    }

    private TraceabilityPredicate createTieredPredicate(@NotNull ICasingGroup group, @NotNull String channelName) {
        boolean requiresUniform = group.requiresUniformTier();
        List<ICasing> casings = group.getCasings();

        Map<IBlockState, ICasing> stateMap = new HashMap<>();
        for (ICasing c : casings) {
            stateMap.put(c.getBlockState(), c);
        }

        TraceabilityPredicate predicate = new TraceabilityPredicate(blockWorldState -> {
            IBlockState blockState = blockWorldState.getBlockState();
            ICasing matched = stateMap.get(blockState);
            if (matched == null) return false;
            trackVariantActiveBlock(blockWorldState);

            if (requiresUniform) {
                Object existing = blockWorldState.getMatchContext().getOrPut(channelName, matched);
                if (!existing.equals(matched)) {
                    blockWorldState.setError(new PatternStringError(
                            "gregtech.multiblock.pattern.error.casing_tier_mismatch"));
                    return false;
                }
            } else {
                blockWorldState.getMatchContext().getOrPut(channelName, matched);
            }
            if (matched.isTiered()) {
                blockWorldState.getMatchContext().set(channelName + ".tier", matched.getTier());
            }
            return true;
        }, () -> casings.stream()
                .map(c -> new BlockInfo(c.getBlockState(), null))
                .toArray(BlockInfo[]::new))
                .addTooltips("gregtech.multiblock.pattern.error.casing_tier_mismatch");

        for (TraceabilityPredicate.SimplePredicate sp : predicate.common) {
            sp.channelName = channelName;
        }
        for (TraceabilityPredicate.SimplePredicate sp : predicate.limited) {
            sp.channelName = channelName;
        }

        return predicate;
    }

    private static void trackVariantActiveBlock(@NotNull BlockWorldState blockWorldState) {
        if (blockWorldState.getBlockState().getBlock() instanceof VariantActiveBlock) {
            blockWorldState.getMatchContext().getOrPut("VABlock", new LinkedList<>())
                    .add(blockWorldState.getPos());
        }
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
        public CasingSlot hatch(@NotNull MultiblockAbility<?> ability, int minCount, int maxCount) {
            if(maxCount == 0)return this;
            info.hatches.add(new HatchInfo(ability, minCount, maxCount));
            return this;
        }

        public CasingSlot hatch(@NotNull MultiblockAbility<?> ability, int currentCount) {
            if(currentCount == 0)return this;
            info.hatches.add(new HatchInfo(ability, currentCount, currentCount));
            return this;
        }

        public CasingSlot optionalHatch(@NotNull MultiblockAbility<?> ability, int maxCount) {
            return hatch(ability, 0, maxCount);
        }

        public CasingSlot muffler() {
            return hatch(MultiblockAbility.MUFFLER_HATCH, 1);
        }

        public CasingSlot maintenance() {
            return hatch(MultiblockAbility.MAINTENANCE_HATCH, 1);
        }

        public CasingSlot computerReception() {
            return hatch(MultiblockAbility.COMPUTATION_DATA_RECEPTION, 1);
        }

        public CasingSlot computerTransmission() {
            return hatch(MultiblockAbility.COMPUTATION_DATA_TRANSMISSION, 1);
        }

        public CasingSlot energyInput(int minCount, int maxCount) {
            return hatch(MultiblockAbility.INPUT_ENERGY, minCount, maxCount);
        }

        public CasingSlot energyInput(int currentCount) {
            return hatch(MultiblockAbility.INPUT_ENERGY, currentCount);
        }

        public CasingSlot optionalEnergyInput(int maxCount) {
            return optionalHatch(MultiblockAbility.INPUT_ENERGY, maxCount);
        }

        public CasingSlot energyOutput(int minCount, int maxCount) {
            return hatch(MultiblockAbility.OUTPUT_ENERGY, minCount, maxCount);
        }

        public CasingSlot energyOutput(int currentCount) {
            return hatch(MultiblockAbility.OUTPUT_ENERGY, currentCount);
        }

        public CasingSlot optionalEnergyOutput(int maxCount) {
            return optionalHatch(MultiblockAbility.OUTPUT_ENERGY, maxCount);
        }

        public CasingSlot substationInput(int minCount, int maxCount) {
            return hatch(MultiblockAbility.SUBSTATION_INPUT_ENERGY, minCount, maxCount);
        }

        public CasingSlot substationInput(int currentCount) {
            return hatch(MultiblockAbility.SUBSTATION_INPUT_ENERGY, currentCount);
        }

        public CasingSlot optionalSubstationInput(int maxCount) {
            return optionalHatch(MultiblockAbility.SUBSTATION_INPUT_ENERGY, maxCount);
        }

        public CasingSlot substationOutput(int minCount, int maxCount) {
            return hatch(MultiblockAbility.SUBSTATION_OUTPUT_ENERGY, minCount, maxCount);
        }

        public CasingSlot substationOutput(int currentCount) {
            return hatch(MultiblockAbility.SUBSTATION_OUTPUT_ENERGY, currentCount);
        }

        public CasingSlot optionalSubstationOutput(int maxCount) {
            return optionalHatch(MultiblockAbility.SUBSTATION_OUTPUT_ENERGY, maxCount);
        }

        public CasingSlot laserInput(int minCount, int maxCount) {
            return hatch(MultiblockAbility.INPUT_LASER, minCount, maxCount);
        }

        public CasingSlot laserInput(int currentCount) {
            return hatch(MultiblockAbility.INPUT_LASER, currentCount);
        }

        public CasingSlot optionalLaserInput(int maxCount) {
            return optionalHatch(MultiblockAbility.INPUT_LASER, maxCount);
        }

        public CasingSlot laserOutput(int minCount, int maxCount) {
            return hatch(MultiblockAbility.OUTPUT_LASER, minCount, maxCount);
        }

        public CasingSlot laserOutput(int currentCount) {
            return hatch(MultiblockAbility.OUTPUT_LASER, currentCount);
        }

        public CasingSlot optionalLaserOutput(int maxCount) {
            return optionalHatch(MultiblockAbility.OUTPUT_LASER, maxCount);
        }

        public CasingSlot fluidInput(int minCount, int maxCount) {
            return hatch(MultiblockAbility.IMPORT_FLUIDS, minCount, maxCount);
        }

        public CasingSlot fluidInput(int currentCount) {
            return hatch(MultiblockAbility.IMPORT_FLUIDS, currentCount);
        }

        public CasingSlot optionalFluidInput(int maxCount) {
            return optionalHatch(MultiblockAbility.IMPORT_FLUIDS, maxCount);
        }

        public CasingSlot fluidOutput(int minCount, int maxCount) {
            return hatch(MultiblockAbility.EXPORT_FLUIDS, minCount, maxCount);
        }

        public CasingSlot fluidOutput(int currentCount) {
            return hatch(MultiblockAbility.EXPORT_FLUIDS, currentCount);
        }

        public CasingSlot optionalFluidOutput(int maxCount) {
            return optionalHatch(MultiblockAbility.EXPORT_FLUIDS, maxCount);
        }

        public CasingSlot itemInput(int minCount, int maxCount) {
            return hatch(MultiblockAbility.IMPORT_ITEMS, minCount, maxCount);
        }

        public CasingSlot itemInput(int currentCount) {
            return hatch(MultiblockAbility.IMPORT_ITEMS, currentCount);
        }

        public CasingSlot optionalItemInput(int maxCount) {
            return optionalHatch(MultiblockAbility.IMPORT_ITEMS, maxCount);
        }

        public CasingSlot itemOutput(int minCount, int maxCount) {
            return hatch(MultiblockAbility.EXPORT_ITEMS, minCount, maxCount);
        }

        public CasingSlot itemOutput(int currentCount) {
            return hatch(MultiblockAbility.EXPORT_ITEMS, currentCount);
        }

        public CasingSlot optionalItemOutput(int maxCount) {
            return optionalHatch(MultiblockAbility.EXPORT_ITEMS, maxCount);
        }

        public CasingSlot auto(){
            return muffler()
                    .maintenance()
                    .energyInput(1,2)
                    .itemInput(1,4)
                    .itemOutput(1,4)
                    .fluidInput(1,2)
                    .fluidOutput(1,2);
        }

        public CasingSlot auto(boolean isMuffler,boolean isMaintenance,boolean isEnergyInput,boolean isItemInput,boolean isItemOutput,boolean isFluidInput,boolean isFluidOutput){
            CasingSlot slot = this;
            if(isMuffler) slot = slot.muffler();
            if(isMaintenance) slot = slot.maintenance();
            if(isEnergyInput) slot = slot.energyInput(1,2);
            if(isItemInput) slot = slot.itemInput(1,4);
            if(isItemOutput) slot = slot.itemOutput(1,4);
            if(isFluidInput) slot = slot.fluidInput(1,2);
            if(isFluidOutput) slot = slot.fluidOutput(1,2);
            return slot;
        }

        /**
         * Add a custom hatch using a raw TraceabilityPredicate.
         * Useful for non-standard hatches that don't use {@link MultiblockAbility},
         * such as special hatch MetaTileEntities (e.g. coke oven hatch, tank valve).
         *
         * <p>The maxCount contributes to the automatic minimum casing count calculation:
         * minCasings = totalPositions - sum(allMaxHatches).
         *
         * @param predicate the custom predicate for the hatch
         * @param maxCount  maximum number of positions this predicate can occupy
         * @return this CasingSlot for chaining
         */
        public CasingSlot custom(@NotNull TraceabilityPredicate predicate, int maxCount) {
            info.customHatches.add(new CustomHatchInfo(predicate, maxCount));
            return this;
        }

        /**
         * Apply a reusable hatch preset to this casing slot.
         * Presets encapsulate common hatch combinations to reduce boilerplate.
         *
         * @param preset the hatch preset to apply
         * @return this CasingSlot for chaining
         * @see HatchPresets for standard presets
         */
        public CasingSlot preset(@NotNull IHatchPreset preset) {
            preset.apply(this);
            return this;
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

        public TieredCasingSlot tieredCasing(char symbol, @NotNull ICasingGroup group) {
            return builder.tieredCasing(symbol, group);
        }

        /** @deprecated Use {@link #buildTemplate()} instead. Will be removed in version 2.10. */
        @Deprecated
        @ApiStatus.ScheduledForRemoval(inVersion = "2.10")
        @SuppressWarnings("deprecation")
        public BlockPattern build() {
            return builder.build();
        }

        public BlockPatternTemplate buildTemplate() {
            return builder.buildTemplate();
        }
    }

    // --- TieredCasingSlot fluent API ---

    /**
     * Fluent API for configuring a tiered casing slot (channel override, etc.).
     */
    public static class TieredCasingSlot {

        private final DeclarativePatternBuilder builder;
        private final TieredSlotInfo info;

        TieredCasingSlot(DeclarativePatternBuilder builder, TieredSlotInfo info) {
            this.builder = builder;
            this.info = info;
        }

        /**
         * Override the default tier channel for this tiered casing slot.
         * By default, the channel name comes from {@link ICasingGroup#getTierChannel()}.
         * Use this method to associate an explicit {@link StructureChannel}.
         *
         * @param channel the structure channel to use
         * @return this TieredCasingSlot for chaining
         */
        public TieredCasingSlot withChannel(@NotNull StructureChannel channel) {
            info.channel = channel;
            return this;
        }

        /**
         * Finish configuring this tiered slot and return to the main builder.
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

        public TieredCasingSlot tieredCasing(char symbol, @NotNull ICasingGroup group) {
            return builder.tieredCasing(symbol, group);
        }

        /** @deprecated Use {@link #buildTemplate()} instead. Will be removed in version 2.10. */
        @Deprecated
        @ApiStatus.ScheduledForRemoval(inVersion = "2.10")
        @SuppressWarnings("deprecation")
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
        final List<CustomHatchInfo> customHatches = new ArrayList<>();

        CasingSlotInfo(char symbol, ICasing casing) {
            this.symbol = symbol;
            this.casing = casing;
        }
    }

    private static class TieredSlotInfo {

        final char symbol;
        final ICasingGroup group;
        StructureChannel channel;

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

    // --- Custom hatch info for non-ability predicates ---

    private static class CustomHatchInfo {

        final TraceabilityPredicate predicate;
        final int maxCount;

        CustomHatchInfo(TraceabilityPredicate predicate, int maxCount) {
            this.predicate = predicate;
            this.maxCount = maxCount;
        }
    }
}
