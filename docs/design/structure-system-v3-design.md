# Structure System V3 Design

**Implementation snapshot:** 2026-06-13
**Scope:** 当前仓库中的 `gregtech.api.pattern`、`gregtech.api.pattern.element`、
`gregtech.api.metatileentity.multiblock` 以及已迁移控制器代码。

## 1. 目标

Structure System V3 的目标不是一次性替换 GregTech 现有多方块系统，而是把已经分散在
`BlockPattern`、`MultiPiecePattern`、`MultiblockState`、`TraceabilityPredicate`、控制器基类和工具逻辑中的结构行为，
收敛到更清晰的边界：

- 结构声明只描述结构是什么。
- 运行时只保存某个控制器自己的可变结构状态。
- 每次检查、预览、提示、创造建造、生存建造、快照检查都用一次 operation 表达。
- operation 内部的分支、回滚、候选探测和收集结果都属于本次 session。
- 只有 server-thread commit 成功后，形成状态才可以发布到控制器。

V3 吸收了 StructureLib 的优点：声明、执行、工具调用、方向状态和诊断解耦；但保留 GregTech 已有的
`PatternMatchContext`、`MultiblockAbility`、声明式 casing/hatch/tier/channel 语义、软缓存模板、piece runtime 和控制器生命周期。

当结构行为不确定时，优先增加有开关的 trace/log，而不是直接改匹配逻辑。日志不能打在高频热路径上，除非明确受
`debugStructureTrace`、`debugStructureCheck` 或等价 debug gate 控制。

## 2. 当前架构概览

当前代码已经形成了以下分层：

| 层级 | 主要类型 | 当前职责 |
|---|---|---|
| 声明层 | `StructureDefinition`、`StructurePiece`、`PieceTemplate`、`BlockPatternTemplate`、`MultiPiecePattern` | 描述结构形状、piece、重复、条件、preview 和旧 API 适配。 |
| 元素层 | `IStructureElement`、`CompiledStructureElement`、`StructureElementCapability`、`TraceabilityPredicate` | 执行单元格匹配、候选、hint、creative/survival placement、能力声明和旧 predicate 兼容。 |
| 运行时层 | `StructureRuntime` | 某个控制器的 V3 状态所有者，持有 resolved definition、compat template、piece runtimes、formed metadata、channels、missing abilities、last failure 和 evaluator。 |
| operation 层 | `StructureOperationRequest`、`StructureOperationEvaluator`、`StructureCheckResult`、`StructureBuildResult`、`StructureHintResult` | 将 check/build/hint/preview/iterate/snapshot 统一为请求和结果。 |
| session 层 | `StructureMatchSession`、`StructureEvaluationContext`、`StructureMatchCollector`、`StructureOperationState`、`BlockWorldState` | 保存一次操作内的 speculative state、collector state、legacy context checkpoint、分支事务和 typed result state。 |
| commit 层 | `MultiblockStructureCommitter`、`PreparedCommit` | 在发布控制器状态前验证 part sharing、ability、metadata、channels 和失败状态。 |
| 控制器协作层 | `MultiblockStructureCheckScheduler`、`MultiblockStructureAssembler`、`MultiblockStructureRegistration`、`MultiblockStructurePreviews`、`MultiblockStructureChannels`、`AsyncStructureChecker` | 把控制器生命周期逻辑从基类中拆出来，当前仍是内部实现边界，不是 addon API。 |

设计上的核心规则是：共享对象必须不可变；可变状态只能属于一个 controller runtime 或一次 operation session。

## 3. 所有权模型

| 概念 | 所有者 | 说明 |
|---|---|---|
| `StructureDefinition` | 共享不可变 | 新结构声明的 canonical public shape。控制器优先返回它。 |
| `PieceTemplate` | 共享不可变 | 编译后的 piece 表示。 |
| `BlockPatternTemplate` | 共享不可变兼容视图 | 单 piece API、旧工具和 legacy controller 仍需要的模板 facade。 |
| `MultiPiecePattern` | 共享不可变 | 多 piece 编译结构和旧多 piece API 的主要形状。 |
| `MultiblockState` | 单控制器 | 单 piece 兼容检查状态、缓存和旧 exact-check 路径。 |
| `PieceRuntime` / piece runtime map | 单控制器 | 多 piece runtime、dirty 标记、piece 缓存和 formed position。 |
| `StructureRuntime` | 单控制器 | V3 的状态入口。runtime 拥有 formed metadata、formed channels、missing abilities、last failure 等 V3 状态。 |
| `StructureOperationRequest` | 单次操作 | 不可变输入，描述 operation kind、trigger、world、orientation、channel、build mode 等。 |
| `StructureMatchSession` | 单次操作 | 分支、fork、checkpoint、collector、operation state 和 typed context。 |
| `StructureEvaluationContext` | 单 cell / 单 traversal 上下文 | 把 world state、session、orientation、piece/cell 坐标和 operation request 组合起来。 |
| `BlockWorldState` | 单 traversal 或 legacy world view | 保存 legacy `PatternMatchContext`、global/layer count map checkpoint 和 block probe/action helper。 |
| `PatternMatchContext` | 兼容视图 | 旧 string-keyed context。新逻辑应尽量走 typed collector 或 typed operation state。 |
| `StructureOperationState` | 单次操作结果状态 | collected parts、active casing positions、counts、ability counts、ability contributors、requirements。 |
| `StructureCheckResult` | 单次 check 结果 | check 成败、operation state、context、channels、missing abilities、failure trace。 |
| `StructureBuildResult` | 单次 build 结果 | visited/existing/placed/unavailable/skipped/failed 等建造进度摘要。 |
| `StructureHintResult` | 单次 hint 结果 | active pieces、traversal、visited cells、trigger-aware/context-fallback dispatch 摘要。 |
| `StructureFailureTrace` | runtime 最近失败 | 稳定的用户可读失败摘要，不等同于 debug 日志流。 |

