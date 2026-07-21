package gregtech.api.gui.widgets;

import gregtech.api.gui.IRenderContext;
import gregtech.api.gui.Widget;
import gregtech.api.gui.resources.FluxWirelessTextures;
import gregtech.api.util.Position;
import gregtech.api.util.Size;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Flux-coloured channel bar. Left-click advances; right-click reverses. */
public class FluxChannelSelectorWidget extends Widget {

    private final Supplier<String> labelSupplier;
    private final IntConsumer cycleExecutor;
    private String label = "";

    public FluxChannelSelectorWidget(int x, int y, Supplier<String> labelSupplier, IntConsumer cycleExecutor) {
        super(new Position(x, y), new Size(135, 12));
        this.labelSupplier = labelSupplier;
        this.cycleExecutor = cycleExecutor;
    }

    public FluxChannelSelectorWidget(int x, int y, Supplier<String> labelSupplier) {
        this(x, y, labelSupplier, null);
    }

    @Override
    public void detectAndSendChanges() {
        String current = labelSupplier.get();
        if (!current.equals(label)) {
            label = current;
            writeUpdateInfo(1, buffer -> buffer.writeString(label));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readUpdateInfo(int id, PacketBuffer buffer) {
        if (id == 1) label = buffer.readString(Short.MAX_VALUE);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawInBackground(int mouseX, int mouseY, float partialTicks, IRenderContext context) {
        Position position = getPosition();
        int color = FluxWirelessTextures.NETWORK_COLOR;
        GlStateManager.color((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F, 1.0F);
        FluxWirelessTextures.bar().draw(position.x, position.y, 135, 12);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        FontRenderer renderer = Minecraft.getMinecraft().fontRenderer;
        String displayLabel = "Main".equals(label) ? I18n.format("gregtech.wireless.channel.main") : label;
        renderer.drawString(displayLabel, position.x + (135 - renderer.getStringWidth(displayLabel)) / 2,
                position.y + 2,
                0xFFFFFF);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (cycleExecutor == null || !isMouseOverElement(mouseX, mouseY)) return false;
        writeClientAction(1, buffer -> buffer.writeByte(button == 1 ? -1 : 1));
        playButtonClickSound();
        return true;
    }

    @Override
    public void handleClientAction(int id, PacketBuffer buffer) {
        if (id == 1 && cycleExecutor != null) cycleExecutor.accept(buffer.readByte());
    }
}
