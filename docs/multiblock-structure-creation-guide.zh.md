# 多方块结构创建指南

本文面向新增或重写 GregTech 多方块结构的开发者。当前结构系统已经从旧的
`BlockPattern` 改为 **共享不可变模板** + **实例可变状态** 的架构，新代码应优先使用：

- `BlockPatternTemplate`：结构定义模板，不随机器实例变化。
- `MultiblockState`：每个控制器实例自己的检查缓存、匹配上下文和重复层数。
- `TemplatePool` / `SoftTemplate` / `LazyTemplate`：静态缓存模板，避免每台机器重复构建结构。
- `DeclarativePatternBuilder`：推荐的新结构构建器，支持自动 casing 数量、声明式仓口、分级 casing 和结构频道。

旧的 `createStructurePattern()` 和 `FactoryBlockPattern.build()` 仍保留兼容，但新结构不要再使用。

## 推荐创建流程

1. 选择控制器基类。
2. 声明静态模板缓存。
3. 用 `DeclarativePatternBuilder` 描述结构层和字符含义。
4. 覆写 `createStructureTemplate()` 返回缓存模板。
5. 在 `formStructure()` 中读取匹配结果，初始化温度、等级、特殊状态等。
6. 在 `invalidateStructure()` 中清理运行时状态。
7. 注册控制器，并确认 JEI 预览、自动建造和实际成型都正常。

## 选择控制器基类

常用基类如下：

| 需求 | 推荐基类 |
| --- | --- |
| 常规耗电配方机器 | `RecipeMapMultiblockController` |
| 原始多方块，内部自带简单物品/流体库存 | `RecipeMapPrimitiveMultiblockController` |
| 有 GUI / 维护 / 消音仓逻辑，但不直接跑 `RecipeMap` | `MultiblockWithDisplayBase` |
| 完全自定义控制器 | `MultiblockControllerBase` |

`RecipeMapMultiblockController` 会在结构成型后通过 `RecipeAbilityManager` 收集输入、输出、能源、维护等能力。通常只需要定义结构和少量特殊逻辑。

## 最小结构模板

以下是一个常规电力多方块的骨架：

```java
public class MetaTileEntityExampleMultiblock extends RecipeMapMultiblockController {

    private static final SoftTemplate TEMPLATE = TemplatePool.getInstance().register(
            "myaddon:example_multiblock",
            () -> DeclarativePatternBuilder.start()
                    .aisle("XXX", "XXX", "XXX")
                    .aisle("XXX", "X#X", "XXX")
                    .aisle("XSX", "XXX", "XXX")
                    .where('S', selfPredicate(MetaTileEntityExampleMultiblock.class))
                    .where('#', air())
                    .casing('X', CasingDefinition.simple(getCasingState()))
                        .maintenance()
                        .energyInput(1, 2)
                        .optionalItemInput(4)
                        .optionalItemOutput(4)
                        .optionalFluidInput(2)
                        .optionalFluidOutput(2)
                    .buildTemplate()
    );

    public MetaTileEntityExampleMultiblock(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.MACERATOR_RECIPES);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityExampleMultiblock(metaTileEntityId);
    }

    @NotNull
    @Override
    protected BlockPatternTemplate createStructureTemplate() {
        return TEMPLATE.get();
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

关键点：

- 静态模板里不要调用实例方法 `selfPredicate()`；使用 `selfPredicate(YourController.class)`。
- 模板必须有一个 `setCenter()` 谓词，通常就是控制器字符 `S`。
- 新代码覆写 `createStructureTemplate()`，不要覆写 `createStructurePattern()`。
- 结构结尾必须用 `buildTemplate()`，不要用 `build()`。

## 结构层和方向

`aisle(...)` 定义结构沿深度方向的一层。每个 `String` 是同一层里的不同行，每个字符代表一个结构槽位：

```java
.aisle("XXX", "CCC", "XXX")
.aisle("XSX", "C#C", "XXX")
```

默认方向来自 `FactoryBlockPattern.start()`：

| 本地轴 | 默认相对方向 | 含义 |
| --- | --- | --- |
| 字符横向 | `RIGHT` | 字符串中从左到右 |
| 字符串行 | `UP` | `aisle` 参数中从第一行到最后一行 |
| `aisle` 顺序 | `BACK` | 多次 `.aisle(...)` 的推进方向 |

如果机器需要特殊轴向，显式传入方向：

```java
DeclarativePatternBuilder.start(FRONT, UP, RIGHT)
```

例如装配线使用 `start(FRONT, UP, RIGHT)`，因为它的长度沿控制器的左右方向展开。

## 字符谓词

常见字符约定：

| 字符 | 常见含义 | 写法 |
| --- | --- | --- |
| `S` | 控制器中心 | `selfPredicate(MyController.class)` |
| `#` | 空气 | `air()` |
| 空格 | 任意方块 | 默认就是 `ANY`，也可显式 `where(' ', any())` |
| `X` | 基础 casing / 可替换仓口位 | `.casing('X', ...)` |
| `C` | 分级 casing，如线圈、玻璃 | `.tieredCasing('C', ...)` |
| 其他 | 固定方块或特殊仓口 | `states(...)` / `abilities(...)` / `metaTileEntities(...)` |

