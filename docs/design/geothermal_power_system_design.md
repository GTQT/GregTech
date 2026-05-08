# 下界地热发电系统 — 设计文档

> **范围**：一套完整的地热能源利用系统，而非单纯的多方块机器。包含：岩浆采集、热交换处理、多等级发电、副产物处理，以及与 MC 下界（The Nether, DIM -1）环境的深度整合。

***

## 1. 系统总览

### 1.1 MC 下界维度环境约束

本设计必须严格尊重 Minecraft 下界（The Nether）的真实维度特性：

| 特性           | 下界表现                                  | 对设计的影响                                                                |
| ------------ | ------------------------------------- | --------------------------------------------------------------------- |
| 维度ID         | -1，`WorldProviderHell`                | 通过 `provider.isNether()` 或 `provider instanceof WorldProviderHell` 判定 |
| **水无法放置为方块** | `doesWaterVaporize() == true`，水桶放置时蒸发 | 不能"从环境取水"，但水在GT管道/容器内完全正常。初始需从主世界带水                                   |
| Y范围          | 0\~127，上下基岩封顶                         | 没有"无限向下钻井"的概念，空间有限                                                    |
| 岩浆海          | Y=31 及以下大面积裸露                         | 岩浆是地表级别的丰富资源，不需要深钻                                                    |
| 无天气          | 无雨无雷                                  | 无法利用天气机制                                                              |
| 无昼夜          | 恒暗环境                                  | 无太阳能辅助                                                                |
| 地形           | 地狱岩为主体，上下基岩，大量悬崖洞穴                    | 地狱岩极易挖掘，玩家可自由改造地形开辟空间，多方块尺寸不受限                                        |
| 温度           | 极高环境温度                                | 散热困难=对 ORC 等"冷端依赖"的系统不利                                               |
| 原生资源         | 地狱岩、灵魂沙、荧石、石英、岩浆                      | 岩浆无限可再生、地狱岩廉价                                                         |
| 敌对生物         | 恶魂（远程火球）、烈焰人、岩浆怪                      | 设备需要防爆考虑                                                              |

### 1.2 设计动机

当前能源体系中：

- **蒸汽锅炉** 已支持 Lava 作为燃料，但仅为 ULV\~LV 级别被动烧制
- **半流质发电机** 支持 Lava（5mB → 1tick → 32EU），效率极低
- **基岩流体钻机** 可在下界提取 Lava（125\~250mB/s），但缺少配套的高效利用方式
- **大量裸露岩浆** 在下界是"免费"的无限资源，但没有高效利用途径

缺失的是：**在下界这个"岩浆无限且裸露"的独特环境中**，一套能将岩浆热能高效转化为 EU 的系统级方案，利用密封管道中的水/冷却液循环实现闭环发电。

### 1.3 核心设计理念

1. **适配下界环境**：水在密封管道/容器中不会蒸发，可直接用水作为MV工作介质；WaterCoolant作为HV/EV效率升级
2. **利用下界优势**：岩浆无限且裸露、环境高温→地热传导效率高、地狱岩无限→裂解为高温流体
3. **系统而非单机**：多个组件组成的能源链
4. **渐进式解锁**：MV(水循环) → HV(冷却液/闪蒸) → EV(热电转换) 三个层级
5. **闭环设计**：初始填充水/冷却液后，蒸汽冷凝/冷却液回收实现自给自足
6. **利用地形**：直接从岩浆海/岩浆湖采集，而非向下钻井

### 1.4 系统组件总览

| 组件      | 类型      | GT等级  | 功能                    |
| ------- | ------- | ----- | --------------------- |
| 岩浆采集泵   | 单方块     | MV    | 从相邻岩浆源方块采集 Lava       |
| 地热蒸发器   | 多方块     | MV    | 用岩浆热量蒸发冷却液→产生蒸汽（密封循环） |
| 闪蒸分离塔   | 多方块     | HV    | 高温岩浆急速降压→分离蒸汽+矿渣      |
| 下界蒸汽涡轮  | 多方块     | HV    | 耐腐蚀涡轮，处理含硫地热蒸汽        |
| 熔岩热电转换器 | 多方块     | EV    | 基于塞贝克效应的直接热电转换        |
| 岩浆离心机   | 单方块/多方块 | MV-HV | 从冷却后的岩浆/渣中提取矿物        |
| 地狱岩裂解炉  | 多方块     | HV    | 裂解地狱岩产出硫磺+矿物+热量       |

***

## 2. 下界水资源策略

### 2.1 MC下界水机制澄清

下界中"水蒸发"机制的实际范围非常有限：

- `doesWaterVaporize() == true` 仅影响**将水方块放置到世界中**的行为
- 水桶/流体容器手动放置时会蒸发（`ItemFluidContainer` 检查 `doesVaporize`）
- `PrimitiveWaterPump` 禁止下界运行——因为它的概念是"从环境收集水"，下界无开放水源

**但是**，在密封系统中水完全正常：

- GT 流体管道中的水 **不会蒸发** ✓
- 机器流体输入/输出仓中的水 **不会蒸发** ✓
- 配方中以水作为流体输入/输出 **完全正常** ✓
- 流体单元/桶内存储的水 **不会蒸发** ✓

**结论**：下界中水可以作为密封系统的工作介质，只是不能在下界本地**无中生有**地取得水。

### 2.2 水的获取策略（下界）

在下界中获取水的可行方式：

| 方式                | 复杂度 | 产量 | 说明             |
| ----------------- | --- | -- | -------------- |
| 从主世界运输（桶/大容量流体单元） | 低   | 有限 | 适合初期填充         |
| 跨维度流体管道           | 中   | 无限 | 需要建设传送门/区块加载器  |
| 冰块融化（配方）          | 低   | 适中 | 可在下界用冰合成水      |
| 蒸汽冷凝回收            | 低   | 循环 | 涡轮废蒸汽→冷凝→水（闭环） |

### 2.3 设计选择：水 vs WaterCoolant

由于水可以在管道中正常使用，系统设计可以同时支持两种冷循环介质：

| 介质                    | 获取难度             | 换热效率     | 适用场景            |
| --------------------- | ---------------- | -------- | --------------- |
| **Water（水）**          | 低（但需从主世界运来或闭环回收） | 标准(1.0x) | MV入门路线，简单直接     |
| **WaterCoolant（冷却液）** | 中（需混合器合成）        | 高(1.5x)  | HV/EV高效路线，更好的热容 |

**设计原则**：

- MV路线直接用水（低门槛入门）：Lava + Water → Steam + CooledLavaSlurry
- HV/EV路线用冷却液（更高效率）：Lava + WaterCoolant → HotWaterCoolant + CooledLavaSlurry
- 两种路线并存，给玩家选择权
- 闪蒸路线完全不需要水/冷却液（纯岩浆相变）

### 2.4 玩法逻辑

**MV入门（直接用水）**：

```
[主世界] 带足够的水到下界（桶/流体单元）→ 初始填充
[下界] 地热热交换器: Lava(热) + Water(冷) → Steam + CooledLavaSlurry
[下界] Steam → 蒸汽涡轮 → EU + 冷凝水(回收!)
         水在密封管道/容器中循环，不蒸发，闭环运行！
```

**HV进阶（冷却液循环）**：

