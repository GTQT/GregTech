package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.resources.FluxWirelessTextures;
import gregtech.api.gui.resources.IGuiTexture;
import gregtech.api.gui.widgets.AdvancedTextWidget;
import gregtech.api.gui.widgets.FluxActionButtonWidget;
import gregtech.api.gui.widgets.FluxChannelListWidget;
import gregtech.api.gui.widgets.FluxChannelSelectorWidget;
import gregtech.api.gui.widgets.FluxInventoryRangeWidget;
import gregtech.api.gui.widgets.FluxSwitchWidget;
import gregtech.api.gui.widgets.FluxTabGroup;
import gregtech.api.gui.widgets.FluxTabListRenderer;
import gregtech.api.gui.widgets.FluxTextFieldWidget;
import gregtech.api.gui.widgets.FluxWirelessBackgroundWidget;
import gregtech.api.gui.widgets.WidgetGroup;
import gregtech.api.gui.widgets.tab.IGuiTextureTabInfo;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.wireless.EnergyContainerWireless;
import gregtech.api.wireless.TransferContext;
import gregtech.api.wireless.WirelessEnergyService;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.wireless.WirelessChannelUi;
import gregtech.common.wireless.WirelessEnergyServiceImpl;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;

import java.util.List;
import java.math.BigInteger;

public class MetaTileEntityWirelessEnergyHatch extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IEnergyContainer> {

    private final int amperage;
    private final boolean isExport;
    private final EnergyContainerWireless energyContainer;
    private int channelId;

    public MetaTileEntityWirelessEnergyHatch(ResourceLocation metaTileEntityId, int tier, int amperage,
                                             boolean isExport) {
        super(metaTileEntityId, tier);
        this.isExport = isExport;
        this.amperage = amperage;
        energyContainer = new EnergyContainerWireless(this, isExport, GTValues.V[tier], this.amperage,
                () -> channelId);

    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityWirelessEnergyHatch(this.metaTileEntityId, this.getTier(), this.amperage,
                this.isExport);
    }

