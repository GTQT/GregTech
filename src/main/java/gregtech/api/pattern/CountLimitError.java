package gregtech.api.pattern;

import net.minecraft.client.resources.I18n;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Typed replacement for {@code TraceabilityPredicate.SinglePredicateError}.
 *
 * <p>Reports a count limit violation on a structure element. The candidate
 * ItemStack preview is sourced from {@link PatternError#getCandidates()},
 * which already reads the {@code StructureElementPreviewEntry} attached to
 * the current {@link BlockWorldState}, so this error does not need to hold a
 * reference to any legacy predicate.
 */
public class CountLimitError extends PatternError {

    /** Limit kind reported by this error. */
    public enum Kind {
        /** Maximum global count exceeded. */
        MAX_GLOBAL(0),
        /** Minimum global count not met. */
        MIN_GLOBAL(1),
        /** Maximum layer count exceeded. */
        MAX_LAYER(2),
        /** Minimum layer count not met. */
        MIN_LAYER(3);

        private final int index;

        Kind(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }
    }

    private final Kind kind;
    private final int limit;

    public CountLimitError(@NotNull Kind kind, int limit) {
        this.kind = kind;
        this.limit = limit;
    }

    public Kind getKind() {
        return kind;
    }

    public int getLimit() {
        return limit;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public String getErrorInfo() {
        return I18n.format("gregtech.multiblock.pattern.error.limited." + kind.getIndex(), limit);
    }
}
