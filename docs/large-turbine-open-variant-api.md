# 大型涡轮开放 Variant API 设计与迁移步骤

## 背景

当前 `MetaTileEntityLargeTurbine` 已经被改成单个 MTE ID 加 NBT variant 的形式，但 variant 类型仍然是 `LargeTurbineType enum`。这会带来两个限制：

- addon 无法在运行期注册新的大型涡轮 variant。
- `ParametricMultiblockController`、JEI、创造栏子物品、模板缓存和 NBT 都隐含依赖 enum ordinal。

上级目录 `GTQTCore` 目前还在使用旧式独立 MTE ID 注册大型涡轮：

```java
new MetaTileEntityLargeTurbine(id, recipeMap, tier,
        casingState, gearboxState, casingRenderer,
        hasMufflerHatch, frontOverlay)
```

因此改造目标必须同时支持：

- GregTech 本体的单 ID 多 variant 模式，例如 `gregtech:large_turbine`。
- GTQTCore 的旧独立 MTE ID 模式，例如 `gtqtcore:large_turbine.exhaust_gas`。
- addon 以后通过开放 registry 注册新的大型涡轮 variant。

## 目标

1. 将 `ParametricMultiblockController` 泛化为真正支持开放 variant registry 的基类。
2. 将 variant 持久化格式从 enum ordinal 改为稳定的 `ResourceLocation` 字符串。
3. 保留旧 enum 机器的低成本迁移路径。
4. 保留 `GTQTCore` 旧构造器和行为兼容。
5. 为大型涡轮提供公开可注册的 variant API。

## 非目标

- 不立即强制 GTQTCore 改源码。
- 不立即删除 `LargeTurbineType`。
- 不把锅炉、LCE、储罐全部改成开放注册，只先通过 enum adapter 迁移到新基类。

## 步骤

### 1. 新增通用 Variant Registry API

在 `gregtech.api.metatileentity.multiblock` 下新增：

- `ParametricVariantRegistry<V>`
- `SimpleParametricVariantRegistry<V>`
- `ParametricVariantRegistries`

核心接口建议如下：

```java
public interface ParametricVariantRegistry<V> {

    @NotNull
    V getDefaultVariant();

    @NotNull
    Collection<V> getVariants();

    @Nullable
    V getVariant(@NotNull ResourceLocation id);

    @NotNull
    ResourceLocation getId(@NotNull V variant);

    @NotNull
    default V getOrDefault(@Nullable ResourceLocation id) {
        V variant = id == null ? null : getVariant(id);
        return variant == null ? getDefaultVariant() : variant;
    }
}
```

`ParametricVariantRegistries` 至少提供两个 helper：

```java
enumRegistry(String namespace, Class<E> enumClass, E defaultVariant)
single(ResourceLocation id, V variant)
```

`enumRegistry` 用于迁移现有 enum 机器。`single` 用于 GTQTCore 旧构造器这种固定单 variant 的独立 MTE。

### 2. 泛化 ParametricMultiblockController

把：

```java
public abstract class ParametricMultiblockController<V extends Enum<V>>
```

改成：

```java
public abstract class ParametricMultiblockController<V>
```

字段从：

```java
private final Class<V> variantClass;
private final V defaultVariant;
```

改成：

```java
private final ParametricVariantRegistry<V> variantRegistry;
private final V defaultVariant;
private V variant;
```

主构造器改成：

```java
protected ParametricMultiblockController(@NotNull ResourceLocation metaTileEntityId,
                                         @NotNull ParametricVariantRegistry<V> variantRegistry) {
    super(metaTileEntityId);
    this.variantRegistry = variantRegistry;
    this.defaultVariant = variantRegistry.getDefaultVariant();
    this.variant = defaultVariant;
}
```

保留 deprecated enum 构造器：

```java
@Deprecated
protected <E extends Enum<E>> ParametricMultiblockController(ResourceLocation id,
                                                            Class<E> enumClass,
                                                            E defaultVariant) {
    this(id, ParametricVariantRegistries.enumRegistry(id.getNamespace(), enumClass, defaultVariant));
}
```

实际实现时 Java 泛型可能需要把 deprecated 构造器写在 enum-only 兼容类或静态 factory 中，避免 `V` 与 `E` 无法安全转换。

