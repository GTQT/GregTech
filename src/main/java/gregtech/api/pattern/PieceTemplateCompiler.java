package gregtech.api.pattern;

import gregtech.api.util.RelativeDirection;
import gregtech.api.pattern.element.CompiledStructureElement;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.impl.AnyElement;

import com.google.common.base.Joiner;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core algorithm for compiling a single structure piece (flat-string aisles +
 * symbol mappings) into a {@link PieceTemplate}.
 *
 * <p>This class owns the canonical piece-compilation logic. The new structure
 * system ({@link gregtech.api.pattern.element.StructureCompiler}) can compile
 * an {@link gregtech.api.pattern.element.IStructurePiece} into a template
 * directly through this compiler.
 *
 * <p>Typical usage:
 * <pre>{@code
 * PieceTemplateCompiler c = new PieceTemplateCompiler(RIGHT, UP, BACK);
 * c.aisle("XXX", "X#X", "XXX")
 *  .aisleRepeated(7, "YEY", "Y#Y", "YEY")
 *  .whereElement('X', someElement)
 *  .whereElement('#', Elements.air());
 * PieceTemplate tpl = c.buildPieceTemplate();
 * }</pre>
 *
 * <p>Thread safety: instances are <b>not</b> thread-safe; each piece compilation
 * should use its own instance.
 */
public final class PieceTemplateCompiler {

    private static final Joiner COMMA_JOIN = Joiner.on(",");

    private final RelativeDirection[] structureDir;
    private final List<String[]> depth = new ArrayList<>();
    private final List<int[]> aisleRepetitions = new ArrayList<>();
    private final List<String> aisleChannelNames = new ArrayList<>();
    private final Map<Character, CompiledStructureElement<?>> elementMap = new HashMap<>();
    private int aisleHeight;
    private int rowWidth;

    /**
     * Create a new compiler bound to the given structure directions.
     *
     * @param charDir   direction for characters within a row (typically RIGHT)
     * @param stringDir direction for rows within an aisle (typically UP)
     * @param aisleDir  direction for aisles (typically BACK)
     * @throws IllegalArgumentException if the three directions do not cover three
     *                                  distinct axes
     */
    public PieceTemplateCompiler(@NotNull RelativeDirection charDir,
                                 @NotNull RelativeDirection stringDir,
                                 @NotNull RelativeDirection aisleDir) {
        this.structureDir = new RelativeDirection[]{charDir, stringDir, aisleDir};
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
        this.elementMap.put(' ', AnyElement.INSTANCE.compile());
    }

    /**
     * Adds a single aisle to this piece. Equivalent to
     * {@code aisleRepeated(1, aisle)}.
     */
    @NotNull
    public PieceTemplateCompiler aisle(@NotNull String... aisle) {
        return addAisle(1, 1, aisle);
    }

    /**
     * Adds an aisle that is repeated an exact number of times.
     *
     * <p>Use a repeatable piece for variable min/max repetition. This method is
     * for fixed-size structures that would otherwise duplicate identical aisle
     * slices in the declaration.
     *
     * <p>Validates that the new aisle matches the previously recorded
     * height/width, and registers any previously-unseen characters as null
     * elements (which must be filled in by
     * {@link #whereElement(char, IStructureElement)} before
     * {@link #buildPieceTemplate()}).
     *
     * @param exactCount exact number of repetitions
     * @param aisle      the flat row strings for this aisle
     * @return this compiler
     * @throws IllegalArgumentException if aisle is empty, or its dimensions do
     *                                  not match previously added aisles, or
     *                                  exactCount is less than 1
     */
    @NotNull
    public PieceTemplateCompiler aisleRepeated(int exactCount, @NotNull String... aisle) {
        validateExactRepeatCount(exactCount);
        return addAisle(exactCount, exactCount, aisle);
    }

