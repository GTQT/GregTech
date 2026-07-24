package gregtech.client.renderer.scene;

import gregtech.api.metatileentity.IFastRenderMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.util.Position;
import gregtech.api.util.PositionedRect;
import gregtech.api.util.Size;
import gregtech.client.renderer.handler.MetaTileEntityRenderer;
import gregtech.client.utils.RenderUtil;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.vec.Vector3;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.vecmath.Vector3f;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: KilaBash
 * @Date: 2021/08/23
 * @Description: Abstract class, and extend a lot of features compared with the original one.
 */
@SideOnly(Side.CLIENT)
public abstract class WorldSceneRenderer {

    protected static final FloatBuffer MODELVIEW_MATRIX_BUFFER = ByteBuffer.allocateDirect(16 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    protected static final FloatBuffer PROJECTION_MATRIX_BUFFER = ByteBuffer.allocateDirect(16 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    protected static final IntBuffer VIEWPORT_BUFFER = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder())
            .asIntBuffer();
    protected static final FloatBuffer PIXEL_DEPTH_BUFFER = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            .asFloatBuffer();
    protected static final FloatBuffer OBJECT_POS_BUFFER = ByteBuffer.allocateDirect(3 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
    // Reusable buffer for glClipPlane (4 doubles = 32 bytes)
    protected static final DoubleBuffer CLIP_PLANE_BUFFER = ByteBuffer.allocateDirect(4 * 8)
            .order(ByteOrder.nativeOrder()).asDoubleBuffer();

    // Per-instance tile entity storage for TESR rendering
    protected final Map<BlockPos, TileEntity> tileEntities = new Object2ObjectArrayMap<>();
    public final World world;
    public final Collection<BlockPos> renderedBlocks = new ObjectOpenHashSet<>();
    protected Consumer<WorldSceneRenderer> beforeRender;
    protected Consumer<WorldSceneRenderer> afterRender;
    private Consumer<RayTraceResult> onLookingAt;
    private int clearColor;
    private RayTraceResult lastTraceResult;
    private Vector3f eyePos = new Vector3f(0, 0, 10f);
    private Vector3f lookAt = new Vector3f(0, 0, 0);
    private Vector3f worldUp = new Vector3f(0, 1, 0);

    // TESR rendering limits
    private int maxTileEntityRenderers = Integer.MAX_VALUE;
    private double maxTileEntityRenderDistSq = Double.MAX_VALUE;
    private Predicate<TileEntity> tileEntityFilter;

    // Hit test throttling: reduce glReadPixels calls
    private int hitTestInterval = 1;
    private int frameCount;

    // Internal block culling: remove blocks fully enclosed by other blocks
    private boolean cullInternal;

    // Y-axis clip planes for layer filtering (avoids VBO rebuild on layer switch)
    private boolean clipEnabled;
    private double clipMinY;
    private double clipMaxY;

    public WorldSceneRenderer(World world) {
        this.world = world;
    }

    /**
     * Release renderer-owned resources when a preview is no longer active.
     * Base renderers do not own GPU buffers; VBO/FBO implementations override this.
     */
    public void dispose() {}

    public WorldSceneRenderer setBeforeWorldRender(Consumer<WorldSceneRenderer> callback) {
        this.beforeRender = callback;
        return this;
    }

    public WorldSceneRenderer setAfterWorldRender(Consumer<WorldSceneRenderer> callback) {
        this.afterRender = callback;
        return this;
    }

    public WorldSceneRenderer addRenderedBlocks(@Nullable Collection<BlockPos> blocks) {
        if (blocks != null) {
            this.renderedBlocks.addAll(blocks);

            // Internal block culling: remove blocks fully enclosed by 6 non-air neighbors
            if (cullInternal) {
                List<BlockPos> toRemove = new ArrayList<>();
                for (BlockPos pos : this.renderedBlocks) {
                    if (isFullyEnclosed(pos)) {
                        toRemove.add(pos);
                    }
                }
                this.renderedBlocks.removeAll(toRemove);
            }

            tileEntities.clear();
            this.renderedBlocks.forEach(pos -> {
                TileEntity tile = world.getTileEntity(pos);
                if (tile != null && (!(tile instanceof IGregTechTileEntity gtte) ||
                        // Put MTEs only when it has FastRenderer
                        gtte.getMetaTileEntity() instanceof IFastRenderMetaTileEntity)) {
                    tileEntities.put(pos, tile);
                }
            });
        }
        return this;
    }

    /**
     * Check if a block position is fully enclosed by opaque full-cube blocks on all 6 faces.
     * A block is considered enclosed only if every neighbor is present in the rendered set,
     * is not air, AND is a full cube (so transparent/partial blocks like glass or slabs
     * do not count as occluders).
     */
    @SuppressWarnings("deprecation")
    private boolean isFullyEnclosed(BlockPos pos) {
        for (EnumFacing facing : EnumFacing.VALUES) {
            BlockPos neighbor = pos.offset(facing);
            if (!this.renderedBlocks.contains(neighbor)) return false;
            IBlockState state = world.getBlockState(neighbor);
            if (state.getBlock() == Blocks.AIR) return false;
            if (!state.isFullCube()) return false;
        }
        return true;
    }

    public WorldSceneRenderer setOnLookingAt(Consumer<RayTraceResult> onLookingAt) {
        this.onLookingAt = onLookingAt;
        return this;
    }

    /**
     * Set the maximum number of TileEntity renderers (TESR) that will be processed per frame.
     * When the structure has more TEs than this limit, excess TEs are skipped.
     * Default is Integer.MAX_VALUE (no limit).
     *
     * @param max maximum number of TESRs to render per frame
     * @return this renderer for chaining
     */
    public WorldSceneRenderer setMaxTileEntityRenderers(int max) {
        this.maxTileEntityRenderers = max;
        return this;
    }

    /**
     * Set the maximum distance (squared) from camera at which TESRs are rendered.
     * TEs beyond this distance are skipped. Default is Double.MAX_VALUE (no limit).
     *
     * @param maxDist maximum render distance (not squared)
     * @return this renderer for chaining
     */
    public WorldSceneRenderer setMaxTileEntityRenderDistance(double maxDist) {
        this.maxTileEntityRenderDistSq = maxDist * maxDist;
        return this;
    }

    /**
     * Set a custom filter for which TileEntities should have their TESR rendered.
     * Only TEs that pass this filter will be rendered. Default is null (all TEs rendered).
     *
     * @param filter predicate that returns true for TEs that should be rendered
     * @return this renderer for chaining
     */
    public WorldSceneRenderer setTileEntityFilter(@Nullable Predicate<TileEntity> filter) {
        this.tileEntityFilter = filter;
        return this;
    }

    /**
     * Set the hit test interval (in frames). The glReadPixels-based mouse hit detection
     * will only run every N frames, reusing the previous result in between.
     * This eliminates the GPU pipeline sync stall that occurs every frame.
     * Default is 1 (every frame). Recommended: 3-5 for large structures.
     *
     * @param interval number of frames between hit tests (minimum 1)
     * @return this renderer for chaining
     */
    public WorldSceneRenderer setHitTestInterval(int interval) {
        this.hitTestInterval = Math.max(1, interval);
        return this;
    }

    /**
     * Enable internal block culling. When enabled, blocks that are fully enclosed
     * (all 6 neighbors are non-air and present in the rendered set) are removed from
     * the render list. This significantly reduces vertex count for large solid structures.
     * Must be called BEFORE {@link #addRenderedBlocks(Collection)}.
     *
     * @param cull true to enable internal culling
     * @return this renderer for chaining
     */
    public WorldSceneRenderer setCullInternalBlocks(boolean cull) {
        this.cullInternal = cull;
        return this;
    }

    /**
     * Set Y-axis clip planes for layer filtering. When enabled, only geometry within
     * [minY, maxY) is rendered. This uses GL clip planes and does NOT require VBO rebuild.
     * Pass -1 for both to disable clipping (show all layers).
     *
     * @param minY lower Y boundary (inclusive)
     * @param maxY upper Y boundary (exclusive)
     * @return this renderer for chaining
     */
    public WorldSceneRenderer setClipPlanes(double minY, double maxY) {
        this.clipEnabled = true;
        this.clipMinY = minY;
        this.clipMaxY = maxY;
        return this;
    }

    /**
     * Disable Y-axis clip planes, showing all layers.
     *
     * @return this renderer for chaining
     */
    public WorldSceneRenderer disableClipPlanes() {
        this.clipEnabled = false;
        return this;
    }

    /**
     * @return true if Y-axis clip planes are currently active
     */
    public boolean isClipEnabled() {
        return clipEnabled;
    }

    /**
     * @return the minimum Y of the active clip range, only valid when {@link #isClipEnabled()} is true
     */
    public double getClipMinY() {
        return clipMinY;
    }

    /**
     * @return the maximum Y of the active clip range, only valid when {@link #isClipEnabled()} is true
     */
    public double getClipMaxY() {
        return clipMaxY;
    }

    public void setClearColor(int clearColor) {
        this.clearColor = clearColor;
    }

    public RayTraceResult getLastTraceResult() {
        return lastTraceResult;
    }

    public void render(float x, float y, float width, float height, int mouseX, int mouseY) {
        // setupCamera
        PositionedRect positionedRect = getPositionedRect((int) x, (int) y, (int) width, (int) height);
        PositionedRect mouse = getPositionedRect(mouseX, mouseY, 0, 0);
        mouseX = mouse.position.x;
        mouseY = mouse.position.y;
        setupCamera(positionedRect);

        // Enable Y-axis clip planes for layer filtering
        if (clipEnabled) {
            enableYClipPlanes(clipMinY, clipMaxY);
        }

        // render TrackedDummyWorld
        drawWorld();

        // Disable clip planes before hit test (unProject needs unclipped depth buffer)
        if (clipEnabled) {
            disableYClipPlanes();
        }

        // check lookingAt (throttled: only run every hitTestInterval frames)
        frameCount++;
        if (frameCount % hitTestInterval == 0) {
            this.lastTraceResult = null;
            if (onLookingAt != null && mouseX > positionedRect.position.x &&
                    mouseX < positionedRect.position.x + positionedRect.size.width &&
                    mouseY > positionedRect.position.y &&
                    mouseY < positionedRect.position.y + positionedRect.size.height) {
                Vector3f hitPos = unProject(mouseX, mouseY);
                RayTraceResult result = rayTrace(hitPos);
                // Filter out hits outside clip range
                if (result != null && clipEnabled && result.getBlockPos() != null) {
                    int hitY = result.getBlockPos().getY();
                    if (hitY < clipMinY || hitY >= clipMaxY) {
                        result = null;
                    }
                }
                if (result != null) {
                    this.lastTraceResult = result;
                    onLookingAt.accept(result);
                }
            }
        }
        // resetCamera
        resetCamera();
    }

    public Vector3f getEyePos() {
        return eyePos;
    }

    public Vector3f getLookAt() {
        return lookAt;
    }

    public Vector3f getWorldUp() {
        return worldUp;
    }

    public void setCameraLookAt(Vector3f eyePos, Vector3f lookAt, Vector3f worldUp) {
        this.eyePos = eyePos;
        this.lookAt = lookAt;
        this.worldUp = worldUp;
    }

    public void setCameraLookAt(Vector3f lookAt, double radius, double rotationPitch, double rotationYaw) {
        this.lookAt = lookAt;
        Vector3 vecX = new Vector3(Math.cos(rotationPitch), 0, Math.sin(rotationPitch));
        Vector3 vecY = new Vector3(0, Math.tan(rotationYaw) * vecX.mag(), 0);
        Vector3 pos = vecX.copy().add(vecY).normalize().multiply(radius);
        this.eyePos = pos.add(lookAt.x, lookAt.y, lookAt.z).vector3f();
    }

    protected PositionedRect getPositionedRect(int x, int y, int width, int height) {
        return new PositionedRect(new Position(x, y), new Size(width, height));
    }

    protected void setupCamera(PositionedRect positionedRect) {
        int x = positionedRect.getPosition().x;
        int y = positionedRect.getPosition().y;
        int width = positionedRect.getSize().width;
        int height = positionedRect.getSize().height;

        GlStateManager.pushAttrib();

        Minecraft.getMinecraft().entityRenderer.disableLightmap();
        GlStateManager.disableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();

        // setup viewport and clear GL buffers
        GlStateManager.viewport(x, y, width, height);

        clearView(x, y, width, height);

        // setup projection matrix to perspective
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();

        float aspectRatio = width / (height * 1.0f);
        GLU.gluPerspective(60.0f, aspectRatio, 0.1f, 10000.0f);

        // setup modelview matrix
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GLU.gluLookAt(eyePos.x, eyePos.y, eyePos.z, lookAt.x, lookAt.y, lookAt.z, worldUp.x, worldUp.y, worldUp.z);
    }

    protected void clearView(int x, int y, int width, int height) {
        RenderUtil.setGlClearColorFromInt(clearColor, clearColor >> 24);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    protected static void resetCamera() {
        // reset viewport
        Minecraft minecraft = Minecraft.getMinecraft();
        GlStateManager.viewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);

        // reset projection matrix
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();

        // reset modelview matrix
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();

        GlStateManager.disableBlend();
        GlStateManager.disableDepth();

        // reset attributes
        GlStateManager.popAttrib();
    }

    protected void drawWorld() {
        if (beforeRender != null) {
            beforeRender.accept(this);
        }

        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.enableCull();
        GlStateManager.enableRescaleNormal();
        RenderHelper.disableStandardItemLighting();
        mc.entityRenderer.disableLightmap();
        mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        BlockRenderLayer oldRenderLayer = MinecraftForgeClient.getRenderLayer();
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();

        try { // render block in each layer
            for (BlockRenderLayer layer : BlockRenderLayer.values()) {

                renderBlockLayer(layer);

                Tessellator.getInstance().draw();
                Tessellator.getInstance().getBuffer().setTranslation(0, 0, 0);
            }
        } finally {
            ForgeHooksClient.setRenderLayer(oldRenderLayer);
        }

        renderTileEntities(); // Handle TileEntities

        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);

        if (afterRender != null) {
            afterRender.accept(this);
        }
    }

    protected void renderBlockLayer(BlockRenderLayer layer) {
        ForgeHooksClient.setRenderLayer(layer);
        int pass = layer == BlockRenderLayer.TRANSLUCENT ? 1 : 0;
        setDefaultPassRenderState(pass);

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);

        for (BlockPos pos : renderedBlocks) {
            renderBlock(layer, pos, buffer);
        }
    }

