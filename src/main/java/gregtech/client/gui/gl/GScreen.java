package gregtech.client.gui.gl;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Base screen for the self-drawn multiblock tool UIs.
 *
 * <p>
 * It owns a flat widget list, dispatches mouse/keyboard input to the topmost hit widget and draws tooltips on top.
 * Subclasses build and lay out widgets in {@link #buildUi()} / {@link #layoutUi()}.
 */
@SideOnly(Side.CLIENT)
public class GScreen extends GuiScreen {

    protected final List<GWidget> widgets = new ArrayList<>();
    protected int hoveredX = -1;
    protected int hoveredY = -1;

    @Override
    public void initGui() {
        super.initGui();
        widgets.clear();
        buildUi();
        layoutUi();
    }

    /** Creates the widget tree. Called on every {@link #initGui()}. */
    protected void buildUi() {}

    /** Positions widgets after the screen size is known. */
    protected void layoutUi() {}

    protected <T extends GWidget> T add(T widget) {
        widgets.add(widget);
        return widget;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (GWidget widget : widgets) {
            widget.update();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        hoveredX = mouseX;
        hoveredY = mouseY;
        drawBackground(mouseX, mouseY, partialTicks);
        for (GWidget widget : widgets) {
            if (widget.isVisible()) {
                widget.draw(mouseX, mouseY, partialTicks);
            }
        }
        drawForeground(mouseX, mouseY, partialTicks);
        drawTooltips(mouseX, mouseY);
    }

    protected void drawBackground(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
    }

    protected void drawForeground(int mouseX, int mouseY, float partialTicks) {}

    protected void drawTooltips(int mouseX, int mouseY) {
        if (mc == null) return;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            GWidget widget = widgets.get(i);
            if (!widget.isVisible() || !widget.contains(mouseX, mouseY)) continue;
            List<String> lines = widget.tooltip();
            if (lines != null && !lines.isEmpty()) {
                drawHoveringText(lines, mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            GWidget widget = widgets.get(i);
            if (!widget.isVisible() || !widget.isEnabled() || !widget.contains(mouseX, mouseY)) continue;
            if (widget.mouseClicked(mouseX, mouseY, mouseButton)) {
                return;
            }
        }
        GTextField.clearFocus();
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        for (GWidget widget : widgets) {
            if (widget.isVisible()) widget.mouseReleased(mouseX, mouseY, state);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        for (GWidget widget : widgets) {
            if (widget.isVisible()) widget.mouseDragged(mouseX, mouseY, clickedMouseButton);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int delta = wheel > 0 ? 1 : -1;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            GWidget widget = widgets.get(i);
            if (widget.isVisible() && widget.mouseScrolled(mouseX, mouseY, delta)) {
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            GWidget widget = widgets.get(i);
            if (widget.isVisible() && widget.keyTyped(typedChar, keyCode)) {
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    protected void setWidgetVisible(boolean visible, GWidget... targets) {
        for (GWidget widget : targets) {
            if (widget != null) widget.visible(visible);
        }
    }
}
