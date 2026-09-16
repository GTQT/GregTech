package gregtech.common.terminal2.game.pong;

import gregtech.api.GTValues;
import gregtech.api.terminal2.ITerminalApp;
import gregtech.api.terminal2.Terminal2Theme;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;

/**
 * A port of the pong app removed along with the mui1 terminal.
 * Entirely client side, so it needs no sync handlers; the physics only runs in {@link GameLoop}.
 */
public class PongApp implements ITerminalApp {

    private PongWidget pong;

    @Override
    public IWidget buildWidgets(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings,
                                ModularPanel panel) {
        pong = new PongWidget();

        return new ParentWidget<>()
                .sizeRel(1.0F)
                .background(Terminal2Theme.COLOR_BACKGROUND_1)
                .child(pong
                        .size(333, 232)
                        .pos(3, 4))
                .child(new GameLoop(pong));
    }

    @Override
    public IDrawable getIcon() {
        return UITexture.fullImage(GTValues.MODID, "textures/gui/terminal/pong/icon.png");
    }

    /**
     * A non drawing ticker: {@link PongWidget} deliberately does not update itself so that the
     * game can be paused by not being fed.
     */
    private static class GameLoop extends Widget<GameLoop> {

        private final PongWidget pong;

        GameLoop(PongWidget pong) {
            this.pong = pong;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void onUpdate() {
            super.onUpdate();
            if (!isEnabled()) return;
            pong.updateGame();
            pong.updatePaddleAI();
        }
    }
}
