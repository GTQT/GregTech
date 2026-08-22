# 多方块结构创建与 API 使用指南（Structure System V3）

本文面向在当前仓库中新增、迁移或维护 GregTech 多方块控制器的开发者。

> 当前实现以 `StructureDefinition` 为唯一结构声明入口。旧资料中的
> `FactoryBlockPattern`、`BlockPattern`、`BlockPatternTemplate`、`MultiblockState`、
> `createStructurePattern()` 和 `buildTemplate()` 都不应再用于新代码。

## 1. 先理解四个核心对象

| 对象 | 生命周期 | 作用 |
| --- | --- | --- |
| `StructureDefinition` | 同类机器共享 | 不可变的顶层结构声明，保存方向、piece、能力数量限制和运行时探测器。 |
| `PieceTemplate` / `MultiPiecePattern` | 同类机器共享 | `StructureDefinition` 的编译结果，描述每个格子如何匹配。通常不手动创建。 |
| `StructureRuntime` | 每台控制器一个 | 保存检查缓存、dirty 状态、piece 运行状态和已形成快照，不能放进 `static` 字段。 |
| `FormedStructureView` | 一次成功提交产生 | 控制器在 `formStructure(...)` 中读取能力数、piece 重复数、channel、part 和聚合值的只读视图。 |

标准调用链：

```text
createStructureDefinition()
  -> StructureDefinition
  -> 编译为 MultiPiecePattern / PieceTemplate
  -> StructureRuntime 执行检查
  -> StructureCheckResult
  -> MultiblockStructureCommitter 提交
  -> formStructure(FormedStructureView)
```

结构检查、JEI 预览、投影和自动建造可以计算结果，但只有提交器能够改变“已成型”状态。

## 2. 选择控制器基类

| 需求 | 推荐基类 |
| --- | --- |
| 常规耗电 RecipeMap 机器 | `RecipeMapMultiblockController` |
| 蒸汽 RecipeMap 机器 | `RecipeMapSteamMultiblockController` |
| 原始机器，自带简单物品/流体库存 | `RecipeMapPrimitiveMultiblockController` |
| 燃料发电类 | `FuelMultiblockController` |
| 热量系统机器 | `HeatMultiblockController` |
| 无能源 RecipeMap 机器 | `NoEnergyMultiblockController` |
| 需要维护、消音、标准显示 GUI，但不直接跑 RecipeMap | `MultiblockWithDisplayBase` |
| 完全自定义行为 | `MultiblockControllerBase` |

常规 RecipeMap 机器通常只需声明结构、贴图和少量特殊逻辑。基类会在成型后收集物品、流体、能源和维护能力。

## 3. 最小可用示例

```java
public class MetaTileEntityExampleMultiblock extends RecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION =
            StructureDefinition.getOrBuild("myaddon:example_multiblock", () ->
                    DeclarativePatternBuilder.start()
                            .aisle("XXX", "XXX", "XXX")
                            .aisle("XXX", "X#X", "XXX")
                            .aisle("XXX", "XSX", "XXX")
                            .self('S', MetaTileEntityExampleMultiblock.class)
                            .air('#')
                            .casing('X', getCasingState())
                                .maintenance()
                                .energyInput(1, 2)
                                .optionalItemInput(4)
                                .optionalItemOutput(4)
                                .optionalFluidInput(2)
                                .optionalFluidOutput(2)
                            .buildStructureDefinition());

    public MetaTileEntityExampleMultiblock(ResourceLocation id) {
        super(id, RecipeMaps.MACERATOR_RECIPES);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityExampleMultiblock(metaTileEntityId);
    }

    @Override
    protected StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    private static IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(MetalCasingType.STEEL_SOLID);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.SOLID_STEEL_CASING;
    }
}
```

关键规则：

- 声明用 `StructureDefinition.getOrBuild(...)` 缓存，key 必须全局唯一且稳定。
- 控制器字符必须用 `self('S', ControllerClass.class)` 或 `Elements.self(...)`，它同时是主 piece 的中心标记。
- 所有非空格字符都必须映射；空格默认是 `any()`，不是空气。
- 最终调用 `buildStructureDefinition()`，控制器覆写 `createStructureDefinition()`。
- `STRUCTURE_DEFINITION` 可以静态共享；`StructureRuntime` 不可以。

## 4. 结构图、坐标与方向

### 4.1 `aisle(...)` 如何组成三维结构

```java
.aisle("XXX", "CCC", "XXX") // 第 0 个切片
.aisle("XSX", "C#C", "XXX") // 第 1 个切片
```

数据维度是 `[aisle][row][char]`：

| 维度 | 默认方向 | 含义 |
| --- | --- | --- |
| 一行内的字符顺序 | `RIGHT` | 从字符串左侧走向右侧。 |
| 一个 `aisle` 内的字符串顺序 | `UP` | 从第一个 row 走向最后一个 row。 |
| 多次调用 `aisle` 的顺序 | `BACK` | 从第一个切片走向后续切片。 |

默认入口等同于：

```java
DeclarativePatternBuilder.start(RIGHT, UP, BACK)
```

三个方向必须分别覆盖左右、上下、前后三条不同的轴，否则构建时抛出
`Must have 3 different axes!`。

例如装配线沿控制器左右方向延伸，使用：

```java
DeclarativePatternBuilder.start(BACK, UP, RIGHT)
```

### 4.2 `S`、`#` 与空格

| 字符 | 推荐含义 | 写法 |
| --- | --- | --- |
| `S` | 控制器及主中心 | `.self('S', MyController.class)` |
| `#` | 必须为空气 | `.air('#')` |
| 空格 | 不检查该位置 | 默认就是 `any()`；也可写 `.any(' ')` |
| `X` | 普通 casing，可被仓口替换 | `.casing('X', state)...` |
| `C` | 同组的分级 casing | `.tieredCasing('C', group)...` |

不要把空格当空气。如果内部必须是空腔，需要显式写一个字符并映射为 `air()`。

### 4.3 `centerOffset(x, y, z)`

`self(...)` 所在格会自动成为主中心。没有控制器的子 piece 需要明确中心时，
`centerOffset` 指定该 piece 局部图中的 `(charIndex, rowIndex, aisleIndex)`。

