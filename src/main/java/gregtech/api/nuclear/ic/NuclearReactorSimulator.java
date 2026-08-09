package gregtech.api.nuclear.ic;

import gregtech.api.util.GTLog;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import gregtech.common.item.behaviors.*;

import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;

import static net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND;

public class NuclearReactorSimulator {
    private final ItemStack[][] componentGrid;
    @Getter
    private final int gridWidth;
    @Getter
    private final int gridHeight;
    private final int[][] reflectorCounts;
    private final int[][] lastEnergyOutput;
    private final int[][] lastHeatOutput;
    private final List<GridPosition> fuelRodPositions = new ArrayList<>();
    private final List<GridPosition> heatVentPositions = new ArrayList<>();
    private final List<GridPosition> coolantCellPositions = new ArrayList<>();
    private final List<GridPosition> reflectorPositions = new ArrayList<>();
    private final List<GridPosition> irradiationTargetPositions = new ArrayList<>();

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

    @Getter
    private int currentHeat = 0;
    @Getter
    private int maxHeatCapacity = 10000;
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
    @Getter
    private int totalReflectors = 0;
    @Getter
    private int totalPlating = 0;

    private int tickCount = 0;
    private long totalEnergyProduced = 0;
    private long totalHeatProduced = 0;
    private int maxHeatReached = 0;
    @Getter
    private boolean isActive = false;

    private float explosionResistance = 0.0f;
    private boolean hasMeltdown = false;

