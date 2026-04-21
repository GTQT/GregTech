package gregtech.common.metatileentities.workbench;

import gregtech.api.util.ItemStackHashStrategy;
import gregtech.common.metatileentities.workbench.CraftingRecipeMemory.MemorizedRecipe;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandlerModifiable;

import it.unimi.dsi.fastutil.Hash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;

/**
 * 合成链求解器。
 * 从目标配方出发，递归查找记忆配方中可以提供中间产物的配方，
 * 构建拓扑排序的执行计划。
 */
public class CraftingChainSolver {

    private final Hash.Strategy<ItemStack> strategy = ItemStackHashStrategy.builder()
            .compareItem(true)
            .compareMetadata(true)
            .build();

    /**
     * 合成链中的一个步骤。
     */
    public static class ChainStep {

        /** 该步骤使用的配方（记忆配方的合成网格快照） */
        public final MemorizedRecipe recipe;
        /** 该配方的 IRecipe 匹配结果 */
        public final IRecipe matchedRecipe;
        /** 需要执行的次数 */
        public int count;

        public ChainStep(MemorizedRecipe recipe, IRecipe matchedRecipe, int count) {
            this.recipe = recipe;
            this.matchedRecipe = matchedRecipe;
            this.count = count;
        }
    }

    /**
     * 合成链求解结果。
     */
    public static class ChainResult {

        /** 按拓扑序排列的执行步骤（从底层到顶层） */
        public final List<ChainStep> steps;
        /** 缺失的基础材料列表（物品→需要数量） */
        public final Map<ItemStack, Integer> missingItems;
        /** 是否所有材料都可用 */
        public final boolean canExecute;

        public ChainResult(List<ChainStep> steps, Map<ItemStack, Integer> missingItems, boolean canExecute) {
            this.steps = steps;
            this.missingItems = missingItems;
            this.canExecute = canExecute;
        }
    }

    /**
     * 从目标配方出发，求解完整的合成链。
     *
     * @param targetRecipe      目标配方（当前合成网格中的配方）
     * @param allRecipes        所有可用的记忆配方（临时 + 锁定）
     * @param availableHandlers 可用的物品库存
     * @param world             世界实例
     * @param countItemFunc     利用 stackLookupMap 索引快速统计库存中物品数量的函数
     * @return 合成链求解结果
     */
    public ChainResult solve(MemorizedRecipe targetRecipe, List<MemorizedRecipe> allRecipes,
                             IItemHandlerModifiable availableHandlers, World world,
                             ToIntFunction<ItemStack> countItemFunc) {
        List<ChainStep> steps = new ArrayList<>();
        Map<ItemStack, Integer> missingItems = new LinkedHashMap<>();
        // 使用 identity-based Set 防止循环引用（兼容虚拟配方的 index=-1）
        Set<MemorizedRecipe> visiting = Collections.newSetFromMap(new IdentityHashMap<>());

        // 预建 output→recipe 索引，将 findRecipeProducing 从 O(N) 降为 O(1)
        Map<ItemStack, MemorizedRecipe> outputIndex = new Object2ObjectOpenCustomHashMap<>(strategy);
        for (MemorizedRecipe recipe : allRecipes) {
            if (recipe != null) {
                outputIndex.putIfAbsent(recipe.getRecipeResult(), recipe);
            }
        }

        // 预建库存可用数量缓存，同物品只扫描一次
        Map<ItemStack, Integer> availableCache = new Object2ObjectOpenCustomHashMap<>(strategy);

        resolveRecipe(targetRecipe, 1, availableHandlers, world,
                steps, missingItems, visiting, outputIndex, availableCache, countItemFunc);

        boolean canExecute = missingItems.isEmpty();
        return new ChainResult(steps, missingItems, canExecute);
    }

