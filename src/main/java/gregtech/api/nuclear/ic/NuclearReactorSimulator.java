package gregtech.api.nuclear.ic;

import gregtech.api.GTValues;
import gregtech.api.util.GTLog;
import gregtech.common.items.behaviors.nuclear.ComponentHeatExchangerBehavior;
import gregtech.common.items.behaviors.nuclear.ComponentHeatVentBehavior;
import gregtech.common.items.behaviors.nuclear.CoolantCellBehavior;
import gregtech.common.items.behaviors.nuclear.FuelRodBehavior;
import gregtech.common.items.behaviors.nuclear.HeatExchangerBehavior;
import gregtech.common.items.behaviors.nuclear.HeatVentBehavior;
import gregtech.common.items.behaviors.nuclear.IrradiationTargetBehavior;
import gregtech.common.items.behaviors.nuclear.NeutronReflectorBehavior;
import gregtech.common.items.behaviors.nuclear.NuclearComponentBehavior;
import gregtech.common.items.behaviors.nuclear.ReactorHeatExchangerBehavior;
import gregtech.common.items.behaviors.nuclear.ReactorPlatingBehavior;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND;

public class NuclearReactorSimulator {

    /**
     * Ticks a single simulation step covers. Fuel rod power is given per tick (EU/t) and is settled once per tick by
     * the controller; heat is settled once per step. See {@link #getEnergyPerStep()}.
     */
    public static final int TICKS_PER_STEP = 20;
    /** Heat capacity of a reactor without any reactor plating installed. */
    public static final int BASE_HEAT_CAPACITY = 10000;
    /** Efficiency bonus a single adjacent reflector grants at 100% reflection efficiency. */
    private static final float REFLECTOR_BONUS = 0.2f;
    /** A component heat vent or component heat exchanger never pulls more than this from one adjacent fuel rod. */
    private static final int MAX_COMPONENT_TRANSFER_PER_ROD = 100;
    /** The four orthogonal neighbours of a grid cell. */
    private static final int[][] NEIGHBOUR_DIRECTIONS = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };

    private ItemStack[][] componentGrid;
    @Getter
    private int gridWidth;
    @Getter
    private int gridHeight;
    /** Reflector bonus granted to the fuel rod in a cell, summed over its adjacent reflectors. */
    private float[][] reflectorBonuses;
    /**
     * Cells whose component actually did work in the current step. Only those are asked for their durability cost, so
     * an idle reactor (no fuel rods, nothing to cool) does not slowly destroy the components installed in it.
     */
    private boolean[][] workedThisStep;
    /** Heat each cell actually moved in the current step, in HU; used as the durability cost of coolant cells. */
    private int[][] movedHeat;
    private final List<GridPosition> fuelRodPositions = new ArrayList<>();
    private final List<GridPosition> heatVentPositions = new ArrayList<>();
    private final List<GridPosition> coolantCellPositions = new ArrayList<>();
    private final List<GridPosition> reflectorPositions = new ArrayList<>();
    private final List<GridPosition> irradiationTargetPositions = new ArrayList<>();
    /** All three heat exchanger types; they move heat into adjacent heat sinks instead of venting it themselves. */
    private final List<GridPosition> heatExchangerPositions = new ArrayList<>();

    @Getter
    @Setter
    private boolean transOut;
    @Getter
    @Setter
    private List<ItemStack> listToTransfer = new ArrayList<>();

    @Getter
    @Setter
    private boolean transIn;
    @Getter
    @Setter
    private List<ItemStack> listToAdd = new ArrayList<>();

    /** Heat currently stored in the reactor, in HU. Heat is settled once per step, i.e. per second. */
    @Getter
    private int currentHeat = 0;
    /** Heat the reactor can hold before it melts down, in HU. */
    @Getter
    private int maxHeatCapacity = BASE_HEAT_CAPACITY;
    /** Combined power of the installed fuel rods in EU/t, the unit fuel rods are defined in. */
    @Getter
    private long currentOutput = 0;
    @Getter
    private int currentNeutronFlux = 0;
    @Getter
    private int totalFuelRods = 0;
    @Getter
    private int totalHeatVents = 0;
    @Getter
    private int totalCoolantCells = 0;
    /** Number of installed heat exchangers of any type. */
    @Getter
    private int totalHeatExchangers = 0;
    @Getter
    private int totalReflectors = 0;
    @Getter
    private int totalPlating = 0;

    /** Number of simulation steps this reactor has run, i.e. its operating time in seconds. */
    @Getter
    private int tickCount = 0;
    @Getter
    private long totalEnergyProduced = 0;
    @Getter
    private boolean isActive = false;

    @Getter
    private float explosionResistance = 0.0f;
    /** Average reflector bonus over all fuel rods; 0.2 means +20% output. */
    private float averageReflectorBonus = 0.0f;
    private boolean hasMeltdown = false;

    public NuclearReactorSimulator(int width, int height) {
        allocateGrid(width, height);
    }

    /**
     * Re-sizes the internal grid. Components that still fit keep their coordinate and all accumulated reactor state
     * (heat, statistics and the pending transfer queues) is preserved, so growing or shrinking the reactor by
     * installing/removing extension hatches never resets a running reactor.
     */
    public void resize(int width, int height) {
        if (width == gridWidth && height == gridHeight) {
            return;
        }

        ItemStack[][] previousGrid = componentGrid;
        int previousWidth = gridWidth;
        int previousHeight = gridHeight;

        allocateGrid(width, height);

        int copyWidth = Math.min(previousWidth, gridWidth);
        int copyHeight = Math.min(previousHeight, gridHeight);
        for (int x = 0; x < copyWidth; x++) {
            for (int y = 0; y < copyHeight; y++) {
                componentGrid[x][y] = previousGrid[x][y];
            }
        }
    }

    private void allocateGrid(int width, int height) {
        this.gridWidth = Math.max(1, width);
        this.gridHeight = Math.max(1, height);
        this.componentGrid = new ItemStack[gridWidth][gridHeight];
        this.reflectorBonuses = new float[gridWidth][gridHeight];
        this.workedThisStep = new boolean[gridWidth][gridHeight];
        this.movedHeat = new int[gridWidth][gridHeight];

        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                componentGrid[x][y] = ItemStack.EMPTY;
            }
        }
    }

    public boolean simulateTick() {
        // A melted down reactor never consumes anything again, including queued input components.
        if (hasMeltdown) return false;

        if (transIn) {
            // Fill the grid in the same order the component panel displays it: row by row.
            for (int y = 0; y < gridHeight && !listToAdd.isEmpty(); y++) {
                for (int x = 0; x < gridWidth && !listToAdd.isEmpty(); x++) {
                    if (!componentGrid[x][y].isEmpty()) continue;
                    componentGrid[x][y] = listToAdd.remove(0).copy();
                }
            }
            if (listToAdd.isEmpty()) {
                transIn = false;
            }
        }

        tickCount++;
        currentOutput = 0;

        clearCaches();

        collectComponentInfo();

        calculateReflectorBonus();

        calculateFuelRodOutput();

        applyHeatDissipation();

        applyCooling();

        processIrradiationTargets();

        calculateHeatBalance();

        checkSafetyStatus();

        updateComponentDurability();

        updateStatistics();

        return !hasMeltdown;
    }

    private void collectComponentInfo() {
        totalFuelRods = 0;
        totalHeatVents = 0;
        totalCoolantCells = 0;
        totalHeatExchangers = 0;
        totalReflectors = 0;
        totalPlating = 0;
        explosionResistance = 0.0f;
        maxHeatCapacity = BASE_HEAT_CAPACITY;

        fuelRodPositions.clear();
        heatVentPositions.clear();
        coolantCellPositions.clear();
        reflectorPositions.clear();
        irradiationTargetPositions.clear();
        heatExchangerPositions.clear();

        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                ItemStack stack = componentGrid[x][y];
                if (stack.isEmpty()) continue;

                if (isFuelRod(stack)) {
                    fuelRodPositions.add(new GridPosition(x, y));
                    totalFuelRods++;
                } else if (isHeatVent(stack) || isComponentHeatVent(stack)) {
                    // Both vent types belong in the same list: ordinary vents cool the whole reactor,
                    // component vents cool the fuel rods they touch (see applyHeatDissipation).
                    heatVentPositions.add(new GridPosition(x, y));
                    totalHeatVents++;
                } else if (isHeatExchanger(stack) || isComponentHeatExchanger(stack) ||
                        isReactorHeatExchanger(stack)) {
                    heatExchangerPositions.add(new GridPosition(x, y));
                    totalHeatExchangers++;
                } else if (isCoolantCell(stack)) {
                    coolantCellPositions.add(new GridPosition(x, y));
                    totalCoolantCells++;
                } else if (isNeutronReflector(stack)) {
                    reflectorPositions.add(new GridPosition(x, y));
                    totalReflectors++;
                } else if (isReactorPlating(stack)) {
                    totalPlating++;
                    ReactorPlatingBehavior plating = getPlatingBehavior(stack);
                    if (plating != null) {
                        maxHeatCapacity += plating.getHeatCapacityBoost();
                        explosionResistance = Math.min(1.0f,
                                explosionResistance + plating.getExplosionResistance());
                    }
                } else if (isIrradiationTarget(stack)) {
                    irradiationTargetPositions.add(new GridPosition(x, y));
                }
            }
        }
    }

    private void calculateReflectorBonus() {
        for (GridPosition pos : fuelRodPositions) {
            float bonus = 0.0f;
            for (int[] direction : NEIGHBOUR_DIRECTIONS) {
                int nx = pos.x + direction[0];
                int ny = pos.y + direction[1];
                if (!isValidPosition(nx, ny)) continue;

                NeutronReflectorBehavior reflector = getNeutronReflectorBehavior(componentGrid[nx][ny]);
                if (reflector == null || reflector.getReflectionEfficiency() <= 0.0f) continue;

                // A reflector only bounces back a fraction of the neutrons, given by its reflection efficiency.
                bonus += REFLECTOR_BONUS * reflector.getReflectionEfficiency();
                workedThisStep[nx][ny] = true;
            }
            reflectorBonuses[pos.x][pos.y] = bonus;
        }
    }

    private void calculateFuelRodOutput() {
        long totalEnergy = 0;
        int totalHeat = 0;
        int totalNeutronFlux = 0;
        float totalReflectorBonus = 0.0f;
        int rods = 0;

        for (GridPosition pos : fuelRodPositions) {
            ItemStack stack = componentGrid[pos.x][pos.y];
            FuelRodBehavior fuelRod = getFuelRodBehavior(stack);
            if (fuelRod == null) continue;

            float rodReflectorBonus = reflectorBonuses[pos.x][pos.y];
            float outputMultiplier = 1.0f + rodReflectorBonus;

            totalEnergy += (long) (fuelRod.getEnergyOutput() * outputMultiplier);
            totalHeat += (int) (fuelRod.getHeatOutput() * outputMultiplier);
            totalNeutronFlux += Math.round(fuelRod.getNeutronEmission() * outputMultiplier * 100.0f);

            totalReflectorBonus += rodReflectorBonus;
            rods++;
            // A running fuel rod is burning fuel, so it always wears.
            workedThisStep[pos.x][pos.y] = true;
        }

        currentOutput = totalEnergy;
        currentNeutronFlux = totalNeutronFlux;
        currentHeat += totalHeat;
        averageReflectorBonus = rods == 0 ? 0.0f : totalReflectorBonus / rods;
    }

    private void processIrradiationTargets() {
        if (currentNeutronFlux <= 0) return;

        for (GridPosition pos : irradiationTargetPositions) {
            ItemStack stack = componentGrid[pos.x][pos.y];
            IrradiationTargetBehavior target = getIrradiationTargetBehavior(stack);
            if (target == null || !target.advanceExposure(stack, currentNeutronFlux)) continue;

            transOut = true;
            listToTransfer.add(target.getIrradiatedProduct());
            componentGrid[pos.x][pos.y] = ItemStack.EMPTY;
        }
    }

    private void applyHeatDissipation() {
        int remainingHeat = currentHeat;

        for (GridPosition pos : heatVentPositions) {
            ItemStack stack = componentGrid[pos.x][pos.y];
            int dissipation = 0;

            if (isHeatVent(stack)) {
                HeatVentBehavior vent = getHeatVentBehavior(stack);
                if (vent != null) {
                    dissipation = vent.getHeatDissipation();
                }
            } else if (isComponentHeatVent(stack)) {
                ComponentHeatVentBehavior componentVent = getComponentHeatVentBehavior(stack);
                if (componentVent != null) {
                    dissipation = coolAdjacentFuelRods(pos.x, pos.y, componentVent.getCoolingRate());
                }
            }

            remainingHeat = dissipate(pos.x, pos.y, dissipation, remainingHeat);
        }

        remainingHeat = absorbHeatExchangerTransfer(remainingHeat);

        currentHeat = Math.max(0, remainingHeat);
    }

    /**
     * Heat exchangers do not vent heat on their own: they only move heat from the reactor (or, for a component heat
     * exchanger, from the adjacent fuel rods) into an adjacent heat sink - a heat vent or a coolant cell. Without such
     * a sink the heat has nowhere to go and the exchanger stays idle, which is what makes their placement matter.
     *
     * @return the remaining reactor heat after every exchanger moved what it could
     */
    private int absorbHeatExchangerTransfer(int availableHeat) {
        int remainingHeat = availableHeat;

        for (GridPosition pos : heatExchangerPositions) {
            ItemStack stack = componentGrid[pos.x][pos.y];
            if (!hasAdjacentHeatSink(pos.x, pos.y)) continue;

            int transferable = 0;
            if (isComponentHeatExchanger(stack)) {
                ComponentHeatExchangerBehavior exchanger = getComponentHeatExchangerBehavior(stack);
                if (exchanger != null) {
                    transferable = coolAdjacentFuelRods(pos.x, pos.y, exchanger.getHeatTransferRate());
                }
            } else if (isReactorHeatExchanger(stack)) {
                ReactorHeatExchangerBehavior exchanger = getReactorHeatExchangerBehavior(stack);
                if (exchanger != null) {
                    // Its heat storage is the amount of hull heat a single step may move through the exchanger.
                    transferable = Math.min(exchanger.getTransferRate(), exchanger.getHeatStorage());
                }
            } else if (isHeatExchanger(stack)) {
                HeatExchangerBehavior exchanger = getHeatExchangerBehavior(stack);
                if (exchanger != null) {
                    transferable = exchanger.getHeatTransferRate();
                }
            }

            remainingHeat = dissipate(pos.x, pos.y, transferable, remainingHeat);
        }

        return remainingHeat;
    }

    /**
     * Removes up to {@code amount} HU from the reactor and records the work for the component, but only for the heat
     * that was actually there to move.
     *
     * @return the remaining reactor heat
     */
    private int dissipate(int x, int y, int amount, int remainingHeat) {
        int removed = Math.min(amount, remainingHeat);
        if (removed <= 0) {
            return remainingHeat;
        }

        workedThisStep[x][y] = true;
        movedHeat[x][y] += removed;
        return remainingHeat - removed;
    }

    /** @return whether one of the four neighbours is a component that can actually dump heat. */
    private boolean hasAdjacentHeatSink(int x, int y) {
        for (int[] direction : NEIGHBOUR_DIRECTIONS) {
            int nx = x + direction[0];
            int ny = y + direction[1];
            if (!isValidPosition(nx, ny)) continue;

            ItemStack neighbour = componentGrid[nx][ny];
            if (isHeatVent(neighbour) || isComponentHeatVent(neighbour) || isCoolantCell(neighbour)) {
                return true;
            }
        }
        return false;
    }

    private void applyCooling() {
        int remainingHeat = currentHeat;

        for (GridPosition pos : coolantCellPositions) {
            ItemStack stack = componentGrid[pos.x][pos.y];
            CoolantCellBehavior coolant = getCoolantCellBehavior(stack);
            if (coolant == null) continue;

            remainingHeat = dissipate(pos.x, pos.y, coolant.getCoolingRate(), remainingHeat);
        }

        currentHeat = Math.max(0, remainingHeat);
    }

    private void calculateHeatBalance() {
        int baseHeatLoss = currentHeat / 100;
        currentHeat = Math.max(0, currentHeat - baseHeatLoss);

        isActive = (currentOutput > 0) || (currentHeat > 1000);
    }

    public int getOverheatingThreshold() {
        return (int) (maxHeatCapacity * 0.8);
    }

    public int getMeltdownThreshold() {
        return (int) (maxHeatCapacity * 0.95);
    }

    public boolean isOverHeat() {
        return currentHeat > getOverheatingThreshold();
    }

    private void checkSafetyStatus() {
        if (hasMeltdown) return;

        int overheatingThreshold = getOverheatingThreshold();
        int meltdownThreshold = getMeltdownThreshold();
        if (currentHeat > overheatingThreshold) {
            float overheatRatio = (float) (currentHeat - overheatingThreshold) /
                    (meltdownThreshold - overheatingThreshold);

            float meltdownChance = 0.001f * overheatRatio;

            if (GTValues.RNG.nextFloat() < meltdownChance && currentHeat > overheatingThreshold + 500) {
                triggerMeltdown();
            }
        }

        if (currentHeat >= meltdownThreshold) {
            triggerMeltdown();
        }
    }

    private void triggerMeltdown() {
        hasMeltdown = true;
        GTLog.logger.error("Reactor meltdown at {} heat! (Max capacity: {})", currentHeat, maxHeatCapacity);
    }

    private void updateComponentDurability() {
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                ItemStack stack = componentGrid[x][y];
                if (stack.isEmpty()) continue;

                NuclearComponentBehavior behavior = getComponentBehavior(stack);
                if (behavior == null || !behavior.consumesDurability()) continue;
                // Components that only wear while working are skipped in steps where they did nothing.
                if (behavior.wearsOnlyWhileWorking() && !workedThisStep[x][y]) continue;

                int damage = behavior.getDurabilityCostForStep(movedHeat[x][y]);
                if (damage <= 0) continue;

                if (!behavior.applyDamage(stack, damage)) {
                    // The component is worn out (or, for fuel rods and coolant cells, transformed):
                    // move it out of the grid and queue it for the output bus.
                    transOut = true;
                    listToTransfer.add(stack);
                    componentGrid[x][y] = ItemStack.EMPTY;
                }
            }
        }
    }

    /**
     * Heat a component vent or a component heat exchanger can pull from the fuel rods it touches. Its rate is spread
     * over those rods, capped per rod.
     */
    private int coolAdjacentFuelRods(int x, int y, int transferRate) {
        int adjacentFuelRods = 0;

        for (int[] direction : NEIGHBOUR_DIRECTIONS) {
            int nx = x + direction[0];
            int ny = y + direction[1];
            if (isValidPosition(nx, ny) && isFuelRod(componentGrid[nx][ny])) {
                adjacentFuelRods++;
            }
        }

        if (adjacentFuelRods == 0) return 0;

        return Math.min(transferRate, adjacentFuelRods * MAX_COMPONENT_TRANSFER_PER_ROD);
    }

    private void clearCaches() {
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                reflectorBonuses[x][y] = 0.0f;
                workedThisStep[x][y] = false;
                movedHeat[x][y] = 0;
            }
        }
        currentNeutronFlux = 0;
    }

    private boolean isFuelRod(ItemStack stack) {
        return getFuelRodBehavior(stack) != null;
    }

    private boolean isHeatVent(ItemStack stack) {
        return getHeatVentBehavior(stack) != null;
    }

    private boolean isComponentHeatVent(ItemStack stack) {
        return getComponentHeatVentBehavior(stack) != null;
    }

    private boolean isHeatExchanger(ItemStack stack) {
        return getHeatExchangerBehavior(stack) != null;
    }

    private boolean isComponentHeatExchanger(ItemStack stack) {
        return getComponentHeatExchangerBehavior(stack) != null;
    }

    private boolean isReactorHeatExchanger(ItemStack stack) {
        return getReactorHeatExchangerBehavior(stack) != null;
    }

    private boolean isCoolantCell(ItemStack stack) {
        return getCoolantCellBehavior(stack) != null;
    }

    private boolean isNeutronReflector(ItemStack stack) {
        return getNeutronReflectorBehavior(stack) != null;
    }

    private boolean isReactorPlating(ItemStack stack) {
        return getPlatingBehavior(stack) != null;
    }

    private boolean isIrradiationTarget(ItemStack stack) {
        return getIrradiationTargetBehavior(stack) != null;
    }

    private FuelRodBehavior getFuelRodBehavior(ItemStack stack) {
        return FuelRodBehavior.getInstanceFor(stack);
    }

    private HeatVentBehavior getHeatVentBehavior(ItemStack stack) {
        return HeatVentBehavior.getInstanceFor(stack);
    }

    private ComponentHeatVentBehavior getComponentHeatVentBehavior(ItemStack stack) {
        return ComponentHeatVentBehavior.getInstanceFor(stack);
    }

    private HeatExchangerBehavior getHeatExchangerBehavior(ItemStack stack) {
        return HeatExchangerBehavior.getInstanceFor(stack);
    }

    private ComponentHeatExchangerBehavior getComponentHeatExchangerBehavior(ItemStack stack) {
        return ComponentHeatExchangerBehavior.getInstanceFor(stack);
    }

    private ReactorHeatExchangerBehavior getReactorHeatExchangerBehavior(ItemStack stack) {
        return ReactorHeatExchangerBehavior.getInstanceFor(stack);
    }

    private CoolantCellBehavior getCoolantCellBehavior(ItemStack stack) {
        return CoolantCellBehavior.getInstanceFor(stack);
    }

    private NeutronReflectorBehavior getNeutronReflectorBehavior(ItemStack stack) {
        return NeutronReflectorBehavior.getInstanceFor(stack);
    }

    private ReactorPlatingBehavior getPlatingBehavior(ItemStack stack) {
        return ReactorPlatingBehavior.getInstanceFor(stack);
    }

    private IrradiationTargetBehavior getIrradiationTargetBehavior(ItemStack stack) {
        return IrradiationTargetBehavior.getInstanceFor(stack);
    }

    private NuclearComponentBehavior getComponentBehavior(ItemStack stack) {
        return NuclearComponentBehavior.getInstanceFor(stack);
    }

    public boolean placeComponent(int x, int y, @NotNull ItemStack stack) {
        if (!isValidPosition(x, y)) return false;
        if (!componentGrid[x][y].isEmpty()) return false;

        componentGrid[x][y] = stack.copy();
        return true;
    }

    public ItemStack removeComponent(int x, int y) {
        if (!isValidPosition(x, y)) return ItemStack.EMPTY;

        ItemStack removed = componentGrid[x][y];
        componentGrid[x][y] = ItemStack.EMPTY;
        return removed;
    }

    public ItemStack getComponent(int x, int y) {
        if (!isValidPosition(x, y)) return ItemStack.EMPTY;
        return componentGrid[x][y];
    }

    public boolean isValidPosition(int x, int y) {
        return x >= 0 && x < gridWidth && y >= 0 && y < gridHeight;
    }

    private void updateStatistics() {
        totalEnergyProduced += getEnergyPerStep();
    }

    public boolean hasMeltdown() {
        return hasMeltdown;
    }

    /**
     * Energy produced by a single simulation step, in EU. Fuel rod power is given per tick while one step covers
     * {@link #TICKS_PER_STEP} ticks, so a step produces {@code currentOutput * TICKS_PER_STEP} EU.
     */
    public long getEnergyPerStep() {
        return currentOutput * TICKS_PER_STEP;
    }

    /** Average output multiplier of the installed fuel rods, 1.0 when no reflector is boosting them. */
    public float getEfficiency() {
        if (totalFuelRods == 0) return 0.0f;

        return Math.min(1.0f + averageReflectorBonus, 2.0f);
    }

    public void setHeat(int currentHeat) {
        this.currentHeat = currentHeat;
    }

    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("GridWidth", gridWidth);
        nbt.setInteger("GridHeight", gridHeight);
        nbt.setInteger("CurrentHeat", currentHeat);
        nbt.setInteger("MaxHeatCapacity", maxHeatCapacity);
        nbt.setLong("CurrentOutput", currentOutput);
        nbt.setBoolean("IsActive", isActive);
        nbt.setBoolean("HasMeltdown", hasMeltdown);
        nbt.setInteger("TickCount", tickCount);
        nbt.setLong("TotalEnergyProduced", totalEnergyProduced);
        nbt.setFloat("AverageReflectorBonus", averageReflectorBonus);
        nbt.setFloat("ExplosionResistance", explosionResistance);
        nbt.setBoolean("TransOut", transOut);
        nbt.setBoolean("TransIn", transIn);
        writeItemList(nbt, "TransferQueue", listToTransfer);
        writeItemList(nbt, "InputQueue", listToAdd);

        // 保存网格数据
        NBTTagCompound gridNBT = new NBTTagCompound();
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                if (!componentGrid[x][y].isEmpty()) {
                    String key = x + "," + y;
                    NBTTagCompound stackNBT = new NBTTagCompound();
                    componentGrid[x][y].writeToNBT(stackNBT);
                    gridNBT.setTag(key, stackNBT);
                }
            }
        }
        nbt.setTag("ComponentGrid", gridNBT);

        return nbt;
    }

    private static void writeItemList(NBTTagCompound nbt, String key, List<ItemStack> stacks) {
        NBTTagList stackList = new NBTTagList();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;
            NBTTagCompound stackNbt = new NBTTagCompound();
            stack.writeToNBT(stackNbt);
            stackList.appendTag(stackNbt);
        }
        nbt.setTag(key, stackList);
    }

    private static void readItemList(NBTTagCompound nbt, String key, List<ItemStack> stacks) {
        stacks.clear();
        NBTTagList stackList = nbt.getTagList(key, TAG_COMPOUND);
        for (int i = 0; i < stackList.tagCount(); i++) {
            ItemStack stack = new ItemStack(stackList.getCompoundTagAt(i));
            if (!stack.isEmpty()) stacks.add(stack);
        }
    }

    public void readFromNBT(NBTTagCompound nbt) {
        currentHeat = nbt.getInteger("CurrentHeat");
        maxHeatCapacity = Math.max(BASE_HEAT_CAPACITY, nbt.getInteger("MaxHeatCapacity"));
        currentOutput = nbt.getLong("CurrentOutput");
        isActive = nbt.getBoolean("IsActive");
        hasMeltdown = nbt.getBoolean("HasMeltdown");
        tickCount = nbt.getInteger("TickCount");
        totalEnergyProduced = nbt.getLong("TotalEnergyProduced");
        averageReflectorBonus = nbt.getFloat("AverageReflectorBonus");
        explosionResistance = nbt.getFloat("ExplosionResistance");
        transOut = nbt.getBoolean("TransOut");
        transIn = nbt.getBoolean("TransIn");
        readItemList(nbt, "TransferQueue", listToTransfer);
        readItemList(nbt, "InputQueue", listToAdd);
        transOut |= !listToTransfer.isEmpty();
        transIn |= !listToAdd.isEmpty();
        currentNeutronFlux = 0;

        int savedWidth = nbt.getInteger("GridWidth");
        int savedHeight = nbt.getInteger("GridHeight");

        if (savedWidth != gridWidth || savedHeight != gridHeight) {
            GTLog.logger.warn("Reactor grid size mismatch: saved {}x{}, current {}x{}. Adapting to the saved grid.",
                    savedWidth, savedHeight, gridWidth, gridHeight);
            resize(savedWidth, savedHeight);
        }

        NBTTagCompound gridNBT = nbt.getCompoundTag("ComponentGrid");
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                String key = x + "," + y;
                componentGrid[x][y] = ItemStack.EMPTY;
                if (gridNBT.hasKey(key, TAG_COMPOUND)) {
                    try {
                        NBTTagCompound stackNBT = gridNBT.getCompoundTag(key);
                        ItemStack stack = new ItemStack(stackNBT);
                        if (!stack.isEmpty()) {
                            componentGrid[x][y] = stack;
                        }
                    } catch (Exception e) {
                        GTLog.logger.error("Failed to load component at ({}, {})", x, y, e);
                    }
                }
            }
        }
    }
}
