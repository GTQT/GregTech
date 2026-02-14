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
import gregtech.api.util.GTUtility;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HeatMultiblockRecipeLogic extends MultiblockRecipeLogic {

    int recipeHeat;
    int recipeTemperature;
    HeatMultiblockController metaTileEntity;

    /**
     * 热量系统有两个参数 热量：每tick消耗的能量，计算时，如果有多个热源仓，则所有热源仓均扣除相应的热量 温度：热源的温度，相当于启动条件，参与计算超频，每高200k，耗时*0.9一次 注意：热量温度二者之间没有任何关联
     */

    public HeatMultiblockRecipeLogic(HeatMultiblockController tileEntity) {
        super(tileEntity);
        metaTileEntity = tileEntity;
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
        return GTValues.V[calculateBousCount()];
    }

    @Override
    protected void runOverclockingLogic(OCParams ocParams, OCResult ocResult, RecipePropertyStorage propertyStorage,
                                        long maxVoltage) {
        ocParams.setEut(1L);
        super.runOverclockingLogic(ocParams, ocResult, propertyStorage, maxVoltage);
    }

    @Override
    public long getMaximumOverclockVoltage() {
        //每高出273K 200K相当于一级电压
        //273K -> ULV
        //473K -> LV
        return this.getMaxVoltage();
    }

    @Override
    public long getInfoProviderEUt() {
        return recipeHeat;
    }

    @MustBeInvokedByOverriders
    protected void setupRecipe(@NotNull Recipe recipe) {
        super.setupRecipe(recipe);

        if (recipeEUt != 0) {
            recipeHeat = Math.max((int) recipeEUt, 7);
            recipeTemperature = Math.max(473 + 200 * GTUtility.getTierByVoltage(recipeEUt), 373);
        } else {
            //最低写7，对应ULV
            recipeHeat = recipe.getProperty(HeatProperty.getInstance(), 7);
            //起码100° 373k
            recipeTemperature = recipe.getProperty(TemperatureProperty.getInstance(), 373);
        }
    }

    @MustBeInvokedByOverriders
    protected void completeRecipe() {
        super.completeRecipe();
        recipeHeat = 0;
        recipeTemperature = 0;
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
        if (metaTileEntity.getTemperature() >= recipeTemperature)
            return super.checkRecipe(recipe);
        setWhyFailed("温度过低，配方需求至少 " + recipeTemperature + "K 温度");
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
        long resultHeat = metaTileEntity.getHeatStored() + recipeHeat;
        if (resultHeat >= 0L && resultHeat <= metaTileEntity.getHeatCapacity()) {
            if (!simulate) metaTileEntity.changeHeat(recipeHeat);
            return true;
        } else return false;
    }

    private boolean consumesHeat() {
        return true;
    }

    @Override
    protected void modifyOverclockPost(@NotNull OCResult ocResult, @NotNull RecipePropertyStorage storage) {
        super.modifyOverclockPost(ocResult, storage);

        // 每高出配方温度200k，耗时*90%一次
        int currentTemperature = getTemperature();

        if (currentTemperature > recipeTemperature) {
            int excessTemperature = currentTemperature - recipeTemperature;
            int bonusCount = excessTemperature / 200; // 每高出200K计算一次加成

            double multiplier = Math.pow(0.9, bonusCount);
            int newDuration = (int) (ocResult.duration() * multiplier);

            ocResult.setDuration(newDuration);
        }
    }

    public int getTemperature() {
        if (!metaTileEntity.isStructureFormed()) return 293;
        List<IHeatable> heatable = metaTileEntity.getHeatHatch();
        if (heatable == null) return 293;
        return heatable
                .stream()
                .mapToInt(IHeatable::getTemperature)
                .max()
                .orElse(293);
    }

    public int calculateBousCount() {
        if (getTemperature() < 473) return 0; //ULV
        int excessTemperature = getTemperature() - 273;
        // 每高出200K计算一次加成
        //刚好473也算ULV 474-673 LV 674-873 MV
        return (excessTemperature / 200) - 1;
    }
}
