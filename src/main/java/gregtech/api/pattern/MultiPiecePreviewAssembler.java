package gregtech.api.pattern;

import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;

import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a multi-piece structure into canonical preview coordinates.
 *
 * <p>The canonical orientation is SOUTH-facing, unflipped, with NORTH as the
 * controller roll reference. Every piece offset, template cell and external
 * repeat step is transformed into that frame before arrays are allocated.
 */
public final class MultiPiecePreviewAssembler {

    private static final EnumFacing CANONICAL_FRONT = EnumFacing.SOUTH;
    private static final EnumFacing CANONICAL_UPWARDS = EnumFacing.NORTH;

    private MultiPiecePreviewAssembler() {}

    @NotNull
    public static Result assemble(@NotNull MultiPiecePattern pattern,
                                  @NotNull PieceRuntimes runtimes,
                                  @Nullable Map<String, Integer> channelValues) {
        return assemble(pattern, runtimes, channelValues, null);
    }

    @NotNull
    public static Result assemble(@NotNull MultiPiecePattern pattern,
                                  @NotNull PieceRuntimes runtimes,
                                  @Nullable Map<String, Integer> channelValues,
                                  @Nullable MultiblockControllerBase controller) {
        Map<BlockPos, BlockInfo> allBlocks = new HashMap<>();
        Map<BlockPos, TraceabilityPredicate> allPredicates = new HashMap<>();
        Map<String, int[]> pieceRepeats = new HashMap<>();
        Map<String, BlockPos> pieceCenters = new HashMap<>();
        List<PieceResult> pieceResults = new ArrayList<>();
        AbilityPlacementTracker abilityTracker = pattern.createAbilityPlacementTracker();

        for (StructurePiece piece : pattern.getPieceList()) {
            FormedStructureMetadata prior = FormedStructureMetadata.fromCheckResult(
                    new HashMap<>(pieceRepeats), Collections.emptyMap(), new HashMap<>(pieceCenters));
            StructureActivationContext<MultiblockControllerBase> activation =
                    new StructureActivationContext<>(controller, null, BlockPos.ORIGIN, prior, null);
            if (!piece.isActive(activation)) {
                pieceResults.add(PieceResult.empty());
                continue;
            }
            BlockPos pieceCenter = piece.getCenterPos(
                    BlockPos.ORIGIN, CANONICAL_FRONT, CANONICAL_UPWARDS, false, prior);
            PieceTemplate template = piece.getPieceTemplate();
            int[] internalRepetitions = resolveInternalRepetitions(template, channelValues);
            int[] externalRepetitions = resolveExternalRepetitions(piece, channelValues);

            PieceRuntime runtime = runtimes.get(piece);
            if (runtime == null) {
                pieceResults.add(PieceResult.empty());
                continue;
            }

            BlockInfo[][][] preview = runtime.getState().getPreview(internalRepetitions, channelValues);
            Bounds rawBounds = computeRawBounds(template, internalRepetitions);
            BlockPos rawCenter = computeRawCenter(template, internalRepetitions);
            Map<BlockPos, TraceabilityPredicate> basePredicates =
                    buildBasePredicateMap(template, internalRepetitions, rawCenter);
            Map<BlockPos, BlockInfo> pieceBlocks = new HashMap<>();
            forEachExternalRepeat(piece, externalRepetitions, localShift -> {
                BlockPos canonicalShift = RelativeDirection.setActualRelativeOffset(
                        localShift.getX(), localShift.getY(), localShift.getZ(),
                        CANONICAL_FRONT, CANONICAL_UPWARDS, false, template.getStructureDir());

                for (int x = 0; x < preview.length; x++) {
                    for (int y = 0; y < preview[x].length; y++) {
                        for (int z = 0; z < preview[x][y].length; z++) {
                            BlockInfo info = preview[x][y][z];
                            if (info == null || info.getBlockState() == null) continue;
                            BlockPos raw = new BlockPos(
                                    x + rawBounds.minX,
                                    y + rawBounds.minY,
                                    z + rawBounds.minZ);
                            BlockPos baseRelative = raw.subtract(rawCenter);
                            TraceabilityPredicate predicate = basePredicates.get(baseRelative);
                            BlockInfo selected = info;
                            if (!abilityTracker.canPlace(selected)) {
                                selected = findFallback(predicate, abilityTracker);
                            }
                            abilityTracker.record(selected);

                            BlockPos relative = baseRelative.add(canonicalShift);
                            BlockPos global = pieceCenter.add(relative);
                            pieceBlocks.put(relative, selected);
                            allBlocks.put(global, selected);
                        }
                    }
                }

                for (Map.Entry<BlockPos, TraceabilityPredicate> entry : basePredicates.entrySet()) {
                    BlockPos relative = entry.getKey().add(canonicalShift);
                    allPredicates.put(pieceCenter.add(relative), entry.getValue());
                }
            });

            NormalizedShape pieceShape = normalize(pieceBlocks);
            pieceResults.add(new PieceResult(
                    pieceShape.shape,
                    new BlockPos(-pieceShape.minX, -pieceShape.minY, -pieceShape.minZ),
                    prior));

            if (externalRepetitions.length > 0) {
                pieceRepeats.put(piece.getName(), externalRepetitions.clone());
            } else if (internalRepetitions.length > 0) {
                pieceRepeats.put(piece.getName(), internalRepetitions.clone());
            }
            pieceCenters.put(piece.getName(), pieceCenter);
        }

        NormalizedShape combined = normalize(allBlocks);
        Map<BlockPos, TraceabilityPredicate> normalizedPredicates = new HashMap<>();
        for (Map.Entry<BlockPos, TraceabilityPredicate> entry : allPredicates.entrySet()) {
            normalizedPredicates.put(new BlockPos(
                    entry.getKey().getX() - combined.minX,
                    entry.getKey().getY() - combined.minY,
                    entry.getKey().getZ() - combined.minZ), entry.getValue());
        }

        FormedStructureMetadata metadata = FormedStructureMetadata.fromCheckResult(
                pieceRepeats, channelValues == null ? Collections.emptyMap() : channelValues,
                pieceCenters);
        return new Result(combined.shape, normalizedPredicates, pieceResults, metadata);
    }

