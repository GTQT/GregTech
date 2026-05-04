package gregtech.common.mui.multiblock.godforge.panel;

import static gregtech.api.metatileentity.MetaTileEntity.TOOLTIP_DELAY;
import static net.minecraft.util.text.translation.I18n.translateToLocal;

import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.math.MathHelper;

import com.cleanroommc.modularui.api.GuiAxis;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.HueBar;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.value.FloatValue;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Row;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import gregtech.api.mui.GTGuiTextures;
import gregtech.common.metatileentities.multi.electric.godforge.data.ColorData;
import gregtech.common.metatileentities.multi.electric.godforge.data.StarColors;

public class CustomStarColorSelector {

    private static final int SIZE = CustomStarColorPanel.SIZE;

    private static final int RGB_PAGE_INDEX = CustomStarColorPanel.RGB_PAGE_INDEX;
    private static final int HSV_PAGE_INDEX = CustomStarColorPanel.HSV_PAGE_INDEX;

    protected static Flow createStarColorRGBPage(ColorData colorData) {
        return new Column().coverChildren()
            .child(createStarColorRGBRow(StarColors.RGB.RED, colorData))
            .child(createStarColorRGBRow(StarColors.RGB.GREEN, colorData))
            .child(createStarColorRGBRow(StarColors.RGB.BLUE, colorData))
            .child(createStarColorGammaRow(colorData));
    }

    private static Flow createStarColorRGBRow(StarColors.RGB color, ColorData colorData) {
        Flow row = new Row().size(SIZE - 16, 16)
            .marginBottom(3);

        row.child(
            IKey.str(color.getTitle())
                .alignment(Alignment.CENTER)
                .asWidget()
                .size(31, 16)
                .marginRight(1));

        row.child(
            new SliderWidget().size(118, 8)
                .background(new DynamicDrawable(() -> {
                    int colorHex = colorData.getColor();
                    int start = 0, end = 0;
                    switch (color) {
                        case RED -> {
                            start = Color.withRed(colorHex, 0);
                            end = Color.withRed(colorHex, 0xFF);
                        }
                        case GREEN -> {
                            start = Color.withGreen(colorHex, 0);
                            end = Color.withGreen(colorHex, 0xFF);
                        }
                        case BLUE -> {
                            start = Color.withBlue(colorHex, 0);
                            end = Color.withBlue(colorHex, 0xFF);
                        }
                    }
                    return new Rectangle().horizontalGradient(start, end);
                }))
                .bounds(0, 255)
                .sliderTexture(new Rectangle().color(Color.WHITE.main))
                .sliderSize(2, 8)
                .value(new DoubleValue.Dynamic(
                    () -> switch (color) {
                        case RED -> colorData.getR();
                        case GREEN -> colorData.getG();
                        case BLUE -> colorData.getB();
                    },
                    val -> {
                        switch (color) {
                            case RED -> colorData.setR((int) val);
                            case GREEN -> colorData.setG((int) val);
                            case BLUE -> colorData.setB((int) val);
                        }
                    }))
                .tooltipDynamic(t -> {
                    float value = switch (color) {
                        case RED -> colorData.getR();
                        case GREEN -> colorData.getG();
                        case BLUE -> colorData.getB();
                    };
                    t.addLine(color.getTooltip(value));
                })
                .tooltipShowUpTimer(TOOLTIP_DELAY)
                .tooltipAutoUpdate(true));

        row.child(
            new TextFieldWidget()
                .setNumbers(0, 255)
                .value(new IntValue.Dynamic(
                    () -> switch (color) {
                        case RED -> colorData.getR();
                        case GREEN -> colorData.getG();
                        case BLUE -> colorData.getB();
                    },
                    val -> {
                        switch (color) {
                            case RED -> colorData.setR(val);
                            case GREEN -> colorData.setG(val);
                            case BLUE -> colorData.setB(val);
                        }
                    }))
                .size(32, 16)
                .marginLeft(2)
                .setTextAlignment(Alignment.CENTER)
                .setTextColor(color.getHexColor())
                .tooltip(t -> t.addLine(translateToLocal("fog.cosmetics.onlyintegers")))
                .tooltipShowUpTimer(TOOLTIP_DELAY));

        return row;
    }

