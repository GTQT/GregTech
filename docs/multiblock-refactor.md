# 多方块结构系统统一重构计划书

更新时间：2026-05-07（实机测试验证后重写）

## 文档状态

本计划书是当前唯一执行入口，已合并以下两份原计划：

- `docs/multiblock-refactor.md`
- `docs/gt5-structure-channel-porting-plan.md`

GT5 信道移植不再作为独立工程执行，而是并入多方块结构系统重构，作为"结构信道、JEI、投影仪、自动建造一致性"主线。

## 当前结论

经过实机功能测试和信道专项测试，运行时结构检查系统（事件驱动、异步检查）已基本可用，核心架构缺陷已修复。
当前阻塞点从"架构层面不工作"转为"信道层面功能不正确"和"异步首次成形链路异常"。

当前状态：

> **已完成的底层修复：**
> - 编译通过（BOM 已修复）
> - 事件驱动检查统一走基类 `doStructureCheck()` 路径（子类 override 已删除）
> - 异步检查已使用临时 `MultiblockState`（`template.createState()`，无 data race）
> - `MultiblockWorldData.INSTANCES` 已使用 `Collections.synchronizedMap`
> - snapshot 范围已改为按结构 AABB 计算（`captureSnapshotForController`）
> - 分片检查 API 已落地，Forge of Gods 已实现 `createMultiPiecePattern()`
> - 首次成形后 `multiPiecePattern.checkAllPieces()` 已调用
> - `StructurePiece.positions` 已使用 volatile reference + swap
>
> **当前阻塞 bug（信道 & 首次成形）：**
> - 投影仪信道值设置无效（传递链路断裂）
> - 预览层数与构建层数不一致（蒸馏塔、装配线）
> - 清空信道按键没有效果
> - 蒸馏塔、装配线、PSS 搭建好后不成形，需退出世界重进（异步确认链路问题）
> - indicator 注册不全（线圈类型未覆盖完整）
> - JEI 选择方块后的循环展示与左侧物品栏重叠（UI 布局问题）

## 实机测试结果汇总

### 功能测试结果（2026-05-07）

| 测试项           | 结果   | 备注                                   |
| ------------- | ---- | ------------------------------------ |
| 普通电力多方块成形与破坏  | ✅ 正常 |                                      |
| Steam 多方块成形与破坏 | ✅ 正常 |                                      |
| 带线圈机器成形与 tier 读取 | ✅ 正常 |                                      |
| 投影仪预览         | ✅ 正常 |                                      |
| 投影仪 compare   | ✅ 正常 |                                      |
| 投影仪自动建造       | ✅ 正常 |                                      |
| 控制器旋转、上下朝向、翻转 | ✅ 正常 |                                      |
| 世界保存、退出、重进    | ✅ 正常 |                                      |
| 多台机器同时存在的 tick 表现 | ✅ 正常 |                                      |
| JEI 结构预览      | ⚠️ 异常 | 鼠标选择后的循环展示与左侧物品栏重叠，左侧物品显示与 GT5 区别较大 |
| 蒸馏塔/装配线/PSS 首次成形 | ❌ 异常 | 搭建好不成形，退出世界重进才成形                     |

### 信道专项测试结果（2026-05-07）

| 测试项                   | 结果   | 具体表现                               |
| --------------------- | ---- | ---------------------------------- |
| EBF 投影仪信道设置 coil     | ❌ 异常 | 线圈设置的值无效，投影仪读取信道逻辑有问题              |
| 投影仪清空信道按键            | ❌ 异常 | 按键没有效果                             |
| 蒸馏塔 height 预览        | ⚠️ 异常 | 预览层数不对，但构建层数正常                     |
| 装配线 length 预览        | ⚠️ 异常 | 预览层数不对，但构建层数正常                     |
| NO_HATCH 自动建造        | ⚠️ 偏差 | 开启后会空缺一个位置不放置方块（应为放置纯外壳）          |
| Indicator 注册         | ⚠️ 不全 | 线圈没注册全？                            |

## 总目标

本轮统一重构同时解决两类问题。

### 结构运行时问题（已基本解决）

