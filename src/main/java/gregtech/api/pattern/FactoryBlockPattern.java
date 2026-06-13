package gregtech.api.pattern;

import gregtech.api.util.RelativeDirection;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Public-facing fluent facade for building a {@link BlockPatternTemplate} from
 * a flat-string pattern. This class is the entry point used by 100+ existing
 * legacy multiblocks; its public API is preserved verbatim.
 *
 * <p>All compilation logic lives in
 * {@link PieceTemplateCompiler} — this class is a thin wrapper that holds a
 * {@code PieceTemplateCompiler} instance and delegates every call to it. The
 * new structure system ({@link gregtech.api.pattern.element.StructureCompiler})
 * uses {@code PieceTemplateCompiler} directly, so the new system no longer
 * depends on this facade.
 *
 * <p>New code should prefer
 * {@link gregtech.api.pattern.element.StructureDefinition.Builder} and the
 * {@link gregtech.api.pattern.element.Elements} factory methods instead of
 * this class. {@code aisleRepeatable(...)} is scheduled for removal in 2.10.
 *
 * <p>Usage example (legacy):
 * <pre>{@code
 * return FactoryBlockPattern.start()
 *     .aisle("XXX", "X#X", "XXX")
 *     .where('X', casingPredicate)
 *     .where('#', airPredicate)
 *     .build();
 * }</pre>
 */
@ApiStatus.Obsolete
public class FactoryBlockPattern {

    private final PieceTemplateCompiler compiler;

    private FactoryBlockPattern(RelativeDirection charDir, RelativeDirection stringDir, RelativeDirection aisleDir) {
        this.compiler = new PieceTemplateCompiler(charDir, stringDir, aisleDir);
    }

    /**
     * Adds a repeatable aisle to this pattern.
     *
     * @deprecated Use {@link gregtech.api.pattern.element.StructureDefinition} builder's
     *         {@code .repeatableX(...) / .repeatableY(...) / .repeatableZ(...)} (uniform single-axis)
     *         or {@code .repeatablePiece(...).repeatAxes(...)} (uniform multi-axis) instead.
     *         This API will be removed in 2.10. New machines must use
     *         {@code createStructureDefinition()} (see {@code docs/design/structure-system-v3-design.md}).
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2.10")
    public FactoryBlockPattern aisleRepeatable(int minRepeat, int maxRepeat, String... aisle) {
        compiler.aisleRepeatable(minRepeat, maxRepeat, aisle);
        return this;
    }

    /**
     * Adds a single aisle to this pattern. (so multiple calls to this will increase the aisleDir by 1)
     */
    public FactoryBlockPattern aisle(String... aisle) {
        compiler.aisle(aisle);
        return this;
    }

    /**
     * Set last aisle repeatable
     */
    public FactoryBlockPattern setRepeatable(int minRepeat, int maxRepeat) {
        compiler.setRepeatable(minRepeat, maxRepeat);
        return this;
    }

    /**
     * Set last aisle repeatable with an associated channel.
     */
    public FactoryBlockPattern setRepeatable(int minRepeat, int maxRepeat, String channelName) {
        compiler.setRepeatable(minRepeat, maxRepeat, channelName);
        return this;
    }

    /**
     * Set last aisle repeatable
     */
    public FactoryBlockPattern setRepeatable(int repeatCount) {
        compiler.setRepeatable(repeatCount);
        return this;
    }

    public static FactoryBlockPattern start() {
        return new FactoryBlockPattern(RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.BACK);
    }

    public static FactoryBlockPattern start(RelativeDirection charDir, RelativeDirection stringDir,
                                            RelativeDirection aisleDir) {
        return new FactoryBlockPattern(charDir, stringDir, aisleDir);
    }

    public FactoryBlockPattern where(char symbol, TraceabilityPredicate blockMatcher) {
        compiler.where(symbol, blockMatcher);
        return this;
    }

    public FactoryBlockPattern where(String symbol, TraceabilityPredicate blockMatcher) {
        compiler.where(symbol, blockMatcher);
        return this;
    }

    /**
     * Build the immutable template. Use this for new code that separates template from state.
     *
     * @return the shared immutable template
     */
    public BlockPatternTemplate buildTemplate() {
        return compiler.buildTemplate();
    }

    /**
     * Build the immutable template with an externally-specified center offset.
     * Use this for multi-piece sub-patterns that don't have a selfPredicate() center marker.
     *
     * @param centerOffset the center offset [x, y, z, minZ, maxZ]
     * @return the shared immutable template
     */
    public BlockPatternTemplate buildTemplate(@NotNull int[] centerOffset) {
        return compiler.buildTemplate(centerOffset);
    }

    /**
     * Build a BlockPattern (template + state combined) for backward compatibility.
     *
     * @deprecated Use {@link #buildTemplate()} and create per-instance state via
     *             {@link BlockPatternTemplate#createState()} for better memory efficiency.
     *             Will be removed in version 2.10.
     */
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2.10")
    public BlockPattern build() {
        return new BlockPattern(buildTemplate());
    }

    // --- Internal-state accessors ---
    //
    // These accessors expose the underlying PieceTemplateCompiler's state and
    // are used by integration code (e.g. StructureDefinition.pieceFromFactory)
    // to migrate factory-built patterns into the new structure system. They
    // are not part of the public builder API.

    /**
     * @return the aisle repetition ranges. Each entry is {@code [minRepeat, maxRepeat]}.
     */
    @NotNull
    public List<int[]> getAisleRepetitions() {
        return compiler.aisleRepetitionsView();
    }

    /**
     * @return the channel names for repeatable aisles. Null for non-channel aisles.
     */
    @NotNull
    public List<String> getAisleChannelNames() {
        return compiler.aisleChannelNamesView();
    }

    /**
     * @return the aisle string definitions. Each entry is an array of row strings.
     */
    @NotNull
    public List<String[]> getDepth() {
        return compiler.depthView();
    }

    /**
     * @return the symbol-to-predicate mapping.
     */
    @NotNull
    public Map<Character, TraceabilityPredicate> getSymbolMap() {
        return compiler.symbolMapView();
    }

    /**
     * @return the structure direction triple [charDir, stringDir, aisleDir].
     */
    @NotNull
    public RelativeDirection[] getStructureDir() {
        return compiler.getStructureDir();
    }
}
