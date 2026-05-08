# 动物-工业联动系统设计方案

> **目标**：将 Minecraft 原版动物/生物体系与 GT 工业系统深度联动，使动物不再只是"杀了磨成粉"的原材料来源，而是工业链中有意义的一环。

***

## 现状分析

### 当前动物与GT的关系

| 动物/掉落物 | 当前用途 | 深度 |
|-----------|---------|------|
| 牛（Raw Beef） | 研磨→Meat dust→Collagen链 | 一次性原材料 |
| 鸡（Chicken） | 研磨→Meat dust | 一次性原材料 |
| 猪（Porkchop） | 研磨→Meat dust | 一次性原材料 |
| 羊（Mutton） | 研磨→Meat dust | 一次性原材料 |
| 牛奶（Milk） | 无GT用途 | ❌ 完全未利用 |
| 皮革（Leather） | 无GT配方（仅原版） | ❌ 完全未利用 |
| 羊毛（Wool） | 无GT配方 | ❌ 完全未利用 |
| 鸡蛋（Egg） | 无GT配方 | ❌ 完全未利用 |
| 蜘蛛丝（String） | 极少GT用途 | 弱 |
| 烈焰粉（Blaze Powder） | 烈焰棒=研磨材料 | 中（有材料用途） |
| 粘液球（Slime Ball） | 有RawRubber等化学用途 | 中 |
| 恶魂之泪（Ghast Tear） | 极少 | 弱 |
| 末影珍珠（Ender Pearl） | 有材料体系（plate/dust/fluid） | 强 |
| 墨囊（Ink Sac） | 无GT用途 | ❌ |

**结论**：绝大多数动物产出在GT中只走"研磨→肉粉"这一条路，失去了动物养殖的意义。GT的生物化学链（Growth Medium/Stem Cells）虽然用到肉粉，但不需要"活的动物"参与。

***

## 方案一：工业畜牧系统（Industrial Livestock）

### 核心思路

引入**工业化动物养殖**作为可持续的生物材料来源，替代"杀动物→研磨"的一次性消耗模式。

### 组件

#### 1.1 工业畜栏（Industrial Livestock Pen）

**类型**：多方块（RecipeMapMultiblockController）

**GT等级**：MV-HV

**功能**：在密封环境中自动化养殖动物，持续产出动物产品而不杀死动物。

**输入**：
- 饲料（Feed，由小麦/种子/GT肥料合成的流体）
- 水
- EU

**持续输出（不杀死动物）**：

| 动物类型 | 持续产出 | 产出速率 |
|---------|---------|---------|
| 牛 | Milk（牛奶流体） | 100mB/t |
| 牛 | Leather（定期脱落/再生） | 1个/200t |
| 羊 | Wool（定期剪毛） | 2个/200t |
| 鸡 | Egg（产蛋） | 1个/100t |
| 鸡 | Feather（换毛） | 2个/200t |
| 猪 | 无持续产出，但繁殖速度最快 | — |
| 鱿鱼 | Ink Sac | 1个/150t |
| 蜘蛛 | String（抽丝） | 3个/200t |

**动物来源**：使用"动物诱捕器"（Trap）捕获活动物，或通过Spawn Egg（如果有mod提供）

**特殊机制**：
- 动物有"幸福度"系统，影响产出效率
- 幸福度由以下因素决定：空间大小、饲料质量、水供应
- 下降到0时动物停止产出（但不死亡）

#### 1.2 饲料配方

```
[混合器 LV]
  Wheat ×4 + FERTILIZER ×1 + Water 1000mB
  → AnimalFeed 2000mB
  Duration: 100t, EUt: 8

[混合器 MV] (高级饲料，提高产量50%)
  Wheat ×2 + Meat dust ×1 + Bone dust ×1 + SterileGrowthMedium 100mB
  → PremiumFeed 2000mB
  Duration: 100t, EUt: 32
```

### 联动点

| 产出 | 联动到GT系统 | 路径 |
|------|-----------|------|
| Milk | 化学处理→乳酸→乳酸菌→生物塑料PLA | 新化工链 |
| Milk | 离心→Calcium + Fat + Lactose | 材料来源 |
| Leather | 化学鞣制→工业皮革→绝缘材料/隔热层 | 新材料 |
| Egg | 提取→卵磷脂（Lecithin）→乳化剂→化工用途 | 新化工链 |
| Wool | 化学处理→蛋白纤维→高强度生物纤维 | 新材料 |
| Feather | 化学处理→角蛋白（Keratin）→生物胶 | 新材料 |
| String | 编织→生物绳→绝缘线缆包裹 | 现有系统增强 |
| Ink Sac | 化学处理→碳纳米管前驱体 | 新化工链 |

