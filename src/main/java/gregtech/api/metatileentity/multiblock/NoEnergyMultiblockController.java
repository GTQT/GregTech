package gregtech.api.metatileentity.multiblock;

import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.NoEnergyMultiblockRecipeLogic;
import gregtech.api.metatileentity.multiblock.ui.KeyManager;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.UISyncer;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.ConfigHolder;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class NoEnergyMultiblockController extends RecipeMapMultiblockController{

    public NoEnergyMultiblockController(ResourceLocation metaTileEntityId,
                                        RecipeMap<?> recipeMap) {
        super(metaTileEntityId,recipeMap);
        this.recipeMapWorkable = new NoEnergyMultiblockRecipeLogic(this);
    }

    @Override
    protected void initializeAbilities() {
        this.inputInventory = new ItemHandlerList(getAbilities(MultiblockAbility.IMPORT_ITEMS));
        this.inputFluidInventory = new FluidTankList(allowSameFluidFillForOutputs(),
                getAbilities(MultiblockAbility.IMPORT_FLUIDS));
        this.outputInventory = new ItemHandlerList(getAbilities(MultiblockAbility.EXPORT_ITEMS));
        this.outputFluidInventory = new FluidTankList(allowSameFluidFillForOutputs(),
                getAbilities(MultiblockAbility.EXPORT_FLUIDS));
    }

    public TraceabilityPredicate autoAbilities(
            boolean checkMaintenance,
                                               boolean checkItemIn,
                                               boolean checkItemOut,
                                               boolean checkFluidIn,
                                               boolean checkFluidOut,
                                               boolean checkMuffler) {
        TraceabilityPredicate predicate = super.autoAbilities(checkMaintenance, checkMuffler);


        return predicate;
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(recipeMapWorkable.isWorkingEnabled(), recipeMapWorkable.isActive())
                .addEnergyTierLine(GTUtility.getTierByVoltage(recipeMapWorkable.getMaxVoltage()))
                .addCustom(this::addCustomCapacity)
                .addParallelsLine(recipeMapWorkable.getParallelLimit())
                .addWorkingStatusLine();

        // Cross-recipe parallel display
        if (recipeMapWorkable.isCrossRecipeMode() && recipeMapWorkable.getCrossRecipeScheduler() != null) {
            addCrossRecipeDisplay(builder, recipeMapWorkable);
        } else {
            builder.addProgressLine(recipeMapWorkable.getProgress(), recipeMapWorkable.getMaxProgress())
                    .addRecipeOutputLine(recipeMapWorkable);
        }
    }

    public void addCustomCapacity(KeyManager keyManager, UISyncer syncer) {

    }

    @Override
    public List<ITextComponent> getDataInfo() {
        List<ITextComponent> list = new ArrayList<>();

        // Cross-recipe parallel: show slot details instead of single progress
        if (recipeMapWorkable.isCrossRecipeMode() && recipeMapWorkable.getCrossRecipeScheduler() != null) {
            RecipeMapMultiblockController.addCrossRecipeTricorderInfo(list,
                    recipeMapWorkable.getCrossRecipeScheduler());
        } else if (recipeMapWorkable.getMaxProgress() > 0) {
            list.add(new TextComponentTranslation("behavior.tricorder.workable_progress",
                    new TextComponentTranslation(
                            TextFormattingUtil.formatNumbers(recipeMapWorkable.getProgress() / 20)).setStyle(
                            new Style().setColor(TextFormatting.GREEN)),
                    new TextComponentTranslation(
                            TextFormattingUtil.formatNumbers(recipeMapWorkable.getMaxProgress() / 20)).setStyle(
                            new Style().setColor(TextFormatting.YELLOW))
            ));
        }

        if (ConfigHolder.machines.enableMaintenance && hasMaintenanceMechanics()) {
            list.add(new TextComponentTranslation("behavior.tricorder.multiblock_maintenance",
                    new TextComponentTranslation(
                            TextFormattingUtil.formatNumbers(getNumMaintenanceProblems())).setStyle(
                            new Style().setColor(TextFormatting.RED))
            ));
        }

        if (recipeMapWorkable.getParallelLimit() > 1) {
            list.add(new TextComponentTranslation("behavior.tricorder.multiblock_parallel",
                    new TextComponentTranslation(
                            TextFormattingUtil.formatNumbers(recipeMapWorkable.getParallelLimit())).setStyle(
                            new Style().setColor(TextFormatting.GREEN))
            ));
        }

        return list;
    }
    
    @Override
    public boolean isMultiblockPartWeatherResistant(@NotNull IMultiblockPart part) {
        return true;
    }

    @Override
    public boolean getIsWeatherOrTerrainResistant() {
        return true;
    }
}
