package gregtech.api.worldgen.shape;

import net.minecraft.util.math.Vec3i;

import java.util.Random;

public abstract class ShapeGenerator {

    public abstract Vec3i getMaxSize();

    /**
     * Generates shape with the given generator access
     *
     * @param gridRandom          random instance to use for generation
     * @param relativeBlockAccess block access
     */
    public abstract void generate(Random gridRandom, IBlockGeneratorAccess relativeBlockAccess);
}
