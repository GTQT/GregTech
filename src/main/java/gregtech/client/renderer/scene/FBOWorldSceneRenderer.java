package gregtech.client.renderer.scene;

import gregtech.api.util.Position;
import gregtech.api.util.PositionedRect;
import gregtech.api.util.Size;
import gregtech.api.util.GTLog;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;

import javax.vecmath.Vector3f;

/**
 * Created with IntelliJ IDEA.
 *
 * @Author: KilaBash
 * @Date: 2021/08/23
 * @Description: FBO-based offscreen renderer that inherits VBO caching from
 *               {@link VBOWorldSceneRenderer}. Renders the scene into an FBO texture
 *               and draws the result as a textured quad.
 *
 *               Supports dirty-flag mechanism: the FBO is only re-rendered when
 *               the scene actually changes (camera movement, layer switch, structure switch).
 *               On clean frames, only the cached FBO texture quad is drawn (~zero cost).
 */
@SideOnly(Side.CLIENT)
public class FBOWorldSceneRenderer extends VBOWorldSceneRenderer {

    private int resolutionWidth = 1080;
    private int resolutionHeight = 1080;
    private Framebuffer fbo;

    // Dirty flag: when true, the FBO content needs to be re-rendered
    private boolean fboDirty = true;

    public FBOWorldSceneRenderer(World world, int resolutionWidth, int resolutionHeight) {
        super(world);
        setFBOSize(resolutionWidth, resolutionHeight);
    }

    public FBOWorldSceneRenderer(World world, Framebuffer fbo) {
        super(world);
        this.fbo = fbo;
    }

    public int getResolutionWidth() {
        return resolutionWidth;
    }

    public int getResolutionHeight() {
        return resolutionHeight;
    }

    /**
     * Mark the FBO content as needing re-render. Call this when the camera moves,
     * the layer changes, or the structure switches.
     */
    public void markFBODirty() {
        this.fboDirty = true;
    }

    /**
     * @return true if the FBO needs re-rendering this frame
     */
    public boolean isFBODirty() {
        return fboDirty;
    }

    /***
     * This will modify the size of the FBO. You'd better know what you're doing before you call it.
     */
    public void setFBOSize(int resolutionWidth, int resolutionHeight) {
        this.resolutionWidth = resolutionWidth;
        this.resolutionHeight = resolutionHeight;
        releaseFBO();
        try {
            fbo = new Framebuffer(resolutionWidth, resolutionHeight, true);
        } catch (Exception e) {
            GTLog.logger.error(e);
        }
        this.fboDirty = true;
    }

    public RayTraceResult screenPos2BlockPosFace(int mouseX, int mouseY) {
        int lastID = bindFBO();
        RayTraceResult looking = super.screenPos2BlockPosFace(mouseX, mouseY, 0, 0, this.resolutionWidth,
                this.resolutionHeight);
        unbindFBO(lastID);
        return looking;
    }

    public Vector3f blockPos2ScreenPos(BlockPos pos, boolean depth) {
        int lastID = bindFBO();
        Vector3f winPos = super.blockPos2ScreenPos(pos, depth, 0, 0, this.resolutionWidth, this.resolutionHeight);
        unbindFBO(lastID);
        return winPos;
    }

    @Override
    protected PositionedRect getPositionedRect(int x, int y, int width, int height) {
        return new PositionedRect(new Position(x, y), new Size(width, height));
    }

    /**
     * Renders the scene. If the FBO is dirty, re-renders the full scene into the FBO.
     * Otherwise, just draws the cached FBO texture as a quad (nearly free).
     * Hit test is only performed on dirty frames (when depth buffer is freshly written).
     */
    public void render(float x, float y, float width, float height, float mouseX, float mouseY) {
        if (fboDirty) {
            // Re-render scene into FBO
            float localMouseX = mouseX - x;
            float localMouseY = mouseY - y;
            int lastID = bindFBO();
            super.render(0, 0, this.resolutionWidth, this.resolutionHeight,
                    (int) (this.resolutionWidth * localMouseX / width),
                    (int) (this.resolutionHeight * (1 - localMouseY / height)));
            unbindFBO(lastID);
            fboDirty = false;
        }

        // Draw cached FBO texture as a screen-aligned quad
        drawFBOQuad(x, y, width, height);
    }

    @Override
    public void render(float x, float y, float width, float height, int mouseX, int mouseY) {
        render(x, y, width, height, (float) mouseX, (float) mouseY);
    }

    @Override
    public WorldSceneRenderer setClipPlanes(double minY, double maxY) {
        markFBODirty();
        return super.setClipPlanes(minY, maxY);
    }

    @Override
    public WorldSceneRenderer disableClipPlanes() {
        markFBODirty();
        return super.disableClipPlanes();
    }

    @Override
    public void setCameraLookAt(Vector3f eyePos, Vector3f lookAt, Vector3f worldUp) {
        super.setCameraLookAt(eyePos, lookAt, worldUp);
        markFBODirty();
    }

    @Override
    public void setCameraLookAt(Vector3f lookAt, double radius, double rotationPitch, double rotationYaw) {
        super.setCameraLookAt(lookAt, radius, rotationPitch, rotationYaw);
        markFBODirty();
    }

    /**
     * Draw the FBO texture as a textured quad covering the specified screen area.
     */
    private void drawFBOQuad(float x, float y, float width, float height) {
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        int lastTexID = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.bindTexture(fbo.framebufferTexture);
        GlStateManager.color(1, 1, 1, 1);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX);

        bufferbuilder.pos(x + width, y + height, 0).tex(1, 0).endVertex();
        bufferbuilder.pos(x + width, y, 0).tex(1, 1).endVertex();
        bufferbuilder.pos(x, y, 0).tex(0, 1).endVertex();
        bufferbuilder.pos(x, y + height, 0).tex(0, 0).endVertex();
        tessellator.draw();

        GlStateManager.bindTexture(lastTexID);
    }

    private int bindFBO() {
        int lastID = GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
        fbo.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
        fbo.framebufferClear();
        fbo.bindFramebuffer(true);
        GlStateManager.pushMatrix();
        return lastID;
    }

    private void unbindFBO(int lastID) {
        GlStateManager.popMatrix();
        fbo.unbindFramebufferTexture();
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, lastID);
    }

    public void releaseFBO() {
        if (fbo != null) {
            fbo.deleteFramebuffer();
        }
        fbo = null;
    }

    @Override
    public void dispose() {
        super.dispose();
        releaseFBO();
    }
}
