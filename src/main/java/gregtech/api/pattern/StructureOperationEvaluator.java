package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.StructureCheckState;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
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
        requireState().autoBuild(player, controller, channelValues, skipHatches);
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
        return requireMultiPiecePattern().autoBuildPiece(
                pieceIndex, player, controller, channelValues, skipHatches,
                requirePieceRuntimes(), abilityTracker);
    }

    public boolean creativeBuildAllPieces(
            @NotNull EntityPlayer player,
            @NotNull MultiblockControllerBase controller,
            @Nullable Map<String, Integer> channelValues,
            boolean skipHatches) {
        MultiPiecePattern pattern = requireMultiPiecePattern();
        AbilityPlacementTracker abilityTracker = pattern.createAbilityPlacementTracker();
        int pieceCount = pattern.getPieceCount();
        for (int pieceIndex = 1; pieceIndex <= pieceCount; pieceIndex++) {
            creativeBuildPiece(
                    pieceIndex, player, controller, channelValues, skipHatches, abilityTracker);
        }
        return pieceCount > 0;
    }

    @NotNull
    public BlockInfo[][][] previewSingle(
            @NotNull int[] repetitions,
            @Nullable Map<String, Integer> channelValues) {
        return requireState().getPreview(repetitions, channelValues);
    }

    @NotNull
    public MultiPiecePreviewAssembler.Result previewMultiPiece(
            @Nullable Map<String, Integer> channelValues,
            @Nullable MultiblockControllerBase controller) {
        return MultiPiecePreviewAssembler.assemble(
                requireMultiPiecePattern(), requirePieceRuntimes(), channelValues, controller);
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
