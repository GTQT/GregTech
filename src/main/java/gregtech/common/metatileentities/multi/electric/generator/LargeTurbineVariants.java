package gregtech.common.metatileentities.multi.electric.generator;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.variant.ParametricVariantRegistry;
import gregtech.api.metatileentity.variant.SimpleParametricVariantRegistry;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockTurbineCasing.TurbineCasingType;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import static gregtech.api.util.GTUtility.gregtechId;

/**
 * Registry and defaults for large turbine variants.
 *
 * <p>Addons may register additional variants before this registry is frozen by
 * calling {@link #register(ResourceLocation, LargeTurbineVariant)}.</p>
 */
public final class LargeTurbineVariants {

    private static final SimpleParametricVariantRegistry<LargeTurbineVariant> REGISTRY =
            new SimpleParametricVariantRegistry<>();

    public static final LargeTurbineVariant STEAM = registerDefault(gregtechId("steam"),
            LargeTurbineVariant.standard(gregtechId("steam"), RecipeMaps.STEAM_TURBINE_FUELS, GTValues.HV,
                    MetaBlocks.TURBINE_CASING.getState(TurbineCasingType.STEEL_TURBINE_CASING),
                    MetaBlocks.TURBINE_CASING.getState(TurbineCasingType.STEEL_GEARBOX),
                    Textures.TURBINE_STEEL_CASING, Textures.LARGE_STEAM_TURBINE_OVERLAY));

    public static final LargeTurbineVariant GAS = register(gregtechId("gas"),
            LargeTurbineVariant.standard(gregtechId("gas"), RecipeMaps.GAS_TURBINE_FUELS, GTValues.EV,
                    MetaBlocks.TURBINE_CASING.getState(TurbineCasingType.STAINLESS_TURBINE_CASING),
                    MetaBlocks.TURBINE_CASING.getState(TurbineCasingType.STAINLESS_STEEL_GEARBOX),
                    Textures.TURBINE_STAINLESS_STEEL_CASING, Textures.LARGE_GAS_TURBINE_OVERLAY));

    public static final LargeTurbineVariant PLASMA = register(gregtechId("plasma"),
            LargeTurbineVariant.standard(gregtechId("plasma"), RecipeMaps.PLASMA_GENERATOR_FUELS, GTValues.IV,
                    MetaBlocks.TURBINE_CASING.getState(TurbineCasingType.TUNGSTENSTEEL_TURBINE_CASING),
                    MetaBlocks.TURBINE_CASING.getState(TurbineCasingType.TUNGSTENSTEEL_GEARBOX),
                    Textures.TURBINE_TUNGSTENSTEEL_CASING, Textures.LARGE_PLASMA_TURBINE_OVERLAY));

    private LargeTurbineVariants() {}

    @NotNull
    public static ParametricVariantRegistry<LargeTurbineVariant> registry() {
        return REGISTRY;
    }

    @NotNull
    public static LargeTurbineVariant register(@NotNull ResourceLocation id,
                                               @NotNull LargeTurbineVariant variant) {
        return REGISTRY.register(id, variant);
    }

    @NotNull
    private static LargeTurbineVariant registerDefault(@NotNull ResourceLocation id,
                                                       @NotNull LargeTurbineVariant variant) {
        return REGISTRY.registerDefault(id, variant);
    }

    public static void freeze() {
        REGISTRY.freeze();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }
}
