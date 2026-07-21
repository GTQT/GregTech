package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.capability.IParallelMultiblock;
import gregtech.api.metatileentity.GCYMAdvanceRecipeMapMultiblockController;
import gregtech.api.metatileentity.GCYMRecipeMapMultiblockController;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
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
        if (metaTileEntity instanceof IParallelMultiblock iParallelMultiblock && iParallelMultiblock.isParallel()) {
            return iParallelMultiblock.getParallel();
        }
        return 1;
    }

    /**
     * 并行：不增加耗电。
     */
    @Override
    protected boolean shouldParallelMultiplyPower() {
        return false;
    }

    @Override
    public @NotNull RecipeMapMultiblockController getMetaTileEntity() {
        return (RecipeMapMultiblockController) super.getMetaTileEntity();
    }

    @Override
    public long getMaxVoltage() {
        if (!ConfigHolder.globalMultiblocks.enableTieredCasings)
            return super.getMaxVoltage();

        if (getMetaTileEntity() instanceof GCYMAdvanceRecipeMapMultiblockController controller && !controller.isTiered())
            return super.getMaxVoltage();

        if (getMetaTileEntity() instanceof GCYMRecipeMapMultiblockController controller && !controller.isTiered())
            return super.getMaxVoltage();

        List<ITieredMetaTileEntity> list = getMetaTileEntity().getAbilities(MultiblockAbility.TIERED_HATCH);

        if (list.isEmpty())
            return super.getMaxVoltage();

        return Math.min(GTValues.V[list.get(0).getTier()], super.getMaxVoltage());
    }
}
