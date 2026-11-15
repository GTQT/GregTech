package gregtech.api.worldgen.generator;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WorldGeneratorImpl implements IWorldGenerator {

    public static final WorldGeneratorImpl INSTANCE = new WorldGeneratorImpl();

    public static final int GRID_SIZE_X = 3;
    public static final int GRID_SIZE_Z = 3;

    private WorldGeneratorImpl() {}

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
                         IChunkProvider chunkProvider) {
        int selfGridX = Math.floorDiv(chunkX, GRID_SIZE_X);
        int selfGridZ = Math.floorDiv(chunkZ, GRID_SIZE_Z);
        // Farmer's Future Delight runs this bridge inside a legacy 0..255 height scope; opt back into the extended
        // range so that veins and bedrock springs can be placed below Y=0 in a "Caves & Cliffs" world.
        AutoCloseable heightScope = WorldHeightCompat.enterExtendedWorldgen(world);
        try {
            generateInternal(world, selfGridX, selfGridZ, chunkX, chunkZ, random);
        } finally {
            closeQuietly(heightScope);
        }
    }

    private static void closeQuietly(AutoCloseable scope) {
        try {
            scope.close();
        } catch (Exception ignored) {
            // restoring the previous height scope must never fail world generation
        }
    }

    private static void generateInternal(World world, int selfGridX, int selfGridZ, int chunkX, int chunkZ,
                                         Random random) {
        int halfSizeX = (GRID_SIZE_X - 1) / 2;
        int halfSizeZ = (GRID_SIZE_Z - 1) / 2;
        List<CachedGridEntry> entries = new ArrayList<>(GRID_SIZE_X * GRID_SIZE_Z);
        for (int gridX = -halfSizeX; gridX <= halfSizeX; gridX++) {
            for (int gridZ = -halfSizeZ; gridZ <= halfSizeZ; gridZ++) {
                entries.add(CachedGridEntry.getOrCreateEntry(world, selfGridX + gridX, selfGridZ + gridZ, chunkX,
                        chunkZ));
            }
        }
        // Two passes: every stone layer (non-vein deposit) of all nine grid entries is written before the first vein.
        // Otherwise a sphere from a grid entry processed later would overwrite the stone around veins that were
        // already written, leaving those veins on their old base block (deepslate ores inside a marble sphere).
        for (CachedGridEntry entry : entries) {
            entry.populateStoneLayers(world, chunkX, chunkZ);
        }
        for (CachedGridEntry entry : entries) {
            entry.populateOreLayers(world, chunkX, chunkZ, random);
        }
    }
}
