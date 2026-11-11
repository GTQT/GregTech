package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Public, server-side entry point for a build dry run.
 *
 * <p>The planner walks exactly the same tooling pieces, channel selection and ability quota logic as
 * {@link StructureBuildOperationService}, but uses {@link StructureEvaluationContext.Operation#DRY_RUN}. That operation
 * records required/missing items and the number of cells a real build would place, while never touching the world or the
 * player inventory. Clients can therefore show a material bill that is guaranteed to match the real build.
 */
public final class StructureBuildPlanner {

    private StructureBuildPlanner() {}

    /**
     * Evaluates a bill with default cell overrides and ME network stock enabled.
     *
     * @see #evaluate(EntityPlayer, MultiblockControllerBase, StructureOrientation, Map, Map, boolean)
     */
    @NotNull
    public static StructureBuildResult evaluate(@NotNull EntityPlayer player,
                                                @NotNull MultiblockControllerBase controller,
                                                @NotNull StructureOrientation orientation,
                                                @Nullable Map<String, Integer> channelValues) {
        return evaluate(player, controller, orientation, channelValues, Collections.emptyMap());
    }

    /**
     * Evaluates a bill with explicit cell overrides and ME network stock enabled.
     *
     * @see #evaluate(EntityPlayer, MultiblockControllerBase, StructureOrientation, Map, Map, boolean)
     */
    @NotNull
    public static StructureBuildResult evaluate(@NotNull EntityPlayer player,
                                                @NotNull MultiblockControllerBase controller,
                                                @NotNull StructureOrientation orientation,
                                                @Nullable Map<String, Integer> channelValues,
                                                @Nullable Map<BlockPos, ItemStack> cellOverrides) {
        return evaluate(player, controller, orientation, channelValues, cellOverrides, true);
    }

    /**
     * Computes the authoritative material bill for the supplied configuration.
     *
     * @param player          the player the real build would use for stock checks
     * @param controller      the controller about to build
     * @param orientation     orientation the real build would use
     * @param channelValues   channel selection shared with the preview and the build packet
     * @param cellOverrides   per-cell candidate overrides, controller-relative
     * @param useNetworkStock whether external item sources (e.g. an ME network) may be consulted
     * @return required/missing items and the number of cells a real build would place; never mutates the world
     */
    @NotNull
    public static StructureBuildResult evaluate(@NotNull EntityPlayer player,
                                                @NotNull MultiblockControllerBase controller,
                                                @NotNull StructureOrientation orientation,
                                                @Nullable Map<String, Integer> channelValues,
                                                @Nullable Map<BlockPos, ItemStack> cellOverrides,
                                                boolean useNetworkStock) {
        StructureRuntime runtime = controller.getOrCreateStructureRuntime();
        MultiPiecePattern pattern = runtime.getMultiPiecePattern();
        PieceRuntimes runtimes = runtime.getPieceRuntimes();
        if (pattern == null || runtimes == null) {
            return StructureBuildResult.empty();
        }
        AbilityPlacementTracker abilityTracker = pattern.createAbilityPlacementTracker(channelValues);
        StructureBuildResult.Builder result = StructureBuildResult.builder();
        int pieceCount = pattern.getToolingPieceCount();
        for (int pieceIndex = 1; pieceIndex <= pieceCount; pieceIndex++) {
            if (StructureExclusions.isExcluded(channelValues, pieceIndex)) {
                continue;
            }
            int compiledPieceIndex = pattern.resolveToolingPieceIndex(pieceIndex);
            if (compiledPieceIndex < 1) {
                result.merge(StructureBuildResult.builder().recordInvalidPieceRequest().build());
                continue;
            }
            result.merge(pattern.autoBuildPieceWithResult(
                    compiledPieceIndex, player, controller, orientation, channelValues,
                    runtimes, abilityTracker, StructureEvaluationContext.Operation.DRY_RUN,
                    ItemStack.EMPTY, cellOverrides, useNetworkStock));
        }
        return result.build();
    }
}
