# 多方块结构系统统一重构计划书

更新时间：2026-05-06（架构审查更新）

## 文档状态

本计划书是当前唯一执行入口，已合并以下两份原计划：

- `docs/multiblock-refactor.md`
- `docs/gt5-structure-channel-porting-plan.md`

GT5 信道移植不再作为独立工程执行，而是并入多方块结构系统重构，作为“结构信道、JEI、投影仪、自动建造一致性”主线。

## 当前结论

当前项目已经写入了多方块结构系统重构的大部分主体代码，但还不能判定为完成。

当前状态：

> 主体实现已落地，但存在多个 P0 级架构缺陷：事件驱动系统被子类绕过、异步检查存在 data race、
> 分片检查首次成形流程缺失。编译阻塞待修复；P0/P2/P4 已接入主流程但未真正生效，
> P1/P3 与 GT5 信道 parity 尚未完全兑现，整体仍处于待修复与验收阶段。

当前最直接的阻塞点是 `compileJava` 未通过：

- 执行命令：`./gradlew --% compileJava --no-daemon -Dorg.gradle.workers.max=1 -Dorg.gradle.compiler.daemon=false`
- 编译失败位置：`src/main/java/gregtech/common/metatileentities/multi/electric/godforge/ForgeOfGodsStructureString.java`
- 报错原因：第 1 行存在 `\ufeff` BOM，编译器报告"非法字符"

后续第一优先级是恢复可编译状态，然后修复 M1 的 P0 级缺陷，再做结构检查、信道、JEI、投影仪的实机验收。

## 总目标

本轮统一重构同时解决两类问题。

### 结构运行时问题

| 问题                          | 影响                 |
| --------------------------- | ------------------ |
| 已成形多方块仍依赖定时轮询               | 世界中多台机器同时存在时浪费 CPU |
| 每次结构检查可能遍历完整结构              | 大型多方块检查代价高         |
| `BlockPattern` 同时承载模板与运行时状态 | 相同机器无法真正共享结构模板     |
| 缺少区块级位置索引                   | 方块变化后无法快速定位受影响的多方块 |
| 缺少分片结构验证                    | 超大结构无法局部重检         |

### 结构定义与展示问题

| 问题                         | 影响                    |
| -------------------------- | --------------------- |
| 外壳与仓室数量手动声明                | 多方块定义冗长且容易出错          |
| tiered casing 没有统一信道语义     | 线圈、玻璃、机器外壳等 tier 选择分散 |
| JEI、投影仪、自动建造不共享同一份结构请求     | 玩家看到的结构和实际建造结构可能不一致   |
| 投影仪配置不是 per ItemStack NBT  | 多个投影仪或多人使用时容易串状态      |
| GT5 legacy channel key 未兼容 | 从 GT5 移植机器时语义容易丢失     |

## 参考来源

- GregTech CEu 1.12 当前实现：`FactoryBlockPattern`、`TraceabilityPredicate`
- GregTech Modern：事件驱动、异步检查、`MultiblockState`
- GT5 / StructureLib：`IStructureChannels`、`StructureWrapper`、`withChannel`、分片检查、NEI preview modifier

GT5 源码结论已经并入本文的“结构信道统一模型”和后续里程碑。原 `gt5-structure-channel-porting-plan.md` 仅保留为归档说明。

## 当前实现总览

| 模块         | 当前状态                                                                                  | 判断                                                       |
| ---------- | ------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| 事件驱动结构检查   | 已接入 `MultiblockWorldData`、Forge 事件、Mixin、controller 注册/注销                             | **名义完成但实际未生效** — 子类 override 绕过了新系统                      |
| 异步结构检查     | 已接入 `AsyncStructureChecker` 和 `BlockStateSnapshot`                                    | **存在严重并发安全问题**，需重写异步检查路径                                 |
| 模板/实例状态拆分  | 已有 `BlockPatternTemplate`、`MultiblockState`、兼容层 `BlockPattern`                        | 部分完成，模板共享收益未完全兑现                                         |
| 分片式结构检查    | 已有 `StructurePiece`、`MultiPiecePattern`、`OffsetMode`、`LazyTemplate`，Forge of Gods 已启用 | **首次实际使用者落地**，待实机验收                                      |
| 声明式 casing | 已有 `ICasing`、`ICasingGroup`、`DeclarativePatternBuilder`，多数机器已迁移                       | 大部分完成，剩余迁移与 tooltip 整合                                   |
| 结构信道       | 已有 `StructureChannel`、`GTStructureChannels`、`channelValues` 预览/建造雏形                   | 部分完成，缺 registry、legacy key、indicator、NBT 统一层             |
| JEI 信道预览   | 已有 `getSupportedChannels()` 与 `getMatchingShapes(channelValues)` 调用                   | 可用雏形，仍有硬编码范围与 metadata 缺口                                |
| 投影仪信道      | 行为类已有 `channelValues` 字段与 GUI 控件                                                      | 未完成，状态不是 per ItemStack NBT，renderer 未强制使用 channel values |

## 已落地的关键代码

### 运行时与调度

- `src/main/java/gregtech/api/metatileentity/multiblock/MultiblockControllerBase.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/MultiblockWorldData.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/AsyncStructureChecker.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/BlockStateSnapshot.java`
- `src/main/java/gregtech/common/event/BlockChangeListener.java`
- `src/main/java/gregtech/mixins/minecraft/WorldBlockStateMixin.java`
- `src/main/resources/mixins.gregtech.minecraft.json`

### Pattern 与状态

- `src/main/java/gregtech/api/pattern/BlockPattern.java`
- `src/main/java/gregtech/api/pattern/BlockPatternTemplate.java`
- `src/main/java/gregtech/api/pattern/MultiblockState.java`
- `src/main/java/gregtech/api/pattern/FactoryBlockPattern.java`
- `src/main/java/gregtech/api/pattern/TraceabilityPredicate.java`
- `src/main/java/gregtech/api/pattern/BlockWorldState.java`

