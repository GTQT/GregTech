# Structure System V3 Design

**Implementation snapshot:** 2026-06-14  
**Code baseline:** `ae4938540`  
**Scope:** `gregtech.api.pattern`、`gregtech.api.pattern.element`、
`gregtech.api.metatileentity.multiblock` 及当前控制器接入代码。

本文主体描述当前仓库已经存在的实现，而不是历史迁移计划。第 22 章单独描述下一阶段
per-piece 增量检查的目标设计。若当前实现描述与代码冲突，以代码和测试为准。

## 1. 当前结论

Structure System V3 已经成为控制器侧的统一声明和同步检查入口，但还不是一个完全统一的执行引擎。

已经成立的事实：

- 每个控制器最终都会解析出一个 `StructureDefinition`。
- `StructureDefinition` 编译为共享、无控制器状态的 `MultiPiecePattern` 和 `PieceTemplate`。
- 每个控制器单独持有 `StructureRuntime` 和 `PieceRuntimes`；只有 single-template
  shape 额外持有 `MultiblockState`。
- 正常同步检查返回不可变的 `StructureCheckResult`，形成状态只由
  `MultiblockStructureCommitter` 在服务端线程发布。
- full check 和 active-graph recheck 都在操作私有的 `PieceRuntimes` 上执行；
  成功结果携带 `PieceRuntimes.Publication`，只在 controller commit 通过后发布。
- direct element、legacy predicate、结构级计数、ability、channel 和 part 收集可以在同一
  `StructureMatchSession` 中事务化。
- preview、hint、creative build、survival build 和 iteration 已经有 request/runtime 入口。
- 世界方块变化只写 dirty index；真正的检查由控制器 tick 中的 scheduler 发起。
- 异步检查只做快照预检，命中后仍在主线程重新执行完整同步检查。

尚未成立的目标：

- `StructureOperationEvaluator` 仍然是对 `MultiblockState`、`StructureCheckState`、
  `MultiPiecePattern` 和 preview assembler 的委托层，不是单一 traversal engine。
- `StructureOperationRequest` 已有独立 `SNAPSHOT_CHECK`，但 diagnose 仍没有 request/result。
- event-driven recheck 明确命名为 active-graph check；它会重扫完整 active piece graph，
  不是只匹配被标记 dirty 的 piece。
- full check 使用每次检查新建的临时 `PieceRuntimes`；控制器持有的 `PieceRuntimes`
  只接收成功 commit 发布的完整状态，并服务 formed position、dirty 标记、重复信息和事件驱动重检。
- `PatternMatchContext`、`TraceabilityPredicate`、`BlockPatternTemplate` 和
  `MultiblockState` 仍在核心实现中承担兼容职责。
- hint result 只统计调度情况，不能证明客户端实际显示了提示。

因此，V3 当前应定义为：

> 统一声明、所有权、事务、结果和控制器提交边界，并在兼容旧结构 API 的前提下逐步收敛执行路径。

## 2. 设计原则

### 2.1 共享声明不可变

允许跨控制器共享的对象必须不含控制器实例状态：

- `StructureDefinition`
- `MultiPiecePattern`
- `StructurePiece`
- `RepeatGroupPiece`
- `PieceTemplate`
- `CompiledStructureElement`

`StructurePiece` 只保存模板、名字、偏移、条件和 snapshot checker。它不保存 dirty、
validated、formed positions 或匹配缓存。

### 2.2 可变状态有明确所有者

可变状态只能属于以下两类所有者：

1. 单个控制器：
   `StructureRuntime`、`MultiblockState`、`PieceRuntimes`、`PieceRuntime`。
2. 单次操作：
   `StructureMatchSession`、`StructureOperationState`、`PatternMatchContext`、
   `BlockWorldState`、`StructureEvaluationContext`。

不得把一次检查产生的 context、count、part、channel 或 repeat 结果写回共享模板。

### 2.3 匹配与发布分离

匹配成功只表示“得到一个可提交结果”，不表示控制器已经形成。

发布前必须完成：

- part sharing 检查；
- controller-specific ability 过滤；
- old/new part diff；
- ability instance 重建；
- formed metadata 和 channel values 发布；
- controller callback 调用；
- 世界 dirty index 注册。

### 2.4 失败分支必须回滚

候选分支、chain alternative、predicate alternative、repeat 搜索和 piece graph 检查失败时，
不能留下以下状态：

- collected part；
- generic count；
- ability count 和 ability contributor；
- channel/tier 值；
- active casing position；
- deferred requirement；
- legacy context key；
- legacy global/layer count；
- piece cache、formed positions、repeat counts。

### 2.5 日志用于确认，不改变语义

结构行为不确定时，优先增加受开关控制的低频 trace，而不是猜测性修改匹配逻辑。

- 生命周期 trace 使用 `debugStructureTrace`。
- 结构检查细节使用 `debugStructureCheck`。
- 不在单元格热路径中增加无门控日志。
- 相同 failure summary 在 `StructureRuntime` 中至少间隔 5 秒才重复输出。

## 3. 架构分层

| 层 | 主要类型 | 当前职责 |
|---|---|---|
| 声明层 | `StructureDefinition`、`IStructurePiece` | 定义 piece、符号、条件、偏移、重复和全局 ability 限制。 |
| 编译层 | `StructureCompiler`、`PieceTemplateCompiler` | 把声明编译成 `MultiPiecePattern`、`StructurePiece`、`PieceTemplate` 和 compiled element。 |
| 元素层 | `IStructureElement`、`CompiledStructureElement`、`StructureElementPreview` | 单元格匹配、requirement、候选、preview、hint 和 placement。 |
| 控制器运行时 | `StructureRuntime`、`MultiblockState`、`PieceRuntimes` | 保存控制器私有缓存、piece 状态、已提交 metadata/channel 和最近失败。 |
| 操作入口 | `StructureOperationRequest`、`StructureOperationEvaluator` | 校验请求类型并分派到现有 check/build/hint/preview/iterate 实现。 |
| 操作事务 | `StructureMatchSession`、`StructureEvaluationContext`、`BlockWorldState` | 保存本次匹配状态并提供 fork、transaction、probe 和 checkpoint。 |
| 操作结果 | `StructureCheckResult`、`StructureSnapshotResult`、`StructureBuildResult`、`StructureHintResult` | 在执行层和控制器生命周期之间传递不可变结果。 |
| 生命周期 | `MultiblockStructureOperations`、`Assembler`、`Committer` | 固定 check、assemble、commit、registration 的调用顺序。 |
| 调度层 | `MultiblockStructureCheckScheduler`、`StructureWorldIndex`、`AsyncStructureChecker` | polling、dirty event、snapshot precheck 和主线程 fallback。 |
| 兼容层 | `BlockPattern`、`BlockPatternTemplate`、`TraceabilityPredicate`、`PatternMatchContext` | 保留旧 addon 和旧控制器 API，并桥接到 canonical definition。 |

## 4. Canonical 声明与编译

### 4.1 控制器解析顺序

`MultiblockControllerBase.reinitializeStructurePattern()` 按以下顺序解析结构：

1. `createStructureDefinition()`
2. `createMultiPiecePattern()`，再通过 `StructureDefinition.fromMultiPiecePattern(...)` 适配
3. `createStructureTemplate()`，再通过 `StructureDefinition.fromTemplate(...)` 适配
4. `createStructureTemplate()` 的默认实现仍可调用已废弃的 `createStructurePattern()`

因此，对正常控制器生命周期而言，`StructureDefinition` 已经是 canonical shape。旧声明入口仍然存在，
但会在 runtime 初始化时被包装成 definition。

新控制器应直接实现：

```java
@Override
protected StructureDefinition<?> createStructureDefinition() {
    return STRUCTURE;
}
```

声明应通过 `StructureDefinition.getOrBuild(...)` 或其他稳定缓存返回幂等结果。

### 4.2 Definition 支持的结构

当前 builder 支持：

- 固定 named piece；
- conditional piece；
- controller-aware `StructureCondition`；
- X/Y/Z 单轴重复；
- 多轴 repeat group；
- repeat channel；
- `OffsetMode`；
- 基于先前 repeatable piece 的动态锚点；
- 全局 ability min/max；
- ability group min/max；
- 从 `FactoryBlockPattern` 或 `BlockPatternTemplate` 导入旧模板。

piece 名称必须非空且唯一。动态锚点只能引用之前已经声明的 piece。

### 4.3 编译结果

`StructureDefinition.getCompiledPattern()` 懒编译并缓存 `MultiPiecePattern`。

编译后的主要关系是：

```text
StructureDefinition
  -> MultiPiecePattern
       -> StructurePiece / RepeatGroupPiece / DynamicOffsetPiece
            -> PieceTemplate
                 -> CompiledStructureElement[][][]
                 -> TraceabilityPredicate[][][] compatibility view
```

`PieceTemplate` 是 canonical piece IR。`BlockPatternTemplate` 是它的兼容 facade。

### 4.4 Single-template 资格

只有“恰好一个且非 repeat group 的 piece”支持 `supportsSingleTemplatePath()`。

该资格当前主要影响：

- 是否创建控制器级 `patternTemplate` 和 `multiblockState`；
- single preview、hint、build 和 iteration 是否可以走 `MultiblockState`；
- 旧 API 是否能取得单模板视图。

它不表示同步 formation check 会绕过 `StructureDefinition`。正常控制器 check 仍调用
`StructureCheckState`，并按 piece graph 检查。

## 5. 所有权模型

