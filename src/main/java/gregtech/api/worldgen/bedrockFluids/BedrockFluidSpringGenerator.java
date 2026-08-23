package gregtech.api.worldgen.bedrockFluids;

import gregtech.api.util.random.XoShiRo256PlusPlusRandom;
import gregtech.api.worldgen.config.BedrockFluidDepositDefinition;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Random;

/**
 * 基岩流体泉生成器：在基岩流体矿脉单位（8x8 区块）中心位置的基岩层生成 12~16 个泉喷口。
 * 喷口以矿脉单位中心为基准呈高斯分布（越靠近中心密度越高），替换所在位置的基岩。
 * <p>
 * 仅在矿脉单位中心区块 populate 时运行一次；位置由世界种子 + 矿脉单位坐标确定性决定，
 * 同一世界多次生成结果一致，无需存档记录喷口位置。
 */
public class BedrockFluidSpringGenerator implements IWorldGenerator {

    public static final BedrockFluidSpringGenerator INSTANCE = new BedrockFluidSpringGenerator();

    private static final int VEIN_CHUNK_SIZE = 8;
    private static final int MIN_SPRINGS = 12;
    private static final int MAX_SPRINGS = 16;
    /** 喷口相对单位中心的偏移上限（方块），保证喷口落在中心区块 16x16 内 */
    private static final int MAX_SPRING_DISTANCE = 7;
    /** 高斯分布标准差，中心密度随距离快速衰减 */
    private static final double SPRING_SIGMA = 3.0;

    private BedrockFluidSpringGenerator() {}

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator,
                         IChunkProvider chunkProvider) {
        int veinX = Math.floorDiv(chunkX, VEIN_CHUNK_SIZE);
        int veinZ = Math.floorDiv(chunkZ, VEIN_CHUNK_SIZE);
        int centerChunkX = veinX * VEIN_CHUNK_SIZE + VEIN_CHUNK_SIZE / 2;
        int centerChunkZ = veinZ * VEIN_CHUNK_SIZE + VEIN_CHUNK_SIZE / 2;
        // 只在矿脉单位中心区块生成，避免重复
        if (chunkX != centerChunkX || chunkZ != centerChunkZ) return;

        BedrockFluidVeinHandler.FluidVeinWorldEntry entry = BedrockFluidVeinHandler
                .getFluidVeinWorldEntry(world, chunkX, chunkZ);
        BedrockFluidDepositDefinition definition = entry.getDefinition();
        if (definition == null || !definition.getDimensionFilter().test(world.provider)) return;

        Random springRandom = new XoShiRo256PlusPlusRandom(
                31L * 31 * veinX + veinZ * 31L + Long.hashCode(world.getSeed()));

        int count = MIN_SPRINGS + springRandom.nextInt(MAX_SPRINGS - MIN_SPRINGS + 1);
        int centerBlockX = centerChunkX * 16 + 8;
        int centerBlockZ = centerChunkZ * 16 + 8;

        LongSet placed = new LongOpenHashSet();
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < count; i++) {
            int dx = gaussianOffset(springRandom);
            int dz = gaussianOffset(springRandom);

            // 去重：两个喷口不能落在同一格
            long key = (long) dx << 32 | (dz & 0xFFFFFFFFL);
            if (!placed.add(key)) {
                i--;
                continue;
            }

            mpos.setPos(centerBlockX + dx, 0, centerBlockZ + dz);
            // 只替换基岩（超平坦等无基岩的世界天然跳过）
            if (world.getBlockState(mpos).getBlock() != Blocks.BEDROCK) continue;

            world.setBlockState(mpos, MetaBlocks.BEDROCK_FLUID_SPRING.getDefaultState(), 3);
        }
    }

    /** 高斯偏移（拒绝采样限制在最大距离内），越靠近中心概率越高 */
    private static int gaussianOffset(Random random) {
        int offset;
        do {
            offset = (int) Math.round(random.nextGaussian() * SPRING_SIGMA);
        } while (offset < -MAX_SPRING_DISTANCE || offset > MAX_SPRING_DISTANCE);
        return offset;
    }
}
