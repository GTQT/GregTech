# 植物-工业联动系统设计方案

> **目标**：将 Minecraft 原版及 GT 植物体系与工业系统深度联动，使不同植物具有各自独特的工业用途，而非所有植物都统一走"PlantBall→BioChaff"或"Biomass→发酵"这两条同质化路径。

***

## 现状分析

### 当前植物与GT的关系

| 植物/农作物 | 当前GT路径 | 独特性 |
|-----------|---------|--------|
| 小麦（Wheat） | 8个→PlantBall→BioChaff 或 +Water→Biomass | ❌ 无独特性 |
| 土豆（Potato） | 同上 | ❌ 无独特性 |
| 胡萝卜（Carrot） | 同上 | ❌ 无独特性 |
| 甜菜（Beetroot） | 同上 | ❌ 无独特性 |
| 甘蔗（Sugar Cane） | 同上，可制糖(原版) | ❌ 弱独特性 |
| 仙人掌（Cactus） | 同上 | ❌ 无独特性 |
| 西瓜（Melon） | 无GT配方 | ❌ 完全未利用 |
| 南瓜（Pumpkin） | 无GT配方 | ❌ 完全未利用 |
| 可可豆（Cocoa） | 无GT配方 | ❌ 完全未利用 |
| 树苗/原木 | Biomass/焦炉/热解 | ⚠️ 有一定用途 |
| **橡胶树** | **StickyResin→RawRubber→Rubber** | ✅ **唯一有独特GT链的植物** |
| 红/棕蘑菇 | 8个→PlantBall | ❌ 无独特性 |
| 各种种子 | 压榨→SeedOil | ⚠️ 微弱用途 |
| 睡莲/花 | 无GT配方 | ❌ 完全未利用 |
| 藤蔓（Vine） | 无GT配方 | ❌ 完全未利用 |
| 地狱疣（Nether Wart） | 原版酿造 | ❌ GT未利用 |
| 紫颂花/果（Chorus） | 无GT配方 | ❌ 完全未利用 |

**核心问题**：
1. 所有植物都走相同的"PlantBall→BioChaff"路径，**植物没有个性**
2. 很多植物在GT中**完全没有用途**（西瓜、南瓜、可可豆、花等）
3. Rubber Tree 是唯一成功范例——它有独特的、不可替代的GT产物链
4. Biomass/Ethanol 链虽然用到植物，但所有植物产出相同结果，无差异

***

## 设计方案

### 方案一：植物特化化工链（Plant-Specific Chemistry）

#### 核心思路

每种（或每类）植物都有**独特的化学提取物**，形成专属下游链路。就像 Rubber Tree → RawRubber → Rubber 一样，其他植物也应该有"只有我能提供"的独特材料。

#### 1.1 甘蔗 → 生物乙醇快速路线

```
// 甘蔗是现实中乙醇的最高效来源（巴西生物乙醇产业）
[榨汁] Sugar Cane ×8 → Sugar Cane Juice 2000mB
[发酵] Sugar Cane Juice 1000mB + Bacteria 10mB → Ethanol 500mB + CO₂ 200mB
// vs 当前路线: Sugar Cane→PlantBall→BioChaff→Biomass→FermentedBiomass→蒸馏→Ethanol 150mB
// 直接路线效率: 3倍以上!
```

**联动**：Ethanol 是 BioDiesel 合成的关键原料、溶剂、清洁剂

#### 1.2 仙人掌 → 天然胶/粘性流体

```
// 仙人掌粘液在现实中可用作天然粘合剂
[榨汁] Cactus ×4 → CactusSlime 500mB
[化学反应器] CactusSlime 1000mB + Sulfur dust ×1 → NaturalAdhesive 500mB

// NaturalAdhesive 用途:
// 1. LV级线缆绝缘(替代Rubber的廉价早期选项，但性能弱)
// 2. 低端粘合剂(替代普通Glue某些配方)
// 3. 生物降解塑料前驱体
```

**联动**：为 LV-MV 玩家提供 Rubber 的早期临时替代品

#### 1.3 西瓜 → 柠檬酸（Citric Acid）

