# RecipeMap Trait 架构 & 最优多方块继承体系

## 问题陈述

当前多方块的继承层次结构：

```
MetaTileEntity
  └── MultiblockControllerBase
        └── MultiblockWithDisplayBase
              ├── RecipeMapMultiblockController          (拥有 RecipeMap + RecipeLogic + ability 管理)
              │     ├── RecipeMapSteamMultiblockController  ← (位置有误，实际继承 MWDB)
              │     ├── HeatMultiblockController
              │     ├── AdvanceRecipeMapMultiblockController
              │     ├── MultiMapMultiblockController
              │     ├── NoEnergyMultiblockController
              │     └── FuelMultiblockController          (拥有 tier、IGenerator、发电输出)
              ├── RecipeMapPrimitiveMultiblockController  (无能源，无 RMMC 的 ability 管理)
              ├── RecipeMapSteamMultiblockController      (蒸汽驱动，重复了部分 ability 逻辑)
              └── ParametricMultiblockController<V>       (NBT 变种系统，不支持 RecipeMap)
```

### 核心冲突

`ParametricMultiblockController<V>`（变种系统）和 `RecipeMapMultiblockController`（配方系统）是**兄弟类** — 两者都继承自 `MultiblockWithDisplayBase`。一个同时需要两种特性的多方块（例如 LargeCombustionEngine、LargeTurbine、LargeBoiler）**无法同时继承两者**。

当前的变通方案：

- LargeBoiler/LargeMiner/FluidDrill：多 ID 注册（不使用变种系统）
- LargeCombustionEngine/LargeTurbine：多 ID 注册 + enum type 字段

这导致每 N 个变种消耗 N 个 ID、`if (engineType != null)` 的防御性分支、以及不一致的代码模式。

***

## 提案架构：RecipeMap 作为 Trait

### 阶段 1：提取 `IRecipeMapHolder` 接口

从 `RecipeMapMultiblockController` 中提取配方相关的 API 为独立接口：

```java
/**
 * Interface for multiblocks that hold and process recipes.
 * Decouples recipe processing from class hierarchy.
 */
public interface IRecipeMapHolder {

    RecipeMap<?> getRecipeMap();

    @Nullable RecipeMap<?>[] getAvailableRecipeMaps();

    MultiblockRecipeLogic getRecipeMapWorkable();

    IItemHandlerModifiable getInputInventory();
    IItemHandlerModifiable getOutputInventory();
    IMultipleTankHandler getInputFluidInventory();
    IMultipleTankHandler getOutputFluidInventory();
    IEnergyContainer getEnergyContainer();

    boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess);

    void refreshAllBeforeConsumption();
}
```

### 阶段 2：提取 `RecipeAbilityManager` 组合工具

将 ability 初始化/重置逻辑提取为可组合的辅助类：

```java
/**
 * Manages recipe-related MultiblockAbility instances.
 * Can be composed into any MultiblockWithDisplayBase subclass.
 */
public class RecipeAbilityManager {

    private final MultiblockControllerBase controller;

    private IItemHandlerModifiable inputInventory;
    private IItemHandlerModifiable outputInventory;
    private IMultipleTankHandler inputFluidInventory;
    private IMultipleTankHandler outputFluidInventory;
    private IEnergyContainer energyContainer;

    public void initializeAbilities() { /* 来自 RMMC.initializeAbilities() */ }
    public void resetTileAbilities() { /* 来自 RMMC.resetTileAbilities() */ }

    // Getters...
}
```

### 阶段 3：让 ParametricMultiblockController 支持配方

```java
public abstract class ParametricMultiblockController<V extends Enum<V>>
        extends MultiblockWithDisplayBase
        implements IRecipeMapHolder {  // <-- 添加配方支持

    private final RecipeAbilityManager abilityManager;
    protected MultiblockRecipeLogic recipeMapWorkable;

    // 变种可以选择性地更改 RecipeMap
    public RecipeMap<?> getRecipeMap() {
        return getRecipeMapForVariant(getVariant());
    }

    // 子类重写点：针对特定变种的 RecipeMap（例如 LargeTurbine）
    protected RecipeMap<?> getRecipeMapForVariant(V variant) {
        return defaultRecipeMap;
    }
}
```

### 阶段 4：迁移所有变种多方块

| 类名                           | 当前基类                           | 新基类                            | 备注                  |
| ---------------------------- | ------------------------------ | ------------------------------ | ------------------- |
| MetaTileEntityMultiblockTank | ParametricMultiblockController | ParametricMultiblockController | 已完成                 |
| LargeBoiler                  | MultiblockWithDisplayBase      | ParametricMultiblockController | 自定义 RecipeLogic     |
| LargeMiner                   | MultiblockWithDisplayBase      | ParametricMultiblockController | 无 RecipeMap（自定义逻辑）  |
| FluidDrill                   | MultiblockWithDisplayBase      | ParametricMultiblockController | 无 RecipeMap（自定义逻辑）  |
| LargeCombustionEngine        | FuelMultiblockController       | ParametricFuelController?      | 需要燃料逻辑              |
| LargeTurbine                 | FuelMultiblockController       | ParametricFuelController?      | 需要燃料 + 可变 RecipeMap |

### 阶段 5：可选 - ParametricFuelController

为基于燃料的变种多方块提供：

```java
public abstract class ParametricFuelMultiblockController<V extends Enum<V>>
        extends ParametricMultiblockController<V>
        implements IGenerator {

    protected int tier;

    @Override
    protected void onVariantChanged() {
        this.tier = getTierForVariant(getVariant());
        this.recipeMapWorkable = createWorkableForVariant(getVariant());
    }

    protected abstract int getTierForVariant(V variant);
}
```

***