控制器仍然保留生命周期上的 formed flag、attached parts、ability instances 等状态。V3 当前已经把 formed metadata、
formed channel values、missing abilities 和 last failure 收归 `StructureRuntime`，但还没有把全部控制器形成状态都搬进 runtime。

## 4. 声明层

### 4.1 Canonical declaration

新控制器应优先实现：

```java
protected StructureDefinition<?> createStructureDefinition()
```

兼容入口仍然存在：

- `createStructureTemplate()`
- `createStructurePattern()`
- `createMultiPiecePattern()`
- `buildTemplate()` 或注册式旧 hook

当前 resolver 的设计是：能拿到 `StructureDefinition` 时优先使用；旧模板和旧多 piece hook 会被适配成 definition 或 compat view，
然后再进入 runtime/evaluator。

### 4.2 Definition 与 template 的关系

`StructureDefinition` 是 V3 公开声明形状。`PieceTemplate` 是编译后的 piece 表示。`BlockPatternTemplate` 保留为旧 API 和工具的兼容 facade。

这意味着：

- 新核心多方块不应该只通过 legacy hook 暴露结构。
- `TraceabilityPredicate` 可以作为 legacy adapter 存在，但不是新元素的执行模型。
- preview、projector、builder 等工具可以继续拿到旧数组格式或旧模板视图，但 runtime 内部应通过 request/evaluator 进入。

### 4.3 当前已迁移控制器形态

当前大量固定单 piece 和多 piece 控制器已经直接或间接返回 `StructureDefinition`。典型范围包括：

- 常规固定结构：vacuum freezer、implosion compressor、coke oven、steam grinder、steam oven、pyrolyse oven、processing array、
  multi smelter、multi alloy furnace、electric blast furnace、cracking unit、large chemical reactor 等。
- 复杂结构：assembly line、distillation tower、data bank、HPCA、power substation、central monitor、cleanroom 等。
- 仍带兼容注册或旧 hook 的结构：large turbine、large miner、fluid drill、fusion reactor 等。

特殊路径：

- Cleanroom 返回 `StructureDefinition`，但内部仍因运行时尺寸动态发现而生成兼容 pattern。工具触发的动态 build 使用 disposable
  `StructureRuntime`，而不是绕过 operation request。
- Network switch 显式覆盖 `createStructureDefinition()`，避免继承路径误选 data bank definition。
- Charcoal pile 和 Godforge/controller-module 路径仍是主要的剩余动态迁移点。

## 5. 元素层

### 5.1 Direct element 是主路径

`gregtech.api.pattern.element.IStructureElement` 当前已经承担 V3 cell runtime：

- `check` / canonical `match`
- context-aware candidates
- `couldBeValid`
- hint dispatch
- creative placement
- survival placement
- deferred requirement collection
- capability advertisement

`CompiledStructureElement` 会直接执行 direct element。新元素不应该为了匹配而强制转换成 `TraceabilityPredicate`。

### 5.2 Legacy predicate 是兼容路径

`TraceabilityPredicate` 仍用于旧声明、旧 tooltip、旧 preview 或 addon 兼容。它的执行必须服从 V3 checkpoint/rollback 语义：

- predicate alternative 失败时要恢复 `PatternMatchContext`。
- legacy global count 和 layer count 要和 context 一起回滚。
- callback 抛异常时要恢复 speculative state。
- 不支持 snapshot 的 legacy element 必须让 evaluator 回退到 live-world check，而不是把 unsupported 当 mismatch。

### 5.3 已具备 direct path 的元素

当前 direct path 覆盖了主要内置元素类别：

- block、air、any、self
- chain、wrapper
- hatch、casing、tiered casing、coil 等 domain element
- no-placement adapter、check-only/deferred 相关适配接口

