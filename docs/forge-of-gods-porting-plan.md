# 诸神之煅炉（Forge of the Gods）移植计划

## 最近更新: 2026-05-07

**当前状态：核心逻辑已实现，处于功能验证和Bug修复阶段。**

---

## 系统总览

诸神之煅炉是 GT5 TecTech 中最复杂的多方块结构，涉及 **85+ 个源文件**，是一个完整的子系统。

源码位置：`GT5-Unofficial-master/src/main/java/tectech/thing/metaTileEntity/multi/godforge/`

### 核心特性
- 主结构 127×29×127（约 570K 方块位置）
- 3 个可选环（根据升级解锁）
- 分片结构检查（beam_shaft + first_ring + second_ring + third_ring）
- 30 个升级节点的升级树系统
- 4 种里程碑追踪
- 4 种子模块类型（Smelting/Molten/Plasma/Exotic），最多 16 个
- 无线能量网络（通过 WirelessEnergyService）
- 可自定义恒星颜色的渲染系统（着色器+VBO）
- 30+ 个 GUI 面板（MUI2）

---

## 当前移植进度总览

### ✅ 已完成并验证编译通过

| 层级 | 模块 | 状态 | 说明 |
|------|------|------|------|
| **底层方块** | BlockGodforgeCasing, BlockGodforgeGlass, BlockGodforgeRender | ✅ | 9+1+1=11种方块状态，TESR已绑定 |
| **贴图资源** | 方块贴图、星体贴图、着色器 | ✅ | 12个贴图+mcmeta + 4星体贴图 + 6着色器文件 |
| **结构定义** | ForgeOfGodsStructureString + 4个txt资源 | ✅ | beam_shaft/first_ring/second_ring/third_ring |
| **数据模型** | ForgeOfGodsData（含NBT序列化） | ✅ | writeToNBT/readFromNBT/writeRenderNBT |
| **数学计算** | GodforgeMath | ✅ | 燃料计算、模块参数、里程碑、防作弊 |
| **颜色系统** | ForgeOfGodsStarColor, StarColorSetting, StarColorStorage | ✅ | NBT持久化+渲染颜色插值 |
| **升级系统** | ForgeOfGodsUpgrade(30节点enum), UpgradeStorage | ✅ | 完整升级树拓扑+前置依赖+解锁逻辑 |
| **控制器** | MetaTileEntityForgeOfGods | ✅ | **核心运行逻辑已完整实现** |
| **模块基类** | MTEBaseModule + GodforgeModuleRecipeLogic | ✅ | 无线能量消耗+参数体系+NBT |
| **子模块×4** | Smelting/Molten/Plasma/Exotic Module | ✅ | 各模块配方逻辑+动态配方生成(Exotic) |
| **GUI主系统** | MTEForgeOfGodsGui + GodforgeBaseGui + 18个面板 | ✅ | panelSupplier已连接 |
| **GUI同步** | SyncHypervisor, Panels, Modules, SyncActions | ✅ | 子面板打开机制已修复 |
| **渲染系统** | GodforgeStarRenderer(TESR) + GodforgeRenderTileEntity | ✅ | 星体着色器+环VBO+颜色循环 |
| **RecipeMap** | GodforgeRecipeMaps(5个Map) | ✅ | Smelting/Plasma/Exotic/Molten/UpgradeCost |
| **配方加载** | GodforgeRecipeLoader | ✅ | 等离子体+熔融+升级花费已注册 |
| **MTE注册** | MetaTileEntities.java (ID 2100-2104) | ✅ | 控制器+4模块 |
| **接口** | IGodforgeModule | ✅ | connect/disconnect/isConnected |

---

### ⚠️ 已发现的Bug与待修复项

| 优先级 | 问题 | 状态 | 详情 |
|--------|------|------|------|
| **P0** | GUI按钮不可用 | ✅ **已修复** | `Panels`枚举缺少`panelSupplier`连接，已为所有子面板添加对应的`openPanel`方法引用 |
| **P0** | 模块贴图为占位符 | ✅ **已修复** | `MTEBaseModule.getBaseTexture()` 从 `SOLID_STEEL_CASING` 改为 `GODFORGE_INNER_CASING` |
| **P0** | 模块结构方向反转 | ✅ **已修复** | 反转了 aisle 排列顺序，G(Siphon)在FRONT，控制器面板在BACK |
| **P1** | 恒星渲染不触发 | 🔍 **排查中** | 已添加`[FOG]`调试日志，等待测试结果确认是区块加载问题还是其他原因 |
| **P2** | `writeToNBT` 不保存 `isRenderActive` | ⚠️ 待修复 | `writeRenderNBT()`是死代码从未被调用，重载后依赖`ensureRendererState()`重建 |
| **P2** | 配方不完整 | ⚠️ 待完善 | 熔炼模块直接使用BLAST_RECIPES/ARC_FURNACE_RECIPES，molten配方生成逻辑待验证 |
| **P3** | ForgeChunkManager未实现 | ⚠️ 待定 | 渲染器在控制器后方122格，需要区块加载保证 |

