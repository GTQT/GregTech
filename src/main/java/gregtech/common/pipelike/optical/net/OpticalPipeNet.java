package gregtech.common.pipelike.optical.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.OpticalCableProperties;
import gregtech.api.util.FacingPos;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class OpticalPipeNet extends PipeNet<OpticalCableProperties> {

    /**
     * Cached route per pipe, capability kind and <b>querying face</b>. A cable can lead to a machine
     * providing computation on one end and a data hatch on the other, so the kinds are cached
     * separately; and {@code OpticalNetWalker} excludes the face it was asked from, so the two ends
     * must be cached separately as well. Keying by position alone made whichever end asked first
     * pin the answer for the other end.
     */
    private final Map<OpticalRoutePath.Kind, Map<FacingPos, OpticalRoutePath>> NET_DATA = new EnumMap<>(
            OpticalRoutePath.Kind.class);

    public OpticalPipeNet(WorldPipeNet<OpticalCableProperties, ? extends PipeNet<OpticalCableProperties>> world) {
        super(world);
    }

    @Nullable
    public OpticalRoutePath getNetData(BlockPos pipePos, EnumFacing facing, OpticalRoutePath.Kind kind) {
        Map<FacingPos, OpticalRoutePath> cache = NET_DATA.computeIfAbsent(kind,
                k -> new Object2ObjectOpenHashMap<>());
        FacingPos key = new FacingPos(pipePos, facing);
        // containsKey 而不是 get() != null：信号在 decayDistance 内走不到终点时 walker 返回的就是
        // null（不是 FAILED_MARKER），那也是算过的结果，必须缓存，否则每次查询都重走一遍。
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        OpticalRoutePath data = OpticalNetWalker.createNetData(getWorldData(), pipePos, facing, kind);
        if (data == OpticalNetWalker.FAILED_MARKER) {
            // walker failed（源方块已经不是光纤）, don't cache, so it tries again on next insertion
            return null;
        }

        cache.put(key, data);
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
    protected void transferNodeData(Map<BlockPos, Node<OpticalCableProperties>> transferredNodes,
                                    PipeNet<OpticalCableProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();
        ((OpticalPipeNet) parentNet).NET_DATA.clear();
    }

    @Override
    protected void writeNodeData(OpticalCableProperties nodeData, NBTTagCompound tagCompound) {
        tagCompound.setInteger("MaxCWUt", nodeData.getMaxCWUt());
        tagCompound.setInteger("DecayDistance", nodeData.getDecayDistance());
    }

    @Override
    protected OpticalCableProperties readNodeData(NBTTagCompound tagCompound) {
        int maxCWUt = tagCompound.hasKey("MaxCWUt") ? tagCompound.getInteger("MaxCWUt") :
                OpticalCableProperties.DEFAULT.getMaxCWUt();
        int decayDistance = tagCompound.hasKey("DecayDistance") ? tagCompound.getInteger("DecayDistance") :
                OpticalCableProperties.DEFAULT.getDecayDistance();
        return new OpticalCableProperties(maxCWUt, decayDistance);
    }
}
