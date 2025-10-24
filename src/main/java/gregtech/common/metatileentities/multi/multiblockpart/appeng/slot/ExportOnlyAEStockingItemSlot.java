package gregtech.common.metatileentities.multi.multiblockpart.appeng.slot;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;

import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEStockingBus;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedItemStack;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

public class ExportOnlyAEStockingItemSlot extends ExportOnlyAEItemSlot {

    private final MetaTileEntityMEStockingBus holder;

        public ExportOnlyAEStockingItemSlot(IAEItemStack config, IAEItemStack stock,
            MetaTileEntityMEStockingBus holder) {
        super(config, stock);
        this.holder = holder;
    }

        public ExportOnlyAEStockingItemSlot(MetaTileEntityMEStockingBus holder) {
        super();
        this.holder = holder;
    }

    @Override
    public ExportOnlyAEStockingItemSlot copy() {
        return new ExportOnlyAEStockingItemSlot(
                this.config == null ? null : this.config.copy(),
                this.stock == null ? null : this.stock.copy(),
                this.holder);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot == 0 && this.stock != null) {
            if (this.config != null) {
                // Extract the items from the real net to either validate (simulate)
                // or extract (modulate) when this is called
                IMEMonitor<IAEItemStack> monitor = holder.getMonitor();
                if (monitor == null) return ItemStack.EMPTY;

                Actionable action = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
                IAEItemStack request;
                if (this.config instanceof WrappedItemStack wis) {
                    request = wis.getAEStack();
                } else {
                    request = this.config.copy();
                }
                request.setStackSize(amount);

                IAEItemStack result = monitor.extractItems(request, action, holder.getActionSource());
                if (result != null) {
                    int extracted = (int) Math.min(result.getStackSize(), amount);
                    this.stock.decStackSize(extracted); // may as well update the display here
                    if (this.trigger != null) {
                        this.trigger.accept(0);
                    }
                    if (extracted != 0) {
                        ItemStack resultStack = this.config.createItemStack();
                        resultStack.setCount(extracted);
                        return resultStack;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
