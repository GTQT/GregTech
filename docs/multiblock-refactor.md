# 多方块结构系统统一重构计划书

更新时间：2026-05-08（根据最新实机验收记录重写）

## 文档状态

本计划书是当前唯一执行入口，已合并以下两份原计划：

- `docs/multiblock-refactor.md`
- `docs/gt5-structure-channel-porting-plan.md`

GT5 信道移植不再作为独立工程执行，而是并入多方块结构系统重构，作为"结构信道、JEI、投影仪、自动建造一致性"主线。

## 当前结论

经过多轮实机功能测试和信道专项测试，运行时结构检查系统已稳定可用，信道功能大部分已验证通过。
当前阻塞点从"功能层面不工作"收窄为"渲染/视觉层面异常"和少量收尾工作。

当前状态：

> **已完成的核心功能：**
>
> - 编译通过（BOM 已修复）
> - 事件驱动检查统一走基类 `doStructureCheck()` 路径（子类 override 已删除）
> - 异步检查已使用临时 `MultiblockState`（`template.createState()`，无 data race）
> - 异步快照使用精确 AABB + 体积上限保护 + debounce + 邻居去重
> - `MultiblockWorldData.INSTANCES` 已使用 `Collections.synchronizedMap`
> - 分片检查 API 已落地，Forge of Gods 已实现 `createMultiPiecePattern()`
> - 首次成形后 `multiPiecePattern.checkAllPieces()` 已调用
> - `StructurePiece.positions` 已使用 volatile reference + swap
> - 蒸馏塔/装配线/PSS 首次成形已正常（不再需要退出重进）
> - 投影仪信道值传递已修复，设置后能正确影响预览和构建
> - JEI 信道调节后预览/材料列表正确更新
> - JEI UI 布局无重叠
> - `StructureChannelRegistry` legacy key alias 已可用
>
> **当前剩余问题：**
>
> - 投影仪预览模型渲染异常（全黑）
> - Forge of Gods 结构检测成型但没渲染动画
> - 预览层数与构建层数不一致（蒸馏塔、装配线）— 需确认是否仍存在
> - indicator 注册不全（已修复：getChannelRange off-by-one）
> - NO_HATCH 放置逻辑偏差
> - addon 迁移文档 + BlockPattern 废弃路径未定义

## 实机测试结果汇总

### 功能测试结果（2026-05-08 更新）

| 测试项               | 结果    | 备注                          |
| ----------------- | ----- | --------------------------- |
| 普通电力多方块成形与破坏      | ✅ 正常  |                             |
| Steam 多方块成形与破坏    | ✅ 正常  |                             |
| 带线圈机器成形与 tier 读取  | ✅ 正常  |                             |
| 投影仪预览             | ⚠️ 异常 | 预览全息图渲染为黑色                  |
| 投影仪 compare       | ✅ 正常  |                             |
| 投影仪自动建造           | ✅ 正常  |                             |
| 控制器旋转、上下朝向、翻转     | ✅ 正常  |                             |
| 世界保存、退出、重进        | ✅ 正常  |                             |
| 多台机器同时存在的 tick 表现 | ✅ 正常  |                             |
| JEI 结构预览          | ✅ 正常  | UI 布局已修复                    |
| JEI 信道调节          | ✅ 正常  | 预览和材料列表正确更新                 |
| 蒸馏塔/装配线/PSS 首次成形  | ✅ 正常  | 不再需要退出重进，fallback 机制已生效     |
| 投影仪信道值设置          | ✅ 正常  | 设置后预览/构建正确消费                |
| Legacy key alias  | ✅ 正常  | `StructureChannelRegistry` 可解析 |

### 信道专项测试结果（2026-05-08 更新）

| 测试项              | 结果    | 具体表现                     |
| ---------------- | ----- | ------------------------ |
| EBF 投影仪信道设置 coil | ✅ 正常  | 信道值正确传递到预览和构建            |
| 投影仪清空信道按键        | ✅ 正常  | 按键可正确清除信道值               |
| 蒸馏塔 height 预览    | ⚠️ 待确认 | 上次异常，需确认最新状态             |
| 蒸馏塔 height 构建    | ✅ 正常  |                          |
| 装配线 length 预览    | ⚠️ 待确认 | 上次异常，需确认最新状态             |
| 装配线 length 构建    | ✅ 正常  |                          |
| NO\_HATCH 自动建造   | ⚠️ 偏差  | 开启后会空缺一个位置不放置方块（应为放置纯外壳） |
| Indicator 注册     | ⚠️ 不全  | 三钛线圈未注册                  |
| GT5 legacy key   | ✅ 正常  | alias 已可解析               |
| JEI 调 coil       | ✅ 正常  | 3D 预览和材料列表同步变化           |
| JEI 调 height     | ✅ 正常  | 预览高度和材料数量同步变化            |
| 两个投影仪不同配置        | ⬜ 待验  |                          |

## 总目标

本轮统一重构同时解决两类问题。

### 结构运行时问题（✅ 已全部解决）

