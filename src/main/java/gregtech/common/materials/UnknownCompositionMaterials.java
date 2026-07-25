package gregtech.common.materials;

import gregtech.api.fluids.FluidBuilder;
import gregtech.api.unification.material.Material;

import static gregtech.api.unification.material.info.MaterialFlags.*;
import static gregtech.api.unification.material.info.MaterialIconSet.DULL;
import static gregtech.api.unification.material.SCMaterials.*;
import static gregtech.api.util.SCUtility.scId;

/*
 * Ranges 25500-25999
 */
public class UnknownCompositionMaterials {

    private static int startId = 25500;

    public static int getStartID() {
        return startId++;
    }

    public static void register() {
        Corium = new Material.Builder(getStartID(), scId("corium"))
                .liquid(new FluidBuilder()
                        .temperature(2500)
                        .density(8.0D)
                        .viscosity(10000))
                .color(0x7A6B50)
                .iconSet(DULL)
                .flags(NO_UNIFICATION, STICKY, GLOWING)
                .build();

        SpentUraniumFuelSolution = new Material.Builder(getStartID(), scId("spent_uranium_fuel_solution"))
                .liquid()
                .color(0x384536).build();

        RadonRichGasMixture = new Material.Builder(getStartID(), scId("radon_rich_gas_mixture"))
                .gas()
                .color(0xd78dd9).build();
    }
}
