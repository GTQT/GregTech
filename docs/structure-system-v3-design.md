# Structure System V3 Current Design

**Implementation snapshot:** 2026-06-14  
**Scope:** `gregtech.api.pattern`、`gregtech.api.pattern.element`、
`gregtech.api.metatileentity.multiblock` 以及当前控制器接入代码。

本文只描述当前代码已经实现的结构系统边界。历史迁移计划、阶段编号和未落地 API 草案不再保留；
需要判断行为时，以代码和测试为准。

## 1. 当前结论

Structure System V3 当前提供的是统一声明、运行时所有权、事务化匹配、typed operation result、
controller commit 边界和调度边界。它已经覆盖 formation check、active-graph recheck、增量 recheck、
snapshot precheck、build、hint、preview 和 iterate，但执行层仍保留若干兼容适配。

已经成立的事实：

- 每个控制器最终都会解析出一个 `StructureDefinition`。
- `StructureDefinition` 编译为共享、无控制器状态的 `MultiPiecePattern` 和 `PieceTemplate`。
- `PieceTemplate` 的 canonical cell storage 是 `CompiledStructureElement`；legacy predicate map 通过
  `PieceTemplateLegacyView` 按需投影。
- 每个控制器持有自己的 `StructureRuntime` 和 `PieceRuntimes`；single-template shape 额外持有
  `MultiblockState` 兼容旧 API。
- formation lifecycle 的 canonical 状态是 `StructureRuntime.getLifecycleState()` 返回的
  `StructureLifecycleState`。
- controller 上的 formed flag、part list 和 ability instances 是 legacy/network projection，不是独立真相。
- 同步 check、active-graph、incremental 和 async precheck 共用 `StructureCommitToken` 做 stale 校验。
- incremental eligibility 会消费 typed condition dependency 和 direct element `getDependencies()`，并把
  external dependency snapshot 映射回 dirty roots。
- standard external dependency 覆盖 controller mode、channel values、configuration 和 upgrades。
- 世界 dirty index 只保存索引和 dirty lease；是否 polling、event-driven 或 async 由
  `StructureSchedulerPolicy` 决定。
- 异步检查只做 snapshot precheck，命中后仍在主线程执行 live check，异步结果不直接发布 formed state。
- debug 模式下有低频 shadow validation，用 full check oracle 校验 incremental result。

仍然是当前实现限制的事实：

- `StructureOperationEvaluator` 是委托层，会路由到 `StructureCheckState`、`MultiPiecePattern`、
  `MultiblockState` 和 preview/build helper；它不是唯一 traversal engine。
- legacy `TraceabilityPredicate`、`PatternMatchContext`、`BlockPatternTemplate` 和 `BlockPattern`
  仍是 addon 兼容 API。
- event-driven active-graph recheck 会重扫完整 active graph；它不是“只检查变更 piece”。
- incremental recheck 只在 definition eligibility、committed graph、orientation 和 dirty roots 满足条件时使用；
  其它情况会 fallback 到 full 或 active-graph 路径。

## 2. 核心不变量

共享声明不可变：

- `StructureDefinition`
- `MultiPiecePattern`
- `StructurePiece`
- `RepeatGroupPiece`
- `PieceTemplate`
- `CompiledStructureElement`

控制器可变状态只属于单个 controller：

- `StructureRuntime`
- `StructureLifecycleState`
- `MultiblockState`
- `PieceRuntimes`
- `PieceRuntime`
- `StructureDirtyState`

单次操作状态只能存在于 operation 生命周期内：

- `StructureMatchSession`
- `StructureOperationState`
- `PatternMatchContext`
- `BlockWorldState`
- `StructureEvaluationContext`
- `PieceRuntimes` candidate publication

匹配成功只表示得到一个可提交结果。形成状态只能由 `MultiblockStructureCommitter` 在服务端线程发布。
任何检查、build、hint、preview、iterate 都不能直接修改 controller formed lifecycle。

失败分支必须回滚以下内容：

- part 和 ability 收集；
- generic count、global count 和 ability count；
- channel/tier 值；
- active casing position；
- deferred requirement；
- legacy context key；
- piece cache、formed positions、repeat counts；
- contribution builder 状态。

## 3. 架构分层

