package gregtech.integration.tic;

import gregtech.api.items.toolitem.IGTTool;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import slimeknights.tconstruct.library.tools.TinkerToolCore;
import slimeknights.tconstruct.library.utils.ToolHelper;

/**
 * Supplements TiC's off-hand mining for GT main + TiC off combinations.
 *
 * <p>
 * Registered at {@link EventPriority#LOW} so TiC's own {@code BreakSpeed} handler fires first.
 */
public final class DualToolHandler {

    private DualToolHandler() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBreakSpeed(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        EntityPlayer player = event.getEntityPlayer();
        ItemStack mainhand = player.getHeldItemMainhand();
        ItemStack offhand = player.getHeldItemOffhand();

        if (offhand.isEmpty()) return;

        IBlockState state = event.getState();

        if (mainhand.getItem() instanceof IGTTool && offhand.getItem() instanceof TinkerToolCore) {
            if (!canHarvestForDrops(player, mainhand, state) && ToolHelper.canHarvest(offhand, state)) {
                float speed = ToolHelper.calcDigSpeed(offhand, state);
                BlockPos pos = event.getPos();
                if (pos == null || !ForgeHooks.canHarvestBlock(state.getBlock(), player, player.world, pos)) {
                    // Block hardness is divided by 100 instead of 30 when canHarvestBlock
                    // returns false; multiply to restore TiC's intended mining speed.
                    speed *= (100.0f / 30.0f);
                }
                event.setNewSpeed(speed);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;

        EntityPlayer player = event.getPlayer();
        if (player == null || player.world.isRemote) return;

        ItemStack mainhand = player.getHeldItemMainhand();
        ItemStack offhand = player.getHeldItemOffhand();

        if (offhand.isEmpty()) return;

        IBlockState state = event.getState();

        if (mainhand.getItem() instanceof IGTTool && offhand.getItem() instanceof TinkerToolCore) {
            if (!canHarvestForDrops(player, mainhand, state) && ToolHelper.canHarvest(offhand, state)) {
                offhand.getItem().onBlockDestroyed(offhand, player.world, state,
                        event.getPos(), player);
            }
        }
    }

    private static boolean canHarvestForDrops(EntityPlayer player, ItemStack stack, IBlockState state) {
        if (state.getMaterial().isToolNotRequired()) return true;
        Block block = state.getBlock();
        String toolType = block.getHarvestTool(state);
        if (stack.isEmpty() || toolType == null) {
            return player.canHarvestBlock(state);
        }
        int level = stack.getItem().getHarvestLevel(stack, toolType, null, state);
        if (level < 0) return player.canHarvestBlock(state);
        return level >= block.getHarvestLevel(state);
    }
}