```
[主世界] 混合器: Water + Lapis → WaterCoolant → 运到下界
[下界] 地热热交换器: Lava + WaterCoolant → HotWaterCoolant + CooledLavaSlurry
[下界] HotWaterCoolant → 涡轮发电 → WaterCoolant(回收!) → 循环回热交换器
         闭环运行，初始填充后不再消耗！
```

**核心区别**：玩家不需要持续从主世界运水——**初始填充后闭环运行**，蒸汽冷凝或冷却液循环实现自给自足。

***

## 3. 新增材料与流体

### 3.1 地热专用流体

| 流体名称                      | ID建议 | 温度(K) | 说明                                     |
| ------------------------- | ---- | ----- | -------------------------------------- |
| MoltenNetherrack（熔融地狱岩）   | 2550 | 1473  | 地狱岩裂解后的熔融态流体                           |
| GeothermalVapor（地热蒸汽）     | 2551 | 523   | 含硫含矿物杂质的蒸汽，从岩浆热交换产生                    |
| CooledLavaSlurry（冷却岩浆浆）   | 2552 | 473   | 岩浆被提取热量后的半凝固浆体                         |
| OrganicWorkingFluid（有机工质） | 2553 | 293   | ORC循环的低沸点工质（异丁烷基），密封循环                 |
| HotOrganicVapor（高温有机蒸汽）   | 2554 | 393   | 被岩浆加热后的有机工质气态                          |
| NetherGas（下界气体）           | —    | —     | 已有 `NetherAir` / `LiquidNetherAir`，可复用 |

### 3.2 复用的现有流体

| 现有流体                      | 在系统中的角色                   |
| ------------------------- | ------------------------- |
| `Lava` (Materials.Lava)   | 主要热源输入（下界无限供应）            |
| `Water` (Materials.Water) | MV路线冷循环介质（需初始填充，之后闭环）     |
| `WaterCoolant`            | HV/EV路线高效冷循环介质（主世界制备）     |
| `HotWaterCoolant`         | 冷却液吸热后的状态                 |
| `Steam`                   | MV路线产出（Lava+Water热交换的主产物） |
| `HighPressureSteam`       | HV路线高阶蒸汽产出                |
| `DistilledWater`          | 蒸汽冷凝回收产物（可循环回用）           |

### 3.3 副产物固体材料

| 材料名称                 | 来源               | 处理                           |
| -------------------- | ---------------- | ---------------------------- |
| NetherrackDust（地狱岩粉） | 已有，从Netherrack研磨 | 裂解炉输入                        |
| VolcanicAsh（火山灰）     | 闪蒸分离塔副产物         | 离心→Iron + Magnesium + 微量Gold |
| SulfurDeposit（硫磺沉积）  | 地热蒸汽冷凝副产物        | 等同 Sulfur dust               |
| MagmaResidue（岩浆残渣）   | 岩浆热量提取后的固渣       | 离心→Iron + SiO₂ + 微量稀有矿物      |

***

## 4. 系统组件详细设计

### 4.1 岩浆采集泵（Magma Pump）

**类型**：单方块 MetaTileEntity，分 MV/HV/EV 三级

**机制**：

- 放置后检测相邻方块是否为岩浆源方块（`Blocks.LAVA` / `Blocks.FLOWING_LAVA`）
- 每 tick 消耗 EU 从相邻岩浆中泵取 Lava 流体
- 不会消耗岩浆源方块（下界岩浆无限再生的设定，16\*16范围内都有岩浆才能实现）
- 在下界维度有额外加成（岩浆更热、流动性更好）

**参数**：

| 等级 | 基础产量     | EU消耗     | 下界产量加成          |
| -- | -------- | -------- | --------------- |
| MV | 100 mB/t | 32 EU/t  | ×2.0（200 mB/t）  |
| HV | 250 mB/t | 128 EU/t | ×2.0（500 mB/t）  |
| EV | 600 mB/t | 512 EU/t | ×2.0（1200 mB/t） |

**下界特殊行为**：

- 检测到 `provider.isNether()` 时自动启用高温模式
- 产量翻倍（下界岩浆温度更高→粘度更低→更易泵送）
- 不需要相邻源方块（可以从任何接触的岩浆面提取，因为下界岩浆海是连通的）

**结构要求**：

- 方块底部或侧面必须与岩浆源方块相邻
- 主世界也可用（建在岩浆池边），但无加成

### 4.2 地热蒸发器（Geothermal Evaporator）

**类型**：多方块结构（继承 `RecipeMapMultiblockController`）

**GT等级**：MV

**核心理念**：用岩浆的热量加热密封管道中的冷循环介质。支持两种模式：

- **MV模式（水）**：Lava + Water → Steam + CooledLavaSlurry（水直接变成蒸汽）
- **HV模式（冷却液）**：Lava + WaterCoolant → HotWaterCoolant + CooledLavaSlurry（冷却液加热循环）

**在下界为什么能工作**：

- 水和冷却液都在 GT 流体管道/机器仓内密封传输
- 不会触发 `doesWaterVaporize()` 机制（该机制仅影响放置方块到世界中）
- 产出的蒸汽在涡轮中冷凝回水 → 闭环循环，初始填充后自给自足

**结构尺寸**：3x3x5（宽×高×长）

```
Aisle layout (side view, controller faces front):
Front:   CCC    Middle: CPC    Middle: CPC    Middle: CPC    Back: CCC
         CYC             C#C             C#C             C#C          CCC
         CCC             CPC             CPC             CPC          CCC

C = Metal Casing (Steel Solid / Invar Heat Proof)
P = Pipe Casing (Steel / Invar — 决定换热效率)
# = Air (热交换腔)
Y = Controller
```

**配方**：

| 输入流体1（热源）              | 输入流体2（冷端）          | 输出流体1                    | 输出流体2                  | 时长 | EU/t |
| ---------------------- | ------------------ | ------------------------ | ---------------------- | -- | ---- |
| Lava 200mB             | Water 200mB        | Steam 6400mB             | CooledLavaSlurry 200mB | 10 | 32   |
| Lava 200mB             | WaterCoolant 100mB | HotWaterCoolant 100mB    | CooledLavaSlurry 200mB | 10 | 32   |
| MoltenNetherrack 100mB | Water 100mB        | HighPressureSteam 3200mB | CooledLavaSlurry 80mB  | 10 | 128  |
| MoltenNetherrack 100mB | WaterCoolant 50mB  | HotWaterCoolant 50mB     | CooledLavaSlurry 80mB  | 10 | 128  |

**下界加成**：

- `provider.isNether()` 时，配方时长 ÷2（环境高温减少热损耗）
- 产出的 HotWaterCoolant 内部温度更高 → 下游涡轮效率更高

### 4.3 闪蒸分离塔（Flash Separation Tower）

**类型**：多方块结构（继承 `RecipeMapMultiblockController`）

**GT等级**：HV

**核心理念**：将高温岩浆在密封容器内急速降压，部分岩浆直接气化为地热蒸汽（GeothermalVapor），剩余变为矿渣。

**为什么适合下界**：

- 不需要水！纯粹利用岩浆自身的热量做相变
- 下界环境温度高 → 闪蒸温差更稳定 → 效率更高
- 产出的 GeothermalVapor 含硫、含矿物微粒（下界岩浆更"脏"=副产物更多）

