# 多方块并行模式指南（并行 / 多线程 / 跨并）

本文面向附属作者，说明怎么给自己的多方块开**多线程**和**跨配方并行**。
不需要改调度器，也不需要记忆任何内部状态——所有配置都是从两个数值推导出来的。

## 1. 三个正交的概念

| 概念 | 含义 | 取值来源 | 默认 |
| --- | --- | --- | --- |
| **并行** | 一个槽位内同时做几份**同一个**配方 | `getParallelLimit()` | 1 |
| **多线程** | 同时允许几个**不同**配方在跑 | `getThreadLimit()` | 1 |
| **跨并** | 槽位数不封顶，并行预算按进料弹性分配 | 跨并控制仓 → `isCrossRecipeParallelEnabled()` | false |

三者都定义在 `AbstractRecipeLogic`，是**拉取式**的：调度器每个 tick 重新读一次，
只有它们全为假时机器才回落到 `AbstractRecipeLogic` 的经典单配方路径
（判定见 `MultiblockRecipeLogic#usesParallelScheduler()`）。

**拉取式意味着：改数值不需要通知任何人。** 不需要重建逻辑对象、不需要发自定义包、
不需要写刷新回调。下一 tick 自动生效。

## 2. 三种模式 = 同一个调度器的三种配置

`MultiblockRecipeLogic#getOrCreateScheduler()` 每 tick 这样推导：

```java
int threads  = Math.max(1, getThreadLimit());
int parallel = Math.max(1, getParallelLimit());
boolean crossRecipe = isCrossRecipeParallelEnabled();
boolean threaded    = !crossRecipe && threads > 1;

scheduler.setParallelLimit(threaded ? parallel * threads : parallel);
scheduler.setMaxSlots(crossRecipe ? 0 : threads);          // 0 = 不限
scheduler.setPerSlotParallelCap(threaded ? parallel : 0);  // 0 = 不限
```

| 模式 | 触发 | 槽位上限 | 单槽并行上限 | 总并行预算 |
| --- | --- | --- | --- | --- |
| 单配方 | 默认 | 1 | 不限 | `parallelLimit` |
| 多线程 | `threadLimit > 1` | 线程数 T | `parallelLimit` P | `T × P` |
| 跨并 | 装跨并控制仓（或覆写返回 true） | 不限 | 不限 | `parallelLimit` |

槽位是这样被填的（`fillSchedulerSlots`）：先用缓存的上次配方占一个槽，再用 `RecipeIterator`
遍历所有**不同**配方，每认领一个就 `exclude` 掉——所以多个槽天然跑不同的配方。
第一阶段只算并行不扣料，第二阶段才按基础功率比例分配超频预算并真正消耗输入。

## 3. 招式一：跨配方并行

### 3.1 装跨并控制仓（推荐）

`MetaTileEntityCrossParallelHatch` 和普通并行仓**共用同一个 ability**
（`MultiblockAbility.PARALLEL_HATCH`），只是多了一个"我要跨并"的标记。
所以结构上不需要任何额外声明：**凡是能装并行仓的机器，直接装跨并控制仓就是跨并模式**。

机器侧要做的只有一件事——把标记接到逻辑上。继承 `GCYMMultiblockRecipeLogic` 的话这一步已经做好了：

```java
    @Override
    public boolean isCrossRecipeParallelEnabled() {
        return metaTileEntity instanceof IParallelMultiblock parallel && parallel.isParallel() &&
                parallel.isCrossParallel();
    }
```

自己写的逻辑类照抄这个即可，控制器侧需要实现 `IParallelMultiblock#isCrossParallel()`——
读装上的那个仓的 `IParallelHatch#isCrossParallel()`。

> 跨并控制仓的 tooltip 已标注**与线程控制仓不兼容**：开跨并后
> `threaded = !crossRecipe && threads > 1` 为假，线程倍率不再生效。

### 3.2 覆写方法（非 GCYM 机器，或想写死行为）

