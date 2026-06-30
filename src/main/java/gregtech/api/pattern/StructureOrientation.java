package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Complete orientation state used by structure operations.
 */
public final class StructureOrientation {

    @NotNull
    private final EnumFacing front;
    @NotNull
    private final EnumFacing structureFront;
    @NotNull
    private final EnumFacing up;
    private final boolean flipped;
    private final boolean allowsFlip;

    private StructureOrientation(@NotNull EnumFacing front,
                                 @NotNull EnumFacing structureFront,
                                 @NotNull EnumFacing up,
                                 boolean flipped,
                                 boolean allowsFlip) {
        this.front = front;
        this.structureFront = structureFront;
        this.up = up;
        this.flipped = flipped;
        this.allowsFlip = allowsFlip;
    }

    @NotNull
    public static StructureOrientation of(@NotNull EnumFacing front,
                                          @NotNull EnumFacing structureFront,
                                          @NotNull EnumFacing up,
                                          boolean flipped,
                                          boolean allowsFlip) {
        return new StructureOrientation(front, structureFront, up, flipped, allowsFlip);
    }

    @NotNull
    public static StructureOrientation fromController(@NotNull MultiblockControllerBase controller) {
        return of(
                controller.getFrontFacing(),
                controller.getFrontFacingForStructure(),
                controller.getUpwardsFacing(),
                controller.isFlipped(),
                controller.allowsFlip());
    }

    @NotNull
    public EnumFacing getFront() {
        return front;
    }

    @NotNull
    public EnumFacing getStructureFront() {
        return structureFront;
    }

    @NotNull
    public EnumFacing getUp() {
        return up;
    }

    public boolean isFlipped() {
        return flipped;
    }

    public boolean allowsFlip() {
        return allowsFlip;
    }

    @NotNull
    public StructureOrientation withFlipped(boolean flipped) {
        if (this.flipped == flipped) {
            return this;
        }
        return of(front, structureFront, up, flipped, allowsFlip);
    }

    /**
     * Compare the stable orientation inputs for a structure check.
     *
     * <p>The current formed flip is deliberately not compared here because an
     * unformed check may try both flipped and unflipped variants from the same
     * captured search orientation.
     */
    public boolean matchesControllerForCheck(@NotNull MultiblockControllerBase controller) {
        return front == controller.getFrontFacing()
                && structureFront == controller.getFrontFacingForStructure()
                && up == controller.getUpwardsFacing()
                && allowsFlip == controller.allowsFlip();
    }

    public boolean matchesControllerExactly(@NotNull MultiblockControllerBase controller) {
        return matchesControllerForCheck(controller)
                && flipped == controller.isFlipped();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StructureOrientation)) {
            return false;
        }
        StructureOrientation other = (StructureOrientation) obj;
        return front == other.front
                && structureFront == other.structureFront
                && up == other.up
                && flipped == other.flipped
                && allowsFlip == other.allowsFlip;
    }

    @Override
    public int hashCode() {
        return Objects.hash(front, structureFront, up, flipped, allowsFlip);
    }

    @Override
    public String toString() {
        return "front=" + front +
                ", structureFront=" + structureFront +
                ", up=" + up +
                ", flipped=" + flipped +
                ", allowsFlip=" + allowsFlip;
    }
}
