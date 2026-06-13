package gregtech.api.pattern;

import gregtech.api.util.RelativeDirection;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;

/**
 * Defines how a {@link StructurePiece}'s center offset is interpreted relative
 * to the controller position and orientation.
 */
public enum OffsetMode {

    ABSOLUTE {
        @Override
        public BlockPos apply(@NotNull BlockPos controllerPos, int[] offset,
                              @NotNull EnumFacing frontFacing, @NotNull EnumFacing upwardsFacing,
                              boolean flipped) {
            return controllerPos.add(offset[0], offset[1], offset[2]);
        }
    },

    /**
     * Offset components are structure-local {@code (right, up, back)} values.
     */
    RELATIVE {
        @Override
        public BlockPos apply(@NotNull BlockPos controllerPos, int[] offset,
                              @NotNull EnumFacing frontFacing, @NotNull EnumFacing upwardsFacing,
                              boolean flipped) {
            BlockPos transformed = RelativeDirection.setActualRelativeOffset(
                    offset[0], offset[1], offset[2],
                    frontFacing, upwardsFacing, flipped, PIECE_OFFSET_DIRECTIONS);
            return controllerPos.add(transformed);
        }
    },

    /**
     * X/Z rotate with the structure while Y remains an absolute world offset.
     */
    HORIZONTAL_RELATIVE {
        @Override
        public BlockPos apply(@NotNull BlockPos controllerPos, int[] offset,
                              @NotNull EnumFacing frontFacing, @NotNull EnumFacing upwardsFacing,
                              boolean flipped) {
            BlockPos horizontal = RelativeDirection.setActualRelativeOffset(
                    offset[0], 0, offset[2],
                    frontFacing, upwardsFacing, flipped, PIECE_OFFSET_DIRECTIONS);
            return controllerPos.add(horizontal.getX(), offset[1], horizontal.getZ());
        }
    };

    private static final RelativeDirection[] PIECE_OFFSET_DIRECTIONS = {
            RelativeDirection.RIGHT,
            RelativeDirection.UP,
            RelativeDirection.BACK
    };

    public abstract BlockPos apply(@NotNull BlockPos controllerPos, int[] offset,
                                   @NotNull EnumFacing frontFacing, @NotNull EnumFacing upwardsFacing,
                                   boolean flipped);

    @NotNull
    public BlockPos apply(@NotNull BlockPos controllerPos, int[] offset,
                          @NotNull StructureOrientation orientation) {
        return apply(
                controllerPos,
                offset,
                orientation.getStructureFront(),
                orientation.getUp(),
                orientation.isFlipped());
    }
}