直接元素的 formed 匹配入口是 `match(StructureEvaluationContext)`。默认实现保持“先收集 requirement，再 check”的行为；组合元素可以让 requirement collection
跟随分支事务走，避免失败分支留下 requirement、ability、tier、active casing position 或 legacy context side effect。

### 5.4 Capability

`StructureElementCapability` 用来说明一个 compiled element 支持哪些操作。当前重要能力包括：

- live check
- snapshot check
- preview/candidate
- hint
- creative placement
- survival placement

能力不足不是 mismatch，而是调度选择问题。比如 snapshot 不支持时，应触发安全 live fallback。

## 6. Operation 模型

### 6.1 Request

`StructureOperationRequest` 是操作输入边界。它承载：

- operation kind
- world 或 snapshot view
- controller / trigger item / player
- orientation
- channel values
- build mode
- preview/hint/build/iterate 所需的 operation-local 参数

旧 public method 可以继续存在，但应尽量只负责构造 request，然后调用 `StructureRuntime`。

### 6.2 Evaluator

`StructureOperationEvaluator` 是当前 operation boundary。它已经被 controller check、preview helper、projector、multiblock builder、
legacy `BlockPattern` 和 structure iteration 使用。

现阶段 evaluator 仍然保留多个 operation-specific entry，但 P0 范围内的坐标和 cell traversal 已经收敛：

- fixed single-piece live/snapshot check 共用 `MultiblockState` fixed-structure cell traversal。
- multi-piece fixed piece、repeat group、dynamic-offset piece 通过 `StructureCellTraversal` / `StructureOrientation`
  传递 center、orientation 和 template-local offset。
- preview、hint、creative/survival build、formed-block iteration 复用 fixed cell visitor。
- repeat group 的 live/snapshot/build/hint slice offset 统一由 `visitRepeatOffsets(...)` 枚举。
- formation collector/state 写入由 operation policy 和 session transaction 边界控制。

后续剩余工作主要是 result 语义细化、survival item accounting、诊断和 async commit pipeline，而不是 P0 的坐标/cell walker 收敛。

### 6.3 Result

当前已有三类主要结果：

- `StructureCheckResult`：同步 check 结果，commit 前的不可变结构。
- `StructureBuildResult`：creative/survival build 进度摘要。
- `StructureHintResult`：hint traversal 和 dispatch 进度摘要。

理想上所有 operation 都应返回统一语义的 typed result，至少包含：

- outcome：success、mismatch、missing requirement、unsupported、stale snapshot、partial placement、error。
- orientation。
- piece、repeat、cell、world pos。
- collected parts、ability counts、requirements。
- formed metadata、channels、tier values。
- failure trace。
- 对 build 来说，还需要 item accounting 和 budget 信息。

当前 check result 最接近完整模型；build/hint result 已有轻量摘要，但还没有完全承载 item accounting、render outcome 等细节。

## 7. Session、事务和回滚

V3 当前最重要的实现成果是：speculative state 不再随意泄漏到失败分支。

### 7.1 Session

`StructureMatchSession` 保存一次 operation 中的 typed state，并提供：

- checkpoint
- restore boundary
- fork / tryFork
- transaction / transactionValue / transactionAction
- probe / probeValue / probeAction

多 piece dirty/full check、repeat-group live/snapshot candidate、definition fixed-piece check 等路径通过 shared fork/commit transaction
执行。`PieceRuntime` / `PieceRuntimes` / `MultiblockState` 也有 checkpoint 边界；multi-piece 全量检查失败时会回滚 cache、
formed positions、repeat counts、aggregated context 和 collector-owned formation state。

### 7.2 Evaluation context

`StructureEvaluationContext` 组合当前 cell 的：

- request
- orientation
- piece/cell 坐标
- world state
- active `StructureMatchSession`
- collector

它也提供 transaction/probe/action API。cell 匹配、chain alternative、casing/tiered-casing/hatch 分支、custom ability-holder 分支都应通过这些 helper，
而不是手写 checkpoint/restore。

### 7.3 BlockWorldState

`BlockWorldState` 已从简单 boolean branch wrapper 扩展为和 session/context 同族的 transaction/probe/action API。

它负责：

- legacy `PatternMatchContext` checkpoint
- legacy global count map checkpoint
- legacy layer count map checkpoint
- legacy predicate branch transaction
- composed evaluation restore 的 world-state 部分

### 7.4 Probe 规则

以下方法语义上是 probe，不应该留下形成态 collector/context/count mutation：

- context-aware candidates
- `couldBeValid(...)`
- `getBlocksToPlace(...)`
- default survival-build 的“already valid”检查
- fallback `spawnHint(StructureEvaluationContext)`
- wrapper/no-placement adapter 的候选和 hint 转发

world placement、item source 消耗、hint particles 这类外部副作用仍然可能发生；rollback 只覆盖结构 evaluation state。

## 8. Check 与 Commit 流程

