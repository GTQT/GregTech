package gregtech.api.pattern;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;

/**
 * Defines how a {@link StructurePiece}'s center offset is interpreted
 * relative to the controller position and facing directions.
 *
 * <p>When building multi-piece patterns, each piece has a center position
 * that is offset from the controller. This enum controls how that offset
 * is applied depending on the controller's rotation state.
 *
 * @see StructurePiece
 * @see MultiPiecePattern
 */
public enum OffsetMode {

    /**
     * The offset is specified in absolute world coordinates (x, y, z deltas).
     * The offset does NOT rotate with the controller's front facing.
     * Useful for pieces that are always in the same relative world position.
     */
    ABSOLUTE {
        @Override
        public BlockPos apply(@NotNull BlockPos controllerPos, int[] offset,
                              @NotNull EnumFacing frontFacing, @NotNull EnumFacing upFacing) {
            return controllerPos.add(offset[0], offset[1], offset[2]);
        }
    },

    /**
     * The offset is specified in structure-relative coordinates (right, up, back)
     * and rotates with the controller's front facing.
     * This is the most common mode — the piece position follows the controller's orientation.
     *
     * <p>Coordinate system (looking from the controller's front face into the structure):
     * <ul>
     *   <li>offset[0] = right (positive = to the controller's right)</li>
     *   <li>offset[1] = up (positive = upward in structure space)</li>
     *   <li>offset[2] = back (positive = behind the controller / deeper into the structure)</li>
     * </ul>
     *
     * <p>Note: The {@code frontFacing} parameter follows the post-refactor convention and is
     * {@link gregtech.api.metatileentity.multiblock.MultiblockControllerBase#getFrontFacingForStructure()}
     * — i.e., the controller's actual front for FRONT templates and the opposite for BACK
     * templates. The "back" direction (offset[2]) is always opposite to this value, which
     * represents the direction the structure extends away from the controller.</p>
     */
    RELATIVE {
        @Override
        public BlockPos apply(@NotNull BlockPos controllerPos, int[] offset,
                              @NotNull EnumFacing frontFacing, @NotNull EnumFacing upFacing) {
            // frontFacing = controller.getFrontFacingForStructure()
            // back = opposite, i.e., into the structure (always "behind" the controller)
            EnumFacing back = frontFacing.getOpposite();
            // Right = looking from front face into structure, right side.
            // Vertical facings cannot use rotateYCCW(), so derive the axis from the structure up vector.
            EnumFacing right = deriveRight(frontFacing, upFacing);

            int dx = right.getXOffset() * offset[0] + upFacing.getXOffset() * offset[1] + back.getXOffset() * offset[2];
            int dy = right.getYOffset() * offset[0] + upFacing.getYOffset() * offset[1] + back.getYOffset() * offset[2];
            int dz = right.getZOffset() * offset[0] + upFacing.getZOffset() * offset[1] + back.getZOffset() * offset[2];

            return controllerPos.add(dx, dy, dz);
        }
    },

    /**
     * The offset rotates with the controller's front facing on the horizontal plane only.
     * Vertical (Y) component of the offset is kept absolute.
     * Useful for large structures where pieces are at fixed heights but rotate horizontally.
     *
     * <p>Note: The {@code frontFacing} parameter follows the post-refactor convention and is
     * {@link gregtech.api.metatileentity.multiblock.MultiblockControllerBase#getFrontFacingForStructure()}.
     * </p>
     */
    HORIZONTAL_RELATIVE {
        @Override
        public BlockPos apply(@NotNull BlockPos controllerPos, int[] offset,
                              @NotNull EnumFacing frontFacing, @NotNull EnumFacing upFacing) {
            // Only apply horizontal rotation for X/Z, keep Y absolute
            // frontFacing = controller.getFrontFacingForStructure()
            EnumFacing back = frontFacing.getOpposite();
            EnumFacing right = deriveRight(frontFacing, upFacing);

            int dx = right.getXOffset() * offset[0] + back.getXOffset() * offset[2];
            int dy = offset[1];
            int dz = right.getZOffset() * offset[0] + back.getZOffset() * offset[2];

            return controllerPos.add(dx, dy, dz);
        }
    };

    /**
     * Apply this offset mode to compute the actual world position of a piece center.
     *
     * @param controllerPos the controller's position
     * @param offset        the raw offset [3 ints]
     * @param frontFacing   {@link gregtech.api.metatileentity.multiblock.MultiblockControllerBase#getFrontFacingForStructure()}
     *                      (the controller's actual front for FRONT templates, opposite for BACK templates)
     * @param upFacing      the controller's upward facing
     * @return the computed world position for the piece center
     */
    public abstract BlockPos apply(@NotNull BlockPos controllerPos, int[] offset,
                                   @NotNull EnumFacing frontFacing, @NotNull EnumFacing upFacing);

    /**
     * Derive the "right" direction from front and up for non-standard orientations.
     * Uses cross product: right = up × front
     */
    private static EnumFacing deriveRight(@NotNull EnumFacing front, @NotNull EnumFacing up) {
        // Cross product: up × front
        int rx = up.getYOffset() * front.getZOffset() - up.getZOffset() * front.getYOffset();
        int ry = up.getZOffset() * front.getXOffset() - up.getXOffset() * front.getZOffset();
        int rz = up.getXOffset() * front.getYOffset() - up.getYOffset() * front.getXOffset();
        // Find the axis-aligned facing closest to the cross product
        for (EnumFacing f : EnumFacing.VALUES) {
            if (f.getXOffset() == rx && f.getYOffset() == ry && f.getZOffset() == rz) {
                return f;
            }
        }
        // Fallback: preserve legacy horizontal behavior, and use a deterministic axis for malformed vertical pairs.
        return front.getAxis().isHorizontal() ? front.rotateYCCW() : EnumFacing.WEST;
    }
}
