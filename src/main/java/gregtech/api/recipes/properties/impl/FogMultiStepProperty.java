package gregtech.api.recipes.properties.impl;

import gregtech.api.GregTechAPI;
import gregtech.api.recipes.properties.RecipeProperty;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;

/**
 * Recipe property for Forge of the Gods plasma recipes.
 * Indicates whether this recipe requires the multi-step plasma capability (TPTP upgrade).
 * <p>
 * Multi-step recipes are those involving compound/alloy materials (non-elemental),
 * which require more complex plasma processing steps.
 */
public final class FogMultiStepProperty extends RecipeProperty<Boolean> {

    public static final String KEY = "fog_multistep";

    private static FogMultiStepProperty INSTANCE;

    private FogMultiStepProperty() {
        super(KEY, Boolean.class);
    }

    public static FogMultiStepProperty getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FogMultiStepProperty();
            GregTechAPI.RECIPE_PROPERTIES.register(KEY, INSTANCE);
        }
        return INSTANCE;
    }

    @Override
    public @NotNull NBTBase serialize(@NotNull Object value) {
        return new NBTTagByte((byte) (castValue(value) ? 1 : 0));
    }

    @Override
    public @NotNull Object deserialize(@NotNull NBTBase nbt) {
        return ((NBTTagByte) nbt).getByte() != 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawInfo(Minecraft minecraft, int x, int y, int color, Object value) {
        if (castValue(value)) {
            minecraft.fontRenderer.drawString(
                    I18n.format("gregtech.recipe.fog_multistep"),
                    x, y, color);
        }
    }

    @Override
    public boolean isHidden() {
        // Only display when value is true; hide property line for non-multistep recipes
        return false;
    }

    @Override
    public int getInfoHeight(@NotNull Object value) {
        // Only take up space if this is a multistep recipe
        return castValue(value) ? 10 : 0;
    }
}
