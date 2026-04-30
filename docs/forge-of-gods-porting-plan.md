# 诸神之煅炉（Forge of the Gods）移植计划

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

## 底层依赖分析

| GT5 依赖 | 当前项目对应物 | 适配难度 |
|----------|--------------|---------|
| `com.gtnewhorizon.structurelib` (checkPiece/buildPiece) | `MultiPiecePattern` (P3) | ⭐⭐⭐ 高 — 核心差异 |
| `TTMultiblockBase` (TecTech 基类) | `MultiblockControllerBase` | ⭐⭐ 中 — API 对齐 |
| `WirelessNetworkManager` (无线能量网络) | **不存在** — 需新建 | ⭐⭐⭐ 高 — 全新系统 |
| MUI2 (`com.cleanroommc.modularui`) | 当前 MUI (同一库 MC 1.12版) | ⭐ 低 — 几乎一致 |
| `GodforgeCasings` (TecTech 方块) | 需新建对应方块 | ⭐⭐ 中 |
| `IStructureDefinition` (structurelib) | `BlockPatternTemplate` (P1) | ⭐⭐ 中 — 概念对应 |
| GT5 RecipeMap 系统 | 当前 RecipeMap | ⭐ 低 — 基本兼容 |

---

## GT5 源文件清单

### godforge/ 主目录（控制器 + 模块）
| 文件 | 行数 | 功能 |
|------|------|------|
| `MTEForgeOfGods.java` | 949 | 主控制器（分片检查、模块管理、燃料、电池、渲染控制） |
| `MTEBaseModule.java` | ~200 | 模块基类（连接/断开、无线能量、超频） |
| `MTESmeltingModule.java` | ~150 | 熔炼模块（电弧炉配方） |
| `MTEMoltenModule.java` | ~150 | 熔融模块（高炉配方） |
| `MTEPlasmaModule.java` | ~200 | 等离子体模块（等离子锻造配方） |
| `MTEExoticModule.java` | ~250 | 奇异物质模块（奇异配方 + 暗物质配方） |

### godforge/structure/（结构字符串）
| 文件 | 行数 | 功能 |
|------|------|------|
| `ForgeOfGodsStructureString.java` | 5432 | 主结构 + 光束轴 + 第一环定义 |
| `ForgeOfGodsRingsStructureString.java` | 5099 | 第二环 + 第三环定义 |

### godforge/util/（工具类）
| 文件 | 行数 | 功能 |
|------|------|------|
| `ForgeOfGodsData.java` | ~500 | 核心数据模型（升级状态/里程碑/燃料/电池/渲染标志） |
| `GodforgeMath.java` | ~300 | 数学计算（热量/超频因子/效率/并行数/能量折扣） |

### godforge/upgrade/（升级系统）
| 文件 | 行数 | 功能 |
|------|------|------|
| `ForgeOfGodsUpgrade.java` | ~500 | 30 个升级节点 enum（前置条件/花费/面板大小/位置/效果） |
| `UpgradeStorage.java` | ~200 | 升级状态管理（解锁/激活/序列化） |

### godforge/color/（恒星颜色）
| 文件 | 行数 | 功能 |
|------|------|------|
| `ForgeOfGodsStarColor.java` | ~100 | 恒星颜色定义 |
| `StarColorSetting.java` | ~80 | 单个颜色设置 |
| `StarColorStorage.java` | ~120 | 颜色持久化存储 |

### GUI 系统（30 个文件）

#### 主 GUI
| 文件 | 功能 |
|------|------|
| `MTEForgeOfGodsGui.java` | 主控制器 GUI（整合所有面板） |
| `MTESmeltingModuleGui.java` | 熔炼模块 GUI |
| `MTEMoltenModuleGui.java` | 熔融模块 GUI |
| `MTEPlasmaModuleGui.java` | 等离子体模块 GUI |
| `MTEExoticModuleGui.java` | 奇异模块 GUI |

#### 面板 (panel/)
| 文件 | 功能 |
|------|------|
| `BatteryConfigPanel.java` | 电池配置面板 |
| `CustomStarColorPanel.java` | 自定义恒星颜色面板 |
| `CustomStarColorSelector.java` | 颜色选择器 |
| `ExoticInputsListPanel.java` | 奇异输入列表 |
| `ExoticPossibleInputsListPanel.java` | 可能的奇异输入 |
| `FuelConfigPanel.java` | 燃料配置面板 |
| `GeneralInfoPanel.java` | 通用信息面板 |
| `IndividualMilestonePanel.java` | 单个里程碑详情 |
| `IndividualUpgradePanel.java` | 单个升级详情 |
| `ManualInsertionPanel.java` | 手动输入面板 |
| `MilestonePanel.java` | 里程碑总览面板 |
| `PlasmaDebugPanel.java` | 等离子体调试面板 |
| `SpecialThanksPanel.java` | 特别致谢面板 |
| `StarColorImportPanel.java` | 颜色导入面板 |
| `StarCosmeticsPanel.java` | 恒星外观面板 |
| `StatisticsPanel.java` | 统计面板 |
| `UpgradeTreePanel.java` | 升级树面板（最复杂的面板） |
| `VoltageConfigPanel.java` | 电压配置面板 |

