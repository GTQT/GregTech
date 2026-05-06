package gregtech.client.renderer.godforge.util;

import java.util.HashSet;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;

import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.opengl.GL11;

public class TextureUpdateRequester {

    private final HashSet<Pair<Block, Integer>> blocks = new HashSet<>();

    public void add(Block block, int meta) {
        blocks.add(Pair.of(block, meta));
    }

    public void requestUpdate() {
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();

        DummyBlockAccess dummyAccess = new DummyBlockAccess();

        for (Pair<Block, Integer> block : blocks) {
            Block b = block.getLeft();
            if (b == Blocks.AIR) continue;
            IBlockState state = b.getStateFromMeta(block.getRight());
            try {
                dispatcher.renderBlock(state, BlockPos.ORIGIN, dummyAccess, buffer);
            } catch (Exception ignored) {}
        }

        buffer.finishDrawing();
        buffer.reset();
    }

    private static class DummyBlockAccess implements net.minecraft.world.IBlockAccess {

        @Override
        public net.minecraft.tileentity.TileEntity getTileEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return 15728880;
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return Blocks.AIR.getDefaultState();
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return true;
        }

        @Override
        public net.minecraft.world.biome.Biome getBiome(BlockPos pos) {
            return net.minecraft.world.biome.Biome.getBiome(0);
        }

        @Override
        public int getStrongPower(BlockPos pos, net.minecraft.util.EnumFacing direction) {
            return 0;
        }

        @Override
        public net.minecraft.world.WorldType getWorldType() {
            return net.minecraft.world.WorldType.DEFAULT;
        }

        @Override
        public boolean isSideSolid(BlockPos pos, net.minecraft.util.EnumFacing side, boolean _default) {
            return false;
        }
    }
}
