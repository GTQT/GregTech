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

    /** 环境温度（K）。任何温度都不会低于它。 */
    public static final int AMBIENT_TEMPERATURE = 293;
    /** 超过最大温度的这个倍数才真正爆炸，余量以内只封顶。 */
    private static final float OVERHEAT_EXPLODE_FACTOR = 1.2f;

    protected long maxHeatCapacity;      // 最大热容量（HU）
    protected int maxTemperature;        // 最大工作温度（K）
    protected long heatStored;           // 当前存储的热量（HU）

    private long maxInputHeatFlow;       // 最大输入热流量（HU/tick）
    private long maxOutputHeatFlow;      // 最大输出热流量（HU/tick）

    private Predicate<EnumFacing> sideInputCondition;
    private Predicate<EnumFacing> sideOutputCondition;

    // 热量流量统计
    protected long lastHeatInputPerSec = 0;
    protected long lastHeatOutputPerSec = 0;
    protected long heatInputPerSec = 0;
    protected long heatOutputPerSec = 0;

    // 温度相关
    private int currentTemperature = AMBIENT_TEMPERATURE; // 当前温度（K），默认室温

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

    public long getMaxInputHeatFlow() {
        return maxInputHeatFlow;
    }

    public long getMaxOutputHeatFlow() {
        return maxOutputHeatFlow;
    }

    public void setSideInputCondition(Predicate<EnumFacing> sideInputCondition) {
        this.sideInputCondition = sideInputCondition;
    }

    public void setSideOutputCondition(Predicate<EnumFacing> sideOutputCondition) {
        this.sideOutputCondition = sideOutputCondition;
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
            // 预算同时受"存量"和"本 tick 剩余输出额度"限制
            long budget = Math.min(getHeatStored(),
                    Math.max(0L, getMaxOutputHeatFlow() - outputHeatFlowThisTick));

            for (EnumFacing side : EnumFacing.VALUES) {
                if (budget <= 0) break;
                if (!canOutputHeat(side)) continue;

                TileEntity tileEntity = metaTileEntity.getNeighbor(side);
                EnumFacing oppositeSide = side.getOpposite();
                if (tileEntity == null) continue;

                IHeatable heatable = tileEntity.getCapability(
                        GregtechCapabilities.CAPABILITY_HEAT_CONTAINER, oppositeSide);
                if (heatable == null || !heatable.canAcceptHeat()) continue;

                long offered = Math.min(budget, getMaxOutputHeatFlow());
                if (offered <= 0) break;

                // 输出热量，同时传递当前温度
                long accepted = heatable.transferHeat(offered, getTemperature());
                if (accepted <= 0) continue;

                budget -= accepted;
                outputHeatFlowThisTick += accepted;

                // 从存储中减去输出的热量
                setHeatStored(getHeatStored() - accepted);
            }
        }
    }

    @Override
    public long transferHeat(long heatToTransfer, int sourceTemperature) {
        // 检查是否可以接受热量
        if (heatToTransfer <= 0 || !canAcceptHeat()) return 0;

        // 计算可接受的热量
        long availableSpace = maxHeatCapacity - heatStored;
        long availableInput = getMaxInputHeatFlow() - inputHeatFlowThisTick;

        long heatToAccept = Math.min(Math.min(heatToTransfer, availableSpace), availableInput);
        if (heatToAccept <= 0) return 0;

        // 热量守恒的混合温度：
        // 旧实现直接 setTemperature(sourceTemperature)，1 HU 的 2000K 就能把 1000K 上限的仓室炸掉，
        // 而且下面那个加权平均分支因为前面 return 了永远走不到。
        // 改成按"已有热量 x 已有温度 + 新增热量 x 热源温度"加权，温度随累积热量平滑逼近热源温度。
        long storedBefore = heatStored;
        int temperatureBefore = getTemperature();
        long newStored = storedBefore + heatToAccept;
        double mixed = (storedBefore * (double) temperatureBefore + heatToAccept * (double) sourceTemperature) /
                newStored;

        setHeatStored(newStored);
        inputHeatFlowThisTick += heatToAccept;
        setTemperature((int) Math.round(mixed));

        return heatToAccept;
    }

    @Override
    public long getHeatCapacity() {
        return this.maxHeatCapacity;
    }

    @Override
    public boolean canAcceptHeat() {
        // 只能进不能出的容器（如热输出仓）不应该报告"可以接受热量"
        return maxInputHeatFlow > 0 && heatStored < maxHeatCapacity && currentTemperature <= maxTemperature;
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
            this.currentTemperature = Math.max(AMBIENT_TEMPERATURE, Math.min(temperature, maxTemperature));
            return;
        }

        if (temperature < AMBIENT_TEMPERATURE) {
            temperature = AMBIENT_TEMPERATURE;
        }

        if (temperature > maxTemperature) {
            if (temperature >= maxTemperature * OVERHEAT_EXPLODE_FACTOR) {
                handleOverheat(temperature);
                return;
            }
            // 超过上限但在 20% 余量以内：封顶，烧红但不炸
            temperature = maxTemperature;
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
        // 只在真正超出安全余量时爆炸；余量以内的封顶由 setTemperature 处理。
        // 这里再判一次是因为 setMaxTemperature（下调上限）也会走到这里。
        if (temperature < maxTemperature * OVERHEAT_EXPLODE_FACTOR) {
            return;
        }
        metaTileEntity.doExplosion(GTUtility.getExplosionPower(
                (int) ((temperature - maxTemperature) / 100.0f)));
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