## 对比：当前方案 vs 提案

### 底层（架构层面）

| 维度            | 当前                                             | 提案                                                              |
| ------------- | ---------------------------------------------- | --------------------------------------------------------------- |
| 配方逻辑耦合        | 硬继承（`extends RMMC`）                            | 接口 + 组合（`implements IRecipeMapHolder` + `RecipeAbilityManager`） |
| 变种系统范围        | 仅限无 RecipeMap 的 `MultiblockWithDisplayBase` 子类 | 所有多方块，包括配方类和燃料类                                                 |
| ID 消耗         | RMMC 子类每 N 个变种消耗 N 个 ID                        | 每个多方块始终 1 个 ID                                                  |
| RecipeMap 可变性 | RMMC 中 `public final`，不可变                      | 虚方法 `getRecipeMap()`，变种可改变                                      |
| 多重继承          | 不可能（PMC vs RMMC vs FMC）                        | 通过组合实现                                                          |

### 功能层

| 维度                 | 当前                     | 提案                            |
| ------------------ | ---------------------- | ----------------------------- |
| 添加新变种多方块           | 每个类约 200 行样板代码         | 继承 PMC，实现 3-4 个方法             |
| 不同变种使用不同 RecipeMap | 不使用多 ID 不可能实现          | 重写 `getRecipeMapForVariant()` |
| 不同变种使用不同 tier/燃料   | 不使用多 ID 不可能实现          | 重写 `onVariantChanged()`       |
| Addon 继承变种多方块      | 必须使用 `@Deprecated` 构造器 | 正常继承，重写变种方法                   |
| JEI 注册             | 每个变种手动注册               | 由 PMC 基类自动处理                  |
| NBT/网络同步/子物品       | 每个类手动实现                | 由 PMC 自动处理                    |

***

## 迁移计划（增量式）

### 步骤 1：提取 IRecipeMapHolder 接口（非破坏性）

- 创建 `IRecipeMapHolder.java` 接口
- 让 `RecipeMapMultiblockController` 实现它（简单 — 已经有所有方法）
- **零行为变更**，纯增量

### 步骤 2：提取 RecipeAbilityManager（非破坏性）

- 将 ability 初始化/重置逻辑移到组合辅助类
- `RecipeMapMultiblockController` 内部委托给它
- **零行为变更**，内部重构

### 步骤 3：为 ParametricMultiblockController 添加 IRecipeMapHolder 支持（非破坏性）

- PMC 可选实现 `IRecipeMapHolder`
- 子类通过提供 RecipeMap 来选择性启用
- 现有的 PMC 子类（MultiblockTank）不受影响

### 步骤 4：创建 ParametricFuelController（非破坏性）

- 新类继承 PMC 并集成燃料逻辑
- `FuelMultiblockController` 保留用于向后兼容

### 步骤 5：逐个迁移变种多方块

- 每次迁移独立，可增量进行
- 旧的多 ID 注册替换为单 ID
- 通过 `@Deprecated` ID 别名保持 addon 兼容

***

## 风险与缓解措施

| 风险                                               | 影响 | 缓解措施                                                 |
| ------------------------------------------------ | -- | ---------------------------------------------------- |
| Addon 代码直接引用 `RecipeMapMultiblockController` 字段  | 高  | 保留 RMMC 不变，IRecipeMapHolder 纯增量                      |
| Addon 继承 `FuelMultiblockController`              | 中  | 保留 FMC，ParametricFuelController 是新的替代方案              |
| RecipeMap getter 变为虚方法（性能）                       | 低  | 热路径已经通过 `recipeMapWorkable.getRecipeMap()`，1 次虚调用可忽略 |
| RMMC 中的 `public final RecipeMap<?> recipeMap` 字段 | 中  | 不移除；新代码使用 `getRecipeMap()` 方法替代                      |
| JEI 通过 `createMetaTileEntity` 创建副本               | 低  | PMC 已支持；燃料变种只需在之后调用 `onVariantChanged()`             |

***

## 预估规模

| 步骤         | 修改文件数      | 新增文件数                        | 是否破坏性？            |
| ---------- | ---------- | ---------------------------- | ----------------- |
| 步骤 1       | 1 (RMMC)   | 1 (IRecipeMapHolder)         | 否                 |
| 步骤 2       | 1 (RMMC)   | 1 (RecipeAbilityManager)     | 否                 |
| 步骤 3       | 1 (PMC)    | 0                            | 否                 |
| 步骤 4       | 0          | 1 (ParametricFuelController) | 否                 |
| 步骤 5 (每个类) | 2 (类 + 注册) | 0                            | 软性（deprecated 别名） |

总计：约 5-7 个新文件，约 10 个修改文件，完全增量式，保持 deprecated 别名即可零破坏性变更。

***

## 实施决策点

1. **`IRecipeMapHolder`** **是否应长期完全替代** **`RecipeMapMultiblockController`？**
   - 是 → 最终废弃 RMMC，所有新多方块使用 PMC + IRecipeMapHolder
   - 否 → 保持两者，IRecipeMapHolder 仅用于 PMC 类的变种多方块
2. **RMMC 中的** **`RecipeMap`** **是否应变为非 final？**
   - 否（推荐） → 新代码使用虚方法 `getRecipeMap()`，保留字段以兼容二进制
   - 是 → 更简单但破坏假设
3. **LargeMiner/FluidDrill 是否需要 IRecipeMapHolder？**
   - 否 — 它们有自定义逻辑且不使用 RecipeMap，仅需 PMC 做变种管理
   - RecipeAbilityManager 对每个类是可选的
4. **何时废弃旧的多 ID 注册？**
   - 在所有 addon 迁移完成后（通过 @ApiStatus.ScheduledForRemoval 跟踪）

