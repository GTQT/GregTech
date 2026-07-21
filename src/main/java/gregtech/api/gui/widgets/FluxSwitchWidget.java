package gregtech.api.gui.widgets;

import gregtech.api.gui.IRenderContext;
import gregtech.api.gui.Widget;
import gregtech.api.gui.resources.FluxWirelessTextures;
import gregtech.api.util.Position;
import gregtech.api.util.Size;
import gregtech.api.util.function.BooleanConsumer;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.function.BooleanSupplier;

/** Flux Networks' sliding switch. */
public class FluxSwitchWidget extends Widget {

    private final BooleanSupplier stateSupplier;
    private final BooleanConsumer stateSetter;
    private boolean state;

    public FluxSwitchWidget(int x, int y, BooleanSupplier stateSupplier, BooleanConsumer stateSetter) {
        super(new Position(x, y), new Size(16, 8));
        this.stateSupplier = stateSupplier;
        this.stateSetter = stateSetter;
    }

    @Override
    public void detectAndSendChanges() {
        if (stateSupplier.getAsBoolean() != state) {
            state = stateSupplier.getAsBoolean();
            writeUpdateInfo(1, buffer -> buffer.writeBoolean(state));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readUpdateInfo(int id, PacketBuffer buffer) {
        if (id == 1) state = buffer.readBoolean();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawInBackground(int mouseX, int mouseY, float partialTicks, IRenderContext context) {
        Position position = getPosition();
        int textureState = state || isMouseOverElement(mouseX, mouseY) ? 0 : 1;
        FluxWirelessTextures.button(16 * textureState, 32, 16, 8).draw(position.x, position.y, 16, 8);
        FluxWirelessTextures.button(16 * textureState, 40, 8, 8).draw(position.x + (state ? 8 : 0), position.y, 8, 8);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!isMouseOverElement(mouseX, mouseY)) return false;
        writeClientAction(1, buffer -> buffer.writeBoolean(!state));
        playButtonClickSound();
        return true;
    }

    @Override
    public void handleClientAction(int id, PacketBuffer buffer) {
        if (id == 1) stateSetter.apply(buffer.readBoolean());
    }
}
