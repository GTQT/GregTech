package gregtech.client.renderer.godforge;

import static gregtech.api.GTValues.MODID;

import java.nio.FloatBuffer;

import gregtech.api.util.GTLog;
import gregtech.client.renderer.godforge.util.SphereVBOCache;
import gregtech.client.renderer.godforge.util.StructureVBO;
import gregtech.client.renderer.godforge.util.TextureUpdateRequester;
import gregtech.common.blocks.BlockGodforgeCasing;
import gregtech.common.blocks.BlockGodforgeGlass;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.multi.electric.godforge.ForgeOfGodsStructureString;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

public class GodforgeStarRenderer extends TileEntitySpecialRenderer<GodforgeRenderTileEntity> {

    private static final ResourceLocation STAR_LAYER_0 = new ResourceLocation(MODID, "textures/godforge/starlayer0.png");
    private static final ResourceLocation STAR_LAYER_1 = new ResourceLocation(MODID, "textures/godforge/starlayer1.png");
    private static final ResourceLocation STAR_LAYER_2 = new ResourceLocation(MODID, "textures/godforge/starlayer2.png");
    private static final ResourceLocation BEAM_TEXTURE = new ResourceLocation(MODID, "textures/godforge/spacelayer.png");

    private static final int MAX_SEGMENTS = 10;
    private static final int BEAM_SEGMENT_QUADS = 16;

    private static boolean initialized = false;
    private static boolean failedInit = false;
    private static long lastFailedInitLogTime = 0;
    private static long lastInvalidOwnerLogTime = 0;
    private static long lastRenderEntryLogTime = 0;

    private static int starProgram = -1;
    private static int u_StarColor = -1, u_StarModelMatrix = -1, u_StarGamma = -1, u_StarTexture = -1;

    private static int beamProgram = -1;
    private static int a_VertexID = -1;
    private static int u_BeamModelMatrix = -1;
    private static int u_CameraPosition = -1, u_SegmentArray = -1, u_SegmentQuads = -1;
    private static int u_BeamIntensity = -1, u_BeamColor = -1, u_BeamTime = -1;
    private static int beamVboID = -1;

    private static int fadeBypassProgram = -1;

    private final FloatBuffer softBeamSegmentBuffer = BufferUtils.createFloatBuffer(MAX_SEGMENTS * 3);
    private final FloatBuffer intenseBeamSegmentBuffer = BufferUtils.createFloatBuffer(MAX_SEGMENTS * 3);
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    private StructureVBO ringOne, ringTwo, ringThree;

    private float cachedRadius = -1f;
    private int cachedRingCount = -1;

    private void init() {
        try {
            starProgram = createShaderProgram("star.vert", "star.frag");
            u_StarColor = GL20.glGetUniformLocation(starProgram, "u_Color");
            u_StarGamma = GL20.glGetUniformLocation(starProgram, "u_Gamma");
            u_StarModelMatrix = GL20.glGetUniformLocation(starProgram, "u_ModelMatrix");
            u_StarTexture = GL20.glGetUniformLocation(starProgram, "u_Texture");
        } catch (Exception e) {
            GTLog.logger.warn("[FOG] GodforgeStarRenderer: star shader initialization failed", e);
            failedInit = true;
            return;
        }

        GL20.glUseProgram(starProgram);
        GL20.glUniform1i(u_StarTexture, 0);
        GL20.glUseProgram(0);

        try {
            beamProgram = createShaderProgram("gorgebeam.vert", "gorgebeam.frag");

            u_BeamModelMatrix = GL20.glGetUniformLocation(beamProgram, "u_ModelMatrix");
            u_CameraPosition = GL20.glGetUniformLocation(beamProgram, "u_CameraPosition");
            u_SegmentQuads = GL20.glGetUniformLocation(beamProgram, "u_SegmentQuads");
            u_SegmentArray = GL20.glGetUniformLocation(beamProgram, "u_SegmentArray");
            u_BeamColor = GL20.glGetUniformLocation(beamProgram, "u_Color");
            u_BeamIntensity = GL20.glGetUniformLocation(beamProgram, "u_Intensity");
            u_BeamTime = GL20.glGetUniformLocation(beamProgram, "u_Time");

            a_VertexID = GL20.glGetAttribLocation(beamProgram, "a_VertexID");
        } catch (Exception e) {
            GTLog.logger.warn("[FOG] GodforgeStarRenderer: beam shader initialization failed", e);
            failedInit = true;
            return;
        }

        GL20.glUseProgram(beamProgram);
        GL20.glUniform1f(u_SegmentQuads, (float) BEAM_SEGMENT_QUADS);

        FloatBuffer buffer = BufferUtils.createFloatBuffer(MAX_SEGMENTS * BEAM_SEGMENT_QUADS * 6 * 3);

        for (int i = 0; i < MAX_SEGMENTS; i++) {
            for (int j = 0; j < BEAM_SEGMENT_QUADS; j++) {
                for (int v = 0; v < 6; v++) {
                    int segID = i * BEAM_SEGMENT_QUADS * 6;
                    int quadID = j * 6;
                    int vertID = segID + quadID + v;
                    buffer.put(vertID);
                    buffer.put(0);
                    buffer.put(0);
                }
            }
        }

        buffer.flip();
        beamVboID = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, beamVboID);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        GL20.glVertexAttribPointer(a_VertexID, 1, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, 0, 0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL20.glUseProgram(0);

        initialized = true;
    }

