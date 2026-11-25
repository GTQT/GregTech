package gregtech.api.recipes.logic;

import gregtech.api.capability.IMultiblockController;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.metatileentity.multiblock.ICleanroomProvider;
import gregtech.api.metatileentity.multiblock.ICleanroomReceiver;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.properties.impl.CleanroomProperty;
import gregtech.common.ConfigHolder;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Built-in {@link IRecipeCondition} enforcing {@link CleanroomProperty} requirements across
 * recipe start, per-tick execution, and world reloads in both single-recipe and
 * {@link CrossRecipeParallelScheduler} multi-slot modes.
 */
public final class CleanroomRecipeCondition implements IRecipeCondition {

    public static final String CONDITION_ID = "gregtech:cleanroom";
    private static final String NBT_KEY_TYPE = "type";
    public static final String MISSING_CLEANROOM_REASON = "当前配方需要超净环境！";

    private final Supplier<MetaTileEntity> mteSupplier;
    @Nullable
    private final Consumer<String> failureReasonSink;

    public CleanroomRecipeCondition(@NotNull Supplier<MetaTileEntity> mteSupplier,
                                    @Nullable Consumer<String> failureReasonSink) {
        this.mteSupplier = mteSupplier;
        this.failureReasonSink = failureReasonSink;
    }

    @Override
    @NotNull
    public String getConditionId() {
        return CONDITION_ID;
    }

    @Override
    public boolean canStart(@NotNull Recipe recipe,
                            @NotNull IItemHandlerModifiable importInventory,
                            @NotNull IMultipleTankHandler importFluids) {
        return checkType(recipe.getProperty(CleanroomProperty.getInstance(), null), true);
    }

    @Override
    public void onRecipeStart(@NotNull RecipeExecutionContext context) {
        Recipe recipe = context.getRecipe();
        if (recipe == null) return;
        CleanroomType requiredType = recipe.getProperty(CleanroomProperty.getInstance(), null);
        if (requiredType != null) {
            context.getConditionData(CONDITION_ID).setString(NBT_KEY_TYPE, requiredType.getName());
        }
    }

    @Override
    public boolean testTick(@NotNull RecipeExecutionContext context) {
        return checkType(resolveRequiredType(context), false);
    }

    @Override
    public boolean handlesEnergyShortage() {
        return false;
    }

    @Override
    @NotNull
    public TickFailureAction onTickFailed(@NotNull RecipeExecutionContext context, boolean energyShortage) {
        if (!energyShortage && failureReasonSink != null) {
            failureReasonSink.accept(MISSING_CLEANROOM_REASON);
        }
        return TickFailureAction.DECREASE_PROGRESS;
    }

    public boolean checkRecipe(@NotNull Recipe recipe) {
        return checkType(recipe.getProperty(CleanroomProperty.getInstance(), null), true);
    }

    @Nullable
    private static CleanroomType resolveRequiredType(@NotNull RecipeExecutionContext context) {
        Recipe recipe = context.getRecipe();
        if (recipe != null) {
            return recipe.getProperty(CleanroomProperty.getInstance(), null);
        }
        NBTTagCompound extra = context.getExtraData();
        if (extra.hasKey(CONDITION_ID, Constants.NBT.TAG_COMPOUND)) {
            String name = extra.getCompoundTag(CONDITION_ID).getString(NBT_KEY_TYPE);
            if (!name.isEmpty()) {
                return CleanroomType.getByName(name);
            }
        }
        return null;
    }

    private boolean checkType(@Nullable CleanroomType requiredType, boolean recordReason) {
        if (requiredType == null) return true;

        MetaTileEntity mte = mteSupplier.get();
        if (mte instanceof ICleanroomReceiver receiver) {
            if (ConfigHolder.machines.cleanMultiblocks && mte instanceof IMultiblockController) {
                return true;
            }
            ICleanroomProvider provider = receiver.getCleanroom();
            if (provider != null && provider.isClean() && provider.checkCleanroomType(requiredType)) {
                return true;
            }
        }
        if (recordReason && failureReasonSink != null) {
            failureReasonSink.accept(MISSING_CLEANROOM_REASON);
        }
        return false;
    }
}
