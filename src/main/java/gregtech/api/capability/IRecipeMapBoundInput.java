package gregtech.api.capability;

import org.jetbrains.annotations.Nullable;

/**
 * An isolated input handler supplied by an ME pattern provider that is bound
 * to one RecipeMap for the lifetime of its buffered materials.
 */
public interface IRecipeMapBoundInput extends IPatternBufferIsolatedHandler {

    /**
     * @return the unlocalized RecipeMap name selected by the originating
     *         pattern, or {@code null} when the input is not map-bound.
     */
    @Nullable
    String getBoundRecipeMapName();
}
