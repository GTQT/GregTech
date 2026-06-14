package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compile-time eligibility decision and diagnostics for a structure definition.
 */
public final class StructureEligibilityPlan {

    private final boolean eligible;
    @NotNull
    private final PieceDependencyGraph graph;
    @NotNull
    private final Set<StructureExternalDependencyKey<?>> externalDependencies;
    @NotNull
    private final Map<StructureExternalDependencyKey<?>, Set<String>> externalDependencyRoots;
    @Nullable
    private final StructureIncrementalFallbackReason fallbackReason;
    @Nullable
    private final String fallbackDetail;
    @NotNull
    private final List<String> diagnostics;

    @NotNull
    static StructureEligibilityPlan eligible(
            @NotNull PieceDependencyGraph graph,
            @NotNull Set<StructureExternalDependencyKey<?>> externalDependencies,
            @NotNull Map<StructureExternalDependencyKey<?>, Set<String>> externalDependencyRoots,
            @NotNull List<String> diagnostics) {
        return new StructureEligibilityPlan(
                true, graph, externalDependencies, externalDependencyRoots, null, null, diagnostics);
    }

    @NotNull
    static StructureEligibilityPlan fallback(
            @NotNull PieceDependencyGraph graph,
            @NotNull Set<StructureExternalDependencyKey<?>> externalDependencies,
            @NotNull Map<StructureExternalDependencyKey<?>, Set<String>> externalDependencyRoots,
            @NotNull StructureIncrementalFallbackReason fallbackReason,
            @NotNull String fallbackDetail,
            @NotNull List<String> diagnostics) {
        return new StructureEligibilityPlan(
                false, graph, externalDependencies, externalDependencyRoots,
                fallbackReason, fallbackDetail, diagnostics);
    }

    private StructureEligibilityPlan(
            boolean eligible,
            @NotNull PieceDependencyGraph graph,
            @NotNull Set<StructureExternalDependencyKey<?>> externalDependencies,
            @NotNull Map<StructureExternalDependencyKey<?>, Set<String>> externalDependencyRoots,
            @Nullable StructureIncrementalFallbackReason fallbackReason,
            @Nullable String fallbackDetail,
            @NotNull List<String> diagnostics) {
        this.eligible = eligible;
        this.graph = graph;
        this.externalDependencies = Collections.unmodifiableSet(
                new LinkedHashSet<>(externalDependencies));
        Map<StructureExternalDependencyKey<?>, Set<String>> copiedRoots = new LinkedHashMap<>();
        for (Map.Entry<StructureExternalDependencyKey<?>, Set<String>> entry :
                externalDependencyRoots.entrySet()) {
            copiedRoots.put(entry.getKey(), Collections.unmodifiableSet(
                    new LinkedHashSet<>(entry.getValue())));
        }
        this.externalDependencyRoots = Collections.unmodifiableMap(copiedRoots);
        this.fallbackReason = fallbackReason;
        this.fallbackDetail = fallbackDetail;
        this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
    }

    public boolean isEligible() {
        return eligible;
    }

    @NotNull
    public PieceDependencyGraph getGraph() {
        return graph;
    }

    @NotNull
    public Set<StructureExternalDependencyKey<?>> getExternalDependencies() {
        return externalDependencies;
    }

    @NotNull
    public Map<StructureExternalDependencyKey<?>, Set<String>> getExternalDependencyRoots() {
        return externalDependencyRoots;
    }

    @NotNull
    public Set<String> getExternalDependencyRoots(
            @NotNull StructureExternalDependencyKey<?> key) {
        Set<String> roots = externalDependencyRoots.get(key);
        return roots == null ? Collections.emptySet() : roots;
    }

    @NotNull
    public Set<String> rootsForExternalDependencyChanges(
            @NotNull Collection<StructureExternalDependencyKey<?>> keys) {
        LinkedHashSet<String> roots = new LinkedHashSet<>();
        for (StructureExternalDependencyKey<?> key : keys) {
            roots.addAll(getExternalDependencyRoots(key));
        }
        return Collections.unmodifiableSet(roots);
    }

    @Nullable
    public StructureIncrementalFallbackReason getFallbackReason() {
        return fallbackReason;
    }

    @Nullable
    public String getFallbackDetail() {
        return fallbackDetail;
    }

    @NotNull
    public List<String> getDiagnostics() {
        return diagnostics;
    }

    @NotNull
    public StructureExternalDependencySnapshot snapshotExternalDependencies(
            @Nullable MultiblockControllerBase controller) {
        return StructureExternalDependencySnapshot.capture(externalDependencies, controller);
    }

    @NotNull
    public String describeFallback() {
        if (eligible) {
            return "eligible";
        }
        return "fallback=" + fallbackReason
                + (fallbackDetail == null ? "" : ", detail=" + fallbackDetail);
    }

    @NotNull
    public String describe() {
        return "eligible=" + eligible
                + (eligible ? "" : ", " + describeFallback())
                + ", externalDependencies=" + externalDependencies
                + ", externalDependencyRoots=" + externalDependencyRoots
                + ", graph={" + graph.describe() + "}";
    }
}
