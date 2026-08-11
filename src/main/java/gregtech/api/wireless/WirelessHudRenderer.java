package gregtech.api.wireless;

import gregtech.api.util.TextFormattingUtil;
import gregtech.common.ConfigHolder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 无线电网 HUD 整体渲染器。
 * 移植自 GTNH PowerGoggles 的 SimplePowerGogglesRenderer：
 * 文字块 + 渐变储能条 + 5m/1h 差值 + GL_LINES 折线图，适配 1.12.2 API。
 */
@SideOnly(Side.CLIENT)
public class WirelessHudRenderer {

    public static final WirelessHudRenderer INSTANCE = new WirelessHudRenderer();

    // ===== 颜色（ARGB）=====
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_GOOD = 0xFF55FF55;
    private static final int COLOR_BAD = 0xFFFF5555;
    private static final int COLOR_OK = 0xFFAAAAAA;
    private static final int COLOR_BG = 0x66000000;
    private static final int COLOR_CHART_BG = 0x88000000;
    private static final int COLOR_CHART_BORDER = 0xCC000000;

    // ===== 布局常量 =====
    private static final int BORDER_RADIUS = 3;
    private static final int GAP = 2;
    private static final int LINE_HEIGHT = 10;
    private static final int BAR_HEIGHT = 8;
    private static final double SUB_SCALE = 0.5;
    private static final int SUB_LINE_HEIGHT = 5;
    private static final double GRADIENT_CHANGE_FACTOR = 3.3;

    private static final String[] ENERGY_UNITS = { " EU", " KEU", " MEU", " GEU", " TEU", " PEU", " EEU", " ZEU", " YEU" };

    private final Minecraft mc = Minecraft.getMinecraft();
    private FontRenderer fontRenderer;

    private WirelessHudRenderer() {}

    public void render() {
        ConfigHolder.ClientOptions.WirelessHud cfg = ConfigHolder.client.wirelessHud;
        if (fontRenderer == null) {
            fontRenderer = mc.fontRenderer;
        }
        if (ClientWirelessHUD.samples.isEmpty()) {
            return;
        }

        ScaledResolution res = new ScaledResolution(mc);
        int screenHeight = res.getScaledHeight();
        int x = cfg.hudOffsetX;
        int bottom = screenHeight - cfg.hudOffsetY;

        // ===== 布局计算（自底向上）=====
        int textY3 = bottom - fontRenderer.FONT_HEIGHT;              // 纯流入/纯流出行
        int textY2 = textY3 - LINE_HEIGHT;                           // 能量吞吐行
        int textY1 = textY2 - LINE_HEIGHT;                           // 标题行
        int storageY = textY1 - GAP - fontRenderer.FONT_HEIGHT;      // 储能大字
        int barTop = storageY - GAP - BAR_HEIGHT;                    // 渐变储能条
        int diff1hY = barTop - GAP - SUB_LINE_HEIGHT;                // 1h 差值小字
        int diff5mY = diff1hY - GAP - SUB_LINE_HEIGHT;               // 5m 差值小字
        int chartTop = diff5mY - GAP - cfg.chartHeight;              // 折线图顶

        int barWidth = Math.max(cfg.chartWidth, getTextBlockWidth());
        int bgLeft = x - BORDER_RADIUS;
        int bgRight = x + barWidth + BORDER_RADIUS;
        int bgTop = chartTop - BORDER_RADIUS;
        int bgBottom = bottom + BORDER_RADIUS;

        // 背景
        drawRect(bgLeft, bgTop, bgRight, bgBottom, COLOR_BG);

        // 原版文字块（储能行由下方大字替代）
        drawTextBlock(x, textY1, textY2, textY3);
        // 储能大字 + 填充百分比
        ClientWirelessHUD.EnergySample last = ClientWirelessHUD.samples.getLast();
        String storedStr = TextFormattingUtil.formatBigIntToScientificString(last.stored, 4) + " EU";
        int storageColor = getTextColor(ClientWirelessHUD.euDifference5m);
        drawScaledString(storedStr, x, storageY, storageColor, 1.0);
        String percentage = formatPercentage();
        drawScaledString(percentage, x + barWidth - fontRenderer.getStringWidth(percentage), storageY, storageColor, 1.0);
        // 渐变储能条
        if (cfg.showPowerBar) {
            renderPowerBar(x, barTop, barWidth, last.stored, ClientWirelessHUD.euDifference5m);
        }
        // 5m/1h 差值
        if (cfg.showTimeDifference) {
            renderTimedDifferences(x, diff5mY, diff1hY);
        }
        // 折线图
        if (cfg.showPowerChart) {
            renderPowerChart(x, chartTop, cfg);
        }
    }

