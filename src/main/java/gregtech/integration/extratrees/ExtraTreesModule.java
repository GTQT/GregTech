package gregtech.integration.extratrees;

import gregtech.api.GTValues;
import gregtech.api.modules.GregTechModule;
import gregtech.api.util.Mods;
import gregtech.integration.IntegrationSubmodule;
import gregtech.integration.extratrees.recipes.ExtraTreesWoodRecipe;
import gregtech.integration.forestry.ForestryModule;
import gregtech.modules.GregTechModules;

import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@GregTechModule(
        moduleID = GregTechModules.MODULE_EXTREES,
        containerID = GTValues.MODID,
        modDependencies = { Mods.Names.FORESTRY, Mods.Names.EXTRA_TREES },
        name = "GregTech Extra Trees(Binnie's Mods) Integration",
        description = "Extra Trees(Binnie's Mods) Integration Module")
public class ExtraTreesModule extends IntegrationSubmodule {
    @NotNull
    @Override
    public List<Class<?>> getEventBusSubscribers() {
        return Collections.singletonList(ExtraTreesModule.class);
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (ExtraTreesConfig.enableGTWoodenCraftingTable) {
            ExtraTreesWoodRecipe.init();
        }
    }
}
