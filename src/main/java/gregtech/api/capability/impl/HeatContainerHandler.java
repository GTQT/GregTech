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

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class HeatContainerHandler extends MTETrait implements IHeatable {

    protected final long maxHeatCapacity;      // 最大热容量（HU）
    protected final int maxTemperature;        // 最大工作温度（K）
    protected long heatStored;                 // 当前存储的热量（HU）

    private final long maxInputHeatFlow;       // 最大输入热流量（HU/tick）
    private final long maxOutputHeatFlow;      // 最大输出热流量（HU/tick）

    private Predicate<EnumFacing> sideInputCondition;
    private Predicate<EnumFacing> sideOutputCondition;

    // 热量流量统计
    protected long lastHeatInputPerSec = 0;
    protected long lastHeatOutputPerSec = 0;
    protected long heatInputPerSec = 0;
    protected long heatOutputPerSec = 0;

    // 温度相关
    private int currentTemperature = 293;      // 当前温度（K），默认室温
    private int baseTemperature = 293;         // 基础温度（环境温度，K）
    private long thermalConductivity = 1000;   // 热导率（HU/K·tick）

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

    public void setSideInputCondition(Predicate<EnumFacing> sideInputCondition) {
        this.sideInputCondition = sideInputCondition;
    }

    public void setSideOutputCondition(Predicate<EnumFacing> sideOutputCondition) {
        this.sideOutputCondition = sideOutputCondition;
    }

    public void setBaseTemperature(int baseTemperature) {
        this.baseTemperature = baseTemperature;
    }

    public void setThermalConductivity(long thermalConductivity) {
        this.thermalConductivity = thermalConductivity;
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
        return compound;
    }

    @Override
    public void deserializeNBT(@NotNull NBTTagCompound compound) {
        this.heatStored = compound.getLong("HeatStored");
        this.currentTemperature = compound.getInteger("Temperature");
        notifyHeatListener(true);
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

        // 根据存储的热量重新计算温度
        updateTemperatureFromHeat();

        if (!metaTileEntity.getWorld().isRemote) {
            metaTileEntity.markDirty();
            notifyHeatListener(false);
        }
    }

    protected void updateTemperatureFromHeat() {
        if (thermalConductivity > 0) {
            // 温度 = 基础温度 + 存储热量 / 热导率
            int calculatedTemp = baseTemperature + (int)(heatStored / thermalConductivity);
            this.currentTemperature = Math.min(calculatedTemp, maxTemperature);

            // 如果计算温度超过最大温度，触发过热保护
            if (calculatedTemp > maxTemperature) {
                handleOverheat(calculatedTemp);
            }
        }
    }

    protected void handleOverheat(int calculatedTemp) {
        // 触发过热事件
        if (metaTileEntity instanceof IHeatChangeListener) {
            ((IHeatChangeListener) metaTileEntity).onOverheat(calculatedTemp, maxTemperature);
        }

        // 根据配置决定是否爆炸
        if (calculatedTemp >= maxTemperature * 1.2) { // 超过20%安全余量
            metaTileEntity.doExplosion(GTUtility.getExplosionPower(
                    (int)((calculatedTemp - maxTemperature) / 100.0f)
            ));
        }
    }

    protected void notifyHeatListener(boolean isInitialChange) {
        if (metaTileEntity instanceof IHeatChangeListener) {
            ((IHeatChangeListener) metaTileEntity).onHeatChanged(this, isInitialChange);
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

                        // 输出热量，考虑温度差
                        long heatToTransfer = calculateHeatTransferForOutput(outputHeat - outputUsed);
                        long heatAccepted = heatable.transferHeat(heatToTransfer);

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

    protected long calculateHeatTransferForOutput(long availableHeat) {
        // 考虑温度差的热量传输
        // 温度差越大，热传导越快
        int ourTemp = getTemperature();

        // 简化计算：最大输出热流量的80%作为基础，然后根据温度差调整
        long baseTransfer = (long)(getMaxOutputHeatFlow() * 0.8);

        // 如果我们温度很高，可以提高传输速率
        float tempFactor = Math.min(1.0f, (ourTemp - baseTemperature) / (float)(maxTemperature - baseTemperature));

        return Math.min(availableHeat, (long)(baseTransfer * (1.0f + tempFactor)));
    }

    @Override
    public long transferHeat(long heatToTransfer) {
        // 检查是否可以接受热量
        if (heatToTransfer <= 0 || !canAcceptHeatInternal()) return 0;

        // 检查输入热流限制
        if (inputHeatFlowThisTick >= getMaxInputHeatFlow()) return 0;

        // 计算可接受的热量
        long availableSpace = maxHeatCapacity - heatStored;
        long availableInput = getMaxInputHeatFlow() - inputHeatFlowThisTick;

        long heatToAccept = Math.min(Math.min(heatToTransfer, availableSpace), availableInput);

        if (heatToAccept > 0) {
            setHeatStored(heatStored + heatToAccept);
            inputHeatFlowThisTick += heatToAccept;
            return heatToAccept;
        }

        return 0;
    }

    protected boolean canAcceptHeatInternal() {
        // 内部检查：温度未超过上限
        return currentTemperature < maxTemperature;
    }

    @Override
    public long getHeatCapacity() {
        return this.maxHeatCapacity;
    }

    @Override
    public boolean canAcceptHeat() {
        // 外部接口：只检查是否有空间
        return heatStored < maxHeatCapacity && currentTemperature < maxTemperature;
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

    public void setTemperature(int temperature) {
        this.currentTemperature = Math.max(baseTemperature, Math.min(temperature, maxTemperature));

        // 根据温度更新存储的热量
        if (thermalConductivity > 0) {
            long calculatedHeat = (long)((currentTemperature - baseTemperature) * thermalConductivity);
            this.heatStored = Math.max(0, Math.min(calculatedHeat, maxHeatCapacity));
        }
    }

    @Override
    public int getMaxTemperature() {
        return this.maxTemperature;
    }

    public long getMaxInputHeatFlow() {
        return this.maxInputHeatFlow;
    }

    public long getMaxOutputHeatFlow() {
        return this.maxOutputHeatFlow;
    }

    public long getHeatCanBeInserted() {
        return maxHeatCapacity - heatStored;
    }

    public long getHeatCanBeExtracted() {
        return heatStored;
    }

    public interface IHeatChangeListener {
        void onHeatChanged(IHeatable container, boolean isInitialChange);
        default void onOverheat(int currentTemp, int maxTemp) {}
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
                ", thermalConductivity=" + thermalConductivity +
                '}';
    }
}