| 问题                          | 状态             | 备注                              |
| --------------------------- | -------------- | ------------------------------- |
| 已成形多方块仍依赖定时轮询               | ✅ 已修复          | 事件驱动 + fallback polling         |
| 每次结构检查可能遍历完整结构              | ✅ 已修复          | 抽样检查 + 分片检查                     |
| `BlockPattern` 同时承载模板与运行时状态 | ✅ 已修复          | template/state 拆分已完成            |
| 缺少区块级位置索引                   | ✅ 已修复          | `MultiblockWorldData` chunk index |
| 缺少分片结构验证                    | ✅ 已实现          | Forge of Gods 已使用               |
| 异步检查 data race             | ✅ 已修复          | 临时 state 策略                     |
| 子类绕过事件驱动                    | ✅ 已修复          | 子类 override 已删除                 |
| 蒸馏塔等首次成形延迟                  | ❌ **未修复**      | 异步确认链路问题，需排查                    |

### 结构定义与展示问题（核心阻塞）

| 问题                         | 状态        | 备注                       |
| -------------------------- | --------- | ------------------------ |
| 投影仪信道值传递断裂                 | ❌ **阻塞**  | 设置值后未正确传递到 preview/build |
| 预览层数与构建层数不一致               | ❌ **阻塞**  | preview path 与 autoBuild path 不同步 |
| 清空信道无效                     | ❌ **阻塞**  | GUI saveToNBT / 逻辑问题     |
| NO_HATCH 空缺                | ⚠️ 需修复    | 应放置纯外壳而非跳过              |
| indicator 注册不全             | ⚠️ 需补全    | `registerIndicatorsFromGroup` 未覆盖全部线圈 |
| JEI 预览 UI 布局重叠            | ⚠️ 需修复    | 选中方块后的展示区域与物品栏冲突         |
| GT5 legacy key 未兼容         | 待实现       | registry alias 未填充       |

## 参考来源

- GregTech CEu 1.12 当前实现：`FactoryBlockPattern`、`TraceabilityPredicate`
- GregTech Modern：事件驱动、异步检查、`MultiblockState`
- GT5 / StructureLib：`IStructureChannels`、`StructureWrapper`、`withChannel`、分片检查、NEI preview modifier

## 当前实现总览

| 模块         | 当前状态                                                                       | 判断                              |
| ---------- | -------------------------------------------------------------------------- | ------------------------------- |
| 事件驱动结构检查   | 统一由基类 `doStructureCheck()` 管理，已接入 `MultiblockWorldData`                    | ✅ **已完成**                       |
| 异步结构检查     | 已使用临时 `MultiblockState`，snapshot 按 AABB 计算                                | ✅ **已完成（但有首次成形延迟 bug）**         |
| 模板/实例状态拆分  | `BlockPatternTemplate` + `MultiblockState` + 兼容层 `BlockPattern`           | ✅ 已完成                           |
| 分片式结构检查    | Forge of Gods 已使用 `MultiPiecePattern`，首次成形流程已补充                            | ✅ 已完成，待实机验收                     |
| 声明式 casing | 已有 `ICasing`、`ICasingGroup`、`DeclarativePatternBuilder`，多数机器已迁移           | ✅ 大部分完成                         |
| 结构信道       | 已有 `StructureChannel`、`GTStructureChannels`、`StructureChannelRegistry`    | ⚠️ **功能可用但存在 bug**              |
| 信道值容器      | 已有 `StructureChannelValues`，支持 NBT/Map/Context 转换                         | ✅ API 完整                        |
| JEI 信道预览   | 已读取 `getSupportedChannels()`、`getChannelRange()`、`getMatchingShapes(cv)`  | ⚠️ **预览层数不一致 + UI 布局重叠**        |
| 投影仪信道      | NBT 持久化已实现（`loadFromNBT`/`saveToNBT`），GUI 控件已有                           | ❌ **信道值传递链路断裂**                 |
| 自动建造信道     | `autoBuild` 已消费 `channelValues`，维度控制可用                                    | ⚠️ **构建层数正确但 NO_HATCH 行为有偏差**   |

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
- `src/main/java/gregtech/api/pattern/casing/StructureChannelRegistry.java`
- `src/main/java/gregtech/api/pattern/casing/StructureChannelValues.java`
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

当前工程已有 `StructureChannel`、`GTStructureChannels`、`StructureChannelRegistry`、`StructureChannelValues`。
信道 API 层面已基本完整，问题集中在消费端（投影仪/JEI/预览）的值传递和 UI 逻辑。

### 信道 API 当前实现 vs 需求

