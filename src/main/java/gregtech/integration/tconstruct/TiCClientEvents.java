package gregtech.integration.tconstruct;

import gregtech.integration.tconstruct.materials.ToolMaterialRegistrar;

import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class TiCClientEvents {

    @SubscribeEvent
    public static void onRegisterModels(ModelRegistryEvent event) {
        ToolMaterialRegistrar.registerFluidModels();
        ToolMaterialRegistrar.registerFluidBlockModelLoader();
        ToolMaterialRegistrar.suppressFluidBlockModels();
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        ToolMaterialRegistrar.injectFluidItemModels(event.getModelRegistry());
    }

    @SubscribeEvent
    public static void onTextureStitchPost(TextureStitchEvent.Post event) {
        ToolMaterialRegistrar.reinjectTranslations();
    }
}
