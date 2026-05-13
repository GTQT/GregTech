package gregtech.api.recipes.logic;

import gregtech.api.recipes.Recipe;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single execution slot within the {@link CrossRecipeParallelScheduler}.
 * Each slot independently tracks one recipe instance's progress, energy cost, and outputs.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>IDLE: No recipe assigned, ready to accept a new recipe via {@link #startRecipe}.</li>
 *   <li>RUNNING: Recipe in progress, {@link #tick()} increments progressTime each tick.</li>
 *   <li>COMPLETED: progressTime > maxProgressTime, outputs ready to be extracted.</li>
 * </ol>
 */
public class RecipeSlot {

    public enum State {
        IDLE,
        RUNNING,
        COMPLETED
    }

    // --- Slot identity ---
    private final int slotIndex;

    // --- Runtime state ---
    private State state = State.IDLE;
    private int progressTime;
    private int maxProgressTime;
    private long recipeEUt;

    // --- Outputs to produce on completion ---
    @NotNull
    private List<ItemStack> itemOutputs = Collections.emptyList();
    @NotNull
    private List<FluidStack> fluidOutputs = Collections.emptyList();

    // --- The original recipe reference (for display/debug only, not serialized) ---
    @Nullable
    private transient Recipe sourceRecipe;

    // --- Human-readable recipe label for UI display (serialized for reloads) ---
    @NotNull
    private String recipeDisplayName = "";

    // --- Parallel multiplier applied to this slot ---
    private int parallelCount = 1;

    // --- Total recipe operations this slot will complete ---
    // = inputParallel × subTickParallel × batchMultiplier.
    // Used for completion reporting (recipe tally). Defaults to parallelCount.
    // Not used for scheduler budget — that's tracked by parallelCount alone.
    private int totalOperations = 1;

    public RecipeSlot(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    // ==================== Lifecycle Operations ====================

    /**
     * Assigns a recipe to this slot and transitions to RUNNING state.
     *
     * @param recipe        the overclocked/paralleled recipe to run
     * @param duration      the total duration in ticks (after overclock)
     * @param eut           the EU/t consumption for this slot
     * @param itemOutputs   the item outputs to produce on completion
     * @param fluidOutputs  the fluid outputs to produce on completion
     * @param parallelCount the number of parallel operations this slot represents
     */
    public void startRecipe(@NotNull Recipe recipe, int duration, long eut,
                            @NotNull List<ItemStack> itemOutputs,
                            @NotNull List<FluidStack> fluidOutputs,
                            int parallelCount) {
        startRecipe(recipe, duration, eut, itemOutputs, fluidOutputs, parallelCount, parallelCount, "");
    }

    public void startRecipe(@NotNull Recipe recipe, int duration, long eut,
                            @NotNull List<ItemStack> itemOutputs,
                            @NotNull List<FluidStack> fluidOutputs,
                            int parallelCount,
                            @NotNull String recipeDisplayName) {
        startRecipe(recipe, duration, eut, itemOutputs, fluidOutputs, parallelCount, parallelCount, recipeDisplayName);
    }

    public void startRecipe(@NotNull Recipe recipe, int duration, long eut,
                            @NotNull List<ItemStack> itemOutputs,
                            @NotNull List<FluidStack> fluidOutputs,
                            int parallelCount,
                            int totalOperations,
                            @NotNull String recipeDisplayName) {
        this.sourceRecipe = recipe;
        this.progressTime = 1;
        this.maxProgressTime = duration;
        this.recipeEUt = eut;
        this.itemOutputs = new ArrayList<>(itemOutputs);
        this.fluidOutputs = new ArrayList<>(fluidOutputs);
        this.parallelCount = parallelCount;
        this.totalOperations = totalOperations;
        this.recipeDisplayName = recipeDisplayName;
        this.state = State.RUNNING;
    }

    /**
     * Advances the progress by one tick.
     *
     * @return true if the recipe just completed this tick
     */
    public boolean tick() {
        if (state != State.RUNNING) {
            return false;
        }
        if (++progressTime > maxProgressTime) {
            state = State.COMPLETED;
            return true;
        }
        return false;
    }

    /**
     * Resets this slot to IDLE state after outputs have been extracted.
     */
    public void reset() {
        this.state = State.IDLE;
        this.progressTime = 0;
        this.maxProgressTime = 0;
        this.recipeEUt = 0;
        this.itemOutputs = Collections.emptyList();
        this.fluidOutputs = Collections.emptyList();
        this.sourceRecipe = null;
        this.recipeDisplayName = "";
        this.parallelCount = 1;
        this.totalOperations = 1;
    }

    // ==================== Getters ====================

    public int getSlotIndex() {
        return slotIndex;
    }

    public State getState() {
        return state;
    }

    public boolean isIdle() {
        return state == State.IDLE;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public boolean isCompleted() {
        return state == State.COMPLETED;
    }

    public int getProgressTime() {
        return progressTime;
    }

    public int getMaxProgressTime() {
        return maxProgressTime;
    }

    public long getRecipeEUt() {
        return recipeEUt;
    }

    @NotNull
    public List<ItemStack> getItemOutputs() {
        return itemOutputs;
    }

    @NotNull
    public List<FluidStack> getFluidOutputs() {
        return fluidOutputs;
    }

    @Nullable
    public Recipe getSourceRecipe() {
        return sourceRecipe;
    }

    @NotNull
    public String getRecipeDisplayName() {
        return recipeDisplayName;
    }

    public int getParallelCount() {
        return parallelCount;
    }

    public int getTotalOperations() {
        return totalOperations;
    }

    /**
     * @return progress as a percentage (0.0 to 1.0)
     */
    public double getProgressPercent() {
        if (maxProgressTime == 0) return 0.0;
        return (double) progressTime / maxProgressTime;
    }

    // ==================== Serialization ====================

    @NotNull
    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("state", state.ordinal());
        tag.setInteger("slotIndex", slotIndex);
        tag.setInteger("parallelCount", parallelCount);
        tag.setInteger("totalOperations", totalOperations);

        if (state == State.RUNNING || state == State.COMPLETED) {
            tag.setInteger("progressTime", progressTime);
            tag.setInteger("maxProgressTime", maxProgressTime);
            tag.setLong("recipeEUt", recipeEUt);
            tag.setString("recipeDisplayName", recipeDisplayName);

            NBTTagList itemList = new NBTTagList();
            for (ItemStack stack : itemOutputs) {
                itemList.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
            tag.setTag("itemOutputs", itemList);

            NBTTagList fluidList = new NBTTagList();
            for (FluidStack fluid : fluidOutputs) {
                fluidList.appendTag(fluid.writeToNBT(new NBTTagCompound()));
            }
            tag.setTag("fluidOutputs", fluidList);
        }

        return tag;
    }

    public void deserializeNBT(@NotNull NBTTagCompound tag) {
        this.state = State.values()[tag.getInteger("state")];
        this.parallelCount = tag.getInteger("parallelCount");
        this.totalOperations = tag.getInteger("totalOperations");

        if (state == State.RUNNING || state == State.COMPLETED) {
            this.progressTime = tag.getInteger("progressTime");
            this.maxProgressTime = tag.getInteger("maxProgressTime");
            this.recipeEUt = tag.getLong("recipeEUt");
            this.recipeDisplayName = tag.getString("recipeDisplayName");

            NBTTagList itemList = tag.getTagList("itemOutputs", Constants.NBT.TAG_COMPOUND);
            this.itemOutputs = new ArrayList<>(itemList.tagCount());
            for (int i = 0; i < itemList.tagCount(); i++) {
                this.itemOutputs.add(new ItemStack(itemList.getCompoundTagAt(i)));
            }

            NBTTagList fluidList = tag.getTagList("fluidOutputs", Constants.NBT.TAG_COMPOUND);
            this.fluidOutputs = new ArrayList<>(fluidList.tagCount());
            for (int i = 0; i < fluidList.tagCount(); i++) {
                this.fluidOutputs.add(FluidStack.loadFluidStackFromNBT(fluidList.getCompoundTagAt(i)));
            }
        }
    }

    @Override
    public String toString() {
        return "RecipeSlot{" +
                "index=" + slotIndex +
                ", state=" + state +
                ", progress=" + progressTime + "/" + maxProgressTime +
                ", eut=" + recipeEUt +
                ", parallel=" + parallelCount +
                ", ops=" + totalOperations +
                '}';
    }
}