    private void initRings() {
        ringOne = createRingVBO(ForgeOfGodsStructureString.FIRST_RING);
        ringOne.build();

        ringTwo = createRingVBO(ForgeOfGodsStructureString.SECOND_RING);
        ringTwo.build();

        ringThree = createRingVBO(ForgeOfGodsStructureString.THIRD_RING);
        ringThree.build();

        try {
            fadeBypassProgram = createShaderProgram("fadebypass.vert", "fadebypass.frag");
        } catch (Exception e) {
            GTLog.logger.warn("[FOG] GodforgeStarRenderer: ring fade shader initialization failed", e);
            failedInit = true;
            return;
        }

        TextureUpdateRequester textureUpdater = ringOne.getTextureUpdateRequestor();
        textureUpdater.requestUpdate();
    }

    private static StructureVBO createRingVBO(String[][] structure) {
        return new StructureVBO()
                .addMapping('H', MetaBlocks.GODFORGE_GLASS, 0)
                .addMapping('B', MetaBlocks.GODFORGE_CASING,
                        BlockGodforgeCasing.CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING.ordinal())
                .addMapping('C', MetaBlocks.GODFORGE_CASING,
                        BlockGodforgeCasing.CasingType.CELESTIAL_MATTER_GUIDANCE_CASING.ordinal())
                .addMapping('D', MetaBlocks.GODFORGE_CASING,
                        BlockGodforgeCasing.CasingType.BOUNDLESS_GRAVITATIONALLY_SEVERED_STRUCTURE_CASING.ordinal())
                .addMapping('E', MetaBlocks.GODFORGE_CASING,
                        BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING.ordinal())
                .addMapping('G', MetaBlocks.GODFORGE_CASING,
                        BlockGodforgeCasing.CasingType.REMOTE_GRAVITON_FLOW_MODULATOR.ordinal())
                .addMapping('K', MetaBlocks.GODFORGE_CASING,
                        BlockGodforgeCasing.CasingType.CENTRAL_GRAVITON_FLOW_MODULATOR.ordinal())
                .addMapping('I', MetaBlocks.GODFORGE_CASING,
                        BlockGodforgeCasing.CasingType.MEDIAL_GRAVITON_FLOW_MODULATOR.ordinal())
                .assignStructure(structure);
    }

