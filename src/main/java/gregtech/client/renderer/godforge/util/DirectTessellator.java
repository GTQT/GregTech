package gregtech.client.renderer.godforge.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

public final class DirectTessellator {

    private static final int FLOATS_PER_VERTEX = 8;
    private static final int VERTEX_SIZE = FLOATS_PER_VERTEX * Float.BYTES;

    private ByteBuffer buffer;
    private final int drawMode;
    private int vertexCount;
    private float normalX;
    private float normalY;
    private float normalZ;

    private DirectTessellator(int drawMode, int expectedVertices) {
        this.drawMode = drawMode;
        int initialCapacity = Math.max(expectedVertices, 1) * VERTEX_SIZE;
        this.buffer = ByteBuffer.allocateDirect(initialCapacity).order(ByteOrder.nativeOrder());
    }

    public static DirectTessellator startDrawing(int drawMode, int expectedVertices) {
        return new DirectTessellator(drawMode, expectedVertices);
    }

    public void setNormal(float x, float y, float z) {
        normalX = x;
        normalY = y;
        normalZ = z;
    }

    public void addVertexWithUV(double x, double y, double z, double u, double v) {
        ensureRemaining(VERTEX_SIZE);
        buffer.putFloat((float) x);
        buffer.putFloat((float) y);
        buffer.putFloat((float) z);
        buffer.putFloat(normalX);
        buffer.putFloat(normalY);
        buffer.putFloat(normalZ);
        buffer.putFloat((float) u);
        buffer.putFloat((float) v);
        vertexCount++;
    }

    public DirectVBO buildStaticVBO() {
        buffer.flip();

        int vboID = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        return new DirectVBO(vboID, drawMode, vertexCount);
    }

    private void ensureRemaining(int bytes) {
        if (buffer.remaining() >= bytes) return;

        int newCapacity = Math.max(buffer.capacity() * 2, buffer.capacity() + bytes);
        ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity).order(ByteOrder.nativeOrder());
        buffer.flip();
        newBuffer.put(buffer);
        buffer = newBuffer;
    }

    public static final class DirectVBO {

        private final int vboID;
        private final int drawMode;
        private final int vertexCount;

        private DirectVBO(int vboID, int drawMode, int vertexCount) {
            this.vboID = vboID;
            this.drawMode = drawMode;
            this.vertexCount = vertexCount;
        }

        public void render() {
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboID);
            GL11.glVertexPointer(3, GL11.GL_FLOAT, VERTEX_SIZE, 0);
            GL11.glNormalPointer(GL11.GL_FLOAT, VERTEX_SIZE, 3 * Float.BYTES);
            GL11.glTexCoordPointer(2, GL11.GL_FLOAT, VERTEX_SIZE, 6 * Float.BYTES);

            GL11.glDrawArrays(drawMode, 0, vertexCount);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
            GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        }

        public void delete() {
            GL15.glDeleteBuffers(vboID);
        }
    }
}
