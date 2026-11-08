package gregtech.client.gui.gl;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Clipped, scrollable row list. Rows are rendered by a callback so callers can draw text, icons or composite
 * controls without subclassing.
 */
@SideOnly(Side.CLIENT)
public class GScrollList<T> extends GWidget {

    public interface RowRenderer<T> {

        void draw(T entry, int index, int x, int y, int width, int height, boolean hovered);
    }

    public interface RowClickListener<T> {

        /** @return true when the click was consumed. */
        boolean onClick(T entry, int index, int mouseX, int mouseY, int button);
    }

    private final List<T> entries = new ArrayList<>();
    private final RowRenderer<T> renderer;
    private RowClickListener<T> clickListener;
    private int rowHeight = 18;
    private int scroll;
    private String emptyText = "";

    public GScrollList(RowRenderer<T> renderer) {
        super(-1, -1);
        this.renderer = renderer;
        this.grow = 1F;
    }

    public GScrollList<T> rows(List<T> entries) {
        this.entries.clear();
        if (entries != null) this.entries.addAll(entries);
        clampScroll();
        return this;
    }

    public List<T> entries() {
        return entries;
    }

    public GScrollList<T> rowHeight(int rowHeight) {
        this.rowHeight = Math.max(1, rowHeight);
        return this;
    }

    public GScrollList<T> emptyText(String emptyText) {
        this.emptyText = emptyText == null ? "" : emptyText;
        return this;
    }

    public GScrollList<T> onClick(RowClickListener<T> clickListener) {
        this.clickListener = clickListener;
        return this;
    }

    public void scrollTo(int index) {
        int target = index * rowHeight;
        if (target < scroll || target + rowHeight > scroll + height) {
            scroll = Math.max(0, target - height / 2);
            clampScroll();
        }
    }

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        GDraw.pushScissor(x, y, width, height);
        if (entries.isEmpty()) {
            if (!emptyText.isEmpty()) {
                GDraw.textCentered(emptyText, x + width / 2, y + height / 2 - 4, GUITokens.TEXT_DIM);
            }
        } else {
            int first = Math.max(0, scroll / rowHeight);
            int last = Math.min(entries.size() - 1, (scroll + height) / rowHeight);
            for (int i = first; i <= last; i++) {
                int rowY = y + i * rowHeight - scroll;
                boolean hovered = isEnabled() && mouseX >= x && mouseX < x + width - 2 && mouseY >= rowY &&
                        mouseY < rowY + rowHeight;
                if (hovered) {
                    GDraw.rect(x, rowY, width - 2, rowHeight, 0x33FFFFC8);
                }
                renderer.draw(entries.get(i), i, x, rowY, width - 2, rowHeight, hovered);
            }
        }
        GDraw.popScissor();
        drawScrollbar();
    }

    private void drawScrollbar() {
        int contentHeight = entries.size() * rowHeight;
        if (contentHeight <= height) return;
        int barX = x + width - 2;
        GDraw.rect(barX, y, 2, height, 0x33000000);
        int thumbHeight = Math.max(8, height * height / contentHeight);
        int maxScroll = contentHeight - height;
        int thumbY = y + (height - thumbHeight) * scroll / Math.max(1, maxScroll);
        GDraw.rect(barX, thumbY, 2, thumbHeight, GUITokens.PANEL_EDGE);
    }

    @Override
    public boolean mouseScrolled(int mouseX, int mouseY, int delta) {
        if (!contains(mouseX, mouseY) || entries.isEmpty()) return false;
        scroll -= delta * rowHeight * 3;
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!contains(mouseX, mouseY) || clickListener == null) return false;
        int index = (mouseY - y + scroll) / rowHeight;
        if (index < 0 || index >= entries.size()) return false;
        return clickListener.onClick(entries.get(index), index, mouseX, mouseY, button);
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, entries.size() * rowHeight - height);
        scroll = Math.max(0, Math.min(maxScroll, scroll));
    }

    @Override
    protected void onBoundsChanged() {
        clampScroll();
    }
}