| 层 | 主要类型 | 当前职责 |
|---|---|---|
| 声明层 | `StructureDefinition`、`IStructurePiece` | 定义 piece、条件、偏移、重复和 ability 限制。 |
| 编译层 | `StructureCompiler`、`PieceTemplateCompiler` | 编译为 `MultiPiecePattern`、`StructurePiece`、`PieceTemplate` 和 compiled element。 |
| 元素层 | `IStructureElement`、`CompiledStructureElement`、`StructureElementPreview`、`StructureDependency` | 单元格匹配、requirement、候选、preview、hint、placement 和 typed dependency 声明。 |
| 运行时 | `StructureRuntime`、`StructureLifecycleState`、`PieceRuntimes`、`MultiblockState` | 保存 controller 私有状态、committed lifecycle、dirty roots 和 failure。 |
| 操作入口 | `StructureOperationRequest`、`StructureOperationEvaluator` | 校验 request kind 并分派到当前实现。 |
| 操作事务 | `StructureMatchSession`、`StructureEvaluationContext`、`BlockWorldState` | 提供 transaction、probe、fork、typed data 和 collector state。 |
| 操作结果 | `StructureCheckResult`、`StructureSnapshotResult`、`StructureBuildResult`、`StructureHintResult`、`StructurePreviewResult`、`StructureIterateResult` | 在执行层和 controller lifecycle 之间传递不可变结果。 |
| 提交流程 | `MultiblockStructureOperations`、`MultiblockStructureAssembler`、`MultiblockStructureCommitter`、`MultiblockStructureRegistration` | 固定 check、assemble、commit、registration 的顺序。 |
| 调度层 | `MultiblockStructureCheckScheduler`、`StructureSchedulerPolicy`、`MultiblockWorldData`、`StructureWorldIndex`、`StructureExternalDependencies`、`AsyncStructureChecker` | first tick、polling、dirty event、external dependency wakeup、incremental/active/full lease、snapshot precheck。 |
| 兼容层 | `BlockPattern`、`BlockPatternTemplate`、`PieceTemplateLegacyView`、`TraceabilityPredicate`、`PatternMatchContext` | 保留旧 addon 和旧工具入口，并桥接到当前 runtime。 |

## 4. 声明与编译

`MultiblockControllerBase.reinitializeStructurePattern()` 的解析顺序是：

1. `createStructureDefinition()`
2. `createMultiPiecePattern()`，再包装为 `StructureDefinition`
3. `createStructureTemplate()`，再包装为 `StructureDefinition`
4. 默认 `createStructureTemplate()` 仍可调用已废弃的 `createStructurePattern()`

因此，controller runtime 初始化后总能取得 `StructureDefinition` 和 compiled `MultiPiecePattern`。
新控制器应优先直接返回 `StructureDefinition`。

`StructureDefinition` 当前支持：

- fixed named piece；
- conditional piece；
- X/Y/Z 单轴 repeat；
- multi-axis repeat group；
- repeat channel；
- `OffsetMode`；
- 基于先前 piece 的 dynamic offset；
- definition 级 ability min/max；
- ability group min/max；
- legacy `FactoryBlockPattern` / `BlockPatternTemplate` 导入。

编译后的关系：

```text
StructureDefinition
  -> MultiPiecePattern
       -> StructurePiece / RepeatGroupPiece / DynamicOffsetPiece
            -> PieceTemplate
                 -> CompiledStructureElement[][][]
                 -> PieceTemplateLegacyView
                      -> TraceabilityPredicate[][][] compatibility view
```

`StructureDefinition.getCompiledPattern()` 懒编译并缓存结果。`StructureDefinition.getEligibilityPlan()`
基于 compiled pattern 生成 dependency/eligibility 计划，供 incremental evaluator 和 fallback 诊断使用。

single-template 资格只表示该 definition 可以投影为一个 `BlockPatternTemplate` 和 `MultiblockState`。
它用于旧 API、single preview/hint/build/iterate 等兼容路径，不表示 formation check 会绕过
`StructureDefinition`。

## 5. Runtime 所有权

