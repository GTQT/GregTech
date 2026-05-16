package gregtech.api.metatileentity.multiblock;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Resolves parametric multiblock variants to stable registry ids.
 *
 * <p>Implementations should keep iteration order stable because creative tab
 * sub-items, JEI previews, and legacy ordinal migration may depend on it.</p>
 *
 * @param <V> variant value type
 */
public interface ParametricVariantRegistry<V> {

    /**
     * @return the variant used when no variant data is present or resolution fails
     */
    @NotNull
    V getDefaultVariant();

    /**
     * @return all registered variants in stable display/iteration order
     */
    @NotNull
    Collection<V> getVariants();

    /**
     * Looks up a variant by its stable id.
     *
     * @param id stable variant id
     * @return the registered variant, or null if the id is unknown
     */
    @Nullable
    V getVariant(@NotNull ResourceLocation id);

    /**
     * Looks up the stable id for a registered variant.
     *
     * @param variant registered variant
     * @return stable variant id
     * @throws IllegalArgumentException when the variant is unknown to this registry
     */
    @NotNull
    ResourceLocation getId(@NotNull V variant);

    /**
     * Resolves an id, falling back to {@link #getDefaultVariant()} when the id is null or unknown.
     */
    @NotNull
    default V getOrDefault(@Nullable ResourceLocation id) {
        V variant = id == null ? null : getVariant(id);
        return variant == null ? getDefaultVariant() : variant;
    }

    /**
     * Returns the name segment used by default parametric item subtypes and translation keys.
     */
    @NotNull
    default String getName(@NotNull V variant) {
        ResourceLocation id = getId(variant);
        return "gregtech".equals(id.getNamespace()) ? id.getPath() : id.getNamespace() + "." + id.getPath();
    }
}
