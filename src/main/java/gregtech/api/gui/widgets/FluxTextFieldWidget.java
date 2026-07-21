package gregtech.api.gui.widgets;

import gregtech.api.gui.IRenderContext;
import gregtech.api.util.Position;
import gregtech.api.util.Size;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Flux Networks' outlined text field, backed by ModularUI synchronization. */
public class FluxTextFieldWidget extends TextFieldWidget {

    public FluxTextFieldWidget(int x, int y, int width, int height, Supplier<String> textSupplier,
                               Consumer<String> textResponder) {
        super(x, y, width, height, false, textSupplier, textResponder);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawInBackground(int mouseX, int mouseY, float partialTicks, IRenderContext context) {
        Position position = getPosition();
        Size size = getSize();
        drawBorder(position.x, position.y, size.width, size.height, 0xFFB4B4B4, 1);
        drawSolidRect(position.x, position.y, size.width, size.height, 0x20000000);
        textField.drawTextBox();
    }
}