```java
.piece("base")
.aisle("WSW", "WWW")
.centerOffset(1, 0, 0)
```

多 piece 编译时，没有显式中心且 `centerOffset` 为 `(0,0,0)` 的 piece 会继承第一个
含 `self` 的 piece 的中心对齐。复杂或不对齐的子结构应显式设置，避免依赖继承规则。

## 5. `DeclarativePatternBuilder` 方法详解

这是新增结构的首选入口。它在底层 `StructureDefinition` 之上提供字符快捷映射、
casing/仓口组合、自动最低 casing 数和多 piece 顺序定位。

### 5.1 启动、切片与构建

| 方法 | 作用 | 使用时机 |
| --- | --- | --- |
| `start()` | 使用 `RIGHT, UP, BACK` 创建 builder，并建立默认的单 piece `main`。 | 绝大多数结构。 |
| `start(charDir, stringDir, aisleDir)` | 自定义三条局部轴。 | 横向机器、倒置或特殊朝向结构。 |
| `aisle(String... rows)` | 添加一个固定切片。所有切片必须等高、所有 row 必须等宽。 | 普通固定结构。 |
| `aisleRepeated(int exactCount, String... rows)` | 将最近声明的这一种切片精确重复 `exactCount` 次；至少为 1。 | 固定长度但想避免复制相同行。 |
| `withAisleChannel(String name)` | 给刚添加的 aisle 设置 channel 名；没有前置 aisle 会抛异常。 | 让预览/构建工具识别长度等选择。 |
| `buildStructureDefinition()` | 验证声明并构建不可变 `StructureDefinition`。 | 链式调用终点。 |

`aisleRepeated` 只支持固定次数。可变长度要用 `repeatablePiece(...)`。

### 5.2 固定 piece 与可变 piece

| 方法 | 作用 |
| --- | --- |
| `piece(name)` | 开始一个固定的命名 piece，后续 `aisle(...)` 加入该 piece。 |
| `repeatablePiece(name, min, max)` | 开始一个按 aisle 轴重复的命名 piece；整个 piece 作为一个单元重复。 |
| `repeatablePiece(name, String[][] pattern, Vec3i offset)` | 开始原始多轴重复 piece；`pattern` 是 `[aisle][row]`，`offset` 是相对控制器的 `(right, up, back)`。 |

`PieceBuilder` 方法：

| 方法 | 作用 |
| --- | --- |
| `aisle(...)` / `aisleRepeated(...)` / `withAisleChannel(...)` | 与顶层同名方法相同，但作用于当前 piece。 |
| `centerOffset(x,y,z)` | 设置当前 piece 的局部中心。 |
| `end()` | 结束当前 piece，返回顶层 builder。 |

`PieceBuilder` 还提供 `where`、`self`、`block`、`casing`、继续创建下一个 piece 和
`buildStructureDefinition` 等透传方法。它们与顶层方法语义完全相同，所以可以像内置
装配线一样不显式写 `.end()`：

```java
DeclarativePatternBuilder.start(BACK, UP, RIGHT)
        .piece("start")
            .aisle("FIF", "RTR", "SAG", " Y ")
        .repeatablePiece("body", 3, 15)
            .aisle("FIF", "RTR", "DAG", " Y ")
            .withAisleChannel(GTStructureChannels.STRUCTURE_LENGTH.getName())
        .piece("end")
            .aisle("FOF", "RTR", "DAG", " Y ")
        // 字符映射……
        .buildStructureDefinition();
```

builder 会找到含 `self` 的中心 piece，并把它之前、之后的 piece 按 aisle 方向串接；
位于可变 body 后面的固定 piece 会根据实际重复数动态定位。

### 5.3 多轴重复 piece

`repeatablePiece(name, pattern, offset)` 返回 `MultiAxisPieceBuilder`：

| 方法 | 作用 |
| --- | --- |
| `repeatAxes(int... axes)` | 设置重复轴；`0=X/char`、`1=Y/row`、`2=Z/aisle`。轴不可重复。 |
| `repeatRange(min0,max0,min1,max1,...)` | 按 `repeatAxes` 的顺序设置每条轴的最小/最大重复数。参数必须成对。 |
| `stepSizes(int... steps)` | 每增加一次重复，沿对应局部轴移动多少格；不能在需要重复时为 0。通常等于基础 piece 在该轴的尺寸。可用负数反向铺展。 |
| `channelNames(String... names)` | 每条重复轴对应的 channel 名，顺序必须与 `repeatAxes` 一致。 |
| `centerOffset(x,y,z)` | 设置基础图内的局部中心。 |
| `end()` | 结束多轴 piece 并返回顶层 builder。 |

三个数组的长度必须相同：

```java
.repeatAxes(0, 1, 2)
.repeatRange(1, 5, 1, 7, 1, 4)
.stepSizes(3, 2, -2)
.channelNames(
        GTStructureChannels.STRUCTURE_WIDTH.getName(),
        GTStructureChannels.STRUCTURE_HEIGHT.getName(),
        GTStructureChannels.STRUCTURE_LENGTH.getName())
```

### 5.4 字符映射方法

| 方法 | 匹配内容 |
| --- | --- |
| `where(symbol, element)` | 直接绑定任意 `IStructureElement`，是所有快捷方法的底层入口。 |
| `self(symbol, controllerClass)` | 指定类型的控制器，同时标记结构中心。 |
| `block(symbol, state)` | 一个精确 `IBlockState`。 |
| `blocks(symbol, IBlockState...)` | 多个允许的精确状态。 |
| `blocks(symbol, Block...)` | 多个方块，忽略具体 block state。 |
| `blockPredicate(symbol, predicate)` | 自定义状态谓词；没有显式预览候选。 |
| `blockPredicate(symbol, predicate, candidates)` | 自定义状态谓词，并给 JEI/投影/自动建造提供 `BlockInfo[]`。 |
| `air(symbol)` | 只接受空气。 |
| `any(symbol)` | 接受任何方块且不参与建造。 |
| `hatch(symbol, ability)` | 接受一种 `MultiblockAbility` 对应的仓口。 |
| `hatches(symbol, abilities...)` | 接受给定能力中的任意一种。 |
| `frames(symbol, materials...)` | 接受指定材料的框架方块或框架管道。 |
| `metaTileEntities(symbol, mtes...)` | 只接受列出的具体 MTE。 |

