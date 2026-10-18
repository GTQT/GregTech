package gregtech.client.renderer.scene;

import gregtech.api.util.BlockInfo;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;

/** Read-only single-block override for candidate models; never replaces a block in the scene. */
public final class PreviewBlockAccess implements IBlockAccess {
    private final IBlockAccess source;
    private final BlockPos position;
    private final BlockInfo candidate;

    public PreviewBlockAccess(IBlockAccess source, BlockPos position, BlockInfo candidate) {
        this.source = source;
        this.position = position;
        this.candidate = candidate;
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return position.equals(pos) ? candidate.getTileEntity() : source.getTileEntity(pos);
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return position.equals(pos) ? candidate.getBlockState() : source.getBlockState(pos);
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        return source.getCombinedLight(pos, lightValue);
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        return getBlockState(pos).getBlock() == Blocks.AIR;
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return source.getBiome(pos);
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        return 0;
    }

    @Override
    public WorldType getWorldType() {
        return source.getWorldType();
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean defaultValue) {
        return getBlockState(pos).isSideSolid(this, pos, side);
    }
}
