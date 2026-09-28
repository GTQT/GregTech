# GT Worldgen 矿脉注册指南（纯代码注册）

> 本项目的矿脉/基岩流体定义**全部通过代码注册**，不再有任何 JSON。
> 附属模组（addon）使用本指南的 API 即可注册自定义矿脉。

---

## 1. 概览

| 类 | 用途 |
|---|---|
| `gregtech.api.worldgen.config.OreDepositBuilder` | 普通矿脉（地下矿体 + 地表指示物） |
| `gregtech.api.worldgen.config.BedrockFluidDepositBuilder` | 基岩流体矿脉（流体钻机抽取的那类） |
| `gregtech.api.worldgen.config.WorldGenRegistry` | 注册表入口（`getOreDeposits()` / `addVeinDefinitions()` / `addNamedDimension()`） |
| `gregtech.api.worldgen.config.DepositBuilder` | 上述两个 builder 的公共基类（通用字段方法） |

注册流程一句话：**builder 链式配置 → `build()` 构建定义 → `registry.addVeinDefinitions()` 注册**，
或直接用便捷方法 `buildAndRegister(registry)`。

一条普通矿脉由三个**互相独立**的组件拼成，任意组合都能用：

| 组件 | 决定什么 | 内置实现 |
|---|---|---|
| **shape** | "哪些坐标要放方块"（几何） | `LayeredGenerator` / `SphereGenerator` / `EllipsoidGenerator` / `PlateGenerator` / `SingleBlockGenerator` / `SlabGenerator` / 自定义 |
| **filler** | "每个坐标放什么方块" | `LayeredBlockFiller` / `SimpleBlockFiller` / `BlacklistedBlockFiller` |
| **populator** | 区块填充阶段的额外内容（地表指示物、喷泉…） | `SurfaceRockPopulator` / `SurfaceBlockPopulator` / `FluidSpringPopulator` / `FluidBallPopulator` |

基岩流体矿脉没有 shape/filler/populator，它只需要一个流体。

## 2. 注册时机

- `WorldGenRegistry.initializeRegistry()` 在 GT 的 `init` 阶段执行，注册全部默认定义，**没有任何锁定机制**
- addon 在**自己的 `init` 或 `postInit`**（`@Mod(dependencies = "required-after:gregtech")` 保证在 GT 之后）注册即可
- `oreVeinCache` 是懒加载的弱引用缓存，服务器启动时尚未建立，注册后首次世界生成时自动包含新定义
- 若 addon 使用了 bedrockOres 联动（`VeinSystemInit.postInit` 会把 GT 矿脉同步为虚拟矿脉），需保证在 GT 的 `postInit` 之前完成注册
- 基岩流体矿脉在 `build()` 里就已经写进了 `BedrockFluidVeinHandler`，所以**不要在世界已经跑起来之后再注册**

## 3. 注册普通矿脉（OreDepositBuilder）

### 3.1 标准分层矿脉（最常见形态）

```java
OreDepositBuilder.definitionBuilder("myaddon/copper_vein")
        .translationKey("myaddon.vein.copper")       // JEI 显示名（lang 键）
        .description("...")                          // JEI 描述（可选）
        .weight(30)                                  // 权重，参与该维度矿脉抽取
        .density(0.2f)                               // 方块放置密度（0~1）
        .minHeight(10)
        .maxHeight(60)
        .surfaceRock(Materials.Copper)               // 地表指示物（surface rock）
        .layeredGeneration(17, 24)                   // 分层椭球半径范围 [min, max)
        .layeredFill(Materials.Chalcopyrite, Materials.Iron,
                Materials.Pyrite, Materials.Copper)  // 主层/次层/中间层/散矿层
        .buildAndRegister(WorldGenRegistry.INSTANCE);
```

`layeredFill` 四个材料即 JSON 时代的 `primary/secondary/between/sporadic` 四层，
分层依据是 `layer = 该方块到本区块内这条矿脉最低方块的高度差`，默认
`primaryLayers = 4`、`secondaryLayers = 3`、`betweenLayers = 3`、`sporadicDivisor = 6`：

