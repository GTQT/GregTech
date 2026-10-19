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
    /** Height of one channel row, including its label and slider track. */
    public static final int CHANNEL_ROW_HEIGHT = 16;
    /** Top and bottom padding of the channel strip around the rows. */
    private static final int CONTROLS_PADDING = 8;
    /** Height of the strip when every channel row is used; also the recipe height JEI reserves. */
    public static final int CONTROLS_HEIGHT = controlsHeight(CHANNEL_ROWS);
    public static final int RECIPE_HEIGHT = CONTROLS_Y + CONTROLS_HEIGHT;

    private CompactPreviewLayout() {}

    /**
     * Height of the compact channel strip for the given channel count. Only the rows that actually exist are
     * reserved; the previous fixed four-row strip left up to three empty rows below the last slider. At least one
     * row is kept because the strip also hosts the layer button and the fullscreen button.
     */
    public static int controlsHeight(int channelCount) {
        int rows = Math.max(1, Math.min(CHANNEL_ROWS, channelCount));
        return rows * CHANNEL_ROW_HEIGHT + CONTROLS_PADDING;
    }

    public static boolean containsViewport(int x, int y) {
        return contains(x, y, 0, 0, WIDTH, HEIGHT);
    }

    public static boolean containsExtensions(int x, int y, int partCount, int controlsHeight) {
        return contains(x, y, partsX(partCount), 0, partsWidth(partCount), partsHeight(partCount)) ||
                contains(x, y, 0, CONTROLS_Y, WIDTH, controlsHeight);
    }

    private static boolean contains(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
