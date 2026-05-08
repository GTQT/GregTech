# 末地维度能量系统 — 设计文档

> **范围**：一套基于末地（The End, DIM 1）维度特性的能量采集、转化和传输系统。利用虚空能量、末影共振、龙息能量三大主题，提供 EV→LuV 的渐进式高端能源方案，同时扩展魔法/超自然能量体系。

***

## 1. 系统总览

### 1.1 MC 末地维度环境特征

| 特性 | 末地表现 | 对设计的影响 |
|------|---------|-----------|
| 维度ID | 1，`WorldProviderEnd` | 通过 `provider instanceof WorldProviderEnd` 判定 |
| 地形结构 | 主岛 + 虚空 + 外岛群（折跃门连接） | 漂浮岛屿之间是虚空 → "虚空能量采集"的物理基础 |
| 无水无岩浆 | 无任何自然流体 | 所有流体需从其他维度运入 |
| 末影水晶 | 黑曜石柱顶端再生 | 已有 `MagicEnergyAbsorber` 利用 → 可扩展为阵列 |
| 末影龙 | 主岛BOSS，可重复召唤 | 龙息/龙蛋作为高级能源材料 |
| 紫颂植物 | 外岛独有植物 | 紫颂果含空间扭曲能量（传送效果） |
| 末地石 | 主体方块 | 硬度3.0，挖掘效率低于地狱岩 |
| 虚空 | Y<0 即死 | 虚空是一种"能量真空"或"维度边界" |
| 环境光照 | 永暗，无太阳无月亮 | 无太阳能 |
| EnderAir | GT已有：Gas Collector DIM 1 采集 | 蒸馏出 Deuterium/Tritium/Krypton/Xenon/Radon + EnderPearl |
| 原生矿脉 | Naquadah、Sheldonite(铂)、Bauxite、Scheelite、Pitchblende | IV-LuV级核心矿物 |

### 1.2 核心设计理念

末地与下界是**对立的能源主题**：

| 维度 | 能源主题 | 物理基础 | GT等级范围 | 系统风格 |
|------|---------|---------|-----------|---------|
| 下界 | **热能** — 岩浆、高温、火焰 | 热力学 | MV-EV | 工业化、管道密封循环 |
| 末地 | **空间能量** — 虚空、维度裂缝、末影共振 | 量子/维度物理 | EV-LuV | 高科技+魔法混合 |

**末地系统的六大设计原则**：
1. **虚空即能源**：末地的虚空不是"空"，而是充满维度能量的边界
2. **末影共振**：末影珍珠/末地石的空间折叠特性可转化为能量
3. **扩展现有系统**：`MagicEnergyAbsorber` 升级为多方块阵列，`EnderAir` 从气体提取延伸到能量利用
4. **科技与魔法融合**：不是纯"科学解释"的系统，保留末影/龙的"超自然"属性
5. **风险/收益**：末影龙相关路线有对抗龙的风险，但收益更高
6. **无线传输整合**：与项目已有的 `WirelessEnergyService` 联动

### 1.3 系统组件总览

| 组件 | 类型 | GT等级 | 功能 |
|------|------|--------|------|
| 虚空能量采集器 | 多方块 | EV | 从虚空边界提取维度能量 |
| 末影水晶阵列 | 多方块 | IV | 扩展版 MagicEnergyAbsorber，多水晶协同 |
| 维度裂缝稳定器 | 多方块 | IV | 在维度裂缝附近提取空间能量 |
| 末影共振发电机 | 多方块 | IV-LuV | 利用末影珍珠的空间折叠释放能量 |
| 龙息反应堆 | 多方块 | LuV | 利用龙息（Dragon's Breath）驱动的高端发电 |
| 末地空气液化站 | 多方块 | IV | 规模化 EnderAir 液化+蒸馏 |
| 末影传输节点 | 单方块 | IV | 跨维度无线能量传输节点 |

***

## 2. 核心概念：虚空能量（Void Energy）

### 2.1 物理设定

在本系统的世界观中：

- 虚空（Void）不是空无一物，而是维度边界的"能量膜"
- 末影粒子（Ender particles，即传送门效果的紫色颗粒）是虚空能量泄漏的可见表现
- 末影珍珠之所以能传送，是因为它与虚空能量产生共振
- 末影龙是虚空能量的具象化实体
- 末影水晶是虚空能量的天然凝聚点（这解释了为什么它能治愈龙）

### 2.2 新增能量单位概念

不引入新的能量单位，所有能量最终转化为 EU。但虚空能量在中间步骤以流体形态存在：

| 流体 | 含义 | 获取 | 用途 |
|------|------|------|------|
| VoidEssence（虚空精华） | 液态虚空能量 | 虚空能量采集器产出 | 发电/合成/空间操控 |
| ResonantEnderFluid（共振末影流体） | 被激发的末影珍珠流体 | 末影珍珠 + VoidEssence 化学反应 | 高效发电/无线传输介质 |
| DragonBreath（龙息） | MC原版龙息流体化 | 对龙息云使用流体单元收集 | 龙息反应堆燃料 |
| ConcentratedDragonBreath（浓缩龙息） | 龙息的EV化学浓缩产物 | 化学反应器处理 | 高功率发电燃料 |

***

## 3. 新增材料与流体

### 3.1 末地专用流体

