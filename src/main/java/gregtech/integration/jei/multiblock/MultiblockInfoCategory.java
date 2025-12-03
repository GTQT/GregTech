package gregtech.integration.jei.multiblock;

import gregtech.api.GTValues;
import gregtech.api.gui.GuiTextures;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.GTLog;

import net.minecraft.client.resources.I18n;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.IJeiHelpers;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.gui.recipes.RecipeLayout;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MultiblockInfoCategory implements IRecipeCategory<MultiblockInfoRecipeWrapper> {

    public static final String UID = String.format("%s.multiblock_info", GTValues.MODID);
    private static final int MAX_THREADS = Math.min(4, Runtime.getRuntime().availableProcessors());

    private final IDrawable background;
    private final IDrawable icon;
    private final IGuiHelper guiHelper;

    public MultiblockInfoCategory(IJeiHelpers helpers) {
        this.guiHelper = helpers.getGuiHelper();
        this.background = this.guiHelper.createBlankDrawable(176, 184);
        this.icon = guiHelper.drawableBuilder(GuiTextures.MULTIBLOCK_CATEGORY.imageLocation, 0, 0, 16, 16)
                .setTextureSize(16, 16).build();
    }

    public static final List<MultiblockControllerBase> REGISTER = new LinkedList<>();

    public static void registerMultiblock(MultiblockControllerBase controllerBase) {
        REGISTER.add(controllerBase);
    }

    public static void registerRecipes(IModRegistry registry) {
        if (REGISTER.isEmpty()) return;

        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
        List<Future<MultiblockInfoRecipeWrapper>> futures = new ArrayList<>(REGISTER.size());

        // 提交所有任务
        for (MultiblockControllerBase controller : REGISTER) {
            futures.add(executor.submit(() -> new MultiblockInfoRecipeWrapper(controller)));
        }

        // 收集结果
        List<MultiblockInfoRecipeWrapper> recipes = new ArrayList<>(REGISTER.size());
        for (Future<MultiblockInfoRecipeWrapper> future : futures) {
            try {
                recipes.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                GTLog.logger.error("Failed to create multiblock info wrapper", e);
                Thread.currentThread().interrupt(); // 恢复中断状态
            }
        }

        // 关闭线程池
        executor.shutdown();
        try {
            if (!executor.awaitTermination(999, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 主线程注册
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
