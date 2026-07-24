package gregtech.client.renderer.scene;

import gregtech.api.util.Mods;
import gregtech.client.utils.OptiFineHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.optifine.shaders.ShadersRender;

import org.lwjgl.opengl.GL11;

import java.util.Collection;
import java.util.Iterator;

@SideOnly(Side.CLIENT)
public class VBOWorldSceneRenderer extends ImmediateWorldSceneRenderer {

    // Per-instance VBO storage: each renderer owns its own vertex buffers
    protected final VertexBuffer[] vbos = new VertexBuffer[BlockRenderLayer.values().length];
    protected boolean isDirty = true;
    private BufferBuilder incrementalUploadBuffer;
    private Iterator<BlockPos> incrementalUploadIterator;
    private int incrementalUploadLayer;
    private int incrementalUploadBlockCount;
    private long incrementalUploadProcessedBlocks;
    private boolean incrementalUploadInProgress;

    public VBOWorldSceneRenderer(World world) {
        super(world);
    }

    /**
     * Release GPU resources held by this renderer's VBOs.
     * Should be called when the renderer is no longer needed.
     */
    @Override
    public void dispose() {
        cancelIncrementalUpload();
        for (int i = 0; i < vbos.length; i++) {
            if (vbos[i] != null) {
                vbos[i].deleteGlBuffers();
                vbos[i] = null;
            }
        }
    }

    private void uploadVBO() {
        cancelIncrementalUpload();
        BlockRenderLayer oldRenderLayer = MinecraftForgeClient.getRenderLayer();

        try { // render block in each layer
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {

                OptiFineHelper.preRenderChunkLayer(layer);

                renderBlockLayer(layer);

                // Get the buffer again
                BufferBuilder buffer = Tessellator.getInstance().getBuffer();
                buffer.finishDrawing();
                buffer.reset();

                int i = layer.ordinal();
                var vbo = vbos[i];
                if (vbo == null) vbo = vbos[i] = new VertexBuffer(DefaultVertexFormats.BLOCK);
                vbo.bufferData(buffer.getByteBuffer());

                OptiFineHelper.postRenderChunkLayer(layer);
            }
        } finally {
            ForgeHooksClient.setRenderLayer(oldRenderLayer);
        }
        this.isDirty = false;
    }

    /**
     * Uploads a bounded number of blocks into this renderer's VBOs. This must run on the render thread; it exists so
     * JEI can keep drawing a loading indicator while a very large preview is meshed.
     *
     * @return {@code true} when all render layers have been uploaded
     */
    public boolean uploadVBOChunk(int maximumBlocks) {
        if (!isDirty) {
            return true;
        }
        if (maximumBlocks <= 0) {
            return false;
        }
        if (!incrementalUploadInProgress) {
            startIncrementalUpload();
        }

        int remaining = maximumBlocks;
        BlockRenderLayer oldRenderLayer = MinecraftForgeClient.getRenderLayer();
        try {
            while (remaining > 0 && incrementalUploadLayer < BlockRenderLayer.values().length) {
                BlockRenderLayer layer = BlockRenderLayer.values()[incrementalUploadLayer];
                if (incrementalUploadBuffer == null) {
                    incrementalUploadBuffer = new BufferBuilder(2_097_152);
                    incrementalUploadBuffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                    incrementalUploadIterator = renderedBlocks.iterator();
                }

                ForgeHooksClient.setRenderLayer(layer);
                int pass = layer == BlockRenderLayer.TRANSLUCENT ? 1 : 0;
                setDefaultPassRenderState(pass);
                OptiFineHelper.preRenderChunkLayer(layer);
                try {
                    while (remaining > 0 && incrementalUploadIterator.hasNext()) {
                        renderBlock(layer, incrementalUploadIterator.next(), incrementalUploadBuffer);
                        remaining--;
                        incrementalUploadProcessedBlocks++;
                    }
                } finally {
                    OptiFineHelper.postRenderChunkLayer(layer);
                }

                if (incrementalUploadIterator.hasNext()) {
                    break;
                }

                incrementalUploadBuffer.finishDrawing();
                incrementalUploadBuffer.reset();
                int layerIndex = layer.ordinal();
                VertexBuffer vbo = vbos[layerIndex];
                if (vbo == null) {
                    vbo = vbos[layerIndex] = new VertexBuffer(DefaultVertexFormats.BLOCK);
                }
                vbo.bufferData(incrementalUploadBuffer.getByteBuffer());
                incrementalUploadBuffer = null;
                incrementalUploadIterator = null;
                incrementalUploadLayer++;
            }
        } finally {
            ForgeHooksClient.setRenderLayer(oldRenderLayer);
        }

        if (incrementalUploadLayer >= BlockRenderLayer.values().length) {
            incrementalUploadInProgress = false;
            isDirty = false;
        }
        return !isDirty;
    }