***

## 方案二：生物反应器体系（Bioreactor System）

### 核心思路

利用动物细胞/组织作为生物反应器的原料，产出高端生物化学材料。将现有 Stem Cell 链路向上延伸。

### 组件

#### 2.1 组织培养器（Tissue Culture Chamber）

**类型**：多方块（RecipeMapMultiblockController）

**GT等级**：EV-IV（需要无菌洁净室）

**功能**：从活体动物组织中培养特定细胞系，持续产出生物材料。

**核心配方**：

```
[组织培养器]
  Raw Beef ×8 + SterileGrowthMedium 4000mB + Mutagen 100mB
  → MuscleCell fluid 2000mB + BacterialSludge 1000mB
  Duration: 600t, EUt: 480 (EV), Cleanroom: STERILE

[组织培养器]
  Leather ×4 + SterileGrowthMedium 2000mB + Mutagen 50mB
  → CollagenFiber fluid 1000mB + BacterialSludge 500mB
  Duration: 400t, EUt: 480 (EV), Cleanroom: STERILE

[组织培养器]
  Spider Eye ×8 + SterileGrowthMedium 4000mB + Mutagen 200mB
  → BioVenom fluid 500mB + BacterialSludge 2000mB
  Duration: 800t, EUt: 1920 (IV), Cleanroom: STERILE

[组织培养器]
  Ghast Tear ×4 + SterileGrowthMedium 2000mB + VoidEssence 100mB
  → EtherealExtract fluid 200mB + BacterialSludge 1000mB
  Duration: 1200t, EUt: 1920 (IV), Cleanroom: STERILE
```

#### 2.2 下游利用

| 培养产物 | 下游用途 | GT等级 |
|---------|---------|--------|
| MuscleCell | 人造肌肉纤维→高端电缆绝缘层 | IV |
| CollagenFiber | 生物复合材料→轻量化机器外壳 | IV |
| BioVenom | 高效腐蚀剂→替代HF酸某些用途 | EV |
| EtherealExtract | 末地系统增强介质（与末地方案联动！） | IV-LuV |
| Stem Cells (已有) | 生物CPU/生物计算芯片 | LuV |

***

## 方案三：敌对生物利用系统（Hostile Mob Industrial Utilization）

### 核心思路

下界/末地的敌对生物掉落物不再只是"垃圾"或极小众用途，而是维度能源系统的**催化剂或增幅材料**。

### 3.1 下界生物 → 地热系统联动

| 生物掉落 | 联动到地热系统 | 方式 |
|---------|------------|------|
| Blaze Rod（烈焰棒） | 地热蒸发器催化剂 | 放入物品输入仓：热交换效率 +30% |
| Blaze Powder | 地热裂化炉助燃剂 | 配方输入：裂化速度 +20%，减少积碳 |
| Magma Cream | 闪蒸分离塔润滑 | 流体输入：减少设备损耗 |
| Ghast Tear | 地热蒸发器防腐蚀 | 配方输入：延长管道寿命 |
| Wither Skeleton Skull | 超高温裂化（IV级别） | 解锁 "Wither Heat" 配方类别 |

#### 3.1.1 烈焰棒催化配方

```
[地热蒸发器 增强模式]
  Lava 200mB + Water 200mB + Blaze Rod ×1 (不消耗, 每1000次操作损耗1)
  → Steam 9600mB + CooledLavaSlurry 200mB  (比基础配方多50%蒸汽!)
  Duration: 8 (比基础快20%), EUt: 32
```

#### 3.1.2 凋零热裂化

```
[地热裂化炉 Wither Heat模式]
  Heavy Fuel 1000mB + Lava 500mB + Nether Star fragment ×1 (消耗)
  → WitherCrackedHeavyFuel 1500mB + CooledLavaSlurry 400mB
  Duration: 20, EUt: 480 (EV)

// WitherCrackedHeavyFuel 蒸馏产出更多高价值烯烃
```

### 3.2 末地生物 → 末地能量系统联动

