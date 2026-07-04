package gregtech.client.renderer.handler;

import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.PieceTemplate;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.MultiPiecePreviewAssembler;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.GTUtility;
import gregtech.api.util.GTLog;
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
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * VBO-cached ghost block renderer for the Structure Projector.
 *
 * <p>Builds one {@link VertexBuffer} per {@link BlockRenderLayer} on activation, then
 * draws the cached VBOs every frame at near-zero per-frame cost. This avoids the
 * per-frame iteration over every ghost block that the previous immediate-mode
 * approach suffered from for very large multiblocks (e.g. the Forge of the Gods,
 * whose single pieces can exceed 300&thinsp;000 blocks).</p>
 *
 * <p>Supports the {@code STRUCTURE_PIECE} channel: when a piece index &gt; 0 is
 * selected, only that single piece is projected instead of the entire merged
 * structure, keeping both build time and VBO size manageable for multi-piece
 * multiblocks.</p>
 *
 * <p>Only one preview (controller or projector) can be active at a time;
 * activating one will cancel the other via mutual {@code reset()} calls.</p>
 */
@SideOnly(Side.CLIENT)
public class GhostBlockRenderer {

    private static final float BLOCK_SCALE = 0.75F;
    private static final float BLOCK_OFFSET = 0.125F;
    private static final int GHOST_TINT = 0x9EDFFF;
    private static final IVertexOperation[] GHOST_MTE_PIPELINE = new IVertexOperation[] {
            new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(GHOST_TINT))
    };
    private static final long REBUILD_COOLDOWN_MS = 500L;

    // VBO storage: one VBO per BlockRenderLayer
    private static final VertexBuffer[] vbos = new VertexBuffer[BlockRenderLayer.values().length];
    private static boolean vboBuilt = false;

    private static BlockPos ghostPos;
    private static long ghostEndTime;
    private static long lastBuildTime;
    private static int layer;
    private static boolean renderEntryLogged;
    private static boolean compareMode = false;
    private static boolean noHatch = false;
    @Nullable
    private static Map<String, Integer> channelValues = null;

    // Comparison mode data
    private static final List<BlockPos> missingPositions = new ArrayList<>();
    private static final List<BlockPos> wrongPositions = new ArrayList<>();

    // ========== Per-frame rendering ==========

    public static void renderWorldLastEvent(RenderWorldLastEvent event) {
        if (ghostPos == null || !vboBuilt) return;

        Minecraft mc = Minecraft.getMinecraft();
        long time = System.currentTimeMillis();
        Entity entity = mc.getRenderViewEntity();
        if (entity == null) entity = mc.player;
        String resetReason = null;
        if (time > ghostEndTime) {
            resetReason = "expired";
        } else if (!(mc.world.getTileEntity(ghostPos) instanceof IGregTechTileEntity)) {
            resetReason = "controller-missing";
        } else if (entity.getDistanceSq(ghostPos) > 1024) {
            resetReason = "player-too-far";
        }
        if (resetReason != null) {
            GTLog.logger.info("[StructureProjector] preview cleared controller={} reason={} distanceSq={}",
                    ghostPos, resetReason, entity.getDistanceSq(ghostPos));
            resetGhostRender();
            return;
        }

        if (!renderEntryLogged) {
            renderEntryLogged = true;
            GTLog.logger.info("[StructureProjector] rendering preview controller={} layer={} compare={} " +
                            "vbos={} missing={} wrong={}",
                    ghostPos, layer, compareMode, countVBOs(), missingPositions.size(), wrongPositions.size());
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

        renderVBOs();

        // Comparison overlay (immediate-mode colored boxes, cheap)
        if (compareMode && (!missingPositions.isEmpty() || !wrongPositions.isEmpty())) {
            PreviewRenderUtils.renderComparisonOverlay(missingPositions, wrongPositions);
        }

        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        mc.entityRenderer.enableLightmap();
        GlStateManager.popMatrix();
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private static void renderVBOs() {
        BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();
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
            ForgeHooksClient.setRenderLayer(oldLayer);
        }
    }

    // ========== Activation API ==========

    public static void renderGhostPreview(MultiblockControllerBase controller, long durTimeMillis) {
        long now = System.currentTimeMillis();
        if (controller.getPos().equals(ghostPos)) {
            // Throttle: skip rebuild if last build was too recent (prevents stutter on rapid clicks)
            if (now - lastBuildTime < REBUILD_COOLDOWN_MS) {
                ghostEndTime = now + durTimeMillis;
                return;
            }
            layer++;
        } else {
            layer = 0;
        }
        rebuildGhostPreview(controller, durTimeMillis, layer);
    }

    public static void refreshCurrentPreview(MultiblockControllerBase controller) {
        long remainingTime = ghostEndTime - System.currentTimeMillis();
        if (controller == null || ghostPos == null || !vboBuilt || remainingTime <= 0 ||
                !controller.getPos().equals(ghostPos)) {
            return;
        }
        rebuildGhostPreview(controller, remainingTime, layer);
    }

    private static void rebuildGhostPreview(MultiblockControllerBase controller, long durTimeMillis,
                                            int requestedLayer) {
        long previewStart = System.nanoTime();
        resetGhostRender();
        // Cancel any active controller preview (mutual exclusion)
        MultiblockPreviewRenderer.resetMultiblockRender();

        ghostPos = controller.getPos();
        ghostEndTime = System.currentTimeMillis() + durTimeMillis;
        lastBuildTime = System.currentTimeMillis();
        layer = requestedLayer;

        // V3 §13: ghost renderer consumes the compiled MultiPiecePattern
        // unconditionally. Single-piece patterns compile to a one-piece
        // MultiPiecePattern, so the multi-piece preview assembler handles them
        // without a separate single-template branch here.
        int pieceIndex = channelValues != null
                ? channelValues.getOrDefault(GTStructureChannels.STRUCTURE_PIECE.getName(), 0)
                : 0;

        boolean built = false;
        try {
            if (pieceIndex > 0) {
                built = buildPieceVBO(controller, pieceIndex);
            } else {
                built = buildFullVBO(controller);
            }
        } catch (RuntimeException e) {
            GTLog.logger.error("[StructureProjector] preview build failed controller={} piece={} layer={}",
                    controller.getPos(), pieceIndex, layer, e);
        }

        if (built) {
            long totalMillis = (System.nanoTime() - previewStart) / 1_000_000L;
            GTLog.logger.info(
                    "[StructureProjector] preview ready controller={} pos={} piece={} layer={} compare={} noHatch={} " +
                            "vbos={} missing={} wrong={} totalMs={}",
                    controller.getMetaName(), controller.getPos(), pieceIndex, layer, compareMode, noHatch,
                    countVBOs(), missingPositions.size(), wrongPositions.size(), totalMillis);
        } else {
            GTLog.logger.warn("[StructureProjector] preview unavailable controller={} pos={} piece={} layer={} " +
                            "channels={}",
                    controller.getMetaName(), controller.getPos(), pieceIndex, layer, channelValues);
            resetGhostRender();
        }
    }

    // ========== VBO Build Methods ==========

    /**
     * Build VBO for a single piece from the MultiPiecePattern, projected at its
     * world-space offset position. This keeps both build time and VBO size
     * manageable for large multi-piece multiblocks like the Forge of the Gods.
     */
    private static boolean buildPieceVBO(MultiblockControllerBase controller, int pieceIndex) {
        MultiPiecePattern multiPiece = controller.getStructureDefinition().getCompiledPattern();
        if (multiPiece == null) {
            GTLog.logger.warn("[StructureProjector] compiled pattern missing controller={} piece={} " +
                            "channels={}",
                    controller.getMetaName(), pieceIndex, channelValues);
            return false;
        }
        MultiPiecePreviewAssembler.Result preview = getMultiPiecePreview(controller, pieceIndex);
        MultiPiecePreviewAssembler.PieceResult piecePreview = preview.getPiece(pieceIndex);
        if (piecePreview == null) {
            GTLog.logger.warn("[StructureProjector] piece preview missing controller={} piece={} channels={} " +
                            "noHatch={}",
                    controller.getMetaName(), pieceIndex, channelValues, noHatch);
            return false;
        }
        MultiblockShapeInfo shapeInfo = piecePreview.getShape();
        StructurePiece piece = multiPiece.getToolingPiece(pieceIndex);
        if (piece == null) {
            GTLog.logger.warn("[StructureProjector] tooling piece missing controller={} piece={}",
                    controller.getMetaName(), pieceIndex);
            return false;
        }

        BlockPos pieceCenterWorld = MultiPiecePreviewAssembler.resolveWorldPieceCenter(
                multiPiece, pieceIndex, piecePreview.getPrior(),
                controller.getPos(), StructureOrientation.fromController(controller), controller);

        PieceTemplate pieceTemplate = piece.getTemplate();
        RelativeDirection[] structureDir = pieceTemplate.getStructureDir();
        BlockPos pieceCenterLocal = piecePreview.getCenter();

        return buildVBOInternal(controller, shapeInfo, pieceCenterLocal, pieceCenterWorld,
                structureDir, true);
    }

    /**
     * Build VBO for the full merged structure (all active pieces combined).
     * Used when no STRUCTURE_PIECE channel value is set.
     */
    private static boolean buildFullVBO(MultiblockControllerBase controller) {
        MultiPiecePattern multiPiece = controller.getStructureDefinition().getCompiledPattern();
        GTLog.logger.info("[StructureProjector] full preview path controller={} pieces={} channels={} " +
                        "noHatch={}",
                controller.getMetaName(),
                multiPiece == null ? 0 : multiPiece.getToolingPieceCount(),
                channelValues, noHatch);
        if (multiPiece == null) {
            GTLog.logger.warn("[StructureProjector] compiled pattern missing controller={} channels={}",
                    controller.getMetaName(), channelValues);
            return false;
        }
        // V3 §13: ghost renderer consumes the compiled MultiPiecePattern via the
        // multi-piece preview assembler for both single-piece and multi-piece
        // structures. Single-piece patterns compile to a one-piece pattern, so
        // the fast-path lives inside the assembler, not as a bypass here.
        MultiPiecePreviewAssembler.Result preview = getMultiPiecePreview(
                controller, MultiPiecePreviewAssembler.ALL_TOOLING_PIECES);
        if (preview == null || preview.isEmpty()) {
            GTLog.logger.warn("[StructureProjector] no multi-piece preview controller={} channels={} " +
                            "noHatch={}",
                    controller.getMetaName(), channelValues, noHatch);
            return false;
        }
        return buildMultiPieceVBO(controller, multiPiece, preview);
    }

    private static MultiPiecePreviewAssembler.Result getMultiPiecePreview(
            MultiblockControllerBase controller, int toolingPieceIndex) {
        return controller.getOrCreateStructureRuntime().previewMultiPiece(
                StructureOperationRequest.previewMultiPiece(
                        channelValues, controller, noHatch, toolingPieceIndex));
    }

    private static boolean buildMultiPieceVBO(MultiblockControllerBase controller,
                                               MultiPiecePattern pattern,
                                               MultiPiecePreviewAssembler.Result preview) {
        Map<BlockPos, BlockInfo> worldBlocks = new HashMap<>();
        Map<BlockPos, Integer> worldLayerCoordinates = new HashMap<>();
        StructureOrientation orientation = StructureOrientation.fromController(controller);
        int activePieces = 0;

        for (int pieceIndex = 1; pieceIndex <= pattern.getToolingPieceCount(); pieceIndex++) {
            StructurePiece piece = pattern.getToolingPiece(pieceIndex);
            if (piece == null) continue;

            MultiPiecePreviewAssembler.PieceResult piecePreview = preview.getPiece(pieceIndex);
            BlockInfo[][][] blocks = piecePreview.getShape().getBlocks();
            BlockPos pieceCenterLocal = piecePreview.getCenter();
            BlockPos pieceCenterWorld = MultiPiecePreviewAssembler.resolveWorldPieceCenter(
                    pattern, pieceIndex, piecePreview.getPrior(), controller.getPos(),
                    orientation, controller);
            RelativeDirection[] structureDir = piece.getTemplate().getStructureDir();
            boolean pieceAdded = false;

            for (int x = 0; x < blocks.length; x++) {
                for (int y = 0; y < blocks[x].length; y++) {
                    for (int z = 0; z < blocks[x][y].length; z++) {
                        BlockInfo info = blocks[x][y][z];
                        if (!isRenderable(info)) continue;

                        BlockPos piecePos = new BlockPos(x, y, z);
                        BlockPos pieceRelative = piecePos.subtract(pieceCenterLocal);
                        BlockPos transformed = PreviewRenderUtils.transformPieceOffset(
                                pieceRelative, structureDir, orientation.getStructureFront(),
                                orientation.getUp(), orientation.isFlipped());
                        BlockPos worldPos = pieceCenterWorld.add(transformed);

                        if (!worldBlocks.containsKey(worldPos)) {
                            worldBlocks.put(worldPos, info);
                            worldLayerCoordinates.put(worldPos, pieceRelative.getY());
                        }
                        pieceAdded = true;
                    }
                }
            }
            if (pieceAdded) activePieces++;
        }

        GTLog.logger.info("[StructureProjector] merged preview controller={} activePieces={} blocks={}",
                controller.getMetaName(), activePieces, worldBlocks.size());

        BlockPos virtualOrigin = getMinimumPosition(worldBlocks);
        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        Map<BlockPos, BlockPos> localToWorld = new HashMap<>();
        Map<BlockPos, Integer> layerCoordinates = new HashMap<>();
        for (Map.Entry<BlockPos, BlockInfo> entry : worldBlocks.entrySet()) {
            BlockPos worldPos = entry.getKey();
            BlockPos virtualPos = worldPos.subtract(virtualOrigin);
            blockMap.put(virtualPos, entry.getValue());
            localToWorld.put(virtualPos, worldPos);
            layerCoordinates.put(virtualPos, worldLayerCoordinates.get(worldPos));
        }
        BlockPos controllerVirtualPos = controller.getPos().subtract(virtualOrigin);
        return buildMappedVBO(
                controller, blockMap, localToWorld, layerCoordinates, controllerVirtualPos, false);
    }

    private static BlockPos getMinimumPosition(Map<BlockPos, BlockInfo> blocks) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (BlockPos pos : blocks.keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
        }
        return blocks.isEmpty() ? BlockPos.ORIGIN : new BlockPos(minX, minY, minZ);
    }

    /**
     * Shared VBO build logic for both piece and full-structure modes.
     *
     * <p>Face culling is performed per-block during VBO construction via
     * {@link PreviewRenderUtils#computeFaceVisibility}, replacing the separate
     * {@code computeSurfaceBlocks} pass. This eliminates the O(n&times;6) HashMap
     * lookups that previously ran on every activation, and bakes only visible
     * faces into the VBO so per-frame draw cost is minimal.</p>
     *
     * @param controller         the multiblock controller
     * @param shapeInfo          the shape to project
     * @param refLocalPos        the reference position in local coords (controller for full mode,
     *                           piece center for piece mode)
     * @param refWorldPos        the reference position in world coords
     * @param pieceStructureDir  the piece's structure directions (null for full mode)
     * @param isPieceMode        true if projecting a single piece
     */
    private static boolean buildVBOInternal(MultiblockControllerBase controller,
                                            MultiblockShapeInfo shapeInfo,
                                            BlockPos refLocalPos,
                                            BlockPos refWorldPos,
                                            @Nullable RelativeDirection[] pieceStructureDir,
                                            boolean isPieceMode) {
        BlockInfo[][][] blocks = shapeInfo.getBlocks();

        Map<BlockPos, BlockInfo> blockMap = new HashMap<>();
        Map<BlockPos, BlockPos> localToWorld = new HashMap<>();
        Map<BlockPos, Integer> layerCoordinates = new HashMap<>();
        for (int x = 0; x < blocks.length; x++) {
            for (int y = 0; y < blocks[x].length; y++) {
                for (int z = 0; z < blocks[x][y].length; z++) {
                    BlockInfo info = blocks[x][y][z];
                    if (!isRenderable(info)) continue;

                    BlockPos pos = new BlockPos(x, y, z);
                    BlockPos worldPos;
                    if (isPieceMode) {
                        BlockPos transformed = PreviewRenderUtils.transformPieceOffset(
                                pos.subtract(refLocalPos), pieceStructureDir,
                                controller.getFrontFacingForStructure(), controller.getUpwardsFacing(),
                                controller.isFlipped());
                        worldPos = refWorldPos.add(transformed);
                    } else {
                        BlockPos transformed = PreviewRenderUtils.transformPreviewOffset(
                                controller, pos.subtract(refLocalPos));
                        worldPos = refWorldPos.add(transformed);
                    }
                    blockMap.put(pos, info);
                    localToWorld.put(pos, worldPos);
                    layerCoordinates.put(pos, y);
                }
            }
        }

        return buildMappedVBO(
                controller, blockMap, localToWorld, layerCoordinates,
                isPieceMode ? null : refLocalPos, isPieceMode);
    }

    private static boolean buildMappedVBO(MultiblockControllerBase controller,
                                          Map<BlockPos, BlockInfo> blockMap,
                                          Map<BlockPos, BlockPos> localToWorld,
                                          Map<BlockPos, Integer> layerCoordinates,
                                          @Nullable BlockPos controllerLocalPos,
                                          boolean isPieceMode) {
        int nonAirBlocks = blockMap.size();
        if (nonAirBlocks == 0) {
            GTLog.logger.warn("[StructureProjector] preview contains no blocks controller={} pieceMode={}",
                    controller.getMetaName(), isPieceMode);
            return false;
        }

        int minLayer = Integer.MAX_VALUE;
        int maxLayer = Integer.MIN_VALUE;
        for (int coordinate : layerCoordinates.values()) {
            minLayer = Math.min(minLayer, coordinate);
            maxLayer = Math.max(maxLayer, coordinate);
        }
        int layerCount = maxLayer - minLayer + 1;
        int selectedLayer = layer % (layerCount + 1);
        boolean isLayerMode = selectedLayer != 0;
        int targetLayer = minLayer + selectedLayer - 1;
        Predicate<BlockPos> renderFilter = pos -> !isLayerMode ||
                layerCoordinates.getOrDefault(pos, Integer.MIN_VALUE) == targetLayer;

        TrackedDummyWorld world = new TrackedDummyWorld();
        world.addBlocks(blockMap);
        world.setRenderFilter(renderFilter);

        Map<BlockPos, IBlockState> expectedBlocks = compareMode ? new HashMap<>() : null;
        int minWorldX = Integer.MAX_VALUE;
        int minWorldY = Integer.MAX_VALUE;
        int minWorldZ = Integer.MAX_VALUE;
        int maxWorldX = Integer.MIN_VALUE;
        int maxWorldY = Integer.MIN_VALUE;
        int maxWorldZ = Integer.MIN_VALUE;

        for (BlockPos pos : blockMap.keySet()) {
            BlockPos worldPos = localToWorld.get(pos);
            IBlockState state = world.getBlockState(pos);
            if (state.getBlock() != Blocks.AIR) {
                minWorldX = Math.min(minWorldX, worldPos.getX());
                minWorldY = Math.min(minWorldY, worldPos.getY());
                minWorldZ = Math.min(minWorldZ, worldPos.getZ());
                maxWorldX = Math.max(maxWorldX, worldPos.getX());
                maxWorldY = Math.max(maxWorldY, worldPos.getY());
                maxWorldZ = Math.max(maxWorldZ, worldPos.getZ());
                if (expectedBlocks != null) {
                    expectedBlocks.put(worldPos, state);
                }
            }
        }

        // Build VBOs per render layer
        FaceCulledRenderBlocks renderer = new FaceCulledRenderBlocks(world);
        PreviewRenderUtils.OffsetBlockAccess mteAccess = new PreviewRenderUtils.OffsetBlockAccess(world);
        BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();
        int totalVertices = 0;

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
                        // Skip controller block in full mode (it's the real block, not a ghost)
                        if (!isPieceMode && pos.equals(controllerLocalPos)) continue;
                        IBlockState state = world.getBlockState(pos);
                        if (state.getBlock() == Blocks.AIR) continue;
                        if (!state.getBlock().canRenderInLayer(state, renderLayer)) continue;

                        BlockPos worldPos = localToWorld.get(pos);
                        renderGhostBlockIntoBuffer(renderer, mteAccess, state, pos, worldPos,
                                blockMap, renderFilter, buffer);
                    }

                    buffer.finishDrawing();
                    drawing = false;

                    if (buffer.getVertexCount() > 0) {
                        totalVertices += buffer.getVertexCount();
                        VertexBuffer vbo = new VertexBuffer(DefaultVertexFormats.BLOCK);
                        vbo.bufferData(buffer.getByteBuffer());
                        vbos[renderLayer.ordinal()] = vbo;
                    }
                } finally {
                    if (drawing) finishDrawingQuietly(buffer);
                    buffer.reset();
                }
            }
        } finally {
            ForgeHooksClient.setRenderLayer(oldLayer);
        }

        vboBuilt = totalVertices > 0;

        if (!vboBuilt) {
            GTLog.logger.warn("[StructureProjector] preview generated no vertices controller={} pieceMode={} " +
                            "nonAirBlocks={} layer={} maxLayer={}",
                    controller.getMetaName(), isPieceMode, nonAirBlocks, layer, layerCount);
            return false;
        }

        if (expectedBlocks != null) {
            World realWorld = Minecraft.getMinecraft().world;
            PreviewRenderUtils.computeComparisonData(expectedBlocks, realWorld,
                    missingPositions, wrongPositions);
        }

        GTLog.logger.info("[StructureProjector] preview geometry controller={} pieceMode={} nonAirBlocks={} " +
                        "vertices={} layer={} maxLayer={} worldBounds=({}, {}, {})..({}, {}, {})",
                controller.getMetaName(), isPieceMode, nonAirBlocks, totalVertices, layer, layerCount,
                minWorldX, minWorldY, minWorldZ, maxWorldX, maxWorldY, maxWorldZ);
        return true;
    }

    private static boolean isRenderable(@Nullable BlockInfo info) {
        return info != null && info.getBlockState() != null &&
                info.getBlockState().getBlock() != Blocks.AIR;
    }

    /**
     * Render a single ghost block into the given buffer. MTE blocks use
     * {@link MetaTileEntityRenderer} with the ghost tint pipeline; regular blocks
     * use {@link FaceCulledRenderBlocks#renderBlockScaled} with per-face visibility
     * culling so only visible faces contribute vertices to the VBO.
     */
    private static void renderGhostBlockIntoBuffer(FaceCulledRenderBlocks renderer,
                                                   PreviewRenderUtils.OffsetBlockAccess mteAccess,
                                                   IBlockState state,
                                                   BlockPos localPos,
                                                   BlockPos worldPos,
                                                   Map<BlockPos, BlockInfo> blockMap,
                                                   Predicate<BlockPos> renderFilter,
                                                   BufferBuilder buffer) {
        if (state.getBlock().getRenderType(state) == MetaTileEntityRenderer.BLOCK_RENDER_TYPE) {
            mteAccess.setPos(localPos, worldPos, true);
            Matrix4 transform = new Matrix4()
                    .translate(
                            worldPos.getX() + BLOCK_OFFSET,
                            worldPos.getY() + BLOCK_OFFSET,
                            worldPos.getZ() + BLOCK_OFFSET)
                    .scale(BLOCK_SCALE);
            MetaTileEntityRenderer.INSTANCE.renderBlock(
                    mteAccess, worldPos, state, buffer, transform, GHOST_MTE_PIPELINE);
            return;
        }

        FaceVisibility faceVisibility = PreviewRenderUtils.computeFaceVisibility(
                localPos, blockMap, renderFilter);
        // Skip block entirely if all faces are occluded
        if (faceVisibility.isEntireObscured()) return;

        renderer.renderBlockScaled(state, localPos, worldPos, BLOCK_SCALE, BLOCK_OFFSET,
                faceVisibility, buffer);
    }

    // ========== Cleanup ==========

    public static void resetGhostRender() {
        ghostPos = null;
        ghostEndTime = 0;
        layer = 0;
        renderEntryLogged = false;
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

    private static int countVBOs() {
        int count = 0;
        for (VertexBuffer vbo : vbos) {
            if (vbo != null) count++;
        }
        return count;
    }

    private static void finishDrawingQuietly(BufferBuilder buffer) {
        try {
            buffer.finishDrawing();
        } catch (IllegalStateException ignored) {
            // BufferBuilder.reset() does not clear isDrawing in 1.12; finishDrawing does.
        }
    }

    // ========== Setters / Getters ==========

    public static void setCompareMode(boolean enabled) {
        compareMode = enabled;
    }

    public static void setNoHatch(boolean enabled) {
        noHatch = enabled;
    }

    public static void setChannelValues(@Nullable Map<String, Integer> values) {
        channelValues = values != null && !values.isEmpty() ? new HashMap<>(values) : null;
    }

    public static boolean isCompareMode() {
        return compareMode;
    }
}
