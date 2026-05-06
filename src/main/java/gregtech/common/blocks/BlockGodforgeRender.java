package gregtech.common.blocks;

import gregtech.client.renderer.godforge.GodforgeRenderTileEntity;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Invisible block that hosts the GodforgeRenderTileEntity for star rendering.
 * Placed at the center of the Forge of Gods structure when the renderer is active.
 */
public class BlockGodforgeRender extends Block {

    private static final AxisAlignedBB EMPTY_AABB = new AxisAlignedBB(0, 0, 0, 0, 0, 0);

    public BlockGodforgeRender() {
        super(Material.BARRIER);
        setTranslationKey("godforge_render");
        setHardness(-1.0f);
        setResistance(Float.MAX_VALUE);
        setLightOpacity(0);
    }

    @Override
    public boolean hasTileEntity(@NotNull IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(@NotNull World world, @NotNull IBlockState state) {
        return new GodforgeRenderTileEntity();
    }

    @Override
    @NotNull
    public EnumBlockRenderType getRenderType(@NotNull IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @Override
    @NotNull
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public boolean isOpaqueCube(@NotNull IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(@NotNull IBlockState state) {
        return false;
    }

    @Override
    @NotNull
    public AxisAlignedBB getBoundingBox(@NotNull IBlockState state, @NotNull IBlockAccess source,
                                        @NotNull BlockPos pos) {
        return EMPTY_AABB;
    }

    @Nullable
    @Override
    public AxisAlignedBB getCollisionBoundingBox(@NotNull IBlockState blockState, @NotNull IBlockAccess worldIn,
                                                 @NotNull BlockPos pos) {
        return NULL_AABB;
    }

    @Override
    public boolean isReplaceable(@NotNull IBlockAccess worldIn, @NotNull BlockPos pos) {
        return false;
    }
}
