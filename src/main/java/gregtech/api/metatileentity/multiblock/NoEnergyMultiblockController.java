package gregtech.api.metatileentity.multiblock;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.NoEnergyMultiblockRecipeLogic;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.ConfigHolder;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.items.IItemHandler;

import gtqt.api.util.GTQTUtility;

import java.util.ArrayList;
import java.util.List;

public abstract class NoEnergyMultiblockController extends RecipeMapMultiblockController{

    public NoEnergyMultiblockController(ResourceLocation metaTileEntityId,
                                        RecipeMap<?> recipeMap) {
        super(metaTileEntityId,recipeMap);
        this.recipeMapWorkable = new NoEnergyMultiblockRecipeLogic(this);
    }

    protected void initializeAbilities() {
        List<IItemHandler> inputItems = new ArrayList<>(this.getAbilities(MultiblockAbility.IMPORT_ITEMS));
        inputItems.addAll(getAbilities(MultiblockAbility.DUAL_IMPORT));
        inputItems.addAll(getAbilities(MultiblockAbility.COMPLEX_DUAL));
        this.inputInventory = new ItemHandlerList(inputItems);

        List<IMultipleTankHandler> inputFluids = new ArrayList<>(getAbilities(MultiblockAbility.DUAL_IMPORT));
        inputFluids.add(new FluidTankList(true, getAbilities(MultiblockAbility.IMPORT_FLUIDS)));
        inputFluids.addAll(getAbilities(MultiblockAbility.COMPLEX_DUAL));
        this.inputFluidInventory = GTQTUtility.mergeTankHandlers(inputFluids, true);

        List<IItemHandler> outputItems = new ArrayList<>(this.getAbilities(MultiblockAbility.EXPORT_ITEMS));
        outputItems.addAll(getAbilities(MultiblockAbility.DUAL_EXPORT));
        outputItems.addAll(getAbilities(MultiblockAbility.COMPLEX_DUAL));
        this.outputInventory = new ItemHandlerList(outputItems);

        List<IMultipleTankHandler> outputFluids = new ArrayList<>(getAbilities(MultiblockAbility.DUAL_EXPORT));
        outputFluids.add(new FluidTankList(false, getAbilities(MultiblockAbility.EXPORT_FLUIDS)));
        outputFluids.addAll(getAbilities(MultiblockAbility.COMPLEX_DUAL));
        this.outputFluidInventory = GTQTUtility.mergeTankHandlers(outputFluids, false);
    }

    @Override
    protected void updateFormedValid() {
        if (!hasMufflerMechanics() || isMufflerReady()) {
            this.recipeMapWorkable.updateWorkable();
        }
    }

    public TraceabilityPredicate autoAbilities(
            boolean checkMaintenance,
                                               boolean checkItemIn,
                                               boolean checkItemOut,
                                               boolean checkFluidIn,
                                               boolean checkFluidOut,
                                               boolean checkMuffler) {
        TraceabilityPredicate predicate = super.autoAbilities(checkMaintenance, checkMuffler);

        if (checkItemIn || checkFluidIn) {
            if (recipeMap.getMaxInputs() > 0 || recipeMap.getMaxFluidInputs() > 0) {
                predicate = predicate.or(abilities(MultiblockAbility.DUAL_IMPORT).setPreviewCount(1));
            }
        }
        if (checkItemOut || checkFluidOut) {
            if (recipeMap.getMaxOutputs() > 0 || recipeMap.getMaxFluidOutputs() > 0) {
                predicate = predicate.or(abilities(MultiblockAbility.DUAL_EXPORT).setPreviewCount(1));
            }
        }

        predicate = predicate.or(abilities(MultiblockAbility.COMPLEX_DUAL).setPreviewCount(1));

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
}
