# 结构元素系统设计文档 v2.2

> 相对 v1 的主要变更：
> 1. 新类从 18 个收敛到 **8 个公共类 + 9 个 impl + 3 个内部/扩展类**；
> 2. 新增显式模板/状态切分（`createState()` 工厂 + `StructureCheckState`）；
> 3. 新增"快速迁移"语法糖（扁平字符串 aisle、静态导入、单轴快捷、FactoryBlockPattern 兼容）；
> 4. 显式文档化"不支持嵌套可重复子区域"的限制；
> 5. **统一编译到 `MultiPiecePattern`**，让新旧机器走同一异步检查路径；
> 6. **新增 `RepeatGroupPiece`**：多轴 piece 的紧凑表示 + 内部 backtracking（每 `IStructurePiece` 至多生成 1 个 `StructurePiece`，**洁净室 7×11×7 从 541 piece 降到 3 piece，180× 内存优化**）；
> 7. **新增 `FormedStructureMetadata`**：每实例成型状态持久化（含各 piece 实际 repeat 数值 + 通道值）；
> 8. **TemplatePool 泛型化**（`registerGeneric` / `PooledReference<T>` / `PoolStats` 统一统计），**不新增桥接类**；
> 9. **编译时绑定 snapshot 能力**到每个 `StructurePiece` 闭包（含 `RepeatGroupPiece` 多轴闭包），`AsyncStructureChecker` 不直接接触 `MultiblockState`；
> 10. **不引用 `LazyTemplate`**（已弃用），改用 `SoftTemplate` / 泛型 `PooledReference<T>`。
> 11. **§5.6 张量积自动分派**（v2.2 增量）：编译期检测 base piece 是否为张量积，自动选 `SLIDING_1D` / `INDEPENDENT_1D` / `NESTED_BACKTRACKING` 三种搜索策略。**展开路径彻底删除**——所有 repeatable piece 统一为 1 个 `RepeatGroupPiece`；多轴张量积 cell 访问再加速 ~190×（独立 1D vs 嵌套 backtracking）。
> 12. **P0: `aisleRepeatable` API 弃用**（v2.2 立即生效，2.10 移除）：`FactoryBlockPattern.aisleRepeatable(...)` 加 `@Deprecated` + `@ApiStatus.ScheduledForRemoval(inVersion = "2.10")` + Javadoc 迁移指引。**不等 P2~P5 反馈**——v2.2 设计自身已具备充分弃用依据（旧机器 0 行为变化，IDE 警告引导 addon 作者走向新 API）。详见 §10。

---

## 1. 背景与目标

### 1.1 现状问题
- `aisleRepeatable` 仅支持单轴（Z 轴）重复。
- 无 `rowRepeatable` / `columnRepeatable` API。
- 洁净室用多个 `aisleRepeatable` 段模拟二维变化，语义不清。
- 固定模式网格使 L 形、T 形等复杂形状难以实现。

### 1.2 设计目标
1. **原生支持多轴重复**（1D / 2D / 3D）。
2. **统一路径**：所有机器（旧的 + 新的）最终都通过 `MultiPiecePattern` 进行结构检查与异步匹配。
3. **成型状态显式持久化**：`FormedStructureMetadata` 写入 NBT，可被异步检查、JEI、配方逻辑读取。
4. **零破坏性**：现有 100+ 多方块机器一行不改。
5. **复用 TemplatePool**：`StructureDefinition` 与 `BlockPatternTemplate` 共享同一套软引用缓存 + 统计体系。
6. **复用现有基础设施**：`AsyncStructureChecker` / `BlockStateSnapshot` / JEI 渲染 / 自动构建 不重写。

### 1.3 非目标
- **不支持嵌套可重复子区域**（一个 piece 内部不能再嵌可重复 piece）。99% 场景下扁平 pieces 足够；如未来需要，再升 v3。
- **不修改** `FactoryBlockPattern` / `TraceabilityPredicate` / `BlockPatternTemplate` / `MultiblockState` / `MultiPiecePattern` 任何已有 API 行为。
- **不要求**现有机器做迁移（迁移是 opt-in）。

---

## 2. 核心抽象（7 公共 + 9 impl + 3 扩展）

| 类别 | 名称 | 角色 |
|---|---|---|
| 公共接口 | `IStructureElement` | 单位置匹配规则 |
| 公共接口 | `IStructurePiece` | 命名片段（可重复属性挂这里） |
| 公共类 | `StructureDefinition` | 顶层结构定义 + Builder + 内化 PieceEntry |
| 公共类 | `StructureCheckState` | 运行期检查状态（含 `checkOnSnapshot` 异步路径） |
| 公共类 | `ElementUtility` + `Elements` | 静态工厂 + 短方法名（语法糖） |
| 公共类 | **`FormedStructureMetadata`** | **每实例成型状态：piece 名 → 该 piece 各轴实际 repeat 数 + 通道值** |
| 公共类 | **`StructureCompiler`** | **统一编译入口：StructureDefinition → MultiPiecePattern；编译时为每个 piece 绑定 snapshot 能力** |
| impl × 9 | `BlockElement` / `AirElement` / `AnyElement` / `SelfElement` / `HatchElement` / `TieredElement` / `ChainElement` / `WrapperElement` / `LegacyElement` | 内置元素 |
| 扩展现有 | `TemplatePool`（+ `registerGeneric`） | 泛型池 + 统一 `PoolStats` |
| 扩展现有 | `SoftTemplate`（内部用 `PooledReference<T>`） | 公共 API 不变；底层泛型化 |
| 扩展现有 | `StructurePiece`（+ `checkOnSnapshot` 闭包） | 编译时绑定 snapshot 能力 |
| 扩展现有 | `MultiblockState`（+ `checkOnSnapshotWithPrior` 重载） | 接受 `FormedStructureMetadata` 做快速验证 |
| 内部 | `PooledReference<T>`（package-private） | 泛型软引用 + 30s pin（`SoftTemplate` 内部委托） |
| 公共 | `SoftReferenceHolder<T>` | 泛型 `SoftTemplate` 等价物（用于非 `BlockPatternTemplate` 类型） |

> 净新增公共类 7 个（IStructureElement / IStructurePiece / StructureDefinition / StructureCheckState / FormedStructureMetadata / StructureCompiler / ElementUtility + Elements / SoftReferenceHolder），impl 9 个，扩展现有 4 个类，删除 0 个现有类。

---

## 3. 接口设计

### 3.1 `IStructureElement`

```java
package gregtech.api.pattern.element;

public interface IStructureElement {
    boolean check(World world, BlockPos pos, PatternMatchContext context);
    BlockInfo[] getCandidates();
    boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                       EntityPlayer player, boolean skipHatches);
    void spawnHint(World world, BlockPos pos);

    default int getMinGlobalCount() { return 0; }
    default int getMaxGlobalCount() { return -1; }
    default int getMinLayerCount()  { return 0; }
    default int getMaxLayerCount()  { return -1; }
    default boolean isCenter() { return false; }
    default void addTooltip(List<String> tooltip) {}

    /** 编译入口：转 TraceabilityPredicate（v1 §6.3 逻辑下沉到元素自身） */
    TraceabilityPredicate toPredicate();
}
```

### 3.2 `IStructurePiece`

```java
package gregtech.api.pattern.element;

public interface IStructurePiece {
    String getName();
    String[][] getPattern();
    Map<Character, IStructureElement> getSymbolMap();

    /** 空数组 = 固定 piece；非空 = 可沿这些轴重复 */
    int[] getRepeatAxes();
    int[][] getRepeatRanges();                    // 与 getRepeatAxes() 平行
    int[] getStepSizes();                         // 与 getRepeatAxes() 平行
    @Nullable String[] getRepeatChannelNames();    // 与 getRepeatAxes() 平行

    int[] getCenterOffset();                      // {x, y, z}
    default boolean isRepeatable() { return getRepeatAxes().length > 0; }
}
```

### 3.3 `StructureDefinition`（纯模板）

```java
package gregtech.api.pattern.element;

public final class StructureDefinition {
    private final RelativeDirection[] structureDir;
    private final List<PieceEntry> pieceEntries;   // PieceEntry 内化为私有静态类

    // 编译产物：使用 SoftReferenceHolder<MultiPiecePattern> 走 TemplatePool
    private final SoftReferenceHolder<MultiPiecePattern> compiledPattern;
    private final SoftReferenceHolder<BlockPos[]> maxRepeatAABB;
    private final boolean singlePiece;            // 编译优化判断

    private StructureDefinition(Builder b) {
        this.structureDir = new RelativeDirection[] { b.charDir, b.stringDir, b.aisleDir };
        this.pieceEntries = List.copyOf(b.pieceEntries);
        this.singlePiece = pieceEntries.size() == 1 && !pieceEntries.get(0).piece.isRepeatable();
        this.compiledPattern = TemplatePool.getInstance()
                .registerGeneric("sd:" + System.identityHashCode(this), this::doCompile);
        this.maxRepeatAABB = TemplatePool.getInstance()
                .registerGeneric("aabb:" + System.identityHashCode(this), this::doComputeAABB);
    }

    /** 工厂：从模板产生一份可丢弃的检查状态 */
    public StructureCheckState createState() { return new StructureCheckState(this); }

    /** 便捷：同步检查 */
    public boolean check(World world, BlockPos controllerPos,
                         EnumFacing front, EnumFacing up, boolean flipped,
                         PatternMatchContext context) {
        return createState().check(world, controllerPos, front, up, flipped, context).success;
    }

    public void autoBuild(World world, BlockPos controllerPos, EntityPlayer player,
                          @Nullable Map<String, Integer> channelValues, boolean skipHatches) { ... }
    public BlockInfo[][][] getPreview(@Nullable Map<String, Integer> channelValues) { ... }

    /** 编译产物 */
    public MultiPiecePattern getCompiledPattern() { return compiledPattern.get(); }

    /** 最大 repeat 范围的 AABB（用于异步检查快照） */
    public BlockPos[] computeWorldAABB(BlockPos center, EnumFacing front,
                                       EnumFacing up, boolean flipped, int margin) {
        BlockPos[] base = maxRepeatAABB.get();
        // 应用 facing / flipping / margin 变换
        ...
    }

    boolean isSinglePiece() { return singlePiece; }
    List<PieceEntry> getPieceEntries() { return pieceEntries; }

    public static Builder builder(RelativeDirection charDir,
                                  RelativeDirection stringDir,
                                  RelativeDirection aisleDir) {
        return new Builder(charDir, stringDir, aisleDir);
    }

    /** TemplatePool 统一缓存 + 统计的入口 */
    public static StructureDefinition getOrBuild(String key,
                                                  Supplier<StructureDefinition> factory) {
        return TemplatePool.getInstance().registerStructure(key, factory).get();
    }

    private MultiPiecePattern doCompile() { return StructureCompiler.compile(this); }
    private BlockPos[] doComputeAABB() { return StructureCompiler.computeMaxAABB(this); }

    // PieceEntry 作为私有静态类内化
    static final class PieceEntry {
        final IStructurePiece piece;
        final Vec3i baseOffset;
        final OffsetMode offsetMode;
        @Nullable final BooleanSupplier condition;
        PieceEntry(IStructurePiece piece, Vec3i baseOffset,
                   OffsetMode offsetMode, @Nullable BooleanSupplier condition) { ... }
    }
}
```

> **关键**：编译产物 (`compiledPattern` / `maxRepeatAABB`) 通过 `TemplatePool.registerGeneric(...)` 走泛型池，**不引入桥接类**。`StructureDefinition` 自身也通过 `getOrBuild(key, factory)` 注册到 `TemplatePool.registerStructure(...)`。

### 3.4 `StructureCheckState`（运行期状态）