**结构尺寸**：3x5x3（宽×高×长）— 竖直塔式

```
Layer 4 (top):    CCC / CCC / CCC
Layer 3:          CCC / C#C / CCC
Layer 2:          CCC / C#C / CCC
Layer 1:          CCC / C#C / CCC
Layer 0 (bottom): CCC / CYC / CCC

# = Air (闪蒸腔)
Y = Controller (底部正面)
C = Stainless Steel Casing (耐高温耐腐蚀)
```

**配方**：

| 输入                     | 输出蒸汽                                             | 输出固体/液体                                 | 时长 | EU/t |
| ---------------------- | ------------------------------------------------ | --------------------------------------- | -- | ---- |
| Lava 1000mB            | GeothermalVapor 2000mB                           | VolcanicAsh ×2 + CooledLavaSlurry 300mB | 40 | 128  |
| Lava 500mB（下界）         | GeothermalVapor 1500mB + HighPressureSteam 500mB | VolcanicAsh ×3 + SulfurDeposit ×1       | 20 | 128  |
| MoltenNetherrack 500mB | GeothermalVapor 1000mB                           | VolcanicAsh ×4 + SulfurDeposit ×2       | 30 | 128  |

**下界加成逻辑**：

```java
if (getWorld().provider.isNether()) {
    // 下界岩浆温度更高(~1300K vs 主世界~1000K概念)
    // 闪蒸效率+50%, 硫含量更高
    recipeDuration /= 1.5;
    sulfurByproductChance *= 2;
}
```

### 4.4 下界蒸汽涡轮（Nether Steam Turbine）

**类型**：多方块发电机（继承 `FuelMultiblockController`）

**GT等级**：HV

**核心理念**：专门设计用于处理含硫、含矿物杂质的 GeothermalVapor。普通蒸汽涡轮会被腐蚀损坏，此涡轮使用不锈钢/耐酸外壳。

**结构**：类似大型蒸汽涡轮

```
Front:   XXX / XDX / XXX
Middle1: XCX / CGC / XCX
Middle2: XCX / CGC / XCX
Back:    XXX / XYX / XXX

X = Stainless Steel Casing（耐腐蚀！）
G = Stainless Steel Gearbox
C = Casing + optional hatches
D = Dynamo Hatch (HV+)
Y = Controller
```

**发电参数**：

| 燃料                | 消耗速率    | 基础输出EU/t | 下界输出EU/t | 废物输出                                     |
| ----------------- | ------- | -------- | -------- | ---------------------------------------- |
| Steam             | 640mB/t | 256 EU/t | 341 EU/t | DistilledWater 4mB/t（回收循环！）              |
| GeothermalVapor   | 320mB/t | 384 EU/t | 512 EU/t | SulfurDeposit(微量) + DistilledWater 2mB/t |
| HighPressureSteam | 160mB/t | 512 EU/t | 682 EU/t | DistilledWater 4mB/t                     |
| HotWaterCoolant   | 50mB/t  | 256 EU/t | 341 EU/t | WaterCoolant 50mB/t（回收！）                 |

**下界加成机制**：

```java
@Override
protected long boostProduction(long production) {
    if (getWorld() != null && getWorld().provider.isNether()) {
        // 下界环境:涡轮排热到高温环境更困难,但蒸汽入口温度也更高
        // 净效果: +33% 输出
        return production * 4 / 3;
    }
    return production;
}
```

**HotWaterCoolant 回收**：涡轮消耗 HotWaterCoolant 后输出 WaterCoolant——实现密封循环！玩家只需要初始填充冷却液，之后闭环运行。

### 4.5 熔岩热电转换器（Magma Thermoelectric Generator）

**类型**：多方块发电机（继承 `FuelMultiblockController`）

**GT等级**：EV

**核心理念**：基于塞贝克效应（Seebeck Effect）的直接热电转换。不需要蒸汽中间环节。高温端接触岩浆，低温端通过冷却液维持温差，温差产生电压。

**为什么是终极下界发电方案**：

- 无运动部件（无转子损耗）
- 直接 热→电 转换（无蒸汽中间步骤）
- 需要 EV 级别的热电材料（碲化铋/锑化物，项目中已有 AntimonyTelluride = `Sb₂Te₃`）
- 效率取决于热端和冷端的温差——下界中冷端温度更高，但热端温度也更高

**结构尺寸**：5x3x5

```
Layer 0 (bottom): CCCCC / CTTTC / CTHTC / CTTTC / CCCCC
Layer 1 (middle): CCCCC / CT#TC / C#Y#C / CT#TC / CCCCC
Layer 2 (top):    CCDCC / CCCCC / CCCCC / CCCCC / CCCCC

C = Titanium Stable Casing
T = Thermoelectric Element (新方块: 碲化铋合金板)
H = Heat Conductor (Pipe Casing, 热传导核心)
# = Air
D = Dynamo Hatch (EV)
Y = Controller
```

**工作原理**：

- 输入热端: Lava（持续消耗）
- 输入冷端: WaterCoolant（密封循环，输出 HotWaterCoolant）
- 输出: EU（直接电力）+ HotWaterCoolant（可外送回收）+ CooledLavaSlurry（矿渣）

**发电参数**：

| 热端输入                    | 冷端输入                | 输出EU/t    | 冷端输出                   | 热端输出                     |
| ----------------------- | ------------------- | --------- | ---------------------- | ------------------------ |
| Lava 100mB/t            | WaterCoolant 30mB/t | 960 EU/t  | HotWaterCoolant 30mB/t | CooledLavaSlurry 100mB/t |
| MoltenNetherrack 50mB/t | WaterCoolant 20mB/t | 1200 EU/t | HotWaterCoolant 20mB/t | CooledLavaSlurry 40mB/t  |

**下界加成**：

- 下界环境温度更高 → 冷端效率略降
- **但**：下界岩浆实际温度也更高（概念上\~1300K vs 主世界\~1000K）
- 净效果: +25% 输出（`production * 5 / 4`）

**热电元件方块**（新增方块）：

- 由 AntimonyTelluride（碲化锑）+ 高导热材料制成
- 有耐久度机制：每运行 72000 tick（1小时）损耗 1 级
- 可替换，类似线圈的功能但带消耗

### 4.6 地狱岩裂解炉（Netherrack Cracking Furnace）

**类型**：多方块结构（继承 `RecipeMapMultiblockController`）

**GT等级**：HV

**核心理念**：地狱岩在下界几乎无限（整个维度的地表方块），可以作为一种"矿物+能源"混合资源。裂解炉将地狱岩高温分解，产出：

- 硫磺（下界地狱岩富含硫）
- MoltenNetherrack（比 Lava 温度更高的流体，可进一步发电）
- 微量金、石英

**下界特殊性**：

- 只有在下界才有无限地狱岩供应
- 裂解过程本身产热 → 可以作为"放大器"给上游增加热源
- 产出的 MoltenNetherrack 比普通 Lava 热值更高

**配方**：

| 输入                              | 输出流体                    | 输出固体                                    | 时长 | EU/t |
| ------------------------------- | ----------------------- | --------------------------------------- | -- | ---- |
| Netherrack ×16                  | MoltenNetherrack 1000mB | Sulfur dust ×4 + Gold tiny dust ×1(20%) | 80 | 128  |
| Netherrack ×16 + Lava 500mB(催化) | MoltenNetherrack 1500mB | Sulfur dust ×6 + NetherQuartz ×1(30%)   | 60 | 128  |

