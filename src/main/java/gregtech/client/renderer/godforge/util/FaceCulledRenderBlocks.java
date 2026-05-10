package gregtech.client.renderer.godforge.util;

import java.util.List;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;

public class FaceCulledRenderBlocks {

    private static final int FULL_BRIGHT = 15728880;

    private final IBlockAccess blockAccess;
    private final BlockRendererDispatcher dispatcher;

    public FaceCulledRenderBlocks(IBlockAccess blockAccess) {
        this.blockAccess = blockAccess;
        this.dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();
    }

    public void renderBlock(IBlockState state, BlockPos pos, FaceVisibility faceVisibility, BufferBuilder buffer) {
        IBlockState renderState = state;
        try {
            renderState = state.getActualState(blockAccess, pos);
        } catch (Exception ignored) {}

        IBakedModel model = dispatcher.getModelForState(renderState);

        try {
            renderState = renderState.getBlock().getExtendedState(renderState, blockAccess, pos);
        } catch (Exception ignored) {}

        long rand = MathHelper.getPositionRandom(pos);
        renderQuads(buffer, model.getQuads(renderState, null, rand), pos);

        if (faceVisibility.bottom) renderQuads(buffer, model.getQuads(renderState, EnumFacing.DOWN, rand), pos);
        if (faceVisibility.top) renderQuads(buffer, model.getQuads(renderState, EnumFacing.UP, rand), pos);
        if (faceVisibility.back) renderQuads(buffer, model.getQuads(renderState, EnumFacing.NORTH, rand), pos);
        if (faceVisibility.front) renderQuads(buffer, model.getQuads(renderState, EnumFacing.SOUTH, rand), pos);
        if (faceVisibility.left) renderQuads(buffer, model.getQuads(renderState, EnumFacing.WEST, rand), pos);
        if (faceVisibility.right) renderQuads(buffer, model.getQuads(renderState, EnumFacing.EAST, rand), pos);
    }

    private static void renderQuads(BufferBuilder buffer, List<BakedQuad> quads, BlockPos pos) {
        for (BakedQuad quad : quads) {
            buffer.addVertexData(quad.getVertexData());
            buffer.putBrightness4(FULL_BRIGHT, FULL_BRIGHT, FULL_BRIGHT, FULL_BRIGHT);
            buffer.putPosition(pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
