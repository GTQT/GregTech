package gregtech.common.pipelike.cable.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.WireProperties;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class EnergyNet extends PipeNet<WireProperties> {

    // 输入 = 源侧实际付出的能量（amps × 全电压）；输出 = 实际送到目的地的能量（amps × 扣压降后的电压）。
    // 差额就是这条线路的线损，必须分开统计，否则 getOutputPerSec() 报的是输入值，线损被吞掉。
    private long energyFluxInput;
    private long energyFluxOutput;
    private long lastEnergyFluxInputPerSec;
    private long lastEnergyFluxOutputPerSec;
    private long lastTime;

    private final Map<BlockPos, List<EnergyRoutePath>> NET_DATA = new Object2ObjectOpenHashMap<>();

    protected EnergyNet(WorldPipeNet<WireProperties, EnergyNet> world) {
        super(world);
    }

    public List<EnergyRoutePath> getNetData(BlockPos pipePos) {
        List<EnergyRoutePath> data = NET_DATA.get(pipePos);
        if (data == null) {
            data = EnergyNetWalker.createNetData(getWorldData(), pipePos);
            if (data == null) {
                // walker failed, don't cache so it tries again on next insertion
                return Collections.emptyList();
            }
            data.sort(Comparator.comparingInt(EnergyRoutePath::getDistance));
            NET_DATA.put(pipePos, data);
        }
        return data;
    }

    /**
     * 结算统计窗口，把累计量按实际经过的 tick 数折算成"每秒"。
     * <p>
     * 结算只在<b>被查询</b>时发生，所以不能把累计量直接当成"每秒"返回：隔了 100 tick 才有人查询，
     * 累计的是 100 tick 的量，当成 1 秒就会虚高 5 倍。
     */
    private void rollFluxWindow() {
        World world = getWorldData();
        if (world == null || world.isRemote) return;

        long now = world.getTotalWorldTime();
        if (lastTime == 0L) {
            lastTime = now;
            return;
        }

        long elapsed = now - lastTime;
        // 统计窗口还没走完，沿用上一次的值
        if (elapsed < 20L) return;

        lastEnergyFluxInputPerSec = energyFluxInput * 20L / elapsed;
        lastEnergyFluxOutputPerSec = energyFluxOutput * 20L / elapsed;
        energyFluxInput = 0L;
        energyFluxOutput = 0L;
        lastTime = now;
    }

    /** 源侧实际付出的功率（EU/s）。 */
    public long getEnergyFluxInputPerSec() {
        rollFluxWindow();
        return lastEnergyFluxInputPerSec;
    }

    /** 实际送到目的地的功率（EU/s）。与输入之差就是线损。 */
    public long getEnergyFluxOutputPerSec() {
        rollFluxWindow();
        return lastEnergyFluxOutputPerSec;
    }

    /** 兼容旧调用：等价于 {@link #getEnergyFluxInputPerSec()}。 */
    public long getEnergyFluxPerSec() {
        return getEnergyFluxInputPerSec();
    }

    public void addEnergyFluxPerSec(long input, long output) {
        energyFluxInput += input;
        energyFluxOutput += output;
    }

    /** 兼容旧调用：不分输入输出时按"无损耗"记账。 */
    public void addEnergyFluxPerSec(long energy) {
        addEnergyFluxPerSec(energy, energy);
    }

    /** 直接清空当前统计窗口（正常结算由查询时的 {@link #rollFluxWindow()} 负责）。 */
    public void clearCache() {
        lastEnergyFluxInputPerSec = energyFluxInput;
        lastEnergyFluxOutputPerSec = energyFluxOutput;
        energyFluxInput = 0L;
        energyFluxOutput = 0L;
        World world = getWorldData();
        if (world != null) {
            lastTime = world.getTotalWorldTime();
        }
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
    protected void transferNodeData(Map<BlockPos, Node<WireProperties>> transferredNodes,
                                    PipeNet<WireProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();
        ((EnergyNet) parentNet).NET_DATA.clear();
    }

    @Override
    protected void writeNodeData(WireProperties nodeData, NBTTagCompound tagCompound) {
        tagCompound.setLong("voltage", nodeData.getVoltage());
        tagCompound.setInteger("amperage", nodeData.getAmperage());
        tagCompound.setInteger("loss", nodeData.getLossPerBlock());
    }

    @Override
    protected WireProperties readNodeData(NBTTagCompound tagCompound) {
        long voltage = tagCompound.getLong("voltage");
        int amperage = tagCompound.getInteger("amperage");
        int lossPerBlock = tagCompound.getInteger("loss");
        return new WireProperties(voltage, amperage, lossPerBlock);
    }
}
