package gregtech.api.util.tooltips;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.StructureSizeDescriptor;
import gregtech.api.pattern.element.StructureDefinition;

import net.minecraft.client.resources.I18n;

import java.util.List;

public class StructureComponent extends AbstractTooltipComponent {
    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        if (metaTileEntity instanceof MultiblockControllerBase mte) {
            StructureDefinition<?> definition = mte.getStructureDefinition();
            StructureSizeDescriptor size = definition.getStructureSizeDescriptor();
            tooltip.add(I18n.format("gregtech.multiblock.structure_size.tooltip",
                    size.getFormattedPalm(),
                    size.getFormattedThumb(),
                    size.getFormattedFinger()));
            int patternCount = GregTechAPI.getPatternCount(metaTileEntity.metaTileEntityId);
            if (patternCount > 1) {
                tooltip.add(I18n.format("gregtech.multiblock.structure_tier.tooltip", patternCount));
            }
        }
    }
}
