package gregtech.client.gui.gl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Design tokens mirrored from GTCEu Modern's {@code uipro/styletemplate/UISizes} and {@code UITheme}.
 *
 * <p>
 * These are the single source of truth for the self-drawn multiblock tool UI. Layout code must use the names below
 * instead of magic numbers so that the whole tool family stays visually identical to the reference UI and keeps its
 * proportions at every GUI scale.
 */
@SideOnly(Side.CLIENT)
public final class GUITokens {

    private GUITokens() {}

    // ---------------------------------------------------------------------------------------------
    // Metrics (uipro UISizes)
    // ---------------------------------------------------------------------------------------------

    /** Height of buttons, text fields, steppers and other single-line controls. */
    public static final int CONTROL_HEIGHT = 14;
    /** Height of one text line. */
    public static final int TEXT_HEIGHT = 9;
    /** Size of an item/fluid slot. */
    public static final int SLOT_SIZE = 18;
    public static final int SLOTS_PER_ROW = 9;
    /** Gap between sibling controls. */
    public static final int GAP = 2;
    /** Gap between sections inside a card. */
    public static final int SECTION_GAP = 4;
    /** Inner padding of a card/panel. */
    public static final int PANEL_PADDING = 3;
    public static final int BUTTON_WIDTH = 48;
    public static final int SWITCH_WIDTH = 24;
    public static final int VALUE_WIDTH = 28;
    public static final int WINDOW_WIDTH = 176;
    public static final int CONTENT_WIDTH = 162;
    public static final int POPUP_CONTENT_WIDTH = 168;
    public static final int POPUP_PADDING = 5;
    public static final int POPUP_NOTCH_HEIGHT = 4;
    /** Outer margin of floating cards on the preview screen. */
    public static final int MARGIN = 8;
    /** Width of one column of the structure configuration tree. */
    public static final int CONFIG_COLUMN = 104;
    /** Step values used by the tree steppers (layer count etc.). */
    public static final int[] CONFIG_STEPS = { 1, 4 };
    public static final float MAX_WINDOW_SCREEN_RATIO = 2F / 3F;

    // ---------------------------------------------------------------------------------------------
    // Palette (uipro UITheme)
    // ---------------------------------------------------------------------------------------------

    public static final int TEXT = 0xFF202020;
    public static final int TEXT_DIM = 0xFF4B4B4B;
    public static final int TEXT_DISABLED = 0xFF7A7A7A;
    public static final int TEXT_INVERTED = 0xFFFFFFFF;
    public static final int SELECTION = 0xFFFFC83D;
    public static final int WINDOW_BG = 0xFFC6C6C6;
    public static final int PANEL_BG = 0xFFC6C6C6;
    public static final int PANEL_EDGE = 0xFF373737;
    public static final int PANEL_EDGE_LIGHT = 0xFFFFFFFF;
    public static final int CANVAS_BG = 0xFF8B8B8B;
    public static final int CANVAS_MINIMAP_BORDER = 0xFF373737;
    public static final int SLOT_BG = 0xFF8B8B8B;
    public static final int BUTTON_BG = 0xFFC6C6C6;
    public static final int BUTTON_BG_HOVER = 0xFFD8D8D8;
    public static final int BUTTON_BG_DISABLED = 0xFFA0A0A0;
    public static final int WARN = 0xFFD9822B;
    public static final int OK = 0xFF3F8F3F;
    public static final int ERROR = 0xFFB03A2E;

    // Structure preview accents, kept from the existing fullscreen viewer so that block highlighting
    // and selection readouts keep their meaning.
    public static final int HIGHLIGHT = 0x5055FF55;
    public static final int DEMOLISH = 0x50FF3B30;

    // ---------------------------------------------------------------------------------------------
    // Text measurement
    // ---------------------------------------------------------------------------------------------

    /** Matches uipro's width estimate: CJK glyphs 9px, ASCII 6px. */
    public static int widthFor(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += text.charAt(i) < 256 ? 6 : 9;
        }
        return width;
    }

    public static int fontWidth(String text) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        return font == null ? widthFor(text) : font.getStringWidth(text);
    }

    /** Truncates {@code text} with an ellipsis so that it fits {@code maxWidth} pixels. */
    public static String trim(String text, int maxWidth) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        if (font == null || font.getStringWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int limit = maxWidth - font.getStringWidth(ellipsis);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (font.getStringWidth(builder.toString() + text.charAt(i)) > limit) break;
            builder.append(text.charAt(i));
        }
        return builder + ellipsis;
    }
}
