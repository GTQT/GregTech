package gregtech.common.terminal2;

import gregtech.api.GTValues;
import gregtech.api.terminal2.ITerminalApp;
import gregtech.api.terminal2.Terminal2;
import gregtech.api.terminal2.Terminal2Theme;

import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;

/**
 * Lists every terminal app as a card, mirroring the icon grid the home screen shows.
 * Opening a card switches the terminal to that app's page.
 *
 * @see Terminal2#openApp(ResourceLocation)
 */
public class AppStoreApp implements ITerminalApp {

    @Override
    public IWidget buildWidgets(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings,
                                ModularPanel panel) {
        var cards = new Grid()
                .minElementMargin(6)
                .nextRow();

        for (var entry : Terminal2.appMap.entrySet()) {
            ResourceLocation appID = entry.getKey();
            if (appID.equals(Terminal2.HOME_ID)) continue;

            cards.child(new AppCard(appID, entry.getValue()));

            if (cards.getChildren().size() % 3 == 0) {
                cards.nextRow();
            }
        }

        var content = Flow.column()
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .widthRel(0.95F)
                .child(IKey.lang("terminal.store.description").asWidget().widthRel(1.0F))
                .child(cards.top(6));

        var scroll = new com.cleanroommc.modularui.widget.ScrollWidget<>(new VerticalScrollData())
                .child(content)
                .sizeRel(1.0F);

        return new ParentWidget<>()
                .sizeRel(0.98F)
                .posRel(0.5F, 0.5F)
                .background(Terminal2Theme.COLOR_BACKGROUND_1)
                .child(scroll);
    }

    @Override
    public IDrawable getIcon() {
        return UITexture.fullImage(GTValues.MODID, "textures/gui/terminal/store/icon.png");
    }

    /** One clickable app tile: the app's own icon above its localized name. */
    private static class AppCard extends ButtonWidget<AppCard> {

        private static final int ICON_SIZE = 32;
        private static final int CARD_WIDTH = 98;
        private static final int CARD_HEIGHT = CARD_WIDTH - ICON_SIZE / 2;

        AppCard(ResourceLocation appID, ITerminalApp app) {
            background(Terminal2Theme.COLOR_BACKGROUND_2)
                    .hoverBackground(Terminal2Theme.COLOR_BACKGROUND_2, Terminal2Theme.COLOR_BRIGHT_2)
                    .size(CARD_WIDTH, CARD_HEIGHT)
                    .onMousePressed(mouseButton -> {
                        Terminal2.openApp(appID);
                        return true;
                    });

            child(app.getIcon().asWidget()
                    .size(ICON_SIZE)
                    .posRel(0.5F, 0.0F)
                    .top(8));
            child(IKey.lang("terminal.app." + appID.getNamespace() + "." + appID.getPath() + ".name")
                    .asWidget()
                    .widthRel(1.0F)
                    .bottom(6));
        }
    }
}
