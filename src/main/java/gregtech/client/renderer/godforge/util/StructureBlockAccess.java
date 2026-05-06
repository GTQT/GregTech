package gregtech.client.renderer.godforge.util;

import java.util.HashMap;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

public class StructureBlockAccess implements IBlockAccess {

    private final String[][] structure;
    private final HashMap<Character, Pair<Block, Integer>> mapper;
    private final HashMap<BlockPos, IBlockState> stateCache = new HashMap<>();
    private final HashMap<BlockPos, Boolean> opaqueCache = new HashMap<>();

    private final int sizeX, sizeY, sizeZ;
    private final int offsetX, offsetY, offsetZ;

    public StructureBlockAccess(String[][] structure, HashMap<Character, Pair<Block, Integer>> mapper) {
        this.structure = structure;
        this.mapper = mapper;

        this.sizeX = structure.length;
        this.sizeY = structure[0].length;
        this.sizeZ = structure[0][0].length();

        this.offsetX = sizeX / 2;
        this.offsetY = sizeY / 2;
        this.offsetZ = sizeZ / 2;
    }

    private boolean isInBounds(int x, int y, int z) {
        return x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= 0 && z < sizeZ;
    }

    private boolean isOpaqueAt(int x, int y, int z) {
        if (!isInBounds(x, y, z)) return false;
        char letter = structure[x][y].charAt(z);
        if (letter == ' ') return false;
        Pair<Block, Integer> info = mapper.get(letter);
        if (info == null) return false;
        if (info.getLeft() == Blocks.AIR) return false;
        return info.getLeft().isOpaqueCube(null);
    }

    @Nullable
    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        return 15728880;
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        BlockPos cacheKey = pos;
        IBlockState cached = stateCache.get(cacheKey);
        if (cached != null) return cached;

        int x = pos.getX() + offsetX;
        int y = pos.getY() + offsetY;
        int z = pos.getZ() + offsetZ;

        if (!isInBounds(x, y, z)) {
            IBlockState air = Blocks.AIR.getDefaultState();
            stateCache.put(cacheKey, air);
            return air;
        }

        char letter = structure[x][y].charAt(z);
        if (letter == ' ') {
            IBlockState air = Blocks.AIR.getDefaultState();
            stateCache.put(cacheKey, air);
            return air;
        }

        Pair<Block, Integer> info = mapper.get(letter);
        if (info == null) {
            IBlockState air = Blocks.AIR.getDefaultState();
            stateCache.put(cacheKey, air);
            return air;
        }

        IBlockState state = info.getLeft().getStateFromMeta(info.getRight());
        stateCache.put(cacheKey, state);
        return state;
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        IBlockState state = getBlockState(pos);
        return state.getBlock() == Blocks.AIR;
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return Biome.getBiome(0);
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        return 0;
    }

    @Override
    public WorldType getWorldType() {
        return WorldType.DEFAULT;
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
        int x = pos.getX() + offsetX;
        int y = pos.getY() + offsetY;
        int z = pos.getZ() + offsetZ;
        if (!isInBounds(x, y, z)) return _default;
        IBlockState state = getBlockState(pos);
        return state.getBlock() != Blocks.AIR && state.isSideSolid(this, pos, side);
    }
}
