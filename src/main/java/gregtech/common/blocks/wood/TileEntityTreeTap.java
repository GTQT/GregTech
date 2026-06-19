package gregtech.common.blocks.wood;

import gregtech.api.metatileentity.TickableTileEntityBase;
import gregtech.common.items.MetaItems;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

public class TileEntityTreeTap extends TickableTileEntityBase {

    private int durability = 0;
    private ItemStack itemStack;

    public TileEntityTreeTap() {}

    public void setDurability(int durability) {
        this.durability = durability;
        markDirty();
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
        markDirty();
    }

    public int getDurability() {
        return durability;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    @Override
    public void update() {
        super.update();

        if (world.isRemote) return;

        // 每 100 ticks（5 秒）检查一次，offset 用于分散负载
        if (getOffsetTimer() % 100 == 0) {

            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();

            if (!(block instanceof BlockTreeTap)) {
                return;
            }

            // 检查附着面方向的橡胶木
            EnumFacing attachedFacing = state.getValue(BlockTreeTap.ATTACHED_FACING);
            BlockPos logPos = pos.offset(attachedFacing);
            IBlockState logState = world.getBlockState(logPos);

            if (logState.getBlock() instanceof BlockRubberLog) {
                BlockRubberLog.RubberWoodState woodState = logState.getValue(BlockRubberLog.STATE);

                // 湿状态 = 有树脂可采集
                if (woodState.wet) {
                    // 掉落树脂（与木龙头交互相同逻辑：1~2 个）
                    Block.spawnAsEntity(world, pos,
                            MetaItems.STICKY_RESIN.getStackForm(1 + world.rand.nextInt(2)));

                    // 将橡胶木转为干状态
                    world.setBlockState(logPos,
                            logState.withProperty(BlockRubberLog.STATE, woodState.getDry()));

                    // 消耗耐久
                    durability--;
                    markDirty();

                    if (durability <= 0) {
                        world.setBlockToAir(pos);
                    }
                }
            }
        }
    }

    // === IHasWorldObjectAndCoords / IDirtyNotifiable bridge methods ===

    @Override
    public World world() {
        return getWorld();
    }

    @Override
    public BlockPos pos() {
        return getPos();
    }

    @Override
    public void notifyBlockUpdate() {
        if (world != null) {
            world.notifyNeighborsOfStateChange(pos, getBlockType(), true);
        }
    }

    @Override
    public void markAsDirty() {
        markDirty();
    }

    // === ISyncedTileEntity implementation ===

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        buf.writeVarInt(durability);
    }

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        this.durability = buf.readVarInt();
    }

    @Override
    public void receiveCustomData(int discriminator, @NotNull PacketBuffer buf) {
        // 无需自定义数据包
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.durability = compound.getInteger("Durability");
        if (compound.hasKey("ItemStack")) {
            this.itemStack = new ItemStack(compound.getCompoundTag("ItemStack"));
        }
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("Durability", durability);
        if (itemStack != null) {
            NBTTagCompound stackTag = new NBTTagCompound();
            itemStack.writeToNBT(stackTag);
            compound.setTag("ItemStack", stackTag);
        }
        return compound;
    }
}
