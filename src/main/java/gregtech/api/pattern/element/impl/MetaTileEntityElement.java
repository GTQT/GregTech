package gregtech.api.pattern.element.impl;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureMatchCollector;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Direct element for a fixed set of MetaTileEntity registrations.
 */
public class MetaTileEntityElement implements ITypedStructureElement<Object> {

    private final ResourceLocation[] ids;
    private final MetaTileEntity[] candidates;
    @Nullable
    private final MultiblockAbility<?> ability;
    private final int minCount;
    private final int maxCount;
    private final TraceabilityPredicate legacyPredicate;
    private final TraceabilityPredicate.SimplePredicate countPredicate;
    private final StructureElementPreview preview;

    public MetaTileEntityElement(MetaTileEntity... metaTileEntities) {
        this(0, -1, -1, metaTileEntities);
    }

    public MetaTileEntityElement(int minCount, int maxCount, MetaTileEntity... metaTileEntities) {
        this(minCount, maxCount, -1, metaTileEntities);
    }

    public MetaTileEntityElement(int minCount, int maxCount, int previewCount,
                                 MetaTileEntity... metaTileEntities) {
        this(null, minCount, maxCount, previewCount, metaTileEntities);
    }

    public MetaTileEntityElement(@Nullable MultiblockAbility<?> ability, int minCount, int maxCount, int previewCount,
                                 MetaTileEntity... metaTileEntities) {
        this.candidates = Arrays.stream(metaTileEntities)
                .filter(Objects::nonNull)
                .toArray(MetaTileEntity[]::new);
        this.ids = Arrays.stream(this.candidates)
                .map(tile -> tile.metaTileEntityId)
                .toArray(ResourceLocation[]::new);
        this.ability = ability;
        this.minCount = minCount;
        this.maxCount = maxCount;
        this.legacyPredicate = buildLegacyPredicate(previewCount);
        this.countPredicate = findCountPredicate(legacyPredicate);
        this.preview = StructureElementPreview.fromPredicate(legacyPredicate);
    }

    @Override
    public boolean check(@NotNull StructureEvaluationContext<Object> context) {
        MetaTileEntity metaTileEntity = getMetaTileEntity(context.getTileEntity());
        if (!matches(metaTileEntity)) {
            return false;
        }

        return context.transaction(transactionContext -> {
            StructureMatchCollector collector = transactionContext.getCollector();
            if (ability != null && metaTileEntity instanceof IMultiblockPart) {
                if (!collector.recordAbility(this, (IMultiblockPart) metaTileEntity)) {
                    transactionContext.setError(new TraceabilityPredicate.SinglePredicateError(countPredicate, 0));
                    return false;
                }
                return true;
            }
            if (hasCountConstraint() && !collector.recordCount(this)) {
                transactionContext.setError(new TraceabilityPredicate.SinglePredicateError(countPredicate, 0));
                return false;
            }
            if (metaTileEntity instanceof IMultiblockPart) {
                collector.addPart((IMultiblockPart) metaTileEntity);
            }
            return true;
        });
    }

    @Override
    public BlockInfo[] getCandidates() {
        return Arrays.stream(candidates)
                .map(MetaTileEntityElement::candidateInfo)
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
    public void collectRequirements(@NotNull StructureEvaluationContext<Object> context) {
        if (!hasCountConstraint()) {
            return;
        }
        if (ability == null) {
            context.getCollector().declareCount(
                    this, minCount, maxCount,
                    () -> new TraceabilityPredicate.SinglePredicateError(countPredicate, 1),
                    () -> new TraceabilityPredicate.SinglePredicateError(countPredicate, 0));
        } else {
            context.getCollector().declareAbility(
                    this, ability, minCount, maxCount,
                    () -> new TraceabilityPredicate.SinglePredicateError(countPredicate, 1),
                    () -> new TraceabilityPredicate.SinglePredicateError(countPredicate, 0));
        }
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return legacyPredicate;
    }

    private boolean matches(MetaTileEntity metaTileEntity) {
        return metaTileEntity != null && ArrayUtils.contains(ids, metaTileEntity.metaTileEntityId);
    }

    private boolean hasCountConstraint() {
        return minCount > 0 || maxCount >= 0;
    }

    private TraceabilityPredicate buildLegacyPredicate(int previewCount) {
        TraceabilityPredicate predicate = new TraceabilityPredicate(
                blockWorldState -> matches(getMetaTileEntity(blockWorldState.getTileEntity())),
                this::getCandidates);
        if (minCount > 0) {
            predicate.setMinGlobalLimited(minCount);
        }
        if (maxCount >= 0) {
            predicate.setMaxGlobalLimited(maxCount);
        }
        if (previewCount >= 0) {
            predicate.setPreviewCount(previewCount);
        }
        if (ability != null) {
            predicate.setAbility(ability);
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
        throw new IllegalStateException("MetaTileEntity predicate did not contain a matcher");
    }

    private static MetaTileEntity getMetaTileEntity(TileEntity tileEntity) {
        if (tileEntity instanceof IGregTechTileEntity) {
            return ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
        }
        return null;
    }

    private static BlockInfo candidateInfo(MetaTileEntity tile) {
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        holder.setMetaTileEntity(tile);
        holder.getMetaTileEntity().onPlacement();
        holder.getMetaTileEntity().setFrontFacing(EnumFacing.SOUTH);
        return new BlockInfo(tile.getBlock().getDefaultState(), holder);
    }
}
