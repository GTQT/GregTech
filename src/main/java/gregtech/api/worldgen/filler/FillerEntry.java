package gregtech.api.worldgen.filler;

import net.minecraft.block.BlockStone;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface FillerEntry {

    IBlockState apply(IBlockState source, IBlockAccess blockAccess, BlockPos blockPos);

    Collection<IBlockState> getPossibleResults();

    default List<Pair<Integer, FillerEntry>> getEntries() {
        return new ArrayList<>();
    }

    static FillerEntry createSimpleFiller(IBlockState blockState) {
        List<IBlockState> possibleResults = ImmutableList.of(blockState);
        return new FillerEntry() {

            @Override
            public IBlockState apply(IBlockState source, IBlockAccess blockAccess, BlockPos blockPos) {
                return blockState;
            }

            @Override
            public List<IBlockState> getPossibleResults() {
                return possibleResults;
            }
        };
    }

    /**
     * 原版石头球的填充语义：只替换天然石头（石头 / 花岗岩 / 闪长岩 / 安山岩），空气、水与其它方块原样保留。
     * 对应 {@code WorldGenMinable.StonePredicate}，避免往洞穴里灌土。
     */
    static FillerEntry createNaturalStoneFiller(IBlockState blockState) {
        List<IBlockState> possibleResults = ImmutableList.of(blockState);
        return new FillerEntry() {

            @Override
            public IBlockState apply(IBlockState source, IBlockAccess blockAccess, BlockPos blockPos) {
                if (source == null || source.getBlock() != Blocks.STONE) return source;
                return source.getValue(BlockStone.VARIANT).isNatural() ? blockState : source;
            }

            @Override
            public List<IBlockState> getPossibleResults() {
                return possibleResults;
            }
        };
    }
}