### 分片结构

- `src/main/java/gregtech/api/pattern/StructurePiece.java`
- `src/main/java/gregtech/api/pattern/MultiPiecePattern.java`
- `src/main/java/gregtech/api/pattern/OffsetMode.java`
- `src/main/java/gregtech/api/pattern/LazyTemplate.java`

### 声明式 casing 与信道

- `src/main/java/gregtech/api/pattern/casing/ICasing.java`
- `src/main/java/gregtech/api/pattern/casing/ICasingGroup.java`
- `src/main/java/gregtech/api/pattern/casing/CasingDefinition.java`
- `src/main/java/gregtech/api/pattern/casing/DeclarativePatternBuilder.java`
- `src/main/java/gregtech/api/pattern/casing/GTCasingGroups.java`
- `src/main/java/gregtech/api/pattern/casing/GTStructureChannels.java`
- `src/main/java/gregtech/api/pattern/casing/StructureChannel.java`
- `src/main/java/gregtech/api/pattern/casing/StructureTooltipBuilder.java`
- `src/main/java/gregtech/api/util/CasingTier.java`
- `src/main/java/gregtech/api/util/GlassTier.java`

### 消费端

- `src/main/java/gregtech/integration/jei/multiblock/MultiblockInfoRecipeWrapper.java`
- `src/main/java/gregtech/common/items/behaviors/StructureProjectorBehavior.java`
- `src/main/java/gregtech/common/items/behaviors/MultiblockBuilderBehavior.java`
- `src/main/java/gregtech/client/renderer/handler/MultiblockPreviewRenderer.java`

## 统一架构方向

后续所有结构预览、投影、对比和自动建造都应围绕同一个请求对象或等价数据流执行：

```text
StructureProjectionRequest
  controller
  triggerStack / projectorStack
  channelValues
  compareMode
  targetPos
  layer
  hatchPlacementMode
```

推荐数据流：

```text
ItemStack NBT
  -> StructureChannelValues
  -> controller.getMatchingShapes(channelValues)
  -> MultiblockPreviewRenderer
  -> compare / material list / autoBuild
```

核心原则：

- JEI 预览、投影仪预览、投影仪 compare、自动建造必须使用同一份 `channelValues`。
- 多方块结构定义中的 tiered casing、尺寸、hatch 放置都应能通过 `StructureChannel` 表达。
- GT5 legacy key 必须可解析，避免移植机器时出现 `coil`、`height`、`length`、`gt_hatch` 等 key 语义丢失。

## 结构信道统一模型

GT5 的 `IStructureChannels` 同时承担四件事：

- 定义 channel key 与默认 tooltip。
- 把 channel 包到结构元素上。
- 从触发 `ItemStack` 读取 channel 值。
- 注册 indicator item，表示某个物品对应某个 channel value。

当前工程已有 `StructureChannel` 与 `GTStructureChannels` 雏形，但还缺少统一 trigger 数据、registry metadata 和 legacy key alias。

### 需要补齐的 API

#### `StructureChannel`

保留当前接口，但补充或通过 companion registry 提供：

- 当前工程内部稳定 id，例如 `heating_coil`。
- GT5 legacy key，例如 `coil`。
- 默认 tooltip。
- 默认值、最小值、最大值。
- trigger 语义的 `getValueClamped(raw, min, max)`。
- matched context 语义的 tier 读取 helper。

注意：GT5 的 trigger 语义是 `raw + min - 1`，而当前 `PatternMatchContext` 中记录的通常是已检测 tier。这两种语义不能混用同一个隐式方法。

#### `StructureChannelRegistry`

新增注册表，职责：

- 注册 channel id 与 legacy key。
- 支持按 id 或 legacy key 查找。
- 保存显示名、tooltip、范围、默认值。
- 保存 indicator `ItemStack -> value`。
- 给 JEI 和投影仪提供 UI metadata。
- 给 addon 机器提供兼容入口。

#### `StructureChannelValues`

新增值对象或工具类，统一三种数据形态：

- `ItemStack` NBT：投影仪/触发物品持久化。
- `Map<String, Integer>`：当前 preview / autoBuild API。
- `PatternMatchContext`：结构成形后的实际 tier。

建议 NBT：

```text
GT.StructureChannels: {
  coil: 3,
  glass: 2,
  height: 12,
  length: 16,
  gt_hatch: 1
}
```

读写时同时支持当前 id 与 GT5 legacy key。

### GT5 重点信道映射

| GT5 key          | 当前建议 id                      | 用途                       |
| ---------------- | ---------------------------- | ------------------------ |
| `coil`           | `heating_coil`               | 加热线圈 tier                |
| `glass`          | `borosilicate_glass`         | 玻璃 tier                  |
| `machine_casing` | `machine_casing`             | 机器外壳 tier                |
| `casing`         | `solid_casing` / alias group | 多类外壳 tier，需 legacy alias |
| `height`         | `structure_height`           | 可变高度                     |
| `length`         | `structure_length`           | 可变长度                     |
| `pipe`           | `pipe_casing`                | 管道外壳 tier                |
| `item_pipe`      | `item_pipe_casing`           | 物品管道外壳 tier              |
| `solenoid`       | `solenoid`                   | 螺线管 tier                 |
| `capacitor`      | `battery` 或 `capacitor`      | 电容/储能元件 tier             |
| `gt_hatch`       | `hatch` / `no_hatch` 转换层     | survival 自动放置 hatch      |

当前 `GTStructureChannels.NO_HATCH` 与 GT5 `HATCH` 的语义需要明确对齐。GT5 是“设置 hatch channel 后允许放置非 exclusive hatch”，而当前注释更接近“skip hatch”。这块必须在转换层显式处理，避免 UI 语义反转。

