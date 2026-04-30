package gregtech.client.renderer.handler;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.KeyUtil;
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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Rotation;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
public class MultiblockPreviewRenderer {

    private static BlockPos mbpPos;
    private static long mbpEndTime;
    private static int opList = -1;
    private static int layer;
    private static int tier;
    private static boolean compareMode = false;

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
            if (opList == -1 || time > mbpEndTime || !(mc.world.getTileEntity(mbpPos) instanceof IGregTechTileEntity)) {
                resetMultiblockRender();
                layer = 0;
                return;
            }
            Entity entity = mc.getRenderViewEntity();
            if (entity == null) entity = mc.player;
            float partialTicks = event.getPartialTicks();
            double tx = entity.lastTickPosX + ((entity.posX - entity.lastTickPosX) * partialTicks);
            double ty = entity.lastTickPosY + ((entity.posY - entity.lastTickPosY) * partialTicks);
            double tz = entity.lastTickPosZ + ((entity.posZ - entity.lastTickPosZ) * partialTicks);

            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.color(1F, 1F, 1F, 1F);
            GlStateManager.pushMatrix();
            GlStateManager.translate(-tx, -ty, -tz);
            GlStateManager.enableBlend();

            GlStateManager.callList(opList);

            // Render comparison overlay (colored outlines for missing/wrong blocks)
            if (compareMode && (!missingPositions.isEmpty() || !wrongPositions.isEmpty())) {
                renderComparisonOverlay();
            }

