package gregtech.common.items.behaviors;

import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.common.ConfigHolder;
import gregtech.common.items.behaviors.multiblock.mover.MoverEnergyService;
import gregtech.common.items.behaviors.multiblock.mover.MoverSessionManager;
import gregtech.common.items.behaviors.multiblock.mover.MoverTargeting;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

public final class MultiblockMoverBehavior implements IItemBehaviour {

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack mover = player.getHeldItem(hand);
        if (!world.isRemote && player instanceof EntityPlayerMP serverPlayer) {
            if (player.isSneaking()) {
                MoverSessionManager.INSTANCE.cancel(serverPlayer, mover, true,
                        "gregtech.multiblock_mover.cancelled");
            } else if (MoverSessionManager.INSTANCE.hasSession(serverPlayer)) {
                MoverSessionManager.INSTANCE.confirm(serverPlayer, mover,
                        MoverTargeting.resolve(serverPlayer, null, 1.0F));
            } else {
                return pass(mover);
            }
        }
        return success(mover);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
                                           EnumFacing side, float hitX, float hitY, float hitZ,
                                           EnumHand hand) {
        if (world.isRemote) return EnumActionResult.PASS;
        if (!(player instanceof EntityPlayerMP serverPlayer)) return EnumActionResult.FAIL;
        ItemStack mover = player.getHeldItem(hand);
        if (player.isSneaking()) {
            MoverSessionManager.INSTANCE.cancel(serverPlayer, mover, true,
                    "gregtech.multiblock_mover.cancelled");
        } else if (MoverSessionManager.INSTANCE.hasSession(serverPlayer)) {
            MoverSessionManager.INSTANCE.confirm(serverPlayer, mover, pos.offset(side));
        } else {
            MoverSessionManager.INSTANCE.select(serverPlayer, mover, pos);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        lines.add(I18n.format("gregtech.multiblock_tool.mover.tooltip.1"));
        lines.add(I18n.format("gregtech.multiblock_tool.mover.tooltip.2"));
        lines.add(I18n.format("gregtech.multiblock_tool.mover.tooltip.air_target",
                ConfigHolder.multiblockMover.airTargetDistance));
        lines.add(I18n.format("gregtech.multiblock_tool.mover.tooltip.energy", MoverEnergyService.CAPACITY));
        lines.add(I18n.format("gregtech.multiblock_tool.mover.tooltip.rotation"));
    }
}
