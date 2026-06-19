package gregtech.integration.theoneprobe.element;

import gregtech.api.gui.IFluidStyle;
import gregtech.api.gui.impl.FluidStyle;
import gregtech.api.util.TextFormattingUtil;
import gregtech.api.utils.FluidStackHelper;
import gregtech.client.utils.RenderUtil;
import gregtech.integration.theoneprobe.TheOneProbeModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fluids.FluidStack;

import io.netty.buffer.ByteBuf;
import mcjty.theoneprobe.api.IElement;
import org.jetbrains.annotations.NotNull;

/**
 * New TOP Element for Fluid stacks.
 *
 * @author Gate Guardian (original author)
 *
 * <p>
 *     This class is port from Gate Guardian's work for 1.20.1,
 *      <a href="https://github.com/EpimorphismMC/Monazite">Monazite</a>.
 * </p>
 */

public class FluidStackElement implements IElement {

    private final FluidStack fluidStack;
    private final IFluidStyle style;

    public FluidStackElement(FluidStack fluidStack, IFluidStyle style) {
        this.fluidStack = fluidStack;
        this.style = style;
    }

    public FluidStackElement(@NotNull ByteBuf buf) {
        if (buf.readBoolean()) {
            this.fluidStack = FluidStackHelper.readFromBuf(buf);
        } else {
            this.fluidStack = null;
        }
        this.style = new FluidStyle().width(buf.readInt()).height(buf.readInt());
    }

    @Override
    public void render(int x, int y) {
        if (this.fluidStack.getFluid() != null) {
            GlStateManager.disableBlend();
            RenderUtil.drawFluidForGui(fluidStack, fluidStack.amount,
                    x,
                    y,
                    16, 16);

            GlStateManager.pushMatrix();
            GlStateManager.scale(0.5, 0.5, 1);

            String fluidAmount = TextFormattingUtil.formatLongToCompactString(fluidStack.amount, 4) + "L";

            FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
            fontRenderer.drawStringWithShadow(fluidAmount,
                    (x + 7) * 2 - fontRenderer.getStringWidth(fluidAmount) + 19,
                    (y + 11) * 2,
                    0xFFFFFF);

            GlStateManager.popMatrix();
            GlStateManager.enableBlend();
        }
    }

    @Override
    public int getWidth() {
        return this.style.width();
    }

    @Override
    public int getHeight() {
        return this.style.height();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        if (this.fluidStack.getFluid() != null) {
            buf.writeBoolean(true);
            FluidStackHelper.writeToBuf(buf, this.fluidStack);
        } else {
            buf.writeBoolean(false);
        }
        buf.writeInt(style.width());
        buf.writeInt(style.height());
    }

    @Override
    public int getID() {
        return TheOneProbeModule.FLUID_STACK_ELEMENT;
    }

}
