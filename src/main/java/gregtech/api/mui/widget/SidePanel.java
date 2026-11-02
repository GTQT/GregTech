package gregtech.api.mui.widget;

import gregtech.api.mui.GTGuiTextures;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * 机器主界面左沿外那一列按钮（“侧边栏”）的注册器。
 *
 * <p>
 * 以前这一列是在 {@code MetaTileEntity} 里内联拼出来的：世界预览、升级插件、覆盖板入口各写一段
 * “建控件 → 判空 → child”，子类想加按钮只能整个覆写。现在统一走这里：
 *
 * <ul>
 * <li>布局（位置、间距、反序、背景）只在这个类里定义一次；</li>
 * <li>每个按钮来源都是一个返回 {@code @Nullable IWidget} 的方法，{@link #add} 负责判空；</li>
 * <li>子类/接口只要拿到这个对象就能加按钮，不用关心列是怎么建的；</li>
 * <li>{@link #attachTo} 在列里一颗按钮都没有时不会往主面板上挂空壳。</li>
 * </ul>
 *
 * <p>
 * <b>注册顺序 = 从上到下的显示顺序</b>（底层 {@code reverseLayout(true)} 会把先加的子控件排在上面）。
 * 这个方法在服务端也会跑，所以按钮本身必须两端都能构建，只有图标可以是客户端专属。
 */
public final class SidePanel {

    private final PosGuiData guiData;
    private final PanelSyncManager syncManager;
    private final Flow column;

    public SidePanel(@NotNull PosGuiData guiData, @NotNull PanelSyncManager syncManager) {
        this.guiData = guiData;
        this.syncManager = syncManager;
        this.column = Flow.col()
                .name("side_panel")
                .coverChildren()
                // 右边缘距父容器右边 100% 宽度 == 落在主面板 x=0 处，整列向面板外侧溢出。
                // 别换成 rightRel(0.5f) 之类：值 < 1 时会被 autoAnchor 当成锚点做居中。
                .rightRel(1.0f)
                .reverseLayout(true)
                .padding(2)
                .bottom(16)
                .childPadding(2)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .background(GTGuiTextures.BACKGROUND_POPUP);
    }

    /** 打开界面的玩家/坐标信息，覆盖板入口按钮需要。 */
    public @NotNull PosGuiData getGuiData() {
        return this.guiData;
    }

    /** 主面板的同步管理器，构建按钮和浮动子面板都要用它。 */
    public @NotNull PanelSyncManager getSyncManager() {
        return this.syncManager;
    }

    /** 底层那一列，只有需要改布局（宽度、背景、位置）时才用。 */
    public @NotNull Flow getColumn() {
        return this.column;
    }

    /**
     * 加一颗按钮，排在最下面。
     *
     * @param button 按钮；{@code null} 表示这次不显示（配置关掉、机器没有这个概念等）
     * @return this，方便链式注册
     */
    public @NotNull SidePanel add(@Nullable IWidget button) {
        if (button != null) {
            this.column.child(button);
        }
        return this;
    }

    /**
     * 加一颗按条件构建的按钮：{@code condition} 为假时连控件都不建。
     *
     * <p>
     * 适合“只有某类机器才有”的入口，避免先建出控件再丢掉（控件构建可能顺带注册子面板）。
     */
    public @NotNull SidePanel addIf(boolean condition, @NotNull Supplier<IWidget> button) {
        return condition ? add(button.get()) : this;
    }

    /** 批量加按钮，顺序保持不变。 */
    public @NotNull SidePanel addAll(@NotNull Iterable<? extends IWidget> buttons) {
        for (IWidget button : buttons) {
            add(button);
        }
        return this;
    }

    /** 这一列现在有没有按钮。 */
    public boolean isEmpty() {
        return this.column.getChildren().isEmpty();
    }

    /** 挂到主面板上；列是空的时候什么也不做。 */
    public void attachTo(@NotNull ModularPanel mainPanel) {
        if (!isEmpty()) {
            mainPanel.child(this.column);
        }
    }
}
