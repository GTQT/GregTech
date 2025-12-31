package gregtech.common.pipelike.heat.net;

import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.HeatConductorProperties;

import net.minecraft.world.World;

public class WorldHNet extends WorldPipeNet<HeatConductorProperties, HeatNet> {

    private static final String DATA_ID_BASE = "gregtech.h_net";

    public static WorldHNet getWorldHNet(World world) {
        final String DATA_ID = getDataID(DATA_ID_BASE, world);
        WorldHNet hNetWorldData = (WorldHNet) world.loadData(WorldHNet.class, DATA_ID);
        if (hNetWorldData == null) {
            hNetWorldData = new WorldHNet(DATA_ID);
            world.setData(DATA_ID, hNetWorldData);
        }
        hNetWorldData.setWorldAndInit(world);
        return hNetWorldData;
    }

    public WorldHNet(String name) {
        super(name);
    }

    @Override
    protected HeatNet createNetInstance() {
        return new HeatNet(this);
    }
}
