package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

final class StructureBuildOperationService {

    @NotNull
    private final StructureOperationContext context;

    StructureBuildOperationService(@NotNull StructureOperationContext context) {
        this.context = context;
    }

    @NotNull
    StructureBuildResult buildSingle(@NotNull StructureOperationRequest request) {
        request.requireBuildKind();
        if (request.getEvaluationOperation().isCreativeBuild()) {
            return creativeBuildSingle(request);
        }
        return survivalBuildSingle(request);
    }

    @NotNull
    StructureBuildResult buildAllPieces(@NotNull StructureOperationRequest request) {
        request.requireBuildKind();
        return request.getEvaluationOperation().isCreativeBuild()
                ? creativeBuildAllPieces(request)
                : survivalBuildAllPieces(request);
    }

    @NotNull
    StructureBuildResult buildPiece(@NotNull StructureOperationRequest request) {
        request.requireBuildKind();
        return request.getEvaluationOperation().isCreativeBuild()
                ? creativeBuildPiece(request)
                : survivalBuildPiece(request);
    }

    void creativeBuildSingle(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches) {
        creativeBuildSingle(player, controller, StructureOrientation.fromController(controller),
                channelValues, skipHatches);
    }

    void creativeBuildSingle(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches) {
        creativeBuildSingle(StructureOperationRequest.creativeBuild(
                player, controller, orientation, channelValues, skipHatches));
    }