```
// 西瓜富含柠檬酸(现实)
[榨汁] Melon Slice ×8 → Melon Juice 2000mB
[蒸馏] Melon Juice 1000mB → CitricAcid 200mB + Water 600mB + Sugar dust ×1

// CitricAcid 用途:
// 1. 金属表面处理（清洗/除锈剂）→ 某些组装配方的前处理步骤
// 2. 食品级酸（对比 HCl/H₂SO₄ 工业酸）→ 特定精密化学合成
// 3. 螯合剂 → 水处理/污水回收
// 4. pH缓冲液 → 生物反应器/Growth Medium的效率提升
```

**联动**：Growth Medium链效率提升、金属加工替代酸

#### 1.4 南瓜 → 胡萝卜素/维生素A（β-Carotene）

```
// 南瓜和胡萝卜富含β-胡萝卜素
[榨汁] Pumpkin ×4 → Pumpkin Pulp 1000mB
[离心] Pumpkin Pulp 1000mB → BetaCarotene 100mB + Cellulose 500mB + Water 300mB

// BetaCarotene 用途:
// 1. 生物传感器材料（感光元件→光学设备）
// 2. 抗氧化剂 → 延长某些有机流体/材料的保质期（减缓降解）
// 3. 有机染料 → 光纤材料/LED前驱体
```

**联动**：光学/电子体系材料来源

#### 1.5 可可豆 → 可可脂/生物碱

```
[研磨] Cocoa Beans ×4 → CocoaPowder dust ×2 + CocoaButter 100mB
[化学提取] CocoaPowder ×8 + Ethanol 500mB → Theobromine dust ×1 + Caffeine dust ×1(20%)

// CocoaButter 用途:
// 1. 精密润滑剂（食品级→某些精密机械部件的无毒润滑）
// 2. 生物相容性涂层（医疗/stem cell相关配方的培养基添加剂）
// 3. 低温密封剂

// Theobromine/Caffeine 用途:
// 1. 生物催化剂 → Growth Medium效率+25%
// 2. 药物前驱体（如果有药物系统）
```

**联动**：Growth Medium链催化、精密机械润滑

#### 1.6 地狱疣（Nether Wart）→ 地狱碱（Nether Alkaloid）

```
// 地狱疣是下界独有的植物，应当产出独特的化合物
[化学反应器] Nether Wart ×8 + Ethanol 500mB + Sulfur dust ×1
  → NetherAlkaloid 200mB + BacterialSludge 100mB

// NetherAlkaloid 用途:
// 1. 地热裂化炉催化剂 → 裂化温度降低=EU消耗-15%
// 2. 耐热密封胶原料 → 下界机器外壳耐久+
// 3. 生物变异剂 → 替代部分Mutagen用途(更容易获得)
// 4. 与下界地热系统联动: 地热蒸发器效率+20%
```

**联动**：直接增强下界地热系统！下界种植地狱疣→产催化剂→本地地热系统效率提升

#### 1.7 紫颂花/果（Chorus）→ 空间活性提取物

```
// 紫颂果有传送效果→含"空间活性"物质
[化学反应器 IV] Chorus Fruit ×16 + VoidEssence 200mB + Ethanol 500mB
  → SpatialExtract 100mB + EnderPearl dust ×2

// SpatialExtract 用途:
// 1. 末影共振发电机效率+25% (已在末地设计中提及)
// 2. 末影传输节点效率提升（传输损耗-5%）
// 3. 空间存储介质（增加ME/数字存储容量）
// 4. 传送门稳定剂
```

**联动**：直接增强末地维度系统！在末地外岛种植紫颂花→产空间活性物→增强末地设备

#### 1.8 蘑菇 → 真菌培养/抗生素

```
// 青霉素就是从真菌中发现的
[生物反应器 HV] Mushroom ×8 + SterileGrowthMedium 1000mB + DistilledWater 2000mB
  → FungalCulture 1500mB + Bacteria 500mB
  Duration: 400t, Cleanroom: STERILE

[化学反应器] FungalCulture 1000mB + Ethanol 200mB
  → AntibioticCompound 100mB + BacterialSludge 800mB

// AntibioticCompound 用途:
// 1. 防止生物材料降解（延长Bio材料shelf life）
// 2. Growth Medium杀菌→提高Stem Cell产出纯度
// 3. 生物传感器防污（防止传感器被细菌污染）
```

**联动**：增强 GrowthMedium→StemCell 链的效率

#### 1.9 花（Flowers）→ 天然染料/精油

