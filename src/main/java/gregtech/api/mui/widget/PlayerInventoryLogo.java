package gregtech.api.mui.widget;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuiTheme;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widget.Widget;
import org.jetbrains.annotations.NotNull;

/**
 * 单方块机器放在玩家背包左上角正上方（也就是配方区左下角）的 LOGO。
 *
 * <p>
 * 玩家背包固定是 4 行 18px 槽位（高 76px）、底边距面板 7px，所以无论配方区占几行，
 * LOGO 的底边都固定在同一位置：{@code 7 + 76 + 2 = 85}。位置常量在这里定义一次，
 * 页面装配处（比如顶部页签给预览页留的高度）直接复用，避免各处再写一遍魔法数。
 */
public final class PlayerInventoryLogo {

    /** LOGO 左边距面板左边缘的距离。 */
    public static final int LEFT_MARGIN = 7;
    /** 玩家背包底边距面板底边的距离。 */
    public static final int BOTTOM_MARGIN = 7;
    /** 玩家背包 4 行槽位的高度（4 × 19px）。 */
    public static final int INVENTORY_HEIGHT = 76;
    /** LOGO 与背包顶边之间留的缝。 */
    public static final int GAP = 2;

    private PlayerInventoryLogo() {}

    /**
     * 构建 LOGO 控件。
     *
     * @param theme 当前界面的主题，决定用哪一套 LOGO 贴图
     * @param size  LOGO 的边长，16 或 17
     */
    public static @NotNull IWidget create(@NotNull GTGuiTheme theme, int size) {
        return new Widget<>()
                .size(size)
                .left(LEFT_MARGIN)
                .bottom(BOTTOM_MARGIN + INVENTORY_HEIGHT + GAP)
                .background(GTGuiTextures.getLogo(theme));
    }
}
