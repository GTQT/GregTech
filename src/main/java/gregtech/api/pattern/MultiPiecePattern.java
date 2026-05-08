package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * A composite multi-piece pattern for super-large multiblock structures.
 * Instead of a single monolithic pattern, the structure is divided into named pieces,
 * each with its own template, offset, and independent dirty/validated state.
 *
 * <p>When a block changes, only the piece(s) containing that position are marked dirty
 * and re-validated, rather than re-checking the entire structure.
 *
 * <p>Usage example:
 * <pre>{@code
 * MultiPiecePattern pattern = MultiPiecePattern.builder()
 *     .piece("core", coreTemplate, Vec3i.ZERO)
 *     .piece("ring1", ring1Template, new Vec3i(0, 0, -59))
 *     .conditionalPiece("ring2", ring2Template, new Vec3i(0, 0, -67), () -> isUpgradeActive())
 *     .build();
 * }</pre>
 *
 * @see StructurePiece for individual piece definition
 */
public class MultiPiecePattern {

    private final Map<String, StructurePiece> pieces;
    private final List<StructurePiece> pieceList;

    private MultiPiecePattern(Map<String, StructurePiece> pieces) {
        this.pieces = Collections.unmodifiableMap(pieces);
        this.pieceList = Collections.unmodifiableList(new ArrayList<>(pieces.values()));
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
     * @return a new LongSet containing all positions
     */
    public LongSet getAllPositions() {
        LongSet all = new LongOpenHashSet();
        for (StructurePiece piece : pieceList) {
            if (piece.isActive() && piece.isValidated()) {
                all.addAll(piece.getPositions());
            }
        }
        return all;
    }

    /**
     * Check if any piece is dirty and needs re-validation.
     *
     * @return true if at least one active piece is dirty
     */
    public boolean hasDirtyPieces() {
        for (StructurePiece piece : pieceList) {
            if (piece.isActive() && piece.isDirty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the list of dirty pieces that need re-checking.
     *
     * @return list of dirty, active pieces
     */
    public List<StructurePiece> getDirtyPieces() {
        List<StructurePiece> dirty = new ArrayList<>();
        for (StructurePiece piece : pieceList) {
            if (piece.isActive() && piece.isDirty()) {
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
     * @return true if all active pieces are valid
     */
    public boolean checkDirtyPieces(World world, BlockPos controllerPos, EnumFacing frontFacing,
                                     EnumFacing upwardsFacing, boolean allowsFlip) {
        for (StructurePiece piece : pieceList) {
            if (!piece.isActive()) continue;

            if (piece.isDirty()) {
                BlockPos pieceCenter = piece.getCenterPos(controllerPos, frontFacing, upwardsFacing);
                PatternMatchContext result = piece.getState().checkPatternFastAt(
                        world, pieceCenter, frontFacing, upwardsFacing, allowsFlip);

                if (result != null) {
                    piece.setValidated(true);
                    // Atomically swap the piece's position set from the state cache
                    LongSet newPositions = new LongOpenHashSet(piece.getState().cache.keySet());
                    piece.swapPositions(newPositions);
                } else {
                    piece.setValidated(false);
                }
                piece.clearDirty();
            }

            if (!piece.isValidated()) {
                return false;
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
     * @return true if all active pieces are valid
     */
    public boolean checkAllPieces(World world, BlockPos controllerPos, EnumFacing frontFacing,
                                   EnumFacing upwardsFacing, boolean allowsFlip) {
        for (StructurePiece piece : pieceList) {
            piece.markDirty();
        }
        return checkDirtyPieces(world, controllerPos, frontFacing, upwardsFacing, allowsFlip);
    }

    /**
     * Mark a specific piece as dirty by position.
     * Called when a block change is detected within the piece's cached positions.
     *
     * @param posLong the changed block position as a long
     * @return true if a piece was found and marked dirty
     */
    public boolean markDirtyByPosition(long posLong) {
        boolean found = false;
        for (StructurePiece piece : pieceList) {
            if (piece.isActive() && piece.isValidated() && piece.getPositions().contains(posLong)) {
                piece.markDirty();
                found = true;
            }
        }
        return found;
    }

    /**
     * Auto-build a specific piece by its 1-based index.
     * Computes the piece's center position from the controller and delegates to the piece's state.
     *
     * @param pieceIndex     1-based index into the piece list
     * @param player         the player performing the build
     * @param controller     the multiblock controller
     * @param channelValues  channel values for tier selection
     * @param skipHatches    if true, skip hatch placement
     * @return true if the piece was successfully built (index valid and piece exists)
     */
    public boolean autoBuildPiece(int pieceIndex, EntityPlayer player, MultiblockControllerBase controller,
                                  @Nullable Map<String, Integer> channelValues, boolean skipHatches) {
        if (pieceIndex < 1 || pieceIndex > pieceList.size()) return false;

        StructurePiece piece = pieceList.get(pieceIndex - 1);
        BlockPos pieceCenter = piece.getCenterPos(
                controller.getPos(), controller.getFrontFacing(), controller.getUpwardsFacing());

        piece.getState().autoBuildAt(player, controller, pieceCenter, channelValues, skipHatches);
        return true;
    }

    /**
     * @return the number of pieces in this pattern
     */
    public int getPieceCount() {
        return pieceList.size();
    }

    /**
     * Reset all pieces (on structure invalidation).
     */
    public void resetAll() {
        for (StructurePiece piece : pieceList) {
            piece.reset();
        }
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
