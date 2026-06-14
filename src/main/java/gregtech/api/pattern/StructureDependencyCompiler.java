package gregtech.api.pattern;

import gregtech.api.pattern.element.IStructureElement;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Compiles static dependency and eligibility metadata for a multi-piece
 * structure pattern.
 */
public final class StructureDependencyCompiler {

    private StructureDependencyCompiler() {}

    @NotNull
    public static StructureEligibilityPlan compile(@NotNull MultiPiecePattern pattern) {
        List<StructurePiece> pieces = pattern.getPieceList();
        List<String> pieceNames = new ArrayList<>(pieces.size());
        for (StructurePiece piece : pieces) {
            pieceNames.add(piece.getName());
        }

        PieceDependencyGraph.Builder graph = PieceDependencyGraph.builder(pieceNames);
        LinkedHashSet<StructureExternalDependencyKey<?>> externalDependencies = new LinkedHashSet<>();
        LinkedHashMap<StructureExternalDependencyKey<?>, Set<String>> externalDependencyRoots =
                new LinkedHashMap<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        MutableFallback fallback = new MutableFallback();

        for (StructurePiece piece : pieces) {
            compileDynamicAnchor(piece, graph, fallback, diagnostics);
            compileCondition(piece, graph, externalDependencies, externalDependencyRoots,
                    fallback, diagnostics);
            compileElements(piece, fallback, diagnostics);
        }

        PieceDependencyGraph completedGraph = graph.build();
        String cycle = completedGraph.findCycleDescription();
        if (cycle != null) {
            fail(fallback, diagnostics, StructureIncrementalFallbackReason.DEPENDENCY_CYCLE,
                    "Dependency cycle detected: " + cycle);
        }

        if (fallback.reason == null) {
            return StructureEligibilityPlan.eligible(
                    completedGraph, externalDependencies, externalDependencyRoots, diagnostics);
        }
        return StructureEligibilityPlan.fallback(
                completedGraph, externalDependencies, externalDependencyRoots,
                fallback.reason, fallback.detail, diagnostics);
    }

    private static void compileDynamicAnchor(
            @NotNull StructurePiece piece,
            @NotNull PieceDependencyGraph.Builder graph,
            @NotNull MutableFallback fallback,
            @NotNull List<String> diagnostics) {
        String anchorName = null;
        if (piece instanceof DynamicOffsetPiece) {
            anchorName = ((DynamicOffsetPiece) piece).getAnchorPieceName();
        } else if (piece instanceof DynamicRepeatGroupPiece) {
            anchorName = ((DynamicRepeatGroupPiece) piece).getAnchorPieceName();
        }
        if (anchorName == null) {
            return;
        }
        addPieceEdge(graph, fallback, diagnostics, anchorName, piece.getName(),
                EnumSet.of(PieceDependencyAspect.CENTER, PieceDependencyAspect.REPETITIONS),
                "dynamic-anchor");
    }

    @SuppressWarnings("unchecked")
    private static void compileCondition(
            @NotNull StructurePiece piece,
            @NotNull PieceDependencyGraph.Builder graph,
            @NotNull Set<StructureExternalDependencyKey<?>> externalDependencies,
            @NotNull Map<StructureExternalDependencyKey<?>, Set<String>> externalDependencyRoots,
            @NotNull MutableFallback fallback,
            @NotNull List<String> diagnostics) {
        BooleanSupplier condition = piece.getCondition();
        if (condition == null) {
            return;
        }
        if (!(condition instanceof StructureCondition)) {
            fail(fallback, diagnostics, StructureIncrementalFallbackReason.OPAQUE_CONDITION,
                    "Piece '" + piece.getName() + "' uses a legacy BooleanSupplier condition");
            return;
        }

        Set<StructureDependency> dependencies;
        try {
            dependencies = ((StructureCondition<Object>) condition).dependencies();
        } catch (RuntimeException e) {
            fail(fallback, diagnostics, StructureIncrementalFallbackReason.OPAQUE_CONDITION,
                    "Piece '" + piece.getName()
                            + "' condition threw while declaring dependencies: "
                            + e.getClass().getSimpleName());
            return;
        }

        if (dependencies.isEmpty()) {
            fail(fallback, diagnostics, StructureIncrementalFallbackReason.OPAQUE_CONDITION,
                    "Piece '" + piece.getName() + "' condition declares no typed dependencies");
            return;
        }

        for (StructureDependency dependency : dependencies) {
            if (dependency.getKind() == StructureDependency.Kind.PIECE) {
                String sourcePiece = dependency.getPieceName();
                if (sourcePiece == null) {
                    fail(fallback, diagnostics, StructureIncrementalFallbackReason.UNKNOWN_DEPENDENCY,
                            "Piece '" + piece.getName() + "' declares a null piece dependency");
                    continue;
                }
                addPieceEdge(graph, fallback, diagnostics, sourcePiece, piece.getName(),
                        dependency.getAspects(), "condition:" + dependency.getReason());
            } else if (dependency.getKind() == StructureDependency.Kind.EXTERNAL) {
                StructureExternalDependencyKey<?> key = dependency.getExternalKey();
                if (key == null) {
                    fail(fallback, diagnostics,
                            StructureIncrementalFallbackReason.UNKNOWN_EXTERNAL_DEPENDENCY,
                            "Piece '" + piece.getName() + "' declares a null external dependency");
                    continue;
                }
                externalDependencies.add(key);
                externalDependencyRoots
                        .computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                        .add(piece.getName());
                diagnostics.add("external dependency '" + key.getId()
                        + "' affects piece '" + piece.getName() + "'");
            }
        }
    }