    /**
     * Appends one block to a supplied render buffer. VBO previews use this to spread an initial mesh upload across
     * several JEI frames while retaining the same rendering path as immediate scenes.
     */
    protected void renderBlock(BlockRenderLayer layer, BlockPos pos, BufferBuilder buffer) {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        state = state.getActualState(world, pos);
        if (block == Blocks.AIR || !block.canRenderInLayer(state, layer)) {
            return;
        }
        if (block.getRenderType(state) == MetaTileEntityRenderer.BLOCK_RENDER_TYPE) {
            MetaTileEntityRenderer.INSTANCE.renderBlock(world, pos, state, buffer);
        } else {
            Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(state, pos, world, buffer);
        }
    }

    protected void renderTileEntities() {
        RenderHelper.enableStandardItemLighting();
        var dispatcher = TileEntityRendererDispatcher.instance;
        for (int pass = 0; pass < 2; pass++) {
            ForgeHooksClient.setRenderPass(pass);
            setDefaultPassRenderState(pass);

            int finalPass = pass;
            int rendered = 0;
            for (Map.Entry<BlockPos, TileEntity> entry : tileEntities.entrySet()) {
                if (rendered >= maxTileEntityRenderers) break;

                BlockPos pos = entry.getKey();
                TileEntity tile = entry.getValue();

                if (!tile.shouldRenderInPass(finalPass)) continue;

                // Distance culling
                if (maxTileEntityRenderDistSq < Double.MAX_VALUE) {
                    double dx = pos.getX() + 0.5 - eyePos.x;
                    double dy = pos.getY() + 0.5 - eyePos.y;
                    double dz = pos.getZ() + 0.5 - eyePos.z;
                    if (dx * dx + dy * dy + dz * dz > maxTileEntityRenderDistSq) continue;
                }

                // Custom filter
                if (tileEntityFilter != null && !tileEntityFilter.test(tile)) continue;

                dispatcher.render(tile, pos.getX(), pos.getY(), pos.getZ(), 0);
                rendered++;
            }
        }
        ForgeHooksClient.setRenderPass(-1);
        RenderHelper.disableStandardItemLighting();
    }