当前同步形成大致流程是：

1. 控制器初始化或重检时，通过 runtime resolver 得到 canonical `StructureDefinition` 和 compat view。
2. 控制器或 scheduler 构造 `StructureOperationRequest`。
3. `StructureRuntime` 调用 evaluator。
4. evaluator 创建 session/context/collector，执行 direct element 或 legacy adapter。
5. 结果归一化为 `StructureCheckResult`。
6. `MultiblockStructureAssembler` 准备 assembly/reassembly 信息。
7. `MultiblockStructureCommitter` 验证 `PreparedCommit`。
8. commit 成功后才发布 formed metadata、formed channels、part attachments、ability state 和 runtime failure clearing。

Commit 阶段必须保证：

- 不发布 stale result。
- part sharing 被验证。
- controller-specific ability filter 被验证。
- old/new parts 和 ability diff 有序执行。
- failed commit 不能覆盖上一次已成功 formed metadata。
- 成功 commit 才清除 last formation failure。

当前 `MultiblockStructureCommitter` 已经统一初始形成和 soft reassembly 的 validation-before-publication 边界。直接 async-result publication 的 generation 验证仍属于后续工作；当前 async checker 主要使用 snapshot match 去请求新的主线程 check。

## 9. Operation 语义

| Operation | 读世界/快照 | 收集形成态 | 修改世界 | 修改控制器形成态 |
|---|---:|---:|---:|---:|
| `CHECK` | 是 | 是 | 否 | commit 成功后 |
| `DIAGNOSE` | 是 | 隔离 | 否 | 只更新诊断 |
| `PREVIEW` | 否 | 否 | 否 | 否 |
| `HINTS` | 是 | 否 | 只允许 hint 可见效果 | 否 |
| `CREATIVE_BUILD` | 是 | 否 | 是 | 否 |
| `SURVIVAL_BUILD` | 是 | 否 | 是，受预算/物品限制 | 否 |
| `SNAPSHOT_CHECK` | 只读快照 | 是 | 否 | 后续 server-thread commit 或 fallback |
| `ITERATE` | 可选 | 否 | 否 | 否 |

Preview、build、hint 可以使用 disposable session 来解析 channel、分支和坐标，但这些 session 不能变成 formation session。

## 10. Orientation 与坐标

`StructureOrientation` 是当前统一方向值对象，目标是替代散落的 `front/up/flipped` 参数组合。

当前已经使用或开始使用 orientation-native path 的范围包括：

- async check token
- failure trace
- controller-facing evaluator check/iterate
- definition/check-state/AABB
- `StructurePiece` center/snapshot
- `StructureCompiler` snapshot closure
- template AABB/predicate facade
- repeat-group live/snapshot slice
- backtracking
- axis-line
- auto-build metadata
- multi-piece dirty/full check
- `MultiblockState` exact live/snapshot/axis-line traversal
- creative-build evaluator path
- `StructureCellTraversal` 入口（center + orientation + template-local offset）
- dynamic fixed piece / dynamic repeat group center resolution
- repeat-group slice check/build/hint
- multi-piece preview world-center resolution

仍然保留的 `front/up/flipped` 主要是：

- 兼容 facade
- `RelativeDirection` 低层输入
- addon-facing legacy auto-build adapter
- 仍从 controller state 读取方向的旧调用点

### 10.1 Shared cell walker

固定重复结构已经有共享 cell walker，用于 orientation-aware 坐标解析。当前已经覆盖：

- live/snapshot exact check
- snapshot axis-line fast path
- creative build placement
- survival build placement
- formed-block iteration
- hints
- single-piece preview projection
- repeat-group slice check/build/hint（通过 `StructureCellTraversal` 把 per-slice local offset 折入 cell traversal）
- multi-piece fixed piece check/build/hint

`StructureCellTraversal` 是 P0 后的统一坐标输入：它承载 piece center、`StructureOrientation` 和 template-local cell offset。
旧 `front/up/flipped` overload 仍保留给兼容 API，但新内部路径应优先构造 traversal/orientation 对象。

## 11. Preview、Hint 和 Build

### 11.1 Preview

当前 preview 仍保留 JEI/projector 所需的 `BlockInfo[][][]` 输出形状。

已有改进：

- single-piece preview 使用 preview-local orientation 加 shared cell traversal。
- fixed repetition preview 坐标包含 aisle offsets。
- repetition range validation 使用有效 repetition 数，而不是最终 loop index。
- multi-piece preview assembly 消费 positioned preview cells/predicates，不再重新手搓 bounds/center/predicate map。
- preview meta tile entities 尽可能朝向开放邻格，提高和实际放置世界的一致性。

### 11.2 Hints

Hint 请求已经通过 runtime request 返回 `StructureHintResult`。它能报告：

- active pieces
- traversals
- visited cells
- trigger-aware dispatch
- context-fallback dispatch

