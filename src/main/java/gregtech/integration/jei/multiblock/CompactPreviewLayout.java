package gregtech.integration.jei.multiblock;

/** Separate the unchanged structure viewport from the full recipe layout reserved by JEI. */
public final class CompactPreviewLayout {
    public static final int WIDTH = 176;
    public static final int HEIGHT = 184;
    private static final int PARTS_RIGHT = -6;
    private static final int SLOT_SIZE = 18;
    private static final int ROWS_PER_COLUMN = 10;

    public static int partsWidth(int count) {
        return count <= 0 ? 0 : ((count + ROWS_PER_COLUMN - 1) / ROWS_PER_COLUMN) * SLOT_SIZE + 4;
    }

    public static int partsHeight(int count) {
        return count <= 0 ? 0 : Math.min(count, ROWS_PER_COLUMN) * SLOT_SIZE + 4;
    }

    public static int partsX(int count) {
        return PARTS_RIGHT - partsWidth(count);
    }

    public static int partSlotX(int index) {
        return PARTS_RIGHT - 2 - (index / ROWS_PER_COLUMN + 1) * SLOT_SIZE;
    }

    public static int partSlotY(int index) {
        return 2 + (index % ROWS_PER_COLUMN) * SLOT_SIZE;
    }
    public static final int CONTROLS_Y = HEIGHT + 8;
    public static final int CHANNEL_ROWS = 4;
    public static final int CONTROLS_HEIGHT = CHANNEL_ROWS * 16 + 8;
    public static final int RECIPE_HEIGHT = CONTROLS_Y + CONTROLS_HEIGHT;

    private CompactPreviewLayout() {}

    public static boolean containsViewport(int x, int y) {
        return contains(x, y, 0, 0, WIDTH, HEIGHT);
    }

    public static boolean containsExtensions(int x, int y, int partCount) {
        return contains(x, y, partsX(partCount), 0, partsWidth(partCount), partsHeight(partCount)) ||
                contains(x, y, 0, CONTROLS_Y, WIDTH, CONTROLS_HEIGHT);
    }

    private static boolean contains(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
