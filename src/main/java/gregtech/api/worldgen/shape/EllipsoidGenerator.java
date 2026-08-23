package gregtech.api.worldgen.shape;

import net.minecraft.util.math.Vec3i;

import java.util.Random;

public class EllipsoidGenerator extends ShapeGenerator {

    private final int radiusMin;
    private final int radiusMax;

    public EllipsoidGenerator(int radiusMin, int radiusMax) {
        this.radiusMin = radiusMin;
        this.radiusMax = radiusMax;
    }

    public int getYRadius() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Vec3i getMaxSize() {
        return new Vec3i(radiusMax * 2, radiusMax, radiusMax * 2);
    }

    @Override
    public void generate(Random gridRandom, IBlockGeneratorAccess blockAccess) {
        Random rand = new Random();
        int yMax = Math.min(radiusMin, getYRadius());
        int randomNum = rand.nextInt(5);
        int randomHig = rand.nextInt(15);
        int h=(randomHig+1)/4;
        int p;

        for (int x = -radiusMax; x <= radiusMax; x++) {
            for (int z = -radiusMax; z <= radiusMax; z++) {
                switch (randomNum) {
                    case (1) -> p = (x / 3) + (z / 3);
                    case (2) -> p = (x / 3)  - (z / 3);
                    case (3) -> p = -(x / 3) + (z / 3);
                    case (4) -> p = -(x / 3) - (z / 3);
                    default -> p=0;
                }
                for (int y = p*h-yMax; y <= p*h+yMax; y++) {
                    generateBlock(x, y, z, blockAccess);
                }
            }
        }
    }

    // Used for overriding
    public void generateBlock(int x, int y, int z, IBlockGeneratorAccess blockAccess) {
        blockAccess.generateBlock(x, y, z);
    }
}