---

## 控制器核心逻辑（已实现）

`MetaTileEntityForgeOfGods.updateFormedValid()` 已完整实现以下功能：

### 运行时循环（每100 tick = 5秒）
```
1. absorbFuelOrShards()     — 从输入总线吸收恒星燃料/引力子碎片
2. drainFuel()              — 从流体仓抽取燃料流体，维持电池
3. ensureRendererState()    — 检查并维护恒星渲染器状态
4. 里程碑计算               — determineCompositionMilestoneLevel() + determineMilestoneProgress()
5. checkInversionStatus()   — 反转状态检查
6. determineGravitonShardAmount() — 引力子碎片产量计算
7. ejectGravitonShards()    — 引力子碎片输出（需END升级）
8. 模块参数下发             — GodforgeMath 计算后下发到每个模块
9. 环状态同步               — 检测环数量变化，更新渲染器和结构
```

### 结构系统（已实现）
- **初始成形：** `createStructurePattern()` — beam_shaft + first_ring 合并为单一 BlockPattern
- **分片验证：** `createMultiPiecePattern()` — 4个piece（beam_shaft, first_ring, second_ring, third_ring）
- **条件片段：** second_ring 由 CD 升级控制，third_ring 由 END 升级控制
- **模块发现：** `discoverModules()` 在 `formStructure()` 中扫描所有 `MTEBaseModule` part

### 渲染系统（已实现）
- `createRenderer()` — 在控制器后方122格放置 `BlockGodforgeRender`（含TESR）
- `destroyRenderer()` — 移除渲染方块
- `updateRenderer()` — 同步星体大小/旋转速度/颜色/环数到 `GodforgeRenderTileEntity`
- `ensureRendererState()` — 每5秒检查渲染器一致性，自动重建

---

## 底层依赖对照

| GT5 依赖 | 当前项目对应物 | 状态 |
|----------|--------------|------|
| `com.gtnewhorizon.structurelib` | `MultiPiecePattern` + `LazyTemplate` | ✅ 已使用 |
| `TTMultiblockBase` | `MultiblockWithDisplayBase` | ✅ |
| `WirelessNetworkManager` | `WirelessEnergyService` + `WirelessEnergyServiceImpl` | ✅ |
| MUI2 `com.cleanroommc.modularui` | 同一库 MC 1.12版 | ✅ |
| `GodforgeCasings` (TecTech) | `BlockGodforgeCasing` + `BlockGodforgeGlass` | ✅ |
| GT5 RecipeMap | `GodforgeRecipeMaps` (5个Map) | ✅ |
| `ForgeChunkManager` (区块加载) | **未实现** | ⚠️ |

---

## 文件清单（85+文件）

### 底层方块 (3文件)
- `gregtech/common/blocks/BlockGodforgeCasing.java` — 9种方块状态
- `gregtech/common/blocks/BlockGodforgeGlass.java` — 1种
- `gregtech/common/blocks/BlockGodforgeRender.java` — 渲染器宿主方块

### 控制器+模块 (9文件)
- `gregtech/common/metatileentities/multi/electric/godforge/MetaTileEntityForgeOfGods.java` — 主控制器(876行)
- `gregtech/common/metatileentities/multi/electric/godforge/GodforgeUIFactory.java` — GUI工厂
- `gregtech/common/metatileentities/multi/electric/godforge/ForgeOfGodsStructureString.java` — 结构字符串加载
- `gregtech/common/metatileentities/multi/electric/godforge/module/MTEBaseModule.java` — 模块基类(364行)
- `gregtech/common/metatileentities/multi/electric/godforge/module/MTESmeltingModule.java`
- `gregtech/common/metatileentities/multi/electric/godforge/module/MTEMoltenModule.java`
- `gregtech/common/metatileentities/multi/electric/godforge/module/MTEPlasmaModule.java`
- `gregtech/common/metatileentities/multi/electric/godforge/module/MTEExoticModule.java` — 动态配方生成
- `gregtech/common/metatileentities/multi/electric/godforge/module/GodforgeModuleRecipeLogic.java` — 无线能量

