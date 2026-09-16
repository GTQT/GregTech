package gregtech.common.terminal2.game.maze;

import gregtech.api.GTValues;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import org.lwjgl.input.Keyboard;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The whole Theseus's Escape playfield: the maze, the player, the minotaur and the four state
 * machine (title, playing, paused, dead). Everything is drawn and ticked on the client only,
 * since the app is entirely client side and {@code buildWidgets} runs on both sides.
 * <p>
 * The widget is positioned so that its top left corner is the origin of the square board.
 */
public class MazeGameWidget extends Widget<MazeGameWidget> implements Interactable {

    public static final int STATE_TITLE = 0;
    public static final int STATE_PLAYING = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_DEAD = 3;

    public static final int CELL_SIZE = MazeWidget.CELL_SIZE;

    private static final int INITIAL_MAZE_SIZE = 9;
    private static final float INITIAL_SPEED = 25;
    /** Ticks between two accepted player inputs. */
    private static final int INPUT_COOLDOWN = 2;
    /** Directions, in the order the original game used them. */
    private static final int LEFT = 0, RIGHT = 1, UP = 2, DOWN = 3;

    private static final int PLAYER_COLOR = 0xAAAAAAFF;
    private static final int MINOTAUR_COLOR = 0xFFFFAAAA;
    private static final int PAUSE_OVERLAY_COLOR = 0xFF000000;

    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 20;

    private MazeWidget maze = new MazeWidget(INITIAL_MAZE_SIZE);

    /** How many cells the board is wide, grows every four solved mazes. */
    private int mazeSize = INITIAL_MAZE_SIZE;
    /** Ticks between two minotaur steps. */
    private float speed = INITIAL_SPEED;

    private int state = STATE_TITLE;
    private int timer;
    private int mazesSolved;
    private int lastPlayerInput = -INPUT_COOLDOWN;

    private int playerX;
    private int playerY;
    private int minotaurX = -100;
    private int minotaurY = -100;

    /**
     * The directions the player walked, oldest first. The minotaur follows this breadcrumb trail,
     * walking backwards towards the player. Walking back the way you came cancels out the trail.
     */
    private final Deque<Integer> movementStore = new ArrayDeque<>();
    private boolean lastPausePress;

