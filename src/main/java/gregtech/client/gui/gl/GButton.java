package gregtech.client.gui.gl;

import net.minecraft.client.gui.Gui;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Flat vanilla-styled button: 14px high, grey bevel, dark text, selection-colored border on hover.
 */
@SideOnly(Side.CLIENT)
public class GButton extends GWidget {

    private final String label;
    private Runnable action;
    private int textColor = GUITokens.TEXT;
    private boolean toggled = false;

    public GButton(String label) {
        this(label, GUITokens.BUTTON_WIDTH, GUITokens.CONTROL_HEIGHT);
    }

    public GButton(String label, int width, int height) {
        super(width, height);
        this.label = label == null ? "" : label;
    }

    public GButton onClick(Runnable action) {
        this.action = action;
        return this;
    }

    public GButton textColor(int textColor) {
        this.textColor = textColor;
        return this;
    }

    public GButton toggled(boolean toggled) {
        this.toggled = toggled;
        return this;
    }

    public boolean isToggled() {
        return toggled;
    }

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        boolean hover = isEnabled() && contains(mouseX, mouseY);
        int fill = !isEnabled() ? GUITokens.BUTTON_BG_DISABLED
                : hover ? GUITokens.BUTTON_BG_HOVER : GUITokens.BUTTON_BG;
        if (toggled) {
            fill = hover ? 0xFFFFD966 : GUITokens.SELECTION;
        }
        GDraw.bevel(x, y, width, height, fill, 0xFFFFFFFF, 0xFF555555);
        if (hover || toggled) {
            GDraw.outline(x, y, width, height, GUITokens.PANEL_EDGE);
        }
        int color = isEnabled() ? textColor : GUITokens.TEXT_DISABLED;
        int textY = y + (height - 8) / 2;
        if (GDraw.textWidth(label) <= width - 4) {
            GDraw.textCentered(label, x + width / 2, textY, color);
        } else {
            GDraw.textTrimmed(label, x + 3, textY, width - 6, color);
        }
        GDraw.rect(x, y, width, 1, 0x33000000);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !isEnabled()) return false;
        if (action != null) action.run();
        return true;
    }

    /** Convenience for icon buttons: the label is ignored when an icon is supplied. */
    public static GButton icon(String tooltipKey, int size, Runnable action) {
        GButton button = new GButton("", size, size).onClick(action);
        return button;
    }

    protected static void frame(int x, int y, int width, int height) {
        Gui.drawRect(x, y, x + width, y + height, 0xFF000000);
    }
}