    // ===== 原版文字块 =====

    private int getTextBlockWidth() {
        ClientWirelessHUD.EnergySample last = ClientWirelessHUD.samples.getLast();
        String inputStr = formatEnergy(last.inputRate) + "/s";
        String outputStr = formatEnergy(last.outputRate) + "/s";
        int width = fontRenderer.getStringWidth("纯流入: " + inputStr + "  纯流出: " + outputStr);
        return Math.max(width, fontRenderer.getStringWidth("能量吞吐: " + ClientWirelessHUD.throughputString));
    }

    private void drawTextBlock(int x, int textY1, int textY2, int textY3) {
        ClientWirelessHUD.EnergySample last = ClientWirelessHUD.samples.getLast();
        fontRenderer.drawStringWithShadow("无线网络", x, textY1, 0xFFFFFF);

        String throughput = ClientWirelessHUD.throughputString;
        if (!throughput.isEmpty()) {
            int color = 0xAAAAAA;
            if (throughput.startsWith("+")) {
                color = 0x55FF55;
            } else if (throughput.startsWith("-")) {
                color = 0xFF5555;
            }
            fontRenderer.drawStringWithShadow("能量吞吐: " + throughput, x, textY2, color);
        }

        String inputStr = formatEnergy(last.inputRate) + "/s";
        String outputStr = formatEnergy(last.outputRate) + "/s";
        String inputLabel = "纯流入: ";
        int xPos = x;
        fontRenderer.drawStringWithShadow(inputLabel, xPos, textY3, 0xFFFFFF);
        xPos += fontRenderer.getStringWidth(inputLabel);
        fontRenderer.drawStringWithShadow(inputStr, xPos, textY3, 0x55FF55);
        xPos += fontRenderer.getStringWidth(inputStr);
        String outputLabel = "  纯流出: ";
        fontRenderer.drawStringWithShadow(outputLabel, xPos, textY3, 0xFFFFFF);
        xPos += fontRenderer.getStringWidth(outputLabel);
        fontRenderer.drawStringWithShadow(outputStr, xPos, textY3, 0xFF5555);
    }

    // ===== 渐变储能条 =====

    private void renderPowerBar(int x, int barTop, int barWidth, BigInteger lastStored, BigInteger euDifference5m) {
        int barBottom = barTop + BAR_HEIGHT;

        double differenceRatio;
        if (lastStored.compareTo(BigInteger.ZERO) == 0) {
            if (ClientWirelessHUD.getMaxStoredInSamples().compareTo(BigInteger.ZERO) > 0) {
                differenceRatio = -1;
            } else {
                differenceRatio = 0;
            }
        } else {
            differenceRatio = euDifference5m.doubleValue() / lastStored.doubleValue();
        }

        int[] gradientColors;
        if (differenceRatio < 0) {
            gradientColors = getGradient(-differenceRatio, GRADIENT_CHANGE_FACTOR, COLOR_BAD, COLOR_OK);
        } else {
            gradientColors = getGradient(differenceRatio, GRADIENT_CHANGE_FACTOR * 1.6, COLOR_GOOD, COLOR_OK);
        }
        int colorLeft = differenceRatio < 0 ? gradientColors[0] : gradientColors[1];
        int colorRight = differenceRatio < 0 ? gradientColors[1] : gradientColors[0];

        drawGradientRect(x, barTop, x + barWidth, barBottom, colorLeft, colorRight);
    }