### 3. 改 Variant NBT 格式

新格式写入 `ResourceLocation` 字符串：

```java
data.setString(NBT_KEY_VARIANT, variantRegistry.getId(variant).toString());
```

读取逻辑：

```java
private V readVariant(NBTTagCompound data) {
    if (data.hasKey(NBT_KEY_VARIANT, Constants.NBT.TAG_STRING)) {
        return variantRegistry.getOrDefault(new ResourceLocation(data.getString(NBT_KEY_VARIANT)));
    }
    if (data.hasKey(NBT_KEY_VARIANT, Constants.NBT.TAG_INT)) {
        return readLegacyVariantFromOrdinal(data.getInteger(NBT_KEY_VARIANT));
    }
    return defaultVariant;
}
```

保留旧 ordinal 兼容钩子：

```java
protected V readLegacyVariantFromOrdinal(int ordinal) {
    List<V> variants = new ArrayList<>(variantRegistry.getVariants());
    return ordinal >= 0 && ordinal < variants.size() ? variants.get(ordinal) : defaultVariant;
}
```

大型涡轮建议覆盖该方法，明确旧映射：

- `0 -> gregtech:steam`
- `1 -> gregtech:gas`
- `2 -> gregtech:plasma`

### 4. 改网络同步和 ItemStack NBT

`writeInitialSyncData` 改为写 variant ID：

```java
buf.writeString(variantRegistry.getId(variant).toString());
```

`receiveInitialSyncData` 改为读 variant ID：

```java
applyVariant(variantRegistry.getOrDefault(new ResourceLocation(buf.readString(32767))), true);
```

`writeItemStackData`、`initFromItemStackData`、`getVariantFromStack` 同样改成字符串 ID，并保留 int ordinal 读取兼容。

`getSubItems` 从：

```java
for (V value : variantClass.getEnumConstants())
```

改成：

```java
for (V value : variantRegistry.getVariants())
```

### 5. 改模板缓存

删除基类对 `Map<V, SoftTemplate>` 的强制要求。由基类统一按 variant ID 懒加载模板：

```java
private final Map<ResourceLocation, SoftTemplate> templateCache = new ConcurrentHashMap<>();

@Override
@NotNull
protected BlockPatternTemplate createStructureTemplate() {
    V current = getVariant();
    ResourceLocation variantId = variantRegistry.getId(current);

    return templateCache.computeIfAbsent(variantId, id ->
            TemplatePool.getInstance().register(getTemplatePoolKey(current),
                    () -> buildStructureTemplate(current)))
            .get();
}

protected String getTemplatePoolKey(@NotNull V variant) {
    return metaTileEntityId + "/" + variantRegistry.getId(variant);
}

@NotNull
protected abstract BlockPatternTemplate buildStructureTemplate(@NotNull V variant);
```

这样 addon 后注册的 variant 不需要在 class static init 时就存在。

### 6. 同步泛化派生基类

以下类一起改掉 `V extends Enum<V>`：

- `ParametricRecipeMapController<V>`
- `ParametricFuelController<V>`
- `ParametricMultiblockPart<V>`

`ParametricMultiblockPart` 也应使用同一套 registry、NBT 和 ItemStack 逻辑，避免以后开放多方块 part variant 时重复改造。

### 7. 改 JEI 注册逻辑

`MetaTileEntities.registerParametricMultiblockVariantsForJei` 从 enum constants 改成 registry variants：

```java
for (V variant : parametric.getVariants()) {
    ParametricMultiblockController<V> copy =
            (ParametricMultiblockController<V>) parametric.createMetaTileEntity(null);
    copy.setVariant(variant);
    MultiblockInfoCategory.registerMultiblock(copy);
}
```

基类提供：

```java
public Collection<V> getVariants() {
    return variantRegistry.getVariants();
}
```

### 8. 新增大型涡轮 Variant 数据类

新增 `LargeTurbineVariant`，建议字段：

```java
public final class LargeTurbineVariant {
    private final ResourceLocation id;
    private final String translationKey;
    private final RecipeMap<?> recipeMap;
    private final int tier;
    private final IBlockState casingState;
    private final IBlockState gearboxState;
    private final ICubeRenderer casingRenderer;
    private final ICubeRenderer frontOverlay;
    private final boolean hasMufflerMechanics;
}
```

