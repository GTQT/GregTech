package gregtech.api.metatileentity.multiblock;

import gregtech.api.util.GTLog;
import gregtech.common.ConfigHolder;

final class MultiblockStructureCheckScheduler {

    private int asyncCheckFallbackTicks;

    void doStructureCheck(MultiblockControllerBase controller) {
        StructureSchedulerPolicy policy = controller.getStructureSchedulerPolicy();
        // First tick always performs a full structure check on main thread.
        if (policy.shouldRunFirstTickCheck(controller)) {
            controller.checkStructurePattern();
            return;
        }

        if (policy.allowsEventDriven(controller)) {
            controller.enqueueChangedStructureExternalDependencies();
        }

        if (tryEventDrivenRecheck(controller, policy)) {
            return;
        }

        if (tryAsyncCheck(controller, policy)) {
            return;
        }

        if (policy.shouldPollingCheck(controller)) {
            controller.checkStructurePattern();
        }
    }

    void resetAsyncFallbackTicks() {
        asyncCheckFallbackTicks = 0;
    }

    private boolean tryEventDrivenRecheck(MultiblockControllerBase controller,
                                          StructureSchedulerPolicy policy) {
        if (!policy.allowsEventDriven(controller)) {
            return false;
        }

        MultiblockWorldData.DirtyCheckLease decision = MultiblockWorldData.get(controller.getWorld())
                .consumeDirtyCheck(controller, controller.getWorld().getTotalWorldTime());
        if (!decision.isRegistered()) {
            return false;
        }

        if (decision.shouldCheck()) {
            if (ConfigHolder.machines.debugStructureCheck) {
                GTLog.logger.debug("[StructureCheck] Event-driven {} recheck triggered for {}",
                        decision.describeAction(), controller.getMetaName());
            }
            if (decision.shouldCheckIncremental()) {
                controller.checkIncrementalStructureGraph();
            } else if (decision.shouldCheckActiveGraph()) {
                controller.checkActiveStructureGraph();
            } else {
                if (ConfigHolder.machines.debugStructureCheck && decision.getFallbackReason() != null) {
                    GTLog.logger.debug("[StructureCheck] Event-driven full recheck fallback={} for {}",
                            decision.getFallbackReason(), controller.getMetaName());
                }
                controller.checkStructurePattern();
            }
        }
        return true;
    }

    private boolean tryAsyncCheck(MultiblockControllerBase controller,
                                  StructureSchedulerPolicy policy) {
        AsyncStructureChecker checker = AsyncStructureChecker.getInstance();
        if (!policy.allowsAsync(controller, checker)) {
            return false;
        }

        checker.registerForAsyncCheck(controller);
        asyncCheckFallbackTicks++;
        if (asyncCheckFallbackTicks >= StructureSchedulerPolicy.ASYNC_FALLBACK_INTERVAL) {
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
}
