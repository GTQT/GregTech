package gregtech.common.pipelike.heat;


import gregtech.api.pipenet.block.material.IMaterialPipeType;
import gregtech.api.unification.material.properties.HeatConductorProperties;
import gregtech.api.unification.ore.OrePrefix;

import org.jetbrains.annotations.NotNull;

public enum HeatConductorType implements IMaterialPipeType<HeatConductorProperties> {

    // 普通热导管道系列（热损失较大）
    HEAT_CONDUCTOR_1X("heat_conductor_1x", 0.125f, 1, 2, OrePrefix.pipeHeatConductorSingle, -1),
    HEAT_CONDUCTOR_2X("heat_conductor_2x", 0.25f, 2, 2, OrePrefix.pipeHeatConductorDouble, -1),
    HEAT_CONDUCTOR_4X("heat_conductor_4x", 0.375f, 4, 3, OrePrefix.pipeHeatConductorQuadruple, -1),
    HEAT_CONDUCTOR_8X("heat_conductor_8x", 0.5f, 8, 3, OrePrefix.pipeHeatConductorOctal, -1),
    HEAT_CONDUCTOR_16X("heat_conductor_16x", 0.75f, 16, 3, OrePrefix.pipeHeatConductorHex, -1),

    // 隔热热导管道系列（热损失小，厚度更大）
    INSULATED_HEAT_CONDUCTOR_1X("insulated_heat_conductor_1x", 0.25f, 1, 1, OrePrefix.insulatedHeatConductorSingle, 0),
    INSULATED_HEAT_CONDUCTOR_2X("insulated_heat_conductor_2x", 0.375f, 2, 1, OrePrefix.insulatedHeatConductorDouble, 1),
    INSULATED_HEAT_CONDUCTOR_4X("insulated_heat_conductor_4x", 0.5f, 4, 1, OrePrefix.insulatedHeatConductorQuadruple, 2),
    INSULATED_HEAT_CONDUCTOR_8X("insulated_heat_conductor_8x", 0.75f, 8, 1, OrePrefix.insulatedHeatConductorOctal, 3),
    INSULATED_HEAT_CONDUCTOR_16X("insulated_heat_conductor_16x", 1.0f, 16, 1, OrePrefix.insulatedHeatConductorHex, 4);

    public static final HeatConductorType[] VALUES = values();

    public final String name;
    public final float thickness;
    public final int heatMultiplier;  // 热量传输倍率
    public final float lossFactor;    // 热损失系数（每格损失的比例）
    public final OrePrefix orePrefix;
    public final int insulationLevel;

    HeatConductorType(String name, float thickness, int heatMultiplier, float lossFactor, OrePrefix orePrefix, int insulated) {
        this.name = name;
        this.thickness = thickness;
        this.heatMultiplier = heatMultiplier;
        this.lossFactor = lossFactor;
        this.orePrefix = orePrefix;
        this.insulationLevel = insulated;
    }

    @NotNull
    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getThickness() {
        return thickness;
    }

    @Override
    public OrePrefix getOrePrefix() {
        return orePrefix;
    }

    @Override
    public HeatConductorProperties modifyProperties(HeatConductorProperties baseProperties) {
        // 基础热传导属性乘以倍率
        int maxTemperature = baseProperties.getMaxTemperature();
        int heatTransfer = baseProperties.getHeatTransfer() * heatMultiplier;
        // 热损失 = 基础热损失 * 损失系数
        float heatLoss = baseProperties.getHeatLossPerBlock() * lossFactor;

        return new HeatConductorProperties(maxTemperature, heatTransfer, heatLoss);
    }

    @Override
    public boolean isPaintable() {
        return true;
    }
}
