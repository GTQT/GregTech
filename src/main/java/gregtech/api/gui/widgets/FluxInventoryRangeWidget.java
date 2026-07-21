package gregtech.api.gui.widgets;

import gregtech.api.gui.IRenderContext;
import gregtech.api.gui.Widget;
import gregtech.api.gui.resources.FluxWirelessTextures;
import gregtech.api.util.Position;
import gregtech.api.util.Size;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Flux wireless-tab inventory diagram mapped to Hands, Armor and All slots. */
public class FluxInventoryRangeWidget extends Widget {

    private static final int[][] AREAS = {
            { 24, 32, 0, 80, 52, 16, 1 }, { 32, 56, 0, 0, 112, 40, 2 },
            { 32, 104, 112, 0, 112, 16, 2 }, { 136, 128, 52, 80, 16, 16, 0 },
            { 24, 128, 52, 80, 16, 16, 0 }
    };

    private final IntSupplier rangeSupplier;
    private final IntConsumer rangeSetter;
    private int range;

    public FluxInventoryRangeWidget(IntSupplier rangeSupplier, IntConsumer rangeSetter) {
        super(new Position(0, 0), new Size(176, 144));
        this.rangeSupplier = rangeSupplier;
        this.rangeSetter = rangeSetter;
    }

    @Override
    public void detectAndSendChanges() {
        int current = clamp(rangeSupplier.getAsInt());
        if (current != range) {
            range = current;
            writeUpdateInfo(1, buffer -> buffer.writeVarInt(range));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readUpdateInfo(int id, PacketBuffer buffer) {
        if (id == 1) range = clamp(buffer.readVarInt());
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawInBackground(int mouseX, int mouseY, float partialTicks, IRenderContext context) {
        Position base = getPosition();
        int color = FluxWirelessTextures.NETWORK_COLOR;
        GlStateManager.color((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F, 1.0F);
        for (int[] area : AREAS) {
            boolean active = range >= area[6];
            FluxWirelessTextures.inventory(area[2], area[3] + (active ? area[5] : 0), area[4], area[5])
                    .draw(base.x + area[0], base.y + area[1], area[4], area[5]);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        Position base = getPosition();
        for (int[] area : AREAS) {
            if (isMouseOver(base.x + area[0], base.y + area[1], area[4], area[5], mouseX, mouseY)) {
                writeClientAction(1, buffer -> buffer.writeVarInt(area[6]));
                return true;
            }
        }
        return false;
    }

    @Override
    public void handleClientAction(int id, PacketBuffer buffer) {
        if (id == 1) rangeSetter.accept(clamp(buffer.readVarInt()));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(value, 2));
    }
}
