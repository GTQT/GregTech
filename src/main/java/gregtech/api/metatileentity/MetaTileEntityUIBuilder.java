package gregtech.api.metatileentity;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.widget.CoverIndexPage;
import gregtech.api.mui.widget.PlayerInventoryLogo;
import gregtech.api.mui.widget.SidePanel;
import gregtech.api.mui.widget.UpgradeSlotsPanel;
import gregtech.api.mui.widget.side.SideOverviewPage;
import gregtech.common.ConfigHolder;

import net.minecraft.world.World;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static gregtech.common.items.MetaItems.ROBOT_ARM_LV;

/**
 * {@link MetaTileEntity} 的界面装配器：把原先堆在机器类里的界面代码集中到这里。
 *
 * <p>
 * 这里只负责拼界面，不碰游戏逻辑：
 *
 * <ul>
 * <li>{@link #initSidePanel}：右侧那一列按钮（升级插件、覆盖板入口）；</li>
 * <li>{@link #attachTabs}：顶部的机器/覆盖板/朝向预览页签；</li>
 * <li>{@link #registerDefaultButtons}、{@link #buildWorldPreviewPage}：上面两处用到的默认内容。</li>
 * </ul>
 *
 * <p>
 * 它和 {@code MetaTileEntity} 放在同一个包，是为了直接调用机器上的 {@code protected} 扩展点
 * （{@link MetaTileEntity#registerSidePanelButtons}、{@link MetaTileEntity#createWorldPreviewPage}），
 * 不额外扩大 API。具体控件实现在 {@link CoverIndexPage}、{@link PlayerInventoryLogo}、
 * {@link UpgradeSlotsPanel}、{@link SideOverviewPage} 里。
 */
public final class MetaTileEntityUIBuilder {

    private final MetaTileEntity machine;

    public MetaTileEntityUIBuilder(@NotNull MetaTileEntity machine) {
        this.machine = machine;
    }

    /**
     * 侧边栏入口：注册覆盖板动作 → 建列 → 交给机器收集按钮 → 挂到主面板。
     *
     * <p>
     * 覆盖板入口（侧栏按钮或顶部页签里的索引格子）共用这个动作；{@code MetaTileEntityGuiFactory}
     * 在 {@link #attachTabs} 之后调用这里，两端都会执行。
     */
    public void initSidePanel(@NotNull PosGuiData guiData, @NotNull PanelSyncManager syncManager,
                              @NotNull ModularPanel mainPanel) {
        CoverIndexPage.registerOpenAction(this.machine, syncManager);
        SidePanel sidePanel = new SidePanel(guiData, syncManager);
        this.machine.registerSidePanelButtons(sidePanel);
        sidePanel.attachTo(mainPanel);
    }

    /**
     * 默认按钮集合：多方块控制器 = 升级插件 + 覆盖板；其它机器只有升级插件。
     */
    public void registerDefaultButtons(@NotNull SidePanel sidePanel) {
        if (this.machine instanceof MultiblockControllerBase) {
            sidePanel.add(createUpgradeSlotsButton(sidePanel.getSyncManager()));
            sidePanel.addAll(CoverIndexPage.createButtons(this.machine, sidePanel.getSyncManager()));
            return;
        }
        sidePanel.add(createUpgradeSlotsButton(sidePanel.getSyncManager()));
    }

    /**
     * 升级插件的入口按钮：只有实现了 {@link IUpgradeSlots} 的机器才有。
     *
     * <p>
     * 返回 {@code null} 表示这台机器没有升级槽位；按钮和面板的细节都在 {@link UpgradeSlotsPanel} 里。
     */
    private @Nullable IWidget createUpgradeSlotsButton(@NotNull PanelSyncManager syncManager) {
        if (this.machine instanceof IUpgradeSlots upgradeSlots) {
            return UpgradeSlotsPanel.create(syncManager, upgradeSlots.getUpgradeInventory(),
                    this.machine.getStackForm());
        }
        return null;
    }

