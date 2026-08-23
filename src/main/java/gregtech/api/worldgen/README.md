# GT Worldgen 矿脉注册指南（纯代码注册）

> 本项目的矿脉/基岩流体定义**全部通过代码注册**。
> 附属模组（addon）使用本指南的 API 即可注册自定义矿脉。

---

## 1. 概览

| 类 | 用途 |
|---|---|
| `gregtech.api.worldgen.config.OreDepositBuilder` | 普通矿脉（地下矿体 + 地表指示物） |
| `gregtech.api.worldgen.config.BedrockFluidDepositBuilder` | 基岩流体矿脉（流体钻机抽取的那类） |
| `gregtech.api.worldgen.config.WorldGenRegistry` | 注册表入口（`getOreDeposits()` / `addVeinDefinitions()` 等） |
| `gregtech.api.worldgen.config.DepositBuilder` | 上述两个 builder 的公共基类（通用字段方法） |

注册流程一句话：**builder 链式配置 → `build()` 构建定义 → `registry.addVeinDefinitions()` 注册**，
或直接用便捷方法 `buildAndRegister(registry)`。

## 2. 注册时机

- `WorldGenRegistry.initializeRegistry()` 在 GT 的 `init` 阶段执行，只注册默认定义，**没有任何锁定机制**
- addon 在**自己的 `init` 或 `postInit`**（`@Mod(dependencies = "required-after:gregtech")` 保证在 GT 之后）注册即可
- `oreVeinCache` 是懒加载的弱引用缓存，服务器启动时尚未建立，注册后首次世界生成时自动包含新定义
- 若 addon 使用了 bedrockOres 联动（`VeinSystemInit.postInit` 会把 GT 矿脉同步为虚拟矿脉），需保证在 GT 的 `postInit` 之前完成注册

## 3. 注册普通矿脉

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
        .layeredGeneration(17, 24)                   // 分层椭球半径范围 [min, max]
        .layeredFill(Materials.Chalcopyrite, Materials.Iron,
                Materials.Pyrite, Materials.Copper)  // 主层/次层/中间层/散矿层
        .buildAndRegister(WorldGenRegistry.INSTANCE);
```

`layeredFill` 四个材料即 JSON 时代的 `primary/secondary/between/sporadic` 四层。

### 3.2 球体矿脉（石材/流体球）

```java
// 石材 sphere（不占用矿脉位）
OreDepositBuilder.definitionBuilder("myaddon/granite_sphere")
        .weight(60)
        .density(1.0f)
        .priority(100)
        .countAsVein(false)                          // 不计入矿脉计数
        .sphereGeneration(10, 20)
        .stoneSmoothSphereFill(StoneVariantBlock.StoneType.RED_GRANITE)
        .buildAndRegister(WorldGenRegistry.INSTANCE);

// 流体球（如原油球，带 fluid_spring 喷泉）
OreDepositBuilder.definitionBuilder("myaddon/oil_sphere")
        .weight(50)
        .density(1.0f)
        .priority(-100)
        .countAsVein(false)
        .generationPredicateAny()                    // 任意方块可替换
        .biomeWeightModifierDictionary(ImmutableMap.of("sandy", 5))
        .sphereGeneration(9, 13)
        .fluidSpring(Materials.RawOil.getFluid().getBlock().getDefaultState(), 0.40f)
        .fluidFill(Materials.RawOil.getFluid())
        .buildAndRegister(WorldGenRegistry.INSTANCE);
