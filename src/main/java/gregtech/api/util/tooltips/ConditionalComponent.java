package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import java.util.List;

public class ConditionalComponent extends AbstractTooltipComponent {
    private final ITooltipComponent component;

    public ConditionalComponent(boolean condition, ITooltipComponent component) {
        super(condition);
        this.component = component;
    }

    @Override
    public void addInformation(MultiblockControllerBase metaTileEntity, List<String> tooltip) {
        if (condition) {
            component.addInformation(metaTileEntity, tooltip);
        }
    }
}