### 4.7 岩浆离心机（Magma Centrifuge）

**类型**：单方块 SimpleMachineMetaTileEntity (MV-HV) 或 配方注册到现有离心机

**功能**：处理 CooledLavaSlurry（冷却岩浆浆）和 VolcanicAsh（火山灰），提取矿物副产物。

**配方注册到现有离心机 RecipeMap**：

```
CooledLavaSlurry 1000mB → [离心机 MV]
  → SiliconDioxide dust ×2
  → Iron dust ×1
  → Magnesium dust ×1 (40%)
  → Gold tiny dust ×1 (15%, 下界产出加倍至30%)

VolcanicAsh ×4 → [离心机 MV]
  → Iron dust ×1
  → Sulfur dust ×2
  → RareEarth tiny dust ×1 (20%)
  → NetherQuartz ×1 (25%)
```

***

## 5. 石化体系融合：地热裂化炉

### 5.1 设计动机

现有裂化体系提供两种裂化方式：

- **蒸汽裂化**（Steam Cracking）：原料 + Steam → SteamCracked 产物（热裂解，产出烯烃为主）
- **加氢裂化**（Hydro Cracking）：原料 + Hydrogen → HydroCracked 产物（加氢饱和，产出烷烃为主）

两者都需要额外消耗蒸汽/氢气作为"裂化介质"。

**地热裂化**是第三种路径：直接用岩浆/高温熔融流体的热量来驱动裂化反应，不需要额外的蒸汽或氢气——岩浆本身就是热源。

### 5.2 与现有体系的关系

```
                    现有石化体系
                         │
    ┌────────────────────┼────────────────────┐
    │                    │                    │
 [加氢裂化]          [蒸汽裂化]          [地热裂化] ← NEW
    │                    │                    │
 原料+H₂→           原料+Steam→          原料+Lava→
 HydroCracked        SteamCracked        ThermalCracked ← NEW
    │                    │                    │
    └────────────────────┼────────────────────┘
                         │
                    [蒸馏塔]
                         │
              分馏为各种小分子化合物
```

**关键**：地热裂化的产物（ThermalCracked系列）进蒸馏塔后仍然产出已有的小分子材料（乙烯、丙烯、苯等），与现有下游完全兼容。

### 5.3 地热裂化炉（Geothermal Cracker）— 多方块

**类型**：多方块结构（继承 `RecipeMapMultiblockController`）

**GT等级**：HV

**核心差异 vs 现有裂化装置**：

| 对比项  | 裂化装置（Cracking Unit） | 地热裂化炉（Geothermal Cracker）            |
| ---- | ------------------- | ------------------------------------ |
| 裂化介质 | Steam 或 Hydrogen    | Lava 或 MoltenNetherrack（热源）          |
| 外壳   | 不锈钢+线圈              | 耐热外壳+线圈+底部岩浆接触面                      |
| 能耗   | MV-HV（电力驱动）         | 更低EU（热源提供大部分能量）                      |
| 产物特点 | 蒸汽裂化偏烯烃，加氢偏烷烃       | 热裂化偏芳烃+积碳（高温深度裂化特征）                  |
| 环境加成 | 无                   | 下界 +50% 效率（环境高温减少热损）                 |
| 副产物  | 无/微量碳               | CooledLavaSlurry + VolcanicAsh（可进离心） |
| 消耗品  | 蒸汽/氢气（持续消耗）         | 岩浆（下界无限免费）                           |

**结构尺寸**：5x3x3（宽×高×长）

```
Aisle layout (top view per layer):

Layer 0 (bottom): HHHHH / HHLHH / HHHHH
Layer 1 (middle): HCHCH / H###H / HCHCH
Layer 2 (top):    HCHCH / HCOCH / HCHCH

O = Controller (top center, front)
H = Heat-Proof Casing (Invar Heat Proof)
C = Heating Coil (same tier system as Cracking Unit)
L = Lava Input (底部中心，特殊方块或流体输入仓接触岩浆面)
# = Air (反应腔)
```

**结构特点**：

- 底部使用耐热外壳（Invar Heat Proof）而非不锈钢
- 底部中心为流体输入仓（用于输入 Lava/MoltenNetherrack）
- 线圈同样影响能耗（每级 -10%，复用现有线圈加成逻辑）
- 比裂化装置更紧凑（不需要两侧对称的流体处理腔）

### 5.4 新增流体材料：ThermalCracked 系列

| 原料               | ThermalCracked 产物       | 说明       |
| ---------------- | ----------------------- | -------- |
| HeavyFuel（重油）    | ThermalCrackedHeavyFuel | 高温热裂解重油  |
| LightFuel（轻油）    | ThermalCrackedLightFuel | 高温热裂解轻油  |
| Naphtha（石脑油）     | ThermalCrackedNaphtha   | 高温热裂解石脑油 |
| RefineryGas（炼厂气） | ThermalCrackedGas       | 高温热裂解炼厂气 |

### 5.5 裂化配方

**地热裂化配方（注册到 GEOTHERMAL\_CRACKING\_RECIPES）**：

```java
// Heavy Fuel thermal cracking
public static void thermalCrack(Material raw, Material thermalCracked) {
    // Lava as heat source (basic)
    GEOTHERMAL_CRACKING_RECIPES.recipeBuilder()
            .circuitMeta(1)
            .fluidInputs(raw.getFluid(1000))
            .fluidInputs(Lava.getFluid(500))
            .fluidOutputs(thermalCracked.getFluid(1000))
            .fluidOutputs(CooledLavaSlurry.getFluid(500))
            .duration(60).EUt(VA[MV]).buildAndRegister();

    // MoltenNetherrack as heat source (advanced, higher temp = better cracking)
    GEOTHERMAL_CRACKING_RECIPES.recipeBuilder()
            .circuitMeta(2)
            .fluidInputs(raw.getFluid(1000))
            .fluidInputs(MoltenNetherrack.getFluid(250))
            .fluidOutputs(thermalCracked.getFluid(1200))  // 20% more output!
            .fluidOutputs(CooledLavaSlurry.getFluid(200))
            .duration(40).EUt(VA[MV]).buildAndRegister();
}
```

**配方注册**：

```java
thermalCrack(HeavyFuel, ThermalCrackedHeavyFuel);
thermalCrack(LightFuel, ThermalCrackedLightFuel);
thermalCrack(Naphtha, ThermalCrackedNaphtha);
thermalCrack(RefineryGas, ThermalCrackedGas);
```

### 5.6 蒸馏产物（ThermalCracked 系列进蒸馏塔）

地热裂化产物的特点：**高温深度裂化 → 芳烃比例更高 + 积碳更多**

