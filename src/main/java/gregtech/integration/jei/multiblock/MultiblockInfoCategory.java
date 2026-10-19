package gregtech.integration.jei.multiblock;

import gregtech.api.GTValues;
import gregtech.api.gui.GuiTextures;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.GTLog;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.IJeiHelpers;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.gui.recipes.RecipesGui;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class MultiblockInfoCategory implements IRecipeCategory<MultiblockInfoRecipeWrapper> {

    public static final String UID = String.format("%s.multiblock_info", GTValues.MODID);
    private static final int MAX_THREADS = Math.min(4, Runtime.getRuntime().availableProcessors());
    /** Tallest compact recipe: four channel rows. */
    private static final int MAX_RECIPE_HEIGHT = CompactPreviewLayout.RECIPE_HEIGHT;
    /** Shortest compact recipe: one row, kept for the layer and fullscreen buttons. */
    private static final int MIN_RECIPE_HEIGHT = CompactPreviewLayout.CONTROLS_Y
            + CompactPreviewLayout.controlsHeight(0);
    /**
     * Height of the recipe currently shown. JEI keeps a single background per category, so it has to follow the
     * displayed multiblock instead of being a fixed constant.
     */
    private static int currentRecipeHeight = MAX_RECIPE_HEIGHT;

    private final IDrawable background = new IDrawable() {

        @Override
        public int getWidth() {
            return CompactPreviewLayout.WIDTH;
        }

        @Override
        public int getHeight() {
            return backgroundHeight();
        }

        @Override
        public void draw(Minecraft minecraft, int xOffset, int yOffset) {}
    };
    private final IDrawable icon;
    private final IGuiHelper guiHelper;

    public MultiblockInfoCategory(IJeiHelpers helpers) {
        this.guiHelper = helpers.getGuiHelper();
        this.icon = guiHelper.drawableBuilder(GuiTextures.MULTIBLOCK_CATEGORY.imageLocation, 0, 0, 16, 16)
                .setTextureSize(16, 16).build();
    }

    /**
     * Height the current multiblock needs. JEI queries this while laying a page out and again while drawing the
     * recipe border, so the border ends right below the last channel row instead of keeping the full four-row area.
     * <p>
     * All recipes on one JEI page share this height. Shrinking is therefore only allowed while the page still fits a
     * single recipe; otherwise the maximum height is reported so multi-recipe pages keep their shared layout.
     */
    private static int backgroundHeight() {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (!(screen instanceof RecipesGui recipesGui) || recipesGui.getYSize() <= 0) {
            return MAX_RECIPE_HEIGHT;
        }
        // Header/tab area reserved by JEI inside its window; slightly over-estimated to stay conservative.
        int available = Math.max(1, recipesGui.getYSize() - 40);
        return currentRecipeHeight + 14 > available / 2 ? currentRecipeHeight : MAX_RECIPE_HEIGHT;
    }

    /** Called when JEI builds the layout of a multiblock recipe, i.e. whenever the displayed recipe changes. */
    public static void setRecipeHeight(int height) {
        currentRecipeHeight = Math.max(MIN_RECIPE_HEIGHT, Math.min(MAX_RECIPE_HEIGHT, height));
    }

    public static final List<MultiblockControllerBase> REGISTER = new LinkedList<>();

    public static void registerMultiblock(MultiblockControllerBase controllerBase) {
        REGISTER.add(controllerBase);
    }

    public static void registerRecipes(IModRegistry registry) {
        List<MultiblockInfoRecipeWrapper> recipes = new ArrayList<>();
        for (MultiblockControllerBase controller : REGISTER) {
            try {
                recipes.add(new MultiblockInfoRecipeWrapper(controller));
            } catch (Exception e) {
                GTLog.logger.error("Failed to create JEI multiblock info for: {}", controller.metaTileEntityId, e);
            }
        }
        registry.addRecipes(recipes, UID);
    }

    @NotNull
    @Override
    public String getUid() {
        return UID;
    }

    @NotNull
    @Override
    public String getTitle() {
        return I18n.format("gregtech.multiblock.title");
    }

    @NotNull
    @Override
    public String getModName() {
        return GTValues.MODID;
    }

    @NotNull
    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayout recipeLayout, MultiblockInfoRecipeWrapper recipeWrapper,
                          @NotNull IIngredients ingredients) {
        recipeWrapper.setRecipeLayout((RecipeLayout) recipeLayout, this.guiHelper);
    }
}
