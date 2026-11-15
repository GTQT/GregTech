package gregtech.api.unification.ore;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.util.GTControlledRegistry;
import gregtech.api.util.Mods;
import gregtech.common.ConfigHolder;
import gregtech.integration.jei.basic.OreByProduct;

import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * For ore generation
 */
public class StoneType implements Comparable<StoneType> {

    public static final GTControlledRegistry<String, StoneType> STONE_TYPE_REGISTRY = new GTControlledRegistry<>(128);
    /**
     * Ids that were handed out by {@link #getStoneTypeID()} but whose StoneType has not been created yet, so two
     * claims in the same initialization pass cannot return the same id.
     */
    private static final java.util.Set<Integer> claimedIds = new java.util.HashSet<>();

    static {
        // ids 0..11 are reserved for the built-in stone types declared in StoneTypes, so a mod that
        // claims an id early can never take one of them.
        for (int id = 0; id < StoneTypes.BUILTIN_STONE_TYPE_COUNT; id++) {
            claimedIds.add(id);
        }
    }

    private static final ThreadLocal<Boolean> hasDummyPredicateRan = ThreadLocal.withInitial(() -> false);
    private static final com.google.common.base.Predicate<IBlockState> dummyPredicate = state -> {
        hasDummyPredicateRan.set(true);
        return false;
    };
    public final String name;
    public final OrePrefix processingPrefix;

    /** @return the lean OrePrefix counterpart for this stone type, or {@link OrePrefix#oreLean} as fallback */
    public OrePrefix getLeanProcessingPrefix() {
        return OrePrefix.leanCounterparts.getOrDefault(processingPrefix, OrePrefix.oreLean);
    }
    public final Material stoneMaterial;
    public final Supplier<IBlockState> stone;
    public final SoundType soundType;
    public final boolean shouldBeDroppedAsItem;
    // we are using guava predicate because isReplaceableOreGen uses it
    @SuppressWarnings("Guava")
    private final com.google.common.base.Predicate<IBlockState> predicate;

    public StoneType(int id, String name, SoundType soundType, OrePrefix processingPrefix, Material stoneMaterial,
                     Supplier<IBlockState> stone, Predicate<IBlockState> predicate, boolean shouldBeDroppedAsItem) {
        Preconditions.checkArgument(
                stoneMaterial.hasProperty(PropertyKey.DUST),
                "Stone type must be made with a Material with the Dust Property!");
        this.name = name;
        this.soundType = soundType;
        this.processingPrefix = processingPrefix;
        this.stoneMaterial = stoneMaterial;
        this.stone = stone;
        this.predicate = predicate::test;
        this.shouldBeDroppedAsItem = shouldBeDroppedAsItem || ConfigHolder.worldgen.allUniqueStoneTypes;
        STONE_TYPE_REGISTRY.register(id, name, this);
        claimedIds.add(id);
        if (Mods.JustEnoughItems.isModLoaded() && this.shouldBeDroppedAsItem) {
            OreByProduct.addOreByProductPrefix(this.processingPrefix);
        }
    }

    public static void init() {
        // noinspection ResultOfMethodCallIgnored
        StoneTypes.STONE.name.getBytes();
    }

    public static int getStoneTypeID() {
        for (int id = 0; id < 128; id++) {
            if (STONE_TYPE_REGISTRY.getObjectById(id) == null && !claimedIds.contains(id)) {
                claimedIds.add(id);
                return id;
            }
        }
        throw new IllegalStateException("No free StoneType id left; the stone type registry is full (128 ids)");
    }