底层常用谓词：

```java
where('A', states(getBlockState()))
where('B', abilities(MultiblockAbility.IMPORT_ITEMS))
where('C', metaTileEntities(MetaTileEntities.COKE_OVEN_HATCH))
where('D', blocks(Blocks.IRON_BLOCK))
where('#', air())
where(' ', any())
```

带数量限制时使用：

```java
abilities(MultiblockAbility.INPUT_ENERGY)
        .setMinGlobalLimited(1)
        .setMaxGlobalLimited(2)
        .setPreviewCount(1)
```

## 使用 DeclarativePatternBuilder

`DeclarativePatternBuilder` 适合绝大多数新结构。它比直接使用 `FactoryBlockPattern` 多做了几件事：

- 根据结构字符出现次数和仓口最大数量，自动计算最低 casing 数量。
- 用 `.maintenance()`、`.energyInput()`、`.optionalItemInput()` 这类方法声明仓口。
- 支持 `.tieredCasing()` 分级 casing，并把匹配结果写入 `PatternMatchContext`。
- 自动生成结构 tooltip 描述，显示 casing 和仓口需求。

### casing 位

```java
.casing('X', CasingDefinition.simple(getCasingState()))
    .maintenance()
    .energyInput(1, 2)
    .optionalItemInput(4)
    .optionalItemOutput(4)
    .optionalFluidInput(2)
    .optionalFluidOutput(2)
```

`X` 在结构中出现的总数会作为 casing + 仓口槽位总数。上面的例子中，builder 会自动把 `X` 的最低 casing 数量设置为：

```text
X 总数 - 所有声明仓口的 maxCount 之和
```

如果结构必须有消音仓，可使用：

```java
.muffler()
```

并在控制器中覆写：

```java
@Override
public boolean hasMufflerMechanics() {
    return true;
}
```

注意：`HatchPresets.ELECTRIC_STANDARD` 包含维护仓、消音仓、1-2 个能源输入仓和标准输入输出仓。如果机器没有消音仓逻辑，不要直接使用这个 preset。

### 自定义仓口或特殊 MTE

不属于 `MultiblockAbility` 的仓口用 `.custom(...)`：

```java
.casing('X', CasingDefinition.simple(getCasingState()))
    .custom(metaTileEntities(MetaTileEntities.COKE_OVEN_HATCH).setMaxGlobalLimited(5), 5)
```

第二个参数是该自定义谓词最多占用多少个 casing 槽位，用于自动计算最低 casing 数量。

### 分级 casing 和频道

线圈、机器 casing、硼硅玻璃这类结构等级使用 `tieredCasing`：

```java
.tieredCasing('C', GTCasingGroups.heatingCoils().group())
    .withChannel(GTCasingGroups.heatingCoils().channel())
```

在 `formStructure()` 里读取成型时匹配到的具体 casing：

```java
@Override
protected void formStructure(PatternMatchContext context) {
    super.formStructure(context);

    ICasing matchedCoil = GTCasingGroups.heatingCoils().channel().getMatchedCasing(context);
    IHeatingCoilBlockStats stats = matchedCoil != null
            ? matchedCoil.getPayloadAs(IHeatingCoilBlockStats.class)
            : null;

    this.temperature = stats != null ? stats.getCoilTemperature() : 0;
}
```

如果只需要整数等级，可以用：

```java
int tier = GTCasingGroups.heatingCoils().channel().getValue(context);
```

控制器基类也会把所有非零频道值保存到 `getFormedChannelValues()`。

### 可变长度结构

可重复层用 `aisleRepeatable(min, max, ...)`：

```java
.aisle("FIF", "RTR", "SAG", " Y ")
.aisleRepeatable(3, 15, "FIF", "RTR", "DAG", " Y ")
    .withAisleChannel(GTStructureChannels.STRUCTURE_LENGTH.getName())
.aisle("FOF", "RTR", "DAG", " Y ")
```

`withAisleChannel(...)` 让 JEI 预览和自动建造可以按频道值选择长度。常用频道在 `GTStructureChannels` 中，例如：

- `STRUCTURE_LENGTH`
- `STRUCTURE_HEIGHT`
- `NO_HATCH`
- `STRUCTURE_PIECE`

## 什么时候使用 FactoryBlockPattern

只有在这些场景下才建议直接使用 `FactoryBlockPattern`：

