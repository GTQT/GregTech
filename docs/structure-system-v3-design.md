# Structure System V3 设计文档

**实现快照：** 2026-07-01

**覆盖范围：** `gregtech.api.pattern`、`gregtech.api.pattern.element`、
`gregtech.api.metatileentity.multiblock`、JEI/预览/投影器/构建工具，以及
GregTech 内置多方块控制器。

## 1. 文档定位

Structure System V3 不是迁移备忘录，而是当前仓库唯一的多方块结构声明、
校验、提交和预览架构。本文档只记录需要长期遵守的设计契约：

- 控制器如何声明结构。
- 声明如何编译成不可变模板。
- 每个控制器实例如何持有运行时状态。
- 结构校验结果如何进入唯一提交边界。
- 增量校验、异步预检、JEI/预览和工具链必须遵守哪些限制。

本文档不维护旧结构系统兼容层，也不保留已经完成的迁移清单。任何新代码如果
需要声明或消费多方块结构，都应直接使用 V3 类型。

## 2. 设计目标

V3 的核心目标是把“结构是什么”和“某个控制器实例当前形成了什么”彻底分开。

1. 结构声明是不可变数据。`StructureDefinition`、`MultiPiecePattern`、
   `PieceTemplate` 和 `CompiledStructureElement` 可以被同类型控制器共享。
2. 运行状态属于控制器实例。`StructureRuntime`、`PieceRuntimeState`、
   `PieceRuntimes`、`StructureLifecycleState` 和 `CommittedStructureGraph`
   不允许跨控制器共享。
3. 所有形成状态只通过 `MultiblockStructureCommitter` 发布。校验、预览、
   构建和提示可以产生结果，但不能绕过提交器修改形成生命周期。
4. 增量校验基于显式依赖。不能证明安全的元素、条件或外部状态必须回退到完整
   active graph 校验。
5. 工具链消费声明和预览元数据。JEI、投影器、ghost 渲染和 build-all 不应为了
   展示数据而执行运行期校验副作用。
6. 不确定时先加诊断日志。优先用 `StructureTrace` 或低频 debug 日志收集
   控制器、piece、坐标、channel、fallback reason 等信息，再决定是否收紧契约。

## 3. 总体架构

标准形成路径如下：

```text
MultiblockControllerBase.createStructureDefinition()
  -> StructureDefinition
  -> StructureCompiler
  -> MultiPiecePattern / PieceTemplate / CompiledStructureElement
  -> StructureRuntime
  -> StructureOperationEvaluator
  -> StructureCheckResult
  -> MultiblockStructureCommitter
  -> StructureLifecycleState / CommittedStructureGraph / world index
```

这条链路有两个不可跨越的边界：

- `StructureDefinition` 之前是控制器声明结构。
- `MultiblockStructureCommitter` 之后才允许发布形成状态、能力、part、channel
  和 committed graph。

中间的 operation service 只负责计算结果。即使结果来自完整校验、active graph、
增量校验或异步预检，最终都必须回到提交器统一处理。

## 4. 分层职责

| 层级 | 主要类型 | 职责 |
| --- | --- | --- |
| 声明层 | `StructureDefinition`、`IStructurePiece` | 描述 piece、方向、repeat、条件、能力限制和 runtime detector。 |
| 编译层 | `StructureCompiler`、`MultiPiecePattern`、`PieceTemplate`、`CompiledStructureElement` | 将声明展开成不可变模板和可执行 cell 序列。 |
| 元素层 | `IStructureElement`、`ITypedStructureElement`、`StructureEvaluationContext` | 描述单个结构格子的匹配、候选方块、构建、提示、贡献和依赖。 |
| 运行层 | `StructureRuntime`、`StructureOperationEvaluator`、各 operation service | 执行 check、snapshot、build、hint、preview、iterate，并维护实例级 dirty/lifecycle 数据。 |
| 提交层 | `MultiblockStructureCommitter`、`StructureCommitToken`、`MultiblockStructureAssembler` | 在服务端线程验证结果仍然新鲜，并发布形成状态。 |
| 生命周期层 | `StructureLifecycleState`、`FormedStructureView`、`CommittedStructureGraph` | 提供已形成结构的只读快照、能力、part、metadata、channel 和增量 baseline。 |
| 工具层 | `StructureElementPreviewEntry`、`MultiPiecePreviewAssembler`、JEI/renderer/tooling | 基于声明和预览元数据生成展示、提示和自动构建数据。 |

## 5. 控制器声明契约

每个 GregTech 多方块控制器必须实现：

```java
@NotNull
protected abstract StructureDefinition<?> createStructureDefinition();
```

推荐形式是：

```java
return StructureDefinition.getOrBuild("gregtech:machine_id", () ->
        StructureDefinition.builder(RIGHT, UP, BACK)
                .piece("main", ...)
                .where('X', ...)
                .build());
```

控制器侧规则：

- `createStructureDefinition()` 必须幂等。优先使用
  `StructureDefinition.getOrBuild(...)`，不要每次 tick 创建全新的可变声明图。
- helper API 应返回 V3 的 declaration、piece、condition 或 typed element，
  不应创建旁路结构系统。
