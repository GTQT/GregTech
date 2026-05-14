package gregtech.api.capability;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;

import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for multiblocks (or machines) that hold and process recipes.
 * Decouples recipe processing capability from class hierarchy, enabling
 * composition-based recipe support for any MultiblockWithDisplayBase subclass.
 *
 * <p>This is the core "recipe holder" contract. For multiblocks that support
 * switching between multiple RecipeMaps, see {@link IMultipleRecipeMaps} which
 * can be composed alongside this interface.
 *
 * @see IMultipleRecipeMaps
 */
public interface IRecipeMapHolder {

    /**
     * @return the current RecipeMap this holder is processing with, or null if none
     */
    @Nullable
    RecipeMap<?> getRecipeMap();

    /**
     * @return the recipe logic workable responsible for recipe execution
     */
    @NotNull
    gregtech.api.capability.impl.AbstractRecipeLogic getRecipeMapWorkable();

    /**
     * @return the combined input item inventory for recipe processing
     */
    @NotNull
    IItemHandlerModifiable getInputInventory();

    /**
     * @return the combined output item inventory for recipe processing
     */
    @NotNull
    IItemHandlerModifiable getOutputInventory();

    /**
     * @return the combined input fluid inventory for recipe processing
     */
    @NotNull
    IMultipleTankHandler getInputFluidInventory();

    /**
     * @return the combined output fluid inventory for recipe processing
     */
    @NotNull
    IMultipleTankHandler getOutputFluidInventory();

    /**
     * @return the energy container used for recipe processing, or null if not applicable
     */
    @Nullable
    IEnergyContainer getEnergyContainer();

    /**
     * Performs extra checks for validity of a given recipe before the multiblock starts processing it.
     *
     * @param recipe           the recipe to check
     * @param consumeIfSuccess whether to consume inputs if the check passes
     * @return true if the recipe is valid for this holder
     */
    boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess);

    /**
     * Refreshes all relevant state before recipe input consumption.
     * Typically iterates over {@link gregtech.api.metatileentity.interfaces.IRefreshBeforeConsumption}
     * parts and invokes their refresh logic.
     */
    void refreshAllBeforeConsumption();
}
