package gregtech.common.items.behaviors.trolley;

import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.items.metaitem.stats.IItemComponent;
import gregtech.api.items.metaitem.stats.IItemModelDispatcher;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

public class TrolleyBehaviour implements IItemComponent, IItemBehaviour, IItemModelDispatcher {

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        String displayKey = TrolleyData.getDisplayKey(itemStack);
        if (displayKey == null) {
            lines.add(I18n.format("metaitem.tool.trolley.empty"));
        } else {
            lines.add(I18n.format("metaitem.tool.trolley.loaded", I18n.format(displayKey)));
        }
        lines.add(I18n.format("metaitem.tool.trolley.tooltip"));
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        return new ActionResult<>(EnumActionResult.PASS, PlayerHeld.get(player, hand));
    }

    @Override
    public ActionResult<ItemStack> onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                             EnumFacing facing, float hitX, float hitY, float hitZ) {
        ItemStack stack = PlayerHeld.get(player, hand);
        EnumActionResult result = player.isSneaking() ? TrolleyHelper.pickUp(world, pos, stack, player) : TrolleyHelper.place(world, pos, facing, stack, player);
        return new ActionResult<>(result, stack);
    }

    @Override
    public int getModelIndex(ItemStack itemStack, int maxIndex) {
        // model 1 (index 0) = empty trolley, model 2 (index 1) = trolley holding a machine
        return TrolleyData.hasMachine(itemStack) ? Math.min(1, maxIndex) : 0;
    }

    public static final class PlayerHeld {

        private PlayerHeld() {
        }

        public static ItemStack main(EntityPlayer player) {
            return player.getHeldItemMainhand();
        }

        public static ItemStack off(EntityPlayer player) {
            return player.getHeldItemOffhand();
        }

        public static ItemStack get(EntityPlayer player, EnumHand hand) {
            return player.getHeldItem(hand);
        }

        public static boolean sneaking(EntityPlayer player) {
            return player.isSneaking();
        }
    }
}
