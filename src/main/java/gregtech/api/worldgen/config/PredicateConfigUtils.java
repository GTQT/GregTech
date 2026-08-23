package gregtech.api.worldgen.config;

import gregtech.api.unification.ore.StoneType;
import gregtech.api.util.WorldBlockPredicate;

public class PredicateConfigUtils {

    private PredicateConfigUtils() {}

    /** 任意方块都可被替换 */
    public static WorldBlockPredicate any() {
        return (state, world, pos) -> true;
    }

    /** 仅石质方块可被替换（默认生成谓词） */
    public static WorldBlockPredicate stoneType() {
        return (state, world, pos) -> StoneType.computeStoneType(state, world, pos) != null;
    }
}