## 统一里程碑

### M0：恢复编译

目标：让 `compileJava` 通过，拿到真实代码错误列表。

任务：

1. 移除 `ForgeOfGodsStructureString.java` 文件开头 BOM。
2. 重新运行：
   ```powershell
   ./gradlew --% compileJava --no-daemon -Dorg.gradle.workers.max=1 -Dorg.gradle.compiler.daemon=false
   ```
3. 修复后续 Java 编译错误。

验收：

- `compileJava` 成功。
- 不再依赖 Gradle Worker Daemon 异常判断项目状态。

### M1：稳定事件驱动与异步检查

目标：保证结构检查调度不会破坏现有多方块行为。

当前已完成：

- 已成形多方块注册到 `MultiblockWorldData`。
- 方块变化通过 Forge 事件和 Mixin 通知。
- 未成形控制器进入 `AsyncStructureChecker`。
- 异步线程通过 `BlockStateSnapshot` 预检查，主线程确认成形。

#### 已发现的架构缺陷

##### 缺陷 1：子类 override `doStructureCheck()` 完全绕过事件驱动系统（P0 严重）

`RecipeMapMultiblockController` 和 `AdvanceRecipeMapMultiblockController` 各自完全 override 了
`doStructureCheck()`，直接走旧的 20 tick 轮询模式，不调用 `super.doStructureCheck()`。

影响：95% 以上的实际多方块（所有 `RecipeMapMultiblockController` 子类）完全不享受事件驱动检查，
`MultiblockWorldData` 即使注册了这些 controller 也不会被查询到。

修复方案：删除子类 override，统一由 `MultiblockControllerBase.doStructureCheck()` 管理。
如果需要 "工作时降低检查频率" 的语义，应在基类 fallback polling 分支中通过可 override 的
`getStructureCheckInterval()` 方法统一处理，而不是让子类完全绕过新系统。

涉及文件：

- `RecipeMapMultiblockController.java` — 删除 `doStructureCheck()` override
- `AdvanceRecipeMapMultiblockController.java` — 删除 `doStructureCheck()` override
- `MultiblockControllerBase.java` — fallback 分支补充 `getStructureCheckInterval()` 钩子

##### 缺陷 2：`AsyncStructureChecker.performAsyncCheck` 从异步线程直接读写 controller 的共享 `MultiblockState`（P0 严重）

```java
private boolean performAsyncCheck(@NotNull SnapshotTask task) {
    BlockPattern structurePattern = task.controller.structurePattern;  // 异步线程直接读
    PatternMatchContext context = structurePattern.getState().checkPatternFastAtSnapshot(...);
    // ↑ 写入 matchContext, globalCount, layerCount, worldState 等可变状态
}
```

`task.controller.structurePattern` 没有 volatile/锁保护。`MultiblockState` 内的 `matchContext`、
`globalCount`、`layerCount`、`worldState`、`cache` 是可变共享状态。如果异步线程正在执行
`checkPatternAtSnapshot`（写 `matchContext.reset()` / `globalCount.clear()`），同时主线程的
`checkStructurePattern()` 也在操作同一个 state — 产生 data race。

`MultiblockState` 虽然有 `ReentrantLock`，但 `performAsyncCheck` 和主线程 `checkStructurePattern`
都没有调用 `lock()`。

修复方案（二选一）：

- **方案 A（推荐）**: 异步检查使用独立的临时 `MultiblockState`（`template.createState()`），
  不共享 controller 主 state。匹配成功后只传回 boolean 结果，由主线程确认时再用主 state 重检。
- **方案 B**: 在 `performAsyncCheck` 中使用 `state.tryLock()`，抢不到锁就跳过本次检查。

##### 缺陷 3：`MultiblockWorldData.INSTANCES` 使用 `WeakHashMap` 非线程安全（P1 中等）

```java
private static final Map<World, MultiblockWorldData> INSTANCES = new WeakHashMap<>();
```

`WeakHashMap` 不是线程安全的。`BlockChangeListener` 的 Forge 事件可能在非主线程触发
（某些模组的异步区块加载）。`get()` 使用 `computeIfAbsent` 读写同一个 `WeakHashMap`，
如果两个线程并发调用会导致 `ConcurrentModificationException`。

修复方案：使用 `Collections.synchronizedMap(new WeakHashMap<>())` 或将 `get/remove` 方法加
`synchronized`。由于 WorldData 本身内部已用 `ConcurrentHashMap`，只需保护外层 `INSTANCES` 访问。

##### 缺陷 4：`SNAPSHOT_RADIUS = 32` 对大型结构不够用，对小型结构浪费（P1 中等）

立方体 `(2*32+1)^3 = 274,625` 个位置全部遍历。对蒸馏塔（14 高）、EBF（3×3×5）等小结构是
巨大浪费；对 Forge of Gods（127×29×127）完全不够用。

修复方案：snapshot 范围应来自 `structurePattern.getTemplate()` 的尺寸信息。使用
`BlockStateSnapshot.captureRegion(world, min, max)` 替代 `capture(center, radius)`，
按结构 AABB 计算 min/max。

任务：

1. **删除** **`RecipeMapMultiblockController`** **和** **`AdvanceRecipeMapMultiblockController`** **中的** **`doStructureCheck()`** **override。**
2. **在** **`MultiblockControllerBase.doStructureCheck()`** **的 fallback polling 分支增加** **`getStructureCheckInterval()`** **钩子，**
   允许子类控制轮询间隔（默认 20 tick，工作时可更长），但不允许绕过整个事件驱动/异步路径。
3. **`AsyncStructureChecker.performAsyncCheck`** **改为使用临时** **`MultiblockState`。**
   创建方式：`task.controller.structurePattern.getTemplate().createState()`。
   异步线程写入临时 state，匹配成功只传回 boolean，主线程再用主 state 做确认检查。
