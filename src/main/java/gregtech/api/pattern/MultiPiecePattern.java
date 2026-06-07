package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.FormedStructureMetadata;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * A composite multi-piece pattern for super-large multiblock structures.
 * Instead of a single monolithic pattern, the structure is divided into named pieces,
 * each with its own template and offset. The pattern itself is pure shared data;
 * per-controller state lives in {@link PieceRuntimes}.
 *
 * <p>When a block changes, only the piece(s) containing that position are marked dirty
 * and re-validated, rather than re-checking the entire structure. The dirty/validated
 * flags and the formed-position set live on the per-controller {@link PieceRuntime}
 * (indexed by piece identity), so the compiled pattern itself is stateless and
 * safe to share across controllers of the same multiblock type.
 *
 * <p>Most check / auto-build / reset entry points take a {@link PieceRuntimes}
 * parameter to resolve the per-controller state for each piece. Callers that
 * don't have a runtime in hand (e.g. read-only queries) can pass a transient
 * "scratch" runtime built from a temporary piece list, but the normal
 * controller-owned runtime is the source of truth.
 *
 * <p>Usage example:
 * <pre>{@code
 * MultiPiecePattern pattern = MultiPiecePattern.builder()
 *     .piece("core", coreTemplate, Vec3i.ZERO)
 *     .piece("ring1", ring1Template, new Vec3i(0, 0, -59))
 *     .conditionalPiece("ring2", ring2Template, new Vec3i(0, 0, -67), () -> isUpgradeActive())
 *     .build();
 *
 * PieceRuntimes runtimes = new PieceRuntimes(pattern);
 * pattern.checkDirtyPieces(world, controllerPos, front, up, flipped, runtimes);
 * }</pre>
 *
 * @see StructurePiece for individual piece definition
 * @see PieceRuntimes for the per-controller state holder
 */
public class MultiPiecePattern {

    private final Map<String, StructurePiece> pieces;
    private final List<StructurePiece> pieceList;

    private MultiPiecePattern(Map<String, StructurePiece> pieces) {
        this.pieces = Collections.unmodifiableMap(pieces);
        this.pieceList = Collections.unmodifiableList(new ArrayList<>(pieces.values()));
    }

    /**
     * Create a MultiPiecePattern from a list of pre-built pieces.
     * Used by StructureCompiler to assemble compiled pieces.
     *
     * @param pieceList the list of pieces (must not be empty, names must be unique)
     * @throws IllegalArgumentException if duplicate piece names are found
     */
    public MultiPiecePattern(@NotNull List<StructurePiece> pieceList) {
        Map<String, StructurePiece> map = new LinkedHashMap<>();
        for (StructurePiece piece : pieceList) {
            if (map.containsKey(piece.getName())) {
                throw new IllegalArgumentException("Duplicate piece name: " + piece.getName());
            }
            map.put(piece.getName(), piece);
        }
        this.pieces = Collections.unmodifiableMap(map);
        this.pieceList = Collections.unmodifiableList(new ArrayList<>(pieceList));
    }

    /**
     * @return an unmodifiable map of piece name -> piece
     */
    public Map<String, StructurePiece> getPieces() {
        return pieces;
    }

    /**
     * @return an unmodifiable list of all pieces in insertion order
     */
    public List<StructurePiece> getPieceList() {
        return pieceList;
    }

    /**
     * @param name the piece name
     * @return the piece, or null if not found
     */
    @Nullable
    public StructurePiece getPiece(String name) {
        return pieces.get(name);
    }

    /**
     * Get the combined set of all block positions across all active, validated pieces.
     *
     * @param runtimes per-controller state for each piece; positions are read from
     *                 the per-piece {@link PieceRuntime#getPositions()}
     * @return a new LongSet containing all positions
     */
    public LongSet getAllPositions(@NotNull PieceRuntimes runtimes) {
        LongSet all = new LongOpenHashSet();
        for (StructurePiece piece : pieceList) {
            if (!piece.isActive()) continue;
            PieceRuntime runtime = runtimes.get(piece);
            if (runtime == null) continue;
            if (runtime.isValidated()) {
                all.addAll(runtime.getPositions());
            }
        }
        return all;
    }

