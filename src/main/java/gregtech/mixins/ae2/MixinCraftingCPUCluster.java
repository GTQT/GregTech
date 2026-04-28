package gregtech.mixins.ae2;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import gtqt.common.items.GTQTMetaItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(CraftingCPUCluster.class)
public abstract class MixinCraftingCPUCluster {

    @Shadow(remap = false) private boolean isComplete;

    @Shadow(remap = false) private IItemList<IAEItemStack> waitingFor;

    @Shadow(remap = false) private Map<?, ?> tasks;

    @Shadow(remap = false) protected abstract void completeJob();

    /**
     * Inject fake crafting completion logic at the end of updateCraftingLogic.
     * When no remaining tasks and waitingFor only has paper, treat as crafting complete.
     */
    @Inject(method = "updateCraftingLogic", at = @At("RETURN"), remap = false)
    private void onUpdateCraftingLogic(IGrid grid, IEnergyGrid eg, CraftingGridCache cc, CallbackInfo ci) {
        if (!isComplete && tasks.isEmpty() && waitingFor.size() == 1) {
            IAEItemStack only = waitingFor.iterator().next();
            if (only != null) {
                ItemStack stack = only.getDefinition();
                Item item = stack.getItem();
                if (item == GTQTMetaItems.GTQT_META_ITEM && stack.getMetadata() == GTQTMetaItems.ORDER.getMetaValue()) {
                    completeJob();
                }
            }
        }
    }
}
