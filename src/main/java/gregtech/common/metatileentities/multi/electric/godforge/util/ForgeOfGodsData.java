package gregtech.common.metatileentities.multi.electric.godforge.util;

import static gregtech.common.metatileentities.multi.electric.godforge.upgrade.ForgeOfGodsUpgrade.CD;
import static gregtech.common.metatileentities.multi.electric.godforge.upgrade.ForgeOfGodsUpgrade.END;

import java.math.BigInteger;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.items.ItemStackHandler;

import gregtech.common.metatileentities.multi.electric.godforge.color.ForgeOfGodsStarColor;
import gregtech.common.metatileentities.multi.electric.godforge.color.StarColorStorage;
import gregtech.common.metatileentities.multi.electric.godforge.data.Formatters;
import gregtech.common.metatileentities.multi.electric.godforge.upgrade.ForgeOfGodsUpgrade;
import gregtech.common.metatileentities.multi.electric.godforge.upgrade.UpgradeStorage;

public class ForgeOfGodsData {

    public static final int DEFAULT_FUEL_CONSUMPTION_FACTOR = 1;
    public static final int DEFAULT_MAX_BATTERY_CHARGE = 100;
    public static final int DEFAULT_RING_AMOUNT = 1;
    public static final int MAX_RING_AMOUNT = 3;
    public static final int DEFAULT_ROTATION_SPEED = 5;
    public static final int DEFAULT_STAR_SIZE = 20;
    public static final String DEFAULT_STAR_COLOR = ForgeOfGodsStarColor.DEFAULT.getName();
    public static final Formatters DEFAULT_FORMATTER = Formatters.COMMA;
    public static final BigInteger DEFAULT_TOTAL_POWER = BigInteger.ZERO;

    public static final long POWER_MILESTONE_CONSTANT = (long) Math.pow(10, 15);
    public static final long RECIPE_MILESTONE_CONSTANT = (long) Math.pow(10, 7);
    public static final long FUEL_MILESTONE_CONSTANT = 10_000;
    public static final long RECIPE_MILESTONE_T7_CONSTANT = RECIPE_MILESTONE_CONSTANT * (long) Math.pow(4, 6);
    public static final long FUEL_MILESTONE_T7_CONSTANT = FUEL_MILESTONE_CONSTANT * (long) Math.pow(3, 6);
    public static final BigInteger POWER_MILESTONE_T7_CONSTANT = BigInteger.valueOf(POWER_MILESTONE_CONSTANT)
        .multiply(BigInteger.valueOf((long) Math.pow(9, 6)));
    public static final double POWER_LOG_CONSTANT = Math.log(9);
    public static final double RECIPE_LOG_CONSTANT = Math.log(4);
    public static final double FUEL_LOG_CONSTANT = Math.log(3);

    public static final int MAX_RESIDUE_FACTOR = 70;
    public static final int MAX_RESIDUE_FACTOR_DISCOUNTED = 72;
    public static final int MAX_STELLAR_PLASMA_FACTOR = 181;
    public static final int MAX_STELLAR_PLASMA_FACTOR_DISCOUNTED = 184;

    private int fuelConsumptionFactor = DEFAULT_FUEL_CONSUMPTION_FACTOR;
    private int selectedFuelType;
    private int internalBattery;
    private int maxBatteryCharge = DEFAULT_MAX_BATTERY_CHARGE;
    private int gravitonShardsAvailable;
    private int gravitonShardsSpent;
    // Save-compatible storage for the highest ring tier committed by structure formation.
    private int ringAmount = DEFAULT_RING_AMOUNT;
    private int clearedRingAmount;
    private int stellarFuelAmount;
    private int neededStartupFuel;
    private long fuelConsumption;
    private long totalRecipesProcessed;
    private long totalFuelConsumed;
    private float totalExtensionsBuilt;

    private float powerMilestonePercentage;
    private float recipeMilestonePercentage;
    private float fuelMilestonePercentage;
    private float structureMilestonePercentage;
    private float invertedPowerMilestonePercentage;
    private float invertedRecipeMilestonePercentage;
    private float invertedFuelMilestonePercentage;
    private float invertedStructureMilestonePercentage;

    private final int[] milestoneProgress = new int[4];