| 流体名称 | ID建议 | 说明 |
|---------|--------|------|
| VoidEssence（虚空精华） | 2560 | 从虚空边界提取的液态维度能量，深紫色 |
| ResonantEnderFluid（共振末影流体） | 2561 | 末影珍珠溶液被虚空精华激发后的共振态 |
| DragonBreath（龙息） | 2562 | 龙息云的流体化（已有玻璃瓶收集机制，扩展为流体） |
| ConcentratedDragonBreath（浓缩龙息） | 2563 | 化学浓缩的龙息，能量密度极高 |
| LiquidEndstone（液化末地石） | 2564 | 末地石高温熔融后的流体，含空间能量残余 |
| StabilizedVoidEssence（稳定虚空精华） | 2565 | 添加稳定剂的虚空精华，用于安全存储和传输 |

### 3.2 复用的现有流体/材料

| 现有材料 | 在系统中的角色 |
|---------|-------------|
| `EnderAir` | 末地Gas Collector产出，离心/蒸馏出稀有气体 |
| `LiquidEnderAir` | EnderAir液化态，蒸馏出Tritium/Krypton/Xenon/Radon |
| `EnderPearl` (material) | 共振末影流体的原料，已有plate/dust形态 |
| `Naquadah` | 末地独有高端矿物，硅岩体系的核心 |

### 3.3 新增固体材料

| 材料名称 | 说明 | 用途 |
|---------|------|------|
| VoidCrystal（虚空水晶） | VoidEssence结晶化的产物 | 制作虚空能量采集器的核心组件 |
| ResonantAlloy（共振合金） | EnderPearl + Naquadah 合金 | 末影设备外壳材料 |
| DragonScale（龙鳞） | 击杀末影龙的稀有掉落（或龙息凝聚） | 龙息反应堆的催化剂（有损耗） |

***

## 4. 系统组件详细设计

### 4.1 虚空能量采集器（Void Energy Collector）

**类型**：多方块（继承 `RecipeMapMultiblockController`，带环境感知）

**GT等级**：EV

**核心理念**：放置在岛屿边缘朝向虚空时，结构中的特殊方块（虚空感应面板）暴露在虚空中，从维度边界提取能量，以 VoidEssence 流体形态输出。

**结构尺寸**：3x3x3

```
Aisle layout (facing void edge):
Layer 0 (back):   CCC / CCC / CCC
Layer 1 (middle): CCC / C#C / CCC
Layer 2 (front):  VVV / VYV / VVV  ← V面朝虚空

C = Resonant Casing (共振合金外壳)
V = Void Sensor Panel (虚空感应面板) — 必须面朝虚空（下方或侧面为Air/虚空）
# = Air (内部腔体)
Y = Controller
```

**环境要求**：
- 必须在末地维度（DIM 1）
- V（虚空感应面板）面朝的方向必须是空气/虚空（不能被方块遮挡）
- Y坐标越低（越接近虚空边界），效率越高
- 最优位置：岛屿底部边缘，Y<30

**产出公式**：
```java
public int getVoidEssenceOutput() {
    if (!(getWorld().provider instanceof WorldProviderEnd)) return 0;
    
    int y = getPos().getY();
    int baseOutput = 50;  // mB/t base
    
    // Lower Y = closer to void boundary = more energy
    double yMultiplier;
    if (y <= 10) yMultiplier = 3.0;       // Dangerously close to void
    else if (y <= 30) yMultiplier = 2.0;  // Island edge
    else if (y <= 50) yMultiplier = 1.5;  // Mid-level
    else yMultiplier = 1.0;               // High up
    
    // Check void exposure (how many V panels face actual void/air)
    int exposedPanels = countExposedVoidPanels();
    double exposureMultiplier = exposedPanels / (double) totalPanels;
    
    return (int) (baseOutput * yMultiplier * exposureMultiplier);
}
```

**消耗**：需要 512 EU/t 维持采集场

**产出**：50-150 mB/t VoidEssence（取决于位置和暴露度）

### 4.2 末影水晶阵列（Ender Crystal Array）

**类型**：多方块发电机（继承 `FuelMultiblockController`，扩展 `MagicEnergyAbsorber`）

**GT等级**：IV

**核心理念**：将现有单方块 `MagicEnergyAbsorber` 升级为规模化多方块。多个末影水晶同时被引导到阵列上，协同发电。加入 VoidEssence 作为催化/增幅介质。

**结构尺寸**：7x5x7（大型）

```
Top view (Layer 2, middle height):
CCCCCCC
C#####C
C##A##C
C#AYA#C
C##A##C
C#####C
CCCCCCC

A = Antenna Pillar (天线柱 — 吸引水晶光束的接收点)
Y = Controller
C = Resonant Casing
# = Air (需要空旷让水晶光束穿入)
```

**机制**：
- 阵列中的 Antenna Pillar 向最近的末影水晶发射"引导信号"
- 每根天线柱可吸引 1 个水晶（最多 4 根天线 = 4 水晶同时连接）
- 比原版单方块的搜索范围更大（单方块 range = 2^(tier-1)*16, 阵列固定 256格）
- 输入 VoidEssence 时，每个水晶的发电效率 ×3
- 龙蛋仍然作为加成（放在控制器上方）

**发电参数**：

| 模式 | 每水晶产出 | 4水晶最大 | VoidEssence消耗 |
|------|---------|---------|---------------|
| 基础（无VoidEssence） | 512 EU/t (IV base) | 2048 EU/t | 0 |
| 增幅（有VoidEssence） | 1536 EU/t | 6144 EU/t | 20 mB/t |
| 龙蛋加成 | 额外 +50% | 9216 EU/t | 30 mB/t |

**与现有 MagicEnergyAbsorber 的关系**：
- 单方块版本保留（HV-EV 级别入门）
- 多方块阵列是升级版（IV 级别，需要 VoidEssence 催化达到最大效率）
- 两者不冲突：不同水晶可以分别被单方块或阵列连接