    protected static Flow createStarColorHSVPage(ColorData colorData) {
        return new Column().coverChildren()
            .child(createStarColorHSVRow(StarColors.HSV.HUE, colorData))
            .child(createStarColorHSVRow(StarColors.HSV.SATURATION, colorData))
            .child(createStarColorHSVRow(StarColors.HSV.VALUE, colorData))
            .child(createStarColorGammaRow(colorData));
    }

    private static Flow createStarColorHSVRow(StarColors.HSV color, ColorData colorData) {
        int maxValue = color == StarColors.HSV.HUE ? 360 : 1;

        Flow row = new Row().size(SIZE - 16, 16)
            .marginBottom(3);

        row.child(
            IKey.str(color.getTitle())
                .alignment(Alignment.CENTER)
                .asWidget()
                .size(31, 16)
                .marginRight(1));

        IDrawable background = switch (color) {
            case HUE -> new HueBar(GuiAxis.X);
            case SATURATION -> new DynamicDrawable(() -> {
                int start = Color.withHSVSaturation(colorData.getColor(), 0.0f);
                int end = Color.withHSVSaturation(colorData.getColor(), 1.0f);
                return new Rectangle().horizontalGradient(start, end);
            });
            case VALUE -> new DynamicDrawable(() -> {
                int start = Color.withValue(colorData.getColor(), 0.0f);
                int end = Color.withValue(colorData.getColor(), 1.0f);
                return new Rectangle().horizontalGradient(start, end);
            });
        };

        row.child(
            new SliderWidget().size(118, 8)
                .background(background)
                .bounds(0, maxValue)
                .sliderTexture(new Rectangle().color(Color.WHITE.main))
                .sliderSize(2, 8)
                .value(new DoubleValue.Dynamic(
                    () -> switch (color) {
                        case HUE -> colorData.getH();
                        case SATURATION -> colorData.getS();
                        case VALUE -> colorData.getV();
                    },
                    val -> {
                        switch (color) {
                            case HUE -> colorData.setH((float) val);
                            case SATURATION -> colorData.setS((float) val);
                            case VALUE -> colorData.setV((float) val);
                        }
                    }))
                .tooltipDynamic(t -> {
                    float value = switch (color) {
                        case HUE -> colorData.getH();
                        case SATURATION -> colorData.getS();
                        case VALUE -> colorData.getV();
                    };
                    t.addLine(color.getTooltip(value));
                })
                .tooltipShowUpTimer(TOOLTIP_DELAY)
                .tooltipAutoUpdate(true));

        row.child(
            new TextFieldWidget().setNumbersDouble(raw -> MathHelper.clamp(raw, 0.0D, maxValue))
                .value(new FloatValue.Dynamic(
                    () -> switch (color) {
                        case HUE -> colorData.getH();
                        case SATURATION -> colorData.getS();
                        case VALUE -> colorData.getV();
                    },
                    val -> {
                        switch (color) {
                            case HUE -> colorData.setH(val);
                            case SATURATION -> colorData.setS(val);
                            case VALUE -> colorData.setV(val);
                        }
                    }))
                .size(32, 16)
                .marginLeft(2)
                .setTextAlignment(Alignment.CENTER)
                .setTextColor(color.getHexColor())
                .tooltip(t -> t.addLine(translateToLocal("fog.cosmetics.onlydecimals")))
                .tooltipShowUpTimer(TOOLTIP_DELAY));

        return row;
    }

