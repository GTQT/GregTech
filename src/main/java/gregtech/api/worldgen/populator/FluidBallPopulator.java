package gregtech.api.worldgen.populator;

import gregtech.api.worldgen.config.OreDepositDefinition;
import gregtech.api.worldgen.generator.GridEntryInfo;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.BlockFluidBase;

import java.util.Random;

public class FluidBallPopulator implements VeinBufferPopulator {

    private final IBlockState fluidState;
    private final float springGenerationChance;

    public FluidBallPopulator(IBlockState fluidState, float springGenerationChance) {
        this.fluidState = fluidState;
        this.springGenerationChance = springGenerationChance;
    }

    @Override
    public void populateBlockBuffer(Random random, GridEntryInfo gridEntryInfo, IBlockModifierAccess modifier,
                                    OreDepositDefinition depositDefinition) {
        if (random.nextFloat() <= springGenerationChance) {
            // 球体半径，随机在3-5格之间
            int radius = 3 + random.nextInt(3);
            int centerY = gridEntryInfo.getCenterPos(depositDefinition).getY();

            // 生成球体
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        // 计算距离中心的平方距离
                        double distanceSq = x * x + y * y + z * z;
                        // 判断是否在球体范围内
                        if (distanceSq <= radius * radius) {
                            // 设置流体方块，索引为0表示满流体
                            modifier.setBlock(x, centerY + y, z, 0);
                        }
                    }
                }
            }
        }
    }

    @Override
    public IBlockState getBlockByIndex(World world, BlockPos pos, int index) {
        return fluidState.withProperty(BlockFluidBase.LEVEL, index);
    }

    public IBlockState getFluidState() {
        return fluidState;
    }
}
