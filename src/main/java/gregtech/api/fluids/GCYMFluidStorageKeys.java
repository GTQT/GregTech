package gregtech.api.fluids;

import static gregtech.api.util.GCYMUtil.gcymId;

import gregtech.api.fluids.store.FluidStorageKey;

import gregtech.api.unification.material.GCYMMaterialIconTypes;
import gregtech.api.unification.properties.GCYMPropertyKey;

public final class GCYMFluidStorageKeys {

    public static final FluidStorageKey MOLTEN = new FluidStorageKey(gcymId("molten"),
            GCYMMaterialIconTypes.molten,
            m -> "molten." + m.getName(),
            m -> {
                if (m.hasProperty(GCYMPropertyKey.ALLOY_BLAST)) {
                    return "gcym.fluid.molten";
                }
                return "gregtech.fluid.generic";
            });

    private GCYMFluidStorageKeys() {}
}
