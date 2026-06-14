package gregtech.api.pattern;

/**
 * Incremental-evaluator support declared by a structure element.
 */
public enum StructureIncrementalSupport {
    /**
     * The element reports all cross-piece effects through typed contributions.
     */
    TYPED_CONTRIBUTION,

    /**
     * The element can be matched independently but does not emit typed
     * contribution data.
     */
    MATCH_ONLY,

    /**
     * The element may use opaque legacy state or undeclared side effects.
     */
    OPAQUE
}
