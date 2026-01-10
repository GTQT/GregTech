package gregtech.common.pipelike.heat.tile;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IHeatable;
import gregtech.api.metatileentity.IDataInfoProvider;
import gregtech.api.pipenet.block.material.TileEntityMaterialPipeBase;
import gregtech.api.unification.material.properties.HeatConductorProperties;
import gregtech.api.util.TaskScheduler;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.pipelike.cable.tile.AveragingPerTickCounter;
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
import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static gregtech.api.capability.GregtechDataCodes.CONDUCTOR_TEMPERATURE;

public class TileEntityHeatConductor extends TileEntityMaterialPipeBase<HeatConductorType, HeatConductorProperties>
        implements IDataInfoProvider {

    private final EnumMap<EnumFacing, HeatNetHandler> handlers = new EnumMap<>(EnumFacing.class);
    private final AveragingPerTickCounter averageHeatCounter = new AveragingPerTickCounter();
    private final IHeatable clientCapability = IHeatable.DEFAULT;
    private HeatNetHandler defaultHandler;
    private WeakReference<HeatNet> currentHeatNet = new WeakReference<>(null);
    private int temperature = 293; // 默认室温，将被网络温度覆盖
    private boolean isTicking = false;

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
            // 连接到网络时，立即同步网络温度
            syncNetworkTemperature();
            if (temperature > getDefaultTemp()) {
                TaskScheduler.scheduleTask(world, this::update);
            }
        }
    }

    /**
     * 同步网络温度
     */
    private void syncNetworkTemperature() {
        HeatNet net = getHeatNet();
        if (net != null) {
            int networkTemp = net.getNetworkTemperature();
            if (networkTemp != temperature) {
                setTemperature(networkTemp);
            }
        }
    }

    /**
     * 不再通过热量计算温度，只用于外部强制设置（如热源连接时）
     */
    public void applyHeat(int amount) {
        // 管道本身不通过热量改变温度，温度由网络决定
        // 这个方法现在只用于标记管道接收到了热量（用于统计等）
        if (world.isRemote) return;

        // 可以记录热量流量，但不改变温度
        averageHeatCounter.increment(getWorld(), amount);
    }

    private boolean update() {
        // 现在只检查温度是否过高（超过管道材料限制）
        if (temperature > getNodeData().getMaxTemperature()) {
            // 超过最大温度，管道损坏
            world.createExplosion(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    2.0f, true);
            world.setBlockToAir(pos);
            return false;
        }

        // 检查是否需要进行自然冷却（如果温度高于网络温度）
        HeatNet net = getHeatNet();
        if (net != null && temperature > net.getNetworkTemperature()) {
            // 缓慢冷却到网络温度
            int cooling = Math.min(temperature - net.getNetworkTemperature(), 10);
            setTemperature(temperature - cooling);
        }

        if (temperature <= getDefaultTemp()) {
            isTicking = false;
            return false;
        }

        return true;
    }

    public int getDefaultTemp() {
        return 293; // 室温20°C
    }

    public int getTemperature() {
        return temperature;
    }

    /**
     * 设置管道温度（主要由网络调用）
     */
    public void setTemperature(int temperature) {
        if (this.temperature == temperature) return;

        this.temperature = temperature;
        world.checkLight(pos);

        // 记录温度变化（用于客户端渲染）
        if (!world.isRemote) {
            writeCustomData(CONDUCTOR_TEMPERATURE, buf -> buf.writeVarInt(temperature));
        }

        // 如果温度接近或超过最大温度，启动tick更新以检查是否损坏
        if (!isTicking && temperature > getNodeData().getMaxTemperature() * 0.8) {
            TaskScheduler.scheduleTask(world, this::update);
            isTicking = true;
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
                world.checkLight(pos);
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
        temperature = compound.getInteger("Temp");
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
