package gregtech.api.util;

import gregtech.api.capability.IHeatable;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeatHatchUtilTest {

    @Test
    void removingHeatChargesTheWholeAmountEvenIfSomeHatchesAreEmpty() {
        TestHatch a = new TestHatch(100L, 1000L);
        TestHatch empty = new TestHatch(0L, 1000L);

        long changed = HeatHatchUtil.distribute(Arrays.asList(a, empty), -100L);

        assertEquals(-100L, changed);
        assertEquals(0L, a.getHeatStored(), "the only hatch that has heat must pay the whole cost");
        assertEquals(0L, empty.getHeatStored());
    }

    @Test
    void removingHeatRedistributesTheShareOfAnExhaustedHatch() {
        TestHatch a = new TestHatch(100L, 1000L);
        TestHatch b = new TestHatch(1L, 1000L);

        long changed = HeatHatchUtil.distribute(Arrays.asList(a, b), -100L);

        assertEquals(-100L, changed);
        assertEquals(1L, a.getHeatStored());
        assertEquals(0L, b.getHeatStored());
    }

    @Test
    void removingHeatCannotTakeMoreThanTheTotalStoredHeat() {
        TestHatch a = new TestHatch(50L, 1000L);
        TestHatch b = new TestHatch(0L, 1000L);

        long changed = HeatHatchUtil.distribute(Arrays.asList(a, b), -100L);

        assertEquals(-50L, changed);
        assertEquals(0L, a.getHeatStored());
    }

    @Test
    void addingHeatRedistributesTheShareOfAFullHatch() {
        TestHatch full = new TestHatch(100L, 100L);
        TestHatch empty = new TestHatch(0L, 100L);

        long changed = HeatHatchUtil.distribute(Arrays.asList(full, empty), 150L);

        assertEquals(100L, changed, "the full hatch has no room, the empty one accepts everything it can");
        assertEquals(100L, full.getHeatStored());
        assertEquals(100L, empty.getHeatStored());
    }

    @Test
    void addingHeatCannotExceedTheRemainingCapacity() {
        TestHatch full = new TestHatch(100L, 100L);
        TestHatch half = new TestHatch(50L, 100L);

        long changed = HeatHatchUtil.distribute(Arrays.asList(full, half), 100L);

        assertEquals(50L, changed);
        assertEquals(100L, full.getHeatStored());
        assertEquals(100L, half.getHeatStored());
    }

    @Test
    void hatchesThatRejectHeatAreSkippedWithoutLosingTheAmount() {
        TestHatch working = new TestHatch(100L, 1000L);
        IHeatable broken = new IHeatable() {

            @Override
            public long transferHeat(long heatToTransfer, int sourceTemperature) {
                return 0L;
            }

            @Override
            public long changeHeat(long heatToAdd) {
                return 0L;
            }

            @Override
            public int getTemperature() {
                return 293;
            }

            @Override
            public void setTemperature(int temperature) {}

            @Override
            public int getMaxTemperature() {
                return 1000;
            }

            @Override
            public void setMaxTemperature(int maxTemperature) {}

            @Override
            public long getHeatCapacity() {
                return 1000L;
            }

            @Override
            public long getHeatStored() {
                return 10L;
            }

            @Override
            public boolean canAcceptHeat() {
                return true;
            }
        };

        long changed = HeatHatchUtil.distribute(Arrays.asList(working, broken), -50L);

        assertEquals(-50L, changed);
        assertEquals(50L, working.getHeatStored());
    }

    @Test
    void emptyOrMissingHatchesAreNoOps() {
        assertEquals(0L, HeatHatchUtil.distribute(null, -50L));
        assertEquals(0L, HeatHatchUtil.distribute(Collections.emptyList(), -50L));
        assertEquals(0L, HeatHatchUtil.distribute(Collections.singletonList(new TestHatch(10L, 100L)), 0L));
    }

    private static final class TestHatch implements IHeatable {

        private final long capacity;
        private final long maxTemperature;
        private long stored;
        private int temperature = 293;

        private TestHatch(long stored, long capacity) {
            this.stored = stored;
            this.capacity = capacity;
            this.maxTemperature = 1000L;
        }

        @Override
        public long transferHeat(long heatToTransfer, int sourceTemperature) {
            return 0L;
        }

        @Override
        public long changeHeat(long heatToAdd) {
            long before = stored;
            stored = Math.max(0L, Math.min(capacity, stored + heatToAdd));
            return stored - before;
        }

        @Override
        public int getTemperature() {
            return temperature;
        }

        @Override
        public void setTemperature(int temperature) {
            this.temperature = temperature;
        }

        @Override
        public int getMaxTemperature() {
            return (int) maxTemperature;
        }

        @Override
        public void setMaxTemperature(int maxTemperature) {}

        @Override
        public long getHeatCapacity() {
            return capacity;
        }

        @Override
        public long getHeatStored() {
            return stored;
        }

        @Override
        public boolean canAcceptHeat() {
            return true;
        }
    }
}