```java
package gregtech.api.pattern.element;

public final class StructureCheckState {

    private final StructureDefinition definition;
    private final Long2ObjectMap<BlockInfo> positionCache = new Long2ObjectMap<>();
    @Nullable private BlockPos lastErrorPos;
    @Nullable private String lastErrorMessage;

    StructureCheckState(StructureDefinition definition) {
        this.definition = definition;
    }

    /** 检查结果 */
    public static final class Result {
        public final boolean success;
        @Nullable public final FormedStructureMetadata metadata;  // 成型时各 piece 实际 repeat 数
        @Nullable public final BlockPos errorPos;
        @Nullable public final String errorMessage;

        private Result(boolean success, @Nullable FormedStructureMetadata metadata,
                       @Nullable BlockPos errorPos, @Nullable String errorMessage) { ... }

        public static Result success(FormedStructureMetadata metadata) {
            return new Result(true, metadata, null, null);
        }
        public static Result failure(BlockPos pos, String msg) {
            return new Result(false, null, pos, msg);
        }
    }

    /** 同步：主线程检查 */
    public Result check(World world, BlockPos controllerPos,
                        EnumFacing front, EnumFacing up, boolean flipped,
                        PatternMatchContext context) { ... }

    @Nullable public BlockPos getLastErrorPos() { return lastErrorPos; }
    @Nullable public String getLastErrorMessage() { return lastErrorMessage; }
}
```

> 与 v1 的差异：`check()` 返回 `Result`（含 `FormedStructureMetadata`），让控制器拿到"成型时各 piece 的实际 repeat 数值"。

### 3.5 `ElementUtility` / `Elements`

```java
package gregtech.api.pattern.element;

public final class ElementUtility {
    private ElementUtility() {}

    public static IStructureElement ofBlock(IBlockState state);
    public static IStructureElement ofBlocks(IBlockState... states);
    public static IStructureElement ofAir();
    public static IStructureElement ofAny();
    public static IStructureElement ofChain(IStructureElement... elements);
    public static IStructureElement ofSelf(Class<? extends MetaTileEntity> clazz);
    public static IStructureElement ofHatchAdder(MultiblockAbility<?> ability);
    public static IStructureElement ofHatchAdder(MultiblockAbility<?> ability, int min, int max);
    public static IStructureElement ofTieredBlock(Supplier<BlockInfo[]> candidates, String channelName);
    public static IStructureElement lazy(Supplier<IStructureElement> supplier);
    public static IStructureElement onElementPass(Consumer<PatternMatchContext> cb, IStructureElement e);
    public static IStructureElement withChannel(String channelName, IStructureElement e);
}

public final class Elements {
    private Elements() {}

    public static IStructureElement block(IBlockState s);
    public static IStructureElement blocks(IBlockState... ss);
    public static IStructureElement air();
    public static IStructureElement any();
    public static IStructureElement self(Class<? extends MetaTileEntity> c);
    public static IStructureElement hatch(MultiblockAbility<?> a);
    public static IStructureElement hatch(MultiblockAbility<?> a, int min, int max);
    public static IStructureElement tiered(Supplier<BlockInfo[]> c, String channel);
    public static IStructureElement lazy(Supplier<IStructureElement> s);
    public static IStructureElement onPass(Consumer<PatternMatchContext> cb, IStructureElement e);
    public static IStructureElement withChannel(String channel, IStructureElement e);
    public static IStructureElement chain(IStructureElement... es);
}
```

### 3.6 `FormedStructureMetadata`（成型状态持久化）

> **v2.2 修订（从 3 map 折叠为 2 map）**：原设计 3 个 map（`pieceRepeats` /
> `pieceChannelNames` / `channelValues`），其中 `pieceChannelNames` 在
> `StructureCheckState.check()` 中**永远传入 null**，全代码库无
> `getPieceChannelNames` 的调用方。属于"设计时预留但实际不需要"的死代码，
> 删除后剩下的两个 map 真正承载成型态持久化。

```java
package gregtech.api.pattern.element;

public final class FormedStructureMetadata {

    /** piece 名 → 该 piece 沿各 repeat 轴的实际 repeat 数（空 = 固定 piece） */
    private final Map<String, int[]> pieceRepeats;

    /** 通道名 → 实际生效的 tier 值 */
    private final Map<String, Integer> channelValues;

    public FormedStructureMetadata(Map<String, int[]> pieceRepeats,
                                   Map<String, Integer> channelValues) {
        this.pieceRepeats = Map.copyOf(pieceRepeats);
        this.channelValues = Map.copyOf(channelValues);
    }

    public int getPieceRepeat(String pieceName, int axisIndex) {
        int[] reps = pieceRepeats.get(pieceName);
        if (reps == null || axisIndex >= reps.length) return 0;
        return reps[axisIndex];
    }

    public int[] getPieceRepeats(String pieceName) {
        return pieceRepeats.getOrDefault(pieceName, new int[0]);
    }

    public int getChannelValue(String channelName) {
        return channelValues.getOrDefault(channelName, 0);
    }

    public Map<String, Integer> getChannelValues() {
        return Collections.unmodifiableMap(channelValues);
    }

    /** NBT 序列化：仅两个 section — "PieceRepeats" + "ChannelValues" */
    public NBTTagCompound writeToNBT() { ... }
    public static FormedStructureMetadata readFromNBT(NBTTagCompound tag) { ... }

    /**
     * 从 check 结果构造。
     * @param pieceRepeats   piece 名 → 实际 repeat 数
     * @param channelValues  通道名 → 实际 tier 值
     */
    public static FormedStructureMetadata fromCheckResult(
            Map<String, int[]> pieceRepeats,
            Map<String, Integer> channelValues) { ... }
}
```

---

## 4. Builder API

### 4.1 完整模式

```java
StructureDefinition.builder(RIGHT, UP, BACK)
    .piece("floor", "SSS", "SSS", "SSS")
        .where('S', self(MetaTileEntityCleanroom.class))
    .repeatablePiece("wall",
            new String[][]{
                {"WWW","WWW","WWW"},
                {"WWW","WWW","WWW"},
                {"WWW","WWW","WWW"}},
            new Vec3i(0, 1, 0))
        .where('W', block(plasticState))
        .repeatAxes(0, 1, 2)
        .repeatRange(1, 7, 1, 11, 1, 7)
        .channelNames("width", "height", "depth")
    .piece("ceiling", "CCC", "CCC", "CCC")
        .where('C', block(plasticState))
    .build();
```

### 4.2 快速迁移模式（语法糖）

- **扁平字符串 aisle**（与旧 `FactoryBlockPattern.aisle(...)` 兼容）：
  ```java
  .piece("name", "XXX", "X#X", "XXX")
      .where('X', block(casingState))
      .where('#', air())
  ```

- **静态导入**（`Elements` 子类，方法名更短）：
  ```java
  import static gregtech.api.pattern.element.Elements.*;
  // ...
  .where('X', block(casingState))
  .where('H', hatch(IMPORT_ITEMS))
  ```

- **单轴快捷构造器**：
  ```java
  .repeatableY("layer", 1, 11, "height", "XXX", "X#X", "XXX")
      .where('X', block(casingState))
      .where('#', air())
  // 等价于：
  //   .repeatablePiece("layer", new Vec3i(0, 1, 0), "XXX", "X#X", "XXX")
  //       .repeatAxes(1).repeatRange(1, 11).channelName("height")
  // 同理：.repeatableX(...), .repeatableZ(...)
  ```

- **混合 FactoryBlockPattern 风格**（最大兼容）：
  ```java
  .pieceFromFactory("base", FactoryBlockPattern.start()
      .aisle("CCC", "C#C", "CCC")
      .where('C', ofBlock(casingState))
      .where('#', isAir()))
  ```

### 4.3 Builder 公共表面

```java
public static final class Builder {
    public PieceBuilder piece(String name, String[][] pattern, Vec3i offset);
    public PieceBuilder piece(String name, Vec3i offset, String... flatRows);     // 语法糖
    public PieceBuilder pieceFromFactory(String name, FactoryBlockPattern factory); // 语法糖
    public RepeatablePieceBuilder repeatablePiece(String name, String[][] pattern, Vec3i offset);
    public RepeatablePieceBuilder repeatablePiece(String name, Vec3i offset, String... flatRows);

    public RepeatablePieceBuilder repeatableX(String name, int min, int max,
                                               @Nullable String channel, String... flatRows);
    public RepeatablePieceBuilder repeatableY(String name, int min, int max,
                                               @Nullable String channel, String... flatRows);
    public RepeatablePieceBuilder repeatableZ(String name, int min, int max,
                                               @Nullable String channel, String... flatRows);

    public PieceBuilder conditionalPiece(String name, String[][] pattern,
                                          Vec3i offset, BooleanSupplier cond);

    public StructureDefinition build();
}
```

---

## 5. 编译策略：统一到 MultiPiecePattern

### 5.1 核心原则

**所有 `StructureDefinition`（单轴 / 多轴 / 混合）都编译成 `MultiPiecePattern`**。`AsyncStructureChecker` / `MultiblockWorldData` / JEI 走单一路径。

**关键约束**：**每个 `IStructurePiece` 至多生成 1 个 `StructurePiece`**。不预展开笛卡尔积。

| piece 类型 | v2.2 编译产物 |
|---|---|
| 固定 piece | 1 × `StructurePiece` |
| **任意 repeatable piece**（单轴 / 多轴 / 张量积 / 异形） | **1 × `RepeatGroupPiece`**（编译期按 base 形状自动选搜索策略，见 §5.6） |

**v2.2 设计彻底消除"展开"路径**——所有 repeatable piece 统一为 1 个 `RepeatGroupPiece`。编译期按 base 形状（张量积 vs 异形）和 axis 数选搜索策略：
- 单轴 → 1D 滑动窗口（O(max)，与旧 `aisleRepeatable` 等价）
- 多轴张量积 → **独立 1D**（O(Σmax_i)，~190× cell 访问加速 vs backtracking）
- 多轴异形 → 嵌套 backtracking（O(∏max_i)）

详见 §5.6 "张量积自动分派"。**单轴不再展开**——展开唯一目的是对齐旧 `aisleRepeatable` per-aisle 行为；但 v2.2 已隐式接受 uniform 简化，per-aisle 能力由 `FormedStructureMetadata` 替代，展开失去存在意义。

### 5.2 `StructureCompiler`

```java
package gregtech.api.pattern.element;

public final class StructureCompiler {
    private StructureCompiler() {}

    public static MultiPiecePattern compile(StructureDefinition def) {
        List<StructurePiece> pieces = new ArrayList<>();
        for (PieceEntry entry : def.getPieceEntries()) {
            BlockPatternTemplate tpl = compilePieceTemplate(entry.piece);
            IStructurePiece p = entry.piece;

            if (!p.isRepeatable()) {
                // 固定 piece：单 StructurePiece
                StructurePiece piece = new StructurePiece(p.getName(), tpl,
                        entry.baseOffset, entry.offsetMode, entry.condition);
                piece.bindSnapshotChecker(piece.getState()::checkOnSnapshotWithPrior);
                pieces.add(piece);

            } else {
                // 任意 repeatable piece（单轴 / 多轴 / 张量积 / 异形）：统一为 1 个 RepeatGroupPiece
                // 编译期按 base 形状 + axis 数选搜索策略（§5.6 张量积自动分派）
                boolean tensor = isTensorProduct(p);
                SearchStrategy strategy = pickStrategy(p, tensor);
                RepeatGroupPiece group = new RepeatGroupPiece(
                    p.getName(), tpl, entry.baseOffset, entry.offsetMode, entry.condition,
                    p.getRepeatAxes(), p.getRepeatRanges(), p.getStepSizes(),
                    p.getRepeatChannelNames(), p.getCenterOffset(), strategy);
                pieces.add(group);
            }
        }
        return new MultiPiecePattern(pieces);
    }

    /** 最大 repeat 范围的世界 AABB（异步检查快照用） */
    public static BlockPos[] computeMaxAABB(StructureDefinition def) {
        // 对每个 piece 取 max repeat 的世界边界
        // 多 piece 合并：取所有 piece AABB 的并集
        ...
    }

    private static BlockPatternTemplate compilePieceTemplate(IStructurePiece piece) {
        // 把 IStructureElement 图编译成 BlockPatternTemplate + TraceabilityPredicate
        // （元素 toPredicate() 方法的实现 + 旧 BlockPatternTemplate 构造逻辑）
        ...
    }
}
```