### 4.3 维度裂缝稳定器（Dimensional Rift Stabilizer）

**类型**：多方块（继承 `RecipeMapMultiblockController`，环境感知+发电混合）

**GT等级**：IV

**核心理念**：末地传送门（Return Portal）是一个"维度裂缝"。在传送门附近放置此设备，利用传送门辐射的维度能量发电，同时"稳定"裂缝防止能量逸散。

**环境条件**：
- 必须放置在返程传送门（Bedrock Portal Frame, Y=64 附近）的 32 格范围内
- 末地折跃门（End Gateway）也可以作为能量源（效率50%）
- 没有附近传送门时不工作

**产出**：
- 距离返程传送门 <8格: 2048 EU/t
- 距离 8-16格: 1024 EU/t
- 距离 16-32格: 512 EU/t
- 消耗: VoidEssence 10 mB/t 作为稳定介质

**特殊机制**：
- 如果末影龙存活（`DragonFightManager` 活跃），维度能量更强烈 → 输出 ×2
- 但此时有被龙攻击的风险

### 4.4 末影共振发电机（Ender Resonance Generator）

**类型**：多方块发电机（继承 `FuelMultiblockController`）

**GT等级**：IV-LuV

**核心理念**：将末影珍珠的"空间折叠"能力工业化利用。末影珍珠流体（已有 `EnderPearl` 作为材料）与 VoidEssence 发生共振反应，释放大量空间能量转化为 EU。

**燃料**：ResonantEnderFluid（共振末影流体）

**ResonantEnderFluid 制备**：
```
[化学反应器 IV]
  EnderPearl fluid 144mB + VoidEssence 100mB + Naquadah dust 1
  → ResonantEnderFluid 200mB
  Duration: 100 ticks, EUt: 1920 (IV)
```

**发电参数**：

| 燃料 | 消耗速率 | 基础输出 | 末地加成输出 |
|------|---------|---------|-----------|
| ResonantEnderFluid | 10 mB/t | 4096 EU/t (IV) | 6144 EU/t |
| VoidEssence (直接) | 50 mB/t | 2048 EU/t (IV) | 3072 EU/t |

**末地加成**：在末地维度发电效率 +50%（末影共振在末地更强）

**结构**：5x3x5，使用 Resonant Casing + Naquadah Alloy框架

### 4.5 龙息反应堆（Dragon Breath Reactor）

**类型**：多方块发电机（继承 `FuelMultiblockController`）

**GT等级**：LuV

**核心理念**：龙息（Dragon's Breath）是末影龙释放的浓缩维度能量。将其流体化并在特殊反应堆中引导受控"维度衰变"，释放巨量能量。这是末地系统的终极发电方案。

**龙息获取**：
- MC原版：对龙息攻击（紫色云）使用空玻璃瓶获得 Dragon's Breath
- GT扩展：对龙息云使用空流体单元 → DragonBreath 流体 250mB/单元
- 浓缩：化学反应器中 DragonBreath + VoidEssence → ConcentratedDragonBreath

**龙息浓缩配方**：
```
[化学反应器 IV]
  DragonBreath 1000mB + VoidEssence 500mB + Naquadah dust 4
  → ConcentratedDragonBreath 500mB
  Duration: 200 ticks, EUt: 7680 (LuV)
```

**发电参数**：

| 燃料 | 消耗速率 | 输出EU/t |
|------|---------|---------|
| DragonBreath | 5 mB/t | 8192 EU/t (LuV) |
| ConcentratedDragonBreath | 2 mB/t | 16384 EU/t |

**特殊机制**：
- DragonScale（龙鳞）作为催化剂，放置在物品输入仓中
- 每 36000 ticks（30分钟）消耗 1 个 DragonScale
- 无催化剂时效率降低 75%
- 末地维度内运行：输出 +25%

**风险/收益玩法**：
- 龙息只能从末影龙身上获取 → 需要重复召唤和对抗末影龙
- ConcentratedDragonBreath 需要 LuV 级化工 → 高前置科技
- 回报：LuV 级别持续稳定发电，比核裂变更稳定

### 4.6 末地空气液化站（End Air Liquefaction Plant）

**类型**：多方块（继承 `RecipeMapMultiblockController`）

**GT等级**：IV

**核心理念**：规模化的 EnderAir 采集+液化+蒸馏一体化设施。比单方块 Gas Collector + 真空冷冻机 + 蒸馏塔的分散方案更紧凑高效。

**一体化流程**：
```
EnderAir(自动采集) → 液化 → 蒸馏 → Deuterium + Tritium + Krypton + Xenon + Radon + EnderPearl副产
```

**效率加成**：一体化设施比分散机器产量 +50%，EU消耗 -30%

### 4.7 末影传输节点（Ender Transmission Node）

**类型**：单方块（TieredMetaTileEntity），IV/LuV/ZPM 三级

**核心理念**：利用末影珍珠的空间折叠能力实现**跨维度**能量无线传输。与项目已有的 `WirelessEnergyService` 整合。

**机制**：
- 分为发送端（Transmitter）和接收端（Receiver）
- 发送端消耗 ResonantEnderFluid 作为传输介质
- 接收端在任何维度都可以工作（这是末地系统的独特卖点——"下界发的电通过末地中转传输到主世界"）
- 传输效率取决于等级和距离（同维度 95%, 跨维度 85%）

**参数**：

| 等级 | 最大传输 | ResonantEnderFluid消耗 | 跨维度效率 |
|------|---------|---------------------|---------|
| IV | 8192 EU/t | 5 mB/t | 85% |
| LuV | 32768 EU/t | 10 mB/t | 90% |
| ZPM | 131072 EU/t | 20 mB/t | 95% |

