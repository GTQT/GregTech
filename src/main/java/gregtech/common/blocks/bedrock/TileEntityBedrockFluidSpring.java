package gregtech.common.blocks.bedrock;

import gregtech.api.metatileentity.TickableTileEntityBase;
import gregtech.api.worldgen.bedrockFluids.BedrockFluidVeinHandler;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;

import org.jetbrains.annotations.NotNull;

/**
 * 基岩流体泉的逻辑 TE：每 25~40 秒（500~800 tick）尝试在自身正上方生成一格所属矿脉的实体流体。
 * <ul>
 * <li>上方被阻挡时不生成；</li>
 * <li>每次成功生成都会消耗所属矿脉的储量（depleteVein）；</li>
 * <li>矿脉枯竭（操作数归零）后泉停止喷射。</li>
 * </ul>
 */
public class TileEntityBedrockFluidSpring extends TickableTileEntityBase {

    /** 喷射间隔下限，对应 25 秒 */
    public static final int MIN_INTERVAL = 500;
    /** 喷射间隔上限，对应 40 秒 */
    public static final int MAX_INTERVAL = 800;

    private int ticksUntilEruption = MIN_INTERVAL + 200;
    private int timer = 0;

    @Override
    public void update() {
        super.update();
        if (world == null || world.isRemote) return;

        timer++;
        if (timer < ticksUntilEruption) return;
        timer = 0;
        ticksUntilEruption = MIN_INTERVAL + world.rand.nextInt(MAX_INTERVAL - MIN_INTERVAL + 1);

        erupt();
    }

    private void erupt() {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        // 联动枯竭：矿脉操作数归零后停喷
        if (BedrockFluidVeinHandler.getOperationsRemaining(world, chunkX, chunkZ) <= 0) return;

        Fluid fluid = BedrockFluidVeinHandler.getFluidInChunk(world, chunkX, chunkZ);
        if (fluid == null) return;

        BlockPos eruptPos = pos.up();
        // 上方被阻挡则不生成
        if (!world.isAirBlock(eruptPos)) return;

        world.setBlockState(eruptPos, fluid.getBlock().getDefaultState(), 3);

        // 每次生成实体流体算进基岩流体的损耗
        BedrockFluidVeinHandler.depleteVein(world, chunkX, chunkZ, 0, false);
    }

    // === ISyncedTileEntity implementation（无需客户端同步数据） ===

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {}

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {}

    @Override
    public void receiveCustomData(int discriminator, @NotNull PacketBuffer buf) {}

    @Override
    public void readFromNBT(@NotNull NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.timer = compound.getInteger("Timer");
        this.ticksUntilEruption = compound.getInteger("TicksUntilEruption");
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("Timer", timer);
        compound.setInteger("TicksUntilEruption", ticksUntilEruption);
        return compound;
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
}
