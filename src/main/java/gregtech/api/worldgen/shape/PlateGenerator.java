package gregtech.api.worldgen.shape;

import net.minecraft.util.math.Vec3i;

import java.util.Random;

public class PlateGenerator extends ShapeGenerator {

    private final int minLength;
    private final int maxLength;
    private final int minDepth;
    private final int maxDepth;
    private final int minHeight;
    private final int maxHeight;
    private final float floorSharpness;
    private final float roofSharpness;

    public PlateGenerator(int minLength, int maxLength, int minDepth, int maxDepth, int minHeight, int maxHeight) {
        this(minLength, maxLength, minDepth, maxDepth, minHeight, maxHeight, 0.3f, 0.7f);
    }

    public PlateGenerator(int minLength, int maxLength, int minDepth, int maxDepth, int minHeight, int maxHeight,
                          float floorSharpness, float roofSharpness) {
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.floorSharpness = floorSharpness;
        this.roofSharpness = roofSharpness;
    }

    @Override
    public Vec3i getMaxSize() {
        int xzSize = Math.max(maxLength, maxDepth);
        return new Vec3i(xzSize * 2, maxDepth * 2, xzSize * 2);
    }

    @Override
    public void generate(Random gridRandom, IBlockGeneratorAccess relativeBlockAccess) {
        int length = (minLength == maxLength ? maxLength : minLength + gridRandom.nextInt(maxLength - minLength)) / 2;
        int depth = (minDepth == maxDepth ? maxDepth : minDepth + gridRandom.nextInt(maxDepth - minDepth)) / 2;
        int height = (minHeight == maxHeight ? maxHeight : minHeight + gridRandom.nextInt(maxHeight - minHeight)) / 2;
        boolean rotate = gridRandom.nextBoolean();
        for (int x = -length; x <= length; x++) {
            for (int z = -depth; z <= depth; z++) {
                boolean hasFloorSub = floorSharpness > gridRandom.nextFloat();
                boolean hasRoofSub = roofSharpness > gridRandom.nextFloat();
                for (int y = -height; y <= height; y++) {
                    if (hasRoofSub && (y == height || gridRandom.nextBoolean())) {
                        continue;
                    } else hasRoofSub = false;
                    if (hasFloorSub && y == -height)
                        continue;
                    relativeBlockAccess.generateBlock(rotate ? z : x, y, rotate ? x : z);
                }
            }
        }
    }
}
