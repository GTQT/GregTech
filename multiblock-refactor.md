# 多方块结构系统重构方案

## 概述

本文档描述了多方块结构定义与验证系统的全面重构方案。目标是针对以下场景优化性能：
- **超大多方块机器**（单机结构体积巨大）
- **大量多方块机器同时存在**（世界中同时运行数百台多方块）

方案基于对三个代码库的分析：
- **GregTech CEu 1.12**（当前项目）- `FactoryBlockPattern` + `TraceabilityPredicate`
- **GregTech Modern 1.20**（GTM）- 事件驱动 + 异步检查 + `MultiblockState` 分离
- **GT5-Unofficial 1.7.10**（GT5）- `IStructureDefinition` + `StructureWrapper` + 分片检查

---

## 当前架构问题

| 问题 | 影响 | 影响范围 |
|------|------|----------|
| 定时轮询检查（每20tick） | 方块无变化时浪费CPU | 多机器场景 |
| 每次检查完整遍历所有方块 O(n) | 大型机器每次检查代价高 | 大型机器 |
| `BlockPattern` 混合了模板和运行时状态 | 内存浪费（100台相同机器 = 100份predicate副本） | 多机器场景 |
| 无区块级索引 | 无法快速确定哪个多方块受方块变化影响 | 多机器场景 |
| 无分片/片段概念 | 无法对大型结构进行局部验证 | 大型机器 |
| 手动计算外壳数量 (`setMinGlobalLimited`) | 开发者负担重且容易出错 | 所有机器 |

---

## 重构任务

### P0: 事件驱动结构检查 + 区块坐标索引

**来源:** GTM `LevelMixin` + `MultiblockWorldSavedData`

**优先级:** 关键

**解决的问题:** 消除已成形多方块的不必要定期检查。世界中有100+台机器时，只有受方块变化影响的那一台触发验证。

**实现细节:**

1. **方块变化事件监听（推荐方案A + 方案B组合）**

   | 方案 | Hook位置 | 影响其他mod? | 推荐度 |
   |------|----------|-------------|--------|
   | **方案A: Forge事件** | `BlockEvent.NeighborNotifyEvent` + `BreakEvent` + `PlaceEvent` | ❌ 零影响 | ⭐⭐⭐⭐⭐ |
   | **方案B: Mixin @Inject** | `World.setBlockState()` 的 `@At("RETURN")` | 🟡 极低影响 | ⭐⭐⭐⭐ |
   | ~~方案C: Mixin @Overwrite~~ | ~~覆盖World方法~~ | 🔴 高风险冲突 | ❌ 不推荐 |

   **推荐策略:**
   - 优先使用Forge原生事件（方案A）覆盖95%场景（玩家破坏/放置/活塞推动等）
   - 仅在Forge事件覆盖不到的特殊情况下（如某mod调用 `world.setBlockState(pos, state, 2)` 只sync客户端不通知邻居），启用Mixin作为补充
   - Mixin使用 `@Inject(at = @At("RETURN"))`，不修改方法行为，只追加观察者逻辑

   方案A的实现:
   ```java
   @SubscribeEvent
   public void onBlockNotify(BlockEvent.NeighborNotifyEvent event) {
       BlockPos pos = event.getPos();
       World world = (World) event.getWorld();
       MultiblockWorldData.get(world).onBlockChanged(pos);
   }
   ```

   方案B的实现（补充覆盖）:
   ```java
   @Mixin(World.class)
   public class MixinWorld {
       @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;"
               + "Lnet/minecraft/block/state/IBlockState;I)Z",
               at = @At("RETURN"))
       private void gregtech$onSetBlockState(BlockPos pos, IBlockState newState, 
               int flags, CallbackInfoReturnable<Boolean> cir) {
           if (cir.getReturnValue()) {
               MultiblockWorldData.get((World)(Object)this).onBlockChanged(pos);
           }
       }
   }
   ```

2. **区块坐标索引注册表**
   - 维护 `Map<ChunkPos, Set<MultiblockController>>` 用于所有已成形的多方块
   - 多方块成形时，按区块注册其所有方块位置
   - 多方块失效时，取消注册

