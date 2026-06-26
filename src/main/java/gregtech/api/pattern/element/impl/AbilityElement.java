package gregtech.api.pattern.element.impl;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockPredicates;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureMatchCollector;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Direct element accepting hatches for any of a shared ability set.
 */
public class AbilityElement implements ITypedStructureElement<Object> {

    private final MultiblockAbility<?>[] abilities;
    private final int minCount;
    private final int maxCount;
    private final int minLayerCount;
    private final int maxLayerCount;
    private final TraceabilityPredicate legacyPredicate;
    private final TraceabilityPredicate.SimplePredicate countPredicate;
    private final StructureElementPreview preview;

    public AbilityElement(MultiblockAbility<?>... abilities) {
        this(0, -1, -1, abilities);
    }

    public AbilityElement(int minCount, int maxCount, MultiblockAbility<?>... abilities) {
        this(minCount, maxCount, -1, abilities);
    }

    public AbilityElement(int minCount, int maxCount, int previewCount,
                          MultiblockAbility<?>... abilities) {
        this(minCount, maxCount, -1, -1, previewCount, abilities);
    }

    private AbilityElement(int minCount, int maxCount, int minLayerCount, int maxLayerCount, int previewCount,
                           MultiblockAbility<?>... abilities) {
        this.abilities = Arrays.stream(abilities)
                .filter(Objects::nonNull)
                .toArray(MultiblockAbility<?>[]::new);
        this.minCount = minCount;
        this.maxCount = maxCount;
        this.minLayerCount = minLayerCount;
        this.maxLayerCount = maxLayerCount;
        this.legacyPredicate = buildLegacyPredicate(previewCount);
        this.countPredicate = findCountPredicate(legacyPredicate);
        this.preview = StructureElementPreview.fromPredicate(legacyPredicate);
    }

    public static AbilityElement perLayer(int minLayerCount, int maxLayerCount, int previewCount,
                                          MultiblockAbility<?>... abilities) {
        return new AbilityElement(0, -1, minLayerCount, maxLayerCount, previewCount, abilities);
    }

    @Override
    public boolean check(@NotNull StructureEvaluationContext<Object> context) {
        MetaTileEntity mte = getMetaTileEntity(context.getTileEntity());
        IMultiblockAbilityPart<?> abilityPart = asAbilityPart(mte);
        MultiblockAbility<?> matchedAbility = findMatchedAbility(abilityPart);
        if (matchedAbility == null) {
            return false;
        }

        return context.transaction(transactionContext -> {
            StructureMatchCollector collector = transactionContext.getCollector();
            if (hasCountConstraint() && !collector.recordAbility(this, (IMultiblockPart) abilityPart)) {
                transactionContext.setError(new TraceabilityPredicate.SinglePredicateError(countPredicate, 0));
                return false;
            }
            if (!hasCountConstraint()) {
                collector.addPart((IMultiblockPart) abilityPart);
            }
            return true;
        });
    }

    @Override
    public BlockInfo[] getCandidates() {
        return Arrays.stream(abilities)
                .map(MultiblockAbility.REGISTRY::get)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(AbilityElement::candidateInfo)
                .toArray(BlockInfo[]::new);
    }

    @Override
    public StructureElementPreview getPreview() {
        return preview;
    }

    @Override
    public int getMinGlobalCount() {
        return minCount;
    }

    @Override
    public int getMaxGlobalCount() {
        return maxCount;
    }

    @Override
    public int getMinLayerCount() {
        return minLayerCount;
    }

    @Override
    public int getMaxLayerCount() {
        return maxLayerCount;
    }

    @Override
    public void collectRequirements(@NotNull StructureEvaluationContext<Object> context) {
        if (!hasCountConstraint()) {
            return;
        }
        if (abilities.length == 1) {
            context.getCollector().declareAbility(
                    this, abilities[0], minCount, maxCount,
                    () -> new TraceabilityPredicate.SinglePredicateError(countPredicate, 1),
                    () -> new TraceabilityPredicate.SinglePredicateError(countPredicate, 0));
        } else {
            context.getCollector().declareCount(
                    this, minCount, maxCount,
                    () -> new TraceabilityPredicate.SinglePredicateError(countPredicate, 1),
                    () -> new TraceabilityPredicate.SinglePredicateError(countPredicate, 0));
        }
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return legacyPredicate;
    }

    @Override
    public boolean usesLegacyPredicateRuntime() {
        return minLayerCount > 0 || maxLayerCount >= 0;
    }

    private boolean hasCountConstraint() {
        return minCount > 0 || maxCount >= 0;
    }

    private MultiblockAbility<?> findMatchedAbility(IMultiblockAbilityPart<?> abilityPart) {
        if (abilityPart == null) {
            return null;
        }
        for (MultiblockAbility<?> ability : abilityPart.getAbilities()) {
            if (ArrayUtils.contains(abilities, ability)) {
                return ability;
            }
        }
        return null;
    }

    private TraceabilityPredicate buildLegacyPredicate(int previewCount) {
        TraceabilityPredicate predicate = MultiblockPredicates.abilities(abilities);
        if (minCount > 0) {
            predicate.setMinGlobalLimited(minCount);
        }
        if (maxCount >= 0) {
            predicate.setMaxGlobalLimited(maxCount);
        }
        if (minLayerCount > 0) {
            predicate.setMinLayerLimited(minLayerCount);
        }
        if (maxLayerCount >= 0) {
            predicate.setMaxLayerLimited(maxLayerCount);
        }
        if (previewCount >= 0) {
            predicate.setPreviewCount(previewCount);
        }
        return predicate;
    }

    private static TraceabilityPredicate.SimplePredicate findCountPredicate(TraceabilityPredicate predicate) {
        if (!predicate.limited.isEmpty()) {
            return predicate.limited.get(0);
        }
        if (!predicate.common.isEmpty()) {
            return predicate.common.get(0);
        }
        throw new IllegalStateException("Ability predicate did not contain a matcher");
    }

    private static MetaTileEntity getMetaTileEntity(TileEntity tileEntity) {
        if (tileEntity instanceof IGregTechTileEntity) {
            return ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
        }
        return null;
    }

    private static IMultiblockAbilityPart<?> asAbilityPart(MetaTileEntity metaTileEntity) {
        return metaTileEntity instanceof IMultiblockAbilityPart<?>
                ? (IMultiblockAbilityPart<?>) metaTileEntity
                : null;
    }

    private static BlockInfo candidateInfo(MetaTileEntity tile) {
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        holder.setMetaTileEntity(tile);
        holder.getMetaTileEntity().onPlacement();
        holder.getMetaTileEntity().setFrontFacing(EnumFacing.SOUTH);
        return new BlockInfo(tile.getBlock().getDefaultState(), holder);
    }
}
