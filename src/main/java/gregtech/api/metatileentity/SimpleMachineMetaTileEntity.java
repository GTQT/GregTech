package gregtech.api.metatileentity;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IActiveOutputSide;
import gregtech.api.capability.IGhostSlotConfigurable;
import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.capability.impl.FluidHandlerProxy;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.ItemHandlerProxy;
import gregtech.api.cover.Cover;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.resources.TextureArea;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.widget.GhostCircuitSlotWidget;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.client.particle.IMachineParticleEffect;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.utils.RenderUtil;
import gregtech.common.covers.CoverConveyor;
import gregtech.common.covers.CoverFluidFilter;
import gregtech.common.covers.CoverItemFilter;
import gregtech.common.covers.CoverPump;
import gregtech.common.covers.CoverStorage;
import gregtech.common.covers.ender.CoverEnderFluidLink;
import gregtech.common.covers.ender.CoverEnderItemLink;
import gregtech.common.covers.filter.BaseFilterContainer;

import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static gregtech.api.capability.GregtechDataCodes.*;

public class SimpleMachineMetaTileEntity extends WorkableTieredMetaTileEntity
        implements IActiveOutputSide, IGhostSlotConfigurable {

    private static final int FONT_HEIGHT = 9; // Minecraft's FontRenderer FONT_HEIGHT value
    protected final GTItemStackHandler chargerInventory;
    @Nullable // particle run every tick when the machine is active
    protected final IMachineParticleEffect tickingParticle;
    @Nullable // particle run in randomDisplayTick() when the machine is active
    protected final IMachineParticleEffect randomParticle;
    private final boolean hasFrontFacing;
    @Nullable
    protected GhostCircuitItemStackHandler circuitInventory;
    protected IItemHandler outputItemInventory;
    protected IFluidHandler outputFluidInventory;
    private EnumFacing outputFacingItems;
    private EnumFacing outputFacingFluids;
    private boolean autoOutputItems;
    private boolean autoOutputFluids;
    private boolean allowInputFromOutputSideItems = false;
    private boolean allowInputFromOutputSideFluids = false;
    private boolean disallowSameItemInsert = false;
    private IItemHandlerModifiable actualImportItems;

    public SimpleMachineMetaTileEntity(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap,
                                       ICubeRenderer renderer, int tier, boolean hasFrontFacing) {
        this(metaTileEntityId, recipeMap, renderer, tier, hasFrontFacing, GTUtility.defaultTankSizeFunction);
    }

    public SimpleMachineMetaTileEntity(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap,
                                       ICubeRenderer renderer, int tier, boolean hasFrontFacing,
                                       Function<Integer, Integer> tankScalingFunction) {
        this(metaTileEntityId, recipeMap, renderer, tier, hasFrontFacing, tankScalingFunction, null, null);
    }

    public SimpleMachineMetaTileEntity(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap,
                                       ICubeRenderer renderer, int tier, boolean hasFrontFacing,
                                       Function<Integer, Integer> tankScalingFunction,
                                       @Nullable IMachineParticleEffect tickingParticle,
                                       @Nullable IMachineParticleEffect randomParticle) {
        super(metaTileEntityId, recipeMap, renderer, tier, tankScalingFunction);
        this.hasFrontFacing = hasFrontFacing;
        this.chargerInventory = new GTItemStackHandler(this, 1);
        this.tickingParticle = tickingParticle;
        this.randomParticle = randomParticle;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new SimpleMachineMetaTileEntity(metaTileEntityId, workable.getRecipeMap(), renderer, getTier(),
                hasFrontFacing, getTankScalingFunction(), tickingParticle, randomParticle);
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        this.outputItemInventory = new ItemHandlerProxy(new GTItemStackHandler(this, 0), exportItems);
        this.outputFluidInventory = new FluidHandlerProxy(new FluidTankList(false), exportFluids);
        if (this.hasGhostCircuitInventory()) {
            this.circuitInventory = new GhostCircuitItemStackHandler(this);
        }

        this.actualImportItems = null;

        // 初始化是否允许输入相同物品的状态
        if (super.getImportItems() instanceof GTItemStackHandler handler) {
            handler.setAllowSameItemInsert(!disallowSameItemInsert);
        }
    }

    @Override
    public IItemHandlerModifiable getImportItems() {
        if (this.actualImportItems == null) {
            this.actualImportItems = this.circuitInventory == null ?
                    super.getImportItems() :
                    new ItemHandlerList(Arrays.asList(super.getImportItems(), this.circuitInventory));
        }
        return this.actualImportItems;
    }

    @Override
    public boolean hasFrontFacing() {
        return hasFrontFacing;
    }

    @Override
    public boolean onWrenchClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                 CuboidRayTraceResult hitResult) {
        if (!playerIn.isSneaking()) {
            // TODO Separate into two output getters
            if (getOutputFacing() == facing) return false;
            if (hasFrontFacing() && facing == getFrontFacing()) return false;
            if (!getWorld().isRemote) {
                // TODO Separate into two output setters
                setOutputFacing(facing);
            }
            return true;
        }
        return super.onWrenchClick(playerIn, hand, facing, hitResult);
    }

    @Override
    public void addCover(@NotNull EnumFacing side, @NotNull Cover cover) {
        super.addCover(side, cover);
        if (cover.canInteractWithOutputSide()) {
            if (getOutputFacingItems() == side) {
                setAllowInputFromOutputSideItems(true);
            }
            if (getOutputFacingFluids() == side) {
                setAllowInputFromOutputSideFluids(true);
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (outputFacingFluids != null && getExportFluids().getTanks() > 0) {
            Textures.PIPE_OUT_OVERLAY.renderSided(outputFacingFluids, renderState,
                    RenderUtil.adjustTrans(translation, outputFacingFluids, 2), pipeline);
        }
        if (outputFacingItems != null && getExportItems().getSlots() > 0) {
            Textures.PIPE_OUT_OVERLAY.renderSided(outputFacingItems, renderState,
                    RenderUtil.adjustTrans(translation, outputFacingItems, 2), pipeline);
        }
        if (isAutoOutputItems() && outputFacingItems != null) {
            Textures.ITEM_OUTPUT_OVERLAY.renderSided(outputFacingItems, renderState,
                    RenderUtil.adjustTrans(translation, outputFacingItems, 2), pipeline);
        }
        if (isAutoOutputFluids() && outputFacingFluids != null) {
            Textures.FLUID_OUTPUT_OVERLAY.renderSided(outputFacingFluids, renderState,
                    RenderUtil.adjustTrans(translation, outputFacingFluids, 2), pipeline);
        }
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote) {
            ItemStack stack = chargerInventory.getStackInSlot(0);
            if (!stack.isEmpty() && energyContainer.getEnergyStored() < energyContainer.getEnergyCapacity()) {
                if (stack.isItemEqual(OreDictUnifier.get(OrePrefix.dust, Materials.Redstone))) {
                    stack.shrink(1);
                    energyContainer.addEnergy(1920);
                } else ((EnergyContainerHandler) this.energyContainer).dischargeOrRechargeEnergyContainers(stack);
            }
            if (getOffsetTimer() % 5 == 0) {
                if (isAutoOutputFluids()) {
                    pushFluidsIntoNearbyHandlers(getOutputFacingFluids());
                }
                if (isAutoOutputItems()) {
                    pushItemsIntoNearbyHandlers(getOutputFacingItems());
                }
            }
        } else if (this.tickingParticle != null && isActive()) {
            tickingParticle.runEffect(this);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick() {
        if (this.randomParticle != null && isActive()) {
            randomParticle.runEffect(this);
        }
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        if (!getWorld().isRemote) {
            if (isAllowInputFromOutputSideItems()) {
                setAllowInputFromOutputSideItems(false);
                setAllowInputFromOutputSideFluids(false);
                playerIn.sendStatusMessage(
                        new TextComponentTranslation("gregtech.machine.basic.input_from_output_side.disallow"), true);
            } else {
                setAllowInputFromOutputSideItems(true);
                setAllowInputFromOutputSideFluids(true);
                playerIn.sendStatusMessage(
                        new TextComponentTranslation("gregtech.machine.basic.input_from_output_side.allow"), true);
            }
        }
        return true;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            IFluidHandler fluidHandler = (side == getOutputFacingFluids() && !isAllowInputFromOutputSideFluids()) ?
                    outputFluidInventory : fluidInventory;
            if (fluidHandler.getTankProperties().length > 0) {
                return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluidHandler);
            }
            return null;
        } else if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            IItemHandler itemHandler = (side == getOutputFacingItems() && !isAllowInputFromOutputSideFluids()) ?
                    outputItemInventory : itemInventory;
            if (itemHandler.getSlots() > 0) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemHandler);
            }
            return null;
        } else if (capability == GregtechTileCapabilities.CAPABILITY_ACTIVE_OUTPUT_SIDE) {
            if (side == getOutputFacingItems() || side == getOutputFacingFluids()) {
                return GregtechTileCapabilities.CAPABILITY_ACTIVE_OUTPUT_SIDE.cast(this);
            }
            return null;
        }
        return super.getCapability(capability, side);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("ChargerInventory", chargerInventory.serializeNBT());
        if (this.circuitInventory != null) {
            this.circuitInventory.write(data);
        }
        data.setInteger("OutputFacing", getOutputFacingItems().getIndex());
        data.setInteger("OutputFacingF", getOutputFacingFluids().getIndex());
        data.setBoolean("AutoOutputItems", autoOutputItems);
        data.setBoolean("AutoOutputFluids", autoOutputFluids);
        data.setBoolean("AllowInputFromOutputSide", allowInputFromOutputSideItems);
        data.setBoolean("AllowInputFromOutputSideF", allowInputFromOutputSideFluids);
        data.setBoolean("DisallowSameItemInsert", disallowSameItemInsert);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.chargerInventory.deserializeNBT(data.getCompoundTag("ChargerInventory"));
        if (this.circuitInventory != null) {
            if (data.hasKey("CircuitInventory", Constants.NBT.TAG_COMPOUND)) {
                // legacy save support - move items in circuit inventory to importItems inventory, if possible
                ItemStackHandler legacyCircuitInventory = new ItemStackHandler();
                legacyCircuitInventory.deserializeNBT(data.getCompoundTag("CircuitInventory"));
                for (int i = 0; i < legacyCircuitInventory.getSlots(); i++) {
                    ItemStack stack = legacyCircuitInventory.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    stack = GTTransferUtils.insertItem(this.importItems, stack, false);
                    // If there's no space left in importItems, just set it as ghost circuit and void the item
                    this.circuitInventory.setCircuitValueFromStack(stack);
                }
            } else {
                this.circuitInventory.read(data);
            }
        }
        this.outputFacingItems = EnumFacing.VALUES[data.getInteger("OutputFacing")];
        this.outputFacingFluids = EnumFacing.VALUES[data.getInteger("OutputFacingF")];
        this.autoOutputItems = data.getBoolean("AutoOutputItems");
        this.autoOutputFluids = data.getBoolean("AutoOutputFluids");
        this.allowInputFromOutputSideItems = data.getBoolean("AllowInputFromOutputSide");
        this.allowInputFromOutputSideFluids = data.getBoolean("AllowInputFromOutputSideF");
        this.disallowSameItemInsert = data.getBoolean("DisallowSameItemInsert");
        // 同步更新底层 GTItemStackHandler 的状态
        if (super.getImportItems() instanceof GTItemStackHandler handler) {
            handler.setAllowSameItemInsert(!disallowSameItemInsert);
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeByte(getOutputFacingItems().getIndex());
        buf.writeByte(getOutputFacingFluids().getIndex());
        buf.writeBoolean(autoOutputItems);
        buf.writeBoolean(autoOutputFluids);
        buf.writeBoolean(disallowSameItemInsert);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.outputFacingItems = EnumFacing.VALUES[buf.readByte()];
        this.outputFacingFluids = EnumFacing.VALUES[buf.readByte()];
        this.autoOutputItems = buf.readBoolean();
        this.autoOutputFluids = buf.readBoolean();
        this.disallowSameItemInsert = buf.readBoolean();
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == UPDATE_OUTPUT_FACING) {
            this.outputFacingItems = EnumFacing.VALUES[buf.readByte()];
            this.outputFacingFluids = EnumFacing.VALUES[buf.readByte()];
            scheduleRenderUpdate();
        } else if (dataId == UPDATE_AUTO_OUTPUT_ITEMS) {
            this.autoOutputItems = buf.readBoolean();
            scheduleRenderUpdate();
        } else if (dataId == UPDATE_AUTO_OUTPUT_FLUIDS) {
            this.autoOutputFluids = buf.readBoolean();
            scheduleRenderUpdate();
        } else if (dataId == UPDATE_DISALLOW_SAME_ITEM) {
            this.disallowSameItemInsert = buf.readBoolean();
        }
    }

    @Override
    public boolean isValidFrontFacing(EnumFacing facing) {
        // use direct outputFacing field instead of getter method because otherwise
        // it will just return SOUTH for null output facing
        return super.isValidFrontFacing(facing) && facing != outputFacingItems && facing != outputFacingFluids;
    }

    @Override
    public int getGhostCircuitConfig() {
        if (this.circuitInventory == null) {
            return 0;
        }
        return this.circuitInventory.getCircuitValue();
    }

    @Override
    public void setGhostCircuitConfig(int config) {
        if (this.circuitInventory == null || this.circuitInventory.getCircuitValue() == config) {
            return;
        }
        this.circuitInventory.setCircuitValue(config);
        if (!getWorld().isRemote) {
            markDirty();
        }
    }

    @Override
    public void setGhostCustomStack(@NotNull ItemStack stack) {
        if (this.circuitInventory == null) {
            return;
        }
        this.circuitInventory.setCustomStack(stack);
        if (!getWorld().isRemote) {
            markDirty();
        }
    }

    @Override
    public void setFrontFacing(EnumFacing frontFacing) {
        super.setFrontFacing(frontFacing);
        if (this.outputFacingItems == null || this.outputFacingFluids == null) {
            // set initial output facing as opposite to front
            setOutputFacing(frontFacing.getOpposite());
        }
    }

    @Deprecated
    public EnumFacing getOutputFacing() {
        return getOutputFacingItems();
    }

    public void setOutputFacing(EnumFacing outputFacing) {
        this.outputFacingItems = outputFacing;
        this.outputFacingFluids = outputFacing;
        if (!getWorld().isRemote) {
            notifyBlockUpdate();
            writeCustomData(UPDATE_OUTPUT_FACING, buf -> {
                buf.writeByte(outputFacingItems.getIndex());
                buf.writeByte(outputFacingFluids.getIndex());
            });
            markDirty();
        }
    }

    public EnumFacing getOutputFacingItems() {
        return outputFacingItems == null ? EnumFacing.SOUTH : outputFacingItems;
    }

    public void setOutputFacingItems(EnumFacing outputFacing) {
        this.outputFacingItems = outputFacing;
        if (!getWorld().isRemote) {
            notifyBlockUpdate();
            writeCustomData(UPDATE_OUTPUT_FACING, buf -> {
                buf.writeByte(outputFacingItems.getIndex());
                buf.writeByte(outputFacingFluids.getIndex());
            });
            markDirty();
        }
    }

    public EnumFacing getOutputFacingFluids() {
        return outputFacingFluids == null ? EnumFacing.SOUTH : outputFacingFluids;
    }

    public void setOutputFacingFluids(EnumFacing outputFacing) {
        this.outputFacingFluids = outputFacing;
        if (!getWorld().isRemote) {
            notifyBlockUpdate();
            writeCustomData(UPDATE_OUTPUT_FACING, buf -> {
                buf.writeByte(outputFacingItems.getIndex());
                buf.writeByte(outputFacingFluids.getIndex());
            });
            markDirty();
        }
    }

    public boolean isAutoOutputItems() {
        return autoOutputItems;
    }

    public void setAutoOutputItems(boolean autoOutputItems) {
        this.autoOutputItems = autoOutputItems;
        if (!getWorld().isRemote) {
            writeCustomData(UPDATE_AUTO_OUTPUT_ITEMS, buf -> buf.writeBoolean(autoOutputItems));
            markDirty();
        }
    }

    public boolean isAutoOutputFluids() {
        return autoOutputFluids;
    }

    public void setAutoOutputFluids(boolean autoOutputFluids) {
        this.autoOutputFluids = autoOutputFluids;
        if (!getWorld().isRemote) {
            writeCustomData(UPDATE_AUTO_OUTPUT_FLUIDS, buf -> buf.writeBoolean(autoOutputFluids));
            markDirty();
        }
    }

    public boolean isAllowInputFromOutputSideItems() {
        return allowInputFromOutputSideItems;
    }

    public void setAllowInputFromOutputSideItems(boolean allowInputFromOutputSide) {
        this.allowInputFromOutputSideItems = allowInputFromOutputSide;
        if (!getWorld().isRemote) {
            markDirty();
        }
    }

    public boolean isAllowInputFromOutputSideFluids() {
        return allowInputFromOutputSideFluids;
    }

    public void setAllowInputFromOutputSideFluids(boolean allowInputFromOutputSide) {
        this.allowInputFromOutputSideFluids = allowInputFromOutputSide;
        if (!getWorld().isRemote) {
            markDirty();
        }
    }

    public boolean isDisallowSameItemInsert() {
        return disallowSameItemInsert;
    }

    public void setDisallowSameItemInsert(boolean disallowSameItemInsert) {
        this.disallowSameItemInsert = disallowSameItemInsert;
        if (!getWorld().isRemote) {
            // 同步更新底层 GTItemStackHandler 的状态，穿透 ItemHandlerList 代理
            IItemHandlerModifiable handler = getImportItems();
            if (handler instanceof ItemHandlerList list) {
                for (IItemHandler h : list.getBackingHandlers()) {
                    if (h instanceof GTItemStackHandler gtHandler) {
                        gtHandler.setAllowSameItemInsert(!disallowSameItemInsert);
                    }
                }
            } else if (handler instanceof GTItemStackHandler gtHandler) {
                gtHandler.setAllowSameItemInsert(!disallowSameItemInsert);
            }
            writeCustomData(UPDATE_DISALLOW_SAME_ITEM, buf -> buf.writeBoolean(disallowSameItemInsert));
            markDirty();
        }
    }

    @Override
    public void clearMachineInventory(@NotNull List<@NotNull ItemStack> itemBuffer) {
        super.clearMachineInventory(itemBuffer);
        clearInventory(itemBuffer, chargerInventory);
    }

    @Override
    public boolean usesMui2() {
        RecipeMap<?> map = getRecipeMap();
        return map != null && map.getRecipeMapUI().usesMui2();
    }

    private BaseFilterContainer getFilterContainerFromCover(Cover cover) {
        if (cover instanceof CoverConveyor conveyor) {
            return conveyor.getItemFilterContainer();
        } else if (cover instanceof CoverPump pump) {
            return pump.getFluidFilterContainer();
        } else if (cover instanceof CoverItemFilter itemFilter) {
            return itemFilter.getFilterContainer();
        } else if (cover instanceof CoverFluidFilter fluidFilter) {
            return fluidFilter.getFilterContainer();
        } else if (cover instanceof CoverEnderFluidLink enderFluidLink) {
            return enderFluidLink.getFluidFilterContainer();
        } else if (cover instanceof CoverEnderItemLink enderItemLink) {
            return enderItemLink.getItemFilterContainer();
        }
        return null;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        RecipeMap<?> workableRecipeMap = Objects.requireNonNull(workable.getRecipeMap(), "recipe map is null");

        var throttle = panelSyncManager.syncedPanel("mte_setting", true, this::makeThrottlePanel);

        Flow flowRow = Flow.row()
                .name("col:extra.buttons")
                .left(7).bottom(18 * 4 + 14);

        int s = 0;

        for (EnumFacing data : EnumFacing.VALUES) {
            Cover cover = this.getCoverAtSide(data);
            BaseFilterContainer filter = getFilterContainerFromCover(cover);

            if (filter != null && filter.hasFilter()) {
                flowRow.child(filter.initUILeisure(guiData, panelSyncManager, data.getIndex()));
                s++;
            } else if (cover instanceof CoverStorage coverStorage) {
                flowRow.child(coverStorage.initUILeisure(guiData, panelSyncManager, data.getIndex()));
                s++;
            }
        }

        flowRow.size(s * 18, 18);

        int colHeight = 18; // logo
        if (hasGhostCircuitInventory() && circuitInventory != null) colHeight += 18;
        colHeight += 18; // charger
        colHeight += 18; // device settings
        if (exportFluids.getTanks() > 0) colHeight += 18;
        if (exportItems.getSlots() > 0) colHeight += 18;

        Flow col = Flow.column()
                .name("col:special.buttons")
                .right(7).bottom(7)
                .height(colHeight)
                .width(18);


        BooleanSyncValue hasNoEnergy = new BooleanSyncValue(workable::isHasNotEnoughEnergy);
        panelSyncManager.syncValue("has_energy", hasNoEnergy);

        int panelHeight = s > 0 ? 188 : 170;

        ModularPanel panel = workableRecipeMap.getRecipeMapUI()
                .constructPanel(this, builder -> builder
                        .calculateOffset()
                        .setMaxSize(176 + 20, panelHeight)
                        .setInputs(importItems, importFluids)
                        .setOutputs(exportItems, exportFluids)
                        .inventorySlotGroups()
                        .progressWidget(workable::getProgressPercent, widget -> widget
                                // todo add tooltip for no energy?
                                .overlay(new DynamicDrawable(() -> hasNoEnergy.getBoolValue() ?
                                        GTGuiTextures.INDICATOR_NO_ENERGY : IDrawable.NONE)
                                        .asIcon().size(18).marginTop(50))))
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(col)
                .child(flowRow)
                .child(SlotGroupWidget.playerInventory(true).left(7));

        int bottomOffset = 0;

        // Logo
        col.child(new Widget<>()
                .size(17)
                .marginTop(1)
                .marginRight(1)
                .bottom(bottomOffset)
                .background(GTGuiTextures.getLogo(getUITheme())));
        bottomOffset += 18 + 4;

        // 电路
        if (hasGhostCircuitInventory() && circuitInventory != null) {
            col.child(new GhostCircuitSlotWidget()
                    .bottom(bottomOffset)
                    .slot(circuitInventory, 0)
                    .background(GTGuiTextures.SLOT, GTGuiTextures.INT_CIRCUIT_OVERLAY));
            bottomOffset += 18;
        }

        // 电池
        col.child(new ItemSlot()
                .name("charger.slot")
                .slot(SyncHandlers.itemSlot(chargerInventory, 0))
                .background(GTGuiTextures.SLOT, GTGuiTextures.CHARGER_OVERLAY)
                .bottom(bottomOffset)
                .addTooltipLine(IKey.lang("gregtech.gui.charger_slot.tooltip",
                        GTValues.VNF[getTier()], GTValues.VNF[getTier()])));
        bottomOffset += 18;

        // 设备设置
        col.child(new ButtonWidget<>()
                .size(18)
                .overlay(GTGuiTextures.FILTER_SETTINGS_OVERLAY.asIcon().size(16))
                .addTooltipLine("设备设置")
                .bottom(bottomOffset)
                .onMousePressed(i -> {
                    if (throttle.isPanelOpen()) {
                        throttle.closePanel();
                    } else {
                        throttle.openPanel();
                    }
                    return true;
                })
        );
        bottomOffset += 18;

        // 流体自动输出
        if (exportFluids.getTanks() > 0) {
            col.child(new ToggleButton()
                    .overlay(GTGuiTextures.BUTTON_FLUID_OUTPUT)
                    .bottom(bottomOffset)
                    .value(new BooleanSyncValue(() -> autoOutputFluids, val -> autoOutputFluids = val))
                    .addTooltip(true, IKey.lang("gregtech.gui.fluid_auto_output.tooltip.enabled"))
                    .addTooltip(false, IKey.lang("gregtech.gui.fluid_auto_output.tooltip.disabled")));
            bottomOffset += 18;
        }

        // 物品自动输出
        if (exportItems.getSlots() > 0) {
            col.child(new ToggleButton()
                    .overlay(GTGuiTextures.BUTTON_ITEM_OUTPUT)
                    .bottom(bottomOffset)
                    .value(new BooleanSyncValue(() -> autoOutputItems, val -> autoOutputItems = val))
                    .addTooltip(true, IKey.lang("gregtech.gui.item_auto_output.tooltip.enabled"))
                    .addTooltip(false, IKey.lang("gregtech.gui.item_auto_output.tooltip.disabled")));
        }

        return panel;
    }

    public String getEnumFacingName(EnumFacing facing) {
        TileEntity tileEntity = this.getWorld().getTileEntity(getPos().offset(facing));
        if (tileEntity instanceof IGregTechTileEntity igtte) {
            MetaTileEntity mte = igtte.getMetaTileEntity();
            return IKey.lang(mte.getMetaFullName()).toString();
        }
        Block block = this.getWorld().getBlockState(getPos().offset(facing)).getBlock();
        return block.getLocalizedName();
    }

    private ModularPanel makeThrottlePanel(PanelSyncManager syncManager, IPanelHandler syncHandler) {
        return GTGuis.createPopupPanel("mte_setting", 180, 115)
                .child(Flow.row()
                        .pos(4, 4)
                        .height(16)
                        .coverChildrenWidth()
                        .child(new ItemDrawable(getStackForm())
                                .asWidget()
                                .size(16)
                                .marginRight(4))
                        .child(IKey.lang("设备设置")
                                .asWidget()
                                .heightRel(1.0f)))
                .child(Flow.row()
                        /// ///////////////////////////////////////////////////////////////////////
                        // 开关 电源
                        .child(new ToggleButton()
                                .pos(20, 25)
                                .overlay(true, GTGuiTextures.BUTTON_POWER[1])
                                .overlay(false, GTGuiTextures.BUTTON_POWER[0])
                                .value(new BooleanSyncValue(
                                        workable::isWorkingEnabled,
                                        val -> {
                                            if (!getWorld().isRemote) {
                                                workable.setWorkingEnabled(val);
                                            }
                                        }))
                                .addTooltipLine("设置电源开关")
                        )
                        .child(new ToggleButton()
                                .pos(40, 25)
                                .overlay(true, GTGuiTextures.SOUND_STATE[1])
                                .overlay(false, GTGuiTextures.SOUND_STATE[0])
                                .value(new BooleanSyncValue(
                                        this::isMuffled,
                                        val -> {
                                            if (!getWorld().isRemote) {
                                                toggleMuffled();
                                                syncManager.getPlayer()
                                                        .sendStatusMessage(new TextComponentTranslation(isMuffled() ?
                                                                "gregtech.machine.muffle.on" :
                                                                "gregtech.machine.muffle.off"), true);
                                            }
                                        }))
                                .addTooltipLine("设置静音开关")
                        )
                        .child(new ToggleButton()
                                .pos(60, 25)
                                .value(new BooleanSyncValue(
                                        workable::isRecipeLockEnable,
                                        val -> {
                                            if (!getWorld().isRemote) {
                                                workable.setRecipeLockEnable(val);
                                            }
                                        }))
                                .overlay(true, GTGuiTextures.OVERLAY_RECIPE_LOCK[1])
                                .overlay(false, GTGuiTextures.OVERLAY_RECIPE_LOCK[0])
                                .addTooltip(true, IKey.lang("gregtech.multiblock.universal.lock_enabled"))
                                .addTooltip(false, IKey.lang("gregtech.multiblock.universal.lock_disabled"))
                        )
                        .child(new ToggleButton()
                                .pos(80, 25)
                                .overlay(true, GTGuiTextures.BUTTON_LOCK)
                                .overlay(false, GTGuiTextures.BUTTON_LOCK)
                                .value(new BooleanSyncValue(
                                        this::isDisallowSameItemInsert,
                                        this::setDisallowSameItemInsert))
                                .addTooltip(true, IKey.lang("gregtech.machine.disallow_same_item.enabled"))
                                .addTooltip(false, IKey.lang("gregtech.machine.disallow_same_item.disabled"))
                        )

                        /// ///////////////////////////////////////////////////////////////////////
                        // 顶部IO按钮
                        .child(new ToggleButton()
                                .pos(40, 45)
                                .overlay(true, GTGuiTextures.OVERLAY_ITEM_EXPORT[1])
                                .overlay(false, GTGuiTextures.OVERLAY_ITEM_EXPORT[0])
                                .value(new BooleanSyncValue(
                                        () -> outputFacingItems == EnumFacing.UP,
                                        val -> {
                                            if (!getWorld().isRemote && val) {
                                                setOutputFacingItems(EnumFacing.UP);
                                            }
                                        }))
                                .addTooltipLine("设置顶部物品IO")
                                .addTooltipLine(getEnumFacingName(EnumFacing.UP))
                        )

                        // 正面IO按钮
                        .child(new ToggleButton()
                                .pos(40, 65)
                                .overlay(true, GTGuiTextures.OVERLAY_ITEM_EXPORT[1])
                                .overlay(false, GTGuiTextures.OVERLAY_ITEM_EXPORT[0])
                                .value(new BooleanSyncValue(
                                        () -> outputFacingItems == frontFacing,
                                        val -> {
                                            if (!getWorld().isRemote && val) {
                                                setOutputFacingItems(frontFacing);
                                            }
                                        }))
                                .addTooltipLine("设置正面物品IO")
                                .addTooltipLine(getEnumFacingName(frontFacing))
                        )

                        // 左面IO按钮
                        .child(new ToggleButton()
                                .pos(20, 65)
                                .overlay(true, GTGuiTextures.OVERLAY_ITEM_EXPORT[1])
                                .overlay(false, GTGuiTextures.OVERLAY_ITEM_EXPORT[0])
                                .value(new BooleanSyncValue(
                                        () -> outputFacingItems == frontFacing.rotateY(),
                                        val -> {
                                            if (!getWorld().isRemote && val) {
                                                setOutputFacingItems(frontFacing.rotateY());
                                            }
                                        }))
                                .addTooltipLine("设置左面物品IO")
                                .addTooltipLine(getEnumFacingName(frontFacing.rotateY()))
                        )

                        // 右面IO按钮
                        .child(new ToggleButton()
                                .pos(60, 65)
                                .overlay(true, GTGuiTextures.OVERLAY_ITEM_EXPORT[1])
                                .overlay(false, GTGuiTextures.OVERLAY_ITEM_EXPORT[0])
                                .value(new BooleanSyncValue(
                                        () -> outputFacingItems == frontFacing.getOpposite().rotateY(),
                                        val -> {
                                            if (!getWorld().isRemote && val) {
                                                setOutputFacingItems(frontFacing.getOpposite().rotateY());
                                            }
                                        }))
                                .addTooltipLine("设置右面物品IO")
                                .addTooltipLine(getEnumFacingName(frontFacing.getOpposite().rotateY()))
                        )

                        // 底部IO按钮
                        .child(new ToggleButton()
                                .pos(40, 85)
                                .overlay(true, GTGuiTextures.OVERLAY_ITEM_EXPORT[1])
                                .overlay(false, GTGuiTextures.OVERLAY_ITEM_EXPORT[0])
                                .value(new BooleanSyncValue(
                                        () -> outputFacingItems == EnumFacing.DOWN,
                                        val -> {
                                            if (!getWorld().isRemote && val) {
                                                setOutputFacingItems(EnumFacing.DOWN);
                                            }
                                        }))
                                .addTooltipLine("设置底部物品IO")
                                .addTooltipLine(getEnumFacingName(EnumFacing.DOWN))
                        )

                        // 背面IO按钮
                        .child(new ToggleButton()
                                .pos(20, 85)
                                .overlay(true, GTGuiTextures.OVERLAY_ITEM_EXPORT[1])
                                .overlay(false, GTGuiTextures.OVERLAY_ITEM_EXPORT[0])
                                .value(new BooleanSyncValue(
                                        () -> outputFacingItems == frontFacing.getOpposite(),
                                        val -> {
                                            if (!getWorld().isRemote && val) {
                                                setOutputFacingItems(frontFacing.getOpposite());
                                            }
                                        }))
                                .addTooltipLine("设置背面物品IO")
                                .addTooltipLine(getEnumFacingName(frontFacing.getOpposite()))
                        )

                        /// ///////////////////////////////////////////////////////////////////////
                        // 顶部IO按钮
                        .child(new ToggleButton()
                                .pos(120, 45)
                                .overlay(true, GTGuiTextures.OVERLAY_FLUID_EXPORT[1])
                                .overlay(false, GTGuiTextures.OVERLAY_FLUID_EXPORT[0])
                                .value(new BooleanSyncValue(
                                        () -> outputFacingFluids == EnumFacing.UP,
                                        val -> {
                                            if (!getWorld().isRemote && val) {
                                                setOutputFacingFluids(EnumFacing.UP);
                                            }
                                        }))
                                .addTooltipLine("设置顶部流体IO")
                                .addTooltipLine(getEnumFacingName(EnumFacing.UP))
                        )

                        // 正面IO按钮
                        .child(new ToggleButton()
                                .pos(120, 65)
                                .overlay(true, GTGuiTextures.OVERLAY_FLUID_EXPORT[1])
                                .overlay(false, GTGuiTextures.OVERLAY_FLUID_EXPORT[0])
                                .value(new BooleanSyncValue(
                                        () -> outputFacingFluids == frontFacing,
                                        val -> {
                                            if (!getWorld().isRemote && val) {
                                                setOutputFacingFluids(frontFacing);
                                            }
                                        }))
                                .addTooltipLine("设置正面流体IO")
                                .addTooltipLine(getEnumFacingName(frontFacing))
                        )

                        // 左面IO按钮
                        .child(new ToggleButton()
                                .pos(100, 65)
                                .overlay(true, GTGuiTextures.OVERLAY_FLUID_EXPORT[1])
                                .overlay(false, GTGuiTextures.OVERLAY_FLUID_EXPORT[0])
                                .value(new BooleanSyncValue(
                                        () -> outputFacingFluids == frontFacing.rotateY(),
                                        val -> {
                                            if (!getWorld().isRemote && val) {
                                                setOutputFacingFluids(frontFacing.rotateY());
                                            }
                                        }))
                                .addTooltipLine("设置左面流体IO")
                                .addTooltipLine(getEnumFacingName(frontFacing.rotateY()))
                        )

                        // 右面IO按钮
                        .child(new ToggleButton()
                                .pos(140, 65)
                                .overlay(true, GTGuiTextures.OVERLAY_FLUID_EXPORT[1])
                                .overlay(false, GTGuiTextures.OVERLAY_FLUID_EXPORT[0])
                                .value(new BooleanSyncValue(
                                        () -> outputFacingFluids == frontFacing.getOpposite().rotateY(),
                                        val -> {
                                            if (!getWorld().isRemote && val) {
                                                setOutputFacingFluids(frontFacing.getOpposite().rotateY());
                                            }
                                        }))
                                .addTooltipLine("设置右面流体IO")
                                .addTooltipLine(getEnumFacingName(frontFacing.getOpposite().rotateY()))
                        )

                        // 底部IO按钮
                        .child(new ToggleButton()
                                .pos(120, 85)
                                .overlay(true, GTGuiTextures.OVERLAY_FLUID_EXPORT[1])
                                .overlay(false, GTGuiTextures.OVERLAY_FLUID_EXPORT[0])
                                .value(new BooleanSyncValue(
                                        () -> outputFacingFluids == EnumFacing.DOWN,
                                        val -> {
                                            if (!getWorld().isRemote && val) {
                                                setOutputFacingFluids(EnumFacing.DOWN);
                                            }
                                        }))
                                .addTooltipLine("设置底部流体IO")
                                .addTooltipLine(getEnumFacingName(EnumFacing.DOWN))
                        )

                        // 背面IO按钮
                        .child(new ToggleButton()
                                .pos(100, 85)
                                .overlay(true, GTGuiTextures.OVERLAY_FLUID_EXPORT[1])
                                .overlay(false, GTGuiTextures.OVERLAY_FLUID_EXPORT[0])
                                .value(new BooleanSyncValue(
                                        () -> outputFacingFluids == frontFacing.getOpposite(),
                                        val -> {
                                            if (!getWorld().isRemote && val) {
                                                setOutputFacingFluids(frontFacing.getOpposite());
                                            }
                                        }))
                                .addTooltipLine("设置背面流体IO")
                                .addTooltipLine(getEnumFacingName(frontFacing.getOpposite()))
                        )

                        //允许从输出口输入
                        .child(new ToggleButton()
                                .pos(60, 85)
                                .overlay(true, GTGuiTextures.OVERLAY_ITEM_EXPORT_IMPORT[1])
                                .overlay(false, GTGuiTextures.OVERLAY_ITEM_EXPORT_IMPORT[0])
                                .value(new BooleanSyncValue(this::isAllowInputFromOutputSideItems,
                                        this::setAllowInputFromOutputSideItems))
                                .addTooltip(true, IKey.lang("允许从物品输出口输入物品"))
                                .addTooltip(false, IKey.lang("禁止从物品输出口输入物品"))
                        )

                        .child(new ToggleButton()
                                .pos(140, 85)
                                .overlay(true, GTGuiTextures.OVERLAY_FLUID_EXPORT_IMPORT[1])
                                .overlay(false, GTGuiTextures.OVERLAY_FLUID_EXPORT_IMPORT[0])
                                .value(new BooleanSyncValue(this::isAllowInputFromOutputSideFluids,
                                        this::setAllowInputFromOutputSideFluids))
                                .addTooltip(true, IKey.lang("允许从流体输出口输入物品"))
                                .addTooltip(false, IKey.lang("禁止从流体输出口输入物品"))
                        )
                );
    }



    protected @NotNull TextureArea getLogo() {
        return GuiTextures.GREGTECH_LOGO;
    }

    protected @NotNull TextureArea getXmasLogo() {
        return GuiTextures.GREGTECH_LOGO_XMAS;
    }

    @Override
    public boolean hasGhostCircuitInventory() {
        return true;
    }

    // Method provided to override
    protected TextureArea getCircuitSlotOverlay() {
        return GuiTextures.INT_CIRCUIT_OVERLAY;
    }

    // Method provided to override
    protected void getCircuitSlotTooltip(SlotWidget widget) {
        String configString;
        if (circuitInventory == null || circuitInventory.getCircuitValue() == GhostCircuitItemStackHandler.NO_CONFIG) {
            configString = new TextComponentTranslation("gregtech.gui.configurator_slot.no_value").getFormattedText();
        } else {
            configString = String.valueOf(circuitInventory.getCircuitValue());
        }

        widget.setTooltipText("gregtech.gui.configurator_slot.tooltip", configString);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        String key = this.metaTileEntityId.getPath().split("\\.")[0];
        String mainKey = String.format("gregtech.machine.%s.tooltip", key);
        if (I18n.hasKey(mainKey)) {
            tooltip.add(1, mainKey);
        }
    }

    @Override
    public boolean needsSneakToRotate() {
        return true;
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.auto_output_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        tooltip.add(I18n.format("gregtech.tool_action.soft_mallet.reset"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }
}
