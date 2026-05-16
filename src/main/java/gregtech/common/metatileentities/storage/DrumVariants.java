package gregtech.common.metatileentities.storage;

import gregtech.api.metatileentity.variant.ParametricVariantRegistry;
import gregtech.api.metatileentity.variant.SimpleParametricVariantRegistry;
import gregtech.api.unification.material.Materials;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static gregtech.api.util.GTUtility.gregtechId;

/**
 * Registry and defaults for single-ID drum variants.
 *
 * <p>The built-in registration order intentionally matches the legacy
 * {@link MetaTileEntityDrum.DrumMaterial} enum order so old ordinal NBT migrates correctly.</p>
 */
public final class DrumVariants {

    private static final SimpleParametricVariantRegistry<DrumVariant> REGISTRY =
            new SimpleParametricVariantRegistry<>();

    public static final DrumVariant WOOD = registerDefault(DrumVariant.material(gregtechId("wood"),
            Materials.Wood, 16_000));
    public static final DrumVariant COPPER = register(DrumVariant.material(gregtechId("copper"),
            Materials.Copper, 24_000));
    public static final DrumVariant LEAD = register(DrumVariant.material(gregtechId("lead"),
            Materials.Lead, 24_000));
    public static final DrumVariant IRON = register(DrumVariant.material(gregtechId("iron"),
            Materials.Iron, 32_000));
    public static final DrumVariant BRONZE = register(DrumVariant.material(gregtechId("bronze"),
            Materials.Bronze, 40_000));
    public static final DrumVariant GOLD = register(DrumVariant.material(gregtechId("gold"),
            Materials.Gold, 48_000));
    public static final DrumVariant STEEL = register(DrumVariant.material(gregtechId("steel"),
            Materials.Steel, 64_000));
    public static final DrumVariant ALUMINIUM = register(DrumVariant.material(gregtechId("aluminium"),
            Materials.Aluminium, 128_000));
    public static final DrumVariant CHROME = register(DrumVariant.material(gregtechId("chrome"),
            Materials.Chrome, 128_000));
    public static final DrumVariant STAINLESS_STEEL = register(DrumVariant.material(gregtechId("stainless_steel"),
            Materials.StainlessSteel, 256_000));
    public static final DrumVariant TITANIUM = register(DrumVariant.material(gregtechId("titanium"),
            Materials.Titanium, 512_000));
    public static final DrumVariant TUNGSTEN = register(DrumVariant.material(gregtechId("tungsten"),
            Materials.Tungsten, 768_000));
    public static final DrumVariant TUNGSTENSTEEL = register(DrumVariant.material(gregtechId("tungstensteel"),
            Materials.TungstenSteel, 1_024_000));
    public static final DrumVariant IRIDIUM = register(DrumVariant.material(gregtechId("iridium"),
            Materials.Iridium, 1_536_000));
    public static final DrumVariant RHODIUM_PLATED_PALLADIUM = register(DrumVariant.material(
            gregtechId("rhodium_plated_palladium"), Materials.RhodiumPlatedPalladium, 2_048_000));
    public static final DrumVariant NAQUADAH_ALLOY = register(DrumVariant.material(gregtechId("naquadah_alloy"),
            Materials.NaquadahAlloy, 4_096_000));
    public static final DrumVariant DARMSTADTIUM = register(DrumVariant.material(gregtechId("darmstadtium"),
            Materials.Darmstadtium, 8_192_000));
    public static final DrumVariant NEUTRONIUM = register(DrumVariant.material(gregtechId("neutronium"),
            Materials.Neutronium, 16_384_000));

    private DrumVariants() {}

    @NotNull
    public static ParametricVariantRegistry<DrumVariant> registry() {
        return REGISTRY;
    }

    @NotNull
    public static Collection<DrumVariant> values() {
        return REGISTRY.getVariants();
    }

    @NotNull
    public static DrumVariant register(@NotNull DrumVariant variant) {
        return register(variant.getId(), variant);
    }

    @NotNull
    public static DrumVariant register(@NotNull ResourceLocation id, @NotNull DrumVariant variant) {
        if (!id.equals(variant.getId())) {
            throw new IllegalArgumentException("Drum variant id mismatch: " + id + " != " + variant.getId());
        }
        return REGISTRY.register(id, variant);
    }

    @NotNull
    private static DrumVariant registerDefault(@NotNull DrumVariant variant) {
        return REGISTRY.registerDefault(variant.getId(), variant);
    }

    public static void freeze() {
        REGISTRY.freeze();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }
}