| 问题                          | 状态    | 备注                                |
| --------------------------- | ----- | --------------------------------- |
| 已成形多方块仍依赖定时轮询               | ✅ 已修复 | 事件驱动 + fallback polling           |
| 每次结构检查可能遍历完整结构              | ✅ 已修复 | 抽样检查 + 分片检查                       |
| `BlockPattern` 同时承载模板与运行时状态 | ✅ 已修复 | template/state 拆分已完成              |
| 缺少区块级位置索引                   | ✅ 已修复 | `MultiblockWorldData` chunk index |
| 缺少分片结构验证                    | ✅ 已实现 | Forge of Gods 已使用                 |
| 异步检查 data race              | ✅ 已修复 | 临时 state 策略                       |
| 子类绕过事件驱动                    | ✅ 已修复 | 子类 override 已删除                   |
| 蒸馏塔等首次成形延迟                  | ✅ 已修复 | fallback 机制生效，已验证通过               |
| 异步快照捕获性能差                   | ✅ 已优化 | 精确 AABB + 体积上限 + debounce + 邻居去重  |

### 结构定义与展示问题

| 问题                 | 状态     | 备注                                      |
| ------------------ | ------ | --------------------------------------- |
| 投影仪信道值传递断裂         | ✅ 已修复  | 已验证通过                                   |
| 清空信道无效             | ✅ 已修复  | 已验证通过                                   |
| JEI 预览 UI 布局重叠     | ✅ 已修复  | 已验证通过                                   |
| JEI 信道调节正确更新       | ✅ 已修复  | 已验证通过                                   |
| Legacy key alias   | ✅ 已修复  | 已验证通过                                   |
| 预览层数与构建层数不一致       | ⚠️ 待确认 | 上轮测试异常，需重新验证                            |
| NO\_HATCH 空缺       | ⚠️ 需修复 | 应放置纯外壳而非跳过                              |
| indicator 注册不全     | ⚠️ 需补全 | 三钛线圈未注册                                 |
| 投影仪预览模型全黑          | ❌ 需修复  | 渲染问题                                    |
| Forge of Gods 渲染动画 | ❌ 需修复  | 结构检测成型了但没播放动画                           |
| GT5 legacy key 未兼容 | ✅ 已完成  | registry alias 已填充                      |

## 参考来源

- GregTech CEu 1.12 当前实现：`FactoryBlockPattern`、`TraceabilityPredicate`
- GregTech Modern：事件驱动、异步检查、`MultiblockState`
- GT5 / StructureLib：`IStructureChannels`、`StructureWrapper`、`withChannel`、分片检查、NEI preview modifier

## 当前实现总览

| 模块         | 当前状态                                                                     | 判断                      |
| ---------- | ------------------------------------------------------------------------ | ----------------------- |
| 事件驱动结构检查   | 统一由基类 `doStructureCheck()` 管理，已接入 `MultiblockWorldData`                  | ✅ **已完成**               |
| 异步结构检查     | 临时 `MultiblockState` + 精确 AABB snapshot + fallback + debounce           | ✅ **已完成**               |
| 模板/实例状态拆分  | `BlockPatternTemplate` + `MultiblockState` + 兼容层 `BlockPattern`          | ✅ 已完成                   |
| 分片式结构检查    | Forge of Gods 已使用 `MultiPiecePattern`，首次成形流程已补充                          | ✅ 已完成（渲染动画问题独立）         |
| 声明式 casing | 已有 `ICasing`、`ICasingGroup`、`DeclarativePatternBuilder`，多数机器已迁移          | ✅ 大部分完成                 |
| 结构信道       | 已有 `StructureChannel`、`GTStructureChannels`、`StructureChannelRegistry`   | ✅ **功能已验证通过**            |
| 信道值容器      | 已有 `StructureChannelValues`，支持 NBT/Map/Context 转换                        | ✅ API 完整                |
| JEI 信道预览   | 已读取 `getSupportedChannels()`、`getChannelRange()`、`getMatchingShapes(cv)` | ✅ **已验证正确**             |
| 投影仪信道      | NBT 持久化已实现，GUI 控件已有，值传递已修复                                               | ✅ **信道功能正常，渲染异常**       |
| 自动建造信道     | `autoBuild` 已消费 `channelValues`，维度控制可用                                   | ⚠️ **NO\_HATCH 行为有偏差** |

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
信道 API 层面已完整，消费端（投影仪/JEI/预览）已验证通过。

### 信道 API 当前实现 vs 需求

| API                        | 状态    | 缺口                                                 |
| -------------------------- | ----- | -------------------------------------------------- |
| `StructureChannel` 接口      | ✅ 完整  |                                                    |
| `GTStructureChannels` enum | ✅ 完整  | 14 个预定义信道                                          |
| `StructureChannelRegistry` | ✅ 完整  | legacy alias 已验证通过                                 |
| `StructureChannelValues`   | ✅ 完整  | NBT/Map/Context 三向转换均可用                            |
| Indicator 注册               | ✅ 已修复 | `getChannelRange` 返回 `[0, maxCandidates]` 而非 `[0, maxCandidates-1]` |
| Legacy key alias           | ✅ 已完成 | 已验证通过                                              |
| 投影仪值传递                     | ✅ 已修复 | 已验证通过                                              |
| preview 层数计算               | ⚠️ 待确认 | 需重新验证 `repetitionDFS` 与 `calculateRepetitionsFromChannels` |

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

