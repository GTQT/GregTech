package gregtech.api.unification.material.properties;

import gregtech.api.GregTechAPI;
import gregtech.api.recipes.alloyblast.CustomAlloyBlastRecipeProducer;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.info.MaterialFlags;
import gregtech.api.unification.stack.MaterialStack;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class AlloyBlastPropertyAddition {

    private AlloyBlastPropertyAddition() {}

    public static void init() {
        for (Material material : GregTechAPI.materialManager.getRegisteredMaterials()) {
            if (!material.hasFlag(MaterialFlags.DISABLE_ALLOY_PROPERTY)) {
                addAlloyBlastProperty(material);
            }
        }
        // Alloy Blast Overriding
        AlloyBlastProperty property = Materials.NiobiumNitride.getProperty(PropertyKey.ALLOY_BLAST);
        property.setRecipeProducer(new CustomAlloyBlastRecipeProducer(1, 11, -1));

        property = Materials.IndiumTinBariumTitaniumCuprate.getProperty(PropertyKey.ALLOY_BLAST);
        property.setRecipeProducer(new CustomAlloyBlastRecipeProducer(-1, -1, 16));
    }

    private static void addAlloyBlastProperty(@NotNull Material material) {
        final List<MaterialStack> components = material.getMaterialComponents();
        // ignore materials which are not alloys
        if (components.size() < 2) return;

        BlastProperty blastProperty = material.getProperty(PropertyKey.BLAST);
        if (blastProperty == null) return;

        if (!material.hasProperty(PropertyKey.FLUID)) return;

        // if there are more than 2 fluid-only components in the material, do not generate a hot fluid
        if (components.stream().filter(AlloyBlastPropertyAddition::isMaterialStackFluidOnly).limit(3).count() > 2) {
            return;
        }

        material.setProperty(PropertyKey.ALLOY_BLAST, new AlloyBlastProperty(material.getBlastTemperature()));
    }

    private static boolean isMaterialStackFluidOnly(@NotNull MaterialStack ms) {
        return !ms.material.hasProperty(PropertyKey.DUST) && ms.material.hasProperty(PropertyKey.FLUID);
    }
}
