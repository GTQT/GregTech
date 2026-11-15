# Farmer's Future Delight 矿物系统兼容

## 背景

Farmer's Future Delight（下称 FFD）的「洞穴与山崖」世界类型（`ffd_cac`）把主世界高度扩展到
**Y=-64..319**（`xy177.farmersfuturedelight.core.FFDHeightHooks`；安装 CaveBiomesAPI 或 Depths Update 时，
普通主世界也会被扩展）。FFD 自己在深部生成深板岩，并把石头层矿石替换成深板岩矿石。

GregTech 的矿脉系统（`gregtech.api.worldgen`）按「3×3 区块网格 → 每个网格抽取矿脉 → shape 生成方块 →
filler 按矿石基底（StoneType）替换」工作，并且假设世界只有 Y=0..255：

- `CachedGridEntry` 丢弃 `y<=0` 的矿脉方块；
- 直接读写区块存储时用 `y>>4` 作为 section 下标、只接受 `0..255`；
- 矿脉定义里的 `minHeight`/`maxHeight` 是绝对 Y。

同时 `BlockOre` 的每个基底是 `STONE_TYPE` 属性的一个取值，而 GT 没有深板岩基底，所以 GT 矿脉无法在
FFD 的深板岩里生成。

## 实现

### 1. 深板岩矿石基底

| 位置 | 内容 |
| --- | --- |
| `gregtech/api/unification/ore/StoneTypes.java` | `StoneTypes.DEEPSLATE`：仅当 FFD 已加载时创建；id 由 `StoneType.Builder.create("deepslate")` 向 `StoneType.getStoneTypeID()` 领取（内置 0..11 已预留，附属再往后领），`stone` 为 FFD 深板岩方块状态，`stoneMaterial` 为 `Materials.Deepslate`，`processingPrefix` 为 `OrePrefix.oreDeepslate` |
| `gregtech/api/unification/material/materials/SecondDegreeMaterials.java` | 新材质 `Materials.Deepslate`（id 2021，`dust()`，`NO_SMASHING` + `DECOMPOSITION_BY_CENTRIFUGING`，`SiO2*4 + Biotite`，颜色 `0x797979`），与 GT-Modern 的定义一致 |
| `gregtech/api/unification/ore/OrePrefix.java` | 新增 `oreDeepslate` / `oreDeepslateLean`，并登记到 `leanCounterparts` |
| `gregtech/integration/ffd/FFDDeepslateCompat.java` | 直接链接 FFD（compileOnly）：经 `FFDModernStoneProvider.get().deepslate()` 取 FFD 实际生成的深板岩状态（`deepslateMode=AUTO` 时可能交给其他模组），未注册时回退 `FFDBlocks.DEEPSLATE`；都没有则回退石头状态，使矿石变体仍可烘焙。FFD 的类只在 `Mods.FarmersFutureDelight.isModLoaded()` 为真时由内部 holder 加载，卸载 FFD 不会触发 `NoClassDefFoundError` |
| `gregtech/common/CommonProxy.java` | 无需改动：所有 `PropertyKey.ORE` 材料会自动获得深板岩矿石变体与 lean 变体（`BlockOre` 按 `stoneType.id/16` 分组） |
| `gregtech/client/model/OreBakedModel.java` | 无需改动：矿石模型 = 基底方块模型 + 材质叠加层，深板岩基底的模型自动正确 |

### 1b. 凝灰岩矿石基底

与深板岩同一套机制，只是换 `OrePrefix.oreTuff` / `FFDBlocks.TUFF`：

| 位置 | 内容 |
| --- | --- |
| `gregtech/api/unification/ore/StoneTypes.java` | `StoneTypes.TUFF`：仅当 FFD 已加载时创建；id 由 `Builder.create("tuff")` 领取（排在 deepslate 之后）；`stone` 为 FFD 凝灰岩状态，`stoneMaterial` 为 `Materials.Tuff`，`predicate` 为 `FFDDeepslateCompat::isTuff` |
| `gregtech/api/unification/material/materials/SecondDegreeMaterials.java` | 新材质 `Materials.Tuff`（id 2025，`dust()`，`NO_SMASHING`，`Ash*2 + PotassiumFeldspar`，颜色 `0x75756A`），与 GT-Modern 的定义一致 |
| `gregtech/api/unification/ore/OrePrefix.java` | 新增 `oreTuff` / `oreTuffLean`，并登记到 `leanCounterparts` |
| `gregtech/integration/ffd/FFDDeepslateCompat.java` | `tuffState()`：`FFDModernStoneProvider.get().tuff()` 优先，回退 `FFDBlocks.TUFF`；`isTuff(IBlockState)` 按方块比较 |
| `assets/gregtech/lang/*.lang` | `item.material.oreprefix.oreTuff` / `oreTuffLean`（英文 `Tuff %s Ore` / `Lean Tuff %s Ore`，中文 `凝灰岩%s矿石` / `贫瘠凝灰岩%s矿石`） |

