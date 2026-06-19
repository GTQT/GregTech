package gregtech.common.metatileentities.multi.electric.godforge;

import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.StructureOperationState;
import gregtech.api.pattern.casing.StructureChannelValues;
import gregtech.api.pattern.element.FormedStructureMetadata;

import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GodforgeRingMatchPolicyTest {

    @Test
    void defaultsToOneRingWhenOnlyFirstRingIsMatched() {
        assertEquals(1, GodforgeRingMatchPolicy.getFormedRingAmount(viewWithPieces("first_ring")));
        assertEquals(1, GodforgeRingMatchPolicy.getFormedRingAmount(viewWithPieces("first_ring_air")));
    }

    @Test
    void commitsSecondRingFromPhysicalOrRenderedPiece() {
        assertEquals(2, GodforgeRingMatchPolicy.getFormedRingAmount(viewWithPieces("second_ring")));
        assertEquals(2, GodforgeRingMatchPolicy.getFormedRingAmount(viewWithPieces("second_ring_air")));
    }

    @Test
    void commitsThirdRingFromPhysicalOrRenderedPiece() {
        assertEquals(3, GodforgeRingMatchPolicy.getFormedRingAmount(viewWithPieces("third_ring")));
        assertEquals(3, GodforgeRingMatchPolicy.getFormedRingAmount(viewWithPieces("second_ring", "third_ring_air")));
    }

    private static FormedStructureView viewWithPieces(String... pieceNames) {
        Map<String, BlockPos> pieceCenters = new HashMap<>();
        for (String pieceName : pieceNames) {
            pieceCenters.put(pieceName, BlockPos.ORIGIN);
        }
        FormedStructureMetadata metadata = FormedStructureMetadata.fromCheckResult(
                Collections.emptyMap(), Collections.emptyMap(), pieceCenters);
        return FormedStructureView.legacy(
                metadata, new StructureChannelValues(), new StructureOperationState(),
                new PatternMatchContext(), false);
    }
}
