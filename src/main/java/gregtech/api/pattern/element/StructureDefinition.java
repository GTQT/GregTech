package gregtech.api.pattern.element;

import gregtech.api.pattern.AbilityGroupLimit;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.OffsetMode;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.PieceTemplate;
import gregtech.api.pattern.RepeatGroupPiece;
import gregtech.api.pattern.SoftReferenceHolder;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.StructureCondition;
import gregtech.api.pattern.StructureDependencyCompiler;
import gregtech.api.pattern.StructureEligibilityPlan;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.StructureRuntimeDetector;
import gregtech.api.pattern.StructureSizeDescriptor;
import gregtech.api.pattern.StructureSizeDescriptor.PieceSize;
import gregtech.api.pattern.TemplatePool;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.RelativeDirection;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
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
public final class StructureDefinition<T extends MultiblockControllerBase> {

    private final RelativeDirection[] structureDir;
    private final List<PieceEntry> pieceEntries;
    private final Map<MultiblockAbility<?>, AbilityLimit> abilityLimits;
    private final List<AbilityGroupLimit> abilityGroupLimits;
    @Nullable
    private final StructureRuntimeDetector<T> runtimeDetector;
    private final List<String> primaryTemplateDescription;
    @Nullable
    private final SoftReferenceHolder<StructureDefinition<T>> delegate;

    // Compiled products: computed lazily on first access and cached for the
    // lifetime of this SD instance. The SD is itself held in TemplatePool
    // behind a SoftReference, so when the SD is reclaimed by GC the compiled
    // products are released alongside it — which is the desired semantics,
    // since they have no value without the SD that produced them. This
    // removes the need to thread a separate cache keyHint through the API
    // just to register a redundant TemplatePool entry.
    private MultiPiecePattern compiledPattern;
    private StructureSizeDescriptor sizeDescriptor;
    private StructureEligibilityPlan eligibilityPlan;
    @Nullable
    private volatile Set<StructureElementCapability> supportedElementCapabilities;
    private final boolean supportsSingleTemplatePath;

    private StructureDefinition(Builder<T> b) {
        this.structureDir = new RelativeDirection[]{b.charDir, b.stringDir, b.aisleDir};
        this.pieceEntries = Collections.unmodifiableList(new ArrayList<>(b.pieceEntries));
        this.abilityLimits = Collections.unmodifiableMap(new HashMap<>(b.abilityLimits));
        this.abilityGroupLimits = Collections.unmodifiableList(new ArrayList<>(b.abilityGroupLimits));
        this.runtimeDetector = b.runtimeDetector;
        this.primaryTemplateDescription = Collections.unmodifiableList(new ArrayList<>(b.primaryTemplateDescription));
        this.delegate = null;
        this.compiledPattern = b.compiledPattern;
        if (b.compiledPattern != null) {
            this.supportsSingleTemplatePath = b.compiledPattern.getPieceCount() == 1
                    && !(b.compiledPattern.getPrimaryPiece() instanceof RepeatGroupPiece);
        } else {
            this.supportsSingleTemplatePath =
                    pieceEntries.size() == 1 && !pieceEntries.get(0).piece.isRepeatable();
        }
    }

    private StructureDefinition(@NotNull SoftReferenceHolder<StructureDefinition<T>> delegate) {
        this.structureDir = new RelativeDirection[0];
        this.pieceEntries = Collections.emptyList();
        this.abilityLimits = Collections.emptyMap();
        this.abilityGroupLimits = Collections.emptyList();
        this.runtimeDetector = null;
        this.primaryTemplateDescription = Collections.emptyList();
        this.delegate = delegate;
        this.supportsSingleTemplatePath = false;
    }

    @NotNull
    private StructureDefinition<T> resolve() {
        return delegate == null ? this : delegate.get();
    }

    /**
     * Create a new check state from this definition.
     * Each check operation should use its own state instance.
     */
    @NotNull
    public StructureCheckState createState() {
        return new StructureCheckState(resolve());
    }

    /**
     * Convenience: synchronous check.
     */
    public boolean check(@NotNull World world, @NotNull BlockPos controllerPos,
                         @NotNull StructureOrientation orientation,
                         @Nullable PatternMatchContext context) {
        return createState().check(world, controllerPos, orientation, context).success;
    }