| 对象 | 生命周期 | 可变性 | 说明 |
|---|---|---:|---|
| `StructureDefinition` | 结构类型级 | 否 | canonical 声明和编译缓存入口。 |
| `MultiPiecePattern` | 结构类型级 | 否 | ordered piece graph 和全局 ability 约束。 |
| `PieceTemplate` | 结构类型级 | 否 | 单 piece 编译 IR。 |
| `StructureRuntime` | 控制器级 | 是 | 操作 facade、已提交 metadata/channel、missing abilities 和 last failure。 |
| `MultiblockState` | 控制器或临时 piece 级 | 是 | 单模板 cache、repeat、error 和 traversal scratch。 |
| `PieceRuntimes` | 控制器或单次检查级 | 是 | 按 piece identity 保存 `PieceRuntime`。 |
| `PieceRuntime` | 单 piece、单所有者 | 是 | state、positions、dirty、validated、last reps/context。 |
| `StructureMatchSession` | 单次匹配 | 是 | 跨 piece 的 context、typed state、counts 和 typed data。 |
| `StructureOperationState` | 单次匹配 | 是 | parts、ability、requirements、counts、active positions。 |
| `StructureCheckResult` | 单次检查结果 | 否 | commit 前的快照。 |

必须注意两套 `PieceRuntimes`：

- `StructureCheckState` 的 full check 和 active-graph evaluator 每次创建操作私有 runtimes，
  匹配、assembly prepare 或 commit 失败都不会污染控制器长期状态。
- 控制器 runtime 的 `PieceRuntimes` 只保存最近一次成功 commit 的完整 piece 状态。

成功检查产生不可变 `PieceRuntimes.Publication`，内容包括 matcher cache、formed positions、
validated/dirty、repeat counts 和 aggregated context。publication 以 compiled piece identity 校验；
`MultiblockStructureCommitter` 在任何 controller mutation 前预校验，在 part assembly 成功后发布。
registration 只读取已经发布的 positions，不执行 matcher，也不再次扫描世界。

## 6. 同步检查与提交

### 6.1 主流程

正常同步检查调用链：

```text
MultiblockControllerBase.checkStructurePattern()
  -> MultiblockStructureOperations.checkStructurePattern()
  -> StructureRuntime.check(CHECK request)
  -> StructureOperationEvaluator.check()
  -> StructureCheckState.check()
  -> StructureCheckResult
  -> MultiblockStructureCommitter.applyCheckResult()
  -> MultiblockStructureAssembler.prepare()
  -> commit controller/runtime state
  -> MultiblockStructureRegistration
```

### 6.2 Full check

`StructureCheckState` 对 non-flipped orientation 执行一次检查；失败且允许 flip 时再检查 flipped orientation。

每个 orientation：

1. 创建临时 `PieceRuntimes`。
2. 创建一个跨 piece 的 `StructureMatchSession`。
3. 按声明顺序计算 active piece。
4. 用之前形成的 repeat/center metadata 解析动态 piece center。
5. fixed piece 通过 session fork 执行 exact check。
6. repeat group 执行 repeat search。
7. 所有 active piece 成功后执行结构级 requirement 和 ability validation。
8. 生成 `FormedStructureMetadata`、operation state 和 legacy context view。

两种 orientation 都失败时，`StructureFailureSelection` 根据 reason priority、progress depth、
稳定 piece/cell、non-flipped 和 sequence 选择更有用的失败。

### 6.3 Commit

`StructureCheckResult.isMatched()` 只允许进入 prepare 阶段。

`MultiblockStructureAssembler.prepare()`：

- 从 `StructureOperationState` 读取 parts；
- 拒绝已挂到其他结构且不可共享的 part；
- 计算 added/removed parts；
- 按控制器规则排序；
- 重建通过 `checkAbilityPart(...)` 过滤后的 ability instances。

`MultiblockStructureCommitter` 才能：

- 预校验 piece runtime publication 与 controller compiled graph 一致；
- 更新 flipped 状态；
- detach removed parts；
- attach added parts；
- 替换 controller part/ability 集合；
- 发布成功检查产生的完整 `PieceRuntimes`；
- 发布 runtime metadata 和 channel values；
- 设置 formed；
- 调用 `formStructure(context)`；
- 注册 formed positions。

失败结果不会覆盖上一次成功提交的 formed metadata。若控制器原本已形成，新的匹配失败会触发
`invalidateStructure()`。

## 7. Dirty Index 与 Active-Graph 重检

### 7.1 世界回调

`StructureWorldIndex` 按 world 保存：

- chunk -> controllers；
- controller -> formed positions；
- controller -> piece pattern；
- pending recheck；
- last changed tick；
- chunk change revision；
- suppressed controllers。

方块变化回调只做：

1. 增加所在 chunk 的 revision；
2. 找到真正包含该位置的 formed controller；
3. 根据 piece position cache 标记 dirty；
4. 把 controller 加入 pending set。

它不直接执行结构检查。

### 7.2 Scheduler

控制器 tick 中的 `MultiblockStructureCheckScheduler` 按顺序处理：

1. first tick full check；
2. formed controller 的 event-driven decision；
3. unformed controller 的 async precheck；
4. polling fallback。

dirty event 有 5 tick cooldown，连续修改不会每 tick 触发扫描。

### 7.3 Active-graph 的真实含义

`MultiPiecePattern.checkActiveGraphWithResult(...)` 会重扫完整 active piece graph。

原因是以下数据不能仅从一个脏 piece 的旧结果安全恢复：

- 结构级 predicate count；
- shared `PatternMatchContext`；
- global ability limit；
- dynamic offset；
- repeat metadata；
- 跨 piece collector state。

dirty 标记只用于：

- 判断事件是否需要走 active-graph recheck；
- 确定 formed position 与 piece 的对应关系；
- 选择 scheduler action。

它不保证只访问变更 piece 的方块。真正的 per-piece 增量检查需要独立的 piece result、
typed contribution 和跨 piece dependency graph，完整目标设计见第 22 章。

active-graph recheck 在操作私有 `PieceRuntimes` 和 session transaction 中执行。成功结果携带
publication，失败结果不接触 controller runtimes。旧 `checkDirtyPieces*` 名称只作为 deprecated
兼容入口保留，并转发到 active-graph 实现。

## 8. 异步检查

异步检查只服务于未形成控制器，并要求 definition 的所有 active-capable elements 支持
`SNAPSHOT_MATCH`。conditional piece 当前会使 definition 保守地报告不支持 snapshot。

流程：

1. 主线程根据 definition 的 world AABB 捕获 `BlockStateSnapshot`。
2. 捕获前后记录 chunk revision。
3. 异步线程创建 disposable `StructureRuntime`，提交显式 `SNAPSHOT_CHECK` request。
4. `StructureOperationEvaluator` 调用 `StructureCheckState.checkSnapshot(...)` 统一调度 piece graph。
5. 返回 `StructureSnapshotResult`，只包含预检 outcome、flip、失败 piece 和 progress depth。
6. 主线程验证 registration generation、runtime generation、world、position、orientation 和 revision。
7. snapshot 命中后调用 `controller.checkStructurePattern()` 做完整 live confirm。
8. 只有 live result 才能进入 committer。

异步结果永远不直接发布 formed state 或 failure state。

`BlockStateSnapshot` 会复制 block state，但 tile entity 仍是捕获时的对象引用，不是深拷贝。
因此 `SNAPSHOT_MATCH` 必须保持显式 opt-in；实现该 capability 的 element 不能在异步线程执行
不安全的 tile entity 读取或修改。

超过 `100 * 100 * 100` snapshot volume 的结构直接回退主线程检查。即使异步一直没有命中，
scheduler 也会每 100 tick 触发一次主线程 fallback。

## 9. Operation API

### 9.1 当前 request kinds

`StructureOperationRequest.Kind` 当前包括：

| Kind | Evaluation operation | 读世界 | 修改世界 | 收集 formation state |
|---|---|---:|---:|---:|
| `CHECK` | `MATCH_WORLD` | 是 | 否 | 是 |
| `SNAPSHOT_CHECK` | `MATCH_SNAPSHOT` | 读 snapshot | 否 | 是 |
| `PREVIEW` | `PREVIEW` | 否 | 否 | 否 |
| `HINT` | `HINT` | 是 | 仅提示副作用 | 否 |
| `CREATIVE_BUILD` | `CREATIVE_BUILD` | 是 | 是 | 否 |
| `SURVIVAL_BUILD` | `SURVIVAL_BUILD` | 是 | 是 | 否 |
| `ITERATE` | `ITERATE` | 是 | 否 | 否 |

`SNAPSHOT_CHECK` 携带 `IBlockAccess` snapshot、controller position、orientation 和可选 controller。
它返回独立的 `StructureSnapshotResult`，不能提交给普通 structure committer。

### 9.2 Request 的职责

request 是不可变输入，按 operation 携带：

- world 和 controller position；
- `StructureOrientation`；
- controller 和 player；
- channel values；
- preview repetitions；
- piece index；
- `AbilityPlacementTracker`；
- trigger stack；
- random check 和 skip hatches 标记。

request 通过 `requireKind(...)`、`requireBuildKind()` 和 `requireXxx()` 在入口处拒绝错误调用。

### 9.3 Runtime 和 Evaluator

`StructureRuntime` 是控制器应使用的 operation facade。`getEvaluator()` 已废弃。

当前 evaluator 的分派关系：

- formation check -> `StructureCheckState`
- snapshot precheck -> `StructureCheckState`
- active-graph recheck -> `MultiPiecePattern`
- single build/hint/preview/iterate -> `MultiblockState`
- multi-piece build/hint -> `MultiPiecePattern`
- multi-piece preview -> `MultiPiecePreviewAssembler`

这解释了为什么 V3 已统一调用边界，但尚未统一底层执行实现。

## 10. Element 执行模型

### 10.1 Direct element

`IStructureElement` 是新结构的 canonical cell contract。

主要入口：

- `match(StructureEvaluationContext)`：formation 匹配；
- `collectRequirements(...)`：声明 deferred requirement；
- `getCandidates(...)`：候选；
- `getPreview(...)`：preview/build metadata；
- `spawnHint(...)`：提示；
- `placeBlock(...)`：creative placement；
- `survivalPlaceBlock(...)`：survival placement；
- `getCapabilities()`：安全能力声明。

默认 `match(...)` 先收集 requirement，再执行 `check(...)`。组合元素应在分支 transaction 内处理
requirement，防止失败 alternative 泄漏状态。

### 10.2 Compiled element

`CompiledStructureElement` 包装 direct element，并缓存 capability。

