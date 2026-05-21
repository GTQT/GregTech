package gregtech.common.mui.multiblock.godforge.panel;

import gregtech.api.mui.GTGuiTextures;
import gregtech.common.mui.multiblock.godforge.ForgeOfGodsGuiUtil;
import gregtech.common.mui.multiblock.godforge.sync.Panels;
import gregtech.common.mui.multiblock.godforge.sync.SyncHypervisor;

import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;

import java.util.ArrayList;
import java.util.List;

public class SpecialThanksPanel {

    private static final int SIZE = 200;

    private static final List<String> SPECIAL_THANKS = new ArrayList<>();

    static {
        SPECIAL_THANKS.add("GTNH Team");
    }

    public static ModularPanel openPanel(SyncHypervisor hypervisor) {
        ModularPanel panel = hypervisor.getModularPanel(Panels.SPECIAL_THANKS);

        panel.size(SIZE)
                .background(GTGuiTextures.BACKGROUND_GLOW_WHITE)
                .disableHoverBackground()
                .child(ForgeOfGodsGuiUtil.panelCloseButton());

        Flow column = new Column().coverChildren()
                .marginTop(12)
                .alignX(0.5f);

        column.child(
                IKey.lang("gt.blockmachines.multimachine.FOG.specialthanks")
                        .style(TextFormatting.GOLD)
                        .alignment(Alignment.CENTER)
                        .asWidget()
                        .alignX(0.5f)
                        .marginBottom(16));

        for (String name : SPECIAL_THANKS) {
            column.child(
                    IKey.str(name)
                            .style(TextFormatting.GREEN)
                            .alignment(Alignment.CENTER)
                            .asWidget()
                            .alignX(0.5f)
                            .marginBottom(2));
        }

        panel.child(column);
        return panel;
    }
}
