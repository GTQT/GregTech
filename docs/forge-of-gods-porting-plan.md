# 诸神之煅炉（Forge of the Gods）移植计划

## 最近更新: 2026-05-06

**当前状态：绝大多数文件已移植完成，处于集成调试阶段。**

---

## 系统总览

诸神之煅炉是 GT5 TecTech 中最复杂的多方块结构，涉及 **58 个源文件**，是一个完整的子系统。

源码位置：`GT5-Unofficial-master/src/main/java/tectech/thing/metaTileEntity/multi/godforge/`

### 核心特性
- 主结构 127×29×127（约 570K 方块位置）
- 3 个可选环（根据升级解锁）
- 分片结构检查（main/shaft/ring1/ring2/ring3）
- 30 个升级节点的升级树系统
- 4 种里程碑追踪
- 4 种子模块类型（Smelting/Molten/Plasma/Exotic），最多 16 个
- 无线能量网络
- 可自定义恒星颜色的渲染系统
- 30+ 个 GUI 面板

---

## 当前移植进度总览

### ✅ 已完成的模块

| 阶段 | 模块 | 状态 | 文件位置 |
|------|------|------|---------|
| 1 | 方块注册 | ✅ 完成 | `BlockGodforgeCasing.java` (9种方块状态), `BlockGodforgeGlass.java` (1种) |
| 1 | 贴图资源 | ✅ 完成 | `textures/blocks/casings/godforge/` (12个贴图+mcmeta) |
| 1 | blockstates | ✅ 完成 | `blockstates/godforge_casing.json`, `godforge_glass.json` |
| 1 | 本地化 | ✅ 完成 | `en_us.lang` 和 `zh_cn.lang` 均已添加 |
| 2 | 结构字符串 | ✅ 完成 | `ForgeOfGodsStructureString.java` + 4个txt资源文件 |
| 3 | 数据模型 | ✅ 完成 | `ForgeOfGodsData.java` (含NBT序列化) |
| 3 | 数学计算 | ✅ 完成 | `GodforgeMath.java` |
| 3 | 颜色系统 | ✅ 完成 | `ForgeOfGodsStarColor.java`, `StarColorSetting.java`, `StarColorStorage.java` |
| 4 | 升级系统 | ✅ 完成 | `ForgeOfGodsUpgrade.java` (30节点enum), `UpgradeStorage.java` |
| 4 | GUI数据 | ✅ 完成 | `data/` 目录: `ColorData`, `Milestones`, `StarColors`, `UpgradeColor`, `UpgradeType`, `Fuels`, `Formatters`, `Statistics` |
| 5 | 无线能量网络 | ✅ 已存在 | `WirelessNetworkManager.java` (旧版GT5风格全局Map) + `gtqt.api.util.wireless` (新版PSS+NetworkNode系统) |
| 6 | 控制器 | ⚠️ 骨架完成 | `MetaTileEntityForgeOfGods.java` (177行, 结构+渲染+NBT已实现, **缺少核心运行逻辑**) |
| 6 | 模块基类 | ✅ 完成 | `MTEBaseModule.java` (357行, 含NBT/参数/RecipeLogic) |
| 6 | 4个子模块 | ✅ 完成 | `MTESmeltingModule`, `MTEMoltenModule`, `MTEPlasmaModule`, `MTEExoticModule` |
| 6 | 模块接口 | ✅ 完成 | `IGodforgeModule.java` (connect/disconnect/isConnected) |
| 6 | 模块RecipeLogic | ✅ 完成 | `GodforgeModuleRecipeLogic.java` (无线能量消耗已集成WirelessNetworkManager) |
| 6 | MTE注册 | ✅ 完成 | `MetaTileEntities.java` (ID 2100-2104) |
| 7 | 主GUI | ✅ 完成 | `MTEForgeOfGodsGui.java` + `GodforgeBaseGui.java` + `ForgeOfGodsGuiUtil.java` |
| 7 | 模块GUI | ✅ 完成 | `MTEBaseModuleGui`, `MTESmeltingModuleGui`, `MTEMoltenModuleGui`, `MTEPlasmaModuleGui`, `MTEExoticModuleGui` |
| 7 | 面板(18个) | ✅ 完成 | `panel/` 目录全部18个面板 |
| 7 | 同步系统 | ✅ 完成 | `sync/` 目录: `SyncHypervisor`, `SyncActions`, `SyncValue`, `SyncValues`, `Modules`, `Panels` |
| 7 | 辅助Widget | ✅ 完成 | `SelectButton`, `SlotLikeButtonWidget`, `LinkedBoolValue`, `RotatedDrawable` |
| 8 | 恒星渲染器 | ✅ 完成 | `GodforgeStarRenderer.java` (着色器+VBO) |
| 8 | 渲染TE | ✅ 完成 | `GodforgeRenderTileEntity.java` |
| 8 | 渲染工具 | ✅ 完成 | `util/SphereVBOCache`, `StructureVBO`, `StructureBlockAccess`, `TextureUpdateRequester` |
| 8 | 渲染贴图 | ✅ 完成 | `textures/godforge/StarLayer0-2.png`, `spaceLayer.png` |
| 9 | RecipeMap | ✅ 完成 | `GodforgeRecipeMaps.java` (5个Map: Smelting/Plasma/Exotic/Molten/UpgradeCost) |
| 9 | 配方注册 | ⚠️ 部分完成 | `GodforgeRecipeLoader.java` (等离子体配方+升级花费已注册, 熔炼/熔融/奇异待完善) |

