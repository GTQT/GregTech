package gregtech.api.worldgen.filler;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.util.List;
import java.util.Random;

public abstract class BlockFiller {

    public abstract IBlockState apply(IBlockState currentState, IBlockAccess blockAccess, BlockPos blockPos,
                                      int relativeX, int relativeY, int relativeZ, double density, Random gridRandom,
                                      int layer);

    public abstract List<FillerEntry> getAllPossibleStates();
}
