package gregtech.api.wireless;

import gregtech.common.ConfigHolder;
import gregtech.common.network.CPacketRequestComputationInfo;
import gregtech.common.network.NetworkHandler;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * 无线算力网络 HUD 数据源：每 20 tick 向服务端拉取信道快照并缓存采样队列。
 * 渲染由 {@link WirelessComputationHudRenderer} 完成。
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(Side.CLIENT)
public class ClientWirelessComputationHUD {

    /** 采样点: 一次轮询得到的瞬时数据快照 */
    public static final class ComputationSample {
        public final int maxCWUt;
        public final int allocatedCWUt;
        public final int allocatedPerSecond;
        public final int nodeCount;

        private ComputationSample(int maxCWUt, int allocatedCWUt, int allocatedPerSecond, int nodeCount) {
            this.maxCWUt = maxCWUt;
            this.allocatedCWUt = allocatedCWUt;
            this.allocatedPerSecond = allocatedPerSecond;
            this.nodeCount = nodeCount;
        }
    }

    /** 轮询间隔 (tick) */
    private static final int REQUEST_INTERVAL = 20;
    /** 折线图窗口: 5 分钟 (20 tick 一次采样) */
    public static final int MEASUREMENT_COUNT_5M = 5 * 60 * 20 / REQUEST_INTERVAL;
    /** 采样队列上限: 1 小时 */
    public static final int STORED_MEASUREMENTS = 60 * 60 * 20 / REQUEST_INTERVAL;

    static final LinkedList<ComputationSample> samples = new LinkedList<>();
    private static volatile boolean hasNetwork = false;
    static int allocationDifference5m = 0;
    static int allocationDifference1h = 0;
    private static int tickCounter = 0;

    public static void updateInfo(int maxCWUt, int allocatedCWUt, int allocatedPerSecond, int nodeCount) {
        samples.addLast(new ComputationSample(maxCWUt, allocatedCWUt, allocatedPerSecond, nodeCount));
        while (samples.size() > STORED_MEASUREMENTS) {
            samples.removeFirst();
        }
        allocationDifference5m = getDifference(getLastSamples(MEASUREMENT_COUNT_5M));
        allocationDifference1h = getDifference(getLastSamples(STORED_MEASUREMENTS));
        hasNetwork = true;
    }

    /** 返回窗口内最新的 count 个采样, 按时间从旧到新排序 */
    public static List<ComputationSample> getLastSamples(int count) {
        List<ComputationSample> last = new ArrayList<>(samples);
        Collections.reverse(last);
        last = last.subList(0, Math.min(count, last.size()));
        Collections.reverse(last);
        return last;
    }

    /** 历史采样中的最大容量 (用于填充百分比) */
    public static int getMaxCapacityInSamples() {
        int max = 0;
        for (ComputationSample sample : samples) {
            if (sample.maxCWUt > max) {
                max = sample.maxCWUt;
            }
        }
        return max;
    }

    private static int getDifference(List<ComputationSample> window) {
        if (window.size() <= 1) {
            return 0;
        }
        return window.get(0).allocatedCWUt - window.get(window.size() - 1).allocatedCWUt;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            hasNetwork = false;
            return;
        }
        if (!ConfigHolder.client.wirelessComputationHud.enabled) {
            hasNetwork = false;
            return;
        }

        tickCounter++;
        if (tickCounter >= REQUEST_INTERVAL) {
            tickCounter = 0;
            NetworkHandler.INSTANCE.sendToServer(new CPacketRequestComputationInfo());
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (!hasNetwork) return;
        if (!ConfigHolder.client.wirelessComputationHud.enabled) return;
        if (samples.isEmpty()) return;
        WirelessComputationHudRenderer.INSTANCE.render();
    }
}
