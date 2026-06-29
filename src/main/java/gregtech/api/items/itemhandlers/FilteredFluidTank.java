package gregtech.api.items.itemhandlers;

import gregtech.common.covers.filter.FluidFilterContainer;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

import org.jetbrains.annotations.Nullable;

/**
 * 流体过滤包装 Tank —— 在 fill 时检查过滤器，不匹配的流体将被拒绝（返回 0）。
 * drain / getFluid / getCapacity 不受过滤影响。
 */
public class FilteredFluidTank implements IFluidTank {

    private final IFluidTank delegate;
    private final FluidFilterContainer filter;

    public FilteredFluidTank(IFluidTank delegate, FluidFilterContainer filter) {
        this.delegate = delegate;
        this.filter = filter;
    }

    @Override
    public @Nullable FluidStack getFluid() {
        return delegate.getFluid();
    }

    @Override
    public int getFluidAmount() {
        return delegate.getFluidAmount();
    }

    @Override
    public int getCapacity() {
        return delegate.getCapacity();
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (!filter.test(resource)) return 0;
        return delegate.fill(resource, doFill);
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        return delegate.drain(maxDrain, doDrain);
    }

    @Override
    public FluidTankInfo getInfo() {
        return delegate.getInfo();
    }
}