**与 WirelessEnergyService 的关系**：
- 末影传输节点作为 `WirelessEnergyService` 的一个新的 node 类型
- 发送端调用 `service.insert()`，接收端调用 `service.extract()`
- 不是替代现有无线系统，而是提供一种"需要消耗末影流体但可跨维度"的传输方式

***

## 5. 魔法Mod增强接口体系

### 5.1 设计理念

本系统提供**挂钩接口（Hook API）**，允许其他魔法mod增强末地维度能量体系的效率、产出或解锁新功能。GT 本身不硬依赖任何魔法mod，但通过以下机制让魔法mod"插入"加成：

**核心思路**：末地系统的某些关键参数设计为"可被外部修改"的，魔法mod通过以下方式注入加成：
1. **IMC消息**（FMLInterModComms）— mod加载时注册加成
2. **能力系统（Capability）** — 通过 Forge Capability 注入实时效果
3. **RecipeMap扩展** — 魔法mod可向已有RecipeMap中注册新配方（如 GroovyScript/CraftTweaker）
4. **事件总线（Event）** — 发布 GT 自定义事件，魔法mod监听并修改

### 5.2 增强接口：效率乘数器（Efficiency Modifier API）

```java
/**
 * API interface for external mods to provide efficiency bonuses to
 * End dimension energy machines.
 * 
 * Magic mods can register an IEndEnergyModifier to boost machine output,
 * reduce EU consumption, or unlock new recipe tiers.
 */
public interface IEndEnergyModifier {

    /**
     * @return unique ID for this modifier (e.g., "thaumcraft:vis_infusion")
     */
    String getModifierId();

    /**
     * Calculate the output multiplier for a given machine at a given position.
     * Called every 20 ticks by the machine's logic handler.
     *
     * @param world the world
     * @param pos the machine's controller position
     * @param machineType the machine type enum (VOID_COLLECTOR, CRYSTAL_ARRAY, etc.)
     * @return multiplier (1.0 = no change, 1.5 = +50%, etc.)
     */
    double getOutputMultiplier(World world, BlockPos pos, EndMachineType machineType);

    /**
     * Calculate the EU consumption multiplier (lower = cheaper to run).
     */
    double getEnergyConsumptionMultiplier(World world, BlockPos pos, EndMachineType machineType);
}
```

**注册方式**：

```java
// In magic mod's init phase:
FMLInterModComms.sendMessage("gregtech", "register_end_energy_modifier",
    "com.mymagicmod.compat.gt.MyEndEnergyModifier");

// GT side: receives and instantiates the modifier via reflection
```

### 5.3 具体魔法Mod增强示例

#### 5.3.1 Thaumcraft（神秘时代）增强

| 增强方式 | 效果 | 条件 |
|---------|------|------|
| Vis灌注 | 虚空能量采集器产出 +30% | 机器附近有充足Vis节点（aura node） |
| 注魔金属外壳 | 所有末地机器EU消耗 -20% | 使用注魔金属（Thaumium/VoidMetal）替代标准外壳 |
| 虚空金属共振 | 末影共振发电机输出 +50% | 结构中包含VoidMetal方块 |
| 奥术透镜 | 维度裂缝稳定器范围 ×2 | 附近有奥术透镜/聚焦装置 |
| 研究加成 | 解锁增强配方（如虚空精华→更高效材料） | 完成对应神秘研究 |

**实现方式**：Thaumcraft addon 实现 `IEndEnergyModifier`，检测机器附近的 aura/vis/warp 状态并返回乘数。

#### 5.3.2 Botania（植物魔法）增强

| 增强方式 | 效果 | 条件 |
|---------|------|------|
| Mana灌注 | 虚空能量采集器效率 +25% | 连接Mana Spreader向机器输送Mana |
| Gaia精华催化 | 龙息反应堆产出 +40% | 物品输入仓中有Gaia Spirit |
| Alfheim共振 | 末影水晶阵列范围 ×1.5 | 附近有Beacon/Alfheim相关方块 |
| Terra Plate充能 | 降低 ResonantEnderFluid 合成成本 | 使用Terra Plate替代化学反应器 |

**实现方式**：
- Botania addon 注册 Forge Capability `IManaReceiver` 到 GT 机器 TileEntity
- 机器如果检测到自身有 `IManaReceiver` capability 且有 Mana，就应用加成

#### 5.3.3 Blood Magic（血魔法）增强

| 增强方式 | 效果 | 条件 |
|---------|------|------|
| LP灌注 | 维度裂缝稳定器输出 +50% | 连接到Blood Altar，消耗LP |
| 恶魔意志增幅 | 龙息反应堆效率 ×2 | 附近有Demon Will结晶 |
| 血之祭坛共振 | VoidEssence产量 +100% | 机器建在5阶血之祭坛顶部 |
| 灵魂网络链接 | 末影传输节点效率 +10%（无损传输） | 通过灵魂网络增强传输 |

#### 5.3.4 Astral Sorcery（星辉魔法）增强

| 增强方式 | 效果 | 条件 |
|---------|------|------|
| 星光灌注 | 所有末地机器夜间效率 +40% | 附近有星辉收集器（但末地无昼夜→永久生效！） |
| 星座加成 | 不同星座提供不同加成类型 | 配合星盘/星座纸 |
| 液态星光冷却 | 替代 WaterCoolant，效率 ×2 | 使用液态星光（Liquid Starlight）作为冷循环 |
| 星辉透镜阵列 | 虚空能量采集器暴露面积扩展 | 使用星辉透镜扩展感应范围 |

