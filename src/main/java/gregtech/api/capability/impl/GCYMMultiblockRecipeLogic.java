package gregtech.api.capability.impl;

import gregtech.api.capability.IParallelMultiblock;
import gregtech.api.metatileentity.GCYMAdvanceRecipeMapMultiblockController;
import gregtech.api.metatileentity.GCYMMultiblockAbility;
import gregtech.api.metatileentity.GCYMRecipeMapMultiblockController;
import gregtech.common.GCYMConfigHolder;
import gregtech.api.GTValues;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.properties.RecipePropertyStorage;
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
        if (metaTileEntity instanceof IParallelMultiblock iParallelMultiblock && iParallelMultiblock.isParallel()) {
            return iParallelMultiblock.getParallel();
        }
        return 1;
    }
    /*
    @Override
    protected void modifyOverclockPost(@NotNull OCResult ocResult, @NotNull RecipePropertyStorage storage) {
        super.modifyOverclockPost(ocResult, storage);
        ocResult.setDuration(ocResult.duration() / getDurationMultiplier());
    }

    private int getDurationMultiplier() {
        if (metaTileEntity instanceof IParallelMultiblock parallelMultiblock && parallelMultiblock.isParallel()) {
            int maxParallel = parallelMultiblock.getParallel();
            if (maxParallel <= 16) {
                return maxParallel;
            }
            if (maxParallel <= 64) {
                return maxParallel / 4;
            }
            if (maxParallel <= 256) {
                return maxParallel / 64;
            }
            if (maxParallel <= 1024) {
                return maxParallel / 512;
            }
        }
        return 1;
    }

     */

    @Override
    public @NotNull RecipeMapMultiblockController getMetaTileEntity() {
        return (RecipeMapMultiblockController) super.getMetaTileEntity();
    }

    @Override
    public long getMaxVoltage() {
        if (!GCYMConfigHolder.globalMultiblocks.enableTieredCasings)
            return super.getMaxVoltage();

        if (getMetaTileEntity() instanceof GCYMAdvanceRecipeMapMultiblockController controller && !controller.isTiered())
            return super.getMaxVoltage();

        if (getMetaTileEntity() instanceof GCYMRecipeMapMultiblockController controller && !controller.isTiered())
            return super.getMaxVoltage();

        List<ITieredMetaTileEntity> list = getMetaTileEntity().getAbilities(GCYMMultiblockAbility.TIERED_HATCH);

        if (list.isEmpty())
            return super.getMaxVoltage();

        return Math.min(GTValues.V[list.get(0).getTier()], super.getMaxVoltage());
    }
}