### M1：稳定事件驱动与异步检查 — ✅ 已完成

目标：保证结构检查调度不会破坏现有多方块行为。

#### 已修复的全部缺陷

| 缺陷                               | 修复方式                                                        | 验证       |
| -------------------------------- | ----------------------------------------------------------- | -------- |
| 子类 override `doStructureCheck()` | 已删除，基类统一管理 + `isWorkingForStructureCheck()` 钩子              | ✅ 实机验证通过 |
| 异步检查 data race                   | `performAsyncCheck` 使用 `template.createState()` 临时 state    | ✅ 代码确认   |
| `INSTANCES` WeakHashMap 非线程安全    | 已改为 `Collections.synchronizedMap(new WeakHashMap<>())`      | ✅ 代码确认   |
| snapshot 范围固定 32                 | 精确 AABB (`computeWorldAABB`) + 体积上限保护 (`MAX_SNAPSHOT_VOLUME`) | ✅ 代码确认   |
| 首次 tick 结构检查                     | `isFirstTick()` 直接走 `checkStructurePattern()`               | ✅ 实机验证通过 |
| 首次成形延迟（蒸馏塔/装配线/PSS）              | `ASYNC_FALLBACK_INTERVAL=100` tick fallback 机制              | ✅ 实机验证通过 |
| 频繁拆放方块导致卡顿                       | 事件驱动 recheck `RECHECK_COOLDOWN_TICKS=5` debounce           | ✅ 代码确认   |
| NeighborNotify 无效遍历              | `onBlockChanged` 返回 boolean，仅命中时扩散                          | ✅ 代码确认   |

验收（全部通过）：

- ✅ 蒸馏塔、装配线、PSS 搭建好后在 5 秒内自动成形
- ✅ 无需退出世界重进
- ✅ 普通多方块成形/破坏行为与旧版本一致
- ✅ 已成形多方块无方块变化时不主动完整轮询

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
3. `BlockPattern` 保留至 M7 收尾阶段
4. M7 阶段如果附属全部迁移完成，标记 `@ApiStatus.ScheduledForRemoval`

剩余任务：

1. ~~逐步将核心机器的 template 改为静态缓存（`LazyTemplate` 已提供 DCL 方案）~~ — ✅ 15 个单 ID 核心机器已迁移
2. 文档化 `BlockPattern` 的废弃路径
3. 多变体机器的静态 template 缓存迁移（见下方方案）

#### 已完成的静态 template 迁移

基础设施：
- `selfPredicate(ResourceLocation)` — 静态版本，按 ID 精确匹配 controller
- `selfPredicateByClass(Class<?>)` — 按类匹配，用于多变体 controller
- `LazyTemplate` — DCL 双重检查惰性加载

已迁移的 15 个单 ID 核心机器：
- ElectricBlastFurnace, MultiSmelter, VacuumFreezer, ImplosionCompressor
- PyrolyseOven, CrackingUnit, MultiAlloyFurnace, ActiveTransformer
- NetworkSwitch, ResearchStation, CokeOven, PrimitiveBlastFurnace
- PrimitiveWaterPump, SteamGrinder, SteamOven, SawMill

#### 多变体机器静态 template 方案（后续执行）

**方案 A：按变体索引的 `LazyTemplate` 数组**

适用于变体数少且用 tier/index 区分的机器。

```java
private static final LazyTemplate[] TEMPLATES = {
    LazyTemplate.of(() -> buildTemplate(GTValues.MV)),
    LazyTemplate.of(() -> buildTemplate(GTValues.HV)),
    LazyTemplate.of(() -> buildTemplate(GTValues.EV)),
};

@Override
protected BlockPatternTemplate createStructureTemplate() {
    return TEMPLATES[tier - GTValues.MV].get();
}
```

**方案 B：`EnumMap` + 惰性缓存**

适用于已有 enum 类型的机器。

```java
private static final Map<BoilerType, LazyTemplate> TEMPLATES = new EnumMap<>(BoilerType.class);
static {
    for (BoilerType type : BoilerType.values()) {
        TEMPLATES.put(type, LazyTemplate.of(() -> buildTemplate(type)));
    }
}

@Override
protected BlockPatternTemplate createStructureTemplate() {
    return TEMPLATES.get(boilerType).get();
}
```

**方案 C：`ConcurrentHashMap` 按参数组合做 key**

适用于参数多且非 enum 形式的机器。

```java
private static final ConcurrentHashMap<CacheKey, BlockPatternTemplate> CACHE = new ConcurrentHashMap<>();

@Override
protected BlockPatternTemplate createStructureTemplate() {
    return CACHE.computeIfAbsent(
        new CacheKey(casingState, gearboxState, hasMufflerHatch),
        key -> buildTemplate(key)
    );
}
```

**方案 D：重构为"变体 enum 化"（长期最优架构）**