direct runtime 调用 source element，不要求 `toPredicate()`。但 compiled element 仍会保留一个
predicate-shaped compatibility view，供旧算法、preview map 和 addon 工具使用。

因此正确表述是：

- direct matching 不依赖 predicate conversion；
- 整个编译产物仍没有完全移除 predicate view；
- 只有 `usesLegacyPredicateRuntime()` 返回 true 时，formation matching 才通过 `LegacyElement` 执行。

### 10.3 Capability

当前 capability：

- `LIVE_MATCH`
- `SNAPSHOT_MATCH`
- `PREVIEW`
- `HINTS`
- `CREATIVE_PLACEMENT`
- `SURVIVAL_PLACEMENT`

默认 element 不声明 snapshot-safe。`IStructureElementNoPlacement` 会移除两种 placement capability。

capability 缺失是调度不支持，不是普通 block mismatch。

### 10.4 Preview metadata

`StructureElementPreview` 是 direct element 的 preview/build metadata。

它可以表达：

- common 和 limited candidate groups；
- channel name；
- default candidate；
- global/layer min/max；
- preview count；
- legacy predicate reference。

新 direct element 不应为了 channel、tooltip 或候选选择而把信息塞回
`TraceabilityPredicate.SimplePredicate`。

## 11. Session、事务与 Probe

### 11.1 Session 状态

`StructureMatchSession` 持有：

- legacy-compatible `PatternMatchContext`；
- typed `StructureOperationState`；
- legacy global predicate counts；
- typed `StructureSessionKey` 数据；
- controller context；
- ability limits 和 ability group limits。

fork 会隔离 session state：legacy context 会复制常见容器，typed value 按
`StructureSessionKey` 的 copier 复制。成功 candidate 调用 `commit()` 合并回 parent。

### 11.2 Transaction

`transaction(...)` 语义：

- action 成功时保留修改；
- action 返回失败时恢复 checkpoint；
- action 抛出异常时恢复后继续抛出。

`probe(...)` 无论返回什么都恢复 checkpoint。

同样的 API 家族存在于：

- `StructureMatchSession`
- `StructureEvaluationContext`
- `BlockWorldState`
- `PatternMatchContext`

### 11.3 回滚范围

evaluation rollback 覆盖 session、legacy context 和 count maps。

它不自动回滚外部副作用：

- world block mutation；
- inventory/AE item consumption；
- hint rendering；
- controller callback。

build path 必须显式安排副作用顺序。当前 survival build 在 block 已放置但 item commit 失败时，
会撤销刚放置的方块，并且不把该物品记录为 consumed。

### 11.4 Non-formation operation

`StructureEvaluationContext.Operation.collectsFormationState()` 控制 collector 是否真的写入 formation state。

preview、hint、build 和 iterate 即使复用 element 逻辑，也不能向本次 session 提交：

- parts；
- ability/count；
- channels；
- active casing positions；
- deferred requirements。

## 12. 坐标、方向和 Piece

`StructureOrientation` 保存：

- controller front；
- structure front；
- up；
- flipped；
- allowsFlip。

runtime/request 内部应传递完整 orientation，不应重新拆成多个散参数。

`StructureCellTraversal` 保存：

- piece center；
- orientation；
- template-local offset。

`MultiblockState` 的 fixed traversal 使用该对象统一 live/snapshot exact check、build、hint 和 iteration
中的 cell 坐标解析。

piece center 由 `OffsetMode` 解释：

- 普通 piece 使用静态 offset；
- `DynamicOffsetPiece` 读取之前 piece 的 center 和 repeat；
- `DynamicRepeatGroupPiece` 对 repeat group 应用同样的 anchor 规则。

repeat group 当前支持：

- 单轴 sliding search；
- 多轴 backtracking；
- snapshot axis-line fast path；
- channel 指定 repeat；
- live/snapshot/build/hint 共用 repeat offset 枚举。

旧 `front/up/flipped` overload 仍保留在 `BlockPattern`、`BlockPatternTemplate` 等兼容 API 边缘。

## 13. Build、Hint、Preview 与 Iterate

### 13.1 Build

creative 和 survival build 共享 `StructurePlacementDecision`：

1. required ability candidate；
2. channel/default preferred candidate；
3. inventory candidate；
4. AE candidate。

creative 不消耗物品。survival 先模拟可用性，world placement 成功后才提交物品消耗。

`StructureBuildResult` 记录：

- attempted traversals；
- inactive/invalid piece；
- placement budget；
- visited/existing/placed cells；
- missing candidate；
- ability limit blocked；
- skipped hatch；
- unavailable item；
- placement failure；
- required/consumed/missing items。

already-valid cell 不消耗 placement budget。`requiresResume()` 表示同一请求再次执行可从已放置状态继续。

当前日志中的 `single-piece-legacy-autobuild` 和 `multi-piece-legacy-autobuild` 说明 build 底层仍依赖
`MultiblockState`/`MultiPiecePattern` 的现有实现。

### 13.2 Hint

`StructureHintResult` 只记录：

- attempted traversals；
- active/inactive pieces；
- visited cells；
- trigger handled；
- context fallback。

它不记录每个 element 是否真的产生了客户端可见效果。

### 13.3 Preview

single preview 返回 `BlockInfo[][][]`。multi-piece preview 由
`MultiPiecePreviewAssembler` 合并 positioned piece result。

preview 和 build 优先读取 `StructureElementPreview`。legacy predicate metadata 会被适配到相同选择模型。

### 13.4 Iterate

`ITERATE` 当前只支持 single-template runtime，并通过 `MultiblockState.getAllStructureBlocks(...)`
返回 world position -> `BlockInfo`。

多 piece 的位置集合由 `PieceRuntime.positions` 和 registration API 管理，不通过 request-based iterate 返回。

## 14. Formed Metadata、Channel 与 Compatibility Context

`FormedStructureMetadata` 保存：

- piece repeat counts；
- channel values；
- piece centers。

成功 commit 后，`StructureRuntime` 保存：

- formed metadata；
- copied `StructureChannelValues`；
- cleared missing abilities；
- cleared last failure。

`StructureOperationState` 是 typed formation state，当前包含：

- requirements 和 counts；
- ability counts 和 explicit ability parts；
- multiblock parts；
- variant active blocks。

为了兼容现有 `formStructure(PatternMatchContext)`，`StructureCheckResult.copyContext()` 会把 typed
parts 和 active blocks 映射回 legacy keys。其他 addon 自己写入的 context key 保留。

这意味着 `PatternMatchContext` 仍是 controller callback 的正式兼容面，尚不能删除。

## 15. Failure Diagnostics

`StructureFailureTrace.Kind` 当前包括：

- `BLOCK_MISMATCH`
- `MISSING_ABILITY`
- `COUNT_LIMIT`
- `CAPABILITY_UNSUPPORTED`
- `ASSEMBLY_REJECTION`
- `COMMIT_REJECTION`
- `LEGACY_PATTERN`
- `UNKNOWN`

trace 可保存：

- controller 和 orientation；
- path 和 operation；
- piece、cell、world position；
- expected/actual；
- missing abilities 和 observed ability counts；
- progress depth；
- sequence。

控制器 runtime 保存最近一次选中的失败。新 check failure 会替换旧 check failure；
多个 orientation 或 lifecycle failure 通过 `StructureFailureSelection` 比较。

开发命令：

```text
/gt structure_trace <x> <y> <z>
```

该命令输出 runtime shape、formed 状态和 `lastFailure` 摘要。

## 16. 兼容边界

当前兼容 API 仍然是可运行代码，不只是类型别名：

- `BlockPattern`
- `FactoryBlockPattern`
- `BlockPatternTemplate`
- `TraceabilityPredicate`
- `PatternMatchContext`
- `createStructurePattern()`
- `createStructureTemplate()`
- `createMultiPiecePattern()`
- controller 上的 `structurePattern`、`patternTemplate`、`multiblockState`

其中：

- `BlockPattern` 和 `createStructurePattern()` 标记计划在 2.10 移除；
- `BlockPatternTemplate` 是 `PieceTemplate` 的 facade，短期仍被大量工具和 addon 使用；
- legacy declaration 会在控制器初始化时适配成 `StructureDefinition`；
- legacy predicate 会编译成 `LegacyElement`；
- direct element 可选提供 predicate view，但不应依赖它执行；
- `StructureCheckResult.Source.LEGACY_TEMPLATE` 和 evaluator 的 nullable definition fallback
  仍保留，不过正常控制器 resolver 会先创建 definition。

兼容策略必须保持单向：

> legacy declaration 可以进入 V3；V3 新代码不能反向依赖 legacy side effect 才能工作。

## 17. Dynamic Structure

运行时尺寸或瞬态条件决定的结构不能替换控制器 canonical runtime。

`MultiblockStructureOperations` 提供 disposable runtime：

- dynamic check；
- dynamic preview；
- dynamic build；
- dynamic hint。

dynamic runtime 只服务当前 request，不发布为控制器 runtime，也不复用不同尺寸之间的 piece cache。

dynamic check 失败会把带 `dynamic-runtime` path 的 failure 写入 canonical runtime；dynamic build
有 blocked cells 时记录生命周期失败摘要。

## 18. 已完成边界修正与后续优先级

### P0：纠正执行边界，已完成

1. registration 不再执行 `checkAllPieces(...)`，只消费 commit 后发布的 formed positions。
2. full check 与 active-graph check 都通过 `PieceRuntimes.Publication` 发布完整临时状态。
3. request/result 已增加 `SNAPSHOT_CHECK` 和 `StructureSnapshotResult`，async checker 不再复制 piece graph。
4. scheduler、runtime、evaluator、trace path 和主实现已统一为 active-graph；旧 dirty API 仅 deprecated 转发。

### P0：下一阶段，per-piece 增量检查

1. 先让 full check 产生独立、不可变的 `PieceEvaluationResult`，并通过统一 fold 得到结构级结果。
2. 把 direct element 的 formation side effect 收口为 typed `StructureContribution`。
3. 编译显式 `PieceDependencyGraph`，对 opaque condition、legacy context 写入和未知依赖保守回退。
4. event-driven recheck 只重算 dirty dependency closure，随后重新 fold 全部 active contribution。
5. 用 differential test 和可选低频 shadow validation 对照 active-graph 结果。

