package gregtech.api.pattern.casing;

import org.jetbrains.annotations.NotNull;

/**
 * Holds the result of registering a casing group together with its associated
 * structure channel. Returned by factory methods in {@link CasingDefinition}
 * that auto-create the channel.
 *
 * <p>Usage:
 * <pre>{@code
 * CasingRegistration reg = CasingDefinition.fromMap("my_group", true,
 *     myMap, MyStats::getTier, MyStats::getName);
 *
 * // Use the group in pattern definitions
 * DeclarativePatternBuilder.start()
 *     .tieredCasing('C', reg.group())
 *     .build();
 *
 * // Retrieve matched casing in formStructure
 * ICasing matched = reg.channel().getMatchedCasing(context);
 * }</pre>
 *
 * @see CasingDefinition#fromMap(String, boolean, java.util.Map, java.util.function.Function, java.util.function.Function)
 * @see CasingDefinition#fromIterable
 * @see CasingDefinition#fromEntries
 */
public class CasingRegistration {

    private final ICasingGroup group;
    private final StructureChannel channel;

    public CasingRegistration(@NotNull ICasingGroup group, @NotNull StructureChannel channel) {
        this.group = group;
        this.channel = channel;
    }

    @NotNull
    public ICasingGroup group() {
        return group;
    }

    @NotNull
    public StructureChannel channel() {
        return channel;
    }
}