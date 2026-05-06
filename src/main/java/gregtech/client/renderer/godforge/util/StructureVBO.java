package gregtech.client.renderer.godforge.util;

import java.util.HashMap;
import java.util.HashSet;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;

import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.opengl.GL11;

public class StructureVBO {

    private String[][] structure;
    private final HashSet<Character> values = new HashSet<>();
    private final HashMap<Character, Pair<Block, Integer>> mapper = new HashMap<>();

    private final VertexBuffer[] vbos = new VertexBuffer[BlockRenderLayer.values().length];
    private boolean built = false;

    private StructureBlockAccess blockAccess;

    public StructureVBO assignStructure(String[][] structure) {
        this.structure = structure;
        return this;
    }

    public StructureVBO addMapping(char letter, Block block) {
        mapper.put(letter, Pair.of(block, 0));
        return this;
    }

    public StructureVBO addMapping(char letter, Block block, int meta) {
        mapper.put(letter, Pair.of(block, meta));
        return this;
    }

    public TextureUpdateRequester getTextureUpdateRequestor() {
        TextureUpdateRequester textureUpdateRequester = new TextureUpdateRequester();
        for (char key : mapper.keySet()) {
            Pair<Block, Integer> pair = mapper.get(key);
            textureUpdateRequester.add(pair.getLeft(), pair.getRight());
        }
        return textureUpdateRequester;
    }

    public void build() {
        blockAccess = new StructureBlockAccess(structure, mapper);

        BlockRenderLayer oldRenderLayer = MinecraftForgeClient.getRenderLayer();

        try {
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                ForgeHooksClient.setRenderLayer(layer);

                BufferBuilder buffer = Tessellator.getInstance().getBuffer();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                BlockRendererDispatcher dispatcher = Minecraft.getMinecraft().getBlockRendererDispatcher();

                int sizeX = structure.length;
                int sizeY = structure[0].length;
                int sizeZ = structure[0][0].length();

                for (int x = 0; x < sizeX; x++) {
                    String[] plane = structure[x];
                    for (int y = 0; y < plane.length; y++) {
                        String row = plane[y];
                        for (int z = 0; z < row.length(); z++) {
                            char letter = row.charAt(z);
                            if (letter == ' ') continue;
                            Pair<Block, Integer> info = mapper.get(letter);
                            if (info == null) {
                                values.add(letter);
                                continue;
                            }
                            if (info.getLeft() == Blocks.AIR) continue;

                            BlockPos renderPos = new BlockPos(
                                    x - sizeX / 2,
                                    sizeY / 2 - y,
                                    z - sizeZ / 2);

                            IBlockState state = blockAccess.getBlockState(renderPos);
                            Block block = state.getBlock();
                            if (block == Blocks.AIR) continue;
                            if (block.canRenderInLayer(state, layer)) {
                                dispatcher.renderBlock(state, renderPos, blockAccess, buffer);
                            }
                        }
                    }
                }

                buffer.finishDrawing();
                VertexBuffer vbo = new VertexBuffer(DefaultVertexFormats.BLOCK);
                vbo.bufferData(buffer.getByteBuffer());
                vbos[layer.ordinal()] = vbo;
                buffer.reset();
            }
        } finally {
            ForgeHooksClient.setRenderLayer(oldRenderLayer);
        }

        built = true;
    }

    public void render() {
        if (!built) return;

        BlockRenderLayer oldRenderLayer = MinecraftForgeClient.getRenderLayer();

        try {
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {
                VertexBuffer vbo = vbos[layer.ordinal()];
                if (vbo == null) continue;

                ForgeHooksClient.setRenderLayer(layer);

                vbo.bindBuffer();
                GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
                GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);

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

    public void delete() {
        for (int i = 0; i < vbos.length; i++) {
            if (vbos[i] != null) {
                vbos[i].deleteGlBuffers();
                vbos[i] = null;
            }
        }
        built = false;
    }
}