    private BigInteger totalPowerConsumed = DEFAULT_TOTAL_POWER;
    private boolean batteryCharging;
    private boolean inversion;
    private boolean gravitonShardEjection;
    private Formatters formatter = DEFAULT_FORMATTER;
    private boolean isRenderActive;
    private boolean secretUpgrade;
    private boolean isRendererDisabled;

    private final UpgradeStorage upgrades = new UpgradeStorage();
    private final ItemStackHandler upgradeWindowHandler = new ItemStackHandler(16);

    private final StarColorStorage starColors = new StarColorStorage();
    private String selectedStarColor = DEFAULT_STAR_COLOR;
    private int rotationSpeed = DEFAULT_ROTATION_SPEED;
    private int starSize = DEFAULT_STAR_SIZE;

    public int getFuelConsumptionFactor() {
        return fuelConsumptionFactor;
    }

    public void setFuelConsumptionFactor(int fuelConsumptionFactor) {
        this.fuelConsumptionFactor = fuelConsumptionFactor;
    }

    public int getSelectedFuelType() {
        return selectedFuelType;
    }

    public void setSelectedFuelType(int selectedFuelType) {
        this.selectedFuelType = selectedFuelType;
    }

    public int getInternalBattery() {
        return internalBattery;
    }

    public void setInternalBattery(int internalBattery) {
        this.internalBattery = internalBattery;
    }

    public int getMaxBatteryCharge() {
        return maxBatteryCharge;
    }

    public void setMaxBatteryCharge(int maxBatteryCharge) {
        this.maxBatteryCharge = maxBatteryCharge;
    }

    public int getGravitonShardsAvailable() {
        return gravitonShardsAvailable;
    }

    public void setGravitonShardsAvailable(int gravitonShardsAvailable) {
        this.gravitonShardsAvailable = gravitonShardsAvailable;
    }

    public int getGravitonShardsSpent() {
        return gravitonShardsSpent;
    }

    public void setGravitonShardsSpent(int gravitonShardsSpent) {
        this.gravitonShardsSpent = gravitonShardsSpent;
    }

    public int getRingAmount() {
        return ringAmount;
    }

    public void setRingAmount(int ringAmount) {
        this.ringAmount = MathHelper.clamp(ringAmount, DEFAULT_RING_AMOUNT, MAX_RING_AMOUNT);
    }

    public int getFormedRingAmount() {
        return getRingAmount();
    }

    public void setFormedRingAmount(int formedRingAmount) {
        setRingAmount(formedRingAmount);
    }

    /**
     * Ring tier the next explicit structure operation should try to validate.
     * This is intentionally separate from {@link #getFormedRingAmount()} so
     * upgrades can request a larger structure without granting formed benefits.
     */
    public int getDesiredRingAmount() {
        int desired = getFormedRingAmount();
        if (isUpgradeActive(CD)) {
            desired = Math.max(desired, 2);
        }
        if (isUpgradeActive(END)) {
            desired = Math.max(desired, MAX_RING_AMOUNT);
        }
        return MathHelper.clamp(desired, DEFAULT_RING_AMOUNT, MAX_RING_AMOUNT);
    }

    public int getClearedRingAmount() {
        return clearedRingAmount;
    }

    public void setClearedRingAmount(int clearedRingAmount) {
        this.clearedRingAmount = MathHelper.clamp(clearedRingAmount, 0, MAX_RING_AMOUNT);
    }

    public boolean isRingCleared(int ringIndex) {
        return clearedRingAmount >= ringIndex;
    }

    public int getStellarFuelAmount() {
        return stellarFuelAmount;
    }

    public void setStellarFuelAmount(int stellarFuelAmount) {
        this.stellarFuelAmount = stellarFuelAmount;
    }

    public int getNeededStartupFuel() {
        return neededStartupFuel;
    }

    public void setNeededStartupFuel(int neededStartupFuel) {
        this.neededStartupFuel = neededStartupFuel;
    }

    public long getFuelConsumption() {
        return fuelConsumption;
    }

    public void setFuelConsumption(long fuelConsumption) {
        this.fuelConsumption = fuelConsumption;
    }

    public long getTotalRecipesProcessed() {
        return totalRecipesProcessed;
    }

    public void setTotalRecipesProcessed(long totalRecipesProcessed) {
        this.totalRecipesProcessed = totalRecipesProcessed;
    }

    public long getTotalFuelConsumed() {
        return totalFuelConsumed;
    }

