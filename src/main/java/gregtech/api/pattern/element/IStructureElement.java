package gregtech.api.pattern.element;

import gregtech.api.pattern.PieceTemplateCompiler;
import gregtech.api.pattern.StructureDependency;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureHintRenderResult;
import gregtech.api.pattern.StructureIncrementalSupport;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.BlockInfo;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Single-position matching rule for structure elements.
 * Each element defines how a single position in a structure piece is matched,
 * previewed, and auto-built.
 *
 * <p>This interface is the single canonical concept for cell-level matching
 * in the new (StructureDefinition) path. The legacy
 * {@link gregtech.api.pattern.TraceabilityPredicate} remains a public API
 * for the old path and as an optional compatibility view for old tooling.
 */
public interface IStructureElement<T> {

    /**
     * Operations this element can execute safely. Snapshot matching is opt-in
     * because legacy predicates and tile-entity reads are not thread-safe by
     * default.
     */
    @NotNull
    default Set<StructureElementCapability> getCapabilities() {
        return StructureElementCapability.standard();
    }

    default boolean supports(@NotNull StructureElementCapability capability) {
        return getCapabilities().contains(capability);
    }

    /**
     * Whether this element can participate in the contribution-eligible
     * evaluator path. New direct elements are typed by default; migration
     * wrappers and legacy predicate adapters override this when they hide
     * side effects from the dependency compiler.
     */
    @NotNull
    default StructureIncrementalSupport getIncrementalSupport() {
        return StructureIncrementalSupport.TYPED_CONTRIBUTION;
    }

    /**
     * Typed inputs that can affect this element's match or contribution result.
     *
     * <p>Direct elements that read previously formed piece metadata, controller
     * mode, configured channels, upgrades, or other non-block state should
     * declare those inputs here. The incremental eligibility compiler consumes
     * this metadata directly; callers should not route new runtime logic through
     * legacy compatibility state just to make dependencies visible.
     */
    @NotNull
    default Set<StructureDependency> getDependencies() {
        return Collections.emptySet();
    }