跨并对控制器没有任何要求，任意 `MultiblockRecipeLogic` 子类都行。
在自己的机器类里放一个内部逻辑类，覆写两个方法：

```java
public class MyMachine extends RecipeMapMultiblockController {

    public MyMachine(ResourceLocation id) {
        super(id, MyRecipeMaps.MY_RECIPES);
        this.recipeMapWorkable = new MyMachineWorkable(this);
    }

    protected class MyMachineWorkable extends MultiblockRecipeLogic {

        public MyMachineWorkable(RecipeMapMultiblockController tileEntity) {
            super(tileEntity);
        }

        /** 跨配方并行：槽位不封顶，并行预算按进料在多个配方之间弹性分配。 */
        @Override
        public boolean isCrossRecipeParallelEnabled() {
            return true;
        }

        /** 跨并的预算就是并行上限，这里给 128。 */
        @Override
        public int getParallelLimit() {
            return 128;
        }
    }
}
```

效果：输入仓里有多少种能跑的配方，就同时开多少个槽；128 的并行预算按各配方**实际能吃饱多少**
分下去，某个配方吃不饱时剩下的自动让给别的配方。

> `getParallelLimit()` 必须大于 1，否则预算只有 1，跨并退化成"一次只跑一个"。

## 4. 招式二：多线程

多线程要三处配合。**推荐直接继承 `GCYMRecipeMapMultiblockController`**，这三处它全都做好了，
你只需要在结构里声明线程仓。

### 4.1 结构里声明（用 GCYM 基类时只需要这一步）

```java
DeclarativePatternBuilder.start()
        ...
        .autoGCYM(true, true, true, true)   // 参数顺序：并行, 线程, 超频, 加速
        ...
```

`autoGCYM` 一次声明四个仓，`false` 表示这台机器不装该仓（该仓不会被注册进结构）。

不用 `autoGCYM` 时，也可以逐个单写 `.parallelHatch()` / `.threadHatch()` / `.overclockHatch()` /
`.accelerationHatch()` / `.tieredHatch()`，效果等价。**但同一个仓不要声明两次**——
`.autoGCYM(..., true, ...)` 之后再补一句 `.threadHatch()` 会让该仓的上限变成 2，
结构里就能塞两个线程仓了。

四个仓对应的接口开关是 `isParallel()` / `isThread()` / `isOverclock()` / `isAccelerate()`，
`GCYMRecipeMapMultiblockController` 里四个都默认返回 `true`。**如果你的机器没有某个仓，
要覆写对应开关返回 false**，否则 tooltip 会宣称支持一个它其实装不了的仓。

### 4.2 不想继承 GCYM：控制器实现 `IThreadMultiblock`

```java
public class MyController extends RecipeMapMultiblockController implements IThreadMultiblock {

    @Override
    public boolean isThread() {
        return true;   // 能力声明：这台机器支持多线程
    }

    @Override
    public int getThread() {
        return getAbilities(MultiblockAbility.THREAD_HATCH).isEmpty() ? 1 :
                getAbilities(MultiblockAbility.THREAD_HATCH).get(0).getCurrentThread();
    }

    @Override
    public void setThread(int thread) {
        if (!getAbilities(MultiblockAbility.THREAD_HATCH).isEmpty()) {
            getAbilities(MultiblockAbility.THREAD_HATCH).get(0).setCurrentThread(thread);
        }
    }

    @Override
    public int getMaxThread() {
        return getAbilities(MultiblockAbility.THREAD_HATCH).isEmpty() ? 1 :
                getAbilities(MultiblockAbility.THREAD_HATCH).get(0).getMaxThread();
    }
}
```

结构里同样要 `.threadHatch()`。`isThread()` 是能力声明（和 `IParallelMultiblock#isParallel()` 同构），
**要判断"这台机器有没有装线程仓"请用 `getMaxThread() > 1`**。

### 4.3 逻辑把线程数拉过来

