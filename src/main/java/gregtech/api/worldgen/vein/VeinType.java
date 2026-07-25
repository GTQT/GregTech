package gregtech.api.worldgen.vein;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 矿脉类型：定义名称、可选矿物池，以及生成时随机抽取 2~4 种矿物的规则。
 *
 * 注册示例（在 VeinRegistry 中）：
 * <pre>
 *   VeinType iron = new VeinType("iron_vein")
 *       .addOre("minecraft:iron_ore",   60)
 *       .addOre("minecraft:copper_ore", 30)
 *       .addOre("minecraft:coal_ore",   10);
 *   VeinRegistry.register(iron);
 * </pre>
 */
public class VeinType {

    /** 矿脉类型唯一标识，全小写，用下划线分隔 */
    public final String id;
    //维度
    private final Set<Integer> allowedDimensions = new HashSet<>();
    /** 矿物候选池（不可变，build 后锁定） */
    private final List<OreEntry> orePool = new ArrayList<>();

    /** 生成时从 orePool 中随机挑选的矿物种数范围 */
    private int minOreTypes = 2;
    private int maxOreTypes = 4;

    /** 随机选择权重（类似 BedrockFluidDepositDefinition.getWeight()） */
    private int weight = 10;
    /** 每次采集产出数量范围 */
    private int minYield = 1;
    private int maxYield = 3;

    /** 初始操作次数（类似 bedrockFluids 的 MAXIMUM_VEIN_OPERATIONS） */
    private int maxOperations = 100_000;
    /** 每次采集触发枯竭的几率（0-100） */
    private int depletionChance = 1;
    /** 触发枯竭时减少的操作次数 */
    private int depletionAmount = 100;
    /** 枯竭后的产出倍率（0.0-1.0），默认 0.2 = 20% */
    private double depletedYield = 0.2;

    public VeinType(String id) {
        this.id = id;
    }

    public VeinType addOre(String name, int weight) {
        orePool.add(new OreEntry(name, weight));
        return this;
    }

    public VeinType setOreTypeRange(int min, int max) {
        this.minOreTypes = min;
        this.maxOreTypes = max;
        return this;
    }

    public VeinType setWeight(int weight) {
        this.weight = weight;
        return this;
    }

    public VeinType setYield(int min, int max) {
        this.minYield = min;
        this.maxYield = max;
        return this;
    }

    public VeinType setMaxOperations(int maxOperations) {
        this.maxOperations = maxOperations;
        return this;
    }

    public VeinType setDepletionChance(int depletionChance) {
        this.depletionChance = depletionChance;
        return this;
    }

    public VeinType setDepletionAmount(int depletionAmount) {
        this.depletionAmount = depletionAmount;
        return this;
    }

    public VeinType setDepletedYield(double depletedYield) {
        this.depletedYield = depletedYield;
        return this;
    }

    public List<OreEntry> getOrePool() {
        return Collections.unmodifiableList(orePool);
    }
    /**
     * 添加允许生成的维度 ID。
     * 不调用此方法 = 所有维度都允许（兼容旧代码）。
     *
     * 常用维度 ID：
     *   0  = 主世界 (Overworld)
     *  -1  = 下界 (Nether)
     *   1  = 末地 (The End)
     *  其他 = 模组自定义维度（通过 DimensionManager 查询）
     */
    public VeinType addDimension(int dimensionId) {
        allowedDimensions.add(dimensionId);
        return this;
    }

    public boolean isAllowedInDimension(int dimensionId) {
        // 白名单为空 = 不限维度
        return allowedDimensions.isEmpty() || allowedDimensions.contains(dimensionId);
    }

    public Set<Integer> getAllowedDimensions() {
        return Collections.unmodifiableSet(allowedDimensions);
    }
    public int getMinOreTypes() { return minOreTypes; }
    public int getMaxOreTypes() { return maxOreTypes; }
    public int getWeight() { return weight; }
    public int getMinYield() { return minYield; }
    public int getMaxYield() { return maxYield; }
    public int getMaxOperations() { return maxOperations; }
    public int getDepletionChance() { return depletionChance; }
    public int getDepletionAmount() { return depletionAmount; }
    public double getDepletedYield() { return depletedYield; }
}
