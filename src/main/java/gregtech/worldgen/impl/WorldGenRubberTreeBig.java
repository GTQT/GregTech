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

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public class WorldGenRubberTreeBig extends WorldGenAbstractTree {

    public static final WorldGenRubberTreeBig INSTANCE = new WorldGenRubberTreeBig(false);

    protected WorldGenRubberTreeBig(boolean notify) {
        super(notify);
    }

    @Override
    public boolean generate(@NotNull World world, @NotNull Random rand, @NotNull BlockPos pos) {

        IBlockState state = world.getBlockState(pos.add(0,-1,0));
        if (!(state.getMaterial().isSolid() && (state.getBlock() instanceof BlockGrass || state.getBlock() instanceof BlockDirt))) {
            return false;
        }

        int trunkHeight = rand.nextInt(6) + 12; // 12-18 logs

        final int maxWorldHeight = world.getHeight();
        int posX = pos.getX();
        int posY = pos.getY();
        int posZ = pos.getZ();

        if (posY <= 1) {
            return false;
        }

        final int ySpaceRequired = posY + trunkHeight + 5;

        // check if there is enough room to fit the whole tree
        if (ySpaceRequired >= maxWorldHeight) {
            return false;
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        // ======== 树脂孔生成逻辑 ========
        int treeholechance = 25; // 初始25%几率生成树脂孔

        // ======== 生成主干（包含树脂孔） ========
        for (int y = 0; y < trunkHeight; ++y) {
            mutable.setPos(posX, posY + y, posZ);

            if (isReplaceable(world, mutable)) {
                if (rand.nextInt(100) <= treeholechance) {
                    // 生成带树脂的橡胶木
                    treeholechance -= 10;
                    EnumFacing resinFacing = EnumFacing.HORIZONTALS[rand.nextInt(4)];
                    IBlockState resinLogState = MetaBlocks.RUBBER_LOG.getDefaultState()
                            .withProperty(BlockRubberLog.STATE, BlockRubberLog.RubberWoodState.getWetState(resinFacing));
                    setBlockAndNotifyAdequately(world, mutable, resinLogState);
                } else {
                    // 生成普通橡胶木
                    IBlockState logState = MetaBlocks.RUBBER_LOG.getDefaultState()
                            .withProperty(BlockRubberLog.STATE, BlockRubberLog.RubberWoodState.PLAIN_Y);
                    setBlockAndNotifyAdequately(world, mutable, logState);
                }
            }
        }

        // ======== 叶子生成部分 ========
        int leafLayers = 4 + rand.nextInt(2); // 4-6层叶子
        int baseLeafY = posY + trunkHeight - leafLayers;

        // 生成锥形叶子层
        for (int layer = 0; layer < leafLayers; ++layer) {
            int currentRadius = 1 + (leafLayers - layer);

            for (int xOffset = -currentRadius; xOffset <= currentRadius; ++xOffset) {
                for (int zOffset = -currentRadius; zOffset <= currentRadius; ++zOffset) {
                    int manhattanDist = Math.abs(xOffset) + Math.abs(zOffset);
                    if (manhattanDist > currentRadius + 1) continue;

                    if (manhattanDist == currentRadius + 1 && rand.nextFloat() < 0.5f) continue;

                    int yPos = baseLeafY + layer;
                    mutable.setPos(posX + xOffset, yPos, posZ + zOffset);

                    if (isReplaceable(world, mutable)) {
                        setBlockAndNotifyAdequately(world, mutable, MetaBlocks.RUBBER_LEAVES.getDefaultState());
                    }
                }
            }
        }

        // ======== 添加小枝干（不生成树脂孔） ========
        for (int layer = 0; layer < 2; ++layer) {
            int branchY = posY + trunkHeight - leafLayers - layer;
            EnumFacing[] directions = EnumFacing.HORIZONTALS;
            Collections.shuffle(Arrays.asList(directions), rand);

            for (int i = 0; i < 2; ++i) {
                EnumFacing dir = directions[i];
                mutable.setPos(posX, branchY, posZ).move(dir);

                if (isReplaceable(world, mutable)) {
                    // 分支只生成普通橡胶木，不生成带树脂的
                    IBlockState logState = MetaBlocks.RUBBER_LOG.getDefaultState()
                            .withProperty(BlockRubberLog.STATE, BlockRubberLog.RubberWoodState.PLAIN_Y);
                    setBlockAndNotifyAdequately(world, mutable, logState);

                    // 添加末端叶子
                    mutable.move(dir);
                    if (isReplaceable(world, mutable)) {
                        setBlockAndNotifyAdequately(world, mutable, MetaBlocks.RUBBER_LEAVES.getDefaultState());
                    }
                }
            }
        }

        return true;
    }
}