自定义谓词若不提供 `candidates`，真实检查可能正常，但 JEI、投影和自动建造不知道该显示或放置什么。

### 5.5 普通 casing 槽与仓口方法

```java
.casing('X', getCasingState())
    .maintenance()
    .energyInput(1, 2)
    .optionalItemInput(4)
    .optionalItemOutput(4)
    .done()
```

`casing(symbol, ICasing)` 或 `casing(symbol, IBlockState)` 把同一个字符声明为
“基础 casing 或以下仓口之一”。builder 会按下式计算最低 casing 数：

```text
该字符的声明槽位数 - 所有可替换仓口的最大数量 - custom 的最大数量
```

`CasingSlot` 的通用方法：

| 方法 | 作用 |
| --- | --- |
| `hatch(ability, min, max)` | 允许某能力仓口，限定最少和最多数量。 |
| `hatch(ability, min, max, defaultCandidate)` | 同上，并指定预览/自动建造首选 MTE。 |
| `hatch(ability, exactCount)` | 要求精确数量。 |
| `optionalHatch(ability, max)` | `min=0` 的快捷写法。 |
| `custom(element, maxCount)` | 添加不是普通 ability 仓口的替代元素；`maxCount` 用于自动计算最低 casing 数。 |
| `preset(IHatchPreset)` | 应用一组预定义仓口规则。 |
| `done()` | 返回顶层 builder。 |

`CasingSlot` 上其余 `aisle`、`where`、字符快捷映射、`casing`、`tieredCasing`、
`abilityGroup`、`globalAbilityLimit`、`piece`、`repeatablePiece` 和
`buildStructureDefinition` 都只是顶层 builder 的透传方法；调用后的作用与本节前面同名
方法完全一致。`TieredCasingSlot` 和 `MultiAxisPieceBuilder` 上出现的同名透传方法也遵循
同一规则。

`max` 通常应使用有限非负值，以便自动最低 casing 数有明确含义；需要无限上限时优先用
`globalAbilityLimit` 或底层 `Elements.counted` 明确表达。

常用快捷方法如下。`foo(n)` 表示精确 `n` 个，`foo(min,max)` 表示范围，
`optionalFoo(max)` 表示 `0..max`：

| 方法组 | 对应能力 |
| --- | --- |
| `muffler()` | 精确 1 个 `MUFFLER_HATCH` |
| `maintenance()` | 精确 1 个 `MAINTENANCE_HATCH` |
| `computerReception()` | 精确 1 个 `COMPUTATION_DATA_RECEPTION` |
| `computerTransmission()` | 精确 1 个 `COMPUTATION_DATA_TRANSMISSION` |
| `energyInput(...)` / `optionalEnergyInput(...)` | `INPUT_ENERGY` |
| `energyOutput(...)` / `optionalEnergyOutput(...)` | `OUTPUT_ENERGY` |
| `substationInput(...)` / `optionalSubstationInput(...)` | `SUBSTATION_INPUT_ENERGY` |
| `substationOutput(...)` / `optionalSubstationOutput(...)` | `SUBSTATION_OUTPUT_ENERGY` |
| `laserInput(...)` / `optionalLaserInput(...)` | `INPUT_LASER` |
| `laserOutput(...)` / `optionalLaserOutput(...)` | `OUTPUT_LASER` |
| `fluidInput(...)` / `optionalFluidInput(...)` | `IMPORT_FLUIDS` |
| `fluidOutput(...)` / `optionalFluidOutput(...)` | `EXPORT_FLUIDS` |
| `itemInput(...)` / `optionalItemInput(...)` | `IMPORT_ITEMS` |
| `itemOutput(...)` / `optionalItemOutput(...)` | `EXPORT_ITEMS` |
| `tieredHatch()` | 配置允许时可放 `0..1` 个 `TIERED_HATCH` |
| `parallelHatch()` | `0..1` 个 `PARALLEL_HATCH` |
| `overclockHatch()` | `0..1` 个 `OVERCLOCK_HATCH` |
| `accelerationHatch()` | `0..1` 个 `ACCELERATE_HATCH` |
| `threadHatch()` | `0..1` 个 `THREAD_HATCH` |

组合能源方法：

| 方法 | 作用 |
| --- | --- |
| `universalEnergyInput(minTotal, maxPerType)` | 普通、变电站、激光三种输入各允许 `0..maxPerType`，并要求三类合计至少 `minTotal`。 |
| `universalEnergyOutput(minTotal, maxPerType)` | 三种输出的对应版本。 |
| `energyIO(minTotal, maxPerType)` | 普通能源输入、输出各允许 `0..maxPerType`，合计数量受组限制。 |
| `auto()` | 维护、消音、1..2 能源输入、1..4 物品输入/输出、1..2 流体输入/输出。注意 I/O 都是必需的。 |
| `auto(flags...)` | 仅为传入 `true` 的类别添加上述默认规则。参数顺序是消音、维护、能源输入、物品输入、物品输出、流体输入、流体输出。 |

预置规则：

| `HatchPresets` 常量 | 精确内容 |
| --- | --- |
| `MUFFLER_IO` | 1 维护 + 1 消音。 |
| `STANDARD_ITEM_IO` | 物品输入 `0..4` + 物品输出 `0..4`。 |
| `STANDARD_FLUID_IO` | 流体输入 `0..4` + 流体输出 `0..4`。 |
| `STANDARD_IO` | `STANDARD_ITEM_IO` + `STANDARD_FLUID_IO`。 |
| `ELECTRIC_STANDARD` | 能源输入 `1..2` + `MUFFLER_IO` + `STANDARD_IO`。 |
| `ELECTRIC_STANDARD_FIXED_MUFFLER` | 能源输入 `1..2` + 1 维护 + `STANDARD_IO`；消音仓应由结构中的独立字符声明。 |

如果机器要求消音仓并使用消音机制，还要覆写：

```java
@Override
public boolean hasMufflerMechanics() {
    return true;
}
```

### 5.6 能力总量与能力组限制

