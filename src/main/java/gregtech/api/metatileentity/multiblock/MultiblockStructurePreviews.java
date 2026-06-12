package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.MultiPiecePreviewAssembler;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.MultiblockState;
import gregtech.api.pattern.PieceRuntimes;
import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.BlockInfo;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;

final class MultiblockStructurePreviews {

    private MultiblockStructurePreviews() {}

    @NotNull
    static List<MultiblockShapeInfo> getMatchingShapes(
            @NotNull MultiblockControllerBase controller,
            @NotNull BlockPatternTemplate patternTemplate,
            @Nullable MultiblockState multiblockState,
            @Nullable StructureRuntime structureRuntime,
            @Nullable Map<String, Integer> channelValues) {
        int[][] aisleRepetitions = patternTemplate.getAisleRepetitions();
        return repetitionDFS(controller, patternTemplate, multiblockState, structureRuntime,
                new ArrayList<>(), aisleRepetitions, new Stack<>(), channelValues);
    }

    @NotNull
    static List<MultiblockShapeInfo> buildMultiPieceShapes(
            @NotNull MultiblockControllerBase controller,
            @Nullable MultiPiecePattern multiPiecePattern,
            @Nullable PieceRuntimes pieceRuntimes,
            @Nullable StructureRuntime structureRuntime,
            @Nullable Map<String, Integer> channelValues) {
        if (multiPiecePattern == null) {
            return Collections.emptyList();
        }
        MultiPiecePreviewAssembler.Result preview = assembleMultiPiecePreview(controller,
                multiPiecePattern, pieceRuntimes, structureRuntime, channelValues);
        return Collections.singletonList(preview.getShape());
    }

    @NotNull
    static Map<BlockPos, TraceabilityPredicate> buildMultiPiecePredicateMap(
            @NotNull MultiblockControllerBase controller,
            @Nullable MultiPiecePattern multiPiecePattern,
            @Nullable PieceRuntimes pieceRuntimes,
            @Nullable StructureRuntime structureRuntime) {
        if (multiPiecePattern == null) return new HashMap<>();
        MultiPiecePreviewAssembler.Result preview = assembleMultiPiecePreview(controller,
                multiPiecePattern, pieceRuntimes, structureRuntime, null);
        return new HashMap<>(preview.getPredicates());
    }

    @Nullable
    static MultiblockShapeInfo getMatchingShapeForPiece(
            @NotNull MultiblockControllerBase controller,
            @Nullable MultiPiecePattern multiPiecePattern,
            @Nullable PieceRuntimes pieceRuntimes,
            @Nullable StructureRuntime structureRuntime,
            int pieceIndex,
            @Nullable Map<String, Integer> channelValues) {
        MultiPiecePreviewAssembler.PieceResult preview = getMatchingPreviewPiece(controller,
                multiPiecePattern, pieceRuntimes, structureRuntime, pieceIndex, channelValues);
        return preview == null ? null : preview.getShape();
    }

    @Nullable
    static MultiPiecePreviewAssembler.PieceResult getMatchingPreviewPiece(
            @NotNull MultiblockControllerBase controller,
            @Nullable MultiPiecePattern multiPiecePattern,
            @Nullable PieceRuntimes pieceRuntimes,
            @Nullable StructureRuntime structureRuntime,
            int pieceIndex,
            @Nullable Map<String, Integer> channelValues) {
        if (multiPiecePattern == null
                || pieceIndex < 1
                || pieceIndex > multiPiecePattern.getPieceList().size()) {
            return null;
        }
        MultiPiecePreviewAssembler.Result preview = assembleMultiPiecePreview(controller,
                multiPiecePattern, pieceRuntimes, structureRuntime, channelValues);
        return preview.getPiece(pieceIndex);
    }

    private static MultiPiecePreviewAssembler.Result assembleMultiPiecePreview(
            @NotNull MultiblockControllerBase controller,
            @NotNull MultiPiecePattern multiPiecePattern,
            @Nullable PieceRuntimes pieceRuntimes,
            @Nullable StructureRuntime structureRuntime,
            @Nullable Map<String, Integer> channelValues) {
        return structureRuntime == null
                ? MultiPiecePreviewAssembler.assemble(multiPiecePattern, pieceRuntimes, channelValues, controller)
                : structureRuntime.previewMultiPiece(
                        StructureOperationRequest.previewMultiPiece(channelValues, controller));
    }

    private static List<MultiblockShapeInfo> repetitionDFS(
            @NotNull MultiblockControllerBase controller,
            @NotNull BlockPatternTemplate patternTemplate,
            @Nullable MultiblockState multiblockState,
            @Nullable StructureRuntime structureRuntime,
            @NotNull List<MultiblockShapeInfo> pages,
            @NotNull int[][] aisleRepetitions,
            @NotNull Stack<Integer> repetitionStack,
            @Nullable Map<String, Integer> channelValues) {
        if (repetitionStack.size() == aisleRepetitions.length) {
            int[] repetition = new int[repetitionStack.size()];
            for (int i = 0; i < repetitionStack.size(); i++) {
                repetition[i] = repetitionStack.get(i);
            }
            BlockInfo[][][] preview = structureRuntime == null
                    ? Objects.requireNonNull(multiblockState).getPreview(repetition, channelValues)
                    : structureRuntime.previewSingle(
                            StructureOperationRequest.preview(repetition, channelValues));
            pages.add(new MultiblockShapeInfo(preview));
        } else {
            int aisleIdx = repetitionStack.size();
            String channelName = null;
            BlockPatternTemplate.AisleDef[] aisles = patternTemplate.getAisles();
            if (aisleIdx < aisles.length) {
                channelName = aisles[aisleIdx].channelName();
            }

            if (channelName != null && channelValues != null && channelValues.containsKey(channelName)) {
                int channelValue = channelValues.get(channelName);
                int min = aisleRepetitions[aisleIdx][0];
                int max = aisleRepetitions[aisleIdx][1];
                int clamped = Math.max(min, Math.min(max, channelValue));
                repetitionStack.push(clamped);
                repetitionDFS(controller, patternTemplate, multiblockState, structureRuntime,
                        pages, aisleRepetitions, repetitionStack, channelValues);
                repetitionStack.pop();
            } else {
                for (int i = aisleRepetitions[aisleIdx][0]; i <= aisleRepetitions[aisleIdx][1]; i++) {
                    repetitionStack.push(i);
                    repetitionDFS(controller, patternTemplate, multiblockState, structureRuntime,
                            pages, aisleRepetitions, repetitionStack, channelValues);
                    repetitionStack.pop();
                }
            }
        }
        return pages;
    }
}