```
// 不同颜色的花→不同染料→特定材料着色
[榨汁] 各色花 ×16 → FloralEssence 200mB (颜色取决于输入花)
[蒸馏] FloralEssence 500mB → EssentialOil 50mB + NaturalDye dust ×2 + Water 400mB

// EssentialOil 用途:
// 1. 精密润滑（替代MolybdeniteLubricant在某些低温场景）
// 2. 有机溶剂（某些敏感化学反应的替代溶剂）
// 3. 香料/驱虫剂（如果有农业系统→提高作物产量）

// NaturalDye 用途:
// 1. 有机显示材料（LCD/LED相关化工）
// 2. 替代某些无机染料配方
```

#### 1.10 藤蔓/竹子 → 纤维素纤维

```
// 藤蔓和竹子(1.12无竹子但有甘蔗类似物)含大量纤维素
[研磨+化学浸泡] Vine ×16 + NaOH solution 500mB
  → Cellulose Fiber ×4 + Lignin dust ×2

// Cellulose Fiber 用途:
// 1. 纸张（已有）
// 2. 赛璐珞→早期塑料(替代PE在某些LV配方)
// 3. 硝化纤维素→炸药/推进剂
// 4. 碳纤维前驱体（PAN之外的另一路线: 纤维素→碳化→碳纤维）

// Lignin 用途:
// 1. 木质素→酚→酚醛树脂（现实路线）
// 2. 低成本粘合剂
// 3. 生物碳源（热解→活性炭）
```

**联动**：碳纤维前驱体替代路线！纤维素→碳化→碳纤维 vs PAN路线

***

### 方案二：工业温室（Industrial Greenhouse）

#### 核心思路

提供一个GT多方块来**自动化种植**，但关键是：温室的**输入**和**加成**大量使用GT系统产出的材料。形成"工业产出→温室加成→植物→化工原料→工业产出"的闭环。

#### 2.1 工业温室（Industrial Greenhouse）

**类型**：多方块（RecipeMapMultiblockController）

**GT等级**：MV-HV

**功能**：自动化种植，速度取决于输入的GT加成材料

**输入**：
- 种子/树苗（决定产出类型）
- Water（基础灌溉）
- EU（照明+温控）
- Fertilizer（GT肥料，加速生长）
- 可选加成：CO₂注入、微量元素溶液、Growth Medium

**产出倍率**：

| 加成输入 | 效果 | 来源 |
|---------|------|------|
| 无加成 | 基础产量（1x） | — |
| Fertilizer | 产量 ×1.5 | FermentedBiomass蒸馏副产物 |
| CO₂ | 产量 ×1.3 | 化石燃料燃烧/发酵副产物 |
| Fertilizer + CO₂ | 产量 ×2.0 | 组合 |
| GrowthMedium | 产量 ×3.0 + 生长速度×2 | 高端生化链 |
| CooledLavaSlurry（微量） | 产量 ×1.2 + 矿物质丰富 | **下界地热系统产出!** |
| VoidEssence（微量） | 生长速度 ×5（但不增加产量） | **末地系统产出!** |

**关键联动点**：
- 下界系统产出的 CooledLavaSlurry 含矿物质，作为温室的微量元素肥料
- 末地系统产出的 VoidEssence 中的空间能量加速生长（时空压缩）
- 温室产出特化作物→化工链→增强其他系统→产出更多加成材料

#### 2.2 温室配方示例

```
[工业温室 MV]
  Input: Wheat Seed ×1 + Water 1000mB + Fertilizer ×1
  Output: Wheat ×32 + Wheat Seed ×2 (种子回收)
  Duration: 600t, EUt: 32

[工业温室 MV - Enhanced]
  Input: Sugar Cane ×1 + Water 2000mB + Fertilizer ×2 + CO₂ 500mB
  Output: Sugar Cane ×48
  Duration: 400t, EUt: 32

[工业温室 HV - Nether Wart]
  Input: Nether Wart ×1 + Lava 100mB (替代水!) + SoulSandDust ×1
  Output: Nether Wart ×24
  Duration: 800t, EUt: 128
  // 地狱疣用Lava和灵魂沙代替水!

[工业温室 HV - Chorus]
  Input: Chorus Flower ×1 + VoidEssence 50mB + End Stone dust ×4
  Output: Chorus Fruit ×16 + Chorus Flower ×1(50%回收)
  Duration: 1200t, EUt: 128
  // 紫颂花用VoidEssence和末地石生长!
```

