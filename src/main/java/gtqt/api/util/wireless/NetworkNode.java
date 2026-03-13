package gtqt.api.util.wireless;

import gregtech.api.util.GTUtility;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

import gtqt.common.metatileentities.multi.multiblockpart.MetaTileEntityWirelessController;
import lombok.Getter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class NetworkNode {

    public final List<HatchLocation> hatchLocations;
    @Getter
    private final UUID ownerUUID;
    @Getter
    private String networkName;

    @Getter
    private BigInteger totalInput = BigInteger.ZERO;
    @Getter
    private BigInteger totalOutput = BigInteger.ZERO;

    public NetworkNode(UUID owner, String name) {
        this.ownerUUID = owner;
        this.networkName = name;
        this.hatchLocations = new ArrayList<>();
    }

    public void setNetworkName(String networkName) {this.networkName = networkName;}

    public List<HatchLocation> getHatchLocations() {
        return new ArrayList<>(hatchLocations);
    }

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

    /**
     * 动态解析当前已加载的无线仓室实例 调用方应始终使用此方法替代直接访问 hatches
     */
    public List<MetaTileEntityWirelessController> getLoadedHatches() {
        List<MetaTileEntityWirelessController> loaded = new ArrayList<>();
        for (HatchLocation loc : hatchLocations) {
            World world = getWorldByDimension(loc.dimension);
            if (world.isBlockLoaded(loc.pos)) {
                var mte = GTUtility.getMetaTileEntity(world, loc.pos);
                if (mte instanceof MetaTileEntityWirelessController controller) {
                    loaded.add(controller);
                }
            }
        }
        return loaded;
    }

    // ==================== 核心：动态获取已加载仓室 ====================

    /**
     * 添加仓室：同时记录位置信息
     */
    public void addNewHatch(MetaTileEntityWirelessController hatch) {
        if (hatch.getWorld() == null) return;
        HatchLocation loc = new HatchLocation(
                hatch.getWorld().provider.getDimension(),
                hatch.getPos()
        );
        if (!hatchLocations.contains(loc)) {
            hatchLocations.add(loc);
        }
    }

    // ==================== 仓室管理（线程安全）====================

    /**
     * 移除仓室：通过实例反查位置并移除
     */
    public void removeHatch(MetaTileEntityWirelessController hatch) {
        if (hatch.getWorld() == null) return;
        HatchLocation loc = new HatchLocation(
                hatch.getWorld().provider.getDimension(),
                hatch.getPos()
        );
        hatchLocations.remove(loc);
    }

    /**
     * 通过位置直接移除（用于清理无效数据）
     */
    public boolean removeHatchByLocation(HatchLocation loc) {
        return hatchLocations.remove(loc);
    }

    /**
     * 清理无效仓室位置： - 区块已加载但控制器不存在/未成形/非目标类型
     */
    private void removeInvalidHatchLocations() {
        Iterator<HatchLocation> iterator = hatchLocations.iterator();
        while (iterator.hasNext()) {
            HatchLocation loc = iterator.next();
            World world = getWorldByDimension(loc.dimension);
            if (world.isBlockLoaded(loc.pos)) {
                // 区块已加载，可以验证有效性
                var mte = GTUtility.getMetaTileEntity(world, loc.pos);
                if (!(mte instanceof MetaTileEntityWirelessController controller) ||
                        !controller.getController().isStructureFormed()) {
                    iterator.remove(); // 无效，移除位置记录
                }
            }
            // 区块未加载则保留位置，等待下次检查
        }
    }

    // ==================== 内部工具方法 ====================

    private World getWorldByDimension(int dimension) {
        return FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(dimension);
    }

    /**
     * 获取按优先级升序排序的已加载仓室列表
     */
    private List<MetaTileEntityWirelessController> getSortedHatches() {
        removeInvalidHatchLocations();
        List<MetaTileEntityWirelessController> sorted = getLoadedHatches();
        sorted.sort(Comparator.comparingInt(MetaTileEntityWirelessController::getPriority));
        return sorted;
    }

    public long fill(long amount) {
        if (amount <= 0) return 0;
        long remaining = amount;
        for (MetaTileEntityWirelessController hatch : getSortedHatches()) {
            if (remaining <= 0) break;
            long filled = hatch.fill(remaining);
            remaining -= filled;
        }
        long filledAmount = amount - remaining;
        if (filledAmount > 0) {
            totalInput = totalInput.add(BigInteger.valueOf(filledAmount));
        }
        return filledAmount;
    }

    // ==================== 能量操作 ====================

    public long drain(long amount) {
        if (amount <= 0) return 0;
        long remaining = amount;
        for (MetaTileEntityWirelessController hatch : getSortedHatches()) {
            if (remaining <= 0) break;
            long drained = hatch.drain(remaining);
            remaining -= drained;
        }
        long drainedAmount = amount - remaining;
        if (drainedAmount > 0) {
            totalOutput = totalOutput.add(BigInteger.valueOf(drainedAmount));
        }
        return drainedAmount;
    }

    public BigInteger getTotalCapacity() {
        BigInteger total = BigInteger.ZERO;
        for (MetaTileEntityWirelessController hatch : getLoadedHatches()) {
            total = total.add(hatch.getCapacity());
        }
        return total;
    }

    public void resetStats() {
        totalInput = BigInteger.ZERO;
        totalOutput = BigInteger.ZERO;
    }

    public BigInteger getTotalStored() {
        BigInteger total = BigInteger.ZERO;
        for (MetaTileEntityWirelessController hatch : getLoadedHatches()) {
            total = total.add(hatch.getStored());
        }
        return total;
    }

    public static class HatchLocation {
        public final int dimension;
        public final BlockPos pos;

        public HatchLocation(int dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos.toImmutable(); // 确保不可变
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HatchLocation that)) return false;
            return dimension == that.dimension && pos.equals(that.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dimension, pos);
        }

        // 用于 NBT 序列化
        public net.minecraft.nbt.NBTTagCompound writeToNBT() {
            net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
            tag.setInteger("dim", dimension);
            tag.setInteger("x", pos.getX());
            tag.setInteger("y", pos.getY());
            tag.setInteger("z", pos.getZ());
            return tag;
        }

        // 用于 NBT 反序列化
        public static HatchLocation readFromNBT(net.minecraft.nbt.NBTTagCompound tag) {
            return new HatchLocation(
                    tag.getInteger("dim"),
                    new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"))
            );
        }
    }
}