    /**
     * 把覆盖板与朝向预览做成窗口顶部的页签（单方块机器、仓使用）。
     *
     * <p>
     * 页 0 是机器原本的界面，后面依次是覆盖板页、朝向预览页，页签条与多方块控制器外观一致
     * （悬在面板上沿外）。没有覆盖板、也没有可用的朝向预览时什么都不做，界面保持原样。
     *
     * <p>
     * 必须在 {@link #initSidePanel} <b>之前</b>调用：这里会把面板现有子控件整体搬进页 0，
     * 而侧边栏是直接挂在面板上的，顺序反了会把侧栏一起搬进去。
     */
    public void attachTabs(@NotNull ModularPanel panel, @NotNull PanelSyncManager syncManager) {
        // 多方块控制器已有自己的信息/仓室/朝向页签，不能再套一层。
        if (this.machine instanceof MultiblockControllerBase) return;

        List<IWidget> coverCells = CoverIndexPage.createCells(this.machine, syncManager);

        List<IWidget> children = new ArrayList<>(panel.getChildren());
        // 玩家背包槽位是附着在面板上的槽位组，切页时不能跟着页 0 一起消失。
        List<IWidget> persistentChildren = new ArrayList<>();
        children.removeIf(child -> {
            if (child instanceof SlotGroupWidget) {
                persistentChildren.add(child);
                return true;
            }
            return false;
        });

        // 背包高 76、底部边距 7；预览页按这个预留高度动态计算正方形边长。
        int previewReserve = persistentChildren.isEmpty() ? 0
                : PlayerInventoryLogo.BOTTOM_MARGIN + PlayerInventoryLogo.INVENTORY_HEIGHT;
        boolean canPreview = ConfigHolder.client.enableMachineWorldPreview && this.machine.getWorld() != null;
        if (coverCells.isEmpty() && !canPreview) return;

        panel.removeAll();

        PagedWidget.Controller controller = new PagedWidget.Controller();
        PagedWidget<?> pages = new PagedWidget<>().name("mte_pages").sizeRel(1f).controller(controller);

        ParentWidget<?> machinePage = new ParentWidget<>().name("mte_page_machine").sizeRel(1f);
        for (IWidget child : children) {
            machinePage.child(child);
        }
        pages.addPage(machinePage);

        Flow tabRow = Flow.row().name("mte_tab_row")
                .widthRel(1f)
                .leftRel(0.5f)
                .margin(3, 0)
                .coverChildrenHeight()
                .topRel(0f, 3, 1f)
                .child(new PageButton(0, controller).tab(GuiTextures.TAB_TOP, 0)
                        .addTooltipLine(IKey.lang("gregtech.mui.mte.tab.machine"))
                        .overlay(new ItemDrawable(this.machine.getStackForm()).asIcon().size(16)));

        int pageIndex = 1;
        if (!coverCells.isEmpty()) {
            pages.addPage(CoverIndexPage.createPage(coverCells));
            tabRow.child(new PageButton(pageIndex++, controller).tab(GuiTextures.TAB_TOP, 0)
                    .addTooltipLine(IKey.lang("gregtech.mui.mte.tab.covers"))
                    .overlay(new ItemDrawable(ROBOT_ARM_LV.getStackForm()).asIcon().size(16)));
        }
        IWidget previewPage = this.machine.createWorldPreviewPage(syncManager, previewReserve);
        if (previewPage != null) {
            pages.addPage(previewPage);
            tabRow.child(new PageButton(pageIndex, controller).tab(GuiTextures.TAB_TOP, 0)
                    .addTooltipLine(IKey.lang("gregtech.mui.mte.tab.preview"))
                    .overlay(GTGuiTextures.BUTTON_STRUCTURE.asIcon().size(16)));
        }

        panel.child(pages);
        for (IWidget child : persistentChildren) {
            panel.child(child);
        }
        panel.child(tabRow);
    }

    /**
     * 朝向预览页的默认实现：{@link SideOverviewPage}。
     *
     * <p>
     * 子类可以覆写 {@link MetaTileEntity#createWorldPreviewPage} 换成别的预览；{@code null} 表示
     * 这次不显示预览页。
     */
    public @Nullable IWidget buildWorldPreviewPage(@NotNull PanelSyncManager syncManager, int bottomReserve) {
        if (!ConfigHolder.client.enableMachineWorldPreview) return null;

        World world = this.machine.getWorld();
        if (world == null) return null;

        return SideOverviewPage.create(syncManager, this.machine, 0, 0, bottomReserve, true);
    }
}
