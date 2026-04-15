package gtqt.common.items.behaviors;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 可编程电路行为类。
 * 通过 NBT 的 "targetItem" 标签存储任意 ItemStack，
 * 模仿 Programmable-Hatches-Mod 的 wrap/unwrap 机制。
 */
public class ProgrammableCircuit implements IItemBehaviour {

    // ==================== NBT 常量 ====================
    private static final String TAG_TARGET_ITEM = "targetItem";
    private static final String TAG_STRING_ID = "string_id";

    public ProgrammableCircuit() {
    }

    // ==================== 静态工具方法 ====================

    /**
     * 从 ItemStack 的 Behaviour 中获取 ProgrammableCircuit 实例。
     */
    @Nullable
    public static ProgrammableCircuit getInstanceFor(@NotNull ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof MetaItem)) return null;
        MetaItem<?>.MetaValueItem valueItem = ((MetaItem<?>) itemStack.getItem()).getItem(itemStack);
        if (valueItem == null) return null;
        for (IItemBehaviour behaviour : valueItem.getBehaviours()) {
            if (behaviour instanceof ProgrammableCircuit) {
                return (ProgrammableCircuit) behaviour;
            }
        }
        return null;
    }

    /**
     * 将任意 ItemStack 包裹到可编程电路中。
     * 被包裹物品的完整 NBT 信息会被存储到可编程电路的 NBT 中。
     *
     * @param wrappedItem   要包裹的物品
     * @param circuitStack  可编程电路的 ItemStack（会被直接修改）
     * @return 修改后的可编程电路 ItemStack
     */
    @NotNull
    public static ItemStack wrap(@NotNull ItemStack wrappedItem, @NotNull ItemStack circuitStack) {
        if (wrappedItem.isEmpty()) {
            // 清除包裹
            if (circuitStack.hasTagCompound()) {
                circuitStack.getTagCompound().removeTag(TAG_TARGET_ITEM);
            }
            return circuitStack;
        }

        ItemStack copy = wrappedItem.copy();
        copy.setCount(1);

        NBTTagCompound circuitTag = circuitStack.getTagCompound();
        if (circuitTag == null) {
            circuitTag = new NBTTagCompound();
            circuitStack.setTagCompound(circuitTag);
        }

        // 将被包裹物品序列化到 NBT
        NBTTagCompound itemTag = copy.writeToNBT(new NBTTagCompound());
        // 使用字符串 ID 代替数字 ID，避免跨存档时 ID 不匹配
        ResourceLocation registryName = copy.getItem().getRegistryName();
        if (registryName != null) {
            itemTag.setString(TAG_STRING_ID, registryName.toString());
        }

        circuitTag.setTag(TAG_TARGET_ITEM, itemTag);
        return circuitStack;
    }

    /**
     * 从可编程电路中读取被包裹的 ItemStack。
     *
     * @param circuitStack 可编程电路的 ItemStack
     * @return 被包裹的物品，如果没有包裹则返回 Optional.empty()
     */
    @NotNull
    public static Optional<ItemStack> getWrappedItem(@NotNull ItemStack circuitStack) {
        try {
            NBTTagCompound circuitTag = circuitStack.getTagCompound();
            if (circuitTag == null || !circuitTag.hasKey(TAG_TARGET_ITEM)) {
                return Optional.empty();
            }

            NBTTagCompound itemTag = circuitTag.getCompoundTag(TAG_TARGET_ITEM).copy();

            // 优先使用字符串 ID 恢复物品
            String stringId = itemTag.getString(TAG_STRING_ID);
            if (!stringId.isEmpty()) {
                Item item = Item.getByNameOrId(stringId);
                if (item != null) {
                    // 确保使用字符串 ID 对应的注册 ID
                    itemTag.setShort("id", (short) Item.getIdFromItem(item));
                }
            }

            ItemStack result = new ItemStack(itemTag);
            if (result.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    /**
     * 判断给定的 ItemStack 是否是一个有效的可编程电路（带有包裹物品）。
     */
    public static boolean hasWrappedItem(@NotNull ItemStack circuitStack) {
        if (getInstanceFor(circuitStack) == null) return false;
        NBTTagCompound tag = circuitStack.getTagCompound();
        return tag != null && tag.hasKey(TAG_TARGET_ITEM);
    }

    // ==================== IItemBehaviour 方法 ====================

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        Optional<ItemStack> wrapped = getWrappedItem(stack);
        if (wrapped.isPresent()) {
            ItemStack wrappedStack = wrapped.get();
            lines.add(I18n.format("metaitem.programmable_circuit.wrapped",
                    wrappedStack.getDisplayName()));
        } else {
            lines.add(I18n.format("metaitem.programmable_circuit.empty"));
        }
        lines.add(I18n.format("metaitem.programmable_circuit.tooltip.1"));
        lines.add(I18n.format("metaitem.programmable_circuit.tooltip.2"));
        lines.add(I18n.format("metaitem.programmable_circuit.tooltip.3"));
    }
}
