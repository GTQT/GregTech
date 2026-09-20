package gregtech.common.pipelike.fiber.net;

import gregtech.api.pipenet.WorldPipeNet;
import gregtech.common.pipelike.fiber.FiberPipeProperties;

import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

public class WorldFiberPipeNet extends WorldPipeNet<FiberPipeProperties, FiberPipeNet> {

    private static final String DATA_ID_BASE = "gregtech.fiber_pipe_net";

    public WorldFiberPipeNet(String name) {
        super(name);
    }

    @NotNull
    public static WorldFiberPipeNet getWorldPipeNet(@NotNull World world) {
        final String DATA_ID = getDataID(DATA_ID_BASE, world);
        WorldFiberPipeNet netWorldData = (WorldFiberPipeNet) world.loadData(WorldFiberPipeNet.class, DATA_ID);
        if (netWorldData == null) {
            netWorldData = new WorldFiberPipeNet(DATA_ID);
            world.setData(DATA_ID, netWorldData);
        }
        netWorldData.setWorldAndInit(world);
        return netWorldData;
    }

    @Override
    protected FiberPipeNet createNetInstance() {
        return new FiberPipeNet(this);
    }
}
