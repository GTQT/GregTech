package gregtech.common.pipelike.heat.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.HeatConductorProperties;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class HeatNet extends PipeNet<HeatConductorProperties> {

    private long lastHeatFluxPerSec;
    private long heatFluxPerSec;
    private long lastTime;

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
        ((HeatNet) parentNet).NET_DATA.clear();
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
}