把多变体机器的构造函数参数收束为一个 enum，然后用方案 B 的 `EnumMap` 缓存。

```java
public enum TurbineType {
    STEAM(MetalCasingType.STEEL_SOLID, GearboxType.STEEL, true, Textures.SOLID_STEEL_CASING),
    GAS(MetalCasingType.STAINLESS_CLEAN, GearboxType.STAINLESS, false, Textures.CLEAN_STAINLESS_STEEL_CASING),
    PLASMA(MetalCasingType.TUNGSTENSTEEL_ROBUST, GearboxType.TUNGSTENSTEEL, false, Textures.ROBUST_TUNGSTENSTEEL_CASING);

    public final IBlockState casingState;
    public final IBlockState gearboxState;
    public final boolean hasMufflerHatch;
    public final ICubeRenderer casingRenderer;
    ...
}

private static final Map<TurbineType, LazyTemplate> TEMPLATES = new EnumMap<>(TurbineType.class);
static {
    for (TurbineType type : TurbineType.values()) {
        TEMPLATES.put(type, LazyTemplate.of(() -> buildTemplate(type)));
    }
}

@Override
protected BlockPatternTemplate createStructureTemplate() {
    return TEMPLATES.get(turbineType).get();
}
```

优点：
- 架构最干净，完全消除构造函数参数组合爆炸
- Template 缓存变为 trivial（方案 B 的 EnumMap）
- 可以附加更多元数据（tooltip, texture, tier, recipeMap 等）
- 更易阅读和维护

缺点：
- 改动面最大：需修改构造函数签名、`createMetaTileEntity`、注册处
- 对 addon 兼容性有影响（如果 addon 继承了该类并传入自定义参数）
- 需要逐步推进，不能一步到位

适用于所有多变体机器，是长期目标方向。建议在确认无 addon 继承后逐步推进。

**各机器推荐方案：**

| 机器                    | 短期推荐 | 长期推荐 | 变体数 | key 类型            |
| --------------------- | ---- | ---- | --- | ----------------- |
| FluidDrill            | A    | D    | 3   | tier (MV/HV/EV)   |
| LargeMiner            | A    | D    | 3   | tier (EV/IV/LuV)  |
| ProcessingArray       | A    | D    | 2   | boolean→index     |
| MultiblockTank        | A    | D    | 2   | boolean→index     |
| LargeCombustionEngine | A    | D    | 2   | boolean→index     |
| LargeBoiler           | B    | B    | 4   | BoilerType enum（已有） |
| LargeTurbine          | C    | D    | 3   | (casing, gearbox, hasMuffler) → TurbineType enum |

**HPCA / LargeChemicalReactor 特殊处理：**

这两个使用了实例方法 (`maintenancePredicate()`, `autoAbilities()`)，不是多变体问题：
- HPCA：将 `maintenancePredicate()` 替换为 `withOptionalHatches(MAINTENANCE_HATCH, ConfigHolder.machines.enableMaintenance ? 1 : 0)`
- LargeChemicalReactor：将 `autoAbilities()` 逻辑内联为等价的 `.withXXXHatches()` 调用

#### 方案4：模板池 + 弱引用（极端内存优化）— ✅ 已实现

适用场景：有几百种多方块（大量 addon），且很多结构非常大，玩家不一定同时使用所有类型。

**核心思路**：用 `SoftReference` 替代强引用，让不活跃类型的 template 在 JVM 内存压力时被回收。

已实现组件：

| 组件 | 文件 | 职责 |
|------|------|------|
| `SoftTemplate` | `api/pattern/SoftTemplate.java` | 底层 SoftReference 持有者 + 30秒防抖 pin |
| `TemplatePool` | `api/pattern/TemplatePool.java` | 全局注册中心 + 统计 + 批量驱逐 |

**与 LazyTemplate 的共存策略：**

| 场景 | 推荐 |
|------|------|
| 核心高频机器（EBF、真空冷冻等） | `LazyTemplate` — 永不回收 |
| 大量 addon 冷门机器 | `SoftTemplate` / `TemplatePool` — 可被回收 |
| 多变体机器 | `TemplatePool.register(key, factory)` 按 key 注册 |

**防抖机制**：template 创建后通过强引用 pin 保持 30 秒，防止 GC→重建→GC 循环。

**内存回收链路**：
Controller 卸载 → 强引用消失 → 仅剩 SoftReference → JVM 内存压力时回收 → factory 按需重建

**使用示例**：

```java
// Addon 冷门机器
private static final SoftTemplate TEMPLATE = TemplatePool.getInstance()
    .register("myaddon:exotic_reactor", () ->
        DeclarativePatternBuilder.start()
            .where('S', selfPredicate(new ResourceLocation("myaddon", "exotic_reactor")))
            .aisle(...)
            .buildTemplate()
    );

@Override
protected BlockPatternTemplate createStructureTemplate() {
    return TEMPLATE.get();
}
```

### M3：信道 bug 修复与完善 — ✅ 大部分完成

目标：修复实机测试发现的信道功能 bug，让投影仪、JEI、自动建造正确消费信道值。

#### 已验证通过

