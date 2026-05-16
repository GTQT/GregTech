package gregtech.common.metatileentities.storage;

import gregtech.api.capability.IPropertyFluidFilter;
import gregtech.api.recipes.ModHandler;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.PropertyKey;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Data object describing one single-ID drum variant.
 */
public final class DrumVariant {

    private final ResourceLocation id;
    private final IPropertyFluidFilter fluidFilter;
    private final boolean isWood;
    private final int color;
    private final int tankSize;
    private final String translationKey;
    @Nullable
    private final Material material;

    public DrumVariant(@NotNull ResourceLocation id,
                       @NotNull IPropertyFluidFilter fluidFilter,
                       boolean isWood,
                       int color,
                       int tankSize,
                       @NotNull String translationKey) {
        this(id, fluidFilter, isWood, color, tankSize, translationKey, null);
    }

    private DrumVariant(@NotNull ResourceLocation id,
                        @NotNull IPropertyFluidFilter fluidFilter,
                        boolean isWood,
                        int color,
                        int tankSize,
                        @NotNull String translationKey,
                        @Nullable Material material) {
        if (tankSize <= 0) {
            throw new IllegalArgumentException("Drum tank size must be positive");
        }
        this.id = Objects.requireNonNull(id, "id");
        this.fluidFilter = Objects.requireNonNull(fluidFilter, "fluidFilter");
        this.isWood = isWood;
        this.color = color;
        this.tankSize = tankSize;
        this.translationKey = Objects.requireNonNull(translationKey, "translationKey");
        this.material = material;
    }

    @NotNull
    public static DrumVariant material(@NotNull ResourceLocation id,
                                       @NotNull Material material,
                                       int tankSize) {
        return new DrumVariant(id, getFluidFilterForMaterial(material), ModHandler.isMaterialWood(material),
                material.getMaterialRGB(), tankSize, id.getNamespace() + ".machine.drum." + id.getPath(), material);
    }

    @NotNull
    public static DrumVariant legacy(@NotNull ResourceLocation metaTileEntityId,
                                     @NotNull IPropertyFluidFilter fluidFilter,
                                     boolean isWood,
                                     int color,
                                     int tankSize) {
        return new DrumVariant(metaTileEntityId, fluidFilter, isWood, color, tankSize,
                metaTileEntityId.getNamespace() + ".machine." + metaTileEntityId.getPath());
    }

    @NotNull
    public ResourceLocation getId() {
        return id;
    }

    @NotNull
    public IPropertyFluidFilter getFluidFilter() {
        return fluidFilter;
    }

    public boolean isWood() {
        return isWood;
    }

    public int getColor() {
        return color;
    }

    public int getTankSize() {
        return tankSize;
    }

    @NotNull
    public String getTranslationKey() {
        return translationKey;
    }

    @Nullable
    public Material getMaterial() {
        return material;
    }

    @NotNull
    static IPropertyFluidFilter getFluidFilterForMaterial(@NotNull Material material) {
        IPropertyFluidFilter filter = material.getProperty(PropertyKey.FLUID_PIPE);
        if (filter == null) {
            throw new IllegalArgumentException("Material " + material + " requires FluidPipeProperty for Drums");
        }
        return filter;
    }

    @Override
    public String toString() {
        return "DrumVariant{" + id + '}';
    }
}