```java
// ThermalCrackedHeavyFuel distillation
DISTILLATION_RECIPES.recipeBuilder()
    .fluidInputs(ThermalCrackedHeavyFuel.getFluid(1000))
    .chancedOutput(dust, Carbon, 4, 5000, 0)  // 50% chance, 4 carbon dust (more coking)
    .fluidOutputs(Toluene.getFluid(150))       // More aromatics than steam cracking
    .fluidOutputs(Benzene.getFluid(500))       // Significantly more benzene
    .fluidOutputs(Butadiene.getFluid(100))
    .fluidOutputs(Propene.getFluid(75))
    .fluidOutputs(Ethylene.getFluid(200))
    .fluidOutputs(Methane.getFluid(100))
    .duration(120).EUt(VA[MV]).buildAndRegister();

// ThermalCrackedLightFuel distillation
DISTILLATION_RECIPES.recipeBuilder()
    .fluidInputs(ThermalCrackedLightFuel.getFluid(1000))
    .chancedOutput(dust, Carbon, 3, 4000, 0)
    .fluidOutputs(Naphtha.getFluid(200))
    .fluidOutputs(Toluene.getFluid(80))
    .fluidOutputs(Benzene.getFluid(350))       // High benzene
    .fluidOutputs(Butadiene.getFluid(120))
    .fluidOutputs(Propene.getFluid(200))
    .fluidOutputs(Ethylene.getFluid(300))      // Good ethylene
    .fluidOutputs(Methane.getFluid(150))
    .duration(120).EUt(VA[MV]).buildAndRegister();

// ThermalCrackedNaphtha distillation
DISTILLATION_RECIPES.recipeBuilder()
    .fluidInputs(ThermalCrackedNaphtha.getFluid(1000))
    .chancedOutput(dust, Carbon, 2, 3333, 0)
    .fluidOutputs(Toluene.getFluid(60))
    .fluidOutputs(Benzene.getFluid(250))
    .fluidOutputs(Butene.getFluid(100))
    .fluidOutputs(Butadiene.getFluid(200))
    .fluidOutputs(Propene.getFluid(300))
    .fluidOutputs(Ethylene.getFluid(400))      // Ethylene main product
    .fluidOutputs(Methane.getFluid(300))
    .duration(120).EUt(VA[MV]).buildAndRegister();

// ThermalCrackedGas distillation
DISTILLATION_RECIPES.recipeBuilder()
    .fluidInputs(ThermalCrackedGas.getFluid(1000))
    .chancedOutput(dust, Carbon, 1, 2222, 0)
    .fluidOutputs(Propene.getFluid(50))
    .fluidOutputs(Ethylene.getFluid(150))
    .fluidOutputs(Methane.getFluid(1200))
    .fluidOutputs(Helium.getFluid(20))
    .duration(120).EUt(VA[MV]).buildAndRegister();
```

### 5.7 产物特性对比

| 裂化方式     | 乙烯产出  | 苯产出   | 积碳    | 介质成本           | 最佳场景                |
| -------- | ----- | ----- | ----- | -------------- | ------------------- |
| 蒸汽裂化     | 高     | 中     | 中     | Steam（需锅炉）     | 乙烯为目标               |
| 加氢裂化     | 低     | 低     | 无     | Hydrogen（需电解）  | 烷烃为目标               |
| **地热裂化** | **中** | **高** | **高** | **Lava（下界免费）** | **苯/芳烃为目标，或下界免费裂化** |

**设计理念**：

- 地热裂化不是"更好的蒸汽裂化"——它是一条不同的产物路径
- 优势：苯/芳烃产量更高（苯是很多高端化工品的原料）、介质免费（下界岩浆）
- 劣势：积碳更多（需要定期清理或处理）、乙烯产量不如蒸汽裂化
- 给玩家提供选择：要乙烯→蒸汽裂化；要苯→地热裂化；要免费→地热裂化

### 5.8 下界环境加成

```java
private class GeothermalCrackerWorkableHandler extends MultiblockRecipeLogic {

    @Override
    protected void modifyOverclockPost(@NotNull OCResult ocResult, @NotNull RecipePropertyStorage storage) {
        super.modifyOverclockPost(ocResult, storage);

        // Coil discount (same as regular Cracking Unit)
        int coilTier = getCoilTier();
        if (coilTier > 0) {
            ocResult.setEut(Math.max(1, (long) (ocResult.eut() * (1.0 - coilTier * 0.1))));
        }

        // Nether environment bonus: 30% faster (ambient heat reduces energy loss)
        if (getWorld() != null && getWorld().provider.isNether()) {
            int newDuration = (int) (ocResult.duration() * 0.7);
            ocResult.setDuration(Math.max(1, newDuration));
        }
    }
}
```

### 5.9 新增 RecipeMap

```java
// Geothermal Cracker — thermal cracking using lava/molten netherrack as heat source
public static final RecipeMap<SimpleRecipeBuilder> GEOTHERMAL_CRACKING_RECIPES = 
    new RecipeMapBuilder<>("geothermal_cracker", new SimpleRecipeBuilder())
        .itemInputs(1)    // circuit
        .fluidInputs(2)   // feedstock + heat source (lava/molten netherrack)
        .fluidOutputs(2)  // cracked product + cooled lava slurry
        .sound(GTSoundEvents.FIRE)
        .build();
```

### 5.10 完整石化+地热联动流程

```
[下界] 基岩流体钻机 → Lava (无限供应)
         ↓
         ├─→ [地热裂化炉] + HeavyFuel/LightFuel/Naphtha
         │        ↓ ThermalCracked产物 + CooledLavaSlurry
         │        ↓                           ↓
         │   [蒸馏塔]                    [离心机] → 矿物副产物
         │        ↓
         │   Benzene(高产!) + Ethylene + Toluene + Methane + Carbon
         │        ↓
         │   [下游化工] → 塑料、橡胶、炸药、PBI等
         │
         ├─→ [地热蒸发器] + Water → Steam
         │        ↓
         │   [蒸汽涡轮] → EU（为整套系统供电）
         │
         └─→ [地狱岩裂解炉] ← Netherrack (无限)
                  ↓ MoltenNetherrack
                  ↓
              [地热裂化炉] (作为高级热源，+20%产出！)
```

**经济性分析**：在下界运行地热裂化炉相比主世界裂化装置：

- 热源免费（Lava 无限 vs Steam需要燃料烧锅炉 / H₂ 需要电解水）
- 速度更快（-30% 时间，下界环境加成）
- 额外副产物收益（CooledLavaSlurry → 离心 → 金/铁/稀土）
- 苯产量更高（对 PBI、酚醛树脂等高端材料友好）
- 代价：需要在下界建设和维护基础设施

***

## 6. 完整能量流

### 6.1 基础路线（MV，入门 — 直接用水）

```
[主世界] 带一桶水到下界 → 填充到地热蒸发器的流体输入仓
[下界] 岩浆采集泵 MV ← 旁边的岩浆海
         ↓ Lava 200mB/t
[下界] 地热蒸发器 + Water(密封管道内)
         ↓ Steam 6400mB/t                 ↓ CooledLavaSlurry
[下界] 下界蒸汽涡轮 ← Steam              [离心机] → 矿物副产物
         ↓ EU ~256 EU/t (下界~341)
         ↓ DistilledWater → 回收循环至蒸发器！
              ↑_________________________________↓

净输出: ~256 EU/t (主世界) / ~341 EU/t (下界)
水需求: 一次性填充（蒸汽冷凝回收 DistilledWater 循环使用）
```

### 6.2 中级路线A（HV，冷却液循环）

