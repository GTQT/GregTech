package gregtech.mixins.ae2;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.item.IMixedStackList;
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

    @Shadow(remap = false) private IMixedStackList waitingFor;

    @Shadow(remap = false) private Map<?, ?> tasks;

    @Shadow(remap = false) protected abstract void completeJob();

    /**
     * 在 updateCraftingLogic 返回前注入假合成完成逻辑
     * 当没有剩余任务、等待列表仅剩纸时，视为合成完成
     */
    @Inject(method = "updateCraftingLogic", at = @At("RETURN"), remap = false)
    private void onUpdateCraftingLogic(IGrid grid, IEnergyGrid eg, CraftingGridCache cc, CallbackInfo ci) {
        if (!isComplete && tasks.isEmpty() && waitingFor.size() == 1) {
            IAEStackBase only = waitingFor.iterator().next();
            if (only instanceof IAEItemStack itemStack) {
                ItemStack stack = itemStack.getDefinition();
                Item item = stack.getItem();
                if (item == GTQTMetaItems.GTQT_META_ITEM && stack.getMetadata() == GTQTMetaItems.ORDER.getMetaValue()) {
                    completeJob();
                }
            }
        }
    }
}