4. **`MultiblockWorldData.INSTANCES`** **改为** **`Collections.synchronizedMap(new WeakHashMap<>())`。**
5. 调整 snapshot 范围策略：从 template 计算结构 AABB，使用 `captureRegion` 或 `capturePositions`。
6. 审查世界卸载、控制器移除、结构失效时的清理路径。
7. 增加配置开关与 debug 统计。

验收：

- 普通多方块成形/破坏行为与旧版本一致。
- **所有** **`RecipeMapMultiblockController`** **子类都走事件驱动路径**（不再有子类绕过）。
- 已成形多方块无方块变化时不主动完整轮询。
- 多台未成形控制器不会造成主线程明显卡顿。
- **异步检查不存在共享** **`MultiblockState`** **的 data race。**
- 世界卸载后无旧 world/controller 引用残留。

### M2：模板共享真正落地

目标：让同类型机器共享 `BlockPatternTemplate`，每台机器只持有自己的 `MultiblockState`。

当前已完成：

- `BlockPatternTemplate` 存在。
- `MultiblockState` 存在。
- `BlockPattern` 已作为兼容层组合 template/state。
- `FactoryBlockPattern#buildTemplate()` 已存在。

尚未完成：

- `createStructurePattern()` 仍返回兼容层 `BlockPattern`。
- 多数机器仍按实例构建完整 pattern。
- 未形成大规模静态 template 缓存。

#### 已发现的架构缺陷

##### 缺陷 5：`BlockPattern` 兼容层过渡策略未明确定义（P2 设计层面）

当前 `BlockPattern` 同时扮演旧 API 兼容和新系统入口。`checkStructurePattern()` 的调用链：

```text
controller.structurePattern (BlockPattern)
  → .checkPatternFastAt() 代理到内部 state.checkPatternFastAt()
  → state 写 cache / matchContext
```

但计划书只描述了 `BlockPatternTemplate` + `MultiblockState` 的目标拆分，没有明确说明
`BlockPattern` 何时删除、如何逐步废弃。这会导致 contributor 不清楚该使用哪个 API。

过渡策略：

1. M2 阶段引入 `createStructureTemplate()` 作为新入口，默认从 `createStructurePattern()` 兼容。
2. 标记 `createStructurePattern()` 为 `@Deprecated`，但保留兼容行为。
3. `BlockPattern` 保留至 M8 收尾阶段，作为向下兼容的 facade。
4. M8 阶段如果附属全部迁移完成，可将 `BlockPattern` 标记为 `@ApiStatus.ScheduledForRemoval`。

任务：

1. 在 `MultiblockControllerBase` 中引入模板优先 API：
   ```java
   protected BlockPatternTemplate createStructureTemplate()
   ```
2. 默认从旧 `createStructurePattern()` 兼容。
3. 将核心机器逐步迁移到静态 `BlockPatternTemplate`。
4. 将直接访问 `structurePattern.cache`、`formedRepetitionCount`、`aisleRepetitions` 的路径迁到 template/state getter。
5. 保留 `BlockPattern` 兼容层，给附属留迁移窗口。
6. 明确标记 `createStructurePattern()` 为 `@Deprecated`，javadoc 指向 `createStructureTemplate()`。

验收：

- 同类型多台机器共享同一份 template。
- JEI、投影仪、自动建造、拆除结构均能从 template/state 正确读取。
- 旧附属只 override `createStructurePattern()` 时仍能工作。
- `BlockPattern` 的过渡生命周期有明确文档化的 deprecation 路径。

### M3：结构信道 registry 与值模型

目标：补齐 GT5 `IStructureChannels` 的当前工程等价层。

任务：

1. 扩展 `GTStructureChannels`，补齐 GT5 常用信道。
2. 新增 `StructureChannelRegistry`。
3. 新增 `StructureChannelValues`。
4. 增加 legacy key alias，支持 `coil`、`height`、`length`、`gt_hatch` 等 GT5 key。
5. 增加 indicator item 注册与查询。
6. 明确 `HATCH` / `NO_HATCH` 语义转换。

验收：

- 可通过当前 id 和 GT5 legacy key 查到同一 channel。
- indicator item 可注册、查询、展示。
- `StructureChannelValues` 可在 ItemStack NBT、Map、PatternMatchContext 之间转换。
- 不改变现有机器默认行为。

### M4：多方块结构定义消费信道

目标：让多方块定义、结构检查、预览候选和成形后的 tier 数据一致。

当前已完成：

- `DeclarativePatternBuilder#tieredCasing(...).withChannel(...)` 已存在。
- `TraceabilityPredicate.SimplePredicate#channelName` 已被 preview / autoBuild 使用。
- 多个线圈机器已声明 `HEATING_COIL` channel。
- `getSupportedChannels()` 已在部分机器中 override。

任务：

1. 统一 `DeclarativePatternBuilder`、`TraceabilityPredicate`、`GTCasingGroups` 的 channel key 处理。
2. 结构检查成功后，把实际 casing tier 写入 `PatternMatchContext` 和 controller 可读状态。
3. 让 `getSupportedChannels()` 优先从结构定义自动收集；不能自动收集的机器手动声明。
4. 对可变尺寸结构统一使用 `STRUCTURE_HEIGHT` / `STRUCTURE_LENGTH`。
5. 迁移样例机器：
   - Electric Blast Furnace：`coil`
   - Cracking Unit：`coil`
   - Pyrolyse Oven：`coil`
   - Multi Alloy Furnace：`coil`
   - Multi Smelter：`coil`
   - Distillation Tower：`height`
   - Assembly Line：`length`

验收：

