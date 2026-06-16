package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Typed handle for a named structure piece.
 *
 * <p>The runtime still serializes and indexes pieces by name, but formation
 * callbacks should prefer carrying this handle instead of repeating raw string
 * keys at read sites.
 */
public final class StructurePieceKey {

    @NotNull
    private final String name;

    private StructurePieceKey(@NotNull String name) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Structure piece key cannot be empty");
        }
        this.name = name;
    }

    @NotNull
    public static StructurePieceKey of(@NotNull String name) {
        return new StructurePieceKey(name);
    }

    @NotNull
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StructurePieceKey)) return false;
        StructurePieceKey that = (StructurePieceKey) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