具体类型、算法、fallback 和迁移顺序见第 22 章。

### P1：统一结果和 traversal

1. 让 check、build、hint、preview、iterate 使用更一致的 typed outcome。
2. 为 multi-piece iterate 提供 request/result。
3. 收敛 `MultiblockState` 和 `MultiPiecePattern` 中重复的 build/hint dispatch。
4. 让 hint result 记录实际 rendering outcome，而不只记录调用路径。

### P1：缩小 legacy 核心影响

1. 逐步让 `PieceTemplate` 的核心算法只依赖 compiled elements。
2. 把 predicate map、旧 tooltip 和旧 preview 所需 view 放到明确 adapter。
3. 降低 `PatternMatchContext` 在内部 requirement/channel 收集中的职责。
4. 保持 `formStructure(PatternMatchContext)` 兼容，直到 addon 迁移窗口结束。

### P2：生命周期状态收口

1. 定义 `StructureRuntime` 与 controller 的 formed flag、part list、ability instances 的最终所有权。
2. 让 generation/stale validation 成为通用 commit token，而不是只存在于 async checker。
3. 将 scheduler policy 与 world index storage 解耦，便于不同结构选择 polling、event-driven 或 async。

## 19. 实现约束

新增或修改结构系统代码时必须遵守：

1. 新控制器优先返回 `StructureDefinition`。
2. 新 element 实现 context-aware direct path；不要以 `toPredicate()` 作为执行入口。
3. 所有 alternative 使用 `transaction` 或 `tryFork`。
4. 所有 advisory check 和候选探测使用 `probe`。
5. 非 formation operation 不得提交 collector state。
6. world mutation 和 item consumption 必须显式安排回滚。
7. 形成状态只能在 server-thread committer 中发布。
8. async snapshot 只能产生预检信号，不能直接形成。
9. piece template 不得保存 controller-owned runtime。
10. 不确定行为先增加低频、有开关的 trace。

## 20. 测试矩阵

当前核心测试：

- `StructureMatchSessionTest`
- `StructureOperationPolicyTest`
- `StructureTraversalBoundaryTest`
- `StructureBuildAccountingTest`
- `StructureFailureDiagnosticsTest`
- `RepeatGroupPieceTest`

每次改动至少按影响范围覆盖：

### 声明与坐标

- fixed single piece；
- repeatable single piece；
- fixed multi-piece；
- conditional piece；
- dynamic-offset piece；
- single-axis 和 multi-axis repeat；
- flipped orientation；
- live/snapshot 坐标一致。

### 事务

- failed branch 回滚 context；
- failed branch 回滚 part/count/ability/channel/tier；
- repeat search 失败回滚 runtime；
- full/active graph 在显式 publication 前不修改 controller `PieceRuntimes`；
- active graph 失败保留最近一次已发布 runtime；
- snapshot request 通过 definition runtime traversal；
- non-formation operation 不写 formation state。

### 生命周期

- initial formation；
- still-valid check；
- soft reassembly；
- part sharing rejection；
- invalidation；
- event-driven cooldown；
- stale async token rejection；
- async match 后 live confirm。

### Build

- creative preferred candidate；
- survival inventory 和 AE candidate；
- insufficient items；
- partial placement 和 resume；
- consume failure 后 world rollback；
- already-valid probe；
- ability placement limit；
- direct preview metadata 不依赖 legacy predicate。

### Diagnostics

- block mismatch；
- missing ability；
- count limit；
- capability unsupported；
- assembly/commit rejection；
- flipped failure selection；
- command summary 字段。

## 21. 代码导航

阅读当前实现时建议按以下顺序：

1. `MultiblockControllerBase`
2. `MultiblockStructureOperations`
3. `StructureRuntime`
4. `StructureOperationRequest`
5. `StructureOperationEvaluator`
6. `StructureDefinition`
7. `StructureCheckState`
8. `MultiPiecePattern`
9. `MultiblockState`
10. `StructureMatchSession`
11. `IStructureElement`
12. `MultiblockStructureCommitter`
13. `StructureWorldIndex`
14. `AsyncStructureChecker`

这条路径分别覆盖控制器入口、operation 边界、声明编译、执行事务、提交和调度。

## 22. Per-piece 增量检查目标设计

**状态：** proposed，尚未实现。

本章定义下一阶段的 canonical formation 模型。它不要求立即删除 legacy API，但要求新的增量路径
不再把共享可变 session 当作结构结果本身。

### 22.1 目标与非目标

目标：

- 世界方块变化后，只重新读取 dirty piece 及其依赖闭包中的方块。
- 未受影响 piece 的已验证结果可以安全复用。
- 结构级 count、ability、part、channel 和 requirement 由独立 piece contribution 重新聚合。
- full check 与 incremental check 使用同一种 piece result 和 aggregate validation。
- 任一失败分支都不能部分发布 piece cache、formed positions 或结构级状态。
- 无法证明增量安全时自动回退 active-graph，不猜测依赖。
- legacy controller callback 继续收到兼容 `PatternMatchContext`，但它只在结果边界生成。

非目标：

- 首版不追求从 aggregate 中减去旧 contribution。
- 首版不持久化完整 piece result table；区块加载后允许执行一次 full baseline。
- 首版不对 legacy predicate 或任意 addon context 写入做不可靠的 delta 推断。
- 首版不让 async snapshot 直接发布 formed state。
- 首版不支持循环 piece dependency；循环依赖必须拒绝或回退 active-graph。

### 22.2 核心结论

首版采用：

> 不可变 piece result table + dirty dependency closure 重算 + 全表确定性 fold。

不采用：

```text
aggregate - oldPieceContribution + newPieceContribution
```

原因：

- set、part 和 ability contributor 的逆运算容易受重复贡献影响；
- requirement declaration 和 uniform channel 不天然支持减法；
- 顺序敏感 typed value 需要保留声明顺序；
- 全表 fold 的成本通常远小于世界方块读取；
- 不需要为每一种 reducer 定义 inverse；
- candidate table 可以用浅拷贝和不可变 result 实现简单事务。

预期复杂度：

```text
world reads = O(dirty dependency closure 中的 cells)
aggregate    = O(active pieces + emitted contributions)
memory       = O(active pieces + formed positions + contributions)
```

### 22.3 新的所有权模型

目标对象关系：

```text
StructureDefinition
  -> IncrementalStructurePlan
       -> PieceDependencyGraph
       -> per-piece incremental support
       -> aggregate schema

StructureRuntime
  -> CommittedStructureGraph
       -> generation
       -> StructureResultTable
       -> AggregatedStructureResult
       -> StructurePositionIndex
```

其中：

| 对象 | 生命周期 | 可变性 | 职责 |
|---|---|---:|---|
| `IncrementalStructurePlan` | definition 级 | 否 | 编译后的增量资格、依赖图和 aggregate schema。 |
| `PieceDependencyGraph` | definition 级 | 否 | dirty root 到受影响 piece 的有向依赖。 |
| `PieceEvaluationResult` | 一次 piece 成功检查 | 否 | active、坐标、repeat、positions、matcher cache 和 contribution。 |
| `StructureContribution` | 一次 piece 成功检查 | 否 | 该 piece 对结构级 formation state 的独立贡献。 |
| `StructureResultTable` | controller generation 级 | 否 | piece identity 到成功 result 的不可变映射。 |
| `AggregatedStructureResult` | controller generation 级 | 否 | fold 后的 parts、ability、channel、metadata 和 validation 输入。 |
| `CommittedStructureGraph` | controller 级 | 是，引用替换 | 当前已发布 table、aggregate、position index、runtime publication、orientation、external snapshot 和 generation。 |

`PieceRuntimes` 的近期演进策略：

1. 先保留现有 matcher cache 和 legacy accessor。
2. 让 publication 同时携带 `StructureResultTable`（已完成，见 `CommittedStructureGraph`）。
3. controller commit 一次性发布 table、piece runtime view 和 aggregate（已完成）。
4. 最终让 `PieceRuntime` 成为 `PieceEvaluationResult` 的 runtime view，删除
   `lastAggregatedContext` 之类的累计 session 状态。

不能让 `StructureRuntime` 和 `PieceRuntimes` 分别成为同一 piece result 的独立真相源。

### 22.4 PieceEvaluationResult

建议的数据形状：

```java
public final class PieceEvaluationResult {
    private final StructurePiece piece;
    private final PieceStatus status;
    private final BlockPos resolvedCenter;
    private final int[] repetitions;
    private final LongSet formedPositions;
    private final LongSet watchedPositions;
    private final PieceMatcherPublication matcherPublication;
    private final StructureContribution contribution;
    private final PieceDependencyOutputs dependencyOutputs;
    private final long semanticFingerprint;
}
```

`PieceStatus` 首版只有：

```text
ACTIVE_MATCHED
INACTIVE
```

失败结果不进入 table，也不能发布。失败通过 `PieceEvaluationFailure` 进入
`StructureCheckResult`：

```java
public final class PieceEvaluationFailure {
    private final StructurePiece piece;
    private final StructureFailureTrace trace;
    private final Map<MultiblockAbility<?>, Integer> observedAbilities;
}
```

evaluator 返回二选一 outcome，避免把失败状态塞进可缓存 result：

```java
public interface PieceEvaluationOutcome {
    boolean isMatched();
    PieceEvaluationResult result();
    PieceEvaluationFailure failure();
}
```

实际实现可使用 sealed hierarchy 或两个明确 factory；`result()` 和 `failure()` 只有对应分支可调用。

约束：

- result 构造后深度不可变；
- `LongSet`、数组、map 和 collection 必须防御复制或使用 immutable 实现；
- `formedPositions` 表示形成结构和注册 part 所属的实际单元格；
- `watchedPositions` 表示本次 piece 结果依赖的 world positions，可以包含边界探测位置；
- inactive result 的 formed positions、watched positions 和 contribution 为空，除非 activation rule
  显式声明 world watch；
