package gregtech.api.util;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import gregtech.api.GCYMValues;

public final class GCYMUtil {

    public static @NotNull ResourceLocation gcymId(@NotNull String path) {
        return new ResourceLocation(GCYMValues.MODID, path);
    }

    private GCYMUtil() {}
}
