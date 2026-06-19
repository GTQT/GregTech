package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.StructureDependency;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.StructureIncrementalSupport;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.util.Set;

/**
 * Element that wraps an existing TraceabilityPredicate for backward compatibility.
 * Used by pieceFromFactory() to convert old-style predicates to the new element system.
 */
public class LegacyElement implements IStructureElement<Object> {

    private final TraceabilityPredicate predicate;
    private final StructureElementPreview preview;

    public LegacyElement(TraceabilityPredicate predicate) {
        this.predicate = predicate;
        this.preview = StructureElementPreview.fromPredicate(predicate);
    }

    @Override
    public boolean check(StructureEvaluationContext<Object> context) {
        return context.test(predicate);
    }

    @Override
    public BlockInfo[] getCandidates() {
        // Aggregate candidates from all simple predicates
        List<BlockInfo> result = new ArrayList<>();
        for (TraceabilityPredicate.SimplePredicate sp : predicate.common) {
            if (sp.candidates != null) {
                BlockInfo[] infos = sp.candidates.get();
                if (infos != null) {
                    result.addAll(Arrays.asList(infos));
                }
            }
        }
        for (TraceabilityPredicate.SimplePredicate sp : predicate.limited) {
            if (sp.candidates != null) {
                BlockInfo[] infos = sp.candidates.get();
                if (infos != null) {
                    result.addAll(Arrays.asList(infos));
                }
            }
        }
        return result.toArray(new BlockInfo[0]);
    }

    @Override
    public boolean isCenter() {
        // Use the public getter rather than the protected isCenter field directly,
        // because LegacyElement lives in gregtech.api.pattern.element.impl while
        // TraceabilityPredicate lives in gregtech.api.pattern — accessing a
        // protected field across packages is not allowed.
        return predicate.isCenter();
    }

    @Override
    public StructureElementPreview getPreview() {
        return preview;
    }

    @Override
    public boolean usesLegacyPredicateRuntime() {
        return true;
    }

    @Override
    public StructureIncrementalSupport getIncrementalSupport() {
        return StructureIncrementalSupport.OPAQUE;
    }

    @Override
    public Set<StructureDependency> getDependencies() {
        return Collections.emptySet();
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return predicate;
    }
}
