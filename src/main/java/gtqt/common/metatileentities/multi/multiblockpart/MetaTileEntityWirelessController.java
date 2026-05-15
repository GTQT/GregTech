package gtqt.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.IWirelessController;
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
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;
import gregtech.common.wireless.WirelessEnergyServiceImpl;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
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
import java.util.UUID;

public class MetaTileEntityWirelessController extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IWirelessController>, IWirelessController {

    // Rebalance every 60 seconds (PSS rebalance is infrequent by design)
    private static final int REBALANCE_INTERVAL = 1200;

    // Base retention ratios by tier — higher tier = more energy shared to wireless pool
    // tier < LuV:  80% local retention (low tier, keep most energy local)
    // LuV-ZPM:     50%
    // UV-UHV:      30%
    // UEV-UMV:     10%
    // UXV+:        5%
    private static final double[] RETENTION_RATIOS = {
            0.80, 0.80, 0.80, 0.80, 0.80,  // ULV, LV, MV, HV, EV
            0.80, 0.50, 0.50,               // IV, LuV, ZPM
            0.30, 0.30,                      // UV, UHV
            0.10, 0.10, 0.10,               // UEV, UIV, UXV
            0.05, 0.05                       // OpV, MAX
    };

    // Wireless amperage by tier — higher tier = more concurrent transfer throughput
    // ULV-HV: 1A, EV-IV: 2A, LuV-ZPM: 4A, UV-UHV: 8A, UEV+: 16A
    private static final long[] WIRELESS_AMPERAGES = {
            1, 1, 1, 1, 1,    // ULV, LV, MV, HV, EV
            2, 4, 4,          // IV, LuV, ZPM
            8, 8,             // UV, UHV
            16, 16, 16,       // UEV, UIV, UXV
            16, 16            // OpV, MAX
    };

    private int priority;
    private int rebalanceTimer = 0;
    private long lastLocalStored; // dirty detection: only rebalance when PSS stored changes

    public MetaTileEntityWirelessController(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        this.priority = tier;
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

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("安装在已成形的PSS上后，PSS将通过无线网络与其他设备交换能量。"));
        tooltip.add(I18n.format("PSS的存储能量将定期与无线网络进行双向重平衡："));
        tooltip.add(I18n.format("· 本地能量超出阈值 → 自动推入无线网络供远程使用"));
        tooltip.add(I18n.format("· 本地能量不足阈值 → 自动从无线网络提取补充"));
        tooltip.add(I18n.format("监控器等级越高，本地保留比例越低，传输速度越快。"));
        tooltip.add(I18n.format("FTB同组玩家自动共享同一网络，无需额外操作。"));
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

    // ==================== Wireless Network Binding ====================

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
     * Only executes when PSS local stored has actually changed (dirty detection).
     * <p>
     * Threshold = PSS capacity × retentionRatio[tier]
     * - PSS stored > threshold → push excess to wireless pool
     * - PSS stored < threshold → pull deficit from wireless pool
     */
    private void tryRebalance(MetaTileEntityPowerSubstation pss) {
        long currentStored = pss.getStoredLong();
        // Dirty check: only rebalance if local stored actually changed
        if (currentStored == lastLocalStored) return;
        lastLocalStored = currentStored;

        UUID ownerId = this.getOwnerGT();
        if (ownerId == null) return;

        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service == null) return;

        BigInteger capacity = pss.getCapacityByBigInteger();
        if (capacity.signum() == 0) return;

        double ratio = getRetentionRatio();
        BigInteger threshold = new BigDecimal(capacity)
                .multiply(BigDecimal.valueOf(ratio))
                .toBigInteger();

        BigInteger localStored = pss.getStoredByBigInteger();
        long maxTransfer = getMaxTransferPerTick() * REBALANCE_INTERVAL;

        if (localStored.compareTo(threshold) > 0) {
            // PSS has excess → push to wireless pool
            BigInteger excess = localStored.subtract(threshold);
            if (excess.compareTo(BigInteger.valueOf(maxTransfer)) > 0) {
                excess = BigInteger.valueOf(maxTransfer);
            }
            if (excess.signum() <= 0) return;
            TransferResult result = service.insert(ownerId, excess.longValue(), TransferContext.PSS_REBALANCE);
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
            TransferResult result = service.extractUpTo(ownerId, deficit.longValue(), TransferContext.PSS_REBALANCE);
            if (result.isSuccess() && result.getAmountLong() > 0) {
                pss.externalFill(result.getAmountLong());
                lastLocalStored = pss.getStoredLong();
            }
        }
    }

    private double getRetentionRatio() {
        int tier = getTier();
        if (tier < 0) tier = 0;
        if (tier >= RETENTION_RATIOS.length) tier = RETENTION_RATIOS.length - 1;
        return RETENTION_RATIOS[tier];
    }

    private long getMaxTransferPerTick() {
        int tier = getTier();
        if (tier < 0) tier = 0;
        if (tier >= GTValues.V.length) tier = GTValues.V.length - 1;
        long amperage = WIRELESS_AMPERAGES[tier >= WIRELESS_AMPERAGES.length ? WIRELESS_AMPERAGES.length - 1 : tier];
        return GTValues.V[tier] * amperage;
    }

    // ==================== IWirelessController ====================

    @Override
    public void sentMTE() {
        // Wireless pool is now stateless with respect to nodes — no registration needed.
        // Initialize dirty tracking on next rebalance.
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

    // ==================== Persistence ====================

    @Override
    public NBTTagCompound writeToNBT(@NotNull NBTTagCompound data) {
        data.setInteger("priority", this.priority);
        data.setLong("lastLocalStored", this.lastLocalStored);
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.priority = data.getInteger("priority");
        this.lastLocalStored = data.getLong("lastLocalStored");
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(this.priority);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.priority = buf.readInt();
    }
}
