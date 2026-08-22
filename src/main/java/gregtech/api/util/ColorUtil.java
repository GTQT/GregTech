package gregtech.api.util;

import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.text.TextFormatting;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

public class ColorUtil {

    /** 染料色 → 聊天格式色(按 EnumDyeColor 顺序 0-15,近似映射) */
    private static final TextFormatting[] DYE_FORMATTING = {
            TextFormatting.WHITE,        // 0 WHITE
            TextFormatting.GOLD,         // 1 ORANGE
            TextFormatting.LIGHT_PURPLE, // 2 MAGENTA
            TextFormatting.AQUA,         // 3 LIGHT_BLUE
            TextFormatting.YELLOW,       // 4 YELLOW
            TextFormatting.GREEN,        // 5 LIME
            TextFormatting.LIGHT_PURPLE, // 6 PINK
            TextFormatting.GRAY,         // 7 GRAY
            TextFormatting.GRAY,         // 8 SILVER
            TextFormatting.DARK_AQUA,    // 9 CYAN
            TextFormatting.DARK_PURPLE,  // 10 PURPLE
            TextFormatting.BLUE,         // 11 BLUE
            TextFormatting.GOLD,         // 12 BROWN
            TextFormatting.DARK_GREEN,   // 13 GREEN
            TextFormatting.RED,          // 14 RED
            TextFormatting.BLACK,        // 15 BLACK
    };

    public static int combineRGB(@Range(from = 0, to = 255) int r, @Range(from = 0, to = 255) int g,
                                 @Range(from = 0, to = 255) int b) {
        return (r << 16) | (g << 8) | b;
    }

    public static int combineARGB(@Range(from = 0, to = 255) int a, @Range(from = 0, to = 255) int r,
                                  @Range(from = 0, to = 255) int g, @Range(from = 0, to = 255) int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static @Nullable EnumDyeColor getDyeColorFromRGB(int color) {
        if (color == -1) return null;

        for (EnumDyeColor dyeColor : EnumDyeColor.values()) {
            if (color == dyeColor.colorValue) {
                return dyeColor;
            }
        }

        return null;
    }

    /**
     * 把 RGB 颜色值映射到聊天格式色(§ 色码),用于文字着色。
     * 仅精确匹配 16 种染料色值;非染料色(如自定义 ARGB 喷涂)返回 null。
     *
     * @param color RGB 颜色值(如 {@code EnumDyeColor#colorValue}),-1 返回 null
     * @return 对应的 {@link TextFormatting},非染料色返回 null
     */
    public static @Nullable TextFormatting getTextFormatting(int color) {
        EnumDyeColor dye = getDyeColorFromRGB(color);
        return dye == null ? null : DYE_FORMATTING[dye.getMetadata()];
    }

    public enum ARGBHelper {

        ALPHA(0xFF000000, 24),
        RED(0xFF0000, 16),
        GREEN(0xFF00, 8),
        BLUE(0xFF, 0);

        public final int overlay;
        public final int invertedOverlay;
        public final int shift;

        ARGBHelper(int overlay, int shift) {
            this.overlay = overlay;
            this.invertedOverlay = ~overlay;
            this.shift = shift;
        }

        /**
         * Isolate this channel as an integer from 0 to 255. <br/>
         * Example: {@code GREEN.isolateAndShift(0xDEADBEEF)} will return {@code 0xBE} or {@code 190}.
         */
        public @Range(from = 0, to = 0xFF) int isolateAndShift(int value) {
            return (value >> shift) & 0xFF;
        }

        /**
         * Remove the other two colors from the integer encoded ARGB and set the alpha to 255. <br/>
         * Will always return {@code 0xFF000000} if called on {@link #ALPHA}. <br/>
         * Unlike {@link #isolateAndShift(int)}, this will not be between 0 and 255. <br/>
         * Example: {@code GREEN.isolateWithFullAlpha(0xDEADBEEF)} will return {@code 0xFF00BE00} or {@code -16728576}.
         */
        public int isolateWithFullAlpha(int value) {
            return (value & overlay) | ALPHA.overlay;
        }

        /**
         * Set the value of this channel in an integer encoded ARGB value.
         */
        public int replace(int originalARGB, @Range(from = 0, to = 0xFF) int value) {
            return (originalARGB & invertedOverlay) | (value << shift);
        }

        /**
         * The same as {@link #replace(int, int)} but will just return the value shifted to this channel.
         */
        public int replace(@Range(from = 0, to = 0xFF) int value) {
            return value << shift;
        }

        /**
         * Add a value to this channel's value. Can overflow in this channel, but will not affect the other channels.
         */
        public int add(int originalARGB, @Range(from = 0, to = 0xFF) int value) {
            return replace(originalARGB, (isolateAndShift(originalARGB) + value) & 0xFF);
        }

        /**
         * Subtract a value from this channel's value. Can underflow in this channel, but will not affect the other
         * channels.
         */
        public int subtract(int originalARGB, @Range(from = 0, to = 0xFF) int value) {
            return replace(originalARGB, (isolateAndShift(originalARGB) - value) & 0xFF);
        }
    }
}
