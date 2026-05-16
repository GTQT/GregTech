package gregtech.api.metatileentity.multiblock;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Factory helpers for common parametric variant registry shapes.
 */
public final class ParametricVariantRegistries {

    private ParametricVariantRegistries() {}

    /**
     * Creates a mutable ordered registry.
     */
    @NotNull
    public static <V> SimpleParametricVariantRegistry<V> create() {
        return new SimpleParametricVariantRegistry<>();
    }

    /**
     * Creates a frozen registry containing exactly one variant.
     */
    @NotNull
    public static <V> ParametricVariantRegistry<V> single(@NotNull ResourceLocation id, @NotNull V variant) {
        SimpleParametricVariantRegistry<V> registry = create();
        registry.registerDefault(id, variant);
        registry.freeze();
        return registry;
    }

    /**
     * Creates a frozen registry for enum-backed parametric machines.
     *
     * <p>Enum values are registered as {@code namespace:enum_name_lowercase}.</p>
     */
    @NotNull
    public static <E extends Enum<E>> ParametricVariantRegistry<E> enumRegistry(@NotNull String namespace,
                                                                                @NotNull Class<E> enumClass,
                                                                                @NotNull E defaultVariant) {
        Objects.requireNonNull(namespace, "namespace");
        return enumRegistry(enumClass, defaultVariant,
                value -> new ResourceLocation(namespace, value.name().toLowerCase(Locale.ROOT)));
    }

    /**
     * Creates a frozen registry for enum-backed parametric machines with custom ids.
     */
    @NotNull
    public static <E extends Enum<E>> ParametricVariantRegistry<E> enumRegistry(@NotNull Class<E> enumClass,
                                                                                @NotNull E defaultVariant,
                                                                                @NotNull Function<E, ResourceLocation> idFactory) {
        Objects.requireNonNull(enumClass, "enumClass");
        Objects.requireNonNull(defaultVariant, "defaultVariant");
        Objects.requireNonNull(idFactory, "idFactory");

        SimpleParametricVariantRegistry<E> registry = create();
        for (E value : enumClass.getEnumConstants()) {
            ResourceLocation id = Objects.requireNonNull(idFactory.apply(value),
                    "idFactory returned null for " + value);
            if (value == defaultVariant) {
                registry.registerDefault(id, value);
            } else {
                registry.register(id, value);
            }
        }
        registry.freeze();
        return registry;
    }
}