提供两个 factory：

```java
standard(ResourceLocation id, String name, RecipeMap<?> recipeMap, int tier, ...)
legacy(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap, int tier, ...)
```

`legacy` 的 `translationKey` 必须从 MTE ID 生成：

```java
metaTileEntityId.getNamespace() + ".machine." + metaTileEntityId.getPath()
```

这样 `gtqtcore:large_turbine.exhaust_gas` 仍然使用：

```text
gtqtcore.machine.large_turbine.exhaust_gas.name
```

### 9. 新增大型涡轮 Variant Registry

新增 `LargeTurbineVariants` 或 `LargeTurbineVariantRegistry`。默认注册：

- `gregtech:steam`
- `gregtech:gas`
- `gregtech:plasma`

示例：

```java
public final class LargeTurbineVariants {
    public static final SimpleParametricVariantRegistry<LargeTurbineVariant> REGISTRY =
            new SimpleParametricVariantRegistry<>();

    public static final LargeTurbineVariant STEAM = REGISTRY.register(gregtechId("steam"), ...);
    public static final LargeTurbineVariant GAS = REGISTRY.register(gregtechId("gas"), ...);
    public static final LargeTurbineVariant PLASMA = REGISTRY.register(gregtechId("plasma"), ...);
}
```

注册窗口结束后调用 `freeze()`，防止 JEI、创造栏和模板缓存初始化后再变更。

### 10. 改 MetaTileEntityLargeTurbine

从：

```java
extends ParametricFuelController<LargeTurbineType>
```

改成：

```java
extends ParametricFuelController<LargeTurbineVariant>
```

原 `buildTemplate(LargeTurbineType type)` 改为：

```java
@Override
protected BlockPatternTemplate buildStructureTemplate(@NotNull LargeTurbineVariant variant) {
    ...
}
```

所有配置从 variant 对象取：

- recipe map
- tier
- casing state
- gearbox state
- casing renderer
- front overlay
- muffler behavior

`hasMufflerMechanics` 改成：

```java
@Override
public boolean hasMufflerMechanics() {
    return getVariant().hasMufflerMechanics();
}
```

### 11. 保留 LargeTurbineType 兼容层

不要立刻删除 `LargeTurbineType`。建议改成 deprecated alias：

```java
@Deprecated
public enum LargeTurbineType {
    STEAM(LargeTurbineVariants.STEAM),
    GAS(LargeTurbineVariants.GAS),
    PLASMA(LargeTurbineVariants.PLASMA);

    private final LargeTurbineVariant variant;

    public LargeTurbineVariant getVariant() {
        return variant;
    }
}
```

旧构造器：

```java
@Deprecated
public MetaTileEntityLargeTurbine(ResourceLocation id, LargeTurbineType type) {
    this(id, type.getVariant());
}
```

### 12. 保留 GTQTCore 旧构造器

必须保留以下构造器签名：

```java
@Deprecated
public MetaTileEntityLargeTurbine(ResourceLocation metaTileEntityId,
                                  RecipeMap<?> recipeMap,
                                  int tier,
                                  IBlockState casingState,
                                  IBlockState gearboxState,
                                  ICubeRenderer casingRenderer,
                                  boolean hasMufflerHatch,
                                  ICubeRenderer frontOverlay) {
    this(metaTileEntityId, LargeTurbineVariant.legacy(metaTileEntityId, recipeMap, tier,
            casingState, gearboxState, casingRenderer, hasMufflerHatch, frontOverlay));
}
```

该构造器内部应使用 single registry：

```java
ParametricVariantRegistries.single(legacyVariant.getId(), legacyVariant)
```

这样 GTQTCore 的三台大型涡轮仍然是三个独立 MTE ID，而不是被强行合并到 `gregtech:large_turbine`。

### 13. 修正大型涡轮名称逻辑

`MetaTileEntityLargeTurbine#getMetaName()` 和 `getMetaName(ItemStack)` 应优先使用 variant 的 `translationKey`：

```java
return getVariant().getTranslationKey();
```

GregTech 默认 variant 可返回：

```text
gregtech.machine.large_turbine.steam
gregtech.machine.large_turbine.gas
gregtech.machine.large_turbine.plasma
```

GTQTCore legacy variant 返回：

