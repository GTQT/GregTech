package gregtech.client.gui.gl;

import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Text input built on vanilla {@link GuiTextField}: sunken GT-style frame, focus ring, validator and commit
 * semantics (Enter or focus loss).
 */
@SideOnly(Side.CLIENT)
public class GTextField extends GWidget {

    private static GTextField focused;

    private final GuiTextField field;
    private final Supplier<String> supplier;
    private final Consumer<String> responder;
    private Predicate<String> validator;

    public GTextField(int width, Supplier<String> supplier, Consumer<String> responder) {
        super(width, GUITokens.CONTROL_HEIGHT);
        this.supplier = supplier;
        this.responder = responder;
        this.field = new GuiTextField(0, mc().fontRenderer, 0, 0, width, GUITokens.CONTROL_HEIGHT);
        this.field.setEnableBackgroundDrawing(false);
        this.field.setCanLoseFocus(true);
        this.field.setMaxStringLength(64);
        this.field.setText(supplier == null ? "" : supplier.get());
    }

    public GTextField validator(Predicate<String> validator) {
        this.validator = validator;
        return this;
    }

    public GTextField maxLength(int maxLength) {
        field.setMaxStringLength(maxLength);
        return this;
    }

    public String getText() {
        return field.getText();
    }

    public void setText(String text) {
        field.setText(text == null ? "" : text);
    }

    @Override
    protected void onBoundsChanged() {
        field.x = x + 3;
        field.y = y + (height - 8) / 2;
        field.width = Math.max(8, width - 6);
        field.height = height;
    }

    @Override
    public void update() {
        field.updateCursorCounter();
    }

    @Override
    public void draw(int mouseX, int mouseY, float partialTicks) {
        GDraw.sunken(x, y, width, height, 0xFFE9E9E9);
        if (focused == this) {
            GDraw.outline(x, y, width, height, 0xFF000000);
        } else if (contains(mouseX, mouseY)) {
            GDraw.outline(x, y, width, height, 0xFF6A6A6A);
        }
        field.drawTextBox();
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        if (focused != this) {
            commit();
            focused = this;
        }
        field.setFocused(true);
        field.mouseClicked(mouseX, mouseY, button);
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (focused != this) return false;
        if (keyCode == 1) { // ESC
            field.setFocused(false);
            focused = null;
            return true;
        }
        if (keyCode == 28) { // ENTER
            commit();
            field.setFocused(false);
            focused = null;
            return true;
        }
        if (!field.textboxKeyTyped(typedChar, keyCode)) {
            // Swallow editing keys even when the field does not consume them, so the screen below
            // does not react to typed characters.
            return false;
        }
        return true;
    }

    /** Pushes the current value to the responder if it is valid. */
    public void commit() {
        if (responder == null) return;
        String text = field.getText();
        if (validator == null || validator.test(text)) {
            responder.accept(text);
        } else {
            field.setText(supplier == null ? "" : supplier.get());
        }
    }

    public static void clearFocus() {
        if (focused != null) {
            focused.commit();
            focused.field.setFocused(false);
            focused = null;
        }
    }

    public static boolean hasFocus() {
        return focused != null;
    }
}
