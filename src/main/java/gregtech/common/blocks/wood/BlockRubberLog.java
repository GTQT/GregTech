package gregtech.common.blocks.wood;

import gregtech.api.items.toolitem.ToolClasses;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.creativetab.GTCreativeTabs;
import gregtech.common.items.MetaItems;

import net.minecraft.block.BlockLog;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class BlockRubberLog extends BlockLog {

    public static final PropertyEnum<RubberWoodState> STATE = PropertyEnum.create("state", RubberWoodState.class);

    public BlockRubberLog() {
        this.setDefaultState(this.blockState.getBaseState()
                .withProperty(STATE, RubberWoodState.PLAIN_Y));
        setTranslationKey("rubber_log");
        setCreativeTab(GTCreativeTabs.TAB_GREGTECH);
        setHarvestLevel(ToolClasses.AXE, 0);
        setTickRandomly(true);
    }

    @NotNull
    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, STATE);
    }

    @NotNull
    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(STATE, RubberWoodState.values()[meta]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(STATE).ordinal();
    }

    // 正确处理轴方向
    @Override
    public @NotNull IBlockState getStateForPlacement(@NotNull World world, @NotNull BlockPos pos, EnumFacing facing,
                                                     float hitX, float hitY, float hitZ,
                                                     int meta, @NotNull EntityLivingBase placer, @NotNull EnumHand hand) {
        EnumAxis axis = EnumAxis.fromFacingAxis(facing.getAxis());

        return switch (axis) {
            case X -> getDefaultState().withProperty(STATE, RubberWoodState.PLAIN_X);
            case Z -> getDefaultState().withProperty(STATE, RubberWoodState.PLAIN_Z);
            default -> getDefaultState().withProperty(STATE, RubberWoodState.PLAIN_Y);
        };
    }


    @Override
    public void getDrops(@NotNull NonNullList<ItemStack> drops, @NotNull IBlockAccess world, @NotNull BlockPos pos,
                         IBlockState state, int fortune) {
        Random rand = world instanceof World ? ((World) world).rand : RANDOM;

        // 添加橡胶木本身
        drops.add(new ItemStack(this));

        // 湿状态的橡胶木有几率掉落树脂
        RubberWoodState woodState = state.getValue(STATE);
        if (woodState.wet && rand.nextInt(6) == 0) {
            drops.add(MetaItems.STICKY_RESIN.getStackForm());
        }
    }

    // 木龙头交互功能
    @Override
    public boolean onBlockActivated(@NotNull World world, @NotNull BlockPos pos, IBlockState state,
                                    EntityPlayer player, @NotNull EnumHand hand, @NotNull EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {

        ItemStack heldItem = player.getHeldItem(hand);
        RubberWoodState woodState = state.getValue(STATE);

        // Shift+右键：放置 TE 木龙头
        if (player.isSneaking() && isTreeTap(heldItem)) {
            if (!world.isRemote) {
                BlockPos tapPos = pos.offset(facing);
                IBlockState tapTargetState = world.getBlockState(tapPos);

                // 确保目标位置可放置
                if (tapTargetState.getBlock().isReplaceable(world, tapPos)) {
                    // 放置木龙头方块，ATTACHED_FACING 朝向橡胶木
                    world.setBlockState(tapPos, MetaBlocks.TREE_TAP.getDefaultState()
                            .withProperty(BlockTreeTap.ATTACHED_FACING, facing.getOpposite()));

                    // 设置 TE
                    TileEntity te = world.getTileEntity(tapPos);
                    if (te instanceof TileEntityTreeTap) {
                        int durability = heldItem.getMaxDamage() - heldItem.getItemDamage();
                        ((TileEntityTreeTap) te).setDurability(durability);
                        ((TileEntityTreeTap) te).setItemStack(heldItem.copy());
                    }

                    // 消耗玩家手中的木龙头
                    if (!player.isCreative()) {
                        heldItem.shrink(1);
                    }
                }
            }
            return true;
        }

        // 检查是否使用木龙头且树干是湿状态（有树脂）
        if (isTreeTap(heldItem) && woodState.wet) {
            if (!world.isRemote) {
                // 掉落粘性树脂
                spawnAsEntity(world, pos, MetaItems.STICKY_RESIN.getStackForm(1 + world.rand.nextInt(2)));

                // 转换为干状态
                world.setBlockState(pos, state.withProperty(STATE, woodState.getDry()));

                // 消耗木龙头耐久
                if (!player.isCreative()) {
                    heldItem.damageItem(1, player);
                }
            }
            return true;
        }

        return false;
    }

    // 树脂再生逻辑
    @Override
    public void randomTick(@NotNull World world, @NotNull BlockPos pos, @NotNull IBlockState state, Random random) {
        if (random.nextInt(7) == 0) {
            RubberWoodState woodState = state.getValue(STATE);

            // 只有干状态（可再生状态）才能变为湿状态
            if (woodState.canRegenerate()) {
                world.setBlockState(pos, state.withProperty(STATE, woodState.getWet()));
            }
        }
    }

    // 检查是否为木龙头工具
    private boolean isTreeTap(ItemStack stack) {
        return stack.getItem().getToolClasses(stack).contains(ToolClasses.TREE_TAP);
    }

    // 橡胶木状态枚举
    public enum RubberWoodState implements IStringSerializable {
        // 普通状态（无树脂）
        PLAIN_Y(EnumFacing.Axis.Y, null, false),
        PLAIN_X(EnumFacing.Axis.X, null, false),
        PLAIN_Z(EnumFacing.Axis.Z, null, false),

        // 干状态（无树脂，可再生）
        DRY_NORTH(EnumFacing.Axis.Y, EnumFacing.NORTH, false),
        DRY_SOUTH(EnumFacing.Axis.Y, EnumFacing.SOUTH, false),
        DRY_WEST(EnumFacing.Axis.Y, EnumFacing.WEST, false),
        DRY_EAST(EnumFacing.Axis.Y, EnumFacing.EAST, false),

        // 湿状态（有树脂）
        WET_NORTH(EnumFacing.Axis.Y, EnumFacing.NORTH, true),
        WET_SOUTH(EnumFacing.Axis.Y, EnumFacing.SOUTH, true),
        WET_WEST(EnumFacing.Axis.Y, EnumFacing.WEST, true),
        WET_EAST(EnumFacing.Axis.Y, EnumFacing.EAST, true);

        public final EnumFacing.Axis axis;
        public final EnumFacing facing;
        public final boolean wet;
        private static final RubberWoodState[] VALUES = values();

        RubberWoodState(EnumFacing.Axis axis, EnumFacing facing, boolean wet) {
            this.axis = axis;
            this.facing = facing;
            this.wet = wet;
        }

        @Override
        public @NotNull String getName() {
            return this.name().toLowerCase();
        }

        public boolean isPlain() {
            return this.facing == null;
        }

        public boolean canRegenerate() {
            return !this.isPlain() && !this.wet;
        }

        public RubberWoodState getWet() {
            if (this.isPlain()) {
                return this;
            }
            return VALUES[this.ordinal() + 4];
        }

        public RubberWoodState getDry() {
            if (this.isPlain()) {
                return this;
            }
            if (this.wet) {
                return VALUES[this.ordinal() - 4];
            }
            return this;
        }

        public static RubberWoodState getPlainState(EnumFacing.Axis axis) {
            return switch (axis) {
                case X -> PLAIN_X;
                case Y -> PLAIN_Y;
                case Z -> PLAIN_Z;
                default -> PLAIN_Y;
            };
        }

        public static RubberWoodState getWetState(EnumFacing facing) {
            return switch (facing) {
                case NORTH -> WET_NORTH;
                case SOUTH -> WET_SOUTH;
                case WEST -> WET_WEST;
                case EAST -> WET_EAST;
                default -> WET_NORTH;
            };
        }
    }

    // 支持树叶衰减
    @Override
    public void breakBlock(@NotNull World world, @NotNull BlockPos pos, @NotNull IBlockState state) {
        int range = 4;

        for (int y = -range; y <= range; ++y) {
            for (int z = -range; z <= range; ++z) {
                for (int x = -range; x <= range; ++x) {
                    BlockPos checkPos = pos.add(x, y, z);
                    IBlockState checkState = world.getBlockState(checkPos);

                    if (checkState.getBlock().isLeaves(checkState, world, checkPos)) {
                        checkState.getBlock().beginLeavesDecay(checkState, world, checkPos);
                    }
                }
            }
        }

        super.breakBlock(world, pos, state);
    }

    // 木材特性
    @Override
    public boolean canSustainLeaves(@NotNull IBlockState state, @NotNull IBlockAccess world, @NotNull BlockPos pos) {
        return true;
    }

}
