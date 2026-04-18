package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.capability.DualHandler;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.IDataStickIntractable;
import gregtech.api.capability.IGhostSlotConfigurable;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gregtech.api.util.Mods;
import gregtech.common.items.MetaItems;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityAEHostablePart;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.config.Actionable;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.fluids.items.ItemFluidDrop;
import appeng.fluids.util.IAEFluidInventory;
import appeng.fluids.util.IAEFluidTank;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.tile.grid.AENetworkPowerTile;
import appeng.util.item.AEItemStack;
import codechicken.lib.raytracer.CuboidRayTraceResult;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static gregtech.api.capability.GregtechDataCodes.UPDATE_ACTIVE;
import static gtqt.api.util.AE2PatternCompat.getFluidStack;
import static gtqt.api.util.GTQTUtility.isFluidTankListEmpty;
import static gtqt.api.util.GTQTUtility.isInventoryEmpty;

public abstract class MetaTileEntityAECraftingPart extends MetaTileEntityAEHostablePart implements ICraftingProvider,
                                                                                                   IMultiblockAbilityPart<IItemHandlerModifiable>,
                                                                                                   IGhostSlotConfigurable,
                                                                                                   IAEFluidInventory,
                                                                                                   IDataStickIntractable,
                                                                                                   IGridProxyable,
                                                                                                   IPowerChannelState {

    // ICONS
    protected final IDrawable CHEST = new ItemDrawable(Blocks.CHEST)
            .asIcon().size(16);
    protected final IDrawable HATCH = new ItemDrawable(getStackForm())
            .asIcon().size(16);
    protected final IDrawable PROXY = new ItemDrawable(Mods.AppliedEnergistics2.getItem("interface"))
            .asIcon().size(16);
    protected final IDrawable TERMINAL = new ItemDrawable(Items.NAME_TAG)
            .asIcon().size(16);
    protected final IDrawable FILTER = new ItemDrawable(Items.PAPER)
            .asIcon().size(16);

    @Nullable
    protected List<ICraftingPatternDetails> patternDetails;

    @Nullable
    protected GhostCircuitItemStackHandler circuitInventory;

    // AE
    protected BlockPos AEProxy_pos = new BlockPos(0, 0, 0);

    @Setter
    @Getter
    protected boolean useProxy;

    @Setter
    @Getter
    protected boolean export = false;

    @Setter
    @Getter
    protected boolean needPatternSync = true;

    @Getter
    protected boolean autoCollapse;

    @Setter
    @Getter
    protected boolean blockedMode = true;

    @Setter
    @Getter
    protected boolean advancedCircuit = false;
    // SLOTS
    @Getter
    protected IItemHandlerModifiable actualImportItems;
    @Nullable
    protected ItemStackHandler extraItem;
    @Getter
    @Nullable
    protected ItemStackHandler patternSlot;
    @Getter
    @Nullable
    protected DualHandler dualHandler;

    @Getter
    @Setter
    protected String showName = this.getMetaFullName();
    @Getter
    @Setter
    protected boolean hideInfo = false;

    public MetaTileEntityAECraftingPart(ResourceLocation metaTileEntityId, int tier, boolean isExportHatch) {
        super(metaTileEntityId, tier, isExportHatch);
    }

    public void pushToGridCache() {
        try {
            if (getProxy() != null) {
                getProxy().getGrid().getCache(ICraftingGrid.class).addNode(getProxy().getNode(), this);
            }
        } catch (GridAccessException ignored) {}
    }

    public void removeFromGridCache() {
        try {
            if (getProxy() != null) {
                getProxy().getGrid().getCache(ICraftingGrid.class).removeNode(getProxy().getNode(), this);
            }
        } catch (GridAccessException ignored) {}
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(isBlockedMode());
        buf.writeBoolean(this.export);
        buf.writeBoolean(isAutoCollapse());
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        setBlockedMode(buf.readBoolean());
        setExport(buf.readBoolean());
        setAutoCollapse(buf.readBoolean());
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == UPDATE_ACTIVE) {
            setBlockedMode(buf.readBoolean());
        }
    }

    @Override
    public boolean hasGhostCircuitInventory() {
        return true;
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
    public @Nullable MultiblockAbility<IItemHandlerModifiable> getAbility() {
        return MultiblockAbility.IMPORT_ITEMS;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(dualHandler);
    }

    public void returnToNet() {
        Utils.returnItems(getItemMonitor(), getImportItems(), getActionSource());
        Utils.returnFluids(getFluidMonitor(), getImportFluids(), getActionSource());
    }

    public boolean MEPatternChange() {
        // don't post until it's active
        if (getProxy() == null || !getProxy().isActive()) return true;

        // remove from grid cache
        pushToGridCache();

        try {
            getProxy().getGrid().postEvent(new MENetworkCraftingPatternChange(this, getProxy().getNode()));
        } catch (Exception ignored) {
            return true;
        }

        return false;
    }

    @Override
    public AENetworkProxy getProxy() {
        if (isUseProxy()) {
            if (this.getWorld() != null) {
                TileEntity tileEntity = this.getWorld().getTileEntity(AEProxy_pos);
                if (tileEntity instanceof AENetworkPowerTile proxy) {
                    return proxy.getProxy();
                }
            }
        }
        return super.getProxy();
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(getWorld(), getPos());
    }

    @Override
    public void provideCrafting(ICraftingProviderHelper iCraftingProviderHelper) {
        setPatternDetails();
        if (!isActive() || patternDetails == null) return;
        for (ICraftingPatternDetails patternDetail : patternDetails) {
            if (patternDetail != null) iCraftingProviderHelper.addCraftingOption(this, patternDetail);
        }
    }

    /**
     * 获取样板槽位数量，子类应覆盖以提供实际的槽位数量。
     *
     * @return 样板槽位数量，默认返回 0
     */
    protected int getPatternSlotCount() {
        return 0;
    }

    /**
     * 设置样板详情。遍历所有样板槽位，将有效的样板物品转换为样板详情。
     * 子类只需覆盖 {@link #getPatternSlotCount()} 提供正确的槽位数量即可复用此方法。
     * 如果子类有不同的样板生成逻辑，可以直接覆盖本方法。
     */
    public void setPatternDetails() {
        if (patternSlot == null || patternDetails == null) {
            return;
        }

        int slotCount = getPatternSlotCount();
        for (int i = 0; i < slotCount; i++) {
            ItemStack pattern = patternSlot.getStackInSlot(i);
            if (pattern == ItemStack.EMPTY) {
                patternDetails.set(i, null);
                continue;
            }

            if (pattern.getItem() instanceof ICraftingPatternItem patternItem) {
                patternDetails.set(i, patternItem.getPatternForItem(pattern, getWorld()));
            }
        }
    }

    public boolean addItemAndFluid(InventoryCrafting inventoryCrafting) throws GridAccessException {
        // 第一阶段：模拟检查所有物品是否可插入
        for (int i = 0; i < inventoryCrafting.getSizeInventory(); ++i) {
            //实际插入的物品
            ItemStack itemStack = inventoryCrafting.getStackInSlot(i);
            if (itemStack.isEmpty()) continue;

            //处理流体

            // 处理假流体/气体物品
            if (ItemFluidDrop.isFluidDrop(itemStack)) {
                FluidStack fluid = getFluidStack(itemStack);
                if (fluid != null) {
                    if (getImportFluids().fill(fluid, false) < fluid.amount) {
                        return false;
                    }
                    continue;
                }
            }

            //处理物品

            // 处理集成电路 - 模拟阶段
            if (isAdvancedCircuit() && isOnline && MetaItems.INTEGRATED_CIRCUIT.isItemEqual(itemStack)) {
                IAEItemStack aeStack = AEItemStack.fromItemStack(itemStack);
                if (aeStack != null) {
                    // 模拟注入网络，检查是否可返还
                    IAEItemStack remaining = getItemMonitor().injectItems(aeStack, Actionable.SIMULATE,
                            getActionSource());
                    //大于0代表无法返回网络（可能是网络满了）
                    if (remaining != null && remaining.getStackSize() > 0) {
                        return false;
                    }
                }
                continue; // 跳过容器插入检查
            }

            //普通物品模拟插入检查
            //样板转化会ItemStack
            ItemStack simulated = itemStack.copy();
            //如果开了自动整理
            if (isAutoCollapse()) {
                //轮插 simulated轮询所有槽位，直到装填完毕
                for (int slot = 0; slot < importItems.getSlots() && !simulated.isEmpty(); slot++) {
                    ItemStack remaining = importItems.insertItem(slot, simulated, true);
                    if (remaining.getCount() < simulated.getCount()) {
                        simulated.shrink(simulated.getCount() - remaining.getCount());
                    }
                }
            } else {
                //simulated只会去填充空槽，用于装配线非64自动化
                //如果没有空槽再去轮询

                // 步骤1: 先尝试填充空槽
                for (int slot = 0; slot < importItems.getSlots() && !simulated.isEmpty(); slot++) {
                    // 检查是否是空槽
                    if (importItems.getStackInSlot(slot).isEmpty()) {
                        ItemStack remaining = importItems.insertItem(slot, simulated, true);
                        if (remaining.getCount() < simulated.getCount()) {
                            simulated.shrink(simulated.getCount() - remaining.getCount());
                        }
                    }
                }

                // 步骤2: 如果没有空槽或者空槽装不完，再轮询所有槽位
                if (!simulated.isEmpty()) {
                    for (int slot = 0; slot < importItems.getSlots() && !simulated.isEmpty(); slot++) {
                        ItemStack remaining = importItems.insertItem(slot, simulated, true);
                        if (remaining.getCount() < simulated.getCount()) {
                            simulated.shrink(simulated.getCount() - remaining.getCount());
                        }
                    }
                }
            }

            if (!simulated.isEmpty()) {
                return false;
            }
        }

        // 第二阶段：实际执行插入操作
        for (int i = 0; i < inventoryCrafting.getSizeInventory(); ++i) {
            ItemStack itemStack = inventoryCrafting.getStackInSlot(i);
            if (itemStack.isEmpty()) continue;

            // 处理假流体/气体物品
            if (ItemFluidDrop.isFluidDrop(itemStack)) {
                FluidStack fluid = getFluidStack(itemStack);
                if (fluid != null) {
                    getImportFluids().fill(fluid, true);
                    continue;
                }
            }

            // 处理集成电路 - 实际执行阶段
            if (isAdvancedCircuit() && isOnline && MetaItems.INTEGRATED_CIRCUIT.isItemEqual(itemStack)) {
                IMEMonitor<IAEItemStack> monitor = getItemMonitor();
                IAEItemStack aeStack = AEItemStack.fromItemStack(itemStack);
                if (aeStack != null) {
                    // 实际注入网络返还物品
                    monitor.injectItems(aeStack, Actionable.MODULATE, getActionSource());
                    // 设置机器电路配置
                    this.setGhostCircuitConfig(IntCircuitIngredient.getCircuitConfiguration(itemStack));
                }
                continue; // 跳过容器插入
            }

            // 普通物品实际插入 - 根据是否自动整理采用不同策略
            ItemStack toInsert = itemStack.copy();

            if (isAutoCollapse()) {
                // 自动整理模式：轮询所有槽位
                for (int slot = 0; slot < importItems.getSlots() && !toInsert.isEmpty(); slot++) {
                    toInsert = importItems.insertItem(slot, toInsert, false);
                }
            } else {
                // 非自动整理模式：先尝试空槽，再尝试所有槽位

                // 阶段1: 只填充空槽
                for (int slot = 0; slot < importItems.getSlots() && !toInsert.isEmpty(); slot++) {
                    if (importItems.getStackInSlot(slot).isEmpty()) {
                        toInsert = importItems.insertItem(slot, toInsert, false);
                    }
                }

                // 阶段2: 如果还有剩余，再尝试所有槽位
                if (!toInsert.isEmpty()) {
                    for (int slot = 0; slot < importItems.getSlots() && !toInsert.isEmpty(); slot++) {
                        toInsert = importItems.insertItem(slot, toInsert, false);
                    }
                }
            }
        }

        return true;
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting inventoryCrafting) {
        if (!isActive()) {
            GTLog.logger.debug("Machine is not active, rejecting pattern");
            return false;
        }

        boolean isEmpty = isInventoryEmpty(getImportItems()) && isFluidTankListEmpty(getImportFluids());

        // 如果不是空容器且处于阻塞模式，进行兼容性检查
        if (!isEmpty && isBlockedMode()) {
            if (!checkBlockedModeCompatibility(inventoryCrafting)) {
                GTLog.logger.debug("Pattern rejected by blocked mode compatibility check");
                return false;
            }
        }

        try {
            return addItemAndFluid(inventoryCrafting);
        } catch (GridAccessException e) {
            GTLog.logger.warn("Grid access failed while pushing pattern", e);
            return false;
        }
    }

    protected boolean checkBlockedModeCompatibility(InventoryCrafting inventoryCrafting) {
        for (int i = 0; i < inventoryCrafting.getSizeInventory(); ++i) {
            ItemStack itemStack = inventoryCrafting.getStackInSlot(i);
            if (itemStack.isEmpty()) continue;

            // 集成电路特殊处理
            if (MetaItems.INTEGRATED_CIRCUIT.isItemEqual(itemStack)) {
                //说明仓不空，应该检查电路是否相等
                if (IntCircuitIngredient.getCircuitConfiguration(itemStack) != getGhostCircuitConfig())
                    return false;
                continue;
            }

            // 处理流体假物品
            if (ItemFluidDrop.isFluidDrop(itemStack)) {
                FluidStack fluid = getFluidStack(itemStack);
                if (fluid == null || !GTUtility.hasMatchingFluid(fluid, getImportFluids())) {
                    return false;
                }
            }
            // 处理普通物品
            else {
                if (!GTUtility.hasMatchingItem(itemStack, importItems)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        setAutoCollapse(!this.autoCollapse);

        if (!getWorld().isRemote) {
            if (this.autoCollapse) {
                playerIn.sendStatusMessage(new TextComponentTranslation("gregtech.bus.collapse_true"), true);
            } else {
                playerIn.sendStatusMessage(new TextComponentTranslation("gregtech.bus.collapse_false"), true);
            }
        }
        return true;
    }

    public void setAutoCollapse(boolean inverted) {
        autoCollapse = inverted;
        if (!getWorld().isRemote) {
            if (autoCollapse) {
                addNotifiedInput(super.getImportItems());
                addNotifiedInput(this.getImportFluids());
            }
            writeCustomData(GregtechDataCodes.TOGGLE_COLLAPSE_ITEMS,
                    packetBuffer -> packetBuffer.writeBoolean(autoCollapse));
            notifyBlockUpdate();
            markDirty();
        }
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.auto_collapse"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }

    @Override
    public void gridChanged() {
        setNeedPatternSync(true);
    }

    @Override
    public boolean isPowered() {
        return getProxy() != null && getProxy().isPowered();
    }

    @Override
    public boolean isActive() {
        return getProxy() != null && getProxy().isActive();
    }

    @Override
    public boolean isBusy() {
        return isExport();
    }

    @Override
    public void onFluidInventoryChanged(IAEFluidTank iaeFluidTank, int i) {
        markDirty();
    }

    @Override
    public void onDataStickLeftClick(EntityPlayer player, ItemStack dataStick) {
        NBTTagCompound tag = new NBTTagCompound();

        tag.setTag("BudgetCRIB", writeLocationToTag());
        dataStick.setTagCompound(tag);
        dataStick.setTranslatableName("gregtech.machine.budget_crib.data_stick_name");
        player.sendStatusMessage(new TextComponentTranslation("gregtech.machine.budget_crib.data_stick_use"), true);
    }

    private NBTTagCompound writeLocationToTag() {
        NBTTagCompound tag = new NBTTagCompound();

        tag.setInteger("MainX", getPos().getX());
        tag.setInteger("MainY", getPos().getY());
        tag.setInteger("MainZ", getPos().getZ());

        return tag;
    }

    @Override
    public boolean onDataStickRightClick(EntityPlayer player, ItemStack dataStick) {
        NBTTagCompound tag = dataStick.getTagCompound();
        if (tag == null) return false;
        if (tag.hasKey("CommonPos")) {
            useProxy = false;
            readLocationFromTag(tag.getCompoundTag("CommonPos"));
            player.sendStatusMessage(new TextComponentTranslation("无线接入点坐标已载入"), true);
            useProxy = true;
            return true;
        }
        return false;
    }

    private void readLocationFromTag(NBTTagCompound tag) {
        AEProxy_pos = new BlockPos(tag.getInteger("MainX"), tag.getInteger("MainY"), tag.getInteger("MainZ"));
    }

    @Override
    public IGridNode getGridNode(@NotNull AEPartLocation aePartLocation) {
        return getProxy().getNode();
    }

    @Override
    public void securityBreak() {

    }
}
