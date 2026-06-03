package gregtech.api.pattern.element;

import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.OffsetMode;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.SoftReferenceHolder;
import gregtech.api.pattern.TemplatePool;
import gregtech.api.util.RelativeDirection;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Top-level structure definition (immutable template).
 * Contains a list of piece entries, each with its pattern, offset, and optional repeat configuration.
 * Compiled products are cached via {@link TemplatePool} soft references.
 *
 * <p>Usage:
 * <pre>{@code
 * StructureDefinition def = StructureDefinition.getOrBuild("gregtech:my_machine", key ->
 *     StructureDefinition.builder(RIGHT, UP, BACK)
 *         .keyHint(key)
 *         .piece("base", "XXX", "X#X", "XXX")
 *             .where('X', block(casingState))
 *             .where('#', air())
 *         .repeatableY("layer", 1, 11, "height", "XXX", "X#X", "XXX")
 *             .where('X', block(casingState))
 *             .where('#', air())
 *         .build());
 * }</pre>
 */
public final class StructureDefinition {

    private final RelativeDirection[] structureDir;
    private final List<PieceEntry> pieceEntries;

    // Compiled products: cached via TemplatePool soft references
    private final SoftReferenceHolder<MultiPiecePattern> compiledPattern;
    private final SoftReferenceHolder<BlockPos[]> maxRepeatAABB;
    private final boolean singlePiece;

    // Key hint for TemplatePool registration
    private final String keyHint;

    private StructureDefinition(Builder b) {
        this.structureDir = new RelativeDirection[]{b.charDir, b.stringDir, b.aisleDir};
        this.pieceEntries = Collections.unmodifiableList(new ArrayList<>(b.pieceEntries));
        this.singlePiece = pieceEntries.size() == 1 && !pieceEntries.get(0).piece.isRepeatable();
        this.keyHint = b.keyHint != null ? b.keyHint : "sd:" + System.identityHashCode(this);

        this.compiledPattern = TemplatePool.getInstance()
                .registerGeneric("sd-compiled:" + keyHint, this::doCompile);
        this.maxRepeatAABB = TemplatePool.getInstance()
                .registerGeneric("sd-aabb:" + keyHint, this::doComputeAABB);
    }

    /**
     * Create a new check state from this definition.
     * Each check operation should use its own state instance.
     */
    @NotNull
    public StructureCheckState createState() {
        return new StructureCheckState(this);
    }

    /**
     * Convenience: synchronous check.
     */
    public boolean check(@NotNull World world, @NotNull BlockPos controllerPos,
                         @NotNull EnumFacing front, @NotNull EnumFacing up, boolean flipped,
                         @Nullable PatternMatchContext context) {
        return createState().check(world, controllerPos, front, up, flipped, context).success;
    }

    /** Get the compiled MultiPiecePattern. */
    @NotNull
    public MultiPiecePattern getCompiledPattern() {
        return compiledPattern.get();
    }

    /**
     * Compute the world AABB for the maximum repeat range.
     */
    @NotNull
    public BlockPos[] computeWorldAABB(@NotNull BlockPos center, @NotNull EnumFacing front,
                                       @NotNull EnumFacing up, boolean flipped, int margin) {
        BlockPos[] base = maxRepeatAABB.get();
        return new BlockPos[]{
                base[0].add(-margin, -margin, -margin),
                base[1].add(margin, margin, margin)
        };
    }

    /** Whether this definition has exactly one non-repeatable piece. */
    public boolean isSinglePiece() {
        return singlePiece;
    }

    @NotNull
    List<PieceEntry> getPieceEntries() {
        return pieceEntries;
    }

    @NotNull
    RelativeDirection[] getStructureDir() {
        return structureDir;
    }

    /**
     * Get or build a StructureDefinition via TemplatePool, passing the key to the factory.
     * The factory receives the key so it can forward it as a keyHint to
     * {@link DeclarativePatternBuilder#buildStructureDefinition(String)}, avoiding
     * the need to specify the same string twice.
     * The factory must produce an idempotent instance (same result each call).
     */
    @NotNull
    public static StructureDefinition getOrBuild(@NotNull String key,
                                                 @NotNull java.util.function.Function<String, StructureDefinition> factory) {
        return TemplatePool.getInstance().registerStructure(key, () -> factory.apply(key)).get();
    }

    private MultiPiecePattern doCompile() {
        return StructureCompiler.compile(this);
    }

