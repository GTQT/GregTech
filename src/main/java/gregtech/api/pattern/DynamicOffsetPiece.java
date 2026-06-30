package gregtech.api.pattern;

import gregtech.api.pattern.element.FormedStructureMetadata;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link StructurePiece} whose center position is computed dynamically based
 * on the runtime repeat count of another (the "anchor") piece.
 *
 * <p>Used to place fixed pieces that follow a repeatable body in the middle of
 * a structure (e.g. the "top" piece after a "body" piece whose extent is
 * unknown at compile time). The effective offset at check time is:
 *
 * <pre>{@code
 * center = anchorCenter + staticOffset + anchorCount * anchorStep
 * }</pre>
 *
 * <p>where {@code anchorCenter} and {@code anchorCount} are read from prior
 * pieces' metadata, and
 * {@code anchorStep} is a (right, up, back) vector that the compiler
 * determined from the structure's aisle direction and the anchor's step size.
 *
 * <p>If the prior metadata is null, missing, or the anchor piece is not yet
 * validated, this falls back to the static {@code baseOffset} so that single
 * piece structures and first-time checks behave exactly like a regular
 * {@link StructurePiece}.
 *
 * @see StructurePiece#getCenterPos(BlockPos, StructureOrientation, FormedStructureMetadata)
 */
public final class DynamicOffsetPiece extends StructurePiece {

    private final String anchorPieceName;
    private final int[] anchorStep;

    /**
     * @param name           unique name for this piece
     * @param template       the canonical piece template
     * @param staticOffset   static base offset (right, up, back) — used as fallback
     *                       and as the starting point for the dynamic offset
     * @param offsetMode     how the offset is interpreted relative to controller facing
     * @param condition      optional activation condition
     * @param anchorPieceName name of the repeatable anchor piece to read the repeat
     *                        count from at check time
     * @param anchorStep     per-repeat step in (right, up, back) world coordinates
     */
    public DynamicOffsetPiece(@NotNull String name, @NotNull PieceTemplate template,
                              @NotNull Vec3i staticOffset, @NotNull OffsetMode offsetMode,
                              @Nullable StructureCondition<?> condition,
                              @NotNull String anchorPieceName,
                              @NotNull int[] anchorStep) {
        this(name, template, staticOffset, offsetMode, condition, anchorPieceName, anchorStep, true);
    }

    public DynamicOffsetPiece(@NotNull String name, @NotNull PieceTemplate template,
                              @NotNull Vec3i staticOffset, @NotNull OffsetMode offsetMode,
                              @Nullable StructureCondition<?> condition,
                              @NotNull String anchorPieceName,
                              @NotNull int[] anchorStep,
                              boolean toolingVisible) {
        super(name, template, staticOffset, offsetMode, condition,
                (snap, origin, orientation, prior, runtime, session) -> false, toolingVisible);
        if (anchorStep.length != 3) {
            throw new IllegalArgumentException("anchorStep must be a 3-element array (right, up, back)");
        }
        this.anchorPieceName = anchorPieceName;
        this.anchorStep = anchorStep.clone();
    }

    @NotNull
    public String getAnchorPieceName() {
        return anchorPieceName;
    }

    @NotNull
    public int[] getAnchorStep() {
        return anchorStep.clone();
    }

    @NotNull
    @Override
    public BlockPos getCenterPos(@NotNull BlockPos controllerPos,
                                 @NotNull StructureOrientation orientation,
                                 @Nullable FormedStructureMetadata prior) {
        // No prior metadata -> fall back to static position. This handles single
        // piece structures and the (legal) case where this piece is checked
        // before the anchor.
        if (prior == null) {
            return super.getCenterPos(controllerPos, orientation);
        }

        BlockPos anchorCenter = prior.getPieceCenter(anchorPieceName);
        if (anchorCenter == null) {
            return super.getCenterPos(controllerPos, orientation);
        }

        int[] anchorReps = prior.getPieceRepeats(anchorPieceName);
        int count = anchorReps.length == 0 ? 1 : anchorReps[0];

        Vec3i baseOffset = super.getOffset();
        int[] dynamic = {
                baseOffset.getX() + anchorStep[0] * count,
                baseOffset.getY() + anchorStep[1] * count,
                baseOffset.getZ() + anchorStep[2] * count
        };
        return super.getOffsetMode().apply(anchorCenter, dynamic, orientation);
    }

}