### 数据/工具 (5文件)
- `gregtech/common/metatileentities/multi/electric/godforge/util/ForgeOfGodsData.java` — 主数据容器
- `gregtech/common/metatileentities/multi/electric/godforge/util/GodforgeMath.java` — 公式计算
- `gregtech/common/metatileentities/multi/electric/godforge/color/ForgeOfGodsStarColor.java`
- `gregtech/common/metatileentities/multi/electric/godforge/color/StarColorSetting.java`
- `gregtech/common/metatileentities/multi/electric/godforge/color/StarColorStorage.java`

### 升级系统 (2文件)
- `gregtech/common/metatileentities/multi/electric/godforge/upgrade/ForgeOfGodsUpgrade.java` — 30节点
- `gregtech/common/metatileentities/multi/electric/godforge/upgrade/UpgradeStorage.java`

### GUI数据 (8文件)
- `data/ColorData`, `Milestones`, `StarColors`, `UpgradeColor`, `UpgradeType`, `Fuels`, `Formatters`, `Statistics`

### GUI系统 (36文件)
- 主GUI: `MTEForgeOfGodsGui`, `GodforgeBaseGui`, `ForgeOfGodsGuiUtil`
- 模块GUI: `MTEBaseModuleGui`, `MTESmeltingModuleGui`, `MTEMoltenModuleGui`, `MTEPlasmaModuleGui`, `MTEExoticModuleGui`
- 面板(18个): `panel/` 目录
- 同步: `sync/SyncHypervisor`, `Panels`, `Modules`, `SyncActions`, `SyncValue`, `SyncValues`
- 辅助Widget: `SelectButton`, `SlotLikeButtonWidget`, `LinkedBoolValue`, `RotatedDrawable`

### 渲染系统 (6文件)
- `gregtech/client/renderer/godforge/GodforgeStarRenderer.java` — TESR着色器渲染
- `gregtech/client/renderer/godforge/GodforgeRenderTileEntity.java` — 渲染数据容器
- `gregtech/client/renderer/godforge/util/SphereVBOCache.java`
- `gregtech/client/renderer/godforge/util/StructureVBO.java`
- `gregtech/client/renderer/godforge/util/StructureBlockAccess.java`
- `gregtech/client/renderer/godforge/util/TextureUpdateRequester.java`

### 配方 (2文件)
- `gregtech/api/recipes/GodforgeRecipeMaps.java`
- `gregtech/loaders/recipe/GodforgeRecipeLoader.java`

### 接口 (1文件)
- `gregtech/api/metatileentity/multiblock/IGodforgeModule.java`

### 资源文件 (26文件)
- 结构: `assets/gregtech/godforge/structures/` (4个txt)
- 星体贴图: `textures/godforge/` (4个png)
- 着色器: `assets/gregtech/shaders/` (star.vert/frag, gorgeBeam.vert/frag, fadebypass.vert/frag)
- 方块贴图: `textures/blocks/casings/godforge/` (12个)
- Blockstates: 2个json

---

## 剩余工作清单

### 🔴 P1: 恒星渲染排查

**问题：** 电池已启动(27/100)但恒星不渲染。

**已添加调试日志：** `createRenderer()` 和 `ensureRendererState()` 中的 `[FOG]` 前缀日志。

**可能原因（按可能性排序）：**
1. `setBlockState` 失败（区块未加载） — 需要 `ForgeChunkManager`
2. `setBlockState` 成功但 TESR 被 frustum culling 剔除（RenderBoundingBox 问题）
3. 着色器初始化失败（`failedInit = true`）— 查看游戏日志中的 GL error
4. 渲染器位置超出客户端渲染距离

**解决方案（视日志结果）：**
- 如果是区块加载问题 → 实现 `ForgeChunkManager` 强制加载渲染区块
- 如果是TESR问题 → 调整 `getRenderBoundingBox()` 或检查着色器兼容性
- 临时方案：可将 `RENDER_OFFSET` 缩小用于测试

---

### 🟡 P2: NBT持久化完善

**问题：** `ForgeOfGodsData.writeRenderNBT()` 从未被调用，`isRenderActive` 不保存到世界NBT。

**影响：** 世界重载后 `isRenderActive = false`，但 `ensureRendererState()` 会自动重建（前提是区块加载成功）。

**修复方案：** 在 `MetaTileEntityForgeOfGods.writeToNBT()` 中调用 `data.writeRenderNBT(tag)` 或将相关字段合并到 `writeToNBT(nbt, force)` 中。

---

### 🟡 P2: ForgeChunkManager 区块加载

**问题：** 渲染器位于控制器后方122格（约8个区块），没有区块加载器时可能不在内存中。

