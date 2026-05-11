package gregtech.client.renderer.godforge.util;

import java.util.Map;

import org.lwjgl.opengl.GL11;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public final class SphereVBOCache {

    private static final Map<Long, DirectTessellator.DirectVBO> CACHE = new Long2ObjectOpenHashMap<>();

    private SphereVBOCache() {}

    public static DirectTessellator.DirectVBO getOrCreate(int slices, int stacks) {
        long key = (((long) slices) << 32) | (stacks & 0xffffffffL);

        DirectTessellator.DirectVBO vbo = CACHE.get(key);
        if (vbo != null) {
            return vbo;
        }

        vbo = buildSphereVBO(slices, stacks);
        CACHE.put(key, vbo);
        return vbo;
    }

    private static DirectTessellator.DirectVBO buildSphereVBO(int slices, int stacks) {
        int vertexCount = slices * stacks * 6;
        DirectTessellator tessellator = DirectTessellator.startDrawing(GL11.GL_TRIANGLES, vertexCount);

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

                addVertex(tessellator, x00, y0, z00, uu0, (float) i / stacks);
                addVertex(tessellator, x10, y1, z10, uu0, (float) (i + 1) / stacks);
                addVertex(tessellator, x11, y1, z11, uu1, (float) (i + 1) / stacks);

                addVertex(tessellator, x00, y0, z00, uu0, (float) i / stacks);
                addVertex(tessellator, x11, y1, z11, uu1, (float) (i + 1) / stacks);
                addVertex(tessellator, x01, y0, z01, uu1, (float) i / stacks);
            }
        }

        return tessellator.buildStaticVBO();
    }

    private static void addVertex(DirectTessellator tessellator, double x, double y, double z, double u, double v) {
        tessellator.setNormal((float) -x, (float) -y, (float) -z);
        tessellator.addVertexWithUV(x, y, z, u, v);
    }
}
