package gregtech.api.gui.resources;

import net.minecraft.util.ResourceLocation;

/** Flux Networks GUI textures (MIT, BloCamLimb); see META-INF/NOTICE. */
public final class FluxWirelessTextures {

    public static final int NETWORK_COLOR = 0x295E8A;
    /* Flux's 512px files deliberately use the 256px logical grid from Gui#drawTexturedModalRect. */
    public static final TextureArea BACKGROUND = area("gui_default_background.png", 0, 0, 256, 256);
    public static final TextureArea FRAME = area("gui_default_frame.png", 0, 0, 256, 256);

    private FluxWirelessTextures() {}

    public static TextureArea bar() {
        return bar(0, 0, 135, 12);
    }

    public static TextureArea bar(int u, int v, int width, int height) {
        return area("gui_bar.png", u, v, width, height);
    }

    public static TextureArea button(int u, int v, int width, int height) {
        return area("gui_button.png", u, v, width, height);
    }

    public static TextureArea inventory(int u, int v, int width, int height) {
        return area("inventory_configuration.png", u, v, width, height);
    }

    private static TextureArea area(String texture, int u, int v, int width, int height) {
        /*
         * TextureArea.areaOfImage stores (u + width) as a width. That is harmless at (0, 0),
         * but stretches every Flux sprite beyond the first column or row.
         */
        return new TextureArea(new ResourceLocation("gregtech", "textures/gui/wireless/flux_style/" + texture),
                u / 256.0, v / 256.0, width / 256.0, height / 256.0);
    }
}
