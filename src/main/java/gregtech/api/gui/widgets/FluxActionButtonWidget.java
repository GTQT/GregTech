package gregtech.api.gui.widgets;

import gregtech.api.gui.IRenderContext;
import gregtech.api.gui.resources.FluxWirelessTextures;
import gregtech.api.util.Position;
import gregtech.api.util.Size;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.input.Keyboard;

import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Flux Networks' outlined normal button. */
public class FluxActionButtonWidget extends ClickButtonWidget {

    private BooleanSupplier enabledSupplier = () -> true;
    private boolean requiresDoubleShift;
    private int shiftPresses;

    public FluxActionButtonWidget(int x, int y, int width, String text, Consumer<ClickData> onPressed) {
        super(x, y, width, 12, text, onPressed);
    }

    public FluxActionButtonWidget setEnabledSupplier(BooleanSupplier enabledSupplier) {
        this.enabledSupplier = enabledSupplier;
        return this;
    }

    public FluxActionButtonWidget requireDoubleShift() {
        this.requiresDoubleShift = true;
        return this;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawInBackground(int mouseX, int mouseY, float partialTicks, IRenderContext context) {
        Position position = getPosition();
        Size size = getSize();
        boolean enabled = isEnabled();
        int color = enabled && isMouseOverElement(mouseX, mouseY) ? FluxWirelessTextures.NETWORK_COLOR :
                darken(FluxWirelessTextures.NETWORK_COLOR);
        if (!enabled) color = darken(color);
        drawBorder(position.x, position.y, size.width, size.height, color, 1);
        FontRenderer renderer = Minecraft.getMinecraft().fontRenderer;
        String text = I18n.format(displayText);
        renderer.drawString(text, position.x + (size.width - renderer.getStringWidth(text)) / 2, position.y + 2,
                color);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!isMouseOverElement(mouseX, mouseY)) return false;
        if (!isEnabled()) return true;
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (handled && requiresDoubleShift) shiftPresses = 0;
        return handled;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean keyTyped(char charTyped, int keyCode) {
        if (!requiresDoubleShift) return false;
        if (keyCode == Keyboard.KEY_LSHIFT || keyCode == Keyboard.KEY_RSHIFT) {
            shiftPresses = Math.min(2, shiftPresses + 1);
        } else {
            shiftPresses = 0;
        }
        return false;
    }

    @SideOnly(Side.CLIENT)
    private boolean isEnabled() {
        return enabledSupplier.getAsBoolean() && (!requiresDoubleShift || shiftPresses >= 2);
    }

    private static int darken(int color) {
        return 0xFF000000 |
                ((int) (((color >> 16) & 0xFF) * 0.7F) << 16) |
                ((int) (((color >> 8) & 0xFF) * 0.7F) << 8) |
                (int) ((color & 0xFF) * 0.7F);
    }
}
