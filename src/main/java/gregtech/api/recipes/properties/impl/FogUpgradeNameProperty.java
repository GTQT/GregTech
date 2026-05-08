package gregtech.api.recipes.properties.impl;

import gregtech.api.GregTechAPI;
import gregtech.api.recipes.properties.RecipeProperty;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;

/**
 * Recipe property for Forge of the Gods upgrade cost recipes.
 * Displays the upgrade short name in JEI and hides EUt/duration info since these are display-only recipes.
 */
public final class FogUpgradeNameProperty extends RecipeProperty<String> {

    public static final String KEY = "fog_upgrade_name";

    private static FogUpgradeNameProperty INSTANCE;

    private FogUpgradeNameProperty() {
        super(KEY, String.class);
    }

    public static FogUpgradeNameProperty getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FogUpgradeNameProperty();
            GregTechAPI.RECIPE_PROPERTIES.register(KEY, INSTANCE);
        }
        return INSTANCE;
    }

    @Override
    public @NotNull NBTBase serialize(@NotNull Object value) {
        return new NBTTagString(castValue(value));
    }

    @Override
    public @NotNull Object deserialize(@NotNull NBTBase nbt) {
        return ((NBTTagString) nbt).getString();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawInfo(Minecraft minecraft, int x, int y, int color, Object value) {
        String upgradeName = I18n.format(castValue(value));
        minecraft.fontRenderer.drawString(
                I18n.format("gregtech.recipe.fog_upgrade_name", upgradeName),
                x, y, color);
    }

    @Override
    public boolean hideTotalEU() {
        return true;
    }

    @Override
    public boolean hideEUt() {
        return true;
    }

    @Override
    public boolean hideDuration() {
        return true;
    }
}
