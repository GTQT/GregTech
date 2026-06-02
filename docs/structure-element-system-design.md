# 结构元素系统设计文档

## 1. 背景与动机

### 1.1 当前限制

GregTech CEu 当前的多方块结构模式系统使用 `aisleRepeatable(min, max)` 来定义可重复的结构层。这种机制 **仅支持一维** —— 它只能沿着 aisle 方向（通常是 Z 轴 / 后方方向）重复。

主要限制：
- **单轴重复**：`aisleRepeatable` 只能沿一个轴重复。多轴可变结构（如洁净室，在 X 和 Z 轴都可变）需要变通方案。
- **无行/列重复**：没有 `rowRepeatable` 或 `columnRepeatable` API。
- **拙劣的多轴实现**：洁净室在同一轴上使用多个 `aisleRepeatable` 段来模拟二维变化，语义上令人困惑。
- **固定模式网格**：整个结构必须表示为 aisle 的平面列表，使得复杂形状（L 形、T 形）难以实现。

### 1.2 GT5-Unofficial 对比

GT5-Unofficial 使用 StructureLib 库，采用基于片段的方法：
- 结构被拆分为命名片段（固定大小的模式片段）
- 通过在 Java 代码中循环 `checkPiece()` 来实现可变大小
- 没有原生的多轴重复 —— 即使是 GT5U 的洁净室也使用完全自定义的检查逻辑

### 1.3 设计目标

创建一个新的 **结构元素** 系统，具备以下特性：
1. 原生支持 **多轴重复**（一维、二维、三维）
2. 使用 **基于片段** 的方法以实现可组合性
3. 通过 **适配器** 与现有系统共存（无破坏性变更）
4. 支持 **增量迁移**（一次一台机器）

结构元素系统为什么不能模板和状态分离？不能简化写法吗？而且这样实现能做到可重复子区域这种方式吗？为什么要新类而不是改现有的类？
---

## 2. 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│                    MultiblockControllerBase                   │
│                                                              │
│  ┌────────────────────────┐   ┌────────────────────────────┐ │
│  │  现有系统               │   │  新系统                    │ │
│  │  (未修改)               │   │  (结构元素)                │ │
│  │                        │   │                            │ │
│  │  BlockPatternTemplate  │   │  StructureDefinition       │ │
│  │  MultiblockState       │   │  IStructureElement         │ │
│  │  FactoryBlockPattern   │   │  IStructurePiece           │ │
│  │  DeclarativePattern... │   │  StructureElementAdapter   │ │
│  └──────────┬─────────────┘   └──────────┬─────────────────┘ │
│             │                             │                   │
│             └─────── 适配器 ─────────────┘                   │
│                  (新 → 旧 系统转换)                           │
└─────────────────────────────────────────────────────────────┘
```

**核心原则**：新系统是附加的。不修改现有代码。适配器将新样式的定义转换为 `BlockPatternTemplate` / `MultiPiecePattern`，使现有的控制器基础设施保持不变。

---

## 3. 核心接口

### 3.1 IStructureElement

基本构建块 —— 表示单个位置的匹配规则。

```java
package gregtech.api.pattern.element;

public interface IStructureElement {

    // --- 核心匹配 ---

    /**
     * 检查 pos 处的方块是否匹配此元素。
     * 匹配成功时，可能在上下文中存储数据（例如 hatch 引用）。
     * 匹配失败时，可能在上下文中设置错误信息。
     */
    boolean check(World world, BlockPos pos, PatternMatchContext context);

    // --- 预览 / 自动构建 ---

    /**
     * 获取 JEI 预览和自动构建的候选方块。
     * 如果此元素匹配任何方块（如空气），则返回空数组。
     */
    BlockInfo[] getCandidates();

