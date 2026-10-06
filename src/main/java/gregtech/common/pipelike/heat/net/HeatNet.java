package gregtech.common.pipelike.heat.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.HeatConductorProperties;
import gregtech.api.util.TaskScheduler;
import gregtech.common.pipelike.heat.tile.TileEntityHeatConductor;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 热导管道网络。
 * <p>
 * 温度模型：管道本身不储能，整张网络共享一个温度，等于"当前还活着的热源里的最高温度"。
 * 热源由 {@link HeatNetHandler#transferHeat} 在真正有热量流入时上报，并且
 * {@link #HEAT_SOURCE_TIMEOUT} tick 没有新上报就自动过期 —— 所以不需要外部显式注销，
 * 热源断电后网络会自己冷却回 {@link #AMBIENT_TEMPERATURE}。
 * <p>
 * 这个"过期 + 广播"需要一个每 tick 的驱动，但不能给每根管道都挂一个任务
 * （那样会随管道数量线性增长，而且区块卸载后任务还攥着 TileEntity）：
 * 这里改成整个网络共用一个任务，并且用 {@link WeakReference} 持有网络本身。
 */
public class HeatNet extends PipeNet<HeatConductorProperties> {

    /** 环境温度（K）。没有热源时全网回到这个温度。 */
    public static final int AMBIENT_TEMPERATURE = 293;
    /** 热源上报的存活时间（tick）。超过这个时间没有新上报就认为该热源已断开。 */
    private static final int HEAT_SOURCE_TIMEOUT = 20;
    /** 热流量统计窗口（tick），20 tick = 1 秒。 */
    private static final int FLUX_WINDOW = 20;

    private static final class HeatSource {

        int temperature;
        long lastReportTick;

        HeatSource(int temperature, long lastReportTick) {
            this.temperature = temperature;
            this.lastReportTick = lastReportTick;
        }
    }

    /** 位置 -> 该处热源的最近一次上报。 */
    private final Map<BlockPos, HeatSource> heatSources = new HashMap<>();

    private final Map<BlockPos, List<HeatRoutePath>> NET_DATA = new Object2ObjectOpenHashMap<>();

    private int networkTemperature = AMBIENT_TEMPERATURE;

    private long fluxWindowStart = -1L;
    private long fluxInput;
    private long fluxOutput;
    private long lastFluxInputPerSec;
    private long lastFluxOutputPerSec;

    private boolean ticking = false;

    protected HeatNet(WorldPipeNet<HeatConductorProperties, HeatNet> world) {
        super(world);
    }

    // ===== 温度 =====

    /**
     * 当前网络温度（K）。这是全网唯一温度，管道、发光、过热判断都用它。
     * 纯读取，不做任何结算 —— 结算由 {@link #tick()} 负责。
     */
    public int getNetworkTemperature() {
        return networkTemperature;
    }

    /**
     * 由管道在真正收到热量时调用，登记"这里有一个温度为 {@code temperature} 的热源"。
     * 记录会在 {@link #HEAT_SOURCE_TIMEOUT} tick 后自动过期，调用方不需要配对注销。
     *
     * @param pos         上报者位置（管道自身）
     * @param temperature 上游热源温度（K）
     */
    public void reportHeatSource(BlockPos pos, int temperature) {
        if (temperature <= AMBIENT_TEMPERATURE) return;
        long now = getWorldTick();
        if (now < 0L) return;

        HeatSource source = heatSources.get(pos);
        if (source == null) {
            heatSources.put(pos, new HeatSource(temperature, now));
        } else {
            source.temperature = temperature;
            source.lastReportTick = now;
        }

        if (temperature > networkTemperature) {
            networkTemperature = temperature;
            notifyPipesOfTemperatureChange();
        }
        ensureTicking();
    }

    // ===== 热流量统计（供 TOP / GUI 读取） =====

    /** 记录流进网络的原始热量（源头实际消耗量，未扣损耗）。 */
    public void addHeatInput(long heat) {
        if (heat > 0) fluxInput += heat;
    }

    /** 记录网络实际交付给端点的热量（已扣损耗）。 */
    public void addHeatOutput(long heat) {
        if (heat > 0) fluxOutput += heat;
    }

    public long getLastHeatInputPerSec() {
        return lastFluxInputPerSec;
    }

    public long getLastHeatOutputPerSec() {
        return lastFluxOutputPerSec;
    }

    // ===== 每 tick 驱动 =====

    private void ensureTicking() {
        if (ticking) return;
        World world = getWorldData();
        if (world == null || world.isRemote) return;

        ticking = true;
        // 用弱引用持有网络：网络被合并/移除后任务要能自然结束，不能把网络钉在内存里
        WeakReference<HeatNet> ref = new WeakReference<>(this);
        TaskScheduler.scheduleTask(world, () -> {
            HeatNet net = ref.get();
            if (net == null || !net.ticking) return false;
            return net.tick();
        });
    }

    /**
     * @return true 表示任务继续，false 表示已冷却下来、可以结束
     */
    private boolean tick() {
        if (!isValid()) {
            ticking = false;
            return false;
        }
        long now = getWorldTick();
        if (now < 0L) {
            ticking = false;
            return false;
        }

        // 过期热源
        heatSources.values().removeIf(source -> now - source.lastReportTick > HEAT_SOURCE_TIMEOUT);

        // 全网温度 = 剩余热源的最高温度
        int hottest = AMBIENT_TEMPERATURE;
        for (HeatSource source : heatSources.values()) {
            if (source.temperature > hottest) hottest = source.temperature;
        }
        if (hottest != networkTemperature) {
            networkTemperature = hottest;
            notifyPipesOfTemperatureChange();
        }

        // 每秒结算一次热流量
        if (fluxWindowStart < 0L) {
            fluxWindowStart = now;
        } else if (now - fluxWindowStart >= FLUX_WINDOW) {
            lastFluxInputPerSec = fluxInput;
            lastFluxOutputPerSec = fluxOutput;
            fluxInput = 0L;
            fluxOutput = 0L;
            fluxWindowStart = now;
        }

        if (heatSources.isEmpty() && networkTemperature <= AMBIENT_TEMPERATURE) {
            ticking = false;
            return false;
        }
        return true;
    }

    private long getWorldTick() {
        World world = getWorldData();
        return world == null ? -1L : world.getTotalWorldTime();
    }

    /**
     * 把网络温度推给网内所有管道（渲染发光、TOP、超温判断都读这个值）。
     * <p>
     * 注意两点：
     * <ul>
     *     <li>遍历的是节点副本 —— 管道超温会在 {@code setTemperature} 里炸掉自己，从而改动节点集合；</li>
     *     <li>先判断区块是否加载 —— {@code World#getTileEntity} 会把未加载的区块读进来。</li>
     * </ul>
     */
    private void notifyPipesOfTemperatureChange() {
        World world = getWorldData();
        if (world == null || world.isRemote) return;

        for (BlockPos pos : new ArrayList<>(getAllNodes().keySet())) {
            if (!world.isBlockLoaded(pos)) continue;
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityHeatConductor pipe && !pipe.isInvalid()) {
                pipe.setTemperature(networkTemperature);
            }
        }
    }

    // ===== 路由缓存 =====

    public List<HeatRoutePath> getNetData(BlockPos pipePos) {
        List<HeatRoutePath> data = NET_DATA.get(pipePos);
        if (data != null) return data;

        data = HeatNetWalker.createNetData(getWorldData(), pipePos);
        if (data == null) {
            // walker 失败（源方块已经不是热导管）。这条路径在 HeatNetWalker 里是提前返回的，
            // 重试很便宜；反过来把失败结果缓存下来反而会把一次瞬时失败永久钉死。
            return Collections.emptyList();
        }
        data.sort(Comparator.comparingInt(HeatRoutePath::getDistance));
        NET_DATA.put(pipePos, data);
        return data;
    }

    @Override
    public void onNeighbourUpdate(BlockPos fromPos) {
        NET_DATA.clear();
    }

    @Override
    public void onPipeConnectionsUpdate() {
        NET_DATA.clear();
    }

    @Override
    public void onChunkUnload() {
        NET_DATA.clear();
    }

    @Override
    protected void transferNodeData(Map<BlockPos, Node<HeatConductorProperties>> transferredNodes,
                                    PipeNet<HeatConductorProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();

        if (parentNet instanceof HeatNet parent) {
            parent.NET_DATA.clear();
            for (Map.Entry<BlockPos, HeatSource> entry : heatSources.entrySet()) {
                if (transferredNodes.containsKey(entry.getKey())) {
                    parent.heatSources.put(entry.getKey(), entry.getValue());
                }
            }
            parent.ensureTicking();
        }
        heatSources.clear();
    }

    @Override
    protected void writeNodeData(HeatConductorProperties nodeData, NBTTagCompound tagCompound) {
        tagCompound.setInteger("maxTemp", nodeData.getMaxTemperature());
        tagCompound.setInteger("heatTransfer", nodeData.getHeatTransfer());
        tagCompound.setFloat("heatLoss", nodeData.getHeatLossPerBlock());
    }

    @Override
    protected HeatConductorProperties readNodeData(NBTTagCompound tagCompound) {
        int maxTemp = tagCompound.getInteger("maxTemp");
        int heatTransfer = tagCompound.getInteger("heatTransfer");
        float heatLoss = tagCompound.getFloat("heatLoss");
        return new HeatConductorProperties(maxTemp, heatTransfer, heatLoss);
    }

    // 温度与热源是瞬时状态（带时间戳），不应该写进存档：
    // 存档里恢复出的"热源"没有任何意义，读档后网络本来就该从环境温度重新开始。
}