3. **方块变化处理器**
   - 当位置P处的方块发生变化时：
     - 找到P所在的区块
     - 对该区块中注册的每个多方块：
       - 检查P是否在结构的缓存位置集合中（O(1)查找）
       - 是：仅对该多方块触发重新验证
       - 否：跳过

4. **未成形控制器的回退机制**
   - 未成形的控制器仍使用定期检查（但频率降低）
   - 只有已成形的结构受益于事件驱动模式

**性能影响:**
- 已成形机器：从每20tick O(N_machines × N_blocks_per_machine) → 无方块变化时 O(1)
- 方块破坏/放置：只有受影响的多方块重新检查，O(受影响方块数) 而非 O(总方块数)

**对其他mod的影响:**
- 方案A（Forge事件）：**零影响**，使用Forge标准API
- 方案B（Mixin @Inject RETURN）：**极低影响**，原因如下：
  - `@Inject` 支持多mod同时注入同一方法，互不干扰
  - 注入在 `RETURN` 位置，不修改原方法逻辑，只追加观察者
  - 不使用 `@Overwrite` 或 `@Redirect`，不会覆盖其他mod的修改

**与常见mod的兼容性:**

| Mod | 它做了什么 | 是否与我们冲突 |
|-----|-----------|---------------|
| Sponge (服务端) | 用Mixin大量修改World | ❌ `@Inject(RETURN)` 安全共存 |
| FoamFix | 优化mod | ❌ 不修改 `setBlockState` |
| Phosphor (光照优化) | 修改光照计算 | ❌ 不修改 `setBlockState` |
| Cubic Chunks | 重写世界高度 | 🟡 可能改World类结构，需测试 |
| Optifine | 渲染优化 | ❌ 不碰 `setBlockState` |
| LittleTiles | 自定义方块系统 | ❌ 不修改vanilla World方法 |
| BuildCraft/IC2/Thermal | 标准工业mod | ❌ 不用Mixin |
| Applied Energistics 2 | AE2 | ❌ 不修改World方法 |

**需要修改的关键文件:**
- `MultiblockControllerBase.java` - 添加注册/注销逻辑
- 新增: `MultiblockWorldData.java` - 区块坐标注册表 + 事件分发
- 新增: `MixinWorld.java`（可选）- 补充覆盖 Forge事件捕获不到的方块变化
- 新增: `mixins.gregtech.minecraft.json` 中添加 `MixinWorld`

**依赖:** 无（可独立完成）
**前置条件:** 项目已具备MixinBooter基础设施（已确认存在）

---

### P1: MultiblockState 分离（模板/实例拆分）

**来源:** GTM `MultiblockState`

**优先级:** 高

**解决的问题:** 内存优化。当前每个多方块实例创建自己的 `BlockPattern`（包含完整的predicate数组）。分离后，100台相同机器共享1份模板。

**实现细节:**

1. **将 `BlockPattern` 拆分为两个类:**
   - `BlockPatternTemplate`（不可变，按机器类型共享）
     - `TraceabilityPredicate[][][]` - 结构形状谓词
     - `int[]` 可重复范围
     - 所有静态结构信息
   - `MultiblockState`（按实例持有，可变）
     - `LongOpenHashSet cache` - 缓存的方块位置
     - `PatternMatchContext matchContext` - 运行时匹配结果
     - `int[] globalCount` - 当前外壳计数
     - `PatternError error` - 当前错误状态
     - 用于线程安全的锁（为P2做准备）

2. **模板创建的工厂模式:**
   ```java
   // 按机器类型（共享单例）
   private static final BlockPatternTemplate TEMPLATE = createStructurePattern();
   
   // 按机器实例（轻量级）
   private final MultiblockState state = TEMPLATE.createState();
   ```

3. **线程安全准备:**
   - `MultiblockState` 持有 `ReentrantLock`
   - `checkPatternAt()` 在修改状态前获取锁
   - 只读访问（如 `isFormed()`）不需要锁

