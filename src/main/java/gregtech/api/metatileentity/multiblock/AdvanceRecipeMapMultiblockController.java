package gregtech.api.metatileentity.multiblock;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.IDistinctBusController;
import gregtech.api.capability.IThreadController;
import gregtech.api.capability.impl.EnergyContainerList;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.IDataInfoProvider;
import gregtech.api.metatileentity.multiblock.ui.KeyManager;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.api.metatileentity.multiblock.ui.UISyncer;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.ConfigHolder;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class AdvanceRecipeMapMultiblockController extends RecipeMapMultiblockController
        implements IDataInfoProvider,
                   ICleanroomReceiver,
                   IDistinctBusController,
                   IThreadController {

    public final RecipeMap<?> recipeMap;
    protected ArrayList<MultiblockRecipeLogic> recipeMapWorkable;

    protected int thread = 1;

    private boolean isDistinct = true;

    @Nullable
    private ICleanroomProvider cleanroom;

    public AdvanceRecipeMapMultiblockController(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap) {
        super(metaTileEntityId, recipeMap);
        this.recipeMap = recipeMap;

        //随便初始化一个
        recipeMapWorkable = new ArrayList<>();
        recipeMapWorkable.add(new MultiblockRecipeLogic(this));
    }

    public ArrayList<MultiblockRecipeLogic> getRecipeMapWorkableList() {
        return recipeMapWorkable;
    }

    /**
     * Performs extra checks for validity of given recipe before multiblock will start it's processing.
     */
    public boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess) {
        return super.checkRecipe(recipe, consumeIfSuccess);
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formAdvancedRecipeMapStructure(formed);
    }

    protected final void formAdvancedRecipeMapStructure(@NotNull FormedStructureView formed) {
        formRecipeMapStructure(formed);
        refreshThread(getThread());
    }

    @Override
    public void refreshThread(int currentThread) {
        if (currentThread == 0) return;
        if (!isActive()) {
            // Every logic registers under the same trait name, so the last one is the instance that NBT and initial
            // sync data restore. Its configuration has to survive the rebuild below, otherwise batch mode and the
            // other button states are silently reset each time the structure forms.
            MultiblockRecipeLogic configured = recipeMapWorkable == null || recipeMapWorkable.isEmpty() ? null :
                    recipeMapWorkable.get(recipeMapWorkable.size() - 1);
            recipeMapWorkable = new ArrayList<>();
            for (int i = 0; i < currentThread; i++) {
                recipeMapWorkable.add(createThreadRecipeLogic(currentThread));
            }
            if (configured != null) {
                for (MultiblockRecipeLogic logic : recipeMapWorkable) {
                    logic.copyUserSettingsFrom(configured);
                }
            }
        }
    }

    /**
     * Creates one recipe logic instance for a thread refresh. Subclasses that need their own logic type override this
     * rather than {@link #refreshThread(int)}, so the rebuild keeps carrying the player-configured toggles over.
     *
     * @param threadCount the thread count the list is being rebuilt for
     * @return the logic instance to add to the list
     */
    protected MultiblockRecipeLogic createThreadRecipeLogic(int threadCount) {
        return new MultiblockRecipeLogic(this) {

            @Override
            public long getMaximumOverclockVoltage() {
                // In CROSS_RECIPE mode, the scheduler manages power distribution internally,
                // so each thread gets the full voltage budget.
                if (isCrossRecipeMode()) {
                    return super.getMaximumOverclockVoltage();
                }
                return super.getMaximumOverclockVoltage() / threadCount;
            }
        };
    }

    @Override
    protected MultiblockUIFactory createUIFactory() {
        return super.createUIFactory()
                .createThreadButton((guiData, syncManager) -> {
                    var throttlePanel = syncManager.syncedPanel("thread_panel", true, this::createThreadThrottlePanel);
                    // 配置按钮 - 打开线程调整UI
                    return new ButtonWidget<>()
                            .size(18)
                            .overlay(GTGuiTextures.OVERLAY_THREAD.asIcon().size(16))
                            .addTooltipLine(IKey.lang("设备线程调整"))
                            .onMousePressed(mouseButton -> {
                                if (throttlePanel.isPanelOpen()) {
                                    throttlePanel.closePanel();
                                } else {
                                    throttlePanel.openPanel();
                                }
                                return true;
                            });
                });
    }

    // 线程节流面板
    protected ModularPanel createThreadThrottlePanel(PanelSyncManager syncManager, IPanelHandler syncHandler) {
        IntSyncValue currentThreadValue = new IntSyncValue(this::getThread, this::setThread);
        syncManager.syncValue("currentThreadValue", currentThreadValue);

        IntSyncValue maxThreadValue = new IntSyncValue(
                this::getMaxThread,
                value -> {}
        );
        syncManager.syncValue("maxThreadValue", maxThreadValue);

        return GTGuis.createPopupPanel("thread_throttle", 200, 60)
                .child(Flow.row()
                        .pos(4, 4)
                        .height(16)
                        .coverChildrenWidth()
                        .child(new ItemDrawable(getStackForm())
                                .asWidget()
                                .size(16)
                                .marginRight(4))
                        .child(IKey.lang("机器线程设置")
                                .asWidget()
                                .heightRel(1.0f)))

                .child(Flow.row()
                        .top(24)
                        .height(20)
                        .child(new ButtonWidget<>()
                                .left(10).widthRel(0.4f)
                                .height(20)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang("减小线程数量")))
                                .onMousePressed(mouseButton -> {
                                    currentThreadValue.setValue(MathHelper.clamp(
                                            currentThreadValue.getValue() -
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)), 1,
                                            maxThreadValue.getValue()));
                                    return true;
                                })
                                .onUpdateListener(widget -> widget.overlay(GTUtility.createAdjustOverlay(false)))
                        )
                        .child(new ButtonWidget<>()
                                .left(110).widthRel(0.4f)
                                .height(20)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang("增大线程数量")))
                                .onMousePressed(mouseButton -> {
                                    currentThreadValue.setValue(MathHelper.clamp(
                                            currentThreadValue.getValue() +
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)), 1,
                                            maxThreadValue.getValue()));
                                    return true;
                                })
                                .onUpdateListener(widget -> widget.overlay(GTUtility.createAdjustOverlay(true))))
                );
    }

    @Override
    public int getThread() {
        thread = this.getAbilities(MultiblockAbility.THREAD_HATCH).isEmpty() ? 1 :
                this.getAbilities(MultiblockAbility.THREAD_HATCH).get(0).getCurrentThread();
        return thread;
    }

    @Override
    public void setThread(int thread) {
        int clampedThread = MathHelper.clamp(thread, 1, getMaxThread());
        int previousThread = getThread();
        markDirty();
        if (getWorld() != null && !getWorld().isRemote) {
            writeCustomData(GregtechDataCodes.UPDATE_THREAD_STATE, buf -> buf.writeInt(clampedThread));
        }
        if (!this.getAbilities(MultiblockAbility.THREAD_HATCH).isEmpty()) {
            this.getAbilities(MultiblockAbility.THREAD_HATCH).get(0).setCurrentThread(clampedThread);
        } else {
            this.thread = clampedThread;
        }
        if (previousThread != clampedThread) {
            notifyStructureConfigChanged();
        }
    }

    @Override
    public void receiveCustomData(int dataId, @NotNull PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.UPDATE_THREAD_STATE) {
            this.thread = buf.readInt();
        }
    }

    @Override
    public int getMaxThread() {
        return this.getAbilities(MultiblockAbility.THREAD_HATCH).isEmpty() ? 1 :
                this.getAbilities(MultiblockAbility.THREAD_HATCH).get(0).getMaxThread();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        resetTileAbilities();
        for (MultiblockRecipeLogic multiblockRecipeLogic : recipeMapWorkable)
            multiblockRecipeLogic.invalidate();
    }

    @Override
    protected void updateFormedValid() {
        if (!hasMufflerMechanics() || isMufflerReady()) {
            for (MultiblockRecipeLogic multiblockRecipeLogic : recipeMapWorkable)
                multiblockRecipeLogic.updateWorkable();
        }
    }

    public boolean checkActive() {
        for (MultiblockRecipeLogic multiblockRecipeLogic : recipeMapWorkable)
            if (multiblockRecipeLogic.isActive()) return true;
        return false;
    }

    public boolean checkWorkingEnable() {
        for (MultiblockRecipeLogic multiblockRecipeLogic : recipeMapWorkable)
            if (multiblockRecipeLogic.isActive()) return true;
        return false;
    }

    @Override
    public boolean isActive() {
        return isStructureFormed() &&
                checkActive() &&
                checkWorkingEnable();
    }

    private void resetTileAbilities() {
        this.inputInventory = new GTItemStackHandler(this, 0);
        this.inputFluidInventory = new FluidTankList(true);
        this.outputInventory = new GTItemStackHandler(this, 0);
        this.outputFluidInventory = new FluidTankList(true);
        this.energyContainer = new EnergyContainerList(Lists.newArrayList());
        this.refreshBeforeConsumptions.clear();
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        if (!isStructureFormed()) return;
        int syncsParallel = builder.syncsInteger(thread);
        if (syncsParallel == 1) {
            MultiblockRecipeLogic logic = recipeMapWorkable.get(0);
            builder.setWorkingStatus(logic.isWorkingEnabled(), logic.isActive())
                    .addEnergyUsageLine(this.getEnergyContainer())
                    .addEnergyTierLine(GTUtility.getTierByVoltage(logic.getMaxVoltage()))
                    .addParallelsLine(logic.getParallelLimit())
                    .addWorkingStatusLine();

            // Cross-recipe parallel display (synced via builder to prevent client/server buffer desync)
            builder.addCrossRecipeOrProgressDisplay(logic);

            builder.addCustom(this::addCustomCapacity);
        } else if (syncsParallel > 1) {
            builder.addEnergyUsageLine(this.getEnergyContainer())
                    .addEnergyTierLine(GTUtility.getTierByVoltage(recipeMapWorkable.get(0).getMaxVoltage()))
                    .addParallelsLine(recipeMapWorkable.get(0).getParallelLimit())
                    .addCustom(this::addCustomCapacity)
                    .addCustom((list, syncer) -> {
                        list.add(KeyUtil.lang(TextFormatting.GOLD, "总线程数：%s",
                                syncsParallel));
                    });

            for (int i = 0; i < Math.min(syncsParallel, recipeMapWorkable.size()); i++) {
                MultiblockRecipeLogic logic = recipeMapWorkable.get(i);
                final int threadIndex = i;

                // Sync the branch condition so both sides take the same path
                boolean isCrossRecipe = builder.getSyncer().syncBoolean(
                        logic.isCrossRecipeMode() && logic.getCrossRecipeScheduler() != null);

                // Cross-recipe parallel display per thread
                if (isCrossRecipe) {
                    builder.addCustom((list, syncer) -> list.add(
                            KeyUtil.lang(TextFormatting.GOLD, ">>线程 %s：", threadIndex + 1)));
                    builder.addCrossRecipeOrProgressDisplay(logic);
                    builder.addEmptyLine();
                } else {
                    builder.addCustom((list, syncer) -> list.add(KeyUtil.lang(TextFormatting.GOLD, ">>线程：")))
                            .setWorkingStatus(logic.isWorkingEnabled(), logic.isActive())
                            .addWorkingStatusLine()
                            .addProgressLine(logic.getProgress(), logic.getMaxProgress())
                            .addEmptyLine();
                }
            }
        }
    }

    /**
     * @deprecated Use {@link MultiblockUIBuilder#addCrossRecipeOrProgressDisplay(MultiblockRecipeLogic)} instead.
     */
    @Deprecated
    @Override
    protected void addCrossRecipeDisplay(MultiblockUIBuilder builder, MultiblockRecipeLogic logic) {
        super.addCrossRecipeDisplay(builder, logic);
    }

    protected void addCustomCapacity(KeyManager keyManager, UISyncer syncer) {

    }

    protected void configureWarningText(MultiblockUIBuilder builder) {
        for (MultiblockRecipeLogic recipeMapWorkable : recipeMapWorkable) {
            builder.addLowPowerLine(recipeMapWorkable.isHasNotEnoughEnergy());
            break;
        }
        super.configureWarningText(builder);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        this.getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(),
                checkActive(), checkWorkingEnable());
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("isDistinct", isDistinct);
        data.setInteger("thread", thread);
        // Indexed by list position so the tags line up with the logics they came from when they are read back.
        for (int i = 0; i < recipeMapWorkable.size(); i++) {
            MultiblockRecipeLogic logic = recipeMapWorkable.get(i);
            if (logic.progressTime == 0) continue;
            data.setTag("rp" + i, logic.serializeNBT());
        }

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        isDistinct = data.getBoolean("isDistinct");
        thread = data.getInteger("thread");
        refreshThread(thread);
        // Only logics with progress in flight are written out, so an absent tag must be skipped: deserializing an
        // empty compound would reset the configuration that was just restored from the trait data.
        for (int i = 0; i < recipeMapWorkable.size(); i++) {
            String key = "rp" + i;
            if (data.hasKey(key)) {
                recipeMapWorkable.get(i).deserializeNBT(data.getCompoundTag(key));
            }
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(isDistinct);
        buf.writeInt(thread);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        isDistinct = buf.readBoolean();
        thread = buf.readInt();
        refreshThread(thread);
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }

    @Override
    public boolean isDistinct() {
        return isDistinct;
    }

    @Override
    public void setDistinct(boolean isDistinct) {
        boolean changed = this.isDistinct != isDistinct;
        this.isDistinct = isDistinct;
        for (MultiblockRecipeLogic multiblockRecipeLogic : recipeMapWorkable)
            multiblockRecipeLogic.onDistinctChanged();
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
        for (MultiblockRecipeLogic multiblockRecipeLogic : recipeMapWorkable) {
            if (multiblockRecipeLogic.getMaxProgress() > 0) {
                list.add(new TextComponentTranslation("behavior.tricorder.workable_progress",
                        new TextComponentTranslation(
                                TextFormattingUtil.formatNumbers(multiblockRecipeLogic.getProgress() / 20))
                                .setStyle(new Style().setColor(TextFormatting.GREEN)),
                        new TextComponentTranslation(
                                TextFormattingUtil.formatNumbers(multiblockRecipeLogic.getMaxProgress() / 20))
                                .setStyle(new Style().setColor(TextFormatting.YELLOW))));
            }

            list.add(new TextComponentTranslation("behavior.tricorder.energy_container_storage",
                    new TextComponentTranslation(TextFormattingUtil.formatNumbers(energyContainer.getEnergyStored()))
                            .setStyle(new Style().setColor(TextFormatting.GREEN)),
                    new TextComponentTranslation(TextFormattingUtil.formatNumbers(energyContainer.getEnergyCapacity()))
                            .setStyle(new Style().setColor(TextFormatting.YELLOW))));

            if (multiblockRecipeLogic.getRecipeEUt() > 0) {
                list.add(new TextComponentTranslation("behavior.tricorder.workable_consumption",
                        new TextComponentTranslation(
                                TextFormattingUtil.formatNumbers(multiblockRecipeLogic.getRecipeEUt()))
                                .setStyle(new Style().setColor(TextFormatting.RED)),
                        new TextComponentTranslation(
                                TextFormattingUtil.formatNumbers(multiblockRecipeLogic.getRecipeEUt() == 0 ? 0 : 1))
                                .setStyle(new Style().setColor(TextFormatting.RED))));
            }

            list.add(new TextComponentTranslation("behavior.tricorder.multiblock_energy_input",
                    new TextComponentTranslation(TextFormattingUtil.formatNumbers(energyContainer.getInputVoltage()))
                            .setStyle(new Style().setColor(TextFormatting.YELLOW)),
                    new TextComponentTranslation(
                            GTValues.VN[GTUtility.getTierByVoltage(energyContainer.getInputVoltage())])
                            .setStyle(new Style().setColor(TextFormatting.YELLOW))));

            if (ConfigHolder.machines.enableMaintenance && hasMaintenanceMechanics()) {
                list.add(new TextComponentTranslation("behavior.tricorder.multiblock_maintenance",
                        new TextComponentTranslation(TextFormattingUtil.formatNumbers(getNumMaintenanceProblems()))
                                .setStyle(new Style().setColor(TextFormatting.RED))));
            }

            if (multiblockRecipeLogic.getParallelLimit() > 1) {
                list.add(new TextComponentTranslation("behavior.tricorder.multiblock_parallel",
                        new TextComponentTranslation(
                                TextFormattingUtil.formatNumbers(multiblockRecipeLogic.getParallelLimit()))
                                .setStyle(new Style().setColor(TextFormatting.GREEN))));
            }
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

    @Override
    public void unsetCleanroom() {
        this.cleanroom = null;
    }

    @Override
    public boolean isWorkingEnabled() {
        for (MultiblockRecipeLogic recipeMapWorkable : recipeMapWorkable)
            if (recipeMapWorkable.isWorkingEnabled())
                return true;
        return false;
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        boolean changed = isWorkingEnabled() != isWorkingAllowed;
        for (MultiblockRecipeLogic recipeMapWorkable : recipeMapWorkable)
            recipeMapWorkable.setWorkingEnabled(isWorkingAllowed);
        if (changed) {
            notifyStructureControllerModeChanged();
        }
    }

    @Override
    public boolean isBatchAllowed() {
        return true;
    }

    @Override
    public boolean isBatchEnable() {
        for (MultiblockRecipeLogic recipeMapWorkable : recipeMapWorkable)
            if (recipeMapWorkable.isBatchEnable())
                return true;
        return false;
    }

    @Override
    public void setBatchEnable(boolean enable) {
        boolean changed = isBatchEnable() != enable;
        for (MultiblockRecipeLogic recipeMapWorkable : recipeMapWorkable)
            recipeMapWorkable.setBatchEnable(enable);
        if (changed) {
            notifyStructureConfigChanged();
        }
    }

    @Override
    public boolean isRecipeLocked() {
        for (MultiblockRecipeLogic recipeMapWorkable : recipeMapWorkable)
            if (recipeMapWorkable.isRecipeLockEnable())
                return true;
        return false;
    }

    @Override
    public void setRecipeLocked(boolean enable) {
        boolean changed = isRecipeLocked() != enable;
        for (MultiblockRecipeLogic recipeMapWorkable : recipeMapWorkable)
            recipeMapWorkable.setRecipeLockEnable(enable);
        if (changed) {
            notifyStructureConfigChanged();
        }
    }

    @Override
    public boolean isEnergyLackWarningEnabled() {
        for (MultiblockRecipeLogic recipeMapWorkable : recipeMapWorkable)
            if (recipeMapWorkable.isEnergyLackWarningEnable())
                return true;
        return false;
    }

    @Override
    public void setEnergyLackWarningEnabled(boolean enable) {
        boolean changed = isEnergyLackWarningEnabled() != enable;
        for (MultiblockRecipeLogic recipeMapWorkable : recipeMapWorkable)
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
        values.put("thread", getThread());
        values.put("maxThread", getMaxThread());
        values.put("batchEnable", isBatchEnable());
        values.put("recipeLocked", isRecipeLocked());
        values.put("energyLackWarning", isEnergyLackWarningEnabled());
        return values;
    }

    @Override
    protected boolean isWorkingForStructureCheck() {
        return checkActive();
    }
}
