package gregtech.api.pattern;

import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.util.RelativeDirection;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureContributionTest {

    @Test
    void contributionKeysRequireNamespacedIds() {
        assertThrows(IllegalArgumentException.class,
                () -> StructureContributionKey.sum("missing_namespace"));
    }

    @Test
    void builtInReducersUseDeterministicEmissionOrder() {
        StructureContributionKey<Integer, Integer> sum =
                StructureContributionKey.sum("gregtech:test_sum");
        StructureContributionKey<Integer, Integer> min =
                StructureContributionKey.min("gregtech:test_min");
        StructureContributionKey<Integer, Integer> max =
                StructureContributionKey.max("gregtech:test_max");
        StructureContributionKey<String, List<String>> ordered =
                StructureContributionKey.orderedList("gregtech:test_ordered");
        StructureContributionKey<String, Set<String>> union =
                StructureContributionKey.setUnion("gregtech:test_union");

        assertEquals(6, reduce(sum, 1, 2, 3));
        assertEquals(1, reduce(min, 3, 1, 2));
        assertEquals(3, reduce(max, 3, 1, 2));
        assertEquals(Arrays.asList("a", "b", "a"), reduce(ordered, "a", "b", "a"));
        assertEquals(2, reduce(union, "a", "b", "a").size());
    }

    @Test
    void uniformConflictFailsAggregateFold() {
        StructurePiece first = piece("first");
        StructurePiece second = piece("second");
        MultiPiecePattern pattern = new MultiPiecePattern(Arrays.asList(first, second));
        StructureContributionKey<Integer, Integer> firstKey =
                StructureContributionKey.uniform("gregtech:test_uniform");
        StructureContributionKey<Integer, Integer> secondKey =
                StructureContributionKey.uniform("gregtech:test_uniform");
        StructureContribution.Builder firstContribution = StructureContribution.builder();
        StructureContribution.Builder secondContribution = StructureContribution.builder();
        firstContribution.emit(firstKey, 1);
        secondContribution.emit(secondKey, 2);
        LongOpenHashSet positions = new LongOpenHashSet();
        positions.add(BlockPos.ORIGIN.toLong());

        StructureResultTable table = StructureResultTable.builder(pattern)
                .add(PieceEvaluationResult.activeMatched(
                        first, BlockPos.ORIGIN, null, positions, positions,
                        firstContribution.build()))
                .add(PieceEvaluationResult.activeMatched(
                        second, BlockPos.ORIGIN.up(), null, positions, positions,
                        secondContribution.build()))
                .build();

        StructureAggregateFolder.Result result =
                StructureAggregateFolder.fold(pattern, table);

        assertFalse(result.isMatched());
        assertTrue(result.getErrorMessage().contains("uniform"));
    }

    @Test
    void sameIdWithDifferentCustomSchemasFailsAggregateFold() {
        StructurePiece first = piece("first");
        StructurePiece second = piece("second");
        MultiPiecePattern pattern = new MultiPiecePattern(Arrays.asList(first, second));
        StructureContributionKey<Integer, Integer> firstKey =
                StructureContributionKey.create("gregtech:test_custom", () -> 0, Integer::sum);
        StructureContributionKey<Integer, Integer> secondKey =
                StructureContributionKey.create("gregtech:test_custom", () -> 1, (left, right) -> left * right);
        StructureContribution.Builder firstContribution = StructureContribution.builder();
        StructureContribution.Builder secondContribution = StructureContribution.builder();
        firstContribution.emit(firstKey, 1);
        secondContribution.emit(secondKey, 2);
        LongOpenHashSet positions = new LongOpenHashSet();
        positions.add(BlockPos.ORIGIN.toLong());
        StructureResultTable table = StructureResultTable.builder(pattern)
                .add(PieceEvaluationResult.activeMatched(
                        first, BlockPos.ORIGIN, null, positions, positions,
                        firstContribution.build()))
                .add(PieceEvaluationResult.activeMatched(
                        second, BlockPos.ORIGIN.up(), null, positions, positions,
                        secondContribution.build()))
                .build();

        StructureAggregateFolder.Result result =
                StructureAggregateFolder.fold(pattern, table);

        assertFalse(result.isMatched());
        assertTrue(result.getErrorMessage().contains("conflicting schemas"));
    }

    @Test
    void pieceResultDefensivelyCopiesMutableInputs() {
        StructurePiece piece = piece("piece");
        int[] repetitions = {2};
        LongOpenHashSet positions = new LongOpenHashSet();
        positions.add(BlockPos.ORIGIN.toLong());

        PieceEvaluationResult result = PieceEvaluationResult.activeMatched(
                piece, BlockPos.ORIGIN, repetitions, positions, positions,
                StructureContribution.empty());
        repetitions[0] = 9;
        positions.clear();

        assertEquals(2, result.getRepetitions()[0]);
        assertEquals(1, result.getFormedPositions().size());
        assertThrows(UnsupportedOperationException.class,
                () -> result.getFormedPositions().clear());
    }

    @SafeVarargs
    private static <E, A> A reduce(StructureContributionKey<E, A> key, E... values) {
        A aggregate = key.identity();
        for (E value : values) {
            aggregate = key.reduce(aggregate, value);
        }
        return key.copyAggregate(aggregate);
    }

    private static StructurePiece piece(String name) {
        TraceabilityPredicate[][][] predicates = {{{TraceabilityPredicate.ANY}}};
        IStructureElement<?>[][][] elements = {{{null}}};
        PieceTemplate template = new PieceTemplate(
                predicates,
                elements,
                new RelativeDirection[] {
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK
                },
                new int[0][],
                null,
                new int[] {0, 0, 0, 0, 0},
                null);
        return new StructurePiece(
                name, template, Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
    }
}