    private static void compileElements(
            @NotNull StructurePiece piece,
            @NotNull MutableFallback fallback,
            @NotNull List<String> diagnostics) {
        IStructureElement<?>[][][] elements = piece.getPieceTemplate().getElements();
        for (int z = 0; z < elements.length; z++) {
            IStructureElement<?>[][] layer = elements[z];
            for (int y = 0; y < layer.length; y++) {
                IStructureElement<?>[] row = layer[y];
                for (int x = 0; x < row.length; x++) {
                    IStructureElement<?> element = row[x];
                    if (element == null) {
                        fail(fallback, diagnostics, StructureIncrementalFallbackReason.OPAQUE_ELEMENT,
                                "Piece '" + piece.getName() + "' has a null element at "
                                        + x + "," + y + "," + z);
                        continue;
                    }
                    if (element.getIncrementalSupport() == StructureIncrementalSupport.OPAQUE) {
                        fail(fallback, diagnostics, StructureIncrementalFallbackReason.OPAQUE_ELEMENT,
                                "Piece '" + piece.getName() + "' has opaque element "
                                        + element.getClass().getName()
                                        + " at " + x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    private static void addPieceEdge(
            @NotNull PieceDependencyGraph.Builder graph,
            @NotNull MutableFallback fallback,
            @NotNull List<String> diagnostics,
            @NotNull String sourcePiece,
            @NotNull String targetPiece,
            @NotNull Set<PieceDependencyAspect> aspects,
            @NotNull String reason) {
        Integer sourceOrdinal = graph.ordinalOf(sourcePiece);
        Integer targetOrdinal = graph.ordinalOf(targetPiece);
        if (sourceOrdinal == null) {
            fail(fallback, diagnostics, StructureIncrementalFallbackReason.UNKNOWN_DEPENDENCY,
                    "Piece '" + targetPiece + "' depends on unknown piece '" + sourcePiece + "'");
            return;
        }
        if (targetOrdinal == null) {
            fail(fallback, diagnostics, StructureIncrementalFallbackReason.UNKNOWN_DEPENDENCY,
                    "Unknown dependency target piece '" + targetPiece + "'");
            return;
        }
        if (sourceOrdinal >= targetOrdinal) {
            fail(fallback, diagnostics, StructureIncrementalFallbackReason.DEPENDENCY_CYCLE,
                    "Piece '" + targetPiece + "' depends on same or later piece '"
                            + sourcePiece + "'");
        }
        graph.addEdge(sourcePiece, targetPiece, aspects, reason);
    }

    private static void fail(
            @NotNull MutableFallback fallback,
            @NotNull List<String> diagnostics,
            @NotNull StructureIncrementalFallbackReason reason,
            @NotNull String detail) {
        diagnostics.add(reason + ": " + detail);
        if (fallback.reason == null) {
            fallback.reason = reason;
            fallback.detail = detail;
        }
    }

    private static final class MutableFallback {
        @Nullable
        private StructureIncrementalFallbackReason reason;
        @NotNull
        private String detail = "";
    }
}
