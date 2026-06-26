package gregtech.api.pattern.element;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.casing.ICasingGroup;
import gregtech.api.pattern.element.impl.AirElement;
import gregtech.api.pattern.element.impl.AnyElement;
import gregtech.api.pattern.element.impl.AbilityElement;
import gregtech.api.pattern.element.impl.BlockElement;
import gregtech.api.pattern.element.impl.BlockPredicateElement;
import gregtech.api.pattern.element.impl.ChainElement;
import gregtech.api.pattern.element.impl.FrameElement;
import gregtech.api.pattern.element.impl.HatchElement;
import gregtech.api.pattern.element.impl.MetaTileEntityElement;
import gregtech.api.pattern.element.impl.SelfElement;
import gregtech.api.pattern.element.impl.TieredCasingElement;
import gregtech.api.pattern.element.impl.TieredElement;
import gregtech.api.pattern.element.impl.WrapperElement;
import gregtech.api.unification.material.Material;
import gregtech.api.util.BlockInfo;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

import java.util.function.Consumer;
import java.util.function.Predicate;
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

    /** Create an element matching any default state of the supplied blocks */
    public static IStructureElement ofBlocks(Block... blocks) {
        return new BlockElement(java.util.Arrays.stream(blocks)
                .map(Block::getDefaultState)
                .toArray(IBlockState[]::new));
    }

    /** Create an element from a block-state predicate */
    public static IStructureElement ofBlockPredicate(Predicate<IBlockState> predicate) {
        return new BlockPredicateElement(predicate, null);
    }

    /** Create an element from a block-state predicate with explicit candidates */
    public static IStructureElement ofBlockPredicate(Predicate<IBlockState> predicate, Supplier<BlockInfo[]> candidates) {
        return new BlockPredicateElement(predicate, candidates);
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

    /** Create a hatch adder element accepting any of the supplied abilities */
    public static IStructureElement ofAbilityGroup(MultiblockAbility<?>... abilities) {
        return new AbilityElement(abilities);
    }

    /** Create a hatch adder element accepting any of the supplied abilities with shared count constraints */
    public static IStructureElement ofAbilityGroup(int min, int max, MultiblockAbility<?>... abilities) {
        return new AbilityElement(min, max, abilities);
    }

    /** Create a hatch adder element accepting any of the supplied abilities with shared constraints and preview count */
    public static IStructureElement ofAbilityGroup(int min, int max, int previewCount,
                                                  MultiblockAbility<?>... abilities) {
        return new AbilityElement(min, max, previewCount, abilities);
    }

    /** Create a hatch adder element accepting any of the supplied abilities with shared per-layer constraints */
    public static IStructureElement ofAbilityGroupPerLayer(int minLayer, int maxLayer, int previewCount,
                                                          MultiblockAbility<?>... abilities) {
        return AbilityElement.perLayer(minLayer, maxLayer, previewCount, abilities);
    }

    /** Create a hatch adder element with min/max count constraints */
    public static IStructureElement ofHatchAdder(MultiblockAbility<?> ability, int min, int max) {
        return new HatchElement(ability, min, max);
    }

    /** Create a hatch adder element with count constraints and an explicit preview count */
    public static IStructureElement ofHatchAdder(MultiblockAbility<?> ability, int min, int max, int previewCount) {
        return new HatchElement(ability, min, max, previewCount);
    }

    /** Create a tiered block element with channel name */
    public static IStructureElement ofTieredBlock(Supplier<BlockInfo[]> candidates, String channelName) {
        return new TieredElement(candidates, channelName);
    }

    /** Create a tiered casing element with channel capture and count constraints */
    public static IStructureElement ofTieredCasing(ICasingGroup group, String channelName, int min, int max) {
        return new TieredCasingElement(group, channelName, min, max);
    }

    /** Create an element matching specific MetaTileEntity registrations */
    public static IStructureElement ofMetaTileEntities(MetaTileEntity... metaTileEntities) {
        return new MetaTileEntityElement(metaTileEntities);
    }

    /** Create an element matching specific MetaTileEntity registrations with shared count constraints */
    public static IStructureElement ofMetaTileEntities(int min, int max, MetaTileEntity... metaTileEntities) {
        return new MetaTileEntityElement(min, max, metaTileEntities);
    }

    /** Create an element matching specific MetaTileEntity registrations with shared constraints and preview count */
    public static IStructureElement ofMetaTileEntities(int min, int max, int previewCount,
                                                      MetaTileEntity... metaTileEntities) {
        return new MetaTileEntityElement(min, max, previewCount, metaTileEntities);
    }

    /** Create an element matching specific MetaTileEntity registrations and contributing an ability */
    public static IStructureElement ofMetaTileEntitiesAsAbility(MultiblockAbility<?> ability,
                                                               int min, int max, int previewCount,
                                                               MetaTileEntity... metaTileEntities) {
        return new MetaTileEntityElement(ability, min, max, previewCount, metaTileEntities);
    }

    /** Create an element matching frame blocks or frame pipes for the supplied materials */
    public static IStructureElement ofFrames(Material... frameMaterials) {
        return new FrameElement(frameMaterials);
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

    /** Create an element with preview tooltip lines */
    public static IStructureElement withTooltips(IStructureElement e, String... tips) {
        return WrapperElement.withTooltips(e, tips);
    }

    /** Create an element with a default MetaTileEntity preview/build candidate */
    public static IStructureElement withDefaultCandidate(IStructureElement e,
                                                         Supplier<? extends MetaTileEntity> candidate) {
        return WrapperElement.withDefaultCandidate(e, candidate);
    }

    /** Create a chain of alternative elements (any may match) */
    public static IStructureElement ofChain(IStructureElement... elements) {
        return new ChainElement(elements);
    }
}