**GT5方案：** GT5使用 `ForgeChunkManager` 注册 ticket 强制加载渲染区块。

**实现要点：**
```java
// 在 mod 初始化时注册 callback
ForgeChunkManager.setForcedChunkLoadingCallback(GregTechMod.instance, callback);

// 在 createRenderer() 中请求 ticket
ForgeChunkManager.Ticket ticket = ForgeChunkManager.requestTicket(instance, world, Type.NORMAL);
ForgeChunkManager.forceChunk(ticket, new ChunkPos(renderPos));
```

---

### 🟡 P2: 配方完善验证

| 模块 | 配方来源 | 状态 |
|------|---------|------|
| Smelting | BLAST_RECIPES + ARC_FURNACE_RECIPES (直接使用) | ✅ 应可用 |
| Molten | GodforgeRecipeLoader 动态生成 | ⚠️ 需验证 |
| Plasma | GodforgeRecipeLoader 自动扫描所有plasma材料 | ✅ 应可用 |
| Exotic | MTEExoticModule 运行时动态生成 | ⚠️ 需验证 |

---

### 🟢 P3: 优化与打磨

| 任务 | 说明 |
|------|------|
| 移除调试日志 | 渲染问题确认修复后移除 `[FOG]` 调试输出 |
| GUI样式对齐 | 与GT5原版GUI效果对比，调整面板布局 |
| JEI集成 | 各模块RecipeMap的JEI预览 |
| 性能测试 | MultiPiecePattern 在570K方块场景下的性能 |
| 超大结构建造辅助 | 创造模式自动放置工具 |

---

## 架构决策记录

### 决策1: 无线能量网络
**选择：** `WirelessEnergyService` + `WirelessEnergyServiceImpl`
**用法：** `GodforgeModuleRecipeLogic.drawEnergy()` 通过 `service.extract(uuid, amount, TransferContext.MACHINE)` 消耗能量

### 决策2: 结构检查策略
**选择：** 双层结构
- 初始成形：`createStructurePattern()` — beam_shaft + first_ring 合并为单一BlockPattern
- 运行时分片：`createMultiPiecePattern()` — 4个独立piece，条件环由升级控制
- 使用 `LazyTemplate` 静态缓存模板实例

### 决策3: 模块作为 MultiblockPart
**选择：** 模块继承 `RecipeMapMultiblockController` 但同时作为主结构的 part
**机制：** 主控制器通过 `getMultiblockParts()` → `instanceof MTEBaseModule` 发现模块
**好处：** 模块有独立的结构检查+配方逻辑+GUI，同时被主控制器管理

### 决策4: GUI面板打开机制
**选择：** `Panels` 枚举持有 `panelSupplier` 函数引用
**机制：** 按钮点击 → `Panels.getFrom(hypervisor)` → `panelSupplier.apply()` → 面板构建并显示
**注意：** 主面板(MAIN/MAIN_SMELTING等)为根面板，不通过 `getFrom()` 打开

### 决策5: 渲染器放置策略
**选择：** 在目标位置放置不可见方块 `BlockGodforgeRender`，绑定 TESR
**位置：** 控制器正后方 `RENDER_OFFSET=122` 格（结构环中心）
**同步：** `GodforgeRenderTileEntity.updateToClient()` → `world.notifyBlockUpdate()` → SPacketUpdateTileEntity

---

## 已修复Bug记录

### 2026-05-07

1. **GUI按钮NPE** — `Panels`枚举的子面板缺少`panelSupplier`连接
   - 原因: 所有子面板使用了默认无参构造器，`panelSupplier = null`
   - 修复: 为每个子面板添加对应的 `::openPanel` 方法引用

2. **模块贴图占位符** — `MTEBaseModule.getBaseTexture()` 返回钢贴图
   - 原因: 占位符代码未更新
   - 修复: 改为 `Textures.GODFORGE_INNER_CASING`

3. **模块结构方向反转** — 控制器面板在FRONT，beam连接端在BACK
   - 原因: aisle排列顺序与实际摆放方向相反
   - 修复: 反转aisle排列，G(Siphon)在第一个aisle(FRONT)，控制器面板在最后(BACK)

---

## 总结

移植进度约 **95%**（核心逻辑已全部实现）。

剩余工作集中在：
1. **恒星渲染不触发**的Bug排查（最可能是区块加载问题）
2. NBT持久化完善
3. 配方验证
4. GUI细节打磨

所有核心系统（控制器运行逻辑、燃料系统、电池管理、模块发现与参数下发、里程碑追踪、升级效果、渲染器管理、GUI面板系统）已完整实现并编译通过。
