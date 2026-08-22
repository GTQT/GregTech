package gregtech.common.items.behaviors.multiblock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiblockToolModeTest {

    @Test
    void defaultsLegacyRemoverMetadataToRemoveMode() {
        assertEquals(MultiblockToolMode.REMOVE, MultiblockToolMode.defaultForMetadata(1005));
        assertEquals(MultiblockToolMode.PROJECT, MultiblockToolMode.defaultForMetadata(1004));
        assertEquals(MultiblockToolMode.PROJECT, MultiblockToolMode.defaultForMetadata(1006));
    }

    @Test
    void modesCycleBackToProject() {
        assertEquals(MultiblockToolMode.PROJECT, MultiblockToolMode.MOVE.next());
        assertEquals(MultiblockToolMode.REMOVE, MultiblockToolMode.PROJECT.next());
        assertEquals(MultiblockToolMode.MOVE, MultiblockToolMode.REMOVE.next());
    }
}