**内存影响:**
- 重构前: 100台EBF = 100 × (predicates + cache + context) ≈ 100 × 50KB = 5MB
- 重构后: 1份模板 (50KB) + 100 × 轻量状态 (2KB) ≈ 250KB
- **约95%的内存减少**（对于相同机器）

**需要修改的关键文件:**
- `BlockPattern.java` → 拆分为 `BlockPatternTemplate.java` + `MultiblockState.java`
- `FactoryBlockPattern.java` → 返回 `BlockPatternTemplate`
- `MultiblockControllerBase.java` → 持有 `MultiblockState` 而非 `BlockPattern`
- 所有 `createStructurePattern()` 实现 → 改为静态/缓存

**依赖:** 无，但应在P2之前完成（异步检查需要线程安全的状态）

---

### P2: 异步结构检查线程

**来源:** GTM `ScheduledExecutorService`

**优先级:** 高

**解决的问题:** 未成形控制器的结构验证在非主线程运行。主线程永远不会在结构检查上阻塞。

**实现细节:**

1. **未成形控制器的异步执行器:**
   ```java
   // 在 MultiblockWorldData 中
   private final ScheduledExecutorService executor = 
       Executors.newSingleThreadScheduledExecutor(r -> {
           Thread t = new Thread(r, "GT-Multiblock-Check");
           t.setDaemon(true);
           return t;
       });
   
   // 每250ms（约5tick）检查未成形的控制器
   executor.scheduleAtFixedRate(this::asyncCheckTask, 0, 250, TimeUnit.MILLISECONDS);
   ```

2. **线程安全的世界访问:**
   - 结构检查需要从世界中读取 `IBlockState`
   - 在1.12.2中，`World.getBlockState()` 不是线程安全的
   - 解决方案：在异步检查前于主线程中拍摄 `ChunkCache` 快照
   - 或：使用 `tryLock()` 模式，主线程正在修改时跳过

3. **主线程回调:**
   - 当异步检查发现结构成形/破坏时：
     - 通过 `FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask()` 调度主线程回调
     - 主线程执行状态变更（成形/失效多方块）

4. **错峰机制:**
   - 不在每个周期检查所有未成形控制器
   - 使用 `(controllerId + tickCount) % 4 == 0` 分散负载

**性能影响:**
- 主线程：未成形控制器检查消耗零时间
- 异步线程：检查是I/O密集型（读取方块状态），CPU占用低
- 成形检测延迟：约250-1000ms（对玩家体验可接受）

**需要修改的关键文件:**
- 新增: `AsyncStructureChecker.java` - 执行器 + 任务调度
- `MultiblockWorldData.java` - 与P0注册表集成
- `MultiblockControllerBase.java` - 注册/注销异步检查
- `BlockPattern.java` / `MultiblockState.java` - 线程安全的检查方法

**依赖:** P1（需要带锁支持的 `MultiblockState`）

---

### P3: 分片式结构检查

**来源:** GT5 `checkPiece()` 多片段系统

**优先级:** 中高

**解决的问题:** 对于超大结构（如127×29×155的上帝之炉 ≈ 570,000个方块），每次检查整个结构代价过高。分片检查允许只验证变化的部分。

**实现细节:**

1. **片段定义API:**
   ```java
   public class StructurePiece {
       private final String name;
       private final BlockPatternTemplate template;
       private final Vec3i offset; // 相对于控制器的偏移
       private boolean validated; // 缓存的验证状态
       private boolean dirty;     // 需要重新检查
   }
   ```

2. **多片段结构构建器:**
   ```java
   return MultiPiecePattern.builder()
       .piece("core", corePattern, Vec3i.ZERO)
       .piece("ring1", ring1Pattern, new Vec3i(0, 0, -59))
       .piece("ring2", ring2Pattern, new Vec3i(0, 0, -67))
       .conditionalPiece("ring2", () -> isUpgradeActive(RING2))
       .build();
   ```

3. **脏片段追踪（与P0集成）:**
   - 当P0检测到已成形多方块中有方块变化时：
     - 确定哪个片段包含变化的位置
     - 仅将那些片段标记为脏
     - 仅重新验证脏片段（而非整个结构）