---

### ⚠️ 待完成的关键任务

| 优先级 | 任务 | 描述 | 估算复杂度 |
|--------|------|------|-----------|
| **P0** | 控制器核心运行逻辑 | `MetaTileEntityForgeOfGods.updateFormedValid()` 当前为空 TODO，需实现：燃料消耗、电池充放、模块发现/连接/参数下发、里程碑追踪、升级效果应用 | ⭐⭐⭐ 高 |
| **P0** | 分片结构检查集成 | 当前控制器使用单一 `BlockPattern`（beam_shaft + first_ring 合并为一个模式），**未使用 `MultiPiecePattern`**，因此第二环/第三环无条件检查支持 | ⭐⭐⭐ 高 |
| **P1** | 模块发现机制 | 控制器如何发现并管理已连接模块（最多16个）。当前有 `godforgeModules()` predicate 但缺少运行时发现逻辑 | ⭐⭐ 中 |
| **P1** | 燃料系统逻辑 | 燃料消耗/补充循环、启动燃料需求、燃料类型切换 | ⭐⭐ 中 |
| **P1** | 电池系统逻辑 | 内部电池充放电循环、电池配置UI交互 | ⭐ 低 |
| **P1** | 里程碑进度系统 | 4种里程碑的进度追踪与奖励应用 | ⭐⭐ 中 |
| **P2** | 升级效果应用 | 30个升级节点的实际效果执行（目前enum定义完成，但效果未连接到控制器） | ⭐⭐ 中 |
| **P2** | 恒星渲染集成 | 控制器的 `updateRenderer()`/`destroyRenderer()` 当前为空方法，需与 `GodforgeStarRenderer` 连接 | ⭐⭐ 中 |
| **P2** | 配方完善 | 熔炼模块(电弧炉/炉模式)、熔融模块(EBF)、奇异模块(暗物质)配方缺失 | ⭐ 低 |
| **P3** | 自动构建支持 | 超大结构的玩家辅助建造（JEI预览 + 创造模式自动放置） | ⭐⭐ 中 |
| **P3** | 编译验证 | 确保所有文件编译通过，无运行时崩溃 | ⭐ 低 |

---

## 底层依赖分析（更新版）

