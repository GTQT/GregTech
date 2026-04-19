package gregtech.common.metatileentities.workbench;

import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.items.toolitem.ItemGTToolbelt;
import gregtech.api.mui.sync.PagedWidgetSyncHandler;
import gregtech.api.mui.sync.RecipeSyncHandler;
import gregtech.api.util.DummyContainer;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.api.util.ItemStackHashStrategy;
import gregtech.common.crafting.ShapedOreEnergyTransferRecipe;
import gregtech.common.mui.widget.workbench.CraftingInputSlot;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.network.NetworkUtils;
import it.unimi.dsi.fastutil.ints.Int2BooleanArrayMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.transfer.RecipeTransferErrorInternal;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

public class CraftingRecipeLogic extends RecipeSyncHandler {

    // client only
    public static final int UPDATE_INGREDIENTS = 1;
    public static final int RESET_INGREDIENTS = 2;
    public static final int SYNC_STACK = 3;

    // server only
    public static final int UPDATE_MATRIX = 0;

    private final World world;
    private IItemHandlerModifiable availableHandlers;
    private final ItemStackHashStrategy strategy = ItemStackHashStrategy.builder()
            .compareItem(true)
            .compareMetadata(true)
            .build();

    /**
     * Used to lookup a list of slots for a given stack
     * filled by {@link CraftingRecipeLogic#refreshStackMap()}
     **/
    private final Map<ItemStack, IntSet> stackLookupMap = new Object2ObjectOpenCustomHashMap<>(this.strategy);

    /**
     * List of items needed to complete the crafting recipe, filled by
     * {@link CraftingRecipeLogic#detectAndSendChanges(boolean)} )}
     **/
    private final Object2IntMap<ItemStack> requiredItems = new Object2IntOpenCustomHashMap<>(
            this.strategy);

    private final Int2IntMap compactedIndexes = new Int2IntArrayMap(9);
    private final Int2IntMap slotMap = new Int2IntArrayMap();

    private final Int2ObjectMap<Object2BooleanMap<ItemStack>> replaceAttemptMap = new Int2ObjectArrayMap<>();
    private final InventoryCrafting craftingMatrix;
    private final IInventory craftingResultInventory = new InventoryCraftResult();
    private final CachedRecipeData cachedRecipeData;
    private final CraftingInputSlot[] inputSlots = new CraftingInputSlot[9];

    // ==================== 库存快照（增量变化检测） ====================
    /** 上一次的库存快照，用于增量比较判断库存是否变化 */
    private ItemStack[] lastSnapshot = new ItemStack[0];
    /** 标记快照是否已被外部操作（如库存结构变化）强制失效 */
    private boolean snapshotDirty = true;
    /** 配方记忆引用，用于在合成网格填充时自动记忆配方 */
    private CraftingRecipeMemory recipeMemory;
    /** 合成链执行中临时禁止自动记忆 */
    private boolean suppressAutoMemorize = false;

    public CraftingRecipeLogic(World world, IItemHandlerModifiable handlers, IItemHandlerModifiable craftingMatrix) {
        this.world = world;
        this.availableHandlers = handlers;
        this.craftingMatrix = wrapHandler(craftingMatrix);
        this.cachedRecipeData = new CachedRecipeData();
    }

    public IInventory getCraftingResultInventory() {
        return craftingResultInventory;
    }

    public InventoryCrafting getCraftingMatrix() {
        return this.craftingMatrix;
    }

    public IItemHandlerModifiable getAvailableHandlers() {
        return this.availableHandlers;
    }

    public void setRecipeMemory(CraftingRecipeMemory memory) {
        this.recipeMemory = memory;
    }

    public void updateSlotMap(int offset, int slot) {
        slotMap.put(offset + slot, slotMap.size());
    }

    public void clearSlotMap() {
        slotMap.clear();
    }

    public void updateInventory(IItemHandlerModifiable handler) {
        this.availableHandlers = handler;
        // 库存结构变化时清空替代品缓存并强制快照失效
        this.replaceAttemptMap.clear();
        this.snapshotDirty = true;
    }

    public void clearCraftingGrid() {
        fillCraftingGrid(Collections.emptyMap());
    }

