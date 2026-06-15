# Structure System V3 Design

**Implementation snapshot:** 2026-06-15

**Scope:** `gregtech.api.pattern`、`gregtech.api.pattern.element`、
`gregtech.api.metatileentity.multiblock`、JEI/preview/tooling 接入点，以及 GregTech 自带
multiblock controller 的迁移边界。

**External compatibility sample:** 上级目录 `GTQTCore`。该项目当前大量使用旧结构 API，
包括 `createStructureTemplate()`、`formStructure(PatternMatchContext)`、
`FactoryBlockPattern`、`BlockPatternTemplate`、`TraceabilityPredicate` 和自定义
predicate helper；未使用 `StructureDefinition` / `FormedStructureView` 作为主入口。

## 1. 核心结论

Structure System V3 的 canonical path 是：

```text
StructureDefinition
  -> MultiPiecePattern / PieceTemplate / CompiledStructureElement
  -> StructureRuntime operation
  -> typed operation result
  -> MultiblockStructureCommitter
  -> StructureLifecycleState
  -> optional deprecated projection
```

旧 API 只允许出现在三个位置：

- 外部 addon compatibility 入口，例如 `GTQTCore` 风格的 controller。
- deprecated facade / detached projection，用于旧 getter、旧工具或 migration 文档。
- compatibility adapter 和兼容测试，用来证明旧入口会被转换到 V3。

除这三个位置以外，GregTech 内部必须直接使用新 API。旧 API 不再作为内部实现方式保留。
其中 `PatternMatchContext` 的边界更严格：GregTech 内部实现不得把它作为参数、返回值、字段、
collector 或 operation state 使用；只能由 legacy callback adapter 为外部 override 临时构造。

不允许出现反向关系：

```text
BlockPattern / BlockPatternTemplate / TraceabilityPredicate[][][] / MultiblockState
  -> decides canonical runtime lifecycle
```

## 2. 术语边界

**新 API** 指 V3 canonical API：

- 声明和编译：`StructureDefinition`、`IStructurePiece`、`StructureCompiler`、
  `MultiPiecePattern`、`PieceTemplate`、`CompiledStructureElement`。
- element 和依赖：`IStructureElement`、`StructureEvaluationContext`、
  `StructureContribution`、`StructureDependency`。
- runtime 和 lifecycle：`StructureRuntime`、`StructureLifecycleState`、
  `PieceRuntimeState`、`PieceRuntimes`、`CommittedStructureGraph`。
- operation result：`StructureCheckResult`、`StructureSnapshotResult`、
  `StructureBuildResult`、`StructureHintResult`、`StructurePreviewResult`、
  `StructureIterateResult`。
- callback 和 tooling：`FormedStructureView`、typed preview metadata、typed candidates。

**旧 API** 指 compatibility API：

- controller override：`createStructurePattern()`、`createStructureTemplate()`、
  `createMultiPiecePattern()`、`formStructure(PatternMatchContext)`。
- builder 和模板：`FactoryBlockPattern`、`BlockPattern`、`BlockPatternTemplate`。
- predicate 和 context：`TraceabilityPredicate`、`PatternMatchContext`、
  `PieceTemplateLegacyView`。其中 `PatternMatchContext` 只能服务外部 legacy callback。
- projection：`MultiblockState`、legacy predicate view、deprecated template getter。
- controller helper 中返回 `TraceabilityPredicate` 的旧 helper，例如 `states()`、
  `blocks()`、`frames()`、`abilities()`、`autoAbilities()`。

**内部代码** 指 GregTech 仓库内 runtime、controller、scheduler、committer、tooling、
preview、JEI、registry 和 tests 中的非 compatibility implementation。

**外部 addon** 指 GregTech 仓库外部的依赖方。本文以 `GTQTCore` 作为必须保住的旧 API
使用样本。

## 3. 当前 V3 主体

### 3.1 声明和编译

每个 controller 初始化后都必须得到 `StructureDefinition`。当前解析顺序在
`MultiblockControllerBase.resolveStructureDefinition()` 中：

1. `createStructureDefinition()`
2. `createMultiPiecePattern()` adapter
3. `createStructureTemplate()` adapter
4. deprecated `createStructurePattern()` adapter