| GT5 依赖 | 当前项目对应物 | 当前状态 |
|----------|--------------|---------|
| `com.gtnewhorizon.structurelib` (checkPiece/buildPiece) | `MultiPiecePattern` (P3) | ✅ API已就绪，⚠️ **未被godforge使用** |
| `TTMultiblockBase` (TecTech 基类) | `MultiblockWithDisplayBase` | ✅ 控制器已继承此类 |
| `WirelessNetworkManager` (无线能量网络) | `gregtech.common.misc.WirelessNetworkManager` | ✅ 已存在且已被 `GodforgeModuleRecipeLogic` 使用 |
| MUI2 (`com.cleanroommc.modularui`) | 当前 MUI (同一库 MC 1.12版) | ✅ GUI系统完整使用MUI2 API |
| `GodforgeCasings` (TecTech 方块) | `BlockGodforgeCasing` + `BlockGodforgeGlass` | ✅ 已注册(9+1=10种方块状态) |
| `IStructureDefinition` (structurelib) | `FactoryBlockPattern` / `BlockPatternTemplate` | ✅ 结构已转换为FactoryBlockPattern格式 |
| GT5 RecipeMap 系统 | `GodforgeRecipeMaps` | ✅ 5个RecipeMap已注册 |

---

## 已完成文件清单（82个文件）

### 底层方块 (2文件)
- `gregtech/common/blocks/BlockGodforgeCasing.java`
- `gregtech/common/blocks/BlockGodforgeGlass.java`

### 控制器+模块 (8文件)
- `gregtech/common/metatileentities/multi/electric/godforge/MetaTileEntityForgeOfGods.java`
- `gregtech/common/metatileentities/multi/electric/godforge/ForgeOfGodsStructureString.java`
- `gregtech/common/metatileentities/multi/electric/godforge/module/MTEBaseModule.java`
- `gregtech/common/metatileentities/multi/electric/godforge/module/MTESmeltingModule.java`
- `gregtech/common/metatileentities/multi/electric/godforge/module/MTEMoltenModule.java`
- `gregtech/common/metatileentities/multi/electric/godforge/module/MTEPlasmaModule.java`
- `gregtech/common/metatileentities/multi/electric/godforge/module/MTEExoticModule.java`
- `gregtech/common/metatileentities/multi/electric/godforge/module/GodforgeModuleRecipeLogic.java`

### 数据/工具 (5文件)
- `gregtech/common/metatileentities/multi/electric/godforge/util/ForgeOfGodsData.java`
- `gregtech/common/metatileentities/multi/electric/godforge/util/GodforgeMath.java`
- `gregtech/common/metatileentities/multi/electric/godforge/color/ForgeOfGodsStarColor.java`
- `gregtech/common/metatileentities/multi/electric/godforge/color/StarColorSetting.java`
- `gregtech/common/metatileentities/multi/electric/godforge/color/StarColorStorage.java`

### 升级系统 (2文件)
- `gregtech/common/metatileentities/multi/electric/godforge/upgrade/ForgeOfGodsUpgrade.java`
- `gregtech/common/metatileentities/multi/electric/godforge/upgrade/UpgradeStorage.java`

### GUI数据 (8文件)
- `gregtech/common/metatileentities/multi/electric/godforge/data/ColorData.java`
- `gregtech/common/metatileentities/multi/electric/godforge/data/Milestones.java`
- `gregtech/common/metatileentities/multi/electric/godforge/data/StarColors.java`
- `gregtech/common/metatileentities/multi/electric/godforge/data/UpgradeColor.java`
- `gregtech/common/metatileentities/multi/electric/godforge/data/UpgradeType.java`
- `gregtech/common/metatileentities/multi/electric/godforge/data/Fuels.java`
- `gregtech/common/metatileentities/multi/electric/godforge/data/Formatters.java`
- `gregtech/common/metatileentities/multi/electric/godforge/data/Statistics.java`

