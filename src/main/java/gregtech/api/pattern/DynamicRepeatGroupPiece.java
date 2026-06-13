package gregtech.api.pattern;

import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.StructureCompiler;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Repeatable piece positioned relative to a previously resolved piece center.
 */
public final class DynamicRepeatGroupPiece extends RepeatGroupPiece {

    private final String anchorPieceName;
    private final int[] anchorStep;

    public DynamicRepeatGroupPiece(@NotNull String name, @NotNull PieceTemplate template,
                                   @NotNull Vec3i staticOffset, @NotNull OffsetMode offsetMode,
                                   @Nullable BooleanSupplier condition,
                                   int[] axes, int[][] ranges, int[] steps,
                                   @Nullable String[] channelNames, int[] centerOffset,
                                   @NotNull StructureCompiler.SearchStrategy strategy,
                                   @NotNull String anchorPieceName, @NotNull int[] anchorStep) {
        super(name, template, staticOffset, offsetMode, condition,
                axes, ranges, steps, channelNames, centerOffset, strategy);
        this.anchorPieceName = anchorPieceName;
        this.anchorStep = anchorStep.clone();
    }

    @NotNull
    @Override
    public BlockPos getCenterPos(@NotNull BlockPos controllerPos,
                                 @NotNull StructureOrientation orientation,
                                 @Nullable FormedStructureMetadata prior) {
        if (prior == null) {
            return super.getCenterPos(controllerPos, orientation);
        }
        BlockPos anchorCenter = prior.getPieceCenter(anchorPieceName);
        if (anchorCenter == null) {
            return super.getCenterPos(controllerPos, orientation);
        }
        int[] anchorReps = prior.getPieceRepeats(anchorPieceName);
        int count = anchorReps.length == 0 ? 1 : anchorReps[0];
        Vec3i offset = getOffset();
        int[] dynamic = {
                offset.getX() + anchorStep[0] * count,
                offset.getY() + anchorStep[1] * count,
                offset.getZ() + anchorStep[2] * count
        };
        return getOffsetMode().apply(anchorCenter, dynamic, orientation);
    }

    @NotNull
    @Override
    public BlockPos getCenterPos(@NotNull BlockPos controllerPos,
                                 @NotNull EnumFacing frontFacing,
                                 @NotNull EnumFacing upFacing,
                                 boolean flipped,
                                 @Nullable FormedStructureMetadata prior) {
        return getCenterPos(controllerPos,
                StructureOrientation.of(frontFacing, frontFacing, upFacing, flipped, false), prior);
    }
}