    /**
     * Check if any piece is dirty and needs re-validation.
     *
     * @param runtimes per-controller state for each piece
     * @return true if at least one active piece is dirty
     */
    public boolean hasDirtyPieces(@NotNull PieceRuntimes runtimes) {
        for (StructurePiece piece : pieceList) {
            if (!piece.isActive()) continue;
            PieceRuntime runtime = runtimes.get(piece);
            if (runtime != null && runtime.isDirty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the list of dirty pieces that need re-checking.
     *
     * @param runtimes per-controller state for each piece
     * @return list of dirty, active pieces
     */
    @NotNull
    public List<StructurePiece> getDirtyPieces(@NotNull PieceRuntimes runtimes) {
        List<StructurePiece> dirty = new ArrayList<>();
        for (StructurePiece piece : pieceList) {
            if (!piece.isActive()) continue;
            PieceRuntime runtime = runtimes.get(piece);
            if (runtime != null && runtime.isDirty()) {
                dirty.add(piece);
            }
        }
        return dirty;
    }

    /**
     * Check all dirty pieces against the world.
     * Only re-validates pieces that are marked dirty.
     * Returns true if ALL active pieces are validated after checking.
     *
     * @param world          the world to check against
     * @param controllerPos  the controller's position
     * @param frontFacing    the controller's front facing (opposite of the facing used for pattern)
     * @param upwardsFacing  the upwards facing
     * @param allowsFlip     whether flipping is allowed
     * @param runtimes       per-controller state for each piece
     * @return true if all active pieces are valid
     */
    public boolean checkDirtyPieces(World world, BlockPos controllerPos, EnumFacing frontFacing,
                                     EnumFacing upwardsFacing, boolean allowsFlip,
                                     @NotNull PieceRuntimes runtimes) {
        // Accumulates per-piece repeat counts as we sweep through the piece list,
        // so a DynamicOffsetPiece can read the runtime repeat count of its
        // anchor when computing its center position. This is the dirty-check
        // equivalent of the prior metadata built up in
        // gregtech.api.pattern.element.StructureCheckState.check.
        Map<String, int[]> priorRepeats = new HashMap<>();

        for (StructurePiece piece : pieceList) {
            if (!piece.isActive()) continue;

            PieceRuntime runtime = runtimes.get(piece);
            if (runtime == null) continue;

            FormedStructureMetadata prior = FormedStructureMetadata.fromCheckResult(
                    new HashMap<>(priorRepeats), Collections.emptyMap());

            if (runtime.isDirty()) {
                BlockPos pieceCenter = piece.getCenterPos(controllerPos, frontFacing, upwardsFacing, prior);
                PatternMatchContext result = runtime.getState().checkPatternFastAt(
                        world, pieceCenter, frontFacing, upwardsFacing, allowsFlip);

                if (result != null) {
                    runtime.setValidated(true);
                    // Atomically swap the piece's position set from the state cache
                    LongSet newPositions = new LongOpenHashSet(runtime.getState().cache.keySet());
                    runtime.swapPositions(newPositions);
                } else {
                    runtime.setValidated(false);
                }
                runtime.clearDirty();
            }

            if (!runtime.isValidated()) {
                return false;
            }

            // Add this piece's repeat counts to the prior metadata so subsequent
            // pieces (which may be DynamicOffsetPieces) can read them.
            if (piece instanceof RepeatGroupPiece) {
                int[] reps = runtime.getLastFormedReps();
                if (reps != null && reps.length > 0) {
                    priorRepeats.put(piece.getName(), reps.clone());
                }
            } else {
                int[] reps = runtime.getState().formedRepetitionCount;
                if (reps != null && reps.length > 0) {
                    priorRepeats.put(piece.getName(), reps.clone());
                }
            }
        }
        return true;
    }

    /**
     * Perform a full check of all active pieces (ignoring dirty flags).
     *
     * @param world          the world to check against
     * @param controllerPos  the controller's position
     * @param frontFacing    the controller's front facing
     * @param upwardsFacing  the upwards facing
     * @param allowsFlip     whether flipping is allowed
     * @param runtimes       per-controller state for each piece
     * @return true if all active pieces are valid
     */
    public boolean checkAllPieces(World world, BlockPos controllerPos, EnumFacing frontFacing,
                                   EnumFacing upwardsFacing, boolean allowsFlip,
                                   @NotNull PieceRuntimes runtimes) {
        for (StructurePiece piece : pieceList) {
            PieceRuntime runtime = runtimes.get(piece);
            if (runtime != null) {
                runtime.markDirty();
            }
        }
        return checkDirtyPieces(world, controllerPos, frontFacing, upwardsFacing, allowsFlip, runtimes);
    }

    /**
     * Mark a specific piece as dirty by position.
     * Called when a block change is detected within the piece's cached positions.
     *
     * @param posLong  the changed block position as a long
     * @param runtimes per-controller state for each piece
     * @return true if a piece was found and marked dirty
     */
    public boolean markDirtyByPosition(long posLong, @NotNull PieceRuntimes runtimes) {
        boolean found = false;
        for (StructurePiece piece : pieceList) {
            if (!piece.isActive()) continue;
            PieceRuntime runtime = runtimes.get(piece);
            if (runtime != null && runtime.isValidated() && runtime.getPositions().contains(posLong)) {
                runtime.markDirty();
                found = true;
            }
        }
        return found;
    }

    /**
     * Auto-build a specific piece by its 1-based index.
     * Computes the piece's center position from the controller and delegates to the
     * piece's runtime state. For {@link RepeatGroupPiece}, the per-piece runtime
     * is forwarded to its multi-axis auto-build path.
     *
     * <p>For pieces anchored to a repeatable body (i.e. {@link DynamicOffsetPiece}),
     * the anchor's repeat count is read from the per-piece runtime's
     * {@link PieceRuntime#getLastFormedReps()} so the dynamic offset can resolve
     * to the correct world position. The body must be built (i.e. its runtime
     * cached the repeat counts) before the anchored piece's auto-build is
     * invoked, otherwise the piece will fall back to its static baseOffset.
     *
     * @param pieceIndex     1-based index into the piece list
     * @param player         the player performing the build
     * @param controller     the multiblock controller
     * @param channelValues  channel values for tier selection
     * @param skipHatches    if true, skip hatch placement
     * @param runtimes       per-controller state for each piece
     * @return true if the piece was successfully built (index valid and piece exists)
     */
    public boolean autoBuildPiece(int pieceIndex, EntityPlayer player, MultiblockControllerBase controller,
                                   @Nullable Map<String, Integer> channelValues, boolean skipHatches,
                                   @NotNull PieceRuntimes runtimes) {
        if (pieceIndex < 1 || pieceIndex > pieceList.size()) return false;

        StructurePiece piece = pieceList.get(pieceIndex - 1);
        PieceRuntime runtime = runtimes.get(piece);
        if (runtime == null) return false;

        // Build prior metadata from preceding pieces' runtime state. The
        // check path accumulates this incrementally as each piece is checked;
        // the auto-build path is per-piece, so we rebuild it on demand from
        // whatever the previous pieces' runtimes have cached via
        // PieceRuntime.getLastFormedReps().
        FormedStructureMetadata prior = buildPriorMetadata(pieceIndex, runtimes);
        if (piece instanceof RepeatGroupPiece repeatPiece) {
            repeatPiece.autoBuildAtRepeated(player, controller, controller.getPos(),
                    controller.getFrontFacingForStructure(), controller.getUpwardsFacing(),
                    controller.isFlipped(), channelValues, skipHatches, runtime);
        } else {
            // Use the 4-arg getCenterPos so a DynamicOffsetPiece receives the
            // prior metadata and can compute its dynamic position. Non-anchor
            // pieces ignore the prior and behave identically to the 3-arg form.
            BlockPos pieceCenter = piece.getCenterPos(
                    controller.getPos(), controller.getFrontFacingForStructure(),
                    controller.getUpwardsFacing(), prior);
            System.out.println("[Build] piece=" + piece.getName() + " index=" + pieceIndex
                    + " pieceCenter=" + pieceCenter
                    + " priorNull=" + (prior == null));
            runtime.getState().autoBuildAt(player, controller, pieceCenter, channelValues, skipHatches);
        }
        return true;
    }

    /**
     * Build a {@link FormedStructureMetadata} snapshot from the per-piece runtimes
     * of all pieces preceding {@code upToIndex} (1-based, exclusive). Repeat
     * counts are read from each runtime's {@code lastFormedReps} field, which is
     * populated by the check path ({@code checkDirtyPieces}) and by
     * {@code RepeatGroupPiece.autoBuildAtRepeated}.
     *
     * <p>This is the auto-build equivalent of the incremental
     * {@code priorRepeats} accumulation done in
     * {@link #checkDirtyPieces(World, BlockPos, EnumFacing, EnumFacing, boolean, PieceRuntimes)}.
     */
    @NotNull
    private FormedStructureMetadata buildPriorMetadata(int upToIndex, @NotNull PieceRuntimes runtimes) {
        Map<String, int[]> priorRepeats = new HashMap<>();
        for (int i = 0; i < upToIndex - 1; i++) {
            StructurePiece p = pieceList.get(i);
            PieceRuntime r = runtimes.get(p);
            if (r == null) continue;
            int[] reps = r.getLastFormedReps();
            if (reps != null && reps.length > 0) {
                priorRepeats.put(p.getName(), reps.clone());
            }
        }
        return FormedStructureMetadata.fromCheckResult(priorRepeats, Collections.emptyMap());
    }

    /**
     * @return the number of pieces in this pattern
     */
    public int getPieceCount() {
        return pieceList.size();
    }

    /**
     * Get the primary (first) piece of this pattern.
     * Useful for single-piece patterns where the first piece is the main structure.
     *
     * @return the first piece in the piece list
     * @throws IllegalStateException if the pattern has no pieces
     */
    @NotNull
    public StructurePiece getPrimaryPiece() {
        if (pieceList.isEmpty()) {
            throw new IllegalStateException("MultiPiecePattern has no pieces");
        }
        return pieceList.get(0);
    }

    /**
     * Reset every piece's runtime state via the per-controller {@link PieceRuntimes}.
     * The pattern itself is unaffected — it carries no per-instance state to reset.
     */
    public void resetAll(@NotNull PieceRuntimes runtimes) {
        runtimes.reset();
    }

    /**
     * @return a new builder for constructing a MultiPiecePattern
     */
    public static Builder builder() {
        return new Builder();
    }

    // --- Builder ---

    public static class Builder {

        private final Map<String, StructurePiece> pieces = new LinkedHashMap<>();

        private Builder() {}

        /**
         * Add an unconditional piece with default RELATIVE offset mode.
         *
         * @param name     unique name for this piece
         * @param template the pattern template
         * @param offset   offset from the controller position
         * @return this builder
         */
        public Builder piece(@NotNull String name, @NotNull BlockPatternTemplate template, @NotNull Vec3i offset) {
            return piece(name, template, offset, OffsetMode.RELATIVE);
        }

        /**
         * Add an unconditional piece with explicit offset mode.
         *
         * @param name       unique name for this piece
         * @param template   the pattern template
         * @param offset     offset from the controller position
         * @param offsetMode how the offset is interpreted relative to controller facing
         * @return this builder
         */
        public Builder piece(@NotNull String name, @NotNull BlockPatternTemplate template,
                             @NotNull Vec3i offset, @NotNull OffsetMode offsetMode) {
            if (pieces.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate piece name: " + name);
            }
            pieces.put(name, new StructurePiece(name, template, offset, offsetMode));
            return this;
        }

        /**
         * Add a conditional piece with default RELATIVE offset mode.
         *
         * @param name      unique name for this piece
         * @param template  the pattern template
         * @param offset    offset from the controller position
         * @param condition condition supplier; piece is only active when this returns true
         * @return this builder
         */
        public Builder conditionalPiece(@NotNull String name, @NotNull BlockPatternTemplate template,
                                        @NotNull Vec3i offset, @NotNull BooleanSupplier condition) {
            return conditionalPiece(name, template, offset, OffsetMode.RELATIVE, condition);
        }

        /**
         * Add a conditional piece with explicit offset mode.
         *
         * @param name       unique name for this piece
         * @param template   the pattern template
         * @param offset     offset from the controller position
         * @param offsetMode how the offset is interpreted relative to controller facing
         * @param condition  condition supplier; piece is only active when this returns true
         * @return this builder
         */
        public Builder conditionalPiece(@NotNull String name, @NotNull BlockPatternTemplate template,
                                        @NotNull Vec3i offset, @NotNull OffsetMode offsetMode,
                                        @NotNull BooleanSupplier condition) {
            if (pieces.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate piece name: " + name);
            }
            pieces.put(name, new StructurePiece(name, template, offset, offsetMode, condition));
            return this;
        }

        /**
         * Build the multi-piece pattern.
         *
         * @return the constructed MultiPiecePattern
         * @throws IllegalStateException if no pieces were added
         */
        public MultiPiecePattern build() {
            if (pieces.isEmpty()) {
                throw new IllegalStateException("MultiPiecePattern must have at least one piece");
            }
            return new MultiPiecePattern(pieces);
        }
    }
}