凝灰岩层的 GT 矿脉会生成凝灰岩矿石变体，lean 矿生成贫瘠凝灰岩变体；矿石模型沿用基底方块 + 材质叠加层，无需新贴图。

### 1c. StoneType id 领取（供附属模组）

`gregtech/api/unification/ore/StoneType.java`：

- `public static int getStoneTypeID()`：返回注册表里最小的空闲 id，并立即预留；内置 0..11 由 `StoneTypes.BUILTIN_STONE_TYPE_COUNT` 预留，附属从 12 起按初始化顺序领取；只在模组初始化阶段（单线程）调用，不加锁；
- `StoneType.Builder.create(String name)`：等价于 `create(StoneType.getStoneTypeID(), name)`；
- 显式 `Builder.create(int id, String name)` 仍保留，但会占用该 id。附属应改用领取式，避免多个模组抢 12..19 造成重复注册异常。

### 2. 拓展高度

`gregtech/api/worldgen/generator/WorldHeightCompat.java`：

- 先按世界类型名 `ffd_cac` 判定（不受 FFD 的线程局部高度作用域影响），再直接链接 `FFDHeightHooks.minY(World)`（`usesExtendedHeight` 是私有方法，`minY < 0` 即拓展高度；也覆盖 Cave Biomes API / Depths Update 为任意主世界开启的高度扩展）；
- 只缓存 `true`：FFD 会在「其它模组的 `IWorldGenerator`」外面套一层 legacy 高度作用域（`Chunk#populate` 里的 `GameRegistry.generateWorld` 已被它改写），此时 `minY` 对拓展世界也返回 0，缓存 false 会把结论永久钉错；
- `gregtech/api/worldgen/generator/WorldGeneratorImpl.java` 在生成期间用 `WorldHeightCompat.enterExtendedWorldgen(world)` 反向进入 FFD 的 extended 高度作用域（与 FFD 自己的 `FFDWorldGenerator` 做法一致）：否则 legacy 作用域下 `World#setBlockState` 会把 Y<0 的写入直接丢弃，基岩流体泉也会因为 Y=-64 处的写入被丢弃而完全不生成；
- 结果按世界缓存；`minY(world)`、`maxYExclusive(world)`、`isExtended(world)`；
- 普通世界 → `0 / 256`，FFD 世界 → `-64 / 320`。

`gregtech/api/worldgen/config/OreDepositBuilder.java` / `OreDepositDefinition.java`：

- 每条矿脉注册两个高度范围：普通世界的 `minHeight/maxHeight`，与拓展高度世界的 `deepYields(a, b)`；
- `deepYields` 未显式指定时，`build()` 用 `WorldHeightCompat.scaleToExtendedHeight` 把普通范围按
  `-64 + y * 384 / 256` 映射（Y=0..255 → -64..319，保持相对深度；`MIN_VALUE/MAX_VALUE` 原样保留）；
- `OreDepositDefinition#getHeightLimit(boolean extended)` 按世界类型返回对应范围，JEI 等信息界面仍读普通范围。

`gregtech/api/worldgen/generator/CachedGridEntry.java`：

- 构造时记录 `minY`；
- `doGenerateVein` 按 `WorldHeightCompat.isExtended(world)` 选择 `getHeightLimit(extended)`：
  `minimumHeight = max(minY + 3, heightLimit[0])`、
  `maximumHeight = min(masterEntry.getMaxBottomHeight(), heightLimit[1] - topHeightOffset)`；
  普通世界下界仍是 3 格，拓展高度世界放宽到 `minY + 3`（FFD 为 −61），所以负 Y 可以落矿脉；
