package gregtech.common.metatileentities.storage;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IActiveOutputSide;
import gregtech.api.capability.IFilter;
import gregtech.api.capability.IFilteredFluidContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.FilteredItemHandler;
import gregtech.api.capability.impl.FluidHandlerProxy;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.GTFluidHandlerItemStack;
import gregtech.api.cover.CoverRayTracer;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.IFastRenderMetaTileEntity;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.sync.GTFluidSyncHandler;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.custom.QuantumStorageRenderer;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.animation.Animator;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.Interpolation;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ScrollingTextWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

import static gregtech.api.capability.GregtechDataCodes.UPDATE_FLUID;
import static gregtech.api.capability.GregtechDataCodes.UPDATE_FLUID_AMOUNT;
import static net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack.FLUID_NBT_KEY;

public class MetaTileEntityQuantumMultiTank extends MetaTileEntityQuantumStorage<FluidTankList>
        implements ITieredMetaTileEntity, IActiveOutputSide, IFastRenderMetaTileEntity {

    private static final int TANK_COUNT = 4;  // 4个流体槽
    private final int tier;
    private final int maxFluidCapacity;
    protected FluidTankList fluidTanks;
    protected IFluidHandler outputFluidInventory;

    @Nullable
    protected FluidStack[] previousFluids;
    @Nullable
    private FluidStack[] lockedFluids;

    public MetaTileEntityQuantumMultiTank(ResourceLocation metaTileEntityId, int tier, int maxFluidCapacity) {
        super(metaTileEntityId);
        this.tier = tier;
        this.maxFluidCapacity = maxFluidCapacity;
        initializeInventory();
    }

    @Override
    public int getTier() {
        return tier;
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        // 创建4个独立的流体储罐
        FluidTank[] tanks = new FluidTank[TANK_COUNT];
        for (int i = 0; i < TANK_COUNT; i++) {
            tanks[i] = new QuantumFluidTank(maxFluidCapacity, i);
        }
        this.fluidTanks = new FluidTankList(false, tanks);
        this.fluidInventory = fluidTanks;
        this.importFluids = fluidTanks;
        this.exportFluids = fluidTanks;

        // 创建输出代理
        this.outputFluidInventory = new FluidHandlerProxy(new FluidTankList(false), exportFluids);

        // 初始化同步数组
        this.previousFluids = new FluidStack[TANK_COUNT];
        this.lockedFluids = new FluidStack[TANK_COUNT];
    }

    // 获取指定槽位的 FluidTank
    protected FluidTank getTankAt(int index) {
        IMultipleTankHandler.ITankEntry entry = fluidTanks.getTankAt(index);
        IFluidTank delegate = entry.getDelegate();
        if (delegate instanceof FluidTank) {
            return (FluidTank) delegate;
        }
        return null;
    }

    @Override
    public void update() {
        super.update();
        EnumFacing currentOutputFacing = getOutputFacing();
        if (!getWorld().isRemote) {
            fillContainerFromInternalTank();
            fillInternalTankFromFluidContainer();
            if (isAutoOutputFluids()) {
                pushFluidsIntoNearbyHandlers(currentOutputFacing);
            }

            // 检查每个流体槽的变化并同步
            for (int i = 0; i < TANK_COUNT; i++) {
                checkFluidSlotChanges(i);
            }
        }
    }

    // 检查单个流体槽的变化
    protected void checkFluidSlotChanges(int slotIndex) {
        FluidTank tank = getTankAt(slotIndex);
        if (tank == null) return;

        FluidStack currentFluid = tank.getFluid();
        FluidStack previousFluid = previousFluids[slotIndex];

        if (previousFluid == null) {
            if (currentFluid != null) {
                updatePreviousFluid(slotIndex, currentFluid);
            }
        } else {
            if (currentFluid == null) {
                updatePreviousFluid(slotIndex, null);
            } else if (previousFluid.getFluid().equals(currentFluid.getFluid()) &&
                    previousFluid.amount != currentFluid.amount) {
                int currentFill = MathHelper.floor(16 * ((float) currentFluid.amount) / tank.getCapacity());
                int previousFill = MathHelper.floor(16 * ((float) previousFluid.amount) / tank.getCapacity());
                if (currentFill != previousFill) {
                    previousFluids[slotIndex].amount = currentFluid.amount;
                    writeCustomData(UPDATE_FLUID_AMOUNT, buf -> {
                        buf.writeInt(slotIndex);
                        buf.writeInt(currentFluid.amount);
                        buf.writeBoolean(true);
                    });
                }
            } else if (!previousFluid.equals(currentFluid)) {
                updatePreviousFluid(slotIndex, currentFluid);
            }
        }
    }

    // should only be called on the server
    protected void updatePreviousFluid(int slotIndex, FluidStack currentFluid) {
        previousFluids[slotIndex] = currentFluid == null ? null : currentFluid.copy();
        writeCustomData(UPDATE_FLUID, buf -> {
            buf.writeInt(slotIndex);
            buf.writeCompoundTag(currentFluid == null ? null : currentFluid.writeToNBT(new NBTTagCompound()));
        });
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        // 保存4个流体槽的数据
        NBTTagList tankList = new NBTTagList();
        for (int i = 0; i < TANK_COUNT; i++) {
            FluidTank tank = getTankAt(i);
            if (tank != null) {
                NBTTagCompound tankTag = new NBTTagCompound();
                tank.writeToNBT(tankTag);
                tankList.appendTag(tankTag);
            }
        }
        data.setTag("FluidTanks", tankList);

        // 保存锁定流体
        if (locked) {
            NBTTagList lockedList = new NBTTagList();
            for (int i = 0; i < TANK_COUNT; i++) {
                NBTTagCompound lockTag = new NBTTagCompound();
                if (lockedFluids[i] != null) {
                    lockedFluids[i].writeToNBT(lockTag);
                }
                lockedList.appendTag(lockTag);
            }
            data.setTag("LockedFluids", lockedList);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);

        // 读取4个流体槽
        NBTTagList tankList = data.getTagList("FluidTanks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < Math.min(tankList.tagCount(), TANK_COUNT); i++) {
            FluidTank tank = getTankAt(i);
            if (tank != null) {
                tank.readFromNBT(tankList.getCompoundTagAt(i));
            }
        }

        // 读取锁定流体
        this.lockedFluids = new FluidStack[TANK_COUNT];
        if (data.hasKey("LockedFluids", Constants.NBT.TAG_LIST)) {
            NBTTagList lockedList = data.getTagList("LockedFluids", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < Math.min(lockedList.tagCount(), TANK_COUNT); i++) {
                NBTTagCompound lockTag = lockedList.getCompoundTagAt(i);
                if (!lockTag.isEmpty()) {
                    lockedFluids[i] = FluidStack.loadFluidStackFromNBT(lockTag);
                }
            }
        }
    }

    @Override
    public void initFromItemStackData(NBTTagCompound tag) {
        super.initFromItemStackData(tag);

        // 读取4个流体槽
        for (int i = 0; i < TANK_COUNT; i++) {
            String key = FLUID_NBT_KEY + "_" + i;
            if (tag.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
                FluidTank tank = getTankAt(i);
                if (tank != null) {
                    tank.setFluid(FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(key)));
                }
            }
        }

        if (tag.getBoolean("IsVoiding") || tag.getBoolean("IsPartialVoiding")) {
            setVoiding(true);
        }

        // 读取锁定流体
        this.lockedFluids = new FluidStack[TANK_COUNT];
        for (int i = 0; i < TANK_COUNT; i++) {
            String key = "LockedFluid_" + i;
            if (tag.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
                lockedFluids[i] = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(key));
            }
        }
        this.locked = true;
    }

    @Override
    public void writeItemStackData(NBTTagCompound tag) {
        super.writeItemStackData(tag);

        // 写入4个流体槽
        for (int i = 0; i < TANK_COUNT; i++) {
            FluidTank tank = getTankAt(i);
            if (tank != null) {
                FluidStack stack = tank.getFluid();
                if (stack != null && stack.amount > 0) {
                    tag.setTag(FLUID_NBT_KEY + "_" + i, stack.writeToNBT(new NBTTagCompound()));
                }
            }
        }

        if (this.voiding) {
            tag.setBoolean("IsVoiding", true);
        }

        // 写入锁定流体
        for (int i = 0; i < TANK_COUNT; i++) {
            if (this.lockedFluids[i] != null) {
                tag.setTag("LockedFluid_" + i, this.lockedFluids[i].writeToNBT(new NBTTagCompound()));
            }
        }
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityQuantumMultiTank(metaTileEntityId, tier, maxFluidCapacity);
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new FilteredItemHandler(this, 4).setFillPredicate(
                FilteredItemHandler.getCapabilityFilter(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY));
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new GTItemStackHandler(this, 4);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        Textures.QUANTUM_STORAGE_RENDERER.renderMachine(renderState, translation,
                ArrayUtils.add(pipeline,
                        new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(getPaintingColorForRendering()))),
                this);
        Textures.QUANTUM_TANK_OVERLAY.renderSided(EnumFacing.UP, renderState, translation, pipeline);
        if (outputFacing != null) {
            Textures.PIPE_OUT_OVERLAY.renderSided(outputFacing, renderState, translation, pipeline);
            if (isAutoOutputFluids()) {
                Textures.FLUID_OUTPUT_OVERLAY.renderSided(outputFacing, renderState, translation, pipeline);
            }
        }
        // 渲染4个流体的液位
        renderMultiTankFluids(renderState, translation, pipeline);
        renderIndicatorOverlay(renderState, translation, pipeline);
    }

    // 渲染4个流体槽的液位
    protected void renderMultiTankFluids(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        FluidTank[] tanks = new FluidTank[TANK_COUNT];
        for (int i = 0; i < TANK_COUNT; i++) {
            tanks[i] = getTankAt(i);
        }
        QuantumStorageRenderer.renderMultiTankFluids(renderState, translation, pipeline,
                tanks, getWorld(), getPos(), getFrontFacing());
    }

    @Override
    public void renderMetaTileEntity(double x, double y, double z, float partialTicks) {
        long[] amounts = new long[TANK_COUNT];
        for (int i = 0; i < TANK_COUNT; i++) {
            FluidTank tank = getTankAt(i);
            if (tank != null && tank.getFluid() != null) {
                amounts[i] = tank.getFluid().amount;
            }
        }
        QuantumStorageRenderer.renderMultiTankAmount(x, y, z, getFrontFacing(), amounts);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(Textures.VOLTAGE_CASINGS[tier].getParticleSprite(), getPaintingColorForRendering());
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.quantum_tank.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity_mult", 4, maxFluidCapacity));

        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            for (int i = 0; i < TANK_COUNT; i++) {
                String key = FLUID_NBT_KEY + "_" + i;
                if (tag.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
                    FluidStack fluidStack = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(key));
                    if (fluidStack != null && fluidStack.amount > 0) {
                        tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_stored", i + 1,
                                fluidStack.getLocalizedName(), fluidStack.amount));
                    }
                }
            }
            if (tag.getBoolean("IsVoiding")) {
                tooltip.add(I18n.format("gregtech.machine.quantum_tank.tooltip.voiding_enabled"));
            }
        }
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.auto_output_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        tooltip.add(I18n.format("gregtech.tool_action.soft_mallet.toggle_working"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        var panel = GTGuis.createPanel(this, 176, 166 + 2 * 18);
        createWidgets(panel, guiSyncManager);
        return panel.padding(4)
                .child(IKey.lang(getMetaFullName()).asWidget())
                .child(createQuantumIO(importItems, exportItems))
                .child(createQuantumButtonRow())
                .child(createConnectionButton()
                        .right(9)
                        .top(18 + 45 + 2 * 18))
                .child(SlotGroupWidget.playerInventory(false)
                        .left(7)
                        .bottom(7));
    }

    public ParentWidget<?> createQuantumIO(IItemHandlerModifiable importHandler,
                                           IItemHandlerModifiable exportHandler) {
        return Flow.row()
                .pos(79, 18 + 45 + 2 * 18)
                .coverChildren()
                .child(new ItemSlot()
                        .background(GTGuiTextures.SLOT, GTGuiTextures.IN_SLOT_OVERLAY)
                        .slot(SyncHandlers.itemSlot(importHandler, 0)
                                .accessibility(true, true)
                                .singletonSlotGroup(200))
                        .marginRight(18))
                .child(new ItemSlot()
                        .background(GTGuiTextures.SLOT, GTGuiTextures.OUT_SLOT_OVERLAY)
                        .slot(SyncHandlers.itemSlot(exportHandler, 0)
                                .accessibility(false, true)));
    }

    public Flow createQuantumButtonRow() {
        boolean isFluid = getType() == Type.FLUID;

        return Flow.row()
                .coverChildren()
                .pos(7, 63 + 2 * 18)
                // fluid
                .childIf(isFluid, () -> new ToggleButton()
                        .overlay(GTGuiTextures.BUTTON_FLUID_OUTPUT)
                        .addTooltip(true, IKey.lang("gregtech.gui.fluid_auto_output.tooltip.enabled"))
                        .addTooltip(false, IKey.lang("gregtech.gui.fluid_auto_output.tooltip.disabled"))
                        .value(new BooleanSyncValue(this::isAutoOutputFluids, this::setAutoOutput)))
                .childIf(isFluid, () -> new ToggleButton()
                        .overlay(GTGuiTextures.FLUID_LOCK_OVERLAY)
                        .addTooltip(true, IKey.lang("gregtech.gui.fluid_lock.tooltip.enabled"))
                        .addTooltip(false, IKey.lang("gregtech.gui.fluid_lock.tooltip.disabled"))
                        .value(new BooleanSyncValue(this::isLocked, this::setLocked)))
                .childIf(isFluid, () -> new ToggleButton()
                        .addTooltip(true, IKey.lang("gregtech.gui.fluid_voiding.tooltip.enabled"))
                        .addTooltip(false, IKey.lang("gregtech.gui.fluid_voiding.tooltip.disabled"))
                        .overlay(isFluid ? GTGuiTextures.FLUID_VOID_OVERLAY : GTGuiTextures.ITEM_VOID_OVERLAY)
                        .value(new BooleanSyncValue(this::isVoiding, this::setVoiding)))
                // item
                .childIf(!isFluid, () -> new ToggleButton()
                        .overlay(GTGuiTextures.BUTTON_ITEM_OUTPUT)
                        .addTooltip(true, IKey.lang("gregtech.gui.item_auto_output.tooltip.enabled"))
                        .addTooltip(false, IKey.lang("gregtech.gui.item_auto_output.tooltip.disabled"))
                        .value(new BooleanSyncValue(this::isAutoOutputItems, this::setAutoOutput)))
                .childIf(!isFluid, () -> new ToggleButton()
                        .overlay(GTGuiTextures.FLUID_LOCK_OVERLAY)
                        .addTooltip(true, IKey.lang("gregtech.gui.item_lock.tooltip.enabled"))
                        .addTooltip(false, IKey.lang("gregtech.gui.item_lock.tooltip.disabled"))
                        .value(new BooleanSyncValue(this::isLocked, this::setLocked)))
                .childIf(!isFluid, () -> new ToggleButton()
                        .addTooltip(true, IKey.lang("gregtech.gui.item_voiding.tooltip.enabled"))
                        .addTooltip(false, IKey.lang("gregtech.gui.item_voiding.tooltip.disabled"))
                        .overlay(isFluid ? GTGuiTextures.FLUID_VOID_OVERLAY : GTGuiTextures.ITEM_VOID_OVERLAY)
                        .value(new BooleanSyncValue(this::isVoiding, this::setVoiding)));
    }

    @Override
    protected void createWidgets(ModularPanel mainPanel, PanelSyncManager syncManager) {
        mainPanel.child(
                Flow.column()
                        .background(GTGuiTextures.DISPLAY)
                        .padding(4)
                        .height(46 + 2 * 18)
                        .top(16));

        for (int i = 0; i < TANK_COUNT; i++) {
            final int slotIndex = i;
            FluidTank tank = getTankAt(slotIndex);
            GTFluidSyncHandler fluidSyncHandler = GTFluidSlot.sync(tank);

            mainPanel.child(new ScrollingTextWidget(IKey.dynamic(() -> {
                FluidStack fluid = fluidSyncHandler.getFluid();
                if (fluid == null) {
                    return String.valueOf(IKey.str("空流体 0L"));
                } else {
                    String amount = TextFormattingUtil.formatNumbers(fluid.amount);
                    return String.valueOf(IKey.lang(fluidSyncHandler.getFluidLocalizedName() + " " + amount + "L"));
                }
            }))
                    .animator(new Animator().curve(Interpolation.SINE_INOUT))
                    .alignment(Alignment.CenterLeft)
                    .color(Color.WHITE.main)
                    .pos(10, 20 + i * 18)
                    .widthRel(0.75f)
                    .height(20)
                    .marginBottom(2));

            mainPanel.child(new GTFluidSlot()
                    .pos(148, 20 + i * 18)
                    .syncHandler(fluidSyncHandler
                            .accessibility(true, false)
                            .handleLocking(
                                    () -> lockedFluids[slotIndex],
                                    fluidStack -> {
                                        setSlotLocked(slotIndex, fluidStack != null);
                                        lockedFluids[slotIndex] = fluidStack;
                                    },
                                    locked -> setSlotLocked(slotIndex, locked),
                                    () -> isSlotLocked(slotIndex)

                            )
                            .showAmount(false, false)
                    ));
        }

    }

    protected void setSlotLocked(int slotIndex, boolean locked) {
        if (locked == isSlotLocked(slotIndex)) return;
        FluidTank tank = getTankAt(slotIndex);
        if (locked && tank != null && tank.getFluid() != null) {
            this.lockedFluids[slotIndex] = GTUtility.copy(1, tank.getFluid());
        } else {
            this.lockedFluids[slotIndex] = null;
        }
    }

    protected boolean isSlotLocked(int slotIndex) {
        return lockedFluids[slotIndex] != null;
    }

    @Override
    public void receiveCustomData(int dataId, @NotNull PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == UPDATE_FLUID) {
            try {
                int slotIndex = buf.readInt();
                FluidTank tank = getTankAt(slotIndex);
                if (tank != null) {
                    tank.setFluid(FluidStack.loadFluidStackFromNBT(buf.readCompoundTag()));
                }
                scheduleRenderUpdate();
            } catch (IOException ignored) {
                GTLog.logger.warn("Failed to load fluid from NBT in a quantum multi-tank at {}", this.getPos());
            }
        } else if (dataId == UPDATE_FLUID_AMOUNT) {
            int slotIndex = buf.readInt();
            int amount = buf.readInt();
            boolean updateRendering = buf.readBoolean();
            FluidTank tank = getTankAt(slotIndex);
            if (tank != null) {
                FluidStack stack = tank.getFluid();
                if (stack != null) {
                    stack.amount = Math.min(amount, tank.getCapacity());
                    if (updateRendering)
                        scheduleRenderUpdate();
                }
            }
        }
    }

    @Override
    public boolean isValidFrontFacing(EnumFacing facing) {
        return super.isValidFrontFacing(facing) && facing != outputFacing;
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        for (int i = 0; i < TANK_COUNT; i++) {
            FluidTank tank = getTankAt(i);
            FluidStack fluid = tank != null ? tank.getFluid() : null;
            NetworkUtils.writeFluidStack(buf, fluid);
            NetworkUtils.writeFluidStack(buf, this.lockedFluids[i]);
        }
    }

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        super.receiveInitialSyncData(buf);

        if (this.frontFacing == EnumFacing.UP) {
            if (this.outputFacing != EnumFacing.DOWN) {
                this.frontFacing = this.outputFacing.getOpposite();
            } else {
                this.frontFacing = EnumFacing.NORTH;
            }
        }

        this.lockedFluids = new FluidStack[TANK_COUNT];
        for (int i = 0; i < TANK_COUNT; i++) {
            FluidTank tank = getTankAt(i);
            if (tank != null) {
                tank.setFluid(NetworkUtils.readFluidStack(buf));
            }
            this.lockedFluids[i] = NetworkUtils.readFluidStack(buf);
        }
        this.locked = true;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == GregtechTileCapabilities.CAPABILITY_ACTIVE_OUTPUT_SIDE) {
            if (side == getOutputFacing()) {
                return GregtechTileCapabilities.CAPABILITY_ACTIVE_OUTPUT_SIDE.cast(this);
            }
            return null;
        } else if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            IFluidHandler fluidHandler = (side == getOutputFacing() && !isAllowInputFromOutputSideFluids()) ?
                    outputFluidInventory : fluidInventory;
            if (fluidHandler.getTankProperties().length > 0) {
                return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluidHandler);
            }
            return null;
        }
        return super.getCapability(capability, side);
    }

    @Override
    public ICapabilityProvider initItemStackCapabilities(ItemStack itemStack) {
        return new GTFluidHandlerItemStack(itemStack, maxFluidCapacity * TANK_COUNT);
    }

    @Override
    public boolean onWrenchClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                 CuboidRayTraceResult hitResult) {
        if (!playerIn.isSneaking()) {
            if (getOutputFacing() == facing || getFrontFacing() == facing) {
                return false;
            }
            if (!getWorld().isRemote) {
                setOutputFacing(facing);
            }
            return true;
        }
        return super.onWrenchClick(playerIn, hand, facing, hitResult);
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        EnumFacing hitFacing = CoverRayTracer.determineGridSideHit(hitResult);
        if (facing == getOutputFacing() || (hitFacing == getOutputFacing() && playerIn.isSneaking())) {
            if (!getWorld().isRemote) {
                if (isAllowInputFromOutputSideFluids()) {
                    setAllowInputFromOutputSide(false);
                    playerIn.sendStatusMessage(
                            new TextComponentTranslation("gregtech.machine.basic.input_from_output_side.disallow"),
                            true);
                } else {
                    setAllowInputFromOutputSide(true);
                    playerIn.sendStatusMessage(
                            new TextComponentTranslation("gregtech.machine.basic.input_from_output_side.allow"), true);
                }
            }
            return true;
        }
        return super.onScrewdriverClick(playerIn, hand, facing, hitResult);
    }

    @Override
    public ItemStack getPickItem(EntityPlayer player) {
        if (!player.isCreative()) return super.getPickItem(player);

        ItemStack baseItemStack = getStackForm();
        NBTTagCompound tag = new NBTTagCompound();
        this.writeItemStackData(tag);
        if (!tag.isEmpty()) {
            baseItemStack.setTagCompound(tag);
        }
        return baseItemStack;
    }

    @Override
    public boolean needsSneakToRotate() {
        return true;
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(getPos());
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public int getLightOpacity() {
        return 0;
    }

    public int getTankSize() {
        return maxFluidCapacity;
    }

    public int getTankCount() {
        return TANK_COUNT;
    }

    @Override
    public boolean onRightClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                CuboidRayTraceResult hitResult) {
        if (playerIn.getHeldItem(hand).hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
            return getWorld().isRemote ||
                    (!playerIn.isSneaking() && FluidUtil.interactWithFluidHandler(playerIn, hand, fluidTanks));
        }
        return super.onRightClick(playerIn, hand, facing, hitResult);
    }

    @Override
    public Type getType() {
        return Type.FLUID;
    }

    @Override
    public FluidTankList getTypeValue() {
        return fluidTanks;
    }

    private class QuantumFluidTank extends FluidTank implements IFilteredFluidContainer, IFilter<FluidStack> {

        private final int slotIndex;

        public QuantumFluidTank(int capacity, int slotIndex) {
            super(capacity);
            this.slotIndex = slotIndex;
        }

        @Override
        public int fillInternal(FluidStack resource, boolean doFill) {
            // 检查是否锁定且流体不匹配
            if (lockedFluids[slotIndex] != null && !resource.isFluidEqual(lockedFluids[slotIndex])) {
                return 0;
            }

            int accepted = super.fillInternal(resource, doFill);

            // 首次填充时自动锁定
            if (doFill && accepted > 0 && lockedFluids[slotIndex] == null) {
                lockedFluids[slotIndex] = resource.copy();
                lockedFluids[slotIndex].amount = 1;
            }

            return voiding ? resource.amount : accepted;
        }

        @Override
        public boolean canFillFluidType(FluidStack fluid) {
            return test(fluid);
        }

        @Override
        public IFilter<FluidStack> getFilter() {
            return this;
        }

        @Override
        public boolean test(@NotNull FluidStack fluidStack) {
            return lockedFluids[slotIndex] == null || fluidStack.isFluidEqual(lockedFluids[slotIndex]);
        }

        @Override
        public int getPriority() {
            return lockedFluids[slotIndex] == null ? IFilter.noPriority() : IFilter.whitelistPriority(1);
        }
    }
}
