package gregtech.api.pattern.element;

import gregtech.api.pattern.PieceTemplateCompiler;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.BlockInfo;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
 * for the old (FactoryBlockPattern) path and as an optional compatibility
 * view for old tooling.
 */
public interface IStructureElement<T> {

    /**
     * Canonical runtime match entry. Compiled templates call this method for
     * both live-world and snapshot checks.
     */
    default boolean check(@NotNull StructureEvaluationContext<T> context) {
        World world = context.getWorld();
        if (world == null) {
            throw new IllegalStateException(
                    "Snapshot structure checks require a context-aware structure element");
        }
        return check(world, context.getPos(), context.getLegacyContext());
    }

    /**
     * Check if the block at the given position matches this element.
     *
     * @param world   the world
     * @param pos     the block position
     * @param context the pattern match context for storing match results
     * @return true if the block matches
     */
    boolean check(World world, BlockPos pos, PatternMatchContext context);

    /**
     * Pure advisory check used by hinting and diagnostics. Returning true is always
     * safe and means "unknown"; returning false means this position cannot become
     * valid for the supplied trigger without changing element/channel state.
     */
    default boolean couldBeValid(World world, BlockPos pos, PatternMatchContext context,
                                 @NotNull ItemStack trigger) {
        return true;
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
     * Return candidate item stacks for survival construction. The default maps
     * block candidates to their dropped item form and is intentionally advisory:
     * specialised elements can still accept/place blocks not represented here.
     */
    @Nullable
    default BlocksToPlace getBlocksToPlace(World world, BlockPos pos, PatternMatchContext context,
                                           @NotNull ItemStack trigger,
                                           @NotNull AutoPlaceEnvironment env) {
        BlockInfo[] candidates = getCandidates();
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

    @Nullable
    default BlocksToPlace getBlocksToPlace(@NotNull StructureEvaluationContext<T> context,
                                           @NotNull ItemStack trigger,
                                           @NotNull AutoPlaceEnvironment env) {
        World world = context.getWorld();
        if (world == null) {
            return null;
        }
        return getBlocksToPlace(world, context.getPos(), context.getLegacyContext(), trigger, env);
    }

    /**
     * Place a block at the given position for auto-build.
     *
     * @param world       the world
     * @param pos         the block position
     * @param context     the pattern match context
     * @param player      the player performing the build
     * @param skipHatches if true, skip hatch placement
     * @return true if a block was placed
     */
    boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                       EntityPlayer player, boolean skipHatches);

    /**
     * Canonical placement entry. The operation in the evaluation context
     * distinguishes creative and survival construction.
     */
    default boolean placeBlock(@NotNull StructureEvaluationContext<T> context,
                               @NotNull EntityPlayer player, boolean skipHatches) {
        World world = context.getWorld();
        if (world == null) {
            throw new IllegalStateException("Cannot place a structure element against a snapshot");
        }
        return placeBlock(world, context.getPos(), context.getLegacyContext(), player, skipHatches);
    }

    /**
     * Survival construction entry point. This mirrors StructureLib's richer
     * placement result contract while keeping the old boolean creative placement
     * hook available for existing elements.
     */
    @NotNull
    default PlaceResult survivalPlaceBlock(World world, BlockPos pos, PatternMatchContext context,
                                           @NotNull ItemStack trigger,
                                           @NotNull AutoPlaceEnvironment env,
                                           boolean skipHatches) {
        BlocksToPlace blocksToPlace = getBlocksToPlace(world, pos, context, trigger, env);
        if (blocksToPlace == null) {
            return PlaceResult.REJECT_CONTINUE;
        }
        if (check(world, pos, context)) {
            return PlaceResult.SKIP;
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
            if (!placeBlock(world, pos, context, actor, skipHatches)) {
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
            if (!placeBlock(world, pos, context, actor, skipHatches)) {
                return PlaceResult.REJECT;
            }
            source.takeOne(one, false);
            return PlaceResult.ACCEPT;
        }
        return PlaceResult.REJECT;
    }

    @NotNull
    default PlaceResult survivalPlaceBlock(@NotNull StructureEvaluationContext<T> context,
                                           @NotNull ItemStack trigger,
                                           @NotNull AutoPlaceEnvironment env,
                                           boolean skipHatches) {
        World world = context.getWorld();
        if (world == null) {
            return PlaceResult.REJECT;
        }
        return survivalPlaceBlock(world, context.getPos(), context.getLegacyContext(), trigger, env, skipHatches);
    }

    /**
     * Spawn a structure hint at the given position.
     *
     * @param world the world
     * @param pos   the block position
     */
    void spawnHint(World world, BlockPos pos);

    /**
     * Trigger-aware hint entry. Returns whether this element handled the hint.
     */
    default boolean spawnHint(World world, BlockPos pos, @NotNull ItemStack trigger) {
        spawnHint(world, pos);
        return true;
    }

    /**
     * Canonical hint entry.
     */
    default void spawnHint(@NotNull StructureEvaluationContext<T> context) {
        World world = context.getWorld();
        if (world == null) {
            throw new IllegalStateException("Cannot spawn a structure hint against a snapshot");
        }
        spawnHint(world, context.getPos());
    }

    /**
     * Human-readable accepted block descriptions for diagnostics.
     */
    @Nullable
    default List<String> getDescription(@Nullable T context) {
        return null;
    }

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
    default boolean usesLegacyPredicateRuntime() {
        return false;
    }

    /**
     * Optional legacy predicate view for old callers and preview/diagnostic
     * surfaces. It is not required for new elements and is not used by the
     * element runtime unless {@link #usesLegacyPredicateRuntime()} opts in.
     */
    @Nullable
    default TraceabilityPredicate toPredicate() {
        return null;
    }

    default IStructureElementNoPlacement<T> noPlacement() {
        return new IStructureElementNoPlacement<T>() {
            @Override
            public boolean check(World world, BlockPos pos, PatternMatchContext context) {
                return IStructureElement.this.check(world, pos, context);
            }

            @Override
            public boolean couldBeValid(World world, BlockPos pos, PatternMatchContext context,
                                        @NotNull ItemStack trigger) {
                return IStructureElement.this.couldBeValid(world, pos, context, trigger);
            }

            @Override
            public BlockInfo[] getCandidates() {
                return IStructureElement.this.getCandidates();
            }

            @Override
            public boolean spawnHint(World world, BlockPos pos, @NotNull ItemStack trigger) {
                return IStructureElement.this.spawnHint(world, pos, trigger);
            }

            @Override
            public void spawnHint(World world, BlockPos pos) {
                IStructureElement.this.spawnHint(world, pos);
            }

            @Nullable
            @Override
            public List<String> getDescription(@Nullable T context) {
                return IStructureElement.this.getDescription(context);
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
