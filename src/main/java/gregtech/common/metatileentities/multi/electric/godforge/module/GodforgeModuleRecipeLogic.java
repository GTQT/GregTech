package gregtech.common.metatileentities.multi.electric.godforge.module;

import java.math.BigInteger;
import java.util.UUID;

import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.wireless.TransferContext;
import gregtech.api.wireless.TransferResult;
import gregtech.api.wireless.WirelessEnergyService;
import gregtech.api.wireless.WirelessNetworkView;
import gregtech.common.wireless.WirelessEnergyServiceImpl;

public class GodforgeModuleRecipeLogic extends MultiblockRecipeLogic {

    private final MTEBaseModule module;

    public GodforgeModuleRecipeLogic(MTEBaseModule module) {
        super(module);
        this.module = module;
    }

    @Override
    protected long getEnergyStored() {
        UUID uuid = module.getOwnerGT();
        if (uuid == null) return 0;
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service == null) return 0;
        BigInteger eu = service.getView(uuid).getStored();
        long clamped = eu.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        return Math.max(clamped, 0);
    }

    @Override
    protected long getEnergyCapacity() {
        return Long.MAX_VALUE;
    }

    @Override
    protected boolean drawEnergy(long recipeEUt, boolean simulate) {
        recipeEUt = appendEfficiency(recipeEUt);
        if (!consumesEnergy()) return true;

        UUID uuid = module.getOwnerGT();
        if (uuid == null) return false;

        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service == null) return false;

        if (simulate) {
            WirelessNetworkView view = service.getView(uuid);
            return view.getStored().compareTo(BigInteger.valueOf(recipeEUt)) >= 0;
        }

        TransferResult result = service.extract(uuid, recipeEUt, TransferContext.MACHINE);
        if (result.isSuccess()) {
            module.addToPowerTally(BigInteger.valueOf(result.getAmountLong()));
            return true;
        }
        return false;
    }

    @Override
    public long getMaxVoltage() {
        return module.getProcessingVoltage();
    }

    @Override
    public long getMaximumOverclockVoltage() {
        return module.getProcessingVoltage();
    }

    @Override
    public IEnergyContainer getEnergyContainer() {
        return IEnergyContainer.DEFAULT;
    }
}