    /** Get the compiled MultiPiecePattern. Computed lazily and cached. */
    @NotNull
    public MultiPiecePattern getCompiledPattern() {
        if (delegate != null) {
            return delegate.get().getCompiledPattern();
        }
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
     * Dependency graph and eligibility diagnostics for the contribution
     * evaluator path. The plan is computed from the compiled pattern so legacy
     * adapters and prebuilt multi-piece patterns receive the same diagnostics.
     */
    @NotNull
    public StructureEligibilityPlan getEligibilityPlan() {
        if (delegate != null) {
            return delegate.get().getEligibilityPlan();
        }
        StructureEligibilityPlan local = eligibilityPlan;
        if (local == null) {
            local = StructureDependencyCompiler.compile(getCompiledPattern());
            eligibilityPlan = local;
        }
        return local;
    }

    /**
     * Conservatively report whether every compiled cell can execute an
     * operation. Conditional pieces are excluded from snapshot matching until
     * activation conditions have their own explicit capability contract.
     */
    public boolean supportsElementCapability(
            @NotNull StructureElementCapability capability) {
        if (delegate != null) {
            return delegate.get().supportsElementCapability(capability);
        }
        if (runtimeDetector != null && capability == StructureElementCapability.SNAPSHOT_MATCH) {
            return false;
        }
        Set<StructureElementCapability> local = supportedElementCapabilities;
        if (local == null) {
            local = computeSupportedElementCapabilities();
            supportedElementCapabilities = local;
        }
        return local.contains(capability);
    }

    @NotNull
    private Set<StructureElementCapability> computeSupportedElementCapabilities() {
        EnumSet<StructureElementCapability> capabilities =
                EnumSet.allOf(StructureElementCapability.class);
        for (StructurePiece piece : getCompiledPattern().getPieceList()) {
            if (piece.isConditional()) {
                capabilities.remove(StructureElementCapability.SNAPSHOT_MATCH);
            }
            for (IStructureElement<?>[][] layer : piece.getTemplate().getElements()) {
                for (IStructureElement<?>[] row : layer) {
                    for (IStructureElement<?> element : row) {
                        if (element == null) {
                            capabilities.clear();
                            return Collections.emptySet();
                        }
                        capabilities.retainAll(element.getCapabilities());
                    }
                }
            }
        }
        return Collections.unmodifiableSet(capabilities);
    }

    /**
     * Compute the world AABB for the maximum repeat range.
     */
    @NotNull
    public BlockPos[] computeWorldAABB(@NotNull BlockPos center,
                                       @NotNull StructureOrientation orientation,
                                       int margin) {
        if (delegate != null) {
            return delegate.get().computeWorldAABB(center, orientation, margin);
        }
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
            BlockPos pieceCenter = piece.getCenterPos(center, orientation, prior);
            PieceTemplate template = piece.getTemplate();

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
                            orientation.getStructureFront(), orientation.getUp(),
                            orientation.isFlipped(), template.getStructureDir());
                    BlockPos[] pieceAabb = template.computeWorldAABB(
                            pieceCenter.add(shift), orientation, 0);
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
                        pieceCenter, orientation, 0);
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

    /**
     * Whether this definition can use the legacy single-template runtime path.
     *
     * <p>This is intentionally narrower than "contains one declared piece": a
     * single repeatable piece still requires the multi-piece runtime because it
     * compiles to a {@link RepeatGroupPiece}.
     */
    public boolean supportsSingleTemplatePath() {
        if (delegate != null) {
            return delegate.get().supportsSingleTemplatePath();
        }
        return supportsSingleTemplatePath;
    }

    /**
     * Convenience: get the primary piece's template when the definition supports
     * the single-template runtime path.
     * Returns {@code null} for multi-piece and repeatable-piece definitions.
     *
     * <p>This bypasses the {@link MultiPiecePattern} wrapping step and compiles
     * the single piece's template directly via
     * {@link StructureCompiler#compilePieceTemplate(IStructurePiece, RelativeDirection[])}.
     * For eligible 1-piece callers (the common case), this avoids one unnecessary
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
    public PieceTemplate getPrimaryTemplate() {
        if (delegate != null) {
            return delegate.get().getPrimaryTemplate();
        }
        return getPrimaryTemplate(primaryTemplateDescription);
    }

    /**
     * Get the primary single-template compiled form, optionally with an auto-generated
     * structure description. Returns {@code null} for definitions that require the
     * multi-piece runtime; those callers should iterate {@link #getCompiledPattern()} instead.
     *
     * @param structureDescription  optional description lines to embed in the template;
     *                               {@code null} or empty means "no description"
     * @return the compiled template, or {@code null} when the multi-piece runtime is required
     */
    @Nullable
    public PieceTemplate getPrimaryTemplate(@Nullable List<String> structureDescription) {
        if (delegate != null) {
            return delegate.get().getPrimaryTemplate(structureDescription);
        }
        if (!supportsSingleTemplatePath) return null;
        return StructureCompiler.compilePieceTemplate(
                getPieceEntries().get(0).piece, getStructureDir(), structureDescription);
    }