### 5.3 `RepeatGroupPiece`（新增：多轴 piece 的紧凑表示）

**设计核心：成型态 vs 建造态 的两态分派**

多轴搜索本质上只在**两种情况**下发生：

| 状态 | prior | 行为 | 复杂度 |
|---|---|---|---|
| **成型态**（已形成结构，玩家可能改了方块） | 不为 null | 单次验证 `tryCheckAtRepeats(priorReps)` | **O(1)** |
| **建造态**（玩家正在建造，结构未形成） | 为 null | 递归 backtracking | O(∏(max-min+1)) |
| **过渡态**（prior 失效，玩家破坏了结构） | 不为 null 但验证失败 | 回退到 backtracking | O(∏(max-min+1)) |

**关键观察**：
- 玩家建造多方块是**短暂事件**（几秒到几分钟），其余 99% 的时间结构是成型态或被拆除。
- 成型态用 `priorReps` 单次验证；建造态才走 backtracking。
- `RepeatGroupPiece` 的存在意义：**避免把 backtracking 时的笛卡尔积物理化为 539 个 StructurePiece**，让搜索发生在运行时而不是数据结构里。
- **搜索本身不消失**——只是从"编译期展开"变成"运行期按需 backtracking"。

```java
// 新增：gregtech.api.pattern.RepeatGroupPiece
package gregtech.api.pattern;

public class RepeatGroupPiece extends StructurePiece {
    private final int[] repeatAxes;
    private final int[][] repeatRanges;
    private final int[] stepSizes;
    @Nullable private final String[] repeatChannelNames;
    private final int[] centerOffset;
    /** 内层 BlockPatternTemplate 的单例 MultiblockState（不预展开） */
    private final MultiblockState innerState;
    /** 上次成功的 repeat 数（用于 prior 加速） */
    @Nullable private int[] lastFormedReps;

    public RepeatGroupPiece(String name, BlockPatternTemplate tpl, Vec3i offset,
                            OffsetMode mode, @Nullable BooleanSupplier cond,
                            int[] axes, int[][] ranges, int[] steps,
                            @Nullable String[] channelNames, int[] centerOffset) {
        super(name, tpl, offset, mode, cond);
        this.repeatAxes = axes;
        this.repeatRanges = ranges;
        this.stepSizes = steps;
        this.repeatChannelNames = channelNames;
        this.centerOffset = centerOffset;
        this.innerState = tpl.createState();
        // 关键：构造函数内绑定多轴 backtracking 闭包
        super.bindSnapshotChecker(this::checkOnSnapshotImpl);
    }

    /** 多轴 backtracking 入口（编译时绑定为闭包） */
    private boolean checkOnSnapshotImpl(IBlockAccess snap, BlockPos origin,
                                          EnumFacing front, EnumFacing up, boolean flipped,
                                          @Nullable FormedStructureMetadata prior) {
        int[] priorReps = (prior != null) ? prior.getPieceRepeats(getName()) : null;
        if (priorReps != null && priorReps.length == repeatAxes.length) {
            // 快速验证：prior 配置存在，单次扫描
            if (tryCheckAtRepeats(snap, origin, front, up, flipped, priorReps)) {
                this.lastFormedReps = priorReps.clone();
                return true;
            }
            // 失败则退化到全搜索（极端：玩家改了结构但 prior 还没更新）
        }
        // 全搜索：递归 backtracking（外轴优先贪婪）
        int[] reps = new int[repeatAxes.length];
        boolean ok = backtrackAxes(0, reps, snap, origin, front, up, flipped);
        if (ok) this.lastFormedReps = reps.clone();
        return ok;
    }

    private boolean backtrackAxes(int axisIdx, int[] currentReps,
                                    IBlockAccess snap, BlockPos origin,
                                    EnumFacing front, EnumFacing up, boolean flipped) {
        if (axisIdx == repeatAxes.length) {
            return tryCheckAtRepeats(snap, origin, front, up, flipped, currentReps);
        }
        int min = repeatRanges[axisIdx][0], max = repeatRanges[axisIdx][1];
        // 外轴优先贪婪：先试 max，再试较小值（最大结构更可能是玩家建造的）
        for (int r = max; r >= min; r--) {
            currentReps[axisIdx] = r;
            if (backtrackAxes(axisIdx + 1, currentReps, snap, origin, front, up, flipped)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryCheckAtRepeats(IBlockAccess snap, BlockPos origin,
                                        EnumFacing front, EnumFacing up, boolean flipped,
                                        int[] reps) {
        // 1. 计算 piece 中心在该 repeat 下的世界偏移
        BlockPos pieceOrigin = computePieceOrigin(origin, reps);
        // 2. 复用 MultiblockState 单次 snapshot 检查
        PatternMatchContext ctx = innerState.checkPatternFastAtSnapshot(
            snap, pieceOrigin, front, up, flipped);
        if (ctx == null) return false;
        // 3. 收集 positions（用于 getAllPositions()）
        LongSet newPositions = new LongOpenHashSet(innerState.cache.keySet());
        shiftPositions(newPositions, pieceOrigin.subtract(origin));
        super.swapPositions(newPositions);
        return true;
    }

    private BlockPos computePieceOrigin(BlockPos controllerOrigin, int[] reps) {
        int dx = 0, dy = 0, dz = 0;
        for (int i = 0; i < repeatAxes.length; i++) {
            int axis = repeatAxes[i];
            int step = stepSizes[i] * (reps[i] - 1);  // 0-indexed from piece base
            if (axis == 0) dx += step;
            else if (axis == 1) dy += step;
            else dz += step;
        }
        return controllerOrigin.add(super.getOffset().getX() + dx,
                                     super.getOffset().getY() + dy,
                                     super.getOffset().getZ() + dz);
    }

    @Override
    public void cacheFormedReps(int[] reps) { this.lastFormedReps = reps; }

    @Override @Nullable
    public int[] getLastFormedReps() { return lastFormedReps; }
}
```

**`StructurePiece` 需新增的虚方法**（不破坏现有 API）：

```java
// 在 StructurePiece.java 中新增（默认实现为 no-op）
public void cacheFormedReps(int[] reps) { /* no-op for flat pieces */ }

@Nullable
public int[] getLastFormedReps() { return null; }
```

### 5.4 内存与性能对比

#### 5.4.1 内存与编译期

| 场景 | v2.2 展开版 | v2.2 + §5.6（张量积分派） | 收益 |
|---|---|---|---|
| 蒸馏塔（Y 1~11） | 13 piece + 13 state | **2 piece + 2 state**（top + 1 RepeatGroupPiece） | **6.5×** |
| 装配线（Z 3~15） | 15 + 15 | **3 + 3**（head + 1 RepeatGroupPiece + tail） | **5×** |
| 洁净室（7×11×7） | 541 + 541 | 3 + 3 | **180×**（不变） |
| 100 个洁净室世界 | 54,100 对象 | 300 对象 | **180×** |
| 编译耗时 | 539 × `new StructurePiece` ~5ms | 1 × `new RepeatGroupPiece` ~0.05ms | 100× |
| `getAllPositions()` 遍历 | 539 次（其中 538 次空集） | 3 次（每次有真实数据） | 180× |
| **多轴张量积 cell 访问**（7×11×7） | backtracking 539 × 27 cells = **14,553** | **独立 1D** 7+11+7 = 25 次 1D 扫描 | **~580×** |
| **单轴张量积 cell 访问**（Y 1~11, base 3×3） | 11 × 9 = 99 | 单 1D 滑动 11 × 3 = 33 | **3×** |

单 piece 内存：~360 bytes × 539 = ~194 KB（v2.2 展开版）→ 360 bytes × 1 = 360 bytes（v2.2 + §5.6 独立 1D，**540× 下降**）。单轴 piece 同样获益：蒸馏塔 13 piece × 360 = ~4.7 KB（展开）→ 1 piece × 360 = 360 bytes（**13× 下降**）。

#### 5.4.2 运行期（按检查路径分）

异步检查 (`AsyncStructureChecker`) 只接收 unformed controllers，所以 prior 在异步路径上**几乎不命中**。prior 主要受益者是成型态的 event-driven recheck。

| 检查路径 | prior | 频率 | v2.2 旧展开版（每 IStructurePiece 多个 piece）单次 check | v2.2 + §5.6（每 IStructurePiece 恒 1 piece）单次 check | 收益 |
|---|---|---|---|---|---|
| **异步检查**（unformed，建造中） | 罕见（破坏后重建） | 服务器繁忙时高 | 539 × 闭包调用 | 1 × 闭包调用 + 内部独立 1D（张量积）/ backtracking（异形） | **540× 闭包调用** + **~190× cell 访问** |
| **Event-driven recheck**（formed，单块变化） | 几乎都命中 | 成型机器多 | 539 × 闭包调用（hit 后停止）| 1 × 闭包，O(1) 验证 | **540×** |
| **首 tick 同步 check**（`isFirstTick()`） | null | 每实例一次 | 539 × 闭包 | 1 × 闭包 + 独立 1D / backtracking | **540× 闭包调用** |
| **挂机期**（全部成型） | N/A（不走异步） | 高 | 0（不检查） | 0 | 等价 |

**真实热路径**：
- 异步路径上，prior 几乎不命中；`RepeatGroupPiece` 的价值是**减少 540× 闭包调用**（不是消除 backtracking）。
- Event-driven 路径上，prior 几乎都命中；`RepeatGroupPiece` 让验证成为 O(1)。
- **Memory 180× 下降是稳态收益；Speed 540× 是闭包调用层面，不是 backtracking 内部**。

#### 5.4.3 为什么 backtracking 仍然可接受

虽然异步检查要走 backtracking，但实际成本被四个因素压制：

1. **早终止**：`backtrackAxes` 一旦某切片失败立即停止，无需扫完 539 组合。常见场景下扫描 ≤ 50 次。
2. **外轴优先贪婪**：先试 max，玩家最可能建造的就是最大尺寸。一旦成型即停止。
3. **无变化时跳过**：连续两次 check 之间若无方块变化，玩家观察 0.5s → async 4Hz 期间 2 次 check 各扫 ~0 次（早终止）。
4. **Snapshot 廉价**：每个 cell 检查是 `IBlockAccess.getBlockState(pos)`，O(1) 数组索引，~50ns/次。539 次扫描 = 27μs，不到一帧（50ms）的 0.1%。

净结果：异步线程在 100 个建造中洁净室的世界里，backtracking 总耗时 < 5ms/秒。单线程 250ms 周期检查器完全无压力。

#### 5.4.4 异步检查（per world, per second）

| 路径 | v2.2 旧展开版 | v2.2 + §5.6 |
|---|---|---|
| 250ms 周期检查（unformed 建造中） | 539 × `checkOnSnapshot` 闭包 | 1 × `checkOnSnapshot` 闭包（内部独立 1D / backtracking） |
| 100 个成型洁净室 / 秒 | 53,900 闭包调用/秒 | 100 闭包调用/秒 |

