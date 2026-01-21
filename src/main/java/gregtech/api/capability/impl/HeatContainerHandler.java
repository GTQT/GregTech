package gregtech.api.capability.impl;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IHeatable;
import gregtech.api.metatileentity.MTETrait;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class HeatContainerHandler extends MTETrait implements IHeatable {

    protected long maxHeatCapacity;      // 最大热容量（HU）
    protected int maxTemperature;        // 最大工作温度（K）
    protected long heatStored;           // 当前存储的热量（HU）

    @Getter
    private long maxInputHeatFlow;       // 最大输入热流量（HU/tick）
    @Getter
    private long maxOutputHeatFlow;      // 最大输出热流量（HU/tick）

    @Setter
    private Predicate<EnumFacing> sideInputCondition;
    @Setter
    private Predicate<EnumFacing> sideOutputCondition;

    // 热量流量统计
    protected long lastHeatInputPerSec = 0;
    protected long lastHeatOutputPerSec = 0;
    protected long heatInputPerSec = 0;
    protected long heatOutputPerSec = 0;

    // 温度相关
    private int currentTemperature = 293; // 当前温度（K），默认室温

    // 热流限制
    protected long inputHeatFlowThisTick = 0;
    protected long outputHeatFlowThisTick = 0;

    public HeatContainerHandler(MetaTileEntity tileEntity, long maxHeatCapacity, int maxTemperature,
                                long maxInputHeatFlow, long maxOutputHeatFlow) {
        super(tileEntity);
        this.maxHeatCapacity = maxHeatCapacity;
        this.maxTemperature = maxTemperature;
        this.maxInputHeatFlow = maxInputHeatFlow;
        this.maxOutputHeatFlow = maxOutputHeatFlow;
    }

    public static HeatContainerHandler emitterContainer(MetaTileEntity tileEntity, long maxHeatCapacity,
                                                        int maxTemperature, long maxOutputHeatFlow) {
        return new HeatContainerHandler(tileEntity, maxHeatCapacity, maxTemperature, 0L, maxOutputHeatFlow);
    }

    public static HeatContainerHandler receiverContainer(MetaTileEntity tileEntity, long maxHeatCapacity,
                                                         int maxTemperature, long maxInputHeatFlow) {
        return new HeatContainerHandler(tileEntity, maxHeatCapacity, maxTemperature, maxInputHeatFlow, 0L);
    }

    @Override
    public long getInputPerSec() {
        return lastHeatInputPerSec;
    }

    @Override
    public long getOutputPerSec() {
        return lastHeatOutputPerSec;
    }

    @NotNull
    @Override
    public String getName() {
        return "HeatContainerHandler";
    }

    @Override
    public <T> T getCapability(Capability<T> capability) {
        if (capability == GregtechCapabilities.CAPABILITY_HEAT_CONTAINER) {
            return GregtechCapabilities.CAPABILITY_HEAT_CONTAINER.cast(this);
        }
        return null;
    }

    @NotNull
    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setLong("HeatStored", heatStored);
        compound.setInteger("Temperature", currentTemperature);
        compound.setInteger("MaxTemperature", maxTemperature);
        compound.setLong("MaxHeatCapacity", maxHeatCapacity);
        return compound;
    }

    @Override
    public void deserializeNBT(@NotNull NBTTagCompound compound) {
        this.heatStored = compound.getLong("HeatStored");
        this.currentTemperature = compound.getInteger("Temperature");
        this.maxTemperature = compound.getInteger("MaxTemperature");
        this.maxHeatCapacity = compound.getLong("MaxHeatCapacity");
    }

    @Override
    public long getHeatStored() {
        return this.heatStored;
    }

    public void setHeatStored(long heatStored) {
        long oldHeatStored = this.heatStored;
        this.heatStored = Math.max(0, Math.min(heatStored, maxHeatCapacity));

        // 更新热量流量统计
        if (this.heatStored > oldHeatStored) {
            heatInputPerSec += this.heatStored - oldHeatStored;
        } else {
            heatOutputPerSec += oldHeatStored - this.heatStored;
        }
    }

    @Override
    public void update() {
        // 重置每tick的热流计数器
        inputHeatFlowThisTick = 0;
        outputHeatFlowThisTick = 0;

        if (metaTileEntity.getWorld().isRemote) return;

        // 更新每秒流量统计
        if (metaTileEntity.getOffsetTimer() % 20 == 0) {
            lastHeatInputPerSec = heatInputPerSec;
            lastHeatOutputPerSec = heatOutputPerSec;
            heatInputPerSec = 0;
            heatOutputPerSec = 0;
        }

        // 如果存储了热量并且可以输出，尝试向周围输出
        if (getHeatStored() > 0 && getMaxOutputHeatFlow() > 0) {
            long outputHeat = Math.min(getHeatStored(), getMaxOutputHeatFlow() - outputHeatFlowThisTick);
            if (outputHeat > 0) {
                long outputUsed = 0;

                for (EnumFacing side : EnumFacing.VALUES) {
                    if (!canOutputHeat(side)) continue;

                    TileEntity tileEntity = metaTileEntity.getNeighbor(side);
                    EnumFacing oppositeSide = side.getOpposite();

                    if (tileEntity != null && tileEntity.hasCapability(
                            GregtechCapabilities.CAPABILITY_HEAT_CONTAINER, oppositeSide)) {

                        IHeatable heatable = tileEntity.getCapability(
                                GregtechCapabilities.CAPABILITY_HEAT_CONTAINER, oppositeSide);

                        if (heatable == null || !heatable.canAcceptHeat()) continue;

                        // 输出热量，同时传递当前温度
                        long heatToTransfer = Math.min(outputHeat - outputUsed, getMaxOutputHeatFlow());
                        long heatAccepted = heatable.transferHeat(heatToTransfer, getTemperature());

                        if (heatAccepted > 0) {
                            outputUsed += heatAccepted;
                            outputHeatFlowThisTick += heatAccepted;

                            // 从存储中减去输出的热量
                            setHeatStored(getHeatStored() - heatAccepted);

                            if (outputUsed >= outputHeat) break;
                        }
                    }
                }
            }
        }
    }

    @Override
    public long transferHeat(long heatToTransfer, int sourceTemperature) {
        // 检查是否可以接受热量
        if (heatToTransfer <= 0 || !canAcceptHeat()) return 0;

        // 检查输入热流限制
        if (inputHeatFlowThisTick >= getMaxInputHeatFlow()) return 0;

        // 计算可接受的热量
        long availableSpace = maxHeatCapacity - heatStored;
        long availableInput = getMaxInputHeatFlow() - inputHeatFlowThisTick;

        long heatToAccept = Math.min(Math.min(heatToTransfer, availableSpace), availableInput);

        if (heatToAccept > 0) {
            // 接受热量
            setHeatStored(heatStored + heatToAccept);
            inputHeatFlowThisTick += heatToAccept;

            // 同步温度（使用热源温度，或取平均值）
            if (sourceTemperature > getTemperature()) {
                // 热力学第二定律：热量从高温传到低温
                // 这里简化处理：直接使用热源温度
                setTemperature(sourceTemperature);
            } else if (heatStored > 0) {
                // 如果热源温度较低，使用加权平均
                long totalHeat = getHeatStored() * getTemperature() + heatToAccept * sourceTemperature;
                int avgTemp = (int)(totalHeat / (getHeatStored() + heatToAccept));
                setTemperature(avgTemp);
            }

            return heatToAccept;
        }

        return 0;
    }

    @Override
    public long getHeatCapacity() {
        return this.maxHeatCapacity;
    }

    @Override
    public boolean canAcceptHeat() {
        return heatStored < maxHeatCapacity && currentTemperature <= maxTemperature;
    }

    public boolean canAcceptHeat(EnumFacing side) {
        return canAcceptHeat() && (sideInputCondition == null || sideInputCondition.test(side));
    }

    @Override
    public boolean canOutputHeat() {
        return getMaxOutputHeatFlow() > 0 && heatStored > 0;
    }

    public boolean canOutputHeat(EnumFacing side) {
        return canOutputHeat() && (sideOutputCondition == null || sideOutputCondition.test(side));
    }

    @Override
    public long changeHeat(long heatToAdd) {
        long oldHeatStored = getHeatStored();
        long newHeatStored = Math.max(0, Math.min(maxHeatCapacity, oldHeatStored + heatToAdd));
        setHeatStored(newHeatStored);
        return newHeatStored - oldHeatStored;
    }

    @Override
    public int getTemperature() {
        return this.currentTemperature;
    }

    @Override
    public void setTemperature(int temperature) {
        // 安全地设置温度
        if (metaTileEntity == null || metaTileEntity.getWorld() == null) {
            // 在初始化阶段，直接设置温度
            this.currentTemperature = Math.max(293, Math.min(temperature, maxTemperature));
            return;
        }

        // 检查温度是否超过最大温度
        if (temperature > maxTemperature) {
            handleOverheat(temperature);
            return;
        }

        if (this.currentTemperature != temperature) {
            this.currentTemperature = temperature;

            // 标记数据已更改
            if (!metaTileEntity.getWorld().isRemote) {
                metaTileEntity.markDirty();
            }
        }
    }

    @Override
    public int getMaxTemperature() {
        return this.maxTemperature;
    }

    @Override
    public void setMaxTemperature(int maxTemperature) {
        this.maxTemperature = maxTemperature;

        // 如果当前温度超过新的最大温度，触发过热
        if (currentTemperature > maxTemperature) {
            handleOverheat(currentTemperature);
        }
    }

    protected void handleOverheat(int temperature) {
        // 触发过热事件
        if (temperature >= maxTemperature * 1.2) { // 超过20%安全余量
            metaTileEntity.doExplosion(GTUtility.getExplosionPower(
                    (int)((temperature - maxTemperature) / 100.0f)
            ));
        } else {
            // 只是警告，不爆炸
            // 可以在这里添加粒子效果、声音等
        }
    }

    public long getHeatCanBeInserted() {
        return maxHeatCapacity - heatStored;
    }

    public long getHeatCanBeExtracted() {
        return heatStored;
    }

    @Override
    public String toString() {
        return "HeatContainerHandler{" +
                "maxHeatCapacity=" + maxHeatCapacity +
                ", heatStored=" + heatStored +
                ", currentTemperature=" + currentTemperature +
                ", maxTemperature=" + maxTemperature +
                ", maxInputHeatFlow=" + maxInputHeatFlow +
                ", maxOutputHeatFlow=" + maxOutputHeatFlow +
                '}';
    }
}