    private int[] getGradient(double differenceRatio, double gradientChangeFactor, int gradientLeft, int gradientRight) {
        int diffRed = red(gradientLeft) - red(gradientRight);
        int diffGreen = green(gradientLeft) - green(gradientRight);
        int diffBlue = blue(gradientLeft) - blue(gradientRight);

        int newLeftRed = getGradientPart(gradientChangeFactor, red(gradientRight), diffRed, differenceRatio);
        int newLeftGreen = getGradientPart(gradientChangeFactor, green(gradientRight), diffGreen, differenceRatio);
        int newLeftBlue = getGradientPart(gradientChangeFactor, blue(gradientRight), diffBlue, differenceRatio);

        int newRightRed = getGradientPart(gradientChangeFactor, red(gradientRight), diffRed, differenceRatio * 0.75);
        int newRightGreen = getGradientPart(gradientChangeFactor, green(gradientRight), diffGreen, differenceRatio * 0.75);
        int newRightBlue = getGradientPart(gradientChangeFactor, blue(gradientRight), diffBlue, differenceRatio * 0.75);

        return new int[] { rgb(newLeftRed, newLeftGreen, newLeftBlue), rgb(newRightRed, newRightGreen, newRightBlue) };
    }

    private int getGradientPart(double gradientChangeFactor, int baseGradientPart, int partDifference, double differenceRatio) {
        double appliedPercentageOfDifference = Math.min(1, differenceRatio * gradientChangeFactor);
        int newPart = baseGradientPart + (int) (partDifference * appliedPercentageOfDifference);
        int clampBottom = Math.max(0, newPart);
        return Math.min(255, clampBottom);
    }

    private String formatPercentage() {
        BigInteger maxStored = ClientWirelessHUD.getMaxStoredInSamples();
        if (maxStored.compareTo(BigInteger.ZERO) == 0) {
            return "0.00%";
        }
        double percentage = new BigDecimal(ClientWirelessHUD.samples.getLast().stored)
                .divide(new BigDecimal(maxStored), 8, RoundingMode.HALF_EVEN)
                .doubleValue();
        return new DecimalFormat("0.00%").format(Math.max(0, Math.min(1, percentage)));
    }

    // ===== 5m/1h 差值 =====

    private void renderTimedDifferences(int x, int diff5mY, int diff1hY) {
        int tickCount5m = 5 * 60 * 20;
        int tickCount1h = 60 * 60 * 20;
        drawScaledString(getTimedDifferenceText("5m: ", ClientWirelessHUD.euDifference5m, tickCount5m), x, diff5mY,
                getTextColor(ClientWirelessHUD.euDifference5m), SUB_SCALE);
        drawScaledString(getTimedDifferenceText("1h: ", ClientWirelessHUD.euDifference1h, tickCount1h), x, diff1hY,
                getTextColor(ClientWirelessHUD.euDifference1h), SUB_SCALE);
    }

    private String getTimedDifferenceText(String prefix, BigInteger difference, int tickCount) {
        String formattedDifference = formatSignedEnergy(difference);
        String formattedTickDifference = formatSignedEnergy(difference.divide(BigInteger.valueOf(tickCount)));
        return String.format("%s%s EU (%s EU/t)", prefix, formattedDifference, formattedTickDifference);
    }

    // ===== 折线图 =====

    private static final class ChartLine {
        final Function<ClientWirelessHUD.EnergySample, BigInteger> extractor;
        final boolean directionColor; // 储能线: 每段按升降着色
        final int fixedColor;

