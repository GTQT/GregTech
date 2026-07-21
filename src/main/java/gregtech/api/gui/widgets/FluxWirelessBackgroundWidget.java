package gregtech.api.gui.widgets;

import gregtech.api.gui.IRenderContext;
import gregtech.api.gui.Widget;
import gregtech.api.gui.resources.FluxWirelessTextures;
import gregtech.api.util.Position;
import gregtech.api.util.Size;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Reproduces GuiFluxCore's 256px background around Flux's 176px container. */
public class FluxWirelessBackgroundWidget extends Widget {

    public FluxWirelessBackgroundWidget() {
        super(new Position(-40, -45), new Size(256, 256));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawInBackground(int mouseX, int mouseY, float partialTicks, IRenderContext context) {
        Position position = getPosition();
        FluxWirelessTextures.BACKGROUND.draw(position.x, position.y, 256, 256);
        int color = FluxWirelessTextures.NETWORK_COLOR;
        GlStateManager.color((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F, 1.0F);
        FluxWirelessTextures.FRAME.draw(position.x, position.y, 256, 256);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
