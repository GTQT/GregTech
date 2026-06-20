package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class StructureItemSourceRegistry {

    private static final List<StructureItemSource> SOURCES = new ArrayList<>();

    private StructureItemSourceRegistry() {}

    public static void register(@NotNull StructureItemSource source) {
        SOURCES.add(Objects.requireNonNull(source));
    }

    static List<StructureItemSource> getSources() {
        return Collections.unmodifiableList(SOURCES);
    }
}
