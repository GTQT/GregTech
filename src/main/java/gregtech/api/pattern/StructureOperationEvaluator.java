package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.StructureCheckState;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Thin operation boundary over the current structure implementations.
 *
 * <p>This class intentionally delegates to {@link MultiblockState},
 * {@link StructureCheckState}, {@link MultiPiecePattern}, and
 * {@link MultiPiecePreviewAssembler}. It provides one place for public
 * check/build/preview/iteration entry points before those implementations
 * are converged onto one traversal engine.
 */
public final class StructureOperationEvaluator {

    @Nullable
    private final StructureDefinition<?> definition;
    @Nullable
    private final MultiblockState state;
    @Nullable
    private final MultiPiecePattern multiPiecePattern;
    @Nullable
    private final PieceRuntimes pieceRuntimes;

    public StructureOperationEvaluator(@Nullable StructureDefinition<?> definition,
                                       @Nullable MultiblockState state,
                                       @Nullable MultiPiecePattern multiPiecePattern,
                                       @Nullable PieceRuntimes pieceRuntimes) {
        this.definition = definition;
        this.state = state;
        this.multiPiecePattern = multiPiecePattern;
        this.pieceRuntimes = pieceRuntimes;
    }

    @NotNull
    public StructureCheckResult check(
            @NotNull World world,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            boolean doRandomCheck,
            @Nullable PatternMatchContext context,
            @Nullable MultiblockControllerBase controller) {
        return check(StructureOperationRequest.check(
                world, controllerPos, orientation, doRandomCheck, context, controller));
    }

