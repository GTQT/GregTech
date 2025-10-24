package gregtech.common.metatileentities.multi.multiblockpart.appeng.slot;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEStockingHatch;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedFluidStack;

import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

public class ExportOnlyAEStockingFluidSlot extends ExportOnlyAEFluidSlot {

    public ExportOnlyAEStockingFluidSlot(MetaTileEntityMEStockingHatch holder, IAEFluidStack config,
                                         IAEFluidStack stock, MetaTileEntity entityToNotify) {
        super(holder, config, stock, entityToNotify);
    }

    public ExportOnlyAEStockingFluidSlot(MetaTileEntityMEStockingHatch holder, MetaTileEntity entityToNotify) {
        super(holder, entityToNotify);
    }

    @Override
    protected MetaTileEntityMEStockingHatch getHolder() {
        return (MetaTileEntityMEStockingHatch) super.getHolder();
    }

    @Override
    public ExportOnlyAEFluidSlot copy() {
        return new ExportOnlyAEStockingFluidSlot(
                this.getHolder(),
                this.config == null ? null : this.config.copy(),
                this.stock == null ? null : this.stock.copy(),
                null);
    }

    @Override
    public @Nullable FluidStack drain(int maxDrain, boolean doDrain) {
        if (this.stock == null) {
            return null;
        }
        if (this.config != null) {
            IMEMonitor<IAEFluidStack> monitor = getHolder().getMonitor();
            if (monitor == null) return null;

            Actionable action = doDrain ? Actionable.MODULATE : Actionable.SIMULATE;
            IAEFluidStack request;
            if (this.config instanceof WrappedFluidStack wfs) {
                request = wfs.getAEStack();
            } else {
                request = this.config.copy();
            }
            request.setStackSize(maxDrain);

            IAEFluidStack result = monitor.extractItems(request, action, getHolder().getActionSource());
            if (result != null) {
                int extracted = (int) Math.min(result.getStackSize(), maxDrain);
                this.stock.decStackSize(extracted);
                trigger();
                if (extracted != 0) {
                    FluidStack resultStack = this.config.getFluidStack();
                    resultStack.amount = extracted;
                    return resultStack;
                }
            }
        }
        return null;
    }
}
