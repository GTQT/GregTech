package gregtech.common;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.registry.MTEManager;
import gregtech.api.unification.material.event.MaterialRegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import org.jetbrains.annotations.ApiStatus;

import gregtech.api.unification.material.event.MaterialEvent;
import gregtech.api.unification.material.event.PostMaterialEvent;

import gregtech.api.GCYMValues;
import gregtech.api.fluids.GeneratedFluidHandler;
import gregtech.api.unification.GCYMMaterialFlagAddition;
import gregtech.api.unification.GCYMMaterials;
import gregtech.api.unification.properties.AlloyBlastPropertyAddition;

@Mod.EventBusSubscriber(modid = GTValues.MODID)
public final class GCYMEventHandlers {

    private GCYMEventHandlers() {}

    @SubscribeEvent
    public static void registerMTERegistry(MTEManager.MTERegistryEvent event) {
        GregTechAPI.mteManager.createRegistry(GCYMValues.MODID);
    }
    @SubscribeEvent
    public static void createMaterialRegistry(MaterialRegistryEvent event) {
        GregTechAPI.materialManager.createRegistry(GCYMValues.MODID);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void registerMaterials(MaterialEvent event) {
        GCYMMaterials.init();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void registerMaterialsPost(PostMaterialEvent event) {
        AlloyBlastPropertyAddition.init();
        GCYMMaterialFlagAddition.initLate();
        GeneratedFluidHandler.init();
    }
}
