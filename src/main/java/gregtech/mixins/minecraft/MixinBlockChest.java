package gregtech.mixins.minecraft;

import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockChest.class)
public abstract class MixinBlockChest extends BlockContainer {

    @Shadow
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    @Shadow
    public final BlockChest.Type chestType;
    protected MixinBlockChest(BlockChest.Type chestTypeIn) {
        super(Material.WOOD);
        this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
        this.chestType = chestTypeIn;
        this.setCreativeTab(chestTypeIn == BlockChest.Type.TRAP ? CreativeTabs.REDSTONE : CreativeTabs.DECORATIONS);
    }

    public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack)
    {
        // 获取标准化后的yaw角度（0~360）
        float normalizedYaw = gregtech$normalizeYaw(placer.rotationYaw);

        // 使用精确计算替代索引计算
        EnumFacing enumfacing = gregtech$calculateFacingFromYaw(normalizedYaw).getOpposite();

        state = state.withProperty(FACING, enumfacing);
        BlockPos blockpos = pos.north();
        BlockPos blockpos1 = pos.south();
        BlockPos blockpos2 = pos.west();
        BlockPos blockpos3 = pos.east();
        boolean flag = this == worldIn.getBlockState(blockpos).getBlock();
        boolean flag1 = this == worldIn.getBlockState(blockpos1).getBlock();
        boolean flag2 = this == worldIn.getBlockState(blockpos2).getBlock();
        boolean flag3 = this == worldIn.getBlockState(blockpos3).getBlock();

        if (!flag && !flag1 && !flag2 && !flag3)
        {
            worldIn.setBlockState(pos, state, 3);
        }
        else if (enumfacing.getAxis() != EnumFacing.Axis.X || !flag && !flag1)
        {
            if (enumfacing.getAxis() == EnumFacing.Axis.Z && (flag2 || flag3))
            {
                if (flag2)
                {
                    worldIn.setBlockState(blockpos2, state, 3);
                }
                else
                {
                    worldIn.setBlockState(blockpos3, state, 3);
                }

                worldIn.setBlockState(pos, state, 3);
            }
        }
        else
        {
            if (flag)
            {
                worldIn.setBlockState(blockpos, state, 3);
            }
            else
            {
                worldIn.setBlockState(blockpos1, state, 3);
            }

            worldIn.setBlockState(pos, state, 3);
        }

        if (stack.hasDisplayName())
        {
            TileEntity tileentity = worldIn.getTileEntity(pos);

            if (tileentity instanceof TileEntityChest)
            {
                ((TileEntityChest)tileentity).setCustomName(stack.getDisplayName());
            }
        }
    }

    @Unique
    private static float gregtech$normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        if (normalized < 0) {
            normalized += 360.0F;
        }
        return normalized;
    }

    @Unique
    private static EnumFacing gregtech$calculateFacingFromYaw(float yaw) {
        float offsetYaw = (yaw + 45.0F) % 360.0F;

        if (offsetYaw < 90.0F) {
            return EnumFacing.SOUTH;
        } else if (offsetYaw < 180.0F) {
            return EnumFacing.WEST;
        } else if (offsetYaw < 270.0F) {
            return EnumFacing.NORTH;
        } else {
            return EnumFacing.EAST;
        }
    }
}