    @SideOnly(Side.CLIENT)
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (this.shouldRenderOverlay()) {
            getOverlay().renderSided(getFrontFacing(), renderState, translation, pipeline);
        }

    }

    @SideOnly(Side.CLIENT)
    private SimpleOverlayRenderer getOverlay() {
        if (amperage == 2) {
            return Textures.MULTIPART_WIRELESS_ENERGY;
        } else if (amperage == 4) {
            return Textures.MULTIPART_WIRELESS_ENERGY_4x;
        } else if (amperage == 16) {
            return Textures.MULTIPART_WIRELESS_ENERGY_16x;
        } else if (amperage == 64) {
            return Textures.MULTIPART_WIRELESS_ENERGY_64x;
        } else if (amperage == 256) {
            return Textures.MULTIPART_WIRELESS_ENERGY_256x;
        } else if (amperage == 1024) {
            return Textures.MULTIPART_WIRELESS_ENERGY_1024x;
        } else if (amperage == 4096) {
            return Textures.MULTIPART_WIRELESS_ENERGY_4096x;
        } else if (amperage == 16384) {
            return Textures.MULTIPART_WIRELESS_ENERGY_16384x;
        } else if (amperage == 65536) {
            return Textures.MULTIPART_WIRELESS_ENERGY_65536x;
        } else if (amperage == 262144) {
            return Textures.MULTIPART_WIRELESS_ENERGY_262144x;
        } else if (amperage == 1048576) {
            return Textures.MULTIPART_WIRELESS_ENERGY_1048576x;
        } else return Textures.MULTIPART_WIRELESS_ENERGY;

    }

    @Override
    public MultiblockAbility<IEnergyContainer> getAbility() {
        return isExport ? MultiblockAbility.OUTPUT_ENERGY : MultiblockAbility.INPUT_ENERGY;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(energyContainer);
    }

    @Override
    public void addInformation(ItemStack stack,
                               World player,
                               @NotNull List<String> tooltip,
                               boolean advanced) {
        String tierName = GTValues.VNF[this.getTier()];
        tooltip.add(I18n.format("gregtech.machine.wireless_energy_hatch.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.wireless_energy_hatch.tooltip.2"));

        if (this.isExport) {
            tooltip.add(I18n.format("gregtech.universal.tooltip.voltage_out", this.energyContainer.getOutputVoltage(),
                    tierName));
            tooltip.add(I18n.format("gregtech.universal.tooltip.amperage_out_till",
                    this.energyContainer.getOutputAmperage()));
        } else {
            tooltip.add(I18n.format("gregtech.universal.tooltip.voltage_in", this.energyContainer.getInputVoltage(),
                    tierName));
            tooltip.add(I18n.format("gregtech.universal.tooltip.amperage_in_till",
                    this.energyContainer.getInputAmperage()));
        }

        tooltip.add(I18n.format("gregtech.universal.tooltip.energy_storage_capacity",
                this.energyContainer.getEnergyCapacity()));
        tooltip.add(I18n.format("gregtech.universal.enabled"));

        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            tooltip.add(I18n.format("gregtech.machine.wireless_energy_hatch.tooltip.shift"));
        } else {
            tooltip.add(I18n.format("gregtech.tooltip.hold_shift"));
        }
    }

    @Override
    protected ModularUI createUI(EntityPlayer player) {
        java.util.UUID owner = player.getUniqueID();
        String[] editedChannelName = { getSelectedChannelName(owner) };
        String[] newChannelName = { "" };
        boolean[] wirelessEnabled = { isWirelessCharging(owner) };
        int[] wirelessRange = { getWirelessSlotMask(owner) };
        FluxTabGroup<WidgetGroup> tabs = new FluxTabGroup<>(0, 0,
                new FluxTabListRenderer(0, 1, 2, 4, 6, 7));

        WidgetGroup home = new WidgetGroup(0, 0, 176, 166);
        home.addWidget(new FluxChannelSelectorWidget(20, 8, () -> getSelectedChannelName(owner)));
        home.addWidget(translatedLabel(20, 30, "gregtech.wireless.gui.voltage",
                () -> new Object[] { getVoltage() }, 0xFFB4B4B4));
        home.addWidget(translatedLabel(20, 42, "gregtech.wireless.gui.amperage",
                () -> new Object[] { getAmperage() }, 0xFFB4B4B4));
        home.addWidget(translatedLabel(20, 54, "gregtech.wireless.gui.mode",
                () -> new Object[] { new TextComponentTranslation(isExport ?
                        "gregtech.wireless.gui.mode.output" : "gregtech.wireless.gui.mode.input") }, 0xFFB4B4B4));
        home.addWidget(translatedLabel(20, 66, "gregtech.wireless.gui.energy",
                () -> new Object[] { getStored(owner) }, 0xFFB4B4B4));

        WidgetGroup selection = new WidgetGroup(0, 0, 176, 166);
        selection.addWidget(translatedLabel(19, 10, "gregtech.wireless.gui.sort.name",
                () -> new Object[0], 0xFFB4B4B4));
        selection.addWidget(translatedLabel(122, 10, "gregtech.wireless.gui.total",
                () -> new Object[] { WirelessChannelUi.getChannels(owner).size() }, 0xFFB4B4B4));
        selection.addWidget(new FluxChannelListWidget(15, 22, () -> WirelessChannelUi.getChannels(owner),
                () -> channelId, this::setChannelId));

        WidgetGroup wireless = new WidgetGroup(0, 0, 176, 166);
        wireless.addWidget(translatedLabel(68, 12, "gregtech.wireless.gui.wireless_charging",
                () -> new Object[0], 0xFFB4B4B4));
        wireless.addWidget(new FluxInventoryRangeWidget(() -> wirelessRange[0], range -> wirelessRange[0] = range));
        wireless.addWidget(translatedLabel(20, 156, "gregtech.wireless.gui.enable_wireless",
                () -> new Object[0], FluxWirelessTextures.NETWORK_COLOR));
        wireless.addWidget(new FluxSwitchWidget(140, 156, () -> wirelessEnabled[0],
                enabled -> wirelessEnabled[0] = enabled));
        wireless.addWidget(new FluxActionButtonWidget(70, 130, 36, "gregtech.wireless.gui.apply",
                data -> setWirelessCharging(owner, wirelessEnabled[0], wirelessRange[0])));

        WidgetGroup statistics = new WidgetGroup(0, 0, 176, 166);
        statistics.addWidget(new FluxChannelSelectorWidget(20, 8, () -> getSelectedChannelName(owner)));
        statistics.addWidget(translatedLabel(12, 30, "gregtech.wireless.gui.energy",
                () -> new Object[] { getStored(owner) }, 0xFFB4B4B4));
        statistics.addWidget(translatedLabel(12, 42, "gregtech.wireless.gui.input_rate",
                () -> new Object[] { getInput(owner) }, 0xFFB4B4B4));
        statistics.addWidget(translatedLabel(12, 54, "gregtech.wireless.gui.output_rate",
                () -> new Object[] { getOutput(owner) }, 0xFFB4B4B4));

        WidgetGroup settings = new WidgetGroup(0, 0, 176, 166);
        settings.addWidget(translatedLabel(14, 18, "gregtech.wireless.gui.channel_name",
                () -> new Object[0], 0xFF606060));
        FluxTextFieldWidget channelName = new FluxTextFieldWidget(16, 28, 144, 12,
                () -> editedChannelName[0], name -> editedChannelName[0] = name);
        channelName.setValidator(value -> true);
        settings.addWidget(channelName);
        FluxActionButtonWidget deleteButton = new FluxActionButtonWidget(18, 140, 36,
                "gregtech.wireless.gui.delete", data -> {
                    if (deleteChannel(owner)) tabs.selectTabFromServer(0);
                }).requireDoubleShift();
        deleteButton.setTooltipText("gregtech.wireless.gui.delete_hint");
        settings.addWidget(deleteButton);
        settings.addWidget(new FluxActionButtonWidget(62, 140, 52, "gregtech.wireless.gui.transfer_1m",
                data -> transferToNextChannel(owner)));
        FluxActionButtonWidget applyName = new FluxActionButtonWidget(122, 140, 36,
                "gregtech.wireless.gui.apply", data -> renameChannel(owner, editedChannelName[0]));
        applyName.setEnabledSupplier(() -> hasText(channelName));
        settings.addWidget(applyName);

        WidgetGroup create = new WidgetGroup(0, 0, 176, 166);
        create.addWidget(translatedLabel(14, 18, "gregtech.wireless.gui.channel_name",
                () -> new Object[0], 0xFF606060));
        FluxTextFieldWidget createdName = new FluxTextFieldWidget(16, 28, 144, 12,
                () -> newChannelName[0], name -> newChannelName[0] = name);
        createdName.setValidator(value -> true);
        create.addWidget(createdName);
        FluxActionButtonWidget createButton = new FluxActionButtonWidget(70, 150, 36,
                "gregtech.wireless.gui.create", data -> {
                    if (createChannel(owner, newChannelName[0])) tabs.selectTabFromServer(1);
                });
        createButton.setEnabledSupplier(() -> hasText(createdName));
        create.addWidget(createButton);

        tabs.addTab(new IGuiTextureTabInfo(IGuiTexture.EMPTY, "gregtech.wireless.gui.tab.home"), home);
        tabs.addTab(new IGuiTextureTabInfo(IGuiTexture.EMPTY, "gregtech.wireless.gui.tab.selection"), selection);
        tabs.addTab(new IGuiTextureTabInfo(IGuiTexture.EMPTY, "gregtech.wireless.gui.tab.wireless"), wireless);
        tabs.addTab(new IGuiTextureTabInfo(IGuiTexture.EMPTY, "gregtech.wireless.gui.tab.statistics"), statistics);
        tabs.addTab(new IGuiTextureTabInfo(IGuiTexture.EMPTY, "gregtech.wireless.gui.tab.settings"), settings);
        tabs.addTab(new IGuiTextureTabInfo(IGuiTexture.EMPTY, "gregtech.wireless.gui.tab.create"), create);
        tabs.setOnTabChanged((oldTab, newTab) -> {
            if (newTab == 2) {
                wirelessEnabled[0] = isWirelessCharging(owner);
                wirelessRange[0] = getWirelessSlotMask(owner);
            } else if (newTab == 4) {
                editedChannelName[0] = getSelectedChannelName(owner);
            } else if (newTab == 5) {
                newChannelName[0] = "";
            }
        });

        return ModularUI.builder(IGuiTexture.EMPTY, 176, 166)
                .shouldColor(false)
                .widget(new FluxWirelessBackgroundWidget())
                .widget(tabs)
                .build(getHolder(), player);
    }

    private long getVoltage() {
        return isExport ? energyContainer.getOutputVoltage() : energyContainer.getInputVoltage();
    }

    private long getAmperage() {
        return isExport ? energyContainer.getOutputAmperage() : energyContainer.getInputAmperage();
    }

    private String getSelectedChannelName(java.util.UUID owner) {
        return WirelessChannelUi.getChannels(owner).stream()
                .filter(channel -> channel.getChannelId() == channelId)
                .findFirst()
                .map(channel -> channel.getNetworkName())
                .orElse("Main");
    }

    private AdvancedTextWidget translatedLabel(int x, int y, String key,
                                               java.util.function.Supplier<Object[]> args, int color) {
        return new AdvancedTextWidget(x, y, lines -> lines.add(new TextComponentTranslation(key, args.get())), color);
    }

    private String getStored(java.util.UUID owner) {
        return WirelessChannelUi.getChannels(owner).stream()
                .filter(channel -> channel.getChannelId() == channelId)
                .findFirst()
                .map(channel -> channel.getStored().toString())
                .orElse("0");
    }

    private String getInput(java.util.UUID owner) {
        return WirelessChannelUi.getChannels(owner).stream()
                .filter(channel -> channel.getChannelId() == channelId)
                .findFirst()
                .map(channel -> channel.getInputPerSecond().toString())
                .orElse("0");
    }

    private String getOutput(java.util.UUID owner) {
        return WirelessChannelUi.getChannels(owner).stream()
                .filter(channel -> channel.getChannelId() == channelId)
                .findFirst()
                .map(channel -> channel.getOutputPerSecond().toString())
                .orElse("0");
    }

    private void renameChannel(java.util.UUID owner, String name) {
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service != null) service.renameChannel(owner, channelId, name);
    }

    private boolean createChannel(java.util.UUID owner, String name) {
        if (name == null || name.trim().isEmpty()) return false;
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        int createdChannel = service == null ? -1 : service.createChannel(owner, name);
        if (createdChannel >= 0) setChannelId(createdChannel);
        return createdChannel >= 0;
    }

    private boolean deleteChannel(java.util.UUID owner) {
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service == null || !service.deleteChannel(owner, channelId)) return false;
        setChannelId(WirelessChannelUi.channelIdAt(owner, 0));
        return true;
    }

    private void transferToNextChannel(java.util.UUID owner) {
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service != null) service.transfer(owner, channelId, WirelessChannelUi.nextChannelId(owner, channelId),
                BigInteger.valueOf(1_000_000L), TransferContext.ADMIN);
    }

    private boolean isWirelessCharging(java.util.UUID owner) {
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        return service != null && service.getView(owner, channelId).isWirelessChargingEnabled();
    }

    private int getWirelessSlotMask(java.util.UUID owner) {
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        return service == null ? 0 : service.getView(owner, channelId).getWirelessChargingSlots();
    }

    private void setWirelessCharging(java.util.UUID owner, boolean enabled, int slotMask) {
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service != null) service.setWirelessCharging(owner, channelId, enabled, slotMask);
    }

    private static boolean hasText(FluxTextFieldWidget textField) {
        String text = textField.getCurrentString();
        return text != null && !text.trim().isEmpty();
    }

    public int getChannelId() {
        return channelId;
    }

    public void setChannelId(int channelId) {
        this.channelId = Math.max(0, channelId);
        markDirty();
    }

    @Override
    public NBTTagCompound writeToNBT(@NotNull NBTTagCompound data) {
        data.setInteger("wirelessChannel", channelId);
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        channelId = Math.max(0, data.getInteger("wirelessChannel"));
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeVarInt(channelId);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        channelId = buf.readVarInt();
    }
}
