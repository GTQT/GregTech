package gregtech.common.metatileentities.multi.electric.godforge.module;

import java.math.BigInteger;
import java.util.UUID;

import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.common.misc.WirelessNetworkManager;

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
        BigInteger eu = WirelessNetworkManager.getUserEU(uuid);
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

        BigInteger currentEU = WirelessNetworkManager.getUserEU(uuid);
        BigInteger required = BigInteger.valueOf(recipeEUt);

        if (currentEU.compareTo(required) >= 0) {
            if (!simulate) {
                WirelessNetworkManager.addEUToGlobalEnergyMap(uuid, required.negate());
                module.addToPowerTally(required);
            }
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