新 controller 必须优先覆盖 `createStructureDefinition()`。旧 override 的返回值只作为导入源，
不得成为 runtime owner。

编译结果是共享不可变对象：

- `StructureDefinition` 描述 piece、repeat、condition、offset、ability limit 和 external dependency。
- `StructureCompiler` 生成 `MultiPiecePattern`。
- `PieceTemplateCompiler` 生成 `PieceTemplate` 和 `CompiledStructureElement`。
- `PieceTemplateLegacyView` 只能按需导出 legacy predicate projection。

### 3.2 Runtime 和 commit

每个 controller 持有自己的 `StructureRuntime`。canonical formed state 是
`StructureLifecycleState`。controller 上的 formed flag、part list、ability map、旧 template
和旧 state 都是 projection，不是事实源。

形成状态只能由 server-thread `MultiblockStructureCommitter` 发布：

```text
controller.checkStructurePattern()
  -> MultiblockStructureOperations.checkStructurePattern()
  -> StructureCommitToken.captureForCheck(controller)
  -> StructureRuntime.check(...)
  -> StructureCheckResult
  -> MultiblockStructureCommitter.applyCheckResult(...)
  -> StructureRuntime.publishLifecycleState(...)
  -> controller projection
  -> optional legacy callback bridge
  -> world index registration
```

失败、stale result 和 async precheck result 都不能直接发布 lifecycle。

### 3.3 Incremental、dirty index 和 async

V3 的增量判断基于 typed dependency：

- `StructureDependencyCompiler` 收集 direct element dependency、condition dependency、
  dynamic anchor、repeat group 和 external dependency。
- `CommittedStructureGraph` 保存 result table、aggregate、position index、runtime publication、
  orientation 和 external dependency snapshot。
- `StructureDirtyState` 保存 pending dirty roots。
- `StructureWorldIndex` 只保存索引、dirty lease、chunk revision、formed positions 和 optional
  position index。

incremental eligibility 必须保守。legacy predicate、opaque callback、未知 side effect、
未声明 dependency 或 external dependency snapshot failure 必须 fallback，并保留 diagnostics。

async worker 只处理 detached immutable data。它不能访问 live `World`、controller、tile entity，
不能执行 live matcher，不能 fold aggregate，也不能发布 lifecycle。

### 3.4 Tooling 和 preview

tooling 主路径必须消费 typed result：

- preview 使用 `StructurePreviewResult` / typed candidates / typed metadata。
- hint/build/iterate 使用对应 typed result。
- JEI、preview renderer、registry 和 UI diagnostics 不应为了主路径显示主动执行 legacy predicate
  traversal。

legacy predicate map 只能作为 deprecated getter、外部 addon fallback 或 detached projection。

## 4. GTQTCore 兼容契约

`GTQTCore` 当前代表必须保留的外部旧 API 使用方式：

- 大量 controller 覆盖 `createStructureTemplate()`。
- 少量 controller 覆盖 `createStructurePattern()`。
- 大量 controller 覆盖 `formStructure(PatternMatchContext)`。
- 自定义 `TraceabilityPredicate` 子类，例如 tier/casing predicate。
- 通过 `FactoryBlockPattern` / `BlockPatternTemplate` 构建结构。
- 通过 controller helper 组合 `TraceabilityPredicate`，例如 ability、block、frame、state helper。

这些旧入口必须继续让外部 addon 编译、加载和形成结构。兼容目标是外部行为可用，不是继续暴露内部旧
traversal。

必须保留给外部 addon 的 surface：

- `FactoryBlockPattern.start()`、`FactoryBlockPattern.buildTemplate()`。
- `BlockPattern`、`BlockPatternTemplate` public/protected 使用。
- `TraceabilityPredicate` 构造、继承、组合、limit、candidate 和 preview 相关方法。
- `PatternMatchContext` callback 中外部已使用的读取能力；该能力只对外部旧 override 保留。
- `createStructurePattern()`、`createStructureTemplate()`、`createMultiPiecePattern()`、
  `formStructure(PatternMatchContext)` override。
- `getPatternTemplate()`、`getMultiblockState()`、legacy predicate projection 等 deprecated getter。
- 旧 controller helper 中返回 `TraceabilityPredicate` 的方法。

不承诺保留的行为：

