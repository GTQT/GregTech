package gregtech.api.pattern.element.impl;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
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
    private final int previewCount;
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
        this.previewCount = previewCount;
        this.preview = buildPreview();
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
                    transactionContext.setError(new CountLimitError(CountLimitError.Kind.MAX_GLOBAL, maxCount));
                    return false;
                }
                return true;
            }
            if (hasCountConstraint() && !collector.recordCount(this)) {
                transactionContext.setError(new CountLimitError(CountLimitError.Kind.MAX_GLOBAL, maxCount));
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
                    () -> new CountLimitError(CountLimitError.Kind.MIN_GLOBAL, minCount),
                    () -> new CountLimitError(CountLimitError.Kind.MAX_GLOBAL, maxCount));
        } else {
            context.getCollector().declareAbility(
                    this, ability, minCount, maxCount,
                    () -> new CountLimitError(CountLimitError.Kind.MIN_GLOBAL, minCount),
                    () -> new CountLimitError(CountLimitError.Kind.MAX_GLOBAL, maxCount));
        }
    }

    private boolean matches(MetaTileEntity metaTileEntity) {
        return metaTileEntity != null && ArrayUtils.contains(ids, metaTileEntity.metaTileEntityId);
    }

    private boolean hasCountConstraint() {
        return minCount > 0 || maxCount >= 0;
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
        StructureElementPreview.CandidateGroup group = groupBuilder.build();
        if (hasCountConstraint()) {
            builder.limited(group);
        } else {
            builder.common(group);
        }
        return builder.build();
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
