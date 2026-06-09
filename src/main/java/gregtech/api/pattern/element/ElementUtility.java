package gregtech.api.pattern.element;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.impl.AirElement;
import gregtech.api.pattern.element.impl.AnyElement;
import gregtech.api.pattern.element.impl.BlockElement;
import gregtech.api.pattern.element.impl.ChainElement;
import gregtech.api.pattern.element.impl.HatchElement;
import gregtech.api.pattern.element.impl.LegacyElement;
import gregtech.api.pattern.element.impl.SelfElement;
import gregtech.api.pattern.element.impl.TieredElement;
import gregtech.api.pattern.element.impl.WrapperElement;
import gregtech.api.util.BlockInfo;

import net.minecraft.block.state.IBlockState;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Static factory methods for creating {@link IStructureElement} instances.
 * Method names are descriptive (longer form). For shorter aliases, see {@link Elements}.
 */
public final class ElementUtility {

    private ElementUtility() {}

    /** Create an element matching a specific block state */
    public static IStructureElement ofBlock(IBlockState state) {
        return new BlockElement(state);
    }

    /** Create an element matching multiple block states */
    public static IStructureElement ofBlocks(IBlockState... states) {
        return new BlockElement(states);
    }

    /** Create an element matching air */
    public static IStructureElement ofAir() {
        return AirElement.INSTANCE;
    }

    /** Create a wildcard element matching any block */
    public static IStructureElement ofAny() {
        return AnyElement.INSTANCE;
    }

    /** Create a self-predicate element matching the controller's own type */
    public static IStructureElement ofSelf(
            Class<? extends gregtech.api.metatileentity.multiblock.MultiblockControllerBase> clazz) {
        return new SelfElement(clazz);
    }

    /** Create a hatch adder element for the given ability */
    public static IStructureElement ofHatchAdder(MultiblockAbility<?> ability) {
        return new HatchElement(ability);
    }

    /** Create a hatch adder element with min/max count constraints */
    public static IStructureElement ofHatchAdder(MultiblockAbility<?> ability, int min, int max) {
        return new HatchElement(ability, min, max);
    }

    /** Create a tiered block element with channel name */
    public static IStructureElement ofTieredBlock(Supplier<BlockInfo[]> candidates, String channelName) {
        return new TieredElement(candidates, channelName);
    }

    /** Create a lazily-initialized element */
    public static IStructureElement lazy(Supplier<IStructureElement> supplier) {
        return new WrapperElement(null, supplier, null, null);
    }

    /** Create an element with a callback on match */
    public static IStructureElement onElementPass(Consumer<PatternMatchContext> callback, IStructureElement e) {
        return new WrapperElement(e, null, callback, null);
    }

    /** Create an element with a channel name for tier selection */
    public static IStructureElement withChannel(String channelName, IStructureElement e) {
        return new WrapperElement(e, null, null, channelName);
    }

    /** Create a chain of alternative elements (any may match) */
    public static IStructureElement ofChain(IStructureElement... elements) {
        return new ChainElement(elements);
    }

    /** Wrap an existing TraceabilityPredicate as an element (backward compatibility) */
    public static IStructureElement ofLegacy(TraceabilityPredicate predicate) {
        return new LegacyElement(predicate);
    }
}