- matcher publication 只能服务下一次同 piece、同 definition generation 的搜索加速；
- `semanticFingerprint` 只比较下游依赖可见输出，不用于判断世界内容是否未变化；
- piece identity 必须绑定 compiled definition generation，不能跨重新编译复用。

### 22.5 StructureContribution

`StructureContribution` 只表示一个 piece 对最终 formed state 的贡献，不包含 traversal scratch：

```java
public final class StructureContribution {
    private final Map<StructureRequirementKey, StructureRequirement> requirements;
    private final Map<StructureCountKey, Integer> counts;
    private final Set<PartContributor> parts;
    private final Map<MultiblockAbility<?>, Set<AbilityContributor>> abilities;
    private final Set<BlockPos> variantActiveBlocks;
    private final Map<StructureContributionKey<?, ?>, List<?>> typedEmissions;
}
```

内置贡献的合并规则：

| 数据 | fold 规则 |
|---|---|
| requirement | 同 key 声明必须兼容，否则 definition/aggregate 失败。 |
| count | 按 key 求和。 |
| part | 按稳定 contributor identity 去重后 union。 |
| ability | 按 ability 和 contributor identity 去重后 union。 |
| variant active block | 按位置 union，并按 piece/cell 顺序生成兼容 list。 |
| repeat | 不进入通用 contribution，由 piece result metadata 持有。 |
| center | 不进入通用 contribution，由 piece result metadata 持有。 |
| channel/custom value | 通过 typed key 的 reducer fold。 |

requirement 必须显式区分 scope：

```java
public enum StructureRequirementScope {
    PIECE,
    STRUCTURE
}
```

- `PIECE` 在当前 piece evaluation 完成后验证；
- `STRUCTURE` 只在全表 fold 后验证；
- per-layer 和 repeat candidate 约束继续属于 traversal-local validation；
- 同一个 requirement key 的 scope 必须固定，不能由不同 piece 以不同 scope 声明。

`PartContributor` 和 `AbilityContributor` 至少包含：

```text
world position
part reference or resolvable handle
ability
declaration key
piece ordinal
cell ordinal
```

稳定 identity 首选 world position + contributor kind。不能只对各 piece 的 raw count 求和，否则同一
part 被重叠 piece 命中时会重复计数。

### 22.6 Typed contribution key

`StructureSessionKey` 继续表示一次 traversal 中的事务 scratch。它不应直接承担可发布结果语义。

新增独立 key：

```java
public final class StructureContributionKey<E, A> {
    private final String id;
    private final Supplier<A> identity;
    private final BiFunction<A, E, A> reducer;
    private final Function<A, StructureValueValidation> validator;
    private final LegacyProjection<A> legacyProjection;
}
```

其中：

- `E` 是 element 发出的 immutable emission；
- `A` 是 fold 后的 aggregate value；
- reducer 必须纯、确定、不得访问 world/controller；
- fold 顺序固定为 piece declaration order，再按 cell traversal order；
- reducer 不强制可交换或可逆，因为每次都从 result table 重新 fold；
- mutable emission 和 aggregate 必须有明确 copier 或 immutable 类型；
- key id 在一个 definition 中必须唯一且语义一致。

内置 reducer：

```text
SUM
MIN
MAX
UNIFORM
SET_UNION
ORDERED_LIST
FIRST_NON_NULL
LAST_NON_NULL
```

`FIRST_NON_NULL` 和 `LAST_NON_NULL` 只因 fold 顺序固定而确定。新结构应优先使用
`UNIFORM`、`MIN`、`MAX` 等能表达约束的 reducer，不要把旧的覆盖写入习惯继续扩散。

channel 和 tier 迁移为 typed key：

```java
StructureContributionKey<CasingTier, CasingTier> tier =
        StructureContributionKeys.uniform("coil-tier");
```

兼容 `PatternMatchContext` 只由 `legacyProjection` 在最终结果边界写入。

### 22.7 Element 执行契约

增量安全的 direct element 必须满足：

1. `match` 只读取当前 cell、只读 piece input 和显式 controller snapshot。
2. formation side effect 只能写入当前 piece 的 collector。
3. 不直接修改 controller、part ownership、inventory、world 或全局静态状态。
4. 不读取其他 piece 的隐式 session mutation。
5. 跨 piece 输入必须通过声明过的 `StructureInputKey`。
6. requirement 的 min/max 在 aggregate validation 执行，不能用当前共享 aggregate 提前拒绝。
7. branch、chain 和 repeat candidate 仍使用 piece-local transaction。

还有一条重要限制：

> `STRUCTURE` scope 的 count、ability max、uniform channel 等 aggregate 状态不能影响单个 cell
> 选择哪个 branch。

例如旧实现可能先通过共享 `canRecordCount()` 判断全局 hatch 上限，再决定 chain 是否尝试其他
element。独立 piece evaluator 中看不到其他 piece 的最终 count，因此 contribution-eligible element
必须先按当前 world cell 做确定分类，再由 aggregate validation 判断全局上限。

如果旧结构依赖“达到跨 piece 上限后改变当前 cell 的匹配分支”，它属于顺序敏感 legacy 语义：

- 迁移为不依赖 aggregate 的确定分类；或
- 把限制改为 `PIECE` scope；或
- 保持 `OPAQUE` 并回退 legacy active-graph。

world read 必须通过受跟踪接口：

```java
public interface StructureReadView {
    IBlockState getBlockState(BlockPos pos);
    @Nullable TileEntity getTileEntity(BlockPos pos);
}
```

piece-local evaluator 持有 `StructureReadTracker`：

- traversal 到达一个 cell 时自动记录该 cell position；
- element 额外读取位置时由 `StructureReadView` 自动记录；
- branch rollback 同时回滚 read tracker；
- branch commit 保留决定最终结果所依赖的读取；
- condition 的显式 world dependency 在 activation evaluation 时记录；
- result freeze 时生成 immutable `watchedPositions`。

`StructureEvaluationContext.getWorld()` 和其他 raw world escape hatch 对 incremental element 不可用。
需要 raw world 的 element 必须报告 `OPAQUE`。不能只靠运行时观察“这次似乎没额外读取”来授予资格。

建议增加：

```java
public enum StructureIncrementalSupport {
    TYPED_CONTRIBUTION,
    MATCH_ONLY,
    OPAQUE
}
```

语义：

- `TYPED_CONTRIBUTION`：匹配和所有 formation state 都符合本章契约。
- `MATCH_ONLY`：元素没有 formation contribution，只做纯 cell match。
- `OPAQUE`：使用 legacy context、未知 controller state 或外部副作用。

首版资格规则采用 all-or-nothing：

```text
definition 中所有 active-capable element 都不是 OPAQUE
AND 所有 condition/offset dependency 可编译
AND 所有 custom key 都有 reducer
=> INCREMENTAL_ELIGIBLE
```

否则整个 definition 回退 active-graph。首版不尝试把 opaque piece 与 typed piece 混合增量，
避免 shared legacy context 污染 aggregate 边界。

### 22.8 PatternMatchContext 退场协议

`PatternMatchContext` 的目标定位：

```text
legacy input adapter
legacy result projection
deprecated controller callback payload
```

它不再是：

```text
direct element 的 canonical collector
跨 piece 通信总线
channel/tier 的 canonical storage
增量 result cache
```

迁移步骤：

1. `StructureEvaluationContext.getLegacyContext()` 标记 internal/deprecated。
2. built-in direct element 全部改用 typed collector 和 typed input。
3. `StructureMatchCollector.recordChannelValue/setValue` 迁到 contribution key。
4. `StructureCheckResult` 从 aggregate 生成一次 compatibility context。
5. `formStructure(PatternMatchContext)` 在 addon 迁移期继续工作。
6. 新增 typed callback，例如：

```java
protected void formStructure(@NotNull FormedStructureView formed);
```

7. legacy override 存在时由 adapter 调用旧 callback；新 controller 不再取得 mutable context。

任意 legacy context 写入都使 definition 增量资格降为 `ACTIVE_GRAPH_REQUIRED`，除非该 adapter
明确把写入转换成有 reducer 的 typed contribution。

当前实现状态：

- session-backed direct element 调用 `getLegacyContext()` 时只得到隔离 compatibility view；内部写入不会进入
  eligible result、piece publication 或 callback payload。
- `StructureMatchCollector.recordChannelValue/setValue` 在 direct/session path 只写 typed
  `StructureContributionKey` emission；无 session legacy traversal 仍保留旧 `PatternMatchContext` 写入。
- piece/result table 的 compatibility context 由 contribution projection 生成；full、incremental 和
  active-graph fallback 的成功结果都使用 fold 后 compatibility projector。
- `MultiblockControllerBase` 新增 `formStructure(FormedStructureView)`；默认实现把 projector 生成的
  `PatternMatchContext` 副本传给旧 `formStructure(PatternMatchContext)`，新 controller 覆盖 typed callback
  后不会自动触发 legacy callback。

### 22.9 Piece dependency graph

dependency graph 是 definition 编译产物，节点是 piece ordinal，边表示：

```text
source result 的某个语义输出变化
可能改变 target 的 activation、center、repeat search、match input 或 contribution
```

边类型：

```java
public enum PieceDependencyAspect {
    ACTIVATION,
    CENTER,
    REPETITIONS,
    CONTRIBUTION_VALUE,
    CONTROLLER_STATE,
    ANY_RESULT
}
```

编译器自动加入：

- repeat anchor -> `DynamicOffsetPiece`，aspect 为 `CENTER | REPETITIONS`；
- repeat anchor -> `DynamicRepeatGroupPiece`，aspect 为 `CENTER | REPETITIONS`；
- contextual condition 声明的 piece input -> conditional piece；
- element 声明的 `StructureInputKey` producer -> consumer；
- 显式 builder dependency。

建议 declaration API：

