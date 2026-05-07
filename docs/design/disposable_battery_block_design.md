# 一次性电池方块 & 氢燃料电池多方块发电机 — 设计文档

> **范围**：共享同一基类的单方块一次性化学能源（A: 锌锰干电池、B: 铅酸电池），以及一个可持续运行的**多方块**氢燃料电池发电机。三者均深度整合现有 GregTech 化工管线。

***

## 1. 总体概述

### 1.1 背景动机

当前能源体系已提供：

- **可充电物品电池**（插入 Battery Buffer 方块使用）
- **流体消耗型发电机方块**（燃油/燃气/蒸汽轮机，从储罐中消耗流体）
- **多方块储能结构**（电力子站、Battery Block 多方块）

缺少的是**单方块、自包含的一次性电源**——一种合成后放置、以预装的化学能量向网络供电、能量耗尽后自毁或变为惰性"放电块"的方块。本设计填补这一空白。

### 1.2 三个组件系列

#### 一次性电池方块系列（A 系列，LV \~ UV，共 8 种）

| Tier | 名称       | 核心化学体系               | 现实原型           |
| ---- | -------- | -------------------- | -------------- |
| LV   | 锌锰干电池方块  | Zn + MnO₂ + KOH      | 碱性干电池          |
| MV   | 锂锰电池方块   | Li + MnO₂            | 一次性锂锰电池（CR 系列） |
| HV   | 镍镉电池方块   | Ni(OH)₂ + Cd + KOH   | 镉镍蓄电池（工业级）     |
| EV   | 铅酸电池方块   | Pb + H₂SO₄           | 铅酸蓄电池          |
| IV   | 钒液流电池方块  | V₂O₅ + H₂SO₄         | 钒氧化还原液流电池      |
| LuV  | 磷酸铁锂电池方块 | LiFePO₄              | 磷酸铁锂（LFP）电池    |
| ZPM  | 钴酸锂电池方块  | LiCoO₂ + PVDF        | 锂钴氧化物（LCO）电池   |
| UV   | 三元锂电池方块  | NMC（Ni+Mn+Co）+ LiPF₆ | NMC 811 三元锂电池  |

#### C 系列（多方块发电机）

| ID | 名称       | 类型                   | Tier    | 核心化学                   |
| -- | -------- | -------------------- | ------- | ---------------------- |
| C  | 氢燃料电池发电机 | **多方块**可持续发电机（消耗 H₂） | EV → IV | H₂ + O₂ → H₂O，支持 O₂ 增益 |

***

## 2. 类继承架构

```
TieredMetaTileEntity
└── WorkableTieredMetaTileEntity
    └── SimpleGeneratorMetaTileEntity          （现有，使用 FuelRecipeLogic）
        ├── MetaTileEntitySingleCombustion     （现有）
        ├── MetaTileEntitySingleTurbine        （现有）
        │
        └── MetaTileEntityDisposableBatteryBase  ← 新增基类（A 系列共用）
            ├── MetaTileEntityZincManganeseCell    ← LV （A0）
            ├── MetaTileEntityLithiumManganeseCell ← MV （A1）
            ├── MetaTileEntityNickelCadmiumCell    ← HV （A2）
            ├── MetaTileEntityLeadAcidBattery      ← EV （A3）
            ├── MetaTileEntityVanadiumFlowCell     ← IV （A4）
            ├── MetaTileEntityLFPBattery           ← LuV（A5）
            ├── MetaTileEntityLCOBattery           ← ZPM（A6）
            └── MetaTileEntityNMCBattery           ← UV （A7）

MultiblockWithDisplayBase
└── RecipeMapMultiblockController
    └── FuelMultiblockController               （现有，使用 MultiblockFuelRecipeLogic）
        ├── MetaTileEntityLargeCombustionEngine （现有）
        ├── MetaTileEntityLargeTurbine         （现有）
        │
        └── MetaTileEntityHydrogenFuelCell     ← 新增多方块（C）
                （内部类）HydrogenFuelCellWorkableHandler
                          extends MultiblockFuelRecipeLogic
```

### 2.1 为什么 C 选择 `FuelMultiblockController`？

`FuelMultiblockController` 已提供：

- `MultiblockFuelRecipeLogic` — 并联燃料消耗、能量输出、无过压
- `EnergyContainerList` — 由 `OUTPUT_ENERGY` hatch 能力自动组装
- 标准多方块结构：输入流体仓、输出流体仓、Dynamo 仓、维护仓、消音仓
- `isDynamoFull()` / `isDynamoTierTooLow()` 安全检查
- `ProgressBarMultiblock` GUI（燃料条 + 可选增益条）

氢燃料电池在此基础上增加：

- **O₂ 增益通道** — 与 `LargeCombustionEngine` 的氧气增益完全对称，注入 O₂ 后 H₂ 消耗减半同时输出翻倍
- **Water 副产物** — 通过输出流体仓导出（H₂ + O₂ → H₂O）
- **无转子槽** — 与大型涡轮不同，燃料电池无旋转件，结构模式中不含 `ROTOR_HOLDER`

***

## 3. `MetaTileEntityDisposableBatteryBase`（A 系列共用基类）

### 3.1 职责

1. 持有**固定总 EU 储量**（构造时确定，放置后不可改变）。
2. 每游戏 tick 将 `outputVoltage × outputAmperage` EU 通过现有 `EnergyContainerHandler` emitter 路径推入网络。
3. 在 NBT 中追踪剩余 EU，确保 chunk 卸载/重载后数据不丢失。
4. 当 EU 归零时执行**耗尽动作**（抽象方法，由子类决定：自毁 vs 掉落方块）。
5. 在方块正面渲染**充电进度条**（复用 `GTGuiTextures` 进度条）。

### 3.2 关键字段

