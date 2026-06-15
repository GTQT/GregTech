package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Compatibility facade for the split structure operation services.
 *
 * <p>The public runtime surface still exposes this evaluator while the actual
 * operation implementations live in small check, snapshot, build, hint,
 * preview, and iterate services.
 */
public final class StructureOperationEvaluator {

    @NotNull
    private final StructureOperationContext operationContext;
    @NotNull
    private final StructureCheckOperationService checkOperations;
    @NotNull
    private final StructureSnapshotOperationService snapshotOperations;
    @NotNull
    private final StructureBuildOperationService buildOperations;
    @NotNull
    private final StructureHintOperationService hintOperations;
    @NotNull
    private final StructurePreviewOperationService previewOperations;
    @NotNull
    private final StructureIterateOperationService iterateOperations;

    public StructureOperationEvaluator(@Nullable StructureDefinition<?> definition,
                                       @Nullable PieceRuntimeState state,
                                       @Nullable MultiPiecePattern multiPiecePattern,
                                       @Nullable PieceRuntimes pieceRuntimes) {
        this.operationContext = new StructureOperationContext(
                definition, state, multiPiecePattern, pieceRuntimes);
        this.checkOperations = new StructureCheckOperationService(operationContext);
        this.snapshotOperations = new StructureSnapshotOperationService(operationContext);
        this.buildOperations = new StructureBuildOperationService(operationContext);
        this.hintOperations = new StructureHintOperationService(operationContext);
        this.previewOperations = new StructurePreviewOperationService(operationContext);
        this.iterateOperations = new StructureIterateOperationService(operationContext);
    }

    public void setAdapterTrace(@Nullable String adapterTrace) {
        operationContext.setAdapterTrace(adapterTrace);
    }

    @NotNull
    public StructureCheckResult check(
            @NotNull World world,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            boolean doRandomCheck,
            @Nullable PatternMatchContext context,
            @Nullable MultiblockControllerBase controller) {
        return checkOperations.check(world, controllerPos, orientation, doRandomCheck, context, controller);
    }

    @NotNull
    public StructureCheckResult check(@NotNull StructureOperationRequest request) {
        return checkOperations.check(request);
    }

    @NotNull
    public StructureSnapshotResult checkSnapshot(@NotNull StructureOperationRequest request) {
        return snapshotOperations.checkSnapshot(request);
    }

    @NotNull
    public StructureCheckResult checkActiveGraph(@NotNull StructureOperationRequest request) {
        return checkOperations.checkActiveGraph(request);
    }

    @NotNull
    public StructureCheckResult checkIncremental(
            @NotNull StructureOperationRequest request,
            @NotNull CommittedStructureGraph baseline,
            @NotNull Set<String> dirtyRoots,
            @NotNull StructureEligibilityPlan plan,
            @Nullable StructureDirtyPrecheck.Result detachedPrecheck) {
        return checkOperations.checkIncremental(request, baseline, dirtyRoots, plan, detachedPrecheck);
    }

    @Deprecated
    @NotNull
    public StructureCheckResult checkDirtyPieces(@NotNull StructureOperationRequest request) {
        return checkOperations.checkDirtyPieces(request);
    }

    @Nullable
    public PatternMatchContext checkSingle(
            @NotNull World world,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation,
            boolean doRandomCheck) {
        return checkOperations.checkSingle(world, centerPos, orientation, doRandomCheck);
    }

    public void clearSingleCache() {
        checkOperations.clearSingleCache();
    }

    @NotNull
    public StructureBuildResult buildSingle(@NotNull StructureOperationRequest request) {
        return buildOperations.buildSingle(request);
    }

    @NotNull
    public StructureBuildResult buildPiece(@NotNull StructureOperationRequest request) {
        return buildOperations.buildPiece(request);
    }

    @NotNull
    public StructureBuildResult buildAllPieces(@NotNull StructureOperationRequest request) {
        return buildOperations.buildAllPieces(request);
    }

    @NotNull
    public StructureBuildResult creativeBuildSingle(@NotNull StructureOperationRequest request) {
        return buildOperations.creativeBuildSingle(request);
    }

    void creativeBuildSingle(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches) {
        buildOperations.creativeBuildSingle(player, controller, channelValues, skipHatches);
    }

    void creativeBuildSingle(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            int tier) {
        buildOperations.creativeBuildSingle(player, controller, tier);
    }

    @NotNull
    public StructureBuildResult creativeBuildPiece(@NotNull StructureOperationRequest request) {
        return buildOperations.creativeBuildPiece(request);
    }

    @NotNull
    public StructureBuildResult creativeBuildAllPieces(@NotNull StructureOperationRequest request) {
        return buildOperations.creativeBuildAllPieces(request);
    }

    @NotNull
    public StructureBuildResult survivalBuildSingle(@NotNull StructureOperationRequest request) {
        return buildOperations.survivalBuildSingle(request);
    }

    @NotNull
    public StructureBuildResult survivalBuildPiece(@NotNull StructureOperationRequest request) {
        return buildOperations.survivalBuildPiece(request);
    }

    @NotNull
    public StructureBuildResult survivalBuildAllPieces(@NotNull StructureOperationRequest request) {
        return buildOperations.survivalBuildAllPieces(request);
    }

    public void spawnHintsSingle(@NotNull StructureOperationRequest request) {
        hintOperations.spawnHintsSingle(request);
    }

    @NotNull
    public StructureHintResult hintSingle(@NotNull StructureOperationRequest request) {
        return hintOperations.hintSingle(request);
    }

    public boolean spawnHintsAllPieces(@NotNull StructureOperationRequest request) {
        return hintOperations.spawnHintsAllPieces(request);
    }

    @NotNull
    public StructureHintResult hintAllPieces(@NotNull StructureOperationRequest request) {
        return hintOperations.hintAllPieces(request);
    }

    @NotNull
    public BlockInfo[][][] previewSingle(@NotNull StructureOperationRequest request) {
        return previewOperations.previewSingle(request);
    }

    @NotNull
    BlockInfo[][][] previewSingle(
            @NotNull int[] repetitions,
            @Nullable Map<String, Integer> channelValues) {
        return previewOperations.previewSingle(repetitions, channelValues);
    }

    @NotNull
    public StructurePreviewResult previewSingleResult(@NotNull StructureOperationRequest request) {
        return previewOperations.previewSingleResult(request);
    }

    @NotNull
    public MultiPiecePreviewAssembler.Result previewMultiPiece(@NotNull StructureOperationRequest request) {
        return previewOperations.previewMultiPiece(request);
    }

    @NotNull
    public StructurePreviewResult previewMultiPieceResult(@NotNull StructureOperationRequest request) {
        return previewOperations.previewMultiPieceResult(request);
    }

    @NotNull
    public Map<BlockPos, BlockInfo> iterateSingle(@NotNull StructureOperationRequest request) {
        return iterateOperations.iterateSingle(request);
    }

    @NotNull
    Map<BlockPos, BlockInfo> iterateSingle(
            @NotNull World world,
            @NotNull BlockPos centerPos,
            @NotNull StructureOrientation orientation) {
        return iterateOperations.iterateSingle(world, centerPos, orientation);
    }

    @NotNull
    public StructureIterateResult iterateSingleResult(@NotNull StructureOperationRequest request) {
        return iterateOperations.iterateSingleResult(request);
    }

    @NotNull
    public StructureIterateResult iterateMultiPiece(@NotNull StructureOperationRequest request) {
        return iterateOperations.iterateMultiPiece(request);
    }
}