| API                        | 状态       | 缺口                           |
| -------------------------- | -------- | ---------------------------- |
| `StructureChannel` 接口     | ✅ 完整     |                              |
| `GTStructureChannels` enum | ✅ 完整     | 14 个预定义信道                    |
| `StructureChannelRegistry` | ✅ 完整     | legacy alias 未填充             |
| `StructureChannelValues`   | ✅ 完整     | NBT/Map/Context 三向转换均可用      |
| Indicator 注册               | ⚠️ 部分完成  | `registerIndicatorsFromGroup` 未调用覆盖全部 casing group |
| Legacy key alias           | ⚠️ 未填充   | GT5 key 未注册                  |
| 投影仪值传递                     | ❌ 断裂     | NBT -> channelValues -> renderer 链路有 bug |
| preview 层数计算               | ❌ 不一致    | `repetitionDFS` 与 `calculateRepetitionsFromChannels` 语义不一致 |

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
| `gt_hatch`       | `gt_no_hatch` (反转语义)         | survival 自动放置 hatch      |

当前 `GTStructureChannels.NO_HATCH` 与 GT5 `HATCH` 的语义需要明确对齐。GT5 是"设置 hatch channel 后允许放置非 exclusive hatch"，而当前实现是 `gt_no_hatch=1` 时跳过 hatch 放置。这块必须在转换层显式处理，避免 UI 语义反转。

## 统一里程碑

### M0：编译通过 — ✅ 已完成

- BOM 已修复
- `compileJava` 已通过
- `reobfJar` 已通过

### M1：稳定事件驱动与异步检查 — ⚠️ 大部分完成（剩余 1 个 bug）

目标：保证结构检查调度不会破坏现有多方块行为。

#### 已修复的缺陷

| 缺陷                                  | 修复方式                                                                | 验证       |
| ----------------------------------- | ------------------------------------------------------------------- | -------- |
| 子类 override `doStructureCheck()`   | 已删除，基类统一管理 + `isWorkingForStructureCheck()` 钩子                      | ✅ 实机验证通过 |
| 异步检查 data race                     | `performAsyncCheck` 使用 `template.createState()` 临时 state           | ✅ 代码确认   |
| `INSTANCES` WeakHashMap 非线程安全       | 已改为 `Collections.synchronizedMap(new WeakHashMap<>())`             | ✅ 代码确认   |
| snapshot 范围固定 32                    | 已改为 `captureSnapshotForController` 按结构 AABB + margin 计算           | ✅ 代码确认   |
| 首次 tick 结构检查                        | `isFirstTick()` 直接走 `checkStructurePattern()`                      | ✅ 实机验证通过 |

#### 未修复的 bug

##### Bug 1：异步确认后某些多方块不成形（P1 高）

**现象**：蒸馏塔、装配线、PSS 搭建好后不成形，需退出世界重进才会成形。

**分析**：这些多方块使用可重复 aisle。异步检查 `performAsyncCheck` 用临时 state 判断
pattern 匹配成功后，在 `processResults()` 中调用 `controller.checkStructurePattern()` 做主线程确认。
但确认检查时，如果 controller 的 `multiblockState` 之前的缓存状态干扰了可重复 aisle 的检测，
或者 `staggering` 时机导致某些 controller 始终不被选中进行 snapshot 捕获，
则会出现"一直不成形"的现象。退出重进后 `isFirstTick()` 强制主线程检查就能成形。

**可能原因**：
1. `prepareSnapshots` 的 staggering 逻辑 `(controller.hashCode() + tickCounter) % 4 != 0` 可能让某些 controller 长时间不被选中
2. 异步线程的 `checkPatternFastAtSnapshot` 使用简化版（跳过 TileEntity 检查），对于有大量 hatch 的结构可能误判为"不匹配"
3. `MAX_SNAPSHOTS_PER_TICK = 4` 限制可能导致队列积压

**修复方案**：
1. 降低 staggering 模数或在首次注册后立即安排一次检查
2. 异步检查失败后在主线程也做一次降频的完整检查（如每 100 tick 做一次 fallback）
3. 或者：对已搭建但未成形的 controller，若连续 N 次异步检查后仍未成形，fallback 到主线程检查

涉及文件：
- `AsyncStructureChecker.java` — 调整 staggering 策略或添加 fallback

任务：

1. 在 `doStructureCheck()` 的异步分支后添加 fallback：如果 controller 已注册异步检查超过一定 tick 数仍未成形，执行一次主线程检查
2. 或调整 `prepareSnapshots` 的 staggering 逻辑确保所有 controller 都能被及时处理

验收：

- 蒸馏塔、装配线、PSS 搭建好后可以在合理时间内（< 5 秒）自动成形
- 无需退出世界重进
- 普通多方块成形/破坏行为与旧版本一致
- 已成形多方块无方块变化时不主动完整轮询

### M2：模板共享 — ✅ 大部分完成

