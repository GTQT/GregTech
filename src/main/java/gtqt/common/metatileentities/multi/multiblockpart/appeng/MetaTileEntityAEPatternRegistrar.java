package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.capability.DualHandler;
import gregtech.api.capability.IDataStickIntractable;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.util.GTLog;
import gregtech.api.util.Mods;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityAEHostablePart;
import gregtech.integration.ae2.GTCircuitHelper;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.tile.grid.AENetworkPowerTile;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import gtqt.common.items.GTQTMetaItems;
import gtqt.common.items.behaviors.ProgrammableCircuit;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for AE2 pattern registrars that only register patterns and
 * forward pushed materials to a master PatternProvider. No local item/fluid storage.
 * <p>
 * Subclasses provide pattern generation logic via {@link #createPatterns()}.
 */
public abstract class MetaTileEntityAEPatternRegistrar extends MetaTileEntityAEHostablePart
        implements ICraftingProvider, IGridProxyable, IPowerChannelState, IDataStickIntractable {

    // UI icons for subclass GUI pages
    protected final IDrawable CHEST = new ItemDrawable(Blocks.CHEST)
            .asIcon().size(16);
    protected final IDrawable HATCH = new ItemDrawable(getStackForm())
            .asIcon().size(16);
    protected final IDrawable PROXY = new ItemDrawable(Mods.AppliedEnergistics2.getItem("interface"))
            .asIcon().size(16);
    protected final IDrawable FILTER = new ItemDrawable(Items.PAPER)
            .asIcon().size(16);

    @Nullable
    protected List<ICraftingPatternDetails> patternDetails;

    @Nullable
    protected GhostCircuitItemStackHandler circuitInventory;

    // Master connection
    @Nullable
    protected MetaTileEntityMEPatternProvider master;
    @Nullable
    protected BlockPos masterPos;
    protected boolean masterSet = false;
    protected boolean checkForMaster = true;

    // AE proxy mode
    @Getter
    @Setter
    protected boolean useProxy = false;
    protected BlockPos AEProxy_pos = new BlockPos(0, 0, 0);

    // Pattern sync flag
    @Getter
    @Setter
    protected boolean needPatternSync = true;

    // State flags used by subclass GUIs
    @Setter
    @Getter
    protected boolean blockedMode = true;

    @Setter
    @Getter
    protected boolean export = false;

    @Getter
    protected boolean autoCollapse;

    @Setter
    @Getter
    protected boolean advancedCircuit = false;

    // Slots used by subclass GUIs
    @Getter
    @Nullable
    protected DualHandler dualHandler;
    @Nullable
    protected ItemStackHandler extraItem;

    public MetaTileEntityAEPatternRegistrar(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier, false);
    }

    // ==================== Utility methods for subclass GUIs ====================

    public boolean hasGhostCircuitInventory() {
        return true;
    }

    public void setAutoCollapse(boolean value) {
        this.autoCollapse = value;
    }

    protected int getTankSize() {
        return (int) Math.sqrt(getInventorySize());
    }

    protected int getItemSize() {
        return getInventorySize();
    }

    protected int getTankCapacity() {
        return 8000 * (1 << Math.min(9, getTier()));
    }

    private int getInventorySize() {
        int sizeRoot = 1 + Math.min(9, getTier());
        return sizeRoot * sizeRoot;
    }

    // ==================== Master connection ====================

    protected void tryToSetMaster() {
        if (getWorld() == null || masterPos == null) {
            this.master = null;
            this.checkForMaster = true;
            return;
        }

        TileEntity tileEntity = getWorld().getTileEntity(masterPos);
        if (!(tileEntity instanceof IGregTechTileEntity iGregTechTileEntity)) {
            this.master = null;
            this.checkForMaster = true;
            return;
        }

        MetaTileEntity metaTileEntity = iGregTechTileEntity.getMetaTileEntity();
        if (metaTileEntity instanceof MetaTileEntityMEPatternProvider provider) {
            setMasterAndRegister(provider);
            return;
        }

        if (metaTileEntity instanceof MetaTileEntityMEPatternProviderProxy proxy) {
            MetaTileEntityMEPatternProvider resolvedMain = proxy.getResolvedMainForLink();
            if (resolvedMain != null) {
                setMasterAndRegister(resolvedMain);
                return;
            }
        }

        this.master = null;
        this.checkForMaster = true;
    }

    private void setMasterAndRegister(MetaTileEntityMEPatternProvider newMaster) {
        if (this.master != null && this.master != newMaster) {
            this.master.removeOrePrefixRegistrar(this);
        }
        this.master = newMaster;
        this.master.addOrePrefixRegistrar(this);
        this.checkForMaster = false;
    }

    public boolean hasMaster() {
        return master != null && master.isValid();
    }

    /**
     * Called by master when it is being removed from the world.
     */
    public void onMasterRemoved() {
        this.master = null;
        this.checkForMaster = true;
    }

    // ==================== AE2 ICraftingProvider ====================

    @Override
    public void provideCrafting(ICraftingProviderHelper helper) {
        setPatternDetails();
        if (!isActive() || patternDetails == null) return;
        for (ICraftingPatternDetails detail : patternDetails) {
            if (detail != null) {
                helper.addCraftingOption(this, detail);
            }
        }
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        if (!isActive() || !hasMaster()) {
            return false;
        }

        // Wrap non-consumable items (extraInput) as ProgrammableCircuit before forwarding
        wrapExtraInputsAsProgrammable(table);

        return master.pushToBuffer(table);
    }

    @Override
    public boolean isBusy() {
        if (!hasMaster()) return true;
        return master.isBusy();
    }

    /**
     * Wrap extraInput items (slots 1-8 in the InventoryCrafting) as ProgrammableCircuit
     * so the master's pushToBuffer() can route them to the circuit slot instead of item slots.
     * Slot 0 is the main input (consumable), slots 1+ are extraInput (non-consumable).
     */
    protected void wrapExtraInputsAsProgrammable(InventoryCrafting table) {
        for (int i = 1; i < table.getSizeInventory(); i++) {
            ItemStack stack = table.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            // Skip if already a ProgrammableCircuit
            if (ProgrammableCircuit.getInstanceFor(stack) != null) continue;

            ItemStack wrapped = wrapAsProgrammable(stack);
            if (wrapped != null) {
                table.setInventorySlotContents(i, wrapped);
            }
        }
    }

    @Nullable
    protected ItemStack wrapAsProgrammable(ItemStack source) {
        if (source.isEmpty() || GTQTMetaItems.PROGRAMMABLE_CIRCUIT == null) return null;
        ItemStack programmable = GTQTMetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(1);
        ItemStack wrappedItem = source.copy();
        wrappedItem.setCount(1);
        ProgrammableCircuit.wrap(wrappedItem, programmable);
        return programmable;
    }

    // ==================== Pattern generation (to be implemented by subclasses) ====================

    /**
     * Generate the list of virtual encoded patterns. Called by {@link #setPatternDetails()}.
     */
    protected abstract List<ItemStack> createPatterns();

    /**
     * Refresh pattern details from generated patterns.
     */
    public void setPatternDetails() {
        patternDetails = new ArrayList<>();
        List<ItemStack> patternSlot = createPatterns();
        for (int i = 0; i < patternSlot.size(); i++) {
            ItemStack pattern = patternSlot.get(i);
            if (pattern.isEmpty()) {
                patternDetails.add(i, null);
                continue;
            }
            if (pattern.getItem() instanceof ICraftingPatternItem patternItem) {
                patternDetails.add(i, patternItem.getPatternForItem(pattern, getWorld()));
            }
        }
    }

    // ==================== AE2 Grid integration ====================

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

    public boolean mePatternChange() {
        if (getProxy() == null || !getProxy().isActive()) return true;
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
    public IGridNode getGridNode(@NotNull AEPartLocation aePartLocation) {
        return getProxy().getNode();
    }

    @Override
    public void securityBreak() {}

    @Override
    public boolean isPowered() {
        return getProxy() != null && getProxy().isPowered();
    }

    @Override
    public boolean isActive() {
        return getProxy() != null && getProxy().isActive();
    }

    @Override
    public void gridChanged() {
        setNeedPatternSync(true);
    }

    // ==================== Lifecycle ====================

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote) {
            if (getOffsetTimer() % 20 == 0) {
                if (checkForMaster && !hasMaster()) {
                    tryToSetMaster();
                }
            }
            if (isWorkingEnabled() && isOnline && shouldSyncME()) {
                if (isNeedPatternSync()) {
                    setNeedPatternSync(mePatternChange());
                }
            }
        }
    }

    @Override
    public void onRemoval() {
        if (this.master != null) {
            this.master.removeOrePrefixRegistrar(this);
        }
        removeFromGridCache();
        super.onRemoval();
    }

    // ==================== Data Stick ====================

    @Override
    public void onDataStickLeftClick(EntityPlayer player, ItemStack dataStick) {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound cribTag = new NBTTagCompound();
        cribTag.setInteger("MainX", getPos().getX());
        cribTag.setInteger("MainY", getPos().getY());
        cribTag.setInteger("MainZ", getPos().getZ());
        tag.setTag("BudgetCRIB", cribTag);
        dataStick.setTagCompound(tag);
        dataStick.setTranslatableName("gregtech.machine.budget_crib.data_stick_name");
        player.sendStatusMessage(new TextComponentTranslation("gregtech.machine.budget_crib.data_stick_use"), true);
    }

    @Override
    public boolean onDataStickRightClick(EntityPlayer player, ItemStack dataStick) {
        NBTTagCompound tag = dataStick.getTagCompound();
        if (tag == null || !tag.hasKey("BudgetCRIB")) return false;

        NBTTagCompound cribTag = tag.getCompoundTag("BudgetCRIB");
        // Unregister from old master before switching
        if (this.master != null) {
            this.master.removeOrePrefixRegistrar(this);
        }
        this.masterPos = new BlockPos(
                cribTag.getInteger("MainX"),
                cribTag.getInteger("MainY"),
                cribTag.getInteger("MainZ"));
        this.masterSet = true;
        this.master = null;
        this.checkForMaster = true;

        player.sendStatusMessage(new TextComponentTranslation(
                "gregtech.machine.pattern_mapping_slave.data_stick_use",
                TextFormattingUtil.formatNumbers(masterPos.getX()),
                TextFormattingUtil.formatNumbers(masterPos.getY()),
                TextFormattingUtil.formatNumbers(masterPos.getZ())), true);

        tryToSetMaster();
        return true;
    }

    // ==================== NBT ====================

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("MasterSet", masterSet);
        if (masterPos != null) {
            data.setInteger("MasterX", masterPos.getX());
            data.setInteger("MasterY", masterPos.getY());
            data.setInteger("MasterZ", masterPos.getZ());
        }
        data.setBoolean("useProxy", useProxy);
        data.setInteger("aeProxy_x", AEProxy_pos.getX());
        data.setInteger("aeProxy_y", AEProxy_pos.getY());
        data.setInteger("aeProxy_z", AEProxy_pos.getZ());
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.masterSet = data.getBoolean("MasterSet");
        if (masterSet) {
            this.masterPos = new BlockPos(
                    data.getInteger("MasterX"),
                    data.getInteger("MasterY"),
                    data.getInteger("MasterZ"));
        }
        this.useProxy = data.getBoolean("useProxy");
        this.AEProxy_pos = new BlockPos(
                data.getInteger("aeProxy_x"),
                data.getInteger("aeProxy_y"),
                data.getInteger("aeProxy_z"));
    }

    // ==================== Sync ====================

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(masterSet);
        if (masterPos != null) {
            buf.writeBlockPos(masterPos);
        }
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.masterSet = buf.readBoolean();
        if (masterSet) {
            this.masterPos = buf.readBlockPos();
        }
    }
}