```
[主世界] 混合器: Water + Lapis → WaterCoolant → 初始填充运到下界
[下界] 岩浆采集泵 HV ← 岩浆海 (500 mB/t in nether)
         ↓ Lava
[下界] 地热蒸发器 + WaterCoolant
         ↓ HotWaterCoolant 250mB/t          ↓ CooledLavaSlurry
[下界] 下界蒸汽涡轮 ← HotWaterCoolant
         ↓ EU ~341 EU/t (with nether boost)
         ↓ WaterCoolant(回收!) → 循环回蒸发器

净输出: ~341 EU/t
完全闭环，初始填充后永不消耗冷却液
```

### 6.2b 中级路线B（HV，闪蒸 — 无需任何水/冷却液！）

```
[下界] 岩浆采集泵 HV ← 岩浆海 (500 mB/t in nether)
         ↓ Lava
[下界] 闪蒸分离塔
         ↓ GeothermalVapor 1500mB/t        ↓ VolcanicAsh + SulfurDeposit
[下界] 下界蒸汽涡轮 ← GeothermalVapor
         ↓ EU ~512 EU/t (with nether boost)
         ↓ SulfurDeposit (额外副产物收益)

净输出: ~512 EU/t
无需冷却液! (闪蒸路线不依赖水/冷却液)
```

### 6.3 高级路线（EV，直接热电）

```
[主世界] 混合器: Water + Lapis → WaterCoolant (一次性大量制备)
              ↓ 桶/大容量流体单元运往下界
[下界] 岩浆采集泵 EV ← 岩浆海 (1200 mB/t)
         ↓ Lava 100mB/t
[下界] 熔岩热电转换器 + WaterCoolant(循环)
         ↓ EU 1200 EU/t (with nether boost)
         ↓ HotWaterCoolant → 回到蒸发器或涡轮二次利用!
         ↓ CooledLavaSlurry → [离心机] → Gold + Iron + RareEarth

总净输出: ~1200 EU/t (单台)
可并联多台: 岩浆无限 → 瓶颈只是冷却液循环速度
```

### 6.4 终极路线（EV+，全系统联动）

```
                    [下界] 地狱岩裂解炉 ← 无限Netherrack
                              ↓ MoltenNetherrack (比Lava更高温)
[下界] 岩浆采集泵 EV ←岩浆海   ↓
         ↓ Lava            ↓
         ↓                 ↓
    [闪蒸分离塔] ← Lava    [熔岩热电转换器] ← MoltenNetherrack + WaterCoolant
         ↓                           ↓
    GeothermalVapor              EU 1200/t
         ↓                           ↓
    [下界蒸汽涡轮]              HotWaterCoolant
         ↓                           ↓
    EU 512/t                   [地热蒸发器补充加热] or [涡轮二次利用]
         ↓                           ↓
         └──── 总计 ~1700+ EU/t ─────┘
                              ↓
                    CooledLavaSlurry + VolcanicAsh
                              ↓
                    [离心机] → Gold + Sulfur + Iron + RareEarth + Quartz
```

***

## 7. 下界环境加成系统

### 7.1 维度检测实现

```java
public class GeothermalUtils {

    /**
     * Check if the world is the Nether dimension.
     * Uses the same pattern as PrimitiveWaterPump's detection.
     */
    public static boolean isNetherDimension(@NotNull World world) {
        return world.provider.isNether() || world.provider.doesWaterVaporize();
    }

    /**
     * Get the geothermal efficiency multiplier for the current position.
     * 
     * In the Nether:
     *   - Lava sea level is Y=31, lava is exposed surface resource
     *   - Lower Y = closer to bedrock = slightly hotter
     *   - Y 0-10: ×1.5 (bedrock proximity)
     *   - Y 11-31: ×1.3 (lava sea level, abundant lava)
     *   - Y 32-80: ×1.1 (above lava sea, still hot)
     *   - Y 81-127: ×1.0 (upper nether, normal)
     * 
     * In the Overworld:
     *   - Y 0-16: ×1.2 (deep underground, mild geothermal)
     *   - Y 17-63: ×1.0 (normal)
     *   - Y 64+: ×0.8 (surface/above, less geothermal)
     */
    public static double getYLevelMultiplier(@NotNull World world, @NotNull BlockPos pos) {
        int y = pos.getY();
        if (isNetherDimension(world)) {
            if (y <= 10) return 1.5;
            if (y <= 31) return 1.3;  // Lava sea level
            if (y <= 80) return 1.1;
            return 1.0;
        } else {
            if (y <= 16) return 1.2;
            if (y <= 63) return 1.0;
            return 0.8;
        }
    }

    /**
     * Base dimension multiplier.
     * Nether is inherently better for geothermal due to:
     * - Higher ambient temperature
     * - Abundant lava (the entire dimension)
     * - Higher lava temperature concept
     */
    public static double getDimensionMultiplier(@NotNull World world) {
        if (isNetherDimension(world)) {
            return 1.5;  // 50% bonus in nether
        }
        return 1.0;
    }
}
```

### 7.2 Y层级设计与MC下界地形对应

| Y坐标     | MC下界实际地形             | 地热系数 | 原因             |
| ------- | -------------------- | ---- | -------------- |
| 0-4     | 底部基岩层                | 1.5x | 无法建造（基岩），但理论最热 |
| 5-10    | 底层洞穴/被岩浆淹没区          | 1.5x | 极端接近热源         |
| 11-31   | **岩浆海区域**（Y=31为岩浆海面） | 1.3x | 最适合建设的高热区      |
| 32-80   | 主要地形层（洞穴、平台）         | 1.1x | 常规建设区，仍有热加成    |
| 81-120  | 上层洞穴，接近顶部基岩          | 1.0x | 无额外加成          |
| 121-127 | 顶部基岩层                | 1.0x | 无法建造           |

**最佳建设位置**：Y=32 附近（刚好在岩浆海面之上），既有1.1x加成，又不会被岩浆淹没，且可以向下伸出泵头直接接触Y=31的岩浆海。

***

## 8. 平衡性分析

### 8.1 与现有发电方式对比

| 发电方式        | 等级     | 稳定输出            | 燃料成本   | 基建复杂度 | 特殊要求            |
| ----------- | ------ | --------------- | ------ | ----- | --------------- |
| 蒸汽锅炉(岩浆)    | LV     | \~30 EU/t       | 低      | 极低    | 无               |
| 半流质发电机(岩浆)  | LV     | \~32 EU/t       | 低      | 极低    | 无               |
| 燃气涡轮(天然气)   | LV-MV  | \~128 EU/t      | 中      | 低     | 气体供应            |
| **地热蒸发器路线** | **MV** | **\~384 EU/t**  | **极低** | **中** | **冷却液+下界**      |
| 大型蒸汽涡轮      | HV     | \~512 EU/t      | 中      | 中     | 转子              |
| **闪蒸路线**    | **HV** | **\~512 EU/t**  | **极低** | **中** | **下界**          |
| 大型燃烧引擎      | EV     | \~2048 EU/t     | 高      | 中     | 燃油+润滑油          |
| **热电转换器**   | **EV** | **\~1200 EU/t** | **极低** | **高** | **下界+热电材料+冷却液** |
| 氢燃料电池       | EV     | \~2048 EU/t     | 中高     | 中     | 氢气+氧气           |

### 8.2 平衡策略

**核心定位**：**低运营成本 + 高前期投入 + 维度锁定**

**优势**：

