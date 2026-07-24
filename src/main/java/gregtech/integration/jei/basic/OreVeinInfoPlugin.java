package gregtech.integration.jei.basic;

import gregtech.api.worldgen.config.BedrockFluidDepositDefinition;
import gregtech.api.worldgen.config.OreDepositDefinition;
import gregtech.api.worldgen.config.WorldGenRegistry;
import gregtech.common.items.OrbItems;

import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.DimensionManager;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeRegistryPlugin;
import mezz.jei.api.recipe.IRecipeWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JEI plugin that handles reverse lookup: clicking a planet/display item
 * shows all ore and fluid veins that generate in the corresponding dimension.
 */
public class OreVeinInfoPlugin implements IRecipeRegistryPlugin {

    private final Int2ObjectMap<List<GTOreInfo>> oreVeinCache = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<List<GTFluidVeinInfo>> fluidVeinCache = new Int2ObjectOpenHashMap<>();

    private List<GTOreInfo> oreWrappers;
    private List<GTFluidVeinInfo> fluidWrappers;

    @Override
    public <V> List<String> getRecipeCategoryUids(IFocus<V> focus) {
        if (focus.getValue() instanceof ItemStack stack && !stack.isEmpty() &&
                OrbItems.getDimension(stack) != null) {
            List<String> uids = new ArrayList<>(2);
            uids.add(GTOreCategory.UID);
            uids.add(GTFluidVeinCategory.UID);
            return uids;
        }
        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IRecipeWrapper, V> List<T> getRecipeWrappers(IRecipeCategory<T> recipeCategory,
                                                                     IFocus<V> focus) {
        if (!(focus.getValue() instanceof ItemStack stack) || stack.isEmpty()) {
            return Collections.emptyList();
        }

        Integer dim = OrbItems.getDimension(stack);
        if (dim == null || !DimensionManager.isDimensionRegistered(dim)) {
            return Collections.emptyList();
        }

        WorldProvider provider = DimensionManager.createProviderFor(dim);

        if (GTOreCategory.UID.equals(recipeCategory.getUid())) {
            return (List<T>) oreVeinCache.computeIfAbsent(dim, id -> {
                List<GTOreInfo> result = new ArrayList<>();
                for (GTOreInfo info : getOreWrappers()) {
                    if (info.getDefinition().getDimensionFilter().test(provider)) {
                        result.add(info);
                    }
                }
                return result;
            });
        }

        if (GTFluidVeinCategory.UID.equals(recipeCategory.getUid())) {
            return (List<T>) fluidVeinCache.computeIfAbsent(dim, id -> {
                List<GTFluidVeinInfo> result = new ArrayList<>();
                for (GTFluidVeinInfo info : getFluidWrappers()) {
                    if (info.getDefinition().getDimensionFilter().test(provider)) {
                        result.add(info);
                    }
                }
                return result;
            });
        }

        return Collections.emptyList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IRecipeWrapper> List<T> getRecipeWrappers(IRecipeCategory<T> recipeCategory) {
        if (GTOreCategory.UID.equals(recipeCategory.getUid())) {
            return (List<T>) getOreWrappers();
        }
        if (GTFluidVeinCategory.UID.equals(recipeCategory.getUid())) {
            return (List<T>) getFluidWrappers();
        }
        return Collections.emptyList();
    }

    private List<GTOreInfo> getOreWrappers() {
        if (oreWrappers == null) {
            List<OreDepositDefinition> veins = WorldGenRegistry.getOreDeposits();
            List<GTOreInfo> wrappers = new ArrayList<>(veins.size());
            for (OreDepositDefinition vein : veins) {
                wrappers.add(new GTOreInfo(vein));
            }
            oreWrappers = wrappers;
        }
        return oreWrappers;
    }

    private List<GTFluidVeinInfo> getFluidWrappers() {
        if (fluidWrappers == null) {
            List<BedrockFluidDepositDefinition> veins = WorldGenRegistry.getBedrockVeinDeposits();
            List<GTFluidVeinInfo> wrappers = new ArrayList<>(veins.size());
            for (BedrockFluidDepositDefinition vein : veins) {
                wrappers.add(new GTFluidVeinInfo(vein));
            }
            fluidWrappers = wrappers;
        }
        return fluidWrappers;
    }
}