| layer | 结果 |
|---|---|
| 0~2 | 以 secondary（第 2 个材料）为主 |
| ≥ 3 | 以 primary（第 1 个材料）为主 |
| 2~4 | 先按 `density / 2` 掷 between（第 3 个材料） |
| 都没中 | 按 `density / 6` 掷 sporadic（第 4 个材料），再没中就保留原方块（该位置等于没生成） |

`layeredGeneration(radiusMin, radiusMax)` 的半径掷法与其它 shape 一致：`min == max` 时取 `min`，
否则取 `min + gridRandom.nextInt(max - min)`（即 `[min, max)`）。

### 3.2 球体矿脉：石材球（岩石球）

默认定义里的 `stoneSphere(...)`（玄武岩/黑花岗岩/大理石/红花岗岩球）就是这一类：

```java
OreDepositBuilder.definitionBuilder("myaddon/granite_sphere")
        .translationKey("myaddon.vein.granite_sphere")
        .weight(90)
        .density(1.0f)                               // 实心球：每个位置都放
        .minHeight(10)                               // 没有 maxHeight → 以地形顶面为限
        .priority(100)                               // 高优先级 = 先生成
        .countAsVein(false)                          // 不占用矿脉名额
        .dimensionId(0)
        .sphereGeneration(10, 20)                    // 球半径 [10, 20)
        .stoneSmoothSphereFill(StoneVariantBlock.StoneType.RED_GRANITE)
        .buildAndRegister(WorldGenRegistry.INSTANCE);
```

每一项为什么这么写：

- **`sphereGeneration(10, 20)`**：半径 `[10, 20)`，`getMaxSize()` 为 `(2r, 2r, 2r)`，所以引擎会把
  `maxHeight` 收缩 `r + 4` 格（见 §6）。
- **`density(1.0f)`**：默认密度通道下 `density < random` 才跳过，1.0 永远不会跳过 → 实心球。
- **`stoneSmoothSphereFill(type)`** = `ignoreBedrockFill(FillerEntry.createSimpleFiller(石方块))`：
  `BlacklistedBlockFiller` 会跳过基岩，其余位置直接替换成光滑石材变体。**球体必须忽略基岩**，
  否则岩浆湖底/世界底部的基岩会被石头替换掉。
  想放自家方块就自己包一层：
  ```java
  .ignoreBedrockFill(FillerEntry.createSimpleFiller(MyBlocks.MY_STONE.getDefaultState()))
  ```
- **`priority(100)`**：优先级大的**先生成**。石材球先铺出一大块石头，随后生成的普通矿脉会用
  `generationPredicate`（默认 `PREDICATE_STONE_TYPE`，判定 `StoneType.computeStoneType(...) != null`）
  判定"当前是石头"，于是**把球里的石头替换成矿**——这正是 GT "石材球里长矿" 的机制。
- **`countAsVein(false)`**：不占用该 3×3 chunk 网格的矿脉名额，抽中它之后引擎会再多抽一次（见 §6）。
  另外 `generateVeinsInCenterOfChunk` 只对 `isVein()` 为真的定义生效，所以球体中心是网格内随机位置，
  不会像普通矿脉那样固定生在区块中心。
- **`dimensionId(0)`**：只主世界。**不写维度过滤时默认是 `WorldProvider::isSurfaceWorld`**，
  也就是所有"地表型维度"（含暮色森林等 mod 维度）都会生成，通常不是你想要的。

### 3.3 球体矿脉：原油球 / 流体球

```java
OreDepositBuilder.definitionBuilder("myaddon/oil_sphere")
        .translationKey("myaddon.vein.oil_sphere")
        .weight(50)
        .density(1.0f)
        .minHeight(10)
        .maxHeight(40)
        .priority(-100)                              // 低优先级 = 最后生成
        .countAsVein(false)
        .dimensionId(0)
        .generationPredicateAny()                    // 任意方块都可被替换（连矿石也能被油顶掉）
        .biomeWeightModifierDictionary(ImmutableMap.of("sandy", 5))
        .sphereGeneration(9, 13)
        .fluidSpring(Materials.RawOil.getFluid().getBlock().getDefaultState(), 0.40f)
        .fluidFill(Materials.RawOil.getFluid())
        .buildAndRegister(WorldGenRegistry.INSTANCE);
```

