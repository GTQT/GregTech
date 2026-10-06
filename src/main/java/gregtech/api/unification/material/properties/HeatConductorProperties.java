package gregtech.api.unification.material.properties;

import java.util.Objects;

public class HeatConductorProperties implements IMaterialProperty {

    private int maxTemperature;           // 最大承受温度（开尔文）
    private int heatTransferRate;         // 热传导率（HU/tick，Heat Unit）
    private float heatLossPerBlock;       // 每格热损失系数（0.0-1.0 的比例）

    /**
     * 把热损失夹到 [0, 1)。取值 1 会让 {@code 1 - loss} 变成 0（热量完全传不出去），
     * 大于 1 更会算出负效率，所以上界必须严格小于 1。
     */
    private static float clampHeatLoss(float heatLossPerBlock) {
        if (Float.isNaN(heatLossPerBlock) || heatLossPerBlock < 0.0f) return 0.0f;
        return Math.min(0.99f, heatLossPerBlock);
    }

    public HeatConductorProperties(int maxTemperature, int heatTransferRate, float heatLossPerBlock) {
        this.maxTemperature = maxTemperature;
        this.heatTransferRate = heatTransferRate;
        this.heatLossPerBlock = clampHeatLoss(heatLossPerBlock);
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
     * @return 热损失系数（0.0 - 0.99 的比例，0.02 表示每格损失 2%）
     */
    public float getHeatLossPerBlock() {
        return heatLossPerBlock;
    }

    /**
     * 设置每格热损失系数
     *
     * @param heatLossPerBlock 新的热损失系数（0.0 - 0.99 的比例）
     * @return 当前实例，便于链式调用
     */
    public HeatConductorProperties setHeatLossPerBlock(float heatLossPerBlock) {
        this.heatLossPerBlock = clampHeatLoss(heatLossPerBlock);
        return this;
    }

    @Override
    public void verifyProperty(MaterialProperties properties) {
        // 热导管道由锭/板加工而来，BlockHeatConductor.isValidPipeMaterial 也要求 INGOT，
        // 这里必须保持一致，否则会出现"属性通过校验但配方/物品生成不出来"。
        properties.ensureSet(PropertyKey.INGOT, true);
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
