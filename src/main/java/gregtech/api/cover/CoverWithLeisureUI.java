package gregtech.api.cover;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import org.jetbrains.annotations.NotNull;

/**
 * 供实现了独立 GUI 的覆盖板使用。
 * <p>
 * 机器主界面会在侧边按钮栏（{@code col:extra.buttons}）中为该覆盖板放置一个按钮，
 * 点击后在机器界面内部打开覆盖板的子面板，而不是新开一个界面。
 * 典型实现见 {@code gregtech.common.covers.CoverStorage} 与
 * {@code gregtech.common.covers.CoverCraftingTable}。
 */
public interface CoverWithLeisureUI {

    /**
     * 构建机器 GUI 中的"休闲按钮"。
     * <p>
     * 子面板必须使用 {@link PanelSyncManager#syncedPanel} 注册，并且面板名与槽位组名要带上
     * {@code index}，否则同一台机器上的多个同类覆盖板会互相冲突。
     *
     * @param guiData        机器界面的 gui data
     * @param guiSyncManager 机器主面板的同步管理器
     * @param index          覆盖板所在面的索引
     * @return 显示在机器界面上的按钮控件
     */
    @NotNull
    IWidget initUILeisure(@NotNull GuiData guiData, @NotNull PanelSyncManager guiSyncManager, int index);
}
