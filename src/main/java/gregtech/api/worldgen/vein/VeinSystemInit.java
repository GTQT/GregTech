package gregtech.api.worldgen.vein;

import gregtech.api.worldgen.config.OreDepositDefinition;
import gregtech.api.worldgen.config.WorldGenRegistry;
import gregtech.api.worldgen.filler.BlockFiller;
import gregtech.api.worldgen.filler.FillerEntry;
import gregtech.api.worldgen.filler.LayeredBlockFiller;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderEnd;
import net.minecraft.world.WorldProviderHell;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * 虚拟矿脉系统初始化入口。
 *
 * <h3>调用时序</h3>
 * <pre>
 *   CoreModule.preInit()  → VeinSystemInit.init()     // 注册 Capability + 事件
 *   CoreModule.init()     → WorldGenRegistry.initializeRegistry() // GT 解析 JSON
 *   CoreModule.postInit() → VeinSystemInit.postInit() // 同步 GT 矿脉 → VeinRegistry
 * </pre>
 */
public class VeinSystemInit {

    private static final Logger LOG = LogManager.getLogger("GregTech/VeinSystemInit");

    /**
     * GT LayeredBlockFiller 四槽位对应的虚拟权重。
     * primary（主矿）密度最高，sporadic（散布）最低。
     */
    private static final int W_PRIMARY   = 60;
    private static final int W_SECONDARY = 25;
    private static final int W_BETWEEN   = 10;
    private static final int W_SPORADIC  =  5;

    private VeinSystemInit() {}

    // ── preInit 阶段 ──────────────────────────────────────────────

    /**
     * 在 {@code CoreModule.preInit()} 末尾调用。
     * 注册 Capability 和 ChunkLoad 事件监听。
     */
    public static void init() {
        LOG.info("[VeinSystemInit] OreVeinHandler 已就绪（惰性生成，无需 Chunk 事件）。");
    }

    // ── postInit 阶段 ─────────────────────────────────────────────

    /**
     * 在 {@code CoreModule.postInit()} 中调用。
     *
     * <p>此时 {@code WorldGenRegistry.INSTANCE.initializeRegistry()} 已在
     * {@code CoreModule.init()} 阶段完成，所有 GT JSON 矿脉定义已解析完毕。
     */
    public static void postInit() {
        List<OreDepositDefinition> deposits = WorldGenRegistry.getOreDeposits();

        if (deposits.isEmpty()) {
            LOG.warn("[VeinSystemInit] getOreDeposits() 返回空集合！" +
                     "请确认 postInit() 不早于 CoreModule.init() 执行。");
            return;
        }

        int synced  = 0;
        int skipped = 0;

        for (OreDepositDefinition deposit : deposits) {
            // 过滤掉非矿脉类型（countAsVein=false，如流体脉、石头层等）
            if (!deposit.isVein()) {
                skipped++;
                continue;
            }
            try {
                VeinType vein = convertDeposit(deposit);
                if (vein == null) {
                    skipped++;
                    continue;
                }
                // 避免重复注册
                if (VeinRegistry.get(vein.id) != null) {
                    LOG.debug("[VeinSystemInit] 跳过已存在的 VeinType: {}", vein.id);
                    skipped++;
                    continue;
                }
                VeinRegistry.register(vein);
                OreVeinHandler.addOreDeposit(vein);
                synced++;
            } catch (Exception e) {
                LOG.warn("[VeinSystemInit] 转换矿脉 '{}' 失败: {}",
                        deposit.getDepositName(), e.getMessage());
                skipped++;
            }
        }

        LOG.info("[VeinSystemInit] GT 矿脉同步完成：{} 个已注册，{} 个跳过，" +
                 "VeinRegistry 共 {} 条。",
                synced, skipped, VeinRegistry.size());
    }

    // ── 核心转换逻辑 ──────────────────────────────────────────────

    private static VeinType convertDeposit(OreDepositDefinition deposit) {
        String rawName = deposit.getDepositName()
                .replace(":", "_")
                .replace("/", "_")
                .replace("\\", "_")
                .replace(" ", "_")
                .toLowerCase();
        String veinId = "gt_" + rawName;

        VeinType vein = new VeinType(veinId);

        // 1. 解析维度过滤器
        parseDimensions(vein, deposit.getDimensionFilter());

        // 2. 从 BlockFiller 提取矿物
        BlockFiller filler = deposit.getBlockFiller();
        if (filler instanceof LayeredBlockFiller layered) {
            addFromEntry(vein, layered.getPrimary(),   W_PRIMARY,   "primary");
            addFromEntry(vein, layered.getSecondary(), W_SECONDARY, "secondary");
            addFromEntry(vein, layered.getBetween(),   W_BETWEEN,   "between");
            addFromEntry(vein, layered.getSporadic(),  W_SPORADIC,  "sporadic");
        } else if (filler != null) {
            for (FillerEntry entry : filler.getAllPossibleStates()) {
                addFromEntry(vein, entry, W_PRIMARY, "simple");
            }
        }

        if (vein.getOrePool().isEmpty()) {
            LOG.debug("[VeinSystemInit] 矿脉 '{}' 无有效矿物，跳过。",
                    deposit.getDepositName());
            return null;
        }

        return vein;
    }

    // ── 矿物提取 ─────────────────────────────────────────────────