| 生物掉落 | 联动到末地系统 | 方式 |
|---------|------------|------|
| Ender Pearl（末影珍珠） | ResonantEnderFluid 制备核心原料 | 已在末地设计中 ✓ |
| Shulker Shell（潜影壳） | 虚空能量采集器效率增幅 | 配方添加剂：VoidEssence产量 +50% |
| Dragon's Breath（龙息） | 龙息反应堆燃料 | 已在末地设计中 ✓ |
| Chorus Fruit（紫颂果） | 空间折叠催化剂 | 末影共振发电机效率 +25% |
| End Crystal（末影水晶） | 水晶阵列核心 | 已在末地设计中 ✓ |

#### 3.2.1 潜影壳增幅

```
[虚空能量采集器 增幅模式]
  Shulker Shell ×1 (每24000 tick消耗1个)
  → VoidEssence产量 ×1.5

// 原理：潜影贝是末地原生生物，其壳含有天然的虚空能量谐振结构
```

#### 3.2.2 紫颂果催化

```
[末影共振发电机 催化模式]
  ResonantEnderFluid 10mB/t + Chorus Fruit ×1 (每6000 tick消耗1个)
  → 输出 EU ×1.25

// 原理：紫颂果含空间传送能力→增强末影共振
```

### 3.3 主世界生物 → 通用系统联动

| 生物掉落 | 联动到GT系统 | 方式 |
|---------|-----------|------|
| Slime Ball（粘液球） | 已有Raw Rubber用途 | ✓ 已联动 |
| Spider Eye | BioVenom（方案二） | 生物反应器 |
| Bone | 已有Bone dust→Calcium | ✓ 部分联动 |
| Feather | 角蛋白→绝缘材料 | 新化工链 |
| Rabbit Foot | 幸运催化剂→提高chanced output概率 | 新机制 |
| Phantom Membrane | — | MC 1.12.2 没有 |

#### 3.3.1 兔子脚幸运催化

```
[任何有chancedOutput的机器]
  物品输入仓中放入 Rabbit Foot → "Lucky Catalyst" 模式
  效果: 所有 chancedOutput 的概率 +1000 (base 10%)
  每次成功触发额外产出时消耗 1 个 Rabbit Foot

// 这给了兔子脚一个真正有价值的GT用途！
```

***

## 方案四：生物能源系统（Bio-Energy System）

### 核心思路

将动物/生物的代谢过程本身作为能源来源——动物吃草、产甲烷、产热、发酵——形成一套**低端但完全可再生**的能源路线。

### 4.1 沼气发电站（Biogas Power Station）

**类型**：多方块

**GT等级**：LV-MV

**核心理念**：动物粪便/生物废料 → 厌氧发酵 → 沼气（Biogas，主要是甲烷+CO₂） → 发电

**物质流**：
```
[工业畜栏] → AnimalWaste (动物粪便, 新流体)
     ↓
[发酵罐] AnimalWaste + Bacteria → Biogas + Fertilizer(副产)
     ↓
[沼气发电机] Biogas → EU + CO₂ + 余热
     ↓
余热 → [温室/畜栏供暖] (提高动物幸福度和作物生长)
```

**发电参数**：
- Biogas: 32 EU/t（LV级别）
- 持续、稳定、完全可再生
- 副产物 Fertilizer 可用于种植系统

### 4.2 生物柴油链增强

项目已有 BioDiesel 配方（植物油→酯化→生物柴油）。增加动物脂肪路线：

```
[化学反应器]
  Milk 1000mB (或 AnimalFat from rendering)
  → AnimalFat 200mB + Lactose dust ×2 + Water 700mB

[化学反应器] 
  AnimalFat 1000mB + Methanol 1000mB + SodiumHydroxide dust ×1
  → BioDiesel 6000mB + Glycerol 1000mB
  // 动物脂肪→生物柴油，和植物油路线产量相当
```

### 4.3 生物发光/Bioluminescence

```
[化学反应器 HV]
  Ink Sac ×16 + Glowstone dust ×4 + Bacteria 1000mB
  → BioluminescentFluid 500mB

// 用途: 
// 1. 机器GUI照明覆盖板（无EU消耗照明方案）
// 2. 标记流体（染色标记不同流体管路）
// 3. 生物传感器（检测生物活性/Growth Medium质量）
```

***

## 方案五：基因工程系统（Genetic Engineering）

### 核心思路