        ChartLine(Function<ClientWirelessHUD.EnergySample, BigInteger> extractor, boolean directionColor, int fixedColor) {
            this.extractor = extractor;
            this.directionColor = directionColor;
            this.fixedColor = fixedColor;
        }
    }

    private void renderPowerChart(int x, int chartTop, ConfigHolder.ClientOptions.WirelessHud cfg) {
        int chartBottom = chartTop + cfg.chartHeight;
        int chartRight = x + cfg.chartWidth;

        // 背景 + 边框
        drawRect(x, chartTop, chartRight, chartBottom, COLOR_CHART_BG);
        drawRect(x - BORDER_RADIUS, chartTop - BORDER_RADIUS, chartRight + BORDER_RADIUS, chartBottom + BORDER_RADIUS,
                COLOR_CHART_BORDER);

        List<ChartLine> lines = new ArrayList<>();
        if (cfg.chartShowStored) {
            lines.add(new ChartLine(s -> s.stored, true, COLOR_GOOD));
        }
        if (cfg.chartShowInput) {
            lines.add(new ChartLine(s -> s.inputRate, false, COLOR_GOOD));
        }
        if (cfg.chartShowOutput) {
            lines.add(new ChartLine(s -> s.outputRate, false, COLOR_BAD));
        }
        if (lines.isEmpty()) {
            return;
        }

        List<ClientWirelessHUD.EnergySample> window = ClientWirelessHUD.getLastSamples(ClientWirelessHUD.MEASUREMENT_COUNT_5M);
        if (window.size() < 2) {
            return;
        }

        // 跨所有可见线的共同 min/max
        BigInteger minReading = null;
        BigInteger maxReading = null;
        for (ChartLine line : lines) {
            for (ClientWirelessHUD.EnergySample sample : window) {
                BigInteger value = line.extractor.apply(sample);
                if (minReading == null || value.compareTo(minReading) < 0) minReading = value;
                if (maxReading == null || value.compareTo(maxReading) > 0) maxReading = value;
            }
        }
        if (minReading == null || maxReading == null) {
            return;
        }
        // 仅储能线时沿用 GTNH 逻辑: min 向下取 10 的幂次, 让曲线更有辨识度
        if (lines.size() == 1 && lines.get(0).directionColor && minReading.compareTo(BigInteger.ZERO) > 0) {
            int exponent = minReading.toString().length() - 1; // floor(log10)
            minReading = BigInteger.TEN.pow(exponent);
        }

        // 刻度文字 (min 左下, max 左上)
        drawScaledString(formatEnergy(minReading), x,
                chartBottom - (int) (fontRenderer.FONT_HEIGHT * SUB_SCALE), COLOR_OK, SUB_SCALE);
        drawScaledString(minReading.equals(maxReading) ? "" : formatEnergy(maxReading), x, chartTop, COLOR_OK, SUB_SCALE);

        // 画各线
        for (ChartLine line : lines) {
            drawChartLine(x, chartTop, chartBottom, cfg.chartWidth, window, line, minReading, maxReading);
        }
    }

    private void drawChartLine(int x, int chartTop, int chartBottom, int chartWidth,
                               List<ClientWirelessHUD.EnergySample> window, ChartLine line,
                               BigInteger minReading, BigInteger maxReading) {
        double chartLeft = x + BORDER_RADIUS + chartWidth * 0.2;
        double lineStep = chartWidth * 0.8 / (window.size() - 1);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GL11.glLineWidth(1.5f);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        BigInteger lastMeasurement = line.extractor.apply(window.get(0));
        double lastX = chartLeft;
        double lastY = getPointY(chartTop, chartBottom, minReading, maxReading, lastMeasurement);

        for (int i = 1; i < window.size(); i++) {
            BigInteger measurement = line.extractor.apply(window.get(i));
            int color = line.directionColor ? getDirectionColor(lastMeasurement, measurement) : line.fixedColor;
            buffer.pos(lastX, lastY, 0)
                    .color(red(color), green(color), blue(color), 255)
                    .endVertex();
            double currentX = lastX + lineStep;
            double currentY = getPointY(chartTop, chartBottom, minReading, maxReading, measurement);
            buffer.pos(currentX, currentY, 0)
                    .color(red(color), green(color), blue(color), 255)
                    .endVertex();
            lastMeasurement = measurement;
            lastX = currentX;
            lastY = currentY;
        }

        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GL11.glPopAttrib();
    }

