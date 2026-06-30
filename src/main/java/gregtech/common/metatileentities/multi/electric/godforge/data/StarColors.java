package gregtech.common.metatileentities.multi.electric.godforge.data;

import gregtech.common.metatileentities.multi.electric.godforge.color.ForgeOfGodsStarColor;

import net.minecraft.util.text.TextFormatting;

import static net.minecraft.util.text.translation.I18n.translateToLocal;

public final class StarColors {

    public enum RGB implements IStarColor {

        RED(TextFormatting.RED, 0xFFFF5555, ForgeOfGodsStarColor.DEFAULT_RED),
        GREEN(TextFormatting.GREEN, 0xFF55FF55, ForgeOfGodsStarColor.DEFAULT_GREEN),
        BLUE(TextFormatting.BLUE, 0xFF5555FF, ForgeOfGodsStarColor.DEFAULT_BLUE);

        private final String title;
        private final TextFormatting color;
        private final int hexColor;
        private final int defaultValue;

        RGB(TextFormatting color, int hexColor, int defaultValue) {
            this.title = "fog.cosmetics.color." + name().toLowerCase();
            this.color = color;
            this.hexColor = hexColor;
            this.defaultValue = defaultValue;
        }

        @Override
        public String getTitle() {
            return color + translateToLocal(title);
        }

        @Override
        public TextFormatting getColor() {
            return color;
        }

        @Override
        public int getHexColor() {
            return hexColor;
        }

        @Override
        public float getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getTooltip(float value) {
            return String.format("%s: %d", getTitle(), (int) value);
        }
    }

    public enum HSV implements IStarColor {

        HUE(TextFormatting.LIGHT_PURPLE, 0xFFFF55FF, ForgeOfGodsStarColor.DEFAULT_HUE),
        SATURATION(TextFormatting.GOLD, 0xFFFFAA00, ForgeOfGodsStarColor.DEFAULT_SATURATION),
        VALUE(TextFormatting.AQUA, 0xFF55FFFF, ForgeOfGodsStarColor.DEFAULT_VALUE);

        private final String title;
        private final TextFormatting color;
        private final int hexColor;
        private final float defaultValue;

        HSV(TextFormatting color, int hexColor, float defaultValue) {
            this.title = "fog.cosmetics.color." + name().toLowerCase();
            this.color = color;
            this.hexColor = hexColor;
            this.defaultValue = defaultValue;
        }

        @Override
        public String getTitle() {
            return color + translateToLocal(title);
        }

        @Override
        public TextFormatting getColor() {
            return color;
        }

        @Override
        public int getHexColor() {
            return hexColor;
        }

        @Override
        public float getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getTooltip(float value) {
            if (this == HUE) {
                return String.format("%s: %.1f", getTitle(), value);
            }
            return String.format("%s: %.3f", getTitle(), value);
        }
    }

    public enum Extra implements IStarColor {

        GAMMA(TextFormatting.GRAY, 0xFFAAAAAA, ForgeOfGodsStarColor.DEFAULT_GAMMA);

        private final String title;
        private final TextFormatting color;
        private final int hexColor;
        private final float defaultValue;

        Extra(TextFormatting color, int hexColor, float defaultValue) {
            this.title = "fog.cosmetics.color." + name().toLowerCase();
            this.color = color;
            this.hexColor = hexColor;
            this.defaultValue = defaultValue;
        }

        @Override
        public String getTitle() {
            return color + translateToLocal(title);
        }

        @Override
        public TextFormatting getColor() {
            return color;
        }

        @Override
        public int getHexColor() {
            return hexColor;
        }

        @Override
        public float getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getTooltip(float value) {
            return String.format("%s: %.2f", getTitle(), value);
        }
    }

    public interface IStarColor {

        String getTitle();

        TextFormatting getColor();

        int getHexColor();

        float getDefaultValue();

        String getTooltip(float value);
    }
}