#### 5.4.5 `priorMetadata` 加速（成型态的桥梁，不是建造态的）

**再次强调**：异步检查路径上 prior 几乎不命中（unformed controller 的 formedMetadata 通常为 null）。prior 加速主要受益于：
- **Event-driven recheck**（`MultiblockControllerBase.doStructureCheck` 第 443-460 行的 `worldData.hasPendingRecheck` 分支）：成型态，单块变化 → prior 几乎都命中 → O(1)
- **重建过渡**：玩家破坏了成型结构，prior 仍存 → 重建时 prior 验证通过 → O(1)

`RepeatGroupPiece` 在这两类路径上是 O(1)；在异步路径上仍是 backtracking（但闭包调用从 539 降到 1）。

**结论**：
- **Memory 180× 是稳态收益**（每个多轴 piece 节省 539 个 `StructurePiece` + 539 个 `MultiblockState` + 539 个空 `positions`）。
- **Speed 540× 闭包调用**（每次 check 只调 1 个闭包而不是 539 个；闭包内部 backtracking 复杂度不变）。
- **prior 加速**适用于成型态的 event-driven recheck 和破坏后重建，对纯异步路径（首次建造）帮助有限。
- **backtracking 仍是最坏 O(∏(max-min+1))**，但 5ms/秒 的成本（100 个建造中洁净室场景）可接受；如果未来需要进一步优化，可加**脏区增量检查**（只验证变化的 slice）。

### 5.5 ~~为什么不把单轴也合并？~~（问题本身已过时，由 §5.6 解决）

> **本节已废弃**。

v2.2 设计中，`aisleRepeatable`（v1 旧 API）**只服务于旧机器**——新机器完全不经过它：
- **新机器**：`createStructureDefinition()` → `StructureDefinition` → `StructureCompiler` → `MultiPiecePattern`（全新代码路径，**不接触** `aisleRepeatable`）
- **旧机器**：`createStructureTemplate()` → `FactoryBlockPattern.aisleRepeatable(...)` → `BlockPatternTemplate`（保留 `aisleRepeatable` API，**不修改**）

原 §5.5 担心的"per-aisle 滑动窗口耦合"、"formedRepetitionCount 细粒度访问"、"getAllPositions() 改动"是**假问题**——这些都是旧 `aisleRepeatable` 路径的内部细节。新机器走的是完全独立的 `MultiPiecePattern` + `RepeatGroupPiece` 路径，跟旧路径**没有共享的内部数据结构**。所谓的"对齐 `aisleRepeatable` 行为"是过度的兼容 shim——新代码不需要和旧代码 1:1 行为对齐，只要对外语义（玩家能造出来 + 异步检查 + JEI 渲染）正确即可。

**结论**：v2.2 初始设计里"单轴展开为 max 个 StructurePiece"是过度的兼容 shim。§5.6 "张量积自动分派"明确删除这条 shim；`aisleRepeatable` API 在 §10 P0 **立即弃用**（@Deprecated + 2.10 移除），引导新代码一律走新 API。

#### 演进路径

| 版本 | 新机器单轴实现 | 展开？ | 旧 `aisleRepeatable` 路径 |
|---|---|---|---|
| v2.2 初始 | uniform repeat，max 个 StructurePiece | ✅ 展开（过度的兼容 shim） | 旧机器保留（不动） |
| **v2.2 + §5.6** | uniform repeat，1 个 RepeatGroupPiece + `SLIDING_1D` | ❌ 不展开 | 旧机器保留（不动） |

> `aisleRepeatable` API 在 v2.2 中**未被删除**（保留作为 100+ 旧机器的兼容入口），新代码一律走 `repeatableX/Y/Z` uniform API + `RepeatGroupPiece`。两个路径并存且互不干扰——这才是 v2.2 "零破坏性" + "新语法+旧后端" 的真正含义。

### 5.6 张量积自动分派：彻底消除"展开"路径

#### 5.6.1 核心洞察

v2.2 的语义已经统一为 **uniform**（piece 内整条 axis 共享 1 个 repeat 数）。uniform 语义下，**展开**（单轴 → max 个 `StructurePiece`）不再是必要的实现路径——它只是过去为了"对齐旧 `aisleRepeatable` per-aisle 行为"而做的兼容 shim。

**§5.6 的核心改动**：所有 repeatable piece 统一为 1 个 `RepeatGroupPiece`，**编译期根据 base piece 形状自动选搜索策略**：

| base 形状 | axis 数 | 编译期策略 | 运行时算法 | cell 访问复杂度 |
|---|---|---|---|---|
| 张量积（base 内 cell 全同） | 1 | `SLIDING_1D` | 单 1D 滑动窗口 | O(max) |
| 张量积 | ≥ 2 | `INDEPENDENT_1D` | **独立 1D**（每 axis 独立搜） | O(Σmax_i) |
| 异形 base | 1 | `SLIDING_1D` | 单 1D 滑动窗口 | O(max) |
| 异形 base | ≥ 2 | `NESTED_BACKTRACKING` | 嵌套 backtracking | O(∏max_i) |

**关键观察**：
- **张量积 + 多轴** = **独立 1D**（用户的提议，~190× cell 访问加速 vs backtracking）
- **单轴**（无论张量/异形）= 都是单 1D 滑动窗口
- **异形 + 多轴** = 唯一需要 backtracking 的场景

#### 5.6.2 张量积自动检测

`StructureCompiler.compilePiece` 编译 piece 时调用：

```java
private static boolean isTensorProduct(IStructurePiece piece) {
    String[][] pattern = piece.getPattern();
    if (pattern.length == 0) return true;
    String first = pattern[0];
    if (first.length() != 1) return false;
    char marker = first.charAt(0);
    // 字符层：base 内所有 cell 是同一 marker
    for (String[] row : pattern) {
        for (String cell : row) {
            if (cell.length() != 1 || cell.charAt(0) != marker) return false;
        }
    }
    // 元素层：where(marker, ...) 把 marker 映射到同一 IStructureElement
    // （candidates 集合一致即视为同一元素）
    IStructureElement elem = piece.getSymbolMap().get(String.valueOf(marker));
    return elem != null && elem.getCandidates().length <= 1;
}
```

字符层全同 + 元素层单 candidate 集合 → 张量积。

#### 5.6.3 简化的编译表（替换 §5.1 旧表）

| piece 类型 | v2.2 + §5.6 编译产物 |
|---|---|
| 固定 piece | 1 × `StructurePiece` |
| repeatable piece（**任意** base 形状 + 任意轴数） | **1 × `RepeatGroupPiece`**（编译期绑定搜索策略：张量 1D / 独立 1D / backtracking） |

**展开路径彻底消失**。每 `IStructurePiece` 恒等于 1 个 `StructurePiece` / `RepeatGroupPiece`。

#### 5.6.4 策略分派代码

```java
// StructureCompiler 编译时绑定策略
public enum SearchStrategy {
    SLIDING_1D,             // 单 1D 滑动窗口（单轴）
    INDEPENDENT_1D,         // 独立 1D（多轴张量积）
    NESTED_BACKTRACKING     // 嵌套 backtracking（多轴异形）
}

private static SearchStrategy pickStrategy(IStructurePiece p, boolean isTensor) {
    if (p.getRepeatAxes().length == 1) return SearchStrategy.SLIDING_1D;
    return isTensor ? SearchStrategy.INDEPENDENT_1D : SearchStrategy.NESTED_BACKTRACKING;
}
```

`RepeatGroupPiece` 构造时接收 `SearchStrategy`，运行时按策略分派：

```java
public class RepeatGroupPiece extends StructurePiece {
    private final SearchStrategy strategy;
    // ...existing fields...

    private boolean checkOnSnapshotImpl(IBlockAccess snap, BlockPos origin,
                                          EnumFacing front, EnumFacing up, boolean flipped,
                                          @Nullable FormedStructureMetadata prior) {
        int[] priorReps = (prior != null) ? prior.getPieceRepeats(getName()) : null;

        // 成型态：O(1) 验证（所有策略共用）
        if (priorReps != null && priorReps.length == repeatAxes.length) {
            if (tryCheckAtRepeats(snap, origin, front, up, flipped, priorReps)) {
                this.lastFormedReps = priorReps.clone();
                return true;
            }
            // 失败退化到全搜索（prior 失效）
        }

        // 建造态：按策略搜
        boolean ok;
        switch (strategy) {
            case SLIDING_1D:
                ok = searchSliding1D(snap, origin, front, up, flipped);
                break;
            case INDEPENDENT_1D:
                ok = searchIndependent1D(snap, origin, front, up, flipped);
                break;
            case NESTED_BACKTRACKING:
            default:
                ok = backtrackAxes(0, new int[repeatAxes.length], snap, origin, front, up, flipped);
                break;
        }
        if (ok) this.lastFormedReps = ...;  // 记录本次成功 reps
        return ok;
    }

    /** 单 1D 滑动窗口（单轴；等价于旧 aisleRepeatable 滑动窗口算法） */
    private boolean searchSliding1D(IBlockAccess snap, BlockPos origin,
                                      EnumFacing front, EnumFacing up, boolean flipped) {
        int min = repeatRanges[0][0], max = repeatRanges[0][1];
        int[] reps = new int[]{min};  // 长度 1（单轴）
        for (int r = max; r >= min; r--) {
            reps[0] = r;
            if (tryCheckAtRepeats(snap, origin, front, up, flipped, reps)) return true;
        }
        return false;
    }

    /** 独立 1D（多轴张量积；每个 axis 独立搜，命中即停） */
    private boolean searchIndependent1D(IBlockAccess snap, BlockPos origin,
                                          EnumFacing front, EnumFacing up, boolean flipped) {
        int[] reps = new int[repeatAxes.length];
        for (int i = 0; i < repeatAxes.length; i++) {
            reps[i] = searchAxisGreedy(snap, origin, i, reps, front, up, flipped);
            if (reps[i] < 0) return false;  // 任一 axis 失败 = 整 piece 失败
        }
        // 末尾联合验证（双保险，处理 axis 边界处的 cell 错配）
        return tryCheckAtRepeats(snap, origin, front, up, flipped, reps);
    }

    private int searchAxisGreedy(IBlockAccess snap, BlockPos origin, int axisIdx,
                                   int[] partialReps, EnumFacing front, EnumFacing up, boolean flipped) {
        int min = repeatRanges[axisIdx][0], max = repeatRanges[axisIdx][1];
        for (int r = max; r >= min; r--) {
            partialReps[axisIdx] = r;
            if (tryCheckAxisLine(snap, origin, axisIdx, partialReps, front, up, flipped)) {
                return r;  // 命中即停（外轴优先贪婪）
            }
        }
        return -1;
    }

    /** 沿 axisIdx 方向的 1D 切片验证（张量积下独立于其他 axis） */
    private boolean tryCheckAxisLine(IBlockAccess snap, BlockPos origin, int axisIdx,
                                       int[] partialReps, EnumFacing front, EnumFacing up, boolean flipped) {
        // 1. 计算当前 partialReps 下的 piece origin
        BlockPos pieceOrigin = computePieceOrigin(origin, partialReps);
        // 2. 只沿 axisIdx 方向做 1D 切片扫描（不动其他 axis）
        //    张量积 base：所有 cell 同符号，1D 切片扫描即可独立判定
        return innerState.checkAxisLineFastAtSnapshot(
            snap, pieceOrigin, repeatAxes[axisIdx], front, up, flipped);
    }

    /** 嵌套 backtracking（多轴异形；§5.3 现有逻辑，等价多轴 backtracking） */
    private boolean backtrackAxes(int axisIdx, int[] currentReps, IBlockAccess snap,
                                    BlockPos origin, EnumFacing front, EnumFacing up, boolean flipped) {
        if (axisIdx == repeatAxes.length) {
            return tryCheckAtRepeats(snap, origin, front, up, flipped, currentReps);
        }
        int min = repeatRanges[axisIdx][0], max = repeatRanges[axisIdx][1];
        for (int r = max; r >= min; r--) {
            currentReps[axisIdx] = r;
            if (backtrackAxes(axisIdx + 1, currentReps, snap, origin, front, up, flipped)) {
                return true;
            }
        }
        return false;
    }
}
```

