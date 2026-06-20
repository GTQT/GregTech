package gregtech.client.renderer.godforge.util;

import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;

public class FaceCulledRenderBlocks {

    private static final int FULL_BRIGHT = 15728880;
    // BLOCK vertex format: 7 ints per vertex (pos×3 + color + uv×2 + lightmap)
    private static final int INTS_PER_VERTEX = 7;
    private static final int VERTICES_PER_QUAD = 4;

    private final IBlockAccess blockAccess;
    private final BlockRendererDispatcher dispatcher;

    public FaceCulledRenderBlocks(IBlockAccess blockAccess) {
        this.blockAccess = blockAccess;
        this.dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
    }

    /**
     * Render a block at the given position with face culling (no scaling).
     */
    public void renderBlock(IBlockState state, BlockPos pos, FaceVisibility faceVisibility, BufferBuilder buffer) {
        IBakedModel model = resolveModel(state, pos);
        IBlockState renderState = resolveRenderState(state, pos);
        long rand = MathHelper.getPositionRandom(pos);

        renderQuads(buffer, model.getQuads(renderState, null, rand), pos);
        if (faceVisibility.bottom) renderQuads(buffer, model.getQuads(renderState, EnumFacing.DOWN, rand), pos);
        if (faceVisibility.top) renderQuads(buffer, model.getQuads(renderState, EnumFacing.UP, rand), pos);
        if (faceVisibility.back) renderQuads(buffer, model.getQuads(renderState, EnumFacing.NORTH, rand), pos);
        if (faceVisibility.front) renderQuads(buffer, model.getQuads(renderState, EnumFacing.SOUTH, rand), pos);
        if (faceVisibility.left) renderQuads(buffer, model.getQuads(renderState, EnumFacing.WEST, rand), pos);
        if (faceVisibility.right) renderQuads(buffer, model.getQuads(renderState, EnumFacing.EAST, rand), pos);
    }

    /**
     * Render a block with per-vertex scaling. Each vertex is transformed as:
     *   finalPos = worldPos + offset + (modelVertex) * scale
     * where modelVertex is the raw 0~1 model-space coordinate from BakedQuad.
     *
     * @param state          the block state to render
     * @param localPos       the local position used for model lookup (usually the block's position in the virtual world)
     * @param worldPos       the world-space position where the block should appear in the VBO
     * @param scale          scale factor applied to each model vertex (e.g. 0.75)
     * @param offset         offset added after worldPos before scaling (e.g. 0.125 to center the scaled block)
     * @param faceVisibility which faces are visible
     * @param buffer         the buffer to write into
     */
    public void renderBlockScaled(IBlockState state, BlockPos localPos, BlockPos worldPos,
                                  float scale, float offset,
                                  FaceVisibility faceVisibility, BufferBuilder buffer) {
        if (state.getRenderType() == EnumBlockRenderType.LIQUID) {
            renderFluidScaled(state, localPos, worldPos, scale, offset, buffer);
            return;
        }

        IBakedModel model = resolveModel(state, localPos);
        IBlockState renderState = resolveRenderState(state, localPos);
        long rand = MathHelper.getPositionRandom(localPos);

        renderQuadsScaled(buffer, model.getQuads(renderState, null, rand), worldPos, scale, offset);
        if (faceVisibility.bottom)
            renderQuadsScaled(buffer, model.getQuads(renderState, EnumFacing.DOWN, rand), worldPos, scale, offset);
        if (faceVisibility.top)
            renderQuadsScaled(buffer, model.getQuads(renderState, EnumFacing.UP, rand), worldPos, scale, offset);
        if (faceVisibility.back)
            renderQuadsScaled(buffer, model.getQuads(renderState, EnumFacing.NORTH, rand), worldPos, scale, offset);
        if (faceVisibility.front)
            renderQuadsScaled(buffer, model.getQuads(renderState, EnumFacing.SOUTH, rand), worldPos, scale, offset);
        if (faceVisibility.left)
            renderQuadsScaled(buffer, model.getQuads(renderState, EnumFacing.WEST, rand), worldPos, scale, offset);
        if (faceVisibility.right)
            renderQuadsScaled(buffer, model.getQuads(renderState, EnumFacing.EAST, rand), worldPos, scale, offset);
    }