    private BlockPos[] doComputeAABB() {
        return StructureCompiler.computeMaxAABB(this);
    }

    // --- PieceEntry (private static inner class) ---

    static final class PieceEntry {
        final IStructurePiece piece;
        final Vec3i baseOffset;
        final OffsetMode offsetMode;
        @Nullable final BooleanSupplier condition;

        PieceEntry(@NotNull IStructurePiece piece, @NotNull Vec3i baseOffset,
                   @NotNull OffsetMode offsetMode, @Nullable BooleanSupplier condition) {
            this.piece = piece;
            this.baseOffset = baseOffset;
            this.offsetMode = offsetMode;
            this.condition = condition;
        }
    }

    // --- Builder ---

    /**
     * Create a new builder with the specified structure directions.
     *
     * @param charDir   direction for characters within a row (typically RIGHT)
     * @param stringDir direction for rows within an aisle (typically UP)
     * @param aisleDir  direction for aisles (typically BACK)
     */
    @NotNull
    public static Builder builder(@NotNull RelativeDirection charDir,
                                  @NotNull RelativeDirection stringDir,
                                  @NotNull RelativeDirection aisleDir) {
        return new Builder(charDir, stringDir, aisleDir);
    }

    /** Builder for constructing a StructureDefinition. */
    public static final class Builder {
        private final RelativeDirection charDir;
        private final RelativeDirection stringDir;
        private final RelativeDirection aisleDir;
        private final List<PieceEntry> pieceEntries = new ArrayList<>();
        private String keyHint;

        private Builder(RelativeDirection charDir, RelativeDirection stringDir,
                        RelativeDirection aisleDir) {
            this.charDir = charDir;
            this.stringDir = stringDir;
            this.aisleDir = aisleDir;
        }

        /** Set a key hint for TemplatePool registration (optional). */
        @NotNull
        public Builder keyHint(@NotNull String key) {
            this.keyHint = key;
            return this;
        }

        /** Add a fixed piece with flat string rows. */
        @NotNull
        public PieceBuilder piece(@NotNull String name, @NotNull String... flatRows) {
            return piece(name, Vec3i.NULL_VECTOR, flatRows);
        }

        /** Add a fixed piece with explicit offset and flat string rows. */
        @NotNull
        public PieceBuilder piece(@NotNull String name, @NotNull Vec3i offset,
                                  @NotNull String... flatRows) {
            String[][] pattern = new String[1][flatRows.length];
            for (int i = 0; i < flatRows.length; i++) {
                pattern[0][i] = flatRows[i];
            }
            return piece(name, pattern, offset);
        }

        /** Add a fixed piece with full pattern and offset. */
        @NotNull
        public PieceBuilder piece(@NotNull String name, @NotNull String[][] pattern,
                                  @NotNull Vec3i offset) {
            MutablePiece mp = new MutablePiece(name, pattern, offset, OffsetMode.RELATIVE,
                    null, new int[0], new int[0][0], new int[0], null, new int[]{0, 0, 0});
            return new PieceBuilder(this, mp);
        }

        /** Add a piece from an existing FactoryBlockPattern (backward compatibility). */
        @NotNull
        public PieceBuilder pieceFromFactory(@NotNull String name,
                                             @NotNull FactoryBlockPattern factory) {
            BlockPatternTemplate template = factory.buildTemplate();
            MutablePiece mp = new MutablePiece(name, null, Vec3i.NULL_VECTOR,
                    OffsetMode.RELATIVE, null, new int[0], new int[0][0], new int[0], null,
                    new int[]{0, 0, 0});
            mp.legacyTemplate = template;
            return new PieceBuilder(this, mp);
        }

        /** Add a repeatable piece with full pattern and offset. */
        @NotNull
        public RepeatablePieceBuilder repeatablePiece(@NotNull String name,
                                                      @NotNull String[][] pattern,
                                                      @NotNull Vec3i offset) {
            MutablePiece mp = new MutablePiece(name, pattern, offset, OffsetMode.RELATIVE,
                    null, new int[0], new int[0][0], new int[0], null, new int[]{0, 0, 0});
            return new RepeatablePieceBuilder(this, mp);
        }

        /** Add a repeatable piece with flat string rows and explicit offset. */
        @NotNull
        public RepeatablePieceBuilder repeatablePiece(@NotNull String name,
                                                      @NotNull Vec3i offset,
                                                      @NotNull String... flatRows) {
            String[][] pattern = new String[1][flatRows.length];
            for (int i = 0; i < flatRows.length; i++) {
                pattern[0][i] = flatRows[i];
            }
            return repeatablePiece(name, pattern, offset);
        }

