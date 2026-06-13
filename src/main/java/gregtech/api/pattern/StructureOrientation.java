package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.NotNull;

/**
 * Complete orientation state used by structure operations.
 *
 * <p>This is intentionally a small value object for now. Existing matchers
 * still receive the legacy {@code structureFront/up/flipped} arguments, but
 * callers can capture and compare the whole orientation consistently before the
 * traversal engine is migrated to accept this type directly.
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
    public static StructureOrientation legacy(@NotNull EnumFacing front,
                                              @NotNull EnumFacing up,
                                              boolean flipped,
                                              boolean allowsFlip) {
        return of(front, front, up, flipped, allowsFlip);
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
    public String toString() {
        return "front=" + front +
                ", structureFront=" + structureFront +
                ", up=" + up +
                ", flipped=" + flipped +
                ", allowsFlip=" + allowsFlip;
    }
}