| Bug                | 状态    | 验证结果     |
| ------------------ | ----- | -------- |
| Bug 2：投影仪信道值设置无效   | ✅ 已修复 | 信道值正确传递  |
| Bug 3：清空信道按键无效     | ✅ 已修复 | 按键可正确清除  |
| Legacy key alias 缺失 | ✅ 已完成 | alias 可解析 |
| JEI 信道调节后预览/材料列表更新  | ✅ 已修复 | 同步正确     |

#### 待确认/待修复

| Bug               | 状态     | 备注                                      |
| ----------------- | ------ | --------------------------------------- |
| Bug 4：预览层数不一致     | ⚠️ 待确认 | 上轮异常，最新状态需重新验证                          |
| Bug 5：NO\_HATCH 空缺 | ⚠️ 需修复 | `autoBuild` 跳过而非替换                      |
| Indicator 注册不全    | ✅ 已修复 | `getChannelRange` off-by-one + `loadComplete` 中 invalidateCache 确保 addon 线圈也被覆盖 |

#### Bug 4：预览层数与构建层数不一致（待重新验证）

**现象**：蒸馏塔设置 height=5，构建正确产出 5 层，但预览显示的层数不对。

**分析**：预览路径和构建路径使用不同的 repetition 计算方式：

- **构建路径** (`autoBuild`)：使用 `calculateRepetitionsFromChannels(channelValues)` — 根据 `aisleChannelNames` 匹配信道名
- **预览路径** (`getMatchingShapes` → `repetitionDFS`)：也消费 `channelValues`，但匹配逻辑可能不同

**状态**：JEI 调 height 已验证正确，投影仪预览是否正确需重新验证（可能已随着其他修复一并解决）。

涉及文件：

- `MultiblockState.java` — `calculateRepetitionsFromChannels`
- `MultiblockControllerBase.java` — `repetitionDFS`

#### Bug 5：NO\_HATCH 空缺位置（P2 中）

**现象**：开启 NO\_HATCH 后，hatch 位置空缺一个方块不放置任何东西。

**分析**：当前 `autoBuild` 中 `skipHatches = true` 的逻辑可能是直接跳过该位置，
而正确行为应该是在 hatch 候选位置放置对应外壳方块（即 predicate 的 non-hatch candidate）。

**修复方案**：
`autoBuild` 中当 `skipHatches` 为 true 时，对于 hatch predicate 位置不是跳过，
而是使用其 casing candidate（TraceabilityPredicate 中的非 hatch 候选方块）。

涉及文件：

- `MultiblockState.java` — `autoBuild` 方法

剩余任务：

1. 重新验证 Bug 4（预览层数是否已正确）
2. 修复 Bug 5：NO\_HATCH 放置逻辑
3. 补全 indicator 注册（确保所有 coil group 都调用了 `registerIndicatorsFromGroup`）

验收（部分通过）：

- ✅ 投影仪设置 `coil=3`，预览、compare、autoBuild 全部使用 tier 3 coil
- ⚠️ 投影仪设置 `height=5`，预览和构建均为 5 层（待重新验证）
- ✅ 清空按键可以正确清除所有信道值
- ⚠️ NO\_HATCH 模式下所有位置都有方块（纯外壳填充）（待修复）
- ⚠️ indicator 物品可以正确查询所有已注册线圈类型（三钛线圈待补全）

### M4：JEI 信道预览修复 — ✅ 已完成

目标：JEI 多方块预览正确展示信道控制效果，UI 布局正常。

验收（全部通过）：

- ✅ JEI 中调 EBF coil，3D 预览和材料列表同步变化
- ✅ JEI 中调蒸馏塔 height，预览高度和材料数量同步变化
- ✅ 选择方块后的候选列表不会遮挡其他 UI 元素

### M5：投影仪完善 — ⚠️ 渲染问题

目标：投影仪作为完整的 GT5 trigger item 等价物正确工作。

已验证通过：

- ✅ NBT 持久化正常
- ✅ 信道值传递正确
- ✅ compare mode 正常
- ✅ 自动建造正常

**新发现问题：投影仪预览模型渲染全黑**

**现象**：投影仪右键 controller 后，全息预览渲染为纯黑色方块，而非正确的半透明彩色预览。

**分析**：可能原因：
1. `MultiblockPreviewRenderer` 中的光照计算有误
2. 纹理/贴图未正确绑定
3. 着色器或 blend 模式设置问题
4. 颜色 uniform 没有传入正确值

涉及文件：

- `MultiblockPreviewRenderer.java` — 渲染逻辑

剩余任务：

1. 修复投影仪预览渲染全黑问题
2. 验证两个不同投影仪物品能保存不同信道配置（per-ItemStack NBT）
3. 验证关闭 GUI、丢地上、重进世界后配置仍在

验收：

- ⚠️ 投影仪预览显示正确的半透明全息图（而非黑色）
- ⬜ 两个投影仪物品保存不同信道配置，互不影响
- ✅ 投影仪选择 `coil=4` 后，compare、自动建造均使用同一 coil tier

### M6：分片式结构检查实机验收 — ⚠️ 渲染动画问题

