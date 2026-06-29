package gregtech.api.pattern.element.impl;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.CountLimitError;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureMatchCollector;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Element that matches hatch positions for multiblock abilities.
 */
public class HatchElement implements ITypedStructureElement<Object> {

    private final MultiblockAbility<?> ability;
    private final int minCount;
    private final int maxCount;
    private final int previewCount;
    @Nullable
    private final Supplier<? extends MetaTileEntity> defaultCandidate;
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
        this.previewCount = previewCount;
        this.defaultCandidate = defaultCandidate;
        this.preview = buildPreview();
    }

    @Override
    public boolean check(StructureEvaluationContext<Object> context) {
        MetaTileEntity mte = getMetaTileEntity(context.getTileEntity());
        IMultiblockAbilityPart<?> abilityPart = asAbilityPart(mte);
        if (!hasAbility(abilityPart)) {
            return false;
        }

        return context.transaction(transactionContext -> {
            StructureMatchCollector collector = transactionContext.getCollector();
            boolean recorded = collector.recordAbility(this, (IMultiblockPart) abilityPart);
            if (!recorded) {
                transactionContext.setError(new CountLimitError(CountLimitError.Kind.MAX_GLOBAL, maxCount));
            }
            return recorded;
        });
    }

    @Override
    public BlockInfo[] getCandidates() {
        List<MetaTileEntity> tiles = MultiblockAbility.REGISTRY.getOrDefault(ability, java.util.Collections.emptyList());
        if (tiles == null || tiles.isEmpty()) {
            return new BlockInfo[0];
        }
        return tiles.stream()
                .filter(Objects::nonNull)
                .map(HatchElement::candidateInfo)
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
    public void collectRequirements(StructureEvaluationContext<Object> context) {
        context.getCollector().declareAbility(
                this, ability, minCount, maxCount,
                () -> new CountLimitError(CountLimitError.Kind.MIN_GLOBAL, minCount),
                () -> new CountLimitError(CountLimitError.Kind.MAX_GLOBAL, maxCount));
    }

    @NotNull
    private StructureElementPreview buildPreview() {
        StructureElementPreview.Builder builder = StructureElementPreview.builder();
        StructureElementPreview.CandidateGroup.Builder groupBuilder =
                StructureElementPreview.CandidateGroup.builder(this::getCandidates);
        if (hasCountConstraint()) {
            groupBuilder.global(minCount, maxCount);
        }
        if (previewCount >= 0) {
            groupBuilder.previewCount(previewCount);
        }
        if (defaultCandidate != null) {
            groupBuilder.defaultCandidate(defaultCandidate);
        }
        StructureElementPreview.CandidateGroup group = groupBuilder.build();
        if (hasCountConstraint()) {
            builder.limited(group);
        } else {
            builder.common(group);
        }
        return builder.build();
    }

    private boolean hasCountConstraint() {
        return minCount > 0 || maxCount >= 0;
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

    private static BlockInfo candidateInfo(MetaTileEntity tile) {
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        holder.setMetaTileEntity(tile);
        holder.getMetaTileEntity().onPlacement();
        holder.getMetaTileEntity().setFrontFacing(EnumFacing.SOUTH);
        return new BlockInfo(tile.getBlock().getDefaultState(), holder);
    }
}