| 对象 | 生命周期 | 说明 |
|---|---|---|
| `StructureRuntime` | controller 级 | operation facade，持有 lifecycle state、dirty roots、committed graph、metadata/channel、last failure。 |
| `StructureLifecycleState` | controller commit 级 | canonical formed snapshot，包含 formed flag、parts、abilities、metadata、channels、committed graph。 |
| `PieceRuntimes` | controller 或单次检查级 | controller 实例保存最近成功 commit 的 piece 状态；检查时使用 candidate runtimes。 |
| `MultiblockState` | controller 级 single-template 兼容状态 | 保存旧单模板 cache、repeat、error 和 traversal scratch。 |
| `CommittedStructureGraph` | controller commit 级 | 保存 result table、aggregate、position index、runtime publication、orientation、external dependency snapshot。 |

server side `MultiblockControllerBase.isStructureFormed()` 优先读取 runtime lifecycle state。client side 和旧网络
同步仍读取 controller 的 `structureFormed` projection。

`MultiblockStructureCommitter` 成功提交时调用：

1. `StructureCheckResult.validatePieceRuntimePublication(...)`
2. part attach/detach；
3. `StructureCheckResult.publishPieceRuntimes(...)`
4. `StructureRuntime.publishLifecycleState(...)`
5. `MultiblockControllerBase.projectStructureLifecycle(...)`
6. `formStructure(FormedStructureView)` / legacy bridge；
7. formed position registration。

`invalidateStructure()` 会清空 runtime formed state、注销 world index、detach parts，并把空 lifecycle projection
写回 controller 字段。

新代码不应直接修改 `structureFormed`、`multiblockParts` 或 `multiblockAbilities`。这些字段只服务旧访问器、
网络同步和旧 callback。

## 6. Operation API

`StructureOperationRequest.Kind` 当前包含：

| Kind | Evaluation operation | 读世界 | 修改世界 | Formation state |
|---|---|---:|---:|---|
| `CHECK` | `MATCH_WORLD` | 是 | 否 | 可产生 commit result |
| `SNAPSHOT_CHECK` | `MATCH_SNAPSHOT` | 读 snapshot | 否 | 只产生 precheck signal |
| `PREVIEW` | `PREVIEW` | 否 | 否 | 否 |
| `HINT` | `HINT` | 是 | 仅 hint side effect | 否 |
| `CREATIVE_BUILD` | `CREATIVE_BUILD` | 是 | 是 | 否 |
| `SURVIVAL_BUILD` | `SURVIVAL_BUILD` | 是 | 是 | 否 |
| `ITERATE` | `ITERATE` | 是 | 否 | 否 |

runtime entry points：

- `StructureRuntime.check(...)`
- `StructureRuntime.checkSnapshot(...)`
- `StructureRuntime.checkActiveGraph(...)`
- `StructureRuntime.checkIncremental(...)`
- `StructureRuntime.buildSingle(...)` / `buildPiece(...)` / `buildAllPieces(...)`
- `StructureRuntime.hintSingle(...)` / `hintAllPieces(...)`
- `StructureRuntime.previewSingleResult(...)` / `previewMultiPieceResult(...)`
- `StructureRuntime.iterateSingleResult(...)` / `iterateMultiPiece(...)`

typed result：

| Result | 用途 |
|---|---|
| `StructureCheckResult` | live full/active/incremental check 的 formation candidate 或 failure。 |
| `StructureSnapshotResult` | async snapshot precheck outcome，不能提交。 |
| `StructureBuildResult` | creative/survival build 的 placed/missing/consumed/resume 信息。 |
| `StructureHintResult` | hint 渲染的 rendered/skipped/failed 汇总。 |
| `StructurePreviewResult` | single-template 和 multi-piece preview 的统一结果。 |
| `StructureIterateResult` | read-only iteration 的统一结果。 |

`StructureOperationEvaluator` 会校验 request kind。错误 kind 会在入口处失败，而不是进入具体 evaluator 后才产生
隐式行为。

## 7. Check 与 Commit

同步 formation check 的主路径：

```text
MultiblockControllerBase.checkStructurePattern()
  -> MultiblockStructureOperations.checkStructurePattern()
  -> StructureCommitToken.captureForCheck(controller)
  -> StructureRuntime.check(CHECK request)
  -> StructureOperationEvaluator.check()
  -> StructureCheckState.check()
  -> StructureCheckResult
  -> MultiblockStructureCommitter.applyCheckResult(result, token)
  -> MultiblockStructureAssembler.prepare()
  -> runtime lifecycle publication
  -> controller projection
  -> world index registration
```

