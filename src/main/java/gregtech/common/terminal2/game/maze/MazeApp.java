package gregtech.common.terminal2.game.maze;

import gregtech.api.GTValues;
import gregtech.api.terminal2.ITerminalApp;
import gregtech.api.terminal2.Terminal2Theme;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.SingleChildWidget;

/**
 * A port of Theseus's Escape, the maze game removed along with the mui1 terminal.
 * Entirely client side, so it is submitted even on the server and needs no sync handlers.
 */
public class MazeApp implements ITerminalApp {

    /** The board is square and grows with the maze, this only has to hold the largest one. */
    private static final int BOARD_WIDTH = 333;

    @Override
    public IWidget buildWidgets(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings,
                                ModularPanel panel) {
        var game = new MazeGameWidget()
                .size(BOARD_WIDTH, BOARD_WIDTH)
                .posRel(Alignment.Center);

        // The game draws its own board, the app only has to hand it a centred, full size area.
        return new ParentWidget<>()
                .sizeRel(1.0F)
                .background(Terminal2Theme.COLOR_BACKGROUND_1)
                .child(new SingleChildWidget<>().sizeRel(1.0F).child(game));
    }

    @Override
    public IDrawable getIcon() {
        return UITexture.fullImage(GTValues.MODID, "textures/gui/terminal/maze/icon.png");
    }
}
