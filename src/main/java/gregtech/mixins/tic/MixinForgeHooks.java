package gregtech.mixins.tic;

import gregtech.api.items.toolitem.IGTTool;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.ForgeHooks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.tools.TinkerToolCore;
import slimeknights.tconstruct.library.utils.ToolHelper;

/**
 * Extends {@code ForgeHooks.canHarvestBlock} to pass when the TiC off-hand can harvest
 * a block that the GT main-hand cannot.
 */
@Mixin(value = ForgeHooks.class, remap = false)
public class MixinForgeHooks {

    @Inject(method = "canHarvestBlock(Lnet/minecraft/block/Block;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)Z",
            at = @At("RETURN"),
            cancellable = true)
    private static void checkOffHandTool(Block block, EntityPlayer player, IBlockAccess world,
                                         BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (pos == null) return;

        ItemStack mainhand = player.getHeldItemMainhand();
        ItemStack offhand = player.getHeldItemOffhand();
        if (offhand.isEmpty()) return;

        if (mainhand.getItem() instanceof IGTTool && offhand.getItem() instanceof TinkerToolCore) {
            IBlockState state = world.getBlockState(pos);
            if (ToolHelper.canHarvest(offhand, state)) {
                cir.setReturnValue(true);
            }
        }
    }
}
