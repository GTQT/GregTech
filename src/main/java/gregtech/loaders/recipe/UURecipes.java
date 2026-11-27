package gregtech.loaders.recipe;

import gregtech.api.GregTechAPI;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.util.GTLog;
import gregtech.common.items.MetaItems;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.REPLICATOR_RECIPES;
import static gregtech.api.recipes.RecipeMaps.SCANNER_RECIPES;
import static gregtech.api.unification.material.info.MaterialFlags.DISABLE_REPLICATE;
import static gregtech.api.unification.ore.OrePrefix.dust;
import static gregtech.common.items.MetaItems.*;

public class UURecipes {

    public static void init() {
        initRecycleRecipe();
        UUUtils();
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

        // 回收肥料
        RecipeMaps.COMPRESSOR_RECIPES.recipeBuilder()
                .input(SCRAP)
                .output(SCRAP_BOX)
                .EUt(VA[LV])
                .duration(5 * SECOND)
                .buildAndRegister();

        // UU 增幅液
        RecipeMaps.MASS_FABRICATOR_RECIPES.recipeBuilder()
                .input(SCRAP)
                .fluidOutputs(Materials.UUAmplifier.getFluid(1))
                .EUt(VA[MV])
                .duration(10 * SECOND)
                .buildAndRegister();

        // UU 物质
        RecipeMaps.MASS_FABRICATOR_RECIPES.recipeBuilder()
                .circuitMeta(1)
                .fluidOutputs(Materials.UUMatter.getFluid(1))
                .EUt(VA[MV])
                .duration(160 * SECOND)
                .buildAndRegister();

        RecipeMaps.MASS_FABRICATOR_RECIPES.recipeBuilder()
                .fluidInputs(Materials.UUAmplifier.getFluid(1))
                .fluidOutputs(Materials.UUMatter.getFluid(1))
                .EUt(VA[MV])
                .duration(40 * SECOND)
                .buildAndRegister();

        RecipeMaps.FLUID_SOLIDFICATION_RECIPES.recipeBuilder()
                .fluidInputs(Materials.UUMatter.getFluid(1000))
                .output(UU_MATER)
                .EUt(VA[LV])
                .duration(10 * SECOND)
                .buildAndRegister();

        RecipeMaps.EXTRACTOR_RECIPES.recipeBuilder()
                .input(UU_MATER)
                .fluidOutputs(Materials.UUMatter.getFluid(1000))
                .EUt(VA[LV])
                .duration(10 * SECOND)
                .buildAndRegister();
    }

    private static void UUUtils() {

        //扫描和复制配方
        Set<String> registered = new HashSet<>();
        for (Material material : GregTechAPI.materialManager.getRegisteredMaterials()) {

            if (!registered.add(material.getName()) || material.hasFlag(DISABLE_REPLICATE) ||
                    material.getMaterialComponents().isEmpty() ||
                    material.getMaterialComponents().size() > 15)
                continue;

            // 先解析代表产物：部分材料带有 DUST/FLUID 属性却没有实际注册出来的物品或流体，
            // 直接注册会得到没有任何输出的配方，被 RecipeMap 的校验拒绝。
            ItemStack primaryOutput = ItemStack.EMPTY;
            FluidStack primaryFluid = null;
            if (material.hasProperty(PropertyKey.DUST)) {
                primaryOutput = OreDictUnifier.get(dust, material, 1);
            }
            if (primaryOutput.isEmpty() && material.hasFluid()) {
                primaryFluid = material.getFluid(144);
                if (primaryFluid != null && primaryFluid.amount <= 0) {
                    primaryFluid = null;
                }
            }
            if (primaryOutput.isEmpty() && primaryFluid == null) {
                GTLog.logger.warn("Skipping UU recipe for material {}: no dust or fluid output",
                        material.getName());
                continue;
            }

            int mass = 0;

            // compute outputs
            for (MaterialStack component : material.getMaterialComponents()) {
                mass += (int) (component.amount * component.material.getMass());
            }

            ItemStack itemStack = MetaItems.TOOL_DATA_STICK.getStackForm();
            NBTTagCompound compound = new NBTTagCompound();
            compound.setString("Name", material.getLocalizedName());
            // 额外记录唯一材料名：仅靠本地化名称时，两个同名的材料会生成完全
            // 相同的数据棒，从而注册出重复的扫描/复制配方。
            compound.setString("Material", material.getName());
            itemStack.setTagCompound(compound);

            var build = SCANNER_RECIPES.recipeBuilder()
                    .input(MetaItems.TOOL_DATA_STICK)
                    .outputs(itemStack)
                    .duration(100 * mass)
                    .EUt(VA[LV]);

            var copyBuild = REPLICATOR_RECIPES.recipeBuilder()
                    .notConsumable(itemStack)
                    .fluidInputs(Materials.UUMatter.getFluid(mass))
                    .duration(100 * mass)
                    .EUt(VA[LV]);

            if (!primaryOutput.isEmpty()) {
                build.inputs(primaryOutput);
                copyBuild.outputs(primaryOutput);
            } else {
                build.fluidInputs(primaryFluid.copy());
                copyBuild.fluidOutputs(primaryFluid.copy());
            }
            build.buildAndRegister();
            copyBuild.buildAndRegister();

            SCANNER_RECIPES.recipeBuilder()
                    .input(MetaItems.TOOL_DATA_STICK)
                    .notConsumable(itemStack)
                    .outputs(itemStack)
                    .duration(5 * SECOND)
                    .EUt(VA[LV])
                    .buildAndRegister();
        }
    }
}
