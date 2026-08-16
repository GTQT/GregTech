package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.client.resources.I18n;

import java.util.List;

public class StructureFromComponent extends AbstractTooltipComponent {

    private final String structurePath;

    public StructureFromComponent(String structurePath) {
        this.structurePath = structurePath;
    }

    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        tooltip.add(I18n.format("gregtech.tooltip.structure_from", structurePath));
    }
}