- 外部旧 projection mutation 影响 canonical lifecycle。
- 外部代码依赖 GregTech 内部继续执行 `TraceabilityPredicate[][][]` traversal。
- 外部代码依赖 `MultiblockState` 作为 live runtime owner。
- 外部代码依赖 GregTech 内部保存、传播或复用 `PatternMatchContext`。
- 外部代码依赖 `PatternMatchContext` 作为跨 operation 或跨 piece 共享状态。
- 未声明 dependency 的 legacy predicate 参与 incremental fast path。

## 5. Adapter-only 规则

旧 API 进入 V3 的边界必须单向、立即、可观测：

```text
external legacy override / builder / predicate
  -> compatibility adapter
  -> StructureDefinition / typed element / typed result
  -> V3 runtime
```

adapter 必须遵守：

1. `createStructurePattern()`、`createStructureTemplate()`、`createMultiPiecePattern()` 的返回值只作为
   import source，controller 初始化时立即转换为 `StructureDefinition`。
2. `TraceabilityPredicate` 只能包装为 typed element，或导出为 detached projection；runtime matcher
   不得直接以 legacy predicate array 作为 canonical traversal model。
3. `PatternMatchContext` 只能在 legacy callback adapter 中为外部
   `formStructure(PatternMatchContext)` override 临时构造。V3 matcher、scheduler、committer、
   preview、async、controller 内部实现不得接收、保存、读取或写入它。
4. `MultiblockState` 只作为 deprecated detached facade/projection；内部 matcher/cache backing 使用
   `PieceRuntimeState`。
5. external old API 缺少 dependency、side effect 或 comparator 信息时，必须 fallback 到 active/full
   path，并保留 diagnostics。
6. adapter 入口保留低频 trace，例如 `legacy-adapter`，遇到不确定行为时优先让使用者输出日志，再决定是否扩展 adapter。

## 6. 内部新 API 规则

GregTech 内部代码必须直接使用新 API：

- 自带 controller 声明结构必须使用 `createStructureDefinition()`。
- 自带 controller 形成回调必须使用 `formStructure(FormedStructureView)` 或 typed helper。
- 自带 controller、base class 和 internal helper 不得新增或继续依赖
  `PatternMatchContext` 参数；旧签名只能作为 addon override bridge。
- 新 element 必须声明 typed contribution、preview、hint/placement 行为和 dependency。
- 新 controller 私有状态如果影响匹配，必须接入 controller mode/channel/config/upgrade snapshot，
  或声明明确的 typed external key。
- runtime、scheduler、committer、async、dirty index 和 operation evaluator 只消费 `StructureRuntime`、
  `StructureLifecycleState`、typed result、typed dependency 和 committed graph。
- preview、JEI、registry、UI diagnostics 主路径只消费 typed preview/metadata。
- 新 internal helper 不得返回 `TraceabilityPredicate` 作为主 API；应返回 typed element、typed candidate、
  `StructureDefinition` builder 片段或 V3 metadata。

内部代码允许引用旧类型的情况只有：

- compatibility adapter implementation。
- deprecated facade/projection implementation。
- compatibility tests。
- migration guide 或文档。

`PatternMatchContext` 是这个例外列表里的特例：除 legacy callback adapter、deprecated public
signature 和 compatibility test 外，GregTech 内部不应引用它。

其它旧引用应清理。特别是，如果某个旧引用不是为了把外部旧入口转换到 V3，也不是为了导出 detached projection，
就不应继续存在。

## 7. 不再保留的内部兼容

以下兼容直接清理，不再为内部代码保留：

- GregTech 已迁移机器上的旧 `createStructurePattern()` / `createStructureTemplate()` 主实现。
- GregTech 已迁移机器上的旧 `formStructure(PatternMatchContext)` 主回调。
- GregTech 内部 controller/base/helper 使用 `PatternMatchContext` 传递形成结果、能力、metadata 或私有状态。
- JEI/preview 内部主路径的 legacy predicate map 补扫。
- `BlockPatternTemplate` supplier registry 作为内部 primary shape source。
- `BlockPattern` / `BlockPatternTemplate` / `MultiblockState` 承载 runtime owner 语义。
- `TraceabilityPredicate[][][]` 决定 scheduler、committer、incremental 或 preview 主路径。
- legacy callback 写入的 `PatternMatchContext` 反向影响 V3 matcher，或被内部后续流程读取。
- 未知 dependency、opaque side effect、未声明 comparator 继续尝试 incremental 的路径。
- 仅为内部调用者保留的旧 traversal convenience method。

