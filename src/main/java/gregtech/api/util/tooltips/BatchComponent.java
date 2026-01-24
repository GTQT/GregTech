package gregtech.api.util.tooltips;

import gregtech.api.capability.IBatch;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class BatchComponent extends AbstractTooltipComponent {
    @Override
    public void addInformation(MetaTileEntity metaTileEntity, List<String> tooltip) {
        if (metaTileEntity instanceof IBatch iBatch && iBatch.isBatchAllowed()) {
            tooltip.add(TextFormatting.GREEN + I18n.format("gregtech.tooltip.batch_available"));
            if (TooltipHelper.isCtrlDown()) {
                tooltip.add(TextFormatting.GRAY + I18n.format("gregtech.machine.batch_process.tooltips.1"));
                tooltip.add(TextFormatting.GRAY + I18n.format("gregtech.machine.batch_process.tooltips.2"));
                tooltip.add(TextFormatting.GRAY + I18n.format("gregtech.machine.batch_process.tooltips.3"));
            } else {
                tooltip.add(I18n.format("gregtech.tooltip.ctrl"));
            }
        }
    }
}
