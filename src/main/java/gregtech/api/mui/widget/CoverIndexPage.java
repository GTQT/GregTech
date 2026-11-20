package gregtech.api.mui.widget;

import gregtech.api.cover.Cover;
import gregtech.api.cover.CoverWithUI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTLog;
import gregtech.common.ConfigHolder;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.ITooltip;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 覆盖板入口的公共实现：顶部“覆盖板”页签里的索引格子，以及侧边栏里的入口按钮。
 *
 * <p>
 * 两条入口的差别只在过滤条件：页签里连没有独立界面的覆盖板也列出来当索引，侧边栏只放能打开界面的。
 * 图标统一用覆盖板自己的掉落物，点击走 {@value #OPEN_ACTION} 同步动作，在服务端调用
 * {@link CoverWithUI#openUI} 打开覆盖板自己的界面。
 */
public final class CoverIndexPage {

    /** 覆盖板索引每行放几个，与仓室索引一致。 */
    public static final int COLUMNS = 9;
    /** “打开覆盖板界面”的同步动作名，两端必须一致。 */
    public static final String OPEN_ACTION = "open_cover";

    private CoverIndexPage() {}

    /**
     * 注册“打开覆盖板界面”的同步动作：客户端点格子时只发包，服务端按面索引找回覆盖板并打开它自己的界面。
     *
     * <p>
     * 同名动作重复注册只会覆盖（{@link PanelSyncManager#registerSyncedAction} 内部是 map put），
     * 所以重复调用不会冲突。
     */
    public static void registerOpenAction(@NotNull MetaTileEntity machine, @NotNull PanelSyncManager syncManager) {
        syncManager.registerSyncedAction(OPEN_ACTION, false, true, buffer -> {
            int index = buffer.readByte();
            if (index < 0 || index >= EnumFacing.VALUES.length) return;
            EntityPlayer player = syncManager.getPlayer();
            if (!(player instanceof EntityPlayerMP playerMP)) return;
            Cover cover = machine.getCoverAtSide(EnumFacing.VALUES[index]);
            if (!(cover instanceof CoverWithUI coverWithUI)) return;
            if (!cover.getCoverableView().isValid()) return;
            if (player.world != cover.getWorld()) return;
            coverWithUI.openUI(playerMP);
        });
    }

    /**
     * 覆盖板索引的格子，按 {@link EnumFacing#VALUES} 的顺序排列。
     *
     * <p>
     * 覆盖板的图标就是它自己的物品，提示里带上所在面；有独立界面的覆盖板点击后打开界面，
     * 没有界面的只作为索引项列出。
     */
    public static @NotNull List<IWidget> createCells(@NotNull MetaTileEntity machine,
                                                     @NotNull PanelSyncManager syncManager) {
        return createEntries(machine, syncManager, false);
    }

    /**
     * 侧边栏里的覆盖板入口：只收录能打开界面的覆盖板。
     */
    public static @NotNull List<IWidget> createButtons(@NotNull MetaTileEntity machine,
                                                       @NotNull PanelSyncManager syncManager) {
        return createEntries(machine, syncManager, true);
    }

    private static @NotNull List<IWidget> createEntries(@NotNull MetaTileEntity machine,
                                                        @NotNull PanelSyncManager syncManager,
                                                        boolean uiOnly) {
        List<IWidget> entries = new ArrayList<>();
        for (EnumFacing side : EnumFacing.VALUES) {
            Cover cover = machine.getCoverAtSide(side);
            if (cover == null) continue;

            try {
                IWidget entry = createEntry(syncManager, cover, side, uiOnly);
                if (entry != null) {
                    entries.add(entry);
                }
            } catch (Throwable t) {
                if (ConfigHolder.misc.debug)
                    GTLog.logger.error("[Cover UI] 为 {} 面的覆盖板 {} 构建入口失败",
                            side, cover.getClass().getName(), t);
            }
        }
        return entries;
    }

    /**
     * 覆盖板索引的一个条目。
     *
     * <p>
     * 实现了 {@link CoverWithUI} 的覆盖板，点击时在服务端调用 {@link CoverWithUI#openUI}；
     * 没有完整界面的覆盖板只作为索引项显示（悬停提示里注明没有独立界面）。
     */
    private static @Nullable IWidget createEntry(@NotNull PanelSyncManager syncManager,
                                                 @NotNull Cover cover, @NotNull EnumFacing side,
                                                 boolean uiOnly) {
        if (uiOnly && !(cover instanceof CoverWithUI)) return null;

        ItemStack stack = cover.getDefinition().getDropItemStack();
        if (stack.isEmpty()) return null;

        if (cover instanceof CoverWithUI) {
            if (!cover.getCoverableView().isValid()) return null;
            return createOpenButton(syncManager, side, stack);
        }

        ButtonWidget<?> cell = new ButtonWidget<>()
                .size(18, 18)
                .overlay(new ItemDrawable(stack).asIcon().size(16));
        addTooltip(cell, stack, side);
        cell.addTooltipLine(IKey.lang("gregtech.mui.cover.no_ui").style(TextFormatting.DARK_GRAY));
        return cell;
    }

    /**
     * 打开覆盖板完整界面的按钮：图标是覆盖板物品，点击只在客户端发包，服务端由
     * {@value #OPEN_ACTION} 同步动作执行 {@link CoverWithUI#openUI}。
     */
    public static @NotNull ButtonWidget<?> createOpenButton(@NotNull PanelSyncManager syncManager,
                                                            @NotNull EnumFacing side,
                                                            @NotNull ItemStack stack) {
        ButtonWidget<?> button = new ButtonWidget<>()
                .size(18, 18)
                .overlay(new ItemDrawable(stack).asIcon().size(16))
                .onMousePressed(clickedButton -> {
                    if (clickedButton != 0) return false;
                    syncManager.callSyncedAction(OPEN_ACTION, buffer -> buffer.writeByte(side.getIndex()));
                    return true;
                });
        addTooltip(button, stack, side);
        return button;
    }

    /**
     * 把索引格子排成覆盖板页：每行 {@value #COLUMNS} 个。
     */
    public static @NotNull IWidget createPage(@NotNull List<IWidget> cells) {
        Flow grid = Flow.column().name("cover_grid").pos(6, 6).childPadding(1).coverChildren();
        for (int start = 0; start < cells.size(); start += COLUMNS) {
            Flow row = Flow.row().name("cover_row:" + (start / COLUMNS))
                    .childPadding(1)
                    .coverChildren();
            int end = Math.min(start + COLUMNS, cells.size());
            for (int i = start; i < end; i++) {
                row.child(cells.get(i));
            }
            grid.child(row);
        }
        return new ParentWidget<>().name("mte_page_covers").sizeRel(1f).child(grid);
    }

    private static void addTooltip(@NotNull ITooltip<?> tooltip, @NotNull ItemStack stack,
                                   @NotNull EnumFacing side) {
        tooltip.addTooltipLine(stack.getDisplayName());
        tooltip.addTooltipLine(IKey.lang("gregtech.mui.side." + side.getName()).style(TextFormatting.GRAY));
    }
}
