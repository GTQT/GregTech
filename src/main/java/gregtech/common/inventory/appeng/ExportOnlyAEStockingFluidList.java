package gregtech.common.inventory.appeng;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEStockingHatch;

import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEStockingFluidSlot;

import net.minecraftforge.fluids.FluidStack;

public class ExportOnlyAEStockingFluidList extends ExportOnlyAEFluidList {

    private final MetaTileEntityMEStockingHatch holder;

    public ExportOnlyAEStockingFluidList(MetaTileEntityMEStockingHatch holder, int slots,
                                         MetaTileEntity entityToNotify) {
        super(holder, slots, entityToNotify);
        this.holder = holder;
    }

    @Override
    protected void createInventory(MetaTileEntity holder, MetaTileEntity entityToNotify) {
        if (!(holder instanceof MetaTileEntityMEStockingHatch stocking)) {
            throw new IllegalArgumentException("Cannot create Stocking Fluid List for nonstocking MetaTileEntity!");
        }
        this.inventory = new ExportOnlyAEStockingFluidSlot[size];
        for (int i = 0; i < size; i++) {
            this.inventory[i] = new ExportOnlyAEStockingFluidSlot(stocking, entityToNotify);
        }
    }

    @Override
    public ExportOnlyAEStockingFluidSlot[] getInventory() {
        return (ExportOnlyAEStockingFluidSlot[]) super.getInventory();
    }

    @Override
    public boolean isStocking() {
        return true;
    }

    @Override
    public boolean isAutoPull() {
        return holder.autoPull;
    }

    @Override
    public boolean hasStackInConfig(FluidStack stack, boolean checkExternal) {
        boolean inThisHatch = super.hasStackInConfig(stack, false);
        if (inThisHatch) return true;
        if (checkExternal) {
            return holder.testConfiguredInOtherHatch(stack);
        }
        return false;
    }
}
