package gregtech.common.pipelike.fiber.recipe;

/**
 * Comparison applied to an intensity bound.
 * <p>
 * Each operator carries the symbol used when the requirement is shown, so recipes and JEI agree.
 */
public enum FiberIntensityOperator {

    GREATER_THAN(">"),
    GREATER_OR_EQUAL(">="),
    LESS_THAN("<"),
    LESS_OR_EQUAL("<="),
    EQUAL("=");

    private final String symbol;

    FiberIntensityOperator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean test(long intensity, long bound) {
        return switch (this) {
            case GREATER_THAN -> intensity > bound;
            case GREATER_OR_EQUAL -> intensity >= bound;
            case LESS_THAN -> intensity < bound;
            case LESS_OR_EQUAL -> intensity <= bound;
            case EQUAL -> intensity == bound;
        };
    }
}