它不会声称客户端一定渲染了粒子。可见 hint 效果仍由 element 的 direct rendering 或 legacy hint 实现决定。后续如果要精确诊断 hint，应扩展 result 记录 per-element rendering outcome。

### 11.3 Creative build

Creative build 已经从 runtime request 进入，并用 operation-local build adapter 保留 legacy candidate selection。它更新 `CREATIVE_BUILD`
cell context，但不收集 formation requirements。

### 11.4 Survival build

Survival build 已有 request/runtime entry 和 `StructureBuildResult` 摘要。当前仍缺：

- placement budget 统一建模。
- item source accounting。
- consumed/required item report。
- partial placement resume 的更完整结果表达。
- rollback-safe item reporting。

默认 survival construction 的“already valid”检查已经通过 context probe 隔离，避免跳过已存在方块时污染 formation collector。

## 12. Snapshot 与异步安全

Snapshot check 只在所有 active piece 和 element 都支持 snapshot capability 时才有效。

当前规则：

- async task 携带 registration/runtime generation。
- async task/result 携带 orientation。
- async task/result 携带 covered chunk change revision。
- stale task/result 被 trace-reject，不能改变 formed 或 failure state。
- 某个 piece 或 element 不支持 snapshot 时，必须 fallback 到 live-world check。

未来如果允许 async result 直接进入 commit，则 commit token 至少要校验：

- controller identity。
- definition/runtime generation。
- orientation。
- snapshot 或 world-index revision。
- chunk coverage。

## 13. Channel、Ability 和 Requirement

### 13.1 Channel

V3 channel 模型由以下类型承载：

- `StructureChannel`
- `SimpleStructureChannel`
- `StructureChannelRegistry`
- `StructureChannelValues`
- `GTStructureChannels`
- `StructureTooltipBuilder`

HPCA 的 repeatable body length 已迁入 declarative aisle channel 模型，preview 和 build request 都能按 channel 选择结构长度。

### 13.2 Ability

`StructureOperationState` 当前有显式 ability-count 和 ability-part map，而不仅依赖 collected part 扫描。

这解决了两个问题：

- 一个 matched part 可以暴露多个 abilities。
- direct path 和 legacy path 混合时不能重复计数同一个 part。

`StructureMatchCollector` 在修改 state 前检查 maximum count limit。超限的 count/ability record 不应留下 speculative part、count 或 ability contributor。

### 13.3 Requirement

Deferred requirement collection 现在应跟随 canonical `match(StructureEvaluationContext)` 进入 cell transaction。失败 alternative 不应提交 requirement。

仍需要继续收敛的是低层 `collectRequirements(...)` 兼容 hook。它可以保留给显式调用者，但 formation matching 应优先使用 `match(...)`。

## 14. Diagnostics 与日志

### 14.1 Failure trace

`StructureFailureTrace` 是 runtime 上最近一次相关失败的稳定摘要。它应该描述：

- controller id 和坐标。
- operation kind。
- orientation。
- piece 名称。
- repeat index。
- local coordinate。
- world coordinate。
- expected element description / candidates。
- actual block/tile。
- missing ability 或 count deficit/excess。
- failure stage：match、requirement、capability、assembly、commit、stale async 等。

成功 preview/build 不应清除 formation failure。成功 committed check 才能清除 formation failure。

### 14.2 Debug trace

`StructureTrace` 和 debug config 负责时间线日志。trace event 应包含：

- controller id 和位置。
- formed state。
- operation。
- check path：definition、legacy-template、multi-piece、async、runtime。
- orientation。
- result。
- missing abilities。
- build result counts。
- pattern error position。
- formed metadata 和 channel values。

日志策略：

- 不确定行为先加 trace，再改逻辑。
- trace 必须受 debug gate 控制。
- 不在 hot traversal path 无条件输出高频日志。
- stored failure state 和 debug log 是不同用途，不能互相替代。

## 15. 兼容边界

兼容方向必须单向：

```text
legacy declaration -> LegacyElement / definition adapter -> V3 evaluator
new definition      -> direct compiled element             -> V3 evaluator
```

不应该出现：

```text
new direct element -> toPredicate() -> legacy matcher
```

保留的兼容 facade：

- `TraceabilityPredicate`
- `FactoryBlockPattern`
- `BlockPattern`
- `BlockPatternTemplate`
- legacy error accessor
- addon-facing build/template hook

新核心代码应该只把这些当输入/输出适配层，不要把新行为写成只能从 legacy hook 触达。

## 16. 当前已完成事项

当前代码已经完成或基本完成：

