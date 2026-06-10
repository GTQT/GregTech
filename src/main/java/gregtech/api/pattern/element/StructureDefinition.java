package gregtech.api.pattern.element;

import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.OffsetMode;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.PieceTemplate;
import gregtech.api.pattern.RepeatGroupPiece;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.StructureCondition;
import gregtech.api.pattern.StructureSizeDescriptor;
import gregtech.api.pattern.StructureSizeDescriptor.PieceSize;
import gregtech.api.pattern.TemplatePool;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Top-level structure definition (immutable template).
 * Contains a list of piece entries, each with its pattern, offset, and optional repeat configuration.
 * The {@link StructureDefinition} instance itself is cached in {@link TemplatePool} behind a
 * soft reference, so it can be reclaimed under memory pressure and rebuilt lazily.
 *
 * <p>Usage:
 * <pre>{@code
 * StructureDefinition def = StructureDefinition.getOrBuild("gregtech:my_machine", () ->
 *     StructureDefinition.builder(RIGHT, UP, BACK)
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
    private final Map<MultiblockAbility<?>, AbilityLimit> abilityLimits;

    // Compiled products: computed lazily on first access and cached for the
    // lifetime of this SD instance. The SD is itself held in TemplatePool
    // behind a SoftReference, so when the SD is reclaimed by GC the compiled
    // products are released alongside it — which is the desired semantics,
    // since they have no value without the SD that produced them. This
    // removes the need to thread a separate cache keyHint through the API
    // just to register a redundant TemplatePool entry.
    private MultiPiecePattern compiledPattern;
    private StructureSizeDescriptor sizeDescriptor;
    private final boolean singlePiece;

    private StructureDefinition(Builder b) {
        this.structureDir = new RelativeDirection[]{b.charDir, b.stringDir, b.aisleDir};
        this.pieceEntries = Collections.unmodifiableList(new ArrayList<>(b.pieceEntries));
        this.abilityLimits = Collections.unmodifiableMap(new HashMap<>(b.abilityLimits));
        this.singlePiece = pieceEntries.size() == 1 && !pieceEntries.get(0).piece.isRepeatable();
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
                         @NotNull EnumFacing front, @NotNull EnumFacing up, boolean allowsFlip,
                         @Nullable PatternMatchContext context) {
        return createState().check(world, controllerPos, front, up, allowsFlip, context).success;
    }

    /** Get the compiled MultiPiecePattern. Computed lazily and cached. */
    @NotNull
    public MultiPiecePattern getCompiledPattern() {
        MultiPiecePattern local = compiledPattern;
        if (local == null) {
            // Double-checked init is unnecessary here: even if multiple
            // threads race and both compute the result, the outcome is
            // identical and deterministic for a given SD, and one of the
            // computed values will simply be discarded.
            local = StructureCompiler.compile(this);
            compiledPattern = local;
        }
        return local;
    }

    /**
     * Compute the world AABB for the maximum repeat range.
     */
    @NotNull
    public BlockPos[] computeWorldAABB(@NotNull BlockPos center, @NotNull EnumFacing front,
                                       @NotNull EnumFacing up, boolean flipped, int margin) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        Map<String, int[]> maxRepeats = new HashMap<>();
        Map<String, BlockPos> pieceCenters = new HashMap<>();

        for (StructurePiece piece : getCompiledPattern().getPieceList()) {
            FormedStructureMetadata prior = FormedStructureMetadata.fromCheckResult(
                    new HashMap<>(maxRepeats), Collections.emptyMap(), new HashMap<>(pieceCenters));
            BlockPos pieceCenter = piece.getCenterPos(center, front, up, flipped, prior);
            PieceTemplate template = piece.getPieceTemplate();

            if (piece instanceof RepeatGroupPiece repeatPiece) {
                int[] axes = repeatPiece.getRepeatAxes();
                int[][] ranges = repeatPiece.getRepeatRanges();
                int[] steps = repeatPiece.getStepSizes();
                int cornerCount = 1 << axes.length;
                for (int mask = 0; mask < cornerCount; mask++) {
                    int[] local = {0, 0, 0};
                    for (int i = 0; i < axes.length; i++) {
                        int repeatIndex = ((mask & (1 << i)) == 0) ? 0 : ranges[i][1] - 1;
                        local[axes[i]] += steps[i] * repeatIndex;
                    }
                    BlockPos shift = RelativeDirection.setActualRelativeOffset(
                            local[0], local[1], local[2],
                            front, up, flipped, template.getStructureDir());
                    BlockPos[] pieceAabb = template.computeWorldAABB(
                            pieceCenter.add(shift), front, up, flipped, 0);
                    minX = Math.min(minX, pieceAabb[0].getX());
                    minY = Math.min(minY, pieceAabb[0].getY());
                    minZ = Math.min(minZ, pieceAabb[0].getZ());
                    maxX = Math.max(maxX, pieceAabb[1].getX());
                    maxY = Math.max(maxY, pieceAabb[1].getY());
                    maxZ = Math.max(maxZ, pieceAabb[1].getZ());
                }

                int[] repeats = new int[ranges.length];
                for (int i = 0; i < ranges.length; i++) {
                    repeats[i] = ranges[i][1];
                }
                maxRepeats.put(piece.getName(), repeats);
            } else {
                BlockPos[] pieceAabb = template.computeWorldAABB(
                        pieceCenter, front, up, flipped, 0);
                minX = Math.min(minX, pieceAabb[0].getX());
                minY = Math.min(minY, pieceAabb[0].getY());
                minZ = Math.min(minZ, pieceAabb[0].getZ());
                maxX = Math.max(maxX, pieceAabb[1].getX());
                maxY = Math.max(maxY, pieceAabb[1].getY());
                maxZ = Math.max(maxZ, pieceAabb[1].getZ());
            }
            pieceCenters.put(piece.getName(), pieceCenter);
        }

        if (minX == Integer.MAX_VALUE) {
            return new BlockPos[]{
                    center.add(-margin, -margin, -margin),
                    center.add(margin, margin, margin)
            };
        }
        return new BlockPos[]{
                new BlockPos(minX - margin, minY - margin, minZ - margin),
                new BlockPos(maxX + margin, maxY + margin, maxZ + margin)
        };
    }

    /** Whether this definition has exactly one non-repeatable piece. */
    public boolean isSinglePiece() {
        return singlePiece;
    }

    /**
     * Convenience: get the primary piece's template (the 1-piece view).
     * Returns {@code null} if this definition is not a single-piece definition.
     *
     * <p>This bypasses the {@link MultiPiecePattern} wrapping step and compiles
     * the single piece's template directly via
     * {@link StructureCompiler#compilePieceTemplate(IStructurePiece, RelativeDirection[])}.
     * For 1-piece callers (the common case), this avoids one unnecessary
     * {@code ArrayList<StructurePiece>}, one {@code StructurePiece}, and one
     * {@code MultiPiecePattern} allocation per call compared to going through
     * {@link #getCompiledPattern()}.
     *
     * <p>Intended for callers that previously used the legacy
     * {@code BlockPatternTemplate} output (e.g. {@code DeclarativePatternBuilder.buildTemplate()})
     * and need a quick path to retrieve the equivalent template for a 1-piece structure.
     * Multi-piece callers should iterate {@link #getCompiledPattern()} instead.
     */
    @Nullable
    public BlockPatternTemplate getPrimaryTemplate() {
        return getPrimaryTemplate(null);
    }

    /**
     * Get the primary (single-piece) compiled template, optionally with an auto-generated
     * structure description. Returns {@code null} for multi-piece definitions; multi-piece
     * callers should iterate {@link #getCompiledPattern()} instead.
     *
     * @param structureDescription  optional description lines to embed in the template;
     *                               {@code null} or empty means "no description"
     * @return the compiled template, or {@code null} for multi-piece structures
     */
    @Nullable
    public BlockPatternTemplate getPrimaryTemplate(@Nullable List<String> structureDescription) {
        if (!singlePiece) return null;
        return StructureCompiler.compilePieceTemplate(
                getPieceEntries().get(0).piece, getStructureDir(), structureDescription);
    }

    @NotNull
    List<PieceEntry> getPieceEntries() {
        return pieceEntries;
    }

    @NotNull
    Map<MultiblockAbility<?>, AbilityLimit> getAbilityLimits() {
        return abilityLimits;
    }

    @NotNull
    public RelativeDirection[] getStructureDir() {
        return structureDir;
    }

    /**
     * Get a {@link StructureSizeDescriptor} describing the pattern-local footprint of
     * this definition. For a single-piece definition the descriptor contains exactly
     * one entry and (typically) min == max on every axis. For a multi-piece definition
     * the descriptor sums per-piece extents so the result reflects the overall world
     * footprint of the structure.
     *
     * <p>Computation is lazy and cached for the lifetime of this {@code StructureDefinition},
     * which is itself held in {@link TemplatePool} behind a soft reference.
     */
    @NotNull
    public StructureSizeDescriptor getStructureSizeDescriptor() {
        StructureSizeDescriptor local = sizeDescriptor;
        if (local == null) {
            // Trigger compilation first so getCompiledPattern() is non-null
            MultiPiecePattern mpp = getCompiledPattern();
            List<PieceSize> sizes = new ArrayList<>(mpp.getPieceList().size());
            for (StructurePiece piece : mpp.getPieceList()) {
                BlockPatternTemplate tpl = piece.getTemplate();
                int palm = tpl.getXLength();
                int thumb = tpl.getYLength();
                int fingerMin = 0;
                int fingerMax = 0;
                for (BlockPatternTemplate.AisleDef aisle : tpl.getAisles()) {
                    fingerMin += aisle.minRepeat();
                    fingerMax += aisle.maxRepeat();
                }
                sizes.add(new PieceSize(piece.getName(), palm, thumb, fingerMin, fingerMax));
            }
            local = StructureSizeDescriptor.of(sizes);
            sizeDescriptor = local;
        }
        return local;
    }

    /**
     * Get or build a StructureDefinition via TemplatePool.
     * The factory must produce an idempotent instance (same result each call).
     *
     * @param key     the cache key
     * @param factory supplier that builds the StructureDefinition
     * @return the resolved StructureDefinition
     */
    @NotNull
    public static StructureDefinition getOrBuild(@NotNull String key,
                                                 @NotNull Supplier<StructureDefinition> factory) {
        return TemplatePool.getInstance().registerStructure(key, factory).get();
    }

    // --- PieceEntry (private static inner class) ---

    static final class PieceEntry {
        final IStructurePiece piece;
        final Vec3i baseOffset;
        final OffsetMode offsetMode;
        @Nullable final BooleanSupplier condition;
        /**
         * If non-null, this piece's center position is computed dynamically based
         * on the runtime repeat count of the named anchor piece. Used to place
         * pieces that follow a repeatable body (e.g. the "top" piece after a
         * middle "body" piece) without overlapping the body.
         */
        @Nullable final String anchorPieceName;
        /**
         * Per-repeat step in (right, up, back) world coordinates to add on top of
         * the anchor piece's actual repeat count. Computed at compile time from
         * the structure's aisle direction and the anchor's step size.
         */
        final int[] anchorStep;

        PieceEntry(@NotNull IStructurePiece piece, @NotNull Vec3i baseOffset,
                   @NotNull OffsetMode offsetMode, @Nullable BooleanSupplier condition) {
            this(piece, baseOffset, offsetMode, condition, null, new int[]{0, 0, 0});
        }

        PieceEntry(@NotNull IStructurePiece piece, @NotNull Vec3i baseOffset,
                   @NotNull OffsetMode offsetMode, @Nullable BooleanSupplier condition,
                   @Nullable String anchorPieceName, @NotNull int[] anchorStep) {
            this.piece = piece;
            this.baseOffset = baseOffset;
            this.offsetMode = offsetMode;
            this.condition = condition;
            this.anchorPieceName = anchorPieceName;
            this.anchorStep = anchorStep;
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
        private final Map<MultiblockAbility<?>, AbilityLimit> abilityLimits = new HashMap<>();

        private Builder(RelativeDirection charDir, RelativeDirection stringDir,
                        RelativeDirection aisleDir) {
            this.charDir = charDir;
            this.stringDir = stringDir;
            this.aisleDir = aisleDir;
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

        @NotNull
        public <T extends MultiblockControllerBase> PieceBuilder conditionalPieceContextual(
                @NotNull String name, @NotNull String[][] pattern,
                @NotNull Vec3i offset, @NotNull StructureCondition<T> condition) {
            MutablePiece mp = new MutablePiece(name, pattern, offset, OffsetMode.RELATIVE,
                    condition, new int[0], new int[0][0], new int[0], null, new int[]{0, 0, 0});
            return new PieceBuilder(this, mp);
        }

        void addPiece(@NotNull MutablePiece mp) {
            pieceEntries.add(new PieceEntry(mp.toIStructurePiece(), mp.baseOffset,
                    mp.offsetMode, mp.condition, mp.anchorPieceName, mp.anchorStep));
        }

        @NotNull
        public Builder globalAbilityLimit(@NotNull MultiblockAbility<?> ability, int min, int max) {
            if (min < 0 || (max >= 0 && max < min)) {
                throw new IllegalArgumentException("Invalid ability range [" + min + ", " + max + "]");
            }
            abilityLimits.merge(ability, new AbilityLimit(min, max),
                    (left, right) -> new AbilityLimit(
                            left.min + right.min,
                            left.max < 0 || right.max < 0 ? -1 : left.max + right.max));
            return this;
        }

        @NotNull
        public StructureDefinition build() {
            validate();
            return new StructureDefinition(this);
        }

        private void validate() {
            if (pieceEntries.isEmpty()) {
                throw new IllegalStateException("StructureDefinition must contain at least one piece");
            }

            Set<String> seenNames = new HashSet<>();
            for (PieceEntry entry : pieceEntries) {
                IStructurePiece piece = entry.piece;
                String name = piece.getName();
                if (name.isEmpty() || seenNames.contains(name)) {
                    throw new IllegalStateException("Piece names must be non-empty and unique: '" + name + "'");
                }
                if (entry.anchorPieceName != null && !seenNames.contains(entry.anchorPieceName)) {
                    throw new IllegalStateException("Piece '" + name + "' references unresolved anchor '"
                            + entry.anchorPieceName + "'");
                }
                seenNames.add(name);

                int[] axes = piece.getRepeatAxes();
                int[][] ranges = piece.getRepeatRanges();
                int[] steps = piece.getStepSizes();
                if (axes.length != ranges.length || axes.length != steps.length) {
                    throw new IllegalStateException("Piece '" + name
                            + "' repeat axes, ranges and steps must have equal lengths");
                }
                Set<Integer> seenAxes = new HashSet<>();
                for (int i = 0; i < axes.length; i++) {
                    if (axes[i] < 0 || axes[i] > 2 || !seenAxes.add(axes[i])) {
                        throw new IllegalStateException("Piece '" + name + "' has invalid repeat axis " + axes[i]);
                    }
                    if (ranges[i] == null || ranges[i].length != 2
                            || ranges[i][0] < 0 || ranges[i][1] < ranges[i][0]) {
                        throw new IllegalStateException("Piece '" + name + "' has invalid repeat range at index " + i);
                    }
                    if (steps[i] == 0 && ranges[i][1] > 1) {
                        throw new IllegalStateException("Piece '" + name + "' repeats with a zero step at index " + i);
                    }
                }
            }
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

        /**
         * Mark this piece as being positioned after a repeatable anchor piece.
         * At check time, the piece's effective {@code baseOffset} is computed as
         * {@code staticBaseOffset + anchorCount * anchorStep}, where
         * {@code anchorCount} is the runtime repeat count of the named anchor
         * piece and {@code anchorStep} is the per-repeat step in
         * (right, up, back) coordinates.
         *
         * <p>This is the mechanism for placing a fixed "top" piece after a
         * repeatable "body" piece in the middle of a structure, when the body's
         * extent is unknown at compile time.
         *
         * @param anchorName the name of the repeatable piece to follow
         * @param anchorStep per-repeat step in (right, up, back) world coords
         */
        @NotNull
        public PieceBuilder positionedAfterRepeatable(@NotNull String anchorName,
                                                     @NotNull int[] anchorStep) {
            piece.anchorPieceName = anchorName;
            piece.anchorStep = anchorStep.clone();
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

        /**
         * Position this repeatable piece relative to a previously resolved piece.
         */
        @NotNull
        public RepeatablePieceBuilder positionedAfter(@NotNull String anchorName,
                                                      @NotNull int[] anchorStep) {
            if (anchorStep.length != 3) {
                throw new IllegalArgumentException("anchorStep must contain right, up and back components");
            }
            piece.anchorPieceName = anchorName;
            piece.anchorStep = anchorStep.clone();
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

    static final class AbilityLimit {

        final int min;
        final int max;

        AbilityLimit(int min, int max) {
            this.min = min;
            this.max = max;
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

        // Dynamic-anchor fields: see PieceEntry.anchorPieceName for semantics.
        @Nullable String anchorPieceName;
        int[] anchorStep = new int[]{0, 0, 0};

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
