package gtqt.loaders.recipe.handlers;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import static gregtech.api.GTValues.*;
import static gregtech.api.recipes.RecipeMaps.LOOM_RECIPES;
import static gregtech.common.items.MetaItems.PLANT_BALL;

public class LoomRecipes {
    public static void init() {

        // 4x string -> 1x web
        LOOM_RECIPES.recipeBuilder()
                .circuitMeta(3)
                .inputs(new ItemStack(Items.STRING, 4))
                .outputs(new ItemStack(Blocks.WEB))
                .EUt(V[ULV])
                .duration(SECOND)
                .buildAndRegister();

        LOOM_RECIPES.recipeBuilder()
                .circuitMeta(4)
                .inputs(new ItemStack(Items.STRING, 4))
                .outputs(new ItemStack(Blocks.WOOL,1,0))
                .EUt(VA[LV])
                .duration(2 * SECOND)
                .buildAndRegister();

        // 8x string -> 3x carpet
        LOOM_RECIPES.recipeBuilder()
                .circuitMeta(8)
                .inputs(new ItemStack(Items.STRING, 8))
                .outputs(new ItemStack(Blocks.CARPET, 3))
                .EUt(VA[LV])
                .duration(2 * SECOND)
                .buildAndRegister();

        // Leather armors.
        LOOM_RECIPES.recipeBuilder()
                .circuitMeta(5)
                .inputs(new ItemStack(Items.LEATHER, 5))
                .outputs(new ItemStack(Items.LEATHER_HELMET))
                .EUt(VA[ULV])
                .duration(2 * SECOND + 10 * TICK)
                .buildAndRegister();

        LOOM_RECIPES.recipeBuilder()
                .circuitMeta(8)
                .inputs(new ItemStack(Items.LEATHER, 8))
                .outputs(new ItemStack(Items.LEATHER_CHESTPLATE))
                .EUt(VA[ULV])
                .duration(2 * SECOND + 10 * TICK)
                .buildAndRegister();

        LOOM_RECIPES.recipeBuilder()
                .circuitMeta(7)
                .inputs(new ItemStack(Items.LEATHER, 7))
                .outputs(new ItemStack(Items.LEATHER_LEGGINGS))
                .EUt(VA[ULV])
                .duration(2 * SECOND + 10 * TICK)
                .buildAndRegister();

        LOOM_RECIPES.recipeBuilder()
                .circuitMeta(4)
                .inputs(new ItemStack(Items.LEATHER, 4))
                .outputs(new ItemStack(Items.LEATHER_BOOTS))
                .EUt(VA[ULV])
                .duration(2 * SECOND + 10 * TICK)
                .buildAndRegister();

        // 1x plant ball -> 2x grass
        LOOM_RECIPES.recipeBuilder()
                .circuitMeta(1)
                .input(PLANT_BALL, 1)
                .outputs(new ItemStack(Blocks.TALLGRASS, 1, 1))
                .EUt(VA[ULV])
                .duration(2 * SECOND)
                .buildAndRegister();

        // 1x plant ball -> 2x tall grass
        LOOM_RECIPES.recipeBuilder()
                .circuitMeta(2)
                .input(PLANT_BALL)
                .outputs(new ItemStack(Blocks.TALLGRASS, 1, 2))
                .EUt(VA[ULV])
                .duration(2 * SECOND)
                .buildAndRegister();

        // 2x plant ball -> 1x vine
        LOOM_RECIPES.recipeBuilder()
                .circuitMeta(3)
                .input(PLANT_BALL, 2)
                .outputs(new ItemStack(Blocks.VINE))
                .EUt(VA[ULV])
                .duration(2 * SECOND)
                .buildAndRegister();

        // 4x plant ball -> 1x waterlily
        LOOM_RECIPES.recipeBuilder()
                .circuitMeta(4)
                .input(PLANT_BALL, 4)
                .outputs(new ItemStack(Blocks.WATERLILY))
                .EUt(VA[ULV])
                .duration(2 * SECOND)
                .buildAndRegister();
    }
}
