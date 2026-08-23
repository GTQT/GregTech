package gregtech.api.worldgen.config;

import gregtech.api.unification.ore.StoneType;
import gregtech.api.util.WorldBlockPredicate;
import gregtech.api.worldgen.filler.BlockFiller;
import gregtech.api.worldgen.populator.IVeinPopulator;
import gregtech.api.worldgen.shape.ShapeGenerator;

import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.Biome;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public class OreDepositDefinition implements IWorldgenDefinition {

    public static final Function<Biome, Integer> NO_BIOME_INFLUENCE = biome -> 0;
    public static final Predicate<WorldProvider> PREDICATE_SURFACE_WORLD = WorldProvider::isSurfaceWorld;
    public static final WorldBlockPredicate PREDICATE_STONE_TYPE = (state, world, pos) -> StoneType
            .computeStoneType(state, world, pos) != null;

    private final String depositName;

    private int weight;
    private int priority;
    private float density;
    private String assignedName;
    private String description;
    private final int[] heightLimit = new int[] { Integer.MIN_VALUE, Integer.MAX_VALUE };
    private boolean countAsVein = true;

    private Function<Biome, Integer> biomeWeightModifier = NO_BIOME_INFLUENCE;
    private Predicate<WorldProvider> dimensionFilter = PREDICATE_SURFACE_WORLD;
    private WorldBlockPredicate generationPredicate = PREDICATE_STONE_TYPE;
    private IVeinPopulator veinPopulator;

    private BlockFiller blockFiller;
    private ShapeGenerator shapeGenerator;

    public OreDepositDefinition(String depositName) {
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

    public float getDensity() {
        return density;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isVein() {
        return countAsVein;
    }

    public boolean checkInHeightLimit(int yLevel) {
        return yLevel >= heightLimit[0] && yLevel <= heightLimit[1];
    }

    public int[] getHeightLimit() {
        return heightLimit;
    }

    public int getMinimumHeight() {
        return heightLimit[0];
    }

    public int getMaximumHeight() {
        return heightLimit[1];
    }

    public Function<Biome, Integer> getBiomeWeightModifier() {
        return biomeWeightModifier;
    }

    public Predicate<WorldProvider> getDimensionFilter() {
        return dimensionFilter;
    }

    public WorldBlockPredicate getGenerationPredicate() {
        return generationPredicate;
    }

    public IVeinPopulator getVeinPopulator() {
        return veinPopulator;
    }

    public BlockFiller getBlockFiller() {
        return blockFiller;
    }

    public ShapeGenerator getShapeGenerator() {
        return shapeGenerator;
    }

    // === Package-private setters, used by DepositBuilder ===

    void setWeight(int weight) {
        this.weight = weight;
    }

    void setPriority(int priority) {
        this.priority = priority;
    }

    void setDensity(float density) {
        this.density = density;
    }

    void setAssignedName(String assignedName) {
        this.assignedName = assignedName;
    }

    void setDescription(String description) {
        this.description = description;
    }

    void setHeightLimit(int minHeight, int maxHeight) {
        this.heightLimit[0] = minHeight;
        this.heightLimit[1] = maxHeight;
    }

    void setCountAsVein(boolean countAsVein) {
        this.countAsVein = countAsVein;
    }

    void setBiomeWeightModifier(Function<Biome, Integer> biomeWeightModifier) {
        this.biomeWeightModifier = biomeWeightModifier;
    }

    void setDimensionFilter(Predicate<WorldProvider> dimensionFilter) {
        this.dimensionFilter = dimensionFilter;
    }

    void setGenerationPredicate(WorldBlockPredicate generationPredicate) {
        this.generationPredicate = generationPredicate;
    }

    void setVeinPopulator(IVeinPopulator veinPopulator) {
        this.veinPopulator = veinPopulator;
    }

    void setBlockFiller(BlockFiller blockFiller) {
        this.blockFiller = blockFiller;
    }

    void setShapeGenerator(ShapeGenerator shapeGenerator) {
        this.shapeGenerator = shapeGenerator;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof OreDepositDefinition))
            return false;

        OreDepositDefinition objDeposit = (OreDepositDefinition) obj;
        if (this.weight != objDeposit.getWeight())
            return false;
        if (this.density != objDeposit.getDensity())
            return false;
        if (this.priority != objDeposit.getPriority())
            return false;
        if (this.countAsVein != objDeposit.isVein())
            return false;
        if (this.getMinimumHeight() != objDeposit.getMinimumHeight())
            return false;
        if (this.getMaximumHeight() != objDeposit.getMaximumHeight())
            return false;
        if (!Objects.equals(this.assignedName, objDeposit.getAssignedName()))
            return false;
        if (!Objects.equals(this.description, objDeposit.getDescription()))
            return false;
        if (!Objects.equals(this.biomeWeightModifier, objDeposit.getBiomeWeightModifier()))
            return false;
        if (!Objects.equals(this.dimensionFilter, objDeposit.getDimensionFilter()))
            return false;
        if (!Objects.equals(this.generationPredicate, objDeposit.getGenerationPredicate()))
            return false;
        if (!Objects.equals(this.veinPopulator, objDeposit.getVeinPopulator()))
            return false;

        return true;
    }
}