### GUI系统 (36文件)
- `gregtech/common/mui/multiblock/godforge/MTEForgeOfGodsGui.java`
- `gregtech/common/mui/multiblock/godforge/MTEBaseModuleGui.java`
- `gregtech/common/mui/multiblock/godforge/MTESmeltingModuleGui.java`
- `gregtech/common/mui/multiblock/godforge/MTEMoltenModuleGui.java`
- `gregtech/common/mui/multiblock/godforge/MTEPlasmaModuleGui.java`
- `gregtech/common/mui/multiblock/godforge/MTEExoticModuleGui.java`
- `gregtech/common/mui/multiblock/godforge/GodforgeBaseGui.java`
- `gregtech/common/mui/multiblock/godforge/ForgeOfGodsGuiUtil.java`
- `gregtech/common/mui/multiblock/godforge/SelectButton.java`
- `gregtech/common/mui/multiblock/godforge/SlotLikeButtonWidget.java`
- `gregtech/common/mui/multiblock/godforge/LinkedBoolValue.java`
- `gregtech/common/mui/multiblock/godforge/RotatedDrawable.java`
- `gregtech/common/mui/multiblock/godforge/panel/BatteryConfigPanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/CustomStarColorPanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/CustomStarColorSelector.java`
- `gregtech/common/mui/multiblock/godforge/panel/ExoticInputsListPanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/ExoticPossibleInputsListPanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/FuelConfigPanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/GeneralInfoPanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/IndividualMilestonePanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/IndividualUpgradePanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/ManualInsertionPanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/MilestonePanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/PlasmaDebugPanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/SpecialThanksPanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/StarColorImportPanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/StarCosmeticsPanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/StatisticsPanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/UpgradeTreePanel.java`
- `gregtech/common/mui/multiblock/godforge/panel/VoltageConfigPanel.java`
- `gregtech/common/mui/multiblock/godforge/sync/Modules.java`
- `gregtech/common/mui/multiblock/godforge/sync/Panels.java`
- `gregtech/common/mui/multiblock/godforge/sync/SyncActions.java`
- `gregtech/common/mui/multiblock/godforge/sync/SyncHypervisor.java`
- `gregtech/common/mui/multiblock/godforge/sync/SyncValue.java`
- `gregtech/common/mui/multiblock/godforge/sync/SyncValues.java`

### 渲染系统 (6文件)
- `gregtech/client/renderer/godforge/GodforgeStarRenderer.java`
- `gregtech/client/renderer/godforge/GodforgeRenderTileEntity.java`
- `gregtech/client/renderer/godforge/util/SphereVBOCache.java`
- `gregtech/client/renderer/godforge/util/StructureVBO.java`
- `gregtech/client/renderer/godforge/util/StructureBlockAccess.java`
- `gregtech/client/renderer/godforge/util/TextureUpdateRequester.java`

### 配方 (2文件)
- `gregtech/api/recipes/GodforgeRecipeMaps.java`
- `gregtech/loaders/recipe/GodforgeRecipeLoader.java`

### 接口 (1文件)
- `gregtech/api/metatileentity/multiblock/IGodforgeModule.java`

### 资源文件 (22文件)
- 结构定义: `assets/gregtech/godforge/structures/beam_shaft.txt`, `first_ring.txt`, `second_ring.txt`, `third_ring.txt`
- 星体贴图: `textures/godforge/StarLayer0.png`, `StarLayer1.png`, `StarLayer2.png`, `spaceLayer.png`
- 方块贴图: `textures/blocks/casings/godforge/` 下12个文件
- Blockstates: `blockstates/godforge_casing.json`, `godforge_glass.json`

---

## 下一步实施计划（优先级排序）

### Phase A: 控制器核心逻辑补全 [P0 - 最高优先级]

**目标：** 让 `MetaTileEntityForgeOfGods` 从"空壳"变为可运行的主控制器。

#### A1. 分片结构检查重构
**当前问题：** 控制器的 `createStructurePattern()` 将 beam_shaft + first_ring 合并为单一 `BlockPattern`，无法支持条件性的第二环/第三环。

**依赖：** 需要先完成 `docs/multiblock-refactor.md` 中 M7 章节的 API 改动。

**详细技术方案见：** `docs/multiblock-refactor.md` → M7: 分片式结构检查试点