definition full check 使用 `StructureCheckState`：

1. 创建 operation-owned `PieceRuntimes`。
2. 创建跨 piece 的 `StructureMatchSession`。
3. 按声明顺序解析 active piece。
4. 根据 prior metadata 解析 dynamic offset。
5. 对 fixed piece、repeat group 和 conditional piece 执行匹配。
6. 收集 `StructureOperationState`、legacy context、metadata、ability counts、channel values。
7. 成功时生成 runtime publication、result table 和 contribution aggregate。

如果 definition 不满足 contribution eligibility，普通 `check(...)` 会进入 active-graph fallback，并在
`StructureCheckResult` 上携带 eligibility/fallback trace context。

commit token 会校验：

- runtime generation；
- lifecycle generation；
- world；
- controller position；
- orientation；
- async precheck 的 optional change snapshot；
- async precheck 不允许 already-formed controller。

stale result 会被 trace 并丢弃，不记录 last failure、不 invalidate、不发布 formed state。

非 stale failure 会写入 `StructureRuntime.recordCheckFailure(...)`。如果 controller 原本已经 formed，
failure 会触发 `invalidateStructure()`。

## 8. Active-Graph 与 Incremental

active-graph recheck 是完整 active graph 重新匹配，不是 per-piece 局部匹配。它使用新的 candidate
`PieceRuntimes`，成功后才发布到 controller runtimes。

incremental recheck 的入口是 `StructureRuntime.checkIncremental(...)`。它要求：

- definition 存在；
- `StructureEligibilityPlan` eligible；
- runtime 有 `CommittedStructureGraph` baseline；
- 当前 orientation 与 baseline orientation 一致；
- runtime 有 pending dirty roots 或 external dependency changes。

`StructureDependencyCompiler` 当前会从三类位置收集 incremental dependency：

- dynamic offset / repeat group anchor，自动产生 center/repetition dependency；
- `StructureCondition.dependencies()`，用于 typed condition；
- direct `IStructureElement.getDependencies()`，用于元素自身读取 typed contribution、先前 piece metadata
  或 external controller state 的场景。

direct element 默认没有额外 dependency。`CompiledStructureElement` 会转发 source element 的 dependency；
`WrapperElement` 只有在没有 callback/lazy supplier 时才透明转发，callback/lazy wrapper 仍视为 opaque。
`ChainElement` 会合并 child dependencies，并按 child 中最保守的 `StructureIncrementalSupport` 上报；
包含 legacy/opaque child 的 chain 不会绕过 eligibility fallback。

满足条件时，incremental evaluator 从 committed graph 复制 baseline result table/runtime publication，只重算
dirty roots 及 dependency closure 中需要重算的 piece，再 fold 成新的 aggregate 和 graph publication。

不满足条件时，incremental path 返回带 fallback reason 的 full/active fallback result。常见原因包括：

- definition 不 eligible；
- 没有 baseline；
- orientation 变化；
- 没有 dirty root；
- baseline/result table 不完整。

debug `debugStructureCheck` 打开时，`StructureShadowValidator` 会对成功的 incremental result 低频抽样执行
一次 full check oracle。shadow validation 不发布 lifecycle、不替换结果，只在 mismatch 时输出 warn。
比较内容包括 result table semantic fingerprint、aggregate values、parts、ability counts、ability parts、
variant active blocks、formed metadata 和 channel values。

当前 world event 对 committed graph 注册的结构会通过 `StructurePositionIndex` 把 changed position 映射为
dirty root piece。旧注册路径仍会使用 piece runtime dirty 标记并走 active-graph。

## 9. Dirty Index、Scheduler 与 Async

`StructureWorldIndex` 按 world 保存：

- chunk 到 controller 的索引；
- controller formed positions；
- optional `StructurePositionIndex`；
- controller 到 `MultiPiecePattern`；
- pending recheck；
- chunk change revision；
- event suppression controller；
- last changed tick。

block change callback 只做 storage 更新：

1. 更新 changed chunk revision；
2. 查找拥有该 position 的 formed controller；
3. 对 committed graph 注册路径标记 runtime dirty roots；
4. 对旧 piece runtime 注册路径标记 piece dirty；
5. 把 controller 加入 pending recheck。