1. 新增 `StructureRuntime`，作为控制器结构状态入口。
2. 新增 `StructureOperationEvaluator`，作为 operation 执行薄边界。
3. 新增 `StructureOperationRequest`，并把 controller check、preview、creative build、survival build entry、hints、iteration 等入口接入 request-backed runtime method。
4. 新增 `StructureCheckResult`，同步 check 在 assembly/commit 前先归一化为不可变结果。
5. 新增 `StructureBuildResult` 和 `StructureHintResult`，让 build/hint 有 operation summary。
6. 新增 `StructureEvaluationContext`、`StructureMatchCollector`、`StructureOperationState`、`StructureMatchSession`。
7. direct element 不再必须通过 `toPredicate()` 执行。
8. block/air/any/self/chain/wrapper/hatch/casing/tiered casing/coil 等核心元素已有 direct runtime path。
9. hatch direct path 仍运行 legacy predicate guard，保持旧 hatch filter 行为。
10. formed metadata、channels、missing abilities、last failure 已收归 runtime 所有。
11. 初始形成和 soft reassembly 共享 `MultiblockStructureCommitter` 的 validation-before-publication 边界。
12. operation state 显式记录 parts、counts、ability counts、ability contributors、active positions、requirements。
13. collector 在写入前检查最大 count/ability 限制。
14. async snapshot task/result 有 generation/orientation/chunk revision stale rejection。
15. `StructureOrientation` 已接入主要 check、snapshot、trace、AABB、多 piece、exact-check、axis-line 和 creative-build 路径。
16. controller、preview-helper、projector、multiblock-builder 等 runtime-owned 调用点通过 `StructureRuntime` request method 进入。
17. 单 piece fixed repetition cell walker 已服务 creative build、formed iteration、hints、single-piece preview。
18. multi-piece preview assembly 改为 positioned preview cell/predicate 输入。
19. preview meta tile entity 尽量朝向开放邻格。
20. fixed-repetition preview 坐标和 repetition validation 已修正。
21. HPCA body length 迁入 declarative aisle channel。
22. direct chain/casing/tiered-casing/hatch/custom ability-holder matches 已使用 checkpoint/transaction，失败分支不泄漏 collector/context/count。
23. legacy `PatternMatchContext`、global/layer count map 具备 checkpoint/transaction/probe helper。
24. `TraceabilityPredicate` simple-branch alternatives 具备 exception-safe checkpoint。
25. wrapper callbacks 通过统一 transaction helper 执行。
26. advisory 方法按 probe 处理：candidate、`couldBeValid`、`getBlocksToPlace`、fallback hint。
27. creative/survival placement bridge 隔离结构 evaluation state，保留 world/item side effect。
28. no-placement adapter 显式剥离 placement capability，并隔离 candidate/hint forwarding。
29. `StructureMatchSession.tryFork(...)` 已用于多 piece dirty check、repeat-group candidate、definition fixed-piece check 等 candidate commit。
30. raw session restore 已收窄到内部或命名边界，调用点使用 transaction/probe/action。
31. `BlockWorldState` 已扩展为完整 transaction/probe/action API 家族。
32. `StructureCellTraversal` 已成为 fixed traversal 的统一坐标输入，承载 center、orientation 和 template-local offset。
33. multi-piece fixed piece、repeat group、dynamic-offset piece 坐标入口已压到 orientation/cell API。
34. repeat group live/snapshot/build/hint 共用 `visitRepeatOffsets(...)` 和 exact traversal。
35. live/snapshot 坐标等价测试已覆盖 fixed traversal、repeat group 和 flipped orientation。
36. formation collector/context mutation 失败回滚测试已覆盖 part、count、channel、tier、active position 和 legacy context key。
37. multi-piece 全量检查失败会回滚 piece runtime、formed positions、repeat reps、cache 和 aggregated context。

## 17. 已知缺口

当前代码仍存在这些未收口点：

1. survival build 虽有 request/result，但 item accounting、budget、partial placement report 还不完整。
2. hint result 还不能表达每个 element 是否实际渲染了可见提示。
3. ability diagnostics 仍跨 session、runtime、controller 和 legacy error object，需要更统一的 structured failure。
4. 部分 addon-facing 或兼容 path 仍使用 `front/up/flipped` facade；内部新路径应优先使用 `StructureOrientation` / `StructureCellTraversal`。
5. dynamic charcoal-pile 和 Godforge/controller-module 路径仍是主要迁移对象。
6. controller lifecycle 仍有 formed flag、attached parts、ability instances 等状态留在控制器/piece runtime。
7. `PatternMatchContext` 仍是 legacy adapter，部分旧调用点还依赖 string-keyed context。
8. `TraceabilityPredicate` 仍是 addon 兼容所必需，不能直接移除。
9. async checker 当前更多用于 snapshot precheck/fallback，还不是完整 async commit pipeline。
10. `StructureWorldIndex` 或等价 dirty-index runtime 边界尚未建立。
11. in-game diagnostic command / UI 仍未完成。

## 18. 路线图

### P0: 统一 traversal 与副作用边界（已完成）

