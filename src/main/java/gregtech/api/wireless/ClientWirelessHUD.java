package gregtech.api.wireless;

import gregtech.common.ConfigHolder;
import gregtech.common.network.CPacketRequestNetworkInfo;
import gregtech.common.network.NetworkHandler;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(Side.CLIENT)
public class ClientWirelessHUD {

    /** 采样点: 一次轮询得到的瞬时数据快照 */
    public static final class EnergySample {
        public final BigInteger stored;
        public final BigInteger inputRate;
        public final BigInteger outputRate;

        private EnergySample(BigInteger stored, BigInteger inputRate, BigInteger outputRate) {
            this.stored = stored;
            this.inputRate = inputRate;
            this.outputRate = outputRate;
        }
    }

    /** 轮询间隔 (tick) */
    private static final int REQUEST_INTERVAL = 20;
    /** 折线图窗口: 5 分钟 (20 tick 一次采样) */
    public static final int MEASUREMENT_COUNT_5M = 5 * 60 * 20 / REQUEST_INTERVAL;
    /** 采样队列上限: 1 小时 */
    public static final int STORED_MEASUREMENTS = 60 * 60 * 20 / REQUEST_INTERVAL;

    static final LinkedList<EnergySample> samples = new LinkedList<>();
    private static BigInteger lastStored = BigInteger.ZERO;
    private static volatile boolean hasNetwork = false;
    static String throughputString = "";
    static BigInteger euDifference5m = BigInteger.ZERO;
    static BigInteger euDifference1h = BigInteger.ZERO;
    private static int tickCounter = 0;

    public static void updateInfo(BigInteger newStored, BigInteger newInputRate, BigInteger newOutputRate) {
        if (lastStored.compareTo(BigInteger.ZERO) != 0) {
            BigDecimal delta = new BigDecimal(newStored.subtract(lastStored));
            throughputString = formatRate(delta);
        } else {
            throughputString = "";
        }
        lastStored = newStored;

        samples.addLast(new EnergySample(newStored, newInputRate, newOutputRate));
        while (samples.size() > STORED_MEASUREMENTS) {
            samples.removeFirst();
        }
        euDifference5m = getDifference(getLastSamples(MEASUREMENT_COUNT_5M));
        euDifference1h = getDifference(getLastSamples(STORED_MEASUREMENTS));
        hasNetwork = true;
    }

    /** 返回窗口内最新的 count 个采样, 按时间从旧到新排序 */
    public static List<EnergySample> getLastSamples(int count) {
        List<EnergySample> last = new ArrayList<>(samples);
        Collections.reverse(last);
        last = last.subList(0, Math.min(count, last.size()));
        Collections.reverse(last);
        return last;
    }

    /** 历史采样中的最大储能 (用于填充百分比) */
    public static BigInteger getMaxStoredInSamples() {
        BigInteger max = BigInteger.ZERO;
        for (EnergySample sample : samples) {
            if (sample.stored.compareTo(max) > 0) {
                max = sample.stored;
            }
        }
        return max;
    }

    private static BigInteger getDifference(List<EnergySample> window) {
        if (window.size() <= 1) {
            return BigInteger.ZERO;
        }
        return window.get(0).stored.subtract(window.get(window.size() - 1).stored);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            hasNetwork = false;
            return;
        }
        if (!ConfigHolder.client.wirelessHud.enabled) {
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
        if (!ConfigHolder.client.wirelessHud.enabled) return;
        if (samples.isEmpty()) return;
        WirelessHudRenderer.INSTANCE.render();
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

    private static final String[] ENERGY_UNITS = { " EU", " KEU", " MEU", " GEU", " TEU", " PEU", " EEU", " ZEU", " YEU" };
}
