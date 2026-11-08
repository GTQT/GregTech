package gregtech.client.gui.gl;

import gregtech.api.gui.resources.IGuiTexture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Drawing primitives backed by vanilla 1.12.2 primitives ({@code Gui.drawRect}, {@code GlStateManager},
 * {@code RenderHelper}) so the self-drawn tool screens do not depend on ModularUI or any widget framework.
 *
 * <p>
 * Every method works in GUI-scaled coordinates. Scissor rectangles are converted to framebuffer pixels internally.
 */
@SideOnly(Side.CLIENT)
public final class GDraw {

    private static final Deque<int[]> SCISSORS = new ArrayDeque<>();

    private GDraw() {}

    // ---------------------------------------------------------------------------------------------
    // Rectangles
    // ---------------------------------------------------------------------------------------------

    public static void rect(int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        Gui.drawRect(x, y, x + width, y + height, color);
    }

    public static void gradient(int x, int y, int width, int height, int top, int bottom) {
        if (width <= 0 || height <= 0) return;
        // Gui.drawGradientRect is protected in 1.12.2, so interpolate one row at a time instead.
        for (int row = 0; row < height; row++) {
            float t = height == 1 ? 0F : (float) row / (float) (height - 1);
            Gui.drawRect(x, y + row, x + width, y + row + 1, lerpColor(top, bottom, t));
        }
    }

    private static int lerpColor(int from, int to, float t) {
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = (int) (((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void outline(int x, int y, int width, int height, int color) {
        rect(x, y, width, 1, color);
        rect(x, y + height - 1, width, 1, color);
        rect(x, y + 1, 1, height - 2, color);
        rect(x + width - 1, y + 1, 1, height - 2, color);
    }

    /** Raised vanilla-style bevel: light top/left, dark bottom/right, filled with {@code fill}. */
    public static void bevel(int x, int y, int width, int height, int fill, int light, int dark) {
        rect(x, y, width, height, fill);
        rect(x, y, width - 1, 1, light);
        rect(x, y, 1, height - 1, light);
        rect(x + 1, y + height - 1, width - 1, 1, dark);
        rect(x + width - 1, y + 1, 1, height - 1, dark);
    }

    /** Sunken bevel used by slots and text inputs. */
    public static void sunken(int x, int y, int width, int height, int fill) {
        rect(x, y, width, height, fill);
        rect(x, y, width - 1, 1, 0xFF373737);
        rect(x, y, 1, height - 1, 0xFF373737);
        rect(x + 1, y + height - 1, width - 1, 1, 0xFFFFFFFF);
        rect(x + width - 1, y + 1, 1, height - 1, 0xFFFFFFFF);
    }

    /** GTO-style card surface: grey fill with a 1px dark edge. */
    public static void card(int x, int y, int width, int height) {
        rect(x, y, width, height, GUITokens.WINDOW_BG);
        outline(x, y, width, height, GUITokens.PANEL_EDGE);
    }

    // ---------------------------------------------------------------------------------------------
    // Text
    // ---------------------------------------------------------------------------------------------

    public static int text(String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) return 0;
        return Minecraft.getMinecraft().fontRenderer.drawString(text, x, y, color);
    }

    public static int textShadow(String text, int x, int y, int color) {
        if (text == null || text.isEmpty()) return 0;
        return Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(text, x, y, color);
    }

    public static void textCentered(String text, int centerX, int y, int color) {
        if (text == null || text.isEmpty()) return;
        int width = Minecraft.getMinecraft().fontRenderer.getStringWidth(text);
        text(text, centerX - width / 2, y, color);
    }

    public static void textRight(String text, int rightX, int y, int color) {
        if (text == null || text.isEmpty()) return;
        int width = Minecraft.getMinecraft().fontRenderer.getStringWidth(text);
        text(text, rightX - width, y, color);
    }

    public static int textWidth(String text) {
        return text == null ? 0 : Minecraft.getMinecraft().fontRenderer.getStringWidth(text);
    }

    public static void textTrimmed(String text, int x, int y, int maxWidth, int color) {
        text(GUITokens.trim(text, maxWidth), x, y, color);
    }

    // ---------------------------------------------------------------------------------------------
    // Textures and items
    // ---------------------------------------------------------------------------------------------

    public static void texture(IGuiTexture texture, int x, int y, int width, int height) {
        if (texture == null || width <= 0 || height <= 0) return;
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.enableBlend();
        GlStateManager.enableTexture2D();
        texture.draw(x, y, width, height);
        GlStateManager.disableBlend();
    }

    /** Draws an item stack scaled to the given slot size (16px content stays centered). */
    public static void item(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableRescaleNormal();
        Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableDepth();
        RenderHelper.disableStandardItemLighting();
    }

    public static void itemOverlay(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        Minecraft.getMinecraft().getRenderItem().renderItemOverlayIntoGUI(
                Minecraft.getMinecraft().fontRenderer, stack, x, y, null);
    }

    // ---------------------------------------------------------------------------------------------
    // Scissor stack
    // ---------------------------------------------------------------------------------------------

    public static void pushScissor(int x, int y, int width, int height) {
        int[] parent = SCISSORS.peek();
        int nx = x;
        int ny = y;
        int nw = width;
        int nh = height;
        if (parent != null) {
            nx = Math.max(nx, parent[0]);
            ny = Math.max(ny, parent[1]);
            nw = Math.min(x + width, parent[0] + parent[2]) - nx;
            nh = Math.min(y + height, parent[1] + parent[3]) - ny;
        }
        nw = Math.max(0, nw);
        nh = Math.max(0, nh);
        SCISSORS.push(new int[] { nx, ny, nw, nh });
        applyScissor(nx, ny, nw, nh);
    }

    public static void popScissor() {
        if (SCISSORS.isEmpty()) return;
        SCISSORS.pop();
        int[] parent = SCISSORS.peek();
        if (parent == null) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        } else {
            applyScissor(parent[0], parent[1], parent[2], parent[3]);
        }
    }

    private static void applyScissor(int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        int scale = resolution.getScaleFactor();
        int framebufferHeight = mc.getFramebuffer().framebufferHeight;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, framebufferHeight - (y + height) * scale, width * scale, height * scale);
    }

    public static void resetScissor() {
        SCISSORS.clear();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
