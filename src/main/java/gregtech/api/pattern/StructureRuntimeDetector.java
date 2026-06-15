package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import org.jetbrains.annotations.NotNull;

/**
 * Runtime geometry detector for structures whose world footprint cannot be
 * expressed as a stable set of fixed or repeatable templates.
 *
 * <p>The detector belongs to an immutable {@code StructureDefinition}. It may
 * discover runtime bounds, but must publish all matched cells and formation
 * state through {@link StructureRuntimeDetectionContext}.
 */
@FunctionalInterface
public interface StructureRuntimeDetector<T extends MultiblockControllerBase> {

    /**
     * Detect and validate the structure.
     *
     * @return {@code true} when every runtime cell matched
     */
    boolean detect(@NotNull StructureRuntimeDetectionContext<T> context);
}
