package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.ParallelLogicType;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class ParallelLogicTypeComponent extends AbstractTooltipComponent {

    ParallelLogicType type;

    public ParallelLogicTypeComponent(ParallelLogicType type) {
        this.type = type;
    }

    @Override
    public void addInformation(MultiblockControllerBase metaTileEntity, List<String> tooltip) {
        tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.multiblock.parallel_mode"));
        if (type == ParallelLogicType.MULTIPLY)
            tooltip.add(I18n.format("gregtech.multiblock.parallel_mode.multiply"));
        if (type == ParallelLogicType.APPEND_ITEMS)
            tooltip.add(I18n.format("gregtech.multiblock.parallel_mode.append_items"));
        if (type == ParallelLogicType.APPEND_FLUIDS)
            tooltip.add(I18n.format("gregtech.multiblock.parallel_mode.append_fluids"));
        if (type == ParallelLogicType.APPEND_ALL)
            tooltip.add(I18n.format("gregtech.multiblock.parallel_mode.append_all"));
    }
}