**`MultiblockState` 新增**：
```java
/**
 * 沿 axisIdx 方向的 1D 切片验证（张量积 piece 专用）。
 * 不扫整个 base piece，只验证沿 axisIdx 方向的那一"线" cell。
 */
public boolean checkAxisLineFastAtSnapshot(
        IBlockAccess snap, BlockPos pieceOrigin, int axisIdx,
        EnumFacing front, EnumFacing up, boolean flipped) {
    // 1. 沿 axisIdx 方向枚举 base piece 在该轴上的所有 cell
    // 2. 每个 cell 检查 IBlockAccess.getBlockState(pos) 是否匹配 IStructureElement
    // 3. 张量积下：所有 cell 同符号，1D 切片 ≈ 9 cells
    // 4. 任一 cell 不匹配 → false
    ...
}
```

#### 5.6.5 内存与性能对比（更新版）

| 场景 | v2.2 展开版 | v2.2 + §5.6（张量积分派） | 收益 |
|---|---|---|---|
| 蒸馏塔（Y 1~11） | 13 piece + 13 state | **2 piece**（top + 1 RepeatGroupPiece）+ 2 state | **6.5×** |
| 装配线（Z 3~15） | 15 + 15 | **3**（head + 1 RepeatGroupPiece + tail） | **5×** |
| 洁净室（7×11×7） | 541 + 541 | 3 + 3 | **180×**（不变） |
| 100 个洁净室世界 | 54,100 对象 | 300 对象 | **180×** |
| 多轴张量积 cell 访问（7×11×7） | backtracking 539 × 27 = **14,553** | 独立 1D 7+11+7 = 25 × 3 | **~190×** |
| 单轴张量积 cell 访问（Y 1~11, base 3×3） | 11 × 9 = 99 | 11 × 3 = 33 | **3×** |

#### 5.6.6 改动量（叠加在 v2.2 既有改动上）

| 位置 | 改动 |
|---|---|
| `StructureCompiler` | + `isTensorProduct(piece)` + `pickStrategy`（~15 行）；**删除**原"单轴展开"for 循环（-13 行） |
| `RepeatGroupPiece` | + `SearchStrategy` 字段 + 3 个 search 方法 + switch 分派（~+60 行） |
| `MultiblockState` | + `checkAxisLineFastAtSnapshot` 1D 切片验证（~+15 行） |
| 公共 API | **0 改动** |
| 编译产物 | -1 路径（删除单轴展开分支） |

**净改动**：~+90 行 / -13 行 = **+77 行**；**展开路径消失**。

#### 5.6.7 收益总结

- **展开路径彻底删除**：单轴、多轴统一为 `RepeatGroupPiece` 1 件
- **每 `IStructurePiece` 恒等于 1 个 piece**：内存简化、闭包调用简化、`getAllPositions()` 简化
- **多轴张量积再加速 ~190×**：独立 1D 替换 backtracking
- **单轴张量积再加速 3×**：1D 切片验证替换完整 base 验证
- **零 API 改动**：编译期自动检测 + 自动分派
- **零破坏性**：所有现有 v2.2 行为（per-axis 验证、prior 加速、成型态 O(1)）保留

---

## 6. FormedStructureMetadata 集成

### 6.1 动机

旧系统：
- `MultiblockState.formedRepetitionCount`（`MultiblockState.java:78/85/320`）记录每条 aisle 实际 repeat 数。
- **不写入 NBT**——只在内存中存在；reload 时通过重新跑结构检查重新发现。
- **无 getter 暴露**。

新系统需要：
1. **可被外部读取**：玩家 / JEI / 配方需要知道"这个洁净室是 5×7×3"。
2. **可被 NBT 序列化**：保存后重载应能恢复。
3. **可被 async checker 读取**：复用上次"配置"作为快速验证起点（加速 10×~100×）。

### 6.2 控制器接入

```java
// MultiblockControllerBase 新增字段
@Nullable
private FormedStructureMetadata formedMetadata;

// 在 checkStructurePattern() 成功后：
if (result.success && result.metadata != null) {
    this.formedMetadata = result.metadata;
    this.structureFormed = true;
    formStructure(context);
}

// 新增 getter
@Nullable
public FormedStructureMetadata getFormedMetadata() { return formedMetadata; }

// NBT 持久化（修改 writeToNBT / readFromNBT）
@Override
public NBTTagCompound writeToNBT(NBTTagCompound data) {
    super.writeToNBT(data);
    // ... 现有字段
    if (formedMetadata != null) {
        data.setTag("FormedMetadata", formedMetadata.writeToNBT());
    }
    return data;
}

@Override
public void readFromNBT(NBTTagCompound data) {
    super.readFromNBT(data);
    // ... 现有字段
    if (data.hasKey("FormedMetadata")) {
        this.formedMetadata = FormedStructureMetadata.readFromNBT(
            data.getCompoundTag("FormedMetadata"));
    }
    this.reinitializeStructurePattern();
}
```

### 6.3 异步检查时使用

```java
// AsyncStructureChecker 收到 SnapshotTask 后：
private boolean performAsyncCheck(SnapshotTask task) {
    MultiblockControllerBase c = task.controller;
    MultiPiecePattern pattern = c.getMultiPiecePattern();
    if (pattern == null) return false;

    FormedStructureMetadata prior = c.getFormedMetadata();
    IBlockAccess snap = task.snapshot;
    BlockPos origin = task.centerPos;
    EnumFacing front = task.frontFacing;
    EnumFacing up = task.upwardsFacing;
    boolean flipped = task.allowsFlip;

    for (StructurePiece piece : pattern.getPieces()) {
        if (piece.isConditional() && !piece.isActive()) continue;
        if (!piece.checkOnSnapshot(snap, origin, front, up, flipped, prior)) {
            return false;
        }
    }
    return true;
}
```

> 加速原理：每个 piece 调 `state.checkOnSnapshotWithPrior(snap, origin, ..., prior)`。`prior` 含该 piece 上次的 `pieceRepeats`，`MultiblockState` 内部把"上次配置"作为初始猜测，单次扫描即完成验证；如果失败才退化到从 min 开始全搜索。

---

## 7. TemplatePool 集成（泛型化 + PoolStats 统一）

### 7.1 现状回顾

- `TemplatePool.getInstance().register(key, Supplier<BlockPatternTemplate>)` —— 现有 API。
- `SoftTemplate` —— 软引用持有 + 30 秒 pin + recreation 计数。
- `MultiblockControllerBase.java:295` 文档化推荐用法。
- `LazyTemplate` —— **已弃用**（`LazyTemplate.java:15, 33`），v2.2 不引用。

### 7.2 v2.2 改动

**不新增桥接类**。直接在 `TemplatePool` 上加泛型重载，并在 `SoftTemplate` 内部用 `PooledReference<T>` 泛型化（公共 API 不变）。

#### 7.2.1 新增 `PooledReference<T>`（内部泛型类）

```java
// 新增：gregtech.api.pattern.internal.PooledReference（package-private）
package gregtech.api.pattern.internal;

final class PooledReference<T> {
    private final Supplier<T> factory;
    private volatile SoftReference<T> softRef;
    private volatile T pin;
    private volatile long pinTimestampNanos;
    private final AtomicInteger recreationCount = new AtomicInteger();
    private static final long MIN_PIN_DURATION_NS = 30_000_000_000L; // 30s

    PooledReference(Supplier<T> factory) { this.factory = factory; }

    T get() {
        // 与 SoftTemplate.java:95-142 相同的双检锁 + 30s pin 逻辑
        T pinned = pin;
        if (pinned != null) {
            if (System.nanoTime() - pinTimestampNanos >= MIN_PIN_DURATION_NS) pin = null;
            return pinned;
        }
        SoftReference<T> ref = softRef;
        T result = (ref != null) ? ref.get() : null;
        if (result != null) return result;
        synchronized (this) {
            // double-check
            pinned = pin;
            if (pinned != null) return pinned;
            ref = softRef;
            result = (ref != null) ? ref.get() : null;
            if (result != null) return result;
            result = factory.get();
            softRef = new SoftReference<>(result);
            pin = result;
            pinTimestampNanos = System.nanoTime();
            recreationCount.incrementAndGet();
            TemplatePool.onHolderRecreated();
            return result;
        }
    }

    void invalidate() { pin = null; softRef = null; }
    boolean isLoaded() { if (pin != null) return true; SoftReference<T> r = softRef; return r != null && r.get() != null; }
    int getRecreationCount() { int c = recreationCount.get(); return c > 0 ? c - 1 : 0; }
}
```

#### 7.2.2 `SoftTemplate` 内部重构（公共 API 不变）

```java
// 现有 SoftTemplate：public API 完全保留
public final class SoftTemplate {
    private final PooledReference<BlockPatternTemplate> ref;

    private SoftTemplate(Supplier<BlockPatternTemplate> factory) {
        this.ref = new PooledReference<>(factory);
    }

    public static SoftTemplate of(Supplier<BlockPatternTemplate> factory) {
        return new SoftTemplate(factory);
    }

    public BlockPatternTemplate get() { return ref.get(); }
    public void invalidate() { ref.invalidate(); }
    public boolean isLoaded() { return ref.isLoaded(); }
    public int getRecreationCount() { return ref.getRecreationCount(); }
    public int getCreationCount() { return ref.getRecreationCount() + 1; }
}
```

> **零破坏性**：所有现有 `SoftTemplate.of(...)` / `template.get()` 调用照旧。

#### 7.2.3 新增 `SoftReferenceHolder<T>`（泛型等价物）

```java
// 新增：gregtech.api.pattern.SoftReferenceHolder
public final class SoftReferenceHolder<T> {
    private final PooledReference<T> ref;

    private SoftReferenceHolder(Supplier<T> factory) {
        this.ref = new PooledReference<>(factory);
    }

    public static <T> SoftReferenceHolder<T> of(Supplier<T> factory) {
        return new SoftReferenceHolder<>(factory);
    }

    public T get() { return ref.get(); }
    public void invalidate() { ref.invalidate(); }
    public boolean isLoaded() { return ref.isLoaded(); }
    public int getRecreationCount() { return ref.getRecreationCount(); }
}
```

#### 7.2.4 `TemplatePool` 扩展