**特殊交互**：末地"永暗"但有星空 → 星辉魔法在末地可能有独特交互（因为末地的"天空"是独特的星空纹理）

#### 5.3.5 Ars Magica / Electroblob's Wizardry 等增强

| 增强方式 | 效果 | 条件 |
|---------|------|------|
| 法力灌注 | 通用效率加成 +15-30% | 消耗法力/魔力值 |
| 附魔核心 | 解锁 ResonantEnderFluid 的"附魔模式"配方 | 特殊附魔核心放入机器 |
| 元素共振 | 不同元素提供不同加成 | 使用对应元素物品/流体 |

### 5.4 接口实现架构

#### 5.4.1 修改器注册表（Modifier Registry）

```java
/**
 * Registry for external mod efficiency modifiers for End energy machines.
 * Magic mods register their modifiers here during init phase.
 */
public class EndEnergyModifierRegistry {

    private static final List<IEndEnergyModifier> modifiers = new ArrayList<>();

    public static void register(IEndEnergyModifier modifier) {
        modifiers.add(modifier);
    }

    /**
     * Calculate combined output multiplier from all registered modifiers.
     * Called by machine logic handlers every 20 ticks.
     */
    public static double getCombinedOutputMultiplier(World world, BlockPos pos, EndMachineType machineType) {
        double combined = 1.0;
        for (IEndEnergyModifier modifier : modifiers) {
            combined *= modifier.getOutputMultiplier(world, pos, machineType);
        }
        return combined;
    }

    public static double getCombinedEnergyMultiplier(World world, BlockPos pos, EndMachineType machineType) {
        double combined = 1.0;
        for (IEndEnergyModifier modifier : modifiers) {
            combined *= modifier.getEnergyConsumptionMultiplier(world, pos, machineType);
        }
        return combined;
    }
}
```

#### 5.4.2 在机器逻辑中调用

```java
// In machine's WorkableHandler:
@Override
protected long boostProduction(long production) {
    // Base dimension bonus
    if (getWorld().provider instanceof WorldProviderEnd) {
        production = (long) (production * 1.5);
    }
    
    // External magic mod multipliers
    double magicMultiplier = EndEnergyModifierRegistry.getCombinedOutputMultiplier(
            getWorld(), getPos(), EndMachineType.VOID_COLLECTOR);
    production = (long) (production * magicMultiplier);
    
    return production;
}
```

#### 5.4.3 IMC消息处理

```java
// In GT's IGregTechModule.processIMC():
@Override
public boolean processIMC(FMLInterModComms.IMCMessage message) {
    if ("register_end_energy_modifier".equals(message.key)) {
        try {
            Class<?> clazz = Class.forName(message.getStringValue());
            IEndEnergyModifier modifier = (IEndEnergyModifier) clazz.newInstance();
            EndEnergyModifierRegistry.register(modifier);
            logger.info("Registered End energy modifier: {} from mod {}", 
                       modifier.getModifierId(), message.getSender());
            return true;
        } catch (Exception e) {
            logger.error("Failed to register End energy modifier from mod {}", message.getSender(), e);
        }
    }
    return false;
}
```

#### 5.4.4 Forge Event 挂钩

```java
/**
 * Fired when an End energy machine calculates its output.
 * External mods can listen and modify the result.
 */
public class EndEnergyOutputEvent extends Event {
    
    private final World world;
    private final BlockPos pos;
    private final EndMachineType machineType;
    private double outputMultiplier = 1.0;
    private double consumptionMultiplier = 1.0;

    // Getters and setters...
    
    public void addOutputBonus(double bonus) {
        this.outputMultiplier += bonus;
    }
    
    public void addConsumptionReduction(double reduction) {
        this.consumptionMultiplier -= reduction;
    }
}
```

#### 5.4.5 Capability 挂钩

```java
/**
 * Capability that external mods can provide on blocks adjacent to End energy machines.
 * If a neighboring block has this capability, the machine queries it for bonuses.
 */
public interface IEndEnergyBooster {
    
    /**
     * @return output bonus (0.0 = no bonus, 0.5 = +50%)
     */
    double getOutputBonus();
    
    /**
     * @return whether this booster is currently active (has mana/vis/LP/etc.)
     */
    boolean isActive();
    
    /**
     * Called when the machine consumes the booster's resource.
     * @param amount abstract "cost" per tick
     * @return true if successfully consumed
     */
    boolean consumeResource(int amount);
}

// Registration:
@CapabilityInject(IEndEnergyBooster.class)
public static Capability<IEndEnergyBooster> END_ENERGY_BOOSTER_CAPABILITY = null;
```

**机器扫描逻辑**：
```java
// Every 20 ticks, scan adjacent blocks for IEndEnergyBooster
private double scanForMagicBoosters() {
    double totalBonus = 0.0;
    for (EnumFacing facing : EnumFacing.VALUES) {
        BlockPos neighbor = getPos().offset(facing);
        TileEntity te = getWorld().getTileEntity(neighbor);
        if (te != null && te.hasCapability(END_ENERGY_BOOSTER_CAPABILITY, facing.getOpposite())) {
            IEndEnergyBooster booster = te.getCapability(END_ENERGY_BOOSTER_CAPABILITY, facing.getOpposite());
            if (booster != null && booster.isActive() && booster.consumeResource(1)) {
                totalBonus += booster.getOutputBonus();
            }
        }
    }
    return totalBonus;
}
```

### 5.5 配方扩展接口

魔法mod可以通过 GroovyScript 或 CraftTweaker 向末地系统的 RecipeMap 中添加新配方：

