package gregtech.api.pattern.element;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.util.BlockInfo;

import net.minecraft.block.state.IBlockState;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Short-method-name aliases for creating {@link IStructureElement} instances.
 * Designed for use with static imports:
 * <pre>{@code
 * import static gregtech.api.pattern.element.Elements.*;
 * // ...
 * .where('X', block(casingState))
 * .where('H', hatch(IMPORT_ITEMS))
 * }</pre>
 *
 * @see ElementUtility for the same factory methods with longer descriptive names
 */
public final class Elements {

    private Elements() {}

    /** Block state element */
    public static IStructureElement block(IBlockState state) {
        return ElementUtility.ofBlock(state);
    }

    /** Multiple block states element */
    public static IStructureElement blocks(IBlockState... states) {
        return ElementUtility.ofBlocks(states);
    }

    /** Air element */
    public static IStructureElement air() {
        return ElementUtility.ofAir();
    }

    /** Any block wildcard element */
    public static IStructureElement any() {
        return ElementUtility.ofAny();
    }

    /** Self-predicate element */
    public static IStructureElement self(
            Class<? extends gregtech.api.metatileentity.multiblock.MultiblockControllerBase> clazz) {
        return ElementUtility.ofSelf(clazz);
    }

    /** Hatch adder element */
    public static IStructureElement hatch(MultiblockAbility<?> ability) {
        return ElementUtility.ofHatchAdder(ability);
    }

    /** Hatch adder element with count constraints */
    public static IStructureElement hatch(MultiblockAbility<?> ability, int min, int max) {
        return ElementUtility.ofHatchAdder(ability, min, max);
    }

    /** Tiered block element */
    public static IStructureElement tiered(Supplier<BlockInfo[]> candidates, String channel) {
        return ElementUtility.ofTieredBlock(candidates, channel);
    }

    /** Lazy element */
    public static IStructureElement lazy(Supplier<IStructureElement> supplier) {
        return ElementUtility.lazy(supplier);
    }

    /** Element with match callback */
    public static IStructureElement onPass(Consumer<PatternMatchContext> callback, IStructureElement e) {
        return ElementUtility.onElementPass(callback, e);
    }

    /** Element with channel name */
    public static IStructureElement withChannel(String channel, IStructureElement e) {
        return ElementUtility.withChannel(channel, e);
    }

    /** Chain of elements */
    public static IStructureElement chain(IStructureElement... elements) {
        return ElementUtility.ofChain(elements);
    }

    /** Legacy TraceabilityPredicate wrapper */
    public static IStructureElement legacy(TraceabilityPredicate predicate) {
        return ElementUtility.ofLegacy(predicate);
    }
}
