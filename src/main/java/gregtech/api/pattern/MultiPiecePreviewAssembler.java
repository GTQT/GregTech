package gregtech.api.pattern;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.FormedStructureMetadata;
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

    public static final int DEFAULT_TOOLING_PIECES = -1;

    private static final EnumFacing CANONICAL_FRONT = EnumFacing.SOUTH;
    private static final EnumFacing CANONICAL_UPWARDS = EnumFacing.NORTH;
    private static final StructureOrientation CANONICAL_PREVIEW_ORIENTATION = StructureOrientation.of(
            CANONICAL_FRONT, CANONICAL_FRONT, CANONICAL_UPWARDS, false, false);

    private MultiPiecePreviewAssembler() {}

    @NotNull
    public static Result assemble(@NotNull MultiPiecePattern pattern,
                                  @NotNull PieceRuntimes runtimes,
                                  @Nullable Map<String, Integer> channelValues) {
        return assemble(pattern, runtimes, channelValues, null, false);
    }

    @NotNull
    public static Result assemble(@NotNull MultiPiecePattern pattern,
                                  @NotNull PieceRuntimes runtimes,
                                  @Nullable Map<String, Integer> channelValues,
                                  @Nullable MultiblockControllerBase controller) {
        return assemble(pattern, runtimes, channelValues, controller, false);
    }

    @NotNull
    public static Result assemble(@NotNull MultiPiecePattern pattern,
                                  @NotNull PieceRuntimes runtimes,
                                  @Nullable Map<String, Integer> channelValues,
                                  @Nullable MultiblockControllerBase controller,
                                  boolean skipHatches) {
        return assemble(pattern, runtimes, channelValues, controller, skipHatches, 0);
    }

    @NotNull
    public static Result assemble(@NotNull MultiPiecePattern pattern,
                                  @NotNull PieceRuntimes runtimes,
                                  @Nullable Map<String, Integer> channelValues,
                                  @Nullable MultiblockControllerBase controller,
                                  boolean skipHatches,
                                  int forcedToolingPieceIndex) {
        Map<BlockPos, BlockInfo> allBlocks = new HashMap<>();
        Map<BlockPos, StructureElementPreviewEntry> allPreviewEntries = new HashMap<>();
        Map<String, int[]> pieceRepeats = new HashMap<>();
        Map<String, BlockPos> pieceCenters = new HashMap<>();
        List<PieceResult> pieceResults = new ArrayList<>();
        AbilityPlacementTracker abilityTracker = pattern.createAbilityPlacementTracker();
        int toolingPieceIndex = 0;

        for (StructurePiece piece : pattern.getPieceList()) {
            FormedStructureMetadata prior = FormedStructureMetadata.fromCheckResult(
                    new HashMap<>(pieceRepeats), Collections.emptyMap(), new HashMap<>(pieceCenters));
            StructureActivationContext<MultiblockControllerBase> activation =
                    new StructureActivationContext<>(controller, null, BlockPos.ORIGIN, prior, null);
            boolean toolingVisible = piece.isToolingVisible();
            if (toolingVisible) {
                toolingPieceIndex++;
            }
            boolean defaultToolingSelection = forcedToolingPieceIndex == DEFAULT_TOOLING_PIECES;
            if (defaultToolingSelection && toolingVisible && toolingPieceIndex > 2) {
                pieceResults.add(PieceResult.empty());
                continue;
            }
            boolean forcedActive = toolingVisible &&
                    (toolingPieceIndex == forcedToolingPieceIndex ||
                            (defaultToolingSelection && toolingPieceIndex <= 2));
            if (!forcedActive && !piece.isActive(activation)) {
                if (toolingVisible) {
                    pieceResults.add(PieceResult.empty());
                }
                continue;
            }
            BlockPos pieceCenter = piece.getCenterPos(
                    BlockPos.ORIGIN, CANONICAL_PREVIEW_ORIENTATION, prior);
            PieceTemplate template = piece.getTemplate();
            int[] internalRepetitions = resolveInternalRepetitions(template, channelValues);
            int[] externalRepetitions = resolveExternalRepetitions(piece, channelValues);

            PieceRuntime runtime = runtimes.get(piece);
            if (runtime == null) {
                if (toolingVisible) {
                    pieceResults.add(PieceResult.empty());
                }
                continue;
            }

            PieceRuntimeState.PreviewCells preview = runtime.getState().createPreviewCells(
                    internalRepetitions, channelValues, CANONICAL_PREVIEW_ORIENTATION, null, skipHatches);
            Map<BlockPos, BlockInfo> pieceBlocks = new HashMap<>();
            forEachExternalRepeat(piece, externalRepetitions, localShift -> {
                BlockPos canonicalShift = RelativeDirection.setActualRelativeOffset(
                        localShift.getX(), localShift.getY(), localShift.getZ(),
                        CANONICAL_PREVIEW_ORIENTATION.getStructureFront(),
                        CANONICAL_PREVIEW_ORIENTATION.getUp(),
                        CANONICAL_PREVIEW_ORIENTATION.isFlipped(),
                        template.getStructureDir());

                for (Map.Entry<BlockPos, BlockInfo> entry : preview.getBlocks().entrySet()) {
                    BlockInfo info = entry.getValue();
                    if (info == null || info.getBlockState() == null) continue;
                    BlockPos baseRelative = entry.getKey().subtract(preview.getCenter());
                    StructureElementPreviewEntry previewEntry = preview.getPreviewEntries().get(entry.getKey());
                    BlockInfo selected = info;
                    BlockPos relative = baseRelative.add(canonicalShift);
                    BlockPos global = pieceCenter.add(relative);
                    if (toolingVisible) {
                        if (!abilityTracker.canPlace(selected)) {
                            selected = findFallback(previewEntry, abilityTracker);
                        }
                        abilityTracker.record(selected);
                        pieceBlocks.put(relative, selected);
                        BlockInfo existing = allBlocks.get(global);
                        if (isOccupied(selected) || !isOccupied(existing)) {
                            allBlocks.put(global, selected);
                        }
                    }
                }

                if (toolingVisible) {
                    for (Map.Entry<BlockPos, StructureElementPreviewEntry> entry : preview.getPreviewEntries().entrySet()) {
                        BlockPos relative = entry.getKey().subtract(preview.getCenter()).add(canonicalShift);
                        allPreviewEntries.put(pieceCenter.add(relative), entry.getValue());
                    }
                }
            });

            if (toolingVisible) {
                NormalizedShape pieceShape = normalize(pieceBlocks);
                pieceResults.add(new PieceResult(
                        pieceShape.shape,
                        new BlockPos(-pieceShape.minX, -pieceShape.minY, -pieceShape.minZ),
                        prior));
            }

            if (externalRepetitions.length > 0) {
                pieceRepeats.put(piece.getName(), externalRepetitions.clone());
            } else if (internalRepetitions.length > 0) {
                pieceRepeats.put(piece.getName(), internalRepetitions.clone());
            }
            pieceCenters.put(piece.getName(), pieceCenter);
        }

        orientPreviewMetaTileEntities(allBlocks);

        NormalizedShape combined = normalize(allBlocks);
        Map<BlockPos, StructureElementPreviewEntry> normalizedPreviewEntries = new HashMap<>();
        for (Map.Entry<BlockPos, StructureElementPreviewEntry> entry : allPreviewEntries.entrySet()) {
            normalizedPreviewEntries.put(new BlockPos(
                    entry.getKey().getX() - combined.minX,
                    entry.getKey().getY() - combined.minY,
                    entry.getKey().getZ() - combined.minZ), entry.getValue());
        }

        FormedStructureMetadata metadata = FormedStructureMetadata.fromCheckResult(
                pieceRepeats, channelValues == null ? Collections.emptyMap() : channelValues,
                pieceCenters);
        BlockPos center = new BlockPos(-combined.minX, -combined.minY, -combined.minZ);
        return new Result(
                combined.shape, center, normalizedPreviewEntries, pieceResults, metadata);
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
            @NotNull StructureOrientation orientation,
            @Nullable MultiblockControllerBase controller) {
        int compiledIndex = pattern.resolveToolingPieceIndex(oneBasedIndex);
        if (compiledIndex < 1) {
            return controllerPos;
        }
        Map<String, int[]> repeats = new HashMap<>();
        Map<String, BlockPos> centers = new HashMap<>();
        List<StructurePiece> pieces = pattern.getPieceList();
        for (int i = 0; i < compiledIndex; i++) {
            StructurePiece piece = pieces.get(i);

            FormedStructureMetadata actualPrior = FormedStructureMetadata.fromCheckResult(
                    new HashMap<>(repeats), Collections.emptyMap(), new HashMap<>(centers));
            StructureActivationContext<MultiblockControllerBase> activation =
                    new StructureActivationContext<>(controller, controller == null ? null : controller.getWorld(),
                            controllerPos, actualPrior, null);
            if (!piece.isActive(activation)) continue;
            BlockPos center = piece.getCenterPos(controllerPos, orientation, actualPrior);
            if (i == compiledIndex - 1) {
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
        PieceTemplate.AisleDef[] aisles = template.getAisles();
        int[] repetitions = new int[aisles.length];
        for (int i = 0; i < aisles.length; i++) {
            PieceTemplate.AisleDef aisle = aisles[i];
            Integer value = aisle.channelName() == null || channelValues == null
                    ? null
                    : channelValues.get(aisle.channelName());
            repetitions[i] = value == null
                    ? aisle.minRepeat()
                    : PieceRuntimeState.resolveRepetitionValue(
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
                    ? ranges[i][0]
                    : PieceRuntimeState.resolveRepetitionValue(value, ranges[i][0], ranges[i][1]);
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

    @Nullable
    private static BlockInfo findFallback(@Nullable StructureElementPreviewEntry previewEntry,
                                          @NotNull AbilityPlacementTracker abilityTracker) {
        if (previewEntry == null || previewEntry.getPreview().isEmpty()) {
            return null;
        }
        BlockInfo allowedHatch = null;
        for (gregtech.api.pattern.element.StructureElementPreview.CandidateGroup group :
                previewEntry.getPreview().getLimited()) {
            BlockInfo fallback = findFallback(group, abilityTracker);
            if (fallback == null) continue;
            if (fallback.getTileEntity() == null) return fallback;
            if (allowedHatch == null) allowedHatch = fallback;
        }
        for (gregtech.api.pattern.element.StructureElementPreview.CandidateGroup group :
                previewEntry.getPreview().getCommon()) {
            BlockInfo fallback = findFallback(group, abilityTracker);
            if (fallback == null) continue;
            if (fallback.getTileEntity() == null) return fallback;
            if (allowedHatch == null) allowedHatch = fallback;
        }
        return allowedHatch;
    }

    @Nullable
    private static BlockInfo findFallback(
            @NotNull gregtech.api.pattern.element.StructureElementPreview.CandidateGroup group,
            @NotNull AbilityPlacementTracker abilityTracker) {
        for (BlockInfo candidate : group.getCandidates()) {
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
        private final BlockPos center;
        private final Map<BlockPos, StructureElementPreviewEntry> previewEntries;
        private final List<PieceResult> pieces;
        private final FormedStructureMetadata metadata;

        private Result(@NotNull MultiblockShapeInfo shape,
                       @NotNull BlockPos center,
                       @NotNull Map<BlockPos, StructureElementPreviewEntry> previewEntries,
                       @NotNull List<PieceResult> pieces,
                       @NotNull FormedStructureMetadata metadata) {
            this.shape = shape;
            this.center = center;
            this.previewEntries = previewEntries;
            this.pieces = pieces;
            this.metadata = metadata;
        }

        @NotNull
        public MultiblockShapeInfo getShape() {
            return shape;
        }

        /** Position of the controller origin in the normalized combined preview array. */
        @NotNull
        public BlockPos getCenter() {
            return center;
        }

        public boolean isEmpty() {
            BlockInfo[][][] blocks = shape.getBlocks();
            for (BlockInfo[][] plane : blocks) {
                for (BlockInfo[] row : plane) {
                    for (BlockInfo info : row) {
                        if (info != null
                                && info != BlockInfo.EMPTY
                                && info.getBlockState() != null
                                && info.getBlockState().getBlock() != Blocks.AIR) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        @NotNull
        public Map<BlockPos, StructureElementPreviewEntry> getPreviewEntries() {
            return previewEntries;
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
