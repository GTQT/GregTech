package gregtech.client.gui.gl;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.function.IntConsumer;

/**
 * Integer stepper: label on the left, {@code [-][value][+]} on the right, 14px high.
 *
 * <p>
 * Holding shift applies the second configured step (GTO's {@code {1,4}} convention) so coarse adjustments stay
 * one click away.
 */
@SideOnly(Side.CLIENT)
public class GStepper extends GWidget {

    private final String label;
    private int value;
    private final int min;
    private final int max;
    private int step = GUITokens.CONFIG_STEPS[0];
    private int shiftStep = GUITokens.CONFIG_STEPS[1];
    private IntConsumer listener;

    public GStepper(String label, int min, int max, int initial) {
        super(GUITokens.CONFIG_COLUMN, GUITokens.CONTROL_HEIGHT);
        this.label = label == null ? "" : label;
        this.min = min;
        this.max = max;
        this.value = clamp(initial);
    }

    public GStepper onChange(IntConsumer listener) {
        this.listener = listener;
        return this;
    }

    public int getValue() {
        return value;
    }

    public GStepper setValue(int value, boolean notify) {
        int clamped = clamp(value);
        if (clamped == this.value) return this;
        this.value = clamped;
        if (notify && listener != null) listener.accept(clamped);
        return this;
    }

    public GStepper setStep(int step, int shiftStep) {
        this.step = Math.max(1, step);
        this.shiftStep = Math.max(this.step, shiftStep);
        return this;
    }

    private int clamp(int candidate) {
        return Math.max(min, Math.min(max, candidate));
    }

    private int controlsWidth() {
        return 10 + GUITokens.VALUE_WIDTH + 10;
    }

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        int controlsX = x + width - controlsWidth();
        if (!label.isEmpty()) {
            GDraw.textTrimmed(label, x, y + (height - 8) / 2, Math.max(0, controlsX - x - 3), GUITokens.TEXT);
        }
        boolean hoverMinus = mouseX >= controlsX && mouseX < controlsX + 10 && mouseY >= y && mouseY < y + height;
        boolean hoverPlus = mouseX >= controlsX + 10 + GUITokens.VALUE_WIDTH &&
                mouseX < controlsX + controlsWidth() && mouseY >= y && mouseY < y + height;

        GDraw.bevel(controlsX, y, 10, height, hoverMinus ? GUITokens.BUTTON_BG_HOVER : GUITokens.BUTTON_BG,
                0xFFFFFFFF, 0xFF555555);
        GDraw.textCentered("-", controlsX + 5, y + (height - 8) / 2, value > min ? GUITokens.TEXT : GUITokens.TEXT_DISABLED);

        int valueX = controlsX + 10;
        GDraw.sunken(valueX, y, GUITokens.VALUE_WIDTH, height, 0xFFE9E9E9);
        GDraw.textCentered(Integer.toString(value), valueX + GUITokens.VALUE_WIDTH / 2, y + (height - 8) / 2, GUITokens.TEXT);

        int plusX = valueX + GUITokens.VALUE_WIDTH;
        GDraw.bevel(plusX, y, 10, height, hoverPlus ? GUITokens.BUTTON_BG_HOVER : GUITokens.BUTTON_BG,
                0xFFFFFFFF, 0xFF555555);
        GDraw.textCentered("+", plusX + 5, y + (height - 8) / 2, value < max ? GUITokens.TEXT : GUITokens.TEXT_DISABLED);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int controlsX = x + width - controlsWidth();
        int delta = GuiScreen.isShiftKeyDown() ? shiftStep : step;
        if (mouseX >= controlsX && mouseX < controlsX + 10) {
            setValue(value - delta, true);
            return true;
        }
        int plusX = controlsX + 10 + GUITokens.VALUE_WIDTH;
        if (mouseX >= plusX && mouseX < plusX + 10) {
            setValue(value + delta, true);
            return true;
        }
        return false;
    }
}
