package gregtech.api.items.metaitem.stats;

import gregtech.api.items.metaitem.MetaItem;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 升级套件的物品行为：让物品能被升级槽识别为某一种一次性套件。
 *
 * <p>
 * 识别与消耗的逻辑在 {@link gregtech.api.metatileentity.IUpgradeSlots} 里，
 * 具体物品见 {@code gregtech.common.items.behaviors.UpgradeKitBehavior}。
 */
public interface IUpgradeKit extends IItemBehaviour {

    /** @return 该物品属于哪一种升级套件。 */
    @NotNull
    UpgradeKitType getType();

    /**
     * 读取物品栈上声明的升级套件种类。
     *
     * @param stack 待检查的物品栈
     * @return 套件种类；不是套件（或不是 GT meta item）时返回 {@code null}
     */
    @Nullable
    static UpgradeKitType getTypeOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof MetaItem<?> metaItem)) {
            return null;
        }
        for (IItemBehaviour behaviour : metaItem.getBehaviours(stack)) {
            if (behaviour instanceof IUpgradeKit kit) {
                return kit.getType();
            }
        }
        return null;
    }
}
