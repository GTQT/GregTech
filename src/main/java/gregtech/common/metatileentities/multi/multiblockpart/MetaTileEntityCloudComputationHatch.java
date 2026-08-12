package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IOpticalComputationHatch;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.resources.IGuiTexture;
import gregtech.api.gui.widgets.AdvancedTextWidget;
import gregtech.api.gui.widgets.FluxActionButtonWidget;
import gregtech.api.gui.widgets.FluxChannelListWidget;
import gregtech.api.gui.widgets.FluxChannelSelectorWidget;
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
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.GTLog;
import gregtech.api.wireless.IWirelessComputationService;
import gregtech.api.wireless.WirelessComputationView;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.wireless.WirelessComputationChannelUi;
import gregtech.common.wireless.WirelessComputationServiceImpl;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static gregtech.api.GTValues.CWT;

/**
 * Cloud computation hatch — the wireless counterpart of the optical
 * computation hatch. No multiblock structure changes required: it reuses the
 * existing {@link MultiblockAbility#COMPUTATION_DATA_TRANSMISSION} /
 * {@link MultiblockAbility#COMPUTATION_DATA_RECEPTION} ability slots.
 * <p>
 * Uplink hatches (attached to computation providers like the HPCA) register
 * their controller into the owner's wireless computation channel and renew
 * the registration as a heartbeat. Downlink hatches (attached to computation
 * receivers like the Research Station) request CWU/t from the channel, which
 * is aggregated across all registered uplink nodes.
 */
public class MetaTileEntityCloudComputationHatch extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IOpticalComputationHatch>, IOpticalComputationHatch {

    private static final int HEARTBEAT_INTERVAL = 20;

    private final boolean isUplink;
    private final int tier;
    private int channelId;
    private boolean registered;

    public MetaTileEntityCloudComputationHatch(ResourceLocation metaTileEntityId, int tier, boolean isUplink) {
        super(metaTileEntityId, tier);
        this.isUplink = isUplink;
        this.tier = tier;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityCloudComputationHatch(metaTileEntityId, tier, isUplink);
    }

    @Override
    public boolean isTransmitter() {
        return isUplink;
    }

    public int maxComputation() {
        return CWT[tier];
    }

    @Override
    public MultiblockAbility<IOpticalComputationHatch> getAbility() {
        return isUplink ? MultiblockAbility.COMPUTATION_DATA_TRANSMISSION :
                MultiblockAbility.COMPUTATION_DATA_RECEPTION;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(this);
    }

    // ==================== Wireless Network ====================

    @Override
    public void update() {
        super.update();
        if (getWorld() == null || getWorld().isRemote || !isUplink) return;
        if (getWorld().getTotalWorldTime() % HEARTBEAT_INTERVAL != 0) return;

        IWirelessComputationService service = WirelessComputationServiceImpl.getService();
        if (service == null) return;

        UUID ownerId = getOwnerGT();
        if (ownerId == null) return;

        MultiblockControllerBase controller = getController();
        boolean formed = controller != null && controller.isStructureFormed();
        if (formed) {
            service.registerProvider(ownerId, channelId, "uplink:" + getPos().toLong(), tier,
                    getWorld().provider.getDimension(), getPos().toLong(), this);
            registered = true;
        } else if (registered) {
            service.unregisterProvider(ownerId, channelId, "uplink:" + getPos().toLong());
            registered = false;
        }
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        if (getWorld() != null && !getWorld().isRemote && isUplink && registered) {
            IWirelessComputationService service = WirelessComputationServiceImpl.getService();
            UUID ownerId = getOwnerGT();
            if (service != null && ownerId != null) {
                service.unregisterProvider(ownerId, channelId, "uplink:" + getPos().toLong());
            }
            registered = false;
        }
    }

    // ==================== IOpticalComputationProvider ====================

    @Override
    public int requestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        if (isUplink) {
            seen.add(this);
            MultiblockControllerBase controller = getController();
            if (controller == null || !controller.isStructureFormed()) return 0;
            if (controller instanceof IOpticalComputationProvider provider) {
                return provider.requestCWUt(cwut, simulate, seen);
            }
            GTLog.logger.error("Cloud Computation Uplink Hatch could not request CWU/t from its controller!");
            return 0;
        }