```java
.globalAbilityLimit(MultiblockAbility.INPUT_ENERGY, 1, 2)
.abilityGroup(MultiblockAbility.INPUT_ENERGY_GROUP, 1, -1,
        MultiblockAbility.INPUT_ENERGY,
        MultiblockAbility.SUBSTATION_INPUT_ENERGY,
        MultiblockAbility.INPUT_LASER)
```

| 方法 | 作用 |
| --- | --- |
| `globalAbilityLimit(ability,min,max)` | 跨所有 piece 限制一种能力的总数。`max=-1` 表示无上限。重复声明同一能力时限制会累加。 |
| `abilityGroup(displayAbility,min,max,abilities...)` | 限制一组不同 ability 的合计数量；`displayAbility` 用于错误和界面展示。 |

多 piece 中仓口规则最终也会生成全局能力限制，避免每个 piece 各自满足一次最低数量。

### 5.7 分级 casing 与 channel

```java
.tieredCasing('C', GTCasingGroups.heatingCoils().group())
    .withChannel(GTCasingGroups.heatingCoils().channel())
```

| 方法 | 作用 |
| --- | --- |
| `tieredCasing(symbol, group)` | 接受 `ICasingGroup` 中的 casing，并按组规则检查等级一致性。 |
| `withChannel(channel)` | 指定形成时写入匹配 casing 与等级的 channel。未指定时使用 group 自带 tier channel。 |
| `done()` | 返回顶层 builder。 |

内置组：

- `GTCasingGroups.heatingCoils()`：加热线圈。
- `GTCasingGroups.machineCasings()`：机器外壳等级。
- `GTCasingGroups.borosilicateGlasses()`：硼硅玻璃等级。

每个 `CasingRegistration` 同时提供 `.group()` 和 `.channel()`。

`StructureChannel` 自身的方法：

| 方法 | 作用 |
| --- | --- |
| `getName()` | 返回全局唯一 channel 名，用于 metadata、NBT 和工具选择。 |
| `getDefaultTooltip()` | 返回 GUI/提示使用的默认翻译键。 |
| `getMatchedCasing(formed)` | 从 `FormedStructureView` 读取该 channel 聚合出的 `ICasing`，没有时为 `null`。 |
| `getIndicatorItem(tier)` | 返回界面代表指定 tier 的物品；未注册时为 `ItemStack.EMPTY`。 |

在成型回调中读取：

```java
@Override
protected void formStructure(@NotNull FormedStructureView formed) {
    formRecipeMapStructure(formed);

    ICasing matched = GTCasingGroups.heatingCoils().channel()
            .getMatchedCasing(formed);
    IHeatingCoilBlockStats stats = matched == null ? null
            : matched.getPayloadAs(IHeatingCoilBlockStats.class);
    this.temperature = stats == null ? 0 : stats.getCoilTemperature();
}
```

自定义组可用 `CasingDefinition`：

| 方法 | 作用 |
| --- | --- |
| `simple(state[, translationKey])` | 创建非分级 casing。 |
| `tiered(state,key,tier[,payload])` | 创建带等级和可选 payload 的 casing。 |
| `tieredGroup(...)` | 用已有 `ICasing` 注册组；可指定翻译键、统一等级要求和 channel 名。 |
| `fromMap(...)` | 从 `Map<IBlockState,V>` 创建组；value 自动成为 payload。无显式 channel 的重载会自动创建同名 channel。 |
| `fromIterable(...)` | 从任意对象集合，通过函数提取 state、tier、名称和可选 payload。 |
| `fromEntries(...)` | 从已构造的 `ICasing` 集合注册组。 |
| `getGroup(id)` / `getAllGroups()` | 查询已注册组。 |
| `getPreviewBlocks(group)` | 获取按 tier 排序的 JEI/工具候选。 |

`requiresUniform=true` 表示同一结构中的该组 casing 必须同等级。

形成后常用只读接口：

| 类型 | 方法 | 作用 |
| --- | --- | --- |
| `ICasing` | `getBlockState()` | 实际 casing 方块状态。 |
| `ICasing` | `getTranslationKey()` | 显示名称的翻译键。 |
| `ICasing` | `isTiered()` / `getTier()` | 是否分级及其等级。 |
| `ICasing` | `getItemStack()` | GUI/指示器使用的物品形式。 |
| `ICasing` | `getPayload()` / `getPayloadAs(type)` | 读取注册时附带的业务对象；类型不匹配时 typed getter 返回 `null`。 |
| `ICasingGroup` | `getGroupId()` / `getTranslationKey()` | 组 ID 与显示翻译键。 |
| `ICasingGroup` | `getCasings()` | 按 tier 升序排列的全部 casing。 |
| `ICasingGroup` | `requiresUniformTier()` | 是否要求结构内等级一致。 |
| `ICasingGroup` | `getTierChannel()` | 匹配时使用的 tier channel 名。 |
| `CasingRegistration` | `group()` / `channel()` | 分别取得成对注册的组与 channel。 |

## 6. `Elements` 全部快捷方法

直接 `where(...)` 时建议静态导入：

```java
import static gregtech.api.pattern.element.Elements.*;
```

### 6.1 基础元素

| 方法 | 作用 |
| --- | --- |
| `block(state)` | 精确匹配一个 block state。 |
| `blocks(states...)` | 匹配多个精确 state。 |
| `blocks(blocks...)` | 匹配多个 Block，忽略 state。 |
| `blockPredicate(predicate)` | 自定义状态判断，没有工具候选。 |
| `blockPredicate(predicate,candidates)` | 自定义状态判断并提供工具候选。 |
| `air()` | 只匹配空气。 |
| `any()` | 无条件通过。 |
| `self(controllerClass)` | 匹配指定类控制器并标记中心。 |
| `self(controllerClass,controllerId)` | 在类约束外再限制具体注册 ID，适合同类多注册变体。 |
| `frames(materials...)` | 匹配指定材料的框架方块或框架管道。 |

### 6.2 仓口、能力与具体 MTE

