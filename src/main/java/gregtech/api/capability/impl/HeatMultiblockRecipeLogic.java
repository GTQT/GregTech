package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.capability.IHeatable;
import gregtech.api.metatileentity.multiblock.HeatMultiblockController;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.OCParams;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.recipes.properties.impl.HeatProperty;
import gregtech.api.recipes.properties.impl.TemperatureProperty;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;

public class HeatMultiblockRecipeLogic extends MultiblockRecipeLogic {

    int recipeHeat;
    int recipeTemperature;

    /**
     * 热量系统有两个参数
     * 热量：每tick消耗的能量，计算时，如果有多个热源仓，则所有热源仓均扣除相应的热量
     * 温度：热源的温度，相当于启动条件，参与计算超频，每高200k，耗时*0.9一次
     * 注意：热量温度二者之间没有任何关联
     */

    public HeatMultiblockRecipeLogic(HeatMultiblockController tileEntity) {
        super(tileEntity);
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
        return GTValues.V[GTValues.ULV];
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

    @Override
    public long getInfoProviderEUt() {
        return 0;
    }

    @MustBeInvokedByOverriders
    protected void setupRecipe(@NotNull Recipe recipe) {
        super.setupRecipe(recipe);
        recipeHeat = recipe.getProperty(HeatProperty.getInstance(), 0);
        recipeTemperature = recipe.getProperty(TemperatureProperty.getInstance(), 0);
    }

    @MustBeInvokedByOverriders
    protected void completeRecipe() {
        super.completeRecipe();
        recipeHeat = 0;
    }

    @NotNull
    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = super.serializeNBT();
        tag.setInteger("recipeHeat", recipeHeat);
        tag.setInteger("recipeTemperature", recipeTemperature);
        return tag;
    }

    @Override
    public void deserializeNBT(@NotNull NBTTagCompound compound) {
        super.deserializeNBT(compound);
        recipeHeat = compound.getInteger("recipeHeat");
        recipeTemperature = compound.getInteger("recipeTemperature");
    }

    @Override
    public boolean checkRecipe(@NotNull Recipe recipe) {
        recipeTemperature = recipe.getProperty(TemperatureProperty.getInstance(), 0);
        if (getTemperature() >= recipeTemperature)
            return super.checkRecipe(recipe);
        return false;
    }

    /**
     * Update the current running recipe's progress
     * <p>
     * Also handles consuming running heat by default
     * </p>
     */
    @Override
    protected void updateRecipeProgress() {
        if (canRecipeProgress && drawHeat(recipeHeat, true)) {
            drawHeat(recipeHeat, false);
            // as recipe starts with progress on 1 this has to be > only not => to compensate for it
            if (++progressTime > maxProgressTime) {
                completeRecipe();
            }
        } else if (recipeHeat > 0) {
            decreaseProgress();
        }
    }

    /**
     * Draw heat from the heat container
     *
     * @param recipeHeat the EUt to remove
     * @param simulate   whether to simulate heat extraction or not
     * @return true if the heat can/was drained, otherwise false
     */
    protected boolean drawHeat(long recipeHeat, boolean simulate) {
        recipeHeat = appendEfficiency(recipeHeat);
        // this should be the ONLY time eut is negative!
        if (consumesHeat()) recipeHeat = -recipeHeat;
        long resultHeat = getHeatStored() + recipeHeat;
        if (resultHeat >= 0L && resultHeat <= getHeatCapacity()) {
            if (!simulate) changeHeat(recipeHeat);
            return true;
        } else return false;
    }

    private boolean consumesHeat() {
        return true;
    }

    private void changeHeat(long recipeEUt) {
        HeatMultiblockController controller = (HeatMultiblockController) metaTileEntity;
        if (controller.getHeatHatch() != null) return;
        controller.getHeatHatch()
                .forEach(hatch -> hatch.changeHeat(recipeEUt));

    }

    private long getHeatStored() {
        HeatMultiblockController controller = (HeatMultiblockController) metaTileEntity;
        if (controller.getHeatHatch() != null) return 0;
        return controller.getHeatHatch()
                .stream()
                .mapToLong(IHeatable::getHeatStored)
                .sum();
    }

    private long getHeatCapacity() {
        HeatMultiblockController controller = (HeatMultiblockController) metaTileEntity;
        if (controller.getHeatHatch() != null) return 0;
        return controller.getHeatHatch()
                .stream()
                .mapToLong(IHeatable::getHeatCapacity)
                .sum();
    }

    private int getTemperature() {
        HeatMultiblockController controller = (HeatMultiblockController) metaTileEntity;
        if (controller.getHeatHatch() != null) return 0;
        return controller.getHeatHatch()
                .stream()
                .mapToInt(IHeatable::getTemperature)
                .max()
                .orElse(0);
    }

    @Override
    protected void modifyOverclockPost(@NotNull OCResult ocResult, @NotNull RecipePropertyStorage storage) {
        super.modifyOverclockPost(ocResult, storage);

        // 每高出200k，耗时*90%一次
        int currentTemperature = getTemperature();

        if (currentTemperature > recipeTemperature) {
            int excessTemperature = currentTemperature - recipeTemperature;
            int bonusCount = excessTemperature / 200; // 每高出200K计算一次加成

            double multiplier = Math.pow(0.9, bonusCount);
            int newDuration = (int) (ocResult.duration() * multiplier);

            ocResult.setDuration(newDuration);
        }
    }
}