- **`fluidFill(fluid)`** = `ignoreBedrockFill(simpleFiller(流体方块))`：球体内部填流体方块
  （`LEVEL = 0` 的源方块），写入后由原版流体逻辑自然流动扩散。
- **`fluidSpring(state, chance)`**：`FluidSpringPopulator`（`VeinBufferPopulator`），在矿脉生成时
  **掷一次骰**（`chance = 0.40f`）。中了就从矿脉中心上方第 1 格开始向上打一口井：
  地表以下为 5 格宽的十字形通道，地表以上为 6~8 格高的单柱，全部是 `LEVEL = 0` 的源方块——
  也就是一口能在地表看到的油泉。**`state` 必须是带 `LEVEL` 属性的流体方块**
  （GT 的 `BlockFluidBase` 子类，`Materials.X.getFluid().getBlock()` 都是），否则会在运行时抛异常。
- **`fluidBall(state, chance)`**：流体球，目前实现里把"矿脉中心的绝对 Y"又当成相对偏移用了一次
  （`FluidBallPopulator` 里的 `centerY + y`），实际会把球放到离矿脉很远的空中，**暂时不要用**，
  需要流体空腔请用 `fluidSpring`。
- **`priority(-100)`**：最后生成 → 配合 `generationPredicateAny()`，油球可以直接覆盖掉先前生成的矿石，
  形成"油泡吃掉矿"的效果。反过来，石材球用 `priority(100)` 先生成，才轮到矿脉往里长。
- **`biomeWeightModifierDictionary(ImmutableMap.of("sandy", 5))`**：按 Forge 生物群系字典标签
  给权重**加** 5（是增量不是倍率，可写负数把定义从某些群系里排除）。见 §3.5。

### 3.4 维度过滤

```java
.dimensionId(42)                             // 按维度 ID（addon 自定义维度推荐）
.dimensionId(0, 41)                          // 多个维度 ID，命中任一即可
.dimensionName("the_end")                    // 按维度类型名（overworld / the_nether / the_end）
.overworldOnly()                             // 等价于默认过滤：任意 isSurfaceWorld 维度
.netherOnly()                                // WorldProviderHell
.endOnly()                                   // WorldProviderEnd
.dimensionFilter(wp -> wp.getDimension() > 0) // 任意谓词
```

自定义维度如果要在 JEI 里显示可读名字，注册时补一句：

```java
WorldGenRegistry.INSTANCE.addNamedDimension(42, "My Dimension");
```

### 3.5 生物群系权重修正

```java
.biomeWeightModifierDictionary(ImmutableMap.of("ocean", 5, "sandy", 10))  // 按字典标签，权重增量
.biomeWeightModifierMap(ImmutableMap.of("minecraft:ocean", 150))          // 按生物群系注册名直接给权重
.biomeWeightModifier(biome -> biome.getTemperature() > 1.0 ? 20 : 0)      // 任意函数
```

实际抽取权重 = `weight + biomeWeightModifier(biome)`，且**权重 ≤ 0 的定义在该群系里会被过滤掉**。

### 3.6 生成谓词与地表指示物

```java
.generationPredicate((state, world, pos) -> ...)   // 替换条件；拿到的是 IBlockAccess + BlockPos
.surfaceRock(Materials.Copper)                     // 地表撒 GT 小石块
.surfaceBlock(Blocks.IRON_ORE.getDefaultState())   // 地表换成指定方块（默认随机 1~2 处 + 中心一处）
```

- `generationPredicate` 默认是 `PREDICATE_STONE_TYPE`（只认 GT 石头类型），
  想覆盖任意方块用 `.generationPredicateAny()`，想只认某几种方块自己写 lambda。
