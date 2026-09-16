package gregtech.common.terminal2.game.minesweeper;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * The playfield of the minesweeper app. Owns the board state and its input handling;
 * the surrounding app drives the retry countdown and the status line.
 */
public class MineMapWidget extends Widget<MineMapWidget> implements Interactable {

    private static final int TILE = 16;

    private static final IDrawable COVERED = tile("covered");
    private static final IDrawable FLAG = tile("flag");
    private static final IDrawable BOMB = tile("bomb");

    private static final IDrawable[] NUMBERS = { tile("blank"), tile("1"), tile("2"), tile("3"), tile("4"),
            tile("5"), tile("6"), tile("7"), tile("8") };

    private static IDrawable tile(String name) {
        return UITexture.fullImage(new ResourceLocation("gregtech", "textures/gui/terminal/minesweeper/" + name));
    }

    public final int width;
    public final int height;
    public final int mineCount;

    private boolean[][] mines;
    private boolean[][] flags;
    private boolean[][] revealed;
    private int[][] adjacentMines;

    private boolean prepared;
    private boolean lost;
    private boolean won;

    public int flagsPlaced;

    public MineMapWidget(int width, int height, int mineCount) {
        this.width = width;
        this.height = height;
        this.mineCount = mineCount;
        reset();
    }

    public void reset() {
        this.mines = new boolean[width][height];
        this.adjacentMines = new int[width][height];
        this.revealed = new boolean[width][height];
        this.flags = new boolean[width][height];
        this.prepared = false;
        this.lost = false;
        this.won = false;
        this.flagsPlaced = 0;
    }

    public boolean isLost() {
        return lost;
    }

    public boolean isWon() {
        if (!prepared || lost) return false;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // Every mine has to be flagged, and every safe tile uncovered.
                if (mines[x][y] != flags[x][y] || revealed[x][y] == mines[x][y]) return false;
            }
        }
        return true;
    }

    public void markWon() {
        this.won = true;
    }

    public boolean isFinished() {
        return lost || won;
    }

    // #region board generation

    /** Places mines, keeping the 3x3 around the first click clear, then counts neighbours. */
    private void generate(int startX, int startY) {
        Random random = new Random();
        int placed = 0;
        while (placed < mineCount) {
            int x = random.nextInt(width);
            int y = random.nextInt(height);
            if (mines[x][y]) continue;
            if (Math.abs(x - startX) < 3 && Math.abs(y - startY) < 3) continue;
            mines[x][y] = true;
            placed++;
        }

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (!mines[x][y]) continue;
                forEachNeighbour(x, y, (nx, ny) -> adjacentMines[nx][ny]++);
            }
        }
        prepared = true;
    }

    private void forEachNeighbour(int x, int y, NeighbourVisitor visitor) {
        for (int dx = x == 0 ? 0 : -1; dx < (x == width - 1 ? 1 : 2); dx++) {
            for (int dy = y == 0 ? 0 : -1; dy < (y == height - 1 ? 1 : 2); dy++) {
                visitor.visit(x + dx, y + dy);
            }
        }
    }

    private interface NeighbourVisitor {

        void visit(int x, int y);
    }

    /** Flood fill out of a blank tile, stopping at numbered tiles. */
    private void uncover(int x, int y) {
        revealed[x][y] = true;
        if (adjacentMines[x][y] != 0) return;
        forEachNeighbour(x, y, (nx, ny) -> {
            if (!revealed[nx][ny]) uncover(nx, ny);
        });
    }

    // #endregion

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        if (isFinished()) return Result.IGNORE;

        ModularGuiContext context = getContext();
        int gridX = (context.getMouseX() - getArea().x) / TILE;
        int gridY = (context.getMouseY() - getArea().y) / TILE;
        if (gridX < 0 || gridY < 0 || gridX >= width || gridY >= height) return Result.IGNORE;

        if (mouseButton == 0 && !flags[gridX][gridY]) {
            if (!prepared) generate(gridX, gridY);
            if (adjacentMines[gridX][gridY] == 0) {
                uncover(gridX, gridY);
            } else {
                revealed[gridX][gridY] = true;
            }
            if (mines[gridX][gridY]) {
                lost = true;
            }
        } else if (mouseButton == 1 && !revealed[gridX][gridY]) {
            flags[gridX][gridY] = !flags[gridX][gridY];
            flagsPlaced += flags[gridX][gridY] ? 1 : -1;
        }
        return Result.SUCCESS;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        WidgetTheme theme = widgetTheme.getTheme();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                IDrawable drawable;
                if (lost && mines[x][y]) {
                    drawable = BOMB;
                } else if (!revealed[x][y]) {
                    drawable = flags[x][y] ? FLAG : COVERED;
                } else if (mines[x][y]) {
                    drawable = BOMB;
                } else {
                    drawable = NUMBERS[adjacentMines[x][y]];
                }
                drawable.draw(context, x * TILE, y * TILE, TILE, TILE, theme);
            }
        }
    }
}