***

### 方案三：植物→能源链增强

#### 核心思路

植物不只是化工原料，也可以直接参与能源系统。

#### 3.1 生物质发电升级

当前 Biomass 只能发酵蒸馏出 Ethanol（微量）。增强路线：

```
// 高效热解路线
[热解炉 HV]
  Plant Ball ×16 + 无氧环境
  → BioChar ×8 + PyrolysisGas 4000mB + BioOil 2000mB

// PyrolysisGas: 可直接烧，热值比Methane高
// BioOil: 类似RawOil但可再生→进石化裂化体系
// BioChar: 活性炭/碳源/固体燃料
```

**联动**：BioOil 可以进入已有的石化裂化链（包括地热裂化炉！），形成**可再生石化原料**

#### 3.2 木质纤维素乙醇（第二代生物乙醇）

```
// 当前: 只有糖/淀粉→乙醇。现实中纤维素也可以→乙醇(更高效)
[酸处理 MV]
  Wood ×8 + SulfuricAcid 200mB
  → Cellulose ×4 + Lignin ×2 + Xylose 500mB

[酶解发酵 HV]
  Cellulose ×4 + Bacteria 100mB + Water 2000mB
  → Ethanol 2000mB + CO₂ 1000mB + BacterialSludge 500mB
  Duration: 800t, EUt: 128, Cleanroom: STERILE

// 比当前 "原木→Biomass→发酵→蒸馏→Ethanol 150mB" 效率高10倍！
// 但需要HV+无菌环境
```

**联动**：大幅提升 BioDiesel 生产链效率，间接增强 EV 燃烧引擎发电

#### 3.3 沼气/生物甲烷（与动物系统联动）

```
// 植物残渣+动物废料→沼气
[发酵罐 LV]
  PlantWaste 1000mB (来自温室副产物) + AnimalWaste 500mB (来自畜栏)
  → Biogas 3000mB + Fertilizer ×4
  Duration: 600t, EUt: 8

// Biogas ≈ Methane，可直接用于:
// 1. 燃气涡轮发电
// 2. 化学反应器作为碳源
// 3. 与地热裂化炉组合
```

**联动**：植物+动物废料→沼气→发电，形成农业能源闭环

***

### 方案四：植物材料学（Plant-Based Advanced Materials）

#### 核心思路

现实中有大量以植物为基础的先进材料（碳纤维、纤维素纳米晶体、生物塑料等）。在GT中引入这些路线作为某些合成材料的**替代/前驱路线**。

#### 4.1 纤维素→碳纤维（替代PAN路线）

```
// 现实中粘胶基碳纤维是PAN基之外的第二大碳纤维来源
[酸浸 MV] Wood/Vine/Sugarcane ×16 + NaOH 500mB → Cellulose Fiber ×8
[纺丝 HV] Cellulose Fiber ×4 → CelluloseFiber plate ×2
[碳化 HV] CelluloseFiber plate ×2 (pyrolyse at 1500K) → CarbonFiber plate ×1

// vs 现有PAN路线:
// Propene→Acrylonitrile→Polyacrylonitrile→CarbonFiber
// 纤维素路线更简单，但碳纤维质量较低(强度80%)
```

**联动**：碳纤维的替代来源（更易获取但品质略低），为LV-MV玩家提供早期碳纤维渠道

#### 4.2 生物塑料（PLA — 聚乳酸）

```
// 现实: 玉米淀粉→乳酸→聚乳酸(PLA)，最常见生物降解塑料
[发酵 MV] Sugar 1000mB (甘蔗/甜菜) + Bacteria 50mB → LacticAcid 800mB
[聚合 HV] LacticAcid 1000mB + Catalyst → PLA 500mB

// PLA 用途:
// 1. 替代PE/PP在某些LV-MV配方中(生物降解版)
// 2. 3D打印材料（如果有打印机系统）
// 3. 一次性模具材料（铸造配方的廉价一次性模具）
// 4. 食品级包装（与肥料/农业系统联动）
```

**联动**：PE/PP的可再生替代品，降低对石化路线的依赖

#### 4.3 天然橡胶增强