    public void setTotalFuelConsumed(long totalFuelConsumed) {
        this.totalFuelConsumed = totalFuelConsumed;
    }

    public float getTotalExtensionsBuilt() {
        return totalExtensionsBuilt;
    }

    public void setTotalExtensionsBuilt(float totalExtensionsBuilt) {
        this.totalExtensionsBuilt = totalExtensionsBuilt;
    }

    public float getPowerMilestonePercentage() {
        return powerMilestonePercentage;
    }

    public void setPowerMilestonePercentage(float powerMilestonePercentage) {
        this.powerMilestonePercentage = powerMilestonePercentage;
    }

    public float getRecipeMilestonePercentage() {
        return recipeMilestonePercentage;
    }

    public void setRecipeMilestonePercentage(float recipeMilestonePercentage) {
        this.recipeMilestonePercentage = recipeMilestonePercentage;
    }

    public float getFuelMilestonePercentage() {
        return fuelMilestonePercentage;
    }

    public void setFuelMilestonePercentage(float fuelMilestonePercentage) {
        this.fuelMilestonePercentage = fuelMilestonePercentage;
    }

    public float getStructureMilestonePercentage() {
        return structureMilestonePercentage;
    }

    public void setStructureMilestonePercentage(float structureMilestonePercentage) {
        this.structureMilestonePercentage = structureMilestonePercentage;
    }

    public float getInvertedPowerMilestonePercentage() {
        return invertedPowerMilestonePercentage;
    }

    public void setInvertedPowerMilestonePercentage(float invertedPowerMilestonePercentage) {
        this.invertedPowerMilestonePercentage = invertedPowerMilestonePercentage;
    }

    public float getInvertedRecipeMilestonePercentage() {
        return invertedRecipeMilestonePercentage;
    }

    public void setInvertedRecipeMilestonePercentage(float invertedRecipeMilestonePercentage) {
        this.invertedRecipeMilestonePercentage = invertedRecipeMilestonePercentage;
    }

    public float getInvertedFuelMilestonePercentage() {
        return invertedFuelMilestonePercentage;
    }

    public void setInvertedFuelMilestonePercentage(float invertedFuelMilestonePercentage) {
        this.invertedFuelMilestonePercentage = invertedFuelMilestonePercentage;
    }

    public float getInvertedStructureMilestonePercentage() {
        return invertedStructureMilestonePercentage;
    }

    public void setInvertedStructureMilestonePercentage(float invertedStructureMilestonePercentage) {
        this.invertedStructureMilestonePercentage = invertedStructureMilestonePercentage;
    }

    public int getMilestoneProgress(int index) {
        return milestoneProgress[index];
    }

    public void setMilestoneProgress(int index, int progress) {
        milestoneProgress[index] = progress;
    }

    public int[] getAllMilestoneProgress() {
        return milestoneProgress;
    }

    public BigInteger getTotalPowerConsumed() {
        return totalPowerConsumed;
    }

    public void setTotalPowerConsumed(BigInteger totalPowerConsumed) {
        this.totalPowerConsumed = totalPowerConsumed;
    }

    public boolean isBatteryCharging() {
        return batteryCharging;
    }

    public void setBatteryCharging(boolean batteryCharging) {
        this.batteryCharging = batteryCharging;
    }

    public boolean isInversion() {
        return inversion;
    }

    public void setInversion(boolean inversion) {
        this.inversion = inversion;
    }

    public boolean isGravitonShardEjection() {
        return gravitonShardEjection;
    }

    public void setGravitonShardEjection(boolean gravitonShardEjection) {
        this.gravitonShardEjection = gravitonShardEjection;
    }

    public Formatters getFormatter() {
        return formatter;
    }

    public void setFormatter(Formatters formatter) {
        this.formatter = formatter;
    }

    public boolean isRenderActive() {
        return isRenderActive;
    }

    public void setRenderActive(boolean renderActive) {
        isRenderActive = renderActive;
    }

    public boolean isSecretUpgrade() {
        return secretUpgrade;
    }

    public void setSecretUpgrade(boolean secretUpgrade) {
        this.secretUpgrade = secretUpgrade;
    }

    public boolean isRendererDisabled() {
        return isRendererDisabled;
    }

    public void setRendererDisabled(boolean rendererDisabled) {
        isRendererDisabled = rendererDisabled;
    }

    public UpgradeStorage getUpgrades() {
        return upgrades;
    }

