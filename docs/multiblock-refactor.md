# 多方块结构系统统一重构计划书

更新时间：2026-05-04

## 文档状态

本计划书是当前唯一执行入口，已合并以下两份原计划：

- `docs/multiblock-refactor.md`
- `docs/gt5-structure-channel-porting-plan.md`

GT5 信道移植不再作为独立工程执行，而是并入多方块结构系统重构，作为“结构信道、JEI、投影仪、自动建造一致性”主线。

## 当前结论

当前项目已经写入了多方块结构系统重构的大部分主体代码，但还不能判定为完成。

当前状态：

> 主体实现已落地，编译阻塞待修复；P0/P2/P4 已接入主流程，P1/P3 与 GT5 信道 parity 尚未完全兑现，整体仍处于待验收阶段。

当前最直接的阻塞点是 `compileJava` 未通过：

- 执行命令：`./gradlew --% compileJava --no-daemon -Dorg.gradle.workers.max=1 -Dorg.gradle.compiler.daemon=false`
- 编译失败位置：`src/main/java/gregtech/common/metatileentities/multi/electric/godforge/ForgeOfGodsStructureString.java`
- 报错原因：第 1 行存在 `\ufeff` BOM，编译器报告“非法字符”

后续第一优先级是恢复可编译状态，然后再做结构检查、信道、JEI、投影仪的实机验收。

## 总目标

本轮统一重构同时解决两类问题。

### 结构运行时问题

| 问题 | 影响 |
|------|------|
| 已成形多方块仍依赖定时轮询 | 世界中多台机器同时存在时浪费 CPU |
| 每次结构检查可能遍历完整结构 | 大型多方块检查代价高 |
| `BlockPattern` 同时承载模板与运行时状态 | 相同机器无法真正共享结构模板 |
| 缺少区块级位置索引 | 方块变化后无法快速定位受影响的多方块 |
| 缺少分片结构验证 | 超大结构无法局部重检 |

### 结构定义与展示问题

| 问题 | 影响 |
|------|------|
| 外壳与仓室数量手动声明 | 多方块定义冗长且容易出错 |
| tiered casing 没有统一信道语义 | 线圈、玻璃、机器外壳等 tier 选择分散 |
| JEI、投影仪、自动建造不共享同一份结构请求 | 玩家看到的结构和实际建造结构可能不一致 |
| 投影仪配置不是 per ItemStack NBT | 多个投影仪或多人使用时容易串状态 |
| GT5 legacy channel key 未兼容 | 从 GT5 移植机器时语义容易丢失 |

## 参考来源

- GregTech CEu 1.12 当前实现：`FactoryBlockPattern`、`TraceabilityPredicate`
- GregTech Modern：事件驱动、异步检查、`MultiblockState`
- GT5 / StructureLib：`IStructureChannels`、`StructureWrapper`、`withChannel`、分片检查、NEI preview modifier

GT5 源码结论已经并入本文的“结构信道统一模型”和后续里程碑。原 `gt5-structure-channel-porting-plan.md` 仅保留为归档说明。

## 当前实现总览

| 模块 | 当前状态 | 判断 |
|------|----------|------|
| 事件驱动结构检查 | 已接入 `MultiblockWorldData`、Forge 事件、Mixin、controller 注册/注销 | 基本完成，待编译与实测 |
| 异步结构检查 | 已接入 `AsyncStructureChecker` 和 `BlockStateSnapshot` | 已实现雏形，需并发安全复核 |
| 模板/实例状态拆分 | 已有 `BlockPatternTemplate`、`MultiblockState`、兼容层 `BlockPattern` | 部分完成，模板共享收益未完全兑现 |
| 分片式结构检查 | 已有 `StructurePiece`、`MultiPiecePattern` 与 controller 入口 | API 完成，暂无机器启用 |
| 声明式 casing | 已有 `ICasing`、`ICasingGroup`、`DeclarativePatternBuilder`，多数机器已迁移 | 大部分完成，剩余迁移与 tooltip 整合 |
| 结构信道 | 已有 `StructureChannel`、`GTStructureChannels`、`channelValues` 预览/建造雏形 | 部分完成，缺 registry、legacy key、indicator、NBT 统一层 |
| JEI 信道预览 | 已有 `getSupportedChannels()` 与 `getMatchingShapes(channelValues)` 调用 | 可用雏形，仍有硬编码范围与 metadata 缺口 |
| 投影仪信道 | 行为类已有 `channelValues` 字段与 GUI 控件 | 未完成，状态不是 per ItemStack NBT，renderer 未强制使用 channel values |

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