        /** Add a repeatable piece along X axis. */
        @NotNull
        public RepeatablePieceBuilder repeatableX(@NotNull String name, int min, int max,
                                                  @Nullable String channel,
                                                  @NotNull String... flatRows) {
            return repeatableAxis(name, 0, min, max, channel, flatRows);
        }

        /** Add a repeatable piece along Y axis. */
        @NotNull
        public RepeatablePieceBuilder repeatableY(@NotNull String name, int min, int max,
                                                  @Nullable String channel,
                                                  @NotNull String... flatRows) {
            return repeatableAxis(name, 1, min, max, channel, flatRows);
        }

        /** Add a repeatable piece along Z axis. */
        @NotNull
        public RepeatablePieceBuilder repeatableZ(@NotNull String name, int min, int max,
                                                  @Nullable String channel,
                                                  @NotNull String... flatRows) {
            return repeatableAxis(name, 2, min, max, channel, flatRows);
        }

        private RepeatablePieceBuilder repeatableAxis(@NotNull String name, int axis,
                                                      int min, int max,
                                                      @Nullable String channel,
                                                      @NotNull String... flatRows) {
            String[][] pattern = new String[1][flatRows.length];
            for (int i = 0; i < flatRows.length; i++) {
                pattern[0][i] = flatRows[i];
            }
            MutablePiece mp = new MutablePiece(name, pattern, Vec3i.NULL_VECTOR,
                    OffsetMode.RELATIVE, null,
                    new int[]{axis}, new int[][]{{min, max}}, new int[]{1},
                    channel != null ? new String[]{channel} : null,
                    new int[]{0, 0, 0});
            return new RepeatablePieceBuilder(this, mp);
        }

        /** Add a conditional piece. */
        @NotNull
        public PieceBuilder conditionalPiece(@NotNull String name, @NotNull String[][] pattern,
                                             @NotNull Vec3i offset,
                                             @NotNull BooleanSupplier condition) {
            MutablePiece mp = new MutablePiece(name, pattern, offset, OffsetMode.RELATIVE,
                    condition, new int[0], new int[0][0], new int[0], null, new int[]{0, 0, 0});
            return new PieceBuilder(this, mp);
        }

        void addPiece(@NotNull MutablePiece mp) {
            pieceEntries.add(new PieceEntry(mp.toIStructurePiece(), mp.baseOffset,
                    mp.offsetMode, mp.condition));
        }

        @NotNull
        public StructureDefinition build() {
            return new StructureDefinition(this);
        }
    }

    // --- PieceBuilder (for fixed pieces) ---

    /** Fluent builder for fixed (non-repeatable) structure pieces. */
    public static final class PieceBuilder {
        private final Builder parent;
        private final MutablePiece piece;

        PieceBuilder(@NotNull Builder parent, @NotNull MutablePiece piece) {
            this.parent = parent;
            this.piece = piece;
        }

        /** Define a symbol mapping. */
        @NotNull
        public PieceBuilder where(char symbol, @NotNull IStructureElement element) {
            piece.symbolMap.put(symbol, element);
            return this;
        }

        /** Set the offset mode. */
        @NotNull
        public PieceBuilder offsetMode(@NotNull OffsetMode mode) {
            piece.offsetMode = mode;
            return this;
        }

        /** Set the center offset. */
        @NotNull
        public PieceBuilder centerOffset(int x, int y, int z) {
            piece.centerOffset = new int[]{x, y, z};
            return this;
        }

        /** Finish this piece and return to the parent builder. */
        @NotNull
        public Builder end() {
            parent.addPiece(piece);
            return parent;
        }

        /** Alias for end() - allows chaining directly in builder() call. */
        @NotNull
        public StructureDefinition build() {
            return end().build();
        }
    }

    // --- RepeatablePieceBuilder ---

    /** Fluent builder for repeatable structure pieces. */
    public static final class RepeatablePieceBuilder {
        private final Builder parent;
        private final MutablePiece piece;

        RepeatablePieceBuilder(@NotNull Builder parent, @NotNull MutablePiece piece) {
            this.parent = parent;
            this.piece = piece;
        }

