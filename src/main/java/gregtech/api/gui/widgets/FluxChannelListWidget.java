package gregtech.api.gui.widgets;

import gregtech.api.gui.IRenderContext;
import gregtech.api.gui.Widget;
import gregtech.api.gui.resources.FluxWirelessTextures;
import gregtech.api.util.Position;
import gregtech.api.util.Size;
import gregtech.api.wireless.WirelessNetworkView;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Flux selection-page channel rows with server-authoritative selection. */
public class FluxChannelListWidget extends Widget {

    private static final int ROW_HEIGHT = 13;
    private static final int ROW_WIDTH = 146;
    private static final int MAX_ROWS = 10;

    private final Supplier<List<WirelessNetworkView>> channelSupplier;
    private final IntSupplier selectedSupplier;
    private final IntConsumer selectionExecutor;
    private final List<ChannelEntry> channels = new ArrayList<>();
    private int selectedChannel;

    public FluxChannelListWidget(int x, int y, Supplier<List<WirelessNetworkView>> channelSupplier,
                                 IntSupplier selectedSupplier, IntConsumer selectionExecutor) {
        super(new Position(x, y), new Size(ROW_WIDTH, ROW_HEIGHT * MAX_ROWS));
        this.channelSupplier = channelSupplier;
        this.selectedSupplier = selectedSupplier;
        this.selectionExecutor = selectionExecutor;
    }

    @Override
    public void detectAndSendChanges() {
        List<ChannelEntry> current = entries(channelSupplier.get());
        int selected = selectedSupplier.getAsInt();
        if (!channels.equals(current) || selectedChannel != selected) {
            channels.clear();
            channels.addAll(current);
            selectedChannel = selected;
            writeUpdateInfo(1, buffer -> writeState(buffer, current, selected));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readUpdateInfo(int id, PacketBuffer buffer) {
        if (id != 1) return;
        selectedChannel = buffer.readVarInt();
        channels.clear();
        int count = Math.min(MAX_ROWS, buffer.readVarInt());
        for (int index = 0; index < count; index++) {
            channels.add(new ChannelEntry(buffer.readVarInt(), buffer.readString(32)));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawInBackground(int mouseX, int mouseY, float partialTicks, IRenderContext context) {
        Position position = getPosition();
        for (int index = 0; index < channels.size(); index++) {
            ChannelEntry channel = channels.get(index);
            boolean selected = channel.id == selectedChannel;
            int color = selected ? FluxWirelessTextures.NETWORK_COLOR : darken(FluxWirelessTextures.NETWORK_COLOR);
            GlStateManager.color((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F, 1.0F);
            FluxWirelessTextures.bar(0, 16, ROW_WIDTH, 12)
                    .draw(position.x, position.y + index * ROW_HEIGHT, ROW_WIDTH, 12);
            String displayName = "Main".equals(channel.name) ? I18n.format("gregtech.wireless.channel.main") :
                    channel.name;
            Minecraft.getMinecraft().fontRenderer.drawString(displayName, position.x + 4,
                    position.y + index * ROW_HEIGHT + 2, selected ? 0xFFFFFF : 0x404040);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        Position position = getPosition();
        for (int index = 0; index < channels.size(); index++) {
            int y = position.y + index * ROW_HEIGHT;
            if (isMouseOver(position.x, y, ROW_WIDTH, 12, mouseX, mouseY)) {
                int channelId = channels.get(index).id;
                writeClientAction(2, buffer -> buffer.writeVarInt(channelId));
                playButtonClickSound();
                return true;
            }
        }
        return false;
    }

    @Override
    public void handleClientAction(int id, PacketBuffer buffer) {
        if (id != 2) return;
        int selected = buffer.readVarInt();
        for (WirelessNetworkView channel : channelSupplier.get()) {
            if (channel.getChannelId() == selected) {
                selectionExecutor.accept(selected);
                return;
            }
        }
    }

    private static void writeState(PacketBuffer buffer, List<ChannelEntry> entries, int selected) {
        buffer.writeVarInt(selected);
        buffer.writeVarInt(Math.min(entries.size(), MAX_ROWS));
        for (int index = 0; index < entries.size() && index < MAX_ROWS; index++) {
            ChannelEntry entry = entries.get(index);
            buffer.writeVarInt(entry.id);
            buffer.writeString(entry.name);
        }
    }

    private static List<ChannelEntry> entries(List<WirelessNetworkView> source) {
        List<ChannelEntry> entries = new ArrayList<>();
        for (WirelessNetworkView channel : source) {
            entries.add(new ChannelEntry(channel.getChannelId(), channel.getNetworkName()));
        }
        entries.sort(Comparator.comparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER));
        return entries.size() > MAX_ROWS ? new ArrayList<>(entries.subList(0, MAX_ROWS)) : entries;
    }

    private static int darken(int color) {
        return ((int) (((color >> 16) & 0xFF) * 0.75F) << 16) |
                ((int) (((color >> 8) & 0xFF) * 0.75F) << 8) |
                (int) ((color & 0xFF) * 0.75F);
    }

    private static final class ChannelEntry {
        private final int id;
        private final String name;

        private ChannelEntry(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ChannelEntry)) return false;
            ChannelEntry entry = (ChannelEntry) other;
            return id == entry.id && name.equals(entry.name);
        }

        @Override
        public int hashCode() {
            return 31 * id + name.hashCode();
        }
    }
}
