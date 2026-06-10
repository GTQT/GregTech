package gregtech.client.renderer.handler;

import gregtech.common.blocks.wood.BlockTreeTap;
import gregtech.common.blocks.wood.TileEntityTreeTap;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class TileEntityTreeTapRenderer extends TileEntitySpecialRenderer<TileEntityTreeTap> {

    private static final float WOOD_R = 0.55F, WOOD_G = 0.35F, WOOD_B = 0.17F;
    private static final float METAL_R = 0.45F, METAL_G = 0.45F, METAL_B = 0.47F;
    private static final float DARK_R = 0.28F, DARK_G = 0.28F, DARK_B = 0.30F;

    private static final float UP = 0.18F;
    private static final float DN = -0.18F;
    private static final float SD = -0.07F;

    private static final int SEGS = 8;

    @Override
    public void render(TileEntityTreeTap te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        EnumFacing facing = EnumFacing.NORTH;
        if (te.getWorld() != null) {
            IBlockState state = te.getWorld().getBlockState(te.getPos());
            if (state.getBlock() instanceof BlockTreeTap) {
                facing = state.getValue(BlockTreeTap.ATTACHED_FACING);
            }
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        applyFacingRotation(facing);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buf = tessellator.getBuffer();

        // ===== 长方体部分 =====
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        // 墙面底板 (4,5,0) -> (12,11,2)
        box(buf, 4, 5, 0, 12, 11, 2, WOOD_R, WOOD_G, WOOD_B);

        // 把手立柱 (7.5,10,3.5) -> (8.5,12,4.5)
        box(buf, 7.5, 10, 3.5, 8.5, 12, 4.5, DARK_R, DARK_G, DARK_B);

        // 把手左右臂 各伸出3px → 总长6，对称
        box(buf, 5, 11, 3.5, 11, 12, 4.5, DARK_R, DARK_G, DARK_B);

        // 把手前后臂 各伸出3px → 总长6，对称
        box(buf, 7.5, 11, 1, 8.5, 12, 7, DARK_R, DARK_G, DARK_B);

        tessellator.draw();

        // ===== 圆柱体部分 =====
        buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);

        // 水平管体 Z=2→9, 中心(8,8), 半径2.2
        cylZ(buf, 8, 8, 2, 9, 2.2, SEGS, METAL_R, METAL_G, METAL_B);

        // 垂直管体 Y=2→8, X中心=8, Z中心=7(离末端往里1/4), 半径2.0
        cylY(buf, 8, 7, 2, 8, 2.0, SEGS, METAL_R, METAL_G, METAL_B);

        // 出料嘴 Y=0.5→2, 同XZ中心, 半径1.5（收窄）
        cylY(buf, 8, 7, 0.5, 2, 1.5, SEGS, DARK_R, DARK_G, DARK_B);

        tessellator.draw();

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    // ===== 朝向旋转 =====

    private void applyFacingRotation(EnumFacing f) {
        switch (f) {
            case NORTH: break;
            case SOUTH: GlStateManager.rotate(180, 0, 1, 0);
                        GlStateManager.translate(-1, 0, -1); break;
            case WEST:  GlStateManager.rotate(90, 0, 1, 0);
                        GlStateManager.translate(-1, 0, 0); break;
            case EAST:  GlStateManager.rotate(-90, 0, 1, 0);
                        GlStateManager.translate(0, 0, -1); break;
            case UP:    GlStateManager.rotate(-90, 1, 0, 0); break;
            case DOWN:  GlStateManager.rotate(90, 1, 0, 0);
                        GlStateManager.translate(0, -1, 0); break;
        }
    }

    // ===== 长方体 =====

    private void box(BufferBuilder buf, double x1, double y1, double z1,
                     double x2, double y2, double z2,
                     float r, float g, float b) {
        double s = 1.0 / 16.0;
        double mx = x1 * s, Mx = x2 * s, my = y1 * s, My = y2 * s, mz = z1 * s, Mz = z2 * s;

        float ru = Math.min(1, r + UP), gu = Math.min(1, g + UP), bu = Math.min(1, b + UP);
        float rd = Math.max(0, r + DN), gd = Math.max(0, g + DN), bd = Math.max(0, b + DN);
        float rs = r, gs = g, bs = b;
        float rh = Math.max(0, r + SD), gh = Math.max(0, g + SD), bh = Math.max(0, b + SD);

        quad(buf, mx, My, Mz, Mx, My, Mz, Mx, My, mz, mx, My, mz, ru, gu, bu); // top
        quad(buf, mx, my, mz, Mx, my, mz, Mx, my, Mz, mx, my, Mz, rd, gd, bd); // bottom
        quad(buf, mx, my, Mz, Mx, my, Mz, Mx, My, Mz, mx, My, Mz, rs, gs, bs); // front (Z+)
        quad(buf, Mx, my, mz, mx, my, mz, mx, My, mz, Mx, My, mz, rs, gs, bs); // back (Z-)
        quad(buf, Mx, my, Mz, Mx, my, mz, Mx, My, mz, Mx, My, Mz, rh, gh, bh); // right (X+)
        quad(buf, mx, my, mz, mx, my, Mz, mx, My, Mz, mx, My, mz, rh, gh, bh); // left (X-)
    }

    // ===== 水平圆柱 (轴线沿 Z) =====

    private void cylZ(BufferBuilder buf, double cx, double cy, double z1, double z2,
                      double radius, int segs, float r, float g, float b) {
        double s = 1.0 / 16.0;
        double cz1 = z1 * s, cz2 = z2 * s;
        cx *= s;
        cy *= s;
        radius *= s;

        double[][] v = new double[segs][2];
        for (int i = 0; i < segs; i++) {
            double a = 2.0 * Math.PI * i / segs;
            v[i][0] = cx + radius * Math.cos(a);
            v[i][1] = cy + radius * Math.sin(a);
        }

        // 侧面 + 端面
        for (int i = 0; i < segs; i++) {
            int j = (i + 1) % segs;
            float shade = (float) (0.85 + 0.15 * Math.cos(2.0 * Math.PI * (i + 0.5) / segs));
            float rs = Math.min(1, r * shade), gs = Math.min(1, g * shade), bs = Math.min(1, b * shade);

            // 侧面两三角形
            // tri 1
            buf.pos(v[i][0], v[i][1], cz1).color(rs, gs, bs, 1).endVertex();
            buf.pos(v[j][0], v[j][1], cz1).color(rs, gs, bs, 1).endVertex();
            buf.pos(v[j][0], v[j][1], cz2).color(rs, gs, bs, 1).endVertex();
            // tri 2
            buf.pos(v[i][0], v[i][1], cz1).color(rs, gs, bs, 1).endVertex();
            buf.pos(v[j][0], v[j][1], cz2).color(rs, gs, bs, 1).endVertex();
            buf.pos(v[i][0], v[i][1], cz2).color(rs, gs, bs, 1).endVertex();

            // 端面三角形 (z1)
            buf.pos(cx, cy, cz1).color(r, g, b, 1).endVertex();
            buf.pos(v[i][0], v[i][1], cz1).color(r, g, b, 1).endVertex();
            buf.pos(v[j][0], v[j][1], cz1).color(r, g, b, 1).endVertex();

            // 端面三角形 (z2)
            buf.pos(cx, cy, cz2).color(r, g, b, 1).endVertex();
            buf.pos(v[j][0], v[j][1], cz2).color(r, g, b, 1).endVertex();
            buf.pos(v[i][0], v[i][1], cz2).color(r, g, b, 1).endVertex();
        }
    }

    // ===== 垂直圆柱 (轴线沿 Y) =====

    private void cylY(BufferBuilder buf, double cx, double cz, double y1, double y2,
                      double radius, int segs, float r, float g, float b) {
        double s = 1.0 / 16.0;
        double cy1 = y1 * s, cy2 = y2 * s;
        cx *= s;
        cz *= s;
        radius *= s;

        double[][] v = new double[segs][2];
        for (int i = 0; i < segs; i++) {
            double a = 2.0 * Math.PI * i / segs;
            v[i][0] = cx + radius * Math.cos(a);
            v[i][1] = cz + radius * Math.sin(a);
        }

        for (int i = 0; i < segs; i++) {
            int j = (i + 1) % segs;
            float shade = (float) (0.85 + 0.15 * Math.cos(2.0 * Math.PI * (i + 0.5) / segs));
            float rs = Math.min(1, r * shade), gs = Math.min(1, g * shade), bs = Math.min(1, b * shade);

            // 侧面两三角形
            buf.pos(v[i][0], cy1, v[i][1]).color(rs, gs, bs, 1).endVertex();
            buf.pos(v[j][0], cy1, v[j][1]).color(rs, gs, bs, 1).endVertex();
            buf.pos(v[j][0], cy2, v[j][1]).color(rs, gs, bs, 1).endVertex();

            buf.pos(v[i][0], cy1, v[i][1]).color(rs, gs, bs, 1).endVertex();
            buf.pos(v[j][0], cy2, v[j][1]).color(rs, gs, bs, 1).endVertex();
            buf.pos(v[i][0], cy2, v[i][1]).color(rs, gs, bs, 1).endVertex();

            // 端面 (y1)
            buf.pos(cx, cy1, cz).color(r, g, b, 1).endVertex();
            buf.pos(v[i][0], cy1, v[i][1]).color(r, g, b, 1).endVertex();
            buf.pos(v[j][0], cy1, v[j][1]).color(r, g, b, 1).endVertex();

            // 端面 (y2)
            buf.pos(cx, cy2, cz).color(r, g, b, 1).endVertex();
            buf.pos(v[j][0], cy2, v[j][1]).color(r, g, b, 1).endVertex();
            buf.pos(v[i][0], cy2, v[i][1]).color(r, g, b, 1).endVertex();
        }
    }

    // ===== 四边形顶点 =====

    private void quad(BufferBuilder buf, double x1, double y1, double z1,
                      double x2, double y2, double z2,
                      double x3, double y3, double z3,
                      double x4, double y4, double z4,
                      float r, float g, float b) {
        buf.pos(x1, y1, z1).color(r, g, b, 1).endVertex();
        buf.pos(x2, y2, z2).color(r, g, b, 1).endVertex();
        buf.pos(x3, y3, z3).color(r, g, b, 1).endVertex();
        buf.pos(x4, y4, z4).color(r, g, b, 1).endVertex();
    }
}