    /**
     * 在 pos 处放置方块以进行自动构建。
     * @return 如果放置了方块则返回 true
     */
    boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                       EntityPlayer player, boolean skipHatches);

    /**
     * 在 pos 处为结构投影器生成提示粒子。
     */
    void spawnHint(World world, BlockPos pos);

    // --- 数量限制 ---

    default boolean hasLimit() { return false; }
    default int getMinGlobalCount() { return 0; }
    default int getMaxGlobalCount() { return -1; }  // -1 = 无限制
    default int getMinLayerCount() { return 0; }
    default int getMaxLayerCount() { return -1; }   // -1 = 无限制

    // --- 通道 / 层级 ---

    /**
     * 分级外壳选择的通道名称。
     * 用于自动构建和预览以选择正确的层级。
     */
    @Nullable default String getChannelName() { return null; }

    // --- 中心标记 ---

    /**
     * 此元素是否标记控制器中心位置。
     * 结构定义中必须恰好有一个元素是中心。
     */
    default boolean isCenter() { return false; }

    // --- 提示 ---

    default void addTooltip(List<String> tooltip) {}
}
```

### 3.2 IStructurePiece

一个命名的、可能可重复的结构片段。

```java
package gregtech.api.pattern.element;

public interface IStructurePiece {

    /** 此片段的唯一名称（例如 "base"、"layer"、"top"）。 */
    String getName();

    /**
     * 此片段的模式网格。
     * 格式：String[z][y]，其中每个字符串是一行字符。
     * 每个字符通过 getSymbolMap() 映射到 IStructureElement。
     *
     * 3x3x3 片段的示例：
     *   {
     *     {"XXX", "XXX", "XXX"},   // z=0
     *     {"X#X", "X#X", "X#X"},   // z=1
     *     {"XXX", "XXX", "XXX"},   // z=2
     *   }
     */
    String[][] getPattern();

    /** 模式字符到 IStructureElement 实例的映射。 */
    Map<Character, IStructureElement> getSymbolMap();

    // --- 重复配置 ---

    /**
     * 此片段在哪些轴上可重复。
     * 0 = X（手掌/右）, 1 = Y（拇指/上）, 2 = Z（手指/后）
     *
     * 示例：
     *   {2}       = 仅 Z 轴（等效于当前的 aisleRepeatable）
     *   {1}       = 仅 Y 轴（高度可重复）
     *   {1, 2}    = Y 和 Z 轴（二维重复）
     *   {0, 1, 2} = 所有轴（三维重复）
     */
    int[] getRepeatAxes();

    /**
     * 每轴重复范围，与 getRepeatAxes() 平行。
     * repeatRanges[i] = {minRepeat, maxRepeat} 对应 getRepeatAxes()[i]。
     * 对于不可重复的片段，此值为空。
     */
    int[][] getRepeatRanges();

    /**
     * 每轴通道名称，与 getRepeatAxes() 平行。
     * 空条目表示该轴无通道控制。
     */
    @Nullable String[] getRepeatChannelNames();

    /**
     * 每轴步长，与 getRepeatAxes() 平行。
     * stepSizes[i] = 沿 getRepeatAxes()[i] 每次重复前进的方块数。
     * 通常等于片段沿该轴的大小。
     */
    int[] getStepSizes();

    /**
     * 片段内的中心偏移 [x, y, z]。
     * 模式网格中控制器/中心方块的位置。
     */
    int[] getCenterOffset();

    /**
     * 此片段是否在任何轴上可重复。
     */
    default boolean isRepeatable() {
        return getRepeatAxes().length > 0;
    }
}
```

### 3.3 StructureCheckResult

结构检查操作的结果。

```java
package gregtech.api.pattern.element;

public class StructureCheckResult {

    private final boolean success;
    @Nullable private final PatternMatchContext matchContext;
    @Nullable private final StructureError error;

    // 对于可重复片段：每轴的实际重复次数
    @Nullable private final int[] formedRepetitions;

    // 所有匹配方块位置的缓存
    @Nullable private final Long2ObjectMap<BlockInfo> positionCache;

    // --- 静态工厂方法 ---
    public static StructureCheckResult success(PatternMatchContext context,
                                                int[] formedRepetitions,
                                                Long2ObjectMap<BlockInfo> cache);
    public static StructureCheckResult failure(StructureError error);

    // --- 访问器 ---
    public boolean isSuccess();
    @Nullable public PatternMatchContext getMatchContext();
    @Nullable public StructureError getError();
    @Nullable public int[] getFormedRepetitions();
    @Nullable public Long2ObjectMap<BlockInfo> getPositionCache();
}
```

### 3.4 StructureError

结构检查失败时的错误信息。

```java
package gregtech.api.pattern.element;