    public void fillCraftingGrid(Map<Integer, ItemStack> ingredients) {
        for (int i = 0; i < craftingMatrix.getSizeInventory(); i++) {
            craftingMatrix.setInventorySlotContents(i, ingredients.getOrDefault(i, ItemStack.EMPTY));
        }
        syncMatrix();
        updateCurrentRecipe();
    }

    public void setInputSlot(CraftingInputSlot slot, int index) {
        this.inputSlots[index] = slot;
    }

    public boolean performRecipe() {
        if (!isRecipeValid()) return false;
        // Chain crafting temporarily swaps the crafting matrix, so recompute inputs right before consuming.
        snapshotDirty = true;
        updateInputSlots();
        return attemptMatchRecipe() && consumeRecipeItems();
    }

    /**
     * 执行合成链中的一个步骤：临时将指定配方加载到合成网格中，执行合成，
     * 产物放入库存，然后恢复原来的合成网格内容。
     *
     * @param step 合成链步骤
     * @return 是否成功
     */
    public boolean executeChainStep(CraftingChainSolver.ChainStep step) {
        // 保存当前合成网格
        ItemStack[] savedGrid = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            savedGrid[i] = craftingMatrix.getStackInSlot(i).copy();
        }

        boolean success = false;
        try {
            // 合成链执行中禁止自动记忆，避免临时网格内容污染记忆列表
            suppressAutoMemorize = true;

            // 加载子配方到合成网格
            for (int i = 0; i < 9; i++) {
                craftingMatrix.setInventorySlotContents(i, step.recipe.getCraftingMatrixSlot(i).copy());
            }
            updateCurrentRecipe();

            if (!isRecipeValid()) return false;

            // 强制刷新 stackLookupMap、requiredItems、compactedIndexes
            snapshotDirty = true;
            updateInputSlots();

            // 尝试消耗材料
            if (!consumeRecipeItems()) return false;

            // 产物放入库存
            ItemStack result = step.matchedRecipe.getCraftingResult(craftingMatrix);
            if (!result.isEmpty()) {
                ItemStack remainder = GTTransferUtils.insertItem(availableHandlers, result.copy(), false);
                success = remainder.isEmpty();
            }
        } finally {
            // 恢复原来的合成网格
            for (int i = 0; i < 9; i++) {
                craftingMatrix.setInventorySlotContents(i, savedGrid[i]);
            }
            suppressAutoMemorize = false;
            updateCurrentRecipe();
            // 恢复后强制刷新
            snapshotDirty = true;
        }

