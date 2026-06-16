package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.util.BlockInfo;

import java.util.Set;

/**
 * Element that matches any block (wildcard).
 */
public class AnyElement implements ITypedStructureElement<Object> {

    public static final AnyElement INSTANCE = new AnyElement();

    private final TraceabilityPredicate cachedPredicate = TraceabilityPredicate.ANY;

    private AnyElement() {}

    @Override
    public Set<StructureElementCapability> getCapabilities() {
        return StructureElementCapability.snapshotSafe();
    }

    @Override
    public boolean check(StructureEvaluationContext<Object> context) {
        return true;
    }

    @Override
    public BlockInfo[] getCandidates() {
        return new BlockInfo[0];
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return cachedPredicate;
    }
}
