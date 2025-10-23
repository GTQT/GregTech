package gtqt.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.DualHandler;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IDataStickIntractable;
import gregtech.api.capability.IGhostSlotConfigurable;
import gregtech.api.capability.INotifiableHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.GhostCircuitSlotWidget;
import gregtech.api.gui.widgets.ImageWidget;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.gui.widget.appeng.AEFluidConfigWidget;
import gregtech.common.gui.widget.appeng.AEItemConfigWidget;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEFluidList;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEFluidSlot;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEItemList;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEItemSlot;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedFluidStack;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedItemStack;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import gtqt.common.metatileentities.GTQTMetaTileEntities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class MetaTileEntityMEDualInputHatch extends MetaTileEntityMEControlBase
        implements IMultiblockAbilityPart<DualHandler>, IControllable, IGhostSlotConfigurable, IDataStickIntractable {

    public final static String ITEM_BUFFER_TAG = "ItemSlots";
    public final static String FLUID_BUFFER_TAG = "FluidTanks";

    private final static int ITEM_CONFIG_SIZE = 16;
    private final static int FLUID_CONFIG_SIZE = 16;

    protected ExportOnlyAEFluidList aeFluidHandler;
    protected ExportOnlyAEItemList aeItemHandler;

    protected GhostCircuitItemStackHandler circuitInventory;
    protected NotifiableItemStackHandler extraSlotInventory;

    private ItemHandlerList actualImportItems;

    public MetaTileEntityMEDualInputHatch(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, 5, false);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityMEDualInputHatch(this.metaTileEntityId);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);

        NBTTagList slots = new NBTTagList();
        for (int i = 0; i < ITEM_CONFIG_SIZE; i++) {
            ExportOnlyAEItemSlot slot = this.getAEItemHandler().getInventory()[i];
            NBTTagCompound slotTag = new NBTTagCompound();
            slotTag.setInteger("slot", i);
            slotTag.setTag("stack", slot.serializeNBT());
            slots.appendTag(slotTag);
        }
        data.setTag(ITEM_BUFFER_TAG, slots);
        this.circuitInventory.write(data);
        // Extra slot inventory
        GTUtility.writeItems(this.extraSlotInventory, "ExtraInventory", data);

        NBTTagList tanks = new NBTTagList();
        for (int i = 0; i < FLUID_CONFIG_SIZE; i++) {
            ExportOnlyAEFluidSlot tank = this.getAEFluidHandler().getInventory()[i];
            NBTTagCompound tankTag = new NBTTagCompound();
            tankTag.setInteger("slot", i);
            tankTag.setTag("tank", tank.serializeNBT());
            tanks.appendTag(tankTag);
        }
        data.setTag(FLUID_BUFFER_TAG, tanks);

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);

        if (data.hasKey(ITEM_BUFFER_TAG, 9)) {
            NBTTagList slots = (NBTTagList) data.getTag(ITEM_BUFFER_TAG);
            for (NBTBase nbtBase : slots) {
                NBTTagCompound slotTag = (NBTTagCompound) nbtBase;
                ExportOnlyAEItemSlot slot = this.getAEItemHandler().getInventory()[slotTag.getInteger("slot")];
                slot.deserializeNBT(slotTag.getCompoundTag("stack"));
            }
        }
        this.circuitInventory.read(data);
        GTUtility.readItems(this.extraSlotInventory, "ExtraInventory", data);
        this.importItems = createImportItemHandler();

        if (data.hasKey(FLUID_BUFFER_TAG, 9)) {
            NBTTagList tanks = (NBTTagList) data.getTag(FLUID_BUFFER_TAG);
            for (NBTBase nbtBase : tanks) {
                NBTTagCompound tankTag = (NBTTagCompound) nbtBase;
                ExportOnlyAEFluidSlot tank = this.getAEFluidHandler().getInventory()[tankTag.getInteger("slot")];
                tank.deserializeNBT(tankTag.getCompoundTag("tank"));
            }
        }
    }

    protected ExportOnlyAEItemList getAEItemHandler() {
        if (aeItemHandler == null) {
            aeItemHandler = new ExportOnlyAEItemList(this, ITEM_CONFIG_SIZE, this.getController());
        }
        return aeItemHandler;
    }

    protected ExportOnlyAEFluidList getAEFluidHandler() {
        if (aeFluidHandler == null) {
            aeFluidHandler = new ExportOnlyAEFluidList(this, FLUID_CONFIG_SIZE, this.getController());
        }
        return aeFluidHandler;
    }

    @Override
    protected void initializeInventory() {
        getAEFluidHandler(); // initialize it
        super.initializeInventory();
        this.aeItemHandler = getAEItemHandler();
        this.circuitInventory = new GhostCircuitItemStackHandler(this);
        this.circuitInventory.addNotifiableMetaTileEntity(this);
        this.extraSlotInventory = new NotifiableItemStackHandler(this, 2, this, false);
        this.extraSlotInventory.addNotifiableMetaTileEntity(this);
        this.actualImportItems = new ItemHandlerList(
                Arrays.asList(this.aeItemHandler, this.circuitInventory, this.extraSlotInventory));
        this.importItems = this.actualImportItems;
    }

    public IItemHandlerModifiable getImportItems() {
        return this.actualImportItems;
    }

    @Override
    protected FluidTankList createImportFluidHandler() {
        return new FluidTankList(false, getAEFluidHandler().getInventory());
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote && this.isWorkingEnabled() && updateMEStatus() && shouldSyncME()) {
            syncItemME();
            syncFluidME();
        }
    }

    protected void syncItemME() {
        IMEMonitor<IAEItemStack> monitor = getItemMonitor();
        if (monitor == null) return;

        for (ExportOnlyAEItemSlot aeSlot : this.getAEItemHandler().getInventory()) {
            // Try to clear the wrong item
            IAEItemStack exceedItem = aeSlot.exceedStack();
            if (exceedItem != null) {
                long total = exceedItem.getStackSize();
                IAEItemStack notInserted = monitor.injectItems(exceedItem, Actionable.MODULATE, this.getActionSource());
                if (notInserted != null && notInserted.getStackSize() > 0) {
                    aeSlot.extractItem(0, (int) (total - notInserted.getStackSize()), false);
                    continue;
                } else {
                    aeSlot.extractItem(0, (int) total, false);
                }
            }
            // Fill it
            IAEItemStack reqItem = aeSlot.requestStack();
            if (reqItem != null) {
                IAEItemStack extracted = monitor.extractItems(reqItem, Actionable.MODULATE, this.getActionSource());
                if (extracted != null) {
                    aeSlot.addStack(extracted);
                }
            }
        }
    }

    protected void syncFluidME() {
        IMEMonitor<IAEFluidStack> monitor = getFluidMonitor();
        if (monitor == null) return;

        for (ExportOnlyAEFluidSlot aeTank : this.getAEFluidHandler().getInventory()) {
            // Try to clear the wrong fluid
            IAEFluidStack exceedFluid = aeTank.exceedStack();
            if (exceedFluid != null) {
                long total = exceedFluid.getStackSize();
                IAEFluidStack notInserted = monitor.injectItems(exceedFluid, Actionable.MODULATE,
                        this.getActionSource());
                if (notInserted != null && notInserted.getStackSize() > 0) {
                    aeTank.drain((int) (total - notInserted.getStackSize()), true);
                    continue;
                } else {
                    aeTank.drain((int) total, true);
                }
            }
            // Fill it
            IAEFluidStack reqFluid = aeTank.requestStack();
            if (reqFluid != null) {
                IAEFluidStack extracted = monitor.extractItems(reqFluid, Actionable.MODULATE, this.getActionSource());
                if (extracted != null) {
                    aeTank.addStack(extracted);
                }
            }
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        flushItemInventory();
        flushFluidInventory();
    }

    protected void flushItemInventory() {
        IMEMonitor<IAEItemStack> monitor = getItemMonitor();
        if (monitor == null) return;

        for (ExportOnlyAEItemSlot aeSlot : this.getAEItemHandler().getInventory()) {
            IAEItemStack stock = aeSlot.getStock();
            if (stock instanceof WrappedItemStack) {
                stock = ((WrappedItemStack) stock).getAEStack();
            }
            if (stock != null) {
                monitor.injectItems(stock, Actionable.MODULATE, this.getActionSource());
            }
        }
    }

    protected void flushFluidInventory() {
        IMEMonitor<IAEFluidStack> monitor = getFluidMonitor();
        if (monitor == null) return;

        for (ExportOnlyAEFluidSlot aeTank : this.getAEFluidHandler().getInventory()) {
            IAEFluidStack stock = aeTank.getStock();
            if (stock instanceof WrappedFluidStack) {
                stock = ((WrappedFluidStack) stock).getAEStack();
            }
            if (stock != null) {
                monitor.injectItems(stock, Actionable.MODULATE, this.getActionSource());
            }
        }
    }

    @Override
    public void addToMultiBlock(MultiblockControllerBase controllerBase) {
        super.addToMultiBlock(controllerBase);
        for (IItemHandler handler : this.actualImportItems.getBackingHandlers()) {
            if (handler instanceof INotifiableHandler notifiable) {
                notifiable.addNotifiableMetaTileEntity(controllerBase);
                notifiable.addToNotifiedList(this, handler, false);
            }
        }
    }

    @Override
    public void removeFromMultiBlock(MultiblockControllerBase controllerBase) {
        super.removeFromMultiBlock(controllerBase);
        for (IItemHandler handler : this.actualImportItems.getBackingHandlers()) {
            if (handler instanceof INotifiableHandler notifiable) {
                notifiable.removeNotifiableMetaTileEntity(controllerBase);
            }
        }
    }

    /// ///////////////////////////////////////////
    @Override
    protected final ModularUI createUI(EntityPlayer player) {
        ModularUI.Builder builder = createUITemplate(player);
        return builder.build(this.getHolder(), player);
    }

    protected ModularUI.Builder createUITemplate(EntityPlayer player) {
        ModularUI.Builder builder = ModularUI
                .builder(GuiTextures.BACKGROUND, 176, 18 + 18 * 8 + 94)
                .label(10, 5, getMetaFullName());
        // ME Network status
        builder.dynamicLabel(10, 15, () -> this.isOnline ?
                        I18n.format("gregtech.gui.me_network.online") :
                        I18n.format("gregtech.gui.me_network.offline"),
                0x404040);

        /// ///////////////////////////////////////////////////////////////////////////////
        // Config slots
        builder.widget(new AEItemConfigWidget(7, 25, this.getAEItemHandler()));

        // Ghost circuit slot
        SlotWidget circuitSlot = new GhostCircuitSlotWidget(circuitInventory, 0, 7 + 18 * 4, 25 + 18 * 3)
                .setBackgroundTexture(GuiTextures.SLOT, GuiTextures.INT_CIRCUIT_OVERLAY);
        builder.widget(circuitSlot.setConsumer(w -> {
            String configString;
            if (circuitInventory == null ||
                    circuitInventory.getCircuitValue() == GhostCircuitItemStackHandler.NO_CONFIG) {
                configString = new TextComponentTranslation("gregtech.gui.configurator_slot.no_value")
                        .getFormattedText();
            } else {
                configString = String.valueOf(circuitInventory.getCircuitValue());
            }

            w.setTooltipText("gregtech.gui.configurator_slot.tooltip", configString);
        }));

        // Extra slot
        builder.widget(new SlotWidget(extraSlotInventory, 0, 7 + 18 * 4, 25 + 18 * 2)
                .setBackgroundTexture(GuiTextures.SLOT)
                .setTooltipText("gregtech.gui.me_bus.extra_slot"));
        builder.widget(new SlotWidget(extraSlotInventory, 1, 7 + 18 * 4, 25 + 18 * 4)
                .setBackgroundTexture(GuiTextures.SLOT)
                .setTooltipText("gregtech.gui.me_bus.extra_slot"));
        // Arrow image
        builder.image(7 + 18 * 4, 25 + 18, 18, 18, GuiTextures.ARROW_DOUBLE);

        /// ///////////////////////////////////////////////////////////////////////////////
        // Config slots
        builder.widget(new AEFluidConfigWidget(7, 25+ 18 * 4, this.getAEFluidHandler()));

        // Arrow image
        builder.image(7 + 18 * 4, 25 + 18 + 18 * 4, 18, 18, GuiTextures.ARROW_DOUBLE);

        // GT Logo, cause there's some free real estate
        builder.widget(new ImageWidget(7 + 18 * 4, 25 + 18 * 3 + 18 * 4, 17, 17,
                GTValues.XMAS.get() ? GuiTextures.GREGTECH_LOGO_XMAS : GuiTextures.GREGTECH_LOGO)
                .setIgnoreColor(true));

        /// ///////////////////////////////////////////////////////////////////////////////

        builder.bindPlayerInventory(player.inventory, GuiTextures.SLOT, 7, 18 + 18 * 8 + 12);
        return builder;
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (this.shouldRenderOverlay()) {
            if (isOnline) {
                Textures.ME_INPUT_BUS_ACTIVE.renderSided(getFrontFacing(), renderState, translation, pipeline);
            } else {
                Textures.ME_INPUT_BUS.renderSided(getFrontFacing(), renderState, translation, pipeline);
            }
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.item_bus.import.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.fluid_hatch.import.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.item_import.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.fluid_import.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me_import_item_hatch.configs.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me_import_fluid_hatch.configs.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.copy_paste.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.me.extra_connections.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    @Override
    public MultiblockAbility<DualHandler> getAbility() {
        return MultiblockAbility.DUAL_IMPORT;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(new DualHandler(this.getImportItems(), this.getImportFluids(), false));
    }

    @Override
    public boolean hasGhostCircuitInventory() {
        return true;
    }

    @Override
    public void setGhostCircuitConfig(int config) {
        if (this.circuitInventory.getCircuitValue() == config) {
            return;
        }
        this.circuitInventory.setCircuitValue(config);
        if (!getWorld().isRemote) {
            markDirty();
        }
    }

    @Override
    public final void onDataStickLeftClick(EntityPlayer player, ItemStack dataStick) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("MEInputBus", writeItemConfigToTag());
        dataStick.setTagCompound(tag);
        dataStick.setTranslatableName("gregtech.machine.me.item_import.data_stick.name");

        tag = new NBTTagCompound();
        tag.setTag("MEInputHatch", writeFluidConfigToTag());
        dataStick.setTagCompound(tag);
        dataStick.setTranslatableName("gregtech.machine.me.fluid_import.data_stick.name");

        player.sendStatusMessage(new TextComponentTranslation("gregtech.machine.me.import_copy_settings"), true);
    }

    protected NBTTagCompound writeItemConfigToTag() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound configStacks = new NBTTagCompound();
        tag.setTag("ConfigStacks", configStacks);
        for (int i = 0; i < ITEM_CONFIG_SIZE; i++) {
            var slot = this.aeItemHandler.getInventory()[i];
            IAEItemStack config = slot.getConfig();
            if (config == null) {
                continue;
            }
            NBTTagCompound stackNbt = new NBTTagCompound();
            config.getDefinition().writeToNBT(stackNbt);
            configStacks.setTag(Integer.toString(i), stackNbt);
        }
        tag.setByte("GhostCircuit", (byte) this.circuitInventory.getCircuitValue());
        return tag;
    }

    protected NBTTagCompound writeFluidConfigToTag() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound configStacks = new NBTTagCompound();
        tag.setTag("ConfigStacks", configStacks);
        for (int i = 0; i < FLUID_CONFIG_SIZE; i++) {
            var slot = this.aeFluidHandler.getInventory()[i];
            IAEFluidStack config = slot.getConfig();
            if (config == null) {
                continue;
            }
            NBTTagCompound stackNbt = new NBTTagCompound();
            config.writeToNBT(stackNbt);
            configStacks.setTag(Integer.toString(i), stackNbt);
        }
        return tag;
    }

    @Override
    public final boolean onDataStickRightClick(EntityPlayer player, ItemStack dataStick) {
        NBTTagCompound tag = dataStick.getTagCompound();

        if (tag != null && tag.hasKey("MEInputBus")) {
            readItemConfigFromTag(tag.getCompoundTag("MEInputBus"));
            syncItemME();
            player.sendStatusMessage(new TextComponentTranslation("gregtech.machine.me.import_paste_settings"), true);
        }
        if (tag != null && tag.hasKey("MEInputHatch")) {
            readFluidConfigFromTag(tag.getCompoundTag("MEInputHatch"));
            syncItemME();
            player.sendStatusMessage(new TextComponentTranslation("gregtech.machine.me.import_paste_settings"), true);
        }
        return true;
    }

    protected void readItemConfigFromTag(NBTTagCompound tag) {
        if (tag.hasKey("ConfigStacks")) {
            NBTTagCompound configStacks = tag.getCompoundTag("ConfigStacks");
            for (int i = 0; i < ITEM_CONFIG_SIZE; i++) {
                String key = Integer.toString(i);
                if (configStacks.hasKey(key)) {
                    NBTTagCompound configTag = configStacks.getCompoundTag(key);
                    this.aeItemHandler.getInventory()[i].setConfig(WrappedItemStack.fromNBT(configTag));
                } else {
                    this.aeItemHandler.getInventory()[i].setConfig(null);
                }
            }
        }
        if (tag.hasKey("GhostCircuit")) {
            this.setGhostCircuitConfig(tag.getByte("GhostCircuit"));
        }
    }

    protected void readFluidConfigFromTag(NBTTagCompound tag) {
        if (tag.hasKey("ConfigStacks")) {
            NBTTagCompound configStacks = tag.getCompoundTag("ConfigStacks");
            for (int i = 0; i < FLUID_CONFIG_SIZE; i++) {
                String key = Integer.toString(i);
                if (configStacks.hasKey(key)) {
                    NBTTagCompound configTag = configStacks.getCompoundTag(key);
                    this.aeFluidHandler.getInventory()[i].setConfig(WrappedFluidStack.fromNBT(configTag));
                } else {
                    this.aeFluidHandler.getInventory()[i].setConfig(null);
                }
            }
        }
    }

    @Override
    public void getSubItems(CreativeTabs creativeTab, NonNullList<ItemStack> subItems) {
        // override here is gross, but keeps things in order despite
        // IDs being out of order, due to UEV+ being added later
        if (this == GTQTMetaTileEntities.ME_DUAL_IMPORT_HATCH) {
            subItems.add(GTQTMetaTileEntities.ME_DUAL_IMPORT_HATCH.getStackForm());
        } else if (this.getClass() != MetaTileEntityMEDualInputHatch.class) {
            // let subclasses fall through this override
            super.getSubItems(creativeTab, subItems);
        }
    }
}
