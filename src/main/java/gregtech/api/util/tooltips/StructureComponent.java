package gregtech.api.util.tooltips;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;

import java.util.List;

public class StructureComponent extends AbstractTooltipComponent {
    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {

        if (metaTileEntity instanceof MultiblockControllerBase mte && TooltipHelper.isShiftDown()) {
            BlockPatternTemplate template = mte.getPatternTemplate();
            if (template == null) return;
            int tier = GregTechAPI.getPatterns(metaTileEntity.metaTileEntityId).length;
            tooltip.add(I18n.format("gregtech.multiblock.structure_size.tooltip",
                    template.getStructureXSize(),
                    template.getStructureYSize(),
                    template.getStructureZSize()));
            if(tier > 1)
                tooltip.add(I18n.format("gregtech.multiblock.structure_tier.tooltip", tier));
        }
    }
}