**概要：**
- 使用 `OffsetMode.STRUCTURE_SPACE` 支持方向感知的 piece offset
- 通过 `buildTemplate(int[] centerOffset)` 为 ring 指定外部 center
- 静态缓存 template 实例（DCL 模式）
- 控制器 `createMultiPiecePattern()` 返回 4 个 piece（beam_shaft + 3 rings）
- 条件片段通过 `conditionalPiece()` 由升级状态控制

#### A2. updateFormedValid() 实现
**需要实现的逻辑（参照GT5 `MTEForgeOfGods.onPostTick()`）：**

1. **燃料消耗循环** - 每tick检查燃料是否充足，消耗stellar fuel
2. **电池充放电** - 内部电池的充电/放电逻辑
3. **模块参数下发** - 定期计算并下发参数到所有已连接模块：
   - 热量(heat)、超频热量(overclockHeat)
   - 并行数(calculatedMaxParallel)、处理速度(processingSpeedBonus)
   - 能量折扣(energyDiscount)、处理电压(processingVoltage)
   - 各种升级标志
4. **里程碑追踪** - 根据模块汇报的统计数据更新4种里程碑进度
5. **引力子碎片生成** - 满足条件时生成引力子碎片
6. **渲染器状态更新** - 恒星渲染器的开关控制

#### A3. 模块发现与连接
**当前机制：** 结构模式中 `'J'` 字符匹配模块 MTE，结构成形后可通过 `getAbilities()` 获取。

**需实现：**
- 在 `formStructure()` 中通过 `MultiblockAbility` 或自定义 predicate 收集所有已连接模块
- 调用每个模块的 `connect()` 方法
- 在 `invalidateStructure()` 中调用 `disconnect()`
- 定期将主控制器计算的参数同步到各模块

---

### Phase B: 系统集成 [P1]

#### B1. 渲染器连接
- 实现 `updateRenderer()` / `destroyRenderer()` 与 `GodforgeRenderTileEntity` + `GodforgeStarRenderer` 的连接
- 控制恒星出现/消失的时机（结构成形且渲染激活）
- 颜色设置同步到渲染器

#### B2. 升级效果连接
- 在 `updateFormedValid()` 中读取 `UpgradeStorage` 状态
- 根据激活的升级修改模块参数（通过 `GodforgeMath` 计算）
- 升级解锁环结构时触发 `reinitializeStructurePattern()`

#### B3. GUI完整集成测试
- 确保 `SyncHypervisor` 正确同步所有面板数据
- 验证升级树面板的节点点击/解锁逻辑
- 验证燃料/电池配置面板的实时反馈

---

### Phase C: 配方完善 [P2]

#### C1. 熔炼模块配方
- 电弧炉配方映射（使用标准RecipeMap的配方复制或映射）
- 炉模式（furnace mode）的实现

#### C2. 熔融模块配方
- 高温高炉配方映射
- 热量相关的配方筛选

#### C3. 奇异模块配方
- 奇异物质配方
- 暗物质（Magmatter）配方

---

### Phase D: 测试与优化 [P3]

#### D1. 编译与基本运行验证
- 确保全部82+文件编译通过
- 游戏内放置控制器不崩溃
- 结构成形检查基本工作

#### D2. 性能优化
- 验证 `MultiPiecePattern` 的分片脏标记在570K方块场景下性能表现
- 恒星渲染器在低端GPU上的帧率

#### D3. JEI集成
- 超大结构的JEI预览（可能需要简化展示）
- 各模块RecipeMap的JEI注册

---


## 架构决策记录

### 决策1: 无线能量网络方案
**选择：** 使用已存在的 `WirelessNetworkManager`（GT5风格全局Map）
**原因：** `GodforgeModuleRecipeLogic` 已经使用此API，无需额外开发。
**备注：** 项目中同时存在新版PSS-based网络系统(`gtqt.api.util.wireless`)，二者目前并存。Godforge使用旧版全局Map系统。