- 控制器读取形成结果时使用 `FormedStructureView`，不要从内部 matcher 状态、
  临时 collector 或未提交的 `StructureCheckResult` 中提取长期状态。
- 控制器模式、配置、upgrade、channel 等会影响结构匹配的外部状态，必须通过
  `StructureExternalDependencies` 或自定义 `StructureExternalDependencyKey`
  表达。

`MultiblockControllerBase.reinitializeStructurePattern()` 是控制器实例的运行时重建入口。
它解析 `StructureDefinition`，取得编译后的 `MultiPiecePattern`，创建
`PieceRuntimes` 和 `StructureRuntime`，并递增 runtime generation。直接修改这些
字段会破坏异步结果的新鲜度判断和形成状态投影。

## 6. 不可变声明与实例状态

以下对象属于声明或编译产物，必须视为不可变：

- `StructureDefinition`
- `MultiPiecePattern`
- `StructurePiece`
- `PieceTemplate`
- `CompiledStructureElement`
- `StructureSizeDescriptor`
- `StructureEligibilityPlan`

以下对象属于控制器实例或一次操作，不能被其它控制器共享：

- `StructureRuntime`
- `PieceRuntimeState`
- `PieceRuntimes`
- `StructureOperationState`
- `StructureLifecycleState`
- `CommittedStructureGraph`
- `StructureCheckResult` 及其它 operation result

这个区分是 V3 的基础。如果某个字段既想缓存声明数据又想保存实例状态，通常说明
抽象边界错了：声明数据应下沉到 `StructureDefinition` 或 compiled pattern，实例
数据应放进 runtime、piece runtime、lifecycle 或 committed graph。

## 7. 校验与提交流程

服务端形成校验由 `MultiblockStructureOperations` 发起：

```text
checkStructurePattern(controller)
  -> StructureCommitToken.captureForCheck(controller)
  -> StructureRuntime.check(...)
  -> StructureCheckResult
  -> MultiblockStructureCommitter.applyCheckResult(...)
```

提交器负责所有副作用：

- 丢弃 stale token 对应的旧结果。
- 记录失败 trace 和 missing ability。
- 在失败且已形成时触发 `invalidateStructure()`。
- 调用 `MultiblockStructureAssembler.prepare(...)` 验证 part/ability 变更。
- 发布 `StructureLifecycleState`、`FormedStructureMetadata`、channel values 和
  `CommittedStructureGraph`。
- 调用 `formStructure(FormedStructureView formed)`。
- 刷新 world index 和 multi-piece registration。

禁止在 operation service、element、preview、hint 或 async worker 中直接发布形成
生命周期。它们可以产生 typed result，但不能修改 controller 的 formed payload。

## 8. 形成状态读取

`StructureLifecycleState` 是 runtime 持有的 canonical formed snapshot。控制器仍然
会为网络同步和 addon 兼容镜像部分字段，但服务端形成状态应以 runtime lifecycle
为准。

新代码读取形成数据时优先使用 `FormedStructureView`：

- `getParts()`
- `getAbilityCount(...)`
- `getChannelValue(...)`
- `getPieceRepeat(...)`
- `getPieceCenter(...)`
- `getAggregate(...)`
- `isFlipped()`

`formStructure(FormedStructureView formed)` 只在提交器确认形成 payload 变化后运行。
如果只是一次仍然有效的校验且 committed payload 没有变化，不应重复执行昂贵的
形成副作用。

## 9. 元素契约

`IStructureElement` 是单个结构格子的 canonical 匹配接口。直接元素必须明确回答三类
问题：

1. 它能执行哪些 operation capability。
2. 它是否能参与增量/贡献感知校验。
3. 哪些 piece 或外部状态会影响它的匹配或贡献结果。

新元素优先实现 `ITypedStructureElement`。该接口默认：

- `getIncrementalSupport()` 返回 `TYPED_CONTRIBUTION`。
- `getDependencies()` 返回空集合。
- `hasExplicitIncrementalContract()` 返回 `true`。

如果元素读取了当前 piece 之外的信息，必须覆盖依赖声明。例如：

- 读取其它 piece 的 center、repeat 或 metadata：声明 piece dependency。
- 读取 controller mode、working enabled 或 controllable 状态：声明
  `StructureExternalDependencies.controllerMode()`。
- 读取 channel value：声明 `StructureExternalDependencies.channelValues()`。
- 读取配置或 upgrade：声明对应 external dependency。

如果元素有隐藏副作用、依赖无法枚举、或不能安全参与增量校验，必须返回 opaque
支持，让系统回退完整路径。不要通过空依赖假装安全。

## 10. 贡献与聚合

结构匹配过程中的 typed contribution 必须是事务性的：

- 成功匹配后才能提交到 operation state。
- 分支失败、choice 回退、repeat 回滚时必须撤销临时贡献。
- ability、part、channel、variant active block、aggregate value 都应随同一次
  operation state 一起发布。

控制器需要读取聚合结果时，通过 `FormedStructureView.getAggregate(...)` 或
`getChannelAggregate(...)` 访问。不要把 aggregate 暴露为元素内部的可变静态状态。

