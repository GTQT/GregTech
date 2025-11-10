package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.capability.IMultiblockController;
import gregtech.api.capability.IMultipleRecipeMaps;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.metatileentity.multiblock.NoEnergyMultiblockController;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.OCParams;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.common.ConfigHolder;

import net.minecraft.util.Tuple;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class NoEnergyMultiblockRecipeLogic extends AbstractRecipeLogic {

    // Used for distinct mode
    protected int lastRecipeIndex = 0;
    protected IItemHandlerModifiable currentDistinctInputBus;
    protected List<IItemHandlerModifiable> invalidatedInputList = new ArrayList<>();

    public NoEnergyMultiblockRecipeLogic(NoEnergyMultiblockController tileEntity, RecipeMap<?> recipeMap) {
        super(tileEntity, recipeMap);
    }

    @Override
    protected long getEnergyInputPerSecond() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected long getEnergyStored() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected long getEnergyCapacity() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected boolean drawEnergy(long recipeEUt, boolean simulate) {
        return true; // spoof energy being drawn
    }

    @Override
    public long getMaxVoltage() {
        return GTValues.LV;
    }

    protected void runOverclockingLogic(OCParams ocParams, OCResult ocResult, RecipePropertyStorage propertyStorage,
                                        long maxVoltage) {
        ocParams.setEut(1L);
        super.runOverclockingLogic(ocParams, ocResult, propertyStorage, maxVoltage);
    }

    @Override
    public long getMaximumOverclockVoltage() {
        return GTValues.V[GTValues.LV];
    }

    /**
     * Used to reset cached values in the Recipe Logic on structure deform
     */
    @Override
    public void invalidate() {
        previousRecipe = null;
        progressTime = 0;
        maxProgressTime = 0;
        this.fluidOutputs = Collections.emptyList();
        this.itemOutputs = Collections.emptyList();
        lastRecipeIndex = 0;
        parallelRecipesPerformed = 0;
        isOutputsFull = false;
        invalidInputsForRecipes = false;
        invalidatedInputList.clear();
        setActive(false); // this marks dirty for us
    }

    @Override
    public void update() {
    }

    public void updateWorkable() {
        super.update();
    }

    @Override
    protected boolean canProgressRecipe() {
        return super.canProgressRecipe() && !((IMultiblockController) metaTileEntity).isStructureObstructed();
    }

    public void onDistinctChanged() {
        this.lastRecipeIndex = 0;
    }

    @Override
    protected IItemHandlerModifiable getInputInventory() {
        NoEnergyMultiblockController controller = (NoEnergyMultiblockController) metaTileEntity;
        return controller.getInputInventory();
    }

    // Used for distinct bus recipe checking
    protected List<IItemHandlerModifiable> getInputBuses() {
        NoEnergyMultiblockController controller = (NoEnergyMultiblockController) metaTileEntity;
        return controller.getAbilities(MultiblockAbility.IMPORT_ITEMS);
    }

    @Override
    protected IItemHandlerModifiable getOutputInventory() {
        NoEnergyMultiblockController controller = (NoEnergyMultiblockController) metaTileEntity;
        return controller.getOutputInventory();
    }

    @Override
    protected IMultipleTankHandler getInputTank() {
        NoEnergyMultiblockController controller = (NoEnergyMultiblockController) metaTileEntity;
        return controller.getInputFluidInventory();
    }

    @Override
    protected IMultipleTankHandler getOutputTank() {
        NoEnergyMultiblockController controller = (NoEnergyMultiblockController) metaTileEntity;
        return controller.getOutputFluidInventory();
    }

    @Override
    protected boolean canWorkWithInputs() {
        MultiblockWithDisplayBase controller = (MultiblockWithDisplayBase) metaTileEntity;
        if (controller instanceof NoEnergyMultiblockController distinctController) {

            if (distinctController.canBeDistinct() && distinctController.isDistinct() &&
                    getInputInventory().getSlots() > 0) {
                boolean canWork = false;
                if (invalidatedInputList.isEmpty()) {
                    return true;
                }
                if (!metaTileEntity.getNotifiedFluidInputList().isEmpty()) {
                    canWork = true;
                    invalidatedInputList.clear();
                    metaTileEntity.getNotifiedFluidInputList().clear();
                    metaTileEntity.getNotifiedItemInputList().clear();
                } else {
                    Iterator<IItemHandlerModifiable> notifiedIter = metaTileEntity.getNotifiedItemInputList()
                            .iterator();
                    while (notifiedIter.hasNext()) {
                        IItemHandlerModifiable bus = notifiedIter.next();
                        Iterator<IItemHandlerModifiable> invalidatedIter = invalidatedInputList.iterator();
                        while (invalidatedIter.hasNext()) {
                            IItemHandler invalidatedHandler = invalidatedIter.next();
                            if (invalidatedHandler instanceof ItemHandlerList) {
                                for (IItemHandler ih : ((ItemHandlerList) invalidatedHandler).getBackingHandlers()) {
                                    if (ih == bus) {
                                        canWork = true;
                                        invalidatedIter.remove();
                                        break;
                                    }
                                }
                            } else if (invalidatedHandler == bus) {
                                canWork = true;
                                invalidatedIter.remove();
                            }
                        }
                        notifiedIter.remove();
                    }
                }
                ArrayList<IItemHandler> flattenedHandlers = new ArrayList<>();
                for (IItemHandler ih : getInputBuses()) {
                    if (ih instanceof ItemHandlerList) {
                        flattenedHandlers.addAll(((ItemHandlerList) ih).getBackingHandlers());
                    }
                    flattenedHandlers.add(ih);
                }

                if (!invalidatedInputList.containsAll(flattenedHandlers)) {
                    canWork = true;
                }
                return canWork;
            }
        }
        return super.canWorkWithInputs();
    }

    @Override
    protected void trySearchNewRecipe() {
        // do not run recipes when there are more than 5 maintenance problems
        // Maintenance can apply to all multiblocks, so cast to a base multiblock class
        MultiblockWithDisplayBase controller = (MultiblockWithDisplayBase) metaTileEntity;
        if (ConfigHolder.machines.enableMaintenance && controller.hasMaintenanceMechanics() &&
                controller.getNumMaintenanceProblems() > 5) {
            return;
        }

        // Distinct buses only apply to some multiblocks, so check the controller against a lower class
        if (controller instanceof NoEnergyMultiblockController distinctController) {

            if (distinctController.canBeDistinct() && distinctController.isDistinct() &&
                    getInputInventory().getSlots() > 0) {
                trySearchNewRecipeDistinct();
                return;
            }
        }

        trySearchNewRecipeCombined();
    }

    /**
     * Put into place so multiblocks can override {@link AbstractRecipeLogic#trySearchNewRecipe()} without having to
     * deal with the maintenance and distinct logic in {@link NoEnergyMultiblockRecipeLogic#trySearchNewRecipe()}
     */
    protected void trySearchNewRecipeCombined() {
        super.trySearchNewRecipe();
    }

    protected void trySearchNewRecipeDistinct() {
        long maxVoltage = getMaxVoltage();
        Recipe currentRecipe;
        List<IItemHandlerModifiable> importInventory = getInputBuses();
        IMultipleTankHandler importFluids = getInputTank();

        // Our caching implementation
        // This guarantees that if we get a recipe cache hit, our efficiency is no different from other machines
        if (checkPreviousRecipeDistinct(importInventory.get(lastRecipeIndex)) && checkRecipe(previousRecipe)) {
            currentRecipe = previousRecipe;
            currentDistinctInputBus = importInventory.get(lastRecipeIndex);
            if (prepareRecipeDistinct(currentRecipe)) {
                // No need to cache the previous recipe here, as it is not null and matched by the current recipe,
                // so it will always be the same
                return;
            }
        }

        // On a cache miss, our efficiency is much worse, as it will check
        // each bus individually instead of the combined inventory all at once.
        for (int i = 0; i < importInventory.size(); i++) {
            IItemHandlerModifiable bus = importInventory.get(i);
            // Skip this bus if no recipe was found last time
            if (invalidatedInputList.contains(bus)) {
                continue;
            }
            // Look for a new recipe after a cache miss
            currentRecipe = findRecipe(maxVoltage, bus, importFluids);
            // Cache the current recipe, if one is found
            if (currentRecipe != null && checkRecipe(currentRecipe)) {
                this.previousRecipe = currentRecipe;
                currentDistinctInputBus = bus;
                if (prepareRecipeDistinct(currentRecipe)) {
                    lastRecipeIndex = i;
                    return;
                }
            }
            if (currentRecipe == null) {
                //no valid recipe found, invalidate this bus
                invalidatedInputList.add(bus);
            }
        }
    }

    @Override
    public void invalidateInputs() {
        MultiblockWithDisplayBase controller = (MultiblockWithDisplayBase) metaTileEntity;
        NoEnergyMultiblockController distinctController = (NoEnergyMultiblockController) controller;
        if (distinctController.canBeDistinct() && distinctController.isDistinct() &&
                getInputInventory().getSlots() > 0) {
            invalidatedInputList.add(currentDistinctInputBus);
        } else {
            super.invalidateInputs();
        }
    }

    protected boolean checkPreviousRecipeDistinct(IItemHandlerModifiable previousBus) {
        return previousRecipe != null && previousRecipe.matches(false, previousBus, getInputTank());
    }

    protected boolean prepareRecipeDistinct(Recipe recipe) {

        recipe = Recipe.trimRecipeOutputs(recipe, getRecipeMap(), metaTileEntity.getItemOutputLimit(),
                metaTileEntity.getFluidOutputLimit());

        recipe = findParallelRecipe(recipe, currentDistinctInputBus, getInputTank(), getOutputInventory(),
                getOutputTank(), getMaxParallelVoltage(), getParallelLimit());

        if (recipe != null) {
            recipe = setupAndConsumeRecipeInputs(recipe, currentDistinctInputBus, this.getInputTank());
            if (recipe != null) {
                setupRecipe(recipe);
                return true;
            }
        }
        return false;
    }

    protected Tuple<Integer, Double> getMaintenanceValues() {
        MultiblockWithDisplayBase displayBase =
                this.metaTileEntity instanceof MultiblockWithDisplayBase ? (MultiblockWithDisplayBase) metaTileEntity :
                        null;
        int numMaintenanceProblems = displayBase == null || !displayBase.hasMaintenanceMechanics() ||
                !ConfigHolder.machines.enableMaintenance ? 0 : displayBase.getNumMaintenanceProblems();
        double durationMultiplier = 1.0D;
        if (displayBase != null && displayBase.hasMaintenanceMechanics() && ConfigHolder.machines.enableMaintenance) {
            durationMultiplier = displayBase.getMaintenanceDurationMultiplier();
        }
        return new Tuple<>(numMaintenanceProblems, durationMultiplier);
    }

    @Override
    public boolean checkRecipe(Recipe recipe) {
        NoEnergyMultiblockController controller = (NoEnergyMultiblockController) metaTileEntity;
        if (controller.checkRecipe(recipe, false)) {
            controller.checkRecipe(recipe, true);
            return super.checkRecipe(recipe);
        }
        return false;
    }

    @Override
    protected void completeRecipe() {
        performMufflerOperations();
        super.completeRecipe();
    }

    protected void performMufflerOperations() {
        if (metaTileEntity instanceof MultiblockWithDisplayBase controller) {
            // output muffler items
            if (controller.hasMufflerMechanics()) {
                controller.outputRecoveryItems(Math.max(parallelRecipesPerformed, 1));
            }
        }
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        // if the multiblock has more than one RecipeMap, return the currently selected one
        if (metaTileEntity instanceof IMultipleRecipeMaps)
            return ((IMultipleRecipeMaps) metaTileEntity).getCurrentRecipeMap();
        return super.getRecipeMap();
    }

    @Override
    public long getInfoProviderEUt() {
        return 0;
    }
}
