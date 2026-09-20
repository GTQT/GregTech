package gregtech.common.pipelike.fiber.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.common.pipelike.fiber.FiberPipeProperties;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class FiberPipeNet extends PipeNet<FiberPipeProperties> {

    /**
     * Route per pipe and capability kind. A line can be queried from either end, so the two ends
     * are cached separately.
     */
    private final Map<FiberRoutePath.Kind, Map<BlockPos, FiberRoutePath>> netData = new Object2ObjectOpenHashMap<>();

    public FiberPipeNet(WorldPipeNet<FiberPipeProperties, ? extends PipeNet<FiberPipeProperties>> world) {
        super(world);
    }

    @Nullable
    public FiberRoutePath getNetData(BlockPos pipePos, EnumFacing facing, FiberRoutePath.Kind kind) {
        Map<BlockPos, FiberRoutePath> cache = netData.get(kind);
        if (cache == null) {
            cache = new Object2ObjectOpenHashMap<>();
            netData.put(kind, cache);
        }
        if (cache.containsKey(pipePos)) {
            return cache.get(pipePos);
        }
        FiberRoutePath data = FiberNetWalker.createNetData(getWorldData(), pipePos, facing, kind);
        if (data == FiberNetWalker.FAILED_MARKER) {
            // walker failed, don't cache, so it tries again on next insertion
            return null;
        }
        cache.put(pipePos, data);
        return data;
    }

    @Override
    public void onNeighbourUpdate(BlockPos fromPos) {
        netData.clear();
    }

    @Override
    public void onPipeConnectionsUpdate() {
        netData.clear();
    }

    @Override
    public void onChunkUnload() {
        netData.clear();
    }

    @Override
    protected void transferNodeData(Map<BlockPos, Node<FiberPipeProperties>> transferredNodes,
                                    PipeNet<FiberPipeProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        netData.clear();
        ((FiberPipeNet) parentNet).netData.clear();
    }

    @Override
    protected void writeNodeData(FiberPipeProperties nodeData, NBTTagCompound tagCompound) {}

    @Override
    protected FiberPipeProperties readNodeData(NBTTagCompound tagCompound) {
        return FiberPipeProperties.INSTANCE;
    }
}
