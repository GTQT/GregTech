package gregtech.api.worldgen.filler;

import gregtech.api.worldgen.config.FillerConfigUtils.LayeredFillerEntry;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import java.util.Collections;
import java.util.List;
import java.util.Random;

public class LayeredBlockFiller extends BlockFiller {

    private final LayeredFillerEntry fillerEntry;

    public LayeredBlockFiller(LayeredFillerEntry fillerEntry) {
        this.fillerEntry = fillerEntry;
    }

    @Override
    public IBlockState apply(IBlockState currentState, IBlockAccess blockAccess, BlockPos blockPos, int relativeX,
                             int relativeY, int relativeZ, double density, Random gridRandom, int layer) {
        return fillerEntry.apply(currentState, blockAccess, blockPos, density, gridRandom, layer);
    }

    @Override
    public List<FillerEntry> getAllPossibleStates() {
        return Collections.singletonList(fillerEntry);
    }

    public FillerEntry getPrimary() {
        return fillerEntry.getPrimary();
    }

    public FillerEntry getSecondary() {
        return fillerEntry.getSecondary();
    }

    public FillerEntry getBetween() {
        return fillerEntry.getBetween();
    }

    public FillerEntry getSporadic() {
        return fillerEntry.getSporadic();
    }
}
