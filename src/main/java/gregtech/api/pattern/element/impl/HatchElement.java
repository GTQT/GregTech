package gregtech.api.pattern.element.impl;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureMatchCollector;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Element that matches hatch positions for multiblock abilities.
 */
public class HatchElement implements IStructureElement<Object> {

    private final MultiblockAbility<?> ability;
    private final int minCount;
    private final int maxCount;
    private final TraceabilityPredicate legacyPredicate;
    private final TraceabilityPredicate.SimplePredicate limitPredicate;
    private final StructureElementPreview preview;

    public HatchElement(MultiblockAbility<?> ability) {
        this(ability, 0, -1, -1, null);
    }

    public HatchElement(MultiblockAbility<?> ability, int minCount, int maxCount) {
        this(ability, minCount, maxCount, -1, null);
    }

    public HatchElement(MultiblockAbility<?> ability, int minCount, int maxCount, int previewCount) {
        this(ability, minCount, maxCount, previewCount, null);
    }

    public HatchElement(MultiblockAbility<?> ability, int minCount, int maxCount, int previewCount,
                        @Nullable Supplier<? extends MetaTileEntity> defaultCandidate) {
        this.ability = ability;
        this.minCount = minCount;
        this.maxCount = maxCount;
        TraceabilityPredicate predicate = MultiblockControllerBase.abilities(ability);
        if (defaultCandidate != null) {
            predicate.setDefaultCandidate(defaultCandidate);
        }
        if (minCount > 0) {
            predicate.setMinGlobalLimited(minCount);
        }
        if (maxCount > 0) {
            predicate.setMaxGlobalLimited(maxCount);
        }
        if (previewCount >= 0) {
            predicate.setPreviewCount(previewCount);
        }
        this.legacyPredicate = predicate;
        this.limitPredicate = findLimitPredicate(predicate);
        this.preview = StructureElementPreview.fromPredicate(predicate);
    }

    @Override
    public boolean check(World world, BlockPos pos, PatternMatchContext context) {
        TileEntity te = world.getTileEntity(pos);
        MetaTileEntity mte = getMetaTileEntity(te);
        IMultiblockAbilityPart<?> abilityPart = asAbilityPart(mte);
        if (hasAbility(abilityPart)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean check(StructureEvaluationContext<Object> context) {
        MetaTileEntity mte = getMetaTileEntity(context.getTileEntity());
        IMultiblockAbilityPart<?> abilityPart = asAbilityPart(mte);
        if (!hasAbility(abilityPart)) {
            return false;
        }

        return context.transaction(transactionContext -> {
            if (!transactionContext.test(legacyPredicate)) {
                return false;
            }
            StructureMatchCollector collector = transactionContext.getCollector();
            boolean recorded = collector.recordAbility(this, (IMultiblockPart) abilityPart);
            if (!recorded) {
                transactionContext.setError(new TraceabilityPredicate.SinglePredicateError(limitPredicate(), 0));
            }
            return recorded;
        });
    }

    @Override
    public BlockInfo[] getCandidates() {
        List<BlockInfo> candidates = new ArrayList<>();
        collectCandidates(legacyPredicate.common, candidates);
        collectCandidates(legacyPredicate.limited, candidates);
        return candidates.toArray(new BlockInfo[0]);
    }

    @Override
    public StructureElementPreview getPreview() {
        return preview;
    }

    @Override
    public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                              EntityPlayer player, boolean skipHatches) {
        return false;
    }

    @Override
    public void spawnHint(World world, BlockPos pos) {
        // Hints are handled at a higher level
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
    public void collectRequirements(StructureEvaluationContext<Object> context) {
        context.getCollector().declareAbility(
                this, ability, minCount, maxCount,
                () -> new TraceabilityPredicate.SinglePredicateError(limitPredicate(), 1),
                () -> new TraceabilityPredicate.SinglePredicateError(limitPredicate(), 0));
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return legacyPredicate;
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

    private boolean hasAbility(IMultiblockAbilityPart<?> abilityPart) {
        if (abilityPart == null) {
            return false;
        }
        for (MultiblockAbility<?> a : abilityPart.getAbilities()) {
            if (a == ability) {
                return true;
            }
        }
        return false;
    }

    private TraceabilityPredicate.SimplePredicate limitPredicate() {
        return limitPredicate;
    }

    private static TraceabilityPredicate.SimplePredicate findLimitPredicate(
            TraceabilityPredicate predicate) {
        if (!predicate.limited.isEmpty()) {
            return predicate.limited.get(0);
        }
        if (!predicate.common.isEmpty()) {
            return predicate.common.get(0);
        }
        throw new IllegalStateException("Ability predicate did not contain a matcher");
    }

    private static void collectCandidates(
            List<TraceabilityPredicate.SimplePredicate> predicates,
            List<BlockInfo> candidates) {
        for (TraceabilityPredicate.SimplePredicate predicate : predicates) {
            if (predicate.candidates == null) continue;
            BlockInfo[] values = predicate.candidates.get();
            if (values != null) {
                candidates.addAll(Arrays.asList(values));
            }
        }
    }
}
