package gregtech.api.pattern;

/**
 * Deterministic reason why a definition cannot use the eligible contribution
 * evaluator path.
 */
public enum StructureIncrementalFallbackReason {
    NO_BASELINE,
    DEFINITION_NOT_ELIGIBLE,
    OPAQUE_ELEMENT,
    OPAQUE_CONDITION,
    UNKNOWN_DEPENDENCY,
    UNKNOWN_EXTERNAL_DEPENDENCY,
    DEPENDENCY_CYCLE,
    DEFINITION_GENERATION_CHANGED,
    ORIENTATION_CHANGED,
    POSITION_NOT_INDEXED,
    UNSUPPORTED_CONTRIBUTION_KEY
}