    public void resetAllUpgrades() {
        upgrades.resetAll();
    }

    public void unlockAllUpgrades() {
        upgrades.unlockAll();
    }

    public void manualInsertion() {}

    public void unlockUpgrade(ForgeOfGodsUpgrade upgrade) {
        if (isUpgradeActive(upgrade)) return;
        if (!upgrades.checkPrerequisites(upgrade)) return;
        if (!upgrades.checkSplit(upgrade, ringAmount)) return;
        if (!upgrades.checkCost(upgrade, gravitonShardsAvailable)) return;

        upgrades.unlockUpgrade(upgrade);
        gravitonShardsAvailable -= upgrade.getShardCost();
        gravitonShardsSpent += upgrade.getShardCost();
    }

    public void respecUpgrade(ForgeOfGodsUpgrade upgrade) {
        if (!isUpgradeActive(upgrade)) return;
        if (!upgrades.checkDependents(upgrade)) return;

        upgrades.respecUpgrade(upgrade);
        gravitonShardsAvailable += upgrade.getShardCost();
        gravitonShardsSpent -= upgrade.getShardCost();

        if (upgrade == END) {
            gravitonShardEjection = false;
        }
    }

    public boolean isUpgradeActive(ForgeOfGodsUpgrade upgrade) {
        return upgrades.isUpgradeActive(upgrade);
    }

    public ItemStack[] getStoredUpgradeWindowItems() {
        ItemStack[] storedUpgradeWindowItems = new ItemStack[upgradeWindowHandler.getSlots()];
        for (int i = 0; i < storedUpgradeWindowItems.length; i++) {
            ItemStack stack = upgradeWindowHandler.getStackInSlot(i);
            storedUpgradeWindowItems[i] = stack.isEmpty() ? null : stack;
        }
        return storedUpgradeWindowItems;
    }

    public ItemStackHandler getUpgradeWindowHandler() {
        return upgradeWindowHandler;
    }

    public StarColorStorage getStarColors() {
        return starColors;
    }

    public String getSelectedStarColor() {
        return selectedStarColor;
    }

    public void setSelectedStarColor(String selectedStarColor) {
        this.selectedStarColor = selectedStarColor;
    }

    public int getRotationSpeed() {
        return rotationSpeed;
    }

    public void setRotationSpeed(int rotationSpeed) {
        this.rotationSpeed = rotationSpeed;
    }

    public int getStarSize() {
        return starSize;
    }

    public void setStarSize(int starSize) {
        this.starSize = starSize;
    }

    /**
     * Serializes all persistent state to NBT for world save/load.
     * Render-related fields (isRenderActive, isRendererDisabled, visual settings) are
     * co-located here instead of the old dead-code writeRenderNBT().
     */
    public void writeToNBT(NBTTagCompound nbt) {
        // --- Core operational state ---
        nbt.setInteger("selectedFuelType", selectedFuelType);
        nbt.setInteger("internalBattery", internalBattery);
        nbt.setBoolean("batteryCharging", batteryCharging);
        nbt.setInteger("gravitonShardsAvailable", gravitonShardsAvailable);
        nbt.setInteger("gravitonShardsSpent", gravitonShardsSpent);
        nbt.setLong("totalRecipesProcessed", totalRecipesProcessed);
        nbt.setLong("totalFuelConsumed", totalFuelConsumed);
        nbt.setInteger("starFuelStored", stellarFuelAmount);
        nbt.setBoolean("gravitonShardEjection", gravitonShardEjection);
        nbt.setBoolean("secretUpgrade", secretUpgrade);
        nbt.setBoolean("inversion", inversion);

        // --- Configurable parameters ---
        nbt.setInteger("fuelConsumptionFactor", fuelConsumptionFactor);
        nbt.setInteger("batterySize", maxBatteryCharge);
        nbt.setByteArray("totalPowerConsumed", totalPowerConsumed.toByteArray());
        nbt.setInteger("formatter", formatter.ordinal());

        // --- Structure state: ring amount must survive reload for structure validation ---
        nbt.setInteger("ringAmount", ringAmount);
        nbt.setInteger("clearedRingAmount", clearedRingAmount);

        // --- Milestone progress: graviton shard count depends on these ---
        for (int i = 0; i < milestoneProgress.length; i++) {
            nbt.setInteger("milestoneProgress" + i, milestoneProgress[i]);
        }
        nbt.setFloat("totalExtensionsBuilt", totalExtensionsBuilt);

        // --- Render / visual settings ---
        nbt.setBoolean("isRenderActive", isRenderActive);
        nbt.setBoolean("isRendererDisabled", isRendererDisabled);
        nbt.setInteger("rotationSpeed", rotationSpeed);
        nbt.setInteger("starSize", starSize);
        nbt.setString("selectedStarColor", selectedStarColor);

        // --- Upgrade window inventory (always persisted) ---
        nbt.setTag("upgradeWindowStorage", upgradeWindowHandler.serializeNBT());

        upgrades.writeToNBT(nbt);
        starColors.writeToNBT(nbt);
    }