## 11. 增量校验设计

增量校验依赖 `StructureDependencyCompiler` 生成的 `StructureEligibilityPlan`。
编译器会检查：

- dynamic anchor 对目标 piece 的依赖。
- condition 声明的 dependencies。
- 每个 direct element 的 explicit incremental contract。
- element dependencies。
- external dependency key 和对应 roots。
- piece dependency graph 是否有环或非法顺序。

只有 eligibility plan 完全可证明时，`StructureRuntime.checkIncremental(...)` 才会使用
dirty roots 和 committed graph 做增量路径。否则系统回退 active graph，并把 fallback
reason 带入 trace context。

常见回退原因包括：

- definition 不可增量。
- runtime detector 需要 live full-box validation。
- 没有 committed graph baseline。
- orientation 变化。
- opaque element 或 opaque condition。
- unknown external dependency。
- dependency cycle 或非法 dependency order。

如果新增元素导致增量回退，不要先强行改依赖。先开启结构诊断日志，确认它读取的
状态、失败 piece、实际 dirty roots 和 fallback reason，再补充最小依赖契约。

## 12. 异步边界

异步结构检查只允许消费不可变或复制后的数据：

- `StructureDefinition` 和 compiled pattern。
- `StructureDirtyPrecheck`。
- block-state snapshot。
- generation/token。
- 已复制的 external dependency snapshot。

异步线程禁止访问：

- live `World`。
- controller mutable fields。
- tile entity。
- ability map。
- inventory 或 item source。
- mutable `PieceRuntimes` 发布目标。

异步 worker 只能产生预检结果。服务端线程仍然负责 live confirm、freshness 检查、
提交、part 注册、ability 发布、world index 刷新和 lifecycle publication。

## 13. 预览、JEI 与工具链

预览工具应消费：

- `StructureDefinition`
- `MultiPiecePattern`
- `StructureElementPreview`
- `StructureElementPreviewEntry`
- typed candidate metadata
- channel range 和默认 channel values

工具链规则：

- JEI、投影器、ghost renderer 和 build-all 不应执行运行期校验逻辑来“推断”展示
  方块。
- runtime-only piece 可以参与真实结构校验，但默认不出现在普通预览、hint、JEI 和
  build-all 表面，除非工具明确进入诊断模式。
- 缺少 typed preview metadata 时应低频记录 controller、piece、位置、channel 和
  element 类型，方便补齐声明。
- preview/build candidate 应来自 element 的 preview/candidate API，而不是读取上一次
  形成状态的副作用。

## 14. 诊断与日志

结构系统的问题通常来自隐藏依赖或跨线程边界不清。诊断优先级如下：

1. 对不确定行为加 `StructureTrace` 或低频 debug 日志。
2. 输出 controller id、controller pos、formed 状态、orientation、piece、cell、
   channel、operation、fallback reason 和 missing ability。
3. 用日志确认真实输入后，再决定是否补 dependency、改 preview metadata 或收紧
   async 边界。

`debugStructureTrace` 适合追踪形成路径和提交路径。`debugStructureCheck` 适合追踪
增量、snapshot precheck、fallback 和 shadow validation。

## 15. 禁止事项

以下做法会破坏 V3 边界：

- 在 GregTech 控制器中重新引入旧结构声明入口。
- 绕过 `StructureDefinition` 构建 parallel pattern 系统。
- 在提交器之外发布 formed lifecycle、ability map、part list 或 committed graph。
- 在异步线程读取 live world、controller、tile entity 或 inventory。
- 用空 dependency 掩盖实际读取的外部状态。
- 让工具链通过执行 runtime-only check 获取展示数据。
- 把 per-controller runtime state 缓存在 static、template 或 compiled pattern 中。
- 在失败路径遗漏 part、ability、channel、metadata 或 contribution rollback。

## 16. 新代码检查清单

新增控制器：

- 实现 `createStructureDefinition()`。
- 使用 `StructureDefinition.getOrBuild(...)`。
- 在 `formStructure(FormedStructureView formed)` 中读取 typed formed data。
- 外部状态变化时递增或更新对应 dependency snapshot。

新增元素：

- 优先实现 `ITypedStructureElement`。
- 明确 `getCapabilities()`。
- 明确 incremental support。
- 声明 piece/external dependencies。
- 提供 preview/candidate metadata。
- 不确定时先返回 opaque 并加诊断日志。

新增工具或预览入口：

- 从 definition/pattern/preview metadata 读取数据。
- 不执行提交路径。
- 不修改 runtime lifecycle。
- 对缺失 metadata 做低频可定位日志。

新增异步逻辑：

- 只传不可变声明和复制快照。
- 带 generation/token。
- 在服务端线程做 live confirm。
- 结果必须进入 `MultiblockStructureCommitter`。

## 17. 维护原则

V3 的设计重心是保守正确性。能证明安全时才走更快的增量或异步路径；不能证明时
回退完整校验，并用诊断日志补齐证据。后续维护应优先保持边界清晰，而不是为了
局部性能把声明、运行、提交和工具链重新耦合在一起。