    private static Flow createStarColorGammaRow(ColorData colorData) {
        StarColors.Extra gamma = StarColors.Extra.GAMMA;
        Flow row = new Row().size(SIZE - 16, 16);

        row.child(
            IKey.str(gamma.getTitle())
                .alignment(Alignment.CENTER)
                .asWidget()
                .size(31, 16)
                .marginRight(1));

        row.child(
            new SliderWidget().size(118, 8)
                .background(new Rectangle().color(Color.GREY.main))
                .bounds(0, 100)
                .sliderTexture(new Rectangle().color(Color.WHITE.main))
                .sliderSize(2, 8)
                .value(new DoubleValue.Dynamic(colorData::getGamma, val -> colorData.setGamma((float) val)))
                .tooltipDynamic(t -> t.addLine(StarColors.Extra.GAMMA.getTooltip(colorData.getGamma())))
                .tooltipShowUpTimer(TOOLTIP_DELAY)
                .tooltipAutoUpdate(true));

        row.child(
            new TextFieldWidget().setNumbersDouble(raw -> MathHelper.clamp(raw, 0.0D, 100.0D))
                .value(new FloatValue.Dynamic(colorData::getGamma, colorData::setGamma))
                .size(32, 16)
                .marginLeft(2)
                .setTextAlignment(Alignment.CENTER)
                .setTextColor(gamma.getHexColor())
                .tooltip(t -> t.addLine(translateToLocal("fog.cosmetics.onlydecimals")))
                .tooltipShowUpTimer(TOOLTIP_DELAY));

        return row;
    }

    protected static Flow createColorPreviewRow(PagedWidget.Controller pageController, ColorData colorData) {
        Flow row = new Row().size(SIZE - 16, 15)
            .marginBottom(4);

        Flow rgbhsvRow = new Row().coverChildrenWidth()
            .childPadding(2)
            .height(15)
            .alignX(0);

        rgbhsvRow.child(
            new PageButton(RGB_PAGE_INDEX, pageController).size(24, 15)
                .background(new DynamicDrawable(() -> {
                    if (pageController.getActivePageIndex() == RGB_PAGE_INDEX) {
                        return GTGuiTextures.BUTTON_OUTLINE_HOLLOW_PRESSED;
                    }
                    return GTGuiTextures.BUTTON_OUTLINE_HOLLOW;
                }))
                .overlay(new DynamicDrawable(() -> {
                    if (pageController.getActivePageIndex() == RGB_PAGE_INDEX) {
                        return IKey.lang("fog.cosmetics.color.rgb_colored");
                    }
                    return IKey.lang("fog.cosmetics.color.rgb")
                        .style(TextFormatting.WHITE);
                })));

        rgbhsvRow.child(
            new PageButton(HSV_PAGE_INDEX, pageController).size(24, 15)
                .background(new DynamicDrawable(() -> {
                    if (pageController.getActivePageIndex() == HSV_PAGE_INDEX) {
                        return GTGuiTextures.BUTTON_OUTLINE_HOLLOW_PRESSED;
                    }
                    return GTGuiTextures.BUTTON_OUTLINE_HOLLOW;
                }))
                .overlay(new DynamicDrawable(() -> {
                    if (pageController.getActivePageIndex() == HSV_PAGE_INDEX) {
                        return IKey.lang("fog.cosmetics.color.hsv_colored");
                    }
                    return IKey.lang("fog.cosmetics.color.hsv")
                        .style(TextFormatting.WHITE);
                })));

        row.child(rgbhsvRow);

        Flow colorRow = new Row().coverChildrenWidth()
            .height(15)
            .alignX(1);

        colorRow.child(
            IKey.lang("fog.cosmetics.color.hex")
                .style(TextFormatting.GOLD)
                .asWidget()
                .height(15)
                .marginRight(3));

        colorRow.child(
            new TextFieldWidget()
                .setValidator(raw -> {
                    if (!raw.startsWith("#")) {
                        if (raw.startsWith("0x") || raw.startsWith("0X")) {
                            raw = raw.substring(2);
                        }
                        return "#" + raw;
                    }
                    return raw;
                })
                .value(new StringValue.Dynamic(colorData::getHexString, colorData::decode))
                .size(50, 15)
                .marginRight(5));

        colorRow.child(
            new DynamicDrawable(() -> new Rectangle().color(colorData.getColor())).asWidget()
                .size(32, 15));

        row.child(colorRow);

        return row;
    }
}
