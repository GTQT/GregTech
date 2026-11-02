package gregtech.api.metatileentity;

import gregtech.api.items.metaitem.stats.IUpgradeKit;
import gregtech.api.items.metaitem.stats.UpgradeKitType;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;

/**
 * 允许机器安装升级插件。
 *
 * <p>
 * 实现这个接口的机器，主界面左侧外沿那一列会多出一颗“升级插件”按钮，点开是一个浮动面板，
 * 面板里是 {@link #UPGRADE_SLOT_COUNT} 个物品槽。
 *
 * <p>
 * 接口只负责“库存从哪来”，以及按种类查找/消耗套件（{@link IUpgradeKit}）。
 * 效果由机器在对应时机触发，例如可调能源仓在过压时熔断保险丝、多方块在跳电时消耗备用电容。
 * 按钮和面板由 {@code MetaTileEntity#registerSidePanelButtons} 统一挂到侧边栏
 * （{@link gregtech.api.mui.widget.SidePanel}）上，库存的生命周期（NBT 存读、拆机掉落）由实现者负责，
 * 参考 {@code SimpleMachineMetaTileEntity} 与 {@code MetaTileEntityEnergyHatch} 的写法。
 */
public interface IUpgradeSlots {

    /** 升级插件槽位数量；面板里排成一行、水平居中。 */
    int UPGRADE_SLOT_COUNT = 4;

    /**
     * 升级插件库存。
     *
     * <p>
     * 约定是 {@link #UPGRADE_SLOT_COUNT} 个槽位、服务端持有（不用做客户端同步，槽位内容由 MUI
     * 的槽位同步自己处理），并且不参与机器的自动输入/输出。
     */
    @NotNull
    IItemHandlerModifiable getUpgradeInventory();

    /** 升级槽里是否有指定种类的套件。 */
    default boolean hasUpgradeKit(@NotNull UpgradeKitType type) {
        return findUpgradeKit(type) >= 0;
    }

    /**
     * 从升级槽里消耗一个指定种类的套件。
     *
     * @return 是否成功消耗（没有对应套件时返回 {@code false}）
     */
    default boolean consumeUpgradeKit(@NotNull UpgradeKitType type) {
        int slot = findUpgradeKit(type);
        if (slot < 0) {
            return false;
        }
        getUpgradeInventory().extractItem(slot, 1, false);
        return true;
    }

    /** @return 装着指定种类套件的第一个槽位下标；没有则返回 {@code -1} */
    default int findUpgradeKit(@NotNull UpgradeKitType type) {
        IItemHandlerModifiable inventory = getUpgradeInventory();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (IUpgradeKit.getTypeOf(stack) == type) {
                return slot;
            }
        }
        return -1;
    }
}
