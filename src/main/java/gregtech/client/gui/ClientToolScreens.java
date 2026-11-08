package gregtech.client.gui;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.integration.jei.multiblock.MultiblockInfoRecipeWrapper;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client-side entry point for screens opened from common item behaviours.
 *
 * <p>Keeping the client class reference behind this thin indirection lets common code dispatch a screen without
 * pulling client-only widget classes into the server's link path.
 */
@SideOnly(Side.CLIENT)
public final class ClientToolScreens {

    private ClientToolScreens() {}

    /** Opens the world-backed builder preview; this is not the read-only JEI viewer. */
    public static void openControllerPreview(MultiblockControllerBase controller,
                                             java.util.Map<String, Integer> channels) {
        MultiblockInfoRecipeWrapper wrapper = new MultiblockInfoRecipeWrapper(controller);
        if (channels != null) {
            wrapper.seedPreviewChannels(channels);
        }
        wrapper.openBuilderPreview();
    }
}