```java
StructureCondition.withDependencies(
    condition,
    StructureDependency.piece("core", PieceDependencyAspect.CONTRIBUTION_VALUE),
    StructureDependency.external(CONTROLLER_MODE, PieceDependencyAspect.CONTROLLER_STATE)
);
```

`StructureCondition` 保留 `BooleanSupplier` 兼容桥，但增加 typed dependency declaration：

```java
public interface StructureCondition<T> extends BooleanSupplier {
    boolean test(StructureActivationContext<T> context);
    default Set<StructureDependency> dependencies();
}
```

旧 `BooleanSupplier` 和没有 dependencies 的 contextual condition 都是 opaque。`StructureDependencyCompiler`
从 compiled `MultiPiecePattern` 生成 `StructureEligibilityPlan`，因此 direct definition、legacy
`fromMultiPiecePattern(...)` adapter 和 prebuilt dynamic piece 都走同一套诊断。

图约束：

- 新 definition 只允许依赖声明顺序之前的 piece；
- 编译时检测缺失 producer、自依赖和 cycle，并给出稳定 fallback reason；
- 当前落地实现中 direct V3 definition 与 legacy adapter 均不抛构建异常，而是生成
  `StructureEligibilityPlan.fallback(...)`，由 runtime 自动进入 active-graph fallback；
- graph 保存每条边的 reason，供 trace 和调试输出。

### 22.10 外部依赖

condition 可能依赖 controller mode、升级、配置或其他非结构方块。仅靠 world position dirty event
不能发现这些变化。

新增稳定 key：

```java
public final class StructureExternalDependencyKey<T> {
    private final String id;
    private final Function<MultiblockControllerBase, T> snapshot;
    private final BiPredicate<T, T> equivalent;
}
```

`StructureExternalDependencySnapshot` 记录 key -> value，并用 key 自带的 equivalent predicate 比较
变化。`StructureEligibilityPlan` 同时保存 external key -> affected root pieces；变化 key 可以直接转为
dependency graph roots，再交给 closure 算法处理。

`CommittedStructureGraph` 保存上次依赖 snapshot。controller 状态变更时优先显式调用：

```java
structureRuntime.invalidate(CONTROLLER_MODE);
```

增量检查开始时也比较 snapshot，作为漏通知保护。变化的 external key 转换成 dependency graph roots。

外部 world 读取必须：

- 声明固定 watched positions/AABB 并注册到 `StructureWorldIndex`；或
- 标记 opaque 并回退 active-graph/polling。

不能允许 condition 任意读取 world 后仍宣称增量安全。

### 22.11 Dirty root 与 position index

成功 aggregate 生成：

```java
public final class StructurePositionIndex {
    private final Long2ObjectMap<PieceBitSet> ownersByWatchedPosition;
    private final LongSet allWatchedPositions;
    private final LongSet allFormedPositions;
}
```

世界变化流程：

1. `StructureWorldIndex` 通过 watched position 找到 formed controller。
2. 从 `ownersByWatchedPosition` 取得所有直接受影响 piece。
3. 将 piece ordinal 加入 controller pending dirty roots。
4. cooldown 内继续 union roots，不执行检查。
5. scheduler 消费 roots 并请求 incremental recheck。

重叠位置必须标记全部 owner。不能只选择第一个 piece。

当 candidate commit 改变 center、repeat 或 activation 时，新的 position index 与 result table 一起
原子发布，然后 world index 重新注册。旧 index 在 commit 完成前仍是当前真相。

`watchedPositions` 是正确性边界，不是可选性能信息：

- fixed piece 通常 watch 全部 pattern cells；
- repeat search 必须 watch 决定当前 repeat count 的边界探测 cells；
- branch/chain 只需保留成功结果仍依赖的读取；若失败分支的读取会影响未来选择，也必须保留；
- condition 的固定 world dependency 加入对应 piece 的 watch set；
- 无法界定读取范围的 condition/element 标记 opaque；
- `allFormedPositions` 继续服务 part registration、渲染和结构 ownership；
- `allWatchedPositions` 服务 dirty event，不应被误当作 formed structure footprint。

如果 matcher 不能证明最小敏感集，可以保守保存本次成功求值的全部 world read set。允许多 watch，
不允许漏 watch。

### 22.12 Incremental check 算法

前置条件：

```text
definition.incrementalPlan == ELIGIBLE
controller 有已提交 baseline table
orientation 和 definition generation 未变化
dirty roots 非空
```

伪代码：

```java
IncrementalCheckResult checkIncremental(
        CommittedStructureGraph baseline,
        Set<PieceId> dirtyRoots,
        StructureCheckRequest request) {

    CandidateTable candidate = baseline.table.shallowCopy();
    Set<PieceId> closure = plan.dependencies.closure(dirtyRoots);

    for (PieceId piece : plan.topologicalOrder()) {
        if (!closure.contains(piece)) {
            continue;
        }

        PieceInputView inputs = candidate.inputsFor(piece);
        boolean active = plan.activation(piece).test(inputs);

        if (!active) {
            candidate.replace(piece, PieceEvaluationResult.inactive(
                    piece, inputs.activationWatchedPositions()));
            continue;
        }

        PieceEvaluationOutcome outcome = evaluatePiece(piece, inputs, request);
        if (!outcome.isMatched()) {
            return IncrementalCheckResult.failure(outcome.failure());
        }
        candidate.replace(piece, outcome.result());
    }

    AggregatedStructureResult aggregate = fold(candidate);
    StructureValidation validation = validate(aggregate);
    if (!validation.success()) {
        return IncrementalCheckResult.failure(validation);
    }

    return IncrementalCheckResult.success(
            StructureGraphPublication.of(candidate.freeze(), aggregate));
}
```

首版 closure 是保守静态闭包：边存在就重算 target。

第二阶段可增加 output-sensitive pruning：

1. edge 声明关心的 aspect；
2. source 重算后比较该 aspect 的旧、新 fingerprint；
3. fingerprint 未变化时不传播该 edge；
4. direct dirty piece 无论 fingerprint 是否变化都必须读取世界并重算；
5. pruning 只减少下游重算，不能跳过 aggregate fold。

### 22.13 Full check 与 baseline

full check 不能继续依赖一个跨 piece 可变 session 才能工作，否则 incremental path 永远只是旁路。

目标 full check：

1. 按 topological/declaration order 独立 evaluate 每个 active piece。
2. 每个 piece 产生 `PieceEvaluationResult`。
3. 将 table fold 成 `AggregatedStructureResult`。
4. 执行结构级 validation。
5. 返回与 incremental check 相同的 `StructureGraphPublication`。

迁移期间保留两条 evaluator：

```text
CONTRIBUTION_V1 -> independent piece evaluation + fold
LEGACY_SESSION  -> current active-graph shared session
```

只有 `CONTRIBUTION_V1` 结果可作为 incremental baseline。不能从 legacy shared session 的累计
checkpoint 猜测每个 piece 的 delta。

### 22.14 Fold 与结构级 validation

fold 必须是纯函数：

```java
AggregatedStructureResult fold(
        IncrementalStructurePlan plan,
        StructureResultTable table)
```

顺序：

1. 遍历 declaration order 中的 active result。
2. 合并 requirement declarations。
3. 累加 counts。
4. union parts、abilities 和 active blocks。
5. 执行 typed key reducer。
6. 从 piece result 生成 repeats、centers 和 channel metadata。
7. 构建 immutable aggregate。

validation 在 fold 后统一执行：

- requirement min/max；
- global predicate/count；
- global ability limit；
- ability group limit；
- part sharing/ownership precondition；
- uniform channel/tier；
- custom key validator；
- structure-specific aggregate validator。

局部 traversal 可以继续执行 per-layer、repeat candidate 和单 piece 几何约束。结构级 max
不能在某个 piece 扫描到一半时根据不完整 aggregate 失败。

### 22.15 Publication 与提交

建议用更高层 publication 取代只包含 matcher cache 的 payload：

```java
public final class StructureGraphPublication {
    private final long expectedGeneration;
    private final long expectedDefinitionGeneration;
    private final StructureWorldReadToken expectedWorldReads;
    private final StructureResultTable table;
    private final AggregatedStructureResult aggregate;
    private final StructurePositionIndex positionIndex;
    private final PieceRuntimes.Publication legacyRuntimeView;
}
```

commit 前先构造：

```java
public final class PreparedStructureCommit {
    private final StructureGraphPublication publication;
    private final ImmutableList<IMultiblockPart> parts;
    private final ImmutableAbilityInstances abilities;
    private final FormedStructureMetadata metadata;
    private final StructureChannelValues channels;
    private final PatternMatchContext legacyCallbackView;
}
```

prepare 阶段允许失败，但不能修改 controller：

1. 验证 expected controller/definition generation。
2. 验证 orientation、controller position 和 request token。
3. 验证 evaluation 期间读取的 chunk/world revision 仍然有效。
4. 执行 controller assembly precondition，例如 part sharing rejection。
5. 解析并验证 parts、ability instances、metadata 和 compatibility view。
6. 生成 `PreparedStructureCommit`。

apply 阶段必须设计成不可失败的引用/字段发布：

1. 进入 event suppression。
2. 原子替换 `CommittedStructureGraph` 引用。
3. 发布 legacy `PieceRuntimes` view。
4. 发布预先构造的 controller part/ability view。
5. 发布 formed metadata/channel。
6. 更新 world position index。
7. 退出 event suppression。
8. 调用 typed callback 或 legacy compatibility callback。

prepare 任一步失败都不能发布 candidate。

同步主线程 evaluator 当前通常不会在检查中途被另一个 tick 修改 world，但 revision token 仍应作为
统一协议保留，覆盖 reentrant callback、未来 async piece evaluation 和测试注入场景。

这里的“原子”是 controller 生命周期意义上的原子：

- server thread 和 event suppression 不能观察中间状态；
- canonical `CommittedStructureGraph` 通过一次引用替换发布；
- legacy controller 字段只是该 graph 的投影视图，不再拥有独立真相。

如果 apply 段发生意外异常，不能继续以 formed 状态运行，应记录 lifecycle failure、注销 index 并
进入 invalidation。不能尝试回滚已经执行的 addon 外部副作用。