- 燃料几乎为零（岩浆+地狱岩均无限）
- 一次建成后近乎永动
- 副产物收益（金、硫、铁、石英、稀土）

**限制**：

- **必须在下界运行**才有足够效率（主世界可用但产出低50%+）
- 冷却液需要跨维度运输（首次建设成本）
- EV级需要碲化锑热电元件（有耐久损耗）
- 单台输出不及大型燃烧引擎（1200 vs 2048 EU/t）
- 下界建设本身有挑战（恶魂、岩浆、封闭空间）
- 闪蒸路线的 GeothermalVapor 有腐蚀性（不能用普通涡轮）

### 8.3 为什么玩家会选择地热系统？

1. **"建好就不管"**：不需要持续供应化石燃料
2. **利用下界基地**：如果已经有下界传输站/采矿点，顺便发电
3. **副产物收益**：持续产出金/硫/稀土，不仅是发电还有材料收益
4. **MV即可入门**：门槛低于大型燃烧引擎(EV)
5. **闪蒸路线零消耗品**：不需要冷却液、不需要转子、不需要润滑油

***

## 9. 实现架构

### 9.1 底层

| 组件                | 基类/位置                           | 说明                                                                                          |
| ----------------- | ------------------------------- | ------------------------------------------------------------------------------------------- |
| `GeothermalUtils` | `gregtech.api.util`             | 维度检测、Y坐标系数、加成计算                                                                             |
| 新RecipeMap        | `RecipeMaps.java` 扩展            | GEOTHERMAL\_EVAPORATOR, FLASH\_SEPARATOR, GEOTHERMAL\_TURBINE\_FUELS, THERMOELECTRIC\_FUELS |
| 新材料               | `HigherDegreeMaterials.java` 扩展 | MoltenNetherrack, GeothermalVapor, CooledLavaSlurry, OrganicWorkingFluid                    |
| 新方块               | `MetaBlocks` 扩展                 | ThermoelectricCasing（热电元件外壳）                                                                |

### 9.2 功能层

| 组件        | 继承类                             | 参考实现                                                      |
| --------- | ------------------------------- | --------------------------------------------------------- |
| 岩浆采集泵     | `TieredMetaTileEntity`          | 参考 `MetaTileEntityWindGenerator`（环境感知）                    |
| 地热蒸发器     | `RecipeMapMultiblockController` | 标准配方多方块                                                   |
| 闪蒸分离塔     | `RecipeMapMultiblockController` | 类似蒸馏塔结构                                                   |
| 下界蒸汽涡轮    | `FuelMultiblockController`      | 参考 `MetaTileEntityLargeTurbine`，带 `boostProduction`       |
| 熔岩热电转换器   | `FuelMultiblockController`      | 参考 `MetaTileEntityLargeCombustionEngine`，带维度加成            |
| 地狱岩裂解炉    | `RecipeMapMultiblockController` | 类似裂解单元结构                                                  |
| **地热裂化炉** | `RecipeMapMultiblockController` | 参考 `MetaTileEntityCrackingUnit`（线圈+WorkableHandler），加下界加成 |

### 9.3 关键集成点

1. **维度检测**：复用 `WorldProviderHell` 判定（同 PrimitiveWaterPump 风格）
2. **冷却液循环**：复用 `WaterCoolant` / `HotWaterCoolant`（已有材料+配方）
3. **蒸汽兼容**：GeothermalVapor 涡轮同时接受 HighPressureSteam（兼容现有蒸汽体系）
4. **副产物处理**：配方注册到现有离心机 RecipeMap
5. **方块检测**：岩浆泵检测相邻 `Blocks.LAVA`（同 `SteamRockBreaker` 的模式）
6. **基岩流体矿脉**：下界 `lava_deposit.json` 存在时给岩浆泵额外产量加成
7. **石化体系融合**：地热裂化炉产出的 ThermalCracked 流体进入现有 `DISTILLATION_RECIPES`，产出兼容现有小分子材料
8. **线圈系统**：地热裂化炉复用 `GTCasingGroups.heatingCoils()` + `GTStructureChannels.HEATING_COIL`（同裂化装置）

***

## 10. 配方注册

### 10.1 新增 RecipeMap

```java
// Geothermal Evaporator — heat exchange: lava + coolant → hot coolant + slurry
public static final RecipeMap<SimpleRecipeBuilder> GEOTHERMAL_EVAPORATOR_RECIPES = 
    new RecipeMapBuilder<>("geothermal_evaporator", new SimpleRecipeBuilder())
        .fluidInputs(2)
        .fluidOutputs(2)
        .sound(GTSoundEvents.BOILER)
        .build();

// Flash Separator — flash evaporation of lava
public static final RecipeMap<SimpleRecipeBuilder> FLASH_SEPARATOR_RECIPES = 
    new RecipeMapBuilder<>("flash_separator", new SimpleRecipeBuilder())
        .fluidInputs(1)
        .fluidOutputs(2)
        .itemOutputs(2)
        .sound(GTSoundEvents.BOILER)
        .build();

// Nether Steam Turbine (fuel map) — GeothermalVapor/HotWaterCoolant → EU
public static final RecipeMap<FuelRecipeBuilder> NETHER_STEAM_TURBINE_FUELS = 
    new RecipeMapBuilder<>("nether_steam_turbine", new FuelRecipeBuilder())
        .fluidInputs(1)
        .fluidOutputs(1)
        .sound(GTSoundEvents.TURBINE)
        .allowEmptyOutputs()
        .generator()
        .disableJeiOverclockButton()
        .build();

// Magma Thermoelectric Generator (fuel map)
public static final RecipeMap<FuelRecipeBuilder> THERMOELECTRIC_GENERATOR_FUELS = 
    new RecipeMapBuilder<>("thermoelectric_generator", new FuelRecipeBuilder())
        .fluidInputs(2)   // hot fluid + coolant
        .fluidOutputs(2)  // slurry + hot coolant
        .sound(GTSoundEvents.ELECTROLYZER)
        .allowEmptyOutputs()
        .generator()
        .disableJeiOverclockButton()
        .build();

// Netherrack Cracking Furnace
public static final RecipeMap<SimpleRecipeBuilder> NETHERRACK_CRACKING_RECIPES = 
    new RecipeMapBuilder<>("netherrack_cracking", new SimpleRecipeBuilder())
        .itemInputs(1)
        .fluidInputs(1)
        .fluidOutputs(1)
        .itemOutputs(3)
        .sound(GTSoundEvents.FIRE)
        .build();
```

### 10.2 副产物离心配方（注册到已有 CENTRIFUGE\_RECIPES）

```java
// CooledLavaSlurry centrifuging
RecipeMaps.CENTRIFUGE_RECIPES.recipeBuilder()
    .fluidInputs(CooledLavaSlurry.getFluid(1000))
    .output(dust, SiliconDioxide, 2)
    .output(dust, Iron, 1)
    .chancedOutput(dustTiny, Gold, 1, 1500, 500)      // 15% base
    .chancedOutput(dustTiny, Magnesium, 1, 4000, 1000) // 40%
    .duration(200).EUt(VA[MV]).buildAndRegister();

// VolcanicAsh centrifuging
RecipeMaps.CENTRIFUGE_RECIPES.recipeBuilder()
    .input(dust, VolcanicAsh, 4)
    .output(dust, Iron, 1)
    .output(dust, Sulfur, 2)
    .chancedOutput(dustTiny, RareEarth, 1, 2000, 500)  // 20%
    .chancedOutput(gem, NetherQuartz, 1, 2500, 500)     // 25%
    .duration(160).EUt(VA[MV]).buildAndRegister();
```

