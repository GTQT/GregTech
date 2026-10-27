package gregtech.common.items.behaviors;

import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.common.ConfigHolder;
import gregtech.common.items.behaviors.multiblock.mover.MoverBinding;
import gregtech.common.items.behaviors.multiblock.mover.MoverEnergyService;
import gregtech.common.items.behaviors.multiblock.mover.MoverSessionManager;
import gregtech.common.items.behaviors.multiblock.mover.MoverTargeting;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * 多方块搬运行为：只负责把点击翻译成会话操作（选中 / 确认 / 取消），
 * 快照捕获、旋转、能量结算与事务搬迁都在 {@code multiblock/mover} 子系统中完成。
 *
 * <p>无状态：会话本体在服务端内存，物品上只留一条会话绑定凭据（见 {@link MoverBinding}）。
 */
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
        ItemStack mover = player.getHeldItem(hand);
        if (world.isRemote) {
            // 客户端只决定本次点击是否被工具消费：会话待确认，或点到的是控制器。
            // 具体动作一律由服务端执行，两侧判定条件保持一致，避免出现客户端放行、
            // 服务端却静默忽略的差异。
            return MoverBinding.hasBinding(mover) || isControllerAt(world, pos)
                    ? EnumActionResult.SUCCESS
                    : EnumActionResult.PASS;
        }
        if (!(player instanceof EntityPlayerMP serverPlayer)) return EnumActionResult.FAIL;

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

    private static boolean isControllerAt(World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof IGregTechTileEntity gregTechTile &&
                gregTechTile.getMetaTileEntity() instanceof MultiblockControllerBase;
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        lines.add(I18n.format("metaitem.tool.multiblock_mover.tooltip"));
        lines.add(I18n.format("gregtech.multiblock_tool.mover.tooltip.air_target",
                ConfigHolder.multiblockMover.airTargetDistance));
        lines.add(I18n.format("gregtech.multiblock_tool.mover.tooltip.energy", MoverEnergyService.CAPACITY));
        lines.add(I18n.format("gregtech.multiblock_tool.mover.tooltip.rotation"));
    }
}
