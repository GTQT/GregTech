package gregtech.client;

import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import gregtech.api.GTValues;
import gregtech.common.blocks.GCYMMetaBlocks;

@Mod.EventBusSubscriber(modid = GTValues.MODID, value = Side.CLIENT)
public class GCYMClientEventHandler {

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        GCYMMetaBlocks.registerItemModels();
    }
}
