package gregtech.api.worldgen.config;

import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fluids.Fluid;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public class BedrockFluidDepositDefinition implements IWorldgenDefinition {

    private final String depositName;

    private int weight; // weight value for determining which vein will appear
    private String assignedName; // vein name for JEI display
    private String description; // vein description for JEI display
    private final int[] yields = new int[2]; // the [minimum, maximum) yields
    private int depletionAmount; // amount of fluid the vein gets drained by
    private int depletionChance; // the chance [0, 100] that the vein will deplete by 1
    private int depletedYield; // yield after the vein is depleted

    private Fluid storedFluid; // the fluid which the vein contains

    private Function<Biome, Integer> biomeWeightModifier = OreDepositDefinition.NO_BIOME_INFLUENCE; // weighting of
    // biomes
    private Predicate<WorldProvider> dimensionFilter = OreDepositDefinition.PREDICATE_SURFACE_WORLD; // filtering of
    // dimensions

    public BedrockFluidDepositDefinition(String depositName) {
        this.depositName = depositName;
    }

    @Override
    public String getDepositName() {
        return depositName;
    }

    public String getAssignedName() {
        return assignedName;
    }

    public String getDescription() {
        return description;
    }

    public int getWeight() {
        return weight;
    }

    @SuppressWarnings("unused")
    public int[] getYields() {
        return yields;
    }

    public int getMinimumYield() {
        return yields[0];
    }

    public int getMaximumYield() {
        return yields[1];
    }

    public int getDepletionAmount() {
        return depletionAmount;
    }

    public int getDepletionChance() {
        return depletionChance;
    }

    public int getDepletedYield() {
        return depletedYield;
    }

    public Fluid getStoredFluid() {
        return storedFluid;
    }

    public Function<Biome, Integer> getBiomeWeightModifier() {
        return biomeWeightModifier;
    }

    public Predicate<WorldProvider> getDimensionFilter() {
        return dimensionFilter;
    }

    // === Package-private setters, used by BedrockFluidDepositBuilder ===

    void setWeight(int weight) {
        this.weight = weight;
    }

    void setAssignedName(String assignedName) {
        this.assignedName = assignedName;
    }

    void setDescription(String description) {
        this.description = description;
    }

    void setYields(int minimumYield, int maximumYield) {
        this.yields[0] = minimumYield;
        this.yields[1] = maximumYield;
    }

    void setDepletionAmount(int depletionAmount) {
        this.depletionAmount = depletionAmount;
    }

    void setDepletionChance(int depletionChance) {
        this.depletionChance = depletionChance;
    }

    void setDepletedYield(int depletedYield) {
        this.depletedYield = depletedYield;
    }

    void setStoredFluid(Fluid storedFluid) {
        this.storedFluid = storedFluid;
    }

    void setBiomeWeightModifier(Function<Biome, Integer> biomeWeightModifier) {
        this.biomeWeightModifier = biomeWeightModifier;
    }

    void setDimensionFilter(Predicate<WorldProvider> dimensionFilter) {
        this.dimensionFilter = dimensionFilter;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BedrockFluidDepositDefinition))
            return false;

        BedrockFluidDepositDefinition objDeposit = (BedrockFluidDepositDefinition) obj;
        if (this.weight != objDeposit.getWeight())
            return false;
        if (this.getMinimumYield() != objDeposit.getMinimumYield())
            return false;
        if (this.getMaximumYield() != objDeposit.getMaximumYield())
            return false;
        if (this.depletionAmount != objDeposit.getDepletionAmount())
            return false;
        if (this.depletionChance != objDeposit.getDepletionChance())
            return false;
        if (!this.storedFluid.equals(objDeposit.getStoredFluid()))
            return false;
        if (!Objects.equals(this.assignedName, objDeposit.getAssignedName()))
            return false;
        if (!Objects.equals(this.description, objDeposit.getDescription()))
            return false;
        if (this.depletedYield != objDeposit.getDepletedYield())
            return false;
        if (!Objects.equals(this.biomeWeightModifier, objDeposit.getBiomeWeightModifier()))
            return false;
        if (!Objects.equals(this.dimensionFilter, objDeposit.getDimensionFilter()))
            return false;
        return true;
    }
}
