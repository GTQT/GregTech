package gregtech.api.fluids;

import gregtech.api.GregTechAPI;
import gregtech.api.fluids.store.FluidStorageKeys;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.info.MaterialFlags;
import gregtech.api.unification.material.properties.AlloyBlastProperty;
import gregtech.api.unification.material.properties.BlastProperty;
import gregtech.api.unification.material.properties.FluidProperty;
import gregtech.api.unification.material.properties.PropertyKey;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Handles generation of fluids based on material properties
 */
@ApiStatus.Internal
public final class GeneratedFluidHandler {

    private GeneratedFluidHandler() {}

    public static void init() {
        for (Material material : GregTechAPI.materialManager.getRegisteredMaterials()) {
            createMoltenFluid(material);
        }
    }

    public static void createMoltenFluid(@NotNull Material material) {
        // ignore materials set not to be alloy blast handled
        if (material.hasFlag(MaterialFlags.DISABLE_ALLOY_PROPERTY)) return;

        // ignore materials which are not alloys
        if (material.getMaterialComponents().size() <= 1) return;

        BlastProperty blastProperty = material.getProperty(PropertyKey.BLAST);
        if (blastProperty == null) return;

        AlloyBlastProperty alloyBlastProperty = material.getProperty(PropertyKey.ALLOY_BLAST);
        if (alloyBlastProperty == null) return;

        FluidProperty fluidProperty = material.getProperty(PropertyKey.FLUID);
        if (fluidProperty == null) return;

        if (alloyBlastProperty.shouldGenerateMolten(material)) {
            fluidProperty.enqueueRegistration(FluidStorageKeys.MOLTEN, new FluidBuilder()
                    .temperature(alloyBlastProperty.getTemperature()));
        }
        // if it is not hot enough to produce molten fluid, ABS Producer grabs normal liquid,
        // thus we don't need to do anything.
    }
}