非方块状态变化通过 external dependency snapshot 进入同一 dirty lease：

- `StructureExternalDependencies.CONTROLLER_MODE`
- `StructureExternalDependencies.CHANNEL_VALUES`
- `StructureExternalDependencies.CONFIGURATION`
- `StructureExternalDependencies.UPGRADES`

这些 key 的 snapshot 保存在 `CommittedStructureGraph` 中。scheduler 在消费 event-driven lease 前会调用
`MultiblockControllerBase.enqueueChangedStructureExternalDependencies()`，比较当前 snapshot 与 committed snapshot，
并通过 `StructureRuntime.rootsForChangedExternalDependencies(...)` 找到受影响 root。若存在 root，
`MultiblockWorldData.enqueueDirtyRoots(...)` 会把它们加入 runtime dirty state 并唤醒 scheduler。

controller 基类提供 generation-backed snapshot hooks：

- `getStructureControllerModeSnapshot()` / `getStructureControllerModeValue()`
- `getStructureChannelDependencySnapshot()` / `getStructureChannelDependencyValue()`
- `getStructureConfigDependencySnapshot()` / `getStructureConfigDependencyValue()`
- `getStructureUpgradeDependencySnapshot()` / `getStructureUpgradeDependencyValue()`
- `notifyStructureControllerModeChanged()`
- `notifyStructureChannelsChanged()`
- `notifyStructureConfigChanged()`
- `notifyStructureUpgradesChanged()`

标准 snapshots 会冻结嵌套 Map/List/Set/数组，避免 committed baseline 被可变对象引用污染。
`setDelayCheck(...)`、`setDelayStructureCheckStandby(...)`、`setDelayStructureCheckWork(...)`、核心
`setWorkingEnabled(...)`、voiding mode、distinct/batch/recipe-lock/energy-warning、generator overflow、
advanced thread、MultiMap recipe-map index、Large Boiler throttle/type，以及 Godforge ring/renderer/upgrade
状态会显式 bump 对应 generation 并尝试 enqueue。
共享层只提供 controller mode、channel、configuration 和 upgrade snapshot 通道；机器私有字段停留在
拥有者类或对应抽象族中，例如 MultiMap/AdvanceMultiMap 的 recipe-map 选择、Large Boiler 的 throttle/type，
以及 Godforge 的 ring/renderer/upgrade state。
尚未迁移的 controller/addon 仍可依靠 scheduler 的 snapshot 比较兜底，但新代码应在状态改变处显式调用 notify。

`DirtyCheckLease` 是 storage lease，不是 scheduler policy。它可能返回：

- `UNREGISTERED`
- `CLEAN`
- `DEFERRED`
- `ACTIVE_GRAPH`
- `INCREMENTAL`
- `FULL`

`MultiblockStructureCheckScheduler` 每 tick 按 controller 的 `StructureSchedulerPolicy` 处理：

1. first tick live check；
2. formed controller event-driven lease；
3. unformed controller async precheck；
4. polling fallback。

默认 policy 保留原行为：配置允许时 formed 结构消费 event-driven dirty，未 formed 结构在 definition 支持
`SNAPSHOT_MATCH` 时进入 async，最后按 working/standby interval polling。

async check 流程：

1. scheduler 将未 formed controller 注册到 `AsyncStructureChecker`。
2. 主线程根据 definition AABB 捕获 `BlockStateSnapshot` 和 chunk change snapshot。
3. async thread 使用 disposable `StructureRuntime.fromDefinition(...)` 执行 `SNAPSHOT_CHECK`。
4. 主线程处理 result 时重新验证 async registration generation 和 `StructureCommitToken`。
5. snapshot matched 时调用 `controller.checkStructurePattern()` 做 live confirm。

async result 永远不直接 publish lifecycle 或 failure。超过 snapshot volume 上限的结构进入主线程 fallback queue。

## 10. Element、Session 与 Transaction

`IStructureElement` 是 direct element 的单元格契约：