- EBF 选择不同 coil 时，预览、材料、自动建造、成形后热量来源一致。
- 蒸馏塔选择不同 height 时，预览层数、材料、输出层一致。
- 装配线选择不同 length 时，重复段数量和末端输出段一致。

### M5：JEI 信道 parity

目标：当前 JEI 多方块预览达到 GT5 NEI 信道预览的功能等价。

当前已完成：

- `MultiblockInfoRecipeWrapper` 已读取 `controller.getSupportedChannels()`。
- 已能调用 `controller.getMatchingShapes(channelValues)`。
- 已有 `channelValues` 状态和调节逻辑雏形。

尚未完成：

- 范围仍偏硬编码。
- UI metadata 不来自 registry。
- 缓存 key 需要包含 channel map。
- 材料列表需要确认完全随 channel 重算。
- tooltip 未统一显示 channel usage。

任务：

1. JEI 从 `StructureChannelRegistry` 获取 label、tooltip、range、indicator。
2. 移除硬编码 `0..5`。
3. 预览缓存 key 纳入排序后的 `channelValues`。
4. 材料列表从调节后的 shape 重新生成。
5. tooltip 增加 `addSubChannelUsage` 等价展示。
6. advanced tooltip 可显示 legacy key，便于调试移植。

验收：

- JEI 中调 EBF coil，3D 预览和材料列表同步变化。
- JEI 中调蒸馏塔 height，预览高度和材料数量同步变化。
- JEI 中显示可读 channel label，而不是只暴露 raw key。

### M6：投影仪 parity

目标：投影仪成为当前项目的 GT5 trigger item 等价物。

当前状态：

- `StructureProjectorBehavior` 已有 `channelValues` 字段。
- GUI 已有高度/长度等控件雏形。
- 但这些字段属于行为实例，不是 per ItemStack NBT。
- `MultiblockPreviewRenderer` 多处仍调用 `controller.getMatchingShapes()`，未强制传 channel values。

任务：

1. 将投影仪 channel、compare mode、hatch mode 全部迁移到 `ItemStack` NBT。
2. 使用 `StructureChannelValues.read/write(ItemStack)`。
3. GUI 根据 controller 支持的 channels 和 registry metadata 自动生成控件。
4. 新增 channel-aware renderer 入口：
   ```java
   renderMultiBlockPreview(controller, duration, channelValues)
   ```
5. compare、preview、autoBuild 共用同一份 `StructureProjectionRequest`。
6. tooltip 显示当前投影仪配置的关键 channel 值。

验收：

- 两个投影仪物品保存不同信道配置，互不影响。
- 关闭 GUI、丢地上、重进世界后配置仍在。
- 投影仪选择 `coil=4` 后，预览、compare、自动建造均使用同一 coil tier。
- compare mode 对 `height=12` 的蒸馏塔只比较 12 层结构。

### M7：分片式结构检查试点

目标：让 P3 从 API 进入实际机器，优先服务超大结构。

当前已完成：

- `StructurePiece`、`MultiPiecePattern` 已存在。
- `createMultiPiecePattern()` 已作为 opt-in 入口。
- `MultiblockWorldData` 可按方块位置标记 dirty piece。
- `OffsetMode` enum 已实现（ABSOLUTE / RELATIVE / HORIZONTAL\_RELATIVE）。
- `MultiPiecePattern.Builder` 支持带 `OffsetMode` 的 `piece()` / `conditionalPiece()` 重载。
- `BlockPatternTemplate` 支持外部 `externalCenterOffset` 构造函数。
- `FactoryBlockPattern.buildTemplate(int[] centerOffset)` 已实现。
- `LazyTemplate` 标准化模板缓存方案已实现（DCL + volatile，零锁开销）。
- `MetaTileEntityForgeOfGods` 已实现 `createMultiPiecePattern()`（4 分片 + 条件片段）。
- `OffsetMode.RELATIVE` 语义修正：正确使用 `frontFacing` = into-structure direction。
- 首次成形后调用 `multiPiecePattern.checkAllPieces()` 并注册到 WorldData（在 `checkStructurePattern` 中）。
- `StructurePiece.positions` 已使用 volatile reference + swap 策略（线程安全）。

尚未完成：

- 实机验收（需要实际放置 Forge of Gods 结构测试各朝向下分片检查）。
- 压力测试（局部 dirty piece 重检耗时）。

#### 架构限制（通过 Forge of Gods 试点发现）— 已解决

1. **~~`MultiPiecePattern`~~~~的 offset 不支持方向旋转~~**  → 已实现 `OffsetMode.RELATIVE`
2. **~~`BlockPatternTemplate`~~~~不支持外部 centerOffset~~**  → 已实现 `externalCenterOffset` 构造函数
3. **~~Template 缓存没有标准化方案~~** → 已实现 `LazyTemplate`
4. **~~缺少 structurelib 的"虚拟 center"概念~~** → 通过 `externalCenterOffset` + piece offset 组合解决

#### 架构缺陷（代码审查发现）

##### 缺陷 6：MultiPiecePattern 首次成形流程缺失（P0 严重）— **已修复**

已在 `MultiblockControllerBase.checkStructurePattern()` 成功后增加 multi-piece 初始检查：

```java
if (multiPiecePattern != null) {
    multiPiecePattern.checkAllPieces(getWorld(), getPos(),
            getFrontFacing().getOpposite(), getUpwardsFacing(), allowsFlip());
    registerMultiPiecePattern();
}
```

##### 缺陷 7：`StructurePiece.positions` 并发访问风险（P1 中等）— **已修复**

已使用 volatile reference + swap 策略。`StructurePiece.positions` 声明为 `volatile LongSet`，
更新时调用 `swapPositions(newPositions)` 原子替换引用。`MultiPiecePattern.checkDirtyPieces()` 中
构建新 set 后 swap：

```java
LongSet newPositions = new LongOpenHashSet(piece.getState().cache.keySet());
piece.swapPositions(newPositions);
```

