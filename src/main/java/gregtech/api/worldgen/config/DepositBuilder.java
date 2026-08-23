package gregtech.api.worldgen.config;

import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 矿脉定义 builder 基类：纯代码注册，不再依赖任何 JSON/config 文件
 *
 * @param <B> builder 自身类型（链式调用用）
 * @param <D> 定义类型
 */
public abstract class DepositBuilder<B extends DepositBuilder<B, D>, D extends IWorldgenDefinition> {

    protected final String depositName;

    protected int weight;
    protected int priority;
    protected String assignedName;
    protected String description;
    protected boolean countAsVein = true;

    protected Function<Biome, Integer> biomeWeightModifier = OreDepositDefinition.NO_BIOME_INFLUENCE;
    protected Predicate<WorldProvider> dimensionFilter = OreDepositDefinition.PREDICATE_SURFACE_WORLD;

    protected DepositBuilder(String depositName) {
        this.depositName = depositName;
    }

    public abstract B getThis();

    public B translationKey(String translationKey) {
        this.assignedName = translationKey;
        return getThis();
    }

    public B description(String description) {
        this.description = description;
        return getThis();
    }

    public B weight(int weight) {
        this.weight = weight;
        return getThis();
    }

    public B priority(int priority) {
        this.priority = priority;
        return getThis();
    }

    public B countAsVein(boolean countAsVein) {
        this.countAsVein = countAsVein;
        return getThis();
    }

    public B biomeWeightModifier(Function<Biome, Integer> modifier) {
        this.biomeWeightModifier = modifier;
        return getThis();
    }

    public B biomeWeightModifierDictionary(Map<String, Integer> dictionaryModifiers) {
        this.biomeWeightModifier = WorldConfigUtils.biomeWeightModifierDictionary(dictionaryModifiers);
        return getThis();
    }

    public B biomeWeightModifierMap(Map<String, Integer> biomeMap) {
        this.biomeWeightModifier = WorldConfigUtils.biomeWeightModifierMap(biomeMap);
        return getThis();
    }

    public B dimensionFilter(Predicate<WorldProvider> filter) {
        this.dimensionFilter = filter;
        return getThis();
    }

    public B overworldOnly() {
        this.dimensionFilter = WorldConfigUtils.predicateIsSurfaceWorld();
        return getThis();
    }

    public B netherOnly() {
        this.dimensionFilter = WorldConfigUtils.predicateIsNether();
        return getThis();
    }

    public B endOnly() {
        this.dimensionFilter = WorldConfigUtils.predicateIsEnd();
        return getThis();
    }

    /** 按维度类型名过滤（如 "the_end" / "the_nether"） */
    public B dimensionName(String name) {
        this.dimensionFilter = WorldConfigUtils.predicateDimensionName(name);
        return getThis();
    }

    /** 按维度 ID 过滤（如 0 = 主世界, -1 = 下界, 1 = 末地） */
    public B dimensionId(int dimensionId) {
        this.dimensionFilter = WorldConfigUtils.predicateDimension(dimensionId);
        return getThis();
    }

    public abstract D createDefinition();

    public abstract void verifyProperties();

    /** 构建定义，写入 builder 中已配置的字段 */
    public D build() {
        verifyProperties();
        D definition = createDefinition();
        applyProperties(definition);
        return definition;
    }

    /** 将 builder 持有的通用字段写入定义 */
    protected void applyProperties(D definition) {
        if (definition instanceof OreDepositDefinition oreDeposit) {
            oreDeposit.setWeight(weight);
            oreDeposit.setPriority(priority);
            oreDeposit.setAssignedName(assignedName);
            oreDeposit.setDescription(description);
            oreDeposit.setCountAsVein(countAsVein);
            oreDeposit.setBiomeWeightModifier(biomeWeightModifier);
            oreDeposit.setDimensionFilter(dimensionFilter);
        } else if (definition instanceof BedrockFluidDepositDefinition bedrockFluid) {
            bedrockFluid.setWeight(weight);
            bedrockFluid.setAssignedName(assignedName);
            bedrockFluid.setDescription(description);
            bedrockFluid.setBiomeWeightModifier(biomeWeightModifier);
            bedrockFluid.setDimensionFilter(dimensionFilter);
        }
    }
}
