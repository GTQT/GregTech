package gregtech.api.capability;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

public interface IGhostSlotConfigurable {

    /**
     * @return if there is a ghost circuit inventory
     */
    boolean hasGhostCircuitInventory();

    /**
     * Set ghost circuit config to given value. If the provided config value is outside of valid config range
     * (0~32), then the circuit is set to empty.
     * <p>
     * If the machine does not have circuit inventory, this method does nothing.
     *
     * @param config New config value
     */
    void setGhostCircuitConfig(int config);

    int getGhostCircuitConfig();

    /**
     * 设置任意自定义物品到 ghost slot 中。
     * 这允许可编程电路覆盖板将包裹的任意物品放入虚拟槽位。
     * 默认实现为空操作，子类需要自行覆盖。
     *
     * @param stack 要设置的自定义物品
     */
    default void setGhostCustomStack(@NotNull ItemStack stack) {
        // 默认空操作
    }
}