    private PieceTemplateCompiler addAisle(int minRepeat, int maxRepeat, @NotNull String... aisle) {
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
                        if (!this.elementMap.containsKey(c0)) {
                            this.elementMap.put(c0, null);
                        }
                    }
                }

                this.depth.add(aisle);
                if (minRepeat > maxRepeat)
                    throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
                aisleRepetitions.add(new int[]{minRepeat, maxRepeat});
                aisleChannelNames.add(null);
                return this;
            }
        } else {
            throw new IllegalArgumentException("Empty pattern for aisle");
        }
    }

    /**
     * Set the last added aisle's repeat range.
     */
    @NotNull
    public PieceTemplateCompiler setRepeatable(int minRepeat, int maxRepeat) {
        if (minRepeat > maxRepeat)
            throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
        aisleRepetitions.set(aisleRepetitions.size() - 1, new int[]{minRepeat, maxRepeat});
        return this;
    }

    /**
     * Set the last added aisle's repeat range and associated channel name.
     */
    @NotNull
    public PieceTemplateCompiler setRepeatable(int minRepeat, int maxRepeat, @NotNull String channelName) {
        if (minRepeat > maxRepeat)
            throw new IllegalArgumentException("Lower bound of repeat counting must smaller than upper bound!");
        aisleRepetitions.set(aisleRepetitions.size() - 1, new int[]{minRepeat, maxRepeat});
        aisleChannelNames.set(aisleChannelNames.size() - 1, channelName);
        return this;
    }

    /**
     * Convenience overload of {@link #setRepeatable(int, int)} with equal
     * bounds (a fixed repeat count).
     */
    @NotNull
    public PieceTemplateCompiler setRepeatable(int repeatCount) {
        validateExactRepeatCount(repeatCount);
        return setRepeatable(repeatCount, repeatCount);
    }

    private static void validateExactRepeatCount(int exactCount) {
        if (exactCount < 1) {
            throw new IllegalArgumentException("Exact repeat count must be at least 1!");
        }
    }

    /**
     * Map a symbol to the canonical element contract. The element is compiled
     * once and stored as a {@link CompiledStructureElement} for runtime use.
     */
    @NotNull
    public PieceTemplateCompiler whereElement(char symbol, @NotNull IStructureElement<?> element) {
        CompiledStructureElement<?> compiled = element.compile();
        this.elementMap.put(symbol, compiled);
        return this;
    }

    @NotNull
    public PieceTemplateCompiler whereElement(@NotNull String symbol,
                                              @NotNull IStructureElement<?> element) {
        if (symbol.length() == 1) {
            return whereElement(symbol.charAt(0), element);
        }
        throw new IllegalArgumentException(
                String.format("Symbol \"%s\" is invalid! It must be exactly one character!", symbol));
    }

    /**
     * Build the canonical {@link PieceTemplate} directly, without going through
     * an intermediate wrapper. Use this from the StructureDefinition compile
     * path: the resulting {@code PieceTemplate} can be wrapped directly in a
     * {@link StructurePiece}.
     */
    @NotNull
    public PieceTemplate buildPieceTemplate() {
        return new PieceTemplate(makeElementArray(), structureDir,
                aisleRepetitions.toArray(new int[aisleRepetitions.size()][]),
                aisleChannelNames.toArray(new String[aisleChannelNames.size()]),
                null, null);
    }

    /**
     * Build the canonical {@link PieceTemplate} directly with an externally-specified
     * center offset. Use this for multi-piece sub-patterns that don't have a
     * self-predicate center marker.
     *
     * @param centerOffset the center offset [x, y, z, minZ, maxZ]
     * @return the canonical piece IR
     */
    @NotNull
    public PieceTemplate buildPieceTemplate(@NotNull int[] centerOffset) {
        return new PieceTemplate(makeElementArray(), structureDir,
                aisleRepetitions.toArray(new int[aisleRepetitions.size()][]),
                aisleChannelNames.toArray(new String[aisleChannelNames.size()]),
                centerOffset, null);
    }

    /**
     * Build the canonical {@link PieceTemplate} directly with an externally-specified
     * center offset and an auto-generated structure description.
     */
    @NotNull
    public PieceTemplate buildPieceTemplate(@NotNull int[] centerOffset,
                                            @Nullable List<String> structureDescription) {
        return new PieceTemplate(makeElementArray(), structureDir,
                aisleRepetitions.toArray(new int[aisleRepetitions.size()][]),
                aisleChannelNames.toArray(new String[aisleChannelNames.size()]),
                centerOffset,
                (structureDescription == null || structureDescription.isEmpty())
                        ? null : structureDescription);
    }

    /**
     * Materialize the 3D element array from the recorded aisles and symbol
     * map.
     */
    @NotNull
    public IStructureElement<?>[][][] makeElementArray() {
        this.checkMissingPredicates();
        IStructureElement<?>[][][] elements = (IStructureElement<?>[][][]) Array
                .newInstance(IStructureElement.class, this.depth.size(), this.aisleHeight, this.rowWidth);

        for (int i = 0; i < this.depth.size(); ++i) {
            for (int j = 0; j < this.aisleHeight; ++j) {
                for (int k = 0; k < this.rowWidth; ++k) {
                    elements[i][j][k] = this.elementMap.get(this.depth.get(i)[j].charAt(k));
                }
            }
        }
        return elements;
    }

    /**
     * Throw {@link IllegalStateException} if any character in the recorded
     * aisles has not been mapped via {@link #whereElement(char, IStructureElement)}.
     */
    public void checkMissingPredicates() {
        List<Character> list = new ArrayList<>();

        for (Map.Entry<Character, CompiledStructureElement<?>> entry : this.elementMap.entrySet()) {
            if (entry.getValue() == null) {
                list.add(entry.getKey());
            }
        }

        if (!list.isEmpty()) {
            throw new IllegalStateException("Predicates for character(s) " + COMMA_JOIN.join(list) + " are missing");
        }
    }

    /**
     * @return the structure direction triple [charDir, stringDir, aisleDir]
     */
    @NotNull
    public RelativeDirection[] getStructureDir() {
        return structureDir;
    }

}
