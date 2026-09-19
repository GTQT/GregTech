package gregtech.common.pipelike.optical;

import gregtech.api.pipenet.block.material.ItemBlockMaterialPipe;
import gregtech.api.unification.material.properties.OpticalCableProperties;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ItemBlockOpticalPipe extends ItemBlockMaterialPipe<OpticalPipeType, OpticalCableProperties> {

    public ItemBlockOpticalPipe(BlockOpticalPipe block) {
        super(block);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@NotNull ItemStack stack, @Nullable World worldIn, @NotNull List<String> tooltip,
                               @NotNull ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        OpticalCableProperties properties = blockPipe.createItemProperties(stack);
        if (properties != null) {
            tooltip.add(I18n.format("gregtech.optical_pipe.max_computation", properties.getMaxCWUt()));
            tooltip.add(I18n.format("gregtech.optical_pipe.decay_distance", properties.getDecayDistance()));
        }

        if (TooltipHelper.isShiftDown()) {
            tooltip.add(I18n.format("gregtech.tool_action.wire_cutter.connect"));
        } else {
            tooltip.add(I18n.format("gregtech.tool_action.show_tooltips"));
        }
    }
}
