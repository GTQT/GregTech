package gregtech.common.pipelike.heat;


import gregtech.api.pipenet.block.material.BlockMaterialPipe;
import gregtech.api.pipenet.block.material.ItemBlockMaterialPipe;
import gregtech.api.unification.material.properties.HeatConductorProperties;
import gregtech.client.utils.TooltipHelper;
import gregtech.common.ConfigHolder;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ItemBlockHeatConductor extends ItemBlockMaterialPipe<HeatConductorType, HeatConductorProperties> {

    public ItemBlockHeatConductor(BlockHeatConductor block) {
        super(block);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@NotNull ItemStack stack, @Nullable World worldIn, @NotNull List<String> tooltip,
                               @NotNull ITooltipFlag flagIn) {
        super.addInformation(stack, worldIn, tooltip, flagIn);
        HeatConductorProperties properties = blockPipe.createItemProperties(stack);

        tooltip.add(I18n.format("gregtech.heat_conductor.max_temperature", properties.getMaxTemperature()));
        tooltip.add(I18n.format("gregtech.heat_conductor.heat_transfer", properties.getHeatTransfer()));
        tooltip.add(I18n.format("gregtech.heat_conductor.heat_loss", properties.getHeatLossPerBlock()));

        if (TooltipHelper.isShiftDown()) {
            tooltip.add(I18n.format("gregtech.tool_action.wrench.connect"));
            tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
            tooltip.add(I18n.format("gregtech.tool_action.crowbar"));
        } else {
            tooltip.add(I18n.format("gregtech.tool_action.show_tooltips"));
        }

        if (ConfigHolder.misc.debug) {
            BlockMaterialPipe<?, ?, ?> blockMaterialPipe = (BlockMaterialPipe<?, ?, ?>) blockPipe;
            tooltip.add("MetaItem Id: " + blockMaterialPipe.getPrefix().name +
                    blockMaterialPipe.getItemMaterial(stack).toCamelCaseString());
        }
    }
}