            GlStateManager.disableBlend();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
            GlStateManager.color(1F, 1F, 1F, 1F);

        }
    }

    public static void renderMultiBlockPreview(MultiblockControllerBase controller, long durTimeMillis) {
        if (!controller.getPos().equals(mbpPos)) {
            layer = 0;
            tier = 0;
        } else {
            if (mbpEndTime - System.currentTimeMillis() < 200) return;
            layer++;
        }
        resetMultiblockRender();
        mbpPos = controller.getPos();
        mbpEndTime = System.currentTimeMillis() + durTimeMillis;
        opList = GLAllocation.generateDisplayLists(1); // allocate op list
        GlStateManager.glNewList(opList, GL11.GL_COMPILE);
        if (tier != controller.getStructureTier()) {
            tier = controller.getStructureTier();
            controller.reinitializeStructurePattern();
        }
        try {
            List<MultiblockShapeInfo> shapes = controller.getMatchingShapes();
            if (!shapes.isEmpty()) {
                renderControllerInList(controller, shapes.get(0), layer);
                // Compute comparison data if compare mode is active
                if (compareMode) {
                    computeComparisonFromController(controller, shapes.get(0));
                }
            }
        } finally {
            GlStateManager.glEndList();
        }
    }

    public static void renderMultiBlockPreviewByTier(EntityPlayer player, MultiblockControllerBase controller,
                                                     BlockPos pos,
                                                     long durTimeMillis) {
        if (!controller.getPos().equals(mbpPos)) {
            tier = 0;
        } else {
            if (mbpEndTime - System.currentTimeMillis() < 200) return;
            tier++;
        }
        controller.noticePlayer(
                "[结构预览]正在预览" + KeyUtil.lang(controller.getMetaFullName()) + "的第" + tier + "等级", player);
        resetMultiblockRender();
        mbpPos = controller.getPos();
        mbpEndTime = System.currentTimeMillis() + durTimeMillis;
        opList = GLAllocation.generateDisplayLists(1); // allocate op list
        GlStateManager.glNewList(opList, GL11.GL_COMPILE);
        List<MultiblockShapeInfo> shapes = controller.getMatchingShapes();
        if (!shapes.isEmpty())
            renderControllerInList(controller, shapes.get(Math.min(tier, shapes.size() - 1)), 0, pos);
        if (tier >= shapes.size() - 1) tier = 0;
        GlStateManager.glEndList();
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
        List<MultiblockShapeInfo> shapes = controller.getMatchingShapes();
        if (!shapes.isEmpty()) renderControllerInList(controller, shapes.get(0), layer, pos);
        GlStateManager.glEndList();
    }

    public static void renderMultiBlockPreviewByTier(MultiblockControllerBase controller, BlockPos pos, int tier,
                                                     long durTimeMillis) {
        resetMultiblockRender();
        mbpPos = controller.getPos();
        mbpEndTime = System.currentTimeMillis() + durTimeMillis;
        opList = GLAllocation.generateDisplayLists(1); // allocate op list
        GlStateManager.glNewList(opList, GL11.GL_COMPILE);
        List<MultiblockShapeInfo> shapes = controller.getMatchingShapes();
        if (!shapes.isEmpty())
            renderControllerInList(controller, shapes.get(Math.min(tier, shapes.size() - 1)), layer, pos);
        GlStateManager.glEndList();
    }

    public static void renderMultiBlockPreview(MultiblockControllerBase controller, BlockPos pos, int layer,
                                               long durTimeMillis) {
        resetMultiblockRender();
        mbpPos = controller.getPos();
        mbpEndTime = System.currentTimeMillis() + durTimeMillis;
        opList = GLAllocation.generateDisplayLists(1); // allocate op list
        GlStateManager.glNewList(opList, GL11.GL_COMPILE);
        List<MultiblockShapeInfo> shapes = controller.getMatchingShapes();
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
        EnumFacing previewFacing = controller.getFrontFacing();

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
                        previewFacing = metaTE.getFrontFacing();
                    }
                }
            }
        }

        // Calculate rotation from preview facing to actual facing
        EnumFacing facing = controller.getFrontFacing();
        EnumFacing frontFacing = facing.getYOffset() == 0 ? facing :
                facing.getYOffset() < 0 ? controller.getUpwardsFacing() :
                        controller.getUpwardsFacing().getOpposite();
        Rotation rotateBy = Rotation
                .values()[(4 + frontFacing.getHorizontalIndex() - previewFacing.getHorizontalIndex()) % 4];

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
                    BlockPos rotated = relPos.rotate(rotateBy);
                    BlockPos worldPos = worldControllerPos.add(rotated);
                    expectedBlocks.put(worldPos, state);
                }
            }
        }

        computeComparisonData(expectedBlocks, world);
    }

    public static void renderControllerInList(MultiblockControllerBase controllerBase, MultiblockShapeInfo shapeInfo,
                                              int layer) {
        BlockPos mbpPos = controllerBase.getPos();
        EnumFacing frontFacing, previewFacing;
        previewFacing = controllerBase.getFrontFacing();
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
                        previewFacing = metaTE.getFrontFacing();
                        mte = (MultiblockControllerBase) metaTE;
                    }
                }
            }
        }
        TrackedDummyWorld world = new TrackedDummyWorld();
        world.addBlocks(blockMap);
        int finalMaxY = layer % (maxY + 1);
        world.setRenderFilter(pos -> pos.getY() + 1 == finalMaxY || finalMaxY == 0);

        EnumFacing facing = controllerBase.getFrontFacing();
        EnumFacing upwardsFacing = controllerBase.getUpwardsFacing();

        frontFacing = facing.getYOffset() == 0 ? facing :
                facing.getYOffset() < 0 ? upwardsFacing : upwardsFacing.getOpposite();
        Rotation rotatePreviewBy = Rotation
                .values()[(4 + frontFacing.getHorizontalIndex() - previewFacing.getHorizontalIndex()) % 4];

        Minecraft mc = Minecraft.getMinecraft();
        BlockRendererDispatcher brd = mc.getBlockRendererDispatcher();
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder buff = tes.getBuffer();
        GlStateManager.pushMatrix();
        GlStateManager.translate(mbpPos.getX(), mbpPos.getY(), mbpPos.getZ());
        GlStateManager.translate(0.5, 0, 0.5);
        GlStateManager.rotate(rotatePreviewBy.ordinal() * 90, 0, -1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);

        if (facing == EnumFacing.UP) {
            GlStateManager.translate(0.5, 0.5, 0.5);
            GlStateManager.rotate(90, -previewFacing.getZOffset(), 0, previewFacing.getXOffset());
            GlStateManager.translate(-0.5, -0.5, -0.5);
        } else if (facing == EnumFacing.DOWN) {
            GlStateManager.translate(0.5, 0.5, 0.5);
            GlStateManager.rotate(90, previewFacing.getZOffset(), 0, -previewFacing.getXOffset());
            GlStateManager.translate(-0.5, -0.5, -0.5);
        } else {
            int degree = 90 * (upwardsFacing == EnumFacing.EAST ? -1 :
                    upwardsFacing == EnumFacing.SOUTH ? 2 : upwardsFacing == EnumFacing.WEST ? 1 : 0);
            GlStateManager.translate(0.5, 0.5, 0.5);
            GlStateManager.rotate(degree, previewFacing.getXOffset(), 0, previewFacing.getZOffset());
            GlStateManager.translate(-0.5, -0.5, -0.5);
        }

        if (mte != null) {
            mte.checkStructurePattern();
        }

        BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();

        TargetBlockAccess targetBA = new TargetBlockAccess(world, BlockPos.ORIGIN);
        for (BlockPos pos : blockMap.keySet()) {
            targetBA.setPos(pos);
            GlStateManager.pushMatrix();
            BlockPos.MutableBlockPos tPos = new BlockPos.MutableBlockPos(pos.subtract(controllerPos));
            GlStateManager.translate(tPos.getX(), tPos.getY(), tPos.getZ());
            GlStateManager.translate(0.125, 0.125, 0.125);
            GlStateManager.scale(0.75, 0.75, 0.75);

            buff.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            IBlockState state = world.getBlockState(pos);
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

        GlStateManager.popMatrix();
    }

    public static void renderControllerInList(MultiblockControllerBase controllerBase, MultiblockShapeInfo shapeInfo,
                                              int layer, BlockPos targetPos) {
        EnumFacing frontFacing, previewFacing;
        previewFacing = controllerBase.getFrontFacing();
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
                        previewFacing = metaTE.getFrontFacing();
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

        EnumFacing facing = controllerBase.getFrontFacing();
        EnumFacing upwardsFacing = controllerBase.getUpwardsFacing();

        frontFacing = facing.getYOffset() == 0 ? facing :
                facing.getYOffset() < 0 ? upwardsFacing : upwardsFacing.getOpposite();
        Rotation rotatePreviewBy = Rotation
                .values()[(4 + frontFacing.getHorizontalIndex() - previewFacing.getHorizontalIndex()) % 4];

        Minecraft mc = Minecraft.getMinecraft();
        BlockRendererDispatcher brd = mc.getBlockRendererDispatcher();
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder buff = tes.getBuffer();
        GlStateManager.pushMatrix();
        GlStateManager.translate(targetPos.getX(), targetPos.getY(), targetPos.getZ());
        GlStateManager.translate(0.5, 0, 0.5);
        GlStateManager.rotate(rotatePreviewBy.ordinal() * 90, 0, -1, 0);
        GlStateManager.translate(-0.5, 0, -0.5);

        if (facing == EnumFacing.UP) {
            GlStateManager.translate(0.5, 0.5, 0.5);
            GlStateManager.rotate(90, -previewFacing.getZOffset(), 0, previewFacing.getXOffset());
            GlStateManager.translate(-0.5, -0.5, -0.5);
        } else if (facing == EnumFacing.DOWN) {
            GlStateManager.translate(0.5, 0.5, 0.5);
            GlStateManager.rotate(90, previewFacing.getZOffset(), 0, -previewFacing.getXOffset());
            GlStateManager.translate(-0.5, -0.5, -0.5);
        } else {
            int degree = 90 * (upwardsFacing == EnumFacing.EAST ? -1 :
                    upwardsFacing == EnumFacing.SOUTH ? 2 : upwardsFacing == EnumFacing.WEST ? 1 : 0);
            GlStateManager.translate(0.5, 0.5, 0.5);
            GlStateManager.rotate(degree, previewFacing.getXOffset(), 0, previewFacing.getZOffset());
            GlStateManager.translate(-0.5, -0.5, -0.5);
        }

        if (mte != null) {
            mte.checkStructurePattern();
        }

        BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();

        TargetBlockAccess targetBA = new TargetBlockAccess(world, BlockPos.ORIGIN);
        for (BlockPos pos : blockMap.keySet()) {
            targetBA.setPos(pos);
            GlStateManager.pushMatrix();
            BlockPos.MutableBlockPos tPos = new BlockPos.MutableBlockPos(pos.subtract(controllerPos));
            GlStateManager.translate(tPos.getX(), tPos.getY(), tPos.getZ());
            GlStateManager.translate(0.125, 0.125, 0.125);
            GlStateManager.scale(0.75, 0.75, 0.75);

            buff.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            IBlockState state = world.getBlockState(pos);
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

        GlStateManager.popMatrix();
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
            return 15;
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