```java
// package: gregtech.common.metatileentities.electric

public abstract class MetaTileEntityDisposableBatteryBase extends TieredMetaTileEntity {

    // --- 静态配置（构造时确定，放置后不可变） ---
    protected final long maxStoredEU;           // 方块总 EU 容量
    protected final long outputVoltagePerTick;  // 每 tick 输出电压（= GTValues.V[tier]）
    protected final long outputAmperagePerTick; // 每 tick 输出安培数

    // --- 运行时状态（NBT 持久化） ---
    protected long remainingEU;  // 可变，每 tick 递减
    protected boolean depleted;  // remainingEU == 0 且动作已执行时置 true

    // NBT 键
    private static final String NBT_REMAINING_EU = "RemainingEU";
    private static final String NBT_DEPLETED     = "Depleted";
}
```

### 3.3 能量输出策略

与 `SimpleGeneratorMetaTileEntity`（委托给 `FuelRecipeLogic`）不同，一次性电池方块直接在 `update()` 中控制输出：

```
每服务器 tick 执行 update()：
  if (depleted) return;
  if (world.isRemote) return;

  long canEmit = Math.min(remainingEU, outputVoltagePerTick * outputAmperagePerTick);
  long emitted = pushEnergyToNetwork(canEmit);   // 使用 EnergyContainerHandler
  remainingEU -= emitted;

  if (remainingEU <= 0) {
      onDepleted();   // 抽象钩子
  }
```

`EnergyContainerHandler` 初始化为 **emitter**（输入电压 = 0，输出电压 = `V[tier]`），与 `TieredMetaTileEntity.reinitializeEnergyContainer()` 的现有发电机模式完全一致。

### 3.4 抽象钩子

```java
/**
 * Called once on the server side when remainingEU reaches zero.
 * Subclasses decide what happens to the block (destroy / replace / drop items).
 */
protected abstract void onDepleted();
```

### 3.5 NBT 序列化

```java
@Override
public NBTTagCompound writeToNBT(NBTTagCompound data) {
    super.writeToNBT(data);
    data.setLong(NBT_REMAINING_EU, remainingEU);
    data.setBoolean(NBT_DEPLETED, depleted);
    return data;
}

@Override
public void readFromNBT(NBTTagCompound data) {
    super.readFromNBT(data);
    remainingEU = data.getLong(NBT_REMAINING_EU);
    // 初次放置时 NBT 不含此键，回退到满容量
    if (remainingEU == 0 && !data.hasKey(NBT_REMAINING_EU)) {
        remainingEU = maxStoredEU;
    }
    depleted = data.getBoolean(NBT_DEPLETED);
}
```

### 3.6 GUI

复用 `WorkableTieredMetaTileEntity` MUI2 面板框架，添加一个 `ProgressWidget`，绑定 `remainingEU / maxStoredEU` 作为"充电"进度条。无流体/物品槽（库存大小为 0）。

### 3.7 Tooltip

```
[机器名称]
总容量：<maxStoredEU> EU  （<VNF[tier]> 级）
输出：<V[tier]> EU/t × <安培数> A
剩余：<remainingEU> EU  （<百分比>%）
⚠ 此电池方块为一次性，耗尽后无法再用。
```

***

## 4. A 系列变体规格（LV \~ UV）

### 4.0 设计通则

所有变体共用以下规则：

- **`outputVoltage`** = `GTValues.V[tier]`
- **`outputAmperage`** = `4`（统一 4A 输出，与现有 Battery Buffer 的 4A 默认一致）
- **耗尽动作**：自毁 + 掉落化学副产物
- **合成终点机器**：组装机（Assembler），对应 Tier 的 EU/t

**容量设计原则**：一次性电池的核心价值是容量优势——用合成材料换取远超可充电电池的单次爆发储能。以\*\*至少能在满功率下持续输出 2 小时（144 000 tick）\*\*为容量下限，向上取整到整洁数值。

| Tier | EU/t      | 2h 最低容量    | 最终容量         | 持续时长  | vs 最强可充电 | vs Lapo/Cluster     |
| ---- | --------- | ---------- | ------------ | ----- | -------- | ------------------- |
| LV   | 128       | 18.4 M EU  | **20 M EU**  | 2.2 h | ×167     | ×167 vs 120K        |
| MV   | 512       | 73.7 M EU  | **80 M EU**  | 2.2 h | ×190     | ×190 vs 420K        |
| HV   | 2 048     | 294.9 M EU | **300 M EU** | 2 h   | ×167     | ×167 vs 1.8M        |
| EV   | 8 192     | 1.18 G EU  | **1.2 G EU** | 2 h   | ×117     | ×117 vs 10.24M      |
| IV   | 32 768    | 4.72 G EU  | **5 G EU**   | 2.1 h | ×20      | ×20 vs Lapo(250M)   |
| LuV  | 131 072   | 18.9 G EU  | **20 G EU**  | 2.1 h | ×20      | ×20 vs Lapo(1G)     |
| ZPM  | 524 288   | 75.5 G EU  | **80 G EU**  | 2.1 h | ×20      | ×20 vs Cluster(4G)  |
| UV   | 2 097 152 | 302 G EU   | **320 G EU** | 2.1 h | ×16      | ×16 vs Cluster(20G) |

***

### 4.1 A0 — `MetaTileEntityZincManganeseCell`（LV，Zn-MnO₂）

**现实原型**：碱性锌锰干电池（Alkaline Dry Cell）

| 参数               | 值                                   |
| ---------------- | ----------------------------------- |
| Tier             | LV (1)，32V                          |
| `maxStoredEU`    | `20_000_000 EU`（20 M EU，满功率 2.2 小时） |
| `outputVoltage`  | `32 V`                              |
| `outputAmperage` | `4 A`（128 EU/t）                     |
| 副产物              | `dustSmall, ZincOxide, 4`           |

**化工制造链：**