    /**
     * 从一个 FillerEntry 提取代表性矿石加入矿物池。
     *
     * <p>GT 矿石的 getPossibleResults() 返回所有石种变体
     * （stone / granite / netherrack / end_stone 等）。
     * 优先取 meta=0（普通石头层）作为代表，避免同矿物重复注册。
     */
    private static void addFromEntry(VeinType vein, FillerEntry entry,
                                     int weight, String slotName) {
        if (entry == null) return;
        Collection<IBlockState> results = entry.getPossibleResults();
        if (results == null || results.isEmpty()) return;

        IBlockState rep = pickRepresentative(results);
        if (rep == null) return;

        String oreName = toOreName(rep);
        if (oreName == null) return;

        // 同矿物已在矿物池中（同一矿物出现于多个槽位）→ 不重复添加
        for (OreEntry existing : vein.getOrePool()) {
            if (existing.oreName.equals(oreName)) {
                LOG.debug("[VeinSystemInit] 槽位 {} 的矿物 '{}' 已存在，跳过。",
                        slotName, oreName);
                return;
            }
        }

        vein.addOre(oreName, weight);
        LOG.debug("[VeinSystemInit] 槽位 {} → '{}' (权重 {})", slotName, oreName, weight);
    }

    /**
     * 从石种变体集合中选取代表性 IBlockState：
     * 1. meta=0 的 gregtech 矿石（普通石头层）—— 最优
     * 2. 任意 gregtech 矿石
     * 3. 任意有效方块（非 GT 矿石，如 ore_dict 类型）
     */
    private static IBlockState pickRepresentative(Collection<IBlockState> states) {
        IBlockState anyGt  = null;
        IBlockState anyOre = null;

        for (IBlockState state : states) {
            Block block = state.getBlock();
            ResourceLocation rl = block.getRegistryName();
            if (rl == null) continue;

            int meta = block.getMetaFromState(state);

            if ("gregtech".equals(rl.getNamespace())) {
                if (meta == 0) return state; // 最优，直接返回
                if (anyGt == null) anyGt = state;
            }
            if (anyOre == null) anyOre = state;
        }

        return anyGt != null ? anyGt : anyOre;
    }

    private static String toOreName(IBlockState state) {
        Block block = state.getBlock();
        ResourceLocation rl = block.getRegistryName();
        if (rl == null) return null;
        int meta = block.getMetaFromState(state);
        return meta == 0 ? rl.toString() : rl.toString() + ":" + meta;
    }

    // ── 维度解析 ─────────────────────────────────────────────────

    /**
     * GT dimensionFilter 的三种判断逻辑（来自 WorldConfigUtils）：
     *   "is_surface_world" → provider.isSurfaceWorld()
     *   "is_nether"        → provider instanceof WorldProviderHell
     *   "is_end"           → provider instanceof WorldProviderEnd
     *   "dimension_id:N"   → provider.getDimension() == N
     *
     * 默认值 PREDICATE_SURFACE_WORLD = WorldProvider::isSurfaceWorld（仅主世界）。
     *
     * 未命中任何原版维度 → 不设维度限制（allowedDimensions 为空 = 全维度），
     * 防止遗漏纯自定义维度矿脉。
     */
    private static void parseDimensions(VeinType vein,
                                         Predicate<WorldProvider> filter) {
        if (filter == null) return; // null = 全维度

        boolean any = false;
        if (safeTest(filter, proxyOverworld())) { vein.addDimension(0);  any = true; }
        if (safeTest(filter, proxyNether()))    { vein.addDimension(-1); any = true; }
        if (safeTest(filter, proxyEnd()))       { vein.addDimension(1);  any = true; }

        if (!any) {
            LOG.debug("[VeinSystemInit] 矿脉未命中任何原版维度，设为全维度可生成。");
        }
    }

    private static boolean safeTest(Predicate<WorldProvider> f, WorldProvider p) {
        try { return f.test(p); } catch (Exception ignored) { return false; }
    }

    // ── WorldProvider 离线代理 ────────────────────────────────────

    private static WorldProvider proxyOverworld() {
        WorldProvider p = new WorldProvider() {
            @Override public boolean isSurfaceWorld() { return true; }
            @Override public net.minecraft.util.math.Vec3d getFogColor(float a, float b) {
                return net.minecraft.util.math.Vec3d.ZERO; }
            @Override public String getSaveFolder() { return null; }

            @Override
            public DimensionType getDimensionType() {
                return null;
            }
        };
        setDimensionId(p, 0);
        return p;
    }

    private static WorldProvider proxyNether() {
        WorldProviderHell p = new WorldProviderHell();
        setDimensionId(p, -1);
        return p;
    }

    private static WorldProvider proxyEnd() {
        WorldProviderEnd p = new WorldProviderEnd();
        setDimensionId(p, 1);
        return p;
    }

    /**
     * 通过反射设置 WorldProvider.dimensionId，使 getDimension() 返回正确值。
     * 用于支持 "dimension_id:N" 类型的过滤器测试。
     */
    private static void setDimensionId(WorldProvider provider, int id) {
        try {
            Field f = WorldProvider.class.getDeclaredField("dimensionId");
            f.setAccessible(true);
            f.setInt(provider, id);
        } catch (NoSuchFieldException e) {
            LOG.debug("[VeinSystemInit] WorldProvider.dimensionId 字段未找到，" +
                      "dimension_id 类型过滤器可能无法识别自定义维度。");
        } catch (Exception ignored) {}
    }
}