- `match(StructureEvaluationContext)` 是 canonical formation match 入口；
- `check(World, BlockPos, PatternMatchContext)` 是 legacy low-level check；
- `getCapabilities()` 声明 snapshot 等 capability；
- `getIncrementalSupport()` 声明 contribution eligibility；
- `getDependencies()` 声明会影响本 element match/contribution 的 typed piece 或 external input；
- `getPreview(...)` 提供 preview/build metadata；
- `survivalPlaceBlock(...)` 提供 survival build result；
- `spawnHintWithResult(...)` 提供 hint outcome。

direct element 如果读取先前 piece 的 contribution、repeat/center metadata、controller mode、channel/config/upgrade
等非方块输入，必须通过 `StructureDependency` 显式声明。未声明依赖的 direct element 只会因自身 watched position
dirty 而重算；读取 opaque legacy context 或 callback side effect 的 element 应保持 opaque support，避免错误复用。

`CompiledStructureElement` 把 element 与 template position、symbol、candidate metadata 等编译信息绑定。
`StructureEvaluationContext` 保存 operation、world/snapshot、position、legacy context、session、controller context
和 auto-place 环境。

`StructureMatchSession` 是跨 piece 的事务状态：

- `fork()` / `tryFork(...)` 用于 alternative branch；
- `transaction(...)` 用于失败回滚；
- `probe(...)` 用于 advisory check，不提交副作用；
- typed data 通过 `StructureSessionKey` 保存；
- collector state 通过 `StructureOperationState` 保存；
- contribution state 通过 `StructureContribution.Builder` 捕获。

新增 direct element 应优先使用 context-aware API，不应把 `toPredicate()` 当作 runtime 执行入口。

## 11. Build、Hint、Preview 与 Iterate

build：

- creative build 和 survival build 都通过 `StructureOperationRequest` 进入 runtime；
- survival build 使用 `StructureBuildResult` 汇总 placement budget、consumed/missing items、partial/resume 状态；
- 单模板路径可走 `MultiblockState`；
- multi-piece 路径可按单 piece 或 all pieces 执行。

hint：

- 旧 `spawnHints...` 方法仍存在；
- typed hint 返回 `StructureHintResult`，记录 rendered、skipped、failed；
- hint 不写 formation state。

preview：

- single-template preview 返回 `BlockInfo[][][]` 或 `StructurePreviewResult`；
- multi-piece preview 通过 `MultiPiecePreviewAssembler` 和 `StructurePreviewResult`；
- direct preview metadata 优先于 legacy predicate candidates。

iterate：

- single-template iteration 返回 world position 到 `BlockInfo` 的映射或 typed result；
- multi-piece iteration 返回 formed position set/result；
- iterate 是 read-only operation。

## 12. Metadata、Channel 与 Compatibility

`FormedStructureMetadata` 保存 successful check 产生的 per-instance metadata，包括 repeat、channel 和 piece center
相关信息。它会随 lifecycle state 一起保存在 `StructureRuntime`。

`StructureChannelValues` 是 channel value 的 runtime snapshot。controller 获取 channel value 时应通过 runtime
或 typed formed view，而不是直接读取 legacy context。

`FormedStructureView` 是新 callback 的 typed formed view。旧
`formStructure(PatternMatchContext)` 仍通过 bridge 支持。callback 接收到的 legacy context 是提交边界生成的
compatibility view，不是共享 matcher 内部状态。
共享 controller 族提供 typed formation helper，已迁移的具体机器在自己的
`formStructure(FormedStructureView)` 中调用 helper，避免父类直接覆盖 typed callback 后跳过仍依赖
legacy override 的子类。当前 Large Boiler、Research Station 和 Godforge 已走 typed formed view；
未迁移子类继续通过 legacy bridge 保持行为。

legacy 兼容边界：

- `BlockPattern` 仍是 deprecated facade；
- `BlockPatternTemplate.getBlockMatches()` 仍可 materialize legacy predicate array；
- `TraceabilityPredicate` 仍可适配为 direct element；
- JEI/projector/diagnostic 优先读取 typed preview metadata，缺失时 fallback 到 legacy predicate candidates。

## 13. Failure、Trace 与日志

formation failure 使用 `StructureFailureTrace` 表示。它可以包含：

- failure kind；
- operation path；
- orientation；
- piece/cell/world position；
- expected/actual detail；
- missing abilities；
- ability counts；
- progress depth；
- flipped state。

