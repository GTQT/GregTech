package gregtech.api.metatileentity.multiblock;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class CleanroomType {

    private static final Map<String, CleanroomType> CLEANROOM_TYPES = new Object2ObjectOpenHashMap<>();

    public static final CleanroomType CLEANROOM = new CleanroomType("cleanroom",
            "gregtech.recipe.cleanroom.display_name");
    public static final CleanroomType STERILE_CLEANROOM = new CleanroomType("sterile_cleanroom",
            "gregtech.recipe.cleanroom_sterile.display_name");
    public static final CleanroomType ISO3 = new CleanroomType("cleanroom_iso_3",
            "gregtech.recipe.cleanroom_iso_3.display_name");
    public static final CleanroomType ISO2 = new CleanroomType("cleanroom_iso_2",
            "gregtech.recipe.cleanroom_iso_2.display_name");
    public static final CleanroomType ISO1 = new CleanroomType("cleanroom_iso_1",
            "gregtech.recipe.cleanroom_iso_1.display_name");
    public static final CleanroomType ISO0 = new CleanroomType("cleanroom_iso_0",
            "gregtech.recipe.cleanroom_iso_0.display_name");

    private final String name;
    private final String translationKey;

    public CleanroomType(@NotNull String name, @NotNull String translationKey) {
        if (CLEANROOM_TYPES.get(name) != null)
            throw new IllegalArgumentException(
                    String.format("CleanroomType with name %s is already registered!", name));

        this.name = name;
        this.translationKey = translationKey;
        CLEANROOM_TYPES.put(name, this);
    }

    @NotNull
    public String getName() {
        return this.name;
    }

    @NotNull
    public String getTranslationKey() {
        return this.translationKey;
    }

    @Nullable
    public static CleanroomType getByName(@NotNull String name) {
        return CLEANROOM_TYPES.get(name);
    }
}
