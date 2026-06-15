package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.StructureFailureTrace;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.pattern.StructureTrace;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.common.ConfigHolder;

import gregtech.api.util.GTLog;
import gregtech.api.util.world.DummyWorld;

import org.jetbrains.annotations.NotNull;

/**
 * Per-controller structure scheduling policy.
 *
 * <p>The world index owns only dirty storage. This policy decides whether a
 * controller may consume event-driven dirty state, async precheck, or polling.
 */
public interface StructureSchedulerPolicy {

    int ASYNC_FALLBACK_INTERVAL = 100;

    @NotNull
    static StructureSchedulerPolicy defaultPolicy() {
        return DefaultStructureSchedulerPolicy.INSTANCE;
    }

    boolean shouldRunFirstTickCheck(@NotNull MultiblockControllerBase controller);

    boolean allowsEventDriven(@NotNull MultiblockControllerBase controller);

    boolean allowsAsync(@NotNull MultiblockControllerBase controller,
                        @NotNull AsyncStructureChecker checker);

    /**
     * Whether a formed incremental lease may use a detached state-only async
     * precheck before the mandatory server-thread live confirmation.
     */
    default boolean allowsAsyncDirtyPrecheck(
            @NotNull MultiblockControllerBase controller,
            @NotNull AsyncStructureChecker checker) {
        return false;
    }

    int pollingInterval(@NotNull MultiblockControllerBase controller);

    boolean shouldPollingCheck(@NotNull MultiblockControllerBase controller);

    final class DefaultStructureSchedulerPolicy implements StructureSchedulerPolicy {

        private static final DefaultStructureSchedulerPolicy INSTANCE =
                new DefaultStructureSchedulerPolicy();

        private DefaultStructureSchedulerPolicy() {}

        @Override
        public boolean shouldRunFirstTickCheck(@NotNull MultiblockControllerBase controller) {
            return controller.isFirstTick();
        }

        @Override
        public boolean allowsEventDriven(@NotNull MultiblockControllerBase controller) {
            return ConfigHolder.machines.enableEventDrivenStructureCheck
                    && controller.isStructureFormed()
                    && controller.getWorld() != null
                    && !(controller.getWorld() instanceof DummyWorld);
        }

        @Override
        public boolean allowsAsync(@NotNull MultiblockControllerBase controller,
                                   @NotNull AsyncStructureChecker checker) {
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
            return true;
        }

        @Override
        public boolean allowsAsyncDirtyPrecheck(
                @NotNull MultiblockControllerBase controller,
                @NotNull AsyncStructureChecker checker) {
            return ConfigHolder.machines.enableAsyncStructureCheck
                    && controller.allowsAsyncStructureCheck()
                    && controller.isStructureFormed()
                    && controller.getWorld() != null
                    && !controller.getWorld().isRemote
                    && !(controller.getWorld() instanceof DummyWorld)
                    && checker.isRunning();
        }

        @Override
        public int pollingInterval(@NotNull MultiblockControllerBase controller) {
            return controller.isWorkingForStructureCheck()
                    ? controller.getStructureCheckIntervalWorking()
                    : controller.getStructureCheckIntervalStandby();
        }

        @Override
        public boolean shouldPollingCheck(@NotNull MultiblockControllerBase controller) {
            return controller.getOffsetTimer() % pollingInterval(controller) == 0;
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
            if (ConfigHolder.machines.debugStructureCheck) {
                GTLog.logger.debug("[StructureCheck] Async disabled for {}: unsupported {}",
                        controller.getMetaName(), capability);
            }
        }
    }
}