    private double getPointY(int chartTop, int chartBottom, BigInteger minReading, BigInteger maxReading,
                             BigInteger measurement) {
        if (maxReading.compareTo(minReading) <= 0) {
            return (chartTop + chartBottom) / 2.0;
        }
        BigInteger clampedMeasurement = measurement.max(minReading).min(maxReading);
        BigDecimal range = new BigDecimal(maxReading.subtract(minReading));
        BigDecimal offset = new BigDecimal(clampedMeasurement.subtract(minReading));
        double heightPercentage = offset.divide(range, 8, RoundingMode.HALF_EVEN).doubleValue();
        return chartBottom - heightPercentage * (chartBottom - chartTop);
    }

    private int getDirectionColor(BigInteger lastMeasurement, BigInteger measurement) {
        return measurement.compareTo(lastMeasurement) < 0 ? COLOR_BAD : COLOR_GOOD;
    }

    // ===== 通用绘制 =====

    private void drawScaledString(String string, int x, int y, int color, double scale) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, 0);
        GL11.glScaled(scale, scale, 1);
        GL11.glTranslated(-x, -y, 0);
        fontRenderer.drawStringWithShadow(string, x, y, color);
        GL11.glPopMatrix();
    }

    private void drawRect(int left, int top, int right, int bottom, int color) {
        drawGradientRect(left, top, right, bottom, color, color);
    }

    private void drawGradientRect(int left, int top, int right, int bottom, int colorLeft, int colorRight) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(left, top, 0).color(red(colorLeft), green(colorLeft), blue(colorLeft), alpha(colorLeft)).endVertex();
        buffer.pos(left, bottom, 0).color(red(colorLeft), green(colorLeft), blue(colorLeft), alpha(colorLeft)).endVertex();
        buffer.pos(right, bottom, 0).color(red(colorRight), green(colorRight), blue(colorRight), alpha(colorRight)).endVertex();
        buffer.pos(right, top, 0).color(red(colorRight), green(colorRight), blue(colorRight), alpha(colorRight)).endVertex();
        tessellator.draw();

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GL11.glPopAttrib();
    }

    private int getTextColor(BigInteger value) {
        int compare = value.compareTo(BigInteger.ZERO);
        return compare < 0 ? COLOR_BAD : compare > 0 ? COLOR_GOOD : COLOR_OK;
    }

    private static String formatEnergy(BigInteger energy) {
        if (energy.compareTo(BigInteger.ZERO) == 0) {
            return "0 EU";
        }
        BigDecimal value = new BigDecimal(energy);
        BigDecimal divisor = BigDecimal.valueOf(1000);
        int unitIndex = 0;
        while (value.compareTo(divisor) >= 0 && unitIndex < ENERGY_UNITS.length - 1) {
            value = value.divide(divisor, 1, RoundingMode.HALF_UP);
            unitIndex++;
        }
        return value.stripTrailingZeros().toPlainString() + ENERGY_UNITS[unitIndex];
    }

    private static String formatSignedEnergy(BigInteger energy) {
        if (energy.compareTo(BigInteger.ZERO) == 0) {
            return "0 EU";
        }
        return (energy.compareTo(BigInteger.ZERO) > 0 ? "+" : "-") + formatEnergy(energy.abs());
    }

    private static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    private static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    private static int alpha(int color) {
        return (color >> 24) & 0xFF;
    }

    private static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }
}
