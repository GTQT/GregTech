package gregtech.common.items.behaviors;

import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.items.metaitem.stats.IItemCapabilityProvider;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.api.terminal2.ITerminalApp;
import gregtech.api.terminal2.Terminal2;
import gregtech.api.terminal2.Terminal2Theme;
import gregtech.common.mui.widget.IDPagedWidget;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Grid;

import java.util.function.Consumer;

public class Terminal2Behavior implements IItemBehaviour, ItemUIFactory, IItemCapabilityProvider {

    /** Hands the terminal item the inventory that backs its storage app. */
    @Override
    public ICapabilityProvider createProvider(ItemStack itemStack) {
        return new TerminalStorageProvider(itemStack);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (!world.isRemote) {
            MetaItemGuiFactory.open(player, hand);
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, heldItem);
    }

    @Override
    public GTGuiTheme getUITheme() {
        return GTGuiTheme.TERMINAL;
    }

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        ModularPanel panel = GTGuis.createPanel(guiData.getUsedItemStack(), 364, 248);

        // to avoid tying the custom disposal logic to IDPagedWidget itself
        @SuppressWarnings("rawtypes")
        IDPagedWidget<?> appPages = new IDPagedWidget() {

            @Override
            public void dispose() {
                super.dispose();
                Terminal2.setPageSwitcher(null);
                Terminal2Theme.gcBoundRects();
                for (var app : Terminal2.appMap.values()) {
                    app.dispose();
                }
            }
        };
        for (var app : Terminal2.appMap.entrySet()) {
            appPages.addPage(app.getKey(), app.getValue().buildWidgets(guiData, guiSyncManager, settings, panel));
        }
        appPages.size(Terminal2.SCREEN_WIDTH, Terminal2.SCREEN_HEIGHT).pos(4, 4);

        // Opening an app is the same two steps wherever it is triggered from: switch the page, then tell the app.
        Consumer<ResourceLocation> openApp = appID -> {
            if (appPages.getCurrentPageID() == appID) return;
            appPages.setPage(appID);
            Terminal2.appMap.get(appID).onOpen();
        };
        Terminal2.setPageSwitcher(openApp);
        guiSyncManager.onCommonTick(Terminal2::tickPageSwitch);
        guiSyncManager.addCloseListener(player -> Terminal2.setPageSwitcher(null));

        Grid appGrid = new Grid()
                .pos(44, 22)
                .size(Terminal2.SCREEN_WIDTH - 44 * 2, Terminal2.SCREEN_HEIGHT - 22 * 2)
                .minElementMargin(6)
                .nextRow();

        for (var appEntry : Terminal2.appMap.entrySet()) {
            ResourceLocation appID = appEntry.getKey();
            ITerminalApp app = appEntry.getValue();
            if (Terminal2.HOME_ID.equals(appID)) continue;

            appGrid.child(new ButtonWidget<>()
                    .overlay(app.getIcon())
                    .background(Terminal2Theme.COLOR_BACKGROUND_2.getCircle())
                    .hoverBackground(Terminal2Theme.COLOR_BACKGROUND_2.getCircle(),
                            Terminal2Theme.COLOR_BRIGHT_2.getRing())
                    .size(24, 24)
                    .addTooltipLine(IKey.lang("terminal.app." + appID.getNamespace() + "." + appID.getPath() + ".name"))
                    .onMousePressed(i -> {
                        openApp.accept(appID);
                        return true;
                    }));

            if (appGrid.getChildren().size() % 7 == 0) {
                appGrid.nextRow();
            }
        }
        appPages.addPage(Terminal2.HOME_ID, appGrid).setDefaultPage(Terminal2.HOME_ID);

        return panel.background(GTGuiTextures.TERMINAL_FRAME)
                .child(new DynamicDrawable(Terminal2Theme::getBackgroundDrawable).asWidget()
                        .size(Terminal2.SCREEN_WIDTH, Terminal2.SCREEN_HEIGHT)
                        .pos(4, 4))
                .child(appPages)
                .child(new ButtonWidget<>()
                        .overlay(GTGuiTextures.HOME_BUTTON)
                        .hoverOverlay(GTGuiTextures.HOME_BUTTON_HOVER)
                        .background(IDrawable.NONE)
                        .disableHoverBackground()
                        .addTooltipLine(IKey.lang("terminal.app.gregtech.home.name"))
                        .size(16, 16)
                        .left(346)
                        .topRelAnchor(0.5F, 0.5F)
                        .onMousePressed(i -> {
                            appPages.setPage(Terminal2.HOME_ID);
                            return true;
                        }));
    }
}