#### 数据 (data/)
| 文件 | 功能 |
|------|------|
| `ColorData.java` | 颜色数据定义 |
| `Milestones.java` | 里程碑 enum + 计算 |
| `StarColors.java` | 预设恒星颜色 |
| `UpgradeColor.java` | 升级节点颜色 enum |

#### 同步 (sync/)
| 文件 | 功能 |
|------|------|
| `Modules.java` | 模块同步数据 |
| `SyncActions.java` | 同步操作定义 |
| `SyncHypervisor.java` | 同步管理器 |
| `SyncValue.java` | 单个同步值 |
| `SyncValues.java` | 同步值集合 |

---

## 分片结构映射（GT5 → 我们的 P3）

GT5 使用 `checkPiece()` / `buildPiece()` 实现分片：

```java
// GT5 原始代码
checkPiece(STRUCTURE_PIECE_MAIN, 63, 14, 1);           // 主结构
checkPiece(STRUCTURE_PIECE_SECOND_RING, 55, 11, -67);   // 第二环（条件）
checkPiece(STRUCTURE_PIECE_THIRD_RING, 47, 13, -76);    // 第三环（条件）
```

映射到我们的 P3 `MultiPiecePattern`：

```java
// 我们的 P3 代码
MultiPiecePattern.builder()
    .piece("core", coreTemplate, Vec3i.ZERO)
    .piece("shaft", shaftTemplate, new Vec3i(0, 0, -59))
    .piece("ring1", ring1Template, new Vec3i(63, 14, -59))
    .conditionalPiece("ring2", ring2Template, new Vec3i(55, 11, -67),
        () -> data.getRingAmount() >= 2)
    .conditionalPiece("ring3", ring3Template, new Vec3i(47, 13, -76),
        () -> data.getRingAmount() >= 3)
    .build();
```

---

## 方块需求清单

需要在当前项目中注册的新方块/方块状态：

| 方块名 | 数量范围(1-3环) | 用途 |
|--------|----------------|------|
| Transcendentally Amplified Magnetic Confinement Casing | 3943-11005 | 主结构外壳 |
| Singularity Reinforced Stellar Shielding Casing | 2818-6567 | 屏蔽外壳 |
| Celestial Matter Guidance Casing | 272-824 | 引导外壳 |
| Boundless Gravitationally Severed Structure Casing | 130-158 | 结构外壳 |
| Spatially Transcendent Gravitational Lens Block | 9-155 | 引力透镜 |
| Remote Graviton Flow Modulator | 345 | 远程调制器 |
| Medial Graviton Flow Modulator | 12 | 中间调制器 |
| Central Graviton Flow Modulator | 40 | 中心调制器 |
| Stellar Energy Siphon Casing | 36 | 能量虹吸 |

---

## 实施阶段

### 阶段1: 方块注册
**依赖：** 无
**文件：** ~5个新文件
**内容：**
- 创建 `GodforgeCasings` 枚举/方块类
- 注册所有 9 种方块/方块状态
- 贴图资源（可先用占位）
- 本地化键

### 阶段2: 结构定义
**依赖：** 阶段1 + P3(MultiPiecePattern)
**文件：** ~4个新文件
**内容：**
- 将 GT5 的 10,531 行结构字符串转换为 `FactoryBlockPattern`/`BlockPatternTemplate`
- 创建 5 个 piece 的模板（core/shaft/ring1/ring2/ring3）
- 集成 `MultiPiecePattern` 和条件片段
- **这是最大的工作量** — 需要逐层转换 127×29 的结构定义

### 阶段3: 数据模型与工具
**依赖：** 无
**文件：** ~5个新文件
**内容：**
- 移植 `ForgeOfGodsData.java`（核心数据模型）
- 移植 `GodforgeMath.java`（数学计算）
- 移植颜色系统（3个文件）
- 适配 NBT 序列化

### 阶段4: 升级系统
**依赖：** 阶段3
**文件：** ~2个新文件 + GUI数据文件
**内容：**
- 移植 `ForgeOfGodsUpgrade.java` enum（30个升级节点）
- 移植 `UpgradeStorage.java`
- 移植 `Milestones.java` / `UpgradeColor.java`
- 适配 GUI 数据依赖

