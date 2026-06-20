package gregicality.multiblocks.api.capability.impl;

import gregicality.multiblocks.api.capability.IParallelMultiblock;
import gregicality.multiblocks.api.metatileentity.GCYMMultiblockAbility;
import gregicality.multiblocks.api.metatileentity.GCYMRecipeMapMultiblockController;
import gregicality.multiblocks.common.GCYMConfigHolder;
import gregtech.api.GTValues;
import gregtech.api.capability.IHeatingCoil;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.recipes.logic.OCParams;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.logic.OverclockingLogic;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.recipes.properties.impl.TemperatureProperty;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class GCYMHeatCoilRecipeLogic extends GCYMMultiblockRecipeLogic {

    public GCYMHeatCoilRecipeLogic(RecipeMapMultiblockController tileEntity) {
        super(tileEntity);
    }

    public GCYMHeatCoilRecipeLogic(RecipeMapMultiblockController tileEntity, boolean hasPerfectOC) {
        super(tileEntity, hasPerfectOC);
        if (!(metaTileEntity instanceof IHeatingCoil)) {
            throw new IllegalArgumentException("MetaTileEntity must be instanceof IHeatingCoil");
        }
    }

    protected void modifyOverclockPre(@NotNull OCParams ocParams, @NotNull RecipePropertyStorage storage) {
        super.modifyOverclockPre(ocParams, storage);
        ocParams.setEut(OverclockingLogic.applyCoilEUtDiscount(ocParams.eut(), ((IHeatingCoil)this.metaTileEntity).getCurrentTemperature(), (Integer)storage.get(TemperatureProperty.getInstance(), 0)));
    }

    protected void runOverclockingLogic(@NotNull OCParams ocParams, @NotNull OCResult ocResult, @NotNull RecipePropertyStorage propertyStorage, long maxVoltage) {
        OverclockingLogic.heatingCoilOC(ocParams, ocResult, maxVoltage, ((IHeatingCoil)this.metaTileEntity).getCurrentTemperature(), (Integer)propertyStorage.get(TemperatureProperty.getInstance(), 0));
    }
}