- 结构字符来自外部数组或生成逻辑。
- 需要非常特殊的谓词组合，声明式 builder 表达起来反而更绕。
- 超大型结构要拆分成多个 piece，并且子 piece 没有控制器中心。

新代码仍然要返回 `BlockPatternTemplate`：

```java
private static final SoftTemplate TEMPLATE = TemplatePool.getInstance().register(
        "myaddon:generated_structure",
        () -> FactoryBlockPattern.start(RIGHT, UP, FRONT)
                .aisle("XXX", "XSX", "XXX")
                .where('S', selfPredicate(MyController.class))
                .where('X', states(getCasingState()).setMinGlobalLimited(8))
                .buildTemplate()
);
```

不要使用：

```java
.build()
```

## 模板缓存选择

| 场景 | 推荐 |
| --- | --- |
| 普通新增机器、addon 机器、数量较多的变体 | `TemplatePool.getInstance().register(...)` 返回的 `SoftTemplate` |
| 高频核心机器，不希望被 GC 回收 | `LazyTemplate.of(...)` |
| enum 变体机器 | `TemplatePool.buildEnumCache(...)` |

单体结构：

```java
private static final SoftTemplate TEMPLATE = TemplatePool.getInstance().register(
        "gregtech:electric_blast_furnace",
        MetaTileEntityElectricBlastFurnace::buildTemplate
);
```

enum 变体：

```java
private static final Map<MyType, SoftTemplate> TEMPLATES = TemplatePool.buildEnumCache(
        "myaddon:large_machine",
        MyType.class,
        type -> () -> buildTemplate(type)
);

@Override
protected BlockPatternTemplate createStructureTemplate() {
    return TEMPLATES.get(type).get();
}
```

缓存 key 必须稳定且唯一，建议使用完整 MTE id；变体结构用 `namespace:path/variant`。

## 结构生命周期

多方块控制器的关键生命周期：

1. `reinitializeStructurePattern()` 创建或重新绑定 `patternTemplate` 和 `multiblockState`。
2. `checkStructurePattern()` 使用 `multiblockState.checkPatternFastAt(...)` 检查结构。
3. 首次成功成型时收集 `IMultiblockPart`、`MultiblockAbility` 和频道值。
4. 调用 `formStructure(context)`。
5. 已成型结构再次检查成功但部件变化时，走 `reassembleStructure(context)`，会重新收集部件并再次调用 `formStructure(context)`。
6. 检查失败或控制器移除时调用 `invalidateStructure()`。

因此：

- `formStructure()` 必须可以重复调用，不要假设它只在第一次成型时执行。
- 从结构读取出的温度、等级、模式等运行时字段，要在 `invalidateStructure()` 清零。
- 覆写这两个方法时先调用 `super`，除非明确知道要绕过基类行为。

示例：

```java
private int coilTemperature;

@Override
protected void formStructure(PatternMatchContext context) {
    super.formStructure(context);
    ICasing casing = GTCasingGroups.heatingCoils().channel().getMatchedCasing(context);
    IHeatingCoilBlockStats stats = casing == null ? null : casing.getPayloadAs(IHeatingCoilBlockStats.class);
    this.coilTemperature = stats == null ? 0 : stats.getCoilTemperature();
}

@Override
public void invalidateStructure() {
    super.invalidateStructure();
    this.coilTemperature = 0;
}
```

## 部件排序和能力过滤

如果输入仓、数据仓等能力顺序会影响逻辑，覆写 `multiblockPartSorter()`：

```java
@Override
protected Function<BlockPos, Integer> multiblockPartSorter() {
    return RelativeDirection.LEFT.getSorter(getFrontFacing(), getUpwardsFacing(), isFlipped());
}
```

如果某些位置的能力不能被收集，覆写 `checkAbilityPart(...)`：

```java
@Override
protected <T> boolean checkAbilityPart(MultiblockAbility<T> ability, BlockPos pos) {
    return ability != MultiblockAbility.IMPORT_ITEMS || isValidInputSide(pos);
}
```

## JEI、预览和自动建造

注册控制器时，`registerMetaTileEntity(...)` 会自动把 `MultiblockControllerBase` 注册到 JEI，前提是：

```java
controller.shouldShowInJei()
```

返回 `true`。默认就是 `true`。

预览候选来自每个谓词的 `BlockInfo[]`：

- `states(...)`、`metaTileEntities(...)`、`abilities(...)` 会自动提供候选。
- 自定义 `TraceabilityPredicate` 如果希望 JEI 和自动建造显示正确，需要传入候选 supplier。
- 数量限制建议配合 `setPreviewCount(...)`，避免 JEI 选错展示数量。

右键空手潜行未成型控制器时，客户端会调用多方块预览渲染。结构频道会影响可变长度、分级 casing 和自动建造的候选选择。

## 注册控制器

在 `MetaTileEntities` 声明字段：

