package gregtech.client.utils;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.vec.Cuboid6;

@SideOnly(Side.CLIENT)
public class RenderBufferHelper {

    public static void renderCubeFrame(BufferBuilder buffer, double minX, double minY, double minZ, double maxX,
                                       double maxY, double maxZ, float r, float g, float b, float a) {
        buffer.pos(minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();

        buffer.pos(minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();

        buffer.pos(minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();

        buffer.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();

        buffer.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();

        buffer.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();

        buffer.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();

        buffer.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();

        buffer.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();

        buffer.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();

        buffer.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();

        buffer.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();
    }

    public static void renderCubeFace(BufferBuilder buffer, Cuboid6 cuboid, float r, float g, float b, float a,
                                      boolean shade) {
        renderCubeFace(buffer, cuboid.min.x, cuboid.min.y, cuboid.min.z, cuboid.max.x, cuboid.max.y, cuboid.max.z, r, g,
                b, a, shade);
    }

    public static void renderCubeFace(BufferBuilder buffer, double minX, double minY, double minZ, double maxX,
                                      double maxY, double maxZ, float red, float green, float blue, float alpha) {
        renderCubeFace(buffer, minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha, false);
    }

    public static void renderCubeFace(BufferBuilder buffer, double minX, double minY, double minZ, double maxX,
                                      double maxY, double maxZ, float red, float green, float blue, float a,
                                      boolean shade) {
        float r = red, g = green, b = blue;

        if (shade) {
            r *= 0.6;
            g *= 0.6;
            b *= 0.6;
        }
        buffer.pos(minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();

        buffer.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();

        if (shade) {
            r = red * 0.5f;
            g = green * 0.5f;
            b = blue * 0.5f;
        }
        buffer.pos(minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();

        if (shade) {
            r = red;
            g = green;
            b = blue;
        }
        buffer.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();

        if (shade) {
            r = red * 0.8f;
            g = green * 0.8f;
            b = blue * 0.8f;
        }
        buffer.pos(minX, minY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();

        buffer.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
        buffer.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();
    }

    public static void renderRing(BufferBuilder buffer, double x, double y, double z, double r, double tubeRadius,
                                  int sides, int segments, float red, float green, float blue, float alpha,
                                  EnumFacing.Axis axis) {
        double sideDelta = 2.0 * Math.PI / sides;
        double ringDelta = 2.0 * Math.PI / segments;
        double theta = 0;
        double cosTheta = 1.0;
        double sinTheta = 0.0;

        double phi, sinPhi, cosPhi;
        double dist;

        for (int i = 0; i < segments; i++) {
            double theta1 = theta + ringDelta;
            double cosTheta1 = MathHelper.cos((float) theta1);
            double sinTheta1 = MathHelper.sin((float) theta1);

            phi = 0;
            for (int j = 0; j <= sides; j++) {
                phi = phi + sideDelta;
                cosPhi = MathHelper.cos((float) phi);
                sinPhi = MathHelper.sin((float) phi);
                dist = r + (tubeRadius * cosPhi);

                switch (axis) {
                    case Y:
                        buffer.pos(x + sinTheta * dist, y + tubeRadius * sinPhi, z + cosTheta * dist)
                                .color(red, green, blue, alpha).endVertex();
                        buffer.pos(x + sinTheta1 * dist, y + tubeRadius * sinPhi, z + cosTheta1 * dist)
                                .color(red, green, blue, alpha).endVertex();
                        break;
                    case X:
                        buffer.pos(x + tubeRadius * sinPhi, y + sinTheta * dist, z + cosTheta * dist)
                                .color(red, green, blue, alpha).endVertex();
                        buffer.pos(x + tubeRadius * sinPhi, y + sinTheta1 * dist, z + cosTheta1 * dist)
                                .color(red, green, blue, alpha).endVertex();
                        break;
                    case Z:
                        buffer.pos(x + cosTheta * dist, y + sinTheta * dist, z + tubeRadius * sinPhi)
                                .color(red, green, blue, alpha).endVertex();
                        buffer.pos(x + cosTheta1 * dist, y + sinTheta1 * dist, z + tubeRadius * sinPhi)
                                .color(red, green, blue, alpha).endVertex();
                        break;
                }

            }
            theta = theta1;
            cosTheta = cosTheta1;
            sinTheta = sinTheta1;

        }
    }

    /**
     * 渲染球体（使用经纬度方法）
     *
     * @param buffer  缓冲区构建器
     * @param centerX 球心X坐标
     * @param centerY 球心Y坐标
     * @param centerZ 球心Z坐标
     * @param radius  球体半径
     * @param slices  经度分段数（垂直方向）
     * @param stacks  纬度分段数（水平方向）
     * @param red     红色分量
     * @param green   绿色分量
     * @param blue    蓝色分量
     * @param alpha   透明度
     */
    public static void renderSphere(BufferBuilder buffer, double centerX, double centerY, double centerZ,
                                    double radius, int slices, int stacks,
                                    float red, float green, float blue, float alpha) {

        for (int i = 0; i < slices; i++) {
            double theta1 = i * Math.PI / slices;
            double theta2 = (i + 1) * Math.PI / slices;

            for (int j = 0; j < stacks; j++) {
                double phi1 = j * 2 * Math.PI / stacks;
                double phi2 = (j + 1) * 2 * Math.PI / stacks;

                // 计算四个顶点
                double x1 = centerX + radius * Math.sin(theta1) * Math.cos(phi1);
                double y1 = centerY + radius * Math.cos(theta1);
                double z1 = centerZ + radius * Math.sin(theta1) * Math.sin(phi1);

                double x2 = centerX + radius * Math.sin(theta1) * Math.cos(phi2);
                double y2 = centerY + radius * Math.cos(theta1);
                double z2 = centerZ + radius * Math.sin(theta1) * Math.sin(phi2);

                double x3 = centerX + radius * Math.sin(theta2) * Math.cos(phi2);
                double y3 = centerY + radius * Math.cos(theta2);
                double z3 = centerZ + radius * Math.sin(theta2) * Math.sin(phi2);

                double x4 = centerX + radius * Math.sin(theta2) * Math.cos(phi1);
                double y4 = centerY + radius * Math.cos(theta2);
                double z4 = centerZ + radius * Math.sin(theta2) * Math.sin(phi1);

                // 绘制两个三角形组成四边形
                buffer.pos(x1, y1, z1).color(red, green, blue, alpha).endVertex();
                buffer.pos(x2, y2, z2).color(red, green, blue, alpha).endVertex();
                buffer.pos(x4, y4, z4).color(red, green, blue, alpha).endVertex();

                buffer.pos(x2, y2, z2).color(red, green, blue, alpha).endVertex();
                buffer.pos(x3, y3, z3).color(red, green, blue, alpha).endVertex();
                buffer.pos(x4, y4, z4).color(red, green, blue, alpha).endVertex();
            }
        }
    }

    /**
     * 渲染线框球体
     *
     * @param buffer   缓冲区构建器
     * @param centerX  球心X坐标
     * @param centerY  球心Y坐标
     * @param centerZ  球心Z坐标
     * @param radius   球体半径
     * @param segments 分段数
     * @param red      红色分量
     * @param green    绿色分量
     * @param blue     蓝色分量
     * @param alpha    透明度
     */
    public static void renderSphereFrame(BufferBuilder buffer, double centerX, double centerY, double centerZ,
                                         double radius, int segments,
                                         float red, float green, float blue, float alpha) {

        // 绘制经线
        for (int i = 0; i < segments; i++) {
            double phi = i * 2 * Math.PI / segments;
            double cosPhi = MathHelper.cos((float) phi);
            double sinPhi = MathHelper.sin((float) phi);

            for (int j = 0; j < segments; j++) {
                double theta1 = j * Math.PI / segments;
                double theta2 = (j + 1) * Math.PI / segments;

                double x1 = centerX + radius * Math.sin(theta1) * cosPhi;
                double y1 = centerY + radius * Math.cos(theta1);
                double z1 = centerZ + radius * Math.sin(theta1) * sinPhi;

                double x2 = centerX + radius * Math.sin(theta2) * cosPhi;
                double y2 = centerY + radius * Math.cos(theta2);
                double z2 = centerZ + radius * Math.sin(theta2) * sinPhi;

                buffer.pos(x1, y1, z1).color(red, green, blue, alpha).endVertex();
                buffer.pos(x2, y2, z2).color(red, green, blue, alpha).endVertex();
            }
        }

        // 绘制纬线
        for (int j = 0; j < segments; j++) {
            double theta = j * Math.PI / segments;
            double cosTheta = MathHelper.cos((float) theta);
            double sinTheta = MathHelper.sin((float) theta);

            for (int i = 0; i < segments; i++) {
                double phi1 = i * 2 * Math.PI / segments;
                double phi2 = (i + 1) * 2 * Math.PI / segments;

                double x1 = centerX + radius * sinTheta * MathHelper.cos((float) phi1);
                double y1 = centerY + radius * cosTheta;
                double z1 = centerZ + radius * sinTheta * MathHelper.sin((float) phi1);

                double x2 = centerX + radius * sinTheta * MathHelper.cos((float) phi2);
                double y2 = centerY + radius * cosTheta;
                double z2 = centerZ + radius * sinTheta * MathHelper.sin((float) phi2);

                buffer.pos(x1, y1, z1).color(red, green, blue, alpha).endVertex();
                buffer.pos(x2, y2, z2).color(red, green, blue, alpha).endVertex();
            }
        }
    }

    /**
     * 渲染八面体
     *
     * @param buffer  缓冲区构建器
     * @param centerX 中心X坐标
     * @param centerY 中心Y坐标
     * @param centerZ 中心Z坐标
     * @param radius  半径（顶点到中心的距离）
     * @param red     红色分量
     * @param green   绿色分量
     * @param blue    蓝色分量
     * @param alpha   透明度
     */
    public static void renderOctahedron(BufferBuilder buffer, double centerX, double centerY, double centerZ,
                                        double radius, float red, float green, float blue, float alpha) {

        // 八面体的6个顶点
        double[][] vertices = {
                { centerX + radius, centerY, centerZ }, // 右
                { centerX - radius, centerY, centerZ }, // 左
                { centerX, centerY + radius, centerZ }, // 上
                { centerX, centerY - radius, centerZ }, // 下
                { centerX, centerY, centerZ + radius }, // 前
                { centerX, centerY, centerZ - radius }  // 后
        };

        // 八面体的8个面（每个面3个顶点）
        int[][] faces = {
                { 0, 2, 4 }, { 0, 4, 3 }, { 0, 3, 5 }, { 0, 5, 2 }, // 右半部分
                { 1, 4, 2 }, { 1, 3, 4 }, { 1, 5, 3 }, { 1, 2, 5 }  // 左半部分
        };

        // 渲染每个面
        for (int[] face : faces) {
            double[] v1 = vertices[face[0]];
            double[] v2 = vertices[face[1]];
            double[] v3 = vertices[face[2]];

            buffer.pos(v1[0], v1[1], v1[2]).color(red, green, blue, alpha).endVertex();
            buffer.pos(v2[0], v2[1], v2[2]).color(red, green, blue, alpha).endVertex();
            buffer.pos(v3[0], v3[1], v3[2]).color(red, green, blue, alpha).endVertex();
        }
    }

    /**
     * 渲染线框八面体
     *
     * @param buffer  缓冲区构建器
     * @param centerX 中心X坐标
     * @param centerY 中心Y坐标
     * @param centerZ 中心Z坐标
     * @param radius  半径（顶点到中心的距离）
     * @param red     红色分量
     * @param green   绿色分量
     * @param blue    蓝色分量
     * @param alpha   透明度
     */
    public static void renderOctahedronFrame(BufferBuilder buffer, double centerX, double centerY, double centerZ,
                                             double radius, float red, float green, float blue, float alpha) {

        // 八面体的6个顶点
        double[][] vertices = {
                { centerX + radius, centerY, centerZ }, // 右
                { centerX - radius, centerY, centerZ }, // 左
                { centerX, centerY + radius, centerZ }, // 上
                { centerX, centerY - radius, centerZ }, // 下
                { centerX, centerY, centerZ + radius }, // 前
                { centerX, centerY, centerZ - radius }  // 后
        };

        // 八面体的12条边
        int[][] edges = {
                { 0, 2 }, { 0, 3 }, { 0, 4 }, { 0, 5 }, // 右顶点连接到其他顶点
                { 1, 2 }, { 1, 3 }, { 1, 4 }, { 1, 5 }, // 左顶点连接到其他顶点
                { 2, 4 }, { 2, 5 }, { 3, 4 }, { 3, 5 }  // 上下顶点连接到前后顶点
        };

        // 渲染每条边
        for (int[] edge : edges) {
            double[] v1 = vertices[edge[0]];
            double[] v2 = vertices[edge[1]];

            buffer.pos(v1[0], v1[1], v1[2]).color(red, green, blue, alpha).endVertex();
            buffer.pos(v2[0], v2[1], v2[2]).color(red, green, blue, alpha).endVertex();
        }
    }

    /**
     * 渲染圆柱体侧面
     *
     * @param buffer   缓冲区构建器
     * @param centerX  圆柱底部中心X坐标
     * @param centerY  圆柱底部中心Y坐标
     * @param centerZ  圆柱底部中心Z坐标
     * @param radius   圆柱半径
     * @param height   圆柱高度
     * @param segments 圆柱分段数（越多越圆滑）
     * @param red      红色分量
     * @param green    绿色分量
     * @param blue     蓝色分量
     * @param alpha    透明度
     */
    public static void renderCylinder(BufferBuilder buffer, double centerX, double centerY, double centerZ,
                                      double radius, double height, int segments,
                                      float red, float green, float blue, float alpha) {

        double angleIncrement = 2 * Math.PI / segments;

        for (int i = 0; i < segments; i++) {
            double angle1 = i * angleIncrement;
            double angle2 = (i + 1) * angleIncrement;

            double x1 = centerX + radius * MathHelper.cos((float) angle1);
            double z1 = centerZ + radius * MathHelper.sin((float) angle1);
            double x2 = centerX + radius * MathHelper.cos((float) angle2);
            double z2 = centerZ + radius * MathHelper.sin((float) angle2);

            // 侧面四边形（两个三角形）
            double bottomY = centerY;
            double topY = centerY + height;

            // 第一个三角形
            buffer.pos(x1, bottomY, z1).color(red, green, blue, alpha).endVertex();
            buffer.pos(x2, bottomY, z2).color(red, green, blue, alpha).endVertex();
            buffer.pos(x1, topY, z1).color(red, green, blue, alpha).endVertex();

            // 第二个三角形
            buffer.pos(x2, bottomY, z2).color(red, green, blue, alpha).endVertex();
            buffer.pos(x2, topY, z2).color(red, green, blue, alpha).endVertex();
            buffer.pos(x1, topY, z1).color(red, green, blue, alpha).endVertex();
        }
    }

    /**
     * 渲染完整圆柱体（包括底面和顶面）
     *
     * @param buffer     缓冲区构建器
     * @param centerX    圆柱底部中心X坐标
     * @param centerY    圆柱底部中心Y坐标
     * @param centerZ    圆柱底部中心Z坐标
     * @param radius     圆柱半径
     * @param height     圆柱高度
     * @param segments   圆柱分段数
     * @param red        红色分量
     * @param green      绿色分量
     * @param blue       蓝色分量
     * @param alpha      透明度
     * @param renderCaps 是否渲染底面和顶面
     */
    public static void renderCylinder(BufferBuilder buffer, double centerX, double centerY, double centerZ,
                                      double radius, double height, int segments,
                                      float red, float green, float blue, float alpha,
                                      boolean renderCaps) {

        // 渲染侧面
        renderCylinder(buffer, centerX, centerY, centerZ, radius, height, segments, red, green, blue, alpha);

        if (renderCaps) {
            // 渲染底面
            renderCircle(buffer, centerX, centerY, centerZ, radius, segments,
                    red * 0.8f, green * 0.8f, blue * 0.8f, alpha, EnumFacing.DOWN);

            // 渲染顶面
            renderCircle(buffer, centerX, centerY + height, centerZ, radius, segments,
                    red, green, blue, alpha, EnumFacing.UP);
        }
    }

    /**
     * 渲染圆形面
     *
     * @param buffer   缓冲区构建器
     * @param centerX  圆心X坐标
     * @param centerY  圆心Y坐标
     * @param centerZ  圆心Z坐标
     * @param radius   圆半径
     * @param segments 分段数
     * @param red      红色分量
     * @param green    绿色分量
     * @param blue     蓝色分量
     * @param alpha    透明度
     * @param facing   面的朝向
     */
    public static void renderCircle(BufferBuilder buffer, double centerX, double centerY, double centerZ,
                                    double radius, int segments,
                                    float red, float green, float blue, float alpha,
                                    EnumFacing facing) {

        double angleIncrement = 2 * Math.PI / segments;

        for (int i = 0; i < segments; i++) {
            double angle1 = i * angleIncrement;
            double angle2 = (i + 1) * angleIncrement;

            double cos1 = MathHelper.cos((float) angle1);
            double sin1 = MathHelper.sin((float) angle1);
            double cos2 = MathHelper.cos((float) angle2);
            double sin2 = MathHelper.sin((float) angle2);

            double x1, y1, z1, x2, y2, z2, x3, y3, z3;

            switch (facing) {
                case UP:
                    // 顶面 - 从圆心向外逆时针
                    x1 = centerX;
                    y1 = centerY;
                    z1 = centerZ;
                    x2 = centerX + radius * cos1;
                    y2 = centerY;
                    z2 = centerZ + radius * sin1;
                    x3 = centerX + radius * cos2;
                    y3 = centerY;
                    z3 = centerZ + radius * sin2;
                    break;
                case DOWN:
                    // 底面 - 从圆心向外顺时针（确保法线正确）
                    x1 = centerX;
                    y1 = centerY;
                    z1 = centerZ;
                    x2 = centerX + radius * cos2;
                    y2 = centerY;
                    z2 = centerZ + radius * sin2;
                    x3 = centerX + radius * cos1;
                    y3 = centerY;
                    z3 = centerZ + radius * sin1;
                    break;
                case NORTH:
                    x1 = centerX;
                    y1 = centerY;
                    z1 = centerZ;
                    x2 = centerX + radius * cos1;
                    y2 = centerY + radius * sin1;
                    z2 = centerZ;
                    x3 = centerX + radius * cos2;
                    y3 = centerY + radius * sin2;
                    z3 = centerZ;
                    break;
                case SOUTH:
                    x1 = centerX;
                    y1 = centerY;
                    z1 = centerZ;
                    x2 = centerX + radius * cos2;
                    y2 = centerY + radius * sin2;
                    z2 = centerZ;
                    x3 = centerX + radius * cos1;
                    y3 = centerY + radius * sin1;
                    z3 = centerZ;
                    break;
                case EAST:
                    x1 = centerX;
                    y1 = centerY;
                    z1 = centerZ;
                    x2 = centerX;
                    y2 = centerY + radius * sin1;
                    z2 = centerZ + radius * cos1;
                    x3 = centerX;
                    y3 = centerY + radius * sin2;
                    z3 = centerZ + radius * cos2;
                    break;
                case WEST:
                    x1 = centerX;
                    y1 = centerY;
                    z1 = centerZ;
                    x2 = centerX;
                    y2 = centerY + radius * sin2;
                    z2 = centerZ + radius * cos2;
                    x3 = centerX;
                    y3 = centerY + radius * sin1;
                    z3 = centerZ + radius * cos1;
                    break;
                default:
                    return;
            }

            buffer.pos(x1, y1, z1).color(red, green, blue, alpha).endVertex();
            buffer.pos(x2, y2, z2).color(red, green, blue, alpha).endVertex();
            buffer.pos(x3, y3, z3).color(red, green, blue, alpha).endVertex();
        }
    }

    /**
     * 渲染圆柱体线框
     *
     * @param buffer   缓冲区构建器
     * @param centerX  圆柱底部中心X坐标
     * @param centerY  圆柱底部中心Y坐标
     * @param centerZ  圆柱底部中心Z坐标
     * @param radius   圆柱半径
     * @param height   圆柱高度
     * @param segments 圆柱分段数
     * @param red      红色分量
     * @param green    绿色分量
     * @param blue     蓝色分量
     * @param alpha    透明度
     */
    public static void renderCylinderFrame(BufferBuilder buffer, double centerX, double centerY, double centerZ,
                                           double radius, double height, int segments,
                                           float red, float green, float blue, float alpha) {

        double angleIncrement = 2 * Math.PI / segments;
        double bottomY = centerY;
        double topY = centerY + height;

        // 绘制底面圆环
        for (int i = 0; i < segments; i++) {
            double angle1 = i * angleIncrement;
            double angle2 = (i + 1) * angleIncrement;

            double x1 = centerX + radius * MathHelper.cos((float) angle1);
            double z1 = centerZ + radius * MathHelper.sin((float) angle1);
            double x2 = centerX + radius * MathHelper.cos((float) angle2);
            double z2 = centerZ + radius * MathHelper.sin((float) angle2);

            buffer.pos(x1, bottomY, z1).color(red, green, blue, alpha).endVertex();
            buffer.pos(x2, bottomY, z2).color(red, green, blue, alpha).endVertex();
        }

        // 绘制顶面圆环
        for (int i = 0; i < segments; i++) {
            double angle1 = i * angleIncrement;
            double angle2 = (i + 1) * angleIncrement;

            double x1 = centerX + radius * MathHelper.cos((float) angle1);
            double z1 = centerZ + radius * MathHelper.sin((float) angle1);
            double x2 = centerX + radius * MathHelper.cos((float) angle2);
            double z2 = centerZ + radius * MathHelper.sin((float) angle2);

            buffer.pos(x1, topY, z1).color(red, green, blue, alpha).endVertex();
            buffer.pos(x2, topY, z2).color(red, green, blue, alpha).endVertex();
        }

        // 绘制连接上下圆的竖直线
        for (int i = 0; i < segments; i += 4) { // 每4段画一条竖直线，避免太密集
            double angle = i * angleIncrement;
            double x = centerX + radius * MathHelper.cos((float) angle);
            double z = centerZ + radius * MathHelper.sin((float) angle);

            buffer.pos(x, bottomY, z).color(red, green, blue, alpha).endVertex();
            buffer.pos(x, topY, z).color(red, green, blue, alpha).endVertex();
        }
    }

    /**
     * 渲染定向圆柱体（可以指定朝向）
     *
     * @param buffer    缓冲区构建器
     * @param centerX   圆柱起始点X坐标
     * @param centerY   圆柱起始点Y坐标
     * @param centerZ   圆柱起始点Z坐标
     * @param radius    圆柱半径
     * @param length    圆柱长度
     * @param segments  分段数
     * @param red       红色分量
     * @param green     绿色分量
     * @param blue      蓝色分量
     * @param alpha     透明度
     * @param direction 圆柱延伸方向
     */
    public static void renderOrientedCylinder(BufferBuilder buffer, double centerX, double centerY, double centerZ,
                                              double radius, double length, int segments,
                                              float red, float green, float blue, float alpha,
                                              EnumFacing direction) {

        // 计算圆柱终点
        double endX = centerX + direction.getXOffset() * length;
        double endY = centerY + direction.getYOffset() * length;
        double endZ = centerZ + direction.getZOffset() * length;

        // 计算垂直于圆柱方向的基向量
        EnumFacing.Axis axis = direction.getAxis();
        EnumFacing perpendicular1, perpendicular2;

        switch (axis) {
            case X:
                perpendicular1 = EnumFacing.UP;
                perpendicular2 = EnumFacing.NORTH;
                break;
            case Y:
                perpendicular1 = EnumFacing.EAST;
                perpendicular2 = EnumFacing.NORTH;
                break;
            case Z:
                perpendicular1 = EnumFacing.EAST;
                perpendicular2 = EnumFacing.UP;
                break;
            default:
                return;
        }

        double angleIncrement = 2 * Math.PI / segments;

        for (int i = 0; i < segments; i++) {
            double angle1 = i * angleIncrement;
            double angle2 = (i + 1) * angleIncrement;

            // 计算圆环上的点
            double dx1 = perpendicular1.getXOffset() * MathHelper.cos((float) angle1) +
                    perpendicular2.getXOffset() * MathHelper.sin((float) angle1);
            double dy1 = perpendicular1.getYOffset() * MathHelper.cos((float) angle1) +
                    perpendicular2.getYOffset() * MathHelper.sin((float) angle1);
            double dz1 = perpendicular1.getZOffset() * MathHelper.cos((float) angle1) +
                    perpendicular2.getZOffset() * MathHelper.sin((float) angle1);

            double dx2 = perpendicular1.getXOffset() * MathHelper.cos((float) angle2) +
                    perpendicular2.getXOffset() * MathHelper.sin((float) angle2);
            double dy2 = perpendicular1.getYOffset() * MathHelper.cos((float) angle2) +
                    perpendicular2.getYOffset() * MathHelper.sin((float) angle2);
            double dz2 = perpendicular1.getZOffset() * MathHelper.cos((float) angle2) +
                    perpendicular2.getZOffset() * MathHelper.sin((float) angle2);

            // 计算实际顶点位置
            double startX1 = centerX + dx1 * radius;
            double startY1 = centerY + dy1 * radius;
            double startZ1 = centerZ + dz1 * radius;
            double startX2 = centerX + dx2 * radius;
            double startY2 = centerY + dy2 * radius;
            double startZ2 = centerZ + dz2 * radius;

            double endX1 = endX + dx1 * radius;
            double endY1 = endY + dy1 * radius;
            double endZ1 = endZ + dz1 * radius;
            double endX2 = endX + dx2 * radius;
            double endY2 = endY + dy2 * radius;
            double endZ2 = endZ + dz2 * radius;

            // 绘制侧面四边形（两个三角形）
            buffer.pos(startX1, startY1, startZ1).color(red, green, blue, alpha).endVertex();
            buffer.pos(startX2, startY2, startZ2).color(red, green, blue, alpha).endVertex();
            buffer.pos(endX1, endY1, endZ1).color(red, green, blue, alpha).endVertex();

            buffer.pos(startX2, startY2, startZ2).color(red, green, blue, alpha).endVertex();
            buffer.pos(endX2, endY2, endZ2).color(red, green, blue, alpha).endVertex();
            buffer.pos(endX1, endY1, endZ1).color(red, green, blue, alpha).endVertex();
        }
    }

    //// 渲染实心球体
    //buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
    //RenderBufferHelper.renderSphere(buffer,
    //    getPos().getX() - context.cameraX() + 0.5,
    //    getPos().getY() - context.cameraY() + 0.5,
    //    getPos().getZ() - context.cameraZ() + 0.5,
    //    3.0, 16, 16, r, g, b, a);
    //Tessellator.getInstance().draw();
    //
    //// 渲染线框球体
    //buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
    //RenderBufferHelper.renderSphereFrame(buffer,
    //    getPos().getX() - context.cameraX() + 0.5,
    //    getPos().getY() - context.cameraY() + 0.5,
    //    getPos().getZ() - context.cameraZ() + 0.5,
    //    3.0, 16, r, g, b, a);
    //Tessellator.getInstance().draw();
    //
    //// 渲染实心八面体
    //buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
    //RenderBufferHelper.renderOctahedron(buffer,
    //    getPos().getX() - context.cameraX() + 0.5,
    //    getPos().getY() - context.cameraY() + 0.5,
    //    getPos().getZ() - context.cameraZ() + 0.5,
    //    2.0, r, g, b, a);
    //Tessellator.getInstance().draw();
    //
    //// 渲染线框八面体
    //buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
    //RenderBufferHelper.renderOctahedronFrame(buffer,
    //    getPos().getX() - context.cameraX() + 0.5,
    //    getPos().getY() - context.cameraY() + 0.5,
    //    getPos().getZ() - context.cameraZ() + 0.5,
    //    2.0, r, g, b, a);
    //Tessellator.getInstance().draw();
    //
    //// 渲染实心圆柱（只有侧面）
    //buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
    //RenderBufferHelper.renderCylinder(buffer,
    //    getPos().getX() - context.cameraX() + 0.5,
    //    getPos().getY() - context.cameraY() + 0.5,
    //    getPos().getZ() - context.cameraZ() + 0.5,
    //    2.0, 4.0, 16, r, g, b, a);
    //Tessellator.getInstance().draw();
    //
    //// 渲染完整圆柱（包括底面和顶面）
    //buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
    //RenderBufferHelper.renderCylinder(buffer,
    //    getPos().getX() - context.cameraX() + 0.5,
    //    getPos().getY() - context.cameraY() + 0.5,
    //    getPos().getZ() - context.cameraZ() + 0.5,
    //    2.0, 4.0, 16, r, g, b, a, true);
    //Tessellator.getInstance().draw();
    //
    //// 渲染圆柱线框
    //buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
    //RenderBufferHelper.renderCylinderFrame(buffer,
    //    getPos().getX() - context.cameraX() + 0.5,
    //    getPos().getY() - context.cameraY() + 0.5,
    //    getPos().getZ() - context.cameraZ() + 0.5,
    //    2.0, 4.0, 16, r, g, b, a);
    //Tessellator.getInstance().draw();
    //
    //// 渲染定向圆柱（沿特定方向）
    //buffer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
    //RenderBufferHelper.renderOrientedCylinder(buffer,
    //    getPos().getX() - context.cameraX() + 0.5,
    //    getPos().getY() - context.cameraY() + 0.5,
    //    getPos().getZ() - context.cameraZ() + 0.5,
    //    1.0, 3.0, 12, r, g, b, a, EnumFacing.NORTH);
    //Tessellator.getInstance().draw();
}
