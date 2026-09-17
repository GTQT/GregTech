package gregtech.common.command;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

/**
 * 从命令发送者取手持物品的公共逻辑。
 * 主手为空时回退副手,两者皆空则报错。
 */
public final class HandStacks {

    private HandStacks() {}

    public static @NotNull ItemStack getHeldItem(@NotNull ICommandSender sender) throws CommandException {
        if (!(sender instanceof EntityPlayerMP player)) {
            throw new CommandException("gregtech.command.material.error.not_player");
        }
        ItemStack stackInHand = player.getHeldItemMainhand();
        if (stackInHand.isEmpty()) {
            stackInHand = player.getHeldItemOffhand();
            if (stackInHand.isEmpty()) {
                throw new CommandException("gregtech.command.material.error.no_item");
            }
        }
        return stackInHand;
    }
}