外部仍需要的同名方法只能走 adapter 或 projection。

## 8. 迁移和清理顺序

### 8.1 当前结论

截至当前代码，V3 主路径已经收敛到 typed runtime：controller check、scheduler、committer、
async precheck、build/hint/preview/iterate operation service、registry preview metadata 和大部分
JEI/工具入口都不再以 `TraceabilityPredicate[][][]`、`BlockPattern` 或 `MultiblockState`
作为 lifecycle owner。

内部旧 API 还没有清零，但剩余面已经比较明确：

| 范围 | 当前数量 | 状态 |
|------|----------|------|
| GregTech common controller 覆盖 `createStructurePattern()` | 0 | 已清完，由扫描测试锁住。 |
| GregTech common controller 覆盖 `formStructure(PatternMatchContext)` | 0 | 已清完，由扫描测试锁住。 |
| common controller 通过 `copyLegacyCallbackContext()` 读旧 payload | 7 个文件 | 仍需转 typed contribution / metadata。 |
| monitor plugin 通过 `getMultiblockState().checkPatternFastAt(...)` 重扫结构 | 2 个文件 | 仍是内部旧 traversal。 |
| JEI tooltip/candidate fallback 触发 legacy predicate view | 1 个文件 | 仅 typed preview 缺失时 fallback，仍需补齐 typed preview。 |
| 动态尺寸机器内部构造 `BlockPattern` 作为模板桥 | 2 个 controller | 已经从 `StructureDefinition` 进入 V3，但声明侧仍未完全 typed 化。 |
| API compatibility bridge | 若干 API base class | 为外部 addon ABI 保留，不按内部主路径债务计算。 |

因此，“内部旧 API 清理”按主路径算已基本完成；按仓库内引用算，剩余重点是
context payload、client/monitor tooling fallback、JEI fallback 和动态声明桥接。`BlockPattern`、
`BlockPatternTemplate`、`TraceabilityPredicate`、`PatternMatchContext`、`MultiblockState`、
`PieceTemplateLegacyView` 这些类型本身仍会继续存在于 deprecated public surface、adapter、
projection 和 compatibility tests 中，直到外部 API 移除窗口结束。

### 8.2 已锁住的边界

以下内容已经作为完成项看待，后续不应反复迁移：

- `LegacyStructureAdapterBoundaryTest` 覆盖 `GTQTCore` 风格的旧 template、旧 pattern、
  自定义 `TraceabilityPredicate` 子类和旧 `formStructure(PatternMatchContext)` callback。
- `StructureInternalLegacyBoundaryScanTest` 锁住 runtime、scheduler、committer、async、preview、
  JEI、registry、builder/removal/projector tooling 的旧 traversal 禁用清单。
- `StructureInternalLegacyBoundaryScanTest.gregTechControllersUseTypedFormationCallbacksAndDefinitions`
  锁住 common controller 树：不能重新覆盖 `createStructurePattern()` 或
  `formStructure(PatternMatchContext)`。
- deprecated getter/projection 测试证明 mutation 不影响 canonical lifecycle。
- adapter trace 通过 `StructureRuntime.getAdapterTrace()`、`describeShape()` 和 typed result
  diagnostics 可观察。遇到不确定旧入口时，优先加低频日志/trace 让调用方输出数据，再扩展 adapter。

### 8.3 剩余项 1：清掉 common controller 的 legacy context payload

当前 common controller 仍有 7 个文件通过
`FormedStructureView.copyLegacyCallbackContext()` 读取旧 predicate 写入的数据：

- `MetaTileEntityElectricBlastFurnace`
- `MetaTileEntityMultiSmelter`
- `MetaTileEntityMultiAlloyFurnace`
- `MetaTileEntityCrackingUnit`
- `MetaTileEntityPyrolyseOven`
- `MetaTileEntityCleanroom`
- `MetaTileEntityPowerSubstation`

这些已经不是旧 `formStructure(PatternMatchContext)` override，而是 typed callback 内部临时读取
legacy projection。清理顺序：