    /**
     * Whether this direct element explicitly declares its incremental support
     * and typed dependencies instead of inheriting compatibility defaults.
     *
     * <p>Existing addon elements remain source-compatible, but the dependency
     * compiler treats undeclared contracts as opaque until both methods are
     * implemented.
     */
    @ApiStatus.Internal
    default boolean hasExplicitIncrementalContract() {
        try {
            return getClass().getMethod("getIncrementalSupport").getDeclaringClass()
                    != IStructureElement.class
                    && getClass().getMethod("getDependencies").getDeclaringClass()
                    != IStructureElement.class;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    /**
     * Low-level runtime check entry. Compiled templates call
     * {@link #match(StructureEvaluationContext)} for normal formation matching;
     * direct callers can use this method when they intentionally need a check
     * without deferred requirement collection.
     */
    boolean check(@NotNull StructureEvaluationContext<T> context);

    /**
     * Canonical runtime match entry for one structure cell. The default
     * preserves the existing contract by collecting requirements before the
     * element check; composite elements may override this to make requirement
     * collection branch-local.
     */
    default boolean match(@NotNull StructureEvaluationContext<T> context) {
        collectRequirements(context);
        return check(context);
    }

    /**
     * Get the candidate blocks for preview and auto-build.
     *
     * @return array of candidate BlockInfo, or empty array if not applicable
     */
    BlockInfo[] getCandidates();

    /**
     * Canonical candidate entry for preview and both build modes.
     */
    default BlockInfo[] getCandidates(@NotNull StructureEvaluationContext<T> context) {
        return getCandidates();
    }

    /**
     * Direct preview/build metadata for this element.
     *
     * <p>New elements should override this when candidate selection needs
     * channel preferences, preview counts, default candidates, or count-limited
     * candidate groups. The default exposes {@link #getCandidates()} as one
     * common group so V3 preview/build code does not need to inspect
     * {@link TraceabilityPredicate} metadata.
     */
    @NotNull
    default StructureElementPreview getPreview() {
        return StructureElementPreview.of(this::getCandidates);
    }

    /**
     * Context-aware direct preview/build metadata.
     */
    @NotNull
    default StructureElementPreview getPreview(@NotNull StructureEvaluationContext<T> context) {
        return getPreview();
    }

    /**
     * Return candidate item stacks for survival construction. The default maps
     * block candidates to their dropped item form and is intentionally advisory:
     * specialised elements can still accept/place blocks not represented here.
     */
    @Nullable
    default BlocksToPlace getBlocksToPlace(@NotNull StructureEvaluationContext<T> context,
                                           @NotNull ItemStack trigger,
                                           @NotNull AutoPlaceEnvironment env) {
        BlockInfo[] candidates = getCandidates(context);
        if (candidates == null || candidates.length == 0) {
            return null;
        }
        return BlocksToPlace.create(Arrays.stream(candidates)
                .filter(Objects::nonNull)
                .map(BlockInfo::getBlockState)
                .filter(Objects::nonNull)
                .filter(state -> state.getBlock() != null)
                .map(state -> {
                    Block block = state.getBlock();
                    Item item = Item.getItemFromBlock(block);
                    if (item == null) {
                        return ItemStack.EMPTY;
                    }
                    return new ItemStack(item, 1, block.damageDropped(state));
                })
                .filter(stack -> !stack.isEmpty())
                .toArray(ItemStack[]::new));
    }

    /**
     * Canonical placement entry. The operation in the evaluation context
     * distinguishes creative and survival construction.
     */
    default boolean placeBlock(@NotNull StructureEvaluationContext<T> context,
                               @NotNull EntityPlayer player, boolean skipHatches) {
        return false;
    }

    /**
     * Survival construction entry point. This mirrors StructureLib's richer
     * placement result contract while keeping the old boolean creative placement
     * hook available for existing elements.
     */
    @NotNull
    default PlaceResult survivalPlaceBlock(@NotNull StructureEvaluationContext<T> context,
                                           @NotNull ItemStack trigger,
                                           @NotNull AutoPlaceEnvironment env,
                                           boolean skipHatches) {
        if (context.probe(this::check)) {
            return PlaceResult.SKIP;
        }

        BlocksToPlace blocksToPlace = context.probeValue(probeContext ->
                getBlocksToPlace(probeContext, trigger, env));
        if (blocksToPlace == null) {
            return PlaceResult.REJECT_CONTINUE;
        }

        IItemSource source = env.getSource();
        EntityPlayer actor = env.getActor();
        if (source == null || actor == null) {
            return PlaceResult.REJECT_CONTINUE;
        }

        if (blocksToPlace.getStacks() == null) {
            ItemStack taken = source.takeOne(blocksToPlace.getPredicate(), true);
            if (taken.isEmpty()) {
                return PlaceResult.REJECT;
            }
            if (!placeBlock(context, actor, skipHatches)) {
                return PlaceResult.REJECT;
            }
            source.takeOne(blocksToPlace.getPredicate(), false);
            return PlaceResult.ACCEPT;
        }

        for (ItemStack stack : blocksToPlace.getStacks()) {
            if (stack.isEmpty()) continue;
            ItemStack one = stack.copy();
            one.setCount(1);
            if (!source.takeOne(one, true)) continue;
            if (!placeBlock(context, actor, skipHatches)) {
                return PlaceResult.REJECT;
            }
            source.takeOne(one, false);
            return PlaceResult.ACCEPT;
        }
        return PlaceResult.REJECT;
    }

    /**
     * Canonical hint entry.
     */
    default void spawnHint(@NotNull StructureEvaluationContext<T> context) {
        spawnHintWithResult(context);
    }

    /**
     * Trigger-aware hint entry with an explicit rendering outcome.
     */
    @NotNull
    default StructureHintRenderResult spawnHintWithResult(@NotNull StructureEvaluationContext<T> context,
                                                          @NotNull ItemStack trigger) {
        return spawnHintWithResult(context);
    }

    /**
     * Canonical hint entry with an explicit rendering outcome.
     */
    @NotNull
    default StructureHintRenderResult spawnHintWithResult(@NotNull StructureEvaluationContext<T> context) {
        return StructureHintRenderResult.skipped(StructureHintRenderResult.Source.CONTEXT);
    }

    /**
     * Human-readable accepted block descriptions for diagnostics.
     */
    @Nullable
    default List<String> getDescription(@Nullable T context) {
        return null;
    }

    /**
     * Register deferred requirements for this element before the current cell is
     * matched. Runtime side effects still belong in {@link #check(StructureEvaluationContext)}.
     */
    default void collectRequirements(@NotNull StructureEvaluationContext<T> context) {}

    /**
     * Compile this declaration to the immutable element executed by templates.
     */
    @NotNull
    default CompiledStructureElement<T> compile() {
        if (usesLegacyPredicateRuntime()) {
            TraceabilityPredicate predicate = toPredicate();
            if (predicate == null) {
                throw new IllegalStateException(
                        getClass().getName() + " requested legacy predicate runtime without a predicate");
            }
            return (CompiledStructureElement<T>) CompiledStructureElement.legacy(predicate);
        }
        return CompiledStructureElement.compile(this);
    }

    /** Minimum global count for this element (0 = no minimum). */
    default int getMinGlobalCount() {
        return 0;
    }

    /** Maximum global count for this element (-1 = no maximum). */
    default int getMaxGlobalCount() {
        return -1;
    }

    /** Minimum per-layer count for this element (0 = no minimum). */
    default int getMinLayerCount() {
        return 0;
    }

    /** Maximum per-layer count for this element (-1 = no maximum). */
    default int getMaxLayerCount() {
        return -1;
    }

    /** Whether this element marks the controller center position. */
    default boolean isCenter() {
        return false;
    }

    /** Add tooltip lines for this element. */
    default void addTooltip(List<String> tooltip) {}

    /**
     * Direct tooltip entry for preview/projector/tooling surfaces.
     *
     * <p>This is the preferred replacement for attaching tooltip text through a
     * legacy predicate view. The default delegates to the historical
     * {@link #addTooltip(List)} hook for source compatibility.
     */
    default void addPreviewTooltip(@NotNull List<String> tooltip) {
        addTooltip(tooltip);
    }

    /**
     * Register this element into a {@link PieceTemplateCompiler} under the
     * given symbol.
     *
     * @param symbol    the single-character symbol this element was bound to
     * @param compiler  the target template compiler (in build state)
     */
    default void applyTo(@NotNull String symbol, @NotNull PieceTemplateCompiler compiler) {
        compiler.whereElement(symbol, this);
    }

    /**
     * Whether this element still needs to execute through a
     * {@link gregtech.api.pattern.element.impl.LegacyElement}. New elements
     * should leave this false and implement {@link #check(StructureEvaluationContext)}
     * directly. This hook exists only for migration cases whose matching still
     * depends on legacy predicate side effects.
     */
    @ApiStatus.Internal
    default boolean usesLegacyPredicateRuntime() {
        return false;
    }

    /**
     * Optional legacy predicate view for old callers and preview/diagnostic
     * surfaces. It is not required for new elements and is not used by the
     * element runtime unless {@link #usesLegacyPredicateRuntime()} opts in.
     */
    @Nullable
    @ApiStatus.Obsolete
    default TraceabilityPredicate toPredicate() {
        return null;
    }

    default IStructureElementNoPlacement<T> noPlacement() {
        return new IStructureElementNoPlacement<T>() {
            @Override
            public boolean check(@NotNull StructureEvaluationContext<T> context) {
                return IStructureElement.this.check(context);
            }

            @Override
            public boolean match(@NotNull StructureEvaluationContext<T> context) {
                return IStructureElement.this.match(context);
            }

            @Override
            public BlockInfo[] getCandidates() {
                return IStructureElement.this.getCandidates();
            }

            @Override
            public BlockInfo[] getCandidates(@NotNull StructureEvaluationContext<T> context) {
                return context.probeValue(probeContext ->
                        IStructureElement.this.getCandidates(probeContext));
            }

            @NotNull
            @Override
            public StructureElementPreview getPreview() {
                return IStructureElement.this.getPreview();
            }

            @NotNull
            @Override
            public StructureElementPreview getPreview(@NotNull StructureEvaluationContext<T> context) {
                return context.probeValue(probeContext ->
                        IStructureElement.this.getPreview(probeContext));
            }

            @NotNull
            @Override
            public StructureHintRenderResult spawnHintWithResult(@NotNull StructureEvaluationContext<T> context,
                                                                 @NotNull ItemStack trigger) {
                return context.probeValue(probeContext ->
                        IStructureElement.this.spawnHintWithResult(probeContext, trigger));
            }

            @Override
            public void spawnHint(@NotNull StructureEvaluationContext<T> context) {
                spawnHintWithResult(context);
            }

            @NotNull
            @Override
            public StructureHintRenderResult spawnHintWithResult(@NotNull StructureEvaluationContext<T> context) {
                return context.probeValue(IStructureElement.this::spawnHintWithResult);
            }

            @Nullable
            @Override
            public List<String> getDescription(@Nullable T context) {
                return IStructureElement.this.getDescription(context);
            }

            @Override
            public void addPreviewTooltip(@NotNull List<String> tooltip) {
                IStructureElement.this.addPreviewTooltip(tooltip);
            }

            @Override
            public void collectRequirements(@NotNull StructureEvaluationContext<T> context) {
                IStructureElement.this.collectRequirements(context);
            }

            @NotNull
            @Override
            public Set<StructureElementCapability> getCapabilities() {
                return StructureElementCapability.withoutPlacement(
                        IStructureElement.this.getCapabilities());
            }

            @Override
            public boolean usesLegacyPredicateRuntime() {
                return IStructureElement.this.usesLegacyPredicateRuntime();
            }

            @Nullable
            @Override
            public TraceabilityPredicate toPredicate() {
                return IStructureElement.this.toPredicate();
            }

            @NotNull
            @Override
            public StructureIncrementalSupport getIncrementalSupport() {
                return IStructureElement.this.getIncrementalSupport();
            }

            @NotNull
            @Override
            public Set<StructureDependency> getDependencies() {
                Set<StructureDependency> dependencies = IStructureElement.this.getDependencies();
                if (dependencies.isEmpty()) {
                    return Collections.emptySet();
                }
                return Collections.unmodifiableSet(new LinkedHashSet<>(dependencies));
            }
        };
    }

    enum PlaceResult {
        /** The position is already valid; no placement needed. */
        SKIP,
        /** This element tried and failed; callers should usually surface an error. */
        REJECT,
        /** This element cannot handle placement but another fallback may. */
        REJECT_CONTINUE,
        /** Autoplace should pause and retry next tick/pass. */
        STOP,
        /** One block was placed successfully. */
        ACCEPT,
        /** One block was placed successfully and autoplace should pause. */
        ACCEPT_STOP
    }

    final class BlocksToPlace {

        public static final BlocksToPlace EMPTY = create();

        private final Predicate<ItemStack> predicate;

        @Nullable
        private final Iterable<ItemStack> stacks;

        public static BlocksToPlace create(ItemStack... stacks) {
            if (stacks == null || stacks.length == 0) {
                return new BlocksToPlace(stack -> false, Collections.emptyList());
            }
            return create(Arrays.asList(stacks));
        }

        public static BlocksToPlace create(@NotNull Iterable<ItemStack> stacks) {
            return new BlocksToPlace(stack -> {
                if (stack == null || stack.isEmpty()) {
                    return false;
                }
                for (ItemStack candidate : stacks) {
                    if (itemStacksEqual(candidate, stack)) {
                        return true;
                    }
                }
                return false;
            }, stacks);
        }

        public static BlocksToPlace create(@NotNull Predicate<ItemStack> predicate) {
            return new BlocksToPlace(predicate, null);
        }

        private BlocksToPlace(@NotNull Predicate<ItemStack> predicate,
                              @Nullable Iterable<ItemStack> stacks) {
            this.predicate = predicate;
            this.stacks = stacks;
        }

        @NotNull
        public Predicate<ItemStack> getPredicate() {
            return predicate;
        }

        @Nullable
        public Iterable<ItemStack> getStacks() {
            return stacks;
        }

        private static boolean itemStacksEqual(@Nullable ItemStack expected, @Nullable ItemStack actual) {
            if (expected == null || expected.isEmpty() || actual == null || actual.isEmpty()) {
                return false;
            }
            return ItemStack.areItemsEqual(expected, actual)
                    && ItemStack.areItemStackTagsEqual(expected, actual);
        }
    }

    static Consumer<ITextComponent> playerChatter(@Nullable EntityPlayer player) {
        if (player == null) {
            return component -> {};
        }
        return component -> player.sendMessage(component == null ? new TextComponentString("") : component);
    }
}