```groovy
// GroovyScript example: Thaumcraft addon adding a recipe
mods.gregtech.void_collector.recipeBuilder()
    .fluidInputs(fluid('liquid_vis') * 100)     // Thaumcraft liquid vis
    .fluidOutputs(fluid('void_essence') * 200)  // Double output with vis!
    .duration(100)
    .EUt(512)
    .buildAndRegister()

// Botania addon: Mana-enhanced resonance
mods.gregtech.ender_resonance_generator.recipeBuilder()
    .fluidInputs(fluid('resonant_ender_fluid') * 5, fluid('mana') * 50)  // Botania mana
    .EUt(-8192)  // Generates 8192 EU/t (fuel recipe)
    .duration(20)
    .buildAndRegister()
```

### 5.6 增强等级系统

不同魔法mod的增强可以叠加，但有上限防止过于OP：

| 增强来源数量 | 最大叠加倍率 | 说明 |
|------------|-----------|------|
| 单一mod加成 | ×2.0 | 一个魔法mod最多提供2倍加成 |
| 双mod叠加 | ×3.0 | 两个魔法mod组合最多3倍 |
| 三mod叠加 | ×3.5 | 收益递减 |
| 四mod及以上 | ×4.0 (硬上限) | 无论多少mod，最终乘数不超过4倍 |

```java
public static double clampMagicMultiplier(double rawMultiplier) {
    return Math.min(rawMultiplier, 4.0);
}
```

### 5.7 UI显示

末地机器的GUI中显示当前所有魔法加成来源：

```
[Machine Status Panel]
━━━━━━━━━━━━━━━━━━━━━━
Base Output: 2048 EU/t
Dimension Bonus: +50% (End)
━━━━ Magic Enhancements ━━━━
✦ Thaumcraft Vis Node: +30%
✦ Botania Mana Link: +25%  
✦ Blood Magic LP: +50%
━━━━━━━━━━━━━━━━━━━━━━
Total Multiplier: ×2.55 (capped from ×2.55)
Final Output: 5222 EU/t
```

### 5.8 为什么这个设计好？

**对GT本身**：
- 零硬依赖：没有任何魔法mod时系统完全正常工作
- 通过 `@Optional` + IMC + Capability 实现完全软连接
- 符合项目已有的 `Mods` enum + `IntegrationSubmodule` 模式

**对魔法mod开发者**：
- 清晰的API接口（`IEndEnergyModifier` / `IEndEnergyBooster` / Event）
- 三种接入方式可选（IMC简单、Capability灵活、Event实时）
- 不需要AT/Core Mod，纯API调用
- 可通过GroovyScript/CraftTweaker快速原型测试

**对玩家**：
- "科技+魔法"联合发展的游戏体验
- 明确的UI反馈（知道哪个mod提供了什么加成）
- 有上限（不会无限叠加变成无聊）
- 激励安装多个mod并协同使用

***

## 6. 完整能量流

### 6.1 入门路线（EV，虚空采集）

```
[末地岛屿边缘] 虚空能量采集器 (需 512 EU/t 维持)
         ↓ VoidEssence 50-150 mB/t
         ├─→ [末影共振发电机] ← VoidEssence 直接发电
         │        ↓ 2048-3072 EU/t
         │
         └─→ 存储/外运，用于下游高端路线

净输出: ~1500-2500 EU/t (减去采集器消耗)
```

### 6.2 中级路线（IV，水晶阵列+共振发电）

```
[末地主岛] 末影水晶阵列 (4个水晶连接)
         ↓ + VoidEssence 20mB/t (增幅模式)
         ↓ 6144 EU/t (龙蛋加成: 9216 EU/t)

[并行] 维度裂缝稳定器 (传送门附近)
         ↓ + VoidEssence 10mB/t
         ↓ 2048 EU/t

[化学反应器] EnderPearl + VoidEssence + Naquadah → ResonantEnderFluid
         ↓
[末影共振发电机] ← ResonantEnderFluid
         ↓ 4096-6144 EU/t

总输出: ~12000-17000 EU/t (IV级别充裕)
```

### 6.3 高级路线（LuV，龙息反应堆）

```
[对抗末影龙] → 收集 DragonBreath + 获得 DragonScale
         ↓
[化学反应器 IV] DragonBreath + VoidEssence + Naquadah
         ↓ → ConcentratedDragonBreath
         ↓
[龙息反应堆] + DragonScale催化
         ↓ 16384 EU/t (LuV级别!)
         ↓
[末影传输节点] → 跨维度传输到主世界/下界基地
         ↓ (消耗 ResonantEnderFluid 10mB/t)
         ↓ 传输效率 90%
         ↓
[主世界接收端] → 实际可用 ~14700 EU/t

总系统: 末地发电 → 跨维度传输 → 全基地供电
```

### 6.4 终极整合路线（跨维度联动）

```
[下界] 地热系统 → ~2000 EU/t (地热发电)
         ↓ (通过末影传输节点传输)
         ↓
[末地] 末影系统 → ~16000 EU/t (龙息+水晶+虚空)
         ↓ (通过末影传输节点传输)
         ↓
[主世界] 接收并分配 → ~16000 EU/t 供全基地使用
         ↑
         │ 所有跨维度传输消耗 ResonantEnderFluid
         │ ResonantEnderFluid 由末地的 VoidEssence 制备
         │ → 末地系统既是发电中心也是传输枢纽
```

***

## 7. 平衡性分析

### 7.1 与现有系统对比