`StructureRuntime` 保存 latest selected failure 和 missing ability summary。相同 failure summary 至少间隔 5 秒才会重复输出。

日志原则：

- 生命周期和 failure trace 使用 `debugStructureTrace`；
- check/scheduler 细节使用 `debugStructureCheck`；
- 不在单元格热路径加入无门控高频日志；
- 不确定行为优先加低频、有开关的 trace，再决定是否改逻辑。

## 14. 实现约束

新增或修改结构系统代码时遵守：

1. 新控制器优先返回 `StructureDefinition`。
2. 新 element 优先实现 context-aware direct path。
3. `toPredicate()` 只作为 legacy/tooling 兼容视图。
4. formation state 只能在 server-thread committer 中发布。
5. controller formed/parts/abilities 字段只作为 projection。
6. check/active/incremental/async live confirm 必须携带 commit token。
7. snapshot async 只能产生 precheck signal，不能直接形成。
8. dirty index 只保存 storage/index/lease，不包含 policy。
9. world mutation 和 item consumption 必须有明确 rollback 或 result accounting。
10. 失败分支使用 transaction/probe/fork，不能泄漏 collector state。
11. 不确定行为用低频 trace 观察，不加热路径日志。

## 15. 测试导航

当前结构系统相关测试：

- `StructureMatchSessionTest`
- `StructureOperationPolicyTest`
- `StructureTraversalBoundaryTest`
- `StructureBuildAccountingTest`
- `StructureFailureDiagnosticsTest`
- `StructureDependencyCompilerTest`
- `StructureContributionTest`
- `StructureIncrementalEvaluatorTest`
- `RepeatGroupPieceTest`
- `StructureLifecycleSchedulingTest`
- `MultiblockStructureChannelsTest`
- `MBPatternTest`

按影响范围选择测试：

- 声明/坐标/重复：`RepeatGroupPieceTest`、`StructureTraversalBoundaryTest`
- session/transaction：`StructureMatchSessionTest`
- build accounting：`StructureBuildAccountingTest`
- failure trace：`StructureFailureDiagnosticsTest`
- contribution/dependency/incremental：`StructureContributionTest`、`StructureDependencyCompilerTest`、
  `StructureIncrementalEvaluatorTest`
- lifecycle/scheduler：`StructureLifecycleSchedulingTest`
- channel/preview adapter：`MultiblockStructureChannelsTest`、`MBPatternTest`

常用验证命令：

```text
./gradlew --% compileJava --no-daemon -Dorg.gradle.workers.max=1 -Dorg.gradle.compiler.daemon=false
./gradlew --% test --no-daemon -Dorg.gradle.workers.max=1 -Dorg.gradle.compiler.daemon=false
./gradlew --% check --no-daemon -Dorg.gradle.workers.max=1 -Dorg.gradle.compiler.daemon=false
```

## 16. 代码导航

入口与 controller：

- `MultiblockControllerBase`
- `MultiblockStructureOperations`
- `MultiblockStructureAssembler`
- `MultiblockStructureCommitter`
- `MultiblockStructureRegistration`

runtime 与结果：

- `StructureRuntime`
- `StructureLifecycleState`
- `StructureOperationRequest`
- `StructureOperationEvaluator`
- `StructureCheckResult`
- `CommittedStructureGraph`
- `StructureShadowValidator`

声明与编译：

- `StructureDefinition`
- `StructureCompiler`
- `MultiPiecePattern`
- `StructurePiece`
- `RepeatGroupPiece`
- `DynamicOffsetPiece`
- `PieceTemplate`
- `PieceTemplateCompiler`
- `PieceTemplateLegacyView`

element 与事务：

- `IStructureElement`
- `CompiledStructureElement`
- `StructureEvaluationContext`
- `StructureMatchSession`
- `StructureOperationState`
- `StructureContribution`
- `StructureDependency`
- `StructureExternalDependencyKey`

调度与索引：

- `MultiblockStructureCheckScheduler`
- `StructureSchedulerPolicy`
- `MultiblockWorldData`
- `StructureWorldIndex`
- `StructureExternalDependencies`
- `StructureCommitToken`
- `AsyncStructureChecker`
- `BlockStateSnapshot`

compatibility：

- `BlockPattern`
- `BlockPatternTemplate`
- `FactoryBlockPattern`
- `TraceabilityPredicate`
- `PatternMatchContext`

