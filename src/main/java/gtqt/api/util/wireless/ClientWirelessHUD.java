package gtqt.api.util.wireless;


import gregtech.core.network.packets.PacketWirelessNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.math.BigInteger;

public class ClientWirelessHUD {

    private static BigInteger stored = BigInteger.ZERO;
    private static BigInteger capacity = BigInteger.ZERO;
    private static boolean hasNetwork = false;
    private static int tickCounter = 0;
    private static final int REQUEST_INTERVAL = 100; // 5秒 (20 ticks/秒 * 5)

    public static void updateInfo(BigInteger newStored, BigInteger newCapacity) {
        stored = newStored;
        capacity = newCapacity;
        hasNetwork = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            hasNetwork = false;
            return;
        }

        tickCounter++;
        if (tickCounter >= REQUEST_INTERVAL) {
            tickCounter = 0;
            // 发送请求包（使用新组合包）
            gregtech.api.GregTechAPI.networkHandler.sendToServer(new PacketWirelessNetwork.Server());
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (!hasNetwork) return;
        if (capacity.compareTo(BigInteger.ZERO) == 0) return;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc);
        int x = 5;
        int y = res.getScaledHeight() - 40;

        String storedStr = formatEnergy(stored);
        double percent = capacity.compareTo(BigInteger.ZERO) == 0 ? 0 :
                stored.doubleValue() / capacity.doubleValue() * 100;
        String percentStr = String.format("%.1f%%", percent);

        mc.fontRenderer.drawStringWithShadow("无线网络", x, y, 0xFFFFFF);
        mc.fontRenderer.drawStringWithShadow("存量: " + storedStr, x, y + 10, 0xAAAAAA);
        mc.fontRenderer.drawStringWithShadow("占比: " + percentStr, x, y + 20, 0xAAAAAA);
    }

    private static String formatEnergy(BigInteger energy) {
        if (energy.compareTo(BigInteger.valueOf(1_000_000_000L)) >= 0) {
            return energy.divide(BigInteger.valueOf(1_000_000_000L)) + " GE";
        } else if (energy.compareTo(BigInteger.valueOf(1_000_000L)) >= 0) {
            return energy.divide(BigInteger.valueOf(1_000_000L)) + " ME";
        } else if (energy.compareTo(BigInteger.valueOf(1_000L)) >= 0) {
            return energy.divide(BigInteger.valueOf(1_000L)) + " KE";
        } else {
            return energy + " EU";
        }
    }
}
