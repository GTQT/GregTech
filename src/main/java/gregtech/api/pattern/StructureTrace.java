package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.util.GTLog;
import gregtech.common.ConfigHolder;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.StringJoiner;

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

    public static Failure failure(@NotNull String path,
                                  @Nullable PatternError error,
                                  @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
        return new Failure(path, getErrorPos(error), describeMissingAbilities(missingAbilities));
    }

    @Nullable
    private static BlockPos getErrorPos(@Nullable PatternError error) {
        if (error == null) {
            return null;
        }
        try {
            return error.getPos();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @NotNull
    public static String describeMissingAbilities(@NotNull Map<MultiblockAbility<?>, Integer> missingAbilities) {
        if (missingAbilities.isEmpty()) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        for (Map.Entry<MultiblockAbility<?>, Integer> entry : missingAbilities.entrySet()) {
            if (entry.getValue() > 0) {
                joiner.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        return joiner.toString();
    }

    public static final class Failure {

        @NotNull
        private final String path;
        @Nullable
        private final BlockPos errorPos;
        @NotNull
        private final String missingAbilities;

        private Failure(@NotNull String path, @Nullable BlockPos errorPos, @NotNull String missingAbilities) {
            this.path = path;
            this.errorPos = errorPos;
            this.missingAbilities = missingAbilities;
        }

        @NotNull
        public String getPath() {
            return path;
        }

        @Nullable
        public BlockPos getErrorPos() {
            return errorPos;
        }

        @NotNull
        public String getMissingAbilities() {
            return missingAbilities;
        }

        @Override
        public String toString() {
            return "path=" + path + ", errorPos=" + errorPos + ", missingAbilities=" + missingAbilities;
        }
    }
}