| 发电方式 | 等级 | 稳定输出 | 燃料可再生性 | 前置要求 |
|---------|------|---------|-----------|---------|
| 大型燃烧引擎 | EV | 2048 EU/t | 需化石燃料(有限) | 石化体系 |
| **虚空能量采集** | **EV** | **~2000 EU/t** | **无限(虚空能量)** | **到达末地+VoidCrystal** |
| 核裂变反应堆 | IV | ~4000 EU/t | 铀(有限矿脉) | 核物理体系 |
| **末影水晶阵列** | **IV** | **~9000 EU/t** | **无限(水晶再生)** | **末地主岛+VoidEssence** |
| **末影共振发电机** | **IV** | **~6000 EU/t** | **需EnderPearl+Naquadah** | **末地化工** |
| 核聚变 | LuV | ~32000 EU/t | 氘/氚(可再生) | 核聚变全链 |
| **龙息反应堆** | **LuV** | **~16000 EU/t** | **龙息(需对抗龙)** | **龙战+LuV化工** |

### 7.2 平衡策略

**优势**：
- 部分路线燃料无限（虚空能量、水晶）
- 跨维度传输是独特功能
- 魔法增强提供附魔/药水等非能源收益

**限制**：
- 必须在末地运行主要设备（虚空采集、水晶阵列、裂缝稳定器）
- 到达末地本身是中后期（击败末影龙/末地传送门）
- 龙息路线需要反复对抗末影龙（风险、时间成本）
- ResonantEnderFluid 制备消耗 Naquadah（珍贵末地矿物）
- DragonScale 有限且难获取
- 跨维度传输有效率损耗（85-95%）

### 7.3 与下界地热系统的定位对比

| 维度 | 定位 | 输出水平 | 运营成本 | 独特价值 |
|------|------|---------|---------|---------|
| 下界 | 中端免费能源 | MV-EV (256-1920 EU/t) | 极低 | 低成本+矿物副产物 |
| 末地 | 高端可扩展能源 | EV-LuV (2000-16000 EU/t) | 中(流体消耗) | 跨维度传输+魔法增强 |

两者互补：
- 下界提供稳定的基础电力
- 末地提供高端峰值电力和跨维度传输枢纽
- 两者通过末影传输节点联动

***

## 8. 实现架构

### 8.1 底层

| 组件 | 位置 | 说明 |
|------|------|------|
| `EndDimensionUtils` | `gregtech.api.util` | 末地维度检测、传送门距离计算、水晶搜索等工具方法 |
| 新RecipeMap | `RecipeMaps.java` 扩展 | VOID_COLLECTOR, ENDER_RESONANCE_FUELS, DRAGON_BREATH_REACTOR_FUELS, ENDER_INFUSION |
| 新材料 | `HigherDegreeMaterials.java` 扩展 | VoidEssence, ResonantEnderFluid, DragonBreath, VoidCrystal, ResonantAlloy |
| 新方块 | `MetaBlocks` 扩展 | ResonantCasing, VoidSensorPanel, AntennaPillar |

### 8.2 功能层

| 组件 | 继承类 | 参考实现 |
|------|--------|---------|
| 虚空能量采集器 | `RecipeMapMultiblockController` (自定义逻辑) | 参考 `MetaTileEntityWindGenerator`（环境产出） |
| 末影水晶阵列 | `FuelMultiblockController` | 参考 `MetaTileEntityMagicEnergyAbsorber`（水晶连接）+ multiblock化 |
| 维度裂缝稳定器 | `FuelMultiblockController` (自定义逻辑) | 检测附近传送门方块 |
| 末影共振发电机 | `FuelMultiblockController` | 标准燃料多方块 |
| 龙息反应堆 | `FuelMultiblockController` | 参考 `LargeCombustionEngine`（催化剂机制） |
| 末影灌注台 | `RecipeMapMultiblockController` | 标准配方多方块 |
| 末影传输节点 | `TieredMetaTileEntity` + `WirelessEnergyService` | 参考 wireless hatch 实现 |

### 8.3 关键集成点

1. **维度检测**：`world.provider instanceof WorldProviderEnd`
2. **水晶搜索**：复用 `BiomeEndDecorator.getSpikesForWorld()` + `EntityEnderCrystal` 搜索（同 MagicEnergyAbsorber）
3. **传送门检测**：搜索 `Blocks.END_PORTAL` / `Blocks.END_GATEWAY` 方块
4. **无线传输**：通过 `WirelessEnergyService.insert/extract` 接入现有无线网络
5. **EnderAir链**：复用现有 Gas Collector → Vacuum → Distillation 链路
6. **EnderPearl材料**：已有 `plate`, `dust` 等形态，可直接用于配方
7. **龙蛋检测**：复用 `MagicEnergyAbsorber.updateDragonEggStatus()` 逻辑

***

## 9. MTE ID 分配建议

| ID范围 | 组件 | 说明 |
|--------|------|------|
| 1020 | 虚空能量采集器 | 多方块 |
| 1021 | 末影水晶阵列 | 多方块发电 |
| 1022 | 维度裂缝稳定器 | 多方块发电 |
| 1023 | 末影共振发电机 | 多方块发电 |
| 1024 | 龙息反应堆 | 多方块发电 |
| 1025 | 末地空气液化站 | 多方块处理 |
| 1026 | 末影灌注台 | 多方块处理 |
| 1027-1029 | 末影传输节点 IV/LuV/ZPM | 单方块 tiered |
| 1030-1032 | 龙蛋充能器 EV/IV/LuV | 单方块 tiered |

***

## 10. 实施路线图

### 阶段一：基础设施

