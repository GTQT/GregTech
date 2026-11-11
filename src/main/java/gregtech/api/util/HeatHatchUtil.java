package gregtech.api.util;

import gregtech.api.capability.IHeatable;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 在一组热仓（{@link IHeatable}）之间分摊一次热量变化量的工具类。
 *
 * <p>
 * 多热源多方块统一使用“总量守恒的公平分摊”：先把变化量尽量平均地分给所有仍可接受/仍可提供的热仓，
 * 当某个热仓已满（或已空）时把它从本轮分摊中剔除，并把剩余的量重新平摊给其余热仓，
 * 直到变化量分配完毕或没有热仓还能继续变化为止。
 *
 * <p>
 * 这样保证了：扣除热量时，实际扣除的总量等于 {@code min(需要扣除量, 所有热仓存量之和)}；
 * 注入热量时，实际注入的总量等于 {@code min(需要注入量, 所有热仓剩余容量之和)}。
 * 不会出现“对已经为 0 HU 的热仓也扣一份，结果总共少扣”的情况。
 *
 * <p>
 * 注意：温度与热量没有关联，温度仍然由多方块控制器按各热仓的最高温度单独取，不受本工具影响。
 */
public final class HeatHatchUtil {

    private HeatHatchUtil() {}

    /**
     * 将一次热量变化量分摊到多个热仓上，返回实际变化的总量（带符号，负数表示净扣除）。
     *
     * @param hatches 参与分摊的热仓列表，允许为 null 或空
     * @param amount  热量变化量，正数为注入，负数为扣除
     * @return 所有热仓实际变化量的总和；正数表示实际注入量，负数表示实际扣除量
     */
    public static long distribute(@Nullable List<IHeatable> hatches, long amount) {
        if (hatches == null || hatches.isEmpty() || amount == 0L) return 0L;
        if (amount > 0L) return addHeat(hatches, amount);
        if (amount == Long.MIN_VALUE) return -removeHeat(hatches, Long.MAX_VALUE);
        return -removeHeat(hatches, -amount);
    }

    /**
     * 向多个热仓注入热量，返回实际注入的总量。
     */
    public static long addHeat(List<IHeatable> hatches, long amount) {
        if (hatches == null || hatches.isEmpty() || amount <= 0L) return 0L;
        long remaining = amount;
        long accepted = 0L;
        List<IHeatable> active = new ArrayList<>(hatches.size());
        for (IHeatable hatch : hatches) {
            if (hatch != null && hatch.getHeatCapacity() > hatch.getHeatStored()) active.add(hatch);
        }
        while (remaining > 0L && !active.isEmpty()) {
            long share = Math.max(1L, remaining / active.size());
            boolean progressed = false;
            for (Iterator<IHeatable> iterator = active.iterator(); iterator.hasNext() && remaining > 0L;) {
                IHeatable hatch = iterator.next();
                long space = hatch.getHeatCapacity() - hatch.getHeatStored();
                if (space <= 0L) {
                    iterator.remove();
                    continue;
                }
                long actual = hatch.changeHeat(Math.min(Math.min(share, space), remaining));
                if (actual <= 0L) {
                    iterator.remove();
                    continue;
                }
                remaining -= actual;
                accepted += actual;
                progressed = true;
                if (hatch.getHeatCapacity() - hatch.getHeatStored() <= 0L) iterator.remove();
            }
            if (!progressed) break;
        }
        return accepted;
    }

    /**
     * 从多个热仓中扣除热量，返回实际扣除的总量（正数）。
     */
    public static long removeHeat(List<IHeatable> hatches, long amount) {
        if (hatches == null || hatches.isEmpty() || amount <= 0L) return 0L;
        long remaining = amount;
        long removed = 0L;
        List<IHeatable> active = new ArrayList<>(hatches.size());
        long totalStored = 0L;
        for (IHeatable hatch : hatches) {
            if (hatch == null) continue;
            long stored = hatch.getHeatStored();
            if (stored > 0L) {
                active.add(hatch);
                totalStored += stored;
            }
        }
        if (totalStored <= 0L) return 0L;
        remaining = Math.min(remaining, totalStored);
        while (remaining > 0L && !active.isEmpty()) {
            long share = Math.max(1L, remaining / active.size());
            boolean progressed = false;
            for (Iterator<IHeatable> iterator = active.iterator(); iterator.hasNext() && remaining > 0L;) {
                IHeatable hatch = iterator.next();
                long stored = hatch.getHeatStored();
                if (stored <= 0L) {
                    iterator.remove();
                    continue;
                }
                long actual = -hatch.changeHeat(-Math.min(share, remaining));
                if (actual <= 0L) {
                    iterator.remove();
                    continue;
                }
                remaining -= actual;
                removed += actual;
                progressed = true;
                if (hatch.getHeatStored() <= 0L) iterator.remove();
            }
            if (!progressed) break;
        }
        return removed;
    }
}
