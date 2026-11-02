package gregtech.api.capability.impl;

import gregtech.api.capability.IEnergyContainer;

import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EnergyContainerList implements IEnergyContainer {

    private final List<IEnergyContainer> energyContainerList;

    /** 对外的电压/电流，由各容器当前的数值汇总得到，见 {@link #refresh()}。 */
    private long inputVoltage;
    private long outputVoltage;
    private long inputAmperage;
    private long outputAmperage;
    /** The highest single energy container's input voltage in the list. */
    private long highestInputVoltage;
    /** The number of energy containers at the highest input voltage in the list. */
    private int numHighestInputContainers;

    /** 上次汇总时的原始数据，用来判断是否需要重做压缩计算。 */
    private boolean initialised;
    private int cachedSize;
    private long cachedTotalInputVoltage;
    private long cachedTotalOutputVoltage;
    private long cachedInputAmperage;
    private long cachedOutputAmperage;
    private long cachedHighestInputVoltage;
    private int cachedNumHighestInputContainers;

    public EnergyContainerList(@NotNull List<IEnergyContainer> energyContainerList) {
        this.energyContainerList = energyContainerList;
    }

    /**
     * Computes the correct max voltage and amperage values
     *
     * @param voltage  the sum of voltage * amperage for each hatch
     * @param amperage the total amperage of all hatches
     * @return [newVoltage, newAmperage]
     */
    @NotNull
    private static long[] calculateVoltageAmperage(long voltage, long amperage) {
        if (voltage > 1 && amperage > 1) {
            // don't operate if there is no voltage or no amperage
            if (hasPrimeFactorGreaterThanTwo(amperage)) {
                // scenarios like 3A, 5A, 6A, etc.
                // treated as 1A of the sum of voltage * amperage for each hatch
                amperage = 1;
            } else if (isPowerOfFour(amperage)) {
                // scenarios like 4A, 16A, etc.
                // treated as 1A of the sum of voltage * amperage for each hatch
                amperage = 1;
            } else if (amperage % 4 == 0) {
                // scenarios like 8A, 32A, etc.
                // reduced to an amperage < 4 and equivalent voltage for the new amperage
                while (amperage > 4) {
                    amperage /= 4;
                }
                voltage /= amperage;
            } else if (amperage == 2) {
                // exactly 2A, all other cases covered by earlier checks
                // reduced to the voltage per amp
                voltage /= amperage;
            } else {
                // fallback case, that should never be hit
                // forced to 1A to prevent excess power draw/output if something falls through
                amperage = 1;
            }
        }
        return new long[] { voltage, amperage };
    }

    private static boolean hasPrimeFactorGreaterThanTwo(long l) {
        int i = 2;
        final long max = l / 2;
        while (i <= max) {
            if (l % i == 0) {
                if (i > 2) return true;
                l /= i;
            } else {
                i++;
            }
        }
        return false;
    }

    /**
     * Checks if a number is a power of 4. Does not include 1, despite it being 4**0.
     */
    private static boolean isPowerOfFour(long l) {
        if (l == 0) return false;
        if ((l & (l - 1)) != 0) return false;
        return (l & 0x55555555) != 0;
    }

    private void refresh() {
        List<IEnergyContainer> containers = this.energyContainerList;
        long totalInputVoltage = 0;
        long totalOutputVoltage = 0;
        long totalInputAmperage = 0;
        long totalOutputAmperage = 0;
        long highestInputVoltage = 0;
        for (IEnergyContainer container : containers) {
            long inputVoltage = container.getInputVoltage();
            long inputAmperage = container.getInputAmperage();
            long outputVoltage = container.getOutputVoltage();
            long outputAmperage = container.getOutputAmperage();
            totalInputVoltage += inputVoltage * inputAmperage;
            totalOutputVoltage += outputVoltage * outputAmperage;
            totalInputAmperage += inputAmperage;
            totalOutputAmperage += outputAmperage;
            if (inputVoltage > highestInputVoltage) {
                highestInputVoltage = inputVoltage;
            }
        }
        int numHighestInputContainers = 0;
        for (IEnergyContainer container : containers) {
            if (container.getInputVoltage() == highestInputVoltage) {
                numHighestInputContainers++;
            }
        }

        if (this.initialised && containers.size() == this.cachedSize &&
                totalInputVoltage == this.cachedTotalInputVoltage &&
                totalOutputVoltage == this.cachedTotalOutputVoltage &&
                totalInputAmperage == this.cachedInputAmperage &&
                totalOutputAmperage == this.cachedOutputAmperage &&
                highestInputVoltage == this.cachedHighestInputVoltage &&
                numHighestInputContainers == this.cachedNumHighestInputContainers) {
            return;
        }

        this.initialised = true;
        this.cachedSize = containers.size();
        this.cachedTotalInputVoltage = totalInputVoltage;
        this.cachedTotalOutputVoltage = totalOutputVoltage;
        this.cachedInputAmperage = totalInputAmperage;
        this.cachedOutputAmperage = totalOutputAmperage;
        this.cachedHighestInputVoltage = highestInputVoltage;
        this.cachedNumHighestInputContainers = numHighestInputContainers;

        long[] voltageAmperage = calculateVoltageAmperage(totalInputVoltage, totalInputAmperage);
        this.inputVoltage = voltageAmperage[0];
        this.inputAmperage = voltageAmperage[1];
        voltageAmperage = calculateVoltageAmperage(totalOutputVoltage, totalOutputAmperage);
        this.outputVoltage = voltageAmperage[0];
        this.outputAmperage = voltageAmperage[1];
        this.highestInputVoltage = highestInputVoltage;
        this.numHighestInputContainers = numHighestInputContainers;
    }

    @Override
    public long getInputPerSec() {
        long sum = 0;
        List<IEnergyContainer> energyContainerList = this.energyContainerList;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            sum += iEnergyContainer.getInputPerSec();
        }
        return sum;
    }

    @Override
    public long getOutputPerSec() {
        long sum = 0;
        List<IEnergyContainer> energyContainerList = this.energyContainerList;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            sum += iEnergyContainer.getOutputPerSec();
        }
        return sum;
    }

    @Override
    public long acceptEnergyFromNetwork(EnumFacing side, long voltage, long amperage) {
        long amperesUsed = 0L;
        List<IEnergyContainer> energyContainerList = this.energyContainerList;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            amperesUsed += iEnergyContainer.acceptEnergyFromNetwork(null, voltage, amperage);
            if (amperage == amperesUsed) {
                return amperesUsed;
            }
        }
        return amperesUsed;
    }

    @Override
    public long changeEnergy(long energyToAdd) {
        long energyAdded = 0L;
        List<IEnergyContainer> energyContainerList = this.energyContainerList;

        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            energyAdded += iEnergyContainer.changeEnergy(energyToAdd - energyAdded);
            if (energyAdded == energyToAdd) {
                return energyAdded;
            }
        }
        return energyAdded;
    }

    @Override
    public long getEnergyStored() {
        long energyStored = 0L;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            energyStored += iEnergyContainer.getEnergyStored();
        }
        return energyStored;
    }

    @Override
    public long getEnergyCapacity() {
        long energyCapacity = 0L;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            energyCapacity += iEnergyContainer.getEnergyCapacity();
        }
        return energyCapacity;
    }

    /** The highest single voltage of an energy container in this list. */
    public long getHighestInputVoltage() {
        refresh();
        return highestInputVoltage;
    }

    /**
     * The number of parts with voltage specified in {@link EnergyContainerList#getHighestInputVoltage()} in this list.
     */
    public int getNumHighestInputContainers() {
        refresh();
        return numHighestInputContainers;
    }

    /**
     * Always < 4. A list with amps > 4 will always be compacted into more voltage at fewer amps.
     *
     * @return maximum amount of receivable energy packets per tick
     */
    @Override
    public long getInputAmperage() {
        refresh();
        return this.inputAmperage;
    }

    /**
     * Always < 4. A list with amps > 4 will always be compacted into more voltage at fewer amps.
     *
     * @return maximum amount of output-able energy packets per tick
     */
    @Override
    public long getOutputAmperage() {
        refresh();
        return this.outputAmperage;
    }

    @Override
    public long getInputVoltage() {
        refresh();
        return this.inputVoltage;
    }

    @Override
    public long getOutputVoltage() {
        refresh();
        return this.outputVoltage;
    }

    @Override
    public boolean inputsEnergy(EnumFacing side) {
        return true;
    }

    @Override
    public boolean outputsEnergy(EnumFacing side) {
        return true;
    }

    @Override
    public String toString() {
        return "EnergyContainerList{" +
                "energyContainerList=" + energyContainerList +
                ", energyStored=" + getEnergyStored() +
                ", energyCapacity=" + getEnergyCapacity() +
                ", inputVoltage=" + getInputVoltage() +
                ", inputAmperage=" + getInputAmperage() +
                ", outputVoltage=" + getOutputVoltage() +
                ", outputAmperage=" + getOutputAmperage() +
                '}';
    }
}
