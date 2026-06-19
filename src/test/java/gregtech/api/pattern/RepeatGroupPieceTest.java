package gregtech.api.pattern;

import gregtech.api.pattern.element.StructureCompiler;
import gregtech.api.util.RelativeDirection;

import net.minecraft.util.math.Vec3i;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatGroupPieceTest {

    @Test
    void singleAxisOffsetsUseConfiguredStepSize() {
        RepeatGroupPiece piece = repeatPiece(new int[] {2}, new int[] {5});
        List<List<Integer>> offsets = collectOffsets(piece, new int[] {4});

        assertEquals(Arrays.asList(
                Arrays.asList(0, 0, 0),
                Arrays.asList(0, 0, 5),
                Arrays.asList(0, 0, 10),
                Arrays.asList(0, 0, 15)), offsets);
    }

    @Test
    void multiAxisOffsetsKeepOriginalOdometerOrder() {
        RepeatGroupPiece piece = repeatPiece(new int[] {0, 1}, new int[] {2, 3});
        List<List<Integer>> offsets = collectOffsets(piece, new int[] {2, 3});

        assertEquals(Arrays.asList(
                Arrays.asList(0, 0, 0),
                Arrays.asList(2, 0, 0),
                Arrays.asList(0, 3, 0),
                Arrays.asList(2, 3, 0),
                Arrays.asList(0, 6, 0),
                Arrays.asList(2, 6, 0)), offsets);
    }

    @Test
    void zeroAxisPieceVisitsOriginOnce() {
        RepeatGroupPiece piece = repeatPiece(new int[0], new int[0]);
        List<List<Integer>> offsets = collectOffsets(piece, new int[0]);

        assertEquals(Arrays.asList(Arrays.asList(0, 0, 0)), offsets);
    }

    @Test
    void visitorCanStopOffsetTraversal() {
        RepeatGroupPiece piece = repeatPiece(new int[] {0, 1}, new int[] {1, 1});
        List<List<Integer>> visited = new ArrayList<>();

        boolean completed = piece.visitRepeatOffsets(new int[] {3, 3}, local -> {
            visited.add(asList(local));
            return visited.size() < 4;
        });

        assertFalse(completed);
        assertEquals(Arrays.asList(
                Arrays.asList(0, 0, 0),
                Arrays.asList(1, 0, 0),
                Arrays.asList(2, 0, 0),
                Arrays.asList(0, 1, 0)), visited);
    }

    @Test
    void visitorCompletesWhenAllOffsetsAreVisited() {
        RepeatGroupPiece piece = repeatPiece(new int[] {0}, new int[] {1});

        assertTrue(piece.visitRepeatOffsets(new int[] {2}, local -> true));
    }

    @Test
    void repeatSearchKeepsDescendingOrderWithoutPreferredSize() {
        RepeatGroupPiece piece = repeatPiece(new int[] {0}, new int[] {1});

        assertArrayEquals(new int[] {4, 3, 2, 1},
                piece.repeatCandidatesForTesting(0, null));
    }

    @Test
    void repeatSearchTriesPreferredSizeNeighborhoodFirst() {
        RepeatGroupPiece piece = repeatPiece(new int[] {0}, new int[] {1});

        assertArrayEquals(new int[] {2, 3, 1, 4},
                piece.repeatCandidatesForTesting(0, new int[] {2}));
    }

    @Test
    void typedIterationReturnsTraversalsAndOutcome() {
        RepeatGroupPiece piece = repeatPiece(new int[] {0, 1}, new int[] {2, 3});
        RepeatIterationResult result = piece.iterate(RepeatIterationRequest.of(
                BlockPos.ORIGIN,
                StructureOrientation.of(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false),
                null,
                null), new int[] {2, 2});

        assertEquals(RepeatIterationResult.Outcome.COMPLETED, result.getOutcome());
        assertEquals(4, result.getVisitedSlices());
        assertEquals(Arrays.asList(
                new BlockPos(0, 0, 0),
                new BlockPos(2, 0, 0),
                new BlockPos(0, 3, 0),
                new BlockPos(2, 3, 0)), result.getLocalOffsets());
    }

    private static List<List<Integer>> collectOffsets(RepeatGroupPiece piece, int[] reps) {
        List<List<Integer>> offsets = new ArrayList<>();
        piece.visitRepeatOffsets(reps, local -> {
            offsets.add(asList(local));
            return true;
        });
        return offsets;
    }

    private static List<Integer> asList(int[] local) {
        return Arrays.asList(local[0], local[1], local[2]);
    }

    private static RepeatGroupPiece repeatPiece(int[] axes, int[] steps) {
        int[][] ranges = new int[axes.length][2];
        for (int i = 0; i < axes.length; i++) {
            ranges[i][0] = 1;
            ranges[i][1] = 4;
        }
        return new RepeatGroupPiece(
                "repeat",
                minimalTemplate(),
                Vec3i.NULL_VECTOR,
                OffsetMode.RELATIVE,
                null,
                axes,
                ranges,
                steps,
                null,
                new int[] {0, 0, 0},
                StructureCompiler.SearchStrategy.NESTED_BACKTRACKING);
    }

    private static PieceTemplate minimalTemplate() {
        TraceabilityPredicate center = new TraceabilityPredicate().setCenter();
        TraceabilityPredicate[][][] predicates = new TraceabilityPredicate[][][] {
                {
                        { center }
                }
        };
        return new PieceTemplate(
                predicates,
                new RelativeDirection[] {
                        RelativeDirection.FRONT,
                        RelativeDirection.UP,
                        RelativeDirection.RIGHT
                },
                new int[][] {
                        { 1, 1 }
                });
    }
}
