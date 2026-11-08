package gregtech.client.gui.gl;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal flex layout used by the self-drawn multiblock tool screens.
 *
 * <p>
 * This is deliberately not a general layout engine: it implements the subset of taffy semantics the reference UI
 * relies on — direction, gap, padding, grow, cross-axis alignment and optional wrapping — so that the same token
 * values produce the same proportions at every GUI scale.
 */
public final class FlexBox {

    public enum Dir {
        ROW,
        COLUMN
    }

    public enum Align {
        START,
        CENTER,
        END,
        STRETCH
    }

    public interface Item {

        /** Preferred main-axis size in pixels, or {@code -1} to fill the remaining space. */
        int prefWidth();

        /** Preferred cross-axis size in pixels, or {@code -1} to fill the remaining space. */
        int prefHeight();

        /** Share of the leftover space; ignored when {@link #prefWidth()} is non-negative. */
        float grow();

        void setBounds(int x, int y, int width, int height);
    }

    private final List<Item> items = new ArrayList<>();
    private Dir direction = Dir.COLUMN;
    private Align align = Align.START;
    private int gap = GUITokens.GAP;
    private int padding = GUITokens.PANEL_PADDING;
    private boolean wrap = false;
    private int x;
    private int y;
    private int width;
    private int height;

    public FlexBox direction(Dir direction) {
        this.direction = direction;
        return this;
    }

    public FlexBox align(Align align) {
        this.align = align;
        return this;
    }

    public FlexBox gap(int gap) {
        this.gap = gap;
        return this;
    }

    public FlexBox padding(int padding) {
        this.padding = padding;
        return this;
    }

    public FlexBox wrap(boolean wrap) {
        this.wrap = wrap;
        return this;
    }

    public FlexBox add(Item item) {
        if (item != null) items.add(item);
        return this;
    }

    public void clear() {
        items.clear();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        layout();
    }

    /** Lays the current item list out inside the last bounds. */
    public void layout() {
        if (items.isEmpty()) return;
        boolean row = direction == Dir.ROW;
        int innerW = Math.max(0, width - padding * 2);
        int innerH = Math.max(0, height - padding * 2);
        if (row) {
            layoutRow(innerW, innerH);
        } else {
            layoutColumn(innerW, innerH);
        }
    }

    private void layoutColumn(int innerW, int innerH) {
        int count = items.size();
        int fixed = 0;
        float grow = 0;
        for (Item item : items) {
            int pref = item.prefHeight();
            if (pref >= 0) fixed += pref;
            else grow += Math.max(0.0001F, item.grow());
        }
        int gaps = gap * (count - 1);
        int remaining = innerH - fixed - gaps;
        int cursor = y + padding;
        for (Item item : items) {
            int prefH = item.prefHeight();
            int h = prefH >= 0 ? prefH : share(remaining, item, grow);
            h = Math.max(1, h);
            int w = innerW;
            int ix = x + padding;
            int prefW = item.prefWidth();
            if (prefW >= 0 && prefW < innerW) {
                w = prefW;
                if (align == Align.CENTER) ix += (innerW - prefW) / 2;
                else if (align == Align.END) ix += innerW - prefW;
            }
            item.setBounds(ix, cursor, w, h);
            cursor += h + gap;
        }
    }

    private void layoutRow(int innerW, int innerH) {
        int count = items.size();
        if (wrap) {
            layoutRowWrapped(innerW, innerH);
            return;
        }
        int fixed = 0;
        float grow = 0;
        for (Item item : items) {
            int pref = item.prefWidth();
            if (pref >= 0) fixed += pref;
            else grow += Math.max(0.0001F, item.grow());
        }
        int gaps = gap * (count - 1);
        int remaining = innerW - fixed - gaps;
        int cursor = x + padding;
        for (Item item : items) {
            int prefW = item.prefWidth();
            int w = prefW >= 0 ? prefW : share(remaining, item, grow);
            w = Math.max(1, w);
            int h = innerH;
            int iy = y + padding;
            int prefH = item.prefHeight();
            if (prefH >= 0 && prefH < innerH) {
                h = prefH;
                if (align == Align.CENTER) iy += (innerH - prefH) / 2;
                else if (align == Align.END) iy += innerH - prefH;
            }
            item.setBounds(cursor, iy, w, h);
            cursor += w + gap;
        }
    }

    private void layoutRowWrapped(int innerW, int innerH) {
        int cursorX = x + padding;
        int cursorY = y + padding;
        int lineHeight = 0;
        int maxX = x + padding + innerW;
        for (Item item : items) {
            int prefW = Math.max(1, item.prefWidth());
            int prefH = Math.max(1, item.prefHeight());
            if (cursorX > x + padding && cursorX + prefW > maxX) {
                cursorX = x + padding;
                cursorY += lineHeight + gap;
                lineHeight = 0;
            }
            item.setBounds(cursorX, cursorY, prefW, prefH);
            cursorX += prefW + gap;
            lineHeight = Math.max(lineHeight, prefH);
        }
    }

    private static int share(int remaining, Item item, float totalGrow) {
        if (remaining <= 0) return 1;
        if (totalGrow <= 0) return remaining;
        return (int) (remaining * Math.max(0.0001F, item.grow()) / totalGrow);
    }
}