- `surfaceRock` 有一个隐藏条件：`SurfaceRockPopulator` 会先检查该区块内这条矿脉**产出的方块**
  能不能对应到带 `ORE` 属性的材料（或材料流体），否则不撒；所以石材球这类"不产矿"的定义
  撒不出地表石。它每个区块撒 1~2 次随机位置，外加矿脉中心一次，`y ≤ 20` 与超平坦世界跳过。
- `surfaceBlock` 没有上述限制，但它要求落点本身可替换（草/雪/空气/流体之类），
  且落点下方是不透明完整方块（`isOpaqueCube() && isFullBlock()`）。

### 3.7 其他可选配置

```java
.priority(-100)              // 生成优先级：大的先生成
.countAsVein(true/false)     // 是否占用每格的矿脉名额
.minHeight / .maxHeight      // 高度限制（会再被 getMaxSize().Y/2 + 4 收缩）
.description("...")          // JEI 描述
```

**`weight` 与 `density` 必填**（`verifyProperties()` 会校验 `weight != 0`、`density != 0`、
filler 与 shape 非空）。

## 4. 注册基岩流体矿脉（流体钻机抽的油田 / 岩浆田）

```java
BedrockFluidDepositBuilder.definitionBuilder("myaddon/geyser_deposit")
        .translationKey("myaddon.vein.geyser")       // JEI 显示名
        .weight(20)
        .yields(150, 300)                            // 总产量范围 [min, max)
        .depletion(1, 100, 30)                       // 每次耗尽量、耗尽几率 [0,100]、耗尽后产量
        .dimensionId(42)
        .biomeWeightModifierDictionary(ImmutableMap.of("ocean", 5))
        .fluid(Materials.Oil.getFluid())             // 直接传 Fluid 实例
        .buildAndRegister(WorldGenRegistry.INSTANCE);
```

| 字段 | 含义（运行时的真实行为） |
|---|---|
| `fluid(Fluid)` | 必填，只有它会被校验 |
| `weight(int)` | 参与抽取的权重；与生物群系修正相加，与普通矿脉是**两套独立**的权重表 |
| `yields(min, max)` | 一个油田的**总产量**：生成时按 `[min, max)` 掷一次并写进存档 |
| `depletion(amount, chance, depletedYield)` | 钻机按机型自带的几率触发耗尽判定后，再由定义决定：按 `chance%` 让剩余 operations 减 `amount`（`chance == 0` 表示永不枯竭）；`depletedYield` 是枯竭后的保底产量 |
| 维度/群系过滤 | 与普通矿脉同一套（`dimensionId` / `dimensionName` / `dimensionFilter` / `biomeWeightModifier*`） |

运行时要点：

- 一个基岩流体矿脉占 **8×8 chunk（128×128 格）**，同一片区域内所有区块共用同一个定义、总产量与剩余量。
- 产量是**持久化**的（`BedrockFluidVeinSaveData`）：区块生成过一次后再改定义，只影响新存档/新区域。
- 单次抽取量 = `max(depletedYield, 总产量 × 剩余operations / 100000) × 钻机倍率`（超频 ×1.5），
  所以随着 operations 被耗掉，产量会线性下降，最后稳定在 `depletedYield`。
- `build()` 内部会自动调用 `BedrockFluidVeinHandler.addFluidDeposit(definition)`，注册即生效
  （流体钻机、基岩流体泉、JEI 页面均立即可见）。

默认定义里 6 个主世界油田 + 1 个下界岩浆田全部在 `WorldgenDefinitions.registerBedrockFluidVeins`
里，可以对照数值。

## 5. 高级：自定义 shape / filler / populator

### 5.1 大平板矿脉（SlabGenerator）

`SlabGenerator(radiusMin, radiusMax[, yRadius])`：把矿脉球拉伸成正方形再压扁——X/Z 用**同一个**半径
（正方形 footprint，掷法与 `SphereGenerator` 完全一致），Y 只有 `yRadius` 那么厚；builder 捷径是
`slabGeneration(radiusMin, radiusMax[, yRadius])`，默认 `yRadius = 1`（3 格厚，`3` → 7 格厚）。
方块走普通密度通道，所以 `density` 决定板的疏密（实心板就设 `density(1.0f)`）。

