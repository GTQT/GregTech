package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.capability.DualHandler;
import gregtech.api.capability.IDataStickIntractable;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.NotifiableFluidTank;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.sync.PagedWidgetSyncHandler;
import gregtech.api.mui.widget.GhostCircuitSlotWidget;
import gregtech.api.util.GTLog;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 样板映射区 — 有自己的样板槽位和 AE 网络连接，通过数据棒链接到样板总成 master。
 * <p>
 * 核心功能（移植自 Programmable-Hatches-Mod 的 PatternDualInputHatchInventoryMappingSlave）：
 * <ul>
 *   <li>拥有 36 个样板槽位，向 AE 网络注册自己的样板</li>
 *   <li>通过数据棒与一个 MetaTileEntityMEPatternProvider（master）建立连接</li>
 *   <li>AE 推送材料时，转发到 master 的缓冲区池</li>
 *   <li>isBusy() 委托给 master</li>
 * </ul>
 */
public class MetaTileEntityPatternProviderMappingSlave extends MetaTileEntityAECraftingPart {

    // ==================== 样板槽位 ====================
    private static final int DEFAULT_PATTERN_SLOT_COUNT = 36;

    // ==================== 与 master 的链接 ====================
    private MetaTileEntityMEPatternProvider master;
    private BlockPos masterPos;
    private boolean masterSet = false;
    private boolean checkForMaster = true;

