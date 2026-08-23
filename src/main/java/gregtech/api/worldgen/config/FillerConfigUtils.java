package gregtech.api.worldgen.config;

import gregtech.api.unification.ore.StoneType;
import gregtech.api.unification.ore.StoneTypes;
import gregtech.api.util.GTUtility;
import gregtech.api.util.WorldBlockPredicate;
import gregtech.api.worldgen.filler.FillerEntry;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class FillerConfigUtils {

    private FillerConfigUtils() {}

    public static class OreFilterEntry implements FillerEntry {

        private final Map<StoneType, IBlockState> blockStateMap;
        private final ImmutableSet<IBlockState> allowedStates;
        private final StoneType defaultValue;

        public OreFilterEntry(Map<StoneType, IBlockState> blockStateMap) {
            this.blockStateMap = blockStateMap;
            this.defaultValue = blockStateMap.containsKey(StoneTypes.STONE) ? StoneTypes.STONE :
                    blockStateMap.keySet().iterator().next();
            this.allowedStates = ImmutableSet.copyOf(blockStateMap.values());
        }

        @Override
        public IBlockState apply(IBlockState source, IBlockAccess blockAccess, BlockPos blockPos) {
            StoneType stoneType = StoneType.computeStoneType(source, blockAccess, blockPos);
            return blockStateMap.get(stoneType == null ? defaultValue : stoneType);
        }

        @Override
        public Set<IBlockState> getPossibleResults() {
            return allowedStates;
        }
    }

    public static class BlockStateMatcherEntry implements FillerEntry {

        private final List<Pair<WorldBlockPredicate, FillerEntry>> matchers;
        private final ImmutableList<IBlockState> blockStates;

        public BlockStateMatcherEntry(List<Pair<WorldBlockPredicate, FillerEntry>> matchers) {
            this.matchers = matchers;
            ImmutableList.Builder<IBlockState> stateBuilder = ImmutableList.builder();
            for (Pair<WorldBlockPredicate, FillerEntry> matcher : matchers) {
                stateBuilder.addAll(matcher.getRight().getPossibleResults());
            }
            this.blockStates = stateBuilder.build();
        }

        @Override
        public IBlockState apply(IBlockState source, IBlockAccess blockAccess, BlockPos blockPos) {
            for (Pair<WorldBlockPredicate, FillerEntry> matcher : matchers) {
                if (matcher.getLeft().test(source, blockAccess, blockPos)) {
                    return matcher.getRight().apply(source, blockAccess, blockPos);
                }
            }
            return Blocks.AIR.getDefaultState();
        }

        @Override
        public Collection<IBlockState> getPossibleResults() {
            return blockStates;
        }
    }

    public static class WeightRandomMatcherEntry implements FillerEntry {

        private final List<Pair<Integer, FillerEntry>> randomList;
        private final ImmutableList<IBlockState> blockStates;

        public WeightRandomMatcherEntry(List<Pair<Integer, FillerEntry>> randomList) {
            this.randomList = randomList;
            ImmutableList.Builder<IBlockState> stateBuilder = ImmutableList.builder();
            for (Pair<Integer, FillerEntry> randomEntry : randomList) {
                stateBuilder.addAll(randomEntry.getRight().getPossibleResults());
            }
            this.blockStates = stateBuilder.build();
        }

        @Override
        public IBlockState apply(IBlockState source, IBlockAccess blockAccess, BlockPos blockPos) {
            int functionIndex = GTUtility.getRandomItem(randomList, randomList.size());
            FillerEntry randomFunction = randomList.get(functionIndex).getValue();
            return randomFunction.apply(source, blockAccess, blockPos);
        }

        @Override
        public Collection<IBlockState> getPossibleResults() {
            return blockStates;
        }

        @Override
        public List<Pair<Integer, FillerEntry>> getEntries() {
            return randomList;
        }
    }

    public static class LayeredFillerEntry implements FillerEntry {

        private final FillerEntry primary;
        private final FillerEntry secondary;
        private final FillerEntry between;
        private final FillerEntry sporadic;

        private final int primaryLayers;
        private final int secondaryLayers;
        private final int betweenLayers;

        // Provided for readability
        private final int sporadicDivisor;
        private final int startPrimary;
        private final int startBetween;

        private final ImmutableList<IBlockState> blockStates;

        public LayeredFillerEntry(FillerEntry primary, FillerEntry secondary, FillerEntry between,
                                  FillerEntry sporadic) {
            this.primary = primary;
            this.secondary = secondary;
            this.between = between;
            this.sporadic = sporadic;

            this.primaryLayers = 4;
            this.secondaryLayers = 3;
            this.betweenLayers = 3;

            this.sporadicDivisor = primaryLayers + secondaryLayers - 1;
            this.startPrimary = secondaryLayers;
            this.startBetween = secondaryLayers - betweenLayers / 2;

            this.blockStates = ImmutableList.<IBlockState>builder()
                    .addAll(this.primary.getPossibleResults())
                    .addAll(this.secondary.getPossibleResults())
                    .addAll(this.between.getPossibleResults())
                    .addAll(this.sporadic.getPossibleResults())
                    .build();
        }

        public LayeredFillerEntry(FillerEntry primary, FillerEntry secondary, FillerEntry between, FillerEntry sporadic,
                                  int primaryLayers, int secondaryLayers, int betweenLayers) {
            this.primary = primary;
            this.secondary = secondary;
            this.between = between;
            this.sporadic = sporadic;

            this.primaryLayers = primaryLayers;
            this.secondaryLayers = secondaryLayers;
            this.betweenLayers = betweenLayers;

            // Ensure "between" is not more than the total primary and secondary layers
            Preconditions.checkArgument(primaryLayers + secondaryLayers >= betweenLayers,
                    "Error: cannot be more \"between\" layers than primary and secondary layers combined!");

            this.sporadicDivisor = primaryLayers + secondaryLayers - 1;
            this.startPrimary = secondaryLayers;
            this.startBetween = secondaryLayers - betweenLayers / 2;

            this.blockStates = ImmutableList.<IBlockState>builder()
                    .addAll(this.primary.getPossibleResults())
                    .addAll(this.secondary.getPossibleResults())
                    .addAll(this.between.getPossibleResults())
                    .addAll(this.sporadic.getPossibleResults())
                    .build();
        }

        @Override
        public IBlockState apply(IBlockState source, IBlockAccess blockAccess, BlockPos blockPos) {
            // should never be called, but just to be safe...
            return apply(source, blockAccess, blockPos, 1.0, new Random(), 0);
        }

        public IBlockState apply(IBlockState source, IBlockAccess blockAccess, BlockPos blockPos, double density,
                                 Random random, int layer) {
            // First try to spawn "between"
            if (layer >= startBetween && layer - startBetween + 1 <= betweenLayers) {
                if (random.nextFloat() <= density / 2) {
                    return between.apply(source, blockAccess, blockPos);
                }
            }

            // Then try primary/secondary
            if (layer >= startPrimary) {
                if (random.nextFloat() <= density) {
                    return primary.apply(source, blockAccess, blockPos);
                }
            } else {
                if (random.nextFloat() <= density) {
                    return secondary.apply(source, blockAccess, blockPos);
                }
            }

            // Then lastly, try sporadic
            if (random.nextFloat() <= density / sporadicDivisor) {
                return sporadic.apply(source, blockAccess, blockPos);
            }
            return source;
        }

        @Override
        public Collection<IBlockState> getPossibleResults() {
            return blockStates;
        }

        @Override
        public List<Pair<Integer, FillerEntry>> getEntries() {
            return Collections.emptyList(); // todo
        }

        public FillerEntry getPrimary() {
            return primary;
        }

        public FillerEntry getSecondary() {
            return secondary;
        }

        public FillerEntry getBetween() {
            return between;
        }

        public FillerEntry getSporadic() {
            return sporadic;
        }

        public int getPrimaryLayers() {
            return primaryLayers;
        }

        public int getSecondaryLayers() {
            return secondaryLayers;
        }

        public int getBetweenLayers() {
            return betweenLayers;
        }
    }
}