```java
public class MyWorkable extends MultiblockRecipeLogic {

    @Override
    public int getThreadLimit() {
        if (metaTileEntity instanceof IThreadMultiblock thread && thread.isThread()) {
            return thread.getThread();
        }
        return 1;
    }
}
```

这就是 `GCYMMultiblockRecipeLogic` 的全部做法，和它拉并行上限的方式完全一样。

> 另一条路是**推**：控制器在结构成型和线程变更时调用
> `recipeMapWorkable.setThreadLimit(n)`。适合不想覆写 getter 的场景。
> 注意两条路二选一，不要既覆写 getter 又推值。

## 5. 坑与注意事项

**跨并会吃掉线程倍率。** `threaded = !crossRecipe && threads > 1`——开了跨并，
总预算从 `T × P` 掉回 `P`。所以跨并**不是**"更强的线程"，它换到的是分配弹性：
没有槽位上限、没有单槽上限，预算能拆给任意多个配方。以 T=4、P=4 为例：

| | 总并行 | 并发配方数 | 每槽上限 |
| --- | --- | --- | --- |
| 多线程 | 16 | ≤ 4 | 4 |
| 跨并 | 4 | 不限 | 不限 |

需要"总量更大"就用线程，需要"同样的钱花得更满"就用跨并。

**耗电模型**由 `shouldParallelMultiplyPower()` 决定：

* 返回 `true`（`MultiblockRecipeLogic` 默认）——每个槽预留 `baseEUt × 并行数`，并行会放大耗电。
* 返回 `false`（`GCYMMultiblockRecipeLogic`）——每个槽只预留 `baseEUt`，**并行不耗电，占用的是线程数**。

**并行受供电限制。** 调度器每 tick 按 `Σ(运行中槽的 EUt)` 一次性扣电，不够则所有槽一起冻结
（不是部分推进）。同时每开一个新槽要先预留基础功率，预留不满就少开一个槽。所以实际并发数
由 `getTotalPowerBudget()`（能量仓 V×A 之和）决定，不是只看线程数。

**线程数变更不需要任何刷新。** 收窄时已有的槽不会被驱逐，会跑完自然排空、不再补新槽；
放宽时下一 tick 自动补槽。结构成型、加载存档也不需要额外处理——线程数存在线程仓里，它自己持久化。

**GUI 与 tooltip。** 并行/线程/超频/加速四个仓位在 GCYM 基类里都接了调节按钮和 tooltip 行；
线程按钮在 `getMaxThread() <= 1` 时自动隐藏。自己实现 `IThreadMultiblock` 的话，
按钮和显示要自己接（可参考 `GCYMRecipeMapMultiblockController#createUIFactory`）。

## 6. 相关类型速查

| 类型 | 职责 |
| --- | --- |
| `AbstractRecipeLogic#getParallelLimit` / `getThreadLimit` / `isCrossRecipeParallelEnabled` | 三种配置的读取点 |
| `MultiblockRecipeLogic#usesParallelScheduler` | 是否启用调度器 |
| `MultiblockRecipeLogic#getOrCreateScheduler` | 三种模式的推导处 |
| `MultiblockRecipeLogic#getTotalPowerBudget` | 功率预算 = 能量仓 V×A |
| `CrossRecipeParallelScheduler` | 槽位容器，`setMaxSlots` / `setPerSlotParallelCap` / `setParallelLimit` |
| `MultiblockAbility.THREAD_HATCH` / `IThreadHatch` / `IThreadMultiblock` | 线程仓能力与控制器接口 |
| `MultiblockAbility.PARALLEL_HATCH` / `IParallelMultiblock` | 并行仓能力与控制器接口 |
| `MetaTileEntityCrossParallelHatch` / `IParallelHatch#isCrossParallel` | 跨并仓：与并行仓共用 ability，只多一个标记 |
| `DeclarativePatternBuilder.CasingSlot#threadHatch` / `#autoGCYM` | 结构声明 |