##### 缺陷 8：`structurePattern` 和 `multiPiecePattern` 的 positions 注册可能冲突（P2 设计层面）

`checkStructurePattern()` 成功后注册的是 `structurePattern.getState().cache.keySet()`。
`checkMultiPieceStructure()` 成功后注册的是 `multiPiecePattern.getAllPositions()`。

如果 controller 同时有两者，且都被调用（如首次 tick），可能重复注册或数据不一致。

修复方案：明确职责边界：

- 如果 `multiPiecePattern != null`，`checkStructurePattern()` 只负责 base pattern（第一个 piece）的成形判断，
  不注册 positions 到 WorldData。
- multi-piece 成形后由 `registerMultiPiecePattern()` 统一注册所有 piece 的合并 positions。
- 或者：如果使用 multi-piece 模式，`structurePattern` 应只是第一个 piece 的等价物，
  整体注册由 multi-piece 管理。

#### API 改动方案

##### 改动1：`StructurePiece` 支持方向感知的 offset

推荐方案：在 `MultiPiecePattern.Builder` 中声明 offset 语义。

```java
MultiPiecePattern.builder()
    .offsetMode(OffsetMode.STRUCTURE_SPACE)  // or WORLD_ABSOLUTE (default, backward compat)
    .piece("beam_shaft", template, new Vec3i(0, 0, 0))
    .piece("first_ring", template, new Vec3i(0, 0, 59))  // FRONT +59
    .build();
```

运行时在 `checkDirtyPieces` 中根据 `offsetMode` 决定是否旋转：

```java
private static BlockPos computeRotatedCenter(Vec3i offset, BlockPos controllerPos,
        EnumFacing frontFacing, EnumFacing upwardsFacing, boolean isFlipped) {
    if (offset.getX() == 0 && offset.getY() == 0 && offset.getZ() == 0) {
        return controllerPos;
    }
    // offset = (RIGHT, UP, FRONT) → convert to offsetPos(UP, LEFT, FRONT)
    return RelativeDirection.offsetPos(controllerPos, frontFacing, upwardsFacing, isFlipped,
            offset.getY(), -offset.getX(), offset.getZ());
}
```

##### 改动2：`BlockPatternTemplate` 支持外部 centerOffset

添加新构造函数接受显式 `int[] centerOffset`：

```java
public BlockPatternTemplate(TraceabilityPredicate[][][] predicatesIn,
                            RelativeDirection[] structureDir,
                            int[][] aisleRepetitions,
                            int[] centerOffset) {
    // ... 初始化字段 ...
    this.centerOffset = centerOffset;  // 跳过 initializeCenterOffsets()
}
```

配套修改：`FactoryBlockPattern.buildTemplate(int[] centerOffset)` 方法。

##### 改动3：Template 缓存

推荐方案：控制器层面的 static volatile + DCL（简单直接，只有极少数超大结构需要）。

#### Forge of Gods 偏移计算参考

GT5 structurelib 的 `checkPiece(pieceName, ox, oy, oz)` 语义：

- "模板中坐标 (ox, oy, oz) 对应控制器在世界中的位置"
- ox=RIGHT 方向偏移, oy=UP 方向偏移, oz=FRONT(aisle) 方向偏移

各 piece 的具体值：

| Piece        | GT5 checkPiece offset | template centerOffset              | piece offset (FRONT) |
| ------------ | --------------------- | ---------------------------------- | -------------------- |
| beam\_shaft  | (63, 14, 1)           | \[63, 14, 1, 1, 1] (auto from 'S') | (0, 0, 0)            |
| first\_ring  | (63, 14, -59)         | \[63, 14, 0, 0, 0] (explicit)      | (0, 0, 59)           |
| second\_ring | (55, 11, -67)         | \[55, 11, 0, 0, 0] (explicit)      | (0, 0, 67)           |
| third\_ring  | (47, 13, -76)         | \[47, 13, 0, 0, 0] (explicit)      | (0, 0, 76)           |

推导公式：`piece offset z = -GT5_oz + template_center_z`

#### Forge of Gods 最终实现方式

```java
@Override
protected MultiPiecePattern createMultiPiecePattern() {
    return MultiPiecePattern.builder()
            .offsetMode(OffsetMode.STRUCTURE_SPACE)
            .piece("beam_shaft", getBeamShaftTemplate(), Vec3i.NULL_VECTOR)
            .piece("first_ring", getFirstRingTemplate(), new Vec3i(0, 0, 59))
            .conditionalPiece("second_ring", getSecondRingTemplate(), new Vec3i(0, 0, 67),
                    () -> data.isUpgradeActive(ForgeOfGodsUpgrade.CD))
            .conditionalPiece("third_ring", getThirdRingTemplate(), new Vec3i(0, 0, 76),
                    () -> data.isUpgradeActive(ForgeOfGodsUpgrade.END))
            .build();
}
```

#### 任务

1. **修复首次成形流程**：在 `checkStructurePattern()` 成功后，如果 `multiPiecePattern != null`，
   调用 `multiPiecePattern.checkAllPieces()` 并通过 `registerMultiPiecePattern()` 注册。
2. **修复** **`StructurePiece.positions`** **并发问题**：改用 volatile reference + swap 策略。
3. **明确** **`structurePattern`** **与** **`multiPiecePattern`** **的 positions 注册职责边界。**
4. 实现 `OffsetMode` enum 和 `MultiPiecePattern.Builder.offsetMode()` 方法。
5. 修改 `MultiPiecePattern.checkDirtyPieces()` 根据 offsetMode 旋转偏移。
6. 添加 `BlockPatternTemplate` 的外部 centerOffset 构造函数。
7. 添加 `FactoryBlockPattern.buildTemplate(int[] centerOffset)`。
8. 在 `MetaTileEntityForgeOfGods` 中实现 `createMultiPiecePattern()` 使用新 API。
9. 实现 static template 缓存（DCL 模式）。
10. 验证各朝向下结构检查的正确性。
11. 测试局部 dirty piece 重检。