- 矿脉形状（层数 / 半径）由 shape generator 按方块数生成，不读世界高度，因此单个矿脉不会被拉长；
- `setBlock` 的守卫从 `y>0` 改为 `y>minY`；
- `ChunkDataEntry` 用 `(y - minY)` 的 16 位偏移打包/解包，负 Y 不再丢失；
- 拓展高度世界直接走 `World#getBlockState` / `World#setBlockState`：FFD 对 section 下标做了重映射
  （`FFDHeightHooks.storageIndex`），不能再用 `storage[y>>4]`；普通世界仍走原来的快速直读路径；
- `ChunkDataEntry#populateChunk` 按矿脉 `priority` 降序写入（同级按名称稳定排序）：方块按定义缓存在
  `Object2ObjectOpenHashMap` 里，原来按哈希顺序写，石材球可能晚于穿过它的矿脉写入，filler 读到的
  基底还是石头 → 正常矿石的基底不跟着变（贫瘠矿在所有写入之后才扫描周边，所以看起来“正常”）。
- `WorldGeneratorImpl` 分两趟写一个区块：先把 3×3 网格里所有非矿脉定义（石材球 / 凝灰岩球 / 油球等
  `countAsVein(false)`）都写完，再写所有矿脉。石材球和矿脉经常属于不同的 `CachedGridEntry`，
  只靠单个 entry 内部排序无法保证“球先于脉”，会出现深板岩矿脉残留在大理岩球里。

### 3. 移除 FFD 的深板岩矿物生成

`gregtech/mixins/ffd/WorldGenModernOresMixin.java`：

```java
@Mixin(value = WorldGenModernOres.class, remap = false)
public class WorldGenModernOresMixin {
    @Redirect(method = "replacement",
              at = @At(value = "INVOKE",
                       target = "Lxy177/farmersfuturedelight/common/worldgen/FFDModernStoneProvider;isDeepBase(Lnet/minecraft/block/state/IBlockState;)Z"))
    private boolean gregtech$skipFfdDeepslateOres(FFDModernStoneProvider stones, IBlockState state) {
        return false;
    }
}
```

`WorldGenModernOres.replacement` 只有在 `baseStone = current.getBlock() == Blocks.STONE || deep` 时返回方块，
`deep` 被固定为 `false` 后，深板岩位置的替换返回 `null` → FFD 跳过自己的深板岩矿石与凝灰岩/石材团簇；
石头层矿石、含水层、洞穴、紫水晶、垂直群系等都不受影响。

### 3b. 禁用 FFD 自己的矿石生成

FFD 的矿石不经过原版 `OreGenEvent.GenerateMinable`：它在自己的区块生成器内部调用
`ChunkGeneratorExtended.generateModernOres` → `WorldGenModernOres.generateChunk(int, int, Access)`（modern 引擎走
`ChunkGeneratorModern` / `ModernTerrainPipeline`，同样是内部调用）。因此
`ConfigHolder.worldgen.disableVanillaOres`（只拒绝原版 `BiomeDecorator`/`WorldGenMinable` 的事件）**拦不到它**；
FFD 也没有总开关（`modernOreVeinSizePercent=0` 只会把矿脉缩到 1 格，不会关掉）。

```java
@Inject(method = "generateChunk", at = @At("HEAD"), cancellable = true)
private void gregtech$skipFfdOreGeneration(int chunkX, int chunkZ, WorldGenModernOres.Access access,
                                           CallbackInfo callbackInfo) {
    if (ConfigHolder.worldgen.disableVanillaOres) callbackInfo.cancel();
}
```

- `generateChunk` 是 FFD 矿石与石材团块的唯一入口，取消后 FFD 不再放任何矿石，也不再放
  dirt/gravel/granite/diorite/andesite/tuff 团块；
- granite/diorite/andesite/tuff 的团块 GT 本来就有 sphere，dirt/gravel 不再生成；
- 开关关闭时 FFD 矿石与团块照旧生成，但上面的深板岩矿石、凝灰岩团块两个 redirect 仍然生效。

### 4. 加载与依赖

- `src/main/resources/mixins.gregtech.ffd.json`：package `gregtech.mixins.ffd`，refmap `mixins.gregtech.refmap.json`；
- `GregTechLateMixinLoadingPlugin`：配置列表加入该文件，`shouldMixinConfigQueue` 只在
  `Mods.FarmersFutureDelight.isModLoaded()` 时排队；
- `Mods.FarmersFutureDelight`（`gregtech/api/util/Mods.java`，id `farmers_future_delight`）；
- `build.gradle`：`compileOnly rfg.deobf(files('libs/farmers-future-delight-1.6.0.jar'))`，仅编译期使用，
  FFD 类不会打进发布 jar。