4. **条件片段:**
   - 某些片段只在特定条件满足时才需要验证
   - 例如：扩展环只在升级激活时检查
   - 减少较小配置下的基准验证开销

**性能影响:**
- 570K方块结构拆成10个片段：单片段中方块变化 → 验证约57K方块而非570K
- 对于典型多方块（< 100方块）：开销极小，单片段行为
- 片段缓存：已验证的片段在标记为脏之前跳过重新检查

**需要修改的关键文件:**
- 新增: `StructurePiece.java` - 单个片段定义
- 新增: `MultiPiecePattern.java` - 带片段的复合模式
- `MultiblockControllerBase.java` - 片段感知的验证逻辑
- `MultiblockWorldData.java` - 片段级脏追踪

**依赖:** P0（事件驱动用于脏标记），P1（每片段的状态）

---

### P4: 声明式 ICasing 管理

**来源:** GT5 `ICasing` / `ICasingGroup` / `StructureWrapper`

**优先级:** 中

**解决的问题:** 开发体验优化。当前每个多方块需要手动指定 `setMinGlobalLimited(14)` 并手动实现分级追踪（线圈类型等）。这容易出错且冗长。

**实现细节:**

1. **ICasing 接口:**
   ```java
   public interface ICasing {
       IBlockState getBlockState();
       String getLocalizedName();
       boolean isTiered();
       int getTier(); // 用于分级外壳（线圈等）
   }
   
   public interface ICasingGroup {
       String getGroupName();
       List<ICasing> getCasings();
       boolean requiresUniformTier(); // 所有外壳必须同级
   }
   ```

2. **声明式仓室配置:**
   ```java
   // 当前冗长写法:
   .where('X', states(getCasingState())
       .setMinGlobalLimited(14)
       .or(abilities(IMPORT_ITEMS).setMinGlobalLimited(1).setMaxGlobalLimited(4))
       .or(abilities(EXPORT_ITEMS).setMinGlobalLimited(1).setMaxGlobalLimited(4))
       .or(abilities(INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(3))
       .or(autoAbilities()))
   
   // 建议的声明式写法:
   .casing('X', Casings.SOLID_STEEL)
       .withHatches(IMPORT_ITEMS, 1, 4)
       .withHatches(EXPORT_ITEMS, 1, 4)
       .withHatches(INPUT_ENERGY, 1, 3)
       .withAutoAbilities()
   // 最小外壳数量从结构定义自动计算
   ```

3. **自动外壳计数:**
   - 统计结构定义中 'X' 字符的总数
   - 减去最大可能的仓室数量
   - 结果 = 最小外壳需求（自动设置）

4. **自动提示信息生成:**
   ```java
   // 自动生成:
   // "至少需要14个固态钢制外壳"
   // "接受1-4个输入总线"
   // "接受1-3个能源仓"
   structureWrapper.buildTooltip(tooltip);
   ```

5. **分级通道追踪:**
   ```java
   // 当前手动方式:
   .where('C', heatingCoils())
   // ... 然后手动: matchContext.getOrPut("CoilType", ...)
   
   // 建议方式:
   .tieredCasing('C', CasingGroups.HEATING_COILS)
   // 自动追踪，通过以下方式访问:
   int coilTier = state.getTierChannel("heating_coils");
   ```

**开发体验影响:**
- 每个多方块定义减少约40%的样板代码
- 消除手动最小/最大计数错误
- 自动保持提示信息一致性
- 分级检测从命令式变为声明式

**需要修改的关键文件:**
- 新增: `ICasing.java`, `ICasingGroup.java` - 接口定义
- 新增: `CasingRegistry.java` - 集中外壳注册
- 新增: `DeclarativePatternBuilder.java` - 新构建器API（与现有共存）
- 新增: `StructureTooltipBuilder.java` - 从定义自动生成提示
- `TraceabilityPredicate.java` - 添加分级通道支持

**依赖:** 无（可独立完成，但P1的状态分离对分级追踪有帮助）