目标：让 live check、snapshot check、single-piece、多 piece、repeat group、preview/hint/build/iteration 尽量共享同一套坐标和 cell traversal。

完成内容：

- `MultiblockState` 的 live single-piece check 和 snapshot single-piece check 已收敛到同一个 fixed-structure cell traversal helper。
- fixed traversal 的 cell 构造现在集中到同一处，统一处理 local coordinate、template offset、`StructureOrientation` 和 world position 投影。
- `StructureCellTraversal` 统一承载 center、orientation 和 template-local offset。
- preview、hint、creative/survival build、formed-block iteration 复用同一 fixed cell visitor。
- multi-piece fixed piece、repeat group 和 dynamic-offset piece 的坐标入口已压到 orientation/cell API。
- dynamic fixed piece 和 dynamic repeat group 使用 orientation-native center resolution；旧 enum overload 只作为兼容适配。
- `RepeatGroupPiece` 的 slice check 通过 `checkPatternAtExact(...)` / `checkPatternAtSnapshotExact(...)` 间接复用该 traversal；snapshot axis-line fast path 也复用了同一 slice visitor。
- `RepeatGroupPiece` 的 repeat-axis offset 枚举已收敛到 `visitRepeatOffsets(...)`，live/snapshot/build/hint 不再各自维护 single-axis/multi-axis 分支和 odometer。
- `RepeatGroupPieceTest` 覆盖了单轴、多轴笛卡尔顺序、零轴兜底和 visitor 早停。
- `StructureEvaluationContext.Operation` 承载 operation effect policy，集中描述是否读 world/snapshot、是否修改 world、是否发 hint、是否收集 formation state。
- `StructureOperationPolicyTest` 覆盖了 operation policy 和 `StructureOperationRequest.Kind` 到 evaluation operation 的映射。
- `StructureEvaluationContext.getCollector()` 已按 operation policy 创建 collector；creative build、survival build、hint、preview、iteration 这类非 formation 操作不会通过 collector 写入 part/count/ability/channel/tier/active-casing 形成态。
- `StructureOperationPolicyTest` 也覆盖了 formation collector 会提交状态，而 non-formation collector 只返回操作成功、不提交形成态。
- multi-piece full check 通过 `StructureMatchSession` transaction 执行，并在失败时回滚 `PieceRuntime` / `PieceRuntimes` / `MultiblockState` checkpoint。
- `StructureTraversalBoundaryTest` 覆盖 live/snapshot 坐标等价、repeat group flipped traversal、multi-piece 失败 runtime 回滚，以及 part/count/channel/tier/context mutation 失败回滚。

完成标准：

- 同一结构在 live 和 snapshot 下使用一致的坐标解析。
- failed alternative 不留下 part、count、ability、channel、tier、active casing position 或 legacy context state。
- preview/hint/build 不污染 formation state。

P0 边界说明：这里完成的是 traversal 坐标入口和 formation side-effect 边界；survival item accounting、diagnostic UI、async commit pipeline
和 dirty-index runtime 属于后续 P1/P2 工作。

### P1: 补完整 Survival Build

目标：让 survival build 和 check 一样有清晰 request/session/result。

具体计划：

1. 扩展 `StructureBuildResult`，记录 placement budget、required items、consumed items、missing items。
2. 明确 partial placement 和 resume 的结果语义。
3. 把 item source accounting 做成 rollback-safe summary。
4. 统一 direct element placement 和 legacy predicate fallback 的 candidate selection。
5. 给 insufficient items、partial placement、already-valid probe、branch fallback 补测试。

完成标准：

- 生存建造可以稳定报告缺什么、放了什么、还差什么。
- probe 和候选失败不会污染 formation collector。
- creative/survival build 共享尽可能多的 placement decision 代码。

### P1: 统一 Failure Diagnostics

目标：让结构失败可诊断，而不是只能看 generic mismatch 或 legacy pattern error。

具体计划：

1. 把 missing ability、count limit、capability unsupported、assembly rejection、commit rejection 都归入 `StructureFailureTrace`。
2. 定义 flipped/non-flipped 都失败时的 failure selection policy：优先保留最接近真实原因、进度最深、tie-break 稳定的失败。
3. 让 ability diagnostics 使用 operation-owned ability counts，而不是多处重新扫描。
4. 增加 `/gt_structure_trace <pos>` 或等价开发命令。
5. 加 debugStructureTrace 的低频生命周期日志，不在 cell hot path 无门控刷屏。

完成标准：

- 常见失败能指出 piece/cell/world pos 和 expected/actual。
- 缺 ability 不会被另一方向的普通 block mismatch 覆盖。
- commit rejection 不会覆盖更有用的 current mismatch，除非它就是最新形成失败。

### P1: Orientation 收口

目标：把剩余 `front/up/flipped` 兼容参数压到 API 边缘。

具体计划：

