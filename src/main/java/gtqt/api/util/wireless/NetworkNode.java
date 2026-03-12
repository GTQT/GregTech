package gtqt.api.util.wireless;

import gtqt.common.metatileentities.multi.multiblockpart.MetaTileEntityWirelessController;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class NetworkNode {

    private final UUID ownerUUID;
    private final List<MetaTileEntityWirelessController> hatches;
    private String networkName;

    public NetworkNode(UUID owner, String name) {
        this.ownerUUID = owner;
        this.networkName = name;
        this.hatches = new ArrayList<>();
    }

    // ==================== Getter / Setter ====================

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getNetworkName() {
        return networkName;
    }

    public void setNetworkName(String networkName) {
        this.networkName = networkName;
    }

    // ==================== 能量格式化方法 ====================

    public String getEnergyContainer() {
        BigInteger totalStored = getTotalStored();
        return formatScientificNotation(totalStored) + " EU (" +
                formatEnergyValue(totalStored) + ")";
    }

    private String formatScientificNotation(BigInteger energy) {
        double value = energy.doubleValue();
        return String.format("%.3E", value);
    }

    private String formatEnergyValue(BigInteger energy) {
        if (energy.compareTo(BigInteger.valueOf(1_000_000_000L)) >= 0) {
            return energy.divide(BigInteger.valueOf(1_000_000_000L)) + " GE";
        } else if (energy.compareTo(BigInteger.valueOf(1_000_000L)) >= 0) {
            return energy.divide(BigInteger.valueOf(1_000_000L)) + " ME";
        } else if (energy.compareTo(BigInteger.valueOf(1_000L)) >= 0) {
            return energy.divide(BigInteger.valueOf(1_000L)) + " KE";
        } else {
            return energy + " EU";
        }
    }

    // ==================== 仓室管理（线程安全）====================

    /**
     * 添加一个新的无线仓室到网络
     */
    public synchronized void addNewHatch(MetaTileEntityWirelessController hatch) {
        if (!hatches.contains(hatch)) {
            hatches.add(hatch);
        }
    }

    /**
     * 从网络中移除一个无线仓室
     */
    public synchronized void removeHatch(MetaTileEntityWirelessController hatch) {
        hatches.remove(hatch);
    }

    /**
     * 获取当前所有仓室的副本（用于外部遍历，避免并发修改）
     */
    public synchronized List<MetaTileEntityWirelessController> getHatches() {
        return new ArrayList<>(hatches);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 移除无效的仓室（如多方块未成形或已失效）
     */
    private synchronized void removeInvalidHatches() {
        hatches.removeIf(hatch -> hatch == null || !hatch.getController().isStructureFormed());
    }

    /**
     * 获取按优先级升序排序的仓室列表（先清理无效仓室）
     */
    private synchronized List<MetaTileEntityWirelessController> getSortedHatches() {
        removeInvalidHatches();
        List<MetaTileEntityWirelessController> sorted = new ArrayList<>(hatches);
        sorted.sort(Comparator.comparingInt(MetaTileEntityWirelessController::getPriority));
        return sorted;
    }

    // ==================== 能量操作 ====================

    /**
     * 向网络填充能量，返回实际填充量
     */
    public synchronized long fill(long amount) {
        if (amount <= 0) return 0;
        long remaining = amount;
        for (MetaTileEntityWirelessController hatch : getSortedHatches()) {
            if (remaining <= 0) break;
            long filled = hatch.fill(remaining);
            remaining -= filled;
        }
        return amount - remaining;
    }

    /**
     * 从网络抽取能量，返回实际抽取量
     */
    public synchronized long drain(long amount) {
        if (amount <= 0) return 0;
        long remaining = amount;
        for (MetaTileEntityWirelessController hatch : getSortedHatches()) {
            if (remaining <= 0) break;
            long drained = hatch.drain(remaining);
            remaining -= drained;
        }
        return amount - remaining;
    }

    /**
     * 获取网络总容量（所有仓室容量之和）
     */
    public BigInteger getTotalCapacity() {
        BigInteger total = BigInteger.ZERO;
        for (MetaTileEntityWirelessController hatch : getHatches()) {
            total = total.add(hatch.getCapacity());
        }
        return total;
    }

    /**
     * 获取网络当前总存储量（所有仓室存储之和）
     */
    public BigInteger getTotalStored() {
        BigInteger total = BigInteger.ZERO;
        for (MetaTileEntityWirelessController hatch : getHatches()) {
            total = total.add(hatch.getStored());
        }
        return total;
    }

    /**
     * 修改网络能量（通过物理仓室操作），返回实际变动量。
     * @param delta 正数表示向网络填充能量，负数表示从网络抽取能量
     * @return 实际改变的能量值：正数表示成功填充的量，负数表示成功抽取的量
     */
    public synchronized BigInteger modifyEnergy(BigInteger delta) {
        if (delta.signum() > 0) {
            // 向网络填充能量
            long filled = fill(delta.longValue());
            return BigInteger.valueOf(filled);
        } else if (delta.signum() < 0) {
            // 从网络抽取能量
            long drained = drain(-delta.longValue());
            return BigInteger.valueOf(-drained);
        } else {
            return BigInteger.ZERO;
        }
    }
}