```

### 3.3 维度过滤

```java
.dimensionId(42)                             // 按维度 ID（addon 自定义维度推荐）
.dimensionName("the_end")                    // 按维度类型名
.overworldOnly()                             // 主世界（isSurfaceWorld）
.netherOnly()                                // 下界
.endOnly()                                   // 末地
.customDimension(wp -> wp.getDimension() > 0) // 任意自定义谓词
```

### 3.4 生物群系权重修正

```java
.biomeWeightModifierDictionary(ImmutableMap.of("ocean", 5, "sandy", 10))  // 按字典标签
.biomeWeightModifierMap(ImmutableMap.of("minecraft:ocean", 150))          // 按生物群系注册名
.biomeWeightModifier(biome -> biome.getTemperature() > 1.0 ? 20 : 0)      // 任意函数
```

### 3.5 其他可选配置

```java
.generationPredicate((state, world, pos) -> ...)   // 替换条件（默认仅石质）
.surfaceBlock(blockState)                          // 地表方块指示物（替代 surfaceRock）
.priority(-100)                                    // 生成优先级（大的先生成）
.minHeight / .maxHeight                            // 高度限制
.countAsVein(true/false)                           // 是否计入每区块矿脉数量
```

## 4. 注册基岩流体矿脉

```java
BedrockFluidDepositBuilder.definitionBuilder("myaddon/geyser_deposit")
        .translationKey("myaddon.vein.geyser")
        .weight(20)
        .yields(150, 300)                          // 产量范围 [min, max)
        .depletion(1, 100, 30)                     // 每次耗尽量、耗尽几率 [0,100]、耗尽后产量
        .dimensionId(42)
        .fluid(Materials.Oil.getFluid())           // 直接传 Fluid 实例
        .buildAndRegister(WorldGenRegistry.INSTANCE);
```

`build()` 内部自动调用 `BedrockFluidVeinHandler.addFluidDeposit`，注册即生效
（流体钻机、基岩流体泉、JEI 页面均立即可见）。

## 5. 高级：自定义 shape / filler / populator

builder 也支持直接传入自定义组件对象：

```java
OreDepositBuilder.definitionBuilder("myaddon/custom_vein")
        .weight(30)
        .density(0.3f)
        .minHeight(20)
        .maxHeight(80)
        // 自定义 shape：实现 ShapeGenerator（generate + getMaxSize）
        .dimensionFilter(WorldConfigUtils.predicateDimension(42))
        // 自定义 filler：实现 BlockFiller（apply + getAllPossibleStates）
        // 自定义 populator：实现 VeinChunkPopulator / VeinBufferPopulator
        .buildAndRegister(WorldGenRegistry.INSTANCE);
```

可用组件（全部支持构造注入，不再有 JSON 解析）：

- **shape**：`LayeredGenerator(min, max)`、`SphereGenerator(min, max)`、
  `EllipsoidGenerator(min, max)`、`PlateGenerator(...)`、`SingleBlockGenerator(min, max)`
- **filler**：`LayeredBlockFiller(LayeredFillerEntry)`、`SimpleBlockFiller(FillerEntry)`、
  `BlacklistedBlockFiller(blacklist, filler)`；
  `FillerConfigUtils` 提供公开的 `OreFilterEntry` / `WeightRandomMatcherEntry` /
  `BlockStateMatcherEntry` / `LayeredFillerEntry`
- **populator**：`SurfaceRockPopulator(material)`、`SurfaceBlockPopulator(state[, min, max])`、
  `FluidSpringPopulator(fluidState, chance)`、`FluidBallPopulator(fluidState, chance)`

## 6. 注意事项

1. **depositName 建议带 addon 前缀**（如 `"myaddon/copper_vein"`）：
   - 默认定义的名称按 `overworld/`、`nether/`、`end/` 分目录，同名会冲突
   - `VeinSystemInit` 会把它转成 bedrockOres 的 `VeinType` id（`/`→`_`），前缀保证唯一
2. **`weight` 与 `density` 必填**（OreDepositBuilder 校验），`fluid` 必填（BedrockFluidDepositBuilder 校验）
3. 矿脉材料的 `ore:` 写法已不存在——`layeredFill` 直接收 `Material`，底层自动映射 `Map<StoneType, IBlockState>`
4. JEI（`GTOreInfo` 等）、探矿器、`CachedGridEntry` 生成引擎全部通过定义 getter 消费，注册后无需任何额外接线
5. 移除矿脉：`WorldGenRegistry` 当前未提供移除 API，默认定义不可删除

## 7. 参考实现

默认定义全部在 `gregtech.api.worldgen.WorldgenDefinitions` 中，作为 54 个定义的实际注册示例，
包含主世界/下界/末地的分层矿脉、石材球、原油球与全部 8 个基岩流体矿脉。