1. `EndDimensionUtils` 工具类
2. 新材料/流体定义（VoidEssence, ResonantEnderFluid, DragonBreath, VoidCrystal, ResonantAlloy）
3. 新方块定义（ResonantCasing, VoidSensorPanel, AntennaPillar）
4. 新 RecipeMap 注册

### 阶段二：核心发电

5. 虚空能量采集器（环境感知多方块，VoidEssence产出）
6. 末影水晶阵列（扩展 MagicEnergyAbsorber 为多方块）
7. 末影共振发电机（标准燃料多方块）
8. 维度裂缝稳定器（传送门检测逻辑）

### 阶段三：高端发电

9. 龙息反应堆（LuV级）
10. 龙息流体化机制（流体单元收集）
11. ConcentratedDragonBreath 化学配方
12. DragonScale 掉落/获取机制

### 阶段四：传输与魔法

13. 末影传输节点（与 WirelessEnergyService 整合）
14. 末影灌注台（附魔系统）
15. 新附魔效果注册
16. 龙蛋充能器
17. 末影药水配方

### 阶段五：打磨

18. 末地空气液化站（便利性多方块）
19. 平衡性调优
20. 纹理与渲染（末影紫色粒子效果）
21. 音效（传送门嗡鸣、水晶共振）

***

## 11. 参考资料

- **MC 末地维度特性（1.12.2）**：
  - 维度ID: 1，`WorldProviderEnd`
  - 主岛（0,0中心）+ 外岛群（~1000格外，折跃门连接）
  - 末影水晶在黑曜石柱顶端，部分有铁栏杆笼
  - 末影龙可重复召唤（4个末影水晶放在返程传送门上）
  - 返程传送门在 (0, 64, 0) 附近
  - 末地折跃门（End Gateway）在龙被击杀后生成
  - Dragon's Breath 可用玻璃瓶从龙息云中收集

- **项目内参考实现**：
  - `MetaTileEntityMagicEnergyAbsorber` — 末影水晶发电单方块（`WorldProviderEnd` 检测 + `EntityEnderCrystal` 搜索）
  - `EnderAir` / `LiquidEnderAir` — 末地空气采集+液化+蒸馏链
  - `GAS_COLLECTOR_RECIPES.dimension(1)` — 维度限定配方注册方式
  - `WirelessEnergyService` — 无线能量网络服务接口
  - `CoverEnderFluidLink` / `CoverEnderItemLink` — 末影传输覆盖板（参考无线传输的 UI/逻辑）
  - `EnchantmentEnderDamage` — 已有的末影伤害附魔（参考附魔注册方式）
  - `MetaTileEntityForgeOfGods` — 超高端多方块参考（升级系统、模块系统）
  - `Naquadah` 矿脉 — 末地独有矿物（`naquadah_vein.json`）

- **物理/幻想参考**：
  - 真空能量/零点能（Zero-Point Energy）— 虚空能量的物理灵感
  - 卡西米尔效应 — 从"真空"中提取能量的理论基础
  - 末影珍珠传送 → 空间折叠/量子隧穿 — 共振发电的灵感
  - 龙息 → 等离子体/暗能量 — 龙息反应堆的灵感

***

## 附录A：三维度系统对比总览

| 维度 | 能源主题 | GT等级 | 核心资源 | 发电峰值 | 独特价值 | 限制 |
|------|---------|--------|---------|---------|---------|------|
| **主世界** | 化石/核能 | LV-IV | 煤/油/铀/氢 | ~4000 EU/t (核裂变) | 全面平衡 | 资源有限 |
| **下界** | 地热能 | MV-EV | 岩浆/地狱岩 | ~2000 EU/t | 低成本+矿物副产物 | 高温环境 |
| **末地** | 维度能量 | EV-LuV | 虚空/水晶/龙息 | ~16000 EU/t | 跨维度传输+魔法增强 | 需对抗龙 |

三者形成完整的**渐进式跨维度能源网络**：
```
[主世界·LV-IV] 化石→核能 (基础自给)
       ↕ 末影传输节点
[下界·MV-EV] 地热能 (免费补充)
       ↕ 末影传输节点
[末地·EV-LuV] 维度能量 (高端峰值+传输枢纽)
```

## 附录B：魔法Mod增强接口总览

| 接入方式 | 适用场景 | 复杂度 | 说明 |
|---------|---------|--------|------|
| IMC消息注册 `IEndEnergyModifier` | 全局性、基于世界状态的加成 | 低 | mod加载时一次性注册 |
| Forge Capability `IEndEnergyBooster` | 相邻方块实时交互 | 中 | 魔法方块放在机器旁边即生效 |
| Forge Event `EndEnergyOutputEvent` | 动态条件判断 | 中 | 每次机器计算输出时触发 |
| RecipeMap 配方注入 (GroovyScript/CT) | 新增魔法流体配方 | 低 | 无需写Java代码 |

| 魔法Mod | 推荐接入方式 | 增强主题 | 最大加成 |
|---------|-----------|---------|---------|
| Thaumcraft | IMC + Capability(Vis Node) | Vis灌注+虚空金属 | +50% 输出, -20% EU |
| Botania | Capability(Mana) + RecipeMap | Mana灌注+Gaia催化 | +40% 输出 |
| Blood Magic | Capability(LP) + Event | LP/恶魔意志+祭坛共振 | +100% VoidEssence |
| Astral Sorcery | IMC + RecipeMap(Liquid Starlight) | 星光灌注+星座加成 | +40% 全机器 |
| Ars Magica | Capability(Mana) | 法力通用加成 | +30% 输出 |
| Electroblob's Wizardry | Event | 元素共振 | +15-30% |
| Abyssalcraft | IMC | 深渊能量共振 | +25% 裂缝稳定器 |
