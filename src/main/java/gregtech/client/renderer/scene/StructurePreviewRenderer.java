package gregtech.client.renderer.scene;

import gregtech.api.util.GTLog;
import gregtech.api.util.Position;
import gregtech.api.util.PositionedRect;
import gregtech.api.util.Size;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.EXTFramebufferBlit;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** JEI scene: cached static color/depth, live overlays, and independent CPU picking. */
public final class StructurePreviewRenderer extends FBOWorldSceneRenderer {
    private PreviewPickIndex pickIndex = new PreviewPickIndex(new HashMap<>());
    private RayTraceResult hit;
    private Framebuffer composite;
    private boolean staticPass;
    private boolean overlayPass;
    private long samples, drawNanos, pickNanos, lastReport = System.nanoTime();
    private boolean reportedFallback;

    public StructurePreviewRenderer(World world, int width, int height) {
        super(world, width, height);
    }

    @Override
    public WorldSceneRenderer addRenderedBlocks(Collection<BlockPos> blocks) {
        super.addRenderedBlocks(blocks);
        Map<BlockPos, AxisAlignedBB> boxes = new HashMap<>();
        for (BlockPos pos : renderedBlocks) {
            boxes.put(pos, world.getBlockState(pos).getSelectedBoundingBox(world, pos));
        }
        pickIndex = new PreviewPickIndex(boxes);
        markFBODirty();
        return this;
    }

    public RayTraceResult pick(float mouseX, float mouseY, float viewX, float viewY,
                               float viewWidth, float viewHeight) {
        long started = System.nanoTime();
        hit = null;
        if (viewWidth > 0 && viewHeight > 0 && mouseX >= viewX && mouseX < viewX + viewWidth &&
                mouseY >= viewY && mouseY < viewY + viewHeight) {
            Vec3d eye = new Vec3d(getEyePos().x, getEyePos().y, getEyePos().z);
            Vec3d forward = new Vec3d(getLookAt().x, getLookAt().y, getLookAt().z).subtract(eye).normalize();
            Vec3d right = forward.crossProduct(new Vec3d(getWorldUp().x, getWorldUp().y, getWorldUp().z)).normalize();
            Vec3d up = right.crossProduct(forward).normalize();
            double scale = Math.tan(Math.toRadians(30));
            double x = (2 * (mouseX - viewX) / viewWidth - 1) * viewWidth / viewHeight * scale;
            double y = (1 - 2 * (mouseY - viewY) / viewHeight) * scale;
            Vec3d direction = forward.add(right.scale(x)).add(up.scale(y)).normalize();
            hit = pickIndex.trace(eye, eye.add(direction.scale(10000)), pos -> !isClipEnabled() ||
                    pos.getY() >= getClipMinY() && pos.getY() < getClipMaxY());
        }
        pickNanos += System.nanoTime() - started;
        return hit;
    }

    @Override
    public RayTraceResult getLastTraceResult() {
        return hit;
    }

    @Override
    protected void renderTileEntities() {
        if (!staticPass) super.renderTileEntities();
    }

    @Override
    protected void clearView(int x, int y, int width, int height) {
        if (!overlayPass) super.clearView(x, y, width, height);
    }

    @Override
    protected void drawFBOQuad(float x, float y, float width, float height) {
        // Only composite once; drawing translucent static pixels twice changes their opacity.
        if (!staticPass) super.drawFBOQuad(x, y, width, height);
    }

    @Override
    protected boolean ensureFBO() {
        int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        try {
            return super.ensureFBO();
        } finally {
            // Framebuffer allocation binds and then unbinds its target.
            OpenGlHelper.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        }
    }

    @Override
    public void render(float x, float y, float width, float height, float mouseX, float mouseY) {
        pick(mouseX, mouseY, x, y, width, height);
        long started = System.nanoTime();
        boolean canBlit = GLContext.getCapabilities().OpenGL30 || GLContext.getCapabilities().GL_ARB_framebuffer_object ||
                GLContext.getCapabilities().GL_EXT_framebuffer_blit;
        if (!canBlit || !ensureFBO()) {
            if (!reportedFallback) {
                GTLog.logger.info("[JEIMultiblockPreview] framebuffer blit unavailable; using cached mesh rendering");
                reportedFallback = true;
            }
            markFBODirty();
            super.render(x, y, width, height, mouseX, mouseY);
            return;
        }
        Consumer<WorldSceneRenderer> overlay = afterRender;
        afterRender = null;
        staticPass = true;
        try {
            super.render(x, y, width, height, mouseX, mouseY);
        } finally {
            staticPass = false;
            afterRender = overlay;
        }
        int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        if (composite == null) composite = new Framebuffer(getResolutionWidth(), getResolutionHeight(), true);
        Framebuffer background = fbo;
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        try {
            OpenGlHelper.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, background.framebufferObject);
            OpenGlHelper.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, composite.framebufferObject);
            int w = getResolutionWidth(), h = getResolutionHeight();
            int mask = GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT;
            if (GLContext.getCapabilities().OpenGL30) {
                GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, mask, GL11.GL_NEAREST);
            } else if (GLContext.getCapabilities().GL_ARB_framebuffer_object) {
                ARBFramebufferObject.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, mask, GL11.GL_NEAREST);
            } else {
                EXTFramebufferBlit.glBlitFramebufferEXT(0, 0, w, h, 0, 0, w, h, mask, GL11.GL_NEAREST);
            }
            composite.bindFramebuffer(false);
            overlayPass = true;
            setupCamera(new PositionedRect(new Position(0, 0), new Size(w, h)));
            try {
                if (isClipEnabled()) enableYClipPlanes(getClipMinY(), getClipMaxY());
                renderTileEntities();
                if (overlay != null) overlay.accept(this);
            } finally {
                if (isClipEnabled()) disableYClipPlanes();
                resetCamera();
                overlayPass = false;
            }
            OpenGlHelper.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
            fbo = composite;
            drawFBOQuad(x, y, width, height);
        } finally {
            fbo = background;
            overlayPass = false;
            OpenGlHelper.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
            GL11.glPopAttrib();
            GlStateManager.color(1, 1, 1, 1);
        }
        drawNanos += System.nanoTime() - started;
        samples++;
        long now = System.nanoTime();
        if (now - lastReport >= 10_000_000_000L) {
            GTLog.logger.debug("[JEIMultiblockPreview] frames={} averageSubmitMs={} averagePickMs={} blocks={}",
                    samples, drawNanos / (samples * 1_000_000.0), pickNanos / (samples * 1_000_000.0), renderedBlocks.size());
            samples = drawNanos = pickNanos = 0;
            lastReport = now;
        }
    }

    @Override
    public void releaseFBO() {
        if (composite != null) composite.deleteFramebuffer();
        composite = null;
        super.releaseFBO();
    }
}
