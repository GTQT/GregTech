package gregtech.api.capability;

import java.util.List;

/**
 * 量子计算机 (Quantum Computer) 的 Rack 舱对外接口。
 * <p>
 * 控制器通过此接口读取舱内物品组件的算力/散热统计，并在超温时销毁计算物品。
 */
public interface IQCComponentHatch {

    /**
     * 舱内全部计算组件（computation &gt; 0）的属性列表。
     */
    List<QCComponentStats> getComputingStats();

    /**
     * 舱内组件槽位数量（本实现为 4）。
     */
    int getSlotCount();

    /**
     * 销毁一个随机的计算物品（超温惩罚）。无计算物品时不做任何事。
     *
     * @return 是否销毁了物品
     */
    boolean destroyRandomComputingComponent();
}
