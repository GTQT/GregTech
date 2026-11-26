package gregtech.api.recipes.logic;

import gregtech.api.capability.impl.AbstractRecipeLogic;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Registry of default {@link IRecipeCondition} factories attached to every
 * {@link AbstractRecipeLogic} on construction, keeping recipe logics decoupled
 * from concrete condition implementations.
 */
public final class RecipeConditionRegistry {

    private static final List<Function<AbstractRecipeLogic, IRecipeCondition>> DEFAULT_FACTORIES =
            new CopyOnWriteArrayList<>();

    static {
        registerDefault(logic -> new CleanroomRecipeCondition(logic::getMetaTileEntity, logic::setWhyFailed));
    }

    private RecipeConditionRegistry() {}

    public static void registerDefault(@NotNull Function<AbstractRecipeLogic, IRecipeCondition> factory) {
        DEFAULT_FACTORIES.add(factory);
    }

    public static void attachDefaults(@NotNull AbstractRecipeLogic logic) {
        for (Function<AbstractRecipeLogic, IRecipeCondition> factory : DEFAULT_FACTORIES) {
            IRecipeCondition condition = factory.apply(logic);
            if (condition != null) {
                logic.addRecipeCondition(condition);
            }
        }
    }
}
