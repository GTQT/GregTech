package gregtech.api.pattern;

import gregtech.api.util.RelativeDirection;

import com.google.common.base.Joiner;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static gregtech.api.util.RelativeDirection.*;

public class FactoryBlockPattern {

    private static final Joiner COMMA_JOIN = Joiner.on(",");
    private final List<String[]> depth = new ArrayList<>();
    private final List<int[]> aisleRepetitions = new ArrayList<>();
    private final List<String> aisleChannelNames = new ArrayList<>();
    private final Map<Character, TraceabilityPredicate> symbolMap = new HashMap<>();
    private int aisleHeight;
    private int rowWidth;
    private final RelativeDirection[] structureDir = new RelativeDirection[3];

    private FactoryBlockPattern(RelativeDirection charDir, RelativeDirection stringDir, RelativeDirection aisleDir) {
        structureDir[0] = charDir;
        structureDir[1] = stringDir;
        structureDir[2] = aisleDir;
        int flags = 0;
        for (RelativeDirection relativeDirection : this.structureDir) {
            switch (relativeDirection) {
                case UP:
                case DOWN:
                    flags |= 0x1;
                    break;
                case LEFT:
                case RIGHT:
                    flags |= 0x2;
                    break;
                case FRONT:
                case BACK:
                    flags |= 0x4;
                    break;
            }
        }
        if (flags != 0x7) throw new IllegalArgumentException("Must have 3 different axes!");
        this.symbolMap.put(' ', TraceabilityPredicate.ANY);
    }

    /**
     * Adds a repeatable aisle to this pattern.
     */
    public FactoryBlockPattern aisleRepeatable(int minRepeat, int maxRepeat, String... aisle) {
        if (!ArrayUtils.isEmpty(aisle) && !StringUtils.isEmpty(aisle[0])) {
            if (this.depth.isEmpty()) {
                this.aisleHeight = aisle.length;
                this.rowWidth = aisle[0].length();
            }

            if (aisle.length != this.aisleHeight) {
                throw new IllegalArgumentException("Expected aisle with height of " + this.aisleHeight +
                        ", but was given one with a height of " + aisle.length + ")");
            } else {
                for (String s : aisle) {
                    if (s.length() != this.rowWidth) {
                        throw new IllegalArgumentException(
                                "Not all rows in the given aisle are the correct width (expected " + this.rowWidth +
                                        ", found one with " + s.length() + ")");
                    }

                    for (char c0 : s.toCharArray()) {
                        if (!this.symbolMap.containsKey(c0)) {
                            this.symbolMap.put(c0, null);
                        }
                    }
                }

                this.depth.add(aisle);
                if (minRepeat > maxRepeat)
                    throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
                aisleRepetitions.add(new int[] { minRepeat, maxRepeat });
                aisleChannelNames.add(null);
                return this;
            }
        } else {
            throw new IllegalArgumentException("Empty pattern for aisle");
        }
    }

    /**
     * Adds a single aisle to this pattern. (so multiple calls to this will increase the aisleDir by 1)
     */
    public FactoryBlockPattern aisle(String... aisle) {
        return aisleRepeatable(1, 1, aisle);
    }

    /**
     * Set last aisle repeatable
     */
    public FactoryBlockPattern setRepeatable(int minRepeat, int maxRepeat) {
        if (minRepeat > maxRepeat)
            throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
        aisleRepetitions.set(aisleRepetitions.size() - 1, new int[] { minRepeat, maxRepeat });
        return this;
    }

    /**
     * Set last aisle repeatable with an associated channel.
     */
    public FactoryBlockPattern setRepeatable(int minRepeat, int maxRepeat, String channelName) {
        if (minRepeat > maxRepeat)
            throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
        aisleRepetitions.set(aisleRepetitions.size() - 1, new int[] { minRepeat, maxRepeat });
        aisleChannelNames.set(aisleChannelNames.size() - 1, channelName);
        return this;
    }

    /**
     * Get the repetition range of the last aisle.
     */
    public int[] getLastAisleRepetition() {
        return aisleRepetitions.get(aisleRepetitions.size() - 1);
    }

    /**
     * Set last aisle repeatable
     */
    public FactoryBlockPattern setRepeatable(int repeatCount) {
        return setRepeatable(repeatCount, repeatCount);
    }

    public static FactoryBlockPattern start() {
        return new FactoryBlockPattern(RIGHT, UP, BACK);
    }

    public static FactoryBlockPattern start(RelativeDirection charDir, RelativeDirection stringDir,
                                            RelativeDirection aisleDir) {
        return new FactoryBlockPattern(charDir, stringDir, aisleDir);
    }

    public FactoryBlockPattern where(char symbol, TraceabilityPredicate blockMatcher) {
        this.symbolMap.put(symbol, new TraceabilityPredicate(blockMatcher).sort());
        return this;
    }

    public FactoryBlockPattern where(String symbol, TraceabilityPredicate blockMatcher) {
        if (symbol.length() == 1) {
            return where(symbol.charAt(0), blockMatcher);
        }
        throw new IllegalArgumentException(
                String.format("Symbol \"%s\" is invalid! It must be exactly one character!", symbol));
    }

    /**
     * Build the immutable template. Use this for new code that separates template from state.
     *
     * @return the shared immutable template
     */
    public BlockPatternTemplate buildTemplate() {
        return new BlockPatternTemplate(makePredicateArray(), structureDir,
                aisleRepetitions.toArray(new int[aisleRepetitions.size()][]),
                aisleChannelNames.toArray(new String[aisleChannelNames.size()]));
    }

    /**
     * Build the immutable template with an externally-specified center offset.
     * Use this for multi-piece sub-patterns that don't have a selfPredicate() center marker.
     *
     * @param centerOffset the center offset [x, y, z, minZ, maxZ]
     * @return the shared immutable template
     */
    public BlockPatternTemplate buildTemplate(@NotNull int[] centerOffset) {
        return new BlockPatternTemplate(makePredicateArray(), structureDir,
                aisleRepetitions.toArray(new int[aisleRepetitions.size()][]),
                aisleChannelNames.toArray(new String[aisleChannelNames.size()]),
                centerOffset);
    }

    /**
     * Build a BlockPattern (template + state combined) for backward compatibility.
     *
     * @deprecated Use {@link #buildTemplate()} and create per-instance state via
     *             {@link BlockPatternTemplate#createState()} for better memory efficiency.
     */
    @Deprecated
    public BlockPattern build() {
        return new BlockPattern(buildTemplate());
    }

    private TraceabilityPredicate[][][] makePredicateArray() {
        this.checkMissingPredicates();
        TraceabilityPredicate[][][] predicate = (TraceabilityPredicate[][][]) Array
                .newInstance(TraceabilityPredicate.class, this.depth.size(), this.aisleHeight, this.rowWidth);

        for (int i = 0; i < this.depth.size(); ++i) {
            for (int j = 0; j < this.aisleHeight; ++j) {
                for (int k = 0; k < this.rowWidth; ++k) {
                    predicate[i][j][k] = this.symbolMap.get(this.depth.get(i)[j].charAt(k));
                }
            }
        }

        return predicate;
    }

    private void checkMissingPredicates() {
        List<Character> list = new ArrayList<>();

        for (Entry<Character, TraceabilityPredicate> entry : this.symbolMap.entrySet()) {
            if (entry.getValue() == null) {
                list.add(entry.getKey());
            }
        }

        if (!list.isEmpty()) {
            throw new IllegalStateException("Predicates for character(s) " + COMMA_JOIN.join(list) + " are missing");
        }
    }
}
