package gregtech.api.capability;

import net.minecraft.util.EnumFacing;

public interface IActiveOutputSide {

    boolean isAutoOutputItems();

    boolean isAutoOutputFluids();

    boolean isAllowInputFromOutputSideItems();

    boolean isAllowInputFromOutputSideFluids();

    /**
     * @return 物品输出面，永不为 null
     */
    EnumFacing getOutputFacingItems();

    /**
     * @return 流体输出面，永不为 null
     */
    EnumFacing getOutputFacingFluids();

    void setOutputFacingItems(EnumFacing facing);

    void setOutputFacingFluids(EnumFacing facing);

    /**
     * 未配置输出面时返回 null。{@link #getOutputFacingItems()} 为了兼容旧逻辑会兜底一个面，
     * 侧面总览需要区分“未配置”和“输出面恰好是兜底面”。
     */
    default EnumFacing getConfiguredOutputFacingItems() {
        return getOutputFacingItems();
    }

    default EnumFacing getConfiguredOutputFacingFluids() {
        return getOutputFacingFluids();
    }

    default boolean supportsAutoOutputItems() {
        return false;
    }

    default boolean supportsAutoOutputFluids() {
        return false;
    }

    default boolean supportsAllowInputFromOutputSideItems() {
        return false;
    }

    default boolean supportsAllowInputFromOutputSideFluids() {
        return false;
    }

    default void setAutoOutputItems(boolean autoOutput) {}

    default void setAutoOutputFluids(boolean autoOutput) {}

    default void setAllowInputFromOutputSideItems(boolean allow) {}

    default void setAllowInputFromOutputSideFluids(boolean allow) {}
}