1. heating coil 系列先做：把 matched `ICasing` / coil stats 从旧 channel context 迁移到 typed
   contribution 或 `FormedStructureView` metadata helper。
2. Cleanroom 再做：`FilterType` 和 `Doors` 由 typed element contribution 发布，形成回调只读 typed
   view。
3. Power Substation 最后做：`PMC_BATTERY_HEADER` / `BatteryMatchWrapper` 改成 typed battery
   contribution，并保留 tier count 的形成结果。
4. common controller 中 `copyLegacyCallbackContext()` 归零后，把扫描测试扩展为禁止
   `gregtech/common/metatileentities` 引用该方法。

验收标准：common controller 不再 import `PatternMatchContext`；旧 callback projection 只由 API
base class 的外部 addon bridge 使用。

### 8.4 剩余项 2：清掉 tooling / JEI 的旧 traversal fallback

当前仍有 2 个 monitor plugin 直接从 deprecated state 重扫结构：

- `FakeGuiPluginBehavior`：通过 `getMultiblockState().checkPatternFastAt(...)` 取
  `MultiblockParts`。
- `AdvancedMonitorPluginBehavior`：通过同一路径重扫，然后读取 `state.cache`。

迁移目标是让它们读取 committed typed publication：

- part 列表从 `StructureLifecycleState` / `FormedStructureView` / typed ability publication 获取。
- valid positions 从 `CommittedStructureGraph` 的 position index 或 formed position set 获取。
- 不再调用 `getMultiblockState()`、`checkPatternFastAt(...)` 或读取 `state.cache`。

JEI 目前主路径已经使用 `StructureElementPreviewEntry`，但
`MultiblockInfoRecipeWrapper` 仍在 typed entry 缺失时调用
`getLegacyPredicateFallback(...)`，并用临时 `PatternMatchContext` 匹配 legacy tooltip。这个 fallback
可以保留到 typed preview coverage 补齐；清理时先给缺失 entry 加 diagnostics/log，确认真实缺口，
再删除 legacy tooltip 匹配。

### 8.5 剩余项 3：清掉动态声明侧的 `BlockPattern` 桥

`CharcoalPileIgniter` 和 `Cleanroom` 已经通过 `createStructureDefinition()` 进入 V3，但内部仍因
动态尺寸、channel preview、auto-build/hint 复用而构造 `FactoryBlockPattern` / `BlockPattern`：

- `CharcoalPileIgniter` 动态生成木堆尺寸，并把 `BlockPattern.getTemplate()` 包成
  `StructureDefinition`。
- `Cleanroom` 用 `FactoryBlockPattern` 生成动态尺寸结构，preview/build/hint 仍临时构造
  `BlockPattern`。

这类不再是 runtime owner 问题，但仍是内部旧声明 facade。清理顺序：

1. 把动态尺寸 builder 输出改为 `PieceTemplate` / `StructureDefinition`，避免先 build
   `BlockPattern` 再取 template。
2. 把固定 repetition 读取从 `BlockPattern.getAisleRepetitions()` 改为 typed template metadata。
3. 把 `MultiblockState.resolveRepetitionValue(...)` 移到非 deprecated helper，例如 channel/value
   resolver。
4. 动态 preview/build/hint 继续走 typed operation request，不回退到旧 state。

另外，`FluidDrill`、`FusionReactor`、`LargeMiner`、`LargeTurbine`、`ForgeOfGods` 和
`MTEBaseModule` 仍有 `BlockPatternTemplate` supplier 或 `FactoryBlockPattern` 声明 helper。它们目前
主要是声明 facade，不是 lifecycle traversal；优先级低于 context payload 和 tooling fallback，但最终
也应迁移到直接返回 `StructureDefinition` / typed template builder。

### 8.6 Operation 服务状态

`StructureOperationEvaluator` 当前只作为 public compatibility facade，主体已经拆成：

- `StructureCheckOperationService`
- `StructureSnapshotOperationService`
- `StructureBuildOperationService`
- `StructureHintOperationService`
- `StructurePreviewOperationService`
- `StructureIterateOperationService`

拆分后的不变量：

- request kind 在入口校验。
- result diagnostics 不丢失。
- commit path 只消费 `StructureCheckResult`。
- build/hint/preview/iterate 不发布 lifecycle。
- 旧 API 不能重新进入 operation 主路径。