## 行为

- FFD 世界（Y=-64..319）：GT 矿脉使用各自注册的 `deepYields`（未指定时由普通范围按 `-64 + y * 1.5` 自动派生，
  例如 diamond `5..20` → `-56..-34`）；深板岩层中的 GT 矿脉生成深板岩矿石变体
  （`BlockOre` 的 `stoneType=deepslate`），且 lean 矿石也会使用深板岩 lean 变体；
- `ConfigHolder.worldgen.disableVanillaOres` 开启时，FFD 自己的矿石生成整段被取消（见 3b），GT 用 `deepYields`
  在深板岩层生成自己的矿石；关闭时 FFD 的石头层矿石照旧生成（仅深板岩矿与凝灰岩团块仍被屏蔽）；
- 泥土/沙砾团块：FFD 的这两种团块随它的矿石生成一起被取消；
- 石头球（`OreDepositBuilder.sphereFill`，GT 平滑石球与原版 dirt/gravel/granite/diorite/andesite 球共用）
  只替换天然石头与 FFD 深板岩，因此深板岩层也会被替换成球体方块，不会灌进洞穴/水里；
- `ConfigHolder.worldgen.disableVanillaOres` 开启时，除原版矿石外还会一并禁用原版的地下
  泥土/沙砾/闪长岩/花岗岩/安山岩团块（它们在 `BiomeDecorator` 里同样注册为矿石生成条目），
  避免 GT/FFD 世界里继续散落原版团块；
- 凝灰岩团块由 GT 接管：检测到 FFD 凝灰岩方块时注册 `overworld/tuff_sphere`（普通世界 `0..30`，
  拓展高度派生为 `-64..-19`，只替换石头/深板岩），同时通过 mixin 关掉 FFD 自己的凝灰岩团块生成
  （`WorldGenModernOres#generateFromOrigin` 里的 `hasTuff()` 判定）；
- 原油球在拓展高度世界下压到 `deepYields(-30, 0)`：球心 -30..-17、球半径最大 13，球体顶部最高 -4，
  整个油球埋在深板岩附近；`fluid_spring` 泉柱仍从球心通到地表（普通世界仍是 `10..40`）；
- 普通世界（Y=0..255）行为完全不变：使用原来的 `minHeight/maxHeight`；
- 矿石探测仪（`ProspectorMode.ORE`）的扫描范围改为「世界底部 → 区块高度图顶端」：`WidgetProspectingMap`
  用 `WorldHeightCompat.minY(world)` 作为起点，不再固定从 `Y=1` 开始，因此拓展高度世界里 Y<0 的矿脉
  也会显示；`PacketProspecting` / `ProspectingTexture` 的 Y 键由 `byte` 改为 `short` / `Integer`，避免
  Y 256..319 溢出；基岩流体/基岩矿两种模式按区块能力查询，与高度无关，未改动。

## 限制与注意

- 新增 `BlockOre` 的 `STONE_TYPE` 取值会改变已有存档的方块状态 ID，**建议新建世界**；
- 自动派生的 `deepYields` 只是按比例映射；个别矿脉（例如原版只在高山出现的矿）建议显式写
  `.deepYields(a, b)` 调整到深板岩层，不写则保持相对深度；
- FFD 的 `WorldGenModernOres` 是私有实现，若 FFD 改方法名，mixin 会失效并在日志中报 mixin 应用失败，
  GT 矿脉仍可生成（只是 FFD 深板岩矿会回来）；
- 仅当世界确实扩展了高度（`usesExtendedHeight` 为真）时才走拓展高度逻辑，普通世界零开销；
- GT 的深板岩矿石基底依赖 FFD 的深板岩方块；FFD 关闭深板岩内容时该基底不会匹配任何方块。

## 验证

- `./gradlew build -x test` 通过；
- 发布 jar 包含 `mixins.gregtech.ffd.json`、`gregtech/mixins/ffd/WorldGenModernOresMixin.class`、
  `gregtech/integration/ffd/FFDDeepslateCompat.class`，不包含任何 `xy177/` 类；
- 生产环境：创建「洞穴与山崖」世界，F3 在 Y<0 处应能看到 GT 深板岩矿石；FFD 的深板岩矿石（原版风格
  煤炭/铁等贴图）不应再出现。