        return success;
    }

    public boolean isRecipeValid() {
        return cachedRecipeData.getRecipe() != null && cachedRecipeData.matches(craftingMatrix, this.world);
    }

    /**
     * Attempts to match the crafting matrix against all connected inventories
     *
     * @return true if all items matched
     */
    public boolean attemptMatchRecipe() {
        for (CraftingInputSlot slot : this.inputSlots) {
            if (!slot.hasIngredients) {
                return false;
            }
        }
        return true;
    }

    protected boolean consumeRecipeItems() {
        if (requiredItems.isEmpty()) {
            return false;
        }
        Int2IntMap gatheredItems = new Int2IntOpenHashMap();

        for (var entry : requiredItems.entrySet()) {
            ItemStack stack = entry.getKey();
            int requestedAmount = entry.getValue();
            var slotList = stackLookupMap.get(stack);

            for (int slot : slotList) {
                var extracted = availableHandlers.extractItem(slot, requestedAmount, true);
                // 使用 merge 进行累加，避免同一 slot 被多种物品匹配时覆盖
                gatheredItems.merge(slot, extracted.getCount(), Integer::sum);
                requestedAmount -= extracted.getCount();
            }
            // not enough to satisfy the recipe, return false
            if (requestedAmount > 0) return false;
        }

        for (var gathered : gatheredItems.entrySet()) {
            int slot = gathered.getKey(), amount = gathered.getValue();
            var stack = availableHandlers.getStackInSlot(slot);
            boolean hasContainer = stack.getItem().hasContainerItem(stack);

            // GT 工具耐久检查：确保工具有足够的耐久完成合成
            if (hasContainer && stack.getItem() instanceof IGTTool) {
                int damagePerCraft = ((IGTTool) stack.getItem()).getToolStats()
                        .getToolDamagePerCraft(stack);
                int remaining = stack.getMaxDamage() - stack.getItemDamage();
                if (remaining < damagePerCraft) {
                    return false;
                }
            }

            if (!hasContainer) {
                availableHandlers.extractItem(slot, amount, false);
            } else if (stack.getCount() > 1) {
                ItemStack newStack = ForgeHooks.getContainerItem(stack.splitStack(1));
                if (!newStack.isEmpty())
                    GTTransferUtils.insertItem(this.availableHandlers, newStack, false);
            } else {
                availableHandlers.setStackInSlot(slot, ForgeHooks.getContainerItem(stack));
            }
        }
        // we've checked everything, return true
        return true;
    }

    /**
     * <p>
     * Searches through all connected inventories for a replacement stack that can be used in the recipe
     * </p>
     *
     * @param craftingIndex Index of the current crafting slot
     * @param stack         The stack to find a substitute for
     * @return a valid replacement stack, or {@link ItemStack#EMPTY} if no valid replacements exist
     */
    public ItemStack findSubstitute(int craftingIndex, ItemStack stack) {
        Object2BooleanMap<ItemStack> map = replaceAttemptMap.computeIfAbsent(craftingIndex,
                (m) -> new Object2BooleanOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount()));

        ItemStack substitute = ItemStack.EMPTY;

        var recipe = getCachedRecipe();
        int index = compactedIndexes.get(craftingIndex);

        // 遍历 stackLookupMap 的物品类型集合而非所有库存槽位，减少重复检查
        for (var entry : stackLookupMap.entrySet()) {
            var itemStack = entry.getKey();
            if (itemStack.isEmpty() || this.strategy.equals(itemStack, stack)) continue;

            boolean matchedPreviously = false;
            if (map.containsKey(itemStack)) {
                if (map.getBoolean(itemStack)) {
                    matchedPreviously = true;
                } else {
                    // 已确认不匹配，跳过
                    continue;
                }
            }

            if (!matchedPreviously) {
                boolean matched = false;
                if (!(recipe instanceof IShapedRecipe)) {
                    for (Ingredient ing : recipe.getIngredients()) {
                        if (ing.apply(itemStack)) {
                            matched = true;
                            break;
                        }
                    }
                } else {
                    matched = cachedRecipeData.canIngredientApply(index, itemStack);
                }
                if (!matched) {
                    map.put(GTUtility.copy(1, itemStack), false);
                    continue;
                }
            }

            ItemStack previousResult = recipe.getCraftingResult(craftingMatrix);

            craftingMatrix.setInventorySlotContents(craftingIndex, itemStack);
            var newResult = recipe.getCraftingResult(craftingMatrix);
            if ((cachedRecipeData.matches(craftingMatrix, world) &&
                    ItemStack.areItemStacksEqual(newResult, previousResult)) ||
                    recipe instanceof ShapedOreEnergyTransferRecipe) {
                craftingMatrix.setInventorySlotContents(craftingIndex, stack);
                map.put(GTUtility.copy(1, itemStack), true);
                substitute = itemStack;
                break;
            }
            map.put(GTUtility.copy(1, itemStack), false);
            craftingMatrix.setInventorySlotContents(craftingIndex, stack);
        }
        return substitute;
    }

    /**
     * Attempts to extract the given stack from connected inventories
     *
     * @param craftingIndex current crafting index
     * @param itemStack     stack from the crafting matrix
     * @return true if the stack was successfully extracted or the stack is empty
     */
    private boolean simulateExtractItem(int craftingIndex, ItemStack itemStack, int count) {
        if (itemStack.isEmpty()) return true;
        if (!stackLookupMap.containsKey(itemStack)) return false;

        int extracted = 0;

        for (int slot : stackLookupMap.get(itemStack)) {
            var slotStack = availableHandlers.extractItem(slot, count, true);
            // we are certain the stack map is correct
            if (slotStack.getItem() instanceof ItemGTToolbelt) {
                ItemGTToolbelt.setCraftingSlot(slotMap.get(slot), (EntityPlayerMP) getSyncManager().getPlayer());
            }
            if (cachedRecipeData.canIngredientApply(compactedIndexes.get(craftingIndex), slotStack)) {
                extracted += slotStack.getCount();
                if (extracted >= count) return true;
            }
        }

        return false;
    }

    public void updateCurrentRecipe() {
        if (!cachedRecipeData.matches(craftingMatrix, world)) {
            IRecipe newRecipe = CraftingManager.findMatchingRecipe(craftingMatrix, world);
            ItemStack resultStack = ItemStack.EMPTY;
            if (newRecipe != null) {
                resultStack = newRecipe.getCraftingResult(craftingMatrix);
            }
            this.craftingResultInventory.setInventorySlotContents(0, resultStack);
            this.cachedRecipeData.setRecipe(newRecipe);
            // 配方变化时清空替代品缓存，防止内存泄漏和缓存过期
            this.replaceAttemptMap.clear();
            // 合成网格填充时自动记忆配方（无需等到合成成功）
            if (recipeMemory != null && !resultStack.isEmpty() && !suppressAutoMemorize) {
                recipeMemory.notifyRecipePerformed(craftingMatrix, resultStack);
            }
        }
    }

    public IRecipe getCachedRecipe() {
        return this.cachedRecipeData.getRecipe();
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        var recipe = getCachedRecipe();
        if (recipe == null) {
            var prevRecipe = cachedRecipeData.getPreviousRecipe();
            if (prevRecipe == null) return;
            cachedRecipeData.setRecipe(null);
            for (CraftingInputSlot inputSlot : this.inputSlots) {
                inputSlot.hasIngredients = true;
            }
            syncToClient(RESET_INGREDIENTS);
            return;
        }

        Int2BooleanMap map = updateInputSlots();

        // only sync when something has changed
        if (!map.isEmpty()) {
            syncToClient(UPDATE_INGREDIENTS, buffer -> {
                buffer.writeByte(map.size());
                for (var set : map.entrySet()) {
                    buffer.writeByte(set.getKey());
                    buffer.writeBoolean(set.getValue());
                }
            });
        }
    }

    /**
     * Updates each input slot for if a valid item exists for that slot
     *
     * @return a map of slots that has changed since last time, if any
     */
    private Int2BooleanMap updateInputSlots() {
        compactedIndexes.clear();
        requiredItems.clear();
        refreshStackMap();

        Int2BooleanMap map = new Int2BooleanArrayMap();
        int next = 0;
        for (CraftingInputSlot slot : this.inputSlots) {
            boolean hadIngredients = slot.hasIngredients;

            // check if existing stack works
            var slotStack = slot.getStack();
            if (slotStack.isEmpty()) {
                if (!hadIngredients) {
                    slot.hasIngredients = true;
                    map.put(slot.getIndex(), slot.hasIngredients);
                }
                continue;
            }

            compactedIndexes.put(slot.getIndex(), next++);
            int count = requiredItems.getOrDefault(slotStack, 0) + 1;
            slot.hasIngredients = simulateExtractItem(slot.getIndex(), slotStack, count);

            if (slot.hasIngredients) {
                requiredItems.put(GTUtility.copy(1, slotStack), count);
            } else {
                // check if substitute exists
                ItemStack substitute = findSubstitute(slot.getIndex(), slotStack);
                if (!substitute.isEmpty()) {
                    count = requiredItems.getOrDefault(substitute, 0) + 1;
                    slot.hasIngredients = simulateExtractItem(slot.getIndex(), substitute, count);
                    requiredItems.put(GTUtility.copy(1, substitute), count);
                }
            }

            if (hadIngredients != slot.hasIngredients)
                map.put(slot.getIndex(), slot.hasIngredients);
        }
        return map;
    }

    /**
     * Searches available handlers and
     * adds the stack and slots the stack lookup map.
     * 使用增量变化检测：通过快照比较判断库存是否变化，未变化时跳过重建。
     */
    public void refreshStackMap() {
        int slots = this.availableHandlers.getSlots();

        // 检查库存是否发生了变化
        boolean changed = snapshotDirty;
        if (!changed) {
            if (lastSnapshot.length != slots) {
                changed = true;
            } else {
                for (int i = 0; i < slots; i++) {
                    ItemStack current = this.availableHandlers.getStackInSlot(i);
                    ItemStack last = lastSnapshot[i];
                    if (!ItemStack.areItemStacksEqual(current, last)) {
                        changed = true;
                        break;
                    }
                }
            }
        }

        if (!changed) return;

        // 库存发生了变化，更新快照并重建 stackLookupMap
        snapshotDirty = false;
        if (lastSnapshot.length != slots) {
            lastSnapshot = new ItemStack[slots];
        }

        stackLookupMap.clear();
        for (int i = 0; i < slots; i++) {
            var curStack = this.availableHandlers.getStackInSlot(i);
            lastSnapshot[i] = curStack.isEmpty() ? ItemStack.EMPTY : curStack.copy();
            if (curStack.isEmpty()) continue;

            IntSet slotSet;
            if (stackLookupMap.containsKey(curStack)) {
                slotSet = stackLookupMap.get(curStack);
            } else {
                stackLookupMap.put(GTUtility.copy(1, curStack), slotSet = new IntArraySet());
            }
            slotSet.add(i);
        }
    }

    public void writeMatrix(PacketBuffer buffer) {
        buffer.writeVarInt(craftingMatrix.getSizeInventory());
        for (int i = 0; i < craftingMatrix.getSizeInventory(); i++) {
            NetworkUtils.writeItemStack(buffer, craftingMatrix.getStackInSlot(i));
        }
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) {
        if (id == UPDATE_INGREDIENTS) {
            int size = buf.readByte();
            for (int i = 0; i < size; i++) {
                this.inputSlots[buf.readByte()].hasIngredients = buf.readBoolean();
            }
        } else if (id == SYNC_STACK) {
            getSyncManager().setCursorItem(NetworkUtils.readItemStack(buf));
        } else if (id == RESET_INGREDIENTS) {
            for (CraftingInputSlot inputSlot : this.inputSlots) {
                inputSlot.hasIngredients = true;
            }
        }
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) {
        if (id == UPDATE_MATRIX) {
            int size = buf.readVarInt();
            for (int i = 0; i < size; i++) {
                this.craftingMatrix.setInventorySlotContents(i, NetworkUtils.readItemStack(buf));
            }
            this.updateCurrentRecipe();
        }
    }

    public void syncMatrix() {
        if (getSyncManager().isClient())
            syncToServer(UPDATE_MATRIX, this::writeMatrix);
    }

    // TODO: extract my recipe transfer into a dedicated branch and improve it.
    @Override
    public IRecipeTransferError receiveRecipe(@NotNull IRecipeLayout recipeLayout, boolean maxTransfer,
                                              boolean simulate) {
        if (!recipeLayout.getRecipeCategory().getUid().equals("minecraft.crafting")) {
            return RecipeTransferErrorInternal.INSTANCE;
        }

        if (simulate) {
            // todo highlighting in JEI?
            return null;
        }

        var matrix = extractMatrix(recipeLayout.getItemStacks());
        fillCraftingGrid(matrix);
        ((PagedWidgetSyncHandler) getSyncManager().getSyncHandler("page_controller:0")).setPage(0);
        return null;
    }

    private Int2ObjectMap<ItemStack> extractMatrix(IGuiItemStackGroup stackGroup) {
        var ingredients = stackGroup.getGuiIngredients();
        Int2ObjectMap<ItemStack> matrix = new Int2ObjectArrayMap<>(9);
        for (var slot : ingredients.keySet()) {
            if (slot != 0) {
                var ing = ingredients.get(slot).getDisplayedIngredient();
                if (ing == null) continue;
                matrix.put(slot - 1, ingredients.get(slot).getDisplayedIngredient());
            }
        }
        return matrix;
    }

    public static InventoryCrafting wrapHandler(IItemHandlerModifiable handler) {
        return new InventoryCrafting(new DummyContainer(), 3, 3) {

            @Override
            public ItemStack getStackInRowAndColumn(int row, int column) {
                int index = row + (3 * column);
                return handler.getStackInSlot(index);
            }

            @Override
            public ItemStack getStackInSlot(int index) {
                return handler.getStackInSlot(index);
            }

            @Override
            public void setInventorySlotContents(int index, ItemStack stack) {
                handler.setStackInSlot(index, GTUtility.copy(1, stack));
            }
        };
    }
}