```java
// 现有 TemplatePool：public API 保留 + 新增方法
public final class TemplatePool {

    // 现有
    private final ConcurrentHashMap<String, SoftTemplate> pool = new ConcurrentHashMap<>();

    // 新增：泛型池
    private final ConcurrentHashMap<String, SoftReferenceHolder<?>> genericPool = new ConcurrentHashMap<>();

    // 现有 onTemplateRecreated 改为包级调用（实现委托给 onHolderRecreated）
    static void onTemplateRecreated() { onHolderRecreated(); }

    // 新增：泛型重建通知
    static void onHolderRecreated() {
        INSTANCE.totalRecreations.incrementAndGet();
        if (ConfigHolder.machines.debugStructureCheck) {
            GTLog.logger.debug("[TemplatePool] A pooled reference was recreated after GC reclaim. " +
                    "Total recreations: {}", INSTANCE.totalRecreations.get());
        }
    }

    // 现有 register 保留
    public SoftTemplate register(String key, Supplier<BlockPatternTemplate> factory) {
        return pool.computeIfAbsent(key, k -> {
            totalRegistrations.incrementAndGet();
            return SoftTemplate.of(factory);
        });
    }

    // 新增：泛型 register
    public <T> SoftReferenceHolder<T> registerGeneric(String key, Supplier<T> factory) {
        SoftReferenceHolder<T> h = (SoftReferenceHolder<T>) genericPool.computeIfAbsent(key, k -> {
            totalRegistrations.incrementAndGet();
            return SoftReferenceHolder.of(factory);
        });
        return h;
    }

    // 新增：便捷方法（结构系统专用）
    public SoftReferenceHolder<StructureDefinition> registerStructure(
            String key, Supplier<StructureDefinition> factory) {
        return registerGeneric(key, factory);
    }

    // 现有 getStats 扩展为同时统计泛型池
    public PoolStats getStats() {
        int loaded = 0;
        int total = pool.size() + genericPool.size();
        long perTemplateRecreations = 0;
        for (SoftTemplate st : pool.values()) {
            if (st.isLoaded()) loaded++;
            perTemplateRecreations += st.getRecreationCount();
        }
        for (SoftReferenceHolder<?> h : genericPool.values()) {
            if (h.isLoaded()) loaded++;
            perTemplateRecreations += h.getRecreationCount();
        }
        return new PoolStats(total, loaded, totalRegistrations.get(),
                totalRecreations.get(), perTemplateRecreations);
    }

    // 现有 evictAll / evict / clearWorld 等也扩展为同时清理 genericPool
}
```

> **效果**：`TemplatePool.getStats()` 同时统计 `BlockPatternTemplate`（现有）和 `StructureDefinition` + 编译产物（新增），统一在 `PoolStats` 里输出。运维/调试统一面板。

### 7.3 编译产物的注册

`StructureDefinition` 构造时通过 `TemplatePool.registerGeneric(...)` 把编译产物注册到泛型池：

```java
private StructureDefinition(Builder b) {
    // ...
    this.compiledPattern = TemplatePool.getInstance()
            .registerGeneric("sd-compiled:" + keyHint, this::doCompile);
    this.maxRepeatAABB = TemplatePool.getInstance()
            .registerGeneric("sd-aabb:" + keyHint, this::doComputeAABB);
}
```

> 注意：`keyHint` 取 `StructureDefinition.getOrBuild(key, factory)` 时传入的 `key`，保证同一机器共享编译产物。

---

## 8. 异步检查：统一路径

### 8.1 现状

- `AsyncStructureChecker`（400 行）已完整。
- `BlockPatternTemplate.computeWorldAABB` + `MultiblockState.checkPatternFastAtSnapshot` 走 snapshot 路径。
- 已有 main-thread fallback / 大 AABB 跳过 / 250ms 间隔等保护。

### 8.2 v2.2 改动

**新机器的 controller 在 `getMultiPiecePattern()` 时返回 `definition.getCompiledPattern()`**。`AsyncStructureChecker` 走统一的"取 `MultiPiecePattern` → 逐 piece 走 snapshot 检查"路径。

#### 8.2.1 `StructurePiece` 新增 snapshot 能力

```java
// 修改 StructurePiece.java：新增字段和方法
public class StructurePiece {
    // ... 现有字段 ...
    private final MultiblockState state;

    /** 编译时由 StructureCompiler.bindSnapshotChecker 绑定 */
    private SnapshotChecker snapshotChecker = (s, o, f, u, fl, p) -> false;

    public void bindSnapshotChecker(SnapshotChecker checker) {
        this.snapshotChecker = checker;
    }

    /** 异步检查的统一入口 */
    public boolean checkOnSnapshot(IBlockAccess snap, BlockPos origin,
                                    EnumFacing front, EnumFacing up, boolean flipped,
                                    @Nullable FormedStructureMetadata prior) {
        return snapshotChecker.check(snap, origin, front, up, flipped, prior);
    }

    @FunctionalInterface
    public interface SnapshotChecker {
        boolean check(IBlockAccess snap, BlockPos origin,
                      EnumFacing front, EnumFacing up, boolean flipped,
                      @Nullable FormedStructureMetadata prior);
    }
}
```

#### 8.2.2 `MultiblockState.checkOnSnapshotWithPrior` 新增重载

```java
// 在 MultiblockState.java 中新增
public boolean checkOnSnapshotWithPrior(
        IBlockAccess snap, BlockPos origin,
        EnumFacing front, EnumFacing up, boolean flipped,
        @Nullable FormedStructureMetadata prior) {
    if (prior == null) {
        // 退化为全搜索（与 checkPatternFastAtSnapshot 等价）
        PatternMatchContext ctx = checkPatternFastAtSnapshot(snap, origin, front, up, flipped);
        return ctx != null;
    }
    // === 快速验证路径 ===
    // 把 prior.pieceRepeats 作为初始 repeat 猜测，单次扫描即完成
    int[][] aisleRepetitions = template.getAisleRepetitions();
    int[] priorReps = extractPriorReps(prior, /* pieceName */);  // 从 metadata 提取
    // 临时覆盖 aisleRepetitions 为 priorReps
    // 单次扫描即可
    // 如果失败，返回 false（外层会 fallback 到全搜索）
    ...
}
```

#### 8.2.3 `AsyncStructureChecker.performAsyncCheck` 简化

```java
private boolean performAsyncCheck(SnapshotTask task) {
    MultiblockControllerBase c = task.controller;
    MultiPiecePattern pattern = c.getMultiPiecePattern();
    if (pattern == null) return false;

    FormedStructureMetadata prior = c.getFormedMetadata();
    IBlockAccess snap = task.snapshot;
    BlockPos origin = task.centerPos;
    EnumFacing front = task.frontFacing;
    EnumFacing up = task.upwardsFacing;
    boolean flipped = task.allowsFlip;

    for (StructurePiece piece : pattern.getPieces()) {
        if (piece.isConditional() && !piece.isActive()) continue;
        if (!piece.checkOnSnapshot(snap, origin, front, up, flipped, prior)) {
            return false;
        }
    }
    return true;
}
```

> **关键**：`AsyncStructureChecker` 内部**不知道** `MultiblockState` / `StructureDefinition` 的存在，只通过 `StructurePiece.checkOnSnapshot` 闭包工作。这就是"编译时绑定 snapshot 能力"的价值。

### 8.3 改动量

| 位置 | 改动 |
|---|---|
| `StructurePiece.java` | + ~20 行（snapshotChecker 字段 + bindSnapshotChecker + checkOnSnapshot + SnapshotChecker 接口）+ 2 个虚方法（cacheFormedReps / getLastFormedReps 默认 no-op） |
| `MultiblockState.java` | + ~50 行（`checkOnSnapshotWithPrior` 重载 + 辅助方法） |
| `AsyncStructureChecker.performAsyncCheck` | 重写为统一的 piece 迭代（~30 行替换） |
| **新增** `RepeatGroupPiece.java` | ~180 行（多轴 piece 紧凑表示 + 3 种搜索策略：SLIDING_1D / INDEPENDENT_1D / NESTED_BACKTRACKING，编译期自动分派） |
| **新增** `PooledReference.java` | ~70 行（package-private） |
| **新增** `SoftReferenceHolder.java` | ~30 行（公共） |
| `SoftTemplate.java` | 重构为内部委托 `PooledReference`（公共 API 不变） |
| `TemplatePool.java` | + ~40 行（`registerGeneric` / `registerStructure` / `genericPool` / `onHolderRecreated` / `getStats` 扩展） |
| `StructureCompiler.java` | 重写为两分支（固定 / repeatable），张量积分派 + 3 策略枚举（~170 行，含 §5.6 增量） |

**总改动**：约 410 行新增 + 80 行重构；**零删除**，**零现有公共 API 行为变更**。

### 8.4 性能特性

- **同步路径**（主线程）：多轴递归最坏 7×11×7 = 539 切片，每切片 9 cells = ~5000 块访问 → 2~5ms（在 `RepeatGroupPiece` 内部完成，无 piece 列表迭代开销）。玩家不可感知。
- **异步路径**：`RepeatGroupPiece` 单次闭包调用完成多轴验证；snapshot 是 `IBlockAccess` 数组索引 O(1)，5000 次访问约 0.1~0.5ms，间隔 250ms 一次 → 吞吐无影响。
- **`priorMetadata` 加速**：从已知配置验证，跳过从 minRepeat 重新搜索，**加速 10×~100×**（典型洁净室 5×7×3：backtracking 539 次 vs prior 验证 1 次）。
- **AABB 限制**：max repeat 的 AABB（7×11×7 洁净室 ~580 cells）远低于 100³=1,000,000 阈值，不触发 main-thread fallback。
- **内存**：洁净室 100 个实例：~54,100 对象（v2.2 展开版）→ **~300 对象**（`RepeatGroupPiece` + §5.6 独立 1D），**180× 下降**；蒸馏塔 100 个实例：1,300 对象（v2.2 展开版）→ **200 对象**（`RepeatGroupPiece` 单 1D），**6.5× 下降**。
- **池命中**：编译产物走 `SoftReferenceHolder<MultiPiecePattern>`，30s pin + 软引用。GC 压力下可回收，访问时自动重建。`TemplatePool.PoolStats` 统一统计命中率。

---

## 9. 控制器集成（约 30~50 行 diff）

> **v2.2 修订（新增 `PieceTemplate` IR 层）**：编译路径
> `StructureDefinition → MultiPiecePattern` 现在引入规范 IR
> `PieceTemplate`。`BlockPatternTemplate` 仍保留为**back-compat 薄 facade**
> —— 所有 accessors 委托给 `PieceTemplate`，仅供 `MultiblockControllerBase`
> 等老代码 `getTemplate()` 回退使用。`MultiblockState` **直接持有**
> `PieceTemplate`（v2.2 之前的版本持有 `BlockPatternTemplate` facade），
> `getTemplate()` 返回 lazy facade。
>
> 核心不变量：
> - 新路径：**piece → StructureCompiler.compilePieceToPieceTemplate → PieceTemplate → StructurePiece**
> - 老路径：**FactoryBlockPattern.buildTemplate → BlockPatternTemplate (facade 包装 PieceTemplate) → StructurePiece (legacy 构造重载自动 unwrap)**
> - 两条路径**共享** `PieceTemplate` 作为底层 IR，无重复数据结构

### 9.1 字段

```java
// MultiblockControllerBase 新增：
@Nullable private StructureDefinition structureDefinition;       // 来自 createStructureDefinition()
@Nullable private FormedStructureMetadata formedMetadata;       // 成型时各 piece repeat 数 + 通道值

// 现有字段（含义已扩展）：
// patternTemplate 现在类型仍为 BlockPatternTemplate，但实际承载的是 PieceTemplate facade
// multiblockState.template 内部字段已改为 PieceTemplate（v2.2 之前是 BlockPatternTemplate）
//   - 外部访问通过 multiblockState.getTemplate() 仍拿 BlockPatternTemplate facade
//   - 新代码应优先用 multiblockState.getPieceTemplate()
```

### 9.2 `reinitializeStructurePattern()`