    @NotNull
    public StructureCheckResult check(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.CHECK);
        if (definition != null) {
            return StructureCheckResult.fromDefinition(checkDefinition(
                    request.requireWorld(), request.requireControllerPos(), request.requireOrientation(),
                    request.getMatchContext(), request.getController()));
        }
        PatternMatchContext legacyContext = checkSingle(
                request.requireWorld(),
                request.requireControllerPos(),
                request.requireOrientation().getStructureFront(),
                request.requireOrientation().getUp(),
                request.requireOrientation().allowsFlip(),
                request.doRandomCheck());
        return StructureCheckResult.fromLegacy(legacyContext, requireState());
    }

    @NotNull
    public StructureCheckResult check(
            @NotNull World world,
            @NotNull BlockPos controllerPos,
            @NotNull EnumFacing front,
            @NotNull EnumFacing up,
            boolean allowsFlip,
            boolean doRandomCheck,
            @Nullable PatternMatchContext context,
            @Nullable MultiblockControllerBase controller) {
        return check(
                world,
                controllerPos,
                StructureOrientation.of(front, front, up, false, allowsFlip),
                doRandomCheck,
                context,
                controller);
    }

    @NotNull
    public StructureCheckState.Result checkDefinition(
            @NotNull World world,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @Nullable PatternMatchContext context,
            @Nullable MultiblockControllerBase controller) {
        if (definition == null) {
            throw new IllegalStateException("Definition check requested without a structure definition");
        }
        return definition.createState().check(
                world, controllerPos, orientation, context, controller);
    }

    @NotNull
    public StructureCheckState.Result checkDefinition(
            @NotNull World world,
            @NotNull BlockPos controllerPos,
            @NotNull EnumFacing front,
            @NotNull EnumFacing up,
            boolean allowsFlip,
            @Nullable PatternMatchContext context,
            @Nullable MultiblockControllerBase controller) {
        if (definition == null) {
            throw new IllegalStateException("Definition check requested without a structure definition");
        }
        return definition.createState().check(
                world, controllerPos, front, up, allowsFlip, context, controller);
    }

    @Nullable
    public PatternMatchContext checkSingle(
            @NotNull World world,
            @NotNull BlockPos centerPos,
            @NotNull EnumFacing front,
            @NotNull EnumFacing up,
            boolean allowsFlip,
            boolean doRandomCheck) {
        return requireState().checkPatternFastAt(
                world, centerPos, front, up, allowsFlip, doRandomCheck);
    }

    public void clearSingleCache() {
        requireState().clearCache();
    }

    public void creativeBuildSingle(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches) {
        creativeBuildSingle(player, controller, StructureOrientation.fromController(controller),
                channelValues, skipHatches);
    }

    public void creativeBuildSingle(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches) {
        creativeBuildSingle(StructureOperationRequest.creativeBuild(
                player, controller, orientation, channelValues, skipHatches));
    }

    public void creativeBuildSingle(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.CREATIVE_BUILD);
        requireState().autoBuildAt(request.requirePlayer(), request.requireController(),
                request.requireControllerPos(), request.requireOrientation(),
                0, 0, 0, request.getChannelValues(), request.skipHatches(), null);
    }

    @Deprecated
    public void creativeBuildSingle(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            int tier) {
        requireState().autoBuild(player, controller, tier);
    }

    public boolean creativeBuildPiece(
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

    public boolean creativeBuildAllPieces(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches) {
        return creativeBuildAllPieces(player, controller, StructureOrientation.fromController(controller),
                channelValues, skipHatches);
    }

    public boolean creativeBuildAllPieces(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches) {
        return creativeBuildAllPieces(StructureOperationRequest.creativeBuild(
                player, controller, orientation, channelValues, skipHatches));
    }

    public boolean creativeBuildAllPieces(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.CREATIVE_BUILD);
        MultiPiecePattern pattern = requireMultiPiecePattern();
        AbilityPlacementTracker abilityTracker = pattern.createAbilityPlacementTracker();
        int pieceCount = pattern.getPieceCount();
        for (int pieceIndex = 1; pieceIndex <= pieceCount; pieceIndex++) {
            creativeBuildPiece(StructureOperationRequest.creativeBuildPiece(
                    pieceIndex, request.requirePlayer(), request.requireController(),
                    request.requireOrientation(), request.getChannelValues(),
                    request.skipHatches(), abilityTracker));
        }
        return pieceCount > 0;
    }

    public boolean creativeBuildPiece(
            int pieceIndex,
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches,
            @NotNull AbilityPlacementTracker abilityTracker) {
        return creativeBuildPiece(StructureOperationRequest.creativeBuildPiece(
                pieceIndex, player, controller, orientation, channelValues, skipHatches, abilityTracker));
    }

    public boolean creativeBuildPiece(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.CREATIVE_BUILD);
        MultiPiecePattern pattern = requireMultiPiecePattern();
        AbilityPlacementTracker abilityTracker = request.getAbilityTracker();
        if (abilityTracker == null) {
            abilityTracker = pattern.createAbilityPlacementTracker();
        }
        return pattern.autoBuildPiece(
                request.getPieceIndex(), request.requirePlayer(), request.requireController(),
                request.requireOrientation(), request.getChannelValues(), request.skipHatches(),
                requirePieceRuntimes(), abilityTracker);
    }

    public void survivalBuildSingle(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches,
            @NotNull ItemStack triggerStack) {
        survivalBuildSingle(StructureOperationRequest.survivalBuild(
                player, controller, orientation, channelValues, skipHatches, triggerStack));
    }

    public void survivalBuildSingle(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.SURVIVAL_BUILD);
        StructureTrace.debug(request.requireController(), "survival-build-single",
                "path=single-piece-legacy-autobuild, operation=" + request.getEvaluationOperation()
                        + ", skipHatches=" + request.skipHatches());
        requireState().autoBuildAt(request.requirePlayer(), request.requireController(),
                request.requireControllerPos(), request.requireOrientation(),
                0, 0, 0, request.getChannelValues(), request.skipHatches(), null,
                request.getEvaluationOperation());
    }

    public boolean survivalBuildAllPieces(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @NotNull StructureOrientation orientation,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches,
            @NotNull ItemStack triggerStack) {
        return survivalBuildAllPieces(StructureOperationRequest.survivalBuild(
                player, controller, orientation, channelValues, skipHatches, triggerStack));
    }

    public boolean survivalBuildAllPieces(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.SURVIVAL_BUILD);
        StructureTrace.debug(request.requireController(), "survival-build-all-pieces",
                "path=multi-piece-legacy-autobuild, operation=" + request.getEvaluationOperation()
                        + ", skipHatches=" + request.skipHatches());
        MultiPiecePattern pattern = requireMultiPiecePattern();
        AbilityPlacementTracker abilityTracker = pattern.createAbilityPlacementTracker();
        int pieceCount = pattern.getPieceCount();
        for (int pieceIndex = 1; pieceIndex <= pieceCount; pieceIndex++) {
            survivalBuildPiece(StructureOperationRequest.survivalBuildPiece(
                    pieceIndex, request.requirePlayer(), request.requireController(),
                    request.requireOrientation(), request.getChannelValues(),
                    request.skipHatches(), abilityTracker, request.requireTriggerStack()));
        }
        return pieceCount > 0;
    }

    public boolean survivalBuildPiece(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.SURVIVAL_BUILD);
        StructureTrace.debug(request.requireController(), "survival-build-piece",
                "path=multi-piece-legacy-autobuild, pieceIndex=" + request.getPieceIndex()
                        + ", operation=" + request.getEvaluationOperation()
                        + ", skipHatches=" + request.skipHatches());
        return requireMultiPiecePattern().autoBuildPiece(
                request.getPieceIndex(), request.requirePlayer(), request.requireController(),
                request.requireOrientation(), request.getChannelValues(), request.skipHatches(),
                requirePieceRuntimes(), request.requireAbilityTracker(),
                request.getEvaluationOperation());
    }

    public void spawnHintsSingle(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.HINT);
        StructureTrace.debug(request.requireController(), "hint-single",
                "path=single-piece-fixed-walker, operation=" + request.getEvaluationOperation());
        requireState().spawnHintsAt(request.requireWorld(), request.requireController(),
                request.requireControllerPos(), request.requireOrientation(),
                request.getChannelValues(), request.requireTriggerStack());
    }

    public boolean spawnHintsAllPieces(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.HINT);
        StructureTrace.debug(request.requireController(), "hint-all-pieces",
                "path=multi-piece-fixed-walker, operation=" + request.getEvaluationOperation());
        return requireMultiPiecePattern().spawnHintsAllPieces(
                request.requireWorld(), request.requireController(), request.requireOrientation(),
                request.getChannelValues(), requirePieceRuntimes(), request.requireTriggerStack());
    }

    @NotNull
    public BlockInfo[][][] previewSingle(
            @NotNull int[] repetitions,
            @Nullable Map<String, Integer> channelValues) {
        return previewSingle(StructureOperationRequest.preview(repetitions, channelValues));
    }

    @NotNull
    public BlockInfo[][][] previewSingle(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.PREVIEW);
        return requireState().getPreview(request.requireRepetitions(), request.getChannelValues());
    }

    @NotNull
    public MultiPiecePreviewAssembler.Result previewMultiPiece(
            @Nullable Map<String, Integer> channelValues,
            @Nullable MultiblockControllerBase controller) {
        return previewMultiPiece(StructureOperationRequest.previewMultiPiece(channelValues, controller));
    }

    @NotNull
    public MultiPiecePreviewAssembler.Result previewMultiPiece(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.PREVIEW);
        return MultiPiecePreviewAssembler.assemble(
                requireMultiPiecePattern(), requirePieceRuntimes(),
                request.getChannelValues(), request.getController());
    }

    @NotNull
    public Map<BlockPos, BlockInfo> iterateSingle(
            @NotNull World world,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation) {
        return iterateSingle(StructureOperationRequest.iterate(world, centerPos, orientation));
    }

    @NotNull
    public Map<BlockPos, BlockInfo> iterateSingle(@NotNull StructureOperationRequest request) {
        request.requireKind(StructureOperationRequest.Kind.ITERATE);
        return requireState().getAllStructureBlocks(
                request.requireWorld(), request.requireControllerPos(), request.requireOrientation());
    }

    @NotNull
    public Map<BlockPos, BlockInfo> iterateSingle(
            @NotNull World world,
            @NotNull BlockPos centerPos,
            @NotNull EnumFacing front,
            @NotNull EnumFacing up,
            boolean flipped) {
        return requireState().getAllStructureBlocks(world, centerPos, front, up, flipped);
    }

    @NotNull
    private MultiblockState requireState() {
        if (state == null) {
            throw new IllegalStateException("Single-piece operation requested without a multiblock state");
        }
        return state;
    }

    @NotNull
    private MultiPiecePattern requireMultiPiecePattern() {
        if (multiPiecePattern == null) {
            throw new IllegalStateException("Multi-piece operation requested without a compiled pattern");
        }
        return multiPiecePattern;
    }

    @NotNull
    private PieceRuntimes requirePieceRuntimes() {
        if (pieceRuntimes == null) {
            throw new IllegalStateException("Multi-piece operation requested without piece runtimes");
        }
        return pieceRuntimes;
    }
}