1. preview/hint placement 内部改成 `StructureOrientation`。
2. addon-facing auto-build adapter 只在入口把旧参数转换为 orientation。
3. `RelativeDirection` 继续作为低层方向描述，但不再承担完整结构朝向状态。
4. 清理 template iteration、AABB、axis-line 中剩余的旧参数穿透。

完成标准：

- 新 runtime/evaluator API 不再接收三散参数。
- 旧参数只在 legacy facade 出现。

### P2: 动态结构迁移

目标：把剩余动态路径也纳入 V3 request/runtime。

具体计划：

1. 梳理 charcoal pile 的动态 check/preview/build 路径。
2. 梳理 Godforge controller/module 的结构声明、preview 和 formed state。
3. 为动态生成 definition/template 的路径建立 disposable runtime 或 dynamic definition cache 规则。
4. 对动态 channel/size/condition 记录 failure trace。

完成标准：

- 动态结构不绕过 operation request。
- preview/build/check 的 channel 和 orientation 一致。
- 动态失败也能生成稳定 failure trace。

### P2: Dirty Index 与 Scheduler

目标：让世界方块变化只标记 dirty，由 scheduler 决定如何检查。

具体计划：

1. 新增 `StructureWorldIndex` 或等价 runtime dirty-index。
2. world block callback 只登记 dirty controller/piece/chunk。
3. scheduler 根据 dirty 范围选择 piece check、full live check、snapshot check 或 polling fallback。
4. async snapshot result 只作为 scheduler 输入，最终 commit 仍走 server-thread validation。

完成标准：

- block update 不直接嵌套触发结构检查。
- dirty-piece check 能覆盖常见局部变化。
- stale async result 永不改变 formed/failure state。

### P2: Controller Orchestration 清理

目标：进一步缩小控制器基类中的结构协调逻辑。

具体计划：

1. 继续把 assembly、registration、previews、channels、client hooks、scheduler 逻辑迁入 helper。
2. 明确哪些 helper 是内部实现，哪些可以成为 addon API。
3. 把 runtime state publication 和 controller callback 顺序写成固定 contract。
4. 保留 legacy public method，但让它们只是 request/facade。

完成标准：

- 控制器基类不再直接协调 traversal 分支。
- addon 看到的兼容 API 稳定，核心内部走 V3。

### P3: 兼容层瘦身

目标：在 addon 迁移可接受后，减少 legacy facade 的核心影响。

具体计划：

1. 写 addon migration guide：如何从 `FactoryBlockPattern` / `TraceabilityPredicate` 迁到 `StructureDefinition` / direct element。
2. 标记只用于兼容的 hook 和 facade。
3. 为 direct element tooltip、preview、hint 提供替代 API。
4. 最后再考虑移除或降级旧 facade。

完成标准：

- 新核心结构不需要 legacy hook。
- 移除 facade 只会影响 addon 迁移，不会影响 V3 evaluator 设计。

## 19. 测试与验证矩阵

每次迁移都应覆盖：

- 单 piece 固定结构。
- 单 piece repeatable 结构。
- 固定多 piece。
- conditional piece。
- dynamic-offset piece。
- repeat group。
- 正常和 flipped orientation。
- 每个合法 front/up 组合。
- live-world 与 snapshot 等价性。
- 初始形成、still-valid recheck、soft reassembly、invalidation。
- event-driven dirty check、polling fallback、async check。
- required ability、optional ability、grouped ability、min/max count、shared part。
- uniform 与 non-uniform tier/channel capture。
- preview/build 使用 default/min/max/explicit channel value。
- creative build。
- survival build insufficient items。
- survival build partial placement resume。
- legacy `FactoryBlockPattern`。
- legacy custom predicate。
- direct element。
- mixed chain direct + legacy alternative。

高风险事务测试应刻意在以下写入后失败，并断言失败分支没有残留：

- collected part。
- generic count。
- ability count。
- ability contributor。
- active casing position。
- channel value。
- tier value。
- deferred requirement。
- legacy `PatternMatchContext` key。
- legacy global count。
- legacy layer count。

## 20. V3 完成标准

V3 可以认为成为主运行时时，需要同时满足：

1. 控制器生命周期只提交 request 和 commit result，不直接协调 traversal 分支。
2. 一个 traversal engine 支持 single-piece、多 piece、live check 和 snapshot check。
3. preview、hint、creative build、survival build、iteration 共享 coordinate/orientation walker。
4. direct element 执行不依赖 predicate conversion。
5. formation effect 在 commit 成功前都是 operation-local。
6. 失败分支和非 check operation 不能污染 formation state。
7. snapshot capability 和 stale-result handling 显式且有测试。
8. `StructureFailureTrace` 能定位 failing piece/cell 或 deferred requirement。
9. 新核心 multiblock 只声明 `StructureDefinition`。
10. legacy facade 的移除只会变成 addon migration 问题，而不是 V3 evaluator 需要重设计。
