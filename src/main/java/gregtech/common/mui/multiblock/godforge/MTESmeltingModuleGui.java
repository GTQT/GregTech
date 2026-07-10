package gregtech.common.mui.multiblock.godforge;

import static gregtech.api.metatileentity.MetaTileEntity.TOOLTIP_DELAY;
import static net.minecraft.util.text.translation.I18n.translateToLocal;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import gregtech.api.mui.GTGuiTextures;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTESmeltingModule;
import gregtech.common.mui.multiblock.godforge.sync.Modules;
import gregtech.common.mui.multiblock.godforge.sync.SyncValues;

public class MTESmeltingModuleGui extends MTEBaseModuleGui<MTESmeltingModule> {

    public MTESmeltingModuleGui(MTESmeltingModule multiblock) {
        super(multiblock);
    }

    @Override
    public Modules<MTESmeltingModule> getModuleType() {
        return Modules.SMELTING;
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);

        SyncValues.SMELTING_MODE.registerFor(getModuleType(), getMainPanel(), hypervisor);
        SyncValues.SMELTING_RECIPE_MAP_ROUTING.registerFor(getModuleType(), getMainPanel(), hypervisor);
    }

    @Override
    protected boolean usesExtraButton() {
        return true;
    }

    @Override
    protected IWidget createExtraButton() {
        BooleanSyncValue furnaceModeSyncer = SyncValues.SMELTING_MODE
            .lookupFrom(getModuleType(), getMainPanel(), hypervisor);
        BooleanSyncValue routingSyncer = SyncValues.SMELTING_RECIPE_MAP_ROUTING
            .lookupFrom(getModuleType(), getMainPanel(), hypervisor);
        return Flow.row().coverChildren().childPadding(2)
            .child(createFurnaceModeButton(furnaceModeSyncer, routingSyncer))
            .child(createRecipeMapRoutingButton(routingSyncer));
    }

    private IWidget createFurnaceModeButton(BooleanSyncValue furnaceModeSyncer, BooleanSyncValue routingSyncer) {
        return new ButtonWidget<>().size(16)
            .background(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32)
            .overlay(new DynamicDrawable(() -> {
                if (multiblock.isFurnaceModeOn()) {
                    return GTGuiTextures.TT_OVERLAY_BUTTON_FURNACE_MODE;
                }
                return GTGuiTextures.TT_OVERLAY_BUTTON_FURNACE_MODE_OFF;
            }))
            .onMousePressed(d -> {
                if (routingSyncer.getBoolValue()) return true;
                furnaceModeSyncer.setBoolValue(!furnaceModeSyncer.getValue());
                return true;
            })
            .tooltipDynamic(t -> {
                if (routingSyncer.getBoolValue()) {
                    t.addLine(translateToLocal("fog.button.recipemap_routing.mode_locked"));
                    return;
                }
                if (furnaceModeSyncer.getBoolValue()) {
                    t.addLine(translateToLocal("fog.button.furnacemode.tooltip.02"));
                } else {
                    t.addLine(translateToLocal("fog.button.furnacemode.tooltip.01"));
                }
            })
            .tooltipAutoUpdate(true)
            .tooltipShowUpTimer(TOOLTIP_DELAY)
            .clickSound(ForgeOfGodsGuiUtil.getButtonSound());
    }

    private IWidget createRecipeMapRoutingButton(BooleanSyncValue routingSyncer) {
        return new ButtonWidget<>().size(16)
            .background(GTGuiTextures.TT_BUTTON_CELESTIAL_32x32)
            .overlay(new DynamicDrawable(() -> routingSyncer.getBoolValue() ?
                GTGuiTextures.TT_OVERLAY_BUTTON_BATCH_MODE : GTGuiTextures.TT_OVERLAY_BUTTON_BATCH_MODE_OFF))
            .onMousePressed(d -> {
                routingSyncer.setBoolValue(!routingSyncer.getBoolValue());
                return true;
            })
            .tooltipDynamic(t -> t.addLine(translateToLocal(routingSyncer.getBoolValue() ?
                "fog.button.recipemap_routing.tooltip.02" : "fog.button.recipemap_routing.tooltip.01")))
            .tooltipAutoUpdate(true)
            .tooltipShowUpTimer(TOOLTIP_DELAY)
            .clickSound(ForgeOfGodsGuiUtil.getButtonSound());
    }
}