| GT5 key | 当前建议 id | 用途 |
|---------|-------------|------|
| `coil` | `heating_coil` | 加热线圈 tier |
| `glass` | `borosilicate_glass` | 玻璃 tier |
| `machine_casing` | `machine_casing` | 机器外壳 tier |
| `casing` | `solid_casing` / alias group | 多类外壳 tier，需 legacy alias |
| `height` | `structure_height` | 可变高度 |
| `length` | `structure_length` | 可变长度 |
| `pipe` | `pipe_casing` | 管道外壳 tier |
| `item_pipe` | `item_pipe_casing` | 物品管道外壳 tier |
| `solenoid` | `solenoid` | 螺线管 tier |
| `capacitor` | `battery` 或 `capacitor` | 电容/储能元件 tier |
| `gt_hatch` | `hatch` / `no_hatch` 转换层 | survival 自动放置 hatch |

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

任务：

1. 修复 `MultiblockState` snapshot 检查写入共享状态的问题。
2. 给异步检查使用临时 `MultiblockState`，避免写入 controller 主 state。
3. 调整 snapshot 范围策略，不能固定依赖 32 半径覆盖所有结构。
4. 审查世界卸载、控制器移除、结构失效时的清理路径。
5. 增加配置开关与 debug 统计。

验收：

- 普通多方块成形/破坏行为与旧版本一致。
- 已成形多方块无方块变化时不主动完整轮询。
- 多台未成形控制器不会造成主线程明显卡顿。
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

任务：

1. 在 `MultiblockControllerBase` 中引入模板优先 API：

   ```java
   protected BlockPatternTemplate createStructureTemplate()
   ```

2. 默认从旧 `createStructurePattern()` 兼容。
3. 将核心机器逐步迁移到静态 `BlockPatternTemplate`。
4. 将直接访问 `structurePattern.cache`、`formedRepetitionCount`、`aisleRepetitions` 的路径迁到 template/state getter。
5. 保留 `BlockPattern` 兼容层，给附属留迁移窗口。

验收：

- 同类型多台机器共享同一份 template。
- JEI、投影仪、自动建造、拆除结构均能从 template/state 正确读取。
- 旧附属只 override `createStructurePattern()` 时仍能工作。

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

尚未完成：

- 当前未发现任何控制器 override `createMultiPiecePattern()`。
- 实际性能收益尚未产生。

任务：

1. 选择 Forge of Gods 或其他超大结构作为试点。
2. 拆分 core、ring、extension、optional upgrade segment。
3. 实现 `createMultiPiecePattern()`。
4. 和 M3/M4 的 channel model 对齐，条件片段可由 upgrade/channel 状态控制。
5. 测试局部 dirty piece 重检。

验收：

- 至少一个实际机器启用 `MultiPiecePattern`。
- 修改单个片段只重检该片段。
- inactive conditional piece 不影响已成形状态。
- piece 失效能导致整个多方块正确失效。

### M8：迁移收尾与清理

目标：把旧结构定义和临时信道逻辑收束到统一体系。

当前迁移情况：

- 多方块中约 29 处使用 `DeclarativePatternBuilder.start()`。
- 仍有约 5 处使用 `FactoryBlockPattern.start()`：
  - `MetaTileEntityCleanroom`
  - `MetaTileEntityFusionReactor`
  - `MetaTileEntityCentralMonitor`
  - `MetaTileEntityForgeOfGods`
  - `MetaTileEntityCharcoalPileIgniter`

任务：

1. 迁移剩余旧 builder 机器，或明确保留原因。
2. 统一 `StructureTooltipBuilder` 到多方块 tooltip 路径。
3. 清理可由 declarative casing 自动计算的手动 `setMinGlobalLimited()`。
4. 扫描 GT5 中所有 `.use(GTStructureChannels...)`、`.withChannel(...)`、`getValueClamped(...)`、`addSubChannelUsage(...)`，逐台机器映射。
5. 写 addon 迁移说明，明确 legacy key 与当前 id 的对应关系。

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