利用GT的高端生物化学链（Stem Cells/Mutagen），可以改造动物基因，创造产出GT材料的改造动物。

### GT等级：IV-LuV（高端内容）

### 5.1 基因改造台（Genetic Modification Bench）

**类型**：多方块（需要无菌洁净室）

**功能**：将 Mutagen + 特定材料 → "基因改造模板"→ 应用于动物 → 改造动物产出GT材料

### 5.2 改造动物品种

| 改造动物 | 基础动物 | 基因模板材料 | 产出 |
|---------|---------|-----------|------|
| 硅化蜘蛛 | Spider | Silicon dust + Mutagen | 产出硅纤维（可替代光纤原料） |
| 金属羊 | Sheep | Copper/Tin dust + Mutagen | 羊毛含微量金属（剪→处理→金属粉） |
| 石油甲虫 | Silverfish | RawOil + Mutagen | 微量持续产出原油 |
| 荧光鱿鱼 | Squid | Glowstone + Mutagen | 产出荧光墨→磷光材料 |
| 聚合鸡 | Chicken | Polyethylene + Mutagen | 蛋壳含聚合物（可回收塑料） |

**注**：这个方案比较激进，可能不适合所有玩家口味。作为高端可选内容。

***

## 推荐优先级与组合

| 方案 | 复杂度 | 与现有系统融合度 | 游戏性 | 推荐优先级 |
|------|--------|--------------|--------|---------|
| 方案三：敌对生物利用 | 低 | **极高**（直接增强已设计的维度系统） | 高 | ⭐⭐⭐⭐⭐ |
| 方案四：生物能源 | 中 | 高（补充LV-MV能源选项） | 中 | ⭐⭐⭐⭐ |
| 方案一：工业畜牧 | 高 | 高（为方案二/四提供原料） | 高 | ⭐⭐⭐⭐ |
| 方案二：生物反应器 | 高 | 高（延伸现有Stem Cell链） | 中 | ⭐⭐⭐ |
| 方案五：基因工程 | 极高 | 中（较独立） | 高但争议 | ⭐⭐ |

### 最佳组合建议

**核心推荐**：方案三 + 方案四 + 方案一部分

理由：
1. **方案三**（敌对生物利用）几乎零新增多方块，只是向已有系统添加配方/催化剂机制。直接增强已设计的下界地热和末地能量系统，联动感极强。
2. **方案四**（生物能源）填补了 LV-MV 可再生能源的空白（当前只有太阳能和水车），且与动物养殖形成闭环。
3. **方案一部分**（牛奶/皮革/羊毛的化工利用）只需要添加配方，不需要新多方块。

***

## 实现路线

### 第一批（低成本高收益，仅配方扩展）

1. 动物掉落物→GT材料的新配方（皮革→绝缘材料、鸡蛋→卵磷脂、牛奶→乳酸/钙/脂肪）
2. 敌对生物掉落物作为催化剂（烈焰棒→地热加成、潜影壳→末地加成、紫颂果→末影共振加成）
3. 兔子脚幸运催化机制
4. 动物脂肪→生物柴油路线

### 第二批（中等成本，新流体/新机制）

5. 沼气发电系统（AnimalWaste→Biogas→发电）
6. 饲料合成配方
7. 组织培养器配方（延伸 GrowthMedium 链）

### 第三批（高成本，新多方块）

8. 工业畜栏多方块
9. 沼气发电站多方块
10. 发酵罐多方块

***

## 与已有设计的集成点

| 已有系统 | 动物联动 |
|---------|---------|
| 下界地热蒸发器 | Blaze Rod 催化 +30% 热交换效率 |
| 下界闪蒸分离塔 | Magma Cream 润滑减损耗 |
| 下界地热裂化炉 | Blaze Powder 助燃 +20% 速度 |
| 末地虚空能量采集器 | Shulker Shell 增幅 VoidEssence ×1.5 |
| 末地末影共振发电机 | Chorus Fruit 催化 +25% 输出 |
| 末地龙息反应堆 | Dragon Scale 催化（已有） |
| Growth Medium 链 | 牛奶→更高效 Collagen 路线 |
| BioDiesel 链 | 动物脂肪→BioDiesel（新路线） |
| 离心机 | 牛奶离心→Calcium + Fat + Lactose |
| 化学反应器 | 蛋→卵磷脂、皮革→鞣酸、羊毛→角蛋白 |