    /**
     * Resolve a preview piece center in an actual controller coordinate frame.
     * Preview metadata stores canonical centers for array assembly, so only its
     * repeat counts are reused here; centers are rebuilt in the target frame.
     */
    @NotNull
    public static BlockPos resolveWorldPieceCenter(
            @NotNull MultiPiecePattern pattern,
            int oneBasedIndex,
            @NotNull FormedStructureMetadata previewPrior,
            @NotNull BlockPos controllerPos,
            @NotNull EnumFacing front,
            @NotNull EnumFacing upwards,
            boolean flipped) {
        return resolveWorldPieceCenter(pattern, oneBasedIndex, previewPrior, controllerPos,
                front, upwards, flipped, null);
    }

    @NotNull
    public static BlockPos resolveWorldPieceCenter(
            @NotNull MultiPiecePattern pattern,
            int oneBasedIndex,
            @NotNull FormedStructureMetadata previewPrior,
            @NotNull BlockPos controllerPos,
            @NotNull EnumFacing front,
            @NotNull EnumFacing upwards,
            boolean flipped,
            @Nullable MultiblockControllerBase controller) {
        Map<String, int[]> repeats = new HashMap<>();
        Map<String, BlockPos> centers = new HashMap<>();
        List<StructurePiece> pieces = pattern.getPieceList();
        for (int i = 0; i < oneBasedIndex; i++) {
            StructurePiece piece = pieces.get(i);

            FormedStructureMetadata actualPrior = FormedStructureMetadata.fromCheckResult(
                    new HashMap<>(repeats), Collections.emptyMap(), new HashMap<>(centers));
            StructureActivationContext<MultiblockControllerBase> activation =
                    new StructureActivationContext<>(controller, controller == null ? null : controller.getWorld(),
                            controllerPos, actualPrior, null);
            if (!piece.isActive(activation)) continue;
            BlockPos center = piece.getCenterPos(
                    controllerPos, front, upwards, flipped, actualPrior);
            if (i == oneBasedIndex - 1) {
                return center;
            }

            int[] pieceRepeats = previewPrior.getPieceRepeats(piece.getName());
            if (pieceRepeats.length > 0) {
                repeats.put(piece.getName(), pieceRepeats);
            }
            centers.put(piece.getName(), center);
        }
        return controllerPos;
    }

    private static int[] resolveInternalRepetitions(@NotNull PieceTemplate template,
                                                    @Nullable Map<String, Integer> channelValues) {
        BlockPatternTemplate.AisleDef[] aisles = template.getAisles();
        int[] repetitions = new int[aisles.length];
        for (int i = 0; i < aisles.length; i++) {
            BlockPatternTemplate.AisleDef aisle = aisles[i];
            Integer value = aisle.channelName() == null || channelValues == null
                    ? null
                    : channelValues.get(aisle.channelName());
            repetitions[i] = value == null
                    ? aisle.maxRepeat()
                    : MultiblockState.resolveRepetitionValue(
                            value, aisle.minRepeat(), aisle.maxRepeat());
        }
        return repetitions;
    }