```
// 当前GT只有Rubber Tree→Resin→Rubber一条路
// 增加: 蒲公英橡胶(Russian Dandelion，现实中有研究)
[工业温室] Dandelion ×1 + Water + Fertilizer → Dandelion Root ×4
[榨汁] Dandelion Root ×8 → NaturalLatex 200mB + PlantWaste 500mB
[化学处理] NaturalLatex 500mB + Sulfur dust ×1 → Rubber 500mB (vulcanized)

// 产量低于Rubber Tree，但:
// 1. 不需要等树生长
// 2. 可在工业温室中全自动
// 3. 为不想种橡胶树的玩家提供替代路线
```

#### 4.4 木质素 → 酚醛树脂 → 隔热材料

```
// Lignin 是制浆造纸的废料，但可以变废为宝
[热解 MV] Lignin ×8 → Phenol 500mB + Methanol 200mB + BioChar ×2
[化学反应器] Phenol 500mB + Formaldehyde 300mB → PhenolicResin 400mB

// PhenolicResin 用途:
// 1. 隔热板 → 下界机器的耐热外壳替代材料
// 2. 电路板基材（FR4的廉价替代）
// 3. 粘合剂 → 某些组装配方的黏合步骤
```

**联动**：为下界地热系统的耐热外壳提供生物基替代材料

***

### 方案五：维度特色植物系统

#### 核心思路

每个维度有独特植物，这些植物只能在该维度种植，且直接参与对应维度系统的效率提升。形成"维度系统需要维度植物→维度植物需要维度系统的副产物做肥料"的互依闭环。

#### 5.1 下界植物 → 地热系统联动

| 植物 | 产出 | 联动到地热系统 |
|------|------|------------|
| 地狱疣 | NetherAlkaloid | 地热裂化炉催化剂(-15% EU) |
| 地狱蘑菇（概念） | HeatResistantFiber | 耐热管道密封材料(延长寿命) |
| 岩浆花（概念） | MagmaticNectar | 闪蒸分离塔效率+20% |

**闭环**：地热系统副产物(CooledLavaSlurry/Sulfur)→下界植物肥料→植物产出催化剂→增强地热系统

#### 5.2 末地植物 → 末地能量系统联动

| 植物 | 产出 | 联动到末地系统 |
|------|------|------------|
| 紫颂花/果 | SpatialExtract | 末影共振效率+25%（已设计） |
| 虚空兰（概念） | VoidPollen | 虚空能量采集器范围×2 |
| 末影苔藓（概念） | EnderMoss | 末影传输节点效率+5% |

**闭环**：末地系统副产物(VoidEssence废液)→末地植物肥料→植物产出增幅材料→增强末地系统

#### 5.3 主世界植物 → 通用系统基础

主世界植物作为所有系统的"基础原料层"：
- 甘蔗→Ethanol→BioDiesel→燃烧引擎
- 木材→碳纤维→高端结构
- 蘑菇→抗生素→GrowthMedium纯度
- 花→精油→精密润滑

***

## 综合系统架构

### 植物-工业闭环示意

```
                        ┌─── [GT工业系统] ───┐
                        │                    │
                   Fertilizer           CO₂ + 余热
                   CooledLavaSlurry      VoidEssence
                   GrowthMedium
                        │                    │
                        ↓                    ↓
               ┌─── [工业温室] ───────────────┐
               │                              │
        ┌──────┼────────┬────────┬────────────┤
        ↓      ↓        ↓        ↓            ↓
     [甘蔗]  [地狱疣] [紫颂花] [仙人掌]     [蘑菇]
        │      │        │        │            │
        ↓      ↓        ↓        ↓            ↓
    Ethanol  Nether   Spatial  Cactus      Fungal
             Alkaloid Extract  Slime       Culture
        │      │        │        │            │
        ↓      ↓        ↓        ↓            ↓
   BioDiesel  地热    末地     LV绝缘     GrowthMedium
   燃烧引擎  裂化炉   共振    替代橡胶      增强
              +20%   +25%                    
        │      │        │                    │
        └──────┴────────┴─────── EU/材料 ────┘
                        │
                   回到工业系统!
```

### 渐进式解锁

| GT等级 | 解锁植物功能 | 核心联动 |
|--------|------------|---------|
| LV | 甘蔗→快速Ethanol，仙人掌→天然胶 | BioDiesel, 早期线缆替代 |
| MV | 工业温室自动化种植，木材纤维素链 | 自动化原料供应，碳纤维前驱 |
| HV | 蘑菇→抗生素，南瓜→β-胡萝卜素，BioOil | GrowthMedium增强，可再生石化 |
| EV | 地狱疣→催化剂，PLA生物塑料 | 下界地热增强，PE/PP替代 |
| IV | 紫颂花→空间提取物，组织培养器 | 末地系统增强，高端生化 |