示例一：薄矿板 + 四材料分层填充 + 地表指示物

```java
OreDepositBuilder.definitionBuilder("myaddon/shallow_plate")
        .translationKey("myaddon.vein.shallow_plate")
        .weight(40)
        .density(0.35f)
        .minHeight(20)
        .maxHeight(50)
        .overworldOnly()
        .slabGeneration(4, 10, 2)                    // 半径 4~9 → 9~19 宽、5 格厚
        .layeredFill(Materials.Copper, Materials.Tin,
                Materials.Cassiterite, Materials.Pyrite)
        .surfaceRock(Materials.Copper)
        .buildAndRegister(WorldGenRegistry.INSTANCE);
```

示例二：单一方块的大平板，只在石头里生成、且不占用矿脉位

```java
OreDepositBuilder.definitionBuilder("myaddon/anthracite_layer")
        .translationKey("myaddon.vein.anthracite")
        .weight(650)                                 // 主世界默认池权重和 ≈1670，650 ≈ 三格出一格
        .density(0.25f)
        .countAsVein(false)                          // 不挤占该格普通矿脉，额外多生成一条
        .minHeight(52)
        .maxHeight(83)
        .dimensionId(0)
        .generationPredicate(MyAddon::isHostStone)   // shape 拿不到 World，宿主方块判定放这里
        .slabGeneration(8, 40, 3)                    // 半径 8~39 → 17~79 宽、7 格厚
        .simpleFill(MyAddonBlocks.ANTHRACITE_ORE.getDefaultState())
        .buildAndRegister(WorldGenRegistry.INSTANCE);
```

关于尺寸与高度的三个数字：

- **`getMaxSize()` 是各轴最大偏移的两倍**：`(radiusMax * 2, yRadius * 2, radiusMax * 2)`。引擎用
  `getMaxSize().getY() / 2 + 4 = yRadius + 4` 收缩 `maxHeight`，用 X/Z 算贫矿壳——示例二 `yRadius = 3`
  收缩 7 格，`.minHeight(52).maxHeight(83)` 得到中心 Y 52~75、板子实际覆盖 49~78（还会被地形顶面再夹一次）。
- **半径上限 48**：引擎只在矿脉中心所在的 3×3 chunk 网格（中心 ±48 格）内落块，越界的方块会被静默丢弃，
  所以构造器直接用 `IllegalArgumentException` 拦下 `radiusMax > 48`。
- **半径决定体量**：半径 39（79 格宽）× 7 格厚在 `density(0.25f)` 下约 1.1 万方块，调 `weight` 之前先
  想清楚这个量级。

关于填充：`layeredFill` 在薄板上会退化（第 1 个材料要 `yRadius ≥ 2` 才够得到 `layer ≥ 3`），
薄板想要多种矿混在一起用 `weightRandomFill(...)` 更直观：

```java
.weightRandomFill(ImmutableList.of(
        Pair.of(4, new FillerConfigUtils.OreFilterEntry(OreConfigUtils.getOreForMaterial(Materials.Copper))),
        Pair.of(2, new FillerConfigUtils.OreFilterEntry(OreConfigUtils.getOreForMaterial(Materials.Tin))),
        Pair.of(1, new FillerConfigUtils.OreFilterEntry(OreConfigUtils.getOreForMaterial(Materials.Cassiterite)))))
```

### 5.2 自定义 shape

实现 `ShapeGenerator` 的两个方法即可，注入用 `shapeGenerator(...)`：

```java
public class CrossShapeGenerator extends ShapeGenerator {

    private final int radius;

    public CrossShapeGenerator(int radius) {
        this.radius = radius;
    }

    @Override
    public Vec3i getMaxSize() {
        return new Vec3i(radius * 2, radius * 2, radius * 2);   // 真实包围盒，别低估
    }

    @Override
    public void generate(Random gridRandom, IBlockGeneratorAccess access) {
        for (int i = -radius; i <= radius; i++) {
            access.generateBlock(i, 0, 0);
            access.generateBlock(0, 0, i);
            access.generateBlock(0, i, 0);
        }
    }
}
```