    private static int[] resolveExternalRepetitions(@NotNull StructurePiece piece,
                                                    @Nullable Map<String, Integer> channelValues) {
        if (!(piece instanceof RepeatGroupPiece repeatPiece)) {
            return new int[0];
        }
        int[][] ranges = repeatPiece.getRepeatRanges();
        String[] names = repeatPiece.getRepeatChannelNames();
        int[] repetitions = new int[ranges.length];
        for (int i = 0; i < ranges.length; i++) {
            Integer value = names == null || i >= names.length || names[i] == null || channelValues == null
                    ? null
                    : channelValues.get(names[i]);
            repetitions[i] = value == null
                    ? ranges[i][1]
                    : MultiblockState.resolveRepetitionValue(value, ranges[i][0], ranges[i][1]);
        }
        return repetitions;
    }

    private static Bounds computeRawBounds(@NotNull PieceTemplate template, @NotNull int[] repetitions) {
        Bounds bounds = new Bounds();
        int finger = 0;
        for (int aisle = 0; aisle < template.getZLength(); aisle++) {
            for (int repeat = 0; repeat < repetitions[aisle]; repeat++, finger++) {
                for (int y = 0; y < template.getYLength(); y++) {
                    for (int x = 0; x < template.getXLength(); x++) {
                        BlockPos pos = RelativeDirection.setActualRelativeOffset(
                                x, y, finger, CANONICAL_FRONT, CANONICAL_UPWARDS,
                                false, template.getStructureDir());
                        bounds.include(pos);
                    }
                }
            }
        }
        return bounds;
    }

    private static BlockPos computeRawCenter(@NotNull PieceTemplate template, @NotNull int[] repetitions) {
        BlockPatternTemplate.CenterOffset center = template.getCenterOffset();
        int finger = 0;
        for (int i = 0; i < center.z() && i < repetitions.length; i++) {
            finger += repetitions[i];
        }
        return RelativeDirection.setActualRelativeOffset(
                center.x(), center.y(), finger,
                CANONICAL_FRONT, CANONICAL_UPWARDS, false, template.getStructureDir());
    }

    @NotNull
    private static Map<BlockPos, TraceabilityPredicate> buildBasePredicateMap(
            @NotNull PieceTemplate template, @NotNull int[] repetitions, @NotNull BlockPos rawCenter) {
        Map<BlockPos, TraceabilityPredicate> predicates = new HashMap<>();
        TraceabilityPredicate[][][] matches = template.getBlockMatches();
        int finger = 0;
        for (int aisle = 0; aisle < template.getZLength(); aisle++) {
            for (int repeat = 0; repeat < repetitions[aisle]; repeat++, finger++) {
                for (int y = 0; y < template.getYLength(); y++) {
                    for (int x = 0; x < template.getXLength(); x++) {
                        TraceabilityPredicate predicate = matches[aisle][y][x];
                        if (predicate == null || predicate == TraceabilityPredicate.ANY) continue;
                        BlockPos raw = RelativeDirection.setActualRelativeOffset(
                                x, y, finger, CANONICAL_FRONT, CANONICAL_UPWARDS,
                                false, template.getStructureDir());
                        predicates.put(raw.subtract(rawCenter), predicate);
                    }
                }
            }
        }
        return predicates;
    }

    private static void forEachExternalRepeat(@NotNull StructurePiece piece,
                                              @NotNull int[] repetitions,
                                              @NotNull java.util.function.Consumer<BlockPos> consumer) {
        if (!(piece instanceof RepeatGroupPiece repeatPiece)) {
            consumer.accept(BlockPos.ORIGIN);
            return;
        }
        for (int repetition : repetitions) {
            if (repetition <= 0) return;
        }

        int[] axes = repeatPiece.getRepeatAxes();
        int[] steps = repeatPiece.getStepSizes();
        int[] indices = new int[repetitions.length];
        boolean more = true;
        while (more) {
            int[] local = {0, 0, 0};
            for (int i = 0; i < indices.length; i++) {
                local[axes[i]] += steps[i] * indices[i];
            }
            consumer.accept(new BlockPos(local[0], local[1], local[2]));

            more = false;
            for (int i = 0; i < indices.length; i++) {
                indices[i]++;
                if (indices[i] < repetitions[i]) {
                    more = true;
                    break;
                }
                indices[i] = 0;
            }
        }
    }

    @NotNull
    private static BlockInfo findFallback(@Nullable TraceabilityPredicate predicate,
                                          @NotNull AbilityPlacementTracker abilityTracker) {
        if (predicate == null) return BlockInfo.EMPTY;

        BlockInfo allowedHatch = null;
        for (TraceabilityPredicate.SimplePredicate simple : predicate.limited) {
            BlockInfo fallback = findFallback(simple, abilityTracker);
            if (fallback == null) continue;
            if (fallback.getTileEntity() == null) return fallback;
            if (allowedHatch == null) allowedHatch = fallback;
        }
        for (TraceabilityPredicate.SimplePredicate simple : predicate.common) {
            BlockInfo fallback = findFallback(simple, abilityTracker);
            if (fallback == null) continue;
            if (fallback.getTileEntity() == null) return fallback;
            if (allowedHatch == null) allowedHatch = fallback;
        }
        return allowedHatch == null ? BlockInfo.EMPTY : allowedHatch;
    }

