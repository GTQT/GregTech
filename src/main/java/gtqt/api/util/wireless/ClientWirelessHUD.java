package gtqt.api.util.wireless;

import gregtech.api.util.TextFormattingUtil;
import gregtech.common.ConfigHolder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import gtqt.common.network.CPacketRequestNetworkInfo;
import gtqt.common.network.NetworkHandler;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(Side.CLIENT)
public class ClientWirelessHUD {

    private static final String[] ENERGY_UNITS = {" EU", " KEU", " MEU", " GEU", " TEU", " PEU", " EEU", " ZEU", " YEU"};

    private static volatile BigInteger stored = BigInteger.ZERO;
    private static volatile BigInteger lastStored = BigInteger.ZERO;
    private static volatile BigInteger inputRate = BigInteger.ZERO;
    private static volatile BigInteger outputRate = BigInteger.ZERO;
    private static volatile boolean hasNetwork = false;
    private static volatile String throughputString = "";
    private static int tickCounter = 0;
    private static final int REQUEST_INTERVAL = 20;

    public static void updateInfo(BigInteger newStored, BigInteger newInputRate, BigInteger newOutputRate) {
        if (lastStored.compareTo(BigInteger.ZERO) != 0) {
            BigInteger delta = newStored.subtract(lastStored);
            BigDecimal ratePerSecond = new BigDecimal(delta);
            throughputString = formatRate(ratePerSecond);
        } else {
            throughputString = "";
        }

        stored = newStored;
        lastStored = newStored;
        inputRate = newInputRate;
        outputRate = newOutputRate;
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
            NetworkHandler.INSTANCE.sendToServer(new CPacketRequestNetworkInfo());
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (!hasNetwork) return;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution res = new ScaledResolution(mc);
        int x = ConfigHolder.client.wirelessHud.hudOffsetX;
        int y = res.getScaledHeight() - ConfigHolder.client.wirelessHud.hudOffsetY;

        String storedStr = formatStoredEnergy(stored);

        int currentY = y;
        mc.fontRenderer.drawStringWithShadow("无线网络", x, currentY, 0xFFFFFF); currentY += 10;
        mc.fontRenderer.drawStringWithShadow("储能: " + storedStr, x, currentY, 0xFFFF55); currentY += 10;

        if (!throughputString.isEmpty()) {
            int throughputColor;
            if (throughputString.startsWith("+")) {
                throughputColor = 0x55FF55;
            } else if (throughputString.startsWith("-")) {
                throughputColor = 0xFF5555;
            } else {
                throughputColor = 0xAAAAAA;
            }
            mc.fontRenderer.drawStringWithShadow("能量吞吐: " + throughputString, x, currentY, throughputColor); currentY += 10;
        }

        String inputStr = formatEnergy(inputRate) + "/s";
        String outputStr = formatEnergy(outputRate) + "/s";

        String inputLabel = "纯流入: ";
        int xPos = x;
        mc.fontRenderer.drawStringWithShadow(inputLabel, xPos, currentY, 0xFFFFFF);
        xPos += mc.fontRenderer.getStringWidth(inputLabel);
        mc.fontRenderer.drawStringWithShadow(inputStr, xPos, currentY, 0x55FF55);
        xPos += mc.fontRenderer.getStringWidth(inputStr);

        String outputLabel = "  纯流出: ";
        mc.fontRenderer.drawStringWithShadow(outputLabel, xPos, currentY, 0xFFFFFF);
        xPos += mc.fontRenderer.getStringWidth(outputLabel);
        mc.fontRenderer.drawStringWithShadow(outputStr, xPos, currentY, 0xFF5555);
    }

    private static String formatEnergy(BigInteger energy) {
        if (energy.compareTo(BigInteger.ZERO) == 0) {
            return "0 EU";
        }
        BigDecimal value = new BigDecimal(energy);
        BigDecimal divisor = BigDecimal.valueOf(1000);
        int unitIndex = 0;
        while (value.compareTo(divisor) >= 0 && unitIndex < ENERGY_UNITS.length - 1) {
            value = value.divide(divisor, 1, RoundingMode.HALF_UP);
            unitIndex++;
        }
        return value.stripTrailingZeros().toPlainString() + ENERGY_UNITS[unitIndex];
    }

    private static String formatStoredEnergy(BigInteger energy) {
        return TextFormattingUtil.formatBigIntToScientificString(energy, 4) + " EU";
    }

    private static String formatRate(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.ZERO) == 0) {
            return "0 EU/s";
        }
        String sign = rate.compareTo(BigDecimal.ZERO) > 0 ? "+" : "-";
        BigDecimal absRate = rate.abs();
        BigDecimal divisor = BigDecimal.valueOf(1000);
        int unitIndex = 0;
        while (absRate.compareTo(divisor) >= 0 && unitIndex < ENERGY_UNITS.length - 1) {
            absRate = absRate.divide(divisor, 1, RoundingMode.HALF_UP);
            unitIndex++;
        }
        return sign + absRate.stripTrailingZeros().toPlainString() + ENERGY_UNITS[unitIndex] + "/s";
    }
}