目标：让同类型机器共享 `BlockPatternTemplate`，每台机器只持有自己的 `MultiblockState`。

当前状态：

- `BlockPatternTemplate` 已存在
- `MultiblockState` 已存在
- `createStructureTemplate()` 已作为新入口
- `createStructurePattern()` 已标记 `@Deprecated`
- `BlockPattern` 作为兼容层保留
- 多数核心机器已迁移到 `DeclarativePatternBuilder`

尚未完成：

- 未形成大规模静态 template 缓存（当前每次 `reinitializeStructurePattern()` 仍创建新 template）
- `BlockPattern` 何时完全移除尚未定义 deadline

过渡策略（已确认）：

1. `createStructureTemplate()` 为新入口（已实现）
2. `createStructurePattern()` 已标记 `@Deprecated`
3. `BlockPattern` 保留至 M8 收尾阶段
4. M8 阶段如果附属全部迁移完成，标记 `@ApiStatus.ScheduledForRemoval`

剩余任务：

1. 逐步将核心机器的 template 改为静态缓存（`LazyTemplate` 已提供 DCL 方案）
2. 文档化 `BlockPattern` 的废弃路径

### M3：信道 bug 修复与完善 — ❌ 当前阻塞

目标：修复实机测试发现的信道功能 bug，让投影仪、JEI、自动建造正确消费信道值。

#### Bug 2：投影仪信道值设置无效（P0 阻塞）

**现象**：在投影仪 GUI 中设置 coil 信道值后，预览和自动建造不使用该值。

**分析**：`StructureProjectorBehavior` 中的 `channelValues` 是一个实例字段 `Map<String, Integer>`。
代码中存在以下传递链：

```text
GUI 设置值 -> channelValues map 更新 -> saveToNBT(stack) -> NBT 持久化
使用时：loadFromNBT(stack) -> channelValues -> setChannelValues() -> renderer / autoBuild
```

问题可能出在：
1. GUI 中的 `IntSyncValue` 写入逻辑与 `channelValues` map 之间的同步时机
2. `buildChannelEntries()` 中对 `supportedChannels` 的依赖 — `supportedChannels` 是客户端列表，
   如果 GUI 打开时 `supportedChannels` 为空（因为没有对准 controller），`autoFillFromSupported` 可能清空有效值
3. `saveToNBT` 中如果 `channelValues` 里的 key 对应的值为 0 则跳过写入，
   但 `updateEntryValue` 中设置 0 可能不等于"删除"

**修复方案**：
1. 审查 `buildChannelEntries()` 的调用时机，确保不会意外清空已有值
2. 确保 GUI sync value 的写入回调正确执行 `saveToNBT`
3. 确保 `loadFromNBT` 在使用前总是被调用（`onItemUseFirst` 开头已调用，需确认其他入口）

涉及文件：
- `StructureProjectorBehavior.java` — GUI sync、value persistence

#### Bug 3：清空信道按键无效（P1 高）

**现象**：投影仪 GUI 中的"清空信道"按钮点击后无效果。

**分析**：需检查 GUI 中 clear button 的 `onClick` 回调是否正确调用 `channelValues.clear()` + `saveToNBT(stack)`。
可能是 sync value 没有正确触发重渲染或 server-side 同步。

涉及文件：
- `StructureProjectorBehavior.java` — clear button callback

#### Bug 4：预览层数与构建层数不一致（P0 阻塞）

**现象**：蒸馏塔设置 height=5，构建正确产出 5 层，但预览显示的层数不对（可能显示最大层数或最小层数）。

**分析**：预览路径和构建路径使用不同的 repetition 计算方式：

- **构建路径** (`autoBuild`)：使用 `calculateRepetitionsFromChannels(channelValues)` — 根据 `aisleChannelNames` 匹配信道名
- **预览路径** (`getMatchingShapes` → `repetitionDFS`)：也消费 `channelValues`，但匹配逻辑是检查 `aisleChannelNames[aisleIdx]` 是否与 `channelValues` 中的 key 匹配

不一致的根因可能是：
1. `calculateRepetitionsFromChannels` 使用固定的 `STRUCTURE_HEIGHT` / `STRUCTURE_LENGTH` 作为前两个可重复 aisle 的控制器，
   而 `repetitionDFS` 使用 `aisleChannelNames[aisleIdx]` 按 index 精确匹配
2. 如果某个结构没有在 `FactoryBlockPattern.setRepeatable(min, max, channelName)` 中设置 channelName，
   则 `aisleChannelNames[aisleIdx]` 为 null，`repetitionDFS` 会 fallback 到遍历所有可能值（产生多个变体），
   而 `calculateRepetitionsFromChannels` 会按 "第一个可重复 aisle = STRUCTURE_HEIGHT" 的约定处理

