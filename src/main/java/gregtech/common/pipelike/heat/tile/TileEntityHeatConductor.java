package gregtech.common.pipelike.heat.tile;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IHeatable;
import gregtech.api.metatileentity.IDataInfoProvider;
import gregtech.api.pipenet.block.material.TileEntityMaterialPipeBase;
import gregtech.api.unification.material.properties.HeatConductorProperties;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.pipelike.heat.HeatConductorType;
import gregtech.common.pipelike.heat.net.HeatNet;
import gregtech.common.pipelike.heat.net.HeatNetHandler;
import gregtech.common.pipelike.heat.net.WorldHNet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static gregtech.api.capability.GregtechDataCodes.CONDUCTOR_TEMPERATURE;

/**
 * 热导管道。
 * <p>
 * 管道不存储热量，温度完全由 {@link HeatNet} 广播决定（全网共享一个温度）。
 * 因此这里<b>不需要</b>给自己挂 {@code TaskScheduler} 任务：网络的过期与广播由
 * {@link HeatNet} 用"整个网络一个任务"的方式驱动。
 * <p>
 * 超温规则与 {@code HeatContainerHandler} 保持一致：超过材料上限的 20% 以内只是封顶
 * （烧红但撑得住），超过才炸管。
 */
public class TileEntityHeatConductor extends TileEntityMaterialPipeBase<HeatConductorType, HeatConductorProperties>
        implements IDataInfoProvider {

    /** 超过材料上限的这个倍数就直接炸管。 */
    private static final float OVERHEAT_EXPLODE_FACTOR = 1.2f;

    private final EnumMap<EnumFacing, HeatNetHandler> handlers = new EnumMap<>(EnumFacing.class);
    private final IHeatable clientCapability = IHeatable.DEFAULT;
    private HeatNetHandler defaultHandler;
    private WeakReference<HeatNet> currentHeatNet = new WeakReference<>(null);
    private int temperature = HeatNet.AMBIENT_TEMPERATURE;

    @Override
    public Class<HeatConductorType> getPipeTypeClass() {
        return HeatConductorType.class;
    }

    @Override
    public boolean supportsTicking() {
        return false;
    }

    private void initHandlers() {
        HeatNet net = getHeatNet();
        if (net == null) {
            return;
        }
        for (EnumFacing facing : EnumFacing.VALUES) {
            handlers.put(facing, new HeatNetHandler(net, this, facing));
        }
        defaultHandler = new HeatNetHandler(net, this, null);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!world.isRemote) {
            // 区块重新加载时把网络当前温度捡回来
            syncNetworkTemperature();
        }
    }

    /**
     * 把管道温度对齐到网络温度
     */
    private void syncNetworkTemperature() {
        HeatNet net = getHeatNet();
        if (net != null) {
            setTemperature(net.getNetworkTemperature());
        }
    }

    public int getDefaultTemp() {
        return HeatNet.AMBIENT_TEMPERATURE;
    }

    public int getTemperature() {
        return temperature;
    }

    // ===== 吞吐额度 =====
    // 热传导率是"这根管子每 tick 能过多少热"，是管子整体的属性，
    // 不能因为接了 6 个面就变成 6 倍。所以额度记在 TileEntity 上，同一个 tick 内所有面共用。

    private long transferBudgetUsed;
    private long transferBudgetTick = -1L;

    /**
     * 本 tick 这根管子还能通过多少热量（HU）。额度在每个世界 tick 开始时重置。
     */
    public long getRemainingTransferBudget() {
        World world = getWorld();
        if (world == null) return 0L;
        long now = world.getTotalWorldTime();
        if (now != transferBudgetTick) {
            transferBudgetTick = now;
            transferBudgetUsed = 0L;
        }
        return Math.max(0L, getNodeData().getHeatTransfer() - transferBudgetUsed);
    }

    /** 记下本 tick 实际通过的热量。 */
    public void consumeTransferBudget(long amount) {
        if (amount > 0) {
            transferBudgetUsed += amount;
        }
    }

    /**
     * 设置管道温度（由网络广播调用）。
     * <p>
     * 这是管道唯一的"温度入口"，超温判定也放在这里 —— 早期版本把它放在一个
     * 常驻的 {@code TaskScheduler} 任务里，那个任务在温度介于室温和 0.8 倍上限之间时
     * 永远返回 true（永不结束），并且强引用 TileEntity，区块卸载后既泄漏内存又重复注册。
     */
    public void setTemperature(int temperature) {
        int ambient = getDefaultTemp();
        if (temperature < ambient) {
            temperature = ambient;
        }

        int maxTemperature = getNodeData().getMaxTemperature();
        if (temperature > maxTemperature) {
            if (temperature >= maxTemperature * OVERHEAT_EXPLODE_FACTOR) {
                overheat();
                return;
            }
            // 20% 余量以内只封顶
            temperature = maxTemperature;
        }

        if (this.temperature == temperature) return;
        this.temperature = temperature;
        final int syncedTemperature = temperature;

        World world = getWorld();
        if (world == null) return;

        world.checkLight(pos);
        if (!world.isRemote) {
            writeCustomData(CONDUCTOR_TEMPERATURE, buf -> buf.writeVarInt(syncedTemperature));
            markDirty();
        }
    }

    private void overheat() {
        World world = getWorld();
        if (world == null || world.isRemote) return;

        world.createExplosion(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2.0f, true);
        // 爆炸通常已经把管子拆掉了，这里兜底
        if (!isInvalid()) {
            world.setBlockToAir(pos);
        }
    }

    @Nullable
    @Override
    public <T> T getCapabilityInternal(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == GregtechCapabilities.CAPABILITY_HEAT_CONTAINER) {
            if (world.isRemote)
                return GregtechCapabilities.CAPABILITY_HEAT_CONTAINER.cast(clientCapability);
            if (handlers.isEmpty())
                initHandlers();
            checkNetwork();
            return GregtechCapabilities.CAPABILITY_HEAT_CONTAINER.cast(handlers.getOrDefault(facing, defaultHandler));
        }
        return super.getCapabilityInternal(capability, facing);
    }

    public void checkNetwork() {
        if (defaultHandler != null) {
            HeatNet current = getHeatNet();
            if (defaultHandler.getNet() != current) {
                defaultHandler.updateNetwork(current);
                for (HeatNetHandler handler : handlers.values()) {
                    handler.updateNetwork(current);
                }
                // 网络变化时同步温度
                syncNetworkTemperature();
            }
        }
    }

    private HeatNet getHeatNet() {
        if (world == null || world.isRemote)
            return null;
        HeatNet currentHeatNet = this.currentHeatNet.get();
        if (currentHeatNet != null && currentHeatNet.isValid() &&
                currentHeatNet.containsNode(getPos()))
            return currentHeatNet;
        WorldHNet worldHNet = WorldHNet.getWorldHNet(getWorld());
        currentHeatNet = worldHNet.getNetFromPos(getPos());
        if (currentHeatNet != null) {
            this.currentHeatNet = new WeakReference<>(currentHeatNet);
        }
        return currentHeatNet;
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        this.handlers.clear();
        this.currentHeatNet.clear();
    }

    @Override
    public int getDefaultPaintingColor() {
        return 0x8B0000; // 深红色
    }

    @Override
    public void receiveCustomData(int discriminator, PacketBuffer buf) {
        if (discriminator == CONDUCTOR_TEMPERATURE) {
            int newTemp = buf.readVarInt();
            if (this.temperature != newTemp) {
                this.temperature = newTemp;
                if (world != null) {
                    world.checkLight(pos);
                }
            }
        } else {
            super.receiveCustomData(discriminator, buf);
        }
    }

    @NotNull
    @Override
    public NBTTagCompound writeToNBT(@NotNull NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("Temp", temperature);
        return compound;
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound compound) {
        super.readFromNBT(compound);
        // 缺键时 getInteger 会给出 0，那不是环境温度
        temperature = compound.hasKey("Temp") ? compound.getInteger("Temp") : HeatNet.AMBIENT_TEMPERATURE;
    }

    @NotNull
    @Override
    public List<ITextComponent> getDataInfo() {
        List<ITextComponent> list = new ArrayList<>();
        list.add(new TextComponentTranslation("behavior.tricorder.temperature",
                new TextComponentTranslation(TextFormattingUtil.formatNumbers(this.getTemperature()) + "K")
                        .setStyle(new Style().setColor(TextFormatting.RED))));
        list.add(new TextComponentTranslation("behavior.tricorder.max_temperature",
                new TextComponentTranslation(
                        TextFormattingUtil.formatNumbers(this.getNodeData().getMaxTemperature()) + "K")
                        .setStyle(new Style().setColor(TextFormatting.YELLOW))));
        return list;
    }
}
