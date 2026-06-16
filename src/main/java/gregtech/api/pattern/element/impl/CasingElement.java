package gregtech.api.pattern.element.impl;

import gregtech.api.block.VariantActiveBlock;
import gregtech.api.pattern.BlockWorldState;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureMatchCollector;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.casing.ICasing;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.Set;

/**
 * Direct element for one declarative casing state.
 */
public final class CasingElement implements ITypedStructureElement<Object> {

    private final IBlockState blockState;
    private final int minGlobalCount;
    private final TraceabilityPredicate legacyPredicate;
    private final TraceabilityPredicate.SimplePredicate countPredicate;
    private final StructureElementPreview preview;

    public CasingElement(@NotNull ICasing casing, int minGlobalCount) {
        this.blockState = casing.getBlockState();
        this.minGlobalCount = Math.max(0, minGlobalCount);
        this.legacyPredicate = new TraceabilityPredicate(
                this::testLegacy,
                this::getCandidates)
                .setMinGlobalLimited(this.minGlobalCount);
        this.countPredicate = legacyPredicate.limited.get(0);
        this.preview = StructureElementPreview.builder()
                .limited(this::getCandidates, this.minGlobalCount, -1, -1, -1, -1)
                .build();
    }

    @Override
    public Set<StructureElementCapability> getCapabilities() {
        return StructureElementCapability.snapshotSafe();
    }

    @Override
    public boolean check(@NotNull StructureEvaluationContext<Object> context) {
        if (!blockState.equals(context.getBlockState())) {
            return false;
        }

        return context.transaction(transactionContext -> {
            StructureMatchCollector collector = transactionContext.getCollector();
            if (blockState.getBlock() instanceof VariantActiveBlock) {
                collector.recordVariantActiveBlock(transactionContext.getPos());
            }
            return collector.recordCount(this);
        });
    }

    @Override
    public BlockInfo[] getCandidates() {
        return new BlockInfo[]{new BlockInfo(blockState, null)};
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
        world.setBlockState(context.getPos(), blockState);
        return true;
    }

    @Override
    public int getMinGlobalCount() {
        return minGlobalCount;
    }

    @Override
    public void collectRequirements(@NotNull StructureEvaluationContext<Object> context) {
        context.getCollector().declareCount(
                this, minGlobalCount, -1,
                () -> new TraceabilityPredicate.SinglePredicateError(countPredicate, 1),
                null);
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return legacyPredicate;
    }

    private boolean testLegacy(@NotNull BlockWorldState worldState) {
        if (!blockState.equals(worldState.getBlockState())) {
            return false;
        }
        if (blockState.getBlock() instanceof VariantActiveBlock) {
            worldState.getMatchContext().getOrPut("VABlock", new LinkedList<BlockPos>())
                    .add(worldState.getPos());
        }
        return true;
    }
}
