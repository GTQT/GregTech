package gregtech.client.renderer.handler;

import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.util.BlockInfo;
import gregtech.client.utils.TrackedDummyWorld;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight immediate-mode ghost block renderer for the Structure Projector.
 *
 * <p>Unlike {@link MultiblockPreviewRenderer} which builds VBOs on right-click (causing
 * a one-time stutter for large structures), this renderer computes only a block position
 * list on activation (near-zero cost) and renders all blocks every frame using
 * {@link BlockRendererDispatcher#renderBlock}.</p>
 *
 * <p>Trade-off: no first-click stutter, but slightly higher per-frame cost for very large
 * structures. Suitable for projector use cases where rapid toggling is common.</p>
 *
 * <p>Only one preview (controller or projector) can be active at a time;
 * activating one will cancel the other via mutual {@code reset()} calls.</p>
 */
@SideOnly(Side.CLIENT)
public class GhostBlockRenderer {

    // Preview block scale factor and centering offset (same as controller preview)
    private static final float BLOCK_SCALE = 0.75F;
    private static final float BLOCK_OFFSET = 0.125F;

    private static BlockPos ghostPos;
    private static long ghostEndTime;
    private static int layer;
    private static boolean compareMode = false;
    @Nullable
    private static Map<String, Integer> channelValues = null;

    // Pre-computed render data: world-space positions and their states in the virtual world
    private static TrackedDummyWorld virtualWorld;
    private static final List<GhostBlock> ghostBlocks = new ArrayList<>();
    private static Set<BlockPos> surfaceBlocks;
    private static BlockPos controllerPos;
    private static int maxY;

    // Comparison mode data
    private static final List<BlockPos> missingPositions = new ArrayList<>();
    private static final List<BlockPos> wrongPositions = new ArrayList<>();

    /**
     * Per-frame rendering entry point. Called from ClientEventHandler.
     */
    public static void renderWorldLastEvent(RenderWorldLastEvent event) {
        if (ghostPos == null || ghostBlocks.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        long time = System.currentTimeMillis();
        Entity entity = mc.getRenderViewEntity();
        if (entity == null) entity = mc.player;
        if (time > ghostEndTime
                || !(mc.world.getTileEntity(ghostPos) instanceof IGregTechTileEntity)
                || entity.getDistanceSq(ghostPos) > 1024) {
            resetGhostRender();
            return;
        }

        float partialTicks = event.getPartialTicks();
        double tx = entity.lastTickPosX + ((entity.posX - entity.lastTickPosX) * partialTicks);
        double ty = entity.lastTickPosY + ((entity.posY - entity.lastTickPosY) * partialTicks);
        double tz = entity.lastTickPosZ + ((entity.posZ - entity.lastTickPosZ) * partialTicks);

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.pushMatrix();
        GlStateManager.translate(-tx, -ty, -tz);

        mc.entityRenderer.disableLightmap();

        // Semi-transparent hologram blending
        GlStateManager.enableBlend();
        GL14.glBlendColor(1.0F, 1.0F, 1.0F, 0.6F);
        GlStateManager.tryBlendFuncSeparate(
                GL11.GL_CONSTANT_ALPHA, GL11.GL_ONE_MINUS_CONSTANT_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);

        // Immediate-mode rendering: draw each ghost block every frame
        renderGhostBlocks();

        // Comparison overlay
        if (compareMode && (!missingPositions.isEmpty() || !wrongPositions.isEmpty())) {
            PreviewRenderUtils.renderComparisonOverlay(missingPositions, wrongPositions);
        }

        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        mc.entityRenderer.enableLightmap();
        GlStateManager.popMatrix();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    /**
     * Render all ghost blocks using immediate-mode per-block rendering.
     * Each block is rendered at its world-space position with 0.75x scale.
     */
    private static void renderGhostBlocks() {
        if (virtualWorld == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        BlockRendererDispatcher brd = mc.getBlockRendererDispatcher();
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder buff = tes.getBuffer();

        BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();
        PreviewRenderUtils.TargetBlockAccess targetBA =
                new PreviewRenderUtils.TargetBlockAccess(virtualWorld, BlockPos.ORIGIN);

        int finalMaxY = layer % (maxY + 1);

        for (GhostBlock ghost : ghostBlocks) {
            // Layer filter
            if (finalMaxY != 0 && ghost.localPos.getY() + 1 != finalMaxY) continue;
            // Surface filter
            if (surfaceBlocks != null && !surfaceBlocks.contains(ghost.localPos)) continue;
            // Skip controller position
            if (ghost.localPos.equals(controllerPos)) continue;

            IBlockState state = virtualWorld.getBlockState(ghost.localPos);
            if (state.getBlock() == Blocks.AIR) continue;

            targetBA.setPos(ghost.localPos);

            GlStateManager.pushMatrix();
            GlStateManager.translate(ghost.worldPos.getX(), ghost.worldPos.getY(), ghost.worldPos.getZ());
            GlStateManager.translate(BLOCK_OFFSET, BLOCK_OFFSET, BLOCK_OFFSET);
            GlStateManager.scale(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE);

            buff.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            for (BlockRenderLayer brl : BlockRenderLayer.values()) {
                if (state.getBlock().canRenderInLayer(state, brl)) {
                    ForgeHooksClient.setRenderLayer(brl);
                    brd.renderBlock(state, BlockPos.ORIGIN, targetBA, buff);
                }
            }
            tes.draw();

            GlStateManager.popMatrix();
        }

        ForgeHooksClient.setRenderLayer(oldLayer);
    }

    // ========== Activation API ==========

    /**
     * Activate ghost block preview for a controller. Near-zero cost:
     * only computes world-space position list, no VBO/geometry building.
     */
    public static void renderGhostPreview(MultiblockControllerBase controller, long durTimeMillis) {
        if (controller.getPos().equals(ghostPos)) {
            // Same controller: advance layer
            layer++;
            ghostEndTime = System.currentTimeMillis() + durTimeMillis;
            // Recompute comparison data for updated layer
            if (compareMode) {
                recomputeComparison(controller);
            }
            return;
        }

        // New controller or first activation
        resetGhostRender();
        // Cancel any active controller preview
        MultiblockPreviewRenderer.resetMultiblockRender();

        ghostPos = controller.getPos();
        ghostEndTime = System.currentTimeMillis() + durTimeMillis;
        layer = 0;

        // Get shape info
        List<MultiblockShapeInfo> shapes = channelValues != null
                ? controller.getMatchingShapes(channelValues)
                : controller.getMatchingShapes();
        if (shapes.isEmpty()) return;

        MultiblockShapeInfo shapeInfo = shapes.get(0);
        BlockInfo[][][] blocks = shapeInfo.getBlocks();

        // Build block map and virtual world
        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        maxY = 0;
        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            maxY = Math.max(maxY, aisle.length);
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                for (int z = 0; z < column.length; z++) {
                    blockMap.put(new BlockPos(x, y, z), column[z]);
                }
            }
        }

        virtualWorld = new TrackedDummyWorld();
        virtualWorld.addBlocks(blockMap);

        controllerPos = PreviewRenderUtils.findControllerInPreview(blocks, controller);
        surfaceBlocks = PreviewRenderUtils.computeSurfaceBlocks(blockMap);

        // Compute world-space positions for all blocks (near-zero cost)
        ghostBlocks.clear();
        BlockPos worldControllerPos = controller.getPos();
        for (BlockPos localPos : blockMap.keySet()) {
            BlockInfo info = blockMap.get(localPos);
            if (info.getBlockState() == null || info.getBlockState().getBlock() == Blocks.AIR) continue;

            BlockPos relPos = localPos.subtract(controllerPos);
            BlockPos transformed = PreviewRenderUtils.transformPreviewOffset(controller, relPos);
            BlockPos worldPos = worldControllerPos.add(transformed);
            ghostBlocks.add(new GhostBlock(localPos, worldPos));
        }

        // Compute comparison data
        if (compareMode) {
            recomputeComparison(controller);
        }
    }

    private static void recomputeComparison(MultiblockControllerBase controller) {
        List<MultiblockShapeInfo> shapes = channelValues != null
                ? controller.getMatchingShapes(channelValues)
                : controller.getMatchingShapes();
        if (!shapes.isEmpty()) {
            PreviewRenderUtils.computeComparisonFromController(
                    controller, shapes.get(0), missingPositions, wrongPositions);
        }
    }

    public static void resetGhostRender() {
        ghostPos = null;
        ghostEndTime = 0;
        layer = 0;
        ghostBlocks.clear();
        virtualWorld = null;
        surfaceBlocks = null;
        controllerPos = null;
        maxY = 0;
        missingPositions.clear();
        wrongPositions.clear();
    }

    public static void setCompareMode(boolean enabled) {
        compareMode = enabled;
    }

    public static void setChannelValues(@Nullable Map<String, Integer> values) {
        channelValues = values != null && !values.isEmpty() ? new HashMap<>(values) : null;
    }

    public static boolean isCompareMode() {
        return compareMode;
    }

    // ========== Internal Data ==========

    /**
     * Pre-computed ghost block data: maps a local position in the virtual world
     * to its corresponding world-space rendering position.
     */
    private static class GhostBlock {

        final BlockPos localPos;
        final BlockPos worldPos;

        GhostBlock(BlockPos localPos, BlockPos worldPos) {
            this.localPos = localPos;
            this.worldPos = worldPos;
        }
    }
}