#### 影响范围

| 文件                               | 改动                                                                   | 影响                        |
| -------------------------------- | -------------------------------------------------------------------- | ------------------------- |
| `MultiblockControllerBase.java`  | 首次成形后补充 multi-piece 初始检查 + positions 注册职责边界                          | 需验证与单 pattern 模式兼容        |
| `StructurePiece.java`            | `positions` 改为 volatile reference + swap                             | 并发安全修复                    |
| `MultiPiecePattern.java`         | 添加 `OffsetMode` + 旋转逻辑 + `markDirtyByPosition` 适配 volatile positions | 默认 `WORLD_ABSOLUTE` 保持旧行为 |
| `MultiPiecePattern.Builder`      | 添加 `.offsetMode()`                                                   | 新代码                       |
| `BlockPatternTemplate.java`      | 新增构造函数                                                               | 无影响（新增）                   |
| `FactoryBlockPattern.java`       | 新增 `buildTemplate(int[])`                                            | 无影响（新增）                   |
| `MetaTileEntityForgeOfGods.java` | 实现 `createMultiPiecePattern()`                                       | 控制器层面                     |

验收：

- 至少一个实际机器启用 `MultiPiecePattern`。
- 修改单个片段只重检该片段。
- inactive conditional piece 不影响已成形状态。
- piece 失效能导致整个多方块正确失效。
- 不同控制器朝向下结构检查结果正确。

### M8：迁移收尾与清理

目标：把旧结构定义和临时信道逻辑收束到统一体系。

当前迁移情况：

- 多方块中约 29 处使用 `DeclarativePatternBuilder.start()`。
- 仍有约 8 处使用 `FactoryBlockPattern.start()`，均已标注保留原因：
  - `MetaTileEntityCleanroom` — 动态结构（运行时根据尺寸生成 aisle）
  - `MetaTileEntityFusionReactor` — 复杂 tier 依赖 + 环形结构
  - `MetaTileEntityCentralMonitor` — 动态宽度 + 非标方向
  - `MetaTileEntityCharcoalPileIgniter` — 动态结构
  - `MetaTileEntityForgeOfGods` — 首次成形配合 multi-piece
  - `MTEBaseModule` — 抽象 coil predicate
  - `MetaTileEntityHugeTransformer` — 极简结构无 casing
  - `MetaTileEntityLogisticsMaterialDistributor` — 非标方向 + 低迁移价值

已完成：

1. ✅ 所有保留 FactoryBlockPattern 的机器已添加注释说明保留原因。
2. ✅ `StructureTooltipBuilder` 已接入 `DeclarativePatternBuilder` tooltip 自动生成管线：
   - `DeclarativePatternBuilder.build()` / `buildTemplate()` 自动计算结构描述
   - 描述数据以 server-safe 格式存储在 `BlockPatternTemplate.structureDescription` 中
   - `MultiblockControllerBase.addInformation()` 自动渲染结构描述 tooltip
3. ✅ 已添加 `gregtech.multiblock.ability.*` 国际化键值（en_us + zh_cn）。
4. ✅ 确认已迁移机器中无冗余 `setMinGlobalLimited`（均为有意设定）。
5. ⬜ GT5 structure channel 逐台映射：待后续 M3/M4 里程碑完善 registry 后执行。
6. ⬜ addon 迁移说明：待 M8 所有子任务完成后撰写。

验收：

- 常规多方块基本使用声明式 casing。
- tooltip、JEI、投影仪显示一致。
- GT5 中依赖 structure channel 的机器都有当前工程对应声明或明确 TODO。

## 测试计划

### 编译测试

必须通过：

```powershell
./gradlew --% compileJava --no-daemon -Dorg.gradle.workers.max=1 -Dorg.gradle.compiler.daemon=false
```

建议追加：

```powershell
./gradlew processResources
./gradlew reobfJar
```

### 实机功能测试

每轮至少覆盖：

- 普通电力多方块成形与破坏。正常
- Steam 多方块成形与破坏。正常
- 带线圈机器成形与 tier 读取。正常
- 带多个 hatch 的机器成形。有这种多方块吗？
- JEI 结构预览。鼠标选择后的循环展示与左侧物品栏重叠，左侧物品显示与gt5的区别较大
- 投影仪预览、compare、自动建造。正常
- 控制器旋转、上下朝向、翻转。正常
- 世界保存、退出、重进。正常
- 多台机器同时存在的 tick 表现。正常
蒸馏塔，装配线，pss搭建好了都不成形要退出世界重进才会成形
### 信道专项测试

- EBF：JEI 和投影仪选择不同 `coil`，预览、材料、自动建造、成型热量一致。不正常，投影仪读取信道逻辑有问题，线圈设置的值无效。清空信道按键没有效果
- Distillation Tower：`height=3` 与 `height=12` 的预览层数、输出 hatch 层数、材料数量一致。预览层数不对，但是构建层数是正常的。
- Assembly Line：`length=5` 与 `length=16` 的重复段和末端输出段位置一致。预览层数不对，但是构建层数是正常的。
- Hatch：确认当前 `NO_HATCH` 与 GT5 `gt_hatch` 的转换后，测试 survival 自动建造 hatch 行为。开始NO_HATCH后会空缺一个位置不放置方块。
- Indicator：注册过的线圈、玻璃、管道 casing 能在 JEI/投影仪中作为对应信道值展示。线圈没注册全？

### 压力测试

