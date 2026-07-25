package gregtech.loaders.recipe;

import gregtech.common.ConfigHolder;
import gregtech.loaders.recipe.handlers.FluidRecipeHandler;
import gregtech.loaders.recipe.handlers.FissionReactorRecipeHandler;
import gregtech.loaders.recipe.handlers.NuclearReactorRecipeHandler;

/**
 * Copyright (C) SymmetricDevs 2025
 * 由 MeowmelMuku 于 2025 修改。
 * 修改内容：添加靶丸。
 * 此文件遵循 GPL-3.0 许可证，详情请见项目根目录的 LICENSE 文件。
 */
public class SCRecipeManager {

    public static void load() {
        if (ConfigHolder.nuclearMisc.disableAllRecipes) return;

        SCMiscRecipes.init();
        SCMachineRecipeLoader.init();
        SCMetaTileEnityLoader.init();
        SCMetaTileEntityMachineRecipeLoader.init();
        SCNuclearRecipes.init();
        FissionReactorRecipeHandler.register();
        //追加注册燃料配方
        SCDecayChamberRecipes.load();
        SCRTGFuel.load();
        FluidRecipeHandler.processCooling();
        NuclearReactorRecipeHandler.init();
    }

    public static void loadLatest() {
        SCRecipeModifications.load();
        FluidRecipeHandler.runRecipeGeneration();
    }
}
