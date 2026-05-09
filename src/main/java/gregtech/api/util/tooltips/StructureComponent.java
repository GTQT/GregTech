package gregtech.api.util.tooltips;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.metatileentity.registry.MBPattern;

import net.minecraft.client.resources.I18n;

import java.util.List;

public class StructureComponent extends AbstractTooltipComponent {
    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        if (metaTileEntity instanceof MultiblockControllerBase mte) {
            BlockPatternTemplate template = mte.getPatternTemplate();
            if (template == null) return;
            tooltip.add(I18n.format("gregtech.multiblock.structure_size.tooltip",
                    template.getStructureXSize(),
                    template.getStructureYSize(),
                    template.getStructureZSize()));
            MBPattern[] patterns = GregTechAPI.getPatterns(metaTileEntity.metaTileEntityId);
            if (patterns != null && patterns.length > 1) {
                tooltip.add(I18n.format("gregtech.multiblock.structure_tier.tooltip", patterns.length));
            }
        }
    }
}