| 方法 | 作用 |
| --- | --- |
| `hatch(ability)` | 匹配一种能力仓口，无显式数量限制。 |
| `hatch(ability,min,max)` | 增加全局数量限制。 |
| `hatch(ability,min,max,previewCount)` | 另指定预览中优先显示的数量。 |
| `abilities(abilities...)` | 匹配多种 ability 中任意一种。 |
| `abilities(min,max,abilities...)` | 多种 ability 共享一个总量限制。 |
| `abilities(min,max,previewCount,abilities...)` | 再指定预览数量。 |
| `abilitiesPerLayer(min,max,previewCount,abilities...)` | 限制每个 aisle/layer 内的数量。 |
| `metaTileEntities(mtes...)` | 匹配列出的具体 MTE。 |
| `metaTileEntities(min,max,mtes...)` | 对具体 MTE 组合增加共享总量限制。 |
| `metaTileEntities(min,max,previewCount,mtes...)` | 再指定预览数量。 |
| `metaTileEntitiesAsAbility(ability,min,max,previewCount,mtes...)` | 匹配具体 MTE，并把它们作为指定 ability 贡献给控制器。 |
| `energyOutput(tier,minimumThroughput)` | 从能源输出仓注册表筛选；`true` 要求总输出吞吐量至少达到该电压档，`false` 要求输出电压不高于该档。 |
| `laserOutput(tier,minimumTier)` | 从激光输出仓中筛选；`true` 接受不低于该 tier，`false` 接受不高于该 tier。 |

`min=0` 表示可选，`max=-1` 表示无上限。数量限制与 `previewCount` 是两回事：前者决定能否成型，后者只决定工具默认展示多少候选。

### 6.3 分级、包装和组合

| 方法 | 作用与注意事项 |
| --- | --- |
| `tiered(candidates,channel)` | 通用分级方块元素，候选顺序代表等级并写入 channel。 |
| `tieredCasing(group,channel,min,max)` | 分级 casing 元素，并附加数量限制。通常优先用 declarative 的 `tieredCasing`。 |
| `lazy(supplier)` | 首次使用时才创建真实元素，用于解决静态初始化顺序；会使增量/快照优化保守回退。 |
| `onPass(callback,element)` | 元素成功匹配后执行事务化回调；高级用法，也会使增量/快照优化回退。不要在回调里直接发布 formed 状态。 |
| `withChannel(name,element)` | 给预览候选附加 channel 元数据，影响 JEI/投影/构建选择；它本身不创建运行期 tier 聚合。 |
| `withTooltips(element,tips...)` | 为预览候选追加说明。 |
| `withDefaultCandidate(element,mteSupplier)` | 指定多候选仓口在预览/自动建造中的默认 MTE。 |
| `chain(elements...)` | 按顺序尝试多个替代元素，任意一个成功即可；各分支自行声明要求。最常用的“casing 或仓口”组合。 |
| `choice(elements...)` | 同样是择一匹配，但在匹配前声明所有分支的要求；只有确实需要共享分支需求时使用。 |
| `counted(min,max,element)` | 给任意元素包装全局数量限制。 |
| `counted(min,max,previewCount,element)` | 全局数量限制 + 预览数量。 |
| `layerCounted(min,max,previewCount,element)` | 每 layer 数量限制 + 预览数量。 |

## 7. 何时直接使用 `StructureDefinition.builder`

以下情况使用底层 builder：

- 需要条件 piece、runtime-only piece 或特殊 offset 模式。
- 已有独立 `PieceTemplate`，需要组合进一个定义。
- 需要显式控制 piece 的静态/动态锚点。
- 声明式 casing 自动计数不适合该结构。
- 需要 runtime detector。

简单例子：

```java
private static final StructureDefinition<MyController> STRUCTURE_DEFINITION =
        StructureDefinition.getOrBuild("myaddon:low_level_example", () ->
                StructureDefinition.<MyController>builder(RIGHT, UP, BACK)
                        .piece("main", "ISI")
                            .where('S', Elements.self(MyController.class))
                            .where('I', Elements.choice(
                                    Elements.hatch(MultiblockAbility.INPUT_ENERGY, 0, 2, 1),
                                    Elements.hatch(MultiblockAbility.OUTPUT_ENERGY, 0, 2, 1)))
                            .end()
                        .globalAbilityGroupLimit(
                                MultiblockAbility.ENERGY_IO_GROUP, 1, 2,
                                MultiblockAbility.INPUT_ENERGY,
                                MultiblockAbility.OUTPUT_ENERGY)
                        .build());
```

### 7.1 `StructureDefinition` 静态入口和查询

| 方法 | 作用 |
| --- | --- |
| `getOrBuild(key,factory)` | 通过 `TemplatePool` 软引用缓存定义。标准入口。 |
| `getOrBuild(ownerKey,typeKey,factory)` | 变体缓存，实际 key 为 `ownerKey + "." + typeKey`。 |
| `fromTemplate(template)` | 把一个 `PieceTemplate` 包成名为 `main` 的单 piece 定义。 |
| `fromTemplate(pieceName,template)` | 同上，自定义 piece 名。 |
| `builder(charDir,stringDir,aisleDir)` | 创建底层 builder。 |
| `getCompiledPattern()` | 懒编译并缓存 `MultiPiecePattern`。普通控制器不应手动调用检查器。 |
| `getEligibilityPlan()` | 获取增量检查依赖计划和回退诊断。用于结构系统调试。 |
| `supportsElementCapability(capability)` | 判断所有适用 cell 是否支持快照匹配等能力。 |
| `computeWorldAABB(center,orientation,margin)` | 计算最大重复范围对应的世界包围盒。 |
| `getStructureDir()` | 返回三条声明方向。 |
| `getRuntimeDetector()` / `hasRuntimeDetector()` | 查询是否配置运行时几何探测器。 |
| `getStructureSizeDescriptor()` | 获取各 piece 以及整体的最小/最大尺寸描述。 |
| `createState()` / `check(world,pos,orientation)` | 创建一次性检查状态/同步便捷检查。控制器正常生命周期应让 `StructureRuntime` 调用，不要据此自行发布 formed 状态。 |

### 7.2 `StructureDefinition.Builder` 方法

