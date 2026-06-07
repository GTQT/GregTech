package gregtech.client.renderer.handler;

import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;
import gregtech.client.renderer.godforge.util.FaceCulledRenderBlocks;
import gregtech.client.renderer.godforge.util.FaceVisibility;
import gregtech.client.utils.TrackedDummyWorld;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@SideOnly(Side.CLIENT)
public class MultiblockPreviewRenderer {

    // Preview block scale factor and centering offset
    private static final float BLOCK_SCALE = 1.0F;
    private static final float BLOCK_OFFSET = 0.0F;
    private static final FaceVisibility FULL_BLOCK_VISIBILITY = new FaceVisibility();
    // Minimum interval between VBO rebuilds to prevent stutter from rapid right-clicks
    private static final long REBUILD_COOLDOWN_MS = 500L;

    private static BlockPos mbpPos;
    private static long mbpEndTime;
    private static long lastBuildTime;
    private static int layer;
    private static boolean compareMode = false;
    @Nullable
    private static Map<String, Integer> channelValues = null;

    // VBO storage: one VBO per BlockRenderLayer
    private static final VertexBuffer[] vbos = new VertexBuffer[BlockRenderLayer.values().length];
    private static boolean vboBuilt = false;

    // Comparison mode data: world positions of missing/wrong blocks for overlay rendering
    private static final List<BlockPos> missingPositions = new ArrayList<>();
    private static final List<BlockPos> wrongPositions = new ArrayList<>();

    public static void renderWorldLastEvent(RenderWorldLastEvent event) {
        if (mbpPos != null) {
            Minecraft mc = Minecraft.getMinecraft();
            long time = System.currentTimeMillis();
            Entity entity = mc.getRenderViewEntity();
            if (entity == null) entity = mc.player;
            if (!vboBuilt || time > mbpEndTime
                    || !(mc.world.getTileEntity(mbpPos) instanceof IGregTechTileEntity)
                    || entity.getDistanceSq(mbpPos) > 1024) {
                resetMultiblockRender();
                layer = 0;
                return;
            }
            float partialTicks = event.getPartialTicks();
            double tx = entity.lastTickPosX + ((entity.posX - entity.lastTickPosX) * partialTicks);
            double ty = entity.lastTickPosY + ((entity.posY - entity.lastTickPosY) * partialTicks);
            double tz = entity.lastTickPosZ + ((entity.posZ - entity.lastTickPosZ) * partialTicks);

            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.color(1F, 1F, 1F, 1F);
            GlStateManager.pushMatrix();
            GlStateManager.translate(-tx, -ty, -tz);

            // Disable lightmap texture unit so block brightness is not affected by
            // per-vertex lightmap UVs (consistent with WorldSceneRenderer approach).
            mc.entityRenderer.disableLightmap();

            // Render VBOs for each layer (fully opaque, no blending)
            renderVBOs();

            // Render comparison overlay (colored outlines for missing/wrong blocks)
            if (compareMode && (!missingPositions.isEmpty() || !wrongPositions.isEmpty())) {
                GlStateManager.enableBlend();
                PreviewRenderUtils.renderComparisonOverlay(missingPositions, wrongPositions);
                GlStateManager.disableBlend();
            }

            GlStateManager.enableLighting();
            mc.entityRenderer.enableLightmap();
            GlStateManager.popMatrix();
            GlStateManager.color(1F, 1F, 1F, 1F);

        }
    }