    public MazeGameWidget() {
        maze.initMaze();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void onUpdate() {
        super.onUpdate();
        if (!GTValues.isClientSide()) return;

        if (state == STATE_PLAYING) {
            updatePlaying();
        } else if (state == STATE_PAUSED) {
            // P has to be released before it counts as another press.
            if (!Keyboard.isKeyDown(Keyboard.KEY_P)) {
                lastPausePress = false;
            } else if (!lastPausePress) {
                lastPausePress = true;
                state = STATE_PLAYING;
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private void updatePlaying() {
        if (Keyboard.isKeyDown(Keyboard.KEY_P)) {
            lastPausePress = true;
            state = STATE_PAUSED;
            return;
        }

        if (Keyboard.isKeyDown(Keyboard.KEY_LEFT) ^ Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) {
            attemptMovePlayer(Keyboard.isKeyDown(Keyboard.KEY_LEFT) ? LEFT : RIGHT);
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_UP) ^ Keyboard.isKeyDown(Keyboard.KEY_DOWN)) {
            attemptMovePlayer(Keyboard.isKeyDown(Keyboard.KEY_UP) ? UP : DOWN);
        }

        timer++;
        // The minotaur waits a while before entering the maze for the first time.
        if (minotaurX < 0 && timer % (int) (speed * mazeSize - 1) < 1) {
            setMinotaurPosition(0, 0);
        } else if (timer % speed < 1) {
            moveMinotaur();
        }

        if (minotaurX == playerX && minotaurY == playerY) {
            state = STATE_DEAD;
        }
    }

    @SideOnly(Side.CLIENT)
    private void attemptMovePlayer(int direction) {
        if (timer < lastPlayerInput + INPUT_COOLDOWN) return;
        // Don't eat the cooldown when the move is blocked by a wall.
        if (isThereWall(direction)) return;
        lastPlayerInput = timer;

        // Down from the bottom right corner ends the maze.
        if (direction == DOWN && playerX == mazeSize - 1 && playerY == mazeSize - 1) {
            solveMaze();
            return;
        }

        switch (direction) {
            case LEFT -> playerX--;
            case RIGHT -> playerX++;
            case UP -> playerY--;
            default -> playerY++;
        }

        // Retracing our steps cancels out the matching entry at the end of the trail.
        int opposite = oppositeOf(direction);
        if (!movementStore.isEmpty() && movementStore.peekLast() == opposite) {
            movementStore.pollLast();
        } else {
            movementStore.addLast(direction);
        }
    }

    @SideOnly(Side.CLIENT)
    private boolean isThereWall(int direction) {
        return switch (direction) {
            case LEFT -> maze.isThereWallAt(playerX, playerY, false);
            case RIGHT -> maze.isThereWallAt(playerX + 1, playerY, false);
            case UP -> maze.isThereWallAt(playerX, playerY, true);
            default -> maze.isThereWallAt(playerX, playerY + 1, true);
        };
    }

    private static int oppositeOf(int direction) {
        return switch (direction) {
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
            case UP -> DOWN;
            default -> UP;
        };
    }

    /** Steps the minotaur one cell along the oldest entry of the breadcrumb trail. */
    @SideOnly(Side.CLIENT)
    private void moveMinotaur() {
        if (minotaurX < 0 || movementStore.isEmpty()) return;

        switch (movementStore.pollFirst()) {
            case LEFT -> minotaurX--;
            case RIGHT -> minotaurX++;
            case UP -> minotaurY--;
            default -> minotaurY++;
        }
    }

    @SideOnly(Side.CLIENT)
    private void solveMaze() {
        mazesSolved++;
        speed *= 0.95F;
        if (mazesSolved % 4 == 0) {
            mazeSize += 2;
            speed *= 1.07F;
        }
        resetMaze();
    }

    /** Puts the player back in the corner and generates a maze of the current size. */
    @SideOnly(Side.CLIENT)
    private void resetMaze() {
        // The generator bails out on a stuck walk, so the board is only ever rebuilt on demand.
        if (maze.getMazeSize() != mazeSize) {
            maze = new MazeWidget(mazeSize);
        }
        maze.initMaze();

        playerX = 0;
        playerY = 0;
        setMinotaurPosition(-100, -100);
        movementStore.clear();
        timer = 0;
        lastPlayerInput = -INPUT_COOLDOWN;
        lastPausePress = true;
    }

    @Override
    public boolean canClickThrough() {
        return false;
    }

    private void setMinotaurPosition(int x, int y) {
        this.minotaurX = x;
        this.minotaurY = y;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (!GTValues.isClientSide()) return;

        WidgetTheme theme = widgetTheme.getTheme();
        // The board is a square centred inside this widget, everything below is relative to it.
        int size = mazeSize * CELL_SIZE;
        int originX = (getArea().width - size) / 2;
        int originY = (getArea().height - size) / 2;

        maze.draw(originX, originY);

        if (state != STATE_TITLE) {
            drawDot(originX, originY, playerX, playerY, PLAYER_COLOR);
            drawDot(originX, originY, minotaurX, minotaurY, MINOTAUR_COLOR);
        }

        switch (state) {
            case STATE_TITLE -> {
                drawCenteredKey(context, theme, IKey.lang("terminal.maze.title"), originY + size / 2 - 50, originX,
                        size);
                drawButton(context, theme, originX, originY + size / 2 - 10, IKey.lang("terminal.maze.play"));
            }
            case STATE_PAUSED -> {
                GuiDraw.drawRect(originX, originY, size, size, PAUSE_OVERLAY_COLOR);
                drawCenteredKey(context, theme, IKey.lang("terminal.maze.pause"), originY + size / 2 - 50, originX,
                        size);
                drawButton(context, theme, originX, originY + size / 2 - 10, IKey.lang("terminal.maze.continue"));
            }
            case STATE_DEAD -> {
                drawCenteredKey(context, theme, IKey.lang("terminal.maze.death.1"), originY + size / 2 - 40, originX,
                        size);
                drawCenteredKey(context, theme, IKey.lang("terminal.maze.death.2", mazesSolved),
                        originY + size / 2 - 28, originX, size);
                drawCenteredKey(context, theme, IKey.lang("terminal.maze.death.3"), originY + size / 2 - 16, originX,
                        size);
                drawButton(context, theme, originX, originY + size / 2 + 10, IKey.lang("terminal.maze.retry"));
            }
            case STATE_PLAYING -> IKey.lang("terminal.maze.score", mazesSolved)
                    .draw(context, originX, originY, size - 4, 10, theme);
        }
    }

    @SideOnly(Side.CLIENT)
    private void drawDot(int originX, int originY, int cellX, int cellY, int color) {
        if (cellX < 0 || cellY < 0) return;
        GuiDraw.drawRect(originX + cellX * CELL_SIZE, originY + cellY * CELL_SIZE, CELL_SIZE, CELL_SIZE, color);
    }

    @SideOnly(Side.CLIENT)
    private void drawCenteredKey(ModularGuiContext context, WidgetTheme theme, IKey key, int y, int originX,
                                 int size) {
        key.draw(context, originX + 10, y, size - 20, 10, theme);
    }

    @SideOnly(Side.CLIENT)
    private void drawButton(ModularGuiContext context, WidgetTheme theme, int originX, int y, IKey label) {
        int x = originX + (mazeSize * CELL_SIZE - BUTTON_WIDTH) / 2;
        GuiDraw.drawRect(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, 0xFF000000);
        label.draw(context, x, y + (BUTTON_HEIGHT - 8) / 2, BUTTON_WIDTH, 8, theme);
    }

    @SideOnly(Side.CLIENT)
    private boolean isOverButton(int mouseX, int mouseY, int originX, int y) {
        int x = originX + (mazeSize * CELL_SIZE - BUTTON_WIDTH) / 2;
        return mouseX >= x && mouseX < x + BUTTON_WIDTH && mouseY >= y && mouseY < y + BUTTON_HEIGHT;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Result onMousePressed(int mouseButton) {
        if (mouseButton != 0) return Result.IGNORE;

        int size = mazeSize * CELL_SIZE;
        int originX = (getArea().width - size) / 2;
        int originY = (getArea().height - size) / 2;
        int mouseX = getContext().getMouseX() - getArea().x - originX;
        int mouseY = getContext().getMouseY() - getArea().y - originY;

        switch (state) {
            case STATE_TITLE -> {
                if (!isOverButton(mouseX, mouseY, 0, size / 2 - 10)) return Result.IGNORE;
                state = STATE_PLAYING;
                resetMaze();
            }
            case STATE_PAUSED -> {
                if (!isOverButton(mouseX, mouseY, 0, size / 2 - 10)) return Result.IGNORE;
                state = STATE_PLAYING;
            }
            case STATE_DEAD -> {
                if (!isOverButton(mouseX, mouseY, 0, size / 2 + 10)) return Result.IGNORE;
                state = STATE_PLAYING;
                mazesSolved = 0;
                speed = INITIAL_SPEED;
                mazeSize = INITIAL_MAZE_SIZE;
                resetMaze();
            }
            default -> {
                return Result.IGNORE;
            }
        }
        return Result.SUCCESS;
    }
}