```java
public static MetaTileEntityExampleMultiblock EXAMPLE_MULTIBLOCK;
```

在对应 registration 类中注册：

```java
EXAMPLE_MULTIBLOCK = registerMetaTileEntity(1200,
        new MetaTileEntityExampleMultiblock(gregtechId("example_multiblock")));
```

`registerMetaTileEntity(...)` 会处理两类额外逻辑：

- 如果注册对象是 `IMultiblockAbilityPart`，自动加入 `MultiblockAbility` 注册表。
- 如果注册对象是多方块控制器且显示 JEI，自动注册多方块 JEI 页面。

## 超大型结构：MultiPiecePattern

普通多方块不要拆 piece。只有上千方块、频繁局部变化、全量检查过重的结构才考虑 `MultiPiecePattern`。

基本做法：

1. 主模板仍用于初始成型和 JEI。
2. 每个子结构单独构建 `BlockPatternTemplate`。
3. 子结构没有 `selfPredicate()` 时，用 `buildTemplate(centerOffset)` 指定中心偏移。
4. 覆写 `createMultiPiecePattern()` 返回 piece 列表。

示意：

```java
private static final int[] RING_CENTER = { 63, 14, 0, 0, 0 };

private static final SoftTemplate RING_TEMPLATE = TemplatePool.getInstance().register(
        "myaddon:huge_machine/ring",
        () -> FactoryBlockPattern.start(RIGHT, UP, FRONT)
                .aisle(...)
                .where('A', states(getRingState()))
                .buildTemplate(RING_CENTER)
);

@Nullable
@Override
protected MultiPiecePattern createMultiPiecePattern() {
    return MultiPiecePattern.builder()
            .piece("ring", RING_TEMPLATE.get(), new Vec3i(0, 0, 64), OffsetMode.RELATIVE)
            .build();
}
```

`GTStructureChannels.STRUCTURE_PIECE` 可用于选择自动建造或预览某个 piece。

## 常见迁移点

| 旧写法 | 新写法 |
| --- | --- |
| `createStructurePattern()` | `createStructureTemplate()` |
| `BlockPattern` | `BlockPatternTemplate` + `MultiblockState` |
| `FactoryBlockPattern.build()` | `FactoryBlockPattern.buildTemplate()` |
| 每个实例重新构建结构 | 静态 `SoftTemplate` / `LazyTemplate` 缓存 |
| `structurePattern.cache` | `multiblockState.cache` |
| `structurePattern.formedRepetitionCount` | `multiblockState.formedRepetitionCount` |
| `structurePattern.getError()` | `multiblockState.getError()` |
| 手写 casing 最低数量 | `DeclarativePatternBuilder.casing(...)` 自动计算 |
| `heatingCoils()` | `GTCasingGroups.heatingCoils().group()` + channel |

旧 API 仍有兼容层，但目标是后续移除。新增结构不应继续依赖旧字段和旧方法。

## 排错清单

- 报 `Didn't find center predicate`：模板中没有 `selfPredicate(...).setCenter()`，或子 piece 忘了用 `buildTemplate(centerOffset)`。
- JEI 预览没有控制器：静态模板里用了实例 `selfPredicate()`，改成 `selfPredicate(MyController.class)`。
- 结构能预览但不能成型：检查字符是否都有 `.where(...)`；空格默认 `ANY`，`#` 需要显式 `air()`。
- 仓口替换后配方库存没更新：确保仓口通过 `abilities(...)` 或 `.casing(...).hatch(...)` 参与匹配，并允许 `reassembleStructure(context)` 重新收集。
- 消音仓必需但机器仍显示阻塞异常：确认覆写 `hasMufflerMechanics()` 返回 `true`，并且消音仓朝向外侧。
- 分级 casing 等级读不到：确认 `.tieredCasing(...).withChannel(...)` 使用的 channel 和 `formStructure()` 读取的是同一个。
- 可变长度自动建造不受选择影响：确认 `aisleRepeatable(...)` 后调用了 `.withAisleChannel(...)`。
- 结构旋转后失效：检查 `start(...)` 的三个方向是否覆盖不同轴，并确认结构图中控制器相对位置正确。
- 大结构频繁卡顿：先确认是否启用事件驱动/异步检查；只有确实需要时再拆 `MultiPiecePattern`。

## 推荐参考实现

- `MetaTileEntityCokeOven`：简单原始多方块，使用 `DeclarativePatternBuilder` 和自定义仓口。
- `MetaTileEntityElectricBlastFurnace`：分级线圈 casing，通过 channel 在 `formStructure()` 读取线圈属性。
- `MetaTileEntityAssemblyLine`：自定义方向、可变长度 aisle、特殊数据仓和部件排序。
- `MetaTileEntityForgeOfGods`：超大型结构和 `MultiPiecePattern` 拆分示例。