    @NotNull
    List<PieceEntry> getPieceEntries() {
        if (delegate != null) {
            return delegate.get().getPieceEntries();
        }
        return pieceEntries;
    }

    @NotNull
    Map<MultiblockAbility<?>, AbilityLimit> getAbilityLimits() {
        if (delegate != null) {
            return delegate.get().getAbilityLimits();
        }
        return abilityLimits;
    }

    @NotNull
    List<AbilityGroupLimit> getAbilityGroupLimits() {
        if (delegate != null) {
            return delegate.get().getAbilityGroupLimits();
        }
        return abilityGroupLimits;
    }

    @NotNull
    public RelativeDirection[] getStructureDir() {
        if (delegate != null) {
            return delegate.get().getStructureDir();
        }
        return structureDir;
    }

    /**
     * Runtime geometry detector, if this definition declares one.
     */
    @Nullable
    public StructureRuntimeDetector<T> getRuntimeDetector() {
        if (delegate != null) {
            return delegate.get().getRuntimeDetector();
        }
        return runtimeDetector;
    }

    public boolean hasRuntimeDetector() {
        return getRuntimeDetector() != null;
    }

    /**
     * Get a {@link StructureSizeDescriptor} describing the pattern-local footprint of
     * this definition. For a single-piece definition the descriptor contains exactly
     * one entry and (typically) min == max on every axis. For a multi-piece definition
     * the descriptor combines per-piece width/height extents and sums their sequential
     * aisle extents so the result reflects the overall pattern-local footprint.
     *
     * <p>Computation is lazy and cached for the lifetime of this {@code StructureDefinition},
     * which is itself held in {@link TemplatePool} behind a soft reference.
     */
    @NotNull
    public StructureSizeDescriptor getStructureSizeDescriptor() {
        if (delegate != null) {
            return delegate.get().getStructureSizeDescriptor();
        }
        StructureSizeDescriptor local = sizeDescriptor;
        if (local == null) {
            // Trigger compilation first so getCompiledPattern() is non-null
            MultiPiecePattern mpp = getCompiledPattern();
            List<PieceSize> sizes = new ArrayList<>(mpp.getPieceList().size());
            for (StructurePiece piece : mpp.getPieceList()) {
                PieceTemplate tpl = piece.getTemplate();
                int palmMin = tpl.getXLength();
                int palmMax = palmMin;
                int thumbMin = tpl.getYLength();
                int thumbMax = thumbMin;
                int fingerMin = 0;
                int fingerMax = 0;
                for (PieceTemplate.AisleDef aisle : tpl.getAisles()) {
                    fingerMin += aisle.minRepeat();
                    fingerMax += aisle.maxRepeat();
                }
                if (piece instanceof RepeatGroupPiece repeatPiece) {
                    int[] axes = repeatPiece.getRepeatAxes();
                    int[][] ranges = repeatPiece.getRepeatRanges();
                    int[] steps = repeatPiece.getStepSizes();
                    for (int i = 0; i < axes.length; i++) {
                        int minGrowth = Math.abs(steps[i]) * (ranges[i][0] - 1);
                        int maxGrowth = Math.abs(steps[i]) * (ranges[i][1] - 1);
                        switch (axes[i]) {
                            case 0 -> {
                                palmMin += minGrowth;
                                palmMax += maxGrowth;
                            }
                            case 1 -> {
                                thumbMin += minGrowth;
                                thumbMax += maxGrowth;
                            }
                            case 2 -> {
                                fingerMin += minGrowth;
                                fingerMax += maxGrowth;
                            }
                            default -> throw new IllegalStateException(
                                    "Invalid repeat axis " + axes[i] + " in piece '" + piece.getName() + "'");
                        }
                    }
                }
                sizes.add(new PieceSize(
                        piece.getName(), palmMin, palmMax, thumbMin, thumbMax, fingerMin, fingerMax));
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
    public static <T extends MultiblockControllerBase> StructureDefinition<T> getOrBuild(
            @NotNull String key,
            @NotNull Supplier<StructureDefinition<T>> factory) {
        return new StructureDefinition<>(TemplatePool.getInstance().registerStructure(key, factory));
    }

    /**
     * Adapt a canonical {@link PieceTemplate} into a single-piece {@link StructureDefinition}.
     */
    @NotNull
    public static <T extends MultiblockControllerBase> StructureDefinition<T> fromTemplate(
            @NotNull PieceTemplate template) {
        return fromTemplate("main", template);
    }

    /**
     * Adapt a canonical {@link PieceTemplate} into a single-piece {@link StructureDefinition}.
     */
    @NotNull
    public static <T extends MultiblockControllerBase> StructureDefinition<T> fromTemplate(
            @NotNull String pieceName,
            @NotNull PieceTemplate template) {
        RelativeDirection[] dirs = template.getStructureDir();
        return StructureDefinition.<T>builder(dirs[0], dirs[1], dirs[2])
                .pieceFromTemplate(pieceName, template)
                .build();
    }

    /**
     * Adapt a legacy multi-piece pattern into the canonical definition model.
     *
     * <p>The supplied pattern is kept as the compiled product so legacy subclasses
     * and contextual conditions keep their exact runtime behavior.
     */
    @NotNull
    public static <T extends MultiblockControllerBase> StructureDefinition<T> fromMultiPiecePattern(
            @NotNull MultiPiecePattern pattern) {
        RelativeDirection[] dirs = pattern.getPrimaryPiece().getTemplate().getStructureDir();
        return fromMultiPiecePattern(dirs, pattern);
    }

    /**
     * Adapt a legacy multi-piece pattern into the canonical definition model with
     * an explicit direction triple.
     */
    @NotNull
    public static <T extends MultiblockControllerBase> StructureDefinition<T> fromMultiPiecePattern(
            @NotNull RelativeDirection[] structureDir,
            @NotNull MultiPiecePattern pattern) {
        if (structureDir.length != 3) {
            throw new IllegalArgumentException("structureDir must contain exactly 3 directions");
        }
        Builder<T> builder = StructureDefinition.<T>builder(
                structureDir[0], structureDir[1], structureDir[2]);
        for (StructurePiece piece : pattern.getPieceList()) {
            PieceBuilder<T> pieceBuilder = builder.pieceFromTemplate(piece.getName(), piece.getTemplate(),
                    piece.getOffset(), piece.getOffsetMode(), piece.getCondition());
            if (!piece.isToolingVisible()) {
                pieceBuilder.runtimeOnly();
            }
            pieceBuilder.end();
        }
        for (Map.Entry<MultiblockAbility<?>, int[]> entry : pattern.getAbilityLimits().entrySet()) {
            int[] range = entry.getValue();
            builder.globalAbilityLimit(entry.getKey(), range[0], range[1]);
        }
        for (AbilityGroupLimit groupLimit : pattern.getAbilityGroupLimits()) {
            builder.globalAbilityGroupLimit(
                    groupLimit.getDisplayAbility(), groupLimit.getMin(), groupLimit.getMax(),
                    groupLimit.getAbilities().toArray(new MultiblockAbility<?>[0]));
        }
        builder.compiledPattern = pattern;
        return builder.build();
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
    public static <T extends MultiblockControllerBase> Builder<T> builder(
            @NotNull RelativeDirection charDir,
            @NotNull RelativeDirection stringDir,
            @NotNull RelativeDirection aisleDir) {
        return new Builder<>(charDir, stringDir, aisleDir);
    }

    /** Builder for constructing a StructureDefinition. */
    public static final class Builder<T extends MultiblockControllerBase> {
        private final RelativeDirection charDir;
        private final RelativeDirection stringDir;
        private final RelativeDirection aisleDir;
        private final List<PieceEntry> pieceEntries = new ArrayList<>();
        private final Map<MultiblockAbility<?>, AbilityLimit> abilityLimits = new HashMap<>();
        private final List<AbilityGroupLimit> abilityGroupLimits = new ArrayList<>();
        private final List<String> primaryTemplateDescription = new ArrayList<>();
        @Nullable
        private MultiPiecePattern compiledPattern;
        @Nullable
        private StructureRuntimeDetector<T> runtimeDetector;

        private Builder(RelativeDirection charDir, RelativeDirection stringDir,
                        RelativeDirection aisleDir) {
            this.charDir = charDir;
            this.stringDir = stringDir;
            this.aisleDir = aisleDir;
        }

        /** Add a fixed piece with flat string rows. */
        @NotNull
        public PieceBuilder<T> piece(@NotNull String name, @NotNull String... flatRows) {
            return piece(name, Vec3i.NULL_VECTOR, flatRows);
        }

        /** Add a fixed piece with explicit offset and flat string rows. */
        @NotNull
        public PieceBuilder<T> piece(@NotNull String name, @NotNull Vec3i offset,
                                     @NotNull String... flatRows) {
            String[][] pattern = new String[1][flatRows.length];
            for (int i = 0; i < flatRows.length; i++) {
                pattern[0][i] = flatRows[i];
            }
            return piece(name, pattern, offset);
        }

        /** Add a fixed piece with full pattern and offset. */
        @NotNull
        public PieceBuilder<T> piece(@NotNull String name, @NotNull String[][] pattern,
                                     @NotNull Vec3i offset) {
            MutablePiece mp = new MutablePiece(name, pattern, offset, OffsetMode.RELATIVE,
                    null, new int[0], new int[0][0], new int[0], null, new int[]{0, 0, 0});
            return new PieceBuilder<>(this, mp);
        }

        /** Add a piece from an existing canonical PieceTemplate. */
        @NotNull
        public PieceBuilder<T> pieceFromTemplate(@NotNull String name,
                                                 @NotNull PieceTemplate template) {
            return pieceFromTemplate(name, template, Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        }

        /** Add a piece from an existing canonical PieceTemplate with explicit placement. */
        @NotNull
        public PieceBuilder<T> pieceFromTemplate(@NotNull String name,
                                                 @NotNull PieceTemplate template,
                                                 @NotNull Vec3i offset,
                                                 @NotNull OffsetMode offsetMode,
                                                 @Nullable BooleanSupplier condition) {
            MutablePiece mp = new MutablePiece(name, null, Vec3i.NULL_VECTOR,
                    OffsetMode.RELATIVE, null, new int[0], new int[0][0], new int[0], null,
                    new int[]{0, 0, 0});
            mp.baseOffset = offset;
            mp.offsetMode = offsetMode;
            mp.condition = condition;
            mp.template = template;
            return new PieceBuilder<>(this, mp);
        }

        /** Add a repeatable piece with full pattern and offset. */
        @NotNull
        public RepeatablePieceBuilder<T> repeatablePiece(@NotNull String name,
                                                         @NotNull String[][] pattern,
                                                         @NotNull Vec3i offset) {
            MutablePiece mp = new MutablePiece(name, pattern, offset, OffsetMode.RELATIVE,
                    null, new int[0], new int[0][0], new int[0], null, new int[]{0, 0, 0});
            return new RepeatablePieceBuilder<>(this, mp);
        }

        /** Add a repeatable piece with flat string rows and explicit offset. */
        @NotNull
        public RepeatablePieceBuilder<T> repeatablePiece(@NotNull String name,
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
        public RepeatablePieceBuilder<T> repeatableX(@NotNull String name, int min, int max,
                                                     @Nullable String channel,
                                                     @NotNull String... flatRows) {
            return repeatableAxis(name, 0, min, max, channel, flatRows);
        }

        /** Add a repeatable piece along Y axis. */
        @NotNull
        public RepeatablePieceBuilder<T> repeatableY(@NotNull String name, int min, int max,
                                                     @Nullable String channel,
                                                     @NotNull String... flatRows) {
            return repeatableAxis(name, 1, min, max, channel, flatRows);
        }

        /** Add a repeatable piece along Z axis. */
        @NotNull
        public RepeatablePieceBuilder<T> repeatableZ(@NotNull String name, int min, int max,
                                                     @Nullable String channel,
                                                     @NotNull String... flatRows) {
            return repeatableAxis(name, 2, min, max, channel, flatRows);
        }

        private RepeatablePieceBuilder<T> repeatableAxis(@NotNull String name, int axis,
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
            return new RepeatablePieceBuilder<>(this, mp);
        }

        /** Add a conditional piece. */
        @NotNull
        public PieceBuilder<T> conditionalPiece(@NotNull String name, @NotNull String[][] pattern,
                                                @NotNull Vec3i offset,
                                                @NotNull BooleanSupplier condition) {
            MutablePiece mp = new MutablePiece(name, pattern, offset, OffsetMode.RELATIVE,
                    condition, new int[0], new int[0][0], new int[0], null, new int[]{0, 0, 0});
            return new PieceBuilder<>(this, mp);
        }

        @NotNull
        public PieceBuilder<T> conditionalPieceContextual(
                @NotNull String name, @NotNull String[][] pattern,
                @NotNull Vec3i offset, @NotNull StructureCondition<T> condition) {
            MutablePiece mp = new MutablePiece(name, pattern, offset, OffsetMode.RELATIVE,
                    condition, new int[0], new int[0][0], new int[0], null, new int[]{0, 0, 0});
            return new PieceBuilder<>(this, mp);
        }

        void addPiece(@NotNull MutablePiece mp) {
            pieceEntries.add(new PieceEntry(mp.toIStructurePiece(), mp.baseOffset,
                    mp.offsetMode, mp.condition, mp.anchorPieceName, mp.anchorStep));
        }

        @NotNull
        public Builder<T> globalAbilityLimit(@NotNull MultiblockAbility<?> ability, int min, int max) {
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
        public Builder<T> globalAbilityGroupLimit(@NotNull MultiblockAbility<?> displayAbility,
                                                  int min,
                                                  int max,
                                                  @NotNull MultiblockAbility<?>... abilities) {
            abilityGroupLimits.add(new AbilityGroupLimit(
                    displayAbility, min, max, Arrays.asList(abilities)));
            return this;
        }

        /**
         * Declare a stable runtime detector for geometry discovered from world state.
         *
         * <p>Detector definitions use one fixed piece as their contribution and
         * registration identity. Preview/build tooling may still use a separate
         * channel-derived template.
         */
        @NotNull
        public Builder<T> runtimeDetector(@NotNull StructureRuntimeDetector<T> runtimeDetector) {
            this.runtimeDetector = runtimeDetector;
            return this;
        }

        /**
         * Optional description lines embedded when exporting the primary single-piece
         * template through {@link StructureDefinition#getPrimaryTemplate()}.
         */
        @NotNull
        public Builder<T> primaryTemplateDescription(@Nullable List<String> description) {
            primaryTemplateDescription.clear();
            if (description != null) {
                primaryTemplateDescription.addAll(description);
            }
            return this;
        }

        @NotNull
        public StructureDefinition<T> build() {
            validate();
            return new StructureDefinition<>(this);
        }

        private void validate() {
            if (pieceEntries.isEmpty()) {
                throw new IllegalStateException("StructureDefinition must contain at least one piece");
            }
            if (runtimeDetector != null
                    && (pieceEntries.size() != 1 || pieceEntries.get(0).piece.isRepeatable())) {
                throw new IllegalStateException(
                        "Runtime detector definitions require exactly one fixed identity piece");
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
    public static final class PieceBuilder<T extends MultiblockControllerBase> {
        private final Builder<T> parent;
        private final MutablePiece piece;

        PieceBuilder(@NotNull Builder<T> parent, @NotNull MutablePiece piece) {
            this.parent = parent;
            this.piece = piece;
        }

        /** Define a symbol mapping. */
        @NotNull
        public PieceBuilder<T> where(char symbol, @NotNull IStructureElement element) {
            piece.symbolMap.put(symbol, element);
            return this;
        }

        /** Set the offset mode. */
        @NotNull
        public PieceBuilder<T> offsetMode(@NotNull OffsetMode mode) {
            piece.offsetMode = mode;
            return this;
        }

        /** Set the center offset. */
        @NotNull
        public PieceBuilder<T> centerOffset(int x, int y, int z) {
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
        public PieceBuilder<T> positionedAfterRepeatable(@NotNull String anchorName,
                                                         @NotNull int[] anchorStep) {
            piece.anchorPieceName = anchorName;
            piece.anchorStep = anchorStep.clone();
            return this;
        }

        /**
         * Mark this piece as part of runtime validation only.
         *
         * <p>Runtime-only pieces still participate in structure matching,
         * formed metadata, dirty tracking, and validation. User-facing preview,
         * hint, projector, and auto-build tooling hides them so they cannot be
         * constructed as normal structure pieces.
         */
        @NotNull
        public PieceBuilder<T> runtimeOnly() {
            piece.toolingVisible = false;
            return this;
        }

        /** Alias for {@link #runtimeOnly()}. */
        @NotNull
        public PieceBuilder<T> hideFromTooling() {
            return runtimeOnly();
        }

        /** Finish this piece and return to the parent builder. */
        @NotNull
        public Builder<T> end() {
            parent.addPiece(piece);
            return parent;
        }

        /** Alias for end() - allows chaining directly in builder() call. */
        @NotNull
        public StructureDefinition<T> build() {
            return end().build();
        }
    }

    // --- RepeatablePieceBuilder ---

    /** Fluent builder for repeatable structure pieces. */
    public static final class RepeatablePieceBuilder<T extends MultiblockControllerBase> {
        private final Builder<T> parent;
        private final MutablePiece piece;

        RepeatablePieceBuilder(@NotNull Builder<T> parent, @NotNull MutablePiece piece) {
            this.parent = parent;
            this.piece = piece;
        }

        /** Define a symbol mapping. */
        @NotNull
        public RepeatablePieceBuilder<T> where(char symbol, @NotNull IStructureElement element) {
            piece.symbolMap.put(symbol, element);
            return this;
        }

        /** Set the repeat axes (0=X, 1=Y, 2=Z). */
        @NotNull
        public RepeatablePieceBuilder<T> repeatAxes(int... axes) {
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
        public RepeatablePieceBuilder<T> repeatRange(int... flatRanges) {
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
        public RepeatablePieceBuilder<T> stepSizes(int... steps) {
            piece.stepSizes = steps;
            return this;
        }

        /** Set the channel names for each repeat axis. */
        @NotNull
        public RepeatablePieceBuilder<T> channelNames(@NotNull String... names) {
            piece.repeatChannelNames = names;
            return this;
        }

        /** Set the offset mode. */
        @NotNull
        public RepeatablePieceBuilder<T> offsetMode(@NotNull OffsetMode mode) {
            piece.offsetMode = mode;
            return this;
        }

        /** Set the center offset. */
        @NotNull
        public RepeatablePieceBuilder<T> centerOffset(int x, int y, int z) {
            piece.centerOffset = new int[]{x, y, z};
            return this;
        }

        /**
         * Position this repeatable piece relative to a previously resolved piece.
         */
        @NotNull
        public RepeatablePieceBuilder<T> positionedAfter(@NotNull String anchorName,
                                                         @NotNull int[] anchorStep) {
            if (anchorStep.length != 3) {
                throw new IllegalArgumentException("anchorStep must contain right, up and back components");
            }
            piece.anchorPieceName = anchorName;
            piece.anchorStep = anchorStep.clone();
            return this;
        }

        /**
         * Mark this repeatable piece as part of runtime validation only.
         */
        @NotNull
        public RepeatablePieceBuilder<T> runtimeOnly() {
            piece.toolingVisible = false;
            return this;
        }

        /** Alias for {@link #runtimeOnly()}. */
        @NotNull
        public RepeatablePieceBuilder<T> hideFromTooling() {
            return runtimeOnly();
        }

        /** Finish this piece and return to the parent builder. */
        @NotNull
        public Builder<T> end() {
            if (piece.repeatAxes.length == 0) {
                throw new IllegalStateException("Repeatable piece '" + piece.name
                        + "' requires at least one repeat axis; use piece(...) for a fixed piece");
            }
            parent.addPiece(piece);
            return parent;
        }

        /** Alias for end(). */
        @NotNull
        public StructureDefinition<T> build() {
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
        Vec3i baseOffset;
        OffsetMode offsetMode;
        @Nullable BooleanSupplier condition;
        final Map<Character, IStructureElement> symbolMap = new HashMap<>();
        int[] repeatAxes;
        int[][] repeatRanges;
        int[] stepSizes;
        @Nullable String[] repeatChannelNames;
        int[] centerOffset;
        boolean toolingVisible = true;

        // For pieceFromTemplate(PieceTemplate): stores the canonical pre-built template.
        @Nullable PieceTemplate template;

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

        @Override
        public boolean isToolingVisible() {
            return toolingVisible;
        }

        /** Convert to a standalone IStructurePiece (identity conversion). */
        IStructurePiece toIStructurePiece() {
            return this;
        }
    }
}
