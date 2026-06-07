package gregtech.integration.theoneprobe.element;

import gregtech.integration.theoneprobe.TheOneProbeModule;
import gregtech.integration.theoneprobe.provider.RecipeOutputInfoProvider;

import net.minecraft.item.ItemStack;

import io.netty.buffer.ByteBuf;
import mcjty.theoneprobe.api.IItemStyle;
import mcjty.theoneprobe.apiimpl.elements.ElementItemStack;

/*
 * From : https://github.com/Supernoobv/GregicProbeCEu/blob/master/src/main/java/vfyjxf/gregicprobe/element/ChancedItemStackElement.java
 */
public class ChancedItemStackElement extends ElementItemStack {

    private final int chance;

    public ChancedItemStackElement(ItemStack itemStack, int chance, IItemStyle style) {
        super(itemStack, style);
        this.chance = chance;
    }

    public ChancedItemStackElement(ByteBuf buf) {
        super(buf);
        chance = buf.readInt();
    }

    @Override
    public void render(int x, int y) {
        super.render(x, y);
        RecipeOutputInfoProvider.renderChance(chance, x, y);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        super.toBytes(buf);
        buf.writeInt(chance);
    }

    @Override
    public int getID() {
        return TheOneProbeModule.CHANCED_ITEM_STACK_ELEMENT;
    }
}
