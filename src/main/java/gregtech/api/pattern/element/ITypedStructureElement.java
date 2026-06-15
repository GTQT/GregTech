package gregtech.api.pattern.element;

import gregtech.api.pattern.StructureDependency;
import gregtech.api.pattern.StructureIncrementalSupport;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * Explicit contract for direct elements whose runtime effects are represented
 * by typed structure contributions and declared dependencies.
 */
public interface ITypedStructureElement<T> extends IStructureElement<T> {

    @NotNull
    @Override
    default StructureIncrementalSupport getIncrementalSupport() {
        return StructureIncrementalSupport.TYPED_CONTRIBUTION;
    }

    @NotNull
    @Override
    default Set<StructureDependency> getDependencies() {
        return Collections.emptySet();
    }

    @Override
    default boolean hasExplicitIncrementalContract() {
        return true;
    }
}