```
// 步骤 1 — 混合机
Zinc（dust, 4）+ MnO₂（dust, 8）+ Potassium Hydroxide（fluid, 500 mB）
→ Zinc-Manganese Paste（dust, 12）
时长：200t  EU/t：VA[LV]

// 步骤 2 — 组装机
Iron Frame（1）+ Iron Plate（4）+ Copper Wire Single（2）
+ Zinc-Manganese Paste（dust, 12）+ Polyethylene（fluid, 144 mB）
→ 锌锰干电池方块（1）
时长：100t  EU/t：VA[LV]
```

**新增材料**：`MnO₂`（ManganeseIVOxide）需在 `FirstDegreeMaterials.java` 补充（若尚未存在）。

***

### 4.2 A1 — `MetaTileEntityLithiumManganeseCell`（MV，Li-MnO₂）

**现实原型**：一次性锂锰电池（CR 系列，相机/工业传感器用）

| 参数               | 值                                                      |
| ---------------- | ------------------------------------------------------ |
| Tier             | MV (2)，128V                                            |
| `maxStoredEU`    | `80_000_000 EU`（80 M EU，满功率 2.2 小时）                    |
| `outputVoltage`  | `128 V`                                                |
| `outputAmperage` | `4 A`（512 EU/t）                                        |
| 副产物              | `dustSmall, LithiumChloride, 2` + `dustSmall, MnO₂, 4` |

**化工制造链：**

```
// 步骤 1 — 混合机
Lithium（dust, 2）+ MnO₂（dust, 8）
→ Lithium-Manganese Electrode（dust, 10）
时长：200t  EU/t：VA[MV]

// 步骤 2 — 组装机
Steel Frame（1）+ Steel Plate（4）+ Tin Wire Single（4）
+ Lithium-Manganese Electrode（dust, 10）
+ Polyethylene（fluid, 288 mB）
→ 锂锰电池方块（1）
时长：150t  EU/t：VA[MV]
```

***

### 4.3 A2 — `MetaTileEntityNickelCadmiumCell`（HV，Ni-Cd）

**现实原型**：工业级镉镍蓄电池（Nickel-Cadmium，NiCd）

| 参数               | 值                                                     |
| ---------------- | ----------------------------------------------------- |
| Tier             | HV (3)，512V                                           |
| `maxStoredEU`    | `300_000_000 EU`（300 M EU，满功率 2 小时）                   |
| `outputVoltage`  | `512 V`                                               |
| `outputAmperage` | `4 A`（2 048 EU/t）                                     |
| 副产物              | `dustSmall, Cadmium, 4` + `dustSmall, NickelOxide, 4` |

**化工制造链：**

```
// 步骤 1 — 化学浴
Cadmium（plate, 4）+ Nickel Hydroxide（dust, 8）+ Potassium Hydroxide（fluid, 1000 mB）
→ Nickel-Cadmium Electrode Stack（4）
时长：300t  EU/t：VA[HV]

// 步骤 2 — 组装机
Stainless Steel Frame（1）+ Stainless Steel Plate（4）
+ Copper Wire Double（4）
+ Nickel-Cadmium Electrode Stack（4）
+ Polyethylene（fluid, 576 mB）
→ 镍镉电池方块（1）
时长：200t  EU/t：VA[HV]
```

**新增中间体材料**：`NickelHydroxide`（Ni(OH)₂），需在材料系统补充（若不存在）。

***

### 4.4 A3 — `MetaTileEntityLeadAcidBattery`（EV，Pb-H₂SO₄）

**现实原型**：铅酸蓄电池（Lead-Acid Battery）

| 参数               | 值                                                                            |
| ---------------- | ---------------------------------------------------------------------------- |
| Tier             | EV (4)，2 048V                                                                |
| `maxStoredEU`    | `1_200_000_000 EU`（1.2 G EU，满功率 2 小时）                                        |
| `outputVoltage`  | `2_048 V`                                                                    |
| `outputAmperage` | `4 A`（8 192 EU/t）                                                            |
| 副产物              | `dustSmall, Lead, 8` + `DilutedSulfuricAcid（fluid, 1 000 mB）`（以 bucket 形式掉落） |

**化工制造链：**

```
// 步骤 1 — 化学浴
Lead Plate（6）+ Sulfuric Acid（fluid, 2 000 mB）
→ Lead-Acid Electrode Stack（6）
时长：400t  EU/t：VA[MV]

// 步骤 2 — 组装机
Titanium Frame（1）+ Glass Plate（4）
+ Aluminium Wire Double（4）
+ Lead-Acid Electrode Stack（6）
+ Polyethylene（fluid, 576 mB）
→ 铅酸电池方块（1）
时长：300t  EU/t：VA[EV]
```

***

### 4.5 A4 — `MetaTileEntityVanadiumFlowCell`（IV，V₂O₅-H₂SO₄）

**现实原型**：全钒液流电池（All-Vanadium Redox Flow Battery，VRFB）

| 参数               | 值                                                                                  |
| ---------------- | ---------------------------------------------------------------------------------- |
| Tier             | IV (5)，8 192V                                                                      |
| `maxStoredEU`    | `5_000_000_000 EU`（5 G EU，满功率 2.1 小时）                                              |
| `outputVoltage`  | `8_192 V`                                                                          |
| `outputAmperage` | `4 A`（32 768 EU/t）                                                                 |
| 副产物              | `dustSmall, VanadiumPentoxide, 4` + `DilutedSulfuricAcid（fluid, 2 000 mB）`（bucket） |

**化工制造链：**

```
// 步骤 1 — 化学反应器
Vanadium Pentoxide（dust, 4）+ Sulfuric Acid（fluid, 3 000 mB）
→ Vanadium Electrolyte（fluid, 3 000 mB）  ← 新增液态材料
时长：400t  EU/t：VA[EV]

// 步骤 2 — 组装机
Titanium Frame（1）+ Titanium Plate（4）
+ Tungsten Wire Double（4）
+ Ion Exchange Membrane（2）  ← 与 PEM Membrane 不同，钒用膜
+ Vanadium Electrolyte（fluid, 3 000 mB）
→ 钒液流电池方块（1）
时长：400t  EU/t：VA[IV]
```

