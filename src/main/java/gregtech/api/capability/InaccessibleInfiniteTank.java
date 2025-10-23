package gregtech.api.capability;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IItemList;
import appeng.fluids.util.AEFluidStack;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class InaccessibleInfiniteTank implements IFluidTank, INotifiableHandler {

    private final IItemList<IAEFluidStack> internalBuffer;
    private final List<MetaTileEntity> notifiableEntities = new ArrayList<>();
    private final MetaTileEntity holder;

    public InaccessibleInfiniteTank(MetaTileEntity holder, IItemList<IAEFluidStack> internalBuffer,
                                    MetaTileEntity mte) {
        this.holder = holder;
        this.internalBuffer = internalBuffer;
        this.notifiableEntities.add(mte);
    }

    @Nullable
    @Override
    public FluidStack getFluid() {
        return null;
    }

    @Override
    public int getFluidAmount() {
        return 0;
    }

    @Override
    public int getCapacity() {
        return Integer.MAX_VALUE - 1;
    }

    @Override
    public FluidTankInfo getInfo() {
        return null;
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (resource == null) {
            return 0;
        }
        if (doFill) {
            this.internalBuffer.add(AEFluidStack.fromFluidStack(resource));
            holder.markDirty();
        }
        this.trigger();
        return resource.amount;
    }

    @Nullable
    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        return null;
    }

    @Override
    public void addNotifiableMetaTileEntity(MetaTileEntity metaTileEntity) {
        this.notifiableEntities.add(metaTileEntity);
    }

    @Override
    public void removeNotifiableMetaTileEntity(MetaTileEntity metaTileEntity) {
        this.notifiableEntities.remove(metaTileEntity);
    }

    private void trigger() {
        for (MetaTileEntity metaTileEntity : this.notifiableEntities) {
            if (metaTileEntity != null && metaTileEntity.isValid()) {
                this.addToNotifiedList(metaTileEntity, this, true);
            }
        }
    }
}