## 17. Per-Piece 增量后续步骤

旧设计中的 per-piece 增量目标已经部分落地，但还不是完整最终形态。当前代码已经具备：

- `CommittedStructureGraph`：保存已提交 result table、aggregate、position index、runtime publication、
  orientation 和 external dependency snapshot。
- `StructureResultTable`：按 piece 保存成功 result。
- `StructureContribution`：记录 typed contribution 和 compatibility projection。
- `StructureDependencyGraph` / `StructureDependencyCompiler`：从 typed condition、direct element dependency、
  dynamic anchor 和 external dependency 推导 dirty closure。
- `StructurePositionIndex`：把 formed/watched positions 映射回 owning pieces。
- `StructureDirtyState`：在 runtime 中保存 pending dirty roots。
- `StructureRuntime.checkIncremental(...)`：在 eligible definition、baseline、orientation 和 dirty roots
  都满足条件时执行 incremental recheck。
- scheduler 的 `DirtyCheckLease.INCREMENTAL`：允许 event-driven dirty lease 选择 incremental 路径。
- `StructureExternalDependencies`：提供 controller mode、channel、configuration、upgrade snapshot key，并能通过
  `enqueueDirtyRoots(...)` 唤醒 scheduler。
- `StructureShadowValidator`：debug 下低频抽样 full oracle，对比 incremental result 的结构语义输出。
- 单元测试覆盖 fixed piece、repeat group、dynamic offset、多 dirty roots、external dependency、chain direct
  element dependency、opaque chain fallback，以及 clean piece 不重复读取 world/snapshot access 的核心场景。
- 真实机器接入已覆盖 Godforge upgrade/config 条件：第二/第三 ring 条件声明 upgrades/config external
  dependency，升级、ring 和 renderer 状态变化通过 snapshot/notify 唤醒 scheduler。
- 真实机器配置迁移已覆盖 MultiMap/AdvanceMultiMap recipe-map index、Large Boiler throttle/type 和
  working-enabled mode；Research Station 的 object-holder direct element 显式声明 typed contribution、
  preview、empty dependencies 和 incremental support。
- 性能验收除固定/repeat/dynamic/multi-root/external 单元场景外，还覆盖 controller-mode/config/upgrade
  组合的 real-machine-style matrix，验证 clean piece world-read 计数不重复增加。

当前尚未完成的部分：

- eligibility 仍是保守判断。legacy predicate、opaque side effect、未知 controller state 或未声明 dependency
  的 element 会 fallback 到 active-graph/full。
- `StructureOperationEvaluator` 仍是委托层，不是所有 operation 共用的单一 traversal engine。
- `PatternMatchContext` 仍是 compatibility surface，legacy callback 和部分旧工具仍依赖它。
- async 仍只做未形成结构的 snapshot precheck，不异步重算 formed dirty piece；默认 policy 和
  `StructureCommitToken` 都拒绝 already-formed async precheck。
- non-eligible event-driven 路径仍可能重扫 active graph 或 full graph。
- 当前性能验收覆盖单元矩阵、Godforge 接入点以及 controller-mode/config/upgrade 组合矩阵；还没有
  大规模存档级 world profiling。

后续推进顺序：

1. 持续收紧 legacy 边界。新增代码只消费 `StructureContribution`、`StructureOperationState` 和 typed preview；
   `PatternMatchContext` 只在 callback/tooling 边界生成。
2. 继续迁移 addon-specific external state。新 direct elements 必须补齐 typed contribution、dependency aspect、
   `StructureIncrementalSupport` 和 `getDependencies()`；新 controller 模式、升级、配置状态必须接入 snapshot/notify。
3. 扩展 shadow validation 和性能验收。继续从 Godforge 扩展到更多真实机器矩阵，观察 debug shadow mismatch
   和 world-read 计数。
4. 评估 async dirty precheck。只有在 live confirm、commit token 和 thread-safe snapshot contract 清晰后，
   才允许把 dirty piece 的预检搬到 async；formed state 仍必须由主线程 committer 发布。
5. 删除过时 fallback。等 addon 迁移窗口结束、typed direct element 覆盖足够后，再移除只服务旧
   predicate-shaped traversal 的内部路径。
