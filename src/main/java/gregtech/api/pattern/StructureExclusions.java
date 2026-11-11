package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Optional-piece exclusion, carried inside the ordinary channel-value map so preview and build use the same
 * selection without a new packet or request type.
 *
 * <p>The key format is {@code @exclude:<visible tooling index>} (1-based, the same index the preview
 * configuration tree and {@link MultiPiecePattern#getToolingPieceList()} use). A value of {@code 0} means
 * "included"; any other value means "excluded". Excluded pieces are skipped by both
 * {@link MultiPiecePreviewAssembler} and {@link StructureBuildOperationService}, so what the preview shows is
 * exactly what the builder places.
 */
public final class StructureExclusions {

    public static final String PREFIX = "@exclude:";

    private StructureExclusions() {}

    /**
     * @param toolingPieceIndex 1-based visible tooling piece index
     * @return channel key holding this piece's exclusion flag
     */
    @NotNull
    public static String key(int toolingPieceIndex) {
        return PREFIX + toolingPieceIndex;
    }

    /** @return whether {@code name} is an exclusion channel key */
    public static boolean isExclusionKey(@Nullable String name) {
        return name != null && name.startsWith(PREFIX);
    }

    /** @return the 1-based tooling piece index encoded in the key, or {@code -1} when the key is malformed. */
    public static int indexOf(@Nullable String name) {
        if (!isExclusionKey(name)) {
            return -1;
        }
        try {
            int index = Integer.parseInt(name.substring(PREFIX.length()));
            return index > 0 ? index : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /**
     * @param channelValues    channel map shared by preview and build
     * @param toolingPieceIndex 1-based visible tooling piece index
     * @return whether that piece is switched off
     */
    public static boolean isExcluded(@Nullable Map<String, Integer> channelValues, int toolingPieceIndex) {
        if (channelValues == null || toolingPieceIndex <= 0) {
            return false;
        }
        Integer value = channelValues.get(key(toolingPieceIndex));
        return value != null && value != 0;
    }
}
