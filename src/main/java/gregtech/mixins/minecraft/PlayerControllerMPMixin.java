package gregtech.mixins.minecraft;

import gregtech.common.items.behaviors.VajraBehavior;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Resolves the phantom interact problem of the Vajra: the client keeps "damaging" the block it
 * already destroyed through {@link VajraBehavior#breakBlock} until the block-change packet makes
 * the target position air, which can instantly dig through to the block behind or repeat break
 * actions while the button is held. Once the target position turns into air, further
 * {@code onPlayerDamageBlock} calls are ignored until the player releases the attack button.
 * Mirrors the Laser Destroyer fix in GregTech Lite Core.
 */
@Mixin(PlayerControllerMP.class)
public abstract class PlayerControllerMPMixin {

    @Shadow
    protected Minecraft mc;
    @Shadow
    private BlockPos currentBlock;
    @Shadow
    private boolean isHittingBlock;

    @Unique
    private boolean vajra$needsRelease;

    @Inject(method = "onPlayerDamageBlock",
            at = @At("HEAD"),
            cancellable = true)
    private void vajra$lockAfterBreak(BlockPos posBlock, EnumFacing directionFacing,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!gregTech$isHoldingVajra()) return;

        if (vajra$needsRelease) {
            cir.setReturnValue(false);
            return;
        }

        if (posBlock.equals(currentBlock) && isHittingBlock && mc.world.isAirBlock(posBlock)) {
            vajra$needsRelease = true;
            isHittingBlock = false;
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "resetBlockRemoving",
            at = @At("HEAD"))
    private void vajra$onRelease(CallbackInfo ci) {
        vajra$needsRelease = false;
    }

    @Unique
    private boolean gregTech$isHoldingVajra() {
        ItemStack item = mc.player.getHeldItemMainhand();
        return VajraBehavior.isVajra(item);
    }
}
