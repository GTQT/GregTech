package gregtech.api.worldgen.shape;

import net.minecraft.util.math.Vec3i;

import java.util.Random;

public class SphereGenerator extends ShapeGenerator {

    private final int radiusMin;
    private final int radiusMax;

    public SphereGenerator(int radiusMin, int radiusMax) {
        this.radiusMin = radiusMin;
        this.radiusMax = radiusMax;
    }

    @Override
    public Vec3i getMaxSize() {
        return new Vec3i(radiusMax * 2, radiusMax * 2, radiusMax * 2);
    }

    @Override
    public void generate(Random gridRandom, IBlockGeneratorAccess relativeBlockAccess) {
        int sphereRadius = radiusMin >= radiusMax ? radiusMin : radiusMin + gridRandom.nextInt(radiusMax - radiusMin);
        for (int x = -sphereRadius; x <= sphereRadius; x++) {
            for (int z = -sphereRadius; z <= sphereRadius; z++) {
                for (int y = -sphereRadius; y <= sphereRadius; y++) {
                    if (x * x + y * y + z * z > sphereRadius * sphereRadius)
                        continue;
                    relativeBlockAccess.generateBlock(x, y, z);
                }
            }
        }
    }
}
