package gregtech.common.metatileentities.electric;

import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.RecipeLogicEnergy;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class MetaTileEntityAutoChisel extends SimpleMachineMetaTileEntity {

    public MetaTileEntityAutoChisel(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap, ICubeRenderer renderer,
                                    int tier, boolean hasFrontFacing, Function<Integer, Integer> tankScalingFunction) {
        super(metaTileEntityId, recipeMap, renderer, tier, hasFrontFacing, tankScalingFunction);
        new AutoChiselRecipeLogic(this, recipeMap, () -> this.energyContainer);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityAutoChisel(metaTileEntityId, workable.getRecipeMap(), renderer, getTier(),
                hasFrontFacing(), getTankScalingFunction());
    }

    private static class AutoChiselRecipeLogic extends RecipeLogicEnergy {

        private static final Object INDEX_LOCK = new Object();
        // Shared across all AutoChisel instances — built once from the recipe map on first use.
        private static volatile Map<Item, List<Recipe>> inputIndex;

        public AutoChiselRecipeLogic(MetaTileEntity tileEntity, RecipeMap<?> recipeMap,
                                     Supplier<IEnergyContainer> energyContainer) {
            super(tileEntity, recipeMap, energyContainer);
        }

        private static Map<Item, List<Recipe>> buildIndex(RecipeMap<?> recipeMap) {
            Map<Item, List<Recipe>> idx = new HashMap<>();
            for (Recipe recipe : recipeMap.getRecipeList()) {
                for (GTRecipeInput input : recipe.getInputs()) {
                    if (input.isNonConsumable()) continue;
                    for (ItemStack match : input.getInputStacks()) {
                        idx.computeIfAbsent(match.getItem(), k -> new ArrayList<>()).add(recipe);
                    }
                    break; // index by first consumable input only
                }
            }
            // Wrap lists as unmodifiable to prevent accidental mutation
            idx.replaceAll((k, v) -> Collections.unmodifiableList(v));
            return Collections.unmodifiableMap(idx);
        }

        @Override
        protected boolean checkPreviousRecipe() {
            return false;
        }

        @Override
        protected Recipe findRecipe(long maxVoltage, IItemHandlerModifiable inputs,
                                    IMultipleTankHandler fluidInputs) {
            Map<Item, List<Recipe>> idx = inputIndex;
            if (idx == null) {
                synchronized (INDEX_LOCK) {
                    idx = inputIndex;
                    if (idx == null) {
                        inputIndex = idx = buildIndex(getRecipeMap());
                    }
                }
            }

            for (int i = 0; i < inputs.getSlots(); i++) {
                ItemStack stack = inputs.getStackInSlot(i);
                if (stack.isEmpty()) continue;

                List<Recipe> candidates = idx.get(stack.getItem());
                if (candidates == null) return null;

                for (Recipe recipe : candidates) {
                    if (recipe.getEUt() <= maxVoltage &&
                            recipe.matches(false, inputs, fluidInputs)) {
                        return recipe;
                    }
                }
                return null; // primary slot found but no match — stop scanning
            }
            return null;
        }
    }
}
