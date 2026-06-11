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
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Element that matches hatch positions for multiblock abilities.
 */
public class HatchElement implements IStructureElement<Object> {

    private final MultiblockAbility<?> ability;
    private final int minCount;
    private final int maxCount;

    public HatchElement(MultiblockAbility<?> ability) {
        this(ability, 0, -1);
    }

    public HatchElement(MultiblockAbility<?> ability, int minCount, int maxCount) {
        this.ability = ability;
        this.minCount = minCount;
        this.maxCount = maxCount;
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

        StructureMatchCollector collector = context.getCollector();
        boolean recorded = collector.recordAbility(this, (IMultiblockPart) abilityPart);
        if (!recorded) {
            context.setError(new TraceabilityPredicate.SinglePredicateError(limitPredicate(), 0));
        }
        return recorded;
    }

    @Override
    public BlockInfo[] getCandidates() {
        return new BlockInfo[0];
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
        TraceabilityPredicate pred = MultiblockControllerBase.abilities(ability);
        if (minCount > 0) {
            pred = pred.setMinGlobalLimited(minCount);
        }
        if (maxCount > 0) {
            pred = pred.setMaxGlobalLimited(maxCount);
        }
        return pred;
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
        TraceabilityPredicate predicate = toPredicate();
        if (!predicate.limited.isEmpty()) {
            return predicate.limited.get(0);
        }
        if (!predicate.common.isEmpty()) {
            return predicate.common.get(0);
        }
        TraceabilityPredicate.SimplePredicate fallback =
                new TraceabilityPredicate.SimplePredicate(state -> false, this::getCandidates);
        fallback.minGlobalCount = minCount;
        fallback.maxGlobalCount = maxCount;
        fallback.previewCount = Math.max(1, minCount);
        fallback.ability = ability;
        return fallback;
    }
}