    public static void setDefaultPassRenderState(int pass) {
        GlStateManager.color(1, 1, 1, 1);
        if (pass == 0) { // SOLID
            GlStateManager.enableDepth();
            GlStateManager.disableBlend();
            GlStateManager.depthMask(true);
        } else { // TRANSLUCENT
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.depthMask(false);
        }
    }

    /**
     * Enable two Y-axis clip planes to restrict rendering to a horizontal slice.
     * GL_CLIP_PLANE0: clips below minY (normal pointing up)
     * GL_CLIP_PLANE1: clips above maxY (normal pointing down)
     */
    protected void enableYClipPlanes(double minY, double maxY) {
        // Clip plane equation: Ax + By + Cz + D >= 0 passes
        CLIP_PLANE_BUFFER.clear();
        // Bottom plane: y >= minY  →  0*x + 1*y + 0*z + (-minY) >= 0
        CLIP_PLANE_BUFFER.put(0).put(1).put(0).put(-minY);
        CLIP_PLANE_BUFFER.flip();
        GL11.glClipPlane(GL11.GL_CLIP_PLANE0, CLIP_PLANE_BUFFER);
        GL11.glEnable(GL11.GL_CLIP_PLANE0);

        CLIP_PLANE_BUFFER.clear();
        // Top plane: y < maxY  →  0*x + (-1)*y + 0*z + maxY > 0
        CLIP_PLANE_BUFFER.put(0).put(-1).put(0).put(maxY);
        CLIP_PLANE_BUFFER.flip();
        GL11.glClipPlane(GL11.GL_CLIP_PLANE1, CLIP_PLANE_BUFFER);
        GL11.glEnable(GL11.GL_CLIP_PLANE1);
    }

