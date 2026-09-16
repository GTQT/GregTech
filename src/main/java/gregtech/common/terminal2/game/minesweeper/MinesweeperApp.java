package gregtech.common.terminal2.game.minesweeper;

import gregtech.api.GTValues;
import gregtech.api.terminal2.ITerminalApp;
import gregtech.api.terminal2.Terminal2;
import gregtech.api.terminal2.Terminal2Theme;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;

/**
 * A port of the minesweeper app removed along with the mui1 terminal.
 * Entirely client side, so it needs no sync handlers.
 */
public class MinesweeperApp implements ITerminalApp {

    private static final int BOARD_WIDTH = 20;
    private static final int BOARD_HEIGHT = 12;
    private static final int MINE_COUNT = 40;
    /** Ticks the finished board stays up before the next round starts. */
    private static final int RESET_DELAY = 100;

    @Override
    public IWidget buildWidgets(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings,
                                ModularPanel panel) {
        MineMapWidget mineField = new MineMapWidget(BOARD_WIDTH, BOARD_HEIGHT, MINE_COUNT);

        var status = new StatusWidget(mineField);
        var board = new ParentWidget<>()
                .sizeRel(1.0F)
                .background(Terminal2Theme.COLOR_BACKGROUND_1)
                .child(mineField
                        .size(BOARD_WIDTH * 16, BOARD_HEIGHT * 16)
                        .posRel(0.5F, 0.5F))
                .child(status
                        .size(Terminal2.SCREEN_WIDTH - 8, 14)
                        .pos(4, 4));

        return board;
    }

    @Override
    public IDrawable getIcon() {
        return UITexture.fullImage(GTValues.MODID, "textures/gui/terminal/minesweeper/icon.png");
    }

    /**
     * The header line: flags placed on the left, and either the running clock or the
     * end-of-round message on the right. Also owns the round timer, since it is the
     * one widget guaranteed to tick for the lifetime of the page.
     */
    private static class StatusWidget extends Widget<StatusWidget> {

        private final MineMapWidget mineField;

        private int timer;
        private int resetCountdown = RESET_DELAY;

        StatusWidget(MineMapWidget mineField) {
            this.mineField = mineField;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void onUpdate() {
            super.onUpdate();

            if (mineField.isFinished()) {
                if (mineField.isWon()) mineField.markWon();
                resetCountdown--;
                if (resetCountdown <= 0) {
                    mineField.reset();
                    resetCountdown = RESET_DELAY;
                    timer = 0;
                }
            } else {
                timer++;
            }
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
            var theme = widgetTheme.getTheme();

            IKey.str(mineField.flagsPlaced + "/" + mineField.mineCount)
                    .draw(context, 0, 0, getArea().width / 2, 12, theme);

            int half = getArea().width / 2;
            IKey message;
            if (!mineField.isFinished()) {
                message = IKey.lang("terminal.minesweeper.time", timer / 20);
            } else if (mineField.isLost()) {
                message = IKey.lang("terminal.minesweeper.lose", resetCountdown / 20);
            } else {
                message = IKey.lang("terminal.minesweeper.win.1", timer / 20);
                IKey.lang("terminal.minesweeper.win.2", resetCountdown / 20)
                        .alignment(Alignment.CenterRight)
                        .draw(context, half, 12, half, 12, theme);
            }
            message.alignment(Alignment.CenterRight).draw(context, half, 0, half, 12, theme);
        }
    }
}