| 方法 | 作用 |
| --- | --- |
| `piece(name, String... rows)` | 一个 aisle 的固定 piece，offset 默认为零。 |
| `piece(name, Vec3i offset, String... rows)` | 一个 aisle 的固定 piece，显式偏移。 |
| `piece(name, String[][] pattern, Vec3i offset)` | 完整 `[aisle][row]` 固定 piece。 |
| `pieceFromTemplate(name,template)` | 复用已有 `PieceTemplate`。 |
| `pieceFromTemplate(name,template,offset,offsetMode,condition)` | 复用模板并指定位置、偏移模式和可选条件。 |
| `repeatablePiece(name,pattern,offset)` | 创建通用可重复 piece，随后必须配置至少一条 repeat axis。 |
| `repeatablePiece(name,offset,rows...)` | 单 aisle 的便捷重载。 |
| `repeatableX(name,min,max,channel,rows...)` | 沿局部 X/char 轴重复的快捷入口，默认步长为 1。 |
| `repeatableY(name,min,max,channel,rows...)` | 沿局部 Y/row 轴重复的快捷入口，默认步长为 1。 |
| `repeatableZ(name,min,max,channel,rows...)` | 沿局部 Z/aisle 轴重复的快捷入口，默认步长为 1。 |
| `conditionalPiece(name,pattern,offset,condition)` | 条件激活的固定 piece。条件依赖外部状态时必须声明 `StructureDependency`。 |
| `globalAbilityLimit(ability,min,max)` | 全定义能力数量限制；同 ability 多次声明会累加。 |
| `globalAbilityGroupLimit(display,min,max,abilities...)` | 多 ability 合计限制。 |
| `runtimeDetector(detector)` | 使用世界状态发现无法静态表达的几何。定义必须只有一个固定 identity piece。 |
| `build()` | 验证 piece 名、锚点顺序、重复轴/范围/步长后构建定义。 |

### 7.3 底层固定 `PieceBuilder`

| 方法 | 作用 |
| --- | --- |
| `where(symbol,element)` | 当前 piece 的字符映射。底层 builder 不共享各 piece 的 symbol map。 |
| `offsetMode(mode)` | 解释 piece base offset 的方式，见下表。 |
| `centerOffset(x,y,z)` | 设置局部中心索引。 |
| `positionedAfterRepeatable(anchorName,anchorStep)` | 位置为 `staticOffset + anchor实际重复数 * anchorStep`；锚点必须在当前 piece 之前声明。 |
| `runtimeOnly()` / `hideFromTooling()` | 真实检查仍包含该 piece，但 JEI、提示、投影和自动建造隐藏它。两个方法等价。 |
| `end()` | 加入父 builder 并返回父 builder。 |
| `build()` | 等价于 `end().build()`，直接结束整个定义。 |

`OffsetMode`：

| 值 | offset 含义 |
| --- | --- |
| `RELATIVE` | `(right,up,back)`，随控制器旋转和翻转；默认值。 |
| `ABSOLUTE` | 世界坐标增量 `(x,y,z)`，不随控制器旋转。 |
| `HORIZONTAL_RELATIVE` | X/Z 随结构水平旋转，Y 保持世界绝对增量。 |

### 7.4 底层 `RepeatablePieceBuilder`

除 `where`、`offsetMode`、`centerOffset`、`runtimeOnly`、`hideFromTooling`、`end`、
`build` 外，还有：

| 方法 | 作用 |
| --- | --- |
| `repeatAxes(axes...)` | 重复轴列表，0/1/2 分别对应 char/row/aisle。 |
| `repeatRange(min0,max0,...)` | 每条重复轴的范围。 |
| `stepSizes(steps...)` | 每次重复的移动距离。 |
| `channelNames(names...)` | 每条重复轴形成元数据使用的 channel。 |
| `positionedAfter(anchorName,anchorStep)` | 将本可重复 piece 动态定位在先前 piece 之后。`anchorStep` 必须恰有 3 个 `(right,up,back)` 分量。 |

### 7.5 条件 piece 与 runtime detector

条件 piece 的判断如果读取控制器模式、channel、配置、upgrade 或其他 piece，必须使用
`StructureCondition.withDependencies(...)` 声明依赖。空依赖的条件会被保守视为 opaque，
系统回退到完整 active graph 校验。

`runtimeDetector(...)` 只用于边界必须从世界现场发现、无法用固定/可重复模板描述的结构，
例如可变长宽高的房间。detector 必须通过 `StructureRuntimeDetectionContext` 发布所有匹配
cell 和贡献，不能绕过提交器修改控制器。JEI/自动建造通常还需要按 channel 生成一次性的
工具模板；可参考 `MetaTileEntityCleanroom`。

## 8. 成型数据：`FormedStructureView` 每个方法

`formStructure(FormedStructureView formed)` 只在提交后的形成 payload 发生变化时调用。
需要长期保存的温度、等级、模式等字段在这里读取，并在 `invalidateStructure()` 清零。

| 方法 | 返回内容 |
| --- | --- |
| `fromCheckResult(result)` | 内部提交边界使用：把检查结果复制成只读形成视图。标记为 `@ApiStatus.Internal`，控制器不要手动调用。 |
| `getChannelValue(channel)` | 已形成的 `StructureChannelValues` 中的整数值；不存在时为 0。 |
| `hasChannelValue(channel)` | channel 是否确实存在，适合区分“未设置”和“值为 0”。 |
| `getMetadataChannelValue(name/channel)` | 从形成 metadata 读取重复 piece 等写入的 channel 值。 |
| `getPieceRepeat(pieceName,axisIndex)` | 某 piece 某重复轴的实际次数。 |
| `getPieceRepeat(pieceKey,axisIndex)` | 使用类型安全 `StructurePieceKey` 的版本。 |
| `getPieceRepeats(pieceName/pieceKey)` | 返回该 piece 所有轴的重复次数数组。 |
| `getPieceCenter(pieceName/pieceKey)` | 返回该 piece 实际世界中心，未记录时为 `null`。 |
| `getParts()` | 本次提交收集到的 `IMultiblockPart` 集合。 |
| `getVariantActiveBlocks()` | 成型后需要切换 active 外观的方块位置。 |
| `getAbilityCount(ability)` | 某 ability 的 part 数量。 |
| `hasAbility(ability)` | `getAbilityCount(...) > 0`。 |
| `getChannelAggregate(channel,type)` | 读取 tiered casing 等写入的 channel 聚合对象。 |
| `getAbilityCounts()` | 所有能力数量的只读 Map。 |
| `getAggregate(key)` | 使用 `StructureContributionKey` 读取自定义 typed aggregate。 |
| `getAggregateValues()` | 内部/诊断用的全部原始聚合 Map，新业务代码优先用 typed getter。 |
| `isFlipped()` | 本次形成结构是否使用翻转方向。 |