***

## 平衡性分析

### 与现有系统对比

| 对比项 | 当前 | 加入植物联动后 |
|--------|------|-------------|
| Ethanol 产量 | Biomass→蒸馏 150mB/原木 | 甘蔗直接发酵 500mB/8甘蔗 |
| 碳纤维获取 | PAN路线(HV化工) | 纤维素路线(MV，品质80%) |
| Rubber替代 | 无 | 仙人掌胶(LV临时)/蒲公英(MV替代) |
| BioDiesel效率 | SeedOil + Ethanol | AnimalFat/SeedOil + 高效Ethanol |
| GrowthMedium效率 | 标准 | +25%(可可碱催化)/+纯度(抗生素) |
| 地热系统效率 | 基础 | +20%(NetherAlkaloid催化) |
| 末地系统效率 | 基础 | +25%(SpatialExtract催化) |

### 设计原则

1. **植物路线不完全替代化工路线**：品质/产量略低，但可再生+更易获取
2. **不同植物不可互替**：每种植物有独特产出，不再"所有植物=PlantBall"
3. **温室是可选的**：手动种植仍然可以，温室只是自动化+效率提升
4. **维度植物增强维度系统**：但不是必需的（没有植物系统照样能跑，有了更好）
5. **形成闭环**：系统副产物→植物肥料→植物→系统催化剂→系统产出

***

## 实施路线

### 第一批（零/低新增多方块，仅配方扩展）

1. 甘蔗直接发酵→Ethanol（新配方，注册到现有机器）
2. 仙人掌→CactusSlime→NaturalAdhesive（新流体+配方）
3. 西瓜→CitricAcid（新流体+配方）
4. 蘑菇→FungalCulture→AntibioticCompound（新配方到现有生物反应器）
5. 地狱疣→NetherAlkaloid（新流体+地热系统催化配方）
6. 紫颂果→SpatialExtract（新配方+末地系统联动）
7. 各类花→EssentialOil/NaturalDye（新配方）
8. 木材→Cellulose+Lignin分离配方
9. BioOil热解路线

### 第二批（中等新增，新流体/材料链）

10. PLA生物塑料链（LacticAcid→PLA）
11. 纤维素→碳纤维替代路线
12. Lignin→PhenolicResin→隔热材料
13. 可可→Theobromine→GrowthMedium催化
14. 南瓜→BetaCarotene→光学材料

### 第三批（新多方块）

15. 工业温室多方块
16. 发酵罐（专用，vs 通用化学反应器）
17. 维度专属植物（概念设计，需要自定义方块/物品）

***

## 与其他设计文档的联动汇总

| 本文档组件 | 联动的其他系统 | 联动方式 |
|-----------|------------|---------|
| NetherAlkaloid(地狱疣) | 下界地热裂化炉 | 催化剂，EU-15% |
| NetherAlkaloid(地狱疣) | 下界地热蒸发器 | 效率+20% |
| SpatialExtract(紫颂果) | 末地末影共振发电机 | 效率+25% |
| SpatialExtract(紫颂果) | 末地传输节点 | 传输损耗-5% |
| CooledLavaSlurry(地热副产) | 工业温室 | 肥料，产量×1.2 |
| VoidEssence(末地副产) | 工业温室 | 生长速度×5 |
| Ethanol(甘蔗快速路线) | BioDiesel→燃烧引擎 | 提升燃料供应效率 |
| BioOil(植物热解) | 地热裂化炉 | 可再生裂化原料 |
| Cellulose→CarbonFiber | 碳纤维线缆/结构 | 替代PAN路线 |
| PhenolicResin(木质素) | 下界机器耐热外壳 | 生物基替代材料 |
| AntibioticCompound(蘑菇) | GrowthMedium→StemCell | 纯度/效率提升 |
| Biogas(温室+畜栏废料) | LV-MV发电 | 可再生能源 |
| Fertilizer(沼气/蒸馏副产) | 工业温室 | 温室加速 |
| CO₂(燃烧/发酵副产) | 工业温室 | 温室加速 |