```java
public void reinitializeStructurePattern() {
    this.structureDefinition = createStructureDefinition();
    if (this.structureDefinition != null) {
        // 新路径：编译成 MultiPiecePattern
        this.multiPiecePattern = this.structureDefinition.getCompiledPattern();
        // 单 piece 情况：从 multiPiecePattern 取主 piece 的 template（兼容 getPatternTemplate()）
        //   - getTemplate() 返回 BlockPatternTemplate facade
        //   - 底层 IR 是 StructurePiece.getPieceTemplate() 返回的 PieceTemplate
        if (this.structureDefinition.isSinglePiece()) {
            this.patternTemplate = this.multiPiecePattern.getPrimaryPiece().getTemplate();
            this.multiblockState = this.patternTemplate.createState();
        } else {
            this.patternTemplate = null;
        }
    } else {
        // 旧路径：未修改
        this.patternTemplate = createStructureTemplate();
        this.multiblockState = this.patternTemplate.createState();
        this.multiPiecePattern = createMultiPiecePattern();
    }
    this.structurePattern = (this.patternTemplate != null)
            ? new BlockPattern(this.patternTemplate, this.multiblockState)
            : null;
}
```

### 9.3 `checkStructurePattern()`

```java
public void checkStructurePattern() {
    if (this.structureDefinition != null) {
        StructureCheckState state = this.structureDefinition.createState();
        StructureCheckState.Result result = state.check(getWorld(), getPos(),
                getFrontFacing(), getUpwardsFacing(), allowsFlip(), /* context */ null);
        if (result.success) {
            this.formedMetadata = result.metadata;   // 关键：保存成型时各 piece 数值
            this.structureFormed = true;
            formStructure(/* context */ null);
        } else {
            this.structureFormed = false;
            // 错误信息：result.errorPos / result.errorMessage
        }
        return;
    }
    // 旧路径：未修改
    ...
}
```

### 9.4 异步路径

`AsyncStructureChecker` 在 `task` 中附加 `task.priorMetadata = controller.getFormedMetadata()`，传给 `piece.checkOnSnapshot(..., prior)`。`MultiblockControllerBase` 不需要改 `doStructureCheck()`——`AsyncStructureChecker.registerForAsyncCheck(this)` 的接口不变。

---

## 10. 迁移策略

| 阶段 | 内容 | 范围 | 验证 |
|---|---|---|---|
| **P0: 弃用 `aisleRepeatable`**（v2.2 自身完成，**不等 P6**） | `FactoryBlockPattern.aisleRepeatable(...)` 加 `@Deprecated` + `@ApiStatus.ScheduledForRemoval(inVersion = "2.10")` + Javadoc 迁移指引 | 1 文件 | 编译期 IDE 警告；现有 100+ 旧机器**零行为变化** |
| **P1: 基础设施** | §2 全部 7+9 个新类 + FormedStructureMetadata + StructureCompiler + PooledReference + SoftReferenceHolder | 仅新文件 + 扩展 TemplatePool/SoftTemplate/StructurePiece/MultiblockState | 单元测试 |
| **P1.5: 异步快照镜像** | `MultiblockState.checkOnSnapshotWithPrior` | 1 文件 | 单元测试 |
| **P2: 单轴验证** | 迁蒸馏塔（单轴 `repeatableY`），通过 `StructureDefinition.getOrBuild` 注册 | 1 机器 | 同步/异步吞吐基准 |
| **P3: 多轴实现** | 迁洁净室（`repeatAxes(0,1,2)`） | 1 机器 + 性能基准 | 7×11×7 极端情况测试 |
| **P4: 集成到主分支** | 提交到 GregTech 主分支 | 全部 | 全量回归 |
| **P5: 按需迁移** | 其他机器 opt-in | 视需求 | — |
| ~~**P6: 弃用评估**~~ | **已并入 P0** | — | — |

**何时停止 P5**：剩余未迁移机器都满足"单轴 / 矩形 / 旧 API 已足够" → 停止迁移。**旧 `aisleRepeatable` 保留到 2.10**（与 `FactoryBlockPattern.build()` 同步移除），旧机器继续用旧 API 即可，**不强制迁移**。

### 10.1 弃用清单（v2.2 标记，2.10 移除）

| API | 状态 | 移除版本 | 替代 |
|---|---|---|---|
| `FactoryBlockPattern.aisleRepeatable(int, int, String...)` | `@Deprecated` + `@ApiStatus.ScheduledForRemoval` | 2.10 | `StructureDefinition.Builder.repeatableX/Y/Z(...)`（单轴 uniform）<br>`StructureDefinition.Builder.repeatablePiece(...).repeatAxes(...)`（多轴 uniform） |
| `LazyTemplate`（类） | `@Deprecated`（v1 已加） | 2.10（待与 `aisleRepeatable` 同步） | `SoftTemplate` / `TemplatePool.registerGeneric(...)` |
| `FactoryBlockPattern.build()` | `@Deprecated` + `@ApiStatus.ScheduledForRemoval`（v1 已加） | 2.10 | `FactoryBlockPattern.buildTemplate()` + `BlockPattern(template)` |

### 10.2 为什么 `aisleRepeatable` 在 v2.2 立刻弃用（不等 P2~P5 反馈）

原计划 P6 等 P2~P5 反馈"再决定"是否弃用。**实际上 v2.2 设计自身已具备立即弃用的充分依据**：

1. **新机器完全不需要 `aisleRepeatable`**：`createStructureDefinition()` → `StructureDefinition` 路径 0 接触
2. **新旧路径互不共享内部数据结构**（§5.5）：没有"对齐旧行为"的硬性需求
3. **§5.6 已彻底消除"展开"shim**：v2.2 在新路径上比旧 `aisleRepeatable` 路径**严格更优**（内存、cell 访问、闭包调用、JEI 预览、异步检查）
4. **Addon 作者最需要明确信号**：IDE 警告（`@Deprecated` 触发）能立即引导新代码走向新 API，避免新增的 addon 继续用旧 API 写新机器
5. **`@Deprecated` 不破坏现有调用**：旧机器继续编译、运行、行为不变；只是 IDE 会有删除线警告

**结论**：弃用是 v2.2 自身的强需求，不是"P2~P5 反馈后才决定"的可选项。**P0 与 P1 并行**——P0 是 1 行注解 + Javadoc 改动，零风险，零阻塞。

### 10.3 迁移示例（从 `aisleRepeatable` 到 `StructureDefinition`）

**旧**（v1 旧机器，仍可用至 2.10）：
```java
public class MetaTileEntityDistillationTower extends MultiblockControllerBase {
    private static final BlockPattern PATTERN = FactoryBlockPattern.start()
        .aisle("YSY", "YYY", "YYY")
        .aisleRepeatable(1, 11, "XXX", "X#X", "XXX")
            .withAisleChannel(GTStructureChannels.STRUCTURE_HEIGHT.getName())
        .aisle("XXX", "XXX", "XXX")
        .where('S', selfPredicate(MetaTileEntityDistillationTower.class))
        .where('#', air())
        .where('Y', /* ... */)
        .where('X', /* ... */)
        .build();
    
    @Override
    protected BlockPattern createStructurePattern() { return PATTERN; }
}
```

**新**（v2.2 新机器，**推荐**）：
```java
public class MetaTileEntityDistillationTower extends MultiblockControllerBase {
    private static final StructureDefinition DEFINITION = StructureDefinition.getOrBuild(
        "gregtech:distillation_tower", () ->
        StructureDefinition.builder(RIGHT, BACK, UP)
            .piece("top", "YSY", "YYY", "YYY")
                .where('S', self(MetaTileEntityDistillationTower.class))
                .where('Y', block(casingState))
            .repeatableY("layer", 1, 11, "height",
                    "XXX", "X#X", "XXX")
                .where('X', block(casingState))
                .where('#', air())
            .piece("bottom", "XXX", "XXX", "XXX")
                .where('X', block(casingState))
            .build());
    
    @Override
    protected StructureDefinition createStructureDefinition() { return DEFINITION; }
}
```

**关键差异**：
- 旧：每行 aisle 独立 repeat（per-aisle 异形），实际几乎都用 uniform
- 新：piece 整体 uniform repeat，编译期更简单，运行时更高效（§5.6 张量积自动分派）
- 旧：`formedRepetitionCount[c]` per-aisle 数组（无 NBT 持久化）
- 新：`FormedStructureMetadata.pieceRepeats[pieceName]`（有 NBT 持久化，可被 JEI / 配方逻辑读取）

### 10.4 旧机器不强制迁移

P0 弃用不等于强制迁移。100+ 旧机器继续用 `aisleRepeatable` + `createStructurePattern()` 完全 OK：
- 编译通过（`@Deprecated` 只警告，不报错）
- 运行行为不变（旧路径独立于新路径）
- 模板池命中、NBT 保存、异步检查全部照旧
- 直到 2.10 移除 `aisleRepeatable` 之前，不需要任何迁移动作

**迁移是 opt-in**：当旧机器需要新功能（如 `FormedStructureMetadata` 写 NBT、加速 6.5×~190×）时，按 §10.3 模板迁移。

---

## 11. 性能基准测试计划

| 测试 | 目标 | 测量 |
|---|---|---|
| T1: 蒸馏塔基线 | 旧 `aisleRepeatable` vs 新 `repeatableY` | 同步检查耗时 / 异步检查耗时 / 内存 |
| T2: 洁净室 1×1×1 | 最小尺寸 | 同上 |
| T3: 洁净室 5×7×3 | 典型尺寸 | 同上 + `priorMetadata` 加速比 |
| T4: 洁净室 7×11×7 | 最大尺寸 | 极端情况 / GC 行为 |
| T5: 100 个洁净室并发 | 多实例压力 | 总内存 / GC pause / 异步吞吐 |
| T6: 模板池命中/未命中 | 验证 SoftTemplate / PooledReference 工作 | `TemplatePool.getStats()` |
| T7: 异步检查加速 | `priorMetadata` 启用 vs 禁用 | 异步检查耗时对比 |

基准测试代码放在 `src/test/java/gregtech/api/pattern/element/StructureElementBenchmark.java`。

---

## 12. 显式限制

1. **不支持嵌套可重复子区域**（piece 是叶子节点）。
2. **每 IStructurePiece 至多生成 1 个 StructurePiece**（**经 §5.6 强化**）：
   - 固定 piece：1 个 `StructurePiece`。
   - **任意 repeatable piece**（单轴 / 多轴 / 张量积 / 异形）：**1 个 `RepeatGroupPiece`，不再展开**。
   - 编译期按 base 形状 + axis 数选搜索策略（`SLIDING_1D` / `INDEPENDENT_1D` / `NESTED_BACKTRACKING`，见 §5.6.1）。
   - 蒸馏塔 = 2 piece、装配线 = 3 piece、洁净室 = 3 piece（v2.2 旧展开版分别为 13 / 15 / 541）。
3. **`preview()` 阶段截断到 max=5**，避免 JEI 卡顿。
4. **成型状态写入 NBT 仅在新系统下生效**；旧机器不写（保留向后兼容）。
5. **异步检查的 `priorMetadata` 优化**仅当 controller 当前已成型时有效；未成型机器仍走全搜索（与旧系统等价）。`RepeatGroupPiece` 把 `prior` 作为单次扫描的初始猜测；命中即返回，失败退化到 backtracking。
6. **`createStructureDefinition()` 必须返回幂等的 `StructureDefinition` 实例**——不能每次 new，否则 TemplatePool 失效。建议用 `StructureDefinition.getOrBuild(key, factory)` 静态方法。
7. **多 piece 编译产物在 `MultiPiecePattern` 中去重**：同样的 piece 在多个位置复用时共享 `BlockPatternTemplate` 引用（避免内存膨胀）。
8. **`MultiblockState.checkOnSnapshotWithPrior` 的快速验证失败时退化到全搜索**——极端情况下（玩家修改了结构但 chunk 未刷新）`prior` 失效，需要重新搜索。
9. **`RepeatGroupPiece.backtrackAxes` 复杂度** O(∏(max_i - min_i + 1) × 切片大小)。最坏 7×11×7 = 539 次切片检查；prior 命中时退化为 1 次。早期外轴优先 + 早终止，实际常见尺寸（5×7×3）下 ≤ 50 次。**但这只在"建造态 / 过渡态"下发生**——成型态是 O(1) 单次验证（占运行期 99%）。
10. **多 piece 总数上界**：单实例 `MultiPiecePattern` 持有的 `StructurePiece` 个数 = Σ(固定 piece 数) + Σ(单轴 max 数) + 多轴 piece 数。洁净室 ≈ 3 + 0 + 1 = 4（v2.2 展开版 541）；蒸馏塔 = 13（不变）。
11. **状态机依赖**：`RepeatGroupPiece` 的两态分派依赖 `MultiblockControllerBase.getFormedMetadata()`。如果 controller 的 formed 状态与 metadata 不一致（例如被外部强制设置），prior 验证会失败、退化到 backtracking——功能正确但性能退化。NBT 加载时务必先于异步检查恢复 `formedMetadata`。