    /**
     * @deprecated No longer needed: render fields are now written by {@link #writeToNBT}.
     *             Kept as a no-op to avoid breaking any call sites that may exist.
     */
    @Deprecated
    public void writeRenderNBT(NBTTagCompound nbt) {}

    public void readFromNBT(NBTTagCompound nbt) {
        selectedFuelType = nbt.getInteger("selectedFuelType");
        internalBattery = nbt.getInteger("internalBattery");
        batteryCharging = nbt.getBoolean("batteryCharging");
        gravitonShardsAvailable = nbt.getInteger("gravitonShardsAvailable");
        gravitonShardsSpent = nbt.getInteger("gravitonShardsSpent");
        totalRecipesProcessed = nbt.getLong("totalRecipesProcessed");
        totalFuelConsumed = nbt.getLong("totalFuelConsumed");
        stellarFuelAmount = nbt.getInteger("starFuelStored");
        gravitonShardEjection = nbt.getBoolean("gravitonShardEjection");
        secretUpgrade = nbt.getBoolean("secretUpgrade");
        inversion = nbt.getBoolean("inversion");

        if (nbt.hasKey("fuelConsumptionFactor")) {
            fuelConsumptionFactor = nbt.getInteger("fuelConsumptionFactor");
        }
        if (nbt.hasKey("batterySize")) {
            maxBatteryCharge = nbt.getInteger("batterySize");
        }
        if (nbt.hasKey("totalPowerConsumed")) {
            totalPowerConsumed = new BigInteger(nbt.getByteArray("totalPowerConsumed"));
        }

        if (nbt.hasKey("formatter")) {
            int index = MathHelper.clamp(nbt.getInteger("formatter"), 0, Formatters.VALUES.length - 1);
            formatter = Formatters.VALUES[index];
        }

        // --- Structure state ---
        if (nbt.hasKey("ringAmount")) {
            setRingAmount(nbt.getInteger("ringAmount"));
        }

        // --- Milestone progress ---
        for (int i = 0; i < milestoneProgress.length; i++) {
            String key = "milestoneProgress" + i;
            if (nbt.hasKey(key)) {
                milestoneProgress[i] = nbt.getInteger(key);
            }
        }
        if (nbt.hasKey("totalExtensionsBuilt")) {
            totalExtensionsBuilt = nbt.getFloat("totalExtensionsBuilt");
        }

        // --- Render / visual settings ---
        isRenderActive = nbt.getBoolean("isRenderActive");
        isRendererDisabled = nbt.getBoolean("isRendererDisabled");
        if (nbt.hasKey("rotationSpeed")) rotationSpeed = nbt.getInteger("rotationSpeed");
        if (nbt.hasKey("starSize")) starSize = nbt.getInteger("starSize");
        if (nbt.hasKey("selectedStarColor")) selectedStarColor = nbt.getString("selectedStarColor");
        if (nbt.hasKey("clearedRingAmount")) {
            setClearedRingAmount(nbt.getInteger("clearedRingAmount"));
        } else if (isRenderActive) {
            // Legacy saves only persisted ringAmount, which used to be derived from upgrades.
            // Only the first ring is guaranteed to have been physically present before rendering.
            setClearedRingAmount(DEFAULT_RING_AMOUNT);
        }

        // --- Upgrade window inventory ---
        NBTTagCompound itemTag = nbt.getCompoundTag("upgradeWindowStorage");
        if (itemTag != null && !itemTag.isEmpty()) {
            upgradeWindowHandler.deserializeNBT(itemTag);
        }

        upgrades.readFromNBT(nbt);
        starColors.readFromNBT(nbt);
    }
}
