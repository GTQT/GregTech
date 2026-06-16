package gregtech.common.metatileentities.multi.electric.godforge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GodforgeRenderedRingTemplatePolicyTest {

    @Test
    void activeRendererRequiresCurrentControllerOwnership() {
        assertTrue(GodforgeRenderedRingPolicy.canUseRenderedRingTemplate(
                true, true, false, true, false, 100));

        assertFalse(GodforgeRenderedRingPolicy.canUseRenderedRingTemplate(
                true, true, false, false, false, 100));

        assertFalse(GodforgeRenderedRingPolicy.canUseRenderedRingTemplate(
                true, true, false, false, true, 100));
    }

    @Test
    void recoveryAllowsMissingRendererButRejectsForeignRenderer() {
        assertTrue(GodforgeRenderedRingPolicy.canUseRenderedRingTemplate(
                true, false, true, false, false, 100));

        assertFalse(GodforgeRenderedRingPolicy.canUseRenderedRingTemplate(
                true, false, true, false, true, 100));

        assertFalse(GodforgeRenderedRingPolicy.canUseRenderedRingTemplate(
                true, false, true, false, false, 0));
    }

    @Test
    void unclearedRingNeverUsesRenderedTemplate() {
        assertFalse(GodforgeRenderedRingPolicy.canUseRenderedRingTemplate(
                false, true, false, true, false, 100));

        assertFalse(GodforgeRenderedRingPolicy.canUseRenderedRingTemplate(
                false, false, true, false, false, 100));
    }
}
