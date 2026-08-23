package gregtech.api.worldgen.shape;

import net.minecraft.util.math.Vec3i;

public class LayeredGenerator extends EllipsoidGenerator {

    private final int yRadius;

    public LayeredGenerator(int radiusMin, int radiusMax) {
        this(radiusMin, radiusMax, 3); // default number of layers
    }

    public LayeredGenerator(int radiusMin, int radiusMax, int yRadius) {
        super(radiusMin, radiusMax);
        this.yRadius = yRadius;
    }

    @Override
    public int getYRadius() {
        return yRadius;
    }

    @Override
    public Vec3i getMaxSize() {
        Vec3i result = super.getMaxSize();
        return new Vec3i(result.getX(), yRadius, result.getZ());
    }

    @Override
    public void generateBlock(int x, int y, int z, IBlockGeneratorAccess blockAccess) {
        blockAccess.generateBlock(x, y, z, false);
    }
}
