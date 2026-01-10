package gregtech.common.pipelike.heat.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.HeatConductorProperties;
import gregtech.common.pipelike.heat.tile.TileEntityHeatConductor;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HeatNet extends PipeNet<HeatConductorProperties> {

    private long lastHeatFluxPerSec;
    private long heatFluxPerSec;
    private long lastTime;

    // 网络温度管理 - 核心改动
    private int networkTemperature = 293; // 默认室温
    private final Set<BlockPos> heatSources = new HashSet<>(); // 热源位置
    private final Map<BlockPos, Integer> heatSourceTemperatures = new HashMap<>(); // 热源温度

    private final Map<BlockPos, List<HeatRoutePath>> NET_DATA = new Object2ObjectOpenHashMap<>();

    protected HeatNet(WorldPipeNet<HeatConductorProperties, HeatNet> world) {
        super(world);
    }

    public List<HeatRoutePath> getNetData(BlockPos pipePos) {
        List<HeatRoutePath> data = NET_DATA.get(pipePos);
        if (data == null) {
            data = HeatNetWalker.createNetData(getWorldData(), pipePos);
            if (data == null) {
                return Collections.emptyList();
            }
            data.sort(Comparator.comparingInt(HeatRoutePath::getDistance));
            NET_DATA.put(pipePos, data);
        }
        return data;
    }

    public long getHeatFluxPerSec() {
        World world = getWorldData();
        if (world != null && !world.isRemote && (world.getTotalWorldTime() - lastTime) >= 20) {
            lastTime = world.getTotalWorldTime();
            clearCache();
        }
        return lastHeatFluxPerSec;
    }

    public void addHeatFluxPerSec(long heat) {
        heatFluxPerSec += heat;
    }

    public void clearCache() {
        lastHeatFluxPerSec = heatFluxPerSec;
        heatFluxPerSec = 0;
    }

    // === 温度管理方法 ===

    /**
     * 获取网络当前温度
     * @return 网络温度（K）
     */
    public int getNetworkTemperature() {
        return networkTemperature;
    }

    /**
     * 添加热源并更新网络温度
     * @param pos 热源位置
     * @param temperature 热源温度
     */
    public void addHeatSource(BlockPos pos, int temperature) {
        heatSources.add(pos);
        heatSourceTemperatures.put(pos, temperature);
        updateNetworkTemperature();
    }

    /**
     * 移除热源并更新网络温度
     * @param pos 热源位置
     */
    public void removeHeatSource(BlockPos pos) {
        heatSources.remove(pos);
        heatSourceTemperatures.remove(pos);
        updateNetworkTemperature();
    }

    /**
     * 更新热源温度
     * @param pos 热源位置
     * @param temperature 新温度
     */
    public void updateHeatSource(BlockPos pos, int temperature) {
        if (heatSources.contains(pos)) {
            heatSourceTemperatures.put(pos, temperature);
            updateNetworkTemperature();
        }
    }

    /**
     * 检查位置是否是热源
     */
    public boolean isHeatSource(BlockPos pos) {
        return heatSources.contains(pos);
    }

    /**
     * 更新网络温度为所有热源中的最高温度
     */
    private void updateNetworkTemperature() {
        if (heatSources.isEmpty()) {
            // 没有热源，使用默认室温
            networkTemperature = 293;
        } else {
            // 取最高温度
            networkTemperature = heatSourceTemperatures.values().stream()
                    .max(Integer::compare)
                    .orElse(293);
        }

        // 通知网络中的管道更新温度显示
        notifyPipesOfTemperatureChange();
    }

    /**
     * 通知网络中的所有管道温度已更新
     */
    private void notifyPipesOfTemperatureChange() {
        World world = getWorldData();
        if (world == null || world.isRemote) return;

        // 遍历网络中的所有节点位置
        for (BlockPos pos : getAllNodes().keySet()) {
            TileEntity tile = world.getTileEntity(pos);
            if (tile instanceof TileEntityHeatConductor pipe) {
                pipe.setTemperature(networkTemperature);
            }
        }
    }

    /**
     * 重置网络温度（当所有热源断开时）
     */
    public void resetTemperature() {
        networkTemperature = 293;
        notifyPipesOfTemperatureChange();
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

        if (parentNet instanceof HeatNet) {
            ((HeatNet) parentNet).NET_DATA.clear();

            for (BlockPos pos : heatSources) {
                if (transferredNodes.containsKey(pos)) {
                    int temp = heatSourceTemperatures.get(pos);
                    ((HeatNet) parentNet).addHeatSource(pos, temp);
                }
            }
        }
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

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = super.serializeNBT();
        nbt.setInteger("NetworkTemperature", networkTemperature);

        // 保存热源信息
        NBTTagCompound sourcesNBT = new NBTTagCompound();
        for (Map.Entry<BlockPos, Integer> entry : heatSourceTemperatures.entrySet()) {
            BlockPos pos = entry.getKey();
            NBTTagCompound sourceNBT = new NBTTagCompound();
            sourceNBT.setInteger("x", pos.getX());
            sourceNBT.setInteger("y", pos.getY());
            sourceNBT.setInteger("z", pos.getZ());
            sourceNBT.setInteger("temp", entry.getValue());
            sourcesNBT.setTag(pos.toString(), sourceNBT);
        }
        nbt.setTag("HeatSources", sourcesNBT);

        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        super.deserializeNBT(nbt);

        if (nbt.hasKey("NetworkTemperature")) {
            networkTemperature = nbt.getInteger("NetworkTemperature");
        }

        // 加载热源信息
        if (nbt.hasKey("HeatSources")) {
            NBTTagCompound sourcesNBT = nbt.getCompoundTag("HeatSources");
            for (String key : sourcesNBT.getKeySet()) {
                NBTTagCompound sourceNBT = sourcesNBT.getCompoundTag(key);
                BlockPos pos = new BlockPos(
                        sourceNBT.getInteger("x"),
                        sourceNBT.getInteger("y"),
                        sourceNBT.getInteger("z")
                );
                int temp = sourceNBT.getInteger("temp");
                heatSources.add(pos);
                heatSourceTemperatures.put(pos, temp);
            }
        }
    }
}