## 9. 控制器生命周期与常用覆写方法

### 9.1 必须或通常需要覆写

| 方法 | 作用与正确用法 |
| --- | --- |
| `createMetaTileEntity(tile)` | 为世界中的新 tile 创建同类型控制器实例。 |
| `createStructureDefinition()` | 返回幂等、已缓存的定义。不要每 tick 构造不同声明。 |
| `getBaseTexture(part)` | casing/part 的基础贴图。客户端方法。 |
| `getFrontOverlay()` | 控制器正面 overlay。需要特殊正面时覆写。 |
| `updateFormedValid()` | 每 tick 且结构已形成时执行。RecipeMap 基类已负责更新配方逻辑。 |
| `formStructure(formed)` | 成型 payload 改变后初始化能力和结构派生字段。 |
| `invalidateStructure()` | 结构失效时解绑 part、清能力并重置派生字段。覆写时先调用 `super.invalidateStructure()`。 |

覆写 `formStructure` 时要调用与基类匹配的 helper：

| 你的父类 | 在自定义 `formStructure` 开头调用 |
| --- | --- |
| `RecipeMapMultiblockController` | `formRecipeMapStructure(formed)` |
| `RecipeMapSteamMultiblockController` | `formSteamRecipeMapStructure(formed)` |
| `MultiblockWithDisplayBase` | `formStructureWithDisplay(formed)` |
| 直接继承 `MultiblockControllerBase` | 自行初始化能力/运行字段。 |

例子：

```java
private int coilTemperature;

@Override
protected void formStructure(@NotNull FormedStructureView formed) {
    formRecipeMapStructure(formed);
    ICasing casing = COIL_REGISTRATION.channel().getMatchedCasing(formed);
    CoilStats stats = casing == null ? null : casing.getPayloadAs(CoilStats.class);
    this.coilTemperature = stats == null ? 0 : stats.temperature();
}

@Override
public void invalidateStructure() {
    super.invalidateStructure();
    this.coilTemperature = 0;
}
```

### 9.2 能力、排序、朝向和 JEI

| 方法 | 何时使用 |
| --- | --- |
| `getAbilities(ability)` | 成型后取得该能力贡献的对象列表。返回只读列表。 |
| `getMultiblockParts()` | 取得全部已绑定 part。 |
| `checkAbilityPart(ability,pos)` | 某 ability 只允许在特定位置参与逻辑时过滤。返回 `false` 表示不收集。 |
| `multiblockPartSorter()` | 能力顺序影响业务时返回位置排序函数，例如装配线按左右、蒸馏塔按高度排序。 |
| `isStructureFormed()` | 查询 canonical runtime lifecycle；客户端使用同步镜像。 |
| `shouldShowInJei()` | 返回 `false` 隐藏 JEI 多方块页；默认 `true`。 |
| `getPreviewFrontFacing()` | 修改 JEI/工具默认预览朝向。 |
| `allowsExtendedFacing()` | 是否允许朝上/旋转扩展方向；默认 `true`。不支持的结构返回 `false`。 |
| `allowsFlip()` | 是否允许镜像翻转；墙面共享控制器等结构通常关闭。 |
| `getSupportedChannels()` | 默认从模板自动收集；有模板外 channel 时覆写补充。 |
| `getChannelRange(channel)` | 查询 tier 或重复次数的可选范围。 |

### 9.3 外部状态依赖

若结构是否有效依赖控制器字段，而不只是世界方块，需要：

1. 覆写对应 value getter，使快照包含稳定且可比较的值。
2. 值改变时调用对应 `notify...Changed()`。

| 状态类别 | Getter | 变更通知 |
| --- | --- | --- |
| 控制器工作/模式 | `getStructureControllerModeValue()` | `notifyStructureControllerModeChanged()` |
| channel 选择 | `getStructureChannelDependencyValue()` | `notifyStructureChannelsChanged()` |
| 配置/开关 | `getStructureConfigDependencyValue()` | `notifyStructureConfigChanged()` |
| upgrade | `getStructureUpgradeDependencyValue()` | `notifyStructureUpgradesChanged()` |

不要让结构元素偷偷读取可变字段却不声明依赖，否则增量检查可能复用过期结果。

### 9.4 检查调度高级覆写

| 方法 | 作用 |
| --- | --- |
| `isWorkingForStructureCheck()` | 告诉 scheduler 当前是否工作，以选择 working/standby 轮询间隔。RecipeMap 基类已实现。 |
| `allowsAsyncStructureCheck()` | 大结构快照成本过高或元素不适合异步时返回 `false`。默认 `true`。 |
| `getStructureSchedulerPolicy()` | 选择默认、仅轮询、仅事件或可异步等策略。 |
| `getStructureCheckIntervalStandby/Working()` | 回退轮询间隔，单位 tick，最少 20。 |
| `setDelayCheck(...)`、`setDelayStructureCheckStandby/Work(...)` | 运行时配置延迟检查；设置值会自动通知结构配置变化。 |

普通机器不要手动每 tick 调 `checkStructurePattern()`；基类 scheduler 已处理事件驱动、增量、异步和回退轮询。

## 10. 三个完整模式

### 10.1 固定结构 + 独立消音位 + 分级线圈

```java
private static final StructureDefinition<?> STRUCTURE_DEFINITION =
        StructureDefinition.getOrBuild("myaddon:heated_machine", () ->
                DeclarativePatternBuilder.start()
                        .aisle("XXX", "CCC", "XXX")
                        .aisle("XXX", "C#C", "XMX")
                        .aisle("XSX", "CCC", "XXX")
                        .self('S', MyHeatedMachine.class)
                        .air('#')
                        .hatch('M', MultiblockAbility.MUFFLER_HATCH)
                        .casing('X', getCasingState())
                            .preset(HatchPresets.ELECTRIC_STANDARD_FIXED_MUFFLER)
                        .tieredCasing('C', GTCasingGroups.heatingCoils().group())
                            .withChannel(GTCasingGroups.heatingCoils().channel())
                        .buildStructureDefinition());
```

