package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.MetaTileEntity;

import java.util.List;

public class ConditionalComponent extends AbstractTooltipComponent {
    private final ITooltipComponent component;

    public ConditionalComponent(boolean condition, ITooltipComponent component) {
        super(condition);
        this.component = component;
    }

    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        if (condition) {
            component.addInformation(metaTileEntity, tooltip);
        }
    }
}
