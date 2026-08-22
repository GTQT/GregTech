package gregtech.api.metatileentity.multiblock;

/**
 * 标记可参与染色隔离(颜色通道分组)的多方块仓,仅输入仓实现。
 * 渲染时在方块右上角显示 2×2px 颜色标记(见 {@link gregtech.api.metatileentity.MetaTileEntity#renderMetaTileEntity})。
 */
public interface IColorChannelPart {

    /**
     * 是否显示颜色通道色块标记。
     *
     * @return 默认 true;输入输出双功能仓在输出态应返回 false
     */
    default boolean showColorChannelPatch() {
        return true;
    }
}