    /**
     * Disable Y-axis clip planes.
     */
    protected void disableYClipPlanes() {
        GL11.glDisable(GL11.GL_CLIP_PLANE0);
        GL11.glDisable(GL11.GL_CLIP_PLANE1);
    }

    public RayTraceResult rayTrace(Vector3f hitPos) {
        Vec3d startPos = new Vec3d(this.eyePos.x, this.eyePos.y, this.eyePos.z);
        hitPos.scale(2); // Double view range to ensure pos can be seen.
        Vec3d endPos = new Vec3d((hitPos.x - startPos.x), (hitPos.y - startPos.y), (hitPos.z - startPos.z));
        return this.world.rayTraceBlocks(startPos, endPos);
    }

    public static Vector3f project(BlockPos pos) {
        // read current rendering parameters
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW_MATRIX_BUFFER);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION_MATRIX_BUFFER);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT_BUFFER);

        // rewind buffers after write by OpenGL glGet calls
        MODELVIEW_MATRIX_BUFFER.rewind();
        PROJECTION_MATRIX_BUFFER.rewind();
        VIEWPORT_BUFFER.rewind();

        // call gluProject with retrieved parameters
        GLU.gluProject(pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, MODELVIEW_MATRIX_BUFFER,
                PROJECTION_MATRIX_BUFFER, VIEWPORT_BUFFER, OBJECT_POS_BUFFER);

        // rewind buffers after read by gluProject
        VIEWPORT_BUFFER.rewind();
        PROJECTION_MATRIX_BUFFER.rewind();
        MODELVIEW_MATRIX_BUFFER.rewind();

        // rewind buffer after write by gluProject
        OBJECT_POS_BUFFER.rewind();

        // obtain position in Screen
        float winX = OBJECT_POS_BUFFER.get();
        float winY = OBJECT_POS_BUFFER.get();
        float winZ = OBJECT_POS_BUFFER.get();

        // rewind buffer after read
        OBJECT_POS_BUFFER.rewind();

        return new Vector3f(winX, winY, winZ);
    }

    public static Vector3f unProject(int mouseX, int mouseY) {
        // read depth of pixel under mouse
        GL11.glReadPixels(mouseX, mouseY, 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, PIXEL_DEPTH_BUFFER);

        // rewind buffer after write by glReadPixels
        PIXEL_DEPTH_BUFFER.rewind();

        // retrieve depth from buffer (0.0-1.0f)
        float pixelDepth = PIXEL_DEPTH_BUFFER.get();

        // rewind buffer after read
        PIXEL_DEPTH_BUFFER.rewind();

        // read current rendering parameters
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW_MATRIX_BUFFER);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION_MATRIX_BUFFER);
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT_BUFFER);

        // rewind buffers after write by OpenGL glGet calls
        MODELVIEW_MATRIX_BUFFER.rewind();
        PROJECTION_MATRIX_BUFFER.rewind();
        VIEWPORT_BUFFER.rewind();

        // call gluUnProject with retrieved parameters
        GLU.gluUnProject(mouseX, mouseY, pixelDepth, MODELVIEW_MATRIX_BUFFER, PROJECTION_MATRIX_BUFFER, VIEWPORT_BUFFER,
                OBJECT_POS_BUFFER);

        // rewind buffers after read by gluUnProject
        VIEWPORT_BUFFER.rewind();
        PROJECTION_MATRIX_BUFFER.rewind();
        MODELVIEW_MATRIX_BUFFER.rewind();

        // rewind buffer after write by gluUnProject
        OBJECT_POS_BUFFER.rewind();

        // obtain absolute position in world
        float posX = OBJECT_POS_BUFFER.get();
        float posY = OBJECT_POS_BUFFER.get();
        float posZ = OBJECT_POS_BUFFER.get();

        // rewind buffer after read
        OBJECT_POS_BUFFER.rewind();

        return new Vector3f(posX, posY, posZ);
    }

    /***
     * For better performance, You'd better handle the event {@link #setOnLookingAt(Consumer)} or
     * {@link #getLastTraceResult()}
     *
     * @param mouseX xPos in Texture
     * @param mouseY yPos in Texture
     * @return RayTraceResult Hit
     */
    protected RayTraceResult screenPos2BlockPosFace(int mouseX, int mouseY, int x, int y, int width, int height) {
        // render a frame
        GlStateManager.enableDepth();
        setupCamera(getPositionedRect(x, y, width, height));

        drawWorld();

        Vector3f hitPos = unProject(mouseX, mouseY);
        RayTraceResult result = rayTrace(hitPos);

        resetCamera();

        return result;
    }

    /***
     * For better performance, You'd better do project in {@link #setAfterWorldRender(Consumer)}
     *
     * @param pos   BlockPos
     * @param depth should pass Depth Test
     * @return x, y, z
     */
    protected Vector3f blockPos2ScreenPos(BlockPos pos, boolean depth, int x, int y, int width, int height) {
        // render a frame
        GlStateManager.enableDepth();
        setupCamera(getPositionedRect(x, y, width, height));

        drawWorld();
        Vector3f winPos = project(pos);

        resetCamera();

        return winPos;
    }
}
