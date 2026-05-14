package gregtech.client.renderer.handler;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;

import gregtech.client.renderer.godforge.util.FaceVisibility;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared utility methods for multiblock preview rendering, used by both
 * {@link MultiblockPreviewRenderer} (VBO-based controller preview) and
 * {@link GhostBlockRenderer} (immediate-mode projector preview).
 */
@SideOnly(Side.CLIENT)
public final class PreviewRenderUtils {

    // Tint colors for comparison mode overlay
    public static final float MISSING_R = 0.3F, MISSING_G = 0.6F, MISSING_B = 1.0F, MISSING_A = 0.5F;
    public static final float WRONG_R = 1.0F, WRONG_G = 0.3F, WRONG_B = 0.3F, WRONG_A = 0.6F;

    private PreviewRenderUtils() {}

    // ========== Coordinate Transform Utilities ==========

    /**
     * Transform a local preview offset into world-space offset using the controller's pattern template.
     * Used for main pattern preview where the controller defines the coordinate mapping.
     */
    public static BlockPos transformPreviewOffset(MultiblockControllerBase controller, BlockPos previewOffset) {
        BlockPatternTemplate template = controller.getPatternTemplate();
        if (template == null) {
            return previewOffset;
        }

        RelativeDirection[] structureDir = template.getStructureDir();
        int[] localOffset = new int[3];
        for (int i = 0; i < structureDir.length; i++) {
            localOffset[i] = getAxisComponent(previewOffset, structureDir[i].getActualFacing(EnumFacing.NORTH));
        }

        return RelativeDirection.setActualRelativeOffset(localOffset[0], localOffset[1], localOffset[2],
                controller.getFrontFacing().getOpposite(), controller.getUpwardsFacing(),
                controller.isFlipped(), structureDir);
    }

    /**
     * Transform a local preview offset for a piece into world-space offset using the piece's structure directions.
     */
    public static BlockPos transformPieceOffset(BlockPos previewOffset, RelativeDirection[] structureDir,
                                                EnumFacing frontFacing, EnumFacing upwardsFacing,
                                                boolean isFlipped) {
        int[] localOffset = new int[3];
        for (int i = 0; i < structureDir.length; i++) {
            localOffset[i] = getAxisComponent(previewOffset, structureDir[i].getActualFacing(EnumFacing.NORTH));
        }
        return RelativeDirection.setActualRelativeOffset(localOffset[0], localOffset[1], localOffset[2],
                frontFacing, upwardsFacing, isFlipped, structureDir);
    }

    /**
     * Extract a single axis component from a BlockPos based on the given facing direction.
     */
    public static int getAxisComponent(BlockPos pos, EnumFacing axis) {
        return switch (axis) {
            case EAST -> pos.getX();
            case WEST -> -pos.getX();
            case UP -> pos.getY();
            case DOWN -> -pos.getY();
            case SOUTH -> pos.getZ();
            case NORTH -> -pos.getZ();
        };
    }

    // ========== Surface Block Computation ==========

