package gregtech.api.metatileentity.multiblock;

import gregtech.api.capability.IControllable;
import gregtech.api.capability.IDistinctBusController;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.IRecipeMapHolder;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for parametric (variant-based) multiblocks that also process recipes.
 * Combines the variant system of {@link ParametricMultiblockController} with the
 * recipe processing capability of {@link IRecipeMapHolder}.
 *
 * <p>This class bridges the gap between the parametric variant system and the recipe
 * processing system, enabling variant multiblocks (e.g., LargeBoiler, LargeTurbine)
 * to be consolidated into a single MTE ID while retaining full recipe support.
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * public class MetaTileEntityLargeBoiler
 *         extends ParametricRecipeMapController<BoilerType> {
 *
 *     public MetaTileEntityLargeBoiler(ResourceLocation id) {
 *         super(id, BoilerType.class, BoilerType.BRONZE, RecipeMaps.LARGE_BOILER_RECIPES);
 *     }
 *
 *     // Optionally override to vary RecipeMap by variant
 *     @Override
 *     protected RecipeMap<?> getRecipeMapForVariant(BoilerType variant) {
 *         return variant.getRecipeMap();
 *     }
 * }
 * }</pre>
 *
 * @param <V> the variant enum type
 * @see ParametricMultiblockController
 * @see IRecipeMapHolder
 * @see RecipeAbilityManager
 */
public abstract class ParametricRecipeMapController<V extends Enum<V>>
        extends ParametricMultiblockController<V>
        implements IRecipeMapHolder, IControllable, IDistinctBusController {

    protected final RecipeMap<?> defaultRecipeMap;
    protected final RecipeAbilityManager abilityManager;
    protected MultiblockRecipeLogic recipeMapWorkable;

    protected ParametricRecipeMapController(@NotNull ResourceLocation metaTileEntityId,
                                            @NotNull Class<V> variantClass,
                                            @NotNull V defaultVariant,
                                            @NotNull RecipeMap<?> recipeMap) {
        super(metaTileEntityId, variantClass, defaultVariant);
        this.defaultRecipeMap = recipeMap;
        this.abilityManager = new RecipeAbilityManager(this);
        this.recipeMapWorkable = createWorkable();
    }

    // region Recipe Logic

    /**
     * Creates the recipe logic instance. Subclasses may override to provide custom logic.
     */
    @NotNull
    protected MultiblockRecipeLogic createWorkable() {
        return new MultiblockRecipeLogic(this, getRecipeMapForVariant(getVariant()));
    }

    /**
     * Returns the RecipeMap for a specific variant. Override to provide different
     * RecipeMaps for different variants (e.g., each turbine type uses a different fuel map).
     *
     * <p>Note: {@code MetaTileEntity.getRecipeMap()} is {@code final} and delegates to
     * the workable's RecipeMap. When variant changes, call
     * {@code recipeMapWorkable.setRecipeMap(getRecipeMapForVariant(variant))} from
     * {@link #onVariantChanged()} to update the effective RecipeMap.
     *
     * @param variant the current variant
     * @return the RecipeMap for this variant
     */
    @NotNull
    protected RecipeMap<?> getRecipeMapForVariant(@NotNull V variant) {
        return defaultRecipeMap;
    }

    @Override
    @NotNull
    public MultiblockRecipeLogic getRecipeMapWorkable() {
        return recipeMapWorkable;
    }

    // endregion

    // region IRecipeMapHolder Implementation

    @Override
    @NotNull
    public IItemHandlerModifiable getInputInventory() {
        return abilityManager.getInputInventory();
    }

    @Override
    @NotNull
    public IItemHandlerModifiable getOutputInventory() {
        return abilityManager.getOutputInventory();
    }

    @Override
    @NotNull
    public IMultipleTankHandler getInputFluidInventory() {
        return abilityManager.getInputFluidInventory();
    }

    @Override
    @NotNull
    public IMultipleTankHandler getOutputFluidInventory() {
        return abilityManager.getOutputFluidInventory();
    }

    @Override
    @Nullable
    public IEnergyContainer getEnergyContainer() {
        return abilityManager.getEnergyContainer();
    }

    @Override
    public boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess) {
        return true;
    }

    @Override
    public void refreshAllBeforeConsumption() {
        abilityManager.refreshAllBeforeConsumption();
    }

    // endregion

    // region Structure Lifecycle

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        initializeAbilities();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        abilityManager.reset();
        recipeMapWorkable.invalidate();
    }

    @Override
    protected void updateFormedValid() {
        if (!hasMufflerMechanics() || isMufflerReady()) {
            recipeMapWorkable.updateWorkable();
        }
    }

    /**
     * Initializes abilities from multiblock parts. Subclasses may override for custom ability setup.
     */
    protected void initializeAbilities() {
        abilityManager.initialize(allowSameFluidFillForOutputs());
    }

    /**
     * @return whether to allow same fluid fill for output fluid tank lists
     */
    protected boolean allowSameFluidFillForOutputs() {
        return true;
    }

    @Override
    public boolean isActive() {
        return isStructureFormed() && recipeMapWorkable.isActive() && recipeMapWorkable.isWorkingEnabled();
    }

    @Override
    protected boolean isWorkingForStructureCheck() {
        return recipeMapWorkable != null && recipeMapWorkable.isActive();
    }

    // endregion

    // region IControllable

    @Override
    public boolean isWorkingEnabled() {
        return recipeMapWorkable.isWorkingEnabled();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        recipeMapWorkable.setWorkingEnabled(isWorkingAllowed);
    }

    // endregion

    // region IDistinctBusController

    @Override
    public boolean canBeDistinct() {
        return false;
    }

    @Override
    public boolean isDistinct() {
        return false;
    }

    @Override
    public void setDistinct(boolean isDistinct) {
        // Default: no distinct mode for parametric recipe controllers
    }

    // endregion

    // region Variant Lifecycle

    @Override
    protected void onVariantChanged() {
        // Update the workable's RecipeMap when variant changes
        recipeMapWorkable.setRecipeMap(getRecipeMapForVariant(getVariant()));
    }

    @Override
    public SoundEvent getSound() {
        RecipeMap<?> map = getRecipeMap();
        return map != null ? map.getSound() : null;
    }

    // endregion
}
