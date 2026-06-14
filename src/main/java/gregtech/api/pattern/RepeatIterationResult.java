package gregtech.api.pattern;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of resolving repeat counts into concrete slice traversals.
 */
public final class RepeatIterationResult {

    public enum Outcome {
        COMPLETED,
        STOPPED,
        EMPTY
    }

    @NotNull
    private final Outcome outcome;
    @NotNull
    private final int[] repetitions;
    @NotNull
    private final List<StructureCellTraversal> traversals;

    private RepeatIterationResult(@NotNull Outcome outcome,
                                  @NotNull int[] repetitions,
                                  @NotNull List<StructureCellTraversal> traversals) {
        this.outcome = outcome;
        this.repetitions = repetitions.clone();
        this.traversals = Collections.unmodifiableList(new ArrayList<>(traversals));
    }

    @NotNull
    public static RepeatIterationResult completed(@NotNull int[] repetitions,
                                                  @NotNull List<StructureCellTraversal> traversals) {
        return new RepeatIterationResult(
                traversals.isEmpty() ? Outcome.EMPTY : Outcome.COMPLETED,
                repetitions, traversals);
    }

    @NotNull
    public static RepeatIterationResult stopped(@NotNull int[] repetitions,
                                                @NotNull List<StructureCellTraversal> traversals) {
        return new RepeatIterationResult(Outcome.STOPPED, repetitions, traversals);
    }

    @NotNull
    public Outcome getOutcome() {
        return outcome;
    }

    public boolean completed() {
        return outcome == Outcome.COMPLETED || outcome == Outcome.EMPTY;
    }

    @NotNull
    public int[] getRepetitions() {
        return repetitions.clone();
    }

    @NotNull
    public List<StructureCellTraversal> getTraversals() {
        return traversals;
    }

    public int getVisitedSlices() {
        return traversals.size();
    }

    @NotNull
    public List<BlockPos> getLocalOffsets() {
        List<BlockPos> offsets = new ArrayList<>(traversals.size());
        for (StructureCellTraversal traversal : traversals) {
            offsets.add(new BlockPos(
                    traversal.getXOffset(), traversal.getYOffset(), traversal.getZOffset()));
        }
        return Collections.unmodifiableList(offsets);
    }
}
