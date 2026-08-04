package gregtech.common.items.behaviors;

import gregtech.api.items.metaitem.stats.IItemBehaviour;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class OffhandPlacerBehavior implements IItemBehaviour {

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        ItemStack offhandStack = player.getHeldItemOffhand();

        if (offhandStack.isEmpty()) {
            return ActionResult.newResult(EnumActionResult.PASS, heldItem);
        }

        if (world.isRemote) {
            return ActionResult.newResult(EnumActionResult.SUCCESS, heldItem);
        }

        // Calculate the block position 1 block directly in front of the player
        Vec3d lookVec = player.getLookVec();
        BlockPos frontPos = new BlockPos(
                player.posX + lookVec.x,
                player.posY + player.getEyeHeight() + lookVec.y,
                player.posZ + lookVec.z);

        // The direction from the block back toward the player (as if clicking the near face)
        EnumFacing facing = player.getHorizontalFacing().getOpposite();

        // Try to place the offhand item at the position in front
        EnumActionResult result = offhandStack.getItem().onItemUse(
                player, world, frontPos, EnumHand.OFF_HAND, facing, 0.5f, 0.5f, 0.5f);

        if (result == EnumActionResult.SUCCESS) {
            if (!player.capabilities.isCreativeMode) {
                offhandStack.shrink(1);
            }
            return ActionResult.newResult(EnumActionResult.SUCCESS, heldItem);
        }

        return ActionResult.newResult(EnumActionResult.PASS, heldItem);
    }
}
