package gregtech.api.metatileentity.multiblock;

import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.EnergyContainerList;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.interfaces.IRefreshBeforeConsumption;

import net.minecraftforge.items.IItemHandlerModifiable;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages recipe-related MultiblockAbility instances (inventories, fluid tanks, energy).
 * Can be composed into any MultiblockWithDisplayBase subclass to provide standard
 * recipe ability initialization and reset logic.
 *
 * <p>This class extracts the ability management pattern from {@link RecipeMapMultiblockController}
 * into a reusable component, enabling composition-based recipe support for classes
 * that cannot inherit from RMMC (e.g., {@link ParametricMultiblockController}).
 *
 * @see RecipeMapMultiblockController
 * @see gregtech.api.capability.IRecipeMapHolder
 */
public class RecipeAbilityManager {

    private final MultiblockControllerBase controller;

    private IItemHandlerModifiable inputInventory;
    private IItemHandlerModifiable outputInventory;
    private IMultipleTankHandler inputFluidInventory;
    private IMultipleTankHandler outputFluidInventory;
    private IEnergyContainer energyContainer;
    private List<IRefreshBeforeConsumption> refreshBeforeConsumptions;

    public RecipeAbilityManager(@NotNull MultiblockControllerBase controller) {
        this.controller = controller;
        this.refreshBeforeConsumptions = new ArrayList<>();
        reset();
    }

    // region Initialization

    /**
     * Initializes all recipe-related abilities from the controller's multiblock parts.
     * This provides the default behavior matching {@link RecipeMapMultiblockController#initializeAbilities()}.
     *
     * @param allowSameFluidFill whether to allow same fluid fill for fluid tank lists
     */
    public void initialize(boolean allowSameFluidFill) {
        this.inputInventory = new ItemHandlerList(
                controller.getAbilities(MultiblockAbility.IMPORT_ITEMS));
        this.inputFluidInventory = new FluidTankList(allowSameFluidFill,
                controller.getAbilities(MultiblockAbility.IMPORT_FLUIDS));
        this.outputInventory = new ItemHandlerList(
                controller.getAbilities(MultiblockAbility.EXPORT_ITEMS));
        this.outputFluidInventory = new FluidTankList(allowSameFluidFill,
                controller.getAbilities(MultiblockAbility.EXPORT_FLUIDS));

        List<IEnergyContainer> inputEnergy = new ArrayList<>(
                controller.getAbilities(MultiblockAbility.INPUT_ENERGY));
        inputEnergy.addAll(controller.getAbilities(MultiblockAbility.SUBSTATION_INPUT_ENERGY));
        inputEnergy.addAll(controller.getAbilities(MultiblockAbility.INPUT_LASER));
        this.energyContainer = new EnergyContainerList(inputEnergy);

        this.refreshBeforeConsumptions.clear();
        for (IMultiblockPart part : controller.getMultiblockParts()) {
            if (part instanceof IRefreshBeforeConsumption refresh) {
                refreshBeforeConsumptions.add(refresh);
            }
        }
    }

    /**
     * Resets all abilities to empty/default state.
     * Called during construction and when the multiblock structure is invalidated.
     */
    public void reset() {
        this.inputInventory = new GTItemStackHandler(controller, 0);
        this.inputFluidInventory = new FluidTankList(true);
        this.outputInventory = new GTItemStackHandler(controller, 0);
        this.outputFluidInventory = new FluidTankList(true);
        this.energyContainer = new EnergyContainerList(Lists.newArrayList());
        if (this.refreshBeforeConsumptions == null) {
            this.refreshBeforeConsumptions = new ArrayList<>();
        } else {
            this.refreshBeforeConsumptions.clear();
        }
    }

    // endregion

    // region Getters

    @NotNull
    public IItemHandlerModifiable getInputInventory() {
        return inputInventory;
    }

    @NotNull
    public IItemHandlerModifiable getOutputInventory() {
        return outputInventory;
    }

    @NotNull
    public IMultipleTankHandler getInputFluidInventory() {
        return inputFluidInventory;
    }

    @NotNull
    public IMultipleTankHandler getOutputFluidInventory() {
        return outputFluidInventory;
    }

    @NotNull
    public IEnergyContainer getEnergyContainer() {
        return energyContainer;
    }

    @NotNull
    public List<IRefreshBeforeConsumption> getRefreshBeforeConsumptions() {
        return refreshBeforeConsumptions;
    }

    // endregion

    // region Setters (for subclass customization)

    public void setInputInventory(@NotNull IItemHandlerModifiable inputInventory) {
        this.inputInventory = inputInventory;
    }

    public void setOutputInventory(@NotNull IItemHandlerModifiable outputInventory) {
        this.outputInventory = outputInventory;
    }

    public void setInputFluidInventory(@NotNull IMultipleTankHandler inputFluidInventory) {
        this.inputFluidInventory = inputFluidInventory;
    }

    public void setOutputFluidInventory(@NotNull IMultipleTankHandler outputFluidInventory) {
        this.outputFluidInventory = outputFluidInventory;
    }

    public void setEnergyContainer(@NotNull IEnergyContainer energyContainer) {
        this.energyContainer = energyContainer;
    }

    // endregion

    // region Refresh

    /**
     * Invokes all registered {@link IRefreshBeforeConsumption} callbacks.
     */
    public void refreshAllBeforeConsumption() {
        for (IRefreshBeforeConsumption refresh : refreshBeforeConsumptions) {
            refresh.refreshBeforeConsumption();
        }
    }

    // endregion
}
