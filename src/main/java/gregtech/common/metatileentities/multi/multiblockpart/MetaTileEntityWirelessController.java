package gregtech.common.metatileentities.multi.multiblockpart;

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
        tooltip.add(I18n.format("安装在已成形的PSS上后，PSS将通过无线网络与其他设备交换能量。"));
        tooltip.add(I18n.format("PSS的存储能量将定期与无线网络进行双向重平衡："));
        tooltip.add(I18n.format("· 本地能量超出阈值 → 自动推入无线网络供远程使用"));
        tooltip.add(I18n.format("· 本地能量不足阈值 → 自动从无线网络提取补充"));
        tooltip.add(I18n.format("§e传输速率公式:"));
        tooltip.add(I18n.format("§f  7 * Σ(N_i * V_i * 2^(T_i - UHV) * 5^(C - UHV)) EU/t"));
        tooltip.add(I18n.format("§7  N_i = 该等级电容数量, V_i = 电容电压"));
        tooltip.add(I18n.format("§7  T_i = 电容等级, C = 监控器等级"));
        tooltip.add(I18n.format("§7  仅UHV及以上等级的电容参与计算"));
        int tier = getTier();
        int index = Math.min(tier - GTValues.UHV, RETENTION_RATIOS.length - 1);
        double ratio = RETENTION_RATIOS[Math.max(index, 0)];
        tooltip.add(I18n.format("§b本地保留比例: §f" + (int) (ratio * 100) + "%%"));
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
