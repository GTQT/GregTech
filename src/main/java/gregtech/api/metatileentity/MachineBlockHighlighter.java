package gregtech.api.metatileentity;

import gregtech.api.GregTechAPI;
import gregtech.api.network.IClientExecutor;
import gregtech.api.network.IPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 机器方块高亮系统。
 * <p>
 * 当机器需要引起玩家注意时（如多方块结构缺少维护仓等），调用
 * {@link #highlight(EntityPlayer, BlockPos)} 会在该方块位置渲染线框高亮。
 * <p>
 * 高亮时长 5 秒，与 QuantumHue-Visualizer 的 ClientHighlightHandler 一致。
 */
public final class MachineBlockHighlighter {

    private static final long HIGHLIGHT_DURATION_MS = 5000;

    // ==================== 客户端高亮状态 ====================

    @SideOnly(Side.CLIENT)
    private static CopyOnWriteArrayList<HighlightEntry> activeHighlights;

    @SideOnly(Side.CLIENT)
    private static boolean rendererRegistered;

    // ==================== 服务端入口 ====================

    /**
     * 在指定位置触发高亮。
     * <p>
     * 向所有正在追踪该位置的客户端发送高亮数据包。
     *
     * @param player 触发高亮的玩家（用于获取所在维度）
     * @param pos    需要高亮的方块坐标
     */
    public static void highlight(@NotNull EntityPlayer player, @NotNull BlockPos pos) {
        if (player.world.isRemote) return;
        GregTechAPI.networkHandler.sendToAllTracking(
                new PacketMachineHighlight(pos),
                new NetworkRegistry.TargetPoint(player.dimension, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 128.0));
    }

    // ==================== 网络数据包 ====================

    public static class PacketMachineHighlight implements IPacket, IClientExecutor {

        private BlockPos pos;

        @SuppressWarnings("unused")
        public PacketMachineHighlight() {}

        public PacketMachineHighlight(@NotNull BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public void encode(PacketBuffer buf) {
            buf.writeBlockPos(pos);
        }

        @Override
        public void decode(PacketBuffer buf) {
            this.pos = buf.readBlockPos();
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void executeClient(NetHandlerPlayClient handler) {
            addClientHighlight(pos);
        }
    }

    // ==================== 客户端逻辑 ====================

    @SideOnly(Side.CLIENT)
    private static void addClientHighlight(@NotNull BlockPos pos) {
        if (activeHighlights == null) {
            activeHighlights = new CopyOnWriteArrayList<>();
        }
        if (!rendererRegistered) {
            rendererRegistered = true;
            MinecraftForge.EVENT_BUS.register(new RenderHandler());
        }
        // 清除同一位置的旧高亮，避免重复
        activeHighlights.removeIf(e -> e.pos.equals(pos));
        activeHighlights.add(new HighlightEntry(pos, System.currentTimeMillis() + HIGHLIGHT_DURATION_MS));
    }

    @SideOnly(Side.CLIENT)
    private static class HighlightEntry {
        final BlockPos pos;
        final long expiryTime;

        HighlightEntry(BlockPos pos, long expiryTime) {
            this.pos = pos;
            this.expiryTime = expiryTime;
        }
    }

    @SideOnly(Side.CLIENT)
    public static class RenderHandler {

        @SubscribeEvent
        public void onRenderWorldLast(RenderWorldLastEvent event) {
            if (activeHighlights == null || activeHighlights.isEmpty()) return;

            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null || mc.world == null) return;

            long now = System.currentTimeMillis();
            activeHighlights.removeIf(e -> now > e.expiryTime);
            if (activeHighlights.isEmpty()) return;

            float partialTicks = event.getPartialTicks();

            Entity viewEntity = mc.getRenderViewEntity();
            if (viewEntity == null) viewEntity = mc.player;
            double dx = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * partialTicks;
            double dy = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * partialTicks;
            double dz = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * partialTicks;

            GlStateManager.pushMatrix();
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.glLineWidth(2.0f);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.getBuffer();

            // 线框颜色：绿色，与 QuantumHue 中键提醒默认色一致
            float r = 0.0f, g = 1.0f, b = 0.0f, a = 0.78f;

            for (HighlightEntry entry : activeHighlights) {
                double x = entry.pos.getX() - dx;
                double y = entry.pos.getY() - dy;
                double z = entry.pos.getZ() - dz;

                drawCubeEdges(buffer, x, y, z, x + 1, y + 1, z + 1, r, g, b, a);
            }

            GlStateManager.glLineWidth(1.0f);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }

        private static void drawCubeEdges(BufferBuilder buffer, double minX, double minY, double minZ,
                                           double maxX, double maxY, double maxZ,
                                           float r, float g, float b, float a) {
            Tessellator tessellator = Tessellator.getInstance();

            // 底面 + 顶面（各 5 个顶点构成闭环）
            buffer.begin(3, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(minX, minY, minZ).color(r, g, b, a).endVertex();
            buffer.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();
            buffer.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();
            buffer.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();
            buffer.pos(minX, minY, minZ).color(r, g, b, a).endVertex();
            tessellator.draw();

            buffer.begin(3, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();
            buffer.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();
            buffer.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
            buffer.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();
            buffer.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();
            tessellator.draw();

            // 四条竖线
            buffer.begin(1, DefaultVertexFormats.POSITION_COLOR);
            buffer.pos(minX, minY, minZ).color(r, g, b, a).endVertex();
            buffer.pos(minX, maxY, minZ).color(r, g, b, a).endVertex();
            buffer.pos(maxX, minY, minZ).color(r, g, b, a).endVertex();
            buffer.pos(maxX, maxY, minZ).color(r, g, b, a).endVertex();
            buffer.pos(maxX, minY, maxZ).color(r, g, b, a).endVertex();
            buffer.pos(maxX, maxY, maxZ).color(r, g, b, a).endVertex();
            buffer.pos(minX, minY, maxZ).color(r, g, b, a).endVertex();
            buffer.pos(minX, maxY, maxZ).color(r, g, b, a).endVertex();
            tessellator.draw();
        }
    }
}
