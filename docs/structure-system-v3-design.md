# Structure System V3 Design

**Implementation snapshot:** 2026-06-16

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

### 8.1 当前口径

当前收口规则已经改为：

```text
旧 API 只保留给仓库外 addon 兼容和 deprecated public projection。
GregTech 仓库内的 common、gtqt、tooling、JEI、registry、operation 主路径必须清理。
```

`src/main/java/gtqt` 不是兼容白名单。它属于当前仓库内部代码，必须和 common 一样迁到
`StructureDefinition` / typed element / `FormedStructureView`。需要保留兼容的是上级目录
`..\GTQTCore` 仍在调用的 GregTech public API。

`DeclarativePatternBuilder.start()` 是 V3 typed builder 的链式声明入口，不是为了外部 add-on
兼容保留，也不属于旧 API 残留。静态单结构推荐保持模板池内联声明：

```java
private static final StructureDefinition STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
        "gregtech:coke_oven", () -> DeclarativePatternBuilder.start()
                .aisle("XXX", "XXX", "XXX")
                .self('Y', MetaTileEntityCokeOven.class)
                .air('#')
                .casing('X', casingState)
                .buildStructureDefinition());
```

只有带 type 参数、并且还要给旧 `buildTemplate(type)` 兼容出口复用同一声明的机器，才保留
`buildStructureDefinition(type)` helper。这个 helper 不是新的默认格式，只是避免变体注册和旧模板导出
写两份结构。

需要禁止的是旧模板构建链路：

- `FactoryBlockPattern.start()`
- builder 链尾 `.buildTemplate()`
- GregTech 内部先生成 `BlockPatternTemplate` 再导入 `StructureDefinition`

保留的 public `buildTemplate(...)` 兼容方法也必须内部走同一个 typed
`StructureDefinition`，并从 `getPrimaryTemplate()` 导出旧模板；不能另起一条旧 builder 链。

### 8.2 当前残留

按当前代码扫描，主路径旧 API 残留如下：

| 范围 | 当前数量 | 状态 |
|------|----------|------|
| `src/main/java/gtqt` 旧结构 API 引用 | 0 | 已清理；不再作为兼容例外。 |
| common / gtqt controller 覆盖 `createStructurePattern()` | 0 | 扫描测试锁住。 |
| common / gtqt controller 覆盖 `formStructure(PatternMatchContext)` | 0 | 扫描测试锁住。 |
| common / gtqt 内部 `FactoryBlockPattern.start()` / `.buildTemplate()` | 0 | 扫描测试锁住；不误伤 `DeclarativePatternBuilder.start()`。 |
| common / gtqt `TraceabilityPredicate` 引用 | 0 | 自带 controller/helper 已迁到 typed element。 |
| common / gtqt `MultiblockState` 引用 | 0 | runtime/tooling 主路径已清理。 |
| common `BlockPatternTemplate` 引用 | 4 个文件 | 仅 public add-on 兼容 API，内部导出走池化 typed definition 的 `getPrimaryTemplate()`。 |
| common / gtqt `PatternMatchContext` 引用 | 0 | custom element 旧签名已清理；扫描测试锁住。 |
| `IStructureElement` 旧 world/context 方法签名 | 0 | 主接口只保留 `StructureEvaluationContext` typed 合约。 |

剩余严格残留只剩 public add-on compatibility API：

`MetaTileEntityFluidDrill`、`MetaTileEntityLargeMiner`、`MetaTileEntityLargeTurbine`、
`MetaTileEntityFusionReactor` 的 `register*Type(... Supplier<BlockPatternTemplate>)` 和
`buildTemplate(...)`。

### 8.3 保留边界

`..\GTQTCore` 当前真实依赖的 GregTech common public compat surface 包括：

- `MetaTileEntityLargeTurbine.registerTurbineType(...)`
- `MetaTileEntityLargeTurbine.buildTemplate(...)`
- `MetaTileEntityFluidDrill.registerFluidDrillType(...)`
- `MetaTileEntityFluidDrill.buildTemplate(...)`
- `MetaTileEntityFusionReactor.registerFusionType(...)`
- `MetaTileEntityFusionReactor.buildTemplate(...)`

同类 add-on 扩展 API `MetaTileEntityLargeMiner.registerLargeMinerType(...)` /
`MetaTileEntityLargeMiner.buildTemplate(...)` 也按同一兼容边界暂留。

