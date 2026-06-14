package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable piece dependency graph in declaration order.
 */
public final class PieceDependencyGraph {

    public static final class Edge {

        private final int sourceOrdinal;
        private final int targetOrdinal;
        @NotNull
        private final String sourcePiece;
        @NotNull
        private final String targetPiece;
        @NotNull
        private final Set<PieceDependencyAspect> aspects;
        @NotNull
        private final String reason;

        private Edge(int sourceOrdinal, int targetOrdinal,
                     @NotNull String sourcePiece, @NotNull String targetPiece,
                     @NotNull Set<PieceDependencyAspect> aspects,
                     @NotNull String reason) {
            this.sourceOrdinal = sourceOrdinal;
            this.targetOrdinal = targetOrdinal;
            this.sourcePiece = sourcePiece;
            this.targetPiece = targetPiece;
            this.aspects = Collections.unmodifiableSet(EnumSet.copyOf(aspects));
            this.reason = reason;
        }

        public int getSourceOrdinal() {
            return sourceOrdinal;
        }

        public int getTargetOrdinal() {
            return targetOrdinal;
        }

        @NotNull
        public String getSourcePiece() {
            return sourcePiece;
        }

        @NotNull
        public String getTargetPiece() {
            return targetPiece;
        }

        @NotNull
        public Set<PieceDependencyAspect> getAspects() {
            return aspects;
        }

        @NotNull
        public String getReason() {
            return reason;
        }

        @NotNull
        public String describe() {
            return sourcePiece + " -> " + targetPiece
                    + " aspects=" + aspects + " reason=" + reason;
        }
    }

    public static final class Builder {

        @NotNull
        private final List<String> pieceNames;
        @NotNull
        private final Map<String, Integer> ordinals;
        @NotNull
        private final List<Edge> edges = new ArrayList<>();

        Builder(@NotNull List<String> pieceNames) {
            this.pieceNames = new ArrayList<>(pieceNames);
            this.ordinals = new LinkedHashMap<>();
            for (int i = 0; i < pieceNames.size(); i++) {
                ordinals.put(pieceNames.get(i), i);
            }
        }

        @Nullable
        Integer ordinalOf(@NotNull String pieceName) {
            return ordinals.get(pieceName);
        }

        @NotNull
        Builder addEdge(@NotNull String sourcePiece,
                        @NotNull String targetPiece,
                        @NotNull Collection<PieceDependencyAspect> aspects,
                        @NotNull String reason) {
            Integer source = ordinalOf(sourcePiece);
            Integer target = ordinalOf(targetPiece);
            if (source == null || target == null) {
                throw new IllegalArgumentException(
                        "Cannot add dependency edge with unknown piece: "
                                + sourcePiece + " -> " + targetPiece);
            }
            edges.add(new Edge(source, target, sourcePiece, targetPiece,
                    EnumSet.copyOf(aspects), reason));
            return this;
        }

        @NotNull
        PieceDependencyGraph build() {
            return new PieceDependencyGraph(pieceNames, edges);
        }
    }

    @NotNull
    static Builder builder(@NotNull List<String> pieceNames) {
        return new Builder(pieceNames);
    }

    @NotNull
    private final List<String> pieceNames;
    @NotNull
    private final Map<String, Integer> ordinals;
    @NotNull
    private final List<Edge> edges;
    @NotNull
    private final Map<String, List<Edge>> outgoing;
    @NotNull
    private final Map<String, List<Edge>> incoming;

    private PieceDependencyGraph(@NotNull List<String> pieceNames,
                                 @NotNull List<Edge> edges) {
        this.pieceNames = Collections.unmodifiableList(new ArrayList<>(pieceNames));
        Map<String, Integer> ordinalMap = new LinkedHashMap<>();
        Map<String, List<Edge>> outgoingMap = new LinkedHashMap<>();
        Map<String, List<Edge>> incomingMap = new LinkedHashMap<>();
        for (int i = 0; i < pieceNames.size(); i++) {
            String name = pieceNames.get(i);
            ordinalMap.put(name, i);
            outgoingMap.put(name, new ArrayList<>());
            incomingMap.put(name, new ArrayList<>());
        }
        for (Edge edge : edges) {
            outgoingMap.get(edge.getSourcePiece()).add(edge);
            incomingMap.get(edge.getTargetPiece()).add(edge);
        }
        for (Map.Entry<String, List<Edge>> entry : outgoingMap.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        for (Map.Entry<String, List<Edge>> entry : incomingMap.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        this.ordinals = Collections.unmodifiableMap(ordinalMap);
        this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
        this.outgoing = Collections.unmodifiableMap(outgoingMap);
        this.incoming = Collections.unmodifiableMap(incomingMap);
    }

    public int getNodeCount() {
        return pieceNames.size();
    }

    @NotNull
    public List<String> getPieceNames() {
        return pieceNames;
    }

    @NotNull
    public List<Edge> getEdges() {
        return edges;
    }

    @Nullable
    public Integer getOrdinal(@NotNull String pieceName) {
        return ordinals.get(pieceName);
    }

    @NotNull
    public List<Edge> getOutgoingEdges(@NotNull String pieceName) {
        List<Edge> result = outgoing.get(pieceName);
        return result == null ? Collections.emptyList() : result;
    }

    @NotNull
    public List<Edge> getIncomingEdges(@NotNull String pieceName) {
        List<Edge> result = incoming.get(pieceName);
        return result == null ? Collections.emptyList() : result;
    }

    @NotNull
    public Set<String> dependentClosure(@NotNull Collection<String> roots) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (String root : roots) {
            if (ordinals.containsKey(root) && result.add(root)) {
                queue.add(root);
            }
        }
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            for (Edge edge : getOutgoingEdges(current)) {
                if (result.add(edge.getTargetPiece())) {
                    queue.addLast(edge.getTargetPiece());
                }
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @Nullable
    String findCycleDescription() {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        for (String pieceName : pieceNames) {
            String cycle = findCycle(pieceName, visiting, visited, stack);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    @Nullable
    private String findCycle(@NotNull String pieceName,
                             @NotNull Set<String> visiting,
                             @NotNull Set<String> visited,
                             @NotNull ArrayDeque<String> stack) {
        if (visited.contains(pieceName)) {
            return null;
        }
        if (visiting.contains(pieceName)) {
            ArrayList<String> cycle = new ArrayList<>();
            boolean inCycle = false;
            for (String entry : stack) {
                if (entry.equals(pieceName)) {
                    inCycle = true;
                }
                if (inCycle) {
                    cycle.add(entry);
                }
            }
            cycle.add(pieceName);
            return String.join(" -> ", cycle);
        }
        visiting.add(pieceName);
        stack.addLast(pieceName);
        for (Edge edge : getOutgoingEdges(pieceName)) {
            String cycle = findCycle(edge.getTargetPiece(), visiting, visited, stack);
            if (cycle != null) {
                return cycle;
            }
        }
        stack.removeLast();
        visiting.remove(pieceName);
        visited.add(pieceName);
        return null;
    }

    @NotNull
    public String describe() {
        StringBuilder builder = new StringBuilder();
        builder.append("pieces=").append(pieceNames);
        builder.append(", edges=[");
        for (int i = 0; i < edges.size(); i++) {
            if (i > 0) {
                builder.append("; ");
            }
            builder.append(edges.get(i).describe());
        }
        builder.append("]");
        return builder.toString();
    }
}
