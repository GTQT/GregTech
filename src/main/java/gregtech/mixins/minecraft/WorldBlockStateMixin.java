package gregtech.mixins.minecraft;

import gregtech.api.metatileentity.multiblock.MultiblockWorldData;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplements Forge events by catching setBlockState calls that don't fire NeighborNotifyEvent.
 * For example, setBlockState with flags=2 (only sync to client, no neighbor notify).
 * Uses @Inject at RETURN which is safe and compatible with other mods' mixins.
 */
@Mixin(World.class)
public abstract class WorldBlockStateMixin {

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
            at = @At("RETURN"))
    private void gregtech$onSetBlockState(BlockPos pos, IBlockState newState, int flags,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        World self = (World) (Object) this;
        if (self.isRemote) return;

        // Only notify for flags that DON'T include neighbor notify (flag & 1),
        // because those are already covered by Forge's NeighborNotifyEvent.
        // This avoids duplicate notifications.
        if ((flags & 1) == 0) {
            MultiblockWorldData.get(self).onBlockChanged(pos);
        }
    }
}