public class StructureError {
    @Nullable private final BlockPos errorPos;
    @Nullable private final IStructureElement errorElement;
    @Nullable private final String pieceName;
    private final String message;

    // 访问器...
}
```

---

## 4. StructureDefinition

组合片段的顶层结构定义。

### 4.1 类设计

```java
package gregtech.api.pattern.element;

public class StructureDefinition {

    private final RelativeDirection[] structureDir;
    private final List<PieceEntry> pieceEntries;

    // --- 片段条目 ---
    public static class PieceEntry {
        final IStructurePiece piece;
        final Vec3i baseOffset;         // 从控制器到此片段中心的偏移
        final OffsetMode offsetMode;    // 如何应用偏移 (ABSOLUTE, RELATIVE, HORIZONTAL_RELATIVE)
        @Nullable final BooleanSupplier condition;  // 条件激活

        // 便捷方法：固定片段（无重复）
        public PieceEntry(IStructurePiece piece, Vec3i baseOffset, OffsetMode offsetMode) { ... }

        // 带条件
        public PieceEntry(IStructurePiece piece, Vec3i baseOffset,
                          OffsetMode offsetMode, @Nullable BooleanSupplier condition) { ... }
    }

    // --- 核心方法 ---

    /**
     * 对照世界检查整个结构。
     * 按顺序检查片段；对于可重复片段，使用滑动窗口搜索。
     */
    public StructureCheckResult check(World world, BlockPos controllerPos,
                                       EnumFacing frontFacing, EnumFacing upwardsFacing,
                                       boolean isFlipped);

    /**
     * 在给定偏移处检查单个片段。
     */
    public boolean checkPiece(World world, IStructurePiece piece,
                               BlockPos pieceCenterPos, EnumFacing frontFacing,
                               EnumFacing upwardsFacing, boolean isFlipped,
                               PatternMatchContext context);

    /**
     * 使用滑动窗口搜索检查可重复片段。
     * 返回每个重复轴的实际重复次数。
     */
    public int[] checkRepeatablePiece(World world, PieceEntry entry,
                                       BlockPos searchStartPos, EnumFacing frontFacing,
                                       EnumFacing upwardsFacing, boolean isFlipped,
                                       PatternMatchContext context);

    /**
     * 在世界中自动构建结构。
     */
    public void autoBuild(World world, BlockPos controllerPos, EntityPlayer player,
                          @Nullable Map<String, Integer> channelValues, boolean skipHatches);

    /**
     * 获取 JEI 显示的预览方块。
     */
    public BlockInfo[][][] getPreview(@Nullable Map<String, Integer> channelValues);

    // --- 构建器 ---
    public static Builder builder(RelativeDirection charDir, RelativeDirection stringDir,
                                   RelativeDirection aisleDir) { ... }
}
```

### 4.2 构建器 API

```java
StructureDefinition.builder(RIGHT, UP, BACK)
    // 固定片段
    .piece("base", basePattern, Vec3i.ZERO)
        .where('S', selfElement())
        .where('C', ofBlock(casingState))

    // 单轴可重复片段（等效于 aisleRepeatable）
    .repeatablePiece("layer", layerPattern, new Vec3i(0, 1, 0))
        .where('X', ofBlock(casingState))
        .where('H', hatchAdder(IMPORT_ITEMS))
        .repeatAxes(1)                    // Y 轴
        .repeatRange(1, 11)               // 1~11 次重复
        .channelName("height")

    // 多轴可重复片段
    .repeatablePiece("body", bodyPattern, new Vec3i(0, 1, 1))
        .where('W', ofBlock(wallState))
        .where('F', ofBlock(floorState))
        .repeatAxes(0, 2)                 // X 和 Z 轴
        .repeatRange(1, 7, 1, 7)          // X: 1~7, Z: 1~7
        .channelNames("width", "depth")

    // 条件片段
    .conditionalPiece("upgrade", upgradePattern,
        new Vec3i(0, 5, 0), () -> isUpgradeActive())
        .where('U', ofBlock(upgradeState))

    .build();
