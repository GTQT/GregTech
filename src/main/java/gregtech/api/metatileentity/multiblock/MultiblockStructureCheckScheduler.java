package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.StructureFailureTrace;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.StructureTrace;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.util.GTLog;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.ConfigHolder;

final class MultiblockStructureCheckScheduler {

    /** Interval (in ticks) before falling back to a main-thread check when async check has not formed. */
    private static final int ASYNC_FALLBACK_INTERVAL = 100;

    private int asyncCheckFallbackTicks;

    void doStructureCheck(MultiblockControllerBase controller) {
        // First tick always performs a full structure check on main thread.
        if (controller.isFirstTick()) {
            controller.checkStructurePattern();
            return;
        }

        if (tryEventDrivenRecheck(controller)) {
            return;
        }

        if (tryAsyncCheck(controller)) {
            return;
        }

        int interval = controller.isWorkingForStructureCheck()
                ? controller.getStructureCheckIntervalWorking()
                : controller.getStructureCheckIntervalStandby();
        if (controller.getOffsetTimer() % interval == 0) {
            controller.checkStructurePattern();
        }
    }

    void resetAsyncFallbackTicks() {
        asyncCheckFallbackTicks = 0;
    }

    private boolean tryEventDrivenRecheck(MultiblockControllerBase controller) {
        if (!ConfigHolder.machines.enableEventDrivenStructureCheck
                || !controller.isStructureFormed()
                || controller.getWorld() == null
                || controller.getWorld() instanceof DummyWorld) {
            return false;
        }

        MultiblockWorldData worldData = MultiblockWorldData.get(controller.getWorld());
        if (!worldData.isRegistered(controller)) {
            return false;
        }

        if (worldData.hasPendingRecheck(controller, controller.getWorld().getTotalWorldTime())) {
            if (ConfigHolder.machines.debugStructureCheck) {
                GTLog.logger.debug("[StructureCheck] Event-driven recheck triggered for {}",
                        controller.getMetaName());
            }
            if (controller.multiPiecePattern != null) {
                controller.checkMultiPieceStructure();
            } else {
                controller.checkStructurePattern();
            }
        }
        return true;
    }

    private boolean tryAsyncCheck(MultiblockControllerBase controller) {
        AsyncStructureChecker checker = AsyncStructureChecker.getInstance();
        if (!ConfigHolder.machines.enableAsyncStructureCheck
                || !controller.allowsAsyncStructureCheck()
                || controller.isStructureFormed()
                || controller.getWorld() == null
                || controller.getWorld().isRemote
                || controller.getWorld() instanceof DummyWorld
                || !checker.isRunning()) {
            checker.unregister(controller);
            return false;
        }
        if (!controller.getStructureDefinition().supportsElementCapability(
                StructureElementCapability.SNAPSHOT_MATCH)) {
            recordCapabilityUnsupported(controller, StructureElementCapability.SNAPSHOT_MATCH);
            checker.unregister(controller);
            return false;
        }

        checker.registerForAsyncCheck(controller);
        asyncCheckFallbackTicks++;
        if (asyncCheckFallbackTicks >= ASYNC_FALLBACK_INTERVAL) {
            asyncCheckFallbackTicks = 0;
            if (ConfigHolder.machines.debugStructureCheck) {
                GTLog.logger.debug("[StructureCheck] Async fallback triggered for {}", controller.getMetaName());
            }
            controller.checkStructurePattern();
            if (controller.isStructureFormed()) {
                checker.unregister(controller);
            }
        }
        return true;
    }

    private static void recordCapabilityUnsupported(
            MultiblockControllerBase controller,
            StructureElementCapability capability) {
        StructureRuntime runtime = controller.getStructureRuntime();
        if (runtime == null) {
            return;
        }
        runtime.recordLifecycleFailure(StructureTrace.lifecycleFailure(
                controller,
                "definition",
                "ASYNC",
                StructureFailureTrace.Kind.CAPABILITY_UNSUPPORTED,
                "Structure definition does not support " + capability));
    }
}
