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
}