**新增材料**：`VanadiumElectrolyte`（流体）。\
**新增中间体**：`IonExchangeMembrane`（与 C 系列 `PEMMembrane` 不同，需独立注册）。

***

### 4.6 A5 — `MetaTileEntityLFPBattery`（LuV，LiFePO₄）

**现实原型**：磷酸铁锂电池（Lithium Iron Phosphate，LFP）

| 参数               | 值                                                          |
| ---------------- | ---------------------------------------------------------- |
| Tier             | LuV (6)，32 768V                                            |
| `maxStoredEU`    | `20_000_000_000 EU`（20 G EU，满功率 2.1 小时）                    |
| `outputVoltage`  | `32_768 V`                                                 |
| `outputAmperage` | `4 A`（131 072 EU/t）                                        |
| 副产物              | `dustSmall, Lithium, 4` + `dustSmall, IronIIIPhosphate, 4` |

**化工制造链：**

```
// 步骤 1 — 化学反应器
Lithium（dust, 4）+ Iron II Phosphate（dust, 8）
→ LFP Cathode Powder（dust, 12）
时长：400t  EU/t：VA[IV]

// 步骤 2 — 组装机
Iridium Frame（1）+ Iridium Plate（4）
+ Tungsten Wire Quadruple（4）
+ LFP Cathode Powder（dust, 12）
+ Carbon Nanotube Film（2）  ← 导电集流体
+ Polybenzimidazole（fluid, 576 mB）  ← 高温聚合物封装
→ 磷酸铁锂电池方块（1）
时长：500t  EU/t：VA[LuV]
```

**新增材料**：`LFPCathodePowder`（粉末）、`CarbonNanotubeFilm`（板件形式）。

***

### 4.7 A6 — `MetaTileEntityLCOBattery`（ZPM，LiCoO₂）

**现实原型**：锂钴氧化物电池（Lithium Cobalt Oxide，LCO），高能量密度

| 参数               | 值                                                           |
| ---------------- | ----------------------------------------------------------- |
| Tier             | ZPM (7)，131 072V                                            |
| `maxStoredEU`    | `80_000_000_000 EU`（80 G EU，满功率 2.1 小时）                     |
| `outputVoltage`  | `131_072 V`                                                 |
| `outputAmperage` | `4 A`（524 288 EU/t）                                         |
| 副产物              | `dustSmall, Cobalt, 4` + `dustSmall, LithiumCobaltOxide, 4` |

**化工制造链：**

```
// 步骤 1 — 化学反应器
Lithium（dust, 2）+ Cobalt Oxide（dust, 4）
→ Lithium Cobalt Oxide（dust, 6）  ← LiCoO₂，可能已存在
时长：500t  EU/t：VA[LuV]

// 步骤 2 — 组装机
Osmium Frame（1）+ Osmium Plate（4）
+ Naquadah Wire Quadruple（4）
+ Lithium Cobalt Oxide（dust, 12）
+ PVDF（fluid, 576 mB）  ← 聚偏氟乙烯粘结剂
+ Carbon Nanotube Film（4）
→ 钴酸锂电池方块（1）
时长：600t  EU/t：VA[ZPM]
```

**新增材料**：`PVDF`（聚偏氟乙烯，Polyvinylidene Fluoride，液态）。

***

### 4.8 A7 — `MetaTileEntityNMCBattery`（UV，NMC 811）

**现实原型**：NMC 811 三元锂电池（Ni₀.₈Mn₀.₁Co₀.₁O₂），当前最高商业能量密度

| 参数               | 值                                                                           |
| ---------------- | --------------------------------------------------------------------------- |
| Tier             | UV (8)，524 288V                                                             |
| `maxStoredEU`    | `320_000_000_000 EU`（320 G EU，满功率 2.1 小时）                                   |
| `outputVoltage`  | `524_288 V`                                                                 |
| `outputAmperage` | `4 A`（2 097 152 EU/t）                                                       |
| 副产物              | `dustSmall, Nickel, 8` + `dustSmall, Cobalt, 2` + `dustSmall, Manganese, 2` |

**化工制造链：**

```
// 步骤 1 — 混合机
Nickel（dust, 8）+ Cobalt（dust, 1）+ Manganese（dust, 1）+ Oxygen（fluid, 2000 mB）
→ NMC Precursor（dust, 12）
时长：400t  EU/t：VA[ZPM]

// 步骤 2 — 化学反应器
NMC Precursor（dust, 12）+ Lithium Hydroxide（fluid, 1000 mB）
→ NMC Cathode（dust, 12）  ← NMC 811 活性材料
时长：500t  EU/t：VA[ZPM]

// 步骤 3 — 组装机
Neutronium Frame（1）+ Neutronium Plate（4）
+ Europium Wire Octuple（4）
+ NMC Cathode（dust, 12）
+ LiPF₆ Electrolyte（fluid, 1000 mB）  ← 六氟磷酸锂电解液
+ Carbon Nanotube Film（6）
→ 三元锂电池方块（1）
时长：800t  EU/t：VA[UV]
```

**新增材料**：`NMCPrecursor`（粉末）、`NMCCathode`（粉末）、`LiPF6Electrolyte`（液体，六氟磷酸锂溶液）。

***

## 5. A 系列 `onDepleted()` 通用实现模式

所有变体的 `onDepleted()` 结构完全一致，差异仅在副产物列表。基类提供一个受保护方法统一处理：

```java
// In MetaTileEntityDisposableBatteryBase

/**
 * Subclasses call this with their specific byproduct stacks.
 * Drops all byproducts at the block position, then removes the block.
 */
protected final void depleteAndDrop(ItemStack... byproducts) {
    depleted = true;
    World world = getWorld();
    BlockPos pos = getPos();
    if (world != null && !world.isRemote) {
        for (ItemStack stack : byproducts) {
            if (!stack.isEmpty()) {
                Block.spawnAsEntity(world, pos, stack);
            }
        }
        world.setBlockToAir(pos);
    }
}
```

