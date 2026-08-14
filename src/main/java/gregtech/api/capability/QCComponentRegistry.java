package gregtech.api.capability;

import gregtech.api.GTValues;
import gregtech.api.unification.OreDictUnifier;

import net.minecraft.item.ItemStack;

/**
 * 量子计算机 (Quantum Computer) 组件属性查询。
 * <p>
 * 不维护物品注册表：直接读取物品矿辞 —— 注册了 {@code circuit} 前缀（含 tier 后缀，
 * 如 circuitEv / circuitZpm）的电路即为计算组件，算力与产热由 tier 档位决定（EV 起，逐档翻倍）。
 */
public final class QCComponentRegistry {

    // tier 档位（GTValues 电压索引）→ 单件 CWU/t；0 = 不支持该档
    private static final int[] COMPUTATION_BY_TIER = { 0, 0, 0, 0,
            GTValues.CWT[GTValues.LuV], GTValues.CWT[GTValues.ZPM], // EV, IV
            GTValues.CWT[GTValues.UV], GTValues.CWT[GTValues.UHV], // LuV, ZPM —— 项目量子电路档
            GTValues.CWT[GTValues.UEV], GTValues.CWT[GTValues.UIV], GTValues.CWT[GTValues.UXV],
            GTValues.CWT[GTValues.OpV], GTValues.CWT[GTValues.MAX], 0, 0, 0 };

    // tier 档位 → 单件满负载产热需求（水冷每 Rack 提供 16，高档电路超载升温）
    private static final int[] HEAT_BY_TIER = { 0, 0, 0, 0,
            4, 8, 16, 32,
            64, 128, 256, 512, 1024, 0, 0, 0 };

    private QCComponentRegistry() {}

    /**
     * 查询物品的组件属性。接受任何 {@code circuit} 矿辞（含 tier 后缀）的电路，
     * 档位由 unification material 名称或矿辞名解析；tier 在支持范围内才有效。
     */
    public static QCComponentStats get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        int tierIndex = resolveTier(stack);
        if (tierIndex < 0 || tierIndex >= COMPUTATION_BY_TIER.length) return null;
        int computation = COMPUTATION_BY_TIER[tierIndex];
        if (computation <= 0) return null;
        return new QCComponentStats(computation, HEAT_BY_TIER[tierIndex]);
    }

    /**
     * 该物品是否为量子计算机计算组件。
     */
    public static boolean isComponent(ItemStack stack) {
        return get(stack) != null;
    }

    /**
     * 解析电路档位：直接读矿辞名字符串（circuitMv / circuitEv / circuitZpm 等，忽略大小写）。
     */
    private static int resolveTier(ItemStack stack) {
        for (String oreName : OreDictUnifier.getOreDictionaryNames(stack)) {
            if (!oreName.startsWith("circuit")) continue;
            String tierName = oreName.substring("circuit".length());
            if (tierName.isEmpty()) continue;
            for (int i = 0; i < GTValues.VN.length; i++) {
                if (tierName.equalsIgnoreCase(GTValues.VN[i])) return i;
            }
        }
        return -1;
    }
}
