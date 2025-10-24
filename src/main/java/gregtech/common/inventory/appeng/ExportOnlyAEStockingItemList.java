package gregtech.common.inventory.appeng;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEStockingBus;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEItemSlot;

import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEStockingItemSlot;

import net.minecraft.item.ItemStack;

public class ExportOnlyAEStockingItemList extends ExportOnlyAEItemList {

    private final MetaTileEntityMEStockingBus holder;

    public ExportOnlyAEStockingItemList(MetaTileEntityMEStockingBus holder, int slots,
                                        MetaTileEntity entityToNotify) {
        super(holder, slots, entityToNotify);
        this.holder = holder;
    }

    @Override
    protected void createInventory(MetaTileEntity holder) {
        if (!(holder instanceof MetaTileEntityMEStockingBus stocking)) {
            throw new IllegalArgumentException("Cannot create Stocking Item List for nonstocking MetaTileEntity!");
        }
        this.inventory = new ExportOnlyAEStockingItemSlot[size];
        for (int i = 0; i < size; i++) {
            this.inventory[i] = new ExportOnlyAEStockingItemSlot(stocking);
        }
        for (ExportOnlyAEItemSlot slot : this.inventory) {
            slot.setTrigger(this::onContentsChanged);
        }
    }

    @Override
    public ExportOnlyAEStockingItemSlot[] getInventory() {
        return (ExportOnlyAEStockingItemSlot[]) super.getInventory();
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
    public boolean hasStackInConfig(ItemStack stack, boolean checkExternal) {
        boolean inThisBus = super.hasStackInConfig(stack, false);
        if (inThisBus) return true;
        if (checkExternal) {
            return holder.testConfiguredInOtherBus(stack);
        }
        return false;
    }
}
