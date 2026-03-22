package gregtech.api.metatileentity.multiblock;

import gregtech.api.capability.IHeatMachine;
import gregtech.api.capability.IHeatable;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.HeatMultiblockRecipeLogic;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.TemplateBarBuilder;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.ConfigHolder;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public abstract class HeatMultiblockController extends RecipeMapMultiblockController implements ProgressBarMultiblock,
                                                                                                IHeatMachine {

    List<IHeatable> heatHatch = null;

    public HeatMultiblockController(ResourceLocation metaTileEntityId,
                                    RecipeMap<?> recipeMap) {
        super(metaTileEntityId, recipeMap);
        this.recipeMapWorkable = new HeatMultiblockRecipeLogic(this);
    }

    public List<IHeatable> getHeatHatch() {
        return heatHatch;
    }

    @Override
    protected void initializeAbilities() {
        this.inputInventory = new ItemHandlerList(getAbilities(MultiblockAbility.IMPORT_ITEMS));
        this.inputFluidInventory = new FluidTankList(allowSameFluidFillForOutputs(),
                getAbilities(MultiblockAbility.IMPORT_FLUIDS));
        this.outputInventory = new ItemHandlerList(getAbilities(MultiblockAbility.EXPORT_ITEMS));
        this.outputFluidInventory = new FluidTankList(allowSameFluidFillForOutputs(),
                getAbilities(MultiblockAbility.EXPORT_FLUIDS));


        if (!getAbilities(MultiblockAbility.INPUT_HEAT).isEmpty())
            heatHatch = getAbilities(MultiblockAbility.INPUT_HEAT);
        else if (!getAbilities(MultiblockAbility.OUTPUT_HEAT).isEmpty())
            heatHatch = getAbilities(MultiblockAbility.OUTPUT_HEAT);
        else heatHatch = null;
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    @Override
    public int getProgressBarCount() {
        return 1;
    }

    @Override
    public GTGuiTheme getUITheme() {
        return GTGuiTheme.STEEL;
    }

    @Override
    protected void configureWarningText(MultiblockUIBuilder builder) {
        super.configureWarningText(builder);
        builder.addCustom((manager, syncer) -> {
            if (isStructureFormed() && syncer.syncBoolean(getHeatStored() == 0)) {
                manager.add(KeyUtil.lang(TextFormatting.YELLOW,
                        "gregtech.multiblock.heat_multiblock.no_heat"));
            }
        });
    }

    @Override
    public void registerBars(List<UnaryOperator<TemplateBarBuilder>> bars, PanelSyncManager syncManager) {
        LongSyncValue heatFilledValue = new LongSyncValue(this::getHeatStored);
        LongSyncValue heatCapacityValue = new LongSyncValue(this::getHeatCapacity);
        syncManager.syncValue("heat_filled", heatFilledValue);
        syncManager.syncValue("heat_capacity", heatCapacityValue);

        bars.add(barTest -> barTest
                .progress(() -> heatCapacityValue.getIntValue() == 0 ? 0 :
                        heatFilledValue.getIntValue() * 1.0 / heatCapacityValue.getIntValue())
                .texture(GTGuiTextures.PROGRESS_BAR_HEAT_TEMP)
                .tooltipBuilder(tooltip -> {
                    if (isStructureFormed()) {
                        if (heatFilledValue.getIntValue() == 0) {
                            tooltip.addLine(IKey.lang("gregtech.multiblock.heat_multiblock.no_heat"));
                        } else {
                            tooltip.addLine(IKey.lang("gregtech.multiblock.heat_multiblock.heat_bar_hover",
                                    heatFilledValue.getIntValue(), heatCapacityValue.getIntValue()));
                        }
                    } else {
                        tooltip.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
                    }
                }));
    }

    public void changeHeat(long amount) {
        if (getHeatHatch() == null) return;
        long average = amount / getHeatHatch().size();
        getHeatHatch().forEach(hatch -> hatch.changeHeat(average));
    }


    public long getHeatStored() {
        if (this.getHeatHatch() == null) return 0;
        return this.getHeatHatch()
                .stream()
                .mapToLong(IHeatable::getHeatStored)
                .sum();
    }

    public long getHeatCapacity() {
        if (this.getHeatHatch() == null) return 0;
        return this.getHeatHatch()
                .stream()
                .mapToLong(IHeatable::getHeatCapacity)
                .sum();
    }

    @Override
    public int getTemperature() {
        if (this.getHeatHatch() == null) return 293;
        return this.getHeatHatch()
                .stream()
                .mapToInt(IHeatable::getTemperature)
                .max()
                .orElse(293);
    }

    public TraceabilityPredicate autoAbilities(
            boolean checkMaintenance,
            boolean checkItemIn,
            boolean checkItemOut,
            boolean checkFluidIn,
            boolean checkFluidOut,
            boolean checkMuffler) {
        TraceabilityPredicate predicate = super.autoAbilities(checkMaintenance, checkMuffler);

        predicate = predicate.or(abilities(MultiblockAbility.INPUT_HEAT).setPreviewCount(1).setMaxGlobalLimited(2));
        predicate = predicate.or(abilities(MultiblockAbility.HEAT_SENSOR).setMaxGlobalLimited(1));

        if (checkItemIn) {
            if (recipeMap.getMaxInputs() > 0) {
                predicate = predicate.or(abilities(MultiblockAbility.IMPORT_ITEMS).setPreviewCount(1));
            }
        }
        if (checkItemOut) {
            if (recipeMap.getMaxOutputs() > 0) {
                predicate = predicate.or(abilities(MultiblockAbility.EXPORT_ITEMS).setPreviewCount(1));
            }
        }
        if (checkFluidIn) {
            if (recipeMap.getMaxFluidInputs() > 0) {
                predicate = predicate.or(abilities(MultiblockAbility.IMPORT_FLUIDS).setPreviewCount(1));
            }
        }
        if (checkFluidOut) {
            if (recipeMap.getMaxFluidOutputs() > 0) {
                predicate = predicate.or(abilities(MultiblockAbility.EXPORT_FLUIDS).setPreviewCount(1));
            }
        }
        return predicate;
    }

    @Override
    public List<ITextComponent> getDataInfo() {
        List<ITextComponent> list = new ArrayList<>();
        if (recipeMapWorkable.getMaxProgress() > 0) {
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