---

## 实施顺序

```
第一阶段: 基础（P1 → P0）
├── P1: MultiblockState 分离（无外部依赖）
└── P0: 事件驱动 + 区块坐标索引（需要ASM/Mixin配置）

第二阶段: 性能优化（P2 → P3）
├── P2: 异步检查（需要P1提供线程安全）
└── P3: 分片检查（需要P0提供脏标记机制）

第三阶段: 开发体验（P4）
└── P4: ICasing 声明式系统（独立，可随时开始）
```

## 对当前mod的内部影响

### 影响范围统计

| 分类 | 数量 | 说明 |
|------|------|------|
| 实现 `createStructurePattern()` 的多方块 | **37个** | gregtech核心32个 + gtqt模块5个 |
| 引用 `BlockPattern` 的文件 | **41个** | 含API层、实现层、工具类 |
| 使用 `PatternMatchContext` 的文件 | **41个** | 结构检查结果读取 |
| 调用 `isStructureFormed()`/`isFormed()` 的文件 | **56个** | 遍布UI、集成、逻辑各处 |
| 使用 `checkStructurePattern()` 的位置 | **37个** | 基本都在多方块控制器内 |

### P0: 事件驱动 — 对当前mod的影响

| 受影响的文件/类 | 改动类型 | 具体影响 |
|----------------|----------|----------|
| `MultiblockControllerBase.doStructureCheck()` | **修改** | 将定时轮询逻辑改为事件驱动回调；已成形时不再主动检查 |
| `MultiblockControllerBase.update()` | **修改** | 移除/简化 `doStructureCheck()` 的调用 |
| `MultiblockControllerBase.invalidateStructure()` | **修改** | 添加向 `MultiblockWorldData` 注销的逻辑 |
| `MultiblockControllerBase.formStructure()` | **修改** | 添加向 `MultiblockWorldData` 注册的逻辑 |
| `MetaTileEntityCleanroom` | **可能影响** | 其有自定义的结构检查节奏，需要适配事件驱动 |
| `MetaTileEntityCentralMonitor` | **可能影响** | 有自定义结构更新逻辑 |
| `MultiblockPreviewRenderer` | **无影响** | 仅读取结构信息用于渲染 |
| `MultiblockInfoRecipeWrapper` (JEI) | **无影响** | 仅用于JEI预览展示 |
| 所有37个 `createStructurePattern()` 实现 | **无影响** | 结构定义本身不变 |
| `ConfigHolder` (延迟检查配置) | **修改** | 延迟检查配置意义改变，可移除或重定义为未成形控制器的检查频率 |

**总结:** 核心改动集中在 `MultiblockControllerBase` 的检查调度逻辑。37个多方块的结构定义代码完全不需要改动。

---

### P1: MultiblockState分离 — 对当前mod的影响

| 受影响的文件/类 | 改动类型 | 具体影响 |
|----------------|----------|----------|
| `BlockPattern.java` | **重构拆分** | 拆为 `BlockPatternTemplate`（模板）+ `MultiblockState`（实例状态） |
| `FactoryBlockPattern.java` | **修改** | `build()` 返回 `BlockPatternTemplate` 而非 `BlockPattern` |
| `MultiblockControllerBase.java` | **修改** | `structurePattern` 字段类型变更；添加 `multiblockState` 字段 |
| `MultiblockControllerBase.reinitializeStructurePattern()` | **修改** | 改为仅重建状态，不重建模板 |
| `PatternMatchContext.java` | **移入MultiblockState** | 成为 `MultiblockState` 的内部状态 |
| `BlockWorldState.java` | **修改** | 可能需要从 `MultiblockState` 获取上下文 |
| 所有37个 `createStructurePattern()` 实现 | **改动极小** | 仅返回类型标注变化（可通过接口兼容保持无改动） |
| `MetaTileEntityEBF.formStructure()` 等 | **小改** | `matchContext` 获取方式从 `structurePattern.xxx` 改为 `multiblockState.xxx` |
| 所有读取 `matchContext.getOrPut("CoilType")` 的类 | **小改** | 约15处，改为从 `multiblockState` 获取 |
| `MetaTileEntityHPCA.formStructure()` | **小改** | 读取结构匹配结果的路径变化 |
| `MetaTileEntityPowerSubstation.formStructure()` | **小改** | 同上 |
| `MetaTileEntityActiveTransformer.formStructure()` | **小改** | 同上 |
| `MetaTileEntityLargeMiner` | **小改** | `structurePattern.formedRepetitionCount` → `multiblockState.getRepetitionCount()` |
| `MetaTileEntityDistillationTower` | **小改** | 同上 |
| `MultiblockPreviewRenderer` | **小改** | 从模板读取结构信息而非实例 |
| `MultiblockInfoRecipeWrapper` (JEI) | **小改** | 同上 |
| `DistillationTowerLogicHandler` | **小改** | `matchContext` 访问路径变化 |