    @NotNull
    StructureBuildResult creativeBuildSingle(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.CREATIVE_BUILD);
        StructureBuildResult result = buildPieceThroughRuntime(request, 1, ItemStack.EMPTY);
        StructureTrace.debug(request.requireController(), "creative-build-single-result",
                result.describeCounts());
        return result;
    }

    void creativeBuildSingle(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            int tier) {
        creativeBuildSingle(StructureOperationRequest.creativeBuild(
                player, controller, StructureOrientation.fromController(controller),
                tierChannelValues(context.runtime().pattern.getPrimaryPiece().getTemplate(), tier),
                false));
    }

    boolean creativeBuildPiece(
            int pieceIndex,
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches,
            @NotNull AbilityPlacementTracker abilityTracker) {
        return creativeBuildPiece(
                pieceIndex, player, controller, StructureOrientation.fromController(controller),
                channelValues, skipHatches, abilityTracker);
    }

    boolean creativeBuildAllPieces(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches) {
        return creativeBuildAllPieces(player, controller, StructureOrientation.fromController(controller),
                channelValues, skipHatches);
    }

    boolean creativeBuildAllPieces(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches) {
        return creativeBuildAllPieces(StructureOperationRequest.creativeBuild(
                player, controller, orientation, channelValues, skipHatches)).isAttempted();
    }

    @NotNull
    StructureBuildResult creativeBuildAllPieces(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.CREATIVE_BUILD);
        StructureOperationRuntime runtime = context.runtime();
        MultiPiecePattern pattern = runtime.pattern;
        AbilityPlacementTracker abilityTracker = pattern.createAbilityPlacementTracker();
        int pieceCount = pattern.getToolingPieceCount();
        StructureBuildResult.Builder result = StructureBuildResult.builder();
        for (int pieceIndex = 1; pieceIndex <= pieceCount; pieceIndex++) {
            int compiledPieceIndex = pattern.resolveToolingPieceIndex(pieceIndex);
            if (compiledPieceIndex < 1) {
                result.merge(StructureBuildResult.builder().recordInvalidPieceRequest().build());
                continue;
            }
            result.merge(creativeBuildPiece(StructureOperationRequest.creativeBuildPiece(
                    compiledPieceIndex, request.requirePlayer(), request.requireController(),
                    request.requireOrientation(), request.getChannelValues(),
                    request.skipHatches(), abilityTracker)));
        }
        StructureBuildResult buildResult = result.build();
        StructureTrace.debug(request.requireController(), "creative-build-all-pieces-result",
                buildResult.describeCounts());
        return buildResult;
    }

    boolean creativeBuildPiece(
            int pieceIndex,
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches,
            @NotNull AbilityPlacementTracker abilityTracker) {
        return creativeBuildPiece(StructureOperationRequest.creativeBuildPiece(
                pieceIndex, player, controller, orientation, channelValues,
                skipHatches, abilityTracker)).isAttempted();
    }

    @NotNull
    StructureBuildResult creativeBuildPiece(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.CREATIVE_BUILD);
        StructureBuildResult result = buildPieceThroughRuntime(
                request, request.getPieceIndex(), ItemStack.EMPTY);
        StructureTrace.debug(request.requireController(), "creative-build-piece-result",
                "pieceIndex=" + request.getPieceIndex() + ", " + result.describeCounts());
        return result;
    }

    void survivalBuildSingle(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches,
            @NotNull ItemStack triggerStack) {
        survivalBuildSingle(StructureOperationRequest.survivalBuild(
                player, controller, orientation, channelValues, skipHatches, triggerStack));
    }

    @NotNull
    StructureBuildResult survivalBuildSingle(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.SURVIVAL_BUILD);
        StructureTrace.debug(request.requireController(), "survival-build-single",
                "path=v3-typed-single, operation=" + request.getEvaluationOperation()
                        + ", skipHatches=" + request.skipHatches());
        StructureBuildResult result = buildPieceThroughRuntime(
                request, 1, request.requireTriggerStack());
        StructureTrace.debug(request.requireController(), "survival-build-single-result",
                result.describeCounts());
        return result;
    }

    boolean survivalBuildAllPieces(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches,
            @NotNull ItemStack triggerStack) {
        return survivalBuildAllPieces(StructureOperationRequest.survivalBuild(
                player, controller, orientation, channelValues, skipHatches, triggerStack)).isAttempted();
    }

    @NotNull
    StructureBuildResult survivalBuildAllPieces(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.SURVIVAL_BUILD);
        StructureTrace.debug(request.requireController(), "survival-build-all-pieces",
                "path=v3-typed-pattern, operation=" + request.getEvaluationOperation()
                        + ", skipHatches=" + request.skipHatches());
        StructureOperationRuntime runtime = context.runtime();
        MultiPiecePattern pattern = runtime.pattern;
        AbilityPlacementTracker abilityTracker = pattern.createAbilityPlacementTracker();
        int pieceCount = pattern.getToolingPieceCount();
        StructureBuildResult.Builder result = StructureBuildResult.builder();
        for (int pieceIndex = 1; pieceIndex <= pieceCount; pieceIndex++) {
            int compiledPieceIndex = pattern.resolveToolingPieceIndex(pieceIndex);
            if (compiledPieceIndex < 1) {
                result.merge(StructureBuildResult.builder().recordInvalidPieceRequest().build());
                continue;
            }
            result.merge(survivalBuildPiece(StructureOperationRequest.survivalBuildPiece(
                    compiledPieceIndex, request.requirePlayer(), request.requireController(),
                    request.requireOrientation(), request.getChannelValues(),
                    request.skipHatches(), abilityTracker, request.requireTriggerStack())));
        }
        StructureBuildResult buildResult = result.build();
        StructureTrace.debug(request.requireController(), "survival-build-all-pieces-result",
                buildResult.describeCounts());
        return buildResult;
    }

    @NotNull
    StructureBuildResult survivalBuildPiece(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.SURVIVAL_BUILD);
        StructureTrace.debug(request.requireController(), "survival-build-piece",
                "path=v3-typed-pattern, pieceIndex=" + request.getPieceIndex()
                        + ", operation=" + request.getEvaluationOperation()
                        + ", skipHatches=" + request.skipHatches());
        StructureBuildResult result = buildPieceThroughRuntime(
                request, request.getPieceIndex(), request.requireTriggerStack());
        StructureTrace.debug(request.requireController(), "survival-build-piece-result",
                "pieceIndex=" + request.getPieceIndex() + ", " + result.describeCounts());
        return result;
    }

    @NotNull
    private StructureBuildResult buildPieceThroughRuntime(
            @NotNull StructureOperationRequest request,
            int pieceIndex,
            @NotNull ItemStack triggerStack) {
        StructureOperationRuntime runtime = context.runtime();
        AbilityPlacementTracker abilityTracker = request.getAbilityTracker();
        if (abilityTracker == null) {
            abilityTracker = runtime.pattern.createAbilityPlacementTracker();
        }
        return runtime.pattern.autoBuildPieceWithResult(
                pieceIndex, request.requirePlayer(), request.requireController(),
                request.requireOrientation(), request.getChannelValues(), request.skipHatches(),
                runtime.runtimes, abilityTracker,
                request.getEvaluationOperation(), triggerStack)
                .withDiagnostics(runtime.diagnostics(request.getEvaluationOperation()));
    }

    @NotNull
    private static Map<String, Integer> tierChannelValues(@NotNull PieceTemplate template, int tier) {
        Map<String, Integer> channels = new HashMap<>();
        if (tier <= 0) {
            return channels;
        }
        PieceTemplate.AisleDef[] aisles = template.getAisles();
        for (PieceTemplate.AisleDef aisle : aisles) {
            if (aisle.minRepeat() == aisle.maxRepeat()) continue;
            String name = aisle.channelName();
            if (name != null) {
                channels.put(name, tier);
            }
        }
        return channels;
    }
}
