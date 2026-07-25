package gregtech.api.unification.material;

import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.material.properties.SCMaterialsFlag.GENERATE_PELLETS;

public class SCMaterialFlagAddition {
    public static void init() {
        Uranium235.addFlags(GENERATE_PELLETS);
        Uranium238.addFlags(GENERATE_PELLETS);
        Plutonium239.addFlags(GENERATE_PELLETS);
        Plutonium241.addFlags(GENERATE_PELLETS);
        Thorium.addFlags(GENERATE_PELLETS);
        Curium.addFlags(GENERATE_PELLETS);
        Americium.addFlags(GENERATE_PELLETS);
        Neptunium.addFlags(GENERATE_PELLETS);
    }
}
