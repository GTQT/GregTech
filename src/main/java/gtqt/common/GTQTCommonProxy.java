package gtqt.common;

import gregtech.api.GregTechAPI;
import gregtech.api.cover.CoverDefinition;
import gregtech.common.items.MetaItems;

import gtqt.common.items.GTQTMetaItems;
import gtqt.common.items.covers.GTQTCoverBehavior;
import gtqt.common.metatileentities.GTQTMetaTileEntities;

import gtqt.loaders.recipe.RecipeManager;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.event.RegistryEvent;

public class GTQTCommonProxy {

    public static final CreativeTabs GTQTCore_PC = new CreativeTabs("gtqt_programmable") {

        @Override
        public ItemStack createIcon() {
            return MetaItems.INTEGRATED_CIRCUIT.getStackForm();
        }
    };
    public static void registerRecipeHandlers(RegistryEvent.Register<IRecipe> event) {

    }

    public static void registerCoverBehavior() {
        GTQTCoverBehavior.init();
    }

    public static void init() {

    }

    public static void preInit() {
        GTQTMetaTileEntities.initialization();
        GTQTMetaItems.initialization();
    }

    public static void registerRecipes() {
        RecipeManager.register();
    }
}
