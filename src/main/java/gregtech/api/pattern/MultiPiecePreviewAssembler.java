package gregtech.api.pattern;

import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
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
    private static final StructureOrientation CANONICAL_PREVIEW_ORIENTATION = StructureOrientation.of(
            CANONICAL_FRONT, CANONICAL_FRONT, CANONICAL_UPWARDS, false, false);

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
                    BlockPos.ORIGIN, CANONICAL_PREVIEW_ORIENTATION, prior);
            PieceTemplate template = piece.getPieceTemplate();
            int[] internalRepetitions = resolveInternalRepetitions(template, channelValues);
            int[] externalRepetitions = resolveExternalRepetitions(piece, channelValues);

            PieceRuntime runtime = runtimes.get(piece);
            if (runtime == null) {
                pieceResults.add(PieceResult.empty());
                continue;
            }

            MultiblockState.PreviewCells preview = runtime.getState().createPreviewCells(
                    internalRepetitions, channelValues, CANONICAL_PREVIEW_ORIENTATION);
            Map<BlockPos, BlockInfo> pieceBlocks = new HashMap<>();
            forEachExternalRepeat(piece, externalRepetitions, localShift -> {
                BlockPos canonicalShift = RelativeDirection.setActualRelativeOffset(
                        localShift.getX(), localShift.getY(), localShift.getZ(),
                        CANONICAL_FRONT, CANONICAL_UPWARDS, false, template.getStructureDir());

                for (Map.Entry<BlockPos, BlockInfo> entry : preview.getBlocks().entrySet()) {
                    BlockInfo info = entry.getValue();
                    if (info == null || info.getBlockState() == null) continue;
                    BlockPos baseRelative = entry.getKey().subtract(preview.getCenter());
                    TraceabilityPredicate predicate = preview.getPredicates().get(entry.getKey());
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

                for (Map.Entry<BlockPos, TraceabilityPredicate> entry : preview.getPredicates().entrySet()) {
                    BlockPos relative = entry.getKey().subtract(preview.getCenter()).add(canonicalShift);
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

        orientPreviewMetaTileEntities(allBlocks);

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
                StructureOrientation.of(front, front, upwards, flipped, false), null);
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
        return resolveWorldPieceCenter(pattern, oneBasedIndex, previewPrior, controllerPos,
                StructureOrientation.of(front, front, upwards, flipped, false), controller);
    }

    @NotNull
    public static BlockPos resolveWorldPieceCenter(
            @NotNull MultiPiecePattern pattern,
            int oneBasedIndex,
            @NotNull FormedStructureMetadata previewPrior,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
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
            BlockPos center = piece.getCenterPos(controllerPos, orientation, actualPrior);
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

    private static void orientPreviewMetaTileEntities(@NotNull Map<BlockPos, BlockInfo> blocks) {
        blocks.forEach((pos, info) -> {
            if (!(info.getTileEntity() instanceof MetaTileEntityHolder holder)) {
                return;
            }
            MetaTileEntity metaTileEntity = holder.getMetaTileEntity();
            if (metaTileEntity == null) {
                return;
            }
            for (EnumFacing facing : RelativeDirection.ALL_FACINGS) {
                if (metaTileEntity.isValidFrontFacing(facing) && !isOccupied(blocks.get(pos.offset(facing)))) {
                    metaTileEntity.setFrontFacing(facing);
                    break;
                }
            }
        });
    }

    private static boolean isOccupied(@Nullable BlockInfo info) {
        return info != null
                && info.getBlockState() != null
                && info.getBlockState().getBlock() != Blocks.AIR;
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
