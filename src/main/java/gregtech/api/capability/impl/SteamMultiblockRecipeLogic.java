package gregtech.api.capability.impl;

import gregtech.api.GTValues;
import gregtech.api.capability.IMultiblockController;
import gregtech.api.capability.IMultipleNotifiableHandler;
import gregtech.api.capability.IMultipleRecipeMaps;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.capability.IDistinctBusController;
import gregtech.api.metatileentity.multiblock.RecipeMapSteamMultiblockController;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.GTUtility;
import gregtech.common.ConfigHolder;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import gtqt.api.util.GTQTUtility;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class SteamMultiblockRecipeLogic extends AbstractRecipeLogic {

    protected final Set<IItemHandlerModifiable> invalidatedInputList = new HashSet<>();
    // EU per mB
    private final double conversionRate;
    // Used for distinct mode
    protected int lastRecipeIndex = 0;
    protected IItemHandlerModifiable currentDistinctInputBus;
    private IMultipleTankHandler steamFluidTank;
    private IFluidTank steamFluidTankCombined;
    private boolean hasDualInputCache;

    public SteamMultiblockRecipeLogic(RecipeMapSteamMultiblockController tileEntity, RecipeMap<?> recipeMap,
                                      IMultipleTankHandler steamFluidTank, double conversionRate) {
        super(tileEntity, recipeMap);
        this.steamFluidTank = steamFluidTank;
        this.conversionRate = conversionRate;
        setAllowOverclocking(false);
        combineSteamTanks();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        lastRecipeIndex = 0;
        invalidatedInputList.clear();
    }

    @Override
    protected boolean canWorkWithInputs() {
        MultiblockWithDisplayBase controller = (MultiblockWithDisplayBase) metaTileEntity;
        if (controller instanceof IDistinctBusController distinctController) {

            if (distinctController.canBeDistinct() && distinctController.isDistinct() &&
                    getInputInventory().getSlots() > 0) {
                boolean canWork = false;
                if (invalidatedInputList.isEmpty()) {
                    return true;
                }
                if (!metaTileEntity.getNotifiedFluidInputList().isEmpty()) {
                    canWork = true;
                    invalidatedInputList.clear();
                    metaTileEntity.getNotifiedFluidInputList().clear();
                    metaTileEntity.getNotifiedItemInputList().clear();
                } else {
                    Iterator<IItemHandlerModifiable> notifiedIter = metaTileEntity.getNotifiedItemInputList()
                            .iterator();
                    while (notifiedIter.hasNext()) {
                        IItemHandlerModifiable bus = notifiedIter.next();
                        Iterator<IItemHandlerModifiable> invalidatedIter = invalidatedInputList.iterator();
                        while (invalidatedIter.hasNext()) {
                            IItemHandler invalidatedHandler = invalidatedIter.next();
                            if (invalidatedHandler instanceof IMultipleNotifiableHandler multipleNotifiableHandler) {
                                for (var notifiableHandler : multipleNotifiableHandler.getBackingNotifiers()) {
                                    if (notifiableHandler == bus) {
                                        canWork = true;
                                        invalidatedIter.remove();
                                        break;
                                    }
                                }
                            } else if (invalidatedHandler == bus) {
                                canWork = true;
                                invalidatedIter.remove();
                            }
                        }
                        notifiedIter.remove();
                    }
                }
                ArrayList<IItemHandler> flattenedHandlers = new ArrayList<>();
                for (IItemHandler ih : getInputBuses()) {
                    if (ih instanceof ItemHandlerList) {
                        flattenedHandlers.addAll(((ItemHandlerList) ih).getBackingHandlers());
                    }
                    flattenedHandlers.add(ih);
                }

                if (!invalidatedInputList.containsAll(flattenedHandlers)) {
                    canWork = true;
                }
                return canWork;
            }
        }
        return super.canWorkWithInputs();
    }

    @Override
    protected void trySearchNewRecipe() {
        // do not run recipes when there are more than 5 maintenance problems
        // Maintenance can apply to all multiblocks, so cast to a base multiblock class
        MultiblockWithDisplayBase controller = (MultiblockWithDisplayBase) metaTileEntity;
        if (ConfigHolder.machines.enableMaintenance && controller.hasMaintenanceMechanics() &&
                controller.getNumMaintenanceProblems() > 5) {
            return;
        }

        // Distinct buses only apply to some multiblocks, so check the controller against the interface
        if (controller instanceof IDistinctBusController distinctController) {

            if (distinctController.canBeDistinct() && distinctController.isDistinct() &&
                    getInputInventory().getSlots() > 0) {
                trySearchNewRecipeDistinct();
                return;
            }
        }

        trySearchNewRecipeCombined();
    }

    /**
     * Put into place so multiblocks can override {@link AbstractRecipeLogic#trySearchNewRecipe()} without having to
     * deal with the maintenance and distinct logic in {@link MultiblockRecipeLogic#trySearchNewRecipe()}
     */
    protected void trySearchNewRecipeCombined() {
        super.trySearchNewRecipe();
    }

    protected void trySearchNewRecipeDistinct() {
        long maxVoltage = getMaxVoltage();
        Recipe currentRecipe;
        List<IItemHandlerModifiable> importInventory = getInputBuses();

        // Our caching implementation
        // This guarantees that if we get a recipe cache hit, our efficiency is no different from other machines
        if (checkPreviousRecipeDistinct(importInventory.get(lastRecipeIndex)) && checkRecipe(previousRecipe)) {
            currentRecipe = previousRecipe;
            currentDistinctInputBus = importInventory.get(lastRecipeIndex);
            if (prepareRecipeDistinct(currentRecipe)) {
                // No need to cache the previous recipe here, as it is not null and matched by the current recipe,
                // so it will always be the same
                return;
            }
        }

        // On a cache miss, our efficiency is much worse, as it will check
        // each bus individually instead of the combined inventory all at once.
        for (int i = 0; i < importInventory.size(); i++) {
            IItemHandlerModifiable bus = importInventory.get(i);
            // Skip this bus if no recipe was found last time
            if (invalidatedInputList.contains(bus)) {
                continue;
            }
            // Look for a new recipe after a cache miss
            currentRecipe = findRecipe(maxVoltage, bus, getInputTank(bus));
            // Cache the current recipe, if one is found
            if (currentRecipe != null && checkRecipe(currentRecipe)) {
                this.previousRecipe = currentRecipe;
                currentDistinctInputBus = bus;
                if (prepareRecipeDistinct(currentRecipe)) {
                    lastRecipeIndex = i;
                    return;
                }
            }
            if (currentRecipe == null) {
                // no valid recipe found, invalidate this bus
                invalidatedInputList.add(bus);
            }
        }
    }

    @Override
    public void invalidateInputs() {
        MultiblockWithDisplayBase controller = (MultiblockWithDisplayBase) metaTileEntity;
        if (controller instanceof IDistinctBusController distinctController &&
                distinctController.canBeDistinct() && distinctController.isDistinct() &&
                getInputInventory().getSlots() > 0) {
            invalidatedInputList.add(currentDistinctInputBus);
        } else {
            super.invalidateInputs();
        }
    }

    protected boolean checkPreviousRecipeDistinct(IItemHandlerModifiable previousBus) {
        return previousRecipe != null && previousRecipe.matches(false, previousBus, getInputTank(previousBus));
    }

    protected boolean prepareRecipeDistinct(Recipe recipe) {
        recipe = Recipe.trimRecipeOutputs(recipe, getRecipeMap(), metaTileEntity.getItemOutputLimit(),
                metaTileEntity.getFluidOutputLimit());

        recipe = findParallelRecipe(
                recipe,
                currentDistinctInputBus,
                getInputTank(currentDistinctInputBus),
                getOutputInventory(),
                getOutputTank(),
                getMaxParallelVoltage(),
                getParallelLimit());

        if (recipe != null) {
            recipe = setupAndConsumeRecipeInputs(recipe, currentDistinctInputBus,
                    getInputTank(currentDistinctInputBus));
            if (recipe != null) {
                setupRecipe(recipe);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean checkRecipe(@NotNull Recipe recipe) {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        if (controller.checkRecipe(recipe, false)) {
            controller.checkRecipe(recipe, true);
            return super.checkRecipe(recipe);
        }
        return false;
    }

    @Nullable
    @Override
    public RecipeMap<?> getRecipeMap() {
        // if the multiblock has more than one RecipeMap, return the currently selected one
        if (metaTileEntity instanceof IMultipleRecipeMaps)
            return ((IMultipleRecipeMaps) metaTileEntity).getCurrentRecipeMap();
        return super.getRecipeMap();
    }

    private void clearNotificationLists() {
        invalidatedInputList.clear();
        metaTileEntity.getNotifiedFluidInputList().clear();
        metaTileEntity.getNotifiedItemInputList().clear();
    }

    protected List<IItemHandlerModifiable> getInputBuses() {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        return new ArrayList<>(
                controller.getAbilities(MultiblockAbility.IMPORT_ITEMS));
    }

    @Override
    protected boolean canProgressRecipe() {
        return super.canProgressRecipe() && !((IMultiblockController) metaTileEntity).isStructureObstructed();
    }

    public void onDistinctChanged() {
        this.lastRecipeIndex = 0;
    }

    public IFluidTank getSteamFluidTankCombined() {
        combineSteamTanks();
        return steamFluidTankCombined;
    }

    /**
     * 你确定这里不是屎山吗？
     * @return
     */
    @Override
    protected IMultipleTankHandler getInputTank() {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        //检查总成，如果有合并流体
        List<IItemHandlerModifiable> itemHandlers = controller.getAbilities(MultiblockAbility.IMPORT_ITEMS);
        List<IMultipleTankHandler> inputFluids = new ArrayList<>();
        boolean allowMerge = controller.getInputFluidInventory().allowSameFluidFill();
        inputFluids.add(controller.getInputFluidInventory());
        // 遍历所有物品总线，检查是否是 DualHandler
        for (IItemHandlerModifiable bus : itemHandlers) {
            if (bus instanceof IMultipleTankHandler dualHandler) {
                // 将 DualHandler 的流体槽添加到总列表中
                inputFluids.add(dualHandler);
            }
        }
        return GTQTUtility.mergeTankHandlers(inputFluids, allowMerge);
    }

    protected IMultipleTankHandler getInputTank(IItemHandler items) {
        var tanks = new ArrayList<>(getInputTank().getFluidTanks());
        if (items instanceof IMultipleTankHandler tankHandler) {
            tanks.addAll(tankHandler.getFluidTanks());
        }
        return new FluidTankList(getInputTank().allowSameFluidFill(), tanks);
    }

    @Override
    protected IMultipleTankHandler getOutputTank() {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        //检查总成，如果有合并流体
        List<IItemHandlerModifiable> itemHandlers = controller.getAbilities(MultiblockAbility.EXPORT_ITEMS);
        List<IMultipleTankHandler> outputFluids = new ArrayList<>();
        boolean allowMerge = controller.getOutputFluidInventory().allowSameFluidFill();
        outputFluids.add(controller.getInputFluidInventory());
        // 遍历所有物品总线，检查是否是 DualHandler
        for (IItemHandlerModifiable bus : itemHandlers) {
            if (bus instanceof IMultipleTankHandler dualHandler) {
                // 将 DualHandler 的流体槽添加到总列表中
                outputFluids.add(dualHandler);
            }
        }
        return GTQTUtility.mergeTankHandlers(outputFluids, allowMerge);
    }

    @Override
    protected IItemHandlerModifiable getInputInventory() {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        return controller.getInputInventory();
    }

    @Override
    protected IItemHandlerModifiable getOutputInventory() {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        return controller.getOutputInventory();
    }

    protected IMultipleTankHandler getSteamFluidTank() {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        return controller.getSteamFluidTank();
    }

    private void combineSteamTanks() {
        steamFluidTank = getSteamFluidTank();
        if (steamFluidTank == null)
            steamFluidTankCombined = new FluidTank(0);
        else if (steamFluidTank.getTanks() == 0) {
            int capacity = steamFluidTank.getTanks() * 64000;
            steamFluidTankCombined = new FluidTank(capacity);
            steamFluidTankCombined.fill(steamFluidTank.drain(capacity, false), true);
        } else if (steamFluidTank.getTanks() == 1) {
            IMultipleTankHandler.ITankEntry tankHatch = steamFluidTank.getTankAt(0);
            int capacity = tankHatch.getCapacity();
            steamFluidTankCombined = new FluidTank(capacity);
            steamFluidTankCombined.fill(steamFluidTank.drain(capacity, false), true);
        }
    }

    protected IMultipleTankHandler getDistinctInputTank(IItemHandler items) {
        var tanks = new ArrayList<>(getInputTank().getFluidTanks());
        tanks.clear();
        if (items instanceof IMultipleTankHandler tankHandler) {
            tanks.addAll(tankHandler.getFluidTanks());
        }
        return new FluidTankList(getInputTank().allowSameFluidFill(), tanks);
    }

    @Override
    public void update() {
        // Fixes an annoying GTCE bug in AbstractRecipeLogic
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        if (isActive && !controller.isStructureFormed()) {
            progressTime = 0;
            wasActiveAndNeedsUpdate = true;
        }

        combineSteamTanks();
        super.update();
    }

    @Override
    protected long getEnergyInputPerSecond() {
        return 0;
    }

    @Override
    protected long getEnergyStored() {
        combineSteamTanks();
        return (long) Math.ceil(steamFluidTankCombined.getFluidAmount() * conversionRate);
    }

    @Override
    protected long getEnergyCapacity() {
        combineSteamTanks();
        return (long) Math.floor(steamFluidTankCombined.getCapacity() * conversionRate);
    }

    @Override
    protected boolean drawEnergy(long recipeEUt, boolean simulate) {
        combineSteamTanks();
        int resultDraw = GTUtility.safeCastLongToInt((long) Math.ceil(recipeEUt / conversionRate));
        return resultDraw >= 0 && steamFluidTankCombined.getFluidAmount() >= resultDraw &&
                steamFluidTank.drain(resultDraw, !simulate) != null;
    }

    @Override
    public long getMaxVoltage() {
        return GTValues.V[GTValues.LV];
    }

    @Override
    public boolean isAllowOverclocking() {
        return false;
    }

    @Override
    protected @Nullable Recipe setupAndConsumeRecipeInputs(@NotNull Recipe recipe,
                                                           @NotNull IItemHandlerModifiable importInventory) {
        RecipeMapSteamMultiblockController controller = (RecipeMapSteamMultiblockController) metaTileEntity;
        if (controller.checkRecipe(recipe, false)) {
            recipe = super.setupAndConsumeRecipeInputs(recipe, importInventory);
            if (recipe != null) {
                controller.checkRecipe(recipe, true);
                return recipe;
            }
        }
        return null;
    }

    @Override
    protected void completeRecipe() {
        super.completeRecipe();
        ventSteam();
    }

    private void ventSteam() {
        BlockPos machinePos = metaTileEntity.getPos();
        EnumFacing ventingSide = metaTileEntity.getFrontFacing();
        BlockPos ventingBlockPos = machinePos.offset(ventingSide);
        IBlockState blockOnPos = metaTileEntity.getWorld().getBlockState(ventingBlockPos);
        if (blockOnPos.getCollisionBoundingBox(metaTileEntity.getWorld(), ventingBlockPos) == Block.NULL_AABB) {
            performVentingAnimation(machinePos, ventingSide);
        } else if (blockOnPos.getBlock() == Blocks.SNOW_LAYER && blockOnPos.getValue(BlockSnow.LAYERS) == 1) {
            performVentingAnimation(machinePos, ventingSide);
            metaTileEntity.getWorld().destroyBlock(ventingBlockPos, false);
        }
    }

    private void performVentingAnimation(BlockPos machinePos, EnumFacing ventingSide) {
        WorldServer world = (WorldServer) metaTileEntity.getWorld();
        double posX = machinePos.getX() + 0.5 + ventingSide.getXOffset() * 0.6;
        double posY = machinePos.getY() + 0.5 + ventingSide.getYOffset() * 0.6;
        double posZ = machinePos.getZ() + 0.5 + ventingSide.getZOffset() * 0.6;

        world.spawnParticle(EnumParticleTypes.CLOUD, posX, posY, posZ,
                7 + GTValues.RNG.nextInt(3),
                ventingSide.getXOffset() / 2.0,
                ventingSide.getYOffset() / 2.0,
                ventingSide.getZOffset() / 2.0, 0.1);
        if (ConfigHolder.machines.machineSounds && !metaTileEntity.isMuffled()) {
            world.playSound(null, posX, posY, posZ, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 1.0f,
                    1.0f);
        }
    }

    @Override
    protected boolean hasEnoughPower(long eut, int duration) {
        long totalSteam = (long) (eut * duration / conversionRate);
        if (totalSteam > 0) {
            long steamStored = getEnergyStored();
            long steamCapacity = getEnergyCapacity();
            // if the required steam is larger than the full buffer, just require the full buffer
            if (steamCapacity < totalSteam) {
                return steamCapacity == steamStored;
            }
            // otherwise require the full amount of steam for the recipe
            return steamStored >= totalSteam;
        }
        // generation case unchanged
        return super.hasEnoughPower(eut, duration);
    }
}
