package gregtech.api.wireless;

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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * 无线算力网络 HUD 整体渲染器。
 * 结构与 {@link WirelessHudRenderer} 一致：文字块 + 利用率渐变条 +
 * 5m/1h 差值 + GL_LINES 折线图。
 */
@SideOnly(Side.CLIENT)
public class WirelessComputationHudRenderer {

    public static final WirelessComputationHudRenderer INSTANCE = new WirelessComputationHudRenderer();

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

    private final Minecraft mc = Minecraft.getMinecraft();
    private FontRenderer fontRenderer;

    private WirelessComputationHudRenderer() {}

    public void render() {
        ConfigHolder.ClientOptions.WirelessComputationHud cfg = ConfigHolder.client.wirelessComputationHud;
        if (fontRenderer == null) {
            fontRenderer = mc.fontRenderer;
        }
        if (ClientWirelessComputationHUD.samples.isEmpty()) {
            return;
        }

        ScaledResolution res = new ScaledResolution(mc);
        int screenHeight = res.getScaledHeight();
        int x = cfg.hudOffsetX;
        int bottom = screenHeight - cfg.hudOffsetY;

        // ===== 布局计算（自底向上）=====
        int textY3 = bottom - fontRenderer.FONT_HEIGHT;              // 节点数行
        int textY2 = textY3 - LINE_HEIGHT;                           // 容量行
        int textY1 = textY2 - LINE_HEIGHT;                           // 标题行
        int storageY = textY1 - GAP - fontRenderer.FONT_HEIGHT;      // 分配大字
        int barTop = storageY - GAP - BAR_HEIGHT;                    // 利用率渐变条
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

        drawTextBlock(x, textY1, textY2, textY3);
        // 分配大字 + 利用率百分比
        ClientWirelessComputationHUD.ComputationSample last = ClientWirelessComputationHUD.samples.getLast();
        String allocatedStr = last.allocatedCWUt + " CWU/t";
        int allocationColor = getUtilizationColor(last);
        drawScaledString(allocatedStr, x, storageY, allocationColor, 1.0);
        String percentage = formatPercentage();
        drawScaledString(percentage, x + barWidth - fontRenderer.getStringWidth(percentage), storageY,
                allocationColor, 1.0);
        // 利用率渐变条
        if (cfg.showUtilizationBar) {
            renderUtilizationBar(x, barTop, barWidth, last);
        }
        // 5m/1h 差值
        if (cfg.showTimeDifference) {
            renderTimedDifferences(x, diff5mY, diff1hY);
        }
        // 折线图
        if (cfg.showChart) {
            renderChart(x, chartTop, cfg);
        }
    }

    // ===== 文字块 =====

    private int getTextBlockWidth() {
        ClientWirelessComputationHUD.ComputationSample last = ClientWirelessComputationHUD.samples.getLast();
        int width = fontRenderer.getStringWidth("已分配: " + last.allocatedCWUt + " CWU/t");
        width = Math.max(width, fontRenderer.getStringWidth("容量: " + last.maxCWUt + " CWU/t"));
        width = Math.max(width, fontRenderer.getStringWidth("节点: " + last.nodeCount));
        return width;
    }

    private void drawTextBlock(int x, int textY1, int textY2, int textY3) {
        ClientWirelessComputationHUD.ComputationSample last = ClientWirelessComputationHUD.samples.getLast();
        fontRenderer.drawStringWithShadow("无线算力网络", x, textY1, COLOR_WHITE);

        String capacityStr = "容量: " + last.maxCWUt + " CWU/t";
        fontRenderer.drawStringWithShadow(capacityStr, x, textY2, COLOR_OK);

        String nodeStr = "节点: " + last.nodeCount;
        fontRenderer.drawStringWithShadow(nodeStr, x, textY3, COLOR_OK);
    }

    // ===== 利用率渐变条 =====

