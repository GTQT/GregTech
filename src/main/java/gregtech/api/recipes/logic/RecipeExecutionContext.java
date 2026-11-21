package gregtech.api.recipes.logic;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.recipes.Recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Execution context passed to {@link IRecipeCondition} callbacks, unifying single-recipe
 * execution in {@link gregtech.api.capability.impl.AbstractRecipeLogic} and per-slot
 * execution in {@link CrossRecipeParallelScheduler}.
 */
public final class RecipeExecutionContext {

    @Nullable
    private final RecipeSlot slot;
    @Nullable
    private final Recipe recipe;
    private final int parallelCount;
    private final int totalOperations;
    private final int progressTime;
    private final int maxProgressTime;
    private final long recipeEUt;
    @Nullable
    private final NBTTagCompound extraData;
    @Nullable
    private final List<ItemStack> itemOutputs;
    @Nullable
    private final List<FluidStack> fluidOutputs;
    @Nullable
    private final IItemHandlerModifiable inputInventory;
    @Nullable
    private final IMultipleTankHandler inputFluids;

    private RecipeExecutionContext(@Nullable RecipeSlot slot,
                                   @Nullable Recipe recipe,
                                   int parallelCount,
                                   int totalOperations,
                                   int progressTime,
                                   int maxProgressTime,
                                   long recipeEUt,
                                   @Nullable NBTTagCompound extraData,
                                   @Nullable List<ItemStack> itemOutputs,
                                   @Nullable List<FluidStack> fluidOutputs,
                                   @Nullable IItemHandlerModifiable inputInventory,
                                   @Nullable IMultipleTankHandler inputFluids) {
        this.slot = slot;
        this.recipe = recipe;
        this.parallelCount = parallelCount;
        this.totalOperations = totalOperations;
        this.progressTime = progressTime;
        this.maxProgressTime = maxProgressTime;
        this.recipeEUt = recipeEUt;
        this.extraData = extraData;
        this.itemOutputs = itemOutputs;
        this.fluidOutputs = fluidOutputs;
        this.inputInventory = inputInventory;
        this.inputFluids = inputFluids;
    }

    /**
     * Creates a context backed by a scheduler {@link RecipeSlot}.
     */
    @NotNull
    public static RecipeExecutionContext forSlot(@NotNull RecipeSlot slot) {
        return forSlot(slot, null, null);
    }

    /**
     * Creates a context backed by a scheduler {@link RecipeSlot} along with the input handlers
     * used when starting the slot.
     */
    @NotNull
    public static RecipeExecutionContext forSlot(@NotNull RecipeSlot slot,
                                                 @Nullable IItemHandlerModifiable inputInventory,
                                                 @Nullable IMultipleTankHandler inputFluids) {
        return new RecipeExecutionContext(slot, null, 0, 0, 0, 0, 0L, null, null, null,
                inputInventory, inputFluids);
    }

    /**
     * Creates a context backed by single-recipe execution state.
     */
    @NotNull
    public static RecipeExecutionContext forSingle(@Nullable Recipe recipe,
                                                   int parallelCount,
                                                   int totalOperations,
                                                   int progressTime,
                                                   int maxProgressTime,
                                                   long recipeEUt,
                                                   @NotNull NBTTagCompound extraData,
                                                   @NotNull List<ItemStack> itemOutputs,
                                                   @NotNull List<FluidStack> fluidOutputs,
                                                   @Nullable IItemHandlerModifiable inputInventory,
                                                   @Nullable IMultipleTankHandler inputFluids) {
        return new RecipeExecutionContext(null, recipe, parallelCount, totalOperations, progressTime, maxProgressTime,
                recipeEUt, extraData, itemOutputs, fluidOutputs, inputInventory, inputFluids);
    }

    /**
     * @return the source {@link Recipe}, or {@code null} after a chunk reload (since recipes are transient)
     */
    @Nullable
    public Recipe getRecipe() {
        return slot != null ? slot.getSourceRecipe() : recipe;
    }

    public int getParallelCount() {
        return slot != null ? slot.getParallelCount() : parallelCount;
    }

    public int getTotalOperations() {
        return slot != null ? slot.getTotalOperations() : totalOperations;
    }

    public int getProgressTime() {
        return slot != null ? slot.getProgressTime() : progressTime;
    }

    public int getMaxProgressTime() {
        return slot != null ? slot.getMaxProgressTime() : maxProgressTime;
    }

    public long getRecipeEUt() {
        return slot != null ? slot.getRecipeEUt() : recipeEUt;
    }

    /**
     * @return the mutable persisted NBT compound attached to this recipe or slot
     */
    @NotNull
    public NBTTagCompound getExtraData() {
        return slot != null ? slot.getExtraData() : extraData;
    }

    /**
     * Returns the persisted sub-compound namespaced under {@code conditionId} inside {@link #getExtraData()},
     * creating and attaching it if absent.
     */
    @NotNull
    public NBTTagCompound getConditionData(@NotNull String conditionId) {
        NBTTagCompound extra = getExtraData();
        if (!extra.hasKey(conditionId, Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound tag = new NBTTagCompound();
            extra.setTag(conditionId, tag);
            return tag;
        }
        return extra.getCompoundTag(conditionId);
    }

    public boolean isSchedulerSlot() {
        return slot != null;
    }

    @Nullable
    public RecipeSlot getSlot() {
        return slot;
    }

    /**
     * @return the mutable list of item outputs for this recipe or slot
     */
    @NotNull
    public List<ItemStack> getItemOutputs() {
        return slot != null ? slot.getItemOutputs() : itemOutputs;
    }

    /**
     * @return the mutable list of fluid outputs for this recipe or slot
     */
    @NotNull
    public List<FluidStack> getFluidOutputs() {
        return slot != null ? slot.getFluidOutputs() : fluidOutputs;
    }

    /**
     * @return the item input inventory if available during start/single-recipe execution, or {@code null}
     */
    @Nullable
    public IItemHandlerModifiable getInputInventory() {
        return inputInventory;
    }

    /**
     * @return the fluid input tanks if available during start/single-recipe execution, or {@code null}
     */
    @Nullable
    public IMultipleTankHandler getInputFluids() {
        return inputFluids;
    }
}