如果 callback 仍可能抛异常，应在发布前完成所有可验证工作。目标状态下 formation callback 只消费
immutable formed view，不负责决定结构是否成立；legacy callback 异常按生命周期故障处理。

### 22.16 失败语义

incremental piece mismatch：

- 返回带 piece/cell 的 failure；
- candidate table 丢弃；
- baseline 在 controller invalidation 完成前保持可读；
- controller 按现有生命周期失效并注销 index；
- 不发布只包含部分新 result 的 table。

aggregate validation failure：

- path 使用 `incremental-aggregate`；
- trace 包含 dirty roots、dependency closure 和失败 key；
- 同样不发布 candidate。

fallback 不是 failure。它转为 active-graph request，并记录低频 reason：

```java
public enum IncrementalFallbackReason {
    NO_BASELINE,
    DEFINITION_NOT_ELIGIBLE,
    OPAQUE_ELEMENT,
    OPAQUE_CONDITION,
    UNKNOWN_EXTERNAL_DEPENDENCY,
    DEPENDENCY_CYCLE,
    DEFINITION_GENERATION_CHANGED,
    ORIENTATION_CHANGED,
    POSITION_NOT_INDEXED,
    UNSUPPORTED_CONTRIBUTION_KEY
}
```

### 22.17 Scheduler 策略

`DirtyCheckDecision` 目标 action：

```text
CLEAN
DEFERRED
INCREMENTAL
ACTIVE_GRAPH
UNREGISTERED
```

选择规则：

```text
formed
AND baseline exists
AND plan eligible
AND dirty roots known
=> INCREMENTAL

否则需要检查时
=> ACTIVE_GRAPH
```

polling 发现“需要确认但不知道哪个位置变化”时不能伪造单 piece root，应执行 active-graph。

dynamic runtime、definition rebuild、orientation change 和 first formation 都执行 full check。

### 22.18 Async 关系

async snapshot 与 incremental contribution 可以复用 `PieceEvaluationResult` 的数据形状，但不能复用
发布权限。

安全边界：

- snapshot result 不保存 live tile entity/part reference；
- snapshot 只能产出 structural match 和纯值 contribution；
- 主线程必须验证 snapshot revision/token；
- formed controller 的增量 live check 首版仍在主线程执行；
- 后续若异步重算 dirty piece，也必须在主线程 live-confirm contributor 和 commit token。

不要把“piece 可以独立计算”误解为“piece 可以无条件异步发布”。

### 22.19 Legacy 与 fallback 矩阵

| 输入特征 | Full check | Incremental |
|---|---|---|
| 全 direct typed element | contribution evaluator | 支持 |
| direct element 但写 legacy context | legacy session | 不支持 |
| `LegacyElement` 纯 block predicate，未声明纯度 | legacy session | 不支持 |
| opaque `BooleanSupplier` condition | legacy session | 不支持 |
| typed condition + 显式 dependency | contribution evaluator | 支持 |
| dynamic offset + 已解析 anchor edge | contribution evaluator | 支持 |
| custom typed key + reducer | contribution evaluator | 支持 |
| custom session key 只作 piece-local scratch | contribution evaluator | 支持 |
| custom session key 跨 piece 读取 | legacy/fallback，直到迁到 input key | 不支持 |

未来可以给已证明无 side effect 的 legacy predicate 增加 `MATCH_ONLY` adapter，但这必须显式 opt-in，
不能根据实现类或运行结果猜测。

### 22.20 API 草案

operation 入口：

```java
StructureCheckResult checkFull(StructureOperationRequest request);

StructureCheckResult checkIncremental(
        StructureOperationRequest request,
        DirtyPieceSet dirtyPieces);

StructureCheckResult checkActiveGraph(StructureOperationRequest request);
```

最终 `checkActiveGraph` 仅作为 legacy/fallback 路径。对 contribution-eligible definition，
full 和 active graph 都可以转发到同一个 independent-piece evaluator。

collector：

```java
public interface PieceContributionCollector {
    void declare(StructureRequirementKey key, StructureRequirement requirement);
    void increment(StructureCountKey key);
    void addPart(PartContributor part);
    void addAbility(AbilityContributor ability);
    <E, A> void emit(StructureContributionKey<E, A> key, E value);
}
```

只读输入：

```java
public interface PieceInputView {
    Optional<PieceEvaluationView> piece(String name);
    <T> Optional<T> value(StructureInputKey<T> key);
    <T> T external(StructureExternalDependencyKey<T> key);
}
```

element 不能取得 mutable aggregate。

### 22.21 迁移阶段

#### Phase A：Contribution 基础，不改变扫描范围（已完成）

1. 新增 immutable `StructureContribution` 和 builder。
2. 新增 typed contribution key/reducer。
3. built-in casing、hatch、tier/channel 改写到 typed collector。
4. full check 仍扫描全部 piece，但同时产出 per-piece result。
5. aggregate fold 与旧 shared session 结果做 differential test。

完成条件：

- eligible fixture 的 typed aggregate 与旧结果一致；
- formation callback compatibility context 一致；
- 没有增量调度行为变化。

实现状态：

- 已新增 immutable `StructureContribution`、builder 和 per-piece contribution capture。
- 已新增 `StructureContributionKey` typed reducer，覆盖 `SUM`、`MIN`、`MAX`、`UNIFORM`、
  `SET_UNION`、`ORDERED_LIST`、`FIRST_NON_NULL`、`LAST_NON_NULL` 等基础 reducer。
- `StructureMatchCollector` 已把 count、part、ability、variant active block、channel/value 写入收口到
  typed contribution；built-in hatch/casing/tier/channel 元素通过 collector 产出 contribution。
- full check 和 active-graph check 都会生成 `PieceEvaluationResult` / `StructureResultTable`，
  aggregate 由 `StructureAggregateFolder.fold(...)` 统一生成。
- differential tests 已覆盖 typed aggregate 与 active-graph oracle 的结果表、compatibility context、
  operation state 和 validation failure。
- Phase A 不改变扫描范围或调度策略：增量 dependency graph 和 dirty-piece 调度仍属于 Phase C/D。

#### Phase B：独立 piece evaluator（已完成）

1. 每个 piece 使用独立 local session。
2. global validation 移到 fold 后。
3. full check 生成 result table/publication。
4. `lastAggregatedContext` 不再参与 eligible path。

完成条件：

- full contribution evaluator 成为 eligible definition 的默认路径；
- active-graph legacy evaluator 仍可 fallback。

实现状态：

- `StructureRuntime.check(...)` 在存在 `StructureDefinition` 时默认进入 full contribution evaluator。
- full evaluator 为每个 active piece 创建独立 `StructureMatchSession`，piece 成功后只发布
  `PieceEvaluationResult` 中的 contribution、positions、repeat 和 matcher publication。
- `StructureAggregateFolder.fold(...)` 从完整 `StructureResultTable` 生成 metadata、operation state 和
  compatibility context；结构级 count、ability、uniform channel 等 validation 只在 fold 后执行。
- full publication 不依赖 `PieceRuntime.lastAggregatedContext`。该字段仍作为 legacy runtime accessor/cache
  留存，active-graph fallback 可以继续使用旧共享 session 语义。
- 显式 `StructureRuntime.checkActiveGraph(...)` / controller active-graph check 仍保留，作为 legacy/fallback
  oracle 与事件图重查路径。

#### Phase C：Dependency compiler

1. 编译 dynamic anchor edges。
2. 引入 typed condition dependencies。
3. 引入 external dependency snapshot。
4. 输出 eligibility 和 fallback reason。

完成条件：

- graph 可诊断；
- cycle、unknown dependency 和 opaque condition 都有确定行为。

实现状态：已完成。

- 新增 `StructureDependencyCompiler`，对 compiled `MultiPiecePattern` 生成
  `StructureEligibilityPlan`。
- 新增 `PieceDependencyGraph`，节点为 declaration order piece name/ordinal，edge 保存 source、
  target、`PieceDependencyAspect` 和 reason；提供 outgoing/incoming 查询、dirty root closure 和
  `describe()` 诊断输出。
- `DynamicOffsetPiece` / `DynamicRepeatGroupPiece` 暴露 anchor metadata，compiler 自动加入
  `anchor -> dependent` edge，aspect 为 `CENTER | REPETITIONS`，reason 为 `dynamic-anchor`。
- `StructureCondition` 增加 `dependencies()` 与 `withDependencies(...)` helper；typed piece
  dependency 编译为 condition edge，typed external dependency 收集为 snapshot key 和 affected root。
- 新增 `StructureExternalDependencyKey` 与 `StructureExternalDependencySnapshot`；snapshot diff
  使用 key 自带 equivalent predicate，changed key 可映射为 root piece set。
- `IStructureElement.getIncrementalSupport()` 默认 `TYPED_CONTRIBUTION`；`LegacyElement` 和带 callback /
  lazy supplier 的 `WrapperElement` 返回 `OPAQUE`。
- `StructureDefinition.getEligibilityPlan()` 懒加载并缓存 plan。
- `StructureOperationEvaluator.check(...)` 只在 plan eligible 时进入 full contribution evaluator；
  不 eligible 时自动运行 active-graph fallback，并把 `StructureCheckResult` trace path 标为
  `active-graph-fallback`，同时挂载 fallback reason。

确定行为：

| case | behavior |
|---|---|
| opaque legacy `BooleanSupplier` condition | `fallback=OPAQUE_CONDITION` |
| contextual `StructureCondition` 未声明 dependencies | `fallback=OPAQUE_CONDITION` |
| unknown piece dependency | `fallback=UNKNOWN_DEPENDENCY` |
| self/future dependency 或 graph cycle | `fallback=DEPENDENCY_CYCLE` |
| null/unknown external dependency declaration | `fallback=UNKNOWN_EXTERNAL_DEPENDENCY` |
| legacy predicate element / opaque wrapper | `fallback=OPAQUE_ELEMENT` |
| eligible definition | `StructureRuntime.check(...)` 默认 full contribution evaluator |
| ineligible definition | `StructureRuntime.check(...)` 自动 active-graph fallback |