    public MetaTileEntityPatternProviderMappingSlave(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier, false);
        patternDetails = new ArrayList<>(Collections.nCopies(getPatternSlotCount(), null));
        initializeInventory();
    }

    /**
     * 获取样板槽位数量，子类可 override 以增加容量。
     */
    protected int getPatternSlotCount() {
        return DEFAULT_PATTERN_SLOT_COUNT;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityPatternProviderMappingSlave(metaTileEntityId, getTier());
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        this.patternSlot = new ItemStackHandler(getPatternSlotCount()) {

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return stack.getItem() instanceof ICraftingPatternItem;
            }

            @Override
            protected void onContentsChanged(int slot) {
                setNeedPatternSync(true);
                setPatternDetails();
            }
        };

        // 映射区不需要自己的物品/流体槽，材料转发给 master
        this.circuitInventory = new GhostCircuitItemStackHandler(this);
        this.circuitInventory.addNotifiableMetaTileEntity(this);
        this.actualImportItems = new ItemHandlerList(
                java.util.Arrays.asList(this.importItems, this.circuitInventory));
        dualHandler = new DualHandler(this.actualImportItems, getImportFluids(), false);
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new NotifiableItemStackHandler(this, 1, null, false);
    }

    @Override
    protected FluidTankList createImportFluidHandler() {
        return new FluidTankList(false,
                new IFluidTank[] { new NotifiableFluidTank(8000, null, false) });
    }

    // ==================== Master 链接 ====================

    @Nullable
    private MetaTileEntityMEPatternProvider getMaster() {
        return master;
    }

    private boolean hasMaster() {
        return master != null && master.isValid();
    }

    private void tryToSetMaster() {
        if (getWorld() == null || masterPos == null) return;

        TileEntity tileEntity = getWorld().getTileEntity(masterPos);
        if (!(tileEntity instanceof IGregTechTileEntity iGregTechTileEntity)) {
            this.checkForMaster = true;
            return;
        }

        MetaTileEntity metaTileEntity = iGregTechTileEntity.getMetaTileEntity();
        if (!(metaTileEntity instanceof MetaTileEntityMEPatternProvider provider)) {
            this.checkForMaster = true;
            return;
        }

        this.master = provider;
        this.checkForMaster = false;
    }

    // ==================== 数据棒交互 ====================

    @Override
    public void onDataStickLeftClick(EntityPlayer player, ItemStack dataStick) {
        // 映射区不写数据棒，只读
    }

    @Override
    public boolean onDataStickRightClick(EntityPlayer player, ItemStack dataStick) {
        NBTTagCompound tag = dataStick.getTagCompound();
        if (tag == null || !tag.hasKey("BudgetCRIB")) return false;

        NBTTagCompound cribTag = tag.getCompoundTag("BudgetCRIB");
        this.masterPos = new BlockPos(
                cribTag.getInteger("MainX"),
                cribTag.getInteger("MainY"),
                cribTag.getInteger("MainZ"));
        this.masterSet = true;

        player.sendStatusMessage(new TextComponentTranslation(
                "gregtech.machine.pattern_mapping_slave.data_stick_use",
                TextFormattingUtil.formatNumbers(masterPos.getX()),
                TextFormattingUtil.formatNumbers(masterPos.getY()),
                TextFormattingUtil.formatNumbers(masterPos.getZ())), true);

        tryToSetMaster();
        return true;
    }

    // ==================== AE2 推送 — 转发给 master ====================

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting inventoryCrafting) {
        if (!isActive()) {
            return false;
        }
        if (!hasMaster()) {
            GTLog.logger.debug("Mapping slave has no master, rejecting pattern");
            return false;
        }
        // 将材料转发到 master 的缓冲区池
        return master.pushToBuffer(inventoryCrafting);
    }

    @Override
    public boolean isBusy() {
        if (!hasMaster()) return true;
        return master.isBusy();
    }

    // ==================== 不注册多方块能力 ====================

    @Override
    public @Nullable MultiblockAbility<IItemHandlerModifiable> getAbility() {
        return null;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        // 映射区不作为多方块的输入总线，不注册任何能力
    }

    // ==================== update ====================

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote) {
            if (isWorkingEnabled() && isOnline && shouldSyncME()) {
                if (isNeedPatternSync()) setNeedPatternSync(MEPatternChange());
            }

            if (getOffsetTimer() % 100 == 0) {
                if (checkForMaster && !hasMaster()) {
                    tryToSetMaster();
                }
            }
        }
    }

    // ==================== 渲染 ====================

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            SimpleOverlayRenderer overlay = Textures.ME_BUFFER_HATCH_PROXY_OVERLAY;
            overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    // ==================== NBT ====================

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("Pattern", this.patternSlot.serializeNBT());

        if (masterSet && masterPos != null) {
            data.setBoolean("HasMaster", true);
            data.setInteger("MasterX", masterPos.getX());
            data.setInteger("MasterY", masterPos.getY());
            data.setInteger("MasterZ", masterPos.getZ());
        } else {
            data.setBoolean("HasMaster", false);
        }

        if (this.circuitInventory != null) {
            this.circuitInventory.write(data);
        }

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.patternSlot.deserializeNBT(data.getCompoundTag("Pattern"));
        setPatternDetails();

        if (data.getBoolean("HasMaster")) {
            this.masterPos = new BlockPos(
                    data.getInteger("MasterX"),
                    data.getInteger("MasterY"),
                    data.getInteger("MasterZ"));
            this.masterSet = true;
            tryToSetMaster();
        }

        if (this.circuitInventory != null) {
            this.circuitInventory.read(data);
        }
    }

    @Override
    public void onRemoval() {
        removeFromGridCache();
        super.onRemoval();
        if (patternSlot != null) {
            gregtech.api.util.GTTransferUtils.dropInventoryItems(getWorld(), getPos(), patternSlot);
        }
    }

    // ==================== 同步 ====================

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);

        if (masterSet && masterPos != null) {
            buf.writeBoolean(true);
            buf.writeBlockPos(masterPos);
        } else {
            buf.writeBoolean(false);
        }
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);

        if (buf.readBoolean()) {
            masterPos = buf.readBlockPos();
            masterSet = true;
            tryToSetMaster();
        }
    }

    // ==================== GUI ====================

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        int rowSize = (int) Math.ceil(Math.sqrt(getPatternSlotCount()));
        guiSyncManager.registerSlotGroup("pattern_inv", rowSize);

        int backgroundWidth = Math.max(9 * 18 + 18 + 14 + 5 + 18, (rowSize + 1) * 18 + 14 + 18);
        int backgroundHeight = 18 + 18 * Math.max(4, rowSize) + 94;

        // 样板槽页面
        List<List<IWidget>> widgetsPattern = new ArrayList<>();
        for (int i = 0; i < rowSize; i++) {
            widgetsPattern.add(new ArrayList<>());
            for (int j = 0; j < rowSize; j++) {
                int index = i * rowSize + j;
                widgetsPattern.get(i)
                        .add(new ItemSlot()
                                .slot(SyncHandlers.itemSlot(patternSlot, index)
                                        .slotGroup("pattern_inv")
                                        .accessibility(true, true))
                                .background(GTGuiTextures.SLOT, GTGuiTextures.PATTERN_OVERLAY));
            }
        }

        var controller = new PagedWidget.Controller();
        guiSyncManager.syncValue("page_controller", new PagedWidgetSyncHandler(controller));

        return GTGuis.createPanel(this, backgroundWidth, backgroundHeight)
                .child(Flow.row()
                        .name("tab row")
                        .widthRel(1f)
                        .leftRel(0.5f)
                        .margin(3, 0)
                        .coverChildrenHeight()
                        .topRel(0f, 3, 1f)
                        .child(new PageButton(0, controller)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .addTooltipLine(IKey.lang("样板"))
                                .overlay(HATCH))
                        .child(new PageButton(1, controller)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .addTooltipLine(IKey.lang("状态"))
                                .overlay(TERMINAL))
                )
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7))
                .child(new PagedWidget<>()
                        .top(18)
                        .margin(0)
                        .widthRel(1f)
                        .controller(controller)
                        // 样板页面
                        .addPage(
                                new Grid()
                                        .top(0)
                                        .height(rowSize * 18)
                                        .minElementMargin(0, 0)
                                        .minColWidth(18)
                                        .minRowHeight(18)
                                        .leftRel(0.5f)
                                        .matrix(widgetsPattern))
                        // 状态页面
                        .addPage(
                                Flow.column()
                                        .top(0)
                                        .widthRel(1f)
                                        .leftRel(0.5f)
                                        .margin(5, 0)
                                        .child(new TextWidget<>(IKey.str("样板映射区")))
                                        .child(new TextWidget<>(IKey.dynamic(() -> {
                                            if (hasMaster() && masterPos != null) {
                                                return "已链接到: " + masterPos.getX() + ", " +
                                                        masterPos.getY() + ", " + masterPos.getZ();
                                            }
                                            return "未链接到样板总成";
                                        })))
                                        .child(new TextWidget<>(IKey.dynamic(() -> {
                                            if (hasMaster()) {
                                                int usedBuffers = 0;
                                                for (MetaTileEntityMEPatternProvider.PatternBuffer buffer :
                                                        master.getBufferPool()) {
                                                    if (!buffer.isEmpty()) usedBuffers++;
                                                }
                                                return "Master 缓冲区: " + usedBuffers + "/" +
                                                        MetaTileEntityMEPatternProvider.BUFFER_COUNT;
                                            }
                                            return "";
                                        })))
                                        .child(new TextWidget<>(IKey.str("使用数据棒右击样板总成写入坐标，再右击此方块进行链接")))
                        )
                )
                .child(Flow.column()
                        .pos(backgroundWidth - 7 - 18, backgroundHeight - 18 * 4 - 7 - 5)
                        .width(18).height(18 * 4 + 5)
                        .child(GTGuiTextures.getLogo(getUITheme()).asWidget()
                                .top(18 * 3 + 5)
                                .size(17))
                );
    }

    // ==================== Tooltip ====================

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.pattern_mapping_slave.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.pattern_mapping_slave.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.pattern_mapping_slave.tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.me.data_stick_proxy"));
    }
}
