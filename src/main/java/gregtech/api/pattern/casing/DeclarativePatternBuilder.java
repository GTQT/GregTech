package gregtech.api.pattern.casing;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.AbilityGroupLimit;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.Elements;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.pattern.element.impl.CasingElement;
import gregtech.api.pattern.element.impl.HatchElement;
import gregtech.api.pattern.element.impl.TieredCasingElement;
import gregtech.api.util.RelativeDirection;

import net.minecraft.util.math.Vec3i;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A declarative builder for multiblock structure patterns.
 * Provides a higher-level API compared to raw {@link FactoryBlockPattern},
 * with automatic minimum casing count calculation, declarative hatch placement,
 * and tiered casing tracking.
 *
 * <p>Supports both single-piece (legacy) and multi-piece (named) structure definitions.
 * Use {@link #piece(String)} and {@link #repeatablePiece(String, int, int)} to define
 * named pieces. When no named pieces are declared, all aisles belong to a single piece "main".
 *
 * <p>Usage example (multi-piece):
 * <pre>{@code
 * DeclarativePatternBuilder.start(RIGHT, BACK, UP)
 *     .piece("base")
 *         .aisle("YSY", "YYY", "YYY")
 *     .repeatablePiece("body", 1, 11)
 *         .aisle("XXX", "X#X", "XXX")
 *         .withAisleChannel(GTStructureChannels.STRUCTURE_HEIGHT.getName())
 *     .piece("top")
 *         .aisle("XXX", "XXX", "XXX")
 *     .where('S', selfPredicate())
 *     .where('#', air())
 *     .casing('Y', casingDef)
 *         .maintenance()
 *     .casing('X', casingDef)
 *         .maintenance()
 *     .buildStructureDefinition();
 * }</pre>
 *
 * @see FactoryBlockPattern for the traditional builder
 * @see ICasing for casing definitions
 * @see ICasingGroup for tiered casing groups
 */
public class DeclarativePatternBuilder {

    private final RelativeDirection[] structureDir;
    private final List<PieceDef> pieces = new ArrayList<>();
    private final Map<Character, CasingSlotInfo> casingSlots = new HashMap<>();
    private final Map<Character, TieredSlotInfo> tieredSlots = new HashMap<>();
    private final Map<Character, TraceabilityPredicate> rawPredicates = new HashMap<>();
    private final Map<Character, IStructureElement> elementMappings = new HashMap<>();
    private final List<AbilityGroupLimit> abilityGroupLimits = new ArrayList<>();

    private PieceDef currentPiece;
    private boolean multiPieceMode;

    private DeclarativePatternBuilder(RelativeDirection[] dirs) {
        this.structureDir = dirs;
        // Default piece "main" for backward compat
        this.currentPiece = new PieceDef("main", false, 0, 0);
        this.pieces.add(currentPiece);
        this.multiPieceMode = false;
    }

    /**
     * Start building a declarative pattern with default directions (RIGHT, UP, BACK).
     */
    public static DeclarativePatternBuilder start() {
        return new DeclarativePatternBuilder(new RelativeDirection[]{
                RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.BACK});
    }

    /**
     * Start building a declarative pattern with specified directions.
     */
    public static DeclarativePatternBuilder start(
            RelativeDirection charDir,
            RelativeDirection stringDir,
            RelativeDirection aisleDir) {
        return new DeclarativePatternBuilder(new RelativeDirection[]{charDir, stringDir, aisleDir});
    }

    // --- Piece definitions ---

    /**
     * Start a new fixed (non-repeatable) named piece.
     * Subsequent {@link #aisle(String...)} calls will add to this piece.
     */
    public PieceBuilder piece(@NotNull String name) {
        multiPieceMode = true;
        closeCurrentPiece();
        currentPiece = new PieceDef(name, false, 0, 0);
        pieces.add(currentPiece);
        return new PieceBuilder(this, currentPiece);
    }

    /**
     * Start a new repeatable named piece (single-axis, aisle-repeatable style).
     *
     * @param name      the piece name
     * @param minRepeat minimum repetition count
     * @param maxRepeat maximum repetition count
     */
    public PieceBuilder repeatablePiece(@NotNull String name, int minRepeat, int maxRepeat) {
        multiPieceMode = true;
        closeCurrentPiece();
        currentPiece = new PieceDef(name, true, minRepeat, maxRepeat);
        pieces.add(currentPiece);
        return new PieceBuilder(this, currentPiece);
    }

    /**
     * Start a new repeatable named piece (multi-axis, raw pattern style).
     *
     * @param name    the piece name
     * @param pattern the raw pattern (aisles x rows)
     * @param offset  the base offset of this piece
     */
    public MultiAxisPieceBuilder repeatablePiece(@NotNull String name, @NotNull String[][] pattern,
                                                  @NotNull Vec3i offset) {
        multiPieceMode = true;
        closeCurrentPiece();
        currentPiece = new PieceDef(name, true, 0, 0);
        currentPiece.rawPattern = pattern;
        currentPiece.offset = offset;
        pieces.add(currentPiece);
        return new MultiAxisPieceBuilder(this, currentPiece);
    }

    private void closeCurrentPiece() {
        // No-op: the PieceDef is already stored in pieces list.
        // Any subsequent aisle() calls will go to the new current piece.
    }

    // --- Aisle methods (delegate to current piece) ---

    /**
     * Define an aisle for the current piece.
     */
    public DeclarativePatternBuilder aisle(String... aisle) {
        currentPiece.aisles.add(new AisleDef(aisle));
        return this;
    }

    /**
     * @deprecated Use {@link #repeatablePiece(String, int, int)} for multi-piece mode instead,
     *             or {@link #aisle(String...)} for fixed aisles.
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2.10")
    public DeclarativePatternBuilder aisleRepeatable(int minRepeat, int maxRepeat, String... aisle) {
        currentPiece.aisles.add(new AisleDef(aisle));
        return this;
    }

    /**
     * @deprecated Use {@link #repeatablePiece(String, int, int)} with
     *             {@link PieceBuilder#withAisleChannel(String)} instead.
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2.10")
    public DeclarativePatternBuilder withAisleChannel(@NotNull String channelName) {
        if (!currentPiece.aisles.isEmpty()) {
            AisleDef last = currentPiece.aisles.get(currentPiece.aisles.size() - 1);
            last.channelName = channelName;
        }
        return this;
    }

    // --- Standard where (shared across all pieces) ---

    /**
     * Define a character mapping using raw TraceabilityPredicate (shared across all pieces).
     */
    public DeclarativePatternBuilder where(char symbol, TraceabilityPredicate predicate) {
        rawPredicates.put(symbol, predicate);
        return this;
    }

    /**
     * Define a character mapping using IStructureElement (for multi-axis pieces).
     * Takes precedence over {@link #where(char, TraceabilityPredicate)} for the same character.
     */
    public DeclarativePatternBuilder where(char symbol, @NotNull IStructureElement element) {
        elementMappings.put(symbol, element);
        return this;
    }

    // --- Declarative casing methods (shared across all pieces) ---

    /**
     * Define a casing slot. The minimum required count will be automatically calculated as:
     * (total occurrences of this char in all aisles) - (sum of all max hatch counts for this slot).
     */
    public CasingSlot casing(char symbol, @NotNull ICasing casing) {
        CasingSlotInfo info = new CasingSlotInfo(symbol, casing);
        casingSlots.put(symbol, info);
        return new CasingSlot(this, info);
    }

    /**
     * Define a tiered casing slot. Automatically tracks tier uniformity through PatternMatchContext.
     */
    public TieredCasingSlot tieredCasing(char symbol, @NotNull ICasingGroup group) {
        TieredSlotInfo info = new TieredSlotInfo(symbol, group);
        tieredSlots.put(symbol, info);
        return new TieredCasingSlot(this, info);
    }

    public DeclarativePatternBuilder abilityGroup(@NotNull MultiblockAbility<?> displayAbility,
                                                  int minCount,
                                                  int maxCount,
                                                  @NotNull MultiblockAbility<?>... abilities) {
        abilityGroupLimits.add(new AbilityGroupLimit(
                displayAbility, minCount, maxCount, Arrays.asList(abilities)));
        return this;
    }

    // --- Build methods ---

    /**
     * Build the structure template (1-piece view of the underlying StructureDefinition).
     *
     * <p>This is a convenience entry point for machines that use a single structure piece
     * (the common case). It internally delegates to {@link #buildStructureDefinition()}
     * and extracts the primary piece's template.
     *
     * <p>Use {@link #buildStructureDefinition()} directly if you need access to
     * named pieces, conditional pieces, or multi-piece composition.
     */
    public BlockPatternTemplate buildTemplate() {
        // Compute description lines first so we can pass them into the template constructor
        // (the template is immutable and no longer has a description setter).
        List<String> description = computeStructureDescription();
        BlockPatternTemplate template = buildStructureDefinition().getPrimaryTemplate(description);
        if (template == null) {
            // Multi-piece definitions cannot be represented as a single BlockPatternTemplate.
            // Use buildStructureDefinition() to access the full multi-piece structure.
            throw new IllegalStateException(
                    "buildTemplate() requires a single-piece structure; "
                            + "use buildStructureDefinition() for multi-piece structures");
        }
        return template;
    }

    /**
     * Build a StructureDefinition from this declarative pattern.
     * When named pieces are declared via {@link #piece(String)} / {@link #repeatablePiece(String, int, int)},
     * each piece is registered as a named entry. Otherwise a single piece "main" is created.
     */
    public StructureDefinition buildStructureDefinition() {
        StructureDefinition.Builder builder = StructureDefinition.builder(
                structureDir[0], structureDir[1], structureDir[2]);

        List<PieceDef> activePieces = new ArrayList<>();
        for (PieceDef piece : pieces) {
            if (piece.rawPattern != null || !piece.aisles.isEmpty()) {
                activePieces.add(piece);
            }
        }
        if (activePieces.isEmpty()) {
            throw new IllegalStateException("Structure must contain at least one non-empty piece");
        }

        int centerIndex = findCenterPieceIndex(activePieces);
        PieceDef centerPiece = activePieces.get(centerIndex);
        registerSequencedPiece(builder, centerPiece, null, null, 1);

        String anchorName = centerPiece.name;
        int[] anchorStep = pieceTraversalStep(centerPiece, 1);
        for (int i = centerIndex + 1; i < activePieces.size(); i++) {
            PieceDef piece = activePieces.get(i);
            registerSequencedPiece(builder, piece, anchorName, anchorStep, 1);
            if (piece.rawPattern == null) {
                anchorName = piece.name;
                anchorStep = pieceTraversalStep(piece, 1);
            }
        }

        anchorName = centerPiece.name;
        anchorStep = pieceTraversalStep(centerPiece, -1);
        for (int i = centerIndex - 1; i >= 0; i--) {
            PieceDef piece = activePieces.get(i);
            registerSequencedPiece(builder, piece, anchorName, anchorStep, -1);
            if (piece.rawPattern == null) {
                anchorName = piece.name;
                anchorStep = pieceTraversalStep(piece, -1);
            }
        }

        if (multiPieceMode) {
            for (CasingSlotInfo slot : casingSlots.values()) {
                for (HatchInfo hatch : slot.hatches) {
                    builder.globalAbilityLimit(hatch.ability, hatch.minCount, hatch.maxCount);
                }
            }
        }
        for (AbilityGroupLimit groupLimit : abilityGroupLimits) {
            builder.globalAbilityGroupLimit(
                    groupLimit.getDisplayAbility(), groupLimit.getMin(), groupLimit.getMax(),
                    groupLimit.getAbilities().toArray(new MultiblockAbility<?>[0]));
        }
        return builder.build();
    }

    private int findCenterPieceIndex(@NotNull List<PieceDef> activePieces) {
        for (int i = 0; i < activePieces.size(); i++) {
            PieceDef piece = activePieces.get(i);
            String[][] pattern = piece.rawPattern != null
                    ? piece.rawPattern
                    : flattenAisles(piece.aisles);
            for (String[] aisle : pattern) {
                for (String row : aisle) {
                    for (int c = 0; c < row.length(); c++) {
                        IStructureElement element = resolveCharacterMap(row.charAt(c));
                        if (element != null && element.isCenter()) {
                            return i;
                        }
                    }
                }
            }
        }
        return 0;
    }

    private void registerSequencedPiece(@NotNull StructureDefinition.Builder builder,
                                        @NotNull PieceDef piece,
                                        @Nullable String anchorName,
                                        @Nullable int[] anchorStep,
        int repeatDirection) {
        if (piece.rawPattern != null) {
            // Raw multi-axis pieces already carry an explicit controller-relative
            // offset. Treating them as members of the linear aisle chain would
            // apply both that offset and an implicit predecessor offset.
            registerMultiAxisPiece(builder, piece, null, null);
        } else if (piece.repeatable) {
            convertFactoryToMultiAxisPiece(
                    builder, piece, Vec3i.NULL_VECTOR, anchorName, anchorStep, repeatDirection);
        } else {
            registerFactoryPiece(builder, piece, anchorName, anchorStep, Vec3i.NULL_VECTOR);
        }
    }

    private int[] pieceTraversalStep(@NotNull PieceDef piece, int direction) {
        int depth = piece.rawPattern != null ? piece.rawPattern.length : piece.aisles.size();
        Vec3i unit = aisleUnitVec(structureDir[2]);
        return new int[]{
                unit.getX() * depth * direction,
                unit.getY() * depth * direction,
                unit.getZ() * depth * direction
        };
    }

    /**
     * Convert a structure aisle direction into a unit vector in the (right, up, back)
     * coordinate system used by {@link OffsetMode#RELATIVE} for piece base offsets.
     * Positive values move in the matching (right, up, back) direction.
     */
    private static Vec3i aisleUnitVec(RelativeDirection dir) {
        return switch (dir) {
            case UP -> new Vec3i(0, 1, 0);
            case DOWN -> new Vec3i(0, -1, 0);
            case RIGHT -> new Vec3i(1, 0, 0);
            case LEFT -> new Vec3i(-1, 0, 0);
            case BACK -> new Vec3i(0, 0, 1);
            case FRONT -> new Vec3i(0, 0, -1);
        };
    }

    /**
     * Flatten a list of {@link AisleDef} into a {@code String[][]} pattern matrix.
     * The channel name on each aisle is intentionally not included here; callers
     * that need the channel information (e.g. {@link #convertFactoryToMultiAxisPiece})
     * read it directly from the {@link AisleDef} list.
     */
    @NotNull
    private static String[][] flattenAisles(@NotNull List<AisleDef> aisles) {
        String[][] pattern = new String[aisles.size()][];
        for (int i = 0; i < aisles.size(); i++) {
            pattern[i] = aisles.get(i).pattern;
        }
        return pattern;
    }

    /**
     * Walk every non-null row in {@code pattern}, resolve each character via
     * {@code resolver} (skipping ones already in {@code piece.mappedChars}),
     * and forward the resulting {@link IStructureElement} to {@code target}.
     * This consolidates the identical char-mapping loops that used to live
     * inline in {@link #registerMultiAxisPiece}, {@link #convertFactoryToMultiAxisPiece},
     * and {@link #registerFactoryPiece}.
     */
    private void addCharMappings(@NotNull String[][] pattern,
                                 @NotNull PieceDef piece,
                                 @NotNull Function<Character, IStructureElement> resolver,
                                 @NotNull BiConsumer<Character, IStructureElement> target) {
        for (String[] aisle : pattern) {
            for (String row : aisle) {
                if (row == null) continue;
                for (int i = 0; i < row.length(); i++) {
                    char c = row.charAt(i);
                    if (!piece.mappedChars.contains(c)) {
                        piece.mappedChars.add(c);
                        IStructureElement element = resolver.apply(c);
                        if (element != null) {
                            target.accept(c, element);
                        }
                    }
                }
            }
        }
    }

    /**
     * Register a multi-axis repeatable piece in the StructureDefinition builder.
     * For multi-axis pieces, character mappings must be converted from TraceabilityPredicate
     * to IStructureElement via {@link Elements#legacy(TraceabilityPredicate)}.
     */
    private void registerMultiAxisPiece(@NotNull StructureDefinition.Builder builder,
                                         @NotNull PieceDef piece,
                                         @Nullable String anchorName,
                                         @Nullable int[] anchorStep) {
        StructureDefinition.RepeatablePieceBuilder rpb = builder.repeatablePiece(
                piece.name, piece.rawPattern, piece.offset);

        // Resolve and register every character used in the pattern
        addCharMappings(piece.rawPattern, piece, this::resolveCharacterMap, rpb::where);

        // Configure repeat axes
        if (piece.axes.length > 0) {
            rpb.repeatAxes(piece.axes);
        }
        if (piece.ranges.length > 0) {
            int[] flat = new int[piece.ranges.length * 2];
            for (int i = 0; i < piece.ranges.length; i++) {
                flat[i * 2] = piece.ranges[i][0];
                flat[i * 2 + 1] = piece.ranges[i][1];
            }
            rpb.repeatRange(flat);
        }
        if (piece.steps.length > 0) {
            rpb.stepSizes(piece.steps);
        }
        if (piece.rawChannelNames != null) {
            rpb.channelNames(piece.rawChannelNames);
        }
        if (piece.centerOffset[0] != 0 || piece.centerOffset[1] != 0 || piece.centerOffset[2] != 0) {
            rpb.centerOffset(piece.centerOffset[0], piece.centerOffset[1], piece.centerOffset[2]);
        }
        if (anchorName != null && anchorStep != null) {
            rpb.positionedAfter(anchorName, anchorStep);
        }
        rpb.end();
    }

    /**
     * Resolve a character to an IStructureElement by checking element mappings,
     * raw predicates, casing slots, and tiered slots in order.
     */
    private IStructureElement resolveCharacterMap(char c) {
        // Check IStructureElement mappings first (for multi-axis pieces)
        IStructureElement element = elementMappings.get(c);
        if (element != null) return element;
        // Check raw predicates
        TraceabilityPredicate pred = rawPredicates.get(c);
        if (pred != null) return Elements.legacy(pred);
        // Check casing slots
        CasingSlotInfo casingInfo = casingSlots.get(c);
        if (casingInfo != null) {
            return buildCasingElement(
                    casingInfo,
                    countCharInAllPieces(casingInfo.symbol),
                    false);
        }
        // Check tiered slots
        TieredSlotInfo tieredInfo = tieredSlots.get(c);
        if (tieredInfo != null) {
            String channelName = tieredInfo.channel != null
                    ? tieredInfo.channel.getName() : tieredInfo.group.getTierChannel();
            return new TieredCasingElement(tieredInfo.group, channelName);
        }
        return null;
    }

    /**
     * Convert a factory-based repeatable piece to a multi-axis RepeatGroupPiece.
     * Groups all aisles into a single String[][] pattern so they repeat as a block.
     *
     * @param baseOffset Offset from the controller to the piece's center, in
     *                   (right, up, back) world coordinates. The caller must compute
     *                   this based on the cumulative aisle count of any fixed pieces
     *                   declared before this repeatable body; otherwise the first
     *                   slice of the body overlaps the previous piece.
     */
    private void convertFactoryToMultiAxisPiece(@NotNull StructureDefinition.Builder builder,
                                                 @NotNull PieceDef piece,
                                                 @NotNull Vec3i baseOffset,
                                                 @Nullable String anchorName,
                                                 @Nullable int[] anchorStep,
                                                 int repeatDirection) {
        // Convert all aisles to a String[][] pattern matrix
        String[][] pattern = flattenAisles(piece.aisles);

        StructureDefinition.RepeatablePieceBuilder rpb = builder.repeatablePiece(
                piece.name, pattern, baseOffset);

        // Repeat along the aisle direction (axis 2)
        rpb.repeatAxes(2);
        rpb.repeatRange(piece.minRepeat, piece.maxRepeat);
        // Step equals the intrinsic depth so there is no gap/overlap between repeats
        rpb.stepSizes(piece.aisles.size() * repeatDirection);

        // Build per-piece character → IStructureElement mappings
        addCharMappings(pattern, piece, c -> buildPieceElement(c, piece), rpb::where);

        // Propagate channel name if all aisles agree on one
        String channel = null;
        for (AisleDef ad : piece.aisles) {
            if (ad.channelName != null) {
                if (channel == null) {
                    channel = ad.channelName;
                } else if (!channel.equals(ad.channelName)) {
                    channel = null;
                    break;
                }
            }
        }
        if (channel != null) {
            rpb.channelNames(channel);
        }

        if (piece.centerOffset[0] != 0 || piece.centerOffset[1] != 0 || piece.centerOffset[2] != 0) {
            rpb.centerOffset(piece.centerOffset[0], piece.centerOffset[1], piece.centerOffset[2]);
        }
        if (anchorName != null && anchorStep != null) {
            rpb.positionedAfter(anchorName, anchorStep);
        }

        rpb.end();
    }

    /**
     * Register a factory-style fixed piece without pre-building a legacy template.
     * This lets StructureCompiler use explicit center offsets for pieces without
     * a controller predicate.
     */
    private void registerFactoryPiece(@NotNull StructureDefinition.Builder builder,
                                      @NotNull PieceDef piece) {
        registerFactoryPiece(builder, piece, null, null, null);
    }

    /**
     * Register a fixed piece, optionally anchoring it to a previously declared
     * repeatable body piece. When {@code anchorPieceName} is non-null, the
     * resulting piece is compiled into a {@code DynamicOffsetPiece} whose
     * center position is computed at check time as
     * {@code anchorBaseOffset + anchorCount * anchorStep}. When the anchor
     * parameters are null, the piece is registered as a regular fixed piece
     * (delegates to the no-anchor overload).
     *
     * <p>{@code anchorBaseOffset} seeds the piece's static baseOffset with the
     * body's own offset, so the dynamic formula naturally lands one slice
     * <i>after</i> the body's last slice (rather than one slice inside it).
     */
    private void registerFactoryPiece(@NotNull StructureDefinition.Builder builder,
                                      @NotNull PieceDef piece,
                                      @Nullable String anchorPieceName,
                                      @Nullable int[] anchorStep,
                                      @Nullable Vec3i anchorBaseOffset) {
        String[][] pattern = flattenAisles(piece.aisles);

        // Seed the piece's baseOffset with the anchor's baseOffset so the
        // dynamic formula resolves to the correct position relative to the
        // body. Falls back to NULL_VECTOR when no anchor is present.
        Vec3i baseOffset = anchorBaseOffset != null ? anchorBaseOffset : Vec3i.NULL_VECTOR;
        StructureDefinition.PieceBuilder pb = builder.piece(piece.name, pattern, baseOffset);

        addCharMappings(pattern, piece, c -> buildPieceElement(c, piece), pb::where);

        if (piece.centerOffset[0] != 0 || piece.centerOffset[1] != 0 || piece.centerOffset[2] != 0) {
            pb.centerOffset(piece.centerOffset[0], piece.centerOffset[1], piece.centerOffset[2]);
        }

        if (anchorPieceName != null && anchorStep != null) {
            pb.positionedAfterRepeatable(anchorPieceName, anchorStep);
        }

        pb.end();
    }

    /**
     * Resolve a character to an IStructureElement using per-piece casing counts.
     */
    private IStructureElement buildPieceElement(char c, @NotNull PieceDef piece) {
        // Check IStructureElement mappings first
        IStructureElement element = elementMappings.get(c);
        if (element != null) return element;
        // Check raw predicates
        TraceabilityPredicate pred = rawPredicates.get(c);
        if (pred != null) return Elements.legacy(pred);
        // Check casing slots (per-piece count)
        CasingSlotInfo casingInfo = casingSlots.get(c);
        if (casingInfo != null) {
            return buildCasingElement(
                    casingInfo,
                    countCharInPiece(piece, casingInfo.symbol),
                    multiPieceMode);
        }
        // Check tiered slots
        TieredSlotInfo tieredInfo = tieredSlots.get(c);
        if (tieredInfo != null) {
            String channelName = tieredInfo.channel != null
                    ? tieredInfo.channel.getName() : tieredInfo.group.getTierChannel();
            return new TieredCasingElement(tieredInfo.group, channelName);
        }
        return null;
    }

    /**
     * Build a direct casing element with hatch alternatives.
     */
    private IStructureElement buildCasingElement(@NotNull CasingSlotInfo info,
                                                  int totalCount,
                                                  boolean deferHatchMinimums) {
        int maxHatches = info.hatches.stream().mapToInt(h -> h.maxCount).sum()
                + info.customHatches.stream().mapToInt(h -> h.maxCount).sum();
        int minCasings = Math.max(0, totalCount - maxHatches);

        List<IStructureElement> alternatives = new ArrayList<>();
        alternatives.add(new CasingElement(info.casing, minCasings));
        for (HatchInfo hatch : info.hatches) {
            alternatives.add(new HatchElement(
                    hatch.ability,
                    deferHatchMinimums ? 0 : hatch.minCount,
                    hatch.maxCount,
                    Math.max(1, hatch.minCount)));
        }
        for (CustomHatchInfo customHatch : info.customHatches) {
            alternatives.add(Elements.legacy(customHatch.predicate));
        }
        return alternatives.size() == 1
                ? alternatives.get(0)
                : Elements.chain(alternatives.toArray(new IStructureElement[0]));
    }

    // --- Structure description ---

    /**
     * Build the auto-generated structure description lines for this pattern. Pure function
     * of the current builder state; returns an empty list if there are no entries to describe.
     *
     * <p>The returned list is the canonical list passed to the template constructor.
     * Callers must not mutate it.
     */
    @NotNull
    private List<String> computeStructureDescription() {
        List<String> lines = new ArrayList<>();

        for (CasingSlotInfo info : casingSlots.values()) {
            char symbol = info.symbol;
            int totalCount = countCharInAllPieces(symbol);
            int maxHatches = info.hatches.stream().mapToInt(h -> h.maxCount).sum()
                    + info.customHatches.stream().mapToInt(h -> h.maxCount).sum();
            int minCasings = Math.max(0, totalCount - maxHatches);

            lines.add("casing:" + info.casing.getTranslationKey() + ":" + minCasings + ":" + totalCount);

            for (HatchInfo hatch : info.hatches) {
                lines.add("hatch:" + hatch.ability.toString() + ":" + hatch.minCount + ":" + hatch.maxCount);
            }
        }

        for (TieredSlotInfo info : tieredSlots.values()) {
            lines.add("tiered:" + info.group.getTranslationKey() + ":" + info.group.requiresUniformTier());
            if (info.channel != null) {
                lines.add("channel:" + info.channel.getDefaultTooltip());
            }
        }

        for (AbilityGroupLimit groupLimit : abilityGroupLimits) {
            lines.add("hatch_group:" + groupLimit.getDisplayAbility() + ":"
                    + groupLimit.getMin() + ":" + groupLimit.getMax());
        }

        return lines;
    }

    // --- Internal helpers ---

    private int countCharInAllPieces(char symbol) {
        int count = 0;
        for (PieceDef piece : pieces) {
            count += countCharInPiece(piece, symbol);
        }
        return count;
    }

    private int countCharInPiece(@NotNull PieceDef piece, char symbol) {
        int count = 0;
        if (piece.rawPattern != null) {
            for (String[] aisle : piece.rawPattern) {
                for (String row : aisle) {
                    if (row == null) continue;
                    for (int i = 0; i < row.length(); i++) {
                        if (row.charAt(i) == symbol) count++;
                    }
                }
            }
        }
        for (AisleDef aisleDef : piece.aisles) {
            for (String row : aisleDef.pattern) {
                for (int i = 0; i < row.length(); i++) {
                    if (row.charAt(i) == symbol) count++;
                }
            }
        }
        return count;
    }

    // --- AisleDef (per-aisle metadata) ---

    private static class AisleDef {
        final String[] pattern;
        String channelName;

        AisleDef(String[] pattern) {
            this.pattern = pattern;
        }
    }

    // --- PieceDef (internal data) ---

    private static class PieceDef {
        final String name;

        // Piece-level repeatability (set by repeatablePiece(), applied to all aisles)
        boolean repeatable;
        int minRepeat, maxRepeat;

        final List<AisleDef> aisles = new ArrayList<>();

        // For multi-axis repeatable pieces:
        String[][] rawPattern;
        Vec3i offset;
        int[] axes = new int[0];
        int[][] ranges = new int[0][0];
        int[] steps = new int[0];
        String[] rawChannelNames;
        int[] centerOffset = {0, 0, 0};

        // Characters already mapped for multi-axis piece (avoid duplicates)
        final List<Character> mappedChars = new ArrayList<>();

        PieceDef(String name, boolean repeatable, int minRepeat, int maxRepeat) {
            this.name = name;
            this.repeatable = repeatable;
            this.minRepeat = minRepeat;
            this.maxRepeat = maxRepeat;
        }
    }

    // --- PieceBuilder fluent API ---

    /**
     * Fluent API for defining the aisles within a named piece.
     */
    public static class PieceBuilder {

        private final DeclarativePatternBuilder parent;
        private final PieceDef piece;

        PieceBuilder(DeclarativePatternBuilder parent, PieceDef piece) {
            this.parent = parent;
            this.piece = piece;
        }

        /** Add an aisle to this piece. */
        public PieceBuilder aisle(String... aisle) {
            piece.aisles.add(new AisleDef(aisle));
            return this;
        }

        /** Set the channel name for a repeatable aisle. */
        public PieceBuilder withAisleChannel(@NotNull String channelName) {
            if (!piece.aisles.isEmpty()) {
                AisleDef last = piece.aisles.get(piece.aisles.size() - 1);
                last.channelName = channelName;
            }
            return this;
        }

        /** Set the center offset for this piece. */
        public PieceBuilder centerOffset(int x, int y, int z) {
            piece.centerOffset = new int[]{x, y, z};
            return this;
        }

        /** Finish defining this piece and return to the parent builder. */
        public DeclarativePatternBuilder end() {
            return parent;
        }

        // --- Pass-through methods for seamless chaining ---

        public DeclarativePatternBuilder where(char symbol, TraceabilityPredicate predicate) {
            return parent.where(symbol, predicate);
        }

        public CasingSlot casing(char symbol, @NotNull ICasing casing) {
            return parent.casing(symbol, casing);
        }

        public TieredCasingSlot tieredCasing(char symbol, @NotNull ICasingGroup group) {
            return parent.tieredCasing(symbol, group);
        }

        public DeclarativePatternBuilder abilityGroup(@NotNull MultiblockAbility<?> displayAbility,
                                                      int minCount,
                                                      int maxCount,
                                                      @NotNull MultiblockAbility<?>... abilities) {
            return parent.abilityGroup(displayAbility, minCount, maxCount, abilities);
        }

        public PieceBuilder piece(@NotNull String name) {
            return parent.piece(name);
        }

        public PieceBuilder repeatablePiece(@NotNull String name, int minRepeat, int maxRepeat) {
            return parent.repeatablePiece(name, minRepeat, maxRepeat);
        }

        public MultiAxisPieceBuilder repeatablePiece(@NotNull String name, @NotNull String[][] pattern,
                                                      @NotNull Vec3i offset) {
            return parent.repeatablePiece(name, pattern, offset);
        }

        public BlockPatternTemplate buildTemplate() {
            return parent.buildTemplate();
        }

        public StructureDefinition buildStructureDefinition() {
            return parent.buildStructureDefinition();
        }
    }

    // --- MultiAxisPieceBuilder fluent API ---

    /**
     * Fluent API for configuring a multi-axis repeatable piece.
     */
    public static class MultiAxisPieceBuilder {

        private final DeclarativePatternBuilder parent;
        private final PieceDef piece;

        MultiAxisPieceBuilder(DeclarativePatternBuilder parent, PieceDef piece) {
            this.parent = parent;
            this.piece = piece;
        }

        /** Set the repeat axes (0=X, 1=Y, 2=Z). */
        public MultiAxisPieceBuilder repeatAxes(int... axes) {
            piece.axes = axes;
            if (piece.ranges.length != axes.length) {
                piece.ranges = new int[axes.length][2];
            }
            if (piece.steps.length != axes.length) {
                piece.steps = new int[axes.length];
                for (int i = 0; i < axes.length; i++) {
                    piece.steps[i] = 1;
                }
            }
            return this;
        }

        /** Set the repeat ranges (flat: min0, max0, min1, max1, ...). */
        public MultiAxisPieceBuilder repeatRange(int... flatRanges) {
            int count = flatRanges.length / 2;
            piece.ranges = new int[count][2];
            for (int i = 0; i < count; i++) {
                piece.ranges[i][0] = flatRanges[i * 2];
                piece.ranges[i][1] = flatRanges[i * 2 + 1];
            }
            return this;
        }

        /** Set the step sizes for each repeat axis. */
        public MultiAxisPieceBuilder stepSizes(int... steps) {
            piece.steps = steps;
            return this;
        }

        /** Set the channel names for each repeat axis. */
        public MultiAxisPieceBuilder channelNames(@NotNull String... names) {
            piece.rawChannelNames = names;
            return this;
        }

        /** Set the center offset. */
        public MultiAxisPieceBuilder centerOffset(int x, int y, int z) {
            piece.centerOffset = new int[]{x, y, z};
            return this;
        }

        /** Finish configuring this piece and return to the parent builder. */
        public DeclarativePatternBuilder end() {
            return parent;
        }

        // --- Pass-through methods for seamless chaining ---

        public DeclarativePatternBuilder where(char symbol, TraceabilityPredicate predicate) {
            return parent.where(symbol, predicate);
        }

        public DeclarativePatternBuilder where(char symbol, @NotNull IStructureElement element) {
            return parent.where(symbol, element);
        }

        public CasingSlot casing(char symbol, @NotNull ICasing casing) {
            return parent.casing(symbol, casing);
        }

        public TieredCasingSlot tieredCasing(char symbol, @NotNull ICasingGroup group) {
            return parent.tieredCasing(symbol, group);
        }

        public PieceBuilder piece(@NotNull String name) {
            return parent.piece(name);
        }

        public PieceBuilder repeatablePiece(@NotNull String name, int minRepeat, int maxRepeat) {
            return parent.repeatablePiece(name, minRepeat, maxRepeat);
        }

        public StructureDefinition buildStructureDefinition() {
            return parent.buildStructureDefinition();
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

        public CasingSlot hatch(@NotNull MultiblockAbility<?> ability, int minCount, int maxCount) {
            if (maxCount == 0) return this;
            info.hatches.add(new HatchInfo(ability, minCount, maxCount));
            return this;
        }

        public CasingSlot hatch(@NotNull MultiblockAbility<?> ability, int currentCount) {
            if (currentCount == 0) return this;
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

        public CasingSlot universalEnergyInput(int minCount, int maxPerType) {
            optionalEnergyInput(maxPerType);
            optionalSubstationInput(maxPerType);
            optionalLaserInput(maxPerType);
            builder.abilityGroup(MultiblockAbility.INPUT_ENERGY_GROUP, minCount, -1,
                    MultiblockAbility.INPUT_ENERGY,
                    MultiblockAbility.SUBSTATION_INPUT_ENERGY,
                    MultiblockAbility.INPUT_LASER);
            return this;
        }

        public CasingSlot universalEnergyOutput(int minCount, int maxPerType) {
            optionalEnergyOutput(maxPerType);
            optionalSubstationOutput(maxPerType);
            optionalLaserOutput(maxPerType);
            builder.abilityGroup(MultiblockAbility.OUTPUT_ENERGY_GROUP, minCount, -1,
                    MultiblockAbility.OUTPUT_ENERGY,
                    MultiblockAbility.SUBSTATION_OUTPUT_ENERGY,
                    MultiblockAbility.OUTPUT_LASER);
            return this;
        }

        public CasingSlot energyIO(int minCount, int maxPerType) {
            optionalEnergyInput(maxPerType);
            optionalEnergyOutput(maxPerType);
            int maxTotal = maxPerType < 0 ? -1 : maxPerType * 2;
            builder.abilityGroup(MultiblockAbility.ENERGY_IO_GROUP, minCount, maxTotal,
                    MultiblockAbility.INPUT_ENERGY,
                    MultiblockAbility.OUTPUT_ENERGY);
            return this;
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

        public CasingSlot auto() {
            return muffler()
                    .maintenance()
                    .energyInput(1, 2)
                    .itemInput(1, 4)
                    .itemOutput(1, 4)
                    .fluidInput(1, 2)
                    .fluidOutput(1, 2);
        }

        public CasingSlot auto(boolean isMuffler, boolean isMaintenance, boolean isEnergyInput,
                               boolean isItemInput, boolean isItemOutput, boolean isFluidInput,
                               boolean isFluidOutput) {
            CasingSlot slot = this;
            if (isMuffler) slot = slot.muffler();
            if (isMaintenance) slot = slot.maintenance();
            if (isEnergyInput) slot = slot.energyInput(1, 2);
            if (isItemInput) slot = slot.itemInput(1, 4);
            if (isItemOutput) slot = slot.itemOutput(1, 4);
            if (isFluidInput) slot = slot.fluidInput(1, 2);
            if (isFluidOutput) slot = slot.fluidOutput(1, 2);
            return slot;
        }

        public CasingSlot custom(@NotNull TraceabilityPredicate predicate, int maxCount) {
            info.customHatches.add(new CustomHatchInfo(predicate, maxCount));
            return this;
        }

        public CasingSlot preset(@NotNull IHatchPreset preset) {
            preset.apply(this);
            return this;
        }

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

        public DeclarativePatternBuilder abilityGroup(@NotNull MultiblockAbility<?> displayAbility,
                                                      int minCount,
                                                      int maxCount,
                                                      @NotNull MultiblockAbility<?>... abilities) {
            return builder.abilityGroup(displayAbility, minCount, maxCount, abilities);
        }

        public PieceBuilder piece(@NotNull String name) {
            return builder.piece(name);
        }

        public PieceBuilder repeatablePiece(@NotNull String name, int minRepeat, int maxRepeat) {
            return builder.repeatablePiece(name, minRepeat, maxRepeat);
        }

        public BlockPatternTemplate buildTemplate() {
            return builder.buildTemplate();
        }

        /**
         * Convenience build returning a {@link BlockPattern} wrapper for the compiled
         * primary template. Intended for legacy call sites that still expect
         * {@link BlockPattern} from a {@link FactoryBlockPattern#build()}-style terminal
         * call. Multiblock controllers that have migrated to {@link StructureDefinition}
         * should prefer {@link #buildStructureDefinition()} instead.
         *
         * @return a BlockPattern wrapping the compiled primary template
         */
        public BlockPattern build() {
            return new BlockPattern(buildTemplate());
        }

        public StructureDefinition buildStructureDefinition() {
            return builder.buildStructureDefinition();
        }
    }

    // --- TieredCasingSlot fluent API ---

    public static class TieredCasingSlot {

        private final DeclarativePatternBuilder builder;
        private final TieredSlotInfo info;

        TieredCasingSlot(DeclarativePatternBuilder builder, TieredSlotInfo info) {
            this.builder = builder;
            this.info = info;
        }

        public TieredCasingSlot withChannel(@NotNull StructureChannel channel) {
            info.channel = channel;
            return this;
        }

        public DeclarativePatternBuilder done() {
            return builder;
        }

        // --- Pass-through methods ---

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

        public DeclarativePatternBuilder abilityGroup(@NotNull MultiblockAbility<?> displayAbility,
                                                      int minCount,
                                                      int maxCount,
                                                      @NotNull MultiblockAbility<?>... abilities) {
            return builder.abilityGroup(displayAbility, minCount, maxCount, abilities);
        }

        public PieceBuilder piece(@NotNull String name) {
            return builder.piece(name);
        }

        public PieceBuilder repeatablePiece(@NotNull String name, int minRepeat, int maxRepeat) {
            return builder.repeatablePiece(name, minRepeat, maxRepeat);
        }

        public BlockPatternTemplate buildTemplate() {
            return builder.buildTemplate();
        }

        public StructureDefinition buildStructureDefinition() {
            return builder.buildStructureDefinition();
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

    private static class CustomHatchInfo {

        final TraceabilityPredicate predicate;
        final int maxCount;

        CustomHatchInfo(TraceabilityPredicate predicate, int maxCount) {
            this.predicate = predicate;
            this.maxCount = maxCount;
        }
    }
}