        /** Define a symbol mapping. */
        @NotNull
        public RepeatablePieceBuilder where(char symbol, @NotNull IStructureElement element) {
            piece.symbolMap.put(symbol, element);
            return this;
        }

        /** Set the repeat axes (0=X, 1=Y, 2=Z). */
        @NotNull
        public RepeatablePieceBuilder repeatAxes(int... axes) {
            piece.repeatAxes = axes;
            // Initialize parallel arrays if not already set
            if (piece.repeatRanges.length != axes.length) {
                piece.repeatRanges = new int[axes.length][2];
            }
            if (piece.stepSizes.length != axes.length) {
                piece.stepSizes = new int[axes.length];
                for (int i = 0; i < axes.length; i++) {
                    piece.stepSizes[i] = 1;
                }
            }
            return this;
        }

        /** Set the repeat ranges (flat: min0, max0, min1, max1, ...). */
        @NotNull
        public RepeatablePieceBuilder repeatRange(int... flatRanges) {
            int count = flatRanges.length / 2;
            piece.repeatRanges = new int[count][2];
            for (int i = 0; i < count; i++) {
                piece.repeatRanges[i][0] = flatRanges[i * 2];
                piece.repeatRanges[i][1] = flatRanges[i * 2 + 1];
            }
            return this;
        }

        /** Set the step sizes for each repeat axis. */
        @NotNull
        public RepeatablePieceBuilder stepSizes(int... steps) {
            piece.stepSizes = steps;
            return this;
        }

        /** Set the channel names for each repeat axis. */
        @NotNull
        public RepeatablePieceBuilder channelNames(@NotNull String... names) {
            piece.repeatChannelNames = names;
            return this;
        }

        /** Set the offset mode. */
        @NotNull
        public RepeatablePieceBuilder offsetMode(@NotNull OffsetMode mode) {
            piece.offsetMode = mode;
            return this;
        }

        /** Set the center offset. */
        @NotNull
        public RepeatablePieceBuilder centerOffset(int x, int y, int z) {
            piece.centerOffset = new int[]{x, y, z};
            return this;
        }

        /** Finish this piece and return to the parent builder. */
        @NotNull
        public Builder end() {
            parent.addPiece(piece);
            return parent;
        }

        /** Alias for end(). */
        @NotNull
        public StructureDefinition build() {
            return end().build();
        }
    }

    // --- MutablePiece (internal helper) ---

    /** Internal mutable piece implementation used during builder construction. */
    static final class MutablePiece implements IStructurePiece {
        final String name;
        @Nullable String[][] pattern;
        final Vec3i baseOffset;
        OffsetMode offsetMode;
        @Nullable BooleanSupplier condition;
        final Map<Character, IStructureElement> symbolMap = new HashMap<>();
        int[] repeatAxes;
        int[][] repeatRanges;
        int[] stepSizes;
        @Nullable String[] repeatChannelNames;
        int[] centerOffset;

        // For pieceFromFactory: stores the pre-built template
        @Nullable BlockPatternTemplate legacyTemplate;

        MutablePiece(String name, @Nullable String[][] pattern, Vec3i baseOffset,
                     OffsetMode offsetMode, @Nullable BooleanSupplier condition,
                     int[] repeatAxes, int[][] repeatRanges, int[] stepSizes,
                     @Nullable String[] repeatChannelNames, int[] centerOffset) {
            this.name = name;
            this.pattern = pattern;
            this.baseOffset = baseOffset;
            this.offsetMode = offsetMode;
            this.condition = condition;
            this.repeatAxes = repeatAxes;
            this.repeatRanges = repeatRanges;
            this.stepSizes = stepSizes;
            this.repeatChannelNames = repeatChannelNames;
            this.centerOffset = centerOffset;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String[][] getPattern() {
            return pattern;
        }

        @Override
        public Map<Character, IStructureElement> getSymbolMap() {
            return symbolMap;
        }

        @Override
        public int[] getRepeatAxes() {
            return repeatAxes;
        }

        @Override
        public int[][] getRepeatRanges() {
            return repeatRanges;
        }

        @Override
        public int[] getStepSizes() {
            return stepSizes;
        }

        @Override
        @Nullable
        public String[] getRepeatChannelNames() {
            return repeatChannelNames;
        }

        @Override
        public int[] getCenterOffset() {
            return centerOffset;
        }

        /** Convert to a standalone IStructurePiece (identity conversion). */
        IStructurePiece toIStructurePiece() {
            return this;
        }
    }
}
