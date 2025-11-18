package gregtech.api.util.tooltips;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;

import java.util.List;

public class StructureComponent extends AbstractTooltipComponent {
    @Override
    public void addInformation(MultiblockControllerBase metaTileEntity, List<String> tooltip) {
        if (metaTileEntity.structurePattern != null && TooltipHelper.isShiftDown()) {
            int tier = GregTechAPI.getPatterns(metaTileEntity.metaTileEntityId).length;
            tooltip.add(I18n.format("gregtech.multiblock.structure_size.tooltip",
                    metaTileEntity.structurePattern.getStructureXSize(),
                    metaTileEntity.structurePattern.getStructureYSize(),
                    metaTileEntity.structurePattern.getStructureZSize()));
            if(tier > 1)
                tooltip.add(I18n.format("gregtech.multiblock.structure_tier.tooltip", tier));
        }
    }
}
