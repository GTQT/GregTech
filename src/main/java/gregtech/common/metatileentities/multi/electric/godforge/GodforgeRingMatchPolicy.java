package gregtech.common.metatileentities.multi.electric.godforge;

import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.StructurePieceKey;

import org.jetbrains.annotations.NotNull;

final class GodforgeRingMatchPolicy {

    private static final StructurePieceKey SECOND_RING = StructurePieceKey.of("second_ring");
    private static final StructurePieceKey SECOND_RING_AIR = StructurePieceKey.of("second_ring_air");
    private static final StructurePieceKey THIRD_RING = StructurePieceKey.of("third_ring");
    private static final StructurePieceKey THIRD_RING_AIR = StructurePieceKey.of("third_ring_air");

    private GodforgeRingMatchPolicy() {}

    static int getFormedRingAmount(@NotNull FormedStructureView formed) {
        int rings = 1;
        if (isRingPieceMatched(formed, SECOND_RING, SECOND_RING_AIR)) rings = Math.max(rings, 2);
        if (isRingPieceMatched(formed, THIRD_RING, THIRD_RING_AIR)) rings = Math.max(rings, 3);
        return rings;
    }

    private static boolean isRingPieceMatched(@NotNull FormedStructureView formed,
                                              @NotNull StructurePieceKey physicalPiece,
                                              @NotNull StructurePieceKey airPiece) {
        return formed.getPieceCenter(physicalPiece) != null
                || formed.getPieceCenter(airPiece) != null;
    }
}