这些方法可以继续暴露 `BlockPatternTemplate`，但只能作为 add-on ABI：

- 外部传入 `BlockPatternTemplate` 时，立即通过 `StructureDefinition.fromTemplate(...)` 导入 V3。
- GregTech 自带类型导出旧模板时，只能从同一个 `TemplatePool` / typed `StructureDefinition`
  调 `getPrimaryTemplate()`。
- 不能在 common / gtqt 内部重新使用 `FactoryBlockPattern.start()` 或 `.buildTemplate()` 构建结构。

### 8.4 已完成项

以下内容已经按完成项处理：

- common 和 gtqt controller 声明侧都使用 `createStructureDefinition()`。
- common 和 gtqt 形成回调都使用 `FormedStructureView` 或 typed helper。
- common 声明 helper 的 `FactoryBlockPattern` / `TraceabilityPredicate` 已迁到
  `DeclarativePatternBuilder` 和 `Elements` typed element。
- `MetaTileEntityForgeOfGods`、`MTEBaseModule` 不再通过 `BlockPatternTemplate` 桥接内部结构。
- monitor tooling、builder/removal/projector、JEI tooltip/candidate preview、registry preview
  metadata 不再把 legacy predicate/state 当主路径。
- dynamic sized controller (`Cleanroom` / `CharcoalPileIgniter`) 的 preview/build/hint 已走 typed
  template path，不再通过 `BlockPattern`、`FactoryBlockPattern` 或 `MultiblockState`。
- custom element (`CharcoalPileIgniter` / `Cleanroom` / `ResearchStation` / `PowerSubstation` /
  `CentralMonitor`) 已迁到 `ITypedStructureElement`，不再实现 `PatternMatchContext` 旧签名。
- `IStructureElement` 不再暴露 `check(World, BlockPos, PatternMatchContext)`、
  `placeBlock(World, BlockPos, PatternMatchContext, ...)`、`spawnHint(World, BlockPos)` 等旧 cell
  element 方法；legacy predicate 兼容集中在 `TraceabilityPredicate` / `LegacyElement` /
  compatibility view。
- `DeclarativePatternBuilder` 增加 typed 快捷方法：
  `.self(...)`、`.block(...)`、`.blocks(...)`、`.air(...)`、`.any(...)`、`.frames(...)`、
  `.hatches(...)`、`.casing(char, IBlockState)`，避免迁移后调用点比旧 API 更复杂。

### 8.5 测试锁定

`StructureInternalLegacyBoundaryScanTest` 当前需要锁住这些边界：

- runtime、scheduler、committer、async、operation、preview、JEI、registry、tooling 不得重新引入
  legacy traversal owner。
- common 和 gtqt controller 不得重新覆盖 `createStructurePattern()`、
  `formStructure(PatternMatchContext)` 或调用 `copyLegacyCallbackContext()`。
- common 和 gtqt 内部不得重新引用 `PatternMatchContext`；它只属于 API compatibility adapter。
- common 和 gtqt controller 不得重新出现 `FactoryBlockPattern.start()` 或 `.buildTemplate()`。
- `DeclarativePatternBuilder.start()` 明确允许，它是 typed declaration 入口。
- `gregtech/api/pattern/element` 不得重新暴露旧 world/`PatternMatchContext` element 方法签名。
- `Cleanroom` / `CharcoalPileIgniter` 的 dynamic tooling 必须继续走 typed template path。

### 8.6 后续清理顺序

后续只剩一条线：

升级上级目录 `..\GTQTCore`，把它对 `register*Type(... Supplier<BlockPatternTemplate>)`
和 `buildTemplate(...)` 的调用迁到 typed `StructureDefinition` 或 typed builder fragment。
外部调用清零后，再移除这 4 个 common public compat 残留。

完成后，common / gtqt 可扩大扫描为：除 `gregtech/api` 的 deprecated public adapter /
projection、compatibility tests 和 migration docs 外，禁止 `BlockPatternTemplate`、`BlockPattern`、
`FactoryBlockPattern`、`TraceabilityPredicate`、`MultiblockState`。`PatternMatchContext` 已经按这个口径
在 common / gtqt 清零并锁住。

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

- 声明 facade/helper 和 common legacy predicate/context hook 清理。
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