**各变体的** **`onDepleted()`** **仅需一行：**

```java
// A0 — 锌锰
@Override
protected void onDepleted() {
    depleteAndDrop(OreDictUnifier.get(OrePrefix.dustSmall, Materials.Zincite, 4));
}

// A3 — 铅酸（含流体 bucket）
@Override
protected void onDepleted() {
    depleteAndDrop(
        OreDictUnifier.get(OrePrefix.dustSmall, Materials.Lead, 8),
        FluidUtil.getFilledBucket(new FluidStack(Materials.DilutedSulfuricAcid.getFluid(), 1000))
    );
}
// 其余变体以此类推
```

***

## 6. 组件 C — `MetaTileEntityHydrogenFuelCell`（EV，多方块发电机）

### 6.1 设计原则

氢燃料电池是一个**多方块燃料发电机**，与 `MetaTileEntityLargeCombustionEngine` 和 `MetaTileEntityLargeTurbine` 并列。它：

- 从输入流体仓持续消耗 **Hydrogen** 流体
- 通过 **Dynamo Hatch**（`OUTPUT_ENERGY` 能力）向网络输出电力
- 通过输出流体仓导出 **Water** 副产物
- 可选注入 **Oxygen** 获得增益（镜像燃油发动机的 O₂ 增益）
- **无需转子槽**（纯电化学，无旋转件）

这使其与四条现有化工链深度耦合：

| 化工链         | 使用材料               |
| ----------- | ------------------ |
| 氢气生产链       | 水电解 / 蒸汽裂解副产物      |
| 聚合物化工       | PTFE（膜材料）、PBI（高级膜） |
| PGM 铂族金属催化链 | 铂催化剂               |
| 氧气生产链       | 电解副产物 / 空气分离       |

### 6.2 多方块结构

控制器放置在 3×3×3 结构的**正面**：

```
第 1 层 — 前面（进气面）：
  A A A
  A C A     C = 控制器，A = 不锈钢洁净外壳
  A A A

第 2 层 — 中间（反应腔）：
  C C C
  C G C     G = 齿轮箱（不锈钢齿轮箱）
  C C C     C = 不锈钢洁净外壳  [H 槽位：各类仓]

第 3 层 — 后面（能量输出面）：
  C C C
  C C C     C = 不锈钢洁净外壳  [H 槽位：各类仓]
  C C C
```

结构模式（与 `MetaTileEntityLargeCombustionEngine` 风格一致）：

```java
DeclarativePatternBuilder.start()
    .aisle("CCC", "CCC", "CCC")         // 第 3 层 — 后
    .aisle("CHC", "HGH", "CHC")         // 第 2 层 — 中
    .aisle("CCC", "CYC", "CCC")         // 第 1 层 — 前
    .where('Y', selfPredicate())
    .where('G', states(getGearboxState()))
    .where('C', states(getCasingState()))
    .casing('H', CasingDefinition.simple(getCasingState(), "gregtech.machine.casing.stainless_clean"))
        .withOptionalHatches(MultiblockAbility.MAINTENANCE_HATCH, 1)
        .withOptionalHatches(MultiblockAbility.MUFFLER_HATCH, 1)
        .withOptionalHatches(MultiblockAbility.IMPORT_FLUIDS, 4)   // H₂ + O₂ 输入
        .withOptionalHatches(MultiblockAbility.EXPORT_FLUIDS, 2)   // H₂O 输出
        .withOptionalHatches(MultiblockAbility.OUTPUT_ENERGY, 1)   // Dynamo Hatch（EV+）
    .build()
```

**外壳材料选用：**

| 组件  | 方块                                    | 依据             |
| --- | ------------------------------------- | -------------- |
| 主外壳 | `STAINLESS_CLEAN`                     | EV 级标准；不锈钢耐氢腐蚀 |
| 齿轮箱 | `STAINLESS_STEEL_GEARBOX`             | 与不锈钢外壳配套；视觉一致性 |
| 渲染器 | `Textures.STAINLESS_CLEAN_CASING`（现有） | 无需新建基础外壳贴图     |

### 6.3 MTE 类骨架

```java
// package: gregtech.common.metatileentities.multi.electric.generator

public class MetaTileEntityHydrogenFuelCell
        extends FuelMultiblockController
        implements ITieredMetaTileEntity, ProgressBarMultiblock {

    // Stacks reused every tick — static constants avoid allocation
    private static final FluidStack OXYGEN_STACK = Materials.Oxygen.getFluid(20);

    private boolean boostAllowed;  // true when dynamo hatch tier >= EV+1

    public MetaTileEntityHydrogenFuelCell(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.HYDROGEN_FUEL_CELL_FUELS, GTValues.EV);
        this.recipeMapWorkable = new HydrogenFuelCellWorkableHandler(this);
        this.recipeMapWorkable.setMaximumOverclockVoltage(GTValues.V[GTValues.EV]);
    }

    // createMetaTileEntity, createStructurePattern,
    // getBaseTexture, getFrontOverlay, formStructure,
    // configureDisplayText, configureErrorText, addInformation,
    // hasMufflerMechanics → 详见下方 6.4 – 6.7
}
```

### 6.4 内部工作处理器 — `HydrogenFuelCellWorkableHandler`

继承 `MultiblockFuelRecipeLogic`，关键覆盖方法：

