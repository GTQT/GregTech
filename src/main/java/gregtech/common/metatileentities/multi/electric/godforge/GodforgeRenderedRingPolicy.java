package gregtech.common.metatileentities.multi.electric.godforge;

final class GodforgeRenderedRingPolicy {

    private GodforgeRenderedRingPolicy() {}

    static boolean canUseRenderedRingTemplate(boolean ringCleared,
                                              boolean renderActive,
                                              boolean recoveringRenderedStructure,
                                              boolean rendererOwnedByThisController,
                                              boolean foreignRendererLoaded,
                                              int internalBattery) {
        if (!ringCleared) return false;
        if (recoveringRenderedStructure) return internalBattery > 0 && !foreignRendererLoaded;
        return renderActive && rendererOwnedByThisController;
    }
}
