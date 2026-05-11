package gregtech.client.renderer.handler;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;
import gregtech.client.utils.TrackedDummyWorld;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SideOnly(Side.CLIENT)
public class MultiblockPreviewRenderer {

    private static BlockPos mbpPos;
    private static long mbpEndTime;
    private static int opList = -1;
    private static int layer;
    private static boolean compareMode = false;
    @Nullable
    private static Map<String, Integer> channelValues = null;

    // Comparison mode data: world positions of missing/wrong blocks for overlay rendering
    private static final List<BlockPos> missingPositions = new ArrayList<>();
    private static final List<BlockPos> wrongPositions = new ArrayList<>();

    // Tint colors for comparison mode
    private static final float MISSING_R = 0.3F, MISSING_G = 0.6F, MISSING_B = 1.0F, MISSING_A = 0.5F;
    private static final float WRONG_R = 1.0F, WRONG_G = 0.3F, WRONG_B = 0.3F, WRONG_A = 0.6F;

    public static void renderWorldLastEvent(RenderWorldLastEvent event) {
        if (mbpPos != null) {
            Minecraft mc = Minecraft.getMinecraft();
            long time = System.currentTimeMillis();
            Entity entity = mc.getRenderViewEntity();
            if (entity == null) entity = mc.player;
            if (opList == -1 || time > mbpEndTime
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
            GlStateManager.pushMatrix();
            GlStateManager.translate(-tx, -ty, -tz);

            // Disable lightmap texture unit so block brightness is not affected by
            // per-vertex lightmap UVs (consistent with WorldSceneRenderer approach).
            mc.entityRenderer.disableLightmap();

            // Enable blending with GL_CONSTANT_ALPHA to achieve semi-transparent hologram.
            // Per-vertex color in BLOCK format has alpha=255, so GL state color alpha is
            // overridden; GL_CONSTANT_ALPHA uses the blend color alpha instead.
            GlStateManager.enableBlend();
            GL14.glBlendColor(1.0F, 1.0F, 1.0F, 0.6F);
            GlStateManager.tryBlendFuncSeparate(
                    GL11.GL_CONSTANT_ALPHA, GL11.GL_ONE_MINUS_CONSTANT_ALPHA,
                    GL11.GL_ONE, GL11.GL_ZERO);

            GlStateManager.callList(opList);

            // Render comparison overlay (colored outlines for missing/wrong blocks)
            if (compareMode && (!missingPositions.isEmpty() || !wrongPositions.isEmpty())) {
                renderComparisonOverlay();
            }

            GlStateManager.disableBlend();
            GlStateManager.enableLighting();
            mc.entityRenderer.enableLightmap();
            GlStateManager.popMatrix();
            GlStateManager.color(1F, 1F, 1F, 1F);

        }
    }

    public static void renderMultiBlockPreview(MultiblockControllerBase controller, long durTimeMillis) {
        if (!controller.getPos().equals(mbpPos)) {
            layer = 0;
        } else {
            if (mbpEndTime - System.currentTimeMillis() < 200) return;
            layer++;
        }
        rebuildMultiblockPreview(controller, durTimeMillis);
    }

    public static void refreshCurrentPreview(MultiblockControllerBase controller) {
        long remainingTime = mbpEndTime - System.currentTimeMillis();
        if (controller == null || mbpPos == null || opList == -1 || remainingTime <= 0 ||
                !controller.getPos().equals(mbpPos)) {
            return;
        }
        rebuildMultiblockPreview(controller, remainingTime);
    }

    private static void rebuildMultiblockPreview(MultiblockControllerBase controller, long durTimeMillis) {
        resetMultiblockRender();
        mbpPos = controller.getPos();
        mbpEndTime = System.currentTimeMillis() + durTimeMillis;
        opList = GLAllocation.generateDisplayLists(1); // allocate op list
        GlStateManager.glNewList(opList, GL11.GL_COMPILE);
        try {
            // Check if a specific piece is requested via STRUCTURE_PIECE channel
            int pieceIndex = channelValues != null
                    ? channelValues.getOrDefault(GTStructureChannels.STRUCTURE_PIECE.getName(), 0)
                    : 0;

            if (pieceIndex > 0) {
                // Render a specific piece from the MultiPiecePattern
                renderPiecePreview(controller, pieceIndex);
            } else {
                // Default: render the main pattern (backward compatible)
                List<MultiblockShapeInfo> shapes = channelValues != null
                        ? controller.getMatchingShapes(channelValues)
                        : controller.getMatchingShapes();
                if (!shapes.isEmpty()) {
                    renderControllerInList(controller, shapes.get(0), layer);
                    // Compute comparison data if compare mode is active
                    if (compareMode) {
                        computeComparisonFromController(controller, shapes.get(0));
                    }
                }
            }
        } finally {
            GlStateManager.glEndList();
        }
    }

    public static void renderMultiBlockPreview(MultiblockControllerBase controller, BlockPos pos, long durTimeMillis) {
        if (!controller.getPos().equals(mbpPos)) {
            layer = 0;
        } else {
            if (mbpEndTime - System.currentTimeMillis() < 200) return;
            layer++;
        }
        resetMultiblockRender();
        mbpPos = controller.getPos();
        mbpEndTime = System.currentTimeMillis() + durTimeMillis;
        opList = GLAllocation.generateDisplayLists(1); // allocate op list
        GlStateManager.glNewList(opList, GL11.GL_COMPILE);
        List<MultiblockShapeInfo> shapes = channelValues != null
                ? controller.getMatchingShapes(channelValues)
                : controller.getMatchingShapes();
        if (!shapes.isEmpty()) renderControllerInList(controller, shapes.get(0), layer, pos);
        GlStateManager.glEndList();
    }

    public static void renderMultiBlockPreview(MultiblockControllerBase controller, BlockPos pos, int layer,
                                               long durTimeMillis) {
        resetMultiblockRender();
        mbpPos = controller.getPos();
        mbpEndTime = System.currentTimeMillis() + durTimeMillis;
        opList = GLAllocation.generateDisplayLists(1); // allocate op list
        GlStateManager.glNewList(opList, GL11.GL_COMPILE);
        List<MultiblockShapeInfo> shapes = channelValues != null
                ? controller.getMatchingShapes(channelValues)
                : controller.getMatchingShapes();
        if (!shapes.isEmpty()) renderControllerInList(controller, shapes.get(0), layer, pos);
        GlStateManager.glEndList();
    }

    public static void resetMultiblockRender() {
        mbpPos = null;
        mbpEndTime = 0;
        if (opList != -1) {
            GlStateManager.glDeleteLists(opList, 1);
            opList = -1;
        }
        missingPositions.clear();
        wrongPositions.clear();
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
     * Render colored box overlays at missing/wrong block positions.
     * Called from renderWorldLastEvent when compareMode is active.
     */
    private static void renderComparisonOverlay() {
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        Tessellator tes = Tessellator.getInstance();
        BufferBuilder buff = tes.getBuffer();

        // Render missing blocks (blue)
        if (!missingPositions.isEmpty()) {
            buff.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (BlockPos pos : missingPositions) {
                renderColoredBox(buff, pos, MISSING_R, MISSING_G, MISSING_B, MISSING_A);
            }
            tes.draw();
        }

        // Render wrong blocks (red)
        if (!wrongPositions.isEmpty()) {
            buff.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (BlockPos pos : wrongPositions) {
                renderColoredBox(buff, pos, WRONG_R, WRONG_G, WRONG_B, WRONG_A);
            }
            tes.draw();
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
    }

    /**
     * Render a colored box at the given block position.
     */
    private static void renderColoredBox(BufferBuilder buff, BlockPos pos,
                                          float r, float g, float b, float a) {
        float x0 = pos.getX() + 0.0625F;
        float y0 = pos.getY() + 0.0625F;
        float z0 = pos.getZ() + 0.0625F;
        float x1 = pos.getX() + 0.9375F;
        float y1 = pos.getY() + 0.9375F;
        float z1 = pos.getZ() + 0.9375F;

        // Bottom face
        buff.pos(x0, y0, z0).color(r, g, b, a).endVertex();
        buff.pos(x1, y0, z0).color(r, g, b, a).endVertex();
        buff.pos(x1, y0, z1).color(r, g, b, a).endVertex();
        buff.pos(x0, y0, z1).color(r, g, b, a).endVertex();

        // Top face
        buff.pos(x0, y1, z0).color(r, g, b, a).endVertex();
        buff.pos(x0, y1, z1).color(r, g, b, a).endVertex();
        buff.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        buff.pos(x1, y1, z0).color(r, g, b, a).endVertex();

        // North face (-Z)
        buff.pos(x0, y0, z0).color(r, g, b, a).endVertex();
        buff.pos(x0, y1, z0).color(r, g, b, a).endVertex();
        buff.pos(x1, y1, z0).color(r, g, b, a).endVertex();
        buff.pos(x1, y0, z0).color(r, g, b, a).endVertex();

        // South face (+Z)
        buff.pos(x0, y0, z1).color(r, g, b, a).endVertex();
        buff.pos(x1, y0, z1).color(r, g, b, a).endVertex();
        buff.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        buff.pos(x0, y1, z1).color(r, g, b, a).endVertex();

        // West face (-X)
        buff.pos(x0, y0, z0).color(r, g, b, a).endVertex();
        buff.pos(x0, y0, z1).color(r, g, b, a).endVertex();
        buff.pos(x0, y1, z1).color(r, g, b, a).endVertex();
        buff.pos(x0, y1, z0).color(r, g, b, a).endVertex();

        // East face (+X)
        buff.pos(x1, y0, z0).color(r, g, b, a).endVertex();
        buff.pos(x1, y1, z0).color(r, g, b, a).endVertex();
        buff.pos(x1, y1, z1).color(r, g, b, a).endVertex();
        buff.pos(x1, y0, z1).color(r, g, b, a).endVertex();
    }

    /**
     * Compute comparison data by comparing expected structure against real world blocks.
     * Should be called when building the display list to populate missingPositions/wrongPositions.
     *
     * @param expectedBlocks map of world positions -> expected block states
     * @param world          the real world to compare against
     */
    public static void computeComparisonData(Map<BlockPos, IBlockState> expectedBlocks, World world) {
        missingPositions.clear();
        wrongPositions.clear();

        if (!compareMode || world == null) return;

        for (Map.Entry<BlockPos, IBlockState> entry : expectedBlocks.entrySet()) {
            BlockPos worldPos = entry.getKey();
            IBlockState expected = entry.getValue();

            if (expected.getBlock() == Blocks.AIR) continue;

            IBlockState actual = world.getBlockState(worldPos);
            if (actual.getBlock() == Blocks.AIR || actual.getMaterial().isReplaceable()) {
                // Position is empty — block is missing
                missingPositions.add(worldPos);
            } else if (!actual.equals(expected)) {
                // Position has a block but it's wrong
                wrongPositions.add(worldPos);
            }
            // else: block matches, skip
        }
    }

    /**
     * Compute comparison data from a controller and its shape info.
     * Maps virtual block positions to real world positions using the controller's orientation.
     */
    private static void computeComparisonFromController(MultiblockControllerBase controller,
                                                         MultiblockShapeInfo shapeInfo) {
        World world = Minecraft.getMinecraft().world;
        if (world == null) return;

        BlockInfo[][][] blocks = shapeInfo.getBlocks();
        BlockPos controllerPos = BlockPos.ORIGIN;

        // Find controller position in the shape
        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                for (int z = 0; z < column.length; z++) {
                    MetaTileEntity metaTE = column[z].getTileEntity() instanceof IGregTechTileEntity ?
                            ((IGregTechTileEntity) column[z].getTileEntity()).getMetaTileEntity() : null;
                    if (metaTE instanceof MultiblockControllerBase &&
                            metaTE.metaTileEntityId.equals(controller.metaTileEntityId)) {
                        controllerPos = new BlockPos(x, y, z);
                    }
                }
            }
        }

        // Build expected blocks map in world coordinates
        Map<BlockPos, IBlockState> expectedBlocks = new HashMap<>();
        BlockPos worldControllerPos = controller.getPos();

        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                for (int z = 0; z < column.length; z++) {
                    IBlockState state = column[z].getBlockState();
                    if (state == null || state.getBlock() == Blocks.AIR) continue;

                    BlockPos relPos = new BlockPos(x, y, z).subtract(controllerPos);
                    BlockPos rotated = transformPreviewOffset(controller, relPos);
                    BlockPos worldPos = worldControllerPos.add(rotated);
                    expectedBlocks.put(worldPos, state);
                }
            }
        }