```java
private static class HydrogenFuelCellWorkableHandler extends MultiblockFuelRecipeLogic {

    private boolean isOxygenBoosted = false;
    private final MetaTileEntityHydrogenFuelCell fuelCell;

    // --- O₂ 增益逻辑（镜像 LargeCombustionEngineWorkableHandler） ---

    @Override
    protected boolean shouldSearchForRecipes() {
        checkOxygen();
        return super.shouldSearchForRecipes();
    }

    private void checkOxygen() {
        if (fuelCell.boostAllowed) {
            IMultipleTankHandler tank = fuelCell.getInputFluidInventory();
            isOxygenBoosted = OXYGEN_STACK.isFluidStackIdentical(tank.drain(OXYGEN_STACK, false));
        }
    }

    @Override
    protected void updateRecipeProgress() {
        if (canRecipeProgress && drawEnergy(recipeEUt, true)) {
            drainOxygen();
            drawEnergy(recipeEUt, false);
            if (++progressTime > maxProgressTime) {
                completeRecipe();
            }
        }
    }

    private void drainOxygen() {
        if (isOxygenBoosted && totalContinuousRunningTime % 20 == 0) {
            fuelCell.getInputFluidInventory().drain(OXYGEN_STACK, true);
        }
    }

    // --- 产量增益 ---

    @Override
    public long getMaxVoltage() {
        // O₂ 增益使有效并联电压加倍 → EU/t 翻倍
        return isOxygenBoosted ? GTValues.V[GTValues.EV] * 2 : GTValues.V[GTValues.EV];
    }

    @Override
    protected long boostProduction(long production) {
        // 与 LargeCombustionEngine 常规模式相同的 150% 效率模式：
        // 配方产出 2A EV → 增益后输出 3A EV
        return isOxygenBoosted ? production * 3 / 2 : production;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        isOxygenBoosted = false;
    }
}
```

**能量输出汇总：**

| 状态    | 基础配方 EU/t | 并联数                                               | 实际输出              |
| ----- | --------- | ------------------------------------------------- | ----------------- |
| 无 O₂  | `VA[EV]`  | `V[EV] / VA[EV]`                                  | 2 048 EU/t（1× EV） |
| O₂ 增益 | `VA[EV]`  | `V[EV]*2 / VA[EV]`（×2 并联）再 `boostProduction ×1.5` | 6 144 EU/t（3× EV） |

### 6.5 `formStructure` — 增益权限判断

```java
@Override
protected void formStructure(PatternMatchContext context) {
    super.formStructure(context);
    IEnergyContainer dynamo = getEnergyContainer();
    // 仅当 Dynamo Hatch 输出电压 >= 下一 Tier 时允许 O₂ 增益
    this.boostAllowed = dynamo != null && dynamo.getOutputVoltage() >= GTValues.V[GTValues.EV + 1];
}
```

### 6.6 结构模式完整代码

```java
@Override
protected BlockPattern createStructurePattern() {
    return DeclarativePatternBuilder.start()
        .aisle("CCC", "CCC", "CCC")         // 第 3 层 — 后
        .aisle("CHC", "HGH", "CHC")         // 第 2 层 — 中
        .aisle("CCC", "CYC", "CCC")         // 第 1 层 — 前（进气）
        .where('Y', selfPredicate())
        .where('G', states(getGearboxState()))
        .where('C', states(getCasingState()))
        .casing('H', CasingDefinition.simple(getCasingState(), "gregtech.machine.casing.stainless_clean"))
            .withOptionalHatches(MultiblockAbility.MAINTENANCE_HATCH, 1)
            .withOptionalHatches(MultiblockAbility.MUFFLER_HATCH, 1)
            .withOptionalHatches(MultiblockAbility.IMPORT_FLUIDS, 4)
            .withOptionalHatches(MultiblockAbility.EXPORT_FLUIDS, 2)
            .withOptionalHatches(MultiblockAbility.OUTPUT_ENERGY, 1)
        .build();
}

public IBlockState getCasingState() {
    return MetaBlocks.METAL_CASING.getState(MetalCasingType.STAINLESS_CLEAN);
}

public IBlockState getGearboxState() {
    return MetaBlocks.TURBINE_CASING.getState(TurbineCasingType.STAINLESS_STEEL_GEARBOX);
}
```

### 6.7 GUI 进度条（`ProgressBarMultiblock`）

三条进度条，镜像 `MetaTileEntityLargeCombustionEngine`：

| 进度条    | 贴图                                             | 内容                                 |
| ------ | ---------------------------------------------- | ---------------------------------- |
| 燃料（H₂） | `GTGuiTextures.PROGRESS_BAR_LCE_FUEL`          | H₂ 当前量 / 最大量                       |
| 水产出    | `GTGuiTextures.PROGRESS_BAR_LCE_FUEL`（蓝色调另设贴图） | 输出仓中的水量                            |
| O₂ 增益  | `GTGuiTextures.PROGRESS_BAR_LCE_OXYGEN`        | O₂ 当前量 / 最大量；Dynamo 级别不足时显示"增益不可用" |

```java
@Override
public int getProgressBarCount() { return 3; }

@Override
public void registerBars(List<UnaryOperator<TemplateBarBuilder>> bars, PanelSyncManager syncManager) {
    // bar 0: H₂ 燃料量
    // bar 1: Water 副产物量（输出流体仓）
    // bar 2: O₂ 增益量
    // （实现镜像 MetaTileEntityLargeCombustionEngine.registerBars()）
}
```

### 6.8 新 RecipeMap

```java
// In RecipeMaps.java
public static final RecipeMap<FuelRecipeBuilder> HYDROGEN_FUEL_CELL_FUELS =
    new RecipeMapBuilder<>("hydrogen_fuel_cell", FuelRecipeBuilder::new)
        .maxIO(0, 0, 2, 1)   // 2 流体进（H₂ + 可选 O₂），1 流体出（H₂O）
        .prepareRecipeCategory(RecipeCategories.FUEL_RECIPES)
        .sound(GTSoundEvents.TURBINE)
        .build();
```

> **说明**：只注册 `Hydrogen` 燃料配方。`Oxygen` 作为运行时增益流体在配方系统之外处理（与 `LargeCombustionEngine` 完全一致），因此 RecipeMap 本身只需 1 个流体输入槽。`maxIO(0,0,2,1)` 为未来增加其他燃料配方预留第二槽。

### 6.9 燃料配方

