package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.util.GTLog;
import gregtech.common.ConfigHolder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Trace logging helper for the structure-system-v3 migration.
 */
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
        return new StructureFailureTrace.Builder(controller.getMetaName(), controller.getPos())
                .formed(controller.isStructureFormed())
                .orientation(controller.getFrontFacing(), controller.getFrontFacingForStructure(),
                        controller.getUpwardsFacing(), controller.isFlipped())
                .path(path)
                .operation("COMMIT")
                .result("assembly-rejected")
                .actual(detail)
                .build();
    }

    @NotNull
    public static String describeMissingAbilities(@NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
        return StructureFailureTrace.describeMissingAbilities(missingAbilities);
    }
}
