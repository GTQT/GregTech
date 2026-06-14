package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.StructureTrace;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Main-thread commit guard for structure lifecycle publications.
 *
 * <p>Async, event-driven and polling checks all validate the same runtime
 * generation, world, position, orientation and optional world revision before
 * publishing side effects.
 */
final class StructureCommitToken {

    @NotNull
    private final MultiblockControllerBase controller;
    private final long runtimeGeneration;
    private final long lifecycleGeneration;
    @Nullable
    private final World world;
    @Nullable
    private final BlockPos centerPos;
    @NotNull
    private final StructureOrientation orientation;
    @Nullable
    private final MultiblockWorldData.ChangeSnapshot changeSnapshot;
    private final boolean allowAlreadyFormed;

    @NotNull
    static StructureCommitToken captureForCheck(@NotNull MultiblockControllerBase controller) {
        return new StructureCommitToken(
                controller,
                controller.getStructureRuntimeGeneration(),
                controller.getStructureLifecycleGeneration(),
                controller.getWorld(),
                controller.getPos() == null ? null : controller.getPos().toImmutable(),
                StructureOrientation.fromController(controller),
                null,
                true);
    }

    @NotNull
    static StructureCommitToken captureForAsyncPrecheck(
            @NotNull MultiblockControllerBase controller,
            @Nullable MultiblockWorldData.ChangeSnapshot changeSnapshot) {
        return new StructureCommitToken(
                controller,
                controller.getStructureRuntimeGeneration(),
                controller.getStructureLifecycleGeneration(),
                controller.getWorld(),
                controller.getPos() == null ? null : controller.getPos().toImmutable(),
                StructureOrientation.fromController(controller),
                changeSnapshot,
                false);
    }

    private StructureCommitToken(
            @NotNull MultiblockControllerBase controller,
            long runtimeGeneration,
            long lifecycleGeneration,
            @Nullable World world,
            @Nullable BlockPos centerPos,
            @NotNull StructureOrientation orientation,
            @Nullable MultiblockWorldData.ChangeSnapshot changeSnapshot,
            boolean allowAlreadyFormed) {
        this.controller = controller;
        this.runtimeGeneration = runtimeGeneration;
        this.lifecycleGeneration = lifecycleGeneration;
        this.world = world;
        this.centerPos = centerPos;
        this.orientation = orientation;
        this.changeSnapshot = changeSnapshot;
        this.allowAlreadyFormed = allowAlreadyFormed;
    }

    @NotNull
    MultiblockControllerBase getController() {
        return controller;
    }

    long getRuntimeGeneration() {
        return runtimeGeneration;
    }

    long getLifecycleGeneration() {
        return lifecycleGeneration;
    }

    @Nullable
    World getWorld() {
        return world;
    }

    @Nullable
    BlockPos getCenterPos() {
        return centerPos;
    }

    @NotNull
    BlockPos requireCenterPos() {
        if (centerPos == null) {
            throw new IllegalStateException("Structure commit token has no controller position");
        }
        return centerPos;
    }

    @NotNull
    StructureOrientation getOrientation() {
        return orientation;
    }

    @Nullable
    MultiblockWorldData.ChangeSnapshot getChangeSnapshot() {
        return changeSnapshot;
    }

    boolean isCurrent() {
        return staleReason() == null;
    }

    @Nullable
    String staleReason() {
        if (controller.getStructureRuntimeGeneration() != runtimeGeneration) {
            return "runtime-generation";
        }
        if (controller.getStructureLifecycleGeneration() != lifecycleGeneration) {
            return "lifecycle-generation";
        }
        if (controller.getWorld() != world) {
            return "world";
        }
        if (!Objects.equals(controller.getPos(), centerPos)) {
            return "controller-position";
        }
        if (!orientation.matchesControllerForCheck(controller)) {
            return "orientation";
        }
        if (!allowAlreadyFormed && controller.isStructureFormed()) {
            return "already-formed";
        }
        if (world != null && changeSnapshot != null
                && !MultiblockWorldData.get(world).isChangeSnapshotCurrent(changeSnapshot)) {
            return "snapshot-version";
        }
        return null;
    }

    void traceStale(@NotNull String operation, @NotNull String reason) {
        StructureTrace.debug(
                controller,
                operation + "-stale-rejected",
                "reason=" + reason
                        + ", runtimeGeneration=" + runtimeGeneration
                        + ", lifecycleGeneration=" + lifecycleGeneration);
    }
}
