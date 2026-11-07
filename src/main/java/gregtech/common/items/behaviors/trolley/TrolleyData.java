package gregtech.common.items.behaviors.trolley;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

public final class TrolleyData {

    public static final String STORED_MACHINE = "StoredMachine";
    public static final String REGISTRY_NAME = "RegistryName";
    public static final String BLOCK_STATE = "BlockState";
    public static final String BLOCK_ENTITY_NBT = "BlockEntityNBT";
    public static final String DISPLAY_KEY = "DisplayKey";

    private TrolleyData() {
    }

    public static NBTTagCompound getStored(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound()) {
            return null;
        }
        NBTTagCompound root = stack.getTagCompound();
        if (root == null || !root.hasKey(STORED_MACHINE, 10)) {
            return null;
        }
        return root.getCompoundTag(STORED_MACHINE);
    }

    public static boolean hasMachine(ItemStack stack) {
        return getStored(stack) != null;
    }

    public static ResourceLocation getRegistryName(ItemStack stack) {
        NBTTagCompound stored = getStored(stack);
        if (stored == null || !stored.hasKey(REGISTRY_NAME, 8)) {
            return null;
        }
        String raw = stored.getString(REGISTRY_NAME);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return new ResourceLocation(raw);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String getDisplayKey(ItemStack stack) {
        NBTTagCompound stored = getStored(stack);
        if (stored == null || !stored.hasKey(DISPLAY_KEY, 8)) {
            return null;
        }
        String key = stored.getString(DISPLAY_KEY);
        return key.isEmpty() ? null : key;
    }

    public static NBTTagCompound getBlockEntityNbt(ItemStack stack) {
        NBTTagCompound stored = getStored(stack);
        if (stored == null || !stored.hasKey(BLOCK_ENTITY_NBT, 10)) {
            return null;
        }
        return stored.getCompoundTag(BLOCK_ENTITY_NBT);
    }

    public static void store(ItemStack stack, NBTTagCompound stored) {
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            stack.setTagCompound(root);
        }
        root.setTag(STORED_MACHINE, stored);
    }

    public static void clear(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasTagCompound()) {
            return;
        }
        NBTTagCompound root = stack.getTagCompound();
        root.removeTag(STORED_MACHINE);
        if (root.isEmpty()) {
            stack.setTagCompound(null);
        }
    }
}
