package gregtech.common.pipelike.fiber;

import gregtech.api.pipenet.block.IPipeType;

import org.jetbrains.annotations.NotNull;

/** There is exactly one kind of fiber: no material variants, no insulation levels. */
public enum FiberPipeType implements IPipeType<FiberPipeProperties> {

    NORMAL;

    public static final FiberPipeType[] VALUES = values();

    @Override
    public float getThickness() {
        return 0.25f;
    }

    @Override
    public FiberPipeProperties modifyProperties(FiberPipeProperties baseProperties) {
        return baseProperties;
    }

    @Override
    public boolean isPaintable() {
        return false;
    }

    @NotNull
    @Override
    public String getName() {
        return "normal";
    }
}