**总结:** 约 **15-20个文件** 需要小幅改动（主要是 `matchContext` 和 `structurePattern` 的访问路径变化）。核心重构集中在3个API层文件。可通过在旧位置保留 `@Deprecated` 的代理方法来减少一次性改动量。

---

### P2: 异步检查线程 — 对当前mod的影响

| 受影响的文件/类 | 改动类型 | 具体影响 |
|----------------|----------|----------|
| `MultiblockControllerBase.doStructureCheck()` | **修改** | 未成形时改为提交异步任务而非直接检查 |
| `BlockPattern.checkPatternAt()` / `MultiblockState.checkPattern()` | **修改** | 添加锁机制；世界读取改为从 `ChunkCache` 快照 |
| `BlockWorldState.java` | **修改** | 方块状态读取支持从快照和World两种来源 |
| `TraceabilityPredicate.java` | **需审查** | 确保所有内置predicate无副作用（不写World） |
| `MultiblockControllerBase.formStructure()` | **修改** | 必须确保在主线程执行（添加线程检查） |
| `MultiblockControllerBase.invalidateStructure()` | **修改** | 同上 |
| `MetaTileEntityCleanroom` | **需注意** | 其清洁室检查可能依赖实时世界状态 |
| `MetaTileEntityLargeMiner` | **需注意** | 矿机的挖掘逻辑可能读取结构状态 |
| `RecipeMapMultiblockController.updateFormedValid()` | **无影响** | 仍在主线程tick中调用 |
| 所有 `updateFormedValid()` 实现 | **无影响** | 仍在主线程执行 |
| `SteamMultiblockRecipeLogic` | **无影响** | 配方逻辑不涉及结构检查 |

**需要特别注意的自定义TraceabilityPredicate:**

| Predicate | 是否有副作用 | 风险 |
|-----------|------------|------|
| `states(...)` | ❌ 纯读取 | 安全 |
| `abilities(...)` | ❌ 纯读取 | 安全 |
| `heatingCoils()` | ❌ 纯读取+记录类型 | 安全 |
| `autoAbilities()` | ❌ 纯读取 | 安全 |
| `air()` | ❌ 纯读取 | 安全 |
| `any()` | ❌ 纯读取 | 安全 |
| 自定义Lambda predicate (如gtqt中) | 🟡 需逐一审查 | 可能不安全 |

**总结:** 核心改动约 **5-8个API层文件**。37个多方块的业务代码基本无需改动（`updateFormedValid()` 仍在主线程）。主要风险在于自定义predicate是否有副作用。

---

### P3: 分片检查 — 对当前mod的影响

| 受影响的文件/类 | 改动类型 | 具体影响 |
|----------------|----------|----------|
| 所有现有37个多方块 | **无需改动** | 自动作为单片段处理，行为完全不变 |
| `MultiblockControllerBase.java` | **新增方法** | 添加 `createMultiPiecePattern()` 可选方法 |
| `BlockPatternTemplate` | **扩展** | 增加片段相关字段（对现有单片段无影响） |
| `MultiblockState` | **扩展** | 增加片段脏标记（对现有机器透明） |
| `MultiblockWorldData` | **扩展** | 增加片段级位置索引 |
| 未来新增的超大多方块 | **可选使用** | opt-in式API，不强制 |