**修复方案**：
1. 统一两个路径的 repetition 计算逻辑 — 让 `repetitionDFS` 也走 `calculateRepetitionsFromChannels` 的等价逻辑
2. 或者：确保所有可变尺寸结构都通过 `setRepeatable(min, max, channelName)` 声明了 channel name，
   使 `repetitionDFS` 能按名字精确匹配

涉及文件：
- `MultiblockState.java` — `calculateRepetitionsFromChannels`
- `MultiblockControllerBase.java` — `repetitionDFS`
- 各可变尺寸多方块（蒸馏塔、装配线）的结构定义 — 确认 `aisleChannelNames` 已设置

#### Bug 5：NO_HATCH 空缺位置（P2 中）

**现象**：开启 NO_HATCH 后，hatch 位置空缺一个方块不放置任何东西。

**分析**：当前 `autoBuild` 中 `skipHatches = true` 的逻辑可能是直接跳过该位置，
而正确行为应该是在 hatch 候选位置放置对应外壳方块（即 predicate 的 non-hatch candidate）。

**修复方案**：
`autoBuild` 中当 `skipHatches` 为 true 时，对于 hatch predicate 位置不是跳过，
而是使用其 casing candidate（TraceabilityPredicate 中的非 hatch 候选方块）。

涉及文件：
- `MultiblockState.java` — `autoBuild` 方法

任务：

1. 修复 Bug 2：投影仪信道值传递链路
2. 修复 Bug 3：清空信道按键
3. 修复 Bug 4：预览层数与构建层数统一
4. 修复 Bug 5：NO_HATCH 放置逻辑
5. 补全 indicator 注册（确保所有 coil group 都调用了 `registerIndicatorsFromGroup`）
6. 注册 GT5 legacy key alias

验收：

- 投影仪设置 `coil=3`，预览、compare、autoBuild 全部使用 tier 3 coil
- 投影仪设置 `height=5`，预览和构建均为 5 层
- 清空按键可以正确清除所有信道值
- NO_HATCH 模式下所有位置都有方块（纯外壳填充）
- indicator 物品可以正确查询所有已注册线圈类型

### M4：JEI 信道预览修复 — ⚠️ 依赖 M3

目标：JEI 多方块预览正确展示信道控制效果，UI 布局正常。

#### Bug 6：JEI 选择方块后循环展示与左侧物品栏重叠（P2 中）

**现象**：在 JEI 多方块预览中鼠标选择一个方块后，右侧的候选方块展示区域与左侧的物品栏重叠。

**分析**：`MultiblockInfoRecipeWrapper` 中选中方块后在 `predicates` 列表中展示候选项，
这些候选项渲染位置可能与左侧 `PARTS_WIDTH` 区域冲突。需检查布局计算。

涉及文件：
- `MultiblockInfoRecipeWrapper.java` — predicate rendering position

#### 预览层数问题（依赖 M3 Bug 4 修复）

修复 `repetitionDFS` 后，JEI 中调信道应自动正确。

任务：

1. 修复 JEI 中选择方块后的候选展示 UI 布局
2. 确认 M3 修复后 JEI 预览层数正确
3. 确保材料列表随信道值变化正确重算（`regeneratePatterns` 已有，需验证）
4. 信道 UI 从 `StructureChannelRegistry` 获取 label/tooltip（当前已基本实现）

验收：

- JEI 中调 EBF coil，3D 预览和材料列表同步变化
- JEI 中调蒸馏塔 height，预览高度和材料数量同步变化
- 选择方块后的候选列表不会遮挡其他 UI 元素

### M5：投影仪完善 — ⚠️ 依赖 M3

目标：投影仪作为完整的 GT5 trigger item 等价物正确工作。

当前实现分析：

`StructureProjectorBehavior` 已实现：
- ✅ NBT 持久化：`NBT_COMPARE_MODE`、`NBT_NO_HATCH`、`NBT_CHANNELS`
- ✅ `loadFromNBT` / `saveToNBT` 完整
- ✅ GUI 有 compare mode、no_hatch、height、length 控件
- ✅ 右键 controller 调用 `MultiblockPreviewRenderer.setChannelValues(channelValues)` 和 `renderMultiBlockPreview`
- ✅ Shift+右键调用 `state.autoBuild(player, multiblock, channels, noHatch)`
- ⚠️ GUI 中 channel entry 编辑逻辑复杂（动态列表 + sync value），可能是值传递断裂的源头

