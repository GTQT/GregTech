package gregtech.integration.bbw;

import gregtech.api.GTValues;
import gregtech.api.modules.GregTechModule;
import gregtech.api.util.Mods;
import gregtech.integration.IntegrationSubmodule;
import gregtech.integration.bbw.recipes.BBWToolsRecipe;
import gregtech.integration.bbw.tools.BBWToolItems;
import gregtech.modules.GregTechModules;

import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@GregTechModule(
        moduleID = GregTechModules.MODULE_BETWEENLANDS,
        containerID = GTValues.MODID,
        modDependencies = Mods.Names.BETTER_BUILDERS_WANDS,
        name = "GTMoreTools Better Builder's Wands Integration",
        description = "Better Builder's Wands Integration Module")
public class BBWModule extends IntegrationSubmodule {

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        BBWToolsRecipe.init();
    }

    @NotNull
    @Override
    public List<Class<?>> getEventBusSubscribers() {
        return Collections.singletonList(BBWModule.class);
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        BBWToolItems.init();
    }
}
