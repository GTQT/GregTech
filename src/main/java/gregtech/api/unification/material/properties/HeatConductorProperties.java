package gregtech.api.unification.material.properties;

import gregtech.api.unification.material.Material;

import java.util.Objects;

import static gregtech.api.unification.material.info.MaterialFlags.GENERATE_FOIL;

public class HeatConductorProperties implements IMaterialProperty {

    private int maxTemperature;           // 最大承受温度（开尔文）
    private int heatTransferRate;         // 热传导率（HU/tick，Heat Unit）
    private float heatLossPerBlock;       // 每格热损失系数（0.0-1.0）

    public HeatConductorProperties(int maxTemperature, int heatTransferRate, float heatLossPerBlock) {
        this.maxTemperature = maxTemperature;
        this.heatTransferRate = heatTransferRate;
        this.heatLossPerBlock = Math.max(0.0f, Math.min(100.0f, heatLossPerBlock)); // 限制在0-100之间
    }

    /**
     * 默认值构造函数
     */
    public HeatConductorProperties() {
        this(1200, 64, 0.1f);
    }

    /**
     * 获取最大承受温度
     *
     * @return 最大温度（开尔文）
     */
    public int getMaxTemperature() {
        return maxTemperature;
    }

    /**
     * 设置最大承受温度
     *
     * @param maxTemperature 新的最大温度
     * @return 当前实例，便于链式调用
     */
    public HeatConductorProperties setMaxTemperature(int maxTemperature) {
        this.maxTemperature = maxTemperature;
        return this;
    }

    /**
     * 获取热传导率
     *
     * @return 热传导率（HU/tick）
     */
    public int getHeatTransfer() {
        return heatTransferRate;
    }

    /**
     * 设置热传导率
     *
     * @param heatTransferRate 新的热传导率
     * @return 当前实例，便于链式调用
     */
    public HeatConductorProperties setHeatTransfer(int heatTransferRate) {
        this.heatTransferRate = heatTransferRate;
        return this;
    }

    /**
     * 获取每格热损失系数
     *
     * @return 热损失系数（0.0-100.0）
     */
    public float getHeatLossPerBlock() {
        return heatLossPerBlock;
    }

    /**
     * 设置每格热损失系数
     *
     * @param heatLossPerBlock 新的热损失系数
     * @return 当前实例，便于链式调用
     */
    public HeatConductorProperties setHeatLossPerBlock(float heatLossPerBlock) {
        this.heatLossPerBlock = Math.max(0.0f, Math.min(100.0f, heatLossPerBlock));
        return this;
    }

    /**
     * 计算实际热传导率（考虑环境因素）
     *
     * @param ambientTemperature 环境温度
     * @return 实际热传导率
     */
    public int getEffectiveHeatTransfer(int ambientTemperature) {
        if (ambientTemperature >= maxTemperature) {
            return 0; // 超过最大温度，热传导失效
        }

        // 温度越高，热传导效率可能降低（模拟热阻增加）
        float efficiency = 1.0f;
        if (ambientTemperature > maxTemperature * 0.8) {
            efficiency = 1.0f - (ambientTemperature - maxTemperature * 0.8f) / (maxTemperature * 0.2f);
        }

        return (int) (heatTransferRate * efficiency);
    }

    @Override
    public void verifyProperty(MaterialProperties properties) {
        // 热导管道需要材料具有DUST属性（固体材料）
        properties.ensureSet(PropertyKey.DUST, true);

        // 如果材料有INGOT属性，确保有板状形态用于制作管道
        Material thisMaterial = properties.getMaterial();
        if (properties.hasProperty(PropertyKey.INGOT)) {
            if (!thisMaterial.hasFlag(GENERATE_FOIL)) {
                thisMaterial.addFlags(GENERATE_FOIL);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HeatConductorProperties that)) return false;
        return maxTemperature == that.maxTemperature &&
                heatTransferRate == that.heatTransferRate &&
                Float.compare(that.heatLossPerBlock, heatLossPerBlock) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxTemperature, heatTransferRate, heatLossPerBlock);
    }

    @Override
    public String toString() {
        return "HeatConductorProperties{" +
                "maxTemperature=" + maxTemperature +
                ", heatTransferRate=" + heatTransferRate +
                ", heatLossPerBlock=" + heatLossPerBlock +
                '}';
    }

    /**
     * 创建副本
     *
     * @return 当前属性的副本
     */
    public HeatConductorProperties copy() {
        return new HeatConductorProperties(
                maxTemperature,
                heatTransferRate,
                heatLossPerBlock
        );
    }

    /**
     * 修改属性（用于管道类型修改）
     *
     * @param temperatureMultiplier 温度乘数
     * @param transferMultiplier    热传导率乘数
     * @param lossMultiplier        热损失乘数
     * @return 修改后的新属性
     */
    public HeatConductorProperties modifyProperties(float temperatureMultiplier,
                                                    float transferMultiplier,
                                                    float lossMultiplier) {
        int newMaxTemp = (int) (maxTemperature * temperatureMultiplier);
        int newTransferRate = (int) (heatTransferRate * transferMultiplier);
        float newHeatLoss = heatLossPerBlock * lossMultiplier;

        return new HeatConductorProperties(
                newMaxTemp,
                newTransferRate,
                newHeatLoss
        );
    }
}
