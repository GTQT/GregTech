package gregtech.api.pattern.element;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.casing.ICasingGroup;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.unification.material.Material;
import gregtech.api.util.BlockInfo;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

import java.util.function.Consumer;
import java.util.function.Predicate;
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

    /** Multiple blocks element */
    public static IStructureElement blocks(Block... blocks) {
        return ElementUtility.ofBlocks(blocks);
    }

    /** Block-state predicate element */
    public static IStructureElement blockPredicate(Predicate<IBlockState> predicate) {
        return ElementUtility.ofBlockPredicate(predicate);
    }

    /** Block-state predicate element with explicit candidates */
    public static IStructureElement blockPredicate(Predicate<IBlockState> predicate, Supplier<BlockInfo[]> candidates) {
        return ElementUtility.ofBlockPredicate(predicate, candidates);
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

    /** Hatch adder element accepting any of the supplied abilities */
    public static IStructureElement abilities(MultiblockAbility<?>... abilities) {
        return ElementUtility.ofAbilityGroup(abilities);
    }

    /** Hatch adder element accepting any of the supplied abilities with shared count constraints */
    public static IStructureElement abilities(int min, int max, MultiblockAbility<?>... abilities) {
        return ElementUtility.ofAbilityGroup(min, max, abilities);
    }

    /** Hatch adder element accepting any of the supplied abilities with shared count constraints and preview count */
    public static IStructureElement abilities(int min, int max, int previewCount, MultiblockAbility<?>... abilities) {
        return ElementUtility.ofAbilityGroup(min, max, previewCount, abilities);
    }

    /** Hatch adder element accepting any of the supplied abilities with shared per-layer count constraints */
    public static IStructureElement abilitiesPerLayer(int minLayer, int maxLayer, int previewCount,
                                                      MultiblockAbility<?>... abilities) {
        return ElementUtility.ofAbilityGroupPerLayer(minLayer, maxLayer, previewCount, abilities);
    }

    /** Hatch adder element with count constraints */
    public static IStructureElement hatch(MultiblockAbility<?> ability, int min, int max) {
        return ElementUtility.ofHatchAdder(ability, min, max);
    }

    /** Hatch adder element with count constraints and an explicit preview count */
    public static IStructureElement hatch(MultiblockAbility<?> ability, int min, int max, int previewCount) {
        return ElementUtility.ofHatchAdder(ability, min, max, previewCount);
    }

    /** Tiered block element */
    public static IStructureElement tiered(Supplier<BlockInfo[]> candidates, String channel) {
        return ElementUtility.ofTieredBlock(candidates, channel);
    }

    /** Tiered casing element with channel capture and count constraints */
    public static IStructureElement tieredCasing(ICasingGroup group, String channel, int min, int max) {
        return ElementUtility.ofTieredCasing(group, channel, min, max);
    }

    /** Specific MetaTileEntity element */
    public static IStructureElement metaTileEntities(MetaTileEntity... metaTileEntities) {
        return ElementUtility.ofMetaTileEntities(metaTileEntities);
    }

    /** Specific MetaTileEntity element with shared count constraints */
    public static IStructureElement metaTileEntities(int min, int max, MetaTileEntity... metaTileEntities) {
        return ElementUtility.ofMetaTileEntities(min, max, metaTileEntities);
    }

    /** Specific MetaTileEntity element with shared count constraints and preview count */
    public static IStructureElement metaTileEntities(int min, int max, int previewCount,
                                                    MetaTileEntity... metaTileEntities) {
        return ElementUtility.ofMetaTileEntities(min, max, previewCount, metaTileEntities);
    }

    /** Specific MetaTileEntity element contributing the supplied multiblock ability */
    public static IStructureElement metaTileEntitiesAsAbility(MultiblockAbility<?> ability,
                                                             int min, int max, int previewCount,
                                                             MetaTileEntity... metaTileEntities) {
        return ElementUtility.ofMetaTileEntitiesAsAbility(ability, min, max, previewCount, metaTileEntities);
    }

    /** Frame element matching frame blocks or frame pipes for the supplied materials */
    public static IStructureElement frames(Material... frameMaterials) {
        return ElementUtility.ofFrames(frameMaterials);
    }

    /** Lazy element */
    public static IStructureElement lazy(Supplier<IStructureElement> supplier) {
        return ElementUtility.lazy(supplier);
    }

    /** Element with match callback */
    public static IStructureElement onPass(Consumer<StructureEvaluationContext<?>> callback, IStructureElement e) {
        return ElementUtility.onElementPass(callback, e);
    }

    /** Element with channel name */
    public static IStructureElement withChannel(String channel, IStructureElement e) {
        return ElementUtility.withChannel(channel, e);
    }

    /** Element with preview tooltip lines */
    public static IStructureElement withTooltips(IStructureElement e, String... tips) {
        return ElementUtility.withTooltips(e, tips);
    }

    /** Element with a default MetaTileEntity preview/build candidate */
    public static IStructureElement withDefaultCandidate(IStructureElement e,
                                                         Supplier<? extends MetaTileEntity> candidate) {
        return ElementUtility.withDefaultCandidate(e, candidate);
    }

    /** Chain of elements */
    public static IStructureElement chain(IStructureElement... elements) {
        return ElementUtility.ofChain(elements);
    }
}
