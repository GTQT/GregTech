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
    private HeatNetHandler defaultHandler;
    private final IHeatable clientCapability = IHeatable.DEFAULT;
    private WeakReference<HeatNet> currentHeatNet = new WeakReference<>(null);
    private int temperature = getDefaultTemp();
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
            setTemperature(temperature);
            if (temperature > getDefaultTemp()) {
                TaskScheduler.scheduleTask(world, this::update);
            }
        }
    }

    public void applyHeat(int amount) {
        if (world.isRemote) return;

        int newTemp = temperature + amount;
        if (newTemp > getNodeData().getMaxTemperature()) {
            // 超过最大温度，管道损坏
            world.createExplosion(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    2.0f, true);
            world.setBlockToAir(pos);
            return;
        }

        setTemperature(newTemp);

        if (!isTicking && newTemp > getDefaultTemp()) {
            TaskScheduler.scheduleTask(world, this::update);
            isTicking = true;
        }
    }

    private boolean update() {
        if (temperature <= getDefaultTemp()) {
            isTicking = false;
            return false;
        }

        // 自然冷却
        int cooling = (int) Math.sqrt(temperature - getDefaultTemp());
        setTemperature(temperature - cooling);

        if (temperature <= getDefaultTemp()) {
            isTicking = false;
            return false;
        }

        return true;
    }

    public void setTemperature(int temperature) {
        this.temperature = temperature;
        world.checkLight(pos);
        if (!world.isRemote) {
            writeCustomData(CONDUCTOR_TEMPERATURE, buf -> buf.writeVarInt(temperature));
        }
    }

    public int getDefaultTemp() {
        return 293; // 室温20°C
    }

    public int getTemperature() {
        return temperature;
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
            setTemperature(buf.readVarInt());
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
                new TextComponentTranslation(TextFormattingUtil.formatNumbers(this.getNodeData().getMaxTemperature()) + "K")
                        .setStyle(new Style().setColor(TextFormatting.YELLOW))));
        return list;
    }
}
