package gregtech.common.metatileentities.multi.electric.godforge.upgrade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.stream.Stream;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;

public class UpgradeStorage {

    private final EnumMap<ForgeOfGodsUpgrade, UpgradeData> unlockedUpgrades = new EnumMap<>(ForgeOfGodsUpgrade.class);

    public UpgradeStorage() {
        for (ForgeOfGodsUpgrade upgrade : ForgeOfGodsUpgrade.VALUES) {
            unlockedUpgrades.put(upgrade, new UpgradeData());
        }
    }

    public boolean isUpgradeActive(ForgeOfGodsUpgrade upgrade) {
        return getData(upgrade).isActive();
    }

    public boolean isCostPaid(ForgeOfGodsUpgrade upgrade) {
        return getData(upgrade).isCostPaid();
    }

    public short[] getPaidCosts(ForgeOfGodsUpgrade upgrade) {
        return getData(upgrade).amountsPaid;
    }

    public void payCost(ForgeOfGodsUpgrade upgrade, ItemStack[] inputStacks) {
        UpgradeData data = getData(upgrade);

        if (!upgrade.hasExtraCost()) {
            data.costPaid = true;
            return;
        }

        ItemStack[] extraCost = upgrade.getExtraCost();
        for (int i = 0; i < inputStacks.length; i++) {
            ItemStack inputStack = inputStacks[i];
            if (inputStack == null) continue;

            for (int j = 0; j < extraCost.length; j++) {
                ItemStack costStack = extraCost[j];
                if (costStack == null) continue;
                int alreadyPaid = data.amountsPaid[j];
                if (alreadyPaid >= costStack.getCount()) continue;

                if (ItemStack.areItemStacksEqual(inputStack, costStack)) {
                    int maxExtract = costStack.getCount() - alreadyPaid;
                    int extractAmount = Math.min(maxExtract, inputStack.getCount());
                    if (extractAmount > 0) {
                        data.amountsPaid[j] += (short) extractAmount;
                        inputStack.setCount(inputStack.getCount() - extractAmount);
                        if (inputStack.getCount() == 0) {
                            inputStacks[i] = null;
                        }
                    }
                }
            }
        }

        for (int i = 0; i < extraCost.length; i++) {
            ItemStack costStack = extraCost[i];
            if (costStack == null) continue;
            if (data.amountsPaid[i] < costStack.getCount()) {
                return;
            }
        }
        data.costPaid = true;
    }

    public void unlockUpgrade(ForgeOfGodsUpgrade upgrade) {
        getData(upgrade).active = true;
    }

    public void respecUpgrade(ForgeOfGodsUpgrade upgrade) {
        getData(upgrade).active = false;
    }

    public boolean checkPrerequisites(ForgeOfGodsUpgrade upgrade) {
        ForgeOfGodsUpgrade[] prereqs = upgrade.getPrerequisites();
        if (prereqs.length == 0) return true;

        Stream<UpgradeData> prereqStream = Arrays.stream(prereqs)
            .map(unlockedUpgrades::get);

        if (upgrade.requiresAllPrerequisites()) {
            return prereqStream.allMatch(UpgradeData::isActive);
        }
        return prereqStream.anyMatch(UpgradeData::isActive);
    }

    public boolean checkSplit(ForgeOfGodsUpgrade upgrade, int maxSplitUpgrades) {
        if (ForgeOfGodsUpgrade.SPLIT_UPGRADES.contains(upgrade)) {
            return ForgeOfGodsUpgrade.SPLIT_UPGRADES.stream()
                .map(unlockedUpgrades::get)
                .filter(UpgradeData::isActive)
                .count() < maxSplitUpgrades;
        }
        return true;
    }

    public boolean checkCost(ForgeOfGodsUpgrade upgrade, int availableShards) {
        if (upgrade.getShardCost() > availableShards) return false;
        return !upgrade.hasExtraCost() || isCostPaid(upgrade);
    }

    public boolean checkDependents(ForgeOfGodsUpgrade upgrade) {
        for (ForgeOfGodsUpgrade dependent : upgrade.getDependents()) {
            if (!isUpgradeActive(dependent)) continue;

            if (dependent.requiresAllPrerequisites()) return false;

            if (Arrays.stream(dependent.getPrerequisites())
                .map(unlockedUpgrades::get)
                .filter(UpgradeData::isActive)
                .count() <= 1) {
                return false;
            }
        }
        return true;
    }