修复 M3 后的剩余任务：

1. 验证两个不同投影仪物品能保存不同信道配置（per-ItemStack NBT）
2. 验证关闭 GUI、丢地上、重进世界后配置仍在
3. GUI 控件应从 `controller.getSupportedChannels()` + `getChannelRange()` 自动生成范围
4. tooltip 显示当前配置的关键信道值（已实现 `addInformation`）

验收：

- 两个投影仪物品保存不同信道配置，互不影响
- 投影仪选择 `coil=4` 后，预览、compare、自动建造均使用同一 coil tier
- compare mode 对 `height=5` 的蒸馏塔只比较 5 层结构

### M6：分片式结构检查实机验收

目标：Forge of Gods 的分片检查在所有朝向下正确工作。

当前已完成：

- `StructurePiece`、`MultiPiecePattern` 已存在
- `createMultiPiecePattern()` 已作为 opt-in 入口
- `MultiblockWorldData` 可按方块位置标记 dirty piece
- `OffsetMode` enum 已实现（ABSOLUTE / RELATIVE / HORIZONTAL_RELATIVE）
- `MultiPiecePattern.Builder` 支持带 `OffsetMode` 的 `piece()` / `conditionalPiece()` 重载
- `BlockPatternTemplate` 支持外部 `externalCenterOffset` 构造函数
- `FactoryBlockPattern.buildTemplate(int[] centerOffset)` 已实现
- `LazyTemplate` 标准化模板缓存方案已实现
- `MetaTileEntityForgeOfGods` 已实现 `createMultiPiecePattern()`
- `OffsetMode.RELATIVE` 语义修正完成
- 首次成形后 `multiPiecePattern.checkAllPieces()` 已调用
- `StructurePiece.positions` 已使用 volatile reference + swap

已知架构限制（已解决）：

1. ~~`MultiPiecePattern` 的 offset 不支持方向旋转~~ → 已实现 `OffsetMode.RELATIVE`
2. ~~`BlockPatternTemplate` 不支持外部 centerOffset~~ → 已实现 `externalCenterOffset` 构造函数
3. ~~Template 缓存没有标准化方案~~ → 已实现 `LazyTemplate`
4. ~~缺少 structurelib 的"虚拟 center"概念~~ → 通过 `externalCenterOffset` + piece offset 组合解决
5. ~~首次成形流程缺失~~ → 已在 `checkStructurePattern()` 成功后补充
6. ~~`StructurePiece.positions` 并发访问~~ → 已修复

剩余架构缺陷：

##### `structurePattern` 与 `multiPiecePattern` 的 positions 注册可能冲突（P2 设计层面）

如果 controller 同时有两者，`checkStructurePattern()` 注册 base pattern positions，
`registerMultiPiecePattern()` 注册所有 piece 合并 positions。当前代码中已有处理
（multi-piece 模式时由 `registerMultiPiecePattern()` 统一注册），但需确认不会重复注册。

任务：

1. 在 Forge of Gods 上验证各朝向（NORTH/SOUTH/EAST/WEST）下的结构检查
2. 测试局部 dirty piece 重检（破坏某一环的一个方块）
3. 验证 conditional piece 的 activate/deactivate 行为
4. 压力测试：记录局部重检耗时 vs 全量重检

验收：

- Forge of Gods 各朝向下结构检查正确
- 修改单个片段只重检该片段
- inactive conditional piece 不影响已成形状态
- piece 失效能导致整个多方块正确失效

### M7：迁移收尾与清理

目标：把旧结构定义和临时信道逻辑收束到统一体系。

当前迁移情况：

- 多方块中约 29 处使用 `DeclarativePatternBuilder.start()`
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

1. ✅ 所有保留 FactoryBlockPattern 的机器已添加注释说明保留原因
2. ✅ `StructureTooltipBuilder` 已接入 `DeclarativePatternBuilder` tooltip 自动生成管线
3. ✅ 已添加 `gregtech.multiblock.ability.*` 国际化键值（en_us + zh_cn）
4. ✅ 确认已迁移机器中无冗余 `setMinGlobalLimited`
5. ⬜ GT5 structure channel 逐台映射：待 M3 修复后执行
6. ⬜ addon 迁移说明：待所有子任务完成后撰写

任务：

1. M3 修复后验证所有已迁移机器的信道行为
2. 对可变尺寸结构确保 `setRepeatable(min, max, channelName)` 已正确设置
3. 撰写 addon 迁移指南
4. `BlockPattern` 标记 `@ApiStatus.ScheduledForRemoval` 并设定移除版本号

