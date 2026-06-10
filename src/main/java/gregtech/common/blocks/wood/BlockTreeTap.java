package gregtech.common.blocks.wood;

import gregtech.common.creativetab.GTCreativeTabs;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockTreeTap extends Block implements ITileEntityProvider {

    // 朝向橡胶木的方向
    public static final PropertyDirection ATTACHED_FACING = PropertyDirection.create("attached_facing");

    // 各方向的 AABB：薄板贴在对应面上
    private static final AxisAlignedBB AABB_NORTH = new AxisAlignedBB(2.0 / 16.0, 2.0 / 16.0, 0.0,
            14.0 / 16.0, 14.0 / 16.0, 1.0 / 16.0);
    private static final AxisAlignedBB AABB_SOUTH = new AxisAlignedBB(2.0 / 16.0, 2.0 / 16.0, 15.0 / 16.0,
            14.0 / 16.0, 14.0 / 16.0, 1.0);
    private static final AxisAlignedBB AABB_WEST = new AxisAlignedBB(0.0, 2.0 / 16.0, 2.0 / 16.0,
            1.0 / 16.0, 14.0 / 16.0, 14.0 / 16.0);
    private static final AxisAlignedBB AABB_EAST = new AxisAlignedBB(15.0 / 16.0, 2.0 / 16.0, 2.0 / 16.0,
            1.0, 14.0 / 16.0, 14.0 / 16.0);
    private static final AxisAlignedBB AABB_UP = new AxisAlignedBB(2.0 / 16.0, 15.0 / 16.0, 2.0 / 16.0,
            14.0 / 16.0, 1.0, 14.0 / 16.0);
    private static final AxisAlignedBB AABB_DOWN = new AxisAlignedBB(2.0 / 16.0, 0.0, 2.0 / 16.0,
            14.0 / 16.0, 1.0 / 16.0, 14.0 / 16.0);

    public BlockTreeTap() {
        super(Material.WOOD);
        this.setDefaultState(this.blockState.getBaseState()
                .withProperty(ATTACHED_FACING, EnumFacing.NORTH));
        setTranslationKey("tree_tap");
        setCreativeTab(GTCreativeTabs.TAB_GREGTECH);
        setHardness(0.5F);
    }

    @NotNull
    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, ATTACHED_FACING);
    }

    @NotNull
    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(ATTACHED_FACING, EnumFacing.byIndex(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(ATTACHED_FACING).getIndex();
    }

    @NotNull
    @Override
    public IBlockState getStateForPlacement(@NotNull World world, @NotNull BlockPos pos,
                                            @NotNull EnumFacing facing,
                                            float hitX, float hitY, float hitZ,
                                            int meta, @NotNull EntityLivingBase placer,
                                            @NotNull EnumHand hand) {
        return getDefaultState().withProperty(ATTACHED_FACING, facing.getOpposite());
    }

    @Override
    public boolean hasTileEntity(@NotNull IBlockState state) {
        return true;
    }

    @Override
    public boolean isFullCube(@NotNull IBlockState state) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(@NotNull IBlockState state) {
        return false;
    }

    @NotNull
    @Override
    public EnumBlockRenderType getRenderType(@NotNull IBlockState state) {
        return EnumBlockRenderType.INVISIBLE;
    }

    @NotNull
    @Override
    public AxisAlignedBB getBoundingBox(@NotNull IBlockState state, @NotNull IBlockAccess source,
                                        @NotNull BlockPos pos) {
        switch (state.getValue(ATTACHED_FACING)) {
            case NORTH:
                return AABB_NORTH;
            case SOUTH:
                return AABB_SOUTH;
            case WEST:
                return AABB_WEST;
            case EAST:
                return AABB_EAST;
            case UP:
                return AABB_UP;
            case DOWN:
            default:
                return AABB_DOWN;
        }
    }

    @Override
    public void neighborChanged(@NotNull IBlockState state, @NotNull World world, @NotNull BlockPos pos,
                                @NotNull Block blockIn, @NotNull BlockPos fromPos) {
        EnumFacing attachedFacing = state.getValue(ATTACHED_FACING);
        BlockPos attachedPos = pos.offset(attachedFacing);

        if (fromPos.equals(attachedPos)) {
            IBlockState attachedState = world.getBlockState(attachedPos);
            if (attachedState.getBlock().isReplaceable(world, attachedPos)) {
                world.setBlockToAir(pos);
            }
        }
    }

    @Override
    public boolean canPlaceBlockOnSide(@NotNull World world, @NotNull BlockPos pos, @NotNull EnumFacing side) {
        BlockPos attachedPos = pos.offset(side.getOpposite());
        IBlockState attachedState = world.getBlockState(attachedPos);
        return attachedState.isSideSolid(world, attachedPos, side);
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(@NotNull World world, int meta) {
        return new TileEntityTreeTap();
    }

    @Override
    public void breakBlock(@NotNull World world, @NotNull BlockPos pos, @NotNull IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityTreeTap treeTap) {
            int durability = treeTap.getDurability();
            ItemStack treeTapStack = treeTap.getItemStack();
            if (durability > 0 && treeTapStack != null) {
                treeTapStack.setItemDamage(treeTapStack.getMaxDamage() - durability);
                Block.spawnAsEntity(world, pos, treeTapStack);
            }
        }
        super.breakBlock(world, pos, state);
    }
}
