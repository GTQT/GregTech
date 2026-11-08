package gregtech.client.gui.gl;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.function.Consumer;

/**
 * 24x14 on/off switch matching the reference UI: dark track when off, selection-colored track when on.
 */
@SideOnly(Side.CLIENT)
public class GToggle extends GWidget {

    private boolean value;
    private final String label;
    private Consumer<Boolean> listener;

    public GToggle(String label, int width) {
        super(width, GUITokens.CONTROL_HEIGHT);
        this.label = label == null ? "" : label;
    }

    public GToggle value(boolean value) {
        this.value = value;
        return this;
    }

    public boolean getValue() {
        return value;
    }

    public GToggle onChange(Consumer<Boolean> listener) {
        this.listener = listener;
        return this;
    }

    public GToggle setValue(boolean value, boolean notify) {
        if (this.value == value) return this;
        this.value = value;
        if (notify && listener != null) listener.accept(value);
        return this;
    }

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        int labelWidth = label.isEmpty() ? 0 : GDraw.textWidth(label) + 4;
        int switchX = x + width - GUITokens.SWITCH_WIDTH;
        if (labelWidth > 0) {
            GDraw.textTrimmed(label, x, y + (height - 8) / 2, width - GUITokens.SWITCH_WIDTH - 2, GUITokens.TEXT);
        }
        int trackColor = value ? GUITokens.SELECTION : 0xFF8B8B8B;
        GDraw.bevel(switchX, y, GUITokens.SWITCH_WIDTH, height, trackColor, 0xFF555555, 0xFFFFFFFF);
        int knobX = value ? switchX + GUITokens.SWITCH_WIDTH / 2 : switchX + 1;
        GDraw.bevel(knobX, y + 1, GUITokens.SWITCH_WIDTH / 2 - 1, height - 2, 0xFFE9E9E9, 0xFFFFFFFF, 0xFF555555);
        if (contains(mouseX, mouseY)) {
            GDraw.outline(switchX, y, GUITokens.SWITCH_WIDTH, height, 0xFF000000);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        setValue(!value, true);
        return true;
    }
}
