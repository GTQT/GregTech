package gregtech.api.pattern;

import gregtech.api.pattern.element.FormedStructureMetadata;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * A {@link StructurePiece} whose center position is computed dynamically based
 * on the runtime repeat count of another (the "anchor") piece.
 *
 * <p>Used to place fixed pieces that follow a repeatable body in the middle of
 * a structure (e.g. the "top" piece after a "body" piece whose extent is
 * unknown at compile time). The effective offset at check time is:
 *
 * <pre>{@code
 * effectiveOffset = staticBaseOffset + anchorCount * anchorStep
 * }</pre>
 *
 * <p>where {@code anchorCount} is read from the prior pieces' formed metadata
 * (i.e. {@link FormedStructureMetadata#getPieceRepeat(String, int)}), and
 * {@code anchorStep} is a (right, up, back) vector that the compiler
 * determined from the structure's aisle direction and the anchor's step size.
 *
 * <p><b>Caller's contract:</b> when this piece is registered to follow an
 * anchor body, the caller must seed {@code staticBaseOffset} with the body's
 * own baseOffset. Otherwise the formula above misses the body's offset and
 * lands one slice <i>inside</i> the body (overlap with the last body slice).
 *
 * <p>If the prior metadata is null, missing, or the anchor piece is not yet
 * validated, this falls back to the static {@code baseOffset} so that single
 * piece structures and first-time checks behave exactly like a regular
 * {@link StructurePiece}.
 *
 * @see StructurePiece#getCenterPos(BlockPos, EnumFacing, EnumFacing, FormedStructureMetadata)
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
                              @Nullable BooleanSupplier condition,
                              @NotNull String anchorPieceName,
                              @NotNull int[] anchorStep) {
        super(name, template, staticOffset, offsetMode, condition);
        if (anchorStep.length != 3) {
            throw new IllegalArgumentException("anchorStep must be a 3-element array (right, up, back)");
        }
        this.anchorPieceName = anchorPieceName;
        this.anchorStep = anchorStep.clone();
    }

    /**
     * Legacy-path constructor accepting a {@link BlockPatternTemplate} facade.
     */
    public DynamicOffsetPiece(@NotNull String name, @NotNull BlockPatternTemplate template,
                              @NotNull Vec3i staticOffset, @NotNull OffsetMode offsetMode,
                              @Nullable BooleanSupplier condition,
                              @NotNull String anchorPieceName,
                              @NotNull int[] anchorStep) {
        super(name, template, staticOffset, offsetMode, condition);
        if (anchorStep.length != 3) {
            throw new IllegalArgumentException("anchorStep must be a 3-element array (right, up, back)");
        }
        this.anchorPieceName = anchorPieceName;
        this.anchorStep = anchorStep.clone();
    }

    @NotNull
    @Override
    public BlockPos getCenterPos(@NotNull BlockPos controllerPos,
                                 @NotNull EnumFacing frontFacing,
                                 @NotNull EnumFacing upFacing,
                                 @Nullable FormedStructureMetadata prior) {
        // No prior metadata → fall back to static position. This handles single
        // piece structures and the (legal) case where this piece is checked
        // before the anchor — both of which we treat as "anchor not present".
        if (prior == null) {
            System.out.println("[Top build] name=" + getName() + " prior=null -> static fallback");
            return super.getCenterPos(controllerPos, frontFacing, upFacing);
        }

        int[] anchorReps = prior.getPieceRepeats(anchorPieceName);
        if (anchorReps.length == 0) {
            System.out.println("[Top build] name=" + getName() + " anchor=" + anchorPieceName
                    + " reps=empty -> static fallback");
            return super.getCenterPos(controllerPos, frontFacing, upFacing);
        }

        // The repeat count of the anchor along its first (and for our use
        // cases, only) axis. The base piece's first repeat index is 0, so the
        // total world offset is (anchorReps[0]) * anchorStep.
        int count = anchorReps[0];

        Vec3i baseOffset = super.getOffset();
        int[] dynamic = {
                baseOffset.getX() + anchorStep[0] * count,
                baseOffset.getY() + anchorStep[1] * count,
                baseOffset.getZ() + anchorStep[2] * count
        };
        BlockPos result = super.getOffsetMode().apply(controllerPos, dynamic, frontFacing, upFacing);
        System.out.println("[Top build] name=" + getName() + " anchor=" + anchorPieceName
                + " anchorReps=" + java.util.Arrays.toString(anchorReps)
                + " count=" + count
                + " baseOffset=" + baseOffset
                + " anchorStep=" + java.util.Arrays.toString(anchorStep)
                + " dynamic=" + java.util.Arrays.toString(dynamic)
                + " controllerPos=" + controllerPos
                + " front=" + frontFacing + " up=" + upFacing
                + " -> " + result);
        return result;
    }
}
