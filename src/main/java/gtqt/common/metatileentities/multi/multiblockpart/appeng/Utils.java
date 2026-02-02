package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.capability.impl.FluidTankList;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import static gtqt.api.util.GTQTUtility.isFluidTankListEmpty;
import static gtqt.api.util.GTQTUtility.isInventoryEmpty;

public class Utils {
    public static void returnItems(IMEMonitor<IAEItemStack> monitor, IItemHandlerModifiable itemHandler,
                                   IActionSource source) {
        if (isInventoryEmpty(itemHandler)) return;

        if (monitor == null) return;

        for (int x = 0; x < itemHandler.getSlots(); x++) {
            ItemStack itemStack = itemHandler.getStackInSlot(x);
            if (itemStack.isEmpty()) continue;

            IAEItemStack iaeItemStack = AEItemStack.fromItemStack(itemStack);

            IAEItemStack notInserted = monitor.injectItems(iaeItemStack, Actionable.MODULATE, source);
            if (notInserted != null && notInserted.getStackSize() > 0) {
                itemStack.setCount((int) notInserted.getStackSize());
            } else {
                itemHandler.setStackInSlot(x, ItemStack.EMPTY);
            }
        }
    }

    public static void returnFluids(IMEMonitor<IAEFluidStack> monitor, FluidTankList fluidTankList,
                                    IActionSource source) {
        if (isFluidTankListEmpty(fluidTankList)) return;

        if (monitor == null) return;

        for (int x = 0; x < fluidTankList.getTanks(); x++){
            FluidStack exportFluid = fluidTankList.getTankAt(x).getFluid();
            if (exportFluid != null) {
                IAEFluidStack aeFluid = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)
                        .createStack(exportFluid);
                if (aeFluid != null) {
                    IAEFluidStack remaining = monitor.injectItems(aeFluid, Actionable.MODULATE, source);
                    if (remaining != null) {
                        fluidTankList.getTankAt(x).drain((int) (aeFluid.getStackSize() - remaining.getStackSize()), true);
                    } else {
                        fluidTankList.getTankAt(x).drain(exportFluid.amount, true);
                    }
                }
            }
        }
    }
}
