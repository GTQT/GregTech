package gregicality.multiblocks.common;

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

import gregicality.multiblocks.GregicalityMultiblocks;
import gregicality.multiblocks.api.fluids.GeneratedFluidHandler;
import gregicality.multiblocks.api.unification.GCYMMaterialFlagAddition;
import gregicality.multiblocks.api.unification.GCYMMaterials;
import gregicality.multiblocks.api.unification.properties.AlloyBlastPropertyAddition;

@Mod.EventBusSubscriber(modid = GTValues.MODID)
public final class GCYMEventHandlers {

    private GCYMEventHandlers() {}

    @SubscribeEvent
    public static void registerMTERegistry(MTEManager.MTERegistryEvent event) {
        GregTechAPI.mteManager.createRegistry(GregicalityMultiblocks.MODID);
    }
    @SubscribeEvent
    public static void createMaterialRegistry(MaterialRegistryEvent event) {
        GregTechAPI.materialManager.createRegistry(GregicalityMultiblocks.MODID);
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
