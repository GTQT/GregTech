package gregtech.api.mui.widget.side;

import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 侧面总览页一帧内需要的全部状态。
 *
 * <p>
 * 这个类只做数据承载，不引用任何客户端类，服务端也会用它来编码同步值。
 */
public final class SideState {

    /** 没有对应输出面时，同步值低 3 位使用这个哨兵。 */
    public static final int NO_FACE = 7;
    /** 自动输出标记位。 */
    public static final int AUTO_BIT = 1 << 3;
    /** 允许从输出面输入标记位。 */
    public static final int ALLOW_INPUT_BIT = 1 << 4;

    public final @NotNull EnumFacing front;
    public final boolean hasItems;
    public final boolean hasFluids;
    public final boolean itemAuto;
    public final boolean fluidAuto;
    public final boolean itemAllowInput;
    public final boolean fluidAllowInput;
    public final @Nullable EnumFacing itemFace;
    public final @Nullable EnumFacing fluidFace;

    public SideState(@NotNull EnumFacing front, boolean hasItems, boolean hasFluids,
                     @Nullable EnumFacing itemFace, @Nullable EnumFacing fluidFace,
                     boolean itemAuto, boolean fluidAuto,
                     boolean itemAllowInput, boolean fluidAllowInput) {
        this.front = front;
        this.hasItems = hasItems;
        this.hasFluids = hasFluids;
        this.itemFace = itemFace;
        this.fluidFace = fluidFace;
        this.itemAuto = itemAuto;
        this.fluidAuto = fluidAuto;
        this.itemAllowInput = itemAllowInput;
        this.fluidAllowInput = fluidAllowInput;
    }

    public static int encode(@Nullable EnumFacing face, boolean auto, boolean allowInput) {
        int value = face == null ? NO_FACE : face.getIndex();
        if (auto) value |= AUTO_BIT;
        if (allowInput) value |= ALLOW_INPUT_BIT;
        return value;
    }

    public static @Nullable EnumFacing decodeFace(int value) {
        int index = value & 7;
        return index >= EnumFacing.VALUES.length ? null : EnumFacing.VALUES[index];
    }

    public static boolean decodeAuto(int value) {
        return (value & AUTO_BIT) != 0;
    }

    public static boolean decodeAllowInput(int value) {
        return (value & ALLOW_INPUT_BIT) != 0;
    }
}