### 决策2: 结构检查策略
**选择：** 控制器使用 `FactoryBlockPattern`（beam_shaft + first_ring合并），暂未使用 `MultiPiecePattern`
**问题：** 这意味着第二环/第三环目前不参与结构检查
**需要决策：** 是否需要在Phase A中将结构检查迁移到 `MultiPiecePattern`？或者简化为仅检查第一环？

### 决策3: 模块作为独立多方块
**选择：** 每个模块(Smelting/Molten/Plasma/Exotic)是独立的 `RecipeMapMultiblockController`
**好处：** 模块有自己的结构检查、配方逻辑、GUI
**注意：** 主控制器通过结构模式中的 predicate 发现模块，但模块同时也是独立的多方块

---

## 关键风险（更新版）

### 1. 分片结构集成 — 最高风险 🔴
当前控制器**没有使用**已就绪的 `MultiPiecePattern`。如果不迁移：
- 第二环/第三环永远无法工作
- 570K方块的检查性能可能有问题
迁移的话需要重写控制器的结构检查流程，这是最复杂的架构改动。

### 2. 控制器运行逻辑缺失 — 高风险 🔴
`updateFormedValid()` 当前为空 TODO。这是整个系统的"心脏"：
- 不实现此方法 = 结构成形但什么都不做
- 涉及燃料/电池/模块管理/里程碑/渲染等所有子系统的协调

### 3. 模块发现机制未实现 — 中风险 🟡
主控制器如何在运行时发现和管理最多16个子模块？
结构检查阶段的 predicate 匹配不等于运行时的模块引用管理。

### 4. 渲染器集成 — 中风险 🟡
渲染代码已完成但未连接到控制器。需要：
- 创建/销毁 `GodforgeRenderTileEntity` 的时机控制
- 恒星颜色/大小参数的同步

### 5. 编译状态未知 — 低风险 🟢
82个文件已存在但未验证是否能编译通过。可能存在：
- import 路径问题
- API不匹配
- 方法签名变更

---

## 工作量预估（更新版）

| 任务 | 新增/修改行数估算 | 复杂度 | 说明 |
|------|-----------------|--------|------|
| A1. MultiPiecePattern集成 | ~150行修改 | ⭐⭐⭐ 高 | 重写结构检查流程 |
| A2. updateFormedValid()实现 | ~200行新增 | ⭐⭐⭐ 高 | 核心运行逻辑 |
| A3. 模块发现连接 | ~80行新增 | ⭐⭐ 中 | formStructure/invalidateStructure |
| B1. 渲染器连接 | ~50行修改 | ⭐⭐ 中 | 连接已有代码 |
| B2. 升级效果连接 | ~100行新增 | ⭐⭐ 中 | 读取并应用升级 |
| B3. GUI集成测试 | ~20行调整 | ⭐ 低 | 可能需要修bug |
| C1-C3. 配方完善 | ~300行新增 | ⭐ 低 | 标准配方注册 |
| D1. 编译验证+修复 | 未知 | ⭐⭐ 中 | 可能有若干编译错误 |
| **总计（剩余工作）** | **~900行** | — | 相比已完成的数千行代码量，剩余约10% |

---

## 建议执行顺序

```
D1: 编译验证（先确保现有代码能编译）
     ↓
A3: 模块发现连接（formStructure中收集模块引用）
     ↓
A2: updateFormedValid()（实现核心运行循环）
     ↓
A1: MultiPiecePattern集成（支持条件环）
     ↓
B1: 渲染器连接
     ↓
B2: 升级效果连接
     ↓
C1-C3: 配方完善
     ↓
B3+D2+D3: 测试优化
```

---

## 总结

移植进度约 **90%**（按文件数计），但剩余 **10% 是最关键的集成逻辑**。
所有"零件"已到位，现在需要把它们"组装"起来——即实现控制器的核心运行逻辑。
最紧迫的任务是 **D1(编译验证)** 和 **A2(updateFormedValid实现)**。

部分测试结果：
1.与gt5GUI相差过大，大部分按键不可用，而且在放入了恒星燃料后且机器内部电池在增加的情况下并不会渲染
2.模块的模型还是用的占位符
3.模块搭建又是反的
