package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.capability.IAccelerateMultiblock;
import gregtech.api.capability.IOverclockMultiblock;
import gregtech.api.capability.IParallelMultiblock;
import gregtech.api.capability.IThreadMultiblock;
import gregtech.api.metatileentity.GCYMRecipeMapMultiblockController;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.util.GTUtility;
import gregtech.common.ConfigHolder;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GCYMMultiblockRecipeLogic extends MultiblockRecipeLogic {

    public GCYMMultiblockRecipeLogic(RecipeMapMultiblockController tileEntity) {
        super(tileEntity);
    }

    public GCYMMultiblockRecipeLogic(RecipeMapMultiblockController tileEntity, boolean hasPerfectOC) {
        super(tileEntity, hasPerfectOC);
    }

    @Override
    public int getParallelLimit() {
        if (metaTileEntity instanceof IParallelMultiblock parallel && parallel.isParallel()) {
            return parallel.getParallel();
        }
        return 1;
    }

    /**
     * Pulled from the controller on every read, exactly like {@link #getParallelLimit()} — the thread hatch, not
     * the inherited field, is what decides how many recipes run at once.
     */
    @Override
    public int getThreadLimit() {
        if (metaTileEntity instanceof IThreadMultiblock thread && thread.isThread()) {
            return thread.getThread();
        }
        return 1;
    }

    /**
     * Cross-recipe parallel is chosen by the hatch rather than by the machine: fitting the cross-parallel hatch
     * turns it on, fitting the ordinary parallel hatch leaves it off. Both share one ability, so this is the only
     * thing that tells them apart.
     */
    @Override
    public boolean isCrossRecipeParallelEnabled() {
        return metaTileEntity instanceof IParallelMultiblock parallel && parallel.isParallel() &&
                parallel.isCrossParallel();
    }

    /**
     * 并行：不增加耗电。
     */
    @Override
    protected boolean shouldParallelMultiplyPower() {
        return false;
    }

    @Override
    protected double getOverclockingDurationFactor() {
        if (metaTileEntity instanceof IOverclockMultiblock provider) {
            int divisor = provider.getOverclockDurationDivisor();
            if (divisor > 0) return 1.0 / divisor;
        }
        return super.getOverclockingDurationFactor();
    }

    @Override
    protected void modifyOverclockPost(@NotNull OCResult ocResult, @NotNull RecipePropertyStorage storage) {
        super.modifyOverclockPost(ocResult, storage);
        if (metaTileEntity instanceof IAccelerateMultiblock provider) {
            int recipeTier = GTUtility.getTierByVoltage(ocResult.eut());
            float multiplier = provider.getAccelerateMultiplier(recipeTier);
            if (multiplier < 1.0f) {
                ocResult.setDuration(Math.max(1, (int) (ocResult.duration() * multiplier)));
            }
        }
    }

    @Override
    public @NotNull RecipeMapMultiblockController getMetaTileEntity() {
        return (RecipeMapMultiblockController) super.getMetaTileEntity();
    }

    @Override
    public long getMaxVoltage() {
        if (!ConfigHolder.globalMultiblocks.enableTieredCasings)
            return super.getMaxVoltage();

        if (getMetaTileEntity() instanceof GCYMRecipeMapMultiblockController controller && !controller.isTiered())
            return super.getMaxVoltage();

        List<ITieredMetaTileEntity> list = getMetaTileEntity().getAbilities(MultiblockAbility.TIERED_HATCH);

        if (list.isEmpty())
            return super.getMaxVoltage();

        return Math.min(GTValues.V[list.get(0).getTier()], super.getMaxVoltage());
    }
}
