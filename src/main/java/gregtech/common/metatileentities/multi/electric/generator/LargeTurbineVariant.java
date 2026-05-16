package gregtech.common.metatileentities.multi.electric.generator;

import gregtech.api.recipes.RecipeMap;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Data object describing a large turbine variant.
 *
 * <p>The id is the stable registry/persistence identity. The translation key is
 * kept separate so legacy independently registered turbines can preserve names
 * derived from their MTE id, such as {@code gtqtcore.machine.large_turbine.exhaust_gas}.</p>
 */
public final class LargeTurbineVariant {

    private final ResourceLocation id;
    private final String translationKey;
    private final RecipeMap<?> recipeMap;
    private final int tier;
    private final IBlockState casingState;
    private final IBlockState gearboxState;
    private final ICubeRenderer casingRenderer;
    private final ICubeRenderer frontOverlay;
    private final boolean hasMufflerMechanics;

    private LargeTurbineVariant(@NotNull ResourceLocation id,
                                @NotNull String translationKey,
                                @NotNull RecipeMap<?> recipeMap,
                                int tier,
                                @NotNull IBlockState casingState,
                                @NotNull IBlockState gearboxState,
                                @NotNull ICubeRenderer casingRenderer,
                                boolean hasMufflerMechanics,
                                @NotNull ICubeRenderer frontOverlay) {
        this.id = Objects.requireNonNull(id, "id");
        this.translationKey = Objects.requireNonNull(translationKey, "translationKey");
        this.recipeMap = Objects.requireNonNull(recipeMap, "recipeMap");
        this.tier = tier;
        this.casingState = Objects.requireNonNull(casingState, "casingState");
        this.gearboxState = Objects.requireNonNull(gearboxState, "gearboxState");
        this.casingRenderer = Objects.requireNonNull(casingRenderer, "casingRenderer");
        this.hasMufflerMechanics = hasMufflerMechanics;
        this.frontOverlay = Objects.requireNonNull(frontOverlay, "frontOverlay");
    }

    /**
     * Creates a standard large turbine variant whose translation key is
     * {@code namespace.machine.large_turbine.<id path>}.
     */
    @NotNull
    public static LargeTurbineVariant standard(@NotNull ResourceLocation id,
                                               @NotNull RecipeMap<?> recipeMap,
                                               int tier,
                                               @NotNull IBlockState casingState,
                                               @NotNull IBlockState gearboxState,
                                               @NotNull ICubeRenderer casingRenderer,
                                               @NotNull ICubeRenderer frontOverlay) {
        return standard(id, id.getPath(), recipeMap, tier, casingState, gearboxState, casingRenderer, frontOverlay);
    }

    /**
     * Creates a standard large turbine variant whose translation key is
     * {@code namespace.machine.large_turbine.<variantName>}.
     */
    @NotNull
    public static LargeTurbineVariant standard(@NotNull ResourceLocation id,
                                               @NotNull String variantName,
                                               @NotNull RecipeMap<?> recipeMap,
                                               int tier,
                                               @NotNull IBlockState casingState,
                                               @NotNull IBlockState gearboxState,
                                               @NotNull ICubeRenderer casingRenderer,
                                               @NotNull ICubeRenderer frontOverlay) {
        String translationKey = id.getNamespace() + ".machine.large_turbine." + variantName;
        return of(id, translationKey, recipeMap, tier, casingState, gearboxState, casingRenderer, true, frontOverlay);
    }

    /**
     * Creates a legacy fixed variant for independently registered turbine MTEs.
     *
     * <p>The variant id and translation key are both derived from the legacy MTE id,
     * preserving names such as {@code gtqtcore.machine.large_turbine.exhaust_gas}.</p>
     */
    @NotNull
    public static LargeTurbineVariant legacy(@NotNull ResourceLocation metaTileEntityId,
                                             @NotNull RecipeMap<?> recipeMap,
                                             int tier,
                                             @NotNull IBlockState casingState,
                                             @NotNull IBlockState gearboxState,
                                             @NotNull ICubeRenderer casingRenderer,
                                             boolean hasMufflerMechanics,
                                             @NotNull ICubeRenderer frontOverlay) {
        String translationKey = metaTileEntityId.getNamespace() + ".machine." + metaTileEntityId.getPath();
        return of(metaTileEntityId, translationKey, recipeMap, tier, casingState, gearboxState, casingRenderer,
                hasMufflerMechanics, frontOverlay);
    }

    /**
     * Creates a fully customized large turbine variant.
     */
    @NotNull
    public static LargeTurbineVariant of(@NotNull ResourceLocation id,
                                         @NotNull String translationKey,
                                         @NotNull RecipeMap<?> recipeMap,
                                         int tier,
                                         @NotNull IBlockState casingState,
                                         @NotNull IBlockState gearboxState,
                                         @NotNull ICubeRenderer casingRenderer,
                                         boolean hasMufflerMechanics,
                                         @NotNull ICubeRenderer frontOverlay) {
        return new LargeTurbineVariant(id, translationKey, recipeMap, tier, casingState, gearboxState, casingRenderer,
                hasMufflerMechanics, frontOverlay);
    }

    @NotNull
    public ResourceLocation getId() {
        return id;
    }

    @NotNull
    public String getTranslationKey() {
        return translationKey;
    }

    @NotNull
    public RecipeMap<?> getRecipeMap() {
        return recipeMap;
    }

    public int getTier() {
        return tier;
    }

    @NotNull
    public IBlockState getCasingState() {
        return casingState;
    }

    @NotNull
    public IBlockState getGearboxState() {
        return gearboxState;
    }

    @NotNull
    public ICubeRenderer getCasingRenderer() {
        return casingRenderer;
    }

    @NotNull
    public ICubeRenderer getFrontOverlay() {
        return frontOverlay;
    }

    public boolean hasMufflerMechanics() {
        return hasMufflerMechanics;
    }

    @Override
    public String toString() {
        return "LargeTurbineVariant{" + id + '}';
    }
}