验收：

- 常规多方块基本使用声明式 casing
- tooltip、JEI、投影仪显示一致
- GT5 中依赖 structure channel 的机器都有当前工程对应声明或明确 TODO

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

| 测试项           | 预期行为                         | 上次结果 |
| ------------- | ---------------------------- | ---- |
| 普通电力多方块成形与破坏  | 放置即成形，破坏即失效                  | ✅    |
| Steam 多方块成形与破坏 | 同上                           | ✅    |
| 带线圈机器成形与 tier 读取 | 检测到正确 coil tier              | ✅    |
| JEI 结构预览      | 正确 3D 渲染，无 UI 重叠            | ⚠️   |
| 投影仪预览         | 显示正确全息图                      | ✅    |
| 投影仪 compare   | 标红缺失/错误方块                    | ✅    |
| 投影仪自动建造       | 正确放置所有方块                     | ✅    |
| 控制器旋转、翻转      | 各朝向下结构检查正确                   | ✅    |
| 世界保存/退出/重进    | 成形状态持久化                      | ✅    |
| 多台机器同时存在      | tick 性能无明显退化                 | ✅    |
| 蒸馏塔/装配线/PSS 首次成形 | 搭建后 < 5 秒自动成形（不需退出重进）       | ❌    |

### 信道专项测试

| 测试项                          | 预期行为                               | 上次结果 |
| ---------------------------- | ---------------------------------- | ---- |
| EBF 投影仪设置 coil              | 预览/构建使用对应线圈                        | ❌    |
| 投影仪清空信道                      | 所有信道值归零                            | ❌    |
| 蒸馏塔 height 预览               | 预览层数 = 设定值                         | ❌    |
| 蒸馏塔 height 构建               | 构建层数 = 设定值                         | ✅    |
| 装配线 length 预览               | 预览段数 = 设定值                         | ❌    |
| 装配线 length 构建               | 构建段数 = 设定值                         | ✅    |
| NO_HATCH 自动建造               | hatch 位置放置纯外壳（非空缺）                 | ❌    |
| Indicator 查询                | 所有已注册 coil/glass 的 ItemStack 可查    | ⚠️   |
| GT5 legacy key resolve       | `resolve("coil")` 返回 `HEATING_COIL` | ⬜    |
| JEI 调 coil                  | 3D 预览和材料列表同步变化                     | ⬜    |
| JEI 调 height                | 预览高度和材料数量同步变化                      | ⬜    |
| 两个投影仪不同配置                    | per-ItemStack NBT 互不影响             | ⬜    |

### 压力测试

- 100 台已成形小型多方块，无方块变化时观察 tick 成本
- 100 台未成形控制器，观察异步检查对主线程影响
- 跨多个 chunk 的大型结构，破坏不同 chunk 中的内部方块
- 大型结构启用分片后，破坏不同片段并记录重检耗时

## 风险清单

| #  | 风险                                               | 严重程度     | 当前状态                                     | 修复方案                              | 里程碑 |
| -- | ------------------------------------------------ | -------- | ---------------------------------------- | --------------------------------- | --- |
| 1  | 子类 override `doStructureCheck()`                 | ~~P0~~   | ✅ **已修复** — override 已删除                 | —                                 | M1  |
| 2  | 异步检查 data race                                  | ~~P0~~   | ✅ **已修复** — 使用临时 state                   | —                                 | M1  |
| 3  | MultiPiecePattern 首次成形流程缺失                       | ~~P0~~   | ✅ **已修复** — `checkAllPieces` 已调用        | —                                 | M6  |
| 4  | 编译失败                                             | ~~高~~    | ✅ **已修复** — BOM 已移除                      | —                                 | M0  |
| 5  | `INSTANCES` WeakHashMap 非线程安全                    | ~~P1~~   | ✅ **已修复** — synchronizedMap              | —                                 | M1  |
| 6  | `StructurePiece.positions` 并发访问                  | ~~P1~~   | ✅ **已修复** — volatile + swap              | —                                 | M6  |
| 7  | snapshot 范围不足                                    | ~~P1~~   | ✅ **已修复** — AABB + margin                | —                                 | M1  |
| 8  | **异步首次成形延迟（蒸馏塔/装配线/PSS）**                       | **P1 高** | ❌ 未修复 — staggering 或 snapshot 导致         | fallback 机制                       | M1  |
| 9  | **投影仪信道值传递断裂**                                    | **P0 阻塞** | ❌ 未修复 — GUI sync / loadFromNBT 时机问题     | 审查 sync value 链路                  | M3  |
| 10 | **预览层数与构建层数不一致**                                  | **P0 阻塞** | ❌ 未修复 — `repetitionDFS` vs `calculateRepetitionsFromChannels` | 统一两路径计算逻辑                         | M3  |
| 11 | **清空信道按键无效**                                      | **P1 高** | ❌ 未修复 — GUI callback 问题                  | 修复 clear button                   | M3  |
| 12 | NO_HATCH 空缺                                      | P2 中     | ❌ 未修复 — 跳过而非替换                            | 改为放置 casing candidate             | M3  |
| 13 | JEI UI 布局重叠                                      | P2 中     | ❌ 未修复 — predicate 渲染位置冲突                 | 调整布局计算                            | M4  |
| 14 | Indicator 注册不全                                   | P2 中     | ⚠️ 部分 — coil group 未全覆盖                   | 调用 `registerIndicatorsFromGroup`  | M3  |
| 15 | GT5 legacy key 缺失                                | P3 低     | ⬜ 未开始 — alias 未注册                        | `registerAlias`                   | M3  |
| 16 | `BlockPattern` 过渡路径未设 deadline                   | P4 低     | ⬜ 已标 deprecated，未设移除版本                   | M7 设定 `@ScheduledForRemoval`      | M7  |
| 17 | 分片 `structurePattern`/`multiPiecePattern` 注册冲突 | P3 低     | ⬜ 代码已有处理但未验证                              | 实机验证                              | M6  |

