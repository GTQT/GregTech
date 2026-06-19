package gregtech.api.pattern;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;

/**
 * Shared coordinate input for structure cell traversal.
 */
public final class StructureCellTraversal {

    @NotNull
    private final BlockPos centerPos;
    @NotNull
    private final StructureOrientation orientation;
    private final int xOffset;
    private final int yOffset;
    private final int zOffset;

    private StructureCellTraversal(@NotNull BlockPos centerPos,
                                   @NotNull StructureOrientation orientation,
                                   int xOffset, int yOffset, int zOffset) {
        this.centerPos = centerPos;
        this.orientation = orientation;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        this.zOffset = zOffset;
    }

    @NotNull
    public static StructureCellTraversal at(@NotNull BlockPos centerPos,
                                            @NotNull StructureOrientation orientation) {
        return new StructureCellTraversal(centerPos, orientation, 0, 0, 0);
    }

    @NotNull
    public StructureCellTraversal withLocalOffset(int xOffset, int yOffset, int zOffset) {
        if (this.xOffset == xOffset && this.yOffset == yOffset && this.zOffset == zOffset) {
            return this;
        }
        return new StructureCellTraversal(centerPos, orientation, xOffset, yOffset, zOffset);
    }

    @NotNull
    public BlockPos getCenterPos() {
        return centerPos;
    }

    @NotNull
    public StructureOrientation getOrientation() {
        return orientation;
    }

    public int getXOffset() {
        return xOffset;
    }

    public int getYOffset() {
        return yOffset;
    }

    public int getZOffset() {
        return zOffset;
    }
}