    /**
     * @return a best-effort mesh upload progress value in the range {@code [0, 1]}.
     */
    public float getVBOUploadProgress() {
        if (!isDirty) {
            return 1.0F;
        }
        int layerCount = BlockRenderLayer.values().length;
        if (!incrementalUploadInProgress) {
            return 0.0F;
        }
        long total = (long) Math.max(1, incrementalUploadBlockCount) * layerCount;
        return Math.min(1.0F, (float) incrementalUploadProcessedBlocks / total);
    }

    private void startIncrementalUpload() {
        cancelIncrementalUpload();
        incrementalUploadInProgress = true;
        incrementalUploadLayer = 0;
        incrementalUploadBlockCount = renderedBlocks.size();
        incrementalUploadProcessedBlocks = 0L;
    }

    private void cancelIncrementalUpload() {
        incrementalUploadBuffer = null;
        incrementalUploadIterator = null;
        incrementalUploadLayer = 0;
        incrementalUploadBlockCount = 0;
        incrementalUploadProcessedBlocks = 0L;
        incrementalUploadInProgress = false;
    }

    @Override
    protected void drawWorld() {
        if (this.isDirty) {
            uploadVBO();
        }
        if (beforeRender != null) {
            beforeRender.accept(this);
        }

        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.enableCull();
        GlStateManager.enableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
        mc.entityRenderer.disableLightmap();
        mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();

        var oldRenderLayer = MinecraftForgeClient.getRenderLayer();
        for (var layer : BlockRenderLayer.values()) {

            ForgeHooksClient.setRenderLayer(layer);

            int pass = layer == BlockRenderLayer.TRANSLUCENT ? 1 : 0;
            setDefaultPassRenderState(pass);

            OptiFineHelper.preRenderChunkLayer(layer);

            GlStateManager.pushMatrix();
            {
                int i = layer.ordinal();
                var vbo = vbos[i];
                vbo.bindBuffer();
                enableClientStates();
                setupArrayPointers();
                vbo.drawArrays(GL11.GL_QUADS);
                disableClientStates();
                vbo.unbindBuffer();
            }
            GlStateManager.popMatrix();

            OptiFineHelper.postRenderChunkLayer(layer);
        }
        ForgeHooksClient.setRenderLayer(oldRenderLayer);

        renderTileEntities(); // Handle TileEntities

        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        RenderHelper.enableStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);

        if (afterRender != null) {
            afterRender.accept(this);
        }
    }

    @Override
    public WorldSceneRenderer addRenderedBlocks(Collection<BlockPos> blocks) {
        this.isDirty = true;
        cancelIncrementalUpload();
        return super.addRenderedBlocks(blocks);
    }

    protected void enableClientStates() {
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.glEnableClientState(GL11.GL_COLOR_ARRAY);
    }

    protected void disableClientStates() {
        for (VertexFormatElement element : DefaultVertexFormats.BLOCK.getElements()) {
            switch (element.getUsage()) {
                case POSITION -> GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
                case COLOR -> GlStateManager.glDisableClientState(GL11.GL_COLOR_ARRAY);
                case UV -> {
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + element.getIndex());
                    GlStateManager.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                }
                default -> {}
            }
        }
    }

    protected void setupArrayPointers() {
        if (Mods.ShadersMod.isModLoaded()) {
            ShadersRender.setupArrayPointersVbo();
        } else {
            // 28 == DefaultVertexFormats.BLOCK.getSize();
            GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 28, 0);
            GlStateManager.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 28, 12);
            GlStateManager.glTexCoordPointer(2, GL11.GL_FLOAT, 28, 16);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.glTexCoordPointer(2, GL11.GL_SHORT, 28, 24);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
        }
    }
}
