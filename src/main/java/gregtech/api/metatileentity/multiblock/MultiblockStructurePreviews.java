package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.PieceTemplate;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.MultiPiecePreviewAssembler;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.PieceRuntimes;
import gregtech.api.pattern.PieceRuntimeState;
import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.StructureElementPreviewEntry;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.util.BlockInfo;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

final class MultiblockStructurePreviews {

    private MultiblockStructurePreviews() {}

    @NotNull
    static List<MultiblockShapeInfo> getMatchingShapes(
            @NotNull MultiblockControllerBase controller,
            @NotNull PieceTemplate patternTemplate,
            @Nullable PieceRuntimeState runtimeState,
            @Nullable StructureRuntime structureRuntime,
            @Nullable Map<String, Integer> channelValues) {
        int[][] aisleRepetitions = patternTemplate.getAisleRepetitions();
        return repetitionDFS(controller, patternTemplate, runtimeState, structureRuntime,
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
        int pieceIndex = resolveToolingPieceIndex(channelValues);
        if (pieceIndex > 0) {
            MultiblockShapeInfo shape = getMatchingShapeForPiece(controller, multiPiecePattern,
                    pieceRuntimes, structureRuntime, pieceIndex, channelValues);
            return shape == null ? Collections.emptyList() : Collections.singletonList(shape);
        }
        MultiPiecePreviewAssembler.Result preview = assembleMultiPiecePreview(controller,
                multiPiecePattern, pieceRuntimes, structureRuntime, channelValues);
        return Collections.singletonList(preview.getShape());
    }

    @Nullable
    static MultiPiecePreviewAssembler.Result getMatchingMultiPiecePreview(
            @NotNull MultiblockControllerBase controller,
            @Nullable MultiPiecePattern multiPiecePattern,
            @Nullable PieceRuntimes pieceRuntimes,
            @Nullable StructureRuntime structureRuntime,
            @Nullable Map<String, Integer> channelValues) {
        if (multiPiecePattern == null) return null;
        return assembleMultiPiecePreview(
                controller, multiPiecePattern, pieceRuntimes, structureRuntime, channelValues);
    }

    @NotNull
    static Map<BlockPos, StructureElementPreviewEntry> buildMultiPiecePreviewEntries(
            @NotNull MultiblockControllerBase controller,
            @Nullable MultiPiecePattern multiPiecePattern,
            @Nullable PieceRuntimes pieceRuntimes,
            @Nullable StructureRuntime structureRuntime,
            @Nullable Map<String, Integer> channelValues) {
        if (multiPiecePattern == null) return new HashMap<>();
        MultiPiecePreviewAssembler.Result preview = assembleMultiPiecePreview(controller,
                multiPiecePattern, pieceRuntimes, structureRuntime, channelValues,
                resolveToolingPieceIndex(channelValues));
        return new HashMap<>(preview.getPreviewEntries());
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
                || pieceIndex > multiPiecePattern.getToolingPieceCount()) {
            return null;
        }
        MultiPiecePreviewAssembler.Result preview = assembleMultiPiecePreview(controller,
                multiPiecePattern, pieceRuntimes, structureRuntime, channelValues, pieceIndex);
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

    private static MultiPiecePreviewAssembler.Result assembleMultiPiecePreview(
            @NotNull MultiblockControllerBase controller,
            @NotNull MultiPiecePattern multiPiecePattern,
            @Nullable PieceRuntimes pieceRuntimes,
            @Nullable StructureRuntime structureRuntime,
            @Nullable Map<String, Integer> channelValues,
            int toolingPieceIndex) {
        return structureRuntime == null
                ? MultiPiecePreviewAssembler.assemble(multiPiecePattern, pieceRuntimes, channelValues,
                        controller, false, toolingPieceIndex)
                : structureRuntime.previewMultiPiece(
                        StructureOperationRequest.previewMultiPiece(
                                channelValues, controller, false, toolingPieceIndex));
    }

    private static int resolveToolingPieceIndex(@Nullable Map<String, Integer> channelValues) {
        if (channelValues == null) return 0;
        return channelValues.getOrDefault(GTStructureChannels.STRUCTURE_PIECE.getName(), 0);
    }

    private static List<MultiblockShapeInfo> repetitionDFS(
            @NotNull MultiblockControllerBase controller,
            @NotNull PieceTemplate patternTemplate,
            @Nullable PieceRuntimeState runtimeState,
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
                    ? runtimeState == null
                            ? new BlockInfo[][][]{{{BlockInfo.EMPTY}}}
                            : runtimeState.createPreviewCells(repetition, channelValues,
                                    controller.multiPiecePattern == null
                                            ? null
                                            : controller.multiPiecePattern.createAbilityPlacementTracker())
                                    .toBlockArray()
                    : structureRuntime.previewSingle(
                            StructureOperationRequest.preview(repetition, channelValues));
            pages.add(new MultiblockShapeInfo(preview));
        } else {
            int aisleIdx = repetitionStack.size();
            String channelName = null;
            PieceTemplate.AisleDef[] aisles = patternTemplate.getAisles();
            if (aisleIdx < aisles.length) {
                channelName = aisles[aisleIdx].channelName();
            }

            if (channelName != null && channelValues != null && channelValues.containsKey(channelName)) {
                int channelValue = channelValues.get(channelName);
                int min = aisleRepetitions[aisleIdx][0];
                int max = aisleRepetitions[aisleIdx][1];
                int clamped = Math.max(min, Math.min(max, channelValue));
                repetitionStack.push(clamped);
                repetitionDFS(controller, patternTemplate, runtimeState, structureRuntime,
                        pages, aisleRepetitions, repetitionStack, channelValues);
                repetitionStack.pop();
            } else {
                for (int i = aisleRepetitions[aisleIdx][0]; i <= aisleRepetitions[aisleIdx][1]; i++) {
                    repetitionStack.push(i);
                    repetitionDFS(controller, patternTemplate, runtimeState, structureRuntime,
                            pages, aisleRepetitions, repetitionStack, channelValues);
                    repetitionStack.pop();
                }
            }
        }
        return pages;
    }
}
