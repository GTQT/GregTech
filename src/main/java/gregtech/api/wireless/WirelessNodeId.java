package gregtech.api.wireless;

import net.minecraft.util.math.BlockPos;

import java.util.Objects;
import java.util.UUID;

/**
 * Unique identifier for a wireless storage node (e.g. a PSS with wireless controller).
 * Composed of dimension + block position, which uniquely identifies a tile entity in the world.
 */
public final class WirelessNodeId {

    private final int dimension;
    private final BlockPos pos;

    public WirelessNodeId(int dimension, BlockPos pos) {
        this.dimension = dimension;
        this.pos = pos.toImmutable();
    }

    public int getDimension() {
        return dimension;
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WirelessNodeId that)) return false;
        return dimension == that.dimension && pos.equals(that.pos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimension, pos);
    }

    @Override
    public String toString() {
        return "WirelessNodeId{dim=" + dimension + ", pos=" + pos + "}";
    }
}
