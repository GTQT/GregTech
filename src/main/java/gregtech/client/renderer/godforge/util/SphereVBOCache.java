package gregtech.client.renderer.godforge.util;

import java.nio.ByteBuffer;
import java.util.Map;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public final class SphereVBOCache {

    private static final Map<Long, SphereVBO> CACHE = new Long2ObjectOpenHashMap<>();

    private SphereVBOCache() {}

    public static SphereVBO getOrCreate(int slices, int stacks) {
        long key = (((long) slices) << 32) | (stacks & 0xffffffffL);

        SphereVBO vbo = CACHE.get(key);
        if (vbo != null) {
            return vbo;
        }

        vbo = buildSphereVBO(slices, stacks);
        CACHE.put(key, vbo);
        return vbo;
    }

    private static SphereVBO buildSphereVBO(int slices, int stacks) {
        int vertexCount = slices * stacks * 6;
        int floatsPerVertex = 8;
        ByteBuffer buffer = ByteBuffer.allocateDirect(vertexCount * floatsPerVertex * Float.BYTES);

        for (int i = 0; i < stacks; i++) {
            double phi0 = Math.PI / 2.0 - i * Math.PI / stacks;
            double phi1 = Math.PI / 2.0 - (i + 1) * Math.PI / stacks;

            double y0 = Math.sin(phi0);
            double y1 = Math.sin(phi1);

            double r0 = Math.cos(phi0);
            double r1 = Math.cos(phi1);

            for (int j = 0; j < slices; j++) {
                double u0 = (double) j / (double) slices;
                double u1 = (double) (j + 1) / (double) slices;

                float uu0 = (float) (1.0 - u0);
                float uu1 = (float) (1.0 - u1);

                double th0 = j * 2.0 * Math.PI / slices;
                double th1 = (j + 1) * 2.0 * Math.PI / slices;

                double x00 = r0 * Math.cos(th0);
                double z00 = r0 * Math.sin(th0);

                double x10 = r1 * Math.cos(th0);
                double z10 = r1 * Math.sin(th0);

                double x11 = r1 * Math.cos(th1);
                double z11 = r1 * Math.sin(th1);

                double x01 = r0 * Math.cos(th1);
                double z01 = r0 * Math.sin(th1);

                putVertex(buffer, x00, y0, z00, uu0, (float) (i) / stacks);
                putVertex(buffer, x10, y1, z10, uu0, (float) (i + 1) / stacks);
                putVertex(buffer, x11, y1, z11, uu1, (float) (i + 1) / stacks);

                putVertex(buffer, x00, y0, z00, uu0, (float) (i) / stacks);
                putVertex(buffer, x11, y1, z11, uu1, (float) (i + 1) / stacks);
                putVertex(buffer, x01, y0, z01, uu1, (float) (i) / stacks);
            }
        }

        buffer.flip();

        int vboID = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        return new SphereVBO(vboID, vertexCount);
    }

    private static void putVertex(ByteBuffer buffer, double x, double y, double z, double u, double v) {
        buffer.putFloat((float) x);
        buffer.putFloat((float) y);
        buffer.putFloat((float) z);
        buffer.putFloat((float) -x);
        buffer.putFloat((float) -y);
        buffer.putFloat((float) -z);
        buffer.putFloat((float) u);
        buffer.putFloat((float) v);
    }

    public static class SphereVBO {

        private final int vboID;
        private final int vertexCount;
        private static final int VERTEX_SIZE = 8 * Float.BYTES;

        SphereVBO(int vboID, int vertexCount) {
            this.vboID = vboID;
            this.vertexCount = vertexCount;
        }

        public void render() {
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
            GL11.glVertexPointer(3, GL11.GL_FLOAT, VERTEX_SIZE, 0);
            GL11.glNormalPointer(GL11.GL_FLOAT, VERTEX_SIZE, 12);
            GL11.glTexCoordPointer(2, GL11.GL_FLOAT, VERTEX_SIZE, 24);

            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        }
    }
}
