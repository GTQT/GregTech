package gregtech.mixins.tic;

import gregtech.api.items.toolitem.IGTTool;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.tools.TinkerToolCore;
import slimeknights.tconstruct.library.utils.ToolHelper;

/**
 * Extends {@code EntityPlayer.canHarvestBlock} to pass when the TiC off-hand can harvest
 * a block that the GT main-hand cannot.
 */
@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayer {

    @Inject(
            method = "canHarvestBlock(Lnet/minecraft/block/state/IBlockState;)Z",
            at = @At("RETURN"),
            cancellable = true)
    private void checkOffHandForHarvest(IBlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        EntityPlayer player = (EntityPlayer) (Object) this;
        ItemStack mainhand = player.getHeldItemMainhand();
        ItemStack offhand = player.getHeldItemOffhand();
        if (offhand.isEmpty()) return;

        if (mainhand.getItem() instanceof IGTTool && offhand.getItem() instanceof TinkerToolCore) {
            if (ToolHelper.canHarvest(offhand, state)) {
                cir.setReturnValue(true);
            }
        }
    }
}
