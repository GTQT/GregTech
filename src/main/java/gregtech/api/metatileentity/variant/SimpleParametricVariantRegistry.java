package gregtech.api.metatileentity.variant;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Ordered, optionally frozen implementation of {@link ParametricVariantRegistry}.
 *
 * <p>Variants are keyed by object identity when resolving variant to id. This
 * avoids surprising behavior for mutable variant objects and still works well
 * for enum constants.</p>
 *
 * @param <V> variant value type
 */
public class SimpleParametricVariantRegistry<V> implements ParametricVariantRegistry<V> {

    private final Map<ResourceLocation, V> variantsById = new LinkedHashMap<>();
    private final Map<V, ResourceLocation> idsByVariant = new IdentityHashMap<>();

    private V defaultVariant;
    private boolean frozen;

    /**
     * Registers a variant. The first registered variant becomes the default unless
     * {@link #registerDefault(ResourceLocation, Object)} was already used.
     */
    @NotNull
    public V register(@NotNull ResourceLocation id, @NotNull V variant) {
        return register(id, variant, defaultVariant == null);
    }

    /**
     * Registers a variant and makes it the default fallback.
     */
    @NotNull
    public V registerDefault(@NotNull ResourceLocation id, @NotNull V variant) {
        return register(id, variant, true);
    }

    @NotNull
    private V register(@NotNull ResourceLocation id, @NotNull V variant, boolean makeDefault) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(variant, "variant");
        ensureMutable();

        if (variantsById.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate parametric variant id: " + id);
        }
        if (idsByVariant.containsKey(variant)) {
            throw new IllegalArgumentException("Parametric variant is already registered: " + variant);
        }

        variantsById.put(id, variant);
        idsByVariant.put(variant, id);
        if (makeDefault) {
            defaultVariant = variant;
        }
        return variant;
    }

    /**
     * Prevents further registrations. Call after all addon registration hooks have run.
     */
    public void freeze() {
        this.frozen = true;
    }

    /**
     * @return true once this registry no longer accepts new variants
     */
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * @return true when the id has a registered variant
     */
    public boolean containsId(@NotNull ResourceLocation id) {
        return variantsById.containsKey(id);
    }

    /**
     * @return true when the variant is registered in this registry
     */
    public boolean containsVariant(@NotNull V variant) {
        return idsByVariant.containsKey(variant);
    }

    /**
     * @return number of registered variants
     */
    public int size() {
        return variantsById.size();
    }

    @Override
    @NotNull
    public V getDefaultVariant() {
        if (defaultVariant == null) {
            throw new IllegalStateException("No default parametric variant has been registered");
        }
        return defaultVariant;
    }

    @Override
    @NotNull
    public Collection<V> getVariants() {
        return Collections.unmodifiableCollection(variantsById.values());
    }

    @Override
    @Nullable
    public V getVariant(@NotNull ResourceLocation id) {
        return variantsById.get(id);
    }

    @Override
    @NotNull
    public ResourceLocation getId(@NotNull V variant) {
        ResourceLocation id = idsByVariant.get(variant);
        if (id == null) {
            throw new IllegalArgumentException("Unknown parametric variant: " + variant);
        }
        return id;
    }

    private void ensureMutable() {
        if (frozen) {
            throw new IllegalStateException("Parametric variant registry is frozen");
        }
    }
}
