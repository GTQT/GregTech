package gregtech.common;

import gregtech.api.GTValues;
import gregtech.api.unification.material.event.MaterialEvent;
import gregtech.api.unification.material.event.PostMaterialEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import gregtech.SCValues;
import gregtech.api.unification.material.SCMaterialFlagAddition;
import gregtech.api.unification.material.SCMaterialPropertyAddition;
import gregtech.api.unification.material.SCMaterials;
import gregtech.api.unification.ore.SCOrePrefix;
import gregtech.common.materials.MaterialModifications;
import gregtech.common.items.MetaItems;

@Mod.EventBusSubscriber(modid = GTValues.MODID)
public final class SCEventHandlers {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void registerMaterials(MaterialEvent event) {
        SCMaterials.register();
        SCOrePrefix.init();
        SCMaterialFlagAddition.init();
        SCMaterialPropertyAddition.init();
    }

    @SubscribeEvent
    public static void registerMaterialsPost(PostMaterialEvent event) {
        MetaItems.addOrePrefix(
                SCOrePrefix.fuelRod,
                SCOrePrefix.fuelRodDepleted,
                SCOrePrefix.fuelPelletRaw,
                SCOrePrefix.fuelRodHotDepleted,
                SCOrePrefix.fuelPellet,
                SCOrePrefix.fuelPelletDepleted,
                SCOrePrefix.dustSpentFuel,
                SCOrePrefix.dustBredFuel,
                SCOrePrefix.dustFissionByproduct,
                SCOrePrefix.fuelPebble,
                SCOrePrefix.fuelPebbleDepleted);

        if (ConfigHolder.nuclearMisc.enableMaterialModifications) {
            MaterialModifications.init();
        }
    }
}
