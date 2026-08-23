package gregtech.api.worldgen.config;

import gregtech.api.worldgen.bedrockFluids.BedrockFluidVeinHandler;

import net.minecraftforge.fluids.Fluid;

/**
 * 基岩流体矿脉定义 builder：纯代码注册，build 时自动注册进 {@link BedrockFluidVeinHandler}
 */
public class BedrockFluidDepositBuilder
        extends DepositBuilder<BedrockFluidDepositBuilder, BedrockFluidDepositDefinition> {

    private int minimumYield;
    private int maximumYield;
    private int depletionAmount;
    private int depletionChance;
    private int depletedYield;
    private Fluid storedFluid;

    public static BedrockFluidDepositBuilder definitionBuilder(String depositName) {
        return new BedrockFluidDepositBuilder(depositName);
    }

    private BedrockFluidDepositBuilder(String depositName) {
        super(depositName);
    }

    @Override
    public BedrockFluidDepositBuilder getThis() {
        return this;
    }

    public BedrockFluidDepositBuilder yields(int minimumYield, int maximumYield) {
        this.minimumYield = minimumYield;
        this.maximumYield = maximumYield;
        return getThis();
    }

    public BedrockFluidDepositBuilder depletion(int amount, int chance, int depletedYield) {
        this.depletionAmount = amount;
        this.depletionChance = chance;
        this.depletedYield = depletedYield;
        return getThis();
    }

    public BedrockFluidDepositBuilder fluid(Fluid fluid) {
        this.storedFluid = fluid;
        return getThis();
    }

    /** 构建并注册到 WorldGenRegistry（addon 推荐入口） */
    public void buildAndRegister(WorldGenRegistry registry) {
        registry.addVeinDefinitions(build());
    }

    @Override
    public BedrockFluidDepositDefinition createDefinition() {
        return new BedrockFluidDepositDefinition(depositName);
    }

    @Override
    public void verifyProperties() {
        if (storedFluid == null) {
            throw new IllegalStateException("BedrockFluidDepositBuilder " + depositName + " doesn't have a fluid!");
        }
    }

    @Override
    public BedrockFluidDepositDefinition build() {
        BedrockFluidDepositDefinition definition = super.build();
        definition.setYields(minimumYield, maximumYield);
        definition.setDepletionAmount(depletionAmount);
        definition.setDepletionChance(Math.max(0, Math.min(100, depletionChance)));
        definition.setDepletedYield(depletedYield);
        definition.setStoredFluid(storedFluid);
        // 原 JSON 解析流程在初始化末尾注册进 veinList，时序保持：init 阶段完成注册
        BedrockFluidVeinHandler.addFluidDeposit(definition);
        return definition;
    }
}
