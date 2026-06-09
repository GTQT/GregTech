package gregtech.api.pattern.element;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.PieceRuntimes;
import gregtech.api.pattern.PieceRuntime;
import gregtech.api.pattern.RepeatGroupPiece;
import gregtech.api.pattern.StructurePiece;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-check mutable state for structure checking.
 * Created via {@link StructureDefinition#createState()}.
 * Each check operation should use its own state instance.
 *
 * <p>The {@link #check} method returns a {@link Result} containing:
 * <ul>
 *   <li>Success/failure status</li>
 *   <li>{@link FormedStructureMetadata} on success (actual repeat counts + channel values)</li>
 *   <li>Aggregated {@link PatternMatchContext} on success (contains "MultiblockParts")</li>
 *   <li>Error position and message on failure</li>
 * </ul>
 */
public final class StructureCheckState {

    private final StructureDefinition definition;

    @Nullable
    private BlockPos lastErrorPos;

    @Nullable
    private String lastErrorMessage;

    StructureCheckState(@NotNull StructureDefinition definition) {
        this.definition = definition;
    }

    /**
     * Check result containing success/failure status and metadata.
     */
    public static final class Result {

        public final boolean success;

        @Nullable
        public final FormedStructureMetadata metadata;

        /** Aggregated PatternMatchContext from all pieces (contains "MultiblockParts"). */
        @Nullable
        public final PatternMatchContext context;

        @Nullable
        public final BlockPos errorPos;

        @Nullable
        public final String errorMessage;

        private Result(boolean success, @Nullable FormedStructureMetadata metadata,
                       @Nullable PatternMatchContext context,
                       @Nullable BlockPos errorPos, @Nullable String errorMessage) {
            this.success = success;
            this.metadata = metadata;
            this.context = context;
            this.errorPos = errorPos;
            this.errorMessage = errorMessage;
        }

        /** Create a success result with formed metadata and aggregated context */
        @NotNull
        public static Result success(@NotNull FormedStructureMetadata metadata,
                                     @NotNull PatternMatchContext context) {
            return new Result(true, metadata, context, null, null);
        }

        /** Create a failure result with error info */
        @NotNull
        public static Result failure(@NotNull BlockPos pos, @NotNull String msg) {
            return new Result(false, null, null, pos, msg);
        }

        /** Create a failure result without specific position */
        @NotNull
        public static Result failure(@NotNull String msg) {
            return new Result(false, null, null, null, msg);
        }
    }

    /**
     * Perform a synchronous structure check.
     *
     * @param world         the world
     * @param controllerPos the controller position
     * @param front         the front facing (into-structure direction)
     * @param up            the upward facing
     * @param flipped       whether the structure is flipped
     * @param context       optional pattern match context (null = create new)
     * @return the check result
     */
    @NotNull
    public Result check(@NotNull World world, @NotNull BlockPos controllerPos,
                        @NotNull EnumFacing front, @NotNull EnumFacing up, boolean flipped,
                        @Nullable PatternMatchContext context) {
        MultiPiecePattern pattern = definition.getCompiledPattern();

        // Per-check transient runtimes: StructureCheckState.check is called from the
        // main thread (e.g. JEI preview render, structure shape computation) and
        // must not mutate the controller's owned PieceRuntimes, which may be
        // concurrently read by the async structure checker.
        PieceRuntimes transientRuntimes = new PieceRuntimes(pattern);

        // Collect piece repeat counts and channel values
        Map<String, int[]> pieceRepeats = new HashMap<>();
        Map<String, Integer> channelValues = new HashMap<>();

        // Aggregated context from all pieces
        PatternMatchContext aggregated = new PatternMatchContext();
        Set<IMultiblockPart> allParts = aggregated.getOrCreate("MultiblockParts", HashSet::new);

        for (StructurePiece piece : pattern.getPieceList()) {
            if (piece.isConditional() && !piece.isActive()) continue;

            // Per-piece runtime for this check. Always non-null since we just built
            // the runtimes from the same pattern above.
            PieceRuntime runtime = transientRuntimes.get(piece);

            // Build a FormedStructureMetadata snapshot of all previously-checked
            // pieces, so a DynamicOffsetPiece can read the repeat count of its
            // anchor at the time this piece's center position is computed. This
            // is rebuilt every iteration because FormedStructureMetadata is
            // immutable.
            FormedStructureMetadata prior = FormedStructureMetadata.fromCheckResult(
                    new HashMap<>(pieceRepeats), new HashMap<>(channelValues));

            if (piece instanceof RepeatGroupPiece repeatPiece) {
                // Repeatable piece: use synchronous World-based check
                // (uses checkPatternFastAt for cache-accelerated checks)
                boolean ok = repeatPiece.checkSync(
                        world, controllerPos, front, up, flipped, prior, runtime);
                if (!ok) {
                    lastErrorPos = controllerPos;
                    lastErrorMessage = "Repeatable piece '" + piece.getName() + "' failed pattern check";
                    return Result.failure(controllerPos, lastErrorMessage);
                }

                // Extract repeat counts from the runtime's cached reps
                int[] formedReps = runtime.getLastFormedReps();
                if (formedReps != null && formedReps.length > 0) {
                    pieceRepeats.put(piece.getName(), formedReps.clone());
                }

                // Merge aggregated context from the runtime
                PatternMatchContext repeatContext = runtime.getLastAggregatedContext();
                if (repeatContext != null) {
                    Set<IMultiblockPart> repeatParts = repeatContext.getOrCreate("MultiblockParts", HashSet::new);
                    allParts.addAll(repeatParts);
                    extractChannelValues(repeatContext, channelValues);
                }
            } else {
                // Fixed piece: standard single-template check
                // Use the 4-arg getCenterPos so dynamic-anchor pieces can compute
                // their position from the prior pieces' repeat counts.
                BlockPos centerPos = piece.getCenterPos(controllerPos, front, up, prior);
                PatternMatchContext pieceContext = runtime.getState()
                        .checkPatternFastAt(world, centerPos, front, up, flipped);

                if (pieceContext == null) {
                    lastErrorPos = centerPos;
                    lastErrorMessage = "Piece '" + piece.getName() + "' failed pattern check";
                    return Result.failure(centerPos, lastErrorMessage);
                }

                // Extract repeat counts from the piece's MultiblockState
                int[] formedReps = runtime.getState().formedRepetitionCount;
                if (formedReps != null && formedReps.length > 0) {
                    pieceRepeats.put(piece.getName(), formedReps.clone());
                }

                // Merge MultiblockParts from this piece into the aggregated context
                Set<IMultiblockPart> pieceParts = pieceContext.getOrCreate("MultiblockParts", HashSet::new);
                allParts.addAll(pieceParts);

                // Extract channel values from the match context
                extractChannelValues(pieceContext, channelValues);
            }
        }

        FormedStructureMetadata metadata = FormedStructureMetadata.fromCheckResult(
                pieceRepeats, channelValues);
        return Result.success(metadata, aggregated);
    }

    /**
     * Extract channel values from a pattern match context.
     * Channel values are stored as Integer entries in the context by the predicate system.
     */
    private void extractChannelValues(@NotNull PatternMatchContext context,
                                      @NotNull Map<String, Integer> channelValues) {
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            if (entry.getValue() instanceof Integer) {
                channelValues.putIfAbsent(entry.getKey(), (Integer) entry.getValue());
            }
        }
    }

    @Nullable
    public BlockPos getLastErrorPos() {
        return lastErrorPos;
    }

    @Nullable
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }
}
