package gregtech.common.items.behaviors.multiblock.mover;

import gregtech.common.items.behaviors.multiblock.MultiblockToolNbt;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 搬运会话与物品的绑定。
 *
 * <p>会话 id 写在物品 NBT 上：物品被丢弃、替换或清空时会话自然失效。
 * 本类不依赖具体物品类型，只认自己写入的会话 id，
 * 因此搬运子系统不会反向依赖物品/行为层。
 */
public final class MoverBinding {

    private static final String KEY_ID = "Id";
    private static final String KEY_DIMENSION = "Dimension";
    private static final String KEY_CONTROLLER = "Controller";

    private MoverBinding() {}

    /** 把会话写进物品，作为“当前会话绑定在这件物品上”的凭据。 */
    public static void bind(@NotNull ItemStack stack, @NotNull MoverSession session) {
        NBTTagCompound tag = stack.getOrCreateSubCompound(MultiblockToolNbt.SESSION);
        tag.setUniqueId(KEY_ID, session.getId());
        tag.setInteger(KEY_DIMENSION, session.getSnapshot().getDimension());
        tag.setLong(KEY_CONTROLLER, session.getSnapshot().getControllerPos().toLong());
    }

    /** 物品上是否带有任意会话绑定（客户端据此判断本次点击会被“确认”消费）。 */
    public static boolean hasBinding(@Nullable ItemStack stack) {
        NBTTagCompound tag = sessionTag(stack);
        return tag != null && tag.hasUniqueId(KEY_ID);
    }

    public static boolean matches(@Nullable ItemStack stack, @NotNull MoverSession session) {
        UUID bound = boundSessionId(stack);
        return bound != null && bound.equals(session.getId());
    }

    @Nullable
    public static UUID boundSessionId(@Nullable ItemStack stack) {
        NBTTagCompound tag = sessionTag(stack);
        return tag != null && tag.hasUniqueId(KEY_ID) ? tag.getUniqueId(KEY_ID) : null;
    }

    public static void clear(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) return;
        root.removeTag(MultiblockToolNbt.SESSION);
        if (root.getKeySet().isEmpty()) stack.setTagCompound(null);
    }

    /** 在玩家背包（主手/副手/热键栏）里找回绑定当前会话的那件物品。 */
    @NotNull
    public static ItemStack findBound(@NotNull EntityPlayerMP player, @NotNull MoverSession session) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (matches(stack, session)) return stack;
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            if (matches(stack, session)) return stack;
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    private static NBTTagCompound sessionTag(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NBTTagCompound root = stack.getTagCompound();
        if (root == null || !root.hasKey(MultiblockToolNbt.SESSION, 10)) return null;
        return root.getCompoundTag(MultiblockToolNbt.SESSION);
    }
}
