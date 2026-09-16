package gregtech.common.terminal2.game.maze;

import com.cleanroommc.modularui.drawable.GuiDraw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A perfect maze on a square grid, carved out with a randomised depth first search.
 * {@code topWalls[x][y]} is the wall between cell (x, y) and the cell above it,
 * {@code leftWalls[x][y]} the one to its left; the outer rim is not stored and is drawn by the caller.
 */
public class Maze {

    /** Drawn size of one cell, and the thickness of the lines between them. */
    public static final int CELL = 10;
    public static final int WALL = 2;

    public final int size;
    private final boolean[][] topWalls;
    private final boolean[][] leftWalls;

    public Maze(int size) {
        this.size = size;
        this.topWalls = new boolean[size][size];
        this.leftWalls = new boolean[size][size];

        boolean[][] visited = new boolean[size][size];
        carve(0, 0, visited);
    }

    private void carve(int x, int y, boolean[][] visited) {
        visited[x][y] = true;

        List<int[]> directions = new ArrayList<>(4);
        // Left, right, up, down as (dx, dy, direction).
        if (x > 0 && !visited[x - 1][y]) directions.add(new int[] { -1, 0, 0 });
        if (x < size - 1 && !visited[x + 1][y]) directions.add(new int[] { 1, 0, 1 });
        if (y > 0 && !visited[x][y - 1]) directions.add(new int[] { 0, -1, 2 });
        if (y < size - 1 && !visited[x][y + 1]) directions.add(new int[] { 0, 1, 3 });
        Collections.shuffle(directions, new Random());

        for (int[] direction : directions) {
            int nx = x + direction[0];
            int ny = y + direction[1];
            if (visited[nx][ny]) continue;

            // Knock down the wall between the two cells.
            switch (direction[2]) {
                case 0 -> leftWalls[x][y] = false;
                case 1 -> leftWalls[x + 1][y] = false;
                case 2 -> topWalls[x][y] = false;
                default -> topWalls[x][y + 1] = false;
            }
            carve(nx, ny, visited);
        }
    }

    /** True if movement from (x, y) is blocked by a wall in the given direction. */
    public boolean isWallAt(int x, int y, int direction) {
        return switch (direction) {
            // Left and up are the cell's own walls; the rim is always solid.
            case 0 -> x <= 0 || leftWalls[x][y];
            case 2 -> y <= 0 || topWalls[x][y];
            // Right and down are the neighbour's opposing wall.
            case 1 -> x >= size - 1 || leftWalls[x + 1][y];
            default -> y >= size - 1 || topWalls[x][y + 1];
        };
    }

    /**
     * Draws the outer rim as a single box starting at ({@code offsetX - WALL}, {@code offsetY - WALL}),
     * so the caller positions this the same way it positions the cells themselves.
     */
    public void drawBorder(int offsetX, int offsetY, int color) {
        int span = size * CELL;
        GuiDraw.drawRect(offsetX - WALL, offsetY - WALL, span + 2 * WALL, WALL, color);
        GuiDraw.drawRect(offsetX - WALL, offsetY + span, span + 2 * WALL, WALL, color);
        GuiDraw.drawRect(offsetX - WALL, offsetY, WALL, span, color);
        GuiDraw.drawRect(offsetX + span, offsetY, WALL, span, color);
    }

    /** Draws the interior walls, with cell (0, 0) at ({@code offsetX}, {@code offsetY}). */
    public void drawWalls(int offsetX, int offsetY, int color) {
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                int left = offsetX + x * CELL;
                int top = offsetY + y * CELL;

                // The rim is handled by drawBorder, so only the interior faces are drawn here.
                if (y > 0 && topWalls[x][y]) {
                    GuiDraw.drawRect(left, top, CELL, WALL, color);
                }
                if (x > 0 && leftWalls[x][y]) {
                    GuiDraw.drawRect(left, top, WALL, CELL, color);
                }
            }
        }
    }
}
