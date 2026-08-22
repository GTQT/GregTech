package gregtech.api.metatileentity.multiblock;

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
