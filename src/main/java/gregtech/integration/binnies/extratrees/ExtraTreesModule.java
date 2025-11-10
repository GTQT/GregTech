package gregtech.integration.binnies.extratrees;

import gregtech.api.GTValues;
import gregtech.api.modules.GregTechModule;
import gregtech.api.util.Mods;
import gregtech.integration.IntegrationSubmodule;
import gregtech.integration.binnies.extratrees.recipes.ExtraTreesWoodRecipe;
import gregtech.integration.forestry.ForestryConfig;
import gregtech.integration.forestry.recipes.CombRecipes;
import gregtech.integration.forestry.recipes.ForestryElectrodeRecipes;
import gregtech.integration.forestry.recipes.ForestryExtractorRecipes;
import gregtech.integration.forestry.recipes.ForestryFrameRecipes;
import gregtech.integration.forestry.recipes.ForestryMiscRecipes;
import gregtech.integration.forestry.recipes.ForestryToolRecipes;
import gregtech.integration.forestry.recipes.ForestryWoodRecipe;
import gregtech.modules.GregTechModules;

import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@GregTechModule(
        moduleID = GregTechModules.MODULE_EXTREES,
        containerID = GTValues.MODID,
        modDependencies = { Mods.Names.FORESTRY, Mods.Names.EXTRA_TREES },
        name = "GregTech Extra Trees(Binnie's Mods) Integration",
        description = "Extra Trees(Binnie's Mods) Integration Module")
public class ExtraTreesModule extends IntegrationSubmodule {

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {

        if(ExtraTreesConfig.enableGTWoodenCraftingTable){
            ExtraTreesWoodRecipe.init();
        }
    }
}
