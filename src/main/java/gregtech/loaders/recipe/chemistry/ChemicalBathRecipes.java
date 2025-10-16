package gregtech.loaders.recipe.chemistry;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.blocks.StoneVariantBlock;
import gregtech.common.blocks.StoneVariantBlock.StoneVariant;
import gregtech.common.blocks.wood.BlockGregPlanks;

import net.minecraft.init.Items;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.*;
import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.ore.OrePrefix.*;

public class ChemicalBathRecipes {

    public static void init() {
        CHEMICAL_BATH_RECIPES.recipeBuilder()
                .input(dust, Wood)
                .fluidInputs(Water.getFluid(100))
                .output(Items.PAPER)
                .duration(200).EUt(4).buildAndRegister();

        CHEMICAL_BATH_RECIPES.recipeBuilder()
                .input(dust, Paper)
                .fluidInputs(Water.getFluid(100))
                .output(Items.PAPER)
                .duration(100).EUt(4).buildAndRegister();

        CHEMICAL_BATH_RECIPES.recipeBuilder()
                .input(Items.REEDS, 1, true)
                .fluidInputs(Water.getFluid(100))
                .output(Items.PAPER)
                .duration(100).EUt(VA[ULV]).buildAndRegister();

        CHEMICAL_BATH_RECIPES.recipeBuilder()
                .input(dust, Wood)
                .fluidInputs(DistilledWater.getFluid(100))
                .output(Items.PAPER)
                .duration(200).EUt(4).buildAndRegister();

        CHEMICAL_BATH_RECIPES.recipeBuilder()
                .input(dust, Paper)
                .fluidInputs(DistilledWater.getFluid(100))
                .output(Items.PAPER)
                .duration(100).EUt(4).buildAndRegister();

        CHEMICAL_BATH_RECIPES.recipeBuilder()
                .input(Items.REEDS, 1, true)
                .fluidInputs(DistilledWater.getFluid(100))
                .output(Items.PAPER)
                .duration(100).EUt(VA[ULV]).buildAndRegister();

        CHEMICAL_BATH_RECIPES.recipeBuilder()
                .input("plankWood", 1)
                .fluidInputs(Creosote.getFluid(100))
                .outputs(MetaBlocks.PLANKS.getItemVariant(BlockGregPlanks.BlockType.TREATED_PLANK))
                .duration(100).EUt(VA[ULV]).buildAndRegister();

        CHEMICAL_BATH_RECIPES.recipeBuilder()
                .inputs(MetaBlocks.STONE_BLOCKS.get(StoneVariant.SMOOTH)
                        .getItemVariant(StoneVariantBlock.StoneType.CONCRETE_LIGHT))
                .fluidInputs(Water.getFluid(100))
                .outputs(MetaBlocks.STONE_BLOCKS.get(StoneVariant.SMOOTH)
                        .getItemVariant(StoneVariantBlock.StoneType.CONCRETE_DARK))
                .duration(100).EUt(VA[ULV]).buildAndRegister();

        CHEMICAL_BATH_RECIPES.recipeBuilder()
                .input(dust, Scheelite, 6)
                .fluidInputs(HydrochloricAcid.getFluid(2000))
                .output(dust, TungsticAcid, 7)
                .output(dust, CalciumChloride, 3)
                .duration(210).EUt(960).buildAndRegister();

        CHEMICAL_BATH_RECIPES.recipeBuilder()
                .input(dust, Tungstate, 7)
                .fluidInputs(HydrochloricAcid.getFluid(2000))
                .output(dust, TungsticAcid, 7)
                .output(dust, LithiumChloride, 4)
                .duration(210).EUt(960).buildAndRegister();

        //冷却
        ChemicalBathCoolMaterial(Kanthal);
        ChemicalBathCoolMaterial(StainlessSteel);
        ChemicalBathCoolMaterial(Silicon);
        ChemicalBathCoolMaterial(BlackSteel);
        ChemicalBathCoolMaterial(RedSteel);
        ChemicalBathCoolMaterial(BlueSteel);

        MIXER_RECIPES.recipeBuilder()
                .input(dust, Lapis,9)
                .fluidInputs(Water.getFluid(1000))
                .fluidOutputs(WaterCoolant.getFluid(1000))
                .duration(200)
                .EUt(VA[MV]).buildAndRegister();

        CHEMICAL_BATH_RECIPES.recipeBuilder()
                .fluidInputs(HotWaterCoolant.getFluid(100))
                .fluidOutputs(WaterCoolant.getFluid(100))
                .duration(200)
                .EUt(VA[LV]).buildAndRegister();
    }

    public static void ChemicalBathCoolMaterial(Material material)
    {
        BATH_CONDENSER_RECIPES.recipeBuilder()
                .input(ingotHot, material)
                .fluidInputs(DistilledWater.getFluid(400))
                .output(OrePrefix.ingot, material)
                .duration(1200).EUt(VA[MV]).buildAndRegister();

        BATH_CONDENSER_RECIPES.recipeBuilder()
                .input(ingotHot, material)
                .fluidInputs(WaterCoolant.getFluid(100))
                .output(OrePrefix.ingot, material)
                .fluidOutputs(HotWaterCoolant.getFluid(100))
                .duration(400).EUt(VA[MV]).buildAndRegister();
    }
}