```java
OreDepositBuilder.definitionBuilder("myaddon/cross_vein")
        .weight(30)
        .density(0.3f)
        .minHeight(20)
        .maxHeight(80)
        .shapeGenerator(new CrossShapeGenerator(6))
        .dimensionFilter(WorldConfigUtils.predicateDimension(42))
        .buildAndRegister(WorldGenRegistry.INSTANCE);
```

写 shape 时的三条硬规则：

1. **只用传入的 `gridRandom`**，不要 `new Random()`：grid 缓存（300 条 / 5 分钟）过期重建时同一
   条矿脉会重新生成一次，未播种的随机数会让形态与已写入区块的数据对不上。
2. **`generateBlock(x, y, z)` 的坐标相对矿脉中心**（y 可以为负，世界 y ≤ 0 的方块会被丢弃）；
   默认走 `density` 判定，要无视密度强制放置就传 `generateBlock(x, y, z, false)`。
3. **shape 里拿不到 World**（`IBlockGeneratorAccess` 只有 `generateBlock`），宿主方块与高度检查
   一律放到定义的 `generationPredicate`。

### 5.3 自定义 filler / populator

```java
// filler：决定放什么方块
.simpleFill(blockState)                                  // 单一方块
.layeredFill(primary, secondary, between, sporadic)      // 四材料分层
.weightRandomFill(List<Pair<Integer, FillerEntry>>)      // 按权重随机
.ignoreBedrockFill(FillerEntry)                          // 跳过基岩的包装（球体/油球用）

// populator：区块填充阶段的额外内容
.surfaceRock(material) / .surfaceBlock(state)
.fluidSpring(state, chance) / .fluidBall(state, chance)
```

也可以直接传对象：`.shapeGenerator(...)` / 用 `SimpleBlockFiller` / `LayeredBlockFiller` /
`BlacklistedBlockFiller` 自己组装 `BlockFiller`；populator 实现 `VeinChunkPopulator`（逐区块）
或 `VeinBufferPopulator`（矿脉生成时一次，占位用 `IBlockModifierAccess.setBlock(x, y, z, index)`）。

## 6. 生成引擎行为（理解这些才能调好概率）

- 世界按 **3×3 chunk（48×48 格）** 划分网格（`WorldGeneratorImpl.GRID_SIZE_X/Z = 3`），
  每个网格抽 `minVeinsInSection + random(additionalVeinsInSection + 1)` 个定义（默认 `1 + 0`）。
- 抽中的定义如果是 `countAsVein(false)`，引擎会**再多抽一次**，所以石材球/油球/大平板这类定义
  不会挤掉该网格的普通矿脉。
- 抽取是按权重按比例随机的：`P ≈ 该定义权重 / 该维度（含生物群系修正后）所有定义权重之和`。
  默认主世界权重和 ≈1670（22 条层状矿脉 1200 + 4 个石材球 420 + 原油球 50），
  想要"每三格出一条"就给个 ≈835 的权重（或配合 `countAsVein(false)` 取 ≈650）。
- `priority` 大的先生成；后生成的矿脉对每个位置跑一次 `generationPredicate`，判定的是**当前方块**
  （所以"石材球先生成 → 矿石后填进去"能成立）。
- 形状坐标以矿脉中心为原点；`worldY ≤ 0` 的方块直接丢弃，矿脉中心所在网格以外 ±48 格的方块也会
  被丢弃（`getMaxSize()` 必须诚实地不超过这个范围）。
- 高度：`centerY = max(3, minHeight) + random(min(maxBottomHeight, maxHeight - (getMaxSize().Y/2 + 4)) - max(3, minHeight))`。
- 矿脉中心：`countAsVein(true)` 且 `generateVeinsInCenterOfChunk` 开启时固定在网格中心，否则网格内随机。
- JEI、探矿器、`CachedGridEntry` 全部通过定义 getter 消费，注册后无需任何额外接线；
  `translationKey` 就是 JEI 上显示的名字（记得补 lang 键）。