目标：Forge of Gods 的分片检查在所有朝向下正确工作。

当前已完成：

- ✅ `StructurePiece`、`MultiPiecePattern` 已存在
- ✅ `createMultiPiecePattern()` 已作为 opt-in 入口
- ✅ `MultiblockWorldData` 可按方块位置标记 dirty piece
- ✅ `OffsetMode` enum 已实现（ABSOLUTE / RELATIVE / HORIZONTAL\_RELATIVE）
- ✅ 结构检测已验证成型

**新发现问题：成型后没有渲染动画**

**现象**：Forge of Gods 结构检测成型后不播放成型动画。

涉及文件：

- `MetaTileEntityForgeOfGods.java` — 动画触发逻辑

剩余任务：

1. 修复成型后的渲染动画触发
2. 在 Forge of Gods 上验证各朝向（NORTH/SOUTH/EAST/WEST）下的结构检查
3. 测试局部 dirty piece 重检（破坏某一环的一个方块）
4. 验证 conditional piece 的 activate/deactivate 行为

验收：

- ✅ Forge of Gods 结构检测成型
- ⚠️ 成型后播放正确的渲染动画
- ⬜ 各朝向下结构检查正确
- ⬜ 修改单个片段只重检该片段
- ⬜ piece 失效能导致整个多方块正确失效

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
3. ✅ 已添加 `gregtech.multiblock.ability.*` 国际化键值（en\_us + zh\_cn）
4. ✅ 确认已迁移机器中无冗余 `setMinGlobalLimited`
5. ⬜ addon 迁移说明：待所有子任务完成后撰写

任务：

1. 验证所有已迁移机器的信道行为
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

| 测试项              | 预期行为                  | 最新结果 |
| ---------------- | --------------------- | ---- |
| 普通电力多方块成形与破坏     | 放置即成形，破坏即失效           | ✅    |
| Steam 多方块成形与破坏   | 同上                    | ✅    |
| 带线圈机器成形与 tier 读取 | 检测到正确 coil tier       | ✅    |
| JEI 结构预览         | 正确 3D 渲染，无 UI 重叠      | ✅    |
| 投影仪预览            | 显示正确全息图（非黑色）          | ❌    |
| 投影仪 compare      | 标红缺失/错误方块             | ✅    |
| 投影仪自动建造          | 正确放置所有方块              | ✅    |
| 控制器旋转、翻转         | 各朝向下结构检查正确            | ✅    |
| 世界保存/退出/重进       | 成形状态持久化               | ✅    |
| 多台机器同时存在         | tick 性能无明显退化          | ✅    |
| 蒸馏塔/装配线/PSS 首次成形 | 搭建后 < 5 秒自动成形（不需退出重进） | ✅    |

### 信道专项测试

| 测试项                    | 预期行为                                | 最新结果   |
| ---------------------- | ----------------------------------- | ------ |
| EBF 投影仪设置 coil         | 预览/构建使用对应线圈                         | ✅      |
| 投影仪清空信道                | 所有信道值归零                             | ✅      |
| 蒸馏塔 height 预览          | 预览层数 = 设定值                          | ⚠️ 待确认 |
| 蒸馏塔 height 构建          | 构建层数 = 设定值                          | ✅      |
| 装配线 length 预览          | 预览段数 = 设定值                          | ⚠️ 待确认 |
| 装配线 length 构建          | 构建段数 = 设定值                          | ✅      |
| NO\_HATCH 自动建造         | hatch 位置放置纯外壳（非空缺）                  | ⚠️ 待修复 |
| Indicator 查询           | 所有已注册 coil/glass 的 ItemStack 可查     | ⚠️ 不全  |
| GT5 legacy key resolve | `resolve("coil")` 返回 `HEATING_COIL` | ✅      |
| JEI 调 coil             | 3D 预览和材料列表同步变化                      | ✅      |
| JEI 调 height           | 预览高度和材料数量同步变化                       | ✅      |
| 两个投影仪不同配置              | per-ItemStack NBT 互不影响              | ⬜ 待验   |

### 压力测试

- 100 台已成形小型多方块，无方块变化时观察 tick 成本
- 100 台未成形控制器，观察异步检查对主线程影响
- 跨多个 chunk 的大型结构，破坏不同 chunk 中的内部方块
- 大型结构启用分片后，破坏不同片段并记录重检耗时

## 风险清单