    /**
     * Render the built VBOs. Uses the same vertex attribute setup as StructureVBO.
     */
    private static void renderVBOs() {
        BlockRenderLayer oldRenderLayer = MinecraftForgeClient.getRenderLayer();
        try {
            for (BlockRenderLayer renderLayer : BlockRenderLayer.values()) {
                VertexBuffer vbo = vbos[renderLayer.ordinal()];
                if (vbo == null) continue;

                ForgeHooksClient.setRenderLayer(renderLayer);

                vbo.bindBuffer();
                GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
                GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);

                // BLOCK format: stride=28 bytes
                // pos(3 float)=0, color(4 ubyte)=12, uv(2 float)=16, lightmap(2 short)=24
                GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 28, 0);
                GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 28, 12);
                GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 28, 16);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
                GlStateManager.glTexCoordPointer(2, GL11.GL_SHORT, 28, 24);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);

                vbo.drawArrays(GL11.GL_QUADS);

                GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
                GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
                vbo.unbindBuffer();
            }
        } finally {
            ForgeHooksClient.setRenderLayer(oldRenderLayer);
        }
    }

    public static void renderMultiBlockPreview(MultiblockControllerBase controller, long durTimeMillis) {
        long now = System.currentTimeMillis();
        if (!controller.getPos().equals(mbpPos)) {
            layer = 0;
        } else {
            // Throttle: skip rebuild if last build was too recent (prevents stutter on rapid clicks)
            if (now - lastBuildTime < REBUILD_COOLDOWN_MS) {
                mbpEndTime = now + durTimeMillis;
                return;
            }
            layer++;
        }
        rebuildMultiblockPreview(controller, durTimeMillis);
    }

    public static void refreshCurrentPreview(MultiblockControllerBase controller) {
        long remainingTime = mbpEndTime - System.currentTimeMillis();
        if (controller == null || mbpPos == null || !vboBuilt || remainingTime <= 0 ||
                !controller.getPos().equals(mbpPos)) {
            return;
        }
        rebuildMultiblockPreview(controller, remainingTime);
    }

    private static void rebuildMultiblockPreview(MultiblockControllerBase controller, long durTimeMillis) {
        resetMultiblockRender();
        // Cancel any active ghost block preview (mutual exclusion)
        GhostBlockRenderer.resetGhostRender();
        mbpPos = controller.getPos();
        mbpEndTime = System.currentTimeMillis() + durTimeMillis;
        lastBuildTime = System.currentTimeMillis();

        // Check if a specific piece is requested via STRUCTURE_PIECE channel
        int pieceIndex = channelValues != null
                ? channelValues.getOrDefault(GTStructureChannels.STRUCTURE_PIECE.getName(), 0)
                : 0;

        if (pieceIndex > 0) {
            // Build VBO for a specific piece from the MultiPiecePattern
            buildPieceVBO(controller, pieceIndex);
        } else {
            // Default: build VBO for the main pattern (backward compatible)
            List<MultiblockShapeInfo> shapes = channelValues != null
                    ? controller.getMatchingShapes(channelValues)
                    : controller.getMatchingShapes();
            if (!shapes.isEmpty()) {
                buildControllerVBO(controller, shapes.get(0), layer);
                // Compute comparison data if compare mode is active
                if (compareMode) {
                    PreviewRenderUtils.computeComparisonFromController(
                            controller, shapes.get(0), missingPositions, wrongPositions);
                }
            }
        }
    }

    public static void renderMultiBlockPreview(MultiblockControllerBase controller, BlockPos pos, long durTimeMillis) {
        long now = System.currentTimeMillis();
        if (!controller.getPos().equals(mbpPos)) {
            layer = 0;
        } else {
            if (now - lastBuildTime < REBUILD_COOLDOWN_MS) {
                mbpEndTime = now + durTimeMillis;
                return;
            }
            layer++;
        }
        resetMultiblockRender();
        mbpPos = controller.getPos();
        mbpEndTime = System.currentTimeMillis() + durTimeMillis;
        List<MultiblockShapeInfo> shapes = channelValues != null
                ? controller.getMatchingShapes(channelValues)
                : controller.getMatchingShapes();
        if (!shapes.isEmpty()) buildControllerVBO(controller, shapes.get(0), layer, pos);
    }

    public static void renderMultiBlockPreview(MultiblockControllerBase controller, BlockPos pos, int layer,
                                               long durTimeMillis) {
        resetMultiblockRender();
        mbpPos = controller.getPos();
        mbpEndTime = System.currentTimeMillis() + durTimeMillis;
        List<MultiblockShapeInfo> shapes = channelValues != null
                ? controller.getMatchingShapes(channelValues)
                : controller.getMatchingShapes();
        if (!shapes.isEmpty()) buildControllerVBO(controller, shapes.get(0), layer, pos);
    }

    public static void resetMultiblockRender() {
        mbpPos = null;
        mbpEndTime = 0;
        deleteVBOs();
        missingPositions.clear();
        wrongPositions.clear();
    }

    private static void deleteVBOs() {
        for (int i = 0; i < vbos.length; i++) {
            if (vbos[i] != null) {
                vbos[i].deleteGlBuffers();
                vbos[i] = null;
            }
        }
        vboBuilt = false;
    }

    /**
     * Enable or disable comparison mode.
     * In comparison mode, only missing and incorrect blocks are rendered with color tinting.
     * Correctly placed blocks are skipped.
     */
    public static void setCompareMode(boolean enabled) {
        compareMode = enabled;
    }

    /**
     * Set the channel values to use when rendering the next multiblock preview.
     * These values are passed to {@code getMatchingShapes(channelValues)} to determine
     * which tier/size variant to preview.
     *
     * @param values the channel values map (null = use default/all variants)
     */
    public static void setChannelValues(@Nullable Map<String, Integer> values) {
        channelValues = values != null && !values.isEmpty() ? new HashMap<>(values) : null;
    }

    public static boolean isCompareMode() {
        return compareMode;
    }

    /**
     * Compute comparison data by comparing expected structure against real world blocks.
     * <p>
     * Delegates to {@link PreviewRenderUtils#computeComparisonData} with this renderer's
     * comparison lists. Retained for backward compatibility with external callers.
     *
     * @param expectedBlocks map of world positions -> expected block states
     * @param world          the real world to compare against
     */
    public static void computeComparisonData(Map<BlockPos, IBlockState> expectedBlocks,
                                             net.minecraft.world.World world) {
        if (!compareMode) {
            missingPositions.clear();
            wrongPositions.clear();
            return;
        }
        PreviewRenderUtils.computeComparisonData(expectedBlocks, world, missingPositions, wrongPositions);
    }

    // ========== VBO Build Methods ==========

    /**
     * Build VBO for a specific piece from the MultiPiecePattern at its world-space offset position.
     */
    private static void buildPieceVBO(MultiblockControllerBase controller, int pieceIndex) {
        MultiblockShapeInfo shapeInfo = controller.getMatchingShapeForPiece(pieceIndex, channelValues);
        if (shapeInfo == null) return;

        MultiPiecePattern multiPiece = controller.getMultiPiecePattern();
        if (multiPiece == null) return;

        List<StructurePiece> pieces = multiPiece.getPieceList();
        if (pieceIndex < 1 || pieceIndex > pieces.size()) return;
        StructurePiece piece = pieces.get(pieceIndex - 1);

        // Compute the piece's center position in world space
        BlockPos pieceCenterPos = piece.getCenterPos(
                controller.getPos(),
                controller.getFrontFacingForStructure(),
                controller.getUpwardsFacing());

        BlockInfo[][][] blocks = shapeInfo.getBlocks();
        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        int maxY = 0;
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

        TrackedDummyWorld world = new TrackedDummyWorld();
        world.addBlocks(blockMap);
        int finalMaxY = layer % (maxY + 1);
        Predicate<BlockPos> renderFilter = pos -> finalMaxY == 0 || pos.getY() + 1 == finalMaxY;
        world.setRenderFilter(renderFilter);

        // Use the piece's own template for coordinate transformation
        gregtech.api.pattern.BlockPatternTemplate pieceTemplate = piece.getTemplate();
        RelativeDirection[] structureDir = pieceTemplate.getStructureDir();
        BlockPatternTemplate.CenterOffset centerOffset = pieceTemplate.getCenterOffset();
        BlockPos pieceCenterInLocal = new BlockPos(centerOffset.x(), centerOffset.y(), centerOffset.minZ());

        FaceCulledRenderBlocks renderer = new FaceCulledRenderBlocks(world);
        PreviewRenderUtils.OffsetBlockAccess mteAccess = new PreviewRenderUtils.OffsetBlockAccess(world);
        BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();

        try {
            for (BlockRenderLayer renderLayer : BlockRenderLayer.values()) {
                ForgeHooksClient.setRenderLayer(renderLayer);

                BufferBuilder buffer = Tessellator.getInstance().getBuffer();
                boolean drawing = false;
                try {
                    buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                    drawing = true;

                    for (BlockPos pos : blockMap.keySet()) {
                        if (!renderFilter.test(pos)) continue;
                        IBlockState state = world.getBlockState(pos);
                        if (state.getBlock() == Blocks.AIR) continue;
                        if (!state.getBlock().canRenderInLayer(state, renderLayer)) continue;

                        // Compute world-space position for this block
                        BlockPos tPos = PreviewRenderUtils.transformPieceOffset(
                                pos.subtract(pieceCenterInLocal), structureDir,
                                controller.getFrontFacingForStructure(),
                                controller.getUpwardsFacing(),
                                controller.isFlipped());
                        BlockPos worldPos = pieceCenterPos.add(tPos);

                        renderPreviewBlock(renderer, mteAccess, state, pos, worldPos, buffer);
                    }

                    buffer.finishDrawing();
                    drawing = false;
                    VertexBuffer vbo = new VertexBuffer(DefaultVertexFormats.BLOCK);
                    vbo.bufferData(buffer.getByteBuffer());
                    vbos[renderLayer.ordinal()] = vbo;
                } finally {
                    if (drawing) {
                        finishDrawingQuietly(buffer);
                    }
                    buffer.reset();
                }
            }
        } finally {
            ForgeHooksClient.setRenderLayer(oldLayer);
        }

        vboBuilt = true;
    }

    /**
     * Build VBO for the main controller pattern preview.
     */
    private static void buildControllerVBO(MultiblockControllerBase controllerBase, MultiblockShapeInfo shapeInfo,
                                           int layer) {
        buildControllerVBO(controllerBase, shapeInfo, layer, controllerBase.getPos());
    }

    /**
     * Build VBO for the main controller pattern preview at a specific target position.
     * <p>
     * The merged preview array produced by
     * {@link MultiblockControllerBase#buildMultiPieceShapes} is laid out in world
     * coordinates: each piece's local y (string index) is offset by the cumulative
     * aisle depth of all preceding pieces, so the merged y already encodes the
     * world-space vertical position of every block. The blockMap / dummy world use
     * these merged world coordinates so neighbor lookups and block-state lookups
     * remain correct, and {@link PreviewRenderUtils#transformPreviewOffset} reads
     * the merged y directly when producing the world-space offset.
     */
    private static void buildControllerVBO(MultiblockControllerBase controllerBase, MultiblockShapeInfo shapeInfo,
                                           int layer, BlockPos targetPos) {
        BlockInfo[][][] blocks = shapeInfo.getBlocks();
        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        int maxY = 0;
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

        BlockPos controllerPos = PreviewRenderUtils.findControllerInPreview(blocks, controllerBase);
        TrackedDummyWorld world = new TrackedDummyWorld();
        world.addBlocks(blockMap);
        int finalMaxY = layer % (maxY + 1);
        Predicate<BlockPos> renderFilter = pos -> finalMaxY == 0 || pos.getY() + 1 == finalMaxY;
        world.setRenderFilter(renderFilter);

        FaceCulledRenderBlocks renderer = new FaceCulledRenderBlocks(world);
        PreviewRenderUtils.OffsetBlockAccess mteAccess = new PreviewRenderUtils.OffsetBlockAccess(world);
        BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();

        try {
            for (BlockRenderLayer renderLayer : BlockRenderLayer.values()) {
                ForgeHooksClient.setRenderLayer(renderLayer);

                BufferBuilder buffer = Tessellator.getInstance().getBuffer();
                boolean drawing = false;
                try {
                    buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                    drawing = true;

                    for (BlockPos pos : blockMap.keySet()) {
                        if (!renderFilter.test(pos)) continue;
                        if (pos.equals(controllerPos)) continue;
                        IBlockState state = world.getBlockState(pos);
                        if (state.getBlock() == Blocks.AIR) continue;
                        if (!state.getBlock().canRenderInLayer(state, renderLayer)) continue;

                        // Compute world-space position for this block. The merged
                        // array's y already includes the piece's vertical offset
                        // from the controller, so transformPreviewOffset can read
                        // it directly as the world y component.
                        BlockPos tPos = PreviewRenderUtils.transformPreviewOffset(
                                controllerBase, pos.subtract(controllerPos));
                        BlockPos worldPos = targetPos.add(tPos);

                        renderPreviewBlock(renderer, mteAccess, state, pos, worldPos, buffer);
                    }

                    buffer.finishDrawing();
                    drawing = false;
                    VertexBuffer vbo = new VertexBuffer(DefaultVertexFormats.BLOCK);
                    vbo.bufferData(buffer.getByteBuffer());
                    vbos[renderLayer.ordinal()] = vbo;
                } finally {
                    if (drawing) {
                        finishDrawingQuietly(buffer);
                    }
                    buffer.reset();
                }
            }
        } finally {
            ForgeHooksClient.setRenderLayer(oldLayer);
        }

        vboBuilt = true;
    }

    private static void renderPreviewBlock(FaceCulledRenderBlocks renderer,
                                           PreviewRenderUtils.OffsetBlockAccess mteAccess,
                                           IBlockState state,
                                           BlockPos localPos,
                                           BlockPos worldPos,
                                           BufferBuilder buffer) {
        if (state.getBlock().getRenderType(state) == MetaTileEntityRenderer.BLOCK_RENDER_TYPE) {
            mteAccess.setPos(localPos, worldPos, true);
            MetaTileEntityRenderer.INSTANCE.renderBlock(mteAccess, worldPos, state, buffer);
            return;
        }

        renderer.renderBlockScaled(state, localPos, worldPos, BLOCK_SCALE, BLOCK_OFFSET,
                FULL_BLOCK_VISIBILITY, buffer);
    }

    private static void finishDrawingQuietly(BufferBuilder buffer) {
        try {
            buffer.finishDrawing();
        } catch (IllegalStateException ignored) {
            // BufferBuilder.reset() does not clear isDrawing in 1.12; finishDrawing does.
        }
    }
}
