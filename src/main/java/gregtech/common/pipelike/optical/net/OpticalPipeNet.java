package gregtech.common.pipelike.optical.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.OpticalCableProperties;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class OpticalPipeNet extends PipeNet<OpticalCableProperties> {

    /**
     * Cached route per pipe and capability kind. A cable can lead to a machine providing
     * computation on one end and a data hatch on the other, so the two are cached separately.
     */
    private final Map<OpticalRoutePath.Kind, Map<BlockPos, OpticalRoutePath>> NET_DATA = new EnumMap<>(
            OpticalRoutePath.Kind.class);

    public OpticalPipeNet(WorldPipeNet<OpticalCableProperties, ? extends PipeNet<OpticalCableProperties>> world) {
        super(world);
    }

    @Nullable
    public OpticalRoutePath getNetData(BlockPos pipePos, EnumFacing facing, OpticalRoutePath.Kind kind) {
        Map<BlockPos, OpticalRoutePath> cache = NET_DATA.get(kind);
        if (cache == null) {
            cache = new Object2ObjectOpenHashMap<>();
            NET_DATA.put(kind, cache);
        }
        if (cache.containsKey(pipePos)) {
            return cache.get(pipePos);
        }
        OpticalRoutePath data = OpticalNetWalker.createNetData(getWorldData(), pipePos, facing, kind);
        if (data == OpticalNetWalker.FAILED_MARKER) {
            // walker failed or the signal decayed, don't cache, so it tries again on next insertion
            return null;
        }

        cache.put(pipePos, data);
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