**总结:** **零影响**。这是纯增量功能，所有现有代码保持不变。

---

### P4: ICasing声明式 — 对当前mod的影响

| 受影响的文件/类 | 改动类型 | 具体影响 |
|----------------|----------|----------|
| 所有现有37个多方块 | **无需改动** | 旧 `.where()` API永久保留 |
| `TraceabilityPredicate.java` | **扩展** | 添加分级通道支持（不破坏现有API） |
| `FactoryBlockPattern.java` | **扩展** | 添加 `.casing()` 等新方法（不影响现有 `.where()`） |
| `MetaTileEntityEBF` 等使用线圈的机器 | **可选迁移** | 可逐步从手动 `getOrPut("CoilType")` 迁移到声明式 |
| `MultiblockDisplayText` | **可选集成** | 可以从ICasing自动生成tooltip |
| `MultiblockControllerBase.addDisplayText()` | **可选修改** | 可以用自动tooltip替代手动文本 |

**如果选择迁移现有机器（可选，非强制）:**

| 机器类别 | 数量 | 迁移难度 |
|----------|------|----------|
| 简单多方块（无分级外壳）| 25个 | 极简单：只需将 `.where()` 替换为 `.casing()` |
| 带线圈分级的多方块 | 5个 | 简单：EBF/多合金炉/多熔炉/焦化炉/裂化装置 |
| 带其他分级外壳的多方块 | 4个 | 简单：融合堆/电力分站/HPCA/数据银行 |
| 有复杂自定义逻辑的多方块 | 3个 | 中等：清洁室/中央监视器/矿机 |

**总结:** **零破坏性影响**。新API与旧API共存。迁移是完全可选的，可以在数月内逐步完成。

---

### 改动量总估算

| 阶段 | 任务 | 新增文件 | 修改文件 | 改动行数（预估） |
|------|------|----------|----------|-----------------|
| Phase 1 | P1 (状态分离) | 2个 | 15-20个 | ~800行 |
| Phase 1 | P0 (事件驱动) | 2-3个 | 3-5个 | ~400行 |
| Phase 2 | P2 (异步检查) | 1-2个 | 5-8个 | ~500行 |
| Phase 2 | P3 (分片) | 2-3个 | 3-4个 | ~600行 |
| Phase 3 | P4 (声明式) | 4-5个 | 2-3个（扩展） | ~1000行 |
| **合计** | | **11-15个新文件** | **28-40个修改** | **~3300行** |

### 向后兼容策略

为确保迁移平滑，建议在每个阶段采用以下策略：

| 策略 | 适用任务 | 说明 |
|------|----------|------|
| **接口兼容** | P1 | `createStructurePattern()` 返回类型改为共同接口，旧代码无需改 |
| **废弃代理** | P1 | 在旧位置保留 `@Deprecated` 的getter代理到新位置 |
| **可选开关** | P0, P2 | 提供配置开关回退到旧的定时轮询行为 |
| **纯增量** | P3, P4 | 完全新增API，旧代码路径完整保留 |
| **渐进迁移** | P4 | 新老API共存，无迁移deadline |

## 风险评估

| 任务 | 风险 | 影响范围 | 缓解措施 |
|------|------|----------|----------|
| P0 (Forge事件) | 极低：标准API使用 | 无其他mod受影响 | 无需特殊处理 |
| P0 (Mixin补充) | 低：`@Inject(RETURN)`注入 | 理论上Cubic Chunks等深度改World的mod可能需要测试 | 作为可选补充，可通过配置开关禁用 |
| P1 (状态拆分) | 中：API破坏性变更 | 直接使用 `BlockPattern` 的附属模组 | 提供兼容层；废弃旧API；附属mod通常通过 `createStructurePattern()` 间接使用 |
| P2 (异步) | 中高：线程安全问题 | 所有结构检查逻辑 | 使用ChunkCache快照；充分单元测试；提供同步回退开关 |
| P3 (分片) | 低：纯新增功能 | 仅使用分片API的新多方块 | 保持单片段为默认；分片为opt-in；旧API不受影响 |
| P4 (ICasing) | 低：纯新增功能 | 新编写的多方块可选使用 | 新API与旧API共存；渐进式迁移；旧方式永久保留 |

