package gregtech.loaders.recipe;

import gregtech.api.GregTechAPI;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.common.items.MetaItems;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.REPLICATOR_RECIPES;
import static gregtech.api.recipes.RecipeMaps.SCANNER_RECIPES;
import static gregtech.api.unification.ore.OrePrefix.dust;
import static gregtech.api.util.Mods.Names.GTQT_TEST;
import static gregtech.common.items.MetaItems.*;

public class UURecipes {

    public static void init() {
        initRecycleRecipe();
        if (!Loader.isModLoaded(GTQT_TEST)) UUUtils();
    }

    public static void initRecycleRecipe() {
        for (Item item : ForgeRegistries.ITEMS) {
            RecipeMaps.RECYCLER_RECIPES.recipeBuilder()
                    .input(item, 1)
                    .chancedOutput(SCRAP, 2500, 500)
                    .EUt(VA[LV])
                    .duration(100)
                    .buildAndRegister();
        }

        RecipeMaps.COMPRESSOR_RECIPES.recipeBuilder()
                .input(SCRAP)
                .output(SCRAP_BOX)
                .EUt(VA[LV])
                .duration(100)
                .buildAndRegister();

        RecipeMaps.MASS_FABRICATOR_RECIPES.recipeBuilder()
                .circuitMeta(1)
                .fluidOutputs(Materials.UUMatter.getFluid(1))
                .EUt(VA[MV])
                .duration(3200)
                .buildAndRegister();

        RecipeMaps.MASS_FABRICATOR_RECIPES.recipeBuilder()
                .input(SCRAP)
                .fluidOutputs(Materials.UUMatter.getFluid(1))
                .EUt(VA[MV])
                .duration(1600)
                .buildAndRegister();

        RecipeMaps.FLUID_SOLIDFICATION_RECIPES.recipeBuilder()
                .fluidInputs(Materials.UUMatter.getFluid(1000))
                .output(UU_MATER)
                .EUt(VA[LV])
                .duration(200)
                .buildAndRegister();

        RecipeMaps.EXTRACTOR_RECIPES.recipeBuilder()
                .input(UU_MATER)
                .fluidOutputs(Materials.UUMatter.getFluid(1000))
                .EUt(VA[LV])
                .duration(200)
                .buildAndRegister();
    }

    private static void UUUtils() {

        //扫描和复制配方
        for (Material material : GregTechAPI.materialManager.getRegisteredMaterials()) {
            ItemStack itemStack = MetaItems.TOOL_DATA_STICK.getStackForm();
            NBTTagCompound compound = new NBTTagCompound();
            compound.setString("Name", material.getLocalizedName());
            itemStack.setTagCompound(compound);
            int mass = 0;

            if (material.getMaterialComponents().isEmpty() || material.getMaterialComponents().size() > 15)
                continue;

            // compute outputs
            for (MaterialStack component : material.getMaterialComponents()) {
                mass += (int) (component.amount * component.material.getMass());
            }

            var buid = SCANNER_RECIPES.recipeBuilder()
                    .input(MetaItems.TOOL_DATA_STICK)
                    .outputs(itemStack)
                    .duration(100 * mass)
                    .EUt(VA[LV]);

            var copybuild = REPLICATOR_RECIPES.recipeBuilder()
                    .notConsumable(itemStack)
                    .fluidInputs(Materials.UUMatter.getFluid(mass))
                    .duration(100 * mass)
                    .EUt(30);

            if (material.hasProperty(PropertyKey.DUST)) {
                buid.input(dust, material, 1);
                copybuild.output(dust, material, 1);
            } else if (material.hasFluid()) {
                buid.fluidInputs(material.getFluid(144));
                copybuild.fluidOutputs(material.getFluid(144));
            } else
                continue;
            buid.buildAndRegister();
            copybuild.buildAndRegister();
            SCANNER_RECIPES.recipeBuilder()
                    .input(MetaItems.TOOL_DATA_STICK)
                    .notConsumable(itemStack)
                    .outputs(itemStack)
                    .duration(100)
                    .EUt(30)
                    .buildAndRegister();
        }
    }
}