`StructureOperationRuntime` / `StructureOperationContext` 负责统一解析 compiled pattern、piece
runtimes、synthetic single-piece runtime 和 operation diagnostics，避免每个 service 重新判断
legacy shape。

### 8.7 建议清理顺序

后续按这个顺序收尾：

1. 先清 7 个 common controller 的 `copyLegacyCallbackContext()`。
2. 再清 2 个 monitor plugin 的 `getMultiblockState().checkPatternFastAt(...)`。
3. 补齐 JEI typed preview entry，删除 legacy tooltip/candidate fallback。
4. 清 `CharcoalPileIgniter` / `Cleanroom` 动态声明侧的 `BlockPattern` 桥。
5. 清 template supplier registry 和 `FactoryBlockPattern` internal helper，转成 typed
   `StructureDefinition` builder。
6. 最后处理 public deprecated API 移除：`createStructurePattern()`、
   `formStructure(PatternMatchContext)`、`getMultiblockState()`、`getPatternTemplate()`、
   `getBlockMatches()` 等外部 surface 在 removal target 前只能继续 adapter/projection。

每一步都先扩大扫描测试，再做代码迁移；如果某个旧 predicate 的 side effect 或 payload 语义不确定，
先加 trace/log 让实际机器输出数据，再决定 typed contribution 的形状。

## 9. 不变量

实现和清理时必须保持：

1. `StructureDefinition`、`MultiPiecePattern`、`PieceTemplate`、`CompiledStructureElement` 是共享不可变声明/编译结果。
2. `StructureRuntime`、`StructureLifecycleState`、`PieceRuntimes`、`StructureDirtyState` 是 controller 私有运行时状态。
3. 每次 operation 的事务状态只存在于 operation 生命周期内。
4. 形成状态只能由 server-thread `MultiblockStructureCommitter` 发布。
5. stale result 不发布、不 invalidate、不覆盖 last failure。
6. failure 分支必须回滚 part、ability、channel、metadata、legacy projection 和 contribution builder 状态。
7. incremental eligibility 保守判断；未知、opaque、legacy side effect 都 fallback，并保留 diagnostics。
8. external dependency snapshot/comparator failure 保守转为 full/active fallback，并保留 diagnostics。
9. async worker 只处理 detached immutable data。
10. cache probe 只能在有 baseline result 可复用时跳过 element traversal；不能用 cached block match 构造空 contribution。
11. 不确定行为优先加低频、有开关的 trace，再根据日志改逻辑。

## 10. 验收矩阵

完整 V3 清理完成前，至少需要覆盖：

| 范围 | 必要验证 |
|---|---|
| GTQTCore compatibility | 旧 template、旧 pattern、旧 predicate 子类、旧 callback、旧 helper 组合可编译、加载、形成结构。 |
| legacy adapter | 旧入口立即转换为 `StructureDefinition` / typed element；旧 projection mutation 不影响 lifecycle。 |
| internal ban | runtime、scheduler、committer、async、preview、JEI、registry 主路径无 legacy traversal。 |
| declaration | fixed piece、conditional piece、repeat、multi-axis repeat、dynamic offset、legacy import parity。 |
| formation | full check、active graph fallback、failed check rollback、commit token stale rejection。 |
| incremental | dirty root、dependency closure、external dependency、unknown dependency fallback、diagnostics retention。 |
| async | unformed snapshot precheck、formed dirty state-only precheck、stale token、chunk revision mismatch、live confirm。 |
| build/hint | creative build、survival accounting、partial/resume、hint side effect isolation。 |
| preview/iterate | single-template parity、multi-piece parity、empty cell metadata、typed candidate map。 |
| performance | clean piece 不重复读 world，dirty piece cache probe hit/miss，dirty piece read 限定，large-save profiling，shadow validation semantic parity。 |

当前已有重点测试：

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
- `LegacyStructureAdapterBoundaryTest`
- `StructureInternalLegacyBoundaryScanTest`

仍需补强：

- controller 私有状态中仍通过 `FormedStructureView.copyLegacyCallbackContext()` 读取的 legacy
  payload typed contribution 化。
- 大规模存档级 world profiling。

## 11. 代码导航

核心 controller 和 commit：

