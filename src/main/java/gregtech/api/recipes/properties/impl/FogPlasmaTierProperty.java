package gregtech.api.recipes.properties.impl;

import gregtech.api.GregTechAPI;
import gregtech.api.recipes.properties.RecipeProperty;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagInt;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;

/**
 * Recipe property for Forge of the Gods plasma recipes.
 * Indicates the minimum plasma tier required to process this recipe.
 * <p>
 * Tier classification is based on material proton count:
 * - Tier 0: protons <= 50 (light elements: H through Sn)
 * - Tier 1: protons 51-100 (mid-range elements: Sb through Fm), requires SEDS upgrade
 * - Tier 2: protons > 100 (heavy/exotic elements), requires EE upgrade
 */
public final class FogPlasmaTierProperty extends RecipeProperty<Integer> {

    public static final String KEY = "fog_plasma_tier";

    // Proton count thresholds for tier classification
    public static final int TIER_1_THRESHOLD = 50;
    public static final int TIER_2_THRESHOLD = 100;

    private static FogPlasmaTierProperty INSTANCE;

    private FogPlasmaTierProperty() {
        super(KEY, Integer.class);
    }

    public static FogPlasmaTierProperty getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FogPlasmaTierProperty();
            GregTechAPI.RECIPE_PROPERTIES.register(KEY, INSTANCE);
        }
        return INSTANCE;
    }

    /**
     * Determines the plasma tier for a material based on its proton count.
     *
     * @param protonCount the average proton count of the material
     * @return 0, 1, or 2
     */
    public static int getTierForProtons(long protonCount) {
        if (protonCount <= TIER_1_THRESHOLD) return 0;
        if (protonCount <= TIER_2_THRESHOLD) return 1;
        return 2;
    }

    @Override
    public @NotNull NBTBase serialize(@NotNull Object value) {
        return new NBTTagInt(castValue(value));
    }

    @Override
    public @NotNull Object deserialize(@NotNull NBTBase nbt) {
        return ((NBTTagInt) nbt).getInt();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawInfo(Minecraft minecraft, int x, int y, int color, Object value) {
        int tier = castValue(value);
        String tierName = getTierName(tier);
        minecraft.fontRenderer.drawString(
                I18n.format("gregtech.recipe.fog_plasma_tier", tierName),
                x, y, color);
    }

    @SideOnly(Side.CLIENT)
    private static String getTierName(int tier) {
        return switch (tier) {
            case 0 -> I18n.format("gregtech.recipe.fog_plasma_tier.0");
            case 1 -> I18n.format("gregtech.recipe.fog_plasma_tier.1");
            case 2 -> I18n.format("gregtech.recipe.fog_plasma_tier.2");
            default -> String.valueOf(tier);
        };
    }
}
