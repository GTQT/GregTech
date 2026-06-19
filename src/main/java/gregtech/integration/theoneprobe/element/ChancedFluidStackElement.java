package gregtech.integration.theoneprobe.element;

import gregtech.api.gui.impl.FluidStyle;
import gregtech.integration.theoneprobe.TheOneProbeModule;
import gregtech.integration.theoneprobe.provider.RecipeOutputInfoProvider;

import net.minecraftforge.fluids.FluidStack;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

/*
 * From : https://github.com/Supernoobv/GregicProbeCEu/blob/master/src/main/java/vfyjxf/gregicprobe/element/ChancedFluidStackElement.java
 */
public class ChancedFluidStackElement extends FluidStackElement {
    private final int chance;

    public ChancedFluidStackElement(@NotNull FluidStack stack, int chance) {
        super(stack, new FluidStyle());
        this.chance = chance;
    }

    public ChancedFluidStackElement(@NotNull ByteBuf buf) {
        super(buf);
        chance = buf.readInt();
    }

    @Override
    public void render(int x, int y) {
        super.render(x, y);
        RecipeOutputInfoProvider.renderChance(chance, x, y);
    }

    @Override
    public void toBytes(@NotNull ByteBuf buf) {
        super.toBytes(buf);
        buf.writeInt(chance);
    }

    @Override
    public int getID() {
        return TheOneProbeModule.CHANCED_FLUID_STACK_ELEMENT;
    }
}