#### Phase D：Event-driven incremental（已完成）

1. world index 保存 position owner bitset。
2. pending dirty state 保存 piece root set。
3. evaluator 重算 dependency closure。
4. 成功 publication 原子替换 table/index。
5. failure 走现有 invalidation。

完成条件：

- world change 能通过 watched position owner index 定位 dirty root piece；
- pending dirty state 只保存 piece root set，实际重算范围由 dependency closure 决定；
- incremental evaluator 复用未受影响的 committed `PieceEvaluationResult`，只重读 closure 内 piece；
- 成功 publication 一次性替换 `CommittedStructureGraph`、`StructureResultTable`、
  `StructurePositionIndex` 和 legacy `PieceRuntimes` projection；
- failure 不发布 candidate graph，继续走现有 formed controller invalidation。

实现状态：

- 新增 `StructurePositionIndex`，从 `StructureResultTable` 编译 watched position -> owner bitset；
  `StructureWorldIndex` 注册 graph index 时用 watched positions 建 chunk index，兼容查询仍返回 formed positions。
- 新增 `StructureDirtyState`，`StructureRuntime` 保存 pending dirty root set；world block change 只把 affected
  owner roots 写入 runtime，不在事件回调中执行结构检查。
- 新增 `CommittedStructureGraph`，controller 成功 check 后保存 generation、result table、aggregate、
  position index、runtime publication、orientation 和 external dependency snapshot。
- full contribution check 成功时生成 graph publication；event-driven scheduler 在 eligible baseline 存在且
  orientation 未变化时调用 `StructureRuntime.checkIncremental(...)`。
- incremental evaluator 使用 `StructureEligibilityPlan.getGraph().dependentClosure(roots)` 计算重算范围：
  closure 内 piece 使用独立 local session 重新 match，closure 外 piece 从 committed table 复用；fold 后统一
  global validation。
- external dependency snapshot diff 会映射到 root set 并参与同一条 incremental path；非 block event 的
  controller/external 状态变化可通过 `MultiblockWorldData.enqueueDirtyRoots(...)` 显式唤醒 scheduler。
- 成功 commit 通过 `MultiblockStructureCommitter` 发布 graph publication，再刷新 world index；
  不成功结果没有 graph publication，formed controller 沿现有 `invalidateStructure()` 失效并注销 index。
- `StructureCheckResult` 暴露 `getGraphPublication()`、`getIncrementalCheckResult()` 和
  `usedIncrementalEvaluator()`，用于诊断 result table/publication 与增量路径。
- fallback 行为确定：无 baseline、definition 不 eligible、orientation 变化、opaque/unknown/cycle 等 plan
  fallback reason 都回到 full contribution 或 active-graph legacy fallback。

新增测试覆盖：

- full check 生成 graph publication；
- dirty root 只重算 dependency closure，独立 piece 复用；
- dynamic anchor dirty root 会重算 dependent piece；
- incremental failure 不发布 successor graph，baseline generation 保持；
- watched position 重叠时 owner bitset 返回多个 owner。

#### Phase E：PatternMatchContext 降级（已完成）

1. direct element 禁止内部 legacy context 写入。
2. channel/tier 全部 typed。
3. 新 controller 使用 `FormedStructureView` callback。
4. legacy callback 只由 compatibility projector 服务。

实现摘要：

- direct/session evaluation 的 legacy context 被降级为隔离 view；legacy predicate boundary 仍可服务
  opaque fallback 和旧 traversal。
- channel/tier 通过 typed contribution emission/fold/projector 输出，`PatternMatchContext` 不再是
  eligible path 的 canonical storage。
- commit 边界构造 `FormedStructureView` 并调用 typed callback；默认 typed callback 才桥接到旧
  `formStructure(PatternMatchContext)`。
- 新增测试覆盖 direct 写入隔离、typed projection、active-graph fallback compatibility context 以及
  typed/legacy formation callback 分流。

#### Phase F：可选优化（已完成）

1. dependency aspect fingerprint pruning。
2. immutable collection 结构共享。
3. contribution fold cache。
4. 安全的 dirty piece snapshot precheck。

实现状态：已完成。

- `PieceEvaluationResult` 生成 per-aspect fingerprint；incremental evaluator 对 direct dirty root
  始终重算，只在 source 重算后根据 edge aspect fingerprint 决定是否继续传播到 downstream piece。
- `StructureIncrementalCheckResult` 输出 `prunedPieces`、snapshot precheck attempted/failed 等诊断，
  因而 graph 的实际重算路径可解释。
- `StructureResultTable` 和 piece result 的空/单元素 collection 使用不可变共享结构；多元素仍防御复制。
- `StructureAggregateFolder` 支持 baseline fold cache。仅当没有 initial compatibility context，且新 table
  semantic fingerprint 与 baseline 一致时复用已发布 aggregate。
- dirty piece snapshot precheck 只作为安全诊断边界：它比较 baseline watched positions 的 cached block
  state，不直接发布 snapshot 结果，也不跳过后续 live incremental check。
- 新增/更新测试覆盖 dynamic anchor unchanged pruning、contribution aspect changed propagation、fold cache
  aggregate reuse，以及 snapshot precheck diagnostic。

### 22.22 验证策略

单元测试：

- 每种 built-in contribution reducer；
- requirement 合并冲突；
- part/ability contributor 去重；
- inactive piece 空 contribution；
- failed branch 不泄漏 emission；
- local session key 不跨 piece；
- opaque element/condition eligibility；
- dependency cycle 和 unresolved producer；
- dynamic anchor closure；
- external dependency invalidation；
- publication generation rejection。

差分测试：

```text
同一 world fixture
-> full contribution evaluator
-> incremental evaluator
-> legacy active-graph oracle
```

比较：

- matched；
- formed positions；
- repeats 和 centers；
- parts；
- ability counts；
- channels/tier；
- missing abilities；
- compatibility context；
- failure kind 和最深 progress。

随机 mutation 测试：

1. 先 full form。
2. 随机改变一个或多个 indexed positions。
3. 执行 incremental。
4. 从空 baseline 执行 full contribution check。
5. 两者结果必须一致。

必须覆盖：

- 多个 dirty roots；
- 重叠 piece positions；
- repeat count 保持和变化；
- dynamic dependent center 保持和变化；
- conditional active -> inactive -> active；
- global min/max 因不同 piece 组合通过或失败；
- uniform channel 冲突；
- part sharing rejection；
- orientation/definition generation 变化；
- commit 前再次 dirty 的 stale token。

### 22.23 低频诊断与 shadow validation

遵循当前日志原则，不在 cell 热路径增加日志。

新增受 `debugStructureCheck` 或独立配置控制的 summary：

```text
[StructureIncremental] controller=...
mode=incremental
roots=2
closure=4/11
rechecked=4
reused=7
worldCells=183
foldPieces=11
fallback=none
result=matched
```

fallback 只记录一次 summary：

```text
mode=active-graph-fallback
reason=OPAQUE_CONDITION
piece=outer_ring
```

开发期增加低频 shadow validation：

```text
incremental 成功
AND debug/shadow 开关开启
AND 采样周期命中
-> 额外运行 full contribution check
-> 比较 semantic result
```

不一致时：

- 不用 shadow result 静默覆盖线上结果；
- 输出 controller、roots、closure、首个不同 field 和 dependency reasons；
- 可配置为开发环境 fail-fast；
- 相同摘要至少间隔 5 秒输出，避免高频日志。

### 22.24 性能验收

需要记录但默认不输出的计数：

```text
direct dirty roots
dependency closure size
reused/rechecked pieces
world cell reads
watched/formed position count
repeat candidates
fold emission count
evaluation/fold/commit time
fallback reason
```

最低验收标准：

- 单 fixed piece dirty 时，其他无依赖 fixed piece 的 world cell reads 为 0；
- dynamic anchor 未 dirty 时，不重读 dependent piece；
- anchor repeat/center 变化时，dependent piece 必须进入 closure；
- aggregate fold 不触发 world 或 tile entity 读取；
- incremental 与 full contribution evaluator 语义一致；
- 对不合格 definition 的性能不低于当前 active-graph 路径一个显著常数级。

### 22.25 实现顺序建议

第一批代码只做 Phase A：

1. `StructureContribution`
2. `StructureContributionKey`
3. `PieceEvaluationResult`
4. `StructureResultTable`
5. `StructureAggregateFolder`
6. built-in collector 迁移
7. full-scan differential tests

在 full contribution evaluator 与当前 active-graph 结果稳定一致之前，不接 scheduler，也不根据 dirty
标记跳过任何 piece。这样可以先证明“独立贡献结果模型”正确，再打开真正的增量执行边界。

### 22.26 首版实现决策

以下选择作为 Phase A 到 Phase D 的默认实现，不留给每个调用点自行决定：

1. **Result table 不写 NBT。** controller load/reinitialize 后执行 full baseline。
2. **Fold 顺序固定。** 使用 compiled piece ordinal 和 cell traversal ordinal。
3. **Definition generation 单调递增。** 不依赖昂贵或不稳定的结构 hash。
4. **Read set 自动采集。** 当前 cell 和 `StructureReadView` 读取进入 piece-local tracker。
5. **Requirement key 强类型化。** 不再使用任意 `Object` identity 作为新 API 的公开 key。
6. **Contribution key 使用 namespaced id。** id 冲突且 schema 不同视为 definition error。
7. **Part/ability 去重按稳定 contributor identity。** 首版至少包含 world position 和 contributor kind。
8. **Eligibility 显式 opt-in。** 未声明 incremental support 的 custom element 默认 `OPAQUE`。
9. **未知依赖 fail closed。** 回退 active-graph，不尝试局部重算。
10. **首版静态 closure。** aspect fingerprint pruning 延后到差分测试稳定之后。
11. **Canonical state 单一。** `CommittedStructureGraph` 是真相，legacy fields 是发布投影。
12. **日志只做低频 summary。** 不在 cell match 热路径输出逐格日志。