        computeComparisonData(expectedBlocks, world);
    }

    /**
     * Render a specific piece from the MultiPiecePattern at its world-space offset position.
     * The piece center is computed using the controller's facing and the piece's OffsetMode.
     *
     * @param controller the multiblock controller
     * @param pieceIndex 1-based index into the MultiPiecePattern's piece list
     */
    private static void renderPiecePreview(MultiblockControllerBase controller, int pieceIndex) {
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
                controller.getFrontFacing().getOpposite(),
                controller.getUpwardsFacing());

        renderPieceInList(controller, shapeInfo, layer, pieceCenterPos, piece);
    }

    /**
     * Render a piece's preview blocks at the specified world position.
     * Unlike renderControllerInList, the piece has no controller block inside it — rendering
     * is anchored at the piece's computed center position.
     */
    private static void renderPieceInList(MultiblockControllerBase controllerBase,
                                           MultiblockShapeInfo shapeInfo, int layer,
                                           BlockPos pieceCenterPos, StructurePiece piece) {
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
        world.setRenderFilter(pos -> pos.getY() + 1 == finalMaxY || finalMaxY == 0);

        Minecraft mc = Minecraft.getMinecraft();
        BlockRendererDispatcher brd = mc.getBlockRendererDispatcher();
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder buff = tes.getBuffer();
        GlStateManager.pushMatrix();
        GlStateManager.translate(pieceCenterPos.getX(), pieceCenterPos.getY(), pieceCenterPos.getZ());

        BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();

        Set<BlockPos> surfaceBlocks = computeSurfaceBlocks(blockMap);

        // Use the piece's own template for coordinate transformation
        gregtech.api.pattern.BlockPatternTemplate pieceTemplate = piece.getTemplate();
        RelativeDirection[] structureDir = pieceTemplate.getStructureDir();
        int[] centerOffset = pieceTemplate.getCenterOffset();
        BlockPos pieceCenterInLocal = new BlockPos(centerOffset[0], centerOffset[1], centerOffset[3]);

        GlStateManager.disableLighting();
        TargetBlockAccess targetBA = new TargetBlockAccess(world, BlockPos.ORIGIN);
        for (BlockRenderLayer brl : BlockRenderLayer.values()) {
            buff.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            ForgeHooksClient.setRenderLayer(brl);
            for (BlockPos pos : surfaceBlocks) {
                IBlockState state = world.getBlockState(pos);
                if (!state.getBlock().canRenderInLayer(state, brl)) continue;
                targetBA.setPos(pos);
                GlStateManager.pushMatrix();
                BlockPos tPos = transformPieceOffset(pos.subtract(pieceCenterInLocal), structureDir,
                        controllerBase.getFrontFacing().getOpposite(), controllerBase.getUpwardsFacing(),
                        controllerBase.isFlipped());
                GlStateManager.translate(tPos.getX(), tPos.getY(), tPos.getZ());
                GlStateManager.translate(0.125, 0.125, 0.125);
                GlStateManager.scale(0.75, 0.75, 0.75);
                brd.renderBlock(state, BlockPos.ORIGIN, targetBA, buff);
                GlStateManager.popMatrix();
            }
            tes.draw();
        }
        GlStateManager.enableLighting();
        ForgeHooksClient.setRenderLayer(oldLayer);

        GlStateManager.popMatrix();
    }

    /**
     * Transform a local preview offset for a piece into world-space offset using the piece's structure directions.
     */
    private static BlockPos transformPieceOffset(BlockPos previewOffset, RelativeDirection[] structureDir,
                                                  EnumFacing frontFacing, EnumFacing upwardsFacing,
                                                  boolean isFlipped) {
        int[] localOffset = new int[3];
        for (int i = 0; i < structureDir.length; i++) {
            localOffset[i] = getAxisComponent(previewOffset, structureDir[i].getActualFacing(EnumFacing.NORTH));
        }
        return RelativeDirection.setActualRelativeOffset(localOffset[0], localOffset[1], localOffset[2],
                frontFacing, upwardsFacing, isFlipped, structureDir);
    }

    public static void renderControllerInList(MultiblockControllerBase controllerBase, MultiblockShapeInfo shapeInfo,
                                              int layer) {
        BlockPos mbpPos = controllerBase.getPos();
        BlockPos controllerPos = BlockPos.ORIGIN;
        MultiblockControllerBase mte = null;
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
                    MetaTileEntity metaTE = column[z].getTileEntity() instanceof IGregTechTileEntity ?
                            ((IGregTechTileEntity) column[z].getTileEntity()).getMetaTileEntity() : null;
                    if (metaTE instanceof MultiblockControllerBase &&
                            metaTE.metaTileEntityId.equals(controllerBase.metaTileEntityId)) {
                        controllerPos = new BlockPos(x, y, z);
                        mte = (MultiblockControllerBase) metaTE;
                    }
                }
            }
        }
        TrackedDummyWorld world = new TrackedDummyWorld();
        world.addBlocks(blockMap);
        int finalMaxY = layer % (maxY + 1);
        world.setRenderFilter(pos -> pos.getY() + 1 == finalMaxY || finalMaxY == 0);

        Minecraft mc = Minecraft.getMinecraft();
        BlockRendererDispatcher brd = mc.getBlockRendererDispatcher();
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder buff = tes.getBuffer();
        GlStateManager.pushMatrix();
        GlStateManager.translate(mbpPos.getX(), mbpPos.getY(), mbpPos.getZ());

        if (mte != null) {
            // 不在渲染路径中做结构校验，避免大机器卡顿
        }

        BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();

        Set<BlockPos> surfaceBlocks = computeSurfaceBlocks(blockMap);

        // Disable lighting so block textures render with their original colors
        // without being tinted by the OpenGL fixed-function lighting pipeline.
        GlStateManager.disableLighting();
        TargetBlockAccess targetBA = new TargetBlockAccess(world, BlockPos.ORIGIN);
        for (BlockRenderLayer brl : BlockRenderLayer.values()) {
            buff.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            ForgeHooksClient.setRenderLayer(brl);
            for (BlockPos pos : surfaceBlocks) {
                if (pos.equals(controllerPos)) continue;
                IBlockState state = world.getBlockState(pos);
                if (!state.getBlock().canRenderInLayer(state, brl)) continue;
                targetBA.setPos(pos);
                GlStateManager.pushMatrix();
                BlockPos tPos = transformPreviewOffset(controllerBase, pos.subtract(controllerPos));
                GlStateManager.translate(tPos.getX(), tPos.getY(), tPos.getZ());
                GlStateManager.translate(0.125, 0.125, 0.125);
                GlStateManager.scale(0.75, 0.75, 0.75);
                brd.renderBlock(state, BlockPos.ORIGIN, targetBA, buff);
                GlStateManager.popMatrix();
            }
            tes.draw();
        }
        GlStateManager.enableLighting();
        ForgeHooksClient.setRenderLayer(oldLayer);

        GlStateManager.popMatrix();
    }

    public static void renderControllerInList(MultiblockControllerBase controllerBase, MultiblockShapeInfo shapeInfo,
                                              int layer, BlockPos targetPos) {
        BlockPos controllerPos = BlockPos.ORIGIN;
        MultiblockControllerBase mte = null;
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
                    MetaTileEntity metaTE = column[z].getTileEntity() instanceof IGregTechTileEntity ?
                            ((IGregTechTileEntity) column[z].getTileEntity()).getMetaTileEntity() : null;
                    if (metaTE instanceof MultiblockControllerBase &&
                            metaTE.metaTileEntityId.equals(controllerBase.metaTileEntityId)) {
                        controllerPos = new BlockPos(x, y, z);
                        mte = (MultiblockControllerBase) metaTE;
                        break;
                    }
                }
            }
        }
        TrackedDummyWorld world = new TrackedDummyWorld();
        world.addBlocks(blockMap);
        int finalMaxY = layer % (maxY + 1);
        world.setRenderFilter(pos -> pos.getY() + 1 == finalMaxY || finalMaxY == 0);

        Minecraft mc = Minecraft.getMinecraft();
        BlockRendererDispatcher brd = mc.getBlockRendererDispatcher();
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder buff = tes.getBuffer();
        GlStateManager.pushMatrix();
        GlStateManager.translate(targetPos.getX(), targetPos.getY(), targetPos.getZ());

        if (mte != null) {
            // Do not perform structure validation in the render path to avoid lag
        }

        BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();

        Set<BlockPos> surfaceBlocks = computeSurfaceBlocks(blockMap);

        // Disable lighting so block textures render with their original colors
        // without being tinted by the OpenGL fixed-function lighting pipeline.
        GlStateManager.disableLighting();
        TargetBlockAccess targetBA = new TargetBlockAccess(world, BlockPos.ORIGIN);
        for (BlockRenderLayer brl : BlockRenderLayer.values()) {
            buff.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            ForgeHooksClient.setRenderLayer(brl);
            for (BlockPos pos : surfaceBlocks) {
                if (pos.equals(controllerPos)) continue;
                IBlockState state = world.getBlockState(pos);
                if (!state.getBlock().canRenderInLayer(state, brl)) continue;
                targetBA.setPos(pos);
                GlStateManager.pushMatrix();
                BlockPos tPos = transformPreviewOffset(controllerBase, pos.subtract(controllerPos));
                GlStateManager.translate(tPos.getX(), tPos.getY(), tPos.getZ());
                GlStateManager.translate(0.125, 0.125, 0.125);
                GlStateManager.scale(0.75, 0.75, 0.75);
                brd.renderBlock(state, BlockPos.ORIGIN, targetBA, buff);
                GlStateManager.popMatrix();
            }
            tes.draw();
        }
        GlStateManager.enableLighting();
        ForgeHooksClient.setRenderLayer(oldLayer);

        GlStateManager.popMatrix();
    }

    /**
     * Compute the set of surface blocks, skipping fully-occluded internal blocks to improve
     * rendering performance. A block is considered "surface" if at least one neighbor is
     * missing from blockMap, is air, or is not a full cube (e.g. glass, slabs).
     */
    @SuppressWarnings("deprecation")
    private static Set<BlockPos> computeSurfaceBlocks(Map<BlockPos, BlockInfo> blockMap) {
        Set<BlockPos> surface = new HashSet<>();
        for (Map.Entry<BlockPos, BlockInfo> entry : blockMap.entrySet()) {
            BlockPos pos = entry.getKey();
            boolean enclosed = true;
            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos neighbor = pos.offset(face);
                BlockInfo neighborInfo = blockMap.get(neighbor);
                if (neighborInfo == null || neighborInfo.getBlockState() == null
                        || neighborInfo.getBlockState().getBlock() == Blocks.AIR
                        || !neighborInfo.getBlockState().isFullCube()) {
                    enclosed = false;
                    break;
                }
            }
            if (!enclosed) {
                surface.add(pos);
            }
        }
        return surface;
    }

    private static EnumFacing getStructureFacing(MultiblockControllerBase controller) {
        return controller.getFrontFacing().getOpposite();
    }

    private static EnumFacing getPreviewStructureFacing(MetaTileEntity metaTileEntity) {
        return metaTileEntity.getFrontFacing().getOpposite();
    }

    private static BlockPos transformPreviewOffset(MultiblockControllerBase controller, BlockPos previewOffset) {
        gregtech.api.pattern.BlockPatternTemplate template = controller.getPatternTemplate();
        if (template == null) {
            return previewOffset;
        }

        RelativeDirection[] structureDir = template.getStructureDir();
        int[] localOffset = new int[3];
        for (int i = 0; i < structureDir.length; i++) {
            localOffset[i] = getAxisComponent(previewOffset, structureDir[i].getActualFacing(EnumFacing.NORTH));
        }

        return RelativeDirection.setActualRelativeOffset(localOffset[0], localOffset[1], localOffset[2],
                getStructureFacing(controller), controller.getUpwardsFacing(), controller.isFlipped(), structureDir);
    }

    private static int getAxisComponent(BlockPos pos, EnumFacing axis) {
        return switch (axis) {
            case EAST -> pos.getX();
            case WEST -> -pos.getX();
            case UP -> pos.getY();
            case DOWN -> -pos.getY();
            case SOUTH -> pos.getZ();
            case NORTH -> -pos.getZ();
        };
    }

    @SideOnly(Side.CLIENT)
    private static class TargetBlockAccess implements IBlockAccess {

        private final IBlockAccess delegate;
        private BlockPos targetPos;

        public TargetBlockAccess(IBlockAccess delegate, BlockPos pos) {
            this.delegate = delegate;
            this.targetPos = pos;
        }

        public void setPos(BlockPos pos) {
            targetPos = pos;
        }

        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return pos.equals(BlockPos.ORIGIN) ? delegate.getTileEntity(targetPos) : null;
        }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            // Full brightness: skyLight=15 << 20 | blockLight=15 << 4
            return 15 << 20 | 15 << 4;
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return pos.equals(BlockPos.ORIGIN) ? delegate.getBlockState(targetPos) : Blocks.AIR.getDefaultState();
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return !pos.equals(BlockPos.ORIGIN) || delegate.isAirBlock(targetPos);
        }

        @Override
        public Biome getBiome(BlockPos pos) {
            return delegate.getBiome(targetPos);
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return 0;
        }

        @Override
        public WorldType getWorldType() {
            return delegate.getWorldType();
        }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
            return pos.equals(BlockPos.ORIGIN) && delegate.isSideSolid(targetPos, side, _default);
        }
    }
}