```java
// In HydrogenFuelCellRecipes.java
// 基准：1 000 mB H₂ 在 EV 级 = VA[EV] EU/t × 200t 每配方周期
HYDROGEN_FUEL_CELL_FUELS.recipeBuilder()
    .fluidInputs(Materials.Hydrogen.getFluid(1000))
    .fluidOutputs(Materials.Water.getFluid(1000))
    .duration(200)
    .EUt(VA[EV])
    .buildAndRegister();
```

### 6.10 合成配方（控制器方块）

**步骤 1** — PEM 膜（化学浴）：

```
Polytetrafluoroethylene（fluid, 576 mB）
+ Sulfuric Acid（fluid, 1 000 mB）
→ PEM Membrane（1）    ← 新增 MetaItem
时长：400t  EU/t：VA[HV]
```

**步骤 2** — 铂催化剂（混合机）：

```
Platinum（dust, 1）+ Carbon Black（dust, 4）
→ Platinum Catalyst（dustSmall, 4）
时长：200t  EU/t：VA[MV]
```

**步骤 3** — 控制器方块（组装机）：

```
Stainless Steel Frame（1）
+ Stainless Steel Plate（4）
+ PEM Membrane（2）
+ Platinum Catalyst（dustSmall, 8）
+ Polytetrafluoroethylene（fluid, 576 mB）
+ Aluminium Cable single（4）
→ 氢燃料电池（控制器）（1）
时长：400t  EU/t：VA[EV]
```

### 6.11 MTE 注册

```java
// MetaTileEntities.java — 单控制器，ID 992
HYDROGEN_FUEL_CELL = registerMetaTileEntity(992,
    new MetaTileEntityHydrogenFuelCell(gregtechId("hydrogen_fuel_cell")));
```

多方块使用**现有**外壳/齿轮箱方块（`STAINLESS_CLEAN`、`STAINLESS_STEEL_GEARBOX`）和**现有** Hatch MTE 类型（`FLUID_IMPORT_HATCH`、`FLUID_EXPORT_HATCH`、`ENERGY_OUTPUT_HATCH`、`MAINTENANCE_HATCH`、`MUFFLER_HATCH`）。**无需新增结构性方块注册**。

***

## 7. 文件变更清单

### 新建文件

| 文件路径                                                                                            | 用途              |
| ----------------------------------------------------------------------------------------------- | --------------- |
| `gregtech/common/metatileentities/electric/MetaTileEntityDisposableBatteryBase.java`            | A 系列共用抽象基类      |
| `gregtech/common/metatileentities/electric/MetaTileEntityZincManganeseCell.java`                | A0：LV 锌锰干电池     |
| `gregtech/common/metatileentities/electric/MetaTileEntityLithiumManganeseCell.java`             | A1：MV 锂锰电池      |
| `gregtech/common/metatileentities/electric/MetaTileEntityNickelCadmiumCell.java`                | A2：HV 镍镉电池      |
| `gregtech/common/metatileentities/electric/MetaTileEntityLeadAcidBattery.java`                  | A3：EV 铅酸电池      |
| `gregtech/common/metatileentities/electric/MetaTileEntityVanadiumFlowCell.java`                 | A4：IV 钒液流电池     |
| `gregtech/common/metatileentities/electric/MetaTileEntityLFPBattery.java`                       | A5：LuV 磷酸铁锂电池   |
| `gregtech/common/metatileentities/electric/MetaTileEntityLCOBattery.java`                       | A6：ZPM 钴酸锂电池    |
| `gregtech/common/metatileentities/electric/MetaTileEntityNMCBattery.java`                       | A7：UV 三元锂电池     |
| `gregtech/common/metatileentities/multi/electric/generator/MetaTileEntityHydrogenFuelCell.java` | C：多方块氢燃料电池发电机   |
| `gregtech/loaders/recipe/chemistry/HydrogenFuelCellRecipes.java`                                | C 的燃料配方 + 中间体配方 |
| `gregtech/loaders/recipe/DisposableBatteryRecipes.java`                                         | A 系列全部合成配方      |

### 修改文件

| 文件                                                            | 改动内容                                                                                                     |
| ------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `MetaTileEntities.java`                                       | 注册 ID 990\~997（A 系列）+ ID 998（C）；增加字段声明                                                                   |
| `RecipeMaps.java`                                             | 新增 `HYDROGEN_FUEL_CELL_FUELS` RecipeMap                                                                  |
| `ChemistryRecipes.java`                                       | 新增各级中间体化学配方                                                                                              |
| `FirstDegreeMaterials.java`                                   | 新增 `MnO₂`（若不存在）、`NickelHydroxide`、`VanadiumPentoxide`（若不存在）                                              |
| `UnknownCompositionMaterials.java` 或新 `BatteryMaterials.java` | 新增 `VanadiumElectrolyte`、`LFPCathodePowder`、`NMCPrecursor`、`NMCCathode`、`LiPF6Electrolyte`、`PVDF` 等中间体材料 |
| `MetaItems.java` + `MetaItem1.java`                           | 新增 `PEM_MEMBRANE`、`ION_EXCHANGE_MEMBRANE`、`CARBON_NANOTUBE_FILM` MetaItem                                |
| `lang/en_us.lang` + `zh_cn.lang`                              | 9 个新方块 + 新物品的翻译键                                                                                         |

***

## 8. MTE ID 分配

单方块发电机区段（935–989）已用情况及新增分配：

| 范围      | 占用内容                  |
| ------- | --------------------- |
| 935–939 | 燃油发电机（LV–IV+）         |
| 940–944 | 蒸汽轮机                  |
| 945–949 | 燃气轮机                  |
| 950–954 | 半流质发电机                |
| 955–959 | 等离子发电机                |
| 960–969 | （预留）                  |
| 970–974 | 风力发电机                 |
| 975–979 | 魔法能量吸收器               |
| 980–984 | 物品收集器                 |
| 985–989 | 机器外壳                  |
| **990** | **A0：锌锰干电池方块（LV）**    |
| **991** | **A1：锂锰电池方块（MV）**     |
| **992** | **A2：镍镉电池方块（HV）**     |
| **993** | **A3：铅酸电池方块（EV）**     |
| **994** | **A4：钒液流电池方块（IV）**    |
| **995** | **A5：磷酸铁锂电池方块（LuV）**  |
| **996** | **A6：钴酸锂电池方块（ZPM）**   |
| **997** | **A7：三元锂电池方块（UV）**    |
| **998** | **C：氢燃料电池多方块控制器（EV）** |

