package gregtech.api.metatileentity.multiblock;

import gregtech.api.GTValues;
import gregtech.api.capability.IBatch;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IDistinctBusController;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.IRecipeControl;
import gregtech.api.capability.IRecipeMapHolder;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.metatileentity.IDataInfoProvider;
import gregtech.api.metatileentity.interfaces.IRefreshBeforeConsumption;
import gregtech.api.metatileentity.multiblock.ui.KeyManager;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.UISyncer;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.CrossRecipeParallelScheduler;
import gregtech.api.recipes.logic.RecipeSlot;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextFormattingUtil;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.common.ConfigHolder;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class RecipeMapMultiblockController extends MultiblockWithDisplayBase implements IDataInfoProvider,
                                                                                                 ICleanroomReceiver,
                                                                                                 IDistinctBusController,
                                                                                                 IControllable, IBatch,
                                                                                                 IRecipeControl,
                                                                                                 IRecipeMapHolder {

    public final RecipeMap<?> recipeMap;
    protected final RecipeAbilityManager abilityManager;
    protected MultiblockRecipeLogic recipeMapWorkable;
    protected IItemHandlerModifiable inputInventory;
    protected IItemHandlerModifiable outputInventory;
    protected IMultipleTankHandler inputFluidInventory;
    protected IMultipleTankHandler outputFluidInventory;
    protected IEnergyContainer energyContainer;
    protected List<IRefreshBeforeConsumption> refreshBeforeConsumptions;

    private boolean isDistinct = false;

    @Nullable
    private ICleanroomProvider cleanroom;

    public RecipeMapMultiblockController(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap) {
        super(metaTileEntityId);
        this.recipeMap = recipeMap;
        this.abilityManager = new RecipeAbilityManager(this);
        this.recipeMapWorkable = new MultiblockRecipeLogic(this);
        this.refreshBeforeConsumptions = new ArrayList<>();
        resetTileAbilities();
    }

    /**
     * Appends cross-recipe parallel slot details to the Tricorder data info. Slots with the same recipe name and
     * duration are merged into a single entry. Shows up to 2 merged entries, with "..." if more exist.
     */
    protected static void addCrossRecipeTricorderInfo(@NotNull List<ITextComponent> list,
                                                      @NotNull CrossRecipeParallelScheduler scheduler) {
        List<CrossRecipeParallelScheduler.MergedSlotDisplay> mergedSlots = scheduler.getMergedDisplaySlots();
        if (mergedSlots.isEmpty()) return;

        int displayed = 0;
        for (CrossRecipeParallelScheduler.MergedSlotDisplay merged : mergedSlots) {
            if (displayed >= 2) {
                list.add(new TextComponentTranslation("behavior.tricorder.cross_recipe.more")
                        .setStyle(new Style().setColor(TextFormatting.GRAY)));
                break;
            }

            String slotLabel = "#" + (merged.slotIndex + 1);
            if (!merged.recipeName.isEmpty()) {
                slotLabel += " " + merged.recipeName;
                if (merged.totalParallelCount > 1) {
                    slotLabel += " x" + merged.totalParallelCount;
                }
            }

            list.add(new TextComponentTranslation("behavior.tricorder.cross_recipe.slot",
                    new TextComponentTranslation(slotLabel)
                            .setStyle(new Style().setColor(TextFormatting.YELLOW)),
                    new TextComponentTranslation(
                            TextFormattingUtil.formatNumbers(merged.progress / 20))
                            .setStyle(new Style().setColor(TextFormatting.GREEN)),
                    new TextComponentTranslation(
                            TextFormattingUtil.formatNumbers(merged.maxProgress / 20))
                            .setStyle(new Style().setColor(TextFormatting.YELLOW))));
            displayed++;
        }
    }

    @Override
    protected boolean isWorkingForStructureCheck() {
        return recipeMapWorkable != null && recipeMapWorkable.isActive();
    }

    @Override
    public void refreshAllBeforeConsumption() {
        for (IRefreshBeforeConsumption refresh : refreshBeforeConsumptions) {
            refresh.refreshBeforeConsumption();
        }
    }

    @Override
    public IEnergyContainer getEnergyContainer() {
        return energyContainer;
    }

    @Override
    public @NotNull IItemHandlerModifiable getInputInventory() {
        return inputInventory;
    }

    @Override
    public @NotNull IItemHandlerModifiable getOutputInventory() {
        return outputInventory;
    }

    @Override
    public @NotNull IMultipleTankHandler getInputFluidInventory() {
        return inputFluidInventory;
    }

    @Override
    public @NotNull IMultipleTankHandler getOutputFluidInventory() {
        return outputFluidInventory;
    }

    @Override
    public @NotNull MultiblockRecipeLogic getRecipeMapWorkable() {
        return recipeMapWorkable;
    }

    /**
     * Performs extra checks for validity of given recipe before multiblock will start it's processing.
     */
    @Override
    public boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess) {
        return true;
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formRecipeMapStructure(formed);
    }

    protected final void formRecipeMapStructure(@NotNull FormedStructureView formed) {
        formStructureWithDisplay(formed);
        initializeAbilities();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        resetTileAbilities();
        this.recipeMapWorkable.invalidate();
    }

    @Override
    protected void updateFormedValid() {
        if (!hasMufflerMechanics() || isMufflerReady()) {
            this.recipeMapWorkable.updateWorkable();
        }
    }

    @Override
    public boolean isActive() {
        return isStructureFormed() && recipeMapWorkable.isActive() && recipeMapWorkable.isWorkingEnabled();
    }

    protected void initializeAbilities() {
        abilityManager.initialize(allowSameFluidFillForOutputs());
        syncFromAbilityManager();
        notifyRecipeAbilityRefresh();
    }

    private void resetTileAbilities() {
        abilityManager.reset();
        syncFromAbilityManager();
    }

    /**
     * Synchronizes protected fields from the ability manager for subclasses that read fields directly.
     */
    private void syncFromAbilityManager() {
        this.inputInventory = abilityManager.getInputInventory();
        this.outputInventory = abilityManager.getOutputInventory();
        this.inputFluidInventory = abilityManager.getInputFluidInventory();
        this.outputFluidInventory = abilityManager.getOutputFluidInventory();
        this.energyContainer = abilityManager.getEnergyContainer();
        this.refreshBeforeConsumptions = abilityManager.getRefreshBeforeConsumptions();
    }

    private void notifyRecipeAbilityRefresh() {
        addNotifiedInput(this.inputInventory);
        addNotifiedInput(this.inputFluidInventory);
        addNotifiedOutput(this.outputInventory);
        addNotifiedOutput(this.outputFluidInventory);
    }

    public boolean allowSameFluidFillForOutputs() {
        return true;
    }

    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(recipeMapWorkable.isWorkingEnabled(), recipeMapWorkable.isActive())
                .addEnergyUsageLine(this.getEnergyContainer())
                .addEnergyTierLine(GTUtility.getTierByVoltage(recipeMapWorkable.getMaxVoltage()))
                .addCustom(this::addCustomCapacity)
                .addParallelsLine(recipeMapWorkable.getParallelLimit())
                .addWorkingStatusLine();

        // Cross-recipe parallel display (synced via builder to prevent client/server buffer desync)
        builder.addCrossRecipeOrProgressDisplay(recipeMapWorkable);
    }

    /**
     * @deprecated Use {@link MultiblockUIBuilder#addCrossRecipeOrProgressDisplay(MultiblockRecipeLogic)} instead. This
     * method does not sync branch conditions via the builder's syncer, causing client/server buffer desynchronization.
     */
    @Deprecated
    protected void addCrossRecipeDisplay(MultiblockUIBuilder builder, MultiblockRecipeLogic logic) {
        CrossRecipeParallelScheduler scheduler = logic.getCrossRecipeScheduler();
        if (scheduler == null) return;

        builder.addCrossRecipeParallelLine(
                scheduler.getTotalParallelCount(),
                scheduler.getParallelLimit(),
                scheduler.getTotalEnergyConsumption());

        List<RecipeSlot> slots = scheduler.getActiveSlots();
        int displayLimit = Math.min(slots.size(), 8);
        for (int i = 0; i < displayLimit; i++) {
            RecipeSlot slot = slots.get(i);
            if (slot.isRunning()) {
                builder.addCrossRecipeSlotLine(slot.getSlotIndex(),
                        slot.getRecipeDisplayName(),
                        slot.getParallelCount(),
                        slot.getProgressTime(),
                        slot.getMaxProgressTime(),
                        slot.getRecipeEUt());
            }
        }
    }

    protected void addCustomCapacity(KeyManager keyManager, UISyncer syncer) {

    }

    protected void configureWarningText(MultiblockUIBuilder builder) {
        builder.addLowPowerLine(recipeMapWorkable.isHasNotEnoughEnergy());
        super.configureWarningText(builder);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        this.getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(),
                recipeMapWorkable.isActive(), recipeMapWorkable.isWorkingEnabled());
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("isDistinct", isDistinct);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        isDistinct = data.getBoolean("isDistinct");
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(isDistinct);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        isDistinct = buf.readBoolean();
    }

    @Override
    public boolean canBeDistinct() {
        return false;
    }

    @Override
    public boolean isDistinct() {
        return isDistinct;
    }

    @Override
    public void setDistinct(boolean isDistinct) {
        boolean changed = this.isDistinct != isDistinct;
        this.isDistinct = isDistinct;
        recipeMapWorkable.onDistinctChanged();
        getMultiblockParts().forEach(part -> part.onDistinctChange(isDistinct));
        // mark buses as changed on distinct toggle
        if (this.isDistinct) {
            this.notifiedItemInputList
                    .addAll(this.getAbilities(MultiblockAbility.IMPORT_ITEMS));
        } else {
            this.notifiedItemInputList.add(this.inputInventory);
        }
        if (changed) {
            notifyStructureConfigChanged();
        }
    }

    @Override
    public SoundEvent getSound() {
        return recipeMap.getSound();
    }

    @NotNull
    @Override
    public List<ITextComponent> getDataInfo() {
        List<ITextComponent> list = new ArrayList<>();

        // Cross-recipe parallel: show slot details instead of single progress
        if (recipeMapWorkable.isCrossRecipeMode() && recipeMapWorkable.getCrossRecipeScheduler() != null) {
            addCrossRecipeTricorderInfo(list, recipeMapWorkable.getCrossRecipeScheduler());
        } else if (recipeMapWorkable.getMaxProgress() > 0) {
            list.add(new TextComponentTranslation("behavior.tricorder.workable_progress",
                    new TextComponentTranslation(TextFormattingUtil.formatNumbers(recipeMapWorkable.getProgress() / 20))
                            .setStyle(new Style().setColor(TextFormatting.GREEN)),
                    new TextComponentTranslation(
                            TextFormattingUtil.formatNumbers(recipeMapWorkable.getMaxProgress() / 20))
                            .setStyle(new Style().setColor(TextFormatting.YELLOW))));
        }

        list.add(new TextComponentTranslation("behavior.tricorder.energy_container_storage",
                new TextComponentTranslation(TextFormattingUtil.formatNumbers(energyContainer.getEnergyStored()))
                        .setStyle(new Style().setColor(TextFormatting.GREEN)),
                new TextComponentTranslation(TextFormattingUtil.formatNumbers(energyContainer.getEnergyCapacity()))
                        .setStyle(new Style().setColor(TextFormatting.YELLOW))));

        if (recipeMapWorkable.getRecipeEUt() > 0) {
            list.add(new TextComponentTranslation("behavior.tricorder.workable_consumption",
                    new TextComponentTranslation(TextFormattingUtil.formatNumbers(recipeMapWorkable.getRecipeEUt()))
                            .setStyle(new Style().setColor(TextFormatting.RED)),
                    new TextComponentTranslation(
                            TextFormattingUtil.formatNumbers(recipeMapWorkable.getRecipeEUt() == 0 ? 0 : 1))
                            .setStyle(new Style().setColor(TextFormatting.RED))));
        }

        list.add(new TextComponentTranslation("behavior.tricorder.multiblock_energy_input",
                new TextComponentTranslation(TextFormattingUtil.formatNumbers(energyContainer.getInputVoltage()))
                        .setStyle(new Style().setColor(TextFormatting.YELLOW)),
                new TextComponentTranslation(GTValues.VN[GTUtility.getTierByVoltage(energyContainer.getInputVoltage())])
                        .setStyle(new Style().setColor(TextFormatting.YELLOW))));

        if (ConfigHolder.machines.enableMaintenance && hasMaintenanceMechanics()) {
            list.add(new TextComponentTranslation("behavior.tricorder.multiblock_maintenance",
                    new TextComponentTranslation(TextFormattingUtil.formatNumbers(getNumMaintenanceProblems()))
                            .setStyle(new Style().setColor(TextFormatting.RED))));
        }

        if (recipeMapWorkable.getParallelLimit() > 1) {
            list.add(new TextComponentTranslation("behavior.tricorder.multiblock_parallel",
                    new TextComponentTranslation(TextFormattingUtil.formatNumbers(recipeMapWorkable.getParallelLimit()))
                            .setStyle(new Style().setColor(TextFormatting.GREEN))));
        }

        return list;
    }

    @Nullable
    @Override
    public ICleanroomProvider getCleanroom() {
        return this.cleanroom;
    }

    @Override
    public void setCleanroom(@NotNull ICleanroomProvider provider) {
        if (cleanroom == null || provider.getPriority() > cleanroom.getPriority()) {
            this.cleanroom = provider;
        }
    }

    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        TooltipBuilder.create().addBatch().build(this, tooltip);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public String recipeMapsToString() {
        if (recipeMap == null) return "";
        return recipeMap.getLocalizedName();
    }

    @Override
    public void unsetCleanroom() {
        this.cleanroom = null;
    }

    @Override
    public boolean isWorkingEnabled() {
        return recipeMapWorkable.isWorkingEnabled();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        boolean changed = recipeMapWorkable.isWorkingEnabled() != isWorkingAllowed;
        recipeMapWorkable.setWorkingEnabled(isWorkingAllowed);
        if (changed) {
            notifyStructureControllerModeChanged();
        }
    }

    @Override
    public boolean isBatchAllowed() {
        return false;
    }

    @Override
    public boolean isBatchEnable() {
        return recipeMapWorkable.isBatchEnable();
    }

    @Override
    public void setBatchEnable(boolean enable) {
        boolean changed = recipeMapWorkable.isBatchEnable() != enable;
        recipeMapWorkable.setBatchEnable(enable);
        if (changed) {
            notifyStructureConfigChanged();
        }
    }

    @Override
    public boolean enableExtendControl() {
        return true;
    }

    @Override
    public boolean isRecipeLocked() {
        return recipeMapWorkable.isRecipeLockEnable();
    }

    @Override
    public void setRecipeLocked(boolean enable) {
        boolean changed = recipeMapWorkable.isRecipeLockEnable() != enable;
        recipeMapWorkable.setRecipeLockEnable(enable);
        if (changed) {
            notifyStructureConfigChanged();
        }
    }

    @Override
    public boolean isEnergyLackWarningEnabled() {
        return recipeMapWorkable.isEnergyLackWarningEnable();
    }

    @Override
    public void setEnergyLackWarningEnabled(boolean enable) {
        boolean changed = recipeMapWorkable.isEnergyLackWarningEnable() != enable;
        recipeMapWorkable.setEnergyLackWarningEnable(enable);
        if (changed) {
            notifyStructureConfigChanged();
        }
    }

    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    protected Object getStructureConfigDependencyValue() {
        Map<String, Object> values = new LinkedHashMap<>(
                (Map<String, Object>) super.getStructureConfigDependencyValue());
        values.put("distinct", isDistinct);
        if (recipeMapWorkable != null) {
            values.put("batchEnable", recipeMapWorkable.isBatchEnable());
            values.put("recipeLocked", recipeMapWorkable.isRecipeLockEnable());
            values.put("energyLackWarning", recipeMapWorkable.isEnergyLackWarningEnable());
        }
        return values;
    }

    @Override
    public boolean hasSideUI() {
        return true;
    }
}
