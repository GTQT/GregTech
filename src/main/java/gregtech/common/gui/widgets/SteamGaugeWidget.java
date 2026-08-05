/*
 * Ported from GTNH (https://github.com/GTNewHorizons/GT5-Unofficial)
 * Original source: gregtech/common/gui/modularui/widget/SteamGaugeWidget.java
 * Licensed under LGPLv3
 */
package gregtech.common.gui.widgets;

import gregtech.api.mui.GTGuiTextures;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.value.sync.IIntSyncValue;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import org.lwjgl.opengl.GL11;

/**
 * Steam pressure gauge — circular dial + rotating needle.
 * Pure ModularUI widget that can be added directly to a ModularPanel.
 */
public class SteamGaugeWidget extends ParentWidget<SteamGaugeWidget> {

    private static final int GAUGE_W = 48;
    private static final int GAUGE_H = 42;

    public SteamGaugeWidget(IIntSyncValue<?> steamStored, IIntSyncValue<?> maxSteam, boolean isHighPressure) {
        coverChildren();
        child(new Widget<>()
                .size(GAUGE_W, GAUGE_H)
                .background(isHighPressure ? GTGuiTextures.STEAM_GAUGE_BG_STEEL : GTGuiTextures.STEAM_GAUGE_BG_BRONZE)
                .tooltipDynamic(t -> t.addLine(
                        I18n.format("gregtech.machines.steam.amount", steamStored.getValue(), maxSteam.getValue())))
                .tooltipAutoUpdate(true));
        child(new Widget<>()
                .size(GAUGE_W, GAUGE_H)
                .overlay(new NeedleDrawable(steamStored, maxSteam)));
    }

    /** Draws the rotating needle on top of the gauge background */
    private static class NeedleDrawable implements IDrawable {

        private static final double MIN_ANGLE = Math.toRadians(-230.0);
        private static final double MAX_ANGLE = Math.toRadians(47.0);
        private static final int COLOR = 0xFF8B4513;
        private final IIntSyncValue<?> steamStored, maxSteam;
        private double lastAngle = Double.NaN;

        NeedleDrawable(IIntSyncValue<?> steamStored, IIntSyncValue<?> maxSteam) {
            this.steamStored = steamStored;
            this.maxSteam = maxSteam;
        }

        @Override
        public void draw(GuiContext ctx, int x, int y, int width, int height, WidgetTheme theme) {
            double progress = maxSteam.getIntValue() > 0
                    ? (double) steamStored.getIntValue() / maxSteam.getIntValue() : 0.0;
            progress = Math.max(0.0, Math.min(1.0, progress));
            double newAngle = MIN_ANGLE + progress * (MAX_ANGLE - MIN_ANGLE);
            if (Double.isNaN(lastAngle)) lastAngle = newAngle;
            else lastAngle = (lastAngle + newAngle) / 2.0;

            int cx = x + 21, cy = y + 20;
            double sin = Math.sin(-lastAngle), cos = Math.cos(-lastAngle);
            double hw = width * 0.4, hh = height * 0.4;
            double thick = 3.2;

            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.disableLighting();
            GlStateManager.tryBlendFuncSeparate(
                    GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.shadeModel(GL11.GL_SMOOTH);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();
            buffer.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            int r = (COLOR >> 16) & 0xFF, g = (COLOR >> 8) & 0xFF, b = COLOR & 0xFF, a = (COLOR >> 24) & 0xFF;
            buffer.pos(cx + hw * cos, cy - hw * sin, 0.0).color(r, g, b, a).endVertex();
            buffer.pos(cx - thick * sin, cy - thick * cos, 0.0).color(r, g, b, a).endVertex();
            buffer.pos(cx + thick * sin, cy + thick * cos, 0.0).color(r, g, b, a).endVertex();
            buffer.pos(cx - thick * cos, cy + thick * sin, 0.0).color(r, g, b, a).endVertex();
            tessellator.draw();

            GlStateManager.shadeModel(7424);
            GlStateManager.disableBlend();
            GlStateManager.enableTexture2D();
        }

        @Override
        public boolean canApplyTheme() { return true; }
    }
}