```

---

## 5. 多轴重复匹配算法

### 5.1 单轴（1D）— 等效于当前 aisleRepeatable

使用与当前 `MultiblockState.checkPatternAt()` 相同的滑动窗口 + 贪婪策略：

```
对于每次重复 r 从 0 到 maxRepeat:
    在偏移 (baseOffset + r * stepSize 沿 repeatAxis) 处检查片段
    如果检查失败:
        如果 r < minRepeat: 回溯（重置搜索起始位置）
        否则: 停止（有效重复次数 = r）
```

### 5.2 多轴（2D/3D）— 递归回溯

对于多轴重复，我们使用 **递归方法**，按顺序检查每个轴：

```
checkAxisRecursive(axisIndex=0):
    for r = 0 to maxRepeat[axisIndex]:
        offset = baseOffset + r * stepSize[axisIndex] 沿 axes[axisIndex]
        if !checkPieceSliceAt(offset, axisIndex):
            break
        validCount[axisIndex] = r + 1

        if axisIndex < axes.length - 1:
            result = checkAxisRecursive(axisIndex + 1)
            if result == null and validCount[axisIndex] > minRepeat[axisIndex]:
                // 内轴失败，但此轴已有足够重复
                // 尝试在此轴上使用较少的重复
                continue
            else if result != null:
                return result  // 所有内轴通过
        else:
            // 最深轴：所有轴都有有效计数
            if 所有计数 >= minRepeat:
                return validCounts

    if validCount[axisIndex] < minRepeat[axisIndex]:
        return null  // 重复次数不足
    return validCounts
```

**关键见解**：对于多轴重复，外轴的重复次数会影响内轴的搜索起始位置。算法首先在外轴上尝试最大重复次数，如果内轴失败则减少。

### 5.3 性能考虑

- **搜索空间**：对于 N 个轴，范围为 [min_i, max_i]，最坏情况是 O(∏(max_i - min_i + 1) × 片段大小)。
- **提前终止**：如果单个切片检查失败，我们立即停止扩展该轴。
- **外轴优先贪婪**：我们首先在外轴上尝试最大重复次数，只有在内轴失败时才减少，这符合最常见的用例（较大的结构更可能是玩家建造的）。
- **实际大小**：大多数多方块结构的重复范围很小（1~15），因此即使二维搜索也可管理（最多 15 × 15 = 225 次切片检查）。

---

## 6. StructureElementAdapter

将新样式的 `StructureDefinition` 转换为现有系统对象。

### 6.1 转换为 BlockPatternTemplate（单轴）

对于只有单轴可重复片段的结构，适配器可以生成一个在功能上等效于当前系统的 `BlockPatternTemplate`：

```java
public static BlockPatternTemplate toTemplate(StructureDefinition definition) {
    // 1. 将所有片段展平为单个 aisle 列表
    // 2. 对于可重复片段，使用 aisleRepetitions
    // 3. 映射 IStructureElement → TraceabilityPredicate
    // 4. 构建并返回 BlockPatternTemplate
}
```

### 6.2 转换为 MultiPiecePattern（多轴）

对于有多轴可重复片段的结构，适配器生成一个 `MultiPiecePattern`，其中每个片段（包括可重复的）成为一个 `StructurePiece`：

```java
public static MultiPiecePattern toMultiPiecePattern(StructureDefinition definition) {
    // 1. 对于每个 PieceEntry，将 IStructurePiece 转换为 BlockPatternTemplate
    // 2. 对于可重复片段，展开到最大重复次数
    //    （因为旧系统无法表达多轴重复）
    // 3. 构建具有适当偏移的 MultiPiecePattern
}
```

### 6.3 IStructureElement → TraceabilityPredicate 转换

```java
private static TraceabilityPredicate toPredicate(IStructureElement element) {
    TraceabilityPredicate predicate = new TraceabilityPredicate(
        new TraceabilityPredicate.SimplePredicate(
            () -> element.getCandidates(),
            bws -> element.check(bws.getWorld(), bws.getPos(), bws.getMatchContext())
        )
    );
    if (element.getMinGlobalCount() > 0) predicate.setMinGlobalLimited(element.getMinGlobalCount());
    if (element.getMaxGlobalCount() > 0) predicate.setMaxGlobalLimited(element.getMaxGlobalCount());
    if (element.getMinLayerCount() > 0) predicate.setMinLayerLimited(element.getMinLayerCount());
    if (element.getMaxLayerCount() > 0) predicate.setMaxLayerLimited(element.getMaxLayerCount());
    if (element.isCenter()) predicate.setCenter();
    return predicate;
}
```

---

## 7. 内置 IStructureElement 实现

### 7.1 ElementUtility（静态工厂方法）

```java
package gregtech.api.pattern.element;

