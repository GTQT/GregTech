package gregtech.common.items.behaviors.multiblock;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public enum MultiblockToolMode {
    PROJECT(0, "project"),
    REMOVE(1, "remove"),
    MOVE(2, "move");

    private final int id;
    private final String translationSuffix;

    MultiblockToolMode(int id, String translationSuffix) {
        this.id = id;
        this.translationSuffix = translationSuffix;
    }

    public int getId() {
        return id;
    }

    public String getTranslationKey() {
        return "gregtech.multiblock_tool.mode." + translationSuffix;
    }

    public MultiblockToolMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static MultiblockToolMode get(ItemStack stack) {
        NBTTagCompound root = stack.getTagCompound();
        NBTTagCompound tag = root != null && root.hasKey(MultiblockToolNbt.ROOT, 10)
                ? root.getCompoundTag(MultiblockToolNbt.ROOT) : null;
        if (tag != null && tag.hasKey(MultiblockToolNbt.MODE, 99)) {
            int id = tag.getInteger(MultiblockToolNbt.MODE);
            for (MultiblockToolMode mode : values()) {
                if (mode.id == id) return mode;
            }
        }
        // Preserve the old metadata-1005 remover behavior in existing worlds.
        return defaultForMetadata(stack.getMetadata());
    }

    static MultiblockToolMode defaultForMetadata(int metadata) {
        return metadata == 1005 ? REMOVE : PROJECT;
    }

    public static void set(ItemStack stack, MultiblockToolMode mode) {
        stack.getOrCreateSubCompound(MultiblockToolNbt.ROOT).setInteger(MultiblockToolNbt.MODE, mode.id);
    }
}