    /**
     * 递归求解一个配方的所有依赖。
     * availableCache 在求解过程中会被修改：预定材料时扣除、子配方产出时增加。
     *
     * @return true 如果配方成功求解（matchedRecipe 有效），false 如果配方无效
     */
    private boolean resolveRecipe(MemorizedRecipe recipe, int requiredCount,
                                  IItemHandlerModifiable availableHandlers,
                                  World world,
                                  List<ChainStep> steps,
                                  Map<ItemStack, Integer> missingItems,
                                  Set<MemorizedRecipe> visiting,
                                  Map<ItemStack, MemorizedRecipe> outputIndex,
                                  Map<ItemStack, Integer> availableCache,
                                  ToIntFunction<ItemStack> countItemFunc) {
        if (!visiting.add(recipe)) return false;

        // 构建该配方的临时合成网格
        InventoryCrafting tempMatrix = new InventoryCrafting(
                new gregtech.api.util.DummyContainer(), 3, 3);
        for (int i = 0; i < 9; i++) {
            tempMatrix.setInventorySlotContents(i, recipe.getCraftingMatrixSlot(i));
        }

        // 查找匹配的 IRecipe
        IRecipe matchedRecipe = CraftingManager.findMatchingRecipe(tempMatrix, world);
        if (matchedRecipe == null) {
            visiting.remove(recipe);
            return false;
        }

        // 计算该配方的产出数量和需要的执行次数
        ItemStack output = matchedRecipe.getCraftingResult(tempMatrix);
        int outputCount = output.getCount();
        int timesToCraft = (int) Math.ceil((double) requiredCount / outputCount);

        // 检查每个原料
        for (int i = 0; i < 9; i++) {
            ItemStack ingredient = recipe.getCraftingMatrixSlot(i);
            if (ingredient.isEmpty()) continue;

            // 工具类物品（有 containerItem）合成后不会被消耗，只需要1个即可复用
            boolean isTool = ingredient.getItem().hasContainerItem(ingredient);

            int totalNeeded = isTool ? 1 : ingredient.getCount() * timesToCraft;

            // 查询库存可用量（首次查询使用索引 O(1) 查找并缓存）
            int available = availableCache.computeIfAbsent(ingredient,
                    countItemFunc::applyAsInt);

            if (isTool) {
                // 工具只需检查库存中是否存在，不从 availableCache 扣除（合成后仍在）
                if (available >= 1) continue;
                addMissing(missingItems, ingredient, 1);
                continue;
            }

            // 从库存中预定材料（扣除可用量）
            int fromInventory = Math.min(available, totalNeeded);
            if (fromInventory > 0) {
                updateAvailable(availableCache, ingredient, -fromInventory);
            }

            int deficit = totalNeeded - fromInventory;
            if (deficit <= 0) continue;

            // 查找是否有记忆配方可以生产这个材料（使用索引 O(1) 查找）
            MemorizedRecipe subRecipe = outputIndex.get(ingredient);
            if (subRecipe != null && !visiting.contains(subRecipe)) {
                // 递归求解子配方，如果子配方无效则回退为缺失材料
                boolean subResolved = resolveRecipe(subRecipe, deficit, availableHandlers,
                        world, steps, missingItems, visiting, outputIndex, availableCache, countItemFunc);
                if (!subResolved) {
                    addMissing(missingItems, ingredient, deficit);
                }
            } else {
                addMissing(missingItems, ingredient, deficit);
            }
        }

        // 子配方产出的中间产品加入可用缓存
        int totalProduced = outputCount * timesToCraft;
        // 被父配方消耗的量 = requiredCount，多余的可供其他步骤使用
        int surplus = totalProduced - requiredCount;
        if (surplus > 0) {
            updateAvailable(availableCache, output, surplus);
        }

        // 后序添加（子配方在前，父配方在后 → 拓扑序）
        steps.add(new ChainStep(recipe, matchedRecipe, timesToCraft));
        visiting.remove(recipe);
        return true;
    }

    /**
     * 查找能够生产指定物品的记忆配方。
     * @deprecated 已被 outputIndex Map 替代，保留仅供参考
     */
    @SuppressWarnings("unused")
    private MemorizedRecipe findRecipeProducing(ItemStack target, List<MemorizedRecipe> allRecipes) {
        for (MemorizedRecipe recipe : allRecipes) {
            if (recipe == null) continue;
            if (strategy.equals(recipe.getRecipeResult(), target)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * 统计库存中某种物品的可用数量。
     */
    private int countAvailable(ItemStack target, IItemHandlerModifiable handlers) {
        int count = 0;
        for (int i = 0; i < handlers.getSlots(); i++) {
            ItemStack stack = handlers.getStackInSlot(i);
            if (!stack.isEmpty() && strategy.equals(stack, target)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * 更新 availableCache 中某种物品的可用数量。
     * delta 为正数表示增加（子配方产出），负数表示扣除（预定材料）。
     */
    private void updateAvailable(Map<ItemStack, Integer> availableCache, ItemStack stack, int delta) {
        int current = availableCache.getOrDefault(stack, 0);
        availableCache.put(stack, Math.max(0, current + delta));
    }

    /**
     * 将缺失材料累加到缺失列表中。
     */
    private void addMissing(Map<ItemStack, Integer> missingItems, ItemStack stack, int amount) {
        for (Map.Entry<ItemStack, Integer> entry : missingItems.entrySet()) {
            if (strategy.equals(entry.getKey(), stack)) {
                entry.setValue(entry.getValue() + amount);
                return;
            }
        }
        missingItems.put(stack.copy(), amount);
    }
}