***

## 11. MTE ID 分配建议

| ID范围      | 组件             | 说明          |
| --------- | -------------- | ----------- |
| 1000-1002 | 岩浆采集泵 MV/HV/EV | 单方块 tiered  |
| 1003      | 地热蒸发器          | 多方块         |
| 1004      | 闪蒸分离塔          | 多方块         |
| 1005      | 下界蒸汽涡轮         | 多方块发电       |
| 1006      | 熔岩热电转换器        | 多方块发电       |
| 1007      | 地狱岩裂解炉         | 多方块         |
| 1008      | 地热裂化炉          | 多方块（石化体系融合） |

> **注意**：实际 ID 需要检查 `MetaTileEntities.java` 中的空闲区段确认。

***

## 12. 实施路线图

### 阶段一：基础设施

1. `GeothermalUtils` 工具类
2. 新材料/流体定义（MoltenNetherrack, GeothermalVapor, CooledLavaSlurry, VolcanicAsh, ThermalCracked系列）
3. 新 RecipeMap 注册（含 GEOTHERMAL\_CRACKING\_RECIPES）
4. 热电元件方块定义

### 阶段二：采集与处理

1. 岩浆采集泵（单方块，检测相邻岩浆，维度感知）
2. 地热蒸发器（多方块，Lava + Water/WaterCoolant 热交换）
3. 闪蒸分离塔（多方块，Lava → GeothermalVapor + VolcanicAsh）
4. 地狱岩裂解炉（多方块，Netherrack → MoltenNetherrack + Sulfur）

### 阶段三：发电

1. 下界蒸汽涡轮（FuelMultiblockController，处理 GeothermalVapor/HotWaterCoolant/Steam）
2. 熔岩热电转换器（FuelMultiblockController，Lava + WaterCoolant → EU）

### 阶段三b：石化融合

1. 地热裂化炉多方块（RecipeMapMultiblockController，耐热外壳+线圈+下界加成）
2. ThermalCracked 系列裂化配方注册
3. ThermalCracked 系列蒸馏配方注册
4. 下界环境加成逻辑（`GeothermalCrackerWorkableHandler`）

### 阶段四：整合与副产物

1. 离心配方注册（CooledLavaSlurry/VolcanicAsh → 矿物）
2. JEI 整合
3. 国际化

### 阶段五：打磨

1. 平衡性调优（三种裂化方式产物比率对比测试）
2. 纹理与渲染
3. 音效与粒子效果

***

## 13. 参考资料

- **MC 下界维度特性**：
  - 维度ID: -1，`WorldProviderHell`
  - `doesWaterVaporize() == true` — 水不可放置/使用
  - Y范围 0\~127，基岩上下封顶
  - 岩浆海面 Y=31，大面积裸露岩浆
  - 无天气、无昼夜
  - 地狱岩为主体方块（廉价无限资源）
  - 原生资源: 石英、荧石、灵魂沙、岩浆
- **项目内参考实现**：
  - `MetaTileEntityPrimitiveWaterPump` — 维度检测 + 下界禁用（`provider.isNether()`）
  - `MetaTileEntityWindGenerator` — 环境感知单方块发电
  - `SteamRockBreaker` — 检测相邻岩浆方块（`Blocks.LAVA`）
  - `MetaTileEntityLargeCombustionEngine` — `FuelMultiblockController` + boost机制
  - `MetaTileEntityFluidDrill` — 基岩流体提取逻辑
  - `BedrockFluidVeinHandler` — `lava_deposit.json`（下界岩浆矿脉）
  - `WaterCoolant` / `HotWaterCoolant` — 已有密封冷却液循环
  - `ChemicalBathRecipes` — 已有冷却液配方
  - `LargeTurbineType` — enum变体化架构模板
  - `DeclarativePatternBuilder` — 声明式结构定义
  - `MetaTileEntityCrackingUnit` — 现有裂化装置（线圈加成+`CrackingUnitWorkableHandler`）
  - `PetrochemRecipes` — 裂化配方注册模式（`lightlyCrack`/`moderatelyCrack`/`severelyCrack`）
  - `CRACKING_RECIPES` RecipeMap — 裂化配方Map定义（2 fluidInputs, 2 fluidOutputs）
- **现实地热发电参考**：
  - 闪蒸电站（Flash Steam）— 对应闪蒸分离塔
  - 双循环电站（Binary Cycle / ORC）— 对应热电转换器的概念（密封二回路）
  - 塞贝克热电效应 — 对应熔岩热电转换器
  - 冰岛 Hellisheiði 地热电站 — 闪蒸+二回路联合运行
- **现实石化裂化参考**：
  - 热裂化（Thermal Cracking）— 最早的裂化方式，纯热驱动，高温深度裂解
  - 蒸汽裂化（Steam Cracking）— 现代主流乙烯生产方式
  - 加氢裂化（Hydrocracking）— 精炼重油为轻质燃料
  - 焦化（Coking）— 极端热裂化，大量积碳（对应 ThermalCracked 的高积碳特征）
  - 催化裂化（FCC）— 使用催化剂降低裂化温度（未来可扩展方向）

***

## 附录A：与初版设计的主要变更

| 初版问题             | V2修正                | V3修正（当前）                                          |
| ---------------- | ------------------- | ------------------------------------------------- |
| 使用"水"作为热交换/冷却介质  | V2: 改为 WaterCoolant | V3: 水在密封管道/容器中不会蒸发，恢复水为MV路线介质，WaterCoolant为HV效率升级 |
| "地热井口"向下钻井提取地热流体 | 改为"岩浆采集泵"直接从相邻岩浆海抽取 | 保持                                                |
| Y层级假设下界有"深层"地热   | 修正为 Y=31 岩浆海面为参考线   | 保持                                                |
| "下界+主世界"通用设计     | 明确下界为主战场            | 保持，但MV路线主世界也可用                                    |
| 矿物沉淀池（依赖露天水蒸发）   | 改为离心机处理             | 保持                                                |
| "双循环ORC发电机"      | 重命名为"熔岩热电转换器"       | 保持                                                |
| 地热冷凝器（用于冷凝回水）    | V2: 删除              | V3: 恢复为涡轮内置蒸汽冷凝功能（输出DistilledWater），实现水循环闭环       |
| 生物群系影响           | 简化                  | 保持                                                |

## 附录B：MC 1.12.2 下界 vs 1.16+ 下界

本项目基于 **MC 1.12.2**，下界特性为：

| 1.12.2 下界                   | 1.16+下界（参考但不使用）     |
| --------------------------- | ------------------- |
| 单一生物群系（Hell）                | 多生物群系（玄武岩三角洲、绯红森林等） |
| 无 Netherite                 | 有远古残骸/下界合金          |
| 无 Basalt/Blackstone         | 有多种新石材              |
| 无 Piglins（只有 Zombie Pigmen） | 有猪灵交易系统             |
| Y=0\~127                    | Y=0\~255（部分版本）      |

因此本设计仅使用 1.12.2 可用的方块和机制。
