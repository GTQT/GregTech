package gregtech.common.mui.multiblock.godforge.panel;

import static gregtech.api.metatileentity.MetaTileEntity.TOOLTIP_DELAY;
import static net.minecraft.util.text.translation.I18n.translateToLocal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;

import gregtech.api.mui.GTGuiTextures;
import gregtech.common.mui.multiblock.godforge.ForgeOfGodsGuiUtil;
import gregtech.common.mui.multiblock.godforge.sync.Panels;
import gregtech.common.mui.multiblock.godforge.sync.SyncHypervisor;

public class SpecialThanksPanel {

    private static final int SIZE = 200;

    private static final List<String> SPECIAL_THANKS = new ArrayList<>();

    static {
        SPECIAL_THANKS.add("BlueWeabo");
        SPECIAL_THANKS.add("Caedis");
        SPECIAL_THANKS.add("C0bra5");
        SPECIAL_THANKS.add("Colen");
        SPECIAL_THANKS.add("Draknyte1");
        SPECIAL_THANKS.add("DreamMasterXXL");
        SPECIAL_THANKS.add("Elisis");
        SPECIAL_THANKS.add("FlyingPerson");
        SPECIAL_THANKS.add("Glease");
        SPECIAL_THANKS.add("Glowredman");
        SPECIAL_THANKS.add("GrigLog");
        SPECIAL_THANKS.add("HoleFish");
        SPECIAL_THANKS.add("Jakob");
        SPECIAL_THANKS.add("Kuba");
        SPECIAL_THANKS.add("MauveCloud");
        SPECIAL_THANKS.add("MineTweaker");
        SPECIAL_THANKS.add("Mitch");
        SPECIAL_THANKS.add("Muramasa");
        SPECIAL_THANKS.add("Nida");
        SPECIAL_THANKS.add("NotMyWing");
        SPECIAL_THANKS.add("Oggz");
        SPECIAL_THANKS.add("Oliwier");
        SPECIAL_THANKS.add("Ostry");
        SPECIAL_THANKS.add("Pilzinsel");
        SPECIAL_THANKS.add("Quarri");
        SPECIAL_THANKS.add("R00tB33r");
        SPECIAL_THANKS.add("RavenholmZombie");
        SPECIAL_THANKS.add("Rechenender");
        SPECIAL_THANKS.add("Rongmario");
        SPECIAL_THANKS.add("S4muel");
        SPECIAL_THANKS.add("Scribit");
        SPECIAL_THANKS.add("SteelGiant");
        SPECIAL_THANKS.add("Tec");
        SPECIAL_THANKS.add("TheDarkDnKTv");
        SPECIAL_THANKS.add("V3ntus");
        SPECIAL_THANKS.add("Vlamonster");
        SPECIAL_THANKS.add("Warlord Wossman");
        SPECIAL_THANKS.add("Xavier");
        SPECIAL_THANKS.add("Zoko");
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