public final class ElementUtility {

    // 匹配特定方块状态
    public static IStructureElement ofBlock(IBlockState state);

    // 匹配任何给定的方块状态
    public static IStructureElement ofBlocks(IBlockState... states);

    // 匹配空气
    public static IStructureElement ofAir();

    // 匹配任何方块（通配符）
    public static IStructureElement ofAny();

    // 链式：如果任何元素匹配则匹配（或逻辑）
    public static IStructureElement ofChain(IStructureElement... elements);

    // 自身谓词（控制器方块）
    public static IStructureElement ofSelf(Class<? extends MetaTileEntity> clazz);

    // Hatch 加法器
    public static IStructureElement ofHatchAdder(MultiblockAbility<?> ability);
    public static IStructureElement ofHatchAdder(MultiblockAbility<?> ability, int min, int max);

    // 分级方块（例如线圈、玻璃层级）
    public static IStructureElement ofTieredBlock(Supplier<BlockInfo[]> candidates,
                                                   String channelName);

    // 延迟初始化
    public static IStructureElement lazy(Supplier<IStructureElement> supplier);

    // 匹配时带回调
    public static IStructureElement onElementPass(Consumer<PatternMatchContext> callback,
                                                   IStructureElement element);

    // 带通道
    public static IStructureElement withChannel(String channelName, IStructureElement element);
}
```

---

## 8. 控制器集成

### 8.1 新的重写方法

向 `MultiblockControllerBase` 添加一个新的可选重写方法：

```java
@Nullable
protected StructureDefinition createStructureDefinition() {
    return null;  // 默认：使用现有系统
}
```

### 8.2 集成流程

在 `reinitializeStructurePattern()` 中：

```java
public void reinitializeStructurePattern() {
    // 首先尝试新系统
    StructureDefinition definition = createStructureDefinition();
    if (definition != null) {
        this.structureDefinition = definition;
        // 转换为现有系统对象以保持兼容性
        if (definition.isSingleAxisRepeatOnly()) {
            this.patternTemplate = StructureElementAdapter.toTemplate(definition);
            this.multiblockState = this.patternTemplate.createState();
        } else {
            this.multiPiecePattern = StructureElementAdapter.toMultiPiecePattern(definition);
        }
        return;
    }

    // 回退到现有系统
    this.patternTemplate = createStructureTemplate();
    this.multiblockState = this.patternTemplate.createState();
    this.multiPiecePattern = createMultiPiecePattern();
}
```

### 8.3 检查流程

在 `checkStructurePattern()` 中：

```java
public void checkStructurePattern() {
    if (structureDefinition != null) {
        // 新系统检查
        StructureCheckResult result = structureDefinition.check(
            world, pos, frontFacing, upwardsFacing, allowsFlip);
        if (result.isSuccess()) {
            // 更新缓存，调用 formStructure
        } else {
            // 处理错误
        }
        return;
    }

    // 现有检查逻辑（未修改）
    ...
}
```

---

## 9. 迁移策略

| 阶段 | 内容 | 范围 |
|-------|---------|-------|
| **P1: 基础设施** | 添加 `IStructureElement`、`IStructurePiece`、`StructureDefinition`、`StructureElementAdapter`、`ElementUtility` | 仅新文件，无现有代码更改 |
| **P2: 单轴验证** | 通过适配器将一台简单的单轴机器（如蒸馏塔）迁移到新系统 | 1 个机器类 |
| **P3: 多轴实现** | 实现多轴匹配算法，用洁净室验证 | 核心算法 + 1 个机器类 |
| **P4: 逐步迁移** | 逐个迁移剩余机器 | 逐机器 |
| **P5: 弃用** | 将 `aisleRepeatable` 标记为 `@Deprecated` | 全局 |

**P1 是安全的**：它只在 `gregtech.api.pattern.element` 下添加新文件。不修改或破坏现有代码。

---

## 10. 包结构

```
gregtech.api.pattern.element/
├── IStructureElement.java           // 核心元素接口
├── IStructurePiece.java             // 片段接口
├── StructureDefinition.java         // 顶层定义 + 构建器
├── StructureCheckResult.java        // 检查结果
├── StructureError.java              // 错误信息
├── StructureElementAdapter.java     // 新 → 旧 系统适配器
├── ElementUtility.java              // 元素的静态工厂方法
├── PieceEntry.java                  // 定义中的片段条目
└── impl/                            // 内置元素实现
    ├── BlockElement.java            // 单方块匹配
    ├── ChainElement.java            // 或组合
    ├── AirElement.java              // 空气匹配
    ├── AnyElement.java              // 通配符匹配
    ├── SelfElement.java             // 控制器自身谓词
    ├── HatchElement.java            // Hatch 加法器
    ├── TieredElement.java           // 分级外壳
    ├── LazyElement.java             // 延迟初始化
    ├── CallbackElement.java         // onElementPass 包装器
    └── ChannelElement.java          // withChannel 包装器
