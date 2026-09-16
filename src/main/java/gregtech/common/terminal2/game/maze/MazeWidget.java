package gregtech.common.terminal2.game.maze;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.drawable.GuiDraw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The maze grid itself: the wall arrays, the Wilson random walk generator that fills them and
 * the line drawing. Purely geometric, it knows nothing about the player or the minotaur, and it
 * is drawn by hand at an explicit origin rather than by the widget tree.
 */
public class MazeWidget {

    /** Rendered size of a single cell. */
    public static final int CELL_SIZE = 10;

    private static final int WALL_COLOR = 0xFFFFFFFF;
    /** Above this many steps a random walk is considered stuck and restarted. */
    private static final int STUCK_LIMIT = 20000;

    private final Random random = new Random();

    /** Whether the wall between a cell and the one to its left / above it exists. */
    private final boolean[][] leftWalls;
    private final boolean[][] topWalls;
    private final boolean[][] includedSpots;

    private int squaresChecked;

    public MazeWidget(int size) {
        this.leftWalls = new boolean[size][size];
        this.topWalls = new boolean[size][size];
        this.includedSpots = new boolean[size][size];
    }

    public int getMazeSize() {
        return leftWalls.length;
    }

    /** Starts a fresh maze and keeps at it until the generator produces one. */
    public void initMaze() {
        while (!generate()) {
            // The generator bailed out because it got stuck, roll a new maze.
        }
    }

    private boolean generate() {
        int size = getMazeSize();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                leftWalls[i][j] = true;
                topWalls[i][j] = true;
                includedSpots[i][j] = false;
            }
        }

        // Seed a single random cell, every other cell has to be reached by a random walk.
        includedSpots[random.nextInt(size)][random.nextInt(size)] = true;

        List<Integer> positions = new ArrayList<>(size * size);
        for (int i = 0; i < size * size; i++) {
            positions.add(i);
        }
        Collections.shuffle(positions);

        for (int position : positions) {
            if (includedSpots[position / size][position % size]) continue;
            // A walk that gets stuck invalidates the whole attempt.
            if (!createPath(position / size, position % size, new boolean[size][size], size)) return false;
        }
        return true;
    }

    /** Wilson's random walk from (x, y) towards a cell that is already part of the maze. */
    private boolean createPath(int x, int y, boolean[][] walkedPaths, int size) {
        if (++squaresChecked > STUCK_LIMIT) return false;
        if (walkedPaths[x][y]) return false;
        if (includedSpots[x][y]) return true;
        includedSpots[x][y] = true;
        walkedPaths[x][y] = true;

        List<Integer> directions = new ArrayList<>(4);
        if (x != 0 && !walkedPaths[x - 1][y]) directions.add(0);
        if (x != size - 1 && !walkedPaths[x + 1][y]) directions.add(1);
        if (y != 0 && !walkedPaths[x][y - 1]) directions.add(2);
        if (y != size - 1 && !walkedPaths[x][y + 1]) directions.add(3);
        Collections.shuffle(directions);

        while (!directions.isEmpty()) {
            int direction = directions.get(directions.size() - 1);
            int newX = x;
            int newY = y;
            switch (direction) {
                case 0 -> newX--;
                case 1 -> newX++;
                case 2 -> newY--;
                default -> newY++;
            }

            if (createPath(newX, newY, walkedPaths, size)) {
                // Carve out the wall we just crossed.
                switch (direction) {
                    case 0 -> leftWalls[x][y] = false;
                    case 1 -> leftWalls[x + 1][y] = false;
                    case 2 -> topWalls[x][y] = false;
                    default -> topWalls[x][y + 1] = false;
                }
                return true;
            }
            directions.remove(directions.size() - 1);
        }

        // Dead end, undo and let the caller try another direction.
        includedSpots[x][y] = false;
        walkedPaths[x][y] = false;
        return false;
    }

    public void resetStuckCounter() {
        this.squaresChecked = 0;
    }

    /** Whether there is a wall on top of, or to the left of, the given cell. */
    public boolean isThereWallAt(int x, int y, boolean onTops) {
        int size = getMazeSize();
        if (x < 0 || y < 0 || x >= size || y >= size) return true;
        if (onTops) {
            return y == 0 || topWalls[x][y];
        }
        return x == 0 || leftWalls[x][y];
    }

    /**
     * Draws the walls with the top left corner of the grid at (originX, originY). The grid is
     * drawn by hand rather than by the widget tree, so it takes its origin as an argument.
     */
    @SideOnly(Side.CLIENT)
    public void draw(int originX, int originY) {
        int size = getMazeSize();
        int length = size * CELL_SIZE;

        // Outer border. The extra pixels make the 4px thick lines line up with the cell walls.
        GuiDraw.drawRect(originX, originY, length, 4, WALL_COLOR);
        GuiDraw.drawRect(originX, originY, 4, length, WALL_COLOR);
        GuiDraw.drawRect(originX, originY + length - 4, length, 4, WALL_COLOR);
        GuiDraw.drawRect(originX + length - 4, originY, 4, length, WALL_COLOR);

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                // Wall above cell (i, j), drawn from its left edge to its right edge.
                if (j != 0 && isThereWallAt(i, j, true)) {
                    GuiDraw.drawRect(originX + i * CELL_SIZE, originY + j * CELL_SIZE - 2, CELL_SIZE, 4, WALL_COLOR);
                }
                // Wall left of cell (i, j), drawn from its top edge to its bottom edge.
                if (i != 0 && isThereWallAt(i, j, false)) {
                    GuiDraw.drawRect(originX + i * CELL_SIZE - 2, originY + j * CELL_SIZE, 4, CELL_SIZE, WALL_COLOR);
                }
            }
        }
    }
}