`M` 已固定消音仓位置，所以 casing preset 不能再要求一个可替换消音位。

### 10.2 起点 + 可变 body + 终点

```java
DeclarativePatternBuilder.start(BACK, UP, RIGHT)
        .piece("start")
            .aisle("XXX", "XSX", "XXX")
        .repeatablePiece("body", 3, 15)
            .aisle("XXX", "X#X", "XXX")
            .withAisleChannel(GTStructureChannels.STRUCTURE_LENGTH.getName())
        .piece("end")
            .aisle("XXX", "XXX", "XXX")
        .self('S', MyController.class)
        .air('#')
        .casing('X', getCasingState())
            .maintenance()
            .energyInput(1, 2)
        .buildStructureDefinition();
```

成型后读取长度：

```java
int bodyLength = formed.getPieceRepeat("body", 0);
```

这里 `body` 只有一条 repeat axis，所以索引是 0；它不是局部坐标轴编号 2。

### 10.3 多轴重复区域

```java
DeclarativePatternBuilder.start(RIGHT, UP, BACK)
        .piece("base")
            .aisle("WSW", "WWW")
            .centerOffset(1, 0, 0)
        .repeatablePiece("wall",
                new String[][] {
                        { "WCW", "W W" },
                        { "WWW", "C W" }
                },
                new Vec3i(0, 2, 0))
            .repeatAxes(0, 1, 2)
            .repeatRange(1, 5, 1, 7, 1, 4)
            .stepSizes(3, 2, -2)
            .channelNames(
                    GTStructureChannels.STRUCTURE_WIDTH.getName(),
                    GTStructureChannels.STRUCTURE_HEIGHT.getName(),
                    GTStructureChannels.STRUCTURE_LENGTH.getName())
            .centerOffset(1, 0, 1)
        .self('S', MyController.class)
        .block('W', getWallState())
        .block('C', getCornerState())
        .air(' ')
        .buildStructureDefinition();
```

此例把空格显式改成空气；若不写 `.air(' ')`，内部位置会是“不检查”。

## 11. 注册控制器

声明字段：

```java
public static MetaTileEntityExampleMultiblock EXAMPLE_MULTIBLOCK;
```

注册：

```java
EXAMPLE_MULTIBLOCK = registerMetaTileEntity(1200,
        new MetaTileEntityExampleMultiblock(gregtechId("example_multiblock")));
```

`registerMetaTileEntity(...)` 还会：

- 为 `IMultiblockAbilityPart` 注册其所有 ability。
- 在 JEI 已加载且 `shouldShowInJei()` 为 `true` 时注册控制器的多方块页面。

数值 ID、资源 ID、结构缓存 key 都必须稳定；缓存 key 推荐直接使用完整 MTE ID。

## 12. 常见错误与排查

| 现象 | 原因与处理 |
| --- | --- |
| `Didn't find center predicate` | 单 piece 没有 `self(...)`，且没有合法 `centerOffset`/预构建模板中心。主结构通常应放控制器字符。 |
| `Must have 3 different axes!` | `start(...)` 的三方向没有覆盖三条不同轴。 |
| aisle 高度或 row 宽度异常 | 同一 piece 的所有 aisle 必须等高，每一行必须等宽。注意尾部空格也计入宽度。 |
| 字符未映射 | 除空格外每个出现的字符都要 `where`；检查拼写和链式调用是否在 `build` 前执行。 |
| 内部空腔被实心方块占用仍能成型 | 空格默认 `any()`；改用 `#` + `air('#')`，或显式 `air(' ')`。 |
| JEI/投影显示错误候选 | 自定义 `blockPredicate` 缺少 candidates，或组合元素没有 `previewCount/defaultCandidate`。 |
| 结构能预览但不能成型 | 检查方向、中心、offset、仓口最小/最大数量、能力组限制和 tier 一致性。 |
| 固定尾部与可变 body 重叠 | declarative 结构应按顺序拆成命名 piece；底层 builder 使用 `positionedAfterRepeatable`。 |
| 分级 casing 成型后读不到 | `tieredCasing` 使用的 channel 与 `formStructure` 读取的 channel 不一致。 |
| 自定义派生字段在拆结构后仍保留 | 在 `invalidateStructure()` 调 `super` 后清零字段。 |
| 覆写 `formStructure` 后配方不工作 | 忘记调用 `formRecipeMapStructure`、`formSteamRecipeMapStructure` 或 `formStructureWithDisplay`。 |
| 仓口顺序错乱 | 覆写 `multiblockPartSorter()`，按结构局部方向排序。 |
| 修改模式/配置后结构不刷新 | value getter 未包含该值，或改变后没有调用对应 `notifyStructure...Changed()`。 |
| 增量检查总是回退 | 元素/条件存在 opaque 行为或缺少依赖声明。查看 `StructureEligibilityPlan` 和结构 trace。正确性优先，不要强行标记为可增量。 |
| 超大结构卡顿 | 先检查是否有明确依赖、事件 dirty 和异步支持；再考虑拆 piece。不要为了“看起来更模块化”随意拆普通结构。 |

调试配置中：

- `debugStructureTrace`：查看形成、提交、piece、坐标、channel 和失败路径。
- `debugStructureCheck`：查看 dirty root、增量 eligibility、快照预检、fallback reason 和 shadow validation。

## 13. 推荐源码示例

| 类 | 适合参考的内容 |
| --- | --- |
| `MetaTileEntityVacuumFreezer` | 最小固定结构、declarative casing 和 preset。 |
| `MetaTileEntityElectricBlastFurnace` | 独立消音位、tiered casing、channel payload 和生命周期清理。 |
| `MetaTileEntityAssemblyLine` | 自定义方向、start/body/end、可变长度、能力组、自定义元素和 part 排序。 |
| `MetaTileEntityMultiAxisDemo` | 三轴重复、offset、step、尺寸 channel。 |
| `MetaTileEntityHugeTransformer` | 直接使用低层 `StructureDefinition.builder`。 |
| `MetaTileEntityCleanroom` | runtime detector 与一次性工具模板，高级用法。 |

设计边界、异步和增量检查的维护契约见
[`structure-system-v3-design.md`](structure-system-v3-design.md)。