    public static StoneType computeStoneType(IBlockState state, IBlockAccess world, BlockPos pos) {
        // Fast path: for blocks with the vanilla isReplaceableOreGen the result only depends on the state
        // (isReplaceableOreGen(state, world, pos, predicate) == predicate.test(state)), so the registry
        // scan is memoized per blockstate. Blocks overriding isReplaceableOreGen may consult world/pos,
        // so they keep the uncached probe path below.
        if (StoneTypeCache.isStateOnly(state.getBlock())) {
            return StoneTypeCache.getOrCompute(state, StoneType::computeByPredicate);
        }
        // First: check if this Block's isReplaceableOreGen even considers the predicate passed through
        boolean dummy$isReplaceableOreGen = state.getBlock().isReplaceableOreGen(state, world, pos, dummyPredicate);
        if (hasDummyPredicateRan.get()) {
            // Current Block's isReplaceableOreGen does indeed consider the predicate
            // Reset hasDummyPredicateRan for the next test
            hasDummyPredicateRan.set(false);
            // Pass through actual predicates and test for real
            for (StoneType stoneType : STONE_TYPE_REGISTRY) {
                if (state.getBlock().isReplaceableOreGen(state, world, pos, stoneType.predicate)) {
                    // Found suitable match
                    return stoneType;
                }
            }
        } else if (dummy$isReplaceableOreGen) {
            // It is not considered, but the test still returned true (this means the impl was probably very lazily
            // done)
            // We have to test against the IBlockState ourselves to see if there's a suitable StoneType
            return computeByPredicate(state);
        }
        return null;
    }

    /**
     * Scans the registry for the first stone type whose predicate accepts the state.
     *
     * <p>Equivalent to testing every predicate against the state; only used where the queried block's
     * isReplaceableOreGen is known to be state-only (see {@link StoneTypeCache}).</p>
     */
    private static StoneType computeByPredicate(IBlockState state) {
        for (StoneType stoneType : STONE_TYPE_REGISTRY) {
            if (stoneType.predicate.test(state)) {
                // Found suitable match
                return stoneType;
            }
        }
        return null;
    }

    @Override
    public int compareTo(@NotNull StoneType stoneType) {
        return STONE_TYPE_REGISTRY.getIDForObject(this) - STONE_TYPE_REGISTRY.getIDForObject(stoneType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StoneType stoneType = (StoneType) o;

        if (shouldBeDroppedAsItem != stoneType.shouldBeDroppedAsItem) return false;
        if (!name.equals(stoneType.name)) return false;
        if (!processingPrefix.equals(stoneType.processingPrefix)) return false;
        if (!stoneMaterial.equals(stoneType.stoneMaterial)) return false;
        if (!stone.equals(stoneType.stone)) return false;
        if (!soundType.equals(stoneType.soundType)) return false;
        return predicate.equals(stoneType.predicate);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + processingPrefix.hashCode();
        result = 31 * result + stoneMaterial.hashCode();
        result = 31 * result + stone.hashCode();
        result = 31 * result + soundType.hashCode();
        result = 31 * result + predicate.hashCode();
        result = 31 * result + (shouldBeDroppedAsItem ? 1 : 0);
        return result;
    }

    public static class Builder {

        private final int id;
        private final String name;
        private SoundType soundType;
        private OrePrefix processingPrefix;
        private Material stoneMaterial;
        private Supplier<IBlockState> stone;
        private Predicate<IBlockState> predicate;
        private boolean shouldBeDroppedAsItem;

        private Builder(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public static Builder create(int id, String name) {
            return new Builder(id, name);
        }

        /**
         * Creates a builder that claims the next free id via {@link StoneType#getStoneTypeID()}.
         *
         * <p>Equivalent to {@code create(StoneType.getStoneTypeID(), name)}; use this so addon stone
         * types never collide with ids already used by other mods.</p>
         */
        public static Builder create(@NotNull String name) {
            return new Builder(StoneType.getStoneTypeID(), name);
        }

        public Builder soundType(SoundType soundType) {
            this.soundType = soundType;
            return this;
        }

        public Builder processingPrefix(OrePrefix processingPrefix) {
            this.processingPrefix = processingPrefix;
            return this;
        }

        public Builder stoneMaterial(Material stoneMaterial) {
            this.stoneMaterial = stoneMaterial;
            return this;
        }

        public Builder stone(Supplier<IBlockState> stone) {
            this.stone = stone;
            return this;
        }

        public Builder predicate(Predicate<IBlockState> predicate) {
            this.predicate = predicate;
            return this;
        }

        public Builder shouldBeDroppedAsItem(boolean shouldBeDroppedAsItem) {
            this.shouldBeDroppedAsItem = shouldBeDroppedAsItem;
            return this;
        }

        public StoneType build() {
            return new StoneType(id, name, soundType, processingPrefix, stoneMaterial, stone, predicate,
                    shouldBeDroppedAsItem);
        }
    }

}