    @Nullable
    private static BlockInfo findFallback(@NotNull TraceabilityPredicate.SimplePredicate predicate,
                                          @NotNull AbilityPlacementTracker abilityTracker) {
        if (predicate.candidates == null) return null;
        BlockInfo[] candidates = predicate.candidates.get();
        if (candidates == null) return null;
        for (BlockInfo candidate : candidates) {
            if (candidate != null
                    && candidate.getBlockState().getBlock() != Blocks.AIR
                    && abilityTracker.canPlace(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static NormalizedShape normalize(@NotNull Map<BlockPos, BlockInfo> blocks) {
        if (blocks.isEmpty()) {
            return new NormalizedShape(
                    new MultiblockShapeInfo(new BlockInfo[][][]{{{BlockInfo.EMPTY}}}),
                    0, 0, 0);
        }

        Bounds bounds = new Bounds();
        for (BlockPos pos : blocks.keySet()) {
            bounds.include(pos);
        }
        BlockInfo[][][] array = (BlockInfo[][][]) Array.newInstance(
                BlockInfo.class,
                bounds.maxX - bounds.minX + 1,
                bounds.maxY - bounds.minY + 1,
                bounds.maxZ - bounds.minZ + 1);
        for (int x = 0; x < array.length; x++) {
            for (int y = 0; y < array[x].length; y++) {
                for (int z = 0; z < array[x][y].length; z++) {
                    array[x][y][z] = BlockInfo.EMPTY;
                }
            }
        }
        for (Map.Entry<BlockPos, BlockInfo> entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            array[pos.getX() - bounds.minX]
                    [pos.getY() - bounds.minY]
                    [pos.getZ() - bounds.minZ] = entry.getValue();
        }
        return new NormalizedShape(
                new MultiblockShapeInfo(array), bounds.minX, bounds.minY, bounds.minZ);
    }

    public static final class Result {

        private final MultiblockShapeInfo shape;
        private final Map<BlockPos, TraceabilityPredicate> predicates;
        private final List<PieceResult> pieces;
        private final FormedStructureMetadata metadata;

        private Result(@NotNull MultiblockShapeInfo shape,
                       @NotNull Map<BlockPos, TraceabilityPredicate> predicates,
                       @NotNull List<PieceResult> pieces,
                       @NotNull FormedStructureMetadata metadata) {
            this.shape = shape;
            this.predicates = predicates;
            this.pieces = pieces;
            this.metadata = metadata;
        }

        @NotNull
        public MultiblockShapeInfo getShape() {
            return shape;
        }

        @NotNull
        public Map<BlockPos, TraceabilityPredicate> getPredicates() {
            return predicates;
        }

        @NotNull
        public PieceResult getPiece(int oneBasedIndex) {
            return pieces.get(oneBasedIndex - 1);
        }

        @NotNull
        public FormedStructureMetadata getMetadata() {
            return metadata;
        }
    }

    public static final class PieceResult {

        private final MultiblockShapeInfo shape;
        private final BlockPos center;
        private final FormedStructureMetadata prior;

        private PieceResult(@NotNull MultiblockShapeInfo shape, @NotNull BlockPos center,
                            @NotNull FormedStructureMetadata prior) {
            this.shape = shape;
            this.center = center;
            this.prior = prior;
        }

        private static PieceResult empty() {
            return new PieceResult(
                    new MultiblockShapeInfo(new BlockInfo[][][]{{{BlockInfo.EMPTY}}}),
                    BlockPos.ORIGIN,
                    FormedStructureMetadata.fromCheckResult(
                            Collections.emptyMap(), Collections.emptyMap()));
        }

        @NotNull
        public MultiblockShapeInfo getShape() {
            return shape;
        }

        @NotNull
        public BlockPos getCenter() {
            return center;
        }

        @NotNull
        public FormedStructureMetadata getPrior() {
            return prior;
        }
    }

    private static final class NormalizedShape {

        private final MultiblockShapeInfo shape;
        private final int minX;
        private final int minY;
        private final int minZ;

        private NormalizedShape(MultiblockShapeInfo shape, int minX, int minY, int minZ) {
            this.shape = shape;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
        }
    }

    private static final class Bounds {

        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        private void include(@NotNull BlockPos pos) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
    }
}
