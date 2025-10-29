package gregtech.worldgen.impl;

import gregtech.common.blocks.MetaBlocks;
import gregtech.common.blocks.wood.BlockRubberLog;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockGrass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenAbstractTree;

import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class WorldGenRubberTree extends WorldGenAbstractTree {

    public static final WorldGenRubberTree INSTANCE = new WorldGenRubberTree(false);
    public static final WorldGenRubberTree INSTANCE_NOTIFY = new WorldGenRubberTree(true);

    protected WorldGenRubberTree(boolean notify) {
        super(notify);
    }

    @Override
    public boolean generate(@NotNull World world, @NotNull Random rand, @NotNull BlockPos pos) {

        IBlockState state = world.getBlockState(pos.add(0,-1,0));
        if (!(state.getMaterial().isSolid() && (state.getBlock() instanceof BlockGrass || state.getBlock() instanceof BlockDirt))) {
            return false; // 返回基座上方位置
        }

        int trunkHeight = rand.nextInt(3) + 5; // 5-7 logs

        final int maxWorldHeight = world.getHeight();
        int posX = pos.getX();
        int posY = pos.getY();
        int posZ = pos.getZ();

        if (posY <= 1) {
            return false;
        }

        final int topLeafHeight = trunkHeight + 3;
        final int ySpaceRequired = posY + topLeafHeight + 1;
        final int leafStartY = ySpaceRequired - 2;

        // check if there is enough room to fit the whole tree
        if (ySpaceRequired >= maxWorldHeight) {
            return false;
        }
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int y = posY; y < ySpaceRequired; y++) {
            int radius;
            if (y == posY) {
                radius = 0;
            } else if (y < leafStartY) {
                radius = 1;
            } else {
                radius = 2;
            }

            final int xLimit = posX + radius;
            final int zLimit = posZ + radius;
            for (int x = posX - radius; x <= xLimit; x++) {
                for (int z = posZ - radius; z < zLimit; z++) {
                    mutable.setPos(x, y, z);
                    if (!isReplaceable(world, mutable)) {
                        return false;
                    }
                }
            }
        }

        // check for valid soil
        mutable.setPos(posX, posY - 1, posZ);
        IBlockState soilState = world.getBlockState(mutable);
        Block soilBlock = soilState.getBlock();
        if (!soilBlock.canSustainPlant(soilState, world, mutable, EnumFacing.UP, MetaBlocks.RUBBER_SAPLING)) {
            return false;
        }

        soilBlock.onPlantGrow(soilState, world, mutable, pos);

        // ======== 树脂孔生成逻辑（参考IC2） ========
        int treeholechance = 25; // 初始25%几率生成树脂孔

        // 生成树干（包含树脂孔）
        for (int cHeight = 0; cHeight < trunkHeight; cHeight++) {
            BlockPos cPos = pos.up(cHeight);

            if (rand.nextInt(100) <= treeholechance) {
                // 生成带树脂的橡胶木
                treeholechance -= 10; // 每生成一个树脂孔，几率减少10%
                EnumFacing resinFacing = EnumFacing.HORIZONTALS[rand.nextInt(4)];
                IBlockState resinLogState = MetaBlocks.RUBBER_LOG.getDefaultState()
                        .withProperty(BlockRubberLog.STATE, BlockRubberLog.RubberWoodState.getWetState(resinFacing));
                setBlockAndNotifyAdequately(world, cPos, resinLogState);
            } else {
                // 生成普通橡胶木
                IBlockState logState = MetaBlocks.RUBBER_LOG.getDefaultState()
                        .withProperty(BlockRubberLog.STATE, BlockRubberLog.RubberWoodState.PLAIN_Y);
                setBlockAndNotifyAdequately(world, cPos, logState);
            }

            // 生成叶子（保持原有逻辑）
            if (trunkHeight < 4 || trunkHeight < 7 && cHeight > 1 || cHeight > 2) {
                for (int cx = posX - 2; cx <= posX + 2; cx++) {
                    for (int cz = posZ - 2; cz <= posZ + 2; cz++) {
                        int chance = Math.max(1, cHeight + 4 - trunkHeight);
                        int dx = Math.abs(cx - posX);
                        int dz = Math.abs(cz - posZ);
                        if (dx <= 1 && dz <= 1 || dx <= 1 && rand.nextInt(chance) == 0 || dz <= 1 && rand.nextInt(chance) == 0) {
                            mutable.setPos(cx, posY + cHeight, cz);
                            if (world.isAirBlock(mutable)) {
                                setBlockAndNotifyAdequately(world, new BlockPos(mutable), MetaBlocks.RUBBER_LEAVES.getDefaultState());
                            }
                        }
                    }
                }
            }
        }

        // 生成顶部叶子
        for (int i = 0; i <= trunkHeight / 4 + rand.nextInt(2); ++i) {
            mutable.setPos(posX, posY + trunkHeight + i, posZ);
            if (world.isAirBlock(mutable)) {
                setBlockAndNotifyAdequately(world, new BlockPos(mutable), MetaBlocks.RUBBER_LEAVES.getDefaultState());
            }
        }

        return true;
    }
}