## 完成定义

只有满足以下条件，统一重构才能标记为完成：

1. ✅ `compileJava` 通过
2. ✅ 事件驱动结构检查所有 controller 子类统一走新调度路径
3. ✅ 异步检查不存在共享状态并发写入风险
4. ❌ 蒸馏塔等可变结构首次成形不需退出重进
5. ❌ 投影仪信道值正确传递到 preview/compare/autoBuild
6. ❌ 预览层数与构建层数一致
7. ⬜ `StructureChannelRegistry` legacy key alias 填充
8. ⬜ indicator 注册覆盖所有 casing group
9. ⬜ JEI 中信道调节后预览/材料列表正确更新
10. ⬜ NO_HATCH 放置纯外壳而非空缺
11. ⬜ JEI UI 布局无重叠
12. ⬜ 至少一个超大结构（Forge of Gods）分片检查实机验收通过
13. ⬜ `BlockPattern` 兼容层有明确的 deprecation 路径和移除版本号
14. ⬜ addon 迁移说明文档完成

## 当前下一步

立即执行 M1 剩余 + M3：

```text
M1 (Bug 1: 异步首次成形延迟)
  -> M3 (Bug 2-5: 信道 bug 修复)  ← 当前最高优先级
  -> M4 (JEI UI 修复)
  -> M5 (投影仪验证)
  -> M6 (分片实机验收)
  -> M7 (收尾)
```

### 推荐执行顺序

```text
Phase 1 — 功能修复（阻塞解除）
  1. M3 Bug 4: 预览层数统一（修改 repetitionDFS / calculateRepetitionsFromChannels）
  2. M3 Bug 2: 投影仪信道值传递（审查 StructureProjectorBehavior GUI sync）
  3. M3 Bug 3: 清空信道按键
  4. M1 Bug 1: 异步首次成形延迟
  5. M3 Bug 5: NO_HATCH 放置逻辑
  6. M3 补全: indicator 注册 + legacy alias

Phase 2 — UI 与体验（非阻塞）
  7. M4: JEI 选中方块展示布局修复
  8. M5: 投影仪 per-ItemStack 验证
  9. M6: Forge of Gods 分片验收

Phase 3 — 收尾
  10. M7: BlockPattern 设定移除版本号
  11. M7: addon 迁移指南
  12. M7: 对可变尺寸结构统一确认 aisleChannelNames 已设置
```

### 里程碑依赖关系

```text
M1(Bug 1) ─→ M3(Bug 2-5) ─→ M4
                 │               │
                 └─→ M5 ←───────┘
                      │
                      └─→ M6 ─→ M7
```

硬依赖：

- M3 阻塞 M4/M5（信道 bug 不修复则 JEI/投影仪无法正确验收）
- M1 Bug 1 不阻塞 M3（首次成形问题独立于信道功能）

可并行：

- M1 Bug 1 和 M3 可并行推进
- M4 和 M6 之间无强依赖，可并行
- M5 在 M3 修复后可立即验证