- `src/main/java/gregtech/api/metatileentity/multiblock/MultiblockControllerBase.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/MultiblockStructureOperations.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/MultiblockStructureAssembler.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/MultiblockStructureCommitter.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/MultiblockStructureRegistration.java`

V3 runtime 和 result：

- `src/main/java/gregtech/api/pattern/StructureRuntime.java`
- `src/main/java/gregtech/api/pattern/StructureLifecycleState.java`
- `src/main/java/gregtech/api/pattern/StructureOperationRequest.java`
- `src/main/java/gregtech/api/pattern/StructureOperationEvaluator.java`
- `src/main/java/gregtech/api/pattern/StructureOperationContext.java`
- `src/main/java/gregtech/api/pattern/StructureOperationRuntime.java`
- `src/main/java/gregtech/api/pattern/StructureOperationDiagnostics.java`
- `src/main/java/gregtech/api/pattern/StructureCheckOperationService.java`
- `src/main/java/gregtech/api/pattern/StructureSnapshotOperationService.java`
- `src/main/java/gregtech/api/pattern/StructureBuildOperationService.java`
- `src/main/java/gregtech/api/pattern/StructureHintOperationService.java`
- `src/main/java/gregtech/api/pattern/StructurePreviewOperationService.java`
- `src/main/java/gregtech/api/pattern/StructureIterateOperationService.java`
- `src/main/java/gregtech/api/pattern/StructureCheckResult.java`
- `src/main/java/gregtech/api/pattern/CommittedStructureGraph.java`
- `src/main/java/gregtech/api/pattern/StructureShadowValidator.java`

声明、编译和 element：

- `src/main/java/gregtech/api/pattern/element/StructureDefinition.java`
- `src/main/java/gregtech/api/pattern/element/StructureCompiler.java`
- `src/main/java/gregtech/api/pattern/MultiPiecePattern.java`
- `src/main/java/gregtech/api/pattern/StructurePiece.java`
- `src/main/java/gregtech/api/pattern/RepeatGroupPiece.java`
- `src/main/java/gregtech/api/pattern/DynamicOffsetPiece.java`
- `src/main/java/gregtech/api/pattern/PieceTemplate.java`
- `src/main/java/gregtech/api/pattern/PieceTemplateCompiler.java`
- `src/main/java/gregtech/api/pattern/element/CompiledStructureElement.java`
- `src/main/java/gregtech/api/pattern/element/IStructureElement.java`

增量和 dependency：

- `src/main/java/gregtech/api/pattern/StructureDependency.java`
- `src/main/java/gregtech/api/pattern/StructureDependencyCompiler.java`
- `src/main/java/gregtech/api/pattern/StructureEligibilityPlan.java`
- `src/main/java/gregtech/api/pattern/StructureExternalDependencies.java`
- `src/main/java/gregtech/api/pattern/StructureExternalDependencySnapshot.java`
- `src/main/java/gregtech/api/pattern/StructureDirtyState.java`
- `src/main/java/gregtech/api/pattern/StructurePositionIndex.java`

调度和 async：

- `src/main/java/gregtech/api/metatileentity/multiblock/MultiblockStructureCheckScheduler.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/StructureSchedulerPolicy.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/MultiblockWorldData.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/StructureWorldIndex.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/StructureCommitToken.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/AsyncStructureChecker.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/BlockStateSnapshot.java`

legacy/compatibility：

- `src/main/java/gregtech/api/pattern/MultiblockState.java`
- `src/main/java/gregtech/api/pattern/BlockPattern.java`
- `src/main/java/gregtech/api/pattern/BlockPatternTemplate.java`
- `src/main/java/gregtech/api/pattern/FactoryBlockPattern.java`
- `src/main/java/gregtech/api/pattern/TraceabilityPredicate.java`
- `src/main/java/gregtech/api/pattern/PatternMatchContext.java`
- `src/main/java/gregtech/api/pattern/PieceTemplateLegacyView.java`

tooling/preview：

- `src/main/java/gregtech/api/metatileentity/multiblock/MultiblockStructurePreviews.java`
- `src/main/java/gregtech/api/metatileentity/registry/MBPattern.java`
- `src/main/java/gregtech/integration/jei/multiblock/MultiblockInfoRecipeWrapper.java`
- `src/main/java/gregtech/api/metatileentity/multiblock/ui/MultiblockUIBuilder.java`