### 各任务对外部mod的详细影响

#### P0: 事件驱动 — 对外部mod影响

| 影响对象 | 影响描述 | 严重程度 |
|----------|----------|----------|
| 使用Forge BlockEvent的mod | 无影响，我们只是新增一个监听者 | ❌ 无 |
| 使用Mixin修改World的mod (如Sponge) | `@Inject(RETURN)`不修改方法逻辑，与其他注入和平共存 | ❌ 无 |
| 重写World类的mod (如Cubic Chunks) | 方法签名可能变化，Mixin目标可能找不到 | 🟡 需测试 |
| GT附属mod | 无影响，不改变任何现有API | ❌ 无 |

#### P1: 状态分离 — 对外部mod影响

| 影响对象 | 影响描述 | 严重程度 |
|----------|----------|----------|
| 直接new BlockPattern的代码 | 编译错误，需改用 `BlockPatternTemplate` | 🔴 破坏性 |
| 调用 `getStructurePattern()` 的代码 | 返回类型变化 | 🟡 需适配 |
| 调用 `createStructurePattern()` 的子类 | 返回类型可改为接口兼容 | 🟡 低影响 |
| 使用 `PatternMatchContext` 的代码 | 移至 `MultiblockState` 内，需通过新getter访问 | 🟡 需适配 |
| 使用 `blockPattern.checkPatternAt()` 的代码 | 改为 `state.checkPattern()` | 🟡 需适配 |
| 普通GT附属（只override `createStructurePattern`） | 仅返回类型变化，改动极小 | 🟢 低影响 |

#### P2: 异步检查 — 对外部mod影响

| 影响对象 | 影响描述 | 严重程度 |
|----------|----------|----------|
| 在 `checkPatternAt` 中读取World状态的代码 | 可能在非主线程执行，需确保线程安全 | 🟡 需注意 |
| 在结构检查回调中修改World的代码 | 必须确保在主线程执行 | 🔴 潜在问题 |
| 依赖 `isFormed()` 立即返回最新状态的代码 | 异步检查有250ms延迟 | 🟡 需注意 |
| GT附属的自定义TraceabilityPredicate | 如果内部有副作用（写World），需要改为线程安全 | 🟡 需审查 |
| 普通GT附属（只关心最终结果） | 无影响，回调仍在主线程 | ❌ 无 |

#### P3: 分片检查 — 对外部mod影响

| 影响对象 | 影响描述 | 严重程度 |
|----------|----------|----------|
| 现有多方块（使用旧API） | 完全不受影响，自动作为单片段处理 | ❌ 无 |
| 新多方块（使用分片API） | 纯增量功能 | ❌ 无 |
| GT附属的超大多方块 | 可选使用分片获得性能提升 | ❌ 无 |

#### P4: ICasing声明式 — 对外部mod影响

| 影响对象 | 影响描述 | 严重程度 |
|----------|----------|----------|
| 现有多方块（使用旧 `.where()` API） | 完全不受影响，旧API永久保留 | ❌ 无 |
| GT附属的新多方块 | 可选使用新声明式API | ❌ 无 |
| 自定义TraceabilityPredicate | 不受影响 | ❌ 无 |

## 参考资料

- GTM源码: `com.gregtechceu.gtceu.api.pattern.BlockPattern` / `MultiblockState` / `MultiblockWorldSavedData`
- GTM Mixin: `LevelMixin.gtceu$updateChunkMultiblocks()`
- GT5源码: `com.gtnewhorizon.structurelib` / `gregtech.api.structure.StructureWrapper`
- GT5分片: `MTEEnhancedMultiBlockBase.checkPiece()`
- 当前项目: `gregtech.api.pattern.BlockPattern` / `FactoryBlockPattern` / `TraceabilityPredicate`