## 7. 注意事项

1. **depositName 建议带 addon 前缀**（如 `"myaddon/copper_vein"`）：
   - 默认定义的名称按 `overworld/`、`nether/`、`end/` 分目录，同名会冲突
   - `VeinSystemInit` 会把它转成 bedrockOres 的 `VeinType` id（`/`→`_`），前缀保证唯一
2. **`weight` 与 `density` 必填且非 0**（`OreDepositBuilder` 校验），`fluid` 必填（基岩流体校验）
3. 矿脉材料的 `ore:` 写法已不存在——`layeredFill` 直接收 `Material`，底层自动映射 `Map<StoneType, IBlockState>`
4. **`getMaxSize()` 必须是真实包围盒**：低估会让矿生成到高度上限之外，或让贫矿壳算错
5. **薄板/薄层配 `layeredFill` 会退化**（primary 要 `layer ≥ 3`），薄结构建议 `simpleFill` / `weightRandomFill`
6. **流体方块必须带 `LEVEL` 属性**（`BlockFluidBase`），`fluidBall` 目前有坐标偏移 bug，别用
7. **`surfaceRock` 只对产出可统一到带 `ORE` 属性材料的矿脉生效**，石材球撒不出地表石
8. 不写维度过滤时默认是 `isSurfaceWorld`（含 mod 的地表维度），要限定主世界请明确 `.dimensionId(0)`
9. 移除矿脉：`WorldGenRegistry` 当前未提供移除 API，默认定义不可删除
10. 注册时机：`init` / `postInit`（且早于 GT 的 `postInit` 联动），不要在世界加载之后再注册

## 8. 参考实现

默认定义全部在 `gregtech.api.worldgen.WorldgenDefinitions` 中，可以直接照抄：

| 方法 | 内容 |
|---|---|
| `registerOverworldVeins` | 主世界层状矿脉 + 4 个石材球 + 原油球 |
| `registerNetherVeins` / `registerEndVeins` | 下界 / 末地层状矿脉（用 `dimensionName` 过滤） |
| `registerBedrockFluidVeins` | 6 个主世界油田 + 下界岩浆田 |

## 9. builder 方法速查

| 方法 | 所属 | 说明 |
|---|---|---|
| `definitionBuilder(name)` | 两个 builder | 入口 |
| `translationKey` / `description` | `DepositBuilder` | JEI 显示名 / 描述 |
| `weight` / `priority` / `countAsVein` | `DepositBuilder` | 抽取与生成顺序 |
| `biomeWeightModifier` / `...Dictionary` / `...Map` | `DepositBuilder` | 生物群系权重 |
| `dimensionFilter` / `dimensionId` / `dimensionName` / `overworldOnly` / `netherOnly` / `endOnly` | `DepositBuilder` | 维度过滤 |
| `build` / `buildAndRegister(registry)` | `DepositBuilder` | 构建 / 构建并注册 |
| `density` / `minHeight` / `maxHeight` / `generationPredicate` / `generationPredicateAny` | `OreDepositBuilder` | 矿脉基本参数 |
| `layeredGeneration` / `sphereGeneration` / `ellipsoidGeneration` / `plateGeneration` / `singleBlockGeneration` / `slabGeneration` | `OreDepositBuilder` | 内置 shape |
| `shapeGenerator(ShapeGenerator)` | `OreDepositBuilder` | 注入自定义 shape |
| `layeredFill` / `simpleFill` / `weightRandomFill` / `ignoreBedrockFill` / `stoneSmoothSphereFill` / `fluidFill` | `OreDepositBuilder` | filler |
| `surfaceRock` / `surfaceBlock` / `fluidSpring` / `fluidBall` | `OreDepositBuilder` | populator |
| `yields` / `depletion` / `fluid` | `BedrockFluidDepositBuilder` | 基岩流体矿脉 |
