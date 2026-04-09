package gregtech.mixins.ae2;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.cache.CraftingGridCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(value = CraftingGridCache.class, remap = false)
public abstract class MixinCraftingGridCache {

    @Shadow
    private Set<ICraftingProvider> craftingProviders;

    @Shadow
    private boolean updatePatterns;

    @Inject(
            method = "removeNode",
            at = @At("TAIL")
    )
    private void onRemoveNode_MetaTileEntityHolder(
            IGridNode gridNode,
            IGridHost machine,
            CallbackInfo ci
    ) {
        if (machine instanceof MetaTileEntityHolder holder) {
            MetaTileEntity mte = holder.getMetaTileEntity();
            if (mte instanceof ICraftingProvider provider) {
                this.craftingProviders.remove(provider);
                this.updatePatterns = true;
            }
        }
    }
}
