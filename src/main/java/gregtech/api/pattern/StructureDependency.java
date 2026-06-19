package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Declared input dependency for a structure condition or future element input.
 */
public final class StructureDependency {

    public enum Kind {
        PIECE,
        EXTERNAL
    }

    @NotNull
    private final Kind kind;
    @Nullable
    private final String pieceName;
    @Nullable
    private final StructureExternalDependencyKey<?> externalKey;
    @NotNull
    private final Set<PieceDependencyAspect> aspects;
    @NotNull
    private final String reason;

    @NotNull
    public static StructureDependency piece(
            @NotNull String pieceName,
            @NotNull PieceDependencyAspect... aspects) {
        return piece(pieceName, "declared-piece-dependency", aspectSet(
                aspects, PieceDependencyAspect.ANY_RESULT));
    }

    @NotNull
    public static StructureDependency piece(
            @NotNull String pieceName,
            @NotNull Collection<PieceDependencyAspect> aspects) {
        return piece(pieceName, "declared-piece-dependency", aspects);
    }

    @NotNull
    public static StructureDependency piece(
            @NotNull String pieceName,
            @NotNull String reason,
            @NotNull Collection<PieceDependencyAspect> aspects) {
        if (pieceName.isEmpty()) {
            throw new IllegalArgumentException("Piece dependency name must be non-empty");
        }
        return new StructureDependency(
                Kind.PIECE, pieceName, null,
                aspectSet(aspects, PieceDependencyAspect.ANY_RESULT), reason);
    }

    @NotNull
    public static StructureDependency external(
            @NotNull StructureExternalDependencyKey<?> key,
            @NotNull PieceDependencyAspect... aspects) {
        return external(key, "declared-external-dependency", aspectSet(
                aspects, PieceDependencyAspect.CONTROLLER_STATE));
    }

    @NotNull
    public static StructureDependency external(
            @NotNull StructureExternalDependencyKey<?> key,
            @NotNull Collection<PieceDependencyAspect> aspects) {
        return external(key, "declared-external-dependency", aspects);
    }

    @NotNull
    public static StructureDependency external(
            @NotNull StructureExternalDependencyKey<?> key,
            @NotNull String reason,
            @NotNull Collection<PieceDependencyAspect> aspects) {
        return new StructureDependency(
                Kind.EXTERNAL, null, key,
                aspectSet(aspects, PieceDependencyAspect.CONTROLLER_STATE), reason);
    }

    private StructureDependency(
            @NotNull Kind kind,
            @Nullable String pieceName,
            @Nullable StructureExternalDependencyKey<?> externalKey,
            @NotNull Set<PieceDependencyAspect> aspects,
            @NotNull String reason) {
        this.kind = kind;
        this.pieceName = pieceName;
        this.externalKey = externalKey;
        this.aspects = aspects;
        this.reason = reason;
    }

    @NotNull
    public Kind getKind() {
        return kind;
    }

    @Nullable
    public String getPieceName() {
        return pieceName;
    }

    @Nullable
    public StructureExternalDependencyKey<?> getExternalKey() {
        return externalKey;
    }

    @NotNull
    public Set<PieceDependencyAspect> getAspects() {
        return aspects;
    }

    @NotNull
    public String getReason() {
        return reason;
    }

    @NotNull
    private static Set<PieceDependencyAspect> aspectSet(
            @NotNull PieceDependencyAspect[] aspects,
            @NotNull PieceDependencyAspect defaultAspect) {
        if (aspects.length == 0) {
            return Collections.unmodifiableSet(EnumSet.of(defaultAspect));
        }
        EnumSet<PieceDependencyAspect> set = EnumSet.noneOf(PieceDependencyAspect.class);
        Collections.addAll(set, aspects);
        return Collections.unmodifiableSet(set);
    }

    @NotNull
    private static Set<PieceDependencyAspect> aspectSet(
            @NotNull Collection<PieceDependencyAspect> aspects,
            @NotNull PieceDependencyAspect defaultAspect) {
        if (aspects.isEmpty()) {
            return Collections.unmodifiableSet(EnumSet.of(defaultAspect));
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(aspects));
    }

    @Override
    public String toString() {
        if (kind == Kind.PIECE) {
            return "piece:" + pieceName + aspects;
        }
        return "external:" + externalKey + aspects;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof StructureDependency)) return false;
        StructureDependency other = (StructureDependency) obj;
        return kind == other.kind
                && Objects.equals(pieceName, other.pieceName)
                && Objects.equals(externalKey, other.externalKey)
                && aspects.equals(other.aspects)
                && reason.equals(other.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, pieceName, externalKey, aspects, reason);
    }
}
