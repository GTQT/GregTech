package gregtech.integration.tconstruct;

import gregtech.api.GTValues;
import gregtech.api.modules.GregTechModule;
import gregtech.api.util.Mods;
import gregtech.integration.IntegrationSubmodule;
import gregtech.integration.tconstruct.handler.ToolCapabilityHandler;
import gregtech.integration.tconstruct.materials.ElasticMaterialRegistrar;
import gregtech.integration.tconstruct.materials.ToolMaterialRegistrar;
import gregtech.integration.tconstruct.village.GTVillageStructures;
import gregtech.modules.GregTechModules;

import net.minecraft.block.Block;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.Side;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@GregTechModule(
        moduleID = GregTechModules.MODULE_TIC,
        containerID = GTValues.MODID,
        modDependencies = Mods.Names.TINKERS_CONSTRUCT,
        name = "GregTech Tinkers' Construct Integration",
        description = "Tinkers' Construct Integration Module")
public class TiCModule extends IntegrationSubmodule {

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        TiCSmeltery.register();
        GTVillageStructures.register();
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        ToolMaterialRegistrar.register(event.getRegistry());
        ElasticMaterialRegistrar.register(event.getRegistry());

    }

    @NotNull
    @Override
    public List<Class<?>> getEventBusSubscribers() {
        List<Class<?>> subscribers = new ArrayList<>(Arrays.asList(
                TiCModule.class, DualToolHandler.class, ToolCapabilityHandler.class));
        if (FMLLaunchHandler.side() == Side.CLIENT) {
            subscribers.add(TiCClientEvents.class);
        }
        return subscribers;
    }
}
