package gregtech.common.pipelike.heat.tile;

import net.minecraft.util.ITickable;

public class TileEntityHeatConductorTickable extends TileEntityHeatConductor implements ITickable {

    public TileEntityHeatConductorTickable() {}

    @Override
    public void update() {
        getCoverableImplementation().update();
    }

    @Override
    public boolean supportsTicking() {
        return true;
    }
}