        // Downlink: ask the wireless computation network
        IWirelessComputationService service = WirelessComputationServiceImpl.getService();
        if (service == null) return 0;
        UUID ownerId = getOwnerGT();
        if (ownerId == null) return 0;
        return Math.min(service.requestCWUt(ownerId, channelId, cwut, simulate, seen), maxComputation());
    }

    @Override
    public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        if (isUplink) {
            seen.add(this);
            MultiblockControllerBase controller = getController();
            if (controller == null || !controller.isStructureFormed()) return 0;
            if (controller instanceof IOpticalComputationProvider provider) {
                return Math.min(provider.getMaxCWUt(seen), maxComputation());
            }
            return 0;
        }

        IWirelessComputationService service = WirelessComputationServiceImpl.getService();
        if (service == null) return 0;
        UUID ownerId = getOwnerGT();
        if (ownerId == null) return 0;
        WirelessComputationView view = service.getView(ownerId, channelId);
        return view.isEmpty() ? 0 : Math.min(view.getMaxCWUt(), maxComputation());
    }

    @Override
    public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
        if (isUplink) {
            seen.add(this);
            MultiblockControllerBase controller = getController();
            // return true here so that unlinked hatches don't cause problems in multis like the Network Switch
            if (controller == null || !controller.isStructureFormed()) return true;
            if (controller instanceof IOpticalComputationProvider provider) {
                return provider.canBridge(seen);
            }
            return false;
        }

        // nothing found on an empty channel: report true to pass quietly
        IWirelessComputationService service = WirelessComputationServiceImpl.getService();
        if (service == null) return true;
        UUID ownerId = getOwnerGT();
        if (ownerId == null) return true;
        return !service.getView(ownerId, channelId).isEmpty();
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (side == getFrontFacing() && capability == GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER) {
            return GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER.cast(this);
        }
        return super.getCapability(capability, side);
    }

    // ==================== Rendering ====================

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            Textures.OPTICAL_DATA_ACCESS_HATCH.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @Override
    public boolean canPartShare() {
        return false;
    }

    // ==================== Tooltip ====================

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(I18n.format(this.isUplink ? "gregtech.machine.cloud_computation_hatch.uplink.tooltip" :
                "gregtech.machine.cloud_computation_hatch.downlink.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.computation_hatch.tier", this.tier));
        tooltip.add(I18n.format("gregtech.machine.computation_hatch.computation", this.maxComputation()));
        tooltip.add(I18n.format("gregtech.machine.cloud_computation_hatch.channel_hint"));
    }

    // ==================== GUI ====================

    @Override
    protected ModularUI createUI(EntityPlayer player) {
        UUID owner = player.getUniqueID();
        String[] editedChannelName = { getSelectedChannelName(owner) };
        String[] newChannelName = { "" };
        FluxTabGroup<WidgetGroup> tabs = new FluxTabGroup<>(0, 0,
                new FluxTabListRenderer(0, 1, 2, 4, 6, 7));

        WidgetGroup home = new WidgetGroup(0, 0, 176, 166);
        home.addWidget(new FluxChannelSelectorWidget(20, 8, () -> getSelectedChannelName(owner)));
        home.addWidget(translatedLabel(20, 30, "gregtech.wireless.gui.compute.cwut",
                () -> new Object[] { maxComputation() }, 0xFFB4B4B4));
        home.addWidget(translatedLabel(20, 42, "gregtech.wireless.gui.compute.allocated",
                () -> new Object[] { getAllocated(owner) }, 0xFFB4B4B4));
        home.addWidget(translatedLabel(20, 54, "gregtech.wireless.gui.mode",
                () -> new Object[] { new TextComponentTranslation(isUplink ?
                        "gregtech.wireless.gui.compute.mode.uplink" :
                        "gregtech.wireless.gui.compute.mode.downlink") }, 0xFFB4B4B4));
        home.addWidget(translatedLabel(20, 66, "gregtech.wireless.gui.compute.capacity",
                () -> new Object[] { getCapacity(owner) }, 0xFFB4B4B4));

        WidgetGroup selection = new WidgetGroup(0, 0, 176, 166);
        selection.addWidget(translatedLabel(19, 10, "gregtech.wireless.gui.sort.name",
                () -> new Object[0], 0xFFB4B4B4));
        selection.addWidget(translatedLabel(122, 10, "gregtech.wireless.gui.total",
                () -> new Object[] { WirelessComputationChannelUi.getChannels(owner).size() }, 0xFFB4B4B4));
        selection.addWidget(new FluxChannelListWidget(15, 22, () -> WirelessComputationChannelUi.getChannels(owner),
                () -> channelId, this::setChannelId));

        WidgetGroup information = new WidgetGroup(0, 0, 176, 166);
        information.addWidget(new FluxChannelSelectorWidget(20, 8, () -> getSelectedChannelName(owner)));
        information.addWidget(translatedLabel(12, 30, "gregtech.wireless.gui.compute.nodes",
                () -> new Object[] { getNodeCount(owner) }, 0xFFB4B4B4));
        information.addWidget(translatedLabel(12, 42, "gregtech.wireless.gui.compute.capacity",
                () -> new Object[] { getCapacity(owner) }, 0xFFB4B4B4));
        information.addWidget(translatedLabel(12, 54, "gregtech.wireless.gui.compute.allocated",
                () -> new Object[] { getAllocated(owner) }, 0xFFB4B4B4));
        information.addWidget(translatedLabel(12, 66, "gregtech.wireless.gui.compute.allocated_per_second",
                () -> new Object[] { getAllocatedPerSecond(owner) }, 0xFFB4B4B4));

        WidgetGroup statistics = new WidgetGroup(0, 0, 176, 166);
        statistics.addWidget(new FluxChannelSelectorWidget(20, 8, () -> getSelectedChannelName(owner)));
        statistics.addWidget(translatedLabel(12, 30, "gregtech.wireless.gui.compute.allocated",
                () -> new Object[] { getAllocated(owner) }, 0xFFB4B4B4));
        statistics.addWidget(translatedLabel(12, 42, "gregtech.wireless.gui.compute.allocated_per_second",
                () -> new Object[] { getAllocatedPerSecond(owner) }, 0xFFB4B4B4));
        statistics.addWidget(translatedLabel(12, 54, "gregtech.wireless.gui.compute.nodes",
                () -> new Object[] { getNodeCount(owner) }, 0xFFB4B4B4));

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
        FluxActionButtonWidget applyName = new FluxActionButtonWidget(62, 140, 52,
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
        tabs.addTab(new IGuiTextureTabInfo(IGuiTexture.EMPTY, "gregtech.wireless.gui.tab.information"), information);
        tabs.addTab(new IGuiTextureTabInfo(IGuiTexture.EMPTY, "gregtech.wireless.gui.tab.statistics"), statistics);
        tabs.addTab(new IGuiTextureTabInfo(IGuiTexture.EMPTY, "gregtech.wireless.gui.tab.settings"), settings);
        tabs.addTab(new IGuiTextureTabInfo(IGuiTexture.EMPTY, "gregtech.wireless.gui.tab.create"), create);
        tabs.setOnTabChanged((oldTab, newTab) -> {
            if (newTab == 4) {
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

    private AdvancedTextWidget translatedLabel(int x, int y, String key,
                                               java.util.function.Supplier<Object[]> args, int color) {
        return new AdvancedTextWidget(x, y, lines -> lines.add(new TextComponentTranslation(key, args.get())), color);
    }

    private String getSelectedChannelName(UUID owner) {
        return WirelessComputationChannelUi.getChannels(owner).stream()
                .filter(channel -> channel.getChannelId() == channelId)
                .findFirst()
                .map(WirelessComputationView::getNetworkName)
                .orElse("Main");
    }

    private String getAllocated(UUID owner) {
        IWirelessComputationService service = WirelessComputationServiceImpl.getService();
        if (service == null) return "0";
        WirelessComputationView view = service.getView(owner, channelId);
        return view.isEmpty() ? "0" : Integer.toString(view.getAllocatedCWUt());
    }

    private String getAllocatedPerSecond(UUID owner) {
        IWirelessComputationService service = WirelessComputationServiceImpl.getService();
        if (service == null) return "0";
        WirelessComputationView view = service.getView(owner, channelId);
        return view.isEmpty() ? "0" : Integer.toString(view.getAllocatedPerSecond());
    }

    private String getCapacity(UUID owner) {
        IWirelessComputationService service = WirelessComputationServiceImpl.getService();
        if (service == null) return "0";
        WirelessComputationView view = service.getView(owner, channelId);
        return view.isEmpty() ? "0" : Integer.toString(view.getMaxCWUt());
    }

    private String getNodeCount(UUID owner) {
        IWirelessComputationService service = WirelessComputationServiceImpl.getService();
        if (service == null) return "0";
        WirelessComputationView view = service.getView(owner, channelId);
        return view.isEmpty() ? "0" : Integer.toString(view.getNodeCount());
    }

    private void renameChannel(UUID owner, String name) {
        IWirelessComputationService service = WirelessComputationServiceImpl.getService();
        if (service != null) service.renameChannel(owner, channelId, name);
    }

    private boolean createChannel(UUID owner, String name) {
        if (name == null || name.trim().isEmpty()) return false;
        IWirelessComputationService service = WirelessComputationServiceImpl.getService();
        int createdChannel = service == null ? -1 : service.createChannel(owner, name);
        if (createdChannel >= 0) setChannelId(createdChannel);
        return createdChannel >= 0;
    }

    private boolean deleteChannel(UUID owner) {
        IWirelessComputationService service = WirelessComputationServiceImpl.getService();
        if (service == null || !service.deleteChannel(owner, channelId)) return false;
        setChannelId(WirelessComputationChannelUi.channelIdAt(owner, 0));
        return true;
    }

    private static boolean hasText(FluxTextFieldWidget textField) {
        String text = textField.getCurrentString();
        return text != null && !text.trim().isEmpty();
    }

    // ==================== Channel Persistence ====================

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
