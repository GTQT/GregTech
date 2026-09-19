package gregtech.common.pipelike.optical.net;

import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.OpticalCableProperties;

import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

public class WorldOpticalPipeNet extends WorldPipeNet<OpticalCableProperties, OpticalPipeNet> {

    private static final String DATA_ID_BASE = "gregtech.optical_pipe_net";

    public WorldOpticalPipeNet(String name) {
        super(name);
    }

    @NotNull
    public static WorldOpticalPipeNet getWorldPipeNet(@NotNull World world) {
        final String DATA_ID = getDataID(DATA_ID_BASE, world);
        WorldOpticalPipeNet netWorldData = (WorldOpticalPipeNet) world.loadData(WorldOpticalPipeNet.class, DATA_ID);
        if (netWorldData == null) {
            netWorldData = new WorldOpticalPipeNet(DATA_ID);
            world.setData(DATA_ID, netWorldData);
        }
        netWorldData.setWorldAndInit(world);
        return netWorldData;
    }

    @Override
    protected OpticalPipeNet createNetInstance() {
        return new OpticalPipeNet(this);
    }
}