---

## 13. 包结构

```
gregtech.api.pattern.element/                      # 新建包
├── IStructureElement.java
├── IStructurePiece.java
├── StructureDefinition.java        # 含 Builder + 内化 PieceEntry + getOrBuild
├── StructureCheckState.java       # 含 Result 内部类
├── FormedStructureMetadata.java   # 新增：成型状态持久化
├── StructureCompiler.java         # 新增：统一编译入口
├── ElementUtility.java
├── Elements.java                   # 短方法名版本（语法糖）
└── impl/
    ├── BlockElement.java
    ├── AirElement.java
    ├── AnyElement.java
    ├── SelfElement.java
    ├── HatchElement.java
    ├── TieredElement.java
    ├── ChainElement.java
    ├── WrapperElement.java        # 合并 lazy/onElementPass/withChannel
    └── LegacyElement.java         # 接收旧 TraceabilityPredicate

gregtech.api.pattern/                              # 复用 / 扩展现有包
├── TemplatePool.java               # 修改：+ registerGeneric / registerStructure
├── SoftTemplate.java               # 重构：内部用 PooledReference；公共 API 不变
├── SoftReferenceHolder.java        # 新增：泛型 SoftTemplate 等价物
├── StructurePiece.java             # 修改：+ bindSnapshotChecker / checkOnSnapshot / 虚方法 cacheFormedReps
├── RepeatGroupPiece.java           # 新增：extends StructurePiece，多轴 piece 的紧凑表示
├── MultiblockState.java            # 修改：+ checkOnSnapshotWithPrior 重载
└── internal/
    └── PooledReference.java        # 新增：泛型软引用 + 30s pin（package-private）
```

**新增文件**：
- `gregtech.api.pattern.element/` 下 9 个公共 + 9 个 impl
- `gregtech.api.pattern/SoftReferenceHolder.java`（公共）
- `gregtech.api.pattern/RepeatGroupPiece.java`（公共，extends StructurePiece）
- `gregtech.api.pattern.internal/PooledReference.java`（package-private）

**修改文件**（v2.2 触及的现有类）：
- `TemplatePool.java`（+ ~40 行：`registerGeneric` / `registerStructure` / `genericPool` / `onHolderRecreated` / `getStats` 扩展）
- `SoftTemplate.java`（内部委托 `PooledReference`；公共 API 不变）
- `StructurePiece.java`（+ ~25 行：`snapshotChecker` 字段 / `bindSnapshotChecker` / `checkOnSnapshot` / `SnapshotChecker` 接口 / 虚方法 `cacheFormedReps` / `getLastFormedReps`）
- `MultiblockState.java`（+ ~50 行：`checkOnSnapshotWithPrior` 重载 + 辅助方法）

**`LazyTemplate` 全文不动**，依然保留 `@Deprecated` 标记，**新代码不引用它**。

---

## 14. 使用示例

### 14.1 蒸馏塔（单轴，演示模板池 + 语法糖）

```java
public class MetaTileEntityDistillationTower extends MultiblockControllerBase {

    private static final StructureDefinition DEFINITION = StructureDefinition.getOrBuild(
        "gregtech:distillation_tower", () ->
        StructureDefinition.builder(RIGHT, BACK, UP)
            .piece("top", "YSY", "YYY", "YYY")
                .where('S', self(MetaTileEntityDistillationTower.class))
                .where('Y', block(casingState))
            .repeatableY("layer", 1, 11, "height",
                    "XXX", "X#X", "XXX")
                .where('X', block(casingState))
                .where('#', air())
            .piece("bottom", "XXX", "XXX", "XXX")
                .where('X', block(casingState))
            .build());

    @Override
    protected StructureDefinition createStructureDefinition() { return DEFINITION; }
}
```

### 14.2 洁净室（多轴，演示 FormedStructureMetadata 读取）

```java
public class MetaTileEntityCleanroom extends MultiblockControllerBase {

    private static final StructureDefinition DEFINITION = StructureDefinition.getOrBuild(
        "gregtech:cleanroom", () ->
        StructureDefinition.builder(RIGHT, UP, BACK)
            .piece("floor", "SSS", "SSS", "SSS")
                .where('S', self(MetaTileEntityCleanroom.class))
            .repeatablePiece("wall",
                    new String[][]{
                        {"WWW","WWW","WWW"},
                        {"WWW","WWW","WWW"},
                        {"WWW","WWW","WWW"}},
                    new Vec3i(0, 1, 0))
                .where('W', block(plasticState))
                .repeatAxes(0, 1, 2)
                .repeatRange(1, 7, 1, 11, 1, 7)
                .channelNames("width", "height", "depth")
            .piece("ceiling", "CCC", "CCC", "CCC")
                .where('C', block(plasticState))
            .build());

    @Override
    protected StructureDefinition createStructureDefinition() { return DEFINITION; }

    /** 配方逻辑：从成型状态读尺寸 */
    @Override
    public int getTier() {
        FormedStructureMetadata meta = getFormedMetadata();
        if (meta == null) return 0;
        int width = meta.getPieceRepeat("wall", 0);
        int height = meta.getPieceRepeat("wall", 1);
        int depth = meta.getPieceRepeat("wall", 2);
        return Math.min(7, Math.min(width, Math.min(height, depth)));
    }
}
```

### 14.3 装配线（演示 `pieceFromFactory` 混合模式 + 旧 API 兼容）

```java
public class MetaTileEntityAssemblyLine extends MultiblockControllerBase {

    private static final StructureDefinition DEFINITION = StructureDefinition.getOrBuild(
        "gregtech:assembly_line", () ->
        StructureDefinition.builder(RIGHT, UP, BACK)
            .pieceFromFactory("head", FactoryBlockPattern.start()
                .aisle("FIF", "RTR", "SAG", " Y ")
                .where('S', self(MetaTileEntityAssemblyLine.class))
                .where('F', block(frameState))
                /* ... */)
            .repeatableZ("body", 3, 15, "length",
                    "FIF", "RTR", "DAG", " Y ")
                .where('F', block(frameState))
                /* ... */
            .pieceFromFactory("tail", FactoryBlockPattern.start()
                .aisle("FOF", "RTR", "DAG", " Y ")
                .where('O', hatchAdder(EXPORT_ITEMS))
                /* ... */)
            .build());
}
```

---

## 15. 与 v1 的差异

| 主题 | v1 | v2.2 |
|---|---|---|
| 新公共类数 | 7 | **8**（IStructureElement / IStructurePiece / StructureDefinition / StructureCheckState / FormedStructureMetadata / StructureCompiler / ElementUtility + Elements / SoftReferenceHolder / **RepeatGroupPiece**） |
| impl 数 | 11 | 9（合并 LazyElement/CallbackElement/ChannelElement 为 WrapperElement） |
| 模板/状态切分 | 隐式 | 显式（`createState()` + `StructureCheckState`） |
| 适配器 | 独立 `StructureElementAdapter` | 内化到 `IStructureElement.toPredicate()` + `StructureCompiler` |
| `StructureCheckResult` / `StructureError` | 独立类 | 合并入 `StructureCheckState.Result` |
| 写法简洁度 | 比旧 API 啰嗦 3~5 倍 | 通过 4 类语法糖打平（扁平字符串 / 静态导入 / 单轴快捷 / FactoryBlockPattern 兼容） |
| 嵌套可重复子区域 | 未说明 | 显式不支持 |
| 何时停止迁移 | 未说明 | §10 给出 P5 停止准则 |
| **TemplatePool 集成** | **完全没用** | **泛型化**：`registerGeneric` / `PooledReference<T>` / `PoolStats` 统一统计 |
| **编译产物缓存** | **未规划** | **`SoftReferenceHolder<MultiPiecePattern>` 走泛型池** |
| **桥接类** | **无** | **无**（v2.2 直接扩展 TemplatePool） |
| **统一路径** | **未明确**（v1 是双轨：单轴 → BlockPatternTemplate，多轴 → MultiPiecePattern） | **统一编译到 MultiPiecePattern；每 IStructurePiece 至多生成 1 个 StructurePiece** |
| **多轴 piece 表示** | **未明确** | **`RepeatGroupPiece` 紧凑表示 + §5.6 张量积自动分派**：洁净室 7×11×7 从 541 piece 降到 3 piece（**180× 内存**）；多轴张量积 cell 访问从 14,553 降到 ~75（**~190× 加速**）；单轴 piece 一律用 1D 滑动窗口（蒸馏塔从 13 piece 降到 2 piece） |
| **成型状态持久化** | **不写 NBT** | **`FormedStructureMetadata` 写入 NBT** |
| **异步检查加速** | **每次从 min 搜** | **`priorMetadata` 验证已知配置 + `RepeatGroupPiece` 单闭包，加速 10×~540×** |
| **snapshot 能力绑定** | **未明确** | **编译时绑定到 `StructurePiece` 闭包**（含 `RepeatGroupPiece` 多轴闭包） |
| **`LazyTemplate` 使用** | 无 | **不用**（已弃用），改用 `SoftTemplate` / 泛型 `PooledReference<T>` |

---

## 16. 总结

v2.2 在 v1 的 18 个新类基础上**收敛到 7+9+3 = 19 个**（多 1 个是因为 `SoftReferenceHolder` 拆出来），但：
- **公共 API 表面从 7 收敛到 8 个**（多 1 个 `FormedStructureMetadata` 和 `StructureCompiler` 是必要的）；
- **零删除现有公共 API**；
- **零修改 `LazyTemplate`**（已弃用，保留兼容标记）；
- **复用 `TemplatePool` 的泛型化**让池统计统一，**不引入桥接类**；
- **统一编译路径**让 `AsyncStructureChecker` 不必知道机器是旧是新；
- **编译时绑定 snapshot 能力**让运行时检查只是闭包调用，性能最简；
- **成型状态持久化**让 save/load / 配方逻辑 / JEI 显示都可读；
- **§5.6 张量积自动分派**让所有 repeatable piece 统一为 1 个 `RepeatGroupPiece`，**展开路径彻底消失**；多轴张量积 cell 访问从 14,553 降到 ~75（**~190× 加速**）；单轴 piece 蒸馏塔从 13 piece 降到 2 piece（**6.5× 内存**）。

新系统是"新语法 + 旧后端"：所有运行时行为（检查、异步、autobuild、JEI、NBT）都复用 `MultiPiecePattern` + `MultiblockState` + `BlockPatternTemplate` + `AsyncStructureChecker` + `BlockStateSnapshot` + `MultiblockWorldData`。**新增的"新"主要是 API 表面**（Builder、Element、Piece、Definition），**不是新的运行时基础设施**——§5.6 进一步把"展开 vs 搜索"的内部选择交给编译期自动分派，运行期只是闭包 + 1D 扫描。