| #  | 风险                                             | 严重程度    | 当前状态                             | 修复方案                             | 里程碑 |
| -- | ---------------------------------------------- | ------- | -------------------------------- | -------------------------------- | --- |
| 1  | 子类 override `doStructureCheck()`               | ~~P0~~ | ✅ 已修复                            | —                                | M1  |
| 2  | 异步检查 data race                                 | ~~P0~~ | ✅ 已修复                            | —                                | M1  |
| 3  | MultiPiecePattern 首次成形流程缺失                     | ~~P0~~ | ✅ 已修复                            | —                                | M6  |
| 4  | 编译失败                                           | ~~高~~  | ✅ 已修复                            | —                                | M0  |
| 5  | `INSTANCES` WeakHashMap 非线程安全                  | ~~P1~~ | ✅ 已修复                            | —                                | M1  |
| 6  | `StructurePiece.positions` 并发访问                | ~~P1~~ | ✅ 已修复                            | —                                | M6  |
| 7  | snapshot 范围不足                                  | ~~P1~~ | ✅ 已修复                            | —                                | M1  |
| 8  | 异步首次成形延迟（蒸馏塔/装配线/PSS）                          | ~~P1~~ | ✅ 已修复                            | —                                | M1  |
| 9  | 投影仪信道值传递断裂                                     | ~~P0~~ | ✅ 已修复                            | —                                | M3  |
| 10 | 清空信道按键无效                                       | ~~P1~~ | ✅ 已修复                            | —                                | M3  |
| 11 | JEI UI 布局重叠                                    | ~~P2~~ | ✅ 已修复                            | —                                | M4  |
| 12 | JEI 信道调节后更新不正确                                 | ~~P2~~ | ✅ 已修复                            | —                                | M4  |
| 13 | Legacy key alias 缺失                            | ~~P3~~ | ✅ 已完成                            | —                                | M3  |
| 14 | **投影仪预览模型渲染全黑**                                | **P1** | ❌ 未修复                            | 排查 `MultiblockPreviewRenderer` 渲染 | M5  |
| 15 | **Forge of Gods 成型后无渲染动画**                      | **P2** | ❌ 未修复                            | 排查动画触发逻辑                         | M6  |
| 16 | 预览层数与构建层数不一致                                   | P1 待确认 | ⚠️ 需重新验证                          | 可能已随其他修复解决                       | M3  |
| 17 | NO\_HATCH 空缺                                   | P2 中   | ❌ 未修复                            | 改为放置 casing candidate            | M3  |
| 18 | Indicator 注册不全                                 | P2 中   | ⚠️ 三钛线圈未注册                        | 调用 `registerIndicatorsFromGroup` | M3  |
| 19 | `BlockPattern` 过渡路径未设 deadline                 | P4 低   | ⬜ 已标 deprecated，未设移除版本           | M7 设定 `@ScheduledForRemoval`     | M7  |
| 20 | 分片 `structurePattern`/`multiPiecePattern` 注册冲突 | P3 低   | ⬜ 代码已有处理但未验证                     | 实机验证                             | M6  |

## 完成定义

只有满足以下条件，统一重构才能标记为完成：

| #  | 条件                                          | 状态    | 备注                                 |
| -- | ------------------------------------------- | ----- | ---------------------------------- |
| 1  | `compileJava` 通过                            | ✅ 已完成 |                                    |
| 2  | 事件驱动结构检查统一走新调度路径                            | ✅ 已完成 |                                    |
| 3  | 异步检查不存在共享状态并发写入风险                           | ✅ 已完成 | `template.createState()` 临时 state  |
| 4  | 异步快照精确 AABB + 体积上限保护                        | ✅ 已完成 | `computeWorldAABB` + `MAX_SNAPSHOT_VOLUME` |
| 5  | 事件驱动 recheck debounce                       | ✅ 已完成 | `RECHECK_COOLDOWN_TICKS = 5`       |
| 6  | NeighborNotify 邻居检查去重                        | ✅ 已完成 | `onBlockChanged` 返回 boolean        |
| 7  | 蒸馏塔等可变结构首次成形不需退出重进                          | ✅ 已验证 |                                    |
| 8  | 投影仪信道值正确传递到 preview/compare/autoBuild       | ✅ 已验证 |                                    |
| 9  | 预览层数与构建层数一致                                 | ⚠️ 待确认 | 需重新验证                              |
| 10 | `StructureChannelRegistry` legacy key alias | ✅ 已验证 |                                    |
| 11 | indicator 注册覆盖所有 casing group（含三钛线圈）        | ⚠️ 未完成 | 三钛线圈待注册                            |
| 12 | JEI 中信道调节后预览/材料列表正确更新                       | ✅ 已验证 |                                    |
| 13 | NO\_HATCH 放置纯外壳而非空缺                         | ⚠️ 待修复 |                                    |
| 14 | JEI UI 布局无重叠                                | ✅ 已验证 |                                    |
| 15 | 投影仪预览模型正确渲染（非全黑）                            | ❌ 需修复 | 当前渲染为黑色                            |
| 16 | 至少一个超大结构（Forge of Gods）分片检查 + 渲染动画          | ⚠️ 部分  | 结构成型但无动画                           |
| 17 | `BlockPattern` 兼容层有 deprecation 路径和移除版本号     | ⬜ 未开始 |                                    |
| 18 | addon 迁移说明文档完成                              | ⬜ 未开始 |                                    |

## 异步快照性能优化记录

更新时间：2026-05-08

### 已完成的优化

#### 优化 1：精确 AABB Snapshot 替代对称立方体

**问题**：旧代码用 `max(palm, thumb, finger)` 作对称半径捕获正方体。细长结构（如装配线 3×3×20）的 radius=20，捕获 41³=68,921 个方块，实际有效只有 ~180 个。

