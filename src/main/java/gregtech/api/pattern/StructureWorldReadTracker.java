package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Debug-only per-thread accounting for structure block-access reads.
 */
final class StructureWorldReadTracker {

    private static final ThreadLocal<Deque<Counter>> COUNTERS =
            ThreadLocal.withInitial(ArrayDeque::new);

    private StructureWorldReadTracker() {}

    @NotNull
    static Scope begin() {
        Counter counter = new Counter();
        COUNTERS.get().push(counter);
        return new Scope(counter);
    }

    static void recordBlockStateRead() {
        Counter counter = current();
        if (counter != null) {
            counter.blockStateReads++;
        }
    }

    static void recordTileEntityRead() {
        Counter counter = current();
        if (counter != null) {
            counter.tileEntityReads++;
        }
    }

    @NotNull
    static Metrics emptyMetrics() {
        return new Metrics(0, 0);
    }

    private static Counter current() {
        Deque<Counter> counters = COUNTERS.get();
        return counters.isEmpty() ? null : counters.peek();
    }

    static final class Scope implements AutoCloseable {

        @NotNull
        private final Counter counter;
        private boolean closed;

        private Scope(@NotNull Counter counter) {
            this.counter = counter;
        }

        @NotNull
        Metrics finish() {
            close();
            return new Metrics(counter.blockStateReads, counter.tileEntityReads);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            Deque<Counter> counters = COUNTERS.get();
            if (counters.isEmpty() || counters.peek() != counter) {
                throw new IllegalStateException("Structure world-read scopes closed out of order");
            }
            counters.pop();
            if (counters.isEmpty()) {
                COUNTERS.remove();
            }
            closed = true;
        }
    }

    static final class Metrics {

        private final int blockStateReads;
        private final int tileEntityReads;

        private Metrics(int blockStateReads, int tileEntityReads) {
            this.blockStateReads = blockStateReads;
            this.tileEntityReads = tileEntityReads;
        }

        int getBlockStateReads() {
            return blockStateReads;
        }

        int getTileEntityReads() {
            return tileEntityReads;
        }

        int getTotalReads() {
            return blockStateReads + tileEntityReads;
        }

        @Override
        public String toString() {
            return "blockStates=" + blockStateReads
                    + ", tileEntities=" + tileEntityReads
                    + ", total=" + getTotalReads();
        }
    }

    private static final class Counter {

        private int blockStateReads;
        private int tileEntityReads;
    }
}