    private static int createShaderProgram(String vertName, String fragName) {
        int vertShader = compileShader(GL20.GL_VERTEX_SHADER, vertName);
        int fragShader = compileShader(GL20.GL_FRAGMENT_SHADER, fragName);

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertShader);
        GL20.glAttachShader(program, fragShader);
        GL20.glLinkProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program, 1024);
            throw new RuntimeException("Shader link error: " + log);
        }

        GL20.glDeleteShader(vertShader);
        GL20.glDeleteShader(fragShader);

        return program;
    }

    private static int compileShader(int type, String name) {
        int shader = GL20.glCreateShader(type);
        String source = readShaderSource(name);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 1024);
            throw new RuntimeException("Shader compile error (" + name + "): " + log);
        }

        return shader;
    }

    private static String readShaderSource(String name) {
        try {
            ResourceLocation location = new ResourceLocation(MODID, "shaders/" + name);
            java.io.InputStream stream = Minecraft.getMinecraft()
                    .getResourceManager()
                    .getResource(location)
                    .getInputStream();
            java.util.Scanner scanner = new java.util.Scanner(stream, "UTF-8");
            String source = scanner.useDelimiter("\\A").next();
            scanner.close();
            return source;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read shader: " + name, e);
        }
    }

    public void renderStarLayer(float r, float g, float b, float a, ResourceLocation texture, float scale,
                                float rotX, float rotY, float rotZ, float degrees) {
        GL11.glPushMatrix();

        bindTexture(texture);

        GL11.glTranslatef(0.5f, 0.5f, 0.5f);
        GL11.glRotatef(degrees, rotX, rotY, rotZ);
        GL11.glScalef(scale, scale, scale);

        GL20.glUseProgram(starProgram);

        matrixBuffer.clear();
        matrixBuffer.put(new float[] {
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1
        });
        matrixBuffer.flip();
        GL20.glUniformMatrix4(u_StarModelMatrix, false, matrixBuffer);
        GL20.glUniform4f(u_StarColor, r, g, b, a);

        SphereVBOCache.SphereVBO sphere = SphereVBOCache.getOrCreate(128, 128);
        sphere.render();

        GL20.glUseProgram(0);

        GL11.glPopMatrix();
    }

    public void renderStarOpaquePass(GodforgeRenderTileEntity tile, double x, double y, double z, float timer) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        GL20.glUseProgram(starProgram);
        GL20.glUniform1f(u_StarGamma, tile.getGamma());

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);

        timer *= tile.getRotationSpeed();

        renderStarLayer(
                tile.getColorR(), tile.getColorG(), tile.getColorB(), 1f,
                STAR_LAYER_0,
                tile.getStarRadius(),
                0, 1, 1,
                130 + (timer) % 360000);

        GL11.glPopMatrix();

        GL20.glUseProgram(0);
        GL11.glPopAttrib();
    }

    public void renderStarTransparentPass(GodforgeRenderTileEntity tile, double x, double y, double z, float timer) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        GL20.glUseProgram(starProgram);
        GL20.glUniform1f(u_StarGamma, tile.getGamma());

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);

        timer *= tile.getRotationSpeed();

        renderStarLayer(
                tile.getColorR(), tile.getColorG(), tile.getColorB(), 0.4f,
                STAR_LAYER_1,
                tile.getStarRadius() * 1.02f,
                1, 1, 0,
                -49 + (timer) % 360000);

        renderStarLayer(
                tile.getColorR(), tile.getColorG(), tile.getColorB(), 0.2f,
                STAR_LAYER_2,
                tile.getStarRadius() * 1.04f,
                1, 0, 1,
                67 + (timer) % 360000);

        GL11.glPopMatrix();

        GL20.glUseProgram(0);
        GL11.glPopAttrib();
    }

    public void bufferSoftBeam(GodforgeRenderTileEntity tile) {
        float angle = tile.getStartAngle();
        float radius = tile.getStarRadius() * 1.1f;
        float startx = -radius * (float) Math.cos(angle);
        float starty = radius * (float) Math.sin(angle);

        softBeamSegmentBuffer.clear();

        softBeamSegmentBuffer.put(starty);
        softBeamSegmentBuffer.put(startx);
        softBeamSegmentBuffer.put(0);

        for (int i = tile.getRingCount() - 1; i >= 0; i--) {
            softBeamSegmentBuffer.put(tile.getLenRadius(i));
            softBeamSegmentBuffer.put(tile.getLensDistance(i));
            softBeamSegmentBuffer.put(1f);
        }

        softBeamSegmentBuffer.put(GodforgeRenderTileEntity.BACK_PLATE_RADIUS);
        softBeamSegmentBuffer.put(GodforgeRenderTileEntity.BACK_PLATE_DISTANCE);
        softBeamSegmentBuffer.put(-.05f);

        softBeamSegmentBuffer.rewind();
    }

    public void bufferIntenseBeam(GodforgeRenderTileEntity tile) {
        float angle = tile.getStartAngle();
        float radius = tile.getStarRadius() * 1.05f;
        float startx = -radius * (float) Math.cos(angle);
        float starty = radius * (float) Math.sin(angle);

        int firstLens = tile.getRingCount() - 1;

        float nextx = tile.getLensDistance(firstLens);
        float nexty = tile.getLenRadius(firstLens) * .75f;

        float backx = Math.max(-radius, (nextx + radius) / 2);
        float backy = GodforgeRenderTileEntity.interpolate(startx, nextx, starty, nexty, backx);

        intenseBeamSegmentBuffer.clear();

        intenseBeamSegmentBuffer.put(backy);
        intenseBeamSegmentBuffer.put(backx);
        intenseBeamSegmentBuffer.put(0);

        float transparency = .2f;
        for (int i = tile.getRingCount() - 1; i >= 0; i--) {
            intenseBeamSegmentBuffer.put(tile.getLenRadius(i) / 2);
            intenseBeamSegmentBuffer.put(tile.getLensDistance(i));
            intenseBeamSegmentBuffer.put(transparency);
            transparency += .3f;
        }

        float currx = tile.getLensDistance(0);
        float curry = tile.getLenRadius(0) / 2;
        float lastx = GodforgeRenderTileEntity.BACK_PLATE_DISTANCE;
        float lasty = Math.min(tile.getLenRadius(firstLens), GodforgeRenderTileEntity.BACK_PLATE_RADIUS);

        float midx = lastx + 8f;
        float midy = GodforgeRenderTileEntity.interpolate(currx, lastx, curry, lasty, midx);

        intenseBeamSegmentBuffer.put(midy);
        intenseBeamSegmentBuffer.put(midx);
        intenseBeamSegmentBuffer.put(transparency);

        intenseBeamSegmentBuffer.put(lasty);
        intenseBeamSegmentBuffer.put(lastx);
        intenseBeamSegmentBuffer.put(0f);

        intenseBeamSegmentBuffer.rewind();
    }

    public void renderBeamSegment(GodforgeRenderTileEntity tile, double x, double y, double z, float timer,
                                  boolean needsBeamUpdate) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        bindTexture(BEAM_TEXTURE);

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        GL11.glRotatef(tile.getRotAngle(), tile.getRotAxisX(), tile.getRotAxisY(), tile.getRotAxisZ());
        GL11.glRotatef(90, 0, 1, 0);

        GL20.glUseProgram(beamProgram);

        if (needsBeamUpdate) {
            bufferSoftBeam(tile);
            bufferIntenseBeam(tile);
        }

        matrixBuffer.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrixBuffer);
        matrixBuffer.flip();
        GL20.glUniformMatrix4(u_BeamModelMatrix, false, matrixBuffer);

        GL20.glUniform3f(
                u_CameraPosition,
                (float) (ActiveRenderInfo.getCameraPosition().x - x - 0.5),
                (float) (ActiveRenderInfo.getCameraPosition().y - y - 0.5),
                (float) (ActiveRenderInfo.getCameraPosition().z - z - 0.5));

        GL20.glEnableVertexAttribArray(a_VertexID);
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);

        GL20.glUniform3f(u_BeamColor, tile.getColorR(), tile.getColorG(), tile.getColorB());
        GL20.glUniform1f(u_BeamIntensity, 2);
        GL20.glUniform1f(u_BeamTime, timer);
        softBeamSegmentBuffer.rewind();
        GL20.glUniform3(u_SegmentArray, softBeamSegmentBuffer);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, MAX_SEGMENTS * BEAM_SEGMENT_QUADS * 6);

        GL20.glUniform3f(u_BeamColor, 1, 1, 1);
        GL20.glUniform1f(u_BeamIntensity, 4);
        intenseBeamSegmentBuffer.rewind();
        GL20.glUniform3(u_SegmentArray, intenseBeamSegmentBuffer);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, MAX_SEGMENTS * BEAM_SEGMENT_QUADS * 6);

        GL20.glDisableVertexAttribArray(a_VertexID);
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);

        GL20.glUseProgram(0);

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private void renderRings(GodforgeRenderTileEntity tile, double x, double y, double z, float timer) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GL20.glUseProgram(fadeBypassProgram);

        GL11.glPushMatrix();
        GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
        GL11.glRotatef(tile.getRotAngle(), tile.getRotAxisX(), tile.getRotAxisY(), tile.getRotAxisZ());
        GL11.glRotatef(timer / 6 * 7, 1, 0, 0);
        GL11.glTranslated(0, -1, 0);
        ringOne.render();
        GL11.glPopMatrix();

        if (tile.getRingCount() > 1) {
            GL11.glPushMatrix();
            GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
            GL11.glRotatef(tile.getRotAngle(), tile.getRotAxisX(), tile.getRotAxisY(), tile.getRotAxisZ());
            GL11.glRotatef(-timer / 4 * 5, 1, 0, 0);
            GL11.glTranslated(0, -1, 0);
            ringTwo.render();
            GL11.glPopMatrix();

            if (tile.getRingCount() > 2) {
                GL11.glPushMatrix();
                GL11.glTranslated(x + 0.5, y + 0.5, z + 0.5);
                GL11.glRotatef(tile.getRotAngle(), tile.getRotAxisX(), tile.getRotAxisY(), tile.getRotAxisZ());
                GL11.glRotatef(timer * 3, 1, 0, 0);
                GL11.glTranslated(0.5f, -1, 0);
                ringThree.render();
                GL11.glPopMatrix();
            }
        }

        GL20.glUseProgram(0);
        GL11.glPopAttrib();
    }

    @Override
    public void render(GodforgeRenderTileEntity tile, double x, double y, double z, float partialTicks,
                       int destroyStage, float alpha) {
        if (failedInit) {
            lastFailedInitLogTime = logThrottled(
                    "[FOG] GodforgeStarRenderer: render skipped because initialization previously failed",
                    lastFailedInitLogTime);
            return;
        }
        if (tile.getRingCount() < 1) {
            GTLog.logger.warn("[FOG] GodforgeStarRenderer: render skipped at {} because ringCount={}",
                    tile.getPos(), tile.getRingCount());
            return;
        }
        if (!tile.hasValidOwner()) {
            lastInvalidOwnerLogTime = logThrottled("[FOG] GodforgeStarRenderer: render skipped at " + tile.getPos() +
                    " because owner is invalid. owner=" + tile.getOwnerPosForDebug(), lastInvalidOwnerLogTime);
            return;
        }

        if (!initialized) {
            GTLog.logger.info("[FOG] GodforgeStarRenderer: initializing renderer at {}, owner={}, radius={}, rings={}",
                    tile.getPos(), tile.getOwnerPosForDebug(), tile.getStarRadius(), tile.getRingCount());
            init();
            if (!initialized) {
                GTLog.logger.warn("[FOG] GodforgeStarRenderer: initialization did not complete at {}", tile.getPos());
                failedInit = true;
                return;
            }
            try {
                initRings();
            } catch (Exception e) {
                GTLog.logger.warn("[FOG] GodforgeStarRenderer: ring VBO initialization failed at {}", tile.getPos(), e);
                failedInit = true;
                return;
            }
            GTLog.logger.info("[FOG] GodforgeStarRenderer: initialization complete");
        }

        lastRenderEntryLogTime = logThrottled("[FOG] GodforgeStarRenderer: rendering at " + tile.getPos() +
                ", owner=" + tile.getOwnerPosForDebug() +
                ", radius=" + tile.getStarRadius() +
                ", rings=" + tile.getRingCount(), lastRenderEntryLogTime);

        tile.incrementColors();

        boolean needsBeamUpdate = false;
        if (tile.getStarRadius() != this.cachedRadius || tile.getRingCount() != this.cachedRingCount) {
            needsBeamUpdate = true;
            this.cachedRadius = tile.getStarRadius();
            this.cachedRingCount = tile.getRingCount();
        }

        float timer = net.minecraft.client.Minecraft.getMinecraft().player != null
                ? net.minecraft.client.Minecraft.getMinecraft().player.ticksExisted + partialTicks
                : partialTicks;

        renderStarOpaquePass(tile, x, y, z, timer);
        renderRings(tile, x, y, z, timer);
        renderStarTransparentPass(tile, x, y, z, timer);
        renderBeamSegment(tile, x, y, z, timer, needsBeamUpdate);
    }

    private static long logThrottled(String message, long lastLogTime) {
        long now = System.currentTimeMillis();
        if (now - lastLogTime >= 5000) {
            GTLog.logger.info(message);
            return now;
        }
        return lastLogTime;
    }
}