- 普通电力多方块成形与破坏。
- Steam 多方块成形与破坏。
- 带线圈机器成形与 tier 读取。
- 带多个 hatch 的机器成形。
- JEI 结构预览。
- 投影仪预览、compare、自动建造。
- 控制器旋转、上下朝向、翻转。
- 世界保存、退出、重进。
- 多台机器同时存在的 tick 表现。

### 信道专项测试

- EBF：JEI 和投影仪选择不同 `coil`，预览、材料、自动建造、成型热量一致。
- Distillation Tower：`height=3` 与 `height=12` 的预览层数、输出 hatch 层数、材料数量一致。
- Assembly Line：`length=5` 与 `length=16` 的重复段和末端输出段位置一致。
- Hatch：确认当前 `NO_HATCH` 与 GT5 `gt_hatch` 的转换后，测试 survival 自动建造 hatch 行为。
- Indicator：注册过的线圈、玻璃、管道 casing 能在 JEI/投影仪中作为对应信道值展示。

### 压力测试

- 100 台已成形小型多方块，无方块变化时观察 tick 成本。
- 100 台未成形控制器，观察异步检查对主线程影响。
- 跨多个 chunk 的大型结构，破坏不同 chunk 中的内部方块。
- 大型结构启用分片后，破坏不同片段并记录重检耗时。

## 风险清单

| 风险 | 严重程度 | 当前缓解方式 | 后续处理 |
|------|----------|--------------|----------|
| 编译失败 | 高 | 已定位到 BOM 阻塞点 | M0 优先修复 |
| 异步检查写入共享状态 | 高 | 主线程确认检查兜底 | 使用临时 state 或完整加锁 |
| snapshot 范围不足 | 中高 | 当前固定半径 32 | 改为按结构 AABB 或位置集合 capture |
| P1 未真正共享模板 | 中 | 已有 template/state 结构 | 引入模板优先 API 与静态模板 |
| GT5 legacy key 缺失 | 中 | 当前有部分 channel enum | 新增 registry 与 alias |
| `HATCH` / `NO_HATCH` 语义反转 | 中 | 暂无统一转换层 | 在 `StructureChannelValues` 中显式转换 |
| JEI channel 范围硬编码 | 中 | 当前已有 UI 雏形 | 改用 registry metadata |
| 投影仪状态非 per ItemStack | 中高 | 行为字段暂存 | 迁移到 ItemStack NBT |
| 分片 API 无实际使用者 | 中 | API 已接入控制器 | Forge of Gods 试点 |
| 自动 tooltip 未统一 | 低 | 已有工具类 | 接入显示路径 |

## 完成定义

只有满足以下条件，统一重构才能标记为完成：

1. `compileJava` 通过。
2. P0 事件驱动结构检查通过实机验收。
3. P1 同类型机器模板共享实际落地。
4. P2 异步检查不存在共享状态并发写入风险。
5. P3 至少有一个实际超大结构启用分片检查。
6. P4 剩余旧 builder 机器已迁移或明确保留原因。
7. `StructureChannelRegistry`、legacy key alias、indicator item、`StructureChannelValues` 完成。
8. JEI、投影仪、compare、autoBuild 使用同一份 channel values。
9. 投影仪配置持久化到 ItemStack NBT。
10. GT5 关键样例机器的 channel 行为完成 parity 验收。
11. 世界卸载、控制器移除、结构失效不会留下注册残留。

## 当前下一步

立即执行 M0：

1. 修复 `ForgeOfGodsStructureString.java` 的 BOM。
2. 重新跑 `compileJava`。
3. 修复新的编译错误。
4. 编译通过后，优先进入 M1 与 M3。

推荐顺序：

```text
M0 编译
  -> M1 稳定结构检查
  -> M3 统一信道 registry / values
  -> M6 投影仪 NBT 与 renderer
  -> M5 JEI parity
  -> M2 模板共享
  -> M7 分片试点
  -> M8 迁移收尾
```

M1 和 M3 可以并行推进，但在投影仪、JEI、自动建造改动前，必须先确定 `StructureChannelValues` 和 legacy key 规则。