**方案**：在 `BlockPatternTemplate` 新增 `computeWorldAABB()` 方法，利用 `RelativeDirection.setActualRelativeOffset` 将 pattern 局部坐标系的 8 个角点变换到世界坐标，取 min/max 得到精确 AABB。

**效果**：装配线从 68,921 → ~1,176 方块（减少 98%）；EBF 从 ~30,000 → ~3,000 方块。

**涉及文件**：

- `BlockPatternTemplate.java` — 新增 `computeWorldAABB(centerPos, frontFacing, upwardsFacing, isFlipped, margin)`
- `AsyncStructureChecker.java` — `captureSnapshotForController` 改用精确 AABB

#### 优化 2：Snapshot 体积上限保护

**问题**：极端大型结构如果被误判为可异步检查，其 snapshot 会在主线程产生巨量世界读取。

**方案**：新增 `MAX_SNAPSHOT_VOLUME = 100³ = 1,000,000`。AABB 体积超限时返回 `null`，控制器被放入 `oversizedQueue`，在 `processResults()` 中直接走主线程 `checkStructurePattern()`。诸神之煅炉已有 `allowsAsyncStructureCheck() = false` 作为首道防线，此为二道保护。

**涉及文件**：

- `AsyncStructureChecker.java` — `MAX_SNAPSHOT_VOLUME` 常量 + `oversizedQueue` 队列

#### 优化 3：事件驱动 Recheck Debounce（5 tick 冷却）

**问题**：玩家持续拆放方块时，每 tick 都触发 `checkStructurePattern()`（完整 pattern 匹配），无节流。

**方案**：`onBlockChanged` 新增 `gameTick` 参数记录变更时刻。`hasPendingRecheck` 新增 `currentTick` 参数，冷却窗口（`RECHECK_COOLDOWN_TICKS = 5`，即 250ms@20TPS）内的多次操作折叠为一次检查。

**效果**：玩家再怎么快速拆放，每 5 tick 最多触发一次完整 pattern check。

**涉及文件**：

- `MultiblockWorldData.java` — `lastChangedTick` map + `RECHECK_COOLDOWN_TICKS` 常量
- `MultiblockControllerBase.java` — `hasPendingRecheck(this, getWorld().getTotalWorldTime())`
- `BlockChangeListener.java` — 传入 `world.getTotalWorldTime()`

#### 优化 4：NeighborNotify 邻居检查去重

**问题**：`NeighborNotifyEvent` 对 source + 每个通知方向各调用一次 `onBlockChanged`（最多 7 次），但大多数 block change 与多方块结构完全无关。

**方案**：`onBlockChanged` 返回 `boolean`（是否命中注册的多方块）。`onNeighborNotify` 只有在 source 位置命中时才进一步检查邻居位置。

**效果**：对大多数与多方块无关的方块变化，直接跳过 1-6 次额外的 map 查找。

**涉及文件**：

- `MultiblockWorldData.java` — `onBlockChanged` 返回 `boolean`
- `BlockChangeListener.java` — 条件传播逻辑

### 性能收益估算

| 场景                          | 旧方案主线程开销/tick | 新方案主线程开销/tick | 改善      |
| --------------------------- | ------------- | ------------- | ------- |
| 4 台装配线未成形，异步快照捕获            | ~275K 世界读取    | ~4.7K 世界读取    | ~98% ↓  |
| 4 台 EBF 未成形，异步快照捕获          | ~120K 世界读取    | ~12K 世界读取     | ~90% ↓  |
| 已成形多方块 + 玩家每 tick 拆放方块      | 20 次完整 check  | 4 次完整 check   | 80% ↓   |
| 远离多方块的方块变化触发 NeighborNotify | 7 次 map 查找    | 1 次 map 查找    | 86% ↓   |

## 当前下一步

当前阻塞项已从"功能不工作"收窄为"渲染/视觉异常"和少量收尾。推荐执行顺序：

```text
Phase 1 — 渲染修复（当前最高优先级）
  1. M5: 投影仪预览渲染全黑修复
  2. M6: Forge of Gods 成型动画修复

Phase 2 — 功能收尾
  3. M3: 重新验证预览层数一致性（Bug 4，可能已解决）
  4. M3: NO_HATCH 放置逻辑修复（Bug 5）
  5. M3: indicator 注册补全（三钛线圈）
  6. M5: 投影仪 per-ItemStack 验证

Phase 3 — 迁移收尾
  7. M6: Forge of Gods 各朝向 + 局部重检验收
  8. M7: BlockPattern 设定移除版本号
  9. M7: addon 迁移指南
  10. M7: 对可变尺寸结构统一确认 aisleChannelNames 已设置
```

### 里程碑依赖关系

```text
M5(渲染修复) ─→ M5(per-ItemStack 验证)
                     │
M3(收尾) ────────────┤
                     │
M6(动画修复) ─→ M6(验收) ─→ M7(收尾)
```

无硬依赖阻塞：

- M5 渲染修复和 M3 收尾可并行
- M6 动画修复独立于 M3/M5
- M7 在 M3/M5/M6 完成后执行


parallelRecipesPerformed 旧兼容桥的移除
