package gregtech.api.pattern.element.impl;

import gregtech.api.block.VariantActiveBlock;
import gregtech.api.pattern.CountLimitError;
import gregtech.api.pattern.PatternStringError;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureMatchCollector;
import gregtech.api.pattern.casing.ICasing;
import gregtech.api.pattern.casing.ICasingGroup;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Direct element for a declarative tiered casing group.
 */
public final class TieredCasingElement implements ITypedStructureElement<Object> {

    private static final String TIER_MISMATCH_ERROR =
            "gregtech.multiblock.pattern.error.casing_tier_mismatch";

    private final Map<IBlockState, ICasing> casings = new LinkedHashMap<>();
    private final String channelName;
    private final boolean requiresUniformTier;
    private final int minGlobalCount;
    private final int maxGlobalCount;
    private final StructureElementPreview preview;

    public TieredCasingElement(@NotNull ICasingGroup group, @NotNull String channelName) {
        this(group, channelName, 0, -1);
    }

    public TieredCasingElement(@NotNull ICasingGroup group, @NotNull String channelName,
                               int minGlobalCount, int maxGlobalCount) {
        for (ICasing casing : group.getCasings()) {
            casings.put(casing.getBlockState(), casing);
        }
        this.channelName = channelName;
        this.requiresUniformTier = group.requiresUniformTier();
        this.minGlobalCount = Math.max(0, minGlobalCount);
        this.maxGlobalCount = maxGlobalCount;
        this.preview = StructureElementPreview.builder()
                .limited(StructureElementPreview.CandidateGroup.builder(this::getCandidates)
                        .global(this.minGlobalCount, this.maxGlobalCount)
                        .channel(channelName)
                        .build())
                .build();
    }

    @Override
    public Set<StructureElementCapability> getCapabilities() {
        return StructureElementCapability.snapshotSafe();
    }

    @Override
    public boolean check(@NotNull StructureEvaluationContext<Object> context) {
        ICasing matched = casings.get(context.getBlockState());
        if (matched == null) {
            return false;
        }

        return context.transaction(transactionContext -> {
            StructureMatchCollector collector = transactionContext.getCollector();
            if (transactionContext.getBlockState().getBlock() instanceof VariantActiveBlock) {
                collector.recordVariantActiveBlock(transactionContext.getPos());
            }
            if (!collector.recordChannelValue(channelName, matched, requiresUniformTier)) {
                transactionContext.setError(new PatternStringError(TIER_MISMATCH_ERROR));
                return false;
            }
            if (matched.isTiered()) {
                collector.setValue(channelName + ".tier", matched.getTier());
            }
            if (!collector.recordCount(this)) {
                transactionContext.setError(new CountLimitError(CountLimitError.Kind.MAX_GLOBAL, maxGlobalCount));
                return false;
            }
            return true;
        });
    }

    @Override
    public BlockInfo[] getCandidates() {
        return casings.keySet().stream()
                .map(state -> new BlockInfo(state, null))
                .toArray(BlockInfo[]::new);
    }

    @Override
    public StructureElementPreview getPreview() {
        return preview;
    }

    @Override
    public boolean placeBlock(@NotNull StructureEvaluationContext<Object> context,
                              @NotNull EntityPlayer player, boolean skipHatches) {
        World world = context.getWorld();
        if (world == null) {
            return false;
        }
        BlockInfo[] candidates = getCandidates();
        if (candidates.length == 0) {
            return false;
        }
        world.setBlockState(context.getPos(), candidates[0].getBlockState());
        return true;
    }

    @Override
    public int getMinGlobalCount() {
        return minGlobalCount;
    }

    @Override
    public int getMaxGlobalCount() {
        return maxGlobalCount;
    }

    @Override
    public void collectRequirements(@NotNull StructureEvaluationContext<Object> context) {
        context.getCollector().declareCount(
                this, minGlobalCount, maxGlobalCount,
                () -> new CountLimitError(CountLimitError.Kind.MIN_GLOBAL, minGlobalCount),
                () -> new CountLimitError(CountLimitError.Kind.MAX_GLOBAL, maxGlobalCount));
    }
}