```

---

## 11. 使用示例

### 11.1 简单单轴（等效于蒸馏塔）

```java
StructureDefinition.builder(RIGHT, FRONT, UP)
    .piece("top", new String[][]{{"YSY", "YYY", "YYY"}}, Vec3i.ZERO)
        .where('S', ElementUtility.ofSelf(MetaTileEntityDistillationTower.class))
        .where('Y', ElementUtility.ofBlock(casingState))
    .repeatablePiece("layer", new String[][]{{"XXX", "X#X", "XXX"}},
        new Vec3i(0, 1, 0))
        .where('X', ElementUtility.ofBlock(casingState))
        .where('#', ElementUtility.ofAir())
        .repeatAxes(1)              // Y 轴
        .repeatRange(1, 11)         // 1~11 次重复
        .channelName("height")
    .piece("bottom", new String[][]{{"XXX", "XXX", "XXX"}},
        new Vec3i(0, 1, 0))        // 偏移相对于上一个片段
        .where('X', ElementUtility.ofBlock(casingState))
    .build();
```

### 11.2 多轴（等效于洁净室）

```java
StructureDefinition.builder(RIGHT, UP, BACK)
    .piece("floor", new String[][]{{"SSS", "SSS", "SSS"}}, Vec3i.ZERO)
        .where('S', ElementUtility.ofSelf(MetaTileEntityCleanroom.class))
    .repeatablePiece("wall", new String[][]{{"WWW", "WWW", "WWW"}},
        new Vec3i(0, 1, 0))
        .where('W', ElementUtility.ofBlock(plasticState))
        .repeatAxes(0, 1, 2)       // X, Y, Z 均可重复
        .repeatRange(1, 7, 1, 11, 1, 7)  // X:1~7, Y:1~11, Z:1~7
        .channelNames("width", "height", "depth")
    .piece("ceiling", new String[][]{{"CCC", "CCC", "CCC"}},
        new Vec3i(0, 1, 0))
        .where('C', ElementUtility.ofBlock(plasticState))
    .build();
```

### 11.3 组装线（两个独立的可重复部分）

```java
StructureDefinition.builder(FRONT, UP, RIGHT)
    .piece("head", new String[][]{{"FIF", "RTR", "SAG", " Y "}}, Vec3i.ZERO)
        .where('S', ElementUtility.ofSelf(MetaTileEntityAssemblyLine.class))
        .where('F', ElementUtility.ofBlock(frameState))
        .where('I', ElementUtility.ofHatchAdder(IMPORT_ITEMS))
        // ... 更多 where 定义
    .repeatablePiece("body", new String[][]{{"FIF", "RTR", "DAG", " Y "}},
        new Vec3i(0, 0, 1))
        .where('F', ElementUtility.ofBlock(frameState))
        // ... 更多 where 定义
        .repeatAxes(2)              // Z 轴
        .repeatRange(3, 15)         // 3~15 次重复
        .channelName("length")
    .piece("tail", new String[][]{{"FOF", "RTR", "DAG", " Y "}},
        new Vec3i(0, 0, 1))
        .where('O', ElementUtility.ofHatchAdder(EXPORT_ITEMS))
        // ... 更多 where 定义
    .build();
```