    // ========== Internal Helpers ==========

    private void renderFluidScaled(IBlockState state, BlockPos localPos, BlockPos worldPos,
                                   float scale, float offset, BufferBuilder buffer) {
        int firstVertex = buffer.getVertexCount();
        dispatcher.renderBlock(state, localPos, blockAccess, buffer);
        int lastVertex = buffer.getVertexCount();
        int vertexSize = buffer.getVertexFormat().getIntegerSize();
        IntBuffer vertices = buffer.getByteBuffer().duplicate()
                .order(ByteOrder.nativeOrder()).asIntBuffer();

        for (int vertex = firstVertex; vertex < lastVertex; vertex++) {
            int base = vertex * vertexSize;
            float x = Float.intBitsToFloat(vertices.get(base)) - localPos.getX();
            float y = Float.intBitsToFloat(vertices.get(base + 1)) - localPos.getY();
            float z = Float.intBitsToFloat(vertices.get(base + 2)) - localPos.getZ();
            vertices.put(base, Float.floatToRawIntBits(worldPos.getX() + offset + x * scale));
            vertices.put(base + 1, Float.floatToRawIntBits(worldPos.getY() + offset + y * scale));
            vertices.put(base + 2, Float.floatToRawIntBits(worldPos.getZ() + offset + z * scale));
            vertices.put(base + vertexSize - 1, FULL_BRIGHT);
        }
    }

    private IBakedModel resolveModel(IBlockState state, BlockPos pos) {
        IBlockState renderState = state;
        try {
            renderState = state.getActualState(blockAccess, pos);
        } catch (Exception ignored) {}
        IBakedModel model = dispatcher.getModelForState(renderState);
        return model;
    }

    private IBlockState resolveRenderState(IBlockState state, BlockPos pos) {
        IBlockState renderState = state;
        try {
            renderState = state.getActualState(blockAccess, pos);
        } catch (Exception ignored) {}
        try {
            renderState = renderState.getBlock().getExtendedState(renderState, blockAccess, pos);
        } catch (Exception ignored) {}
        return renderState;
    }

    private static void renderQuads(BufferBuilder buffer, List<BakedQuad> quads, BlockPos pos) {
        for (BakedQuad quad : quads) {
            buffer.addVertexData(quad.getVertexData());
            buffer.putBrightness4(FULL_BRIGHT, FULL_BRIGHT, FULL_BRIGHT, FULL_BRIGHT);
            buffer.putPosition(pos.getX(), pos.getY(), pos.getZ());
        }
    }

    /**
     * Write quad vertex data with per-vertex scaling applied.
     * BakedQuad vertices are in model space (0~1), so final position =
     *   worldPos + offset + modelVertex * scale
     */
    private static void renderQuadsScaled(BufferBuilder buffer, List<BakedQuad> quads,
                                          BlockPos worldPos, float scale, float offset) {
        float wx = worldPos.getX() + offset;
        float wy = worldPos.getY() + offset;
        float wz = worldPos.getZ() + offset;

        for (BakedQuad quad : quads) {
            int[] vertexData = quad.getVertexData().clone();
            for (int v = 0; v < VERTICES_PER_QUAD; v++) {
                int base = v * INTS_PER_VERTEX;
                float vx = Float.intBitsToFloat(vertexData[base]);
                float vy = Float.intBitsToFloat(vertexData[base + 1]);
                float vz = Float.intBitsToFloat(vertexData[base + 2]);
                vertexData[base] = Float.floatToRawIntBits(wx + vx * scale);
                vertexData[base + 1] = Float.floatToRawIntBits(wy + vy * scale);
                vertexData[base + 2] = Float.floatToRawIntBits(wz + vz * scale);
            }
            buffer.addVertexData(vertexData);
            buffer.putBrightness4(FULL_BRIGHT, FULL_BRIGHT, FULL_BRIGHT, FULL_BRIGHT);
            // putPosition(0,0,0) since positions are already absolute
            buffer.putPosition(0, 0, 0);
        }
    }
}