    private void renderUtilizationBar(int x, int barTop, int barWidth,
                                      ClientWirelessComputationHUD.ComputationSample last) {
        int barBottom = barTop + BAR_HEIGHT;
        double utilization = last.maxCWUt <= 0 ? 0 :
                (double) last.allocatedCWUt / last.maxCWUt;

        // 利用率低 → 绿，接近容量 → 红（容量紧张警示）
        int[] gradientColors = getGradient(utilization, GRADIENT_CHANGE_FACTOR * 2, COLOR_BAD, COLOR_GOOD);
        int colorLeft = gradientColors[0];
        int colorRight = gradientColors[1];

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

    private int getGradientPart(double gradientChangeFactor, int baseGradientPart, int partDifference,
                                double differenceRatio) {
        double appliedPercentageOfDifference = Math.min(1, differenceRatio * gradientChangeFactor);
        int newPart = baseGradientPart + (int) (partDifference * appliedPercentageOfDifference);
        int clampBottom = Math.max(0, newPart);
        return Math.min(255, clampBottom);
    }

    private String formatPercentage() {
        int maxCapacity = ClientWirelessComputationHUD.getMaxCapacityInSamples();
        if (maxCapacity <= 0) {
            return "0.00%";
        }
        double percentage = (double) ClientWirelessComputationHUD.samples.getLast().allocatedCWUt / maxCapacity;
        return new DecimalFormat("0.00%").format(Math.max(0, Math.min(1, percentage)));
    }

    // ===== 5m/1h 差值 =====

    private void renderTimedDifferences(int x, int diff5mY, int diff1hY) {
        drawScaledString(getTimedDifferenceText("5m: ", ClientWirelessComputationHUD.allocationDifference5m), x,
                diff5mY, getDifferenceColor(ClientWirelessComputationHUD.allocationDifference5m), SUB_SCALE);
        drawScaledString(getTimedDifferenceText("1h: ", ClientWirelessComputationHUD.allocationDifference1h), x,
                diff1hY, getDifferenceColor(ClientWirelessComputationHUD.allocationDifference1h), SUB_SCALE);
    }

    private String getTimedDifferenceText(String prefix, int difference) {
        String formatted = difference > 0 ? "+" + difference : Integer.toString(difference);
        return String.format("%s%s CWU", prefix, formatted);
    }

    // ===== 折线图 =====

    private static final class ChartLine {
        final ToIntFunction<ClientWirelessComputationHUD.ComputationSample> extractor;
        final boolean directionColor;
        final int fixedColor;

        ChartLine(ToIntFunction<ClientWirelessComputationHUD.ComputationSample> extractor, boolean directionColor,
                  int fixedColor) {
            this.extractor = extractor;
            this.directionColor = directionColor;
            this.fixedColor = fixedColor;
        }
    }

    private void renderChart(int x, int chartTop, ConfigHolder.ClientOptions.WirelessComputationHud cfg) {
        int chartBottom = chartTop + cfg.chartHeight;
        int chartRight = x + cfg.chartWidth;

        drawRect(x, chartTop, chartRight, chartBottom, COLOR_CHART_BG);
        drawRect(x - BORDER_RADIUS, chartTop - BORDER_RADIUS, chartRight + BORDER_RADIUS, chartBottom + BORDER_RADIUS,
                COLOR_CHART_BORDER);

        List<ChartLine> lines = new ArrayList<>();
        if (cfg.chartShowAllocated) {
            lines.add(new ChartLine(s -> s.allocatedCWUt, true, COLOR_GOOD));
        }
        if (cfg.chartShowRate) {
            lines.add(new ChartLine(s -> s.allocatedPerSecond, false, COLOR_BAD));
        }
        if (lines.isEmpty()) {
            return;
        }

        List<ClientWirelessComputationHUD.ComputationSample> window =
                ClientWirelessComputationHUD.getLastSamples(ClientWirelessComputationHUD.MEASUREMENT_COUNT_5M);
        if (window.size() < 2) {
            return;
        }

        int minReading = Integer.MAX_VALUE;
        int maxReading = Integer.MIN_VALUE;
        for (ChartLine line : lines) {
            for (ClientWirelessComputationHUD.ComputationSample sample : window) {
                int value = line.extractor.applyAsInt(sample);
                if (value < minReading) minReading = value;
                if (value > maxReading) maxReading = value;
            }
        }
        if (minReading == Integer.MAX_VALUE || maxReading == Integer.MIN_VALUE) {
            return;
        }
        if (minReading == maxReading) {
            // 平线时给一点余量, 避免除零
            minReading = 0;
        }

        drawScaledString(Integer.toString(minReading), x,
                chartBottom - (int) (fontRenderer.FONT_HEIGHT * SUB_SCALE), COLOR_OK, SUB_SCALE);
        drawScaledString(Integer.toString(maxReading), x, chartTop, COLOR_OK, SUB_SCALE);

        for (ChartLine line : lines) {
            drawChartLine(x, chartTop, chartBottom, cfg.chartWidth, window, line, minReading, maxReading);
        }
    }

    private void drawChartLine(int x, int chartTop, int chartBottom, int chartWidth,
                               List<ClientWirelessComputationHUD.ComputationSample> window, ChartLine line,
                               int minReading, int maxReading) {
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

        int lastMeasurement = line.extractor.applyAsInt(window.get(0));
        double lastX = chartLeft;
        double lastY = getPointY(chartTop, chartBottom, minReading, maxReading, lastMeasurement);

        for (int i = 1; i < window.size(); i++) {
            int measurement = line.extractor.applyAsInt(window.get(i));
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

    private double getPointY(int chartTop, int chartBottom, int minReading, int maxReading, int measurement) {
        if (maxReading <= minReading) {
            return (chartTop + chartBottom) / 2.0;
        }
        int clamped = Math.max(minReading, Math.min(maxReading, measurement));
        double heightPercentage = (double) (clamped - minReading) / (maxReading - minReading);
        return chartBottom - heightPercentage * (chartBottom - chartTop);
    }

    private int getDirectionColor(int lastMeasurement, int measurement) {
        return measurement < lastMeasurement ? COLOR_BAD : COLOR_GOOD;
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

    private int getUtilizationColor(ClientWirelessComputationHUD.ComputationSample last) {
        if (last.maxCWUt <= 0) return COLOR_OK;
        double utilization = (double) last.allocatedCWUt / last.maxCWUt;
        if (utilization > 0.8) return COLOR_BAD;
        if (utilization > 0.5) return 0xFFFFFF55;
        return COLOR_GOOD;
    }

    private int getDifferenceColor(int difference) {
        return difference < 0 ? COLOR_BAD : difference > 0 ? COLOR_GOOD : COLOR_OK;
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
