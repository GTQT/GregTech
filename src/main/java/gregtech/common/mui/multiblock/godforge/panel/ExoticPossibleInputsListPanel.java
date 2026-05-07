package gregtech.common.mui.multiblock.godforge.panel;

import static gregtech.api.metatileentity.MetaTileEntity.TOOLTIP_DELAY;
import static net.minecraft.util.text.translation.I18n.translateToLocal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Row;

import gregtech.api.mui.GTGuiTextures;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEExoticModule;
import gregtech.common.mui.multiblock.godforge.ForgeOfGodsGuiUtil;
import gregtech.common.mui.multiblock.godforge.sync.Modules;
import gregtech.common.mui.multiblock.godforge.sync.Panels;
import gregtech.common.mui.multiblock.godforge.sync.SyncHypervisor;
import gregtech.common.mui.multiblock.godforge.sync.SyncValues;

public class ExoticPossibleInputsListPanel {

    private static final int SIZE = 200;

    public static ModularPanel openPanel(SyncHypervisor hypervisor) {
        ModularPanel panel = hypervisor.getModularPanel(Modules.EXOTIC, Panels.EXOTIC_POSSIBLE_INPUTS_LIST);

        registerSyncValues(hypervisor);

        panel.size(SIZE)
            .background(GTGuiTextures.BACKGROUND_GLOW_WHITE)
            .disableHoverBackground()
            .child(ForgeOfGodsGuiUtil.panelCloseButton());

        Flow column = new Column().coverChildren()
            .marginTop(12)
            .alignX(0.5f);

        column.child(
            IKey.lang("gt.blockmachines.multimachine.FOG.exoticpossibleinputslist")
                .style(TextFormatting.GOLD)
                .alignment(Alignment.CENTER)
                .asWidget()
                .alignX(0.5f)
                .marginBottom(16));

        GenericListSyncHandler<ItemStack> exoticPossibleInputsSyncer = SyncValues.EXOTIC_POSSIBLE_INPUTS
            .lookupFrom(Modules.EXOTIC, Panels.EXOTIC_POSSIBLE_INPUTS_LIST, hypervisor);

        List<ItemStack> possibleInputs = exoticPossibleInputsSyncer.getValue();
        if (possibleInputs != null) {
            for (int i = 0; i < possibleInputs.size(); i++) {
                column.child(createPossibleInputRow(hypervisor, possibleInputs.get(i), i));
            }
        }

        panel.child(column);
        return panel;
    }

    private static void registerSyncValues(SyncHypervisor hypervisor) {
        SyncValues.EXOTIC_POSSIBLE_INPUTS.registerFor(Modules.EXOTIC, Panels.EXOTIC_POSSIBLE_INPUTS_LIST, hypervisor);
    }

    private static Flow createPossibleInputRow(SyncHypervisor hypervisor, ItemStack stack, int index) {
        Flow row = new Row().size(180, 20)
            .marginBottom(2)
            .alignX(0.5f);

        row.child(
            new ItemSlot().size(18)
                .slot(new MTEExoticModule.ExoticPossibleInputSlot(index, stack)));

        row.child(
            IKey.str(stack.getDisplayName())
                .style(TextFormatting.GREEN)
                .alignment(Alignment.CenterLeft)
                .asWidget()
                .size(158, 18)
                .marginLeft(4));

        return row;
    }
}