### 阶段5: 无线能量网络
**依赖：** 独立新系统
**文件：** ~3个新文件
**内容：**
- **方案A：** 完整移植 `WirelessNetworkManager`（全局能量网络，UUID绑定玩家）
- **方案B：** 简化为直接无线能量仓（降低复杂度但丧失原版体验）
- **推荐方案A** — 诸神之煅炉的核心机制依赖全局能量网络

### 阶段6: 控制器逻辑
**依赖：** 阶段1-5
**文件：** ~6个新文件
**内容：**
- 移植 `MTEForgeOfGods.java` → 适配 `MultiblockControllerBase`
- 移植 `MTEBaseModule.java` → 适配为模块基类
- 移植 4 个模块类（Smelting/Molten/Plasma/Exotic）
- 关键适配点：
  - `TTMultiblockBase` → `MultiblockControllerBase`
  - `checkPiece()` → `MultiPiecePattern.checkDirtyPieces()`
  - `ISurvivalConstructable` → 自动构建支持
  - 模块连接 → `MultiblockAbility` 自定义能力

### 阶段7: GUI 系统
**依赖：** 阶段3-6
**文件：** ~30个新文件
**内容：**
- 移植主 GUI + 5 个模块 GUI
- 移植 18 个面板
- 移植同步系统（5个文件）
- 移植 GUI 数据文件（4个文件）
- MUI API 差异适配（GT5 MUI2 → 当前 MUI，差异极小）

### 阶段8: 渲染
**依赖：** 阶段3
**文件：** ~3个新文件
**内容：**
- 恒星 TESR 渲染器
- 颜色自定义系统
- 动画开关控制

### 阶段9: 配方
**依赖：** 阶段6
**文件：** ~2个新文件
**内容：**
- 注册专用 RecipeMap
- 各模块配方逻辑（熔炼/熔融/等离子/奇异/暗物质）

---

## 关键风险

### 1. 结构字符串转换（阶段2）— 最高风险
GT5 使用 `StructureDefinition` + `ofBlock()` 式 API，结构字符串是一维字符数组。
我们需要将其转换为 `FactoryBlockPattern` 的 `aisle()` 格式。
10,531 行的转换需要仔细对照，一个字符错误就会导致整个结构无法形成。

**建议：** 编写一个自动转换脚本。

### 2. 无线能量网络（阶段5）— 高风险
GT5 的 `WirelessNetworkManager` 是全局单例，通过 UUID 绑定玩家。
诸神之煅炉及其所有模块完全依赖此系统（不使用传统能量仓）。
不移植此系统 = 诸神之煅炉无法运作。

### 3. TecTech 基类适配（阶段6）— 中风险
`TTMultiblockBase` 有一些特有功能：
- 能量数据仓（非标准能量 I/O）
- 自定义结构检查周期
- TecTech 纹理系统
需要识别哪些是必需的，哪些可以省略。

### 4. GUI 复杂度（阶段7）— 中风险
30 个 GUI 文件是最大的代码量，但 MUI 库基本一致。
主要风险在于同步系统（`SyncHypervisor`）的适配。

### 5. 模块连接机制（阶段6）— 中风险
GT5 通过自定义 hatch 类型发现子模块（`moduleHatches`），
需要在当前架构中用 `MultiblockAbility` 实现等效机制。

---

## 实施顺序总结

```
阶段1: 方块注册        →  前置条件，无依赖
       ↓
阶段3: 数据模型        →  无依赖，可与阶段1并行
       ↓
阶段4: 升级系统        →  依赖阶段3
       ↓
阶段2: 结构定义        →  依赖阶段1 + P3
       ↓
阶段5: 无线能量网络    →  独立新系统
       ↓
阶段6: 控制器逻辑      →  依赖阶段1-5（核心）
       ↓
阶段7: GUI系统         →  依赖阶段3-6（最大工作量）
       ↓
阶段8: 渲染           →  依赖阶段3
       ↓
阶段9: 配方           →  依赖阶段6
```

---

## 工作量预估

| 阶段 | 新文件数 | 复杂度 | 说明 |
|------|---------|--------|------|
| 1. 方块注册 | ~5 | 低 | 标准方块注册流程 |
| 2. 结构定义 | ~4 | **高** | 10K行结构字符串转换 |
| 3. 数据模型 | ~5 | 中 | 直接移植，适配序列化 |
| 4. 升级系统 | ~4 | 中 | enum + 存储移植 |
| 5. 无线能量 | ~3 | **高** | 全新系统 |
| 6. 控制器 | ~6 | **高** | 核心逻辑适配 |
| 7. GUI | ~30 | **高** | 数量最多 |
| 8. 渲染 | ~3 | 中 | TESR + 颜色系统 |
| 9. 配方 | ~2 | 低 | RecipeMap 注册 |
| **总计** | **~62** | — | — |
