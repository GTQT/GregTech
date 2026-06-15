package gregtech.api.metatileentity.multiblock;

import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MultiblockWorldDataChangeSnapshotTest {

    @Test
    void onlyChangesInCoveredChunksInvalidateTheSnapshot() {
        MultiblockWorldData data = new MultiblockWorldData();
        BlockPos min = new BlockPos(0, 0, 0);
        BlockPos max = new BlockPos(31, 10, 31);
        MultiblockWorldData.ChangeSnapshot snapshot =
                data.captureChangeSnapshot(min, max);

        data.onBlockChanged(new BlockPos(64, 0, 64), 1);
        assertTrue(data.isChangeSnapshotCurrent(snapshot));

        data.onBlockChanged(new BlockPos(16, 0, 16), 2);
        assertFalse(data.isChangeSnapshotCurrent(snapshot));
        assertTrue(data.isChangeSnapshotCurrent(
                data.captureChangeSnapshot(min, max)));
    }
}
