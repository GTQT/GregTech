package gregtech.common.mui.multiblock.godforge.sync;

import java.util.function.BiConsumer;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import gregtech.common.metatileentities.multi.electric.godforge.MetaTileEntityForgeOfGods;
import gregtech.common.metatileentities.multi.electric.godforge.color.ForgeOfGodsStarColor;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEExoticModule;
import gregtech.common.metatileentities.multi.electric.godforge.upgrade.ForgeOfGodsUpgrade;
import gregtech.common.metatileentities.multi.electric.godforge.util.ForgeOfGodsData;

public final class SyncActions<T, U> {

    public static SyncActions<ForgeOfGodsUpgrade, ForgeOfGodsData> RESPEC_UPGRADE = new SyncActions<>(
        "fog.sync_action.respec_upgrade",
        (buf, upgrade) -> buf.writeByte(upgrade.ordinal()),
        (buf, data) -> {
            ForgeOfGodsUpgrade upgrade = ForgeOfGodsUpgrade.VALUES[buf.readByte()];
            data.respecUpgrade(upgrade);
        },
        Side.SERVER);

    public static SyncActions<ForgeOfGodsUpgrade, ForgeOfGodsData> COMPLETE_UPGRADE = new SyncActions<>(
        "fog.sync_action.complete_upgrade",
        (buf, upgrade) -> buf.writeByte(upgrade.ordinal()),
        (buf, data) -> {
            ForgeOfGodsUpgrade upgrade = ForgeOfGodsUpgrade.VALUES[buf.readByte()];
            data.unlockUpgrade(upgrade);
        },
        Side.SERVER);

    public static SyncActions<ForgeOfGodsUpgrade, ForgeOfGodsData> PAY_UPGRADE_COST = new SyncActions<>(
        "fog.sync_action.pay_upgrade_cost",
        (buf, upgrade) -> buf.writeByte(upgrade.ordinal()),
        (buf, data) -> {
            ForgeOfGodsUpgrade upgrade = ForgeOfGodsUpgrade.VALUES[buf.readByte()];
            data.getUpgrades().payCost(upgrade, data.getStoredUpgradeWindowItems());
        },
        Side.SERVER);

    public static SyncActions<ForgeOfGodsStarColor, MetaTileEntityForgeOfGods> UPDATE_RENDERER = new SyncActions<>(
        "fog.sync_action.update_renderer",
        (buf, starColor) -> {},
        (buf, multiblock) -> multiblock.updateRenderer(),
        Side.SERVER);

    public static SyncActions<Boolean, MetaTileEntityForgeOfGods> DISABLE_RENDERER = new SyncActions<>(
        "fog.sync_action.disable_renderer",
        PacketBuffer::writeBoolean,
        (buf, multiblock) -> {
            ForgeOfGodsData data = multiblock.getData();
            boolean newValue = buf.readBoolean();
            data.setRendererDisabled(newValue);
            if (newValue && data.isRenderActive()) {
                multiblock.destroyRenderer();
            } else if (!newValue && data.getInternalBattery() > 0) {
                if (data.isRenderActive()) {
                    multiblock.updateRenderer();
                } else {
                    multiblock.createRenderer();
                }
            }
        },
        Side.SERVER);

    public static SyncActions<Panels, SyncHypervisor> REFRESH_DYNAMIC = new SyncActions<>(
        "fog.sync_action.refresh_dynamic",
        (buf, panel) -> buf.writeByte(panel.ordinal()),
        (buf, hypervisor) -> {
            Panels panel = Panels.VALUES[buf.readByte()];
            hypervisor.refreshDynamicWidget(panel);
        },
        Side.SERVER);

    public static SyncActions<Void, MTEExoticModule> REFRESH_EXOTIC_RECIPE = new SyncActions<>(
        "fog.sync_action.refresh_exotic_recipe",
        (buf, $) -> {},
        (buf, module) -> module.refreshRecipe(),
        Side.SERVER);

    public static SyncActions<Void, ForgeOfGodsData> MANUAL_INSERTION = new SyncActions<>(
        "fog.sync_action.manual_insertion",
        (buf, $) -> {},
        (buf, data) -> data.manualInsertion(),
        Side.SERVER);

    private final String syncId;
    private final BiConsumer<PacketBuffer, T> writer;
    private final BiConsumer<PacketBuffer, U> action;
    private final Side executeSide;

    private SyncActions(String syncId, BiConsumer<PacketBuffer, T> writer, BiConsumer<PacketBuffer, U> action,
        Side executeSide) {
        this.syncId = syncId;
        this.writer = writer;
        this.action = action;
        this.executeSide = executeSide;
    }

    @SuppressWarnings("unchecked")
    public void registerFor(Panels forPanel, SyncHypervisor hypervisor) {
        registerFor(forPanel, hypervisor, (U) hypervisor.getData());
    }

    public void registerFor(Panels forPanel, SyncHypervisor hypervisor, U data) {
        registerFor(hypervisor.getMainModule(), forPanel, hypervisor, data);
    }

    public void registerFor(Modules<?> forModule, Panels forPanel, SyncHypervisor hypervisor, U data) {
        PanelSyncManager syncManager = hypervisor.getSyncManager(forModule, forPanel);
        syncManager.registerSyncedAction(syncId, buf -> {
            switch (executeSide) {
                case SERVER -> {
                    if (!syncManager.isClient()) {
                        action.accept(buf, data);
                    }
                }
                case CLIENT -> {
                    if (syncManager.isClient()) {
                        action.accept(buf, data);
                    }
                }
                case BOTH -> action.accept(buf, data);
            }
        });
    }

    public void callFrom(Panels fromPanel, SyncHypervisor hypervisor, T data) {
        callFrom(hypervisor.getMainModule(), fromPanel, hypervisor, data);
    }

    public void callFrom(Modules<?> fromModule, Panels fromPanel, SyncHypervisor hypervisor, T data) {
        PanelSyncManager syncManager = hypervisor.getSyncManager(fromModule, fromPanel);
        syncManager.callSyncedAction(syncId, buf -> writer.accept(buf, data));
    }

    private enum Side {
        SERVER,
        CLIENT,
        BOTH
    }
}