***

## 9. 实现任务拆分

> 按独立子任务分批推进，互不依赖。A 系列可以各 Tier 独立提交。

### Task 1 — 基类骨架（共用）

- 创建 `MetaTileEntityDisposableBatteryBase`，实现 NBT 读写、能量输出循环、`depleteAndDrop()` 工具方法、`onDepleted()` 抽象钩子
- 验证：NBT 往返正确（剩余电量在 chunk 重载后不丢失）

### Task 2 — A0 锌锰（LV）

- 实现 `MetaTileEntityZincManganeseCell`
- 补充 `MnO₂` 材料（若不存在）
- 编写中间体配方 + 组装机配方，化工流程2步，组装外壳1步，灌装一步，
- 注册 ID 990，补充语言键

### Task 3 — A1 锂锰（MV）

- 实现 `MetaTileEntityLithiumManganeseCell`
- 化工流程2步，组装外壳1步，灌装一步
- 注册 ID 991，补充配方和语言键

### Task 4 — A2 镍镉（HV）

- 实现 `MetaTileEntityNickelCadmiumCell`
- 补充 `NickelHydroxide` 材料（若不存在）
- 化工流程3步，组装外壳1步，灌装一步
- 注册 ID 992，补充配方和语言键

### Task 5 — A3 铅酸（EV）

- 实现 `MetaTileEntityLeadAcidBattery`
- 化工流程3步，组装外壳2步，灌装一步
- 注册 ID 993，补充配方和语言键

### Task 6 — A4 钒液流（IV）

- 实现 `MetaTileEntityVanadiumFlowCell`
- 新增 `VanadiumElectrolyte` 流体材料 + `IonExchangeMembrane` MetaItem
- 化工流程4步，组装外壳2步，灌装一步
- 注册 ID 994，补充配方和语言键

### Task 7 — A5 磷酸铁锂（LuV）

- 实现 `MetaTileEntityLFPBattery`
- 新增 `LFPCathodePowder`、`CarbonNanotubeFilm` 材料/物品
- 化工流程4步，组装外壳2步，灌装一步
- 注册 ID 995，补充配方和语言键

### Task 8 — A6 钴酸锂（ZPM）

- 实现 `MetaTileEntityLCOBattery`
- 新增 `PVDF` 流体材料
- 化工流程5步，组装外壳2步，灌装一步
- 注册 ID 996，补充配方和语言键

### Task 9 — A7 三元锂（UV）

- 实现 `MetaTileEntityNMCBattery`
- 新增 `NMCPrecursor`、`NMCCathode`、`LiPF6Electrolyte` 材料
- 化工流程5步，组装外壳2步，灌装一步
- 注册 ID 997，补充配方和语言键

### Task 10 — C 氢燃料电池多方块

- 新增 `HYDROGEN_FUEL_CELL_FUELS` RecipeMap
- 实现 `MetaTileEntityHydrogenFuelCell` 及内部 `HydrogenFuelCellWorkableHandler`
- 新增 `PEM_MEMBRANE` MetaItem
- 编写 PEM 膜、铂催化剂中间体配方
- 编写 H₂ 燃料配方
- 编写多方块控制器合成配方
- 注册 ID 998，补充语言键

### Task 11 — 打磨

- A 系列的 GUI 充电进度条
- Tooltip 最终确认
- 集成测试：放置方块 → EU 流入网络 → 方块耗尽 → 副产物掉落
- 贴图占位（C 的 frontOverlay）

***

## 10. 设计决策与依据

| 决策点                   | 采用方案                               | 拒绝方案                               | 理由                                                        |
| --------------------- | ---------------------------------- | ---------------------------------- | --------------------------------------------------------- |
| A 系列 Tier 起点          | 从 LV 开始                            | 从 ULV 开始                           | ULV 阶段化工基础薄弱，无法合理制备电极材料                                   |
| A 系列 Tier 覆盖范围        | LV \~ UV（8 种）                      | 仅 ULV + HV（2 种）                    | 完整覆盖整个游戏进程，每个阶段都有对应选项                                     |
| 化学体系选择                | 每 Tier 对应独立化学体系                    | 同一体系多倍容量                           | 增加内容深度；与现有 GT 化工链产生交叉互动                                   |
| A 系列能量输出机制            | 在 `update()` 中直接倒计 `remainingEU`   | `FuelRecipeLogic` + 虚拟流体           | 无流体参与；RecipeMap 反而是多余的间接层                                 |
| `onDepleted()` 统一工具方法 | `depleteAndDrop(ItemStack...)` 在基类 | 各子类独立实现                            | 消除重复代码；副产物逻辑变化只需修改子类的一行调用                                 |
| C 的架构层级               | 继承 `FuelMultiblockController`      | 继承 `SimpleGeneratorMetaTileEntity` | 多方块提供 Dynamo Hatch / 维护仓 / 多流体 hatch / O₂ boost 所需的全套基础设施 |
| C 的 O₂ boost          | 运行时额外 drain，不进配方                   | 写入 RecipeMap 配方                    | 与 `LargeCombustionEngine` 完全一致；O₂ 配方化反而限制了并联逻辑            |
| C 的 Water 产出          | Export Fluid Hatch 导出              | 配方输出 void                          | 使氢能化学闭环对玩家可见；水可被导管导走回收                                    |
| MTE ID 区段             | 990–998                            | 1100+ 区段                           | 990–999 当前空闲且紧邻发电机区，语义一致                                  |