    public NuclearReactorSimulator(int width, int height) {
        this.gridWidth = width;
        this.gridHeight = height;
        this.componentGrid = new ItemStack[gridWidth][gridHeight];
        this.reflectorCounts = new int[gridWidth][gridHeight];
        this.lastEnergyOutput = new int[gridWidth][gridHeight];
        this.lastHeatOutput = new int[gridWidth][gridHeight];

        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                componentGrid[x][y] = ItemStack.EMPTY;
            }
        }
    }

    public boolean simulateTick() {
        if (transIn) {
            for (int x = 0; x < gridWidth; x++) {
                for (int y = 0; y < gridHeight; y++) {
                    if (componentGrid[x][y].isEmpty() && !listToAdd.isEmpty()) {
                        componentGrid[x][y] = listToAdd.get(0);
                        listToAdd.remove(0);
                    }
                    if (listToAdd.isEmpty()) {
                        transIn = false;
                    }
                }
            }
        }


        if (hasMeltdown) return false;

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
        totalReflectors = 0;
        totalPlating = 0;
        explosionResistance = 0.0f;
        maxHeatCapacity = 10000;

        fuelRodPositions.clear();
        heatVentPositions.clear();
        coolantCellPositions.clear();
        reflectorPositions.clear();
        irradiationTargetPositions.clear();

        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                ItemStack stack = componentGrid[x][y];
                if (stack.isEmpty()) continue;

                if (isFuelRod(stack)) {
                    fuelRodPositions.add(new GridPosition(x, y));
                    totalFuelRods++;
                } else if (isHeatVent(stack)) {
                    heatVentPositions.add(new GridPosition(x, y));
                    totalHeatVents++;
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
            int reflectorCount = countAdjacentReflectors(pos.x, pos.y);
            reflectorCounts[pos.x][pos.y] = reflectorCount;
        }
    }

    private void calculateFuelRodOutput() {
        long totalEnergy = 0;
        int totalHeat = 0;
        int totalNeutronFlux = 0;

        for (GridPosition pos : fuelRodPositions) {
            ItemStack stack = componentGrid[pos.x][pos.y];
            FuelRodBehavior fuelRod = getFuelRodBehavior(stack);
            if (fuelRod == null) continue;

            int reflectorCount = reflectorCounts[pos.x][pos.y];

            int energyOutput = fuelRod.getEnergyOutput();
            int heatOutput = fuelRod.getHeatOutput();

            float reflectorBonus = 1.0f + (reflectorCount * 0.2f);

            int finalEnergy = (int) (energyOutput * reflectorBonus);
            int finalHeat = (int) (heatOutput * reflectorBonus);

            totalEnergy += finalEnergy;
            totalHeat += finalHeat;
            totalNeutronFlux += Math.round(fuelRod.getNeutronEmission() * reflectorBonus * 100.0f);

            lastEnergyOutput[pos.x][pos.y] = finalEnergy;
            lastHeatOutput[pos.x][pos.y] = finalHeat;
        }

        currentOutput = totalEnergy;
        currentNeutronFlux = totalNeutronFlux;
        currentHeat += totalHeat;
        totalHeatProduced += totalHeat;
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
        int totalDissipation = 0;

        for (GridPosition pos : heatVentPositions) {
            ItemStack stack = componentGrid[pos.x][pos.y];
            if (isHeatVent(stack)) {
                HeatVentBehavior vent = getHeatVentBehavior(stack);
                if (vent != null) {
                    totalDissipation += vent.getHeatDissipation();
                }
            } else if (isComponentHeatVent(stack)) {
                ComponentHeatVentBehavior componentVent = getComponentHeatVentBehavior(stack);
                if (componentVent != null) {
                    totalDissipation += coolAdjacentFuelRods(pos.x, pos.y, componentVent);
                }
            }
        }

        currentHeat = Math.max(0, currentHeat - totalDissipation);
    }

    private void applyCooling() {
        int totalCooling = 0;

        for (GridPosition pos : coolantCellPositions) {
            ItemStack stack = componentGrid[pos.x][pos.y];
            CoolantCellBehavior coolant = getCoolantCellBehavior(stack);
            if (coolant != null) {
                totalCooling += coolant.getCoolingRate();
            }
        }

        currentHeat = Math.max(0, currentHeat - totalCooling);
    }

    private void calculateHeatBalance() {
        int baseHeatLoss = currentHeat / 100;
        currentHeat = Math.max(0, currentHeat - baseHeatLoss);

        maxHeatReached = Math.max(maxHeatReached, currentHeat);

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

    public boolean isMeltdown() {
        return currentHeat > getMeltdownThreshold();
    }

    private void checkSafetyStatus() {
        if (hasMeltdown) return;

        int overheatingThreshold = getOverheatingThreshold();
        int meltdownThreshold = getMeltdownThreshold();
        if (currentHeat > overheatingThreshold) {
            float overheatRatio = (float) (currentHeat - overheatingThreshold) /
                    (meltdownThreshold - overheatingThreshold);

            float meltdownChance = 0.001f * overheatRatio;

            if (Math.random() < meltdownChance && currentHeat > overheatingThreshold + 500) {
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
                if (behavior == null) continue;

                if (!behavior.applyDamage(stack, 1)) {
                    //将stack转移出去
                    //设置当前slot为EMPTY
                    transOut = true;
                    listToTransfer.add(stack);
                    componentGrid[x][y] = ItemStack.EMPTY;
                }
            }
        }
    }

    private int coolAdjacentFuelRods(int x, int y, ComponentHeatVentBehavior vent) {
        int coolingRate = vent.getCoolingRate();
        int adjacentFuelRods = 0;

        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            if (isValidPosition(nx, ny) && isFuelRod(componentGrid[nx][ny])) {
                adjacentFuelRods++;
            }
        }

        if (adjacentFuelRods == 0) return 0;

        int coolingPerRod = Math.min(100, coolingRate / adjacentFuelRods);
        return coolingPerRod * adjacentFuelRods;
    }

    private int countAdjacentReflectors(int x, int y) {
        int count = 0;
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            if (isValidPosition(nx, ny) &&
                    isNeutronReflector(componentGrid[nx][ny])) {
                count++;
            }
        }

        return count;
    }

    private void clearCaches() {
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                reflectorCounts[x][y] = 0;
                lastEnergyOutput[x][y] = 0;
                lastHeatOutput[x][y] = 0;
            }
        }

        fuelRodPositions.clear();
        heatVentPositions.clear();
        coolantCellPositions.clear();
        reflectorPositions.clear();
        irradiationTargetPositions.clear();
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

    private void setReflectorCount(int x, int y, int count) {
        ItemStack stack = componentGrid[x][y];
        if (!stack.isEmpty()) {
            NBTTagCompound nbt = stack.getOrCreateSubCompound("ReactorData");
            nbt.setInteger("ReflectorCount", count);
        }
    }

    private int getReflectorCount(int x, int y) {
        ItemStack stack = componentGrid[x][y];
        if (stack.isEmpty()) return 0;

        NBTTagCompound nbt = stack.getSubCompound("ReactorData");
        if (nbt == null) return 0;

        return nbt.getInteger("ReflectorCount");
    }

    private void setFuelOutput(int x, int y, int energy, int heat) {
        ItemStack stack = componentGrid[x][y];
        if (!stack.isEmpty()) {
            NBTTagCompound nbt = stack.getOrCreateSubCompound("ReactorData");
            nbt.setInteger("LastEnergyOutput", energy);
            nbt.setInteger("LastHeatOutput", heat);
        }
    }

    private void clearCellCache(int x, int y) {
        ItemStack stack = componentGrid[x][y];
        if (!stack.isEmpty()) {
            NBTTagCompound nbt = stack.getSubCompound("ReactorData");
            if (nbt != null) {
                nbt.removeTag("ReflectorCount");
                nbt.removeTag("LastEnergyOutput");
                nbt.removeTag("LastHeatOutput");
            }
        }
    }

    private void updateStatistics() {
        totalEnergyProduced += currentOutput;
    }

    public boolean hasMeltdown() {
        return hasMeltdown;
    }

    public int getHeatPercentage() {
        return maxHeatCapacity > 0 ? (currentHeat * 100) / maxHeatCapacity : 0;
    }

    public float getEfficiency() {
        if (totalFuelRods == 0) return 0.0f;

        float baseEfficiency = 1.0f + (totalReflectors * 0.1f);
        return Math.min(baseEfficiency, 2.0f);
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
        nbt.setLong("TotalHeatProduced", totalHeatProduced);
        nbt.setInteger("MaxHeatReached", maxHeatReached);
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
        maxHeatCapacity = Math.max(10000, nbt.getInteger("MaxHeatCapacity"));
        currentOutput = nbt.getLong("CurrentOutput");
        isActive = nbt.getBoolean("IsActive");
        hasMeltdown = nbt.getBoolean("HasMeltdown");
        tickCount = nbt.getInteger("TickCount");
        totalEnergyProduced = nbt.getLong("TotalEnergyProduced");
        totalHeatProduced = nbt.getLong("TotalHeatProduced");
        maxHeatReached = nbt.getInteger("MaxHeatReached");
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
            GTLog.logger.warn("Reactor grid size mismatch: {}x{} vs {}x{}. Resetting grid.",
                    savedWidth, savedHeight, gridWidth, gridHeight);
            resetGrid();
            return;
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

    private void resetGrid() {
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                componentGrid[x][y] = ItemStack.EMPTY;
            }
        }
    }
}
