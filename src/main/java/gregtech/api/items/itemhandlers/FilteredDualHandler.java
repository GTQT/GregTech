package gregtech.api.items.itemhandlers;

import gregtech.api.capability.DualHandler;
import gregtech.common.covers.filter.FluidFilterContainer;
import gregtech.common.covers.filter.ItemFilterContainer;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 双模过滤 Handler —— 继承 DualHandler，同时过滤物品 insertItem 和流体 fill。
 * 未配置的过滤器（null）始终放行。
 */
public class FilteredDualHandler extends DualHandler {

    @Nullable
    private final ItemFilterContainer itemFilter;
    @Nullable
    private final FluidFilterContainer fluidFilter;

    public FilteredDualHandler(DualHandler original,
                        @Nullable ItemFilterContainer itemFilter,
                        @Nullable FluidFilterContainer fluidFilter) {
        super(original.getItemDelegate(), original.getFluidDelegate(), original.isExport());
        this.itemFilter = itemFilter;
        this.fluidFilter = fluidFilter;
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (itemFilter != null && !itemFilter.test(stack)) return stack;
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (fluidFilter != null && !fluidFilter.test(resource)) return 0;
        return super.fill(resource, doFill);
    }
}
