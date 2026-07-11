package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.util.GTLog;
import gregtech.common.ConfigHolder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Trace logging helper for V3 structure operations. */
public final class StructureTrace {

    private StructureTrace() {}

    public static boolean isEnabled() {
        return ConfigHolder.machines.debugStructureTrace;
    }

    public static void debug(@NotNull MultiblockControllerBase controller,
                             @NotNull String phase,
                             @Nullable String detail) {
        if (!isEnabled()) return;
        GTLog.logger.debug("[StructureTrace] phase={} controller={} pos={} formed={} front={} structureFront={} up={} flipped={} {}",
                phase,
                controller.getMetaName(),
                controller.getPos(),
                controller.isStructureFormed(),
                controller.getFrontFacing(),
                controller.getFrontFacingForStructure(),
                controller.getUpwardsFacing(),
                controller.isFlipped(),
                detail == null ? "" : detail);
    }

    public static void debugLifecycle(@NotNull StructureFailureTrace failure) {
        if (!isEnabled()) return;
        GTLog.logger.debug("[StructureTrace] failure controller={} pos={} {}",
                failure.getControllerId(), failure.getControllerPos(), failure.describeForCommand());
    }

    public static StructureFailureTrace failure(@NotNull MultiblockControllerBase controller,
                                                @NotNull String path,
                                                @NotNull String operation,
                                                @Nullable PatternError error,
                                                @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
        return StructureFailureTrace.fromController(controller, path, operation, error, missingAbilities);
    }

    @NotNull
    public static StructureFailureTrace commitFailure(@NotNull MultiblockControllerBase controller,
                                                      @NotNull String path,
                                                      @NotNull String detail) {
        return lifecycleFailure(controller, path, "COMMIT",
                StructureFailureTrace.Kind.COMMIT_REJECTION, detail);
    }

    @NotNull
    public static StructureFailureTrace assemblyFailure(@NotNull MultiblockControllerBase controller,
                                                        @NotNull String path,
                                                        @NotNull String detail) {
        return lifecycleFailure(controller, path, "ASSEMBLY",
                StructureFailureTrace.Kind.ASSEMBLY_REJECTION, detail);
    }

    @NotNull
    public static StructureFailureTrace lifecycleFailure(@NotNull MultiblockControllerBase controller,
                                                        @NotNull String path,
                                                        @NotNull String operation,
                                                        @NotNull StructureFailureTrace.Kind kind,
                                                        @NotNull String detail) {
        return new StructureFailureTrace.Builder(controller.getMetaName(), controller.getPos())
                .formed(controller.isStructureFormed())
                .orientation(StructureOrientation.fromController(controller))
                .path(path)
                .operation(operation)
                .result(kind.getTraceName())
                .kind(kind)
                .piece("commit")
                .cell("lifecycle")
                .actual(detail)
                .build();
    }

    @NotNull
    public static String describeMissingAbilities(@NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
        return StructureFailureTrace.describeMissingAbilities(missingAbilities);
    }
}
