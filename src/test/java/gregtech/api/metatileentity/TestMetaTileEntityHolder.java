package gregtech.api.metatileentity;

import gregtech.api.util.world.DummyChunkProvider;
import gregtech.api.util.world.DummySaveHandler;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.WorldInfo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Headless {@link MetaTileEntityHolder} test helper backed by a server-side dummy {@link World}
 * with network/chunk/reflection side effects disabled.
 */
public class TestMetaTileEntityHolder extends MetaTileEntityHolder {

    private static final World TEST_WORLD = new World(
            new DummySaveHandler(),
            new WorldInfo(new WorldSettings(1L, GameType.SURVIVAL, true, false, WorldType.DEFAULT), "TestServer"),
            new WorldProviderSurface(),
            new Profiler(),
            false) {

        @Override
        protected @NotNull IChunkProvider createChunkProvider() {
            return new DummyChunkProvider(this);
        }

        @Override
        protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
            return false;
        }

        @Override
        public void notifyNeighborsRespectDebug(@NotNull BlockPos pos, @NotNull Block blockType, boolean update) {}

        @Override
        public void notifyNeighborsOfStateChange(@NotNull BlockPos pos, @NotNull Block blockType, boolean update) {}

        @Override
        public void notifyNeighborsOfStateExcept(@NotNull BlockPos pos, @NotNull Block blockType,
                                                 @NotNull EnumFacing skipSide) {}

        @Override
        public void markAndNotifyBlock(@NotNull BlockPos pos, @Nullable Chunk chunk, @NotNull IBlockState oldState,
                                       @NotNull IBlockState newState, int flags) {}

        @Override
        public void notifyBlockUpdate(@NotNull BlockPos pos, @NotNull IBlockState oldState,
                                      @NotNull IBlockState newState, int flags) {}

        @Override
        public boolean checkLightFor(@NotNull EnumSkyBlock lightType, @NotNull BlockPos pos) {
            return true;
        }
    };

    public TestMetaTileEntityHolder(@NotNull MetaTileEntity mte) {
        setWorld(TEST_WORLD);
        setPos(BlockPos.ORIGIN);
        setRawMetaTileEntity(mte);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void notifyBlockUpdate() {}

    @Override
    public void markAsDirty() {}

    @Override
    public @NotNull Block getBlockType() {
        return Blocks.AIR;
    }

    @Override
    public int getBlockMetadata() {
        return 0;
    }
}