- 100 台已成形小型多方块，无方块变化时观察 tick 成本。
- 100 台未成形控制器，观察异步检查对主线程影响。
- 跨多个 chunk 的大型结构，破坏不同 chunk 中的内部方块。
- 大型结构启用分片后，破坏不同片段并记录重检耗时。

## 风险清单

| #  | 风险                                                         | 严重程度          | 当前缓解方式                                      | 后续处理                                 | 里程碑 |
| -- | ---------------------------------------------------------- | ------------- | ------------------------------------------- | ------------------------------------ | --- |
| 1  | 子类 override `doStructureCheck()` 绕过事件驱动系统                  | **P0 严重**     | 无 — 95% 多方块实际未走新路径                          | 删除子类 override，基类统一调度                 | M1  |
| 2  | 异步检查从异步线程读写 controller 共享 `MultiblockState` (data race)    | **P0 严重**     | 主线程确认检查兜底（但已经污染了 state）                     | 使用临时 state（`template.createState()`） | M1  |
| 3  | MultiPiecePattern 首次成形流程缺失                                 | **P0 严重**     | 无 — multi-piece 从未执行初始检查                    | `formStructure` 后补充 `checkAllPieces` | M7  |
| 4  | 编译失败                                                       | 高             | 已定位到 BOM 阻塞点                                | M0 优先修复                              | M0  |
| 5  | `MultiblockWorldData.INSTANCES` WeakHashMap 非线程安全          | P1 中高         | 当前仅主线程访问时碰巧安全                               | 改用 synchronizedMap 包装                | M1  |
| 6  | `StructurePiece.positions` 并发访问 (clear/addAll vs contains) | P1 中高         | 漏标 dirty 仅在极窄窗口触发                           | volatile reference + swap 策略         | M7  |
| 7  | snapshot 范围不足 (固定半径 32)                                    | P1 中高         | 小结构浪费大结构不够                                  | 改为按结构 AABB captureRegion             | M1  |
| 8  | `structurePattern` 与 `multiPiecePattern` positions 注册冲突    | P2 中          | 当前无机器同时启用两者                                 | 明确注册职责边界                             | M7  |
| 9  | `BlockPattern` 兼容层过渡策略未定义                                  | P2 中          | 已有 template/state 结构                        | 引入 deprecation 路径                    | M2  |
| 10 | P1 未真正共享模板                                                 | 中             | 已有 template/state 结构                        | 引入模板优先 API 与静态模板                     | M2  |
| 11 | GT5 legacy key 缺失                                          | 中             | 当前有部分 channel enum                          | 新增 registry 与 alias                  | M3  |
| 12 | `HATCH` / `NO_HATCH` 语义反转                                  | 中             | 暂无统一转换层                                     | 在 `StructureChannelValues` 中显式转换     | M3  |
| 13 | JEI channel 范围硬编码                                          | 中             | 当前已有 UI 雏形                                  | 改用 registry metadata                 | M5  |
| 14 | 投影仪状态非 per ItemStack                                       | 中高            | 行为字段暂存                                      | 迁移到 ItemStack NBT                    | M6  |
| 15 | 分片 API 无实际使用者                                              | ~~中~~ **已解决** | Forge of Gods 已实现 createMultiPiecePattern() | 待实机验收                                | M7  |
| 16 | 自动 tooltip 未统一                                             | ~~低~~ **已解决** | DeclarativePatternBuilder 自动生成并注入 tooltip    | 待实机验收                                | M8  |

## 完成定义

只有满足以下条件，统一重构才能标记为完成：

1. `compileJava` 通过。
2. P0 事件驱动结构检查通过实机验收，**所有 controller 子类统一走新调度路径**。
3. P1 同类型机器模板共享实际落地。
4. P2 异步检查不存在共享状态并发写入风险（**使用临时 state**）。
5. P3 至少有一个实际超大结构启用分片检查，**首次成形流程正确**。
6. P4 剩余旧 builder 机器已迁移或明确保留原因。
7. `StructureChannelRegistry`、legacy key alias、indicator item、`StructureChannelValues` 完成。
8. JEI、投影仪、compare、autoBuild 使用同一份 channel values。
9. 投影仪配置持久化到 ItemStack NBT。
10. GT5 关键样例机器的 channel 行为完成 parity 验收。
11. 世界卸载、控制器移除、结构失效不会留下注册残留。
12. **`BlockPattern`** **兼容层有明确的 deprecation 路径和文档。**

## 当前下一步

立即执行 M0：

1. 修复 `ForgeOfGodsStructureString.java` 的 BOM。
2. 重新跑 `compileJava`。
3. 修复新的编译错误。
4. 编译通过后，优先进入 M1。

推荐顺序：

```text
M0 编译
  -> M1 稳定结构检查（含 P0 缺陷修复）
  -> M2 模板共享（含 BlockPattern 过渡策略）
  -> M3 统一信道 registry / values
  -> M4 多方块结构定义消费信道
  -> M5 JEI parity
  -> M6 投影仪 NBT 与 renderer
  -> M7 分片试点（含首次成形流程 + positions 并发修复）
  -> M8 迁移收尾
```

### 里程碑依赖关系

```text
M0 ─→ M1 ─→ M2 ─→ M7
       │           ↑
       └─→ M3 ─→ M4 ─→ M5
                        ↓
                       M6
                        ↓
                       M8
```

硬依赖：

- M1 阻塞所有后续里程碑（如果事件驱动/异步都有 race condition，后续改动无法验证）
- M2 阻塞 M7（M7 的 template 缓存依赖 M2 的模板共享 API）
- M3 阻塞 M4、M5、M6（信道 registry 是预览/投影/建造的基础）

可并行：

- M1 完成后，M2 和 M3 可并行推进
- M5 和 M6 之间无强依赖，可并行

M1 和 M3 可以并行推进，但在投影仪、JEI、自动建造改动前，必须先确定 `StructureChannelValues` 和 legacy key 规则。
