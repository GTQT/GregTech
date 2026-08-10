package gregtech.api.mattermanipulator;

import java.util.Objects;

import net.minecraft.util.math.BlockPos;

/**
 * Persistent identity of a source endpoint shared by a smart-copy target.
 *
 * <p>The link deliberately identifies only a world position. Implementations
 * own endpoint-specific validation and lifecycle handling.</p>
 */
public final class SmartCopyLink {

    private final int sourceDimension;
    private final BlockPos sourcePosition;

    public SmartCopyLink(int sourceDimension, BlockPos sourcePosition) {
        this.sourceDimension = sourceDimension;
        this.sourcePosition = Objects.requireNonNull(sourcePosition, "sourcePosition").toImmutable();
    }

    public int sourceDimension() {
        return sourceDimension;
    }

    public BlockPos sourcePosition() {
        return sourcePosition;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SmartCopyLink link)) return false;
        return sourceDimension == link.sourceDimension && sourcePosition.equals(link.sourcePosition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceDimension, sourcePosition);
    }

    @Override
    public String toString() {
        return "SmartCopyLink{" + sourceDimension + ":" + sourcePosition + '}';
    }
}