    private UpgradeData getData(ForgeOfGodsUpgrade upgrade) {
        return unlockedUpgrades.computeIfAbsent(upgrade, $ -> new UpgradeData());
    }

    public int getTotalActiveUpgrades() {
        return (int) unlockedUpgrades.values()
            .stream()
            .filter(UpgradeData::isActive)
            .count();
    }

    public Collection<ForgeOfGodsUpgrade> getAllUpgrades() {
        return unlockedUpgrades.keySet();
    }

    public GenericListSyncHandler<?> getFullSyncer() {
        return GenericListSyncHandler.<UpgradeData>builder()
            .getter(() -> new ArrayList<>(unlockedUpgrades.values()))
            .setter(values -> {
                for (int i = 0; i < values.size() && i < ForgeOfGodsUpgrade.VALUES.length; i++) {
                    unlockedUpgrades.put(ForgeOfGodsUpgrade.VALUES[i], values.get(i));
                }
            })
            .serializer(UpgradeData::writeToBuffer)
            .deserializer(UpgradeData::readFromBuffer)
            .build();
    }

    public void resetAll() {
        for (UpgradeData data : unlockedUpgrades.values()) {
            data.active = false;
            data.costPaid = false;
        }
    }

    public void unlockAll() {
        for (UpgradeData data : unlockedUpgrades.values()) {
            data.active = true;
        }
    }

    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagCompound upgradeTag = new NBTTagCompound();
        for (ForgeOfGodsUpgrade upgrade : ForgeOfGodsUpgrade.VALUES) {
            UpgradeData data = unlockedUpgrades.get(upgrade);
            upgradeTag.setBoolean("upgrade" + upgrade.ordinal(), data.isActive());
            if (upgrade.hasExtraCost()) {
                NBTTagCompound costTag = new NBTTagCompound();
                costTag.setBoolean("paid", data.isCostPaid());
                for (int i = 0; i < data.amountsPaid.length; i++) {
                    costTag.setShort("costPaid" + i, data.amountsPaid[i]);
                }
                upgradeTag.setTag("extraCost" + upgrade.ordinal(), costTag);
            }
        }
        nbt.setTag("upgrades", upgradeTag);
    }

    public void readFromNBT(NBTTagCompound nbt) {
        if (!nbt.hasKey("upgrades")) return;

        NBTTagCompound upgradeTag = nbt.getCompoundTag("upgrades");
        for (int i = 0; i < ForgeOfGodsUpgrade.VALUES.length; i++) {
            ForgeOfGodsUpgrade upgrade = ForgeOfGodsUpgrade.VALUES[i];
            UpgradeData data = unlockedUpgrades.get(upgrade);
            data.active = upgradeTag.getBoolean("upgrade" + upgrade.ordinal());
            if (upgrade.hasExtraCost() && upgradeTag.hasKey("extraCost" + upgrade.ordinal())) {
                NBTTagCompound costTag = upgradeTag.getCompoundTag("extraCost" + upgrade.ordinal());
                data.costPaid = costTag.getBoolean("paid");
                for (int j = 0; j < data.amountsPaid.length; j++) {
                    data.amountsPaid[j] = costTag.getShort("costPaid" + j);
                }
            }
        }
    }

    private static class UpgradeData {

        private boolean active;
        private boolean costPaid;
        private final short[] amountsPaid = new short[12];

        public boolean isActive() {
            return active;
        }

        public boolean isCostPaid() {
            return costPaid;
        }

        private static void writeToBuffer(PacketBuffer buf, UpgradeData data) {
            buf.writeBoolean(data.isActive());
            buf.writeBoolean(data.isCostPaid());
            for (short amountPaid : data.amountsPaid) {
                buf.writeShort(amountPaid);
            }
        }

        private static UpgradeData readFromBuffer(PacketBuffer buf) {
            UpgradeData data = new UpgradeData();
            data.active = buf.readBoolean();
            data.costPaid = buf.readBoolean();
            for (int i = 0; i < data.amountsPaid.length; i++) {
                data.amountsPaid[i] = buf.readShort();
            }
            return data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            UpgradeData that = (UpgradeData) o;

            if (active != that.active) return false;
            if (costPaid != that.costPaid) return false;
            return Arrays.equals(amountsPaid, that.amountsPaid);
        }

        @Override
        public int hashCode() {
            int result = (active ? 1 : 0);
            result = 31 * result + (costPaid ? 1 : 0);
            result = 31 * result + Arrays.hashCode(amountsPaid);
            return result;
        }
    }
}
