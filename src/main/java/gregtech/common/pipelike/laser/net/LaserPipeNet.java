package gregtech.common.pipelike.laser.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.util.FacingPos;
import gregtech.common.pipelike.laser.LaserPipeProperties;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class LaserPipeNet extends PipeNet<LaserPipeProperties> {

    /**
     * 路由缓存。
     * <p>
     * 键必须带上<b>查询面</b>：{@code LaserNetWalker} 会把"问话的那一面"排除在外
     * （{@code checkNeighbour} 里的 {@code faceToNeighbour == facingToHandler}），
     * 并只用 {@code faceToSourceHandler.getAxis()} 那条轴遍历。所以同一根管子从不同面问，
     * 答案是不同的（甚至方向相反）。早期只按 {@code pipePos} 缓存，先被问的那一面会把结果
     * 钉死，其它面拿到错误路由。
     */
    private final Map<FacingPos, LaserRoutePath> netData = new Object2ObjectOpenHashMap<>();

    public LaserPipeNet(WorldPipeNet<LaserPipeProperties, ? extends PipeNet<LaserPipeProperties>> world) {
        super(world);
    }

    @Nullable
    public LaserRoutePath getNetData(BlockPos pipePos, EnumFacing facing) {
        FacingPos key = new FacingPos(pipePos, facing);
        // 注意用 containsKey 而不是 get() != null：walker 找不到终点时返回的就是 null，
        // 那也是"算过了"的结果，必须缓存下来，否则每次查询都要重走一遍。
        if (netData.containsKey(key)) {
            return netData.get(key);
        }
        LaserRoutePath data = LaserNetWalker.createNetData(getWorldData(), pipePos, facing);
        if (data == LaserNetWalker.FAILED_MARKER) {
            // walker 失败（源方块已经不是激光管）不缓存，下次重试；这条路径是提前返回的，很便宜
            return null;
        }
        netData.put(key, data);
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
    protected void transferNodeData(Map<BlockPos, Node<LaserPipeProperties>> transferredNodes,
                                    PipeNet<LaserPipeProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        netData.clear();
        ((LaserPipeNet) parentNet).netData.clear();
    }

    @Override
    protected void writeNodeData(LaserPipeProperties nodeData, NBTTagCompound tagCompound) {}

    @Override
    protected LaserPipeProperties readNodeData(NBTTagCompound tagCompound) {
        return LaserPipeProperties.INSTANCE;
    }
}