```text
gtqtcore.machine.large_turbine.exhaust_gas
gtqtcore.machine.large_turbine.high_pressure_steam
gtqtcore.machine.large_turbine.supercritical_steam
```

### 14. 更新 GregTech 本体注册

`MultiblockRegistration` 继续只注册一个本体 MTE：

```java
MetaTileEntityLargeTurbine turbine = registerMetaTileEntity(1010,
        new MetaTileEntityLargeTurbine(gregtechId("large_turbine")));
```

旧字段继续保留：

```java
LARGE_STEAM_TURBINE = turbine;
LARGE_GAS_TURBINE = new MetaTileEntityLargeTurbine(gregtechId("large_turbine"), LargeTurbineVariants.GAS);
LARGE_PLASMA_TURBINE = new MetaTileEntityLargeTurbine(gregtechId("large_turbine"), LargeTurbineVariants.PLASMA);
```

### 15. 更新 LARGE_TURBINES Map

新增开放 registry map：

```java
public static final Map<ResourceLocation, MetaTileEntityLargeTurbine> LARGE_TURBINE_VARIANTS =
        new LinkedHashMap<>();
```

旧字段可以作为 deprecated 兼容：

```java
@Deprecated
public static final EnumMap<LargeTurbineType, MetaTileEntityLargeTurbine> LARGE_TURBINES =
        new EnumMap<>(LargeTurbineType.class);
```

这样旧 addon 查询 `LARGE_TURBINES.get(LargeTurbineType.GAS)` 还能工作，新 addon 用 `ResourceLocation` 查询。

### 16. 迁移现有 enum parametric 机器

以下机器先通过 enum adapter 迁移，不改变功能：

- `MetaTileEntityLargeBoiler`
- `MetaTileEntityLargeCombustionEngine`
- `MetaTileEntityMultiblockTank`

示例：

```java
private static final ParametricVariantRegistry<BoilerType> VARIANTS =
        ParametricVariantRegistries.enumRegistry("gregtech", BoilerType.class, BoilerType.BRONZE);

public MetaTileEntityLargeBoiler(ResourceLocation id) {
    super(id, VARIANTS);
}
```

它们的模板方法从 `createStructureTemplate` 改为 `buildStructureTemplate(variant)`。

### 17. GTQTCore 兼容验证点

确认以下旧代码无需修改即可编译：

```java
new MetaTileEntityLargeTurbine(gtqtcoreId("large_turbine.exhaust_gas"),
        RecipeMaps.EXHAUST_GAS_TURBINE_FUELS, EV,
        GTQTMetaBlocks.TURBINE_CASING.getState(RHODIUM_PLATED_PALLADIUM_TURBINE),
        GTQTMetaBlocks.TURBINE_CASING.getState(RHODIUM_PLATED_PALLADIUM_GEARBOX),
        GTQTTextures.TURBINE_RHODIUM_PLATED_PALLADIUM_CASING,
        false,
        Textures.LARGE_STEAM_TURBINE_OVERLAY)
```

同时确认：

- 显示名仍然读取 `gtqtcore.machine.large_turbine.*.name`。
- `hasMufflerHatch=false` 的大型涡轮不强制 muffler。
- GTQT 自己的 turbine casing 和 renderer 可以作为 `IBlockState`、`ICubeRenderer` 正常传入。

### 18. 编译验证

先编译 GregTech：

```powershell
.\gradlew compileJava
```

再编译上级 `GTQTCore`，验证旧构造器兼容。

如果 GTQTCore 编译失败，优先检查：

- 构造器签名是否完全保留。
- `LargeTurbineType` deprecated alias 是否覆盖旧引用。
- `RecipeMap<?>`、`IBlockState`、`ICubeRenderer` 的 import 是否未被移动。

## 后续迁移建议

兼容桥稳定后，GTQTCore 可以逐步迁移到新 API：

```java
LargeTurbineVariants.register(gtqtcoreId("exhaust_gas"), new LargeTurbineVariant(...));
```

迁移后可以选择：

- 继续保留独立 MTE ID，维持旧存档兼容。
- 新增单 ID variant 项，作为未来推荐路径。

不要在同一次改造中删除旧 MTE ID，否则已有存档中的 `gtqtcore:large_turbine.exhaust_gas` 会丢失控制器。
