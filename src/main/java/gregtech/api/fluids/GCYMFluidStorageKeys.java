package gregtech.api.fluids;

import gregtech.api.fluids.store.FluidStorageKey;
import gregtech.api.unification.material.info.MaterialIconType;
import gregtech.api.unification.properties.GCYMPropertyKey;

import static gregtech.api.util.GCYMUtil.gcymId;

public final class GCYMFluidStorageKeys {

    public static final FluidStorageKey MOLTEN = new FluidStorageKey(gcymId("molten"),
            MaterialIconType.molten,
            m -> "molten." + m.getName(),
            m -> {
                if (m.hasProperty(GCYMPropertyKey.ALLOY_BLAST)) {
                    return "gcym.fluid.molten";
                }
                return "gregtech.fluid.generic";
            });

    private GCYMFluidStorageKeys() {}
}
