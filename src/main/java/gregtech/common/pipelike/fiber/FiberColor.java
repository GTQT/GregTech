package gregtech.common.pipelike.fiber;

import net.minecraft.item.EnumDyeColor;

import org.jetbrains.annotations.NotNull;

/**
 * The 16 colors a fiber optic line can carry.
 * <p>
 * Color stands in for wavelength: it is a plain discrete channel, so a recipe can ask for a
 * specific one. The order matches {@link EnumDyeColor}'s metadata, which keeps it in line with the
 * vanilla dye order.
 */
public enum FiberColor {

    WHITE(0, 0xF9FFFE),
    ORANGE(1, 0xF9801D),
    MAGENTA(2, 0xC74EBD),
    LIGHT_BLUE(3, 0x3AB3DA),
    YELLOW(4, 0xFED83D),
    LIME(5, 0x80C71F),
    PINK(6, 0xF38BAA),
    GRAY(7, 0x474F52),
    LIGHT_GRAY(8, 0x9D9D97),
    CYAN(9, 0x169C9C),
    PURPLE(10, 0x8932B8),
    BLUE(11, 0x3C44AA),
    BROWN(12, 0x835432),
    GREEN(13, 0x5E7C16),
    RED(14, 0xB02E26),
    BLACK(15, 0x1D1D21);

    public static final FiberColor[] VALUES = values();

    private final int dyeMeta;
    private final int rgb;

    FiberColor(int dyeMeta, int rgb) {
        this.dyeMeta = dyeMeta;
        this.rgb = rgb;
    }

    /** Metadata of the matching {@link EnumDyeColor}. */
    public int getDyeMeta() {
        return dyeMeta;
    }

    /** Packed {@code 0xRRGGBB} used for rendering and tooltips. */
    public int getRgb() {
        return rgb;
    }

    @NotNull
    public EnumDyeColor getDyeColor() {
        return EnumDyeColor.byMetadata(dyeMeta);
    }

    /** This color's name, matching the vanilla dye naming so it can be localised. */
    @NotNull
    public String getName() {
        return getDyeColor().getTranslationKey();
    }

    @NotNull
    public static FiberColor byDyeMeta(int meta) {
        return VALUES[meta & 0xF];
    }

    @NotNull
    public static FiberColor byDyeColor(@NotNull EnumDyeColor color) {
        return byDyeMeta(color.getMetadata());
    }
}
