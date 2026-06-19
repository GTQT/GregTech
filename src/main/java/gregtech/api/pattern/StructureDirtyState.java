package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Controller-local pending dirty roots accumulated by world/external events.
 */
public final class StructureDirtyState {

    @NotNull
    private final LinkedHashSet<String> roots = new LinkedHashSet<>();

    public synchronized boolean addRoot(@NotNull String pieceName) {
        return roots.add(pieceName);
    }

    public synchronized boolean addRoots(@NotNull Iterable<String> pieceNames) {
        boolean changed = false;
        for (String pieceName : pieceNames) {
            changed |= roots.add(pieceName);
        }
        return changed;
    }

    @NotNull
    public synchronized Set<String> snapshot() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(roots));
    }

    @NotNull
    public synchronized Set<String> consume() {
        Set<String> result = snapshot();
        roots.clear();
        return result;
    }

    public synchronized boolean isEmpty() {
        return roots.isEmpty();
    }

    public synchronized void clear() {
        roots.clear();
    }
}
