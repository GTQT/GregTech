package gregtech.api.mui.widget;

import gregtech.api.mui.sync.MultiblockPartsSyncHandler;
import gregtech.api.mui.sync.MultiblockPartsSyncHandler.PartEntry;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 多方块控制器界面里的仓室索引页：以图标网格列出整个多方块包含的全部仓室，
 * 点击某个仓室的图标就会打开该仓室自己的界面。
 */
public class MultiblockPartsWidget extends ParentWidget<MultiblockPartsWidget> {

    private static final int COLUMNS = 9;
    private static final int CELL_SIZE = 18;

    private final MultiblockPartsSyncHandler syncHandler;
    private int builtVersion = -1;

    public MultiblockPartsWidget(MultiblockPartsSyncHandler syncHandler) {
        this.syncHandler = syncHandler;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (syncHandler.getVersion() != builtVersion) {
            builtVersion = syncHandler.getVersion();
            rebuild();
        }
    }

    private void rebuild() {
        removeAll();

        List<PartEntry> parts = new ArrayList<>(syncHandler.getParts());
        if (parts.isEmpty()) {
            child(IKey.lang("gregtech.multiblock.tab.parts.empty")
                    .asWidget()
                    .pos(4, 4));
            scheduleResize();
            return;
        }

        parts.sort(Comparator.comparing(entry -> entry.stack().getDisplayName(), String.CASE_INSENSITIVE_ORDER));

        List<IWidget> rows = new ArrayList<>();
        for (int start = 0; start < parts.size(); start += COLUMNS) {
            Flow row = Flow.row()
                    .name("part_row:" + rows.size())
                    .widthRel(1f)
                    .height(CELL_SIZE)
                    .childPadding(1);
            int end = Math.min(parts.size(), start + COLUMNS);
            for (int i = start; i < end; i++) {
                row.child(createPartButton(parts.get(i)));
            }
            rows.add(row);
        }

        PartsList list = new PartsList();
        list.name("part_list")
                .sizeRel(1f)
                .scrollDirection(new VerticalScrollData())
                .children(rows);
        child(list);
        scheduleResize();
    }

    private IWidget createPartButton(PartEntry entry) {
        ItemStack stack = entry.stack();
        return new ButtonWidget<>()
                .name("part:" + entry.pos().getX() + "," + entry.pos().getY() + "," + entry.pos().getZ())
                .size(CELL_SIZE)
                .overlay(new ItemDrawable(stack).asIcon().size(16))
                .onMousePressed(mouseButton -> {
                    if (mouseButton != 0) return false;
                    syncHandler.requestOpen(entry.pos());
                    return true;
                })
                .addTooltipLine(stack.getDisplayName())
                .addTooltipLine(IKey.str(String.format("(%d, %d, %d)",
                        entry.pos().getX(), entry.pos().getY(), entry.pos().getZ()))
                        .style(TextFormatting.DARK_GRAY));
    }

    private static final class PartsList extends ListWidget<IWidget, PartsList> {}
}
