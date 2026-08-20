package gregtech.api.unification.ore;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import com.google.common.base.Predicate;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Memoizes {@link StoneType#computeStoneType(IBlockState, IBlockAccess, BlockPos)} results.
 *
 * <p>Worldgen performs the linear registry scan for every generated block. Since stone type
 * predicates are pure functions of the blockstate, the outcome can be cached per state. This is
 * only sound for blocks whose {@link Block#isReplaceableOreGen} is the vanilla implementation,
 * which evaluates exactly {@code predicate.test(state)}; blocks overriding it may consult
 * {@code world}/{@code pos}, so {@link StoneType} keeps the uncached probe path for them.</p>
 *
 * <p>The stone type registry is populated statically and must not change at runtime, and blockstates
 * are immutable, so cached entries never go stale. All maps are concurrent: chunk generation can run
 * off the server thread (e.g. async chunk gen mods).</p>
 */
final class StoneTypeCache {

    /** Whether a block class declares the vanilla {@link Block#isReplaceableOreGen} implementation. */
    private static final Map<Class<?>, Boolean> STATE_ONLY_BLOCKS = new ConcurrentHashMap<>();
    /** state -> matching stone type, for state-only blocks only. */
    private static final Map<IBlockState, StoneType> MATCHES = new ConcurrentHashMap<>(1024);
    /** states that matched no stone type, cached to skip re-scanning misses. */
    private static final Set<IBlockState> MISSES = ConcurrentHashMap.newKeySet();

    private StoneTypeCache() {
    }

    /** @return whether this block's isReplaceableOreGen result depends only on the state */
    static boolean isStateOnly(Block block) {
        return STATE_ONLY_BLOCKS.computeIfAbsent(block.getClass(), StoneTypeCache::declaresVanillaImplementation);
    }

    private static boolean declaresVanillaImplementation(Class<?> blockClass) {
        try {
            return blockClass.getMethod("isReplaceableOreGen",
                            IBlockState.class, IBlockAccess.class, BlockPos.class, Predicate.class)
                    .getDeclaringClass() == Block.class;
        } catch (NoSuchMethodException | SecurityException e) {
            return false;
        }
    }

    @Nullable
    static StoneType getOrCompute(IBlockState state, Function<IBlockState, StoneType> computer) {
        StoneType type = MATCHES.get(state);
        if (type != null) {
            return type;
        }
        if (MISSES.contains(state)) {
            return null;
        }
        type = computer.apply(state);
        if (type == null) {
            MISSES.add(state);
        } else {
            MATCHES.put(state, type);
        }
        return type;
    }
}
