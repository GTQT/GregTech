package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.IWirelessController;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.AdvancedTextWidget;
import gregtech.api.gui.resources.FluxWirelessTextures;
import gregtech.api.gui.resources.IGuiTexture;
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
import gregtech.api.wireless.TransferContext;
import gregtech.api.wireless.TransferResult;
import gregtech.api.wireless.WirelessEnergyService;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityPowerSubstation;
import gregtech.common.wireless.WirelessEnergyServiceImpl;
import gregtech.common.wireless.WirelessChannelUi;

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
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MetaTileEntityWirelessController extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IWirelessController>, IWirelessController {

    // Rebalance every 60 seconds (PSS rebalance is infrequent by design)
    private static final int REBALANCE_INTERVAL = 1200;

    // Base retention ratios by tier (indexed by tier - UHV)
    // UHV: 60%, UEV: 50%, UIV: 40%, UXV: 30%, OpV: 20%, MAX: 10%
    private static final double[] RETENTION_RATIOS = {
            0.60,  // UHV
            0.50,  // UEV
            0.40,  // UIV
            0.30,  // UXV
            0.20,  // OpV
            0.10   // MAX
    };

    // Wireless transfer rate formula multiplier
    private static final long TRANSFER_RATE_BASE_MULTIPLIER = 7;

    private int priority;
    private int channelId;
    private int rebalanceTimer = 0;
    private long lastLocalStored;

    public MetaTileEntityWirelessController(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        this.priority = tier;
    }

    private static long longPow(long base, int exponent) {
        long result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= base;
        }
        return result;
    }

    private static long saturatingAdd(long left, long right) {
        if (left <= 0) return Math.max(right, 0);
        if (right <= 0) return left;
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0 || right <= 0) return 0;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityWirelessController(metaTileEntityId, getTier());
    }

    @Override
    public MultiblockAbility<IWirelessController> getAbility() {
        return MultiblockAbility.WIRELESS_CONTROLLER;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(this);
    }

    // ==================== Wireless Network Binding ====================

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.wireless_controller.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.wireless_controller.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.wireless_controller.tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.wireless_controller.tooltip.4"));
        tooltip.add(I18n.format("gregtech.machine.wireless_controller.tooltip.5"));
        tooltip.add(I18n.format("gregtech.machine.wireless_controller.tooltip.6"));
        tooltip.add(I18n.format("gregtech.machine.wireless_controller.tooltip.7"));
        tooltip.add(I18n.format("gregtech.machine.wireless_controller.tooltip.8"));
        tooltip.add(I18n.format("gregtech.machine.wireless_controller.tooltip.9"));
        int tier = getTier();
        int index = Math.min(tier - GTValues.UHV, RETENTION_RATIOS.length - 1);
        double ratio = RETENTION_RATIOS[Math.max(index, 0)];
        tooltip.add(I18n.format("gregtech.machine.wireless_controller.tooltip.retention", (int) (ratio * 100)));
        tooltip.add(I18n.format("gregtech.machine.wireless_controller.tooltip.team"));
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            getFrontOverlay().renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    protected ICubeRenderer getFrontOverlay() {
        return Textures.FUSION_REACTOR_OVERLAY;
    }

    @Override
    public void update() {
        super.update();
        if (getWorld() == null || getWorld().isRemote) return;

        MetaTileEntityPowerSubstation pss = getPSS();
        if (pss == null) {
            lastLocalStored = 0;
            return;
        }

        rebalanceTimer++;
        if (rebalanceTimer >= REBALANCE_INTERVAL) {
            rebalanceTimer = 0;
            tryRebalance(pss);
        }
    }

    /**
     * Bidirectional rebalance between PSS local energyBank and the wireless pool.
     * <p>
     * Threshold = PSS capacity × retentionRatio[tier] - PSS stored > threshold → push excess to wireless pool - PSS
     * stored < threshold → pull deficit from wireless pool
     */
    private void tryRebalance(MetaTileEntityPowerSubstation pss) {
        UUID ownerId = this.getOwnerGT();
        if (ownerId == null) return;

        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service == null) return;
        int effectiveChannel = getEffectiveChannelId(service, ownerId);
        service.updateEndpoint(ownerId, effectiveChannel, "pss:" + getPos().toLong(), "pss_controller",
                getWorld().provider.getDimension(), getPos().toLong(), true, false, getWorld().getTotalWorldTime());

        BigInteger capacity = pss.getCapacityByBigInteger();
        if (capacity.signum() == 0) return;

        double ratio = getRetentionRatio();
        BigInteger threshold = new BigDecimal(capacity)
                .multiply(BigDecimal.valueOf(ratio))
                .toBigInteger();

        BigInteger localStored = pss.getStoredByBigInteger();
        lastLocalStored = pss.getStoredLong();

        long transferPerTick = getMaxTransferPerTick();
        long maxTransfer = saturatingMultiply(transferPerTick, REBALANCE_INTERVAL);
        // No transfer capacity (controller tier < UHV or no UHV+ capacitors)
        if (maxTransfer <= 0) return;

        if (localStored.compareTo(threshold) > 0) {
            // PSS has excess → push to wireless pool
            BigInteger excess = localStored.subtract(threshold);
            if (excess.compareTo(BigInteger.valueOf(maxTransfer)) > 0) {
                excess = BigInteger.valueOf(maxTransfer);
            }
            if (excess.signum() <= 0) return;
            TransferResult result = service.insert(ownerId, effectiveChannel, excess.longValue(),
                    TransferContext.PSS_REBALANCE);
            if (result.isSuccess()) {
                pss.externalDrain(result.getAmountLong());
                lastLocalStored = pss.getStoredLong();
            }
        } else if (localStored.compareTo(threshold) < 0) {
            // PSS deficit → pull from wireless pool
            BigInteger deficit = threshold.subtract(localStored);
            if (deficit.compareTo(BigInteger.valueOf(maxTransfer)) > 0) {
                deficit = BigInteger.valueOf(maxTransfer);
            }
            if (deficit.signum() <= 0) return;
            TransferResult result = service.extractUpTo(ownerId, effectiveChannel,
                    deficit.longValue(), TransferContext.PSS_REBALANCE);
            if (result.isSuccess() && result.getAmountLong() > 0) {
                pss.externalFill(result.getAmountLong());
                lastLocalStored = pss.getStoredLong();
            }
        }
    }

    private double getRetentionRatio() {
        int index = getTier() - GTValues.UHV;
        if (index < 0) index = 0;
        if (index >= RETENTION_RATIOS.length) index = RETENTION_RATIOS.length - 1;
        return RETENTION_RATIOS[index];
    }

    /**
     * Calculates max transfer per tick using the formula: 7 * Σ(count_i * V[capacitor_tier_i] * 2^(capacitor_tier_i -
     * UHV) * 5^(controller_tier - UHV)) Only capacitors at UHV tier and above contribute to the transfer rate. Each
     * voltage tier's capacitors are counted separately.
     */
    private long getMaxTransferPerTick() {
        MetaTileEntityPowerSubstation pss = getPSS();
        if (pss == null) return 0;

        Map<Integer, Integer> batteryTierCounts = pss.getBatteryTierCounts();
        if (batteryTierCounts.isEmpty()) return 0;

        int controllerTier = getTier();
        int controllerTierOffset = controllerTier - GTValues.UHV;
        if (controllerTierOffset < 0) return 0;

        long controllerPowerOf5 = longPow(5, controllerTierOffset);
        long totalTransfer = 0;

        for (Map.Entry<Integer, Integer> entry : batteryTierCounts.entrySet()) {
            int capacitorTier = entry.getKey();
            // Only capacitors at UHV and above contribute
            if (capacitorTier < GTValues.UHV) continue;

            int count = entry.getValue();
            int capacitorTierOffset = capacitorTier - GTValues.UHV;
            long voltage = GTValues.V[Math.min(capacitorTier, GTValues.V.length - 1)];
            long powerOf2 = 1L << capacitorTierOffset;

            // count * V[tier] * 2^(capacitor_tier - UHV) * 5^(controller_tier - UHV)
            long contribution = saturatingMultiply(count, voltage);
            contribution = saturatingMultiply(contribution, powerOf2);
            contribution = saturatingMultiply(contribution, controllerPowerOf5);
            totalTransfer = saturatingAdd(totalTransfer, contribution);
        }

        return saturatingMultiply(TRANSFER_RATE_BASE_MULTIPLIER, totalTransfer);
    }

    // ==================== IWirelessController ====================

    @Override
    public void sentMTE() {
        // Wireless pool is now stateless with respect to nodes — no registration needed.
        // Initialize the persisted local energy snapshot.
        MetaTileEntityPowerSubstation pss = getPSS();
        if (pss != null) {
            lastLocalStored = pss.getStoredLong();
        }
    }

    @Override
    public void removeMTE() {
        // No-op: wireless pool does not track individual nodes.
        // Energy in the wireless pool from this PSS remains available to the network.
        lastLocalStored = 0;
    }

    public MetaTileEntityPowerSubstation getPSS() {
        if (this.getController() instanceof MetaTileEntityPowerSubstation powerStation
                && powerStation.isStructureFormed()) {
            return powerStation;
        }
        return null;
    }

    // ==================== Priority ====================

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public int getChannelId() {
        return channelId;
    }

    public void setChannelId(int channelId) {
        this.channelId = Math.max(0, channelId);
        markDirty();
    }

    @Override
    protected ModularUI createUI(EntityPlayer player) {
        UUID playerId = player.getUniqueID();
        String[] editedChannelName = { getChannelName(playerId) };
        String[] newChannelName = { "" };
        boolean[] wirelessEnabled = { isWirelessCharging(playerId) };
        int[] wirelessRange = { getWirelessSlotMask(playerId) };
        FluxTabGroup<WidgetGroup> tabs = new FluxTabGroup<>(0, 0,
                new FluxTabListRenderer(0, 1, 2, 4, 6, 7));

        WidgetGroup home = new WidgetGroup(0, 0, 176, 166);
        home.addWidget(new FluxChannelSelectorWidget(20, 8, () -> getChannelName(playerId)));
        home.addWidget(translatedLabel(20, 30, "gregtech.wireless.gui.pss_transfer",
                () -> new Object[] { getMaxTransferPerTick() }, 0xFFB4B4B4));
        home.addWidget(translatedLabel(20, 42, "gregtech.wireless.gui.energy",
                () -> new Object[] { getStored(playerId) }, 0xFFB4B4B4));
        home.addWidget(translatedLabel(20, 54, "gregtech.wireless.gui.input_rate",
                () -> new Object[] { getInput(playerId) }, 0xFFB4B4B4));
        home.addWidget(translatedLabel(20, 66, "gregtech.wireless.gui.output_rate",
                () -> new Object[] { getOutput(playerId) }, 0xFFB4B4B4));

        WidgetGroup selection = new WidgetGroup(0, 0, 176, 166);
        selection.addWidget(translatedLabel(19, 10, "gregtech.wireless.gui.sort.name",
                () -> new Object[0], 0xFFB4B4B4));
        selection.addWidget(translatedLabel(122, 10, "gregtech.wireless.gui.total",
                () -> new Object[] { WirelessChannelUi.getChannels(playerId).size() }, 0xFFB4B4B4));
        selection.addWidget(new FluxChannelListWidget(15, 22, () -> WirelessChannelUi.getChannels(playerId),
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
                data -> setWirelessCharging(playerId, wirelessEnabled[0], wirelessRange[0])));

        WidgetGroup statistics = new WidgetGroup(0, 0, 176, 166);
        statistics.addWidget(new FluxChannelSelectorWidget(20, 8, () -> getChannelName(playerId)));
        statistics.addWidget(translatedLabel(12, 30, "gregtech.wireless.gui.channel_id",
                () -> new Object[] { channelId }, 0xFFB4B4B4));
        statistics.addWidget(translatedLabel(12, 42, "gregtech.wireless.gui.energy",
                () -> new Object[] { getStored(playerId) }, 0xFFB4B4B4));
        statistics.addWidget(translatedLabel(12, 54, "gregtech.wireless.gui.input_rate",
                () -> new Object[] { getInput(playerId) }, 0xFFB4B4B4));
        statistics.addWidget(translatedLabel(12, 66, "gregtech.wireless.gui.output_rate",
                () -> new Object[] { getOutput(playerId) }, 0xFFB4B4B4));

        WidgetGroup settings = new WidgetGroup(0, 0, 176, 166);
        settings.addWidget(translatedLabel(14, 18, "gregtech.wireless.gui.channel_name",
                () -> new Object[0], 0xFF606060));
        FluxTextFieldWidget channelName = new FluxTextFieldWidget(16, 28, 144, 12,
                () -> editedChannelName[0], name -> editedChannelName[0] = name);
        channelName.setValidator(value -> true);
        settings.addWidget(channelName);
        FluxActionButtonWidget deleteButton = new FluxActionButtonWidget(18, 140, 36,
                "gregtech.wireless.gui.delete", data -> {
                    if (deleteChannel(playerId)) tabs.selectTabFromServer(0);
                }).requireDoubleShift();
        deleteButton.setTooltipText("gregtech.wireless.gui.delete_hint");
        settings.addWidget(deleteButton);
        settings.addWidget(new FluxActionButtonWidget(62, 140, 52, "gregtech.wireless.gui.transfer_1m",
                data -> transferToNextChannel(playerId)));
        FluxActionButtonWidget applyName = new FluxActionButtonWidget(122, 140, 36,
                "gregtech.wireless.gui.apply", data -> renameChannel(playerId, editedChannelName[0]));
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
                    if (createChannel(playerId, newChannelName[0])) tabs.selectTabFromServer(1);
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
                wirelessEnabled[0] = isWirelessCharging(playerId);
                wirelessRange[0] = getWirelessSlotMask(playerId);
            } else if (newTab == 4) {
                editedChannelName[0] = getChannelName(playerId);
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

    private String getStored(UUID owner) {
        return WirelessEnergyServiceImpl.getService() == null ? "0" :
                WirelessEnergyServiceImpl.getService().getView(owner, channelId).getStored().toString();
    }

    private String getInput(UUID owner) {
        return WirelessEnergyServiceImpl.getService() == null ? "0" :
                WirelessEnergyServiceImpl.getService().getView(owner, channelId).getInputPerSecond().toString();
    }

    private String getOutput(UUID owner) {
        return WirelessEnergyServiceImpl.getService() == null ? "0" :
                WirelessEnergyServiceImpl.getService().getView(owner, channelId).getOutputPerSecond().toString();
    }

    private AdvancedTextWidget translatedLabel(int x, int y, String key,
                                               java.util.function.Supplier<Object[]> args, int color) {
        return new AdvancedTextWidget(x, y, lines -> lines.add(new TextComponentTranslation(key, args.get())), color);
    }

    private String getChannelName(UUID owner) {
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service == null) return "Main";
        String name = service.getView(owner, channelId).getNetworkName();
        return name == null || name.isEmpty() || "No Network".equals(name) ? "Main" : name;
    }

    private void renameChannel(UUID owner, String name) {
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service != null) service.renameChannel(owner, channelId, name);
    }

    private boolean createChannel(UUID owner, String name) {
        if (name == null || name.trim().isEmpty()) return false;
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        int createdChannel = service == null ? -1 : service.createChannel(owner, name);
        if (createdChannel >= 0) setChannelId(createdChannel);
        return createdChannel >= 0;
    }

    private boolean deleteChannel(UUID owner) {
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service == null || !service.deleteChannel(owner, channelId)) return false;
        setChannelId(WirelessChannelUi.channelIdAt(owner, 0));
        return true;
    }

    private void transferToNextChannel(UUID owner) {
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service != null) service.transfer(owner, channelId, WirelessChannelUi.nextChannelId(owner, channelId),
                BigInteger.valueOf(1_000_000L), TransferContext.ADMIN);
    }

    private boolean isWirelessCharging(UUID owner) {
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        return service != null && service.getView(owner, channelId).isWirelessChargingEnabled();
    }

    private int getWirelessSlotMask(UUID owner) {
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        return service == null ? 0 : service.getView(owner, channelId).getWirelessChargingSlots();
    }

    private void setWirelessCharging(UUID owner, boolean enabled, int slotMask) {
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service != null) service.setWirelessCharging(owner, channelId, enabled, slotMask);
    }

    private static boolean hasText(FluxTextFieldWidget textField) {
        String text = textField.getCurrentString();
        return text != null && !text.trim().isEmpty();
    }

    private int getEffectiveChannelId(WirelessEnergyService service, UUID ownerId) {
        return service.getView(ownerId, channelId).isEmpty() ? 0 : channelId;
    }

    // ==================== Persistence ====================

    @Override
    public NBTTagCompound writeToNBT(@NotNull NBTTagCompound data) {
        data.setInteger("priority", this.priority);
        data.setInteger("wirelessChannel", this.channelId);
        data.setLong("lastLocalStored", this.lastLocalStored);
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.priority = data.getInteger("priority");
        this.channelId = Math.max(0, data.getInteger("wirelessChannel"));
        this.lastLocalStored = data.getLong("lastLocalStored");
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(this.priority);
        buf.writeVarInt(this.channelId);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.priority = buf.readInt();
        this.channelId = buf.readVarInt();
    }
}