    /**
     * Compute the set of surface blocks, skipping fully-occluded internal blocks to improve
     * rendering performance. A block is considered "surface" if at least one neighbor is
     * missing from blockMap, is air, or is not a full cube (e.g. glass, slabs).
     */
    @SuppressWarnings("deprecation")
    public static Set<BlockPos> computeSurfaceBlocks(Map<BlockPos, BlockInfo> blockMap) {
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

    // ========== Comparison Mode Utilities ==========

    /**
     * Compute per-face visibility for a block position in the given block map.
     * A face is visible if the neighboring block in that direction is absent, air, or not opaque.
     */
    @SuppressWarnings("deprecation")
    public static FaceVisibility computeFaceVisibility(BlockPos pos, Map<BlockPos, BlockInfo> blockMap) {
        FaceVisibility visibility = new FaceVisibility();
        for (EnumFacing face : EnumFacing.VALUES) {
            BlockPos neighbor = pos.offset(face);
            BlockInfo neighborInfo = blockMap.get(neighbor);
            boolean opaque = neighborInfo != null && neighborInfo.getBlockState() != null
                    && neighborInfo.getBlockState().getBlock() != Blocks.AIR
                    && neighborInfo.getBlockState().isOpaqueCube();
            if (opaque) {
                switch (face) {
                    case DOWN -> visibility.bottom = false;
                    case UP -> visibility.top = false;
                    case NORTH -> visibility.back = false;
                    case SOUTH -> visibility.front = false;
                    case WEST -> visibility.left = false;
                    case EAST -> visibility.right = false;
                }
            }
        }
        return visibility;
    }

    // ========== Comparison Mode Utilities ==========

    /**
     * Render colored box overlays for missing and wrong block positions.
     * Should be called within a GL matrix context that has already been translated to camera space.
     */
    public static void renderComparisonOverlay(List<BlockPos> missingPositions, List<BlockPos> wrongPositions) {
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
    public static void renderColoredBox(BufferBuilder buff, BlockPos pos,
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
     * Populates the provided missing/wrong position lists.
     *
     * @param expectedBlocks   map of world positions -> expected block states
     * @param world            the real world to compare against
     * @param missingPositions output list for missing block positions (cleared first)
     * @param wrongPositions   output list for wrong block positions (cleared first)
     */
    public static void computeComparisonData(Map<BlockPos, IBlockState> expectedBlocks, World world,
                                             List<BlockPos> missingPositions, List<BlockPos> wrongPositions) {
        missingPositions.clear();
        wrongPositions.clear();

        if (world == null) return;

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
     *
     * @param controller       the multiblock controller
     * @param shapeInfo        the expected shape info
     * @param missingPositions output list for missing block positions
     * @param wrongPositions   output list for wrong block positions
     */
    public static void computeComparisonFromController(MultiblockControllerBase controller,
                                                       MultiblockShapeInfo shapeInfo,
                                                       List<BlockPos> missingPositions,
                                                       List<BlockPos> wrongPositions) {
        World world = Minecraft.getMinecraft().world;
        if (world == null) return;

        BlockInfo[][][] blocks = shapeInfo.getBlocks();
        BlockPos controllerPos = findControllerInPreview(blocks, controller);

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

        computeComparisonData(expectedBlocks, world, missingPositions, wrongPositions);
    }

    // ========== Controller Position Lookup ==========

    /**
     * Find the controller position in a preview shape's block array.
     * Uses a two-pass strategy for maximum compatibility:
     * 1. First pass: exact metaTileEntityId match (precise, handles normal multiblocks)
     * 2. Second pass (fallback): class-based match (handles selfPredicateByClass variants
     *    where multiple IDs share the same class, e.g. FluidDrill, LargeMiner)
     *
     * @param blocks         the preview block array [x][y][z]
     * @param controllerBase the actual controller in the world
     * @return the controller's position in the array, or BlockPos.ORIGIN if not found
     */
    public static BlockPos findControllerInPreview(BlockInfo[][][] blocks,
                                                   MultiblockControllerBase controllerBase) {
        BlockPos classFallback = null;
        for (int x = 0; x < blocks.length; x++) {
            BlockInfo[][] aisle = blocks[x];
            for (int y = 0; y < aisle.length; y++) {
                BlockInfo[] column = aisle[y];
                for (int z = 0; z < column.length; z++) {
                    MetaTileEntity metaTE = column[z].getTileEntity() instanceof IGregTechTileEntity ?
                            ((IGregTechTileEntity) column[z].getTileEntity()).getMetaTileEntity() : null;
                    if (metaTE instanceof MultiblockControllerBase) {
                        if (metaTE.metaTileEntityId.equals(controllerBase.metaTileEntityId)) {
                            return new BlockPos(x, y, z);
                        }
                        if (classFallback == null &&
                                controllerBase.getClass().isInstance(metaTE)) {
                            classFallback = new BlockPos(x, y, z);
                        }
                    }
                }
            }
        }
        return classFallback != null ? classFallback : BlockPos.ORIGIN;
    }

    // ========== IBlockAccess Adapter ==========

    /**
     * An IBlockAccess adapter that redirects queries at BlockPos.ORIGIN to a configurable
     * target position in the delegate world. Used to render blocks at arbitrary positions
     * while the block renderer thinks it's rendering at the origin.
     */
    @SideOnly(Side.CLIENT)
    public static class TargetBlockAccess implements IBlockAccess {

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
