package gtqt.common.items.behaviors;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Programmable circuit wrapper using NBT tag "targetItem".
 */
public class ProgrammableCircuit implements IItemBehaviour {

    private static final String TAG_TARGET_ITEM = "targetItem";
    private static final String TAG_STRING_ID = "string_id";

    public ProgrammableCircuit() {
    }

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

    @NotNull
    public static ItemStack wrap(@NotNull ItemStack wrappedItem, @NotNull ItemStack circuitStack) {
        if (wrappedItem.isEmpty()) {
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

        NBTTagCompound itemTag = copy.writeToNBT(new NBTTagCompound());
        ResourceLocation registryName = copy.getItem().getRegistryName();
        if (registryName != null) {
            itemTag.setString(TAG_STRING_ID, registryName.toString());
        }

        circuitTag.setTag(TAG_TARGET_ITEM, itemTag);
        return circuitStack;
    }

    @NotNull
    public static Optional<ItemStack> getWrappedItem(@NotNull ItemStack circuitStack) {
        try {
            NBTTagCompound circuitTag = circuitStack.getTagCompound();
            if (circuitTag == null || !circuitTag.hasKey(TAG_TARGET_ITEM, Constants.NBT.TAG_COMPOUND)) {
                return Optional.empty();
            }

            NBTTagCompound itemTag = circuitTag.getCompoundTag(TAG_TARGET_ITEM).copy();

            // In 1.12, ItemStack NBT id must be string resource location.
            String stringId = itemTag.getString(TAG_STRING_ID);
            if (!stringId.isEmpty()) {
                itemTag.setString("id", stringId);
            } else if (!itemTag.hasKey("id", Constants.NBT.TAG_STRING)) {
                Item item = null;
                if (itemTag.hasKey("id", Constants.NBT.TAG_INT)) {
                    item = Item.getItemById(itemTag.getInteger("id"));
                } else if (itemTag.hasKey("id", Constants.NBT.TAG_SHORT)) {
                    item = Item.getItemById(itemTag.getShort("id"));
                }
                if (item != null && item.getRegistryName() != null) {
                    itemTag.setString("id", item.getRegistryName().toString());
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

    public static boolean hasWrappedItem(@NotNull ItemStack circuitStack) {
        if (getInstanceFor(circuitStack) == null) return false;
        NBTTagCompound tag = circuitStack.getTagCompound();
        return tag != null && tag.hasKey(TAG_TARGET_ITEM, Constants.NBT.TAG_COMPOUND);
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        Optional<ItemStack> wrapped = getWrappedItem(stack);
        if (wrapped.isPresent()) {
            ItemStack wrappedStack = wrapped.get();
            lines.add(I18n.format("metaitem.programmable_circuit.wrapped", wrappedStack.getDisplayName()));
        } else {
            lines.add(I18n.format("metaitem.programmable_circuit.empty"));
        }
        lines.add(I18n.format("metaitem.programmable_circuit.tooltip.1"));
        lines.add(I18n.format("metaitem.programmable_circuit.tooltip.2"));
        lines.add(I18n.format("metaitem.programmable_circuit.tooltip.3"));
    }
}
