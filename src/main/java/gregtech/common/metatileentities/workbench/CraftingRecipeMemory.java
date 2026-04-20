package gregtech.common.metatileentities.workbench;

import gregtech.api.util.ItemStackHashStrategy;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import it.unimi.dsi.fastutil.Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CraftingRecipeMemory extends SyncHandler {

    public static final int TEMP_RECIPE_SLOTS = 9;
    public static final int LOCKED_RECIPE_SLOTS = 45;

    // client and server
    public static final int UPDATE_RECIPES = 1;

    // client only
    public static final int SYNC_RECIPE = 4;
    public static final int OFFSET_RECIPE = 5;
    public static final int REMOVE_RECIPE = 2;
    public static final int MAKE_RECIPE = 3;
    public static final int UPDATE_LOGIC = 6;

    // server only
    public static final int MOUSE_CLICK = 7;

    private final Hash.Strategy<ItemStack> strategy = ItemStackHashStrategy.builder()
            .compareItem(true)
            .compareMetadata(true)
            .build();

    private final MemorizedRecipe[] memorizedRecipes;
    private final IItemHandlerModifiable craftingMatrix;

    // ==================== 配方列表缓存 ====================
    /** 缓存的非空配方列表，避免每次调用 getAllRecipes 都重新创建 */
    private List<MemorizedRecipe> cachedAllRecipes;
    /** 配方列表是否需要重建 */
    private boolean recipeListDirty = true;
    /** 配方内容或列表变化的全局版本号，供外部检测变化使用 */
    private int recipeVersion = 0;

    public CraftingRecipeMemory(int memorySize, IItemHandlerModifiable craftingMatrix) {
        this.memorizedRecipes = new MemorizedRecipe[memorySize];
        this.craftingMatrix = craftingMatrix;
    }

    public void loadRecipe(int index) {
        if (index < 0 || index >= memorizedRecipes.length) return;
        MemorizedRecipe recipe = memorizedRecipes[index];
        if (recipe != null) {
            // 手动加载配方时清除保护标志
            recipe.matrixProtected = false;
            copyInventoryItems(recipe.craftingMatrix, this.craftingMatrix);
            getRecipeLogic().updateCurrentRecipe();
            syncToClient(UPDATE_LOGIC);
        }
    }

    public CraftingRecipeLogic getRecipeLogic() {
        return (CraftingRecipeLogic) getSyncManager().getSyncHandler("recipe_logic:0");
    }

    @Nullable
    public MemorizedRecipe getRecipeAtIndex(int index) {
        if (index < 0 || index >= memorizedRecipes.length) return null;
        return memorizedRecipes[index];
    }

    @SuppressWarnings("DataFlowIssue")
    public @NotNull ItemStack getRecipeOutputAtIndex(int index) {
        return hasRecipe(index) ? getRecipeAtIndex(index).getRecipeResult() : ItemStack.EMPTY;
    }

    public int getTemporaryRecipeIndex(int displayIndex) {
        return displayIndex;
    }

    public int getLockedRecipeIndex(int displayIndex) {
        return getLockedStart() + displayIndex;
    }

    public int getLockedRecipeSlots() {
        return getLockedCapacity();
    }

    /** 获取所有非空的记忆配方（临时 + 锁定），结果已缓存 */
    public List<MemorizedRecipe> getAllRecipes() {
        if (recipeListDirty || cachedAllRecipes == null) {
            List<MemorizedRecipe> result = new ArrayList<>();
            for (MemorizedRecipe recipe : memorizedRecipes) {
                if (recipe != null) {
                    result.add(recipe);
                }
            }
            cachedAllRecipes = Collections.unmodifiableList(result);
            recipeListDirty = false;
        }
        return cachedAllRecipes;
    }

    /** 标记配方列表需要重建，并递增版本号 */
    private void invalidateRecipeCache() {
        recipeListDirty = true;
        recipeVersion++;
    }

    /** 获取配方变化版本号，供外部检测变化使用 */
    public int getRecipeVersion() {
        return recipeVersion;
    }

    private static final class RecipeBuckets {

        private final List<MemorizedRecipe> temporary = new ArrayList<>();
        private final List<MemorizedRecipe> locked = new ArrayList<>();
    }

    private int getTemporaryCapacity() {
        return Math.min(TEMP_RECIPE_SLOTS, memorizedRecipes.length);
    }

    private int getLockedStart() {
        return getTemporaryCapacity();
    }

    private int getLockedCapacity() {
        return Math.max(0, Math.min(LOCKED_RECIPE_SLOTS, memorizedRecipes.length - getLockedStart()));
    }

    private RecipeBuckets collectBuckets() {
        RecipeBuckets buckets = new RecipeBuckets();
        for (MemorizedRecipe recipe : memorizedRecipes) {
            if (recipe == null) continue;
            if (recipe.recipeLocked) {
                buckets.locked.add(recipe);
            } else {
                buckets.temporary.add(recipe);
            }
        }
        return buckets;
    }

    private void applyBuckets(RecipeBuckets buckets) {
        Arrays.fill(memorizedRecipes, null);

        int tempCount = Math.min(getTemporaryCapacity(), buckets.temporary.size());
        for (int i = 0; i < tempCount; i++) {
            MemorizedRecipe recipe = buckets.temporary.get(i);
            recipe.index = i;
            recipe.recipeLocked = false;
            memorizedRecipes[i] = recipe;
        }

        int lockedStart = getLockedStart();
        int lockedCount = Math.min(getLockedCapacity(), buckets.locked.size());
        for (int i = 0; i < lockedCount; i++) {
            MemorizedRecipe recipe = buckets.locked.get(i);
            int index = lockedStart + i;
            recipe.index = index;
            recipe.recipeLocked = true;
            memorizedRecipes[index] = recipe;
        }
    }

    private void normalizeRecipeBuckets() {
        applyBuckets(collectBuckets());
    }

    private void syncRecipesToClient() {
        syncToClient(UPDATE_RECIPES, this::writeRecipes);
    }

    private boolean toggleRecipeLock(int index) {
        MemorizedRecipe recipe = getRecipeAtIndex(index);
        if (recipe == null) return false;

        RecipeBuckets buckets = collectBuckets();
        if (recipe.recipeLocked) {
            // 解锁：从锁定区移到临时区，保护 craftingMatrix 不被自动记忆覆盖
            if (!buckets.locked.remove(recipe)) return false;
            recipe.recipeLocked = false;
            recipe.matrixProtected = true;
            buckets.temporary.add(0, recipe);
            applyBuckets(buckets);
            invalidateRecipeCache();
            return true;
        }

        // 加锁：从临时区移到锁定区，清除保护标志
        if (buckets.locked.size() >= getLockedCapacity()) {
            return false;
        }
        if (!buckets.temporary.remove(recipe)) return false;
        recipe.recipeLocked = true;
        recipe.matrixProtected = false;
        buckets.locked.add(0, recipe);
        applyBuckets(buckets);
        invalidateRecipeCache();
        return true;
    }

    @Nullable
    private MemorizedRecipe findOrCreateRecipe(ItemStack resultItemStack) {
        RecipeBuckets buckets = collectBuckets();

        for (int i = 0; i < buckets.temporary.size(); i++) {
            MemorizedRecipe existing = buckets.temporary.get(i);
            if (!strategy.equals(existing.recipeResult, resultItemStack)) continue;
            if (i == 0) return existing;
            buckets.temporary.remove(i);
            buckets.temporary.add(0, existing);
            applyBuckets(buckets);
            return existing;
        }

        for (MemorizedRecipe existing : buckets.locked) {
            if (strategy.equals(existing.recipeResult, resultItemStack)) {
                return existing;
            }
        }

        if (getTemporaryCapacity() <= 0) return null;
        MemorizedRecipe recipe = new MemorizedRecipe(-1);
        recipe.initialize(resultItemStack);
        buckets.temporary.add(0, recipe);
        applyBuckets(buckets);
        return recipe;
    }

    public void notifyRecipePerformed(IItemHandler craftingGrid, ItemStack resultStack) {
        MemorizedRecipe recipe = findOrCreateRecipe(resultStack);
        if (recipe != null) {
            recipe.updateCraftingMatrix(craftingGrid);
            recipe.timesUsed++;
            invalidateRecipeCache();
            syncRecipesToClient();
        }
    }

    public void notifyRecipePerformed(IInventory craftingGrid, ItemStack resultStack) {
        MemorizedRecipe recipe = findOrCreateRecipe(resultStack);
        if (recipe != null) {
            recipe.updateCraftingMatrix(craftingGrid);
            recipe.timesUsed++;
            invalidateRecipeCache();
            syncRecipesToClient();
        }
    }

    public NBTTagCompound serializeNBT() {
        NBTTagCompound tagCompound = new NBTTagCompound();
        NBTTagList resultList = new NBTTagList();
        tagCompound.setTag("Memory", resultList);
        for (int i = 0; i < memorizedRecipes.length; i++) {
            MemorizedRecipe recipe = memorizedRecipes[i];
            if (recipe == null) continue;
            NBTTagCompound entryComponent = new NBTTagCompound();
            entryComponent.setInteger("Slot", i);
            entryComponent.setTag("Recipe", recipe.serializeNBT());
            resultList.appendTag(entryComponent);
        }
        return tagCompound;
    }

    public void deserializeNBT(NBTTagCompound tagCompound) {
        Arrays.fill(this.memorizedRecipes, null);
        NBTTagList resultList = tagCompound.getTagList("Memory", NBT.TAG_COMPOUND);
        for (int i = 0; i < resultList.tagCount(); i++) {
            NBTTagCompound entryComponent = resultList.getCompoundTagAt(i);
            int slotIndex = entryComponent.getInteger("Slot");
            if (slotIndex < 0 || slotIndex >= this.memorizedRecipes.length) continue;
            MemorizedRecipe recipe = MemorizedRecipe.deserializeNBT(entryComponent.getCompoundTag("Recipe"), slotIndex);
            this.memorizedRecipes[slotIndex] = recipe;
        }
        normalizeRecipeBuckets();
        invalidateRecipeCache();
    }

    private static void copyInventoryItems(IItemHandler src, IItemHandlerModifiable dest) {
        for (int i = 0; i < src.getSlots(); i++) {
            ItemStack itemStack = src.getStackInSlot(i);
            dest.setStackInSlot(i, itemStack.isEmpty() ? ItemStack.EMPTY : itemStack.copy());
        }
    }

    private static void copyInventoryItems(IInventory src, IItemHandlerModifiable dest) {
        for (int i = 0; i < Math.min(src.getSizeInventory(), dest.getSlots()); i++) {
            ItemStack itemStack = src.getStackInSlot(i);
            dest.setStackInSlot(i, itemStack.isEmpty() ? ItemStack.EMPTY : itemStack.copy());
        }
    }

    public final MemorizedRecipe removeRecipe(int index) {
        if (!hasRecipe(index)) return null;
        MemorizedRecipe removed = memorizedRecipes[index];
        RecipeBuckets buckets = collectBuckets();
        if (removed.recipeLocked) {
            buckets.locked.remove(removed);
        } else {
            buckets.temporary.remove(removed);
        }
        applyBuckets(buckets);
        invalidateRecipeCache();
        return removed;
    }

    public final boolean hasRecipe(int index) {
        if (index < 0 || index >= memorizedRecipes.length) return false;
        return memorizedRecipes[index] != null;
    }

    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        this.writeRecipes(buf);
    }

    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        this.readRecipes(buf);
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) {
        if (id == UPDATE_RECIPES) {
            this.readRecipes(buf);
        } else if (id == REMOVE_RECIPE) {
            this.removeRecipe(buf.readByte());
        } else if (id == MAKE_RECIPE) {
            int index = buf.readByte();
            if (index < 0 || index >= memorizedRecipes.length) {
                NetworkUtils.readItemStack(buf);
                return;
            }
            var recipe = memorizedRecipes[index];
            if (recipe == null) recipe = new MemorizedRecipe(index);
            recipe.recipeResult = NetworkUtils.readItemStack(buf);
            recipe.index = index;
            memorizedRecipes[index] = recipe;
            invalidateRecipeCache();
        } else if (id == SYNC_RECIPE) {
            var recipe = MemorizedRecipe.fromBuffer(buf);
            if (recipe.index < 0 || recipe.index >= memorizedRecipes.length) return;
            memorizedRecipes[recipe.index] = recipe;
            invalidateRecipeCache();
        } else if (id == OFFSET_RECIPE) {
            buf.readByte();
            invalidateRecipeCache();
        } else if (id == UPDATE_LOGIC) {
            getRecipeLogic().updateCurrentRecipe();
        }
    }

    public void writeRecipes(PacketBuffer buf) {
        int written = 0;
        for (int i = 0; i < memorizedRecipes.length; i++) {
            if (memorizedRecipes[i] != null) {
                written++;
            }
        }
        buf.writeByte(written);
        for (int i = 0; i < memorizedRecipes.length; i++) {
            var recipe = memorizedRecipes[i];
            if (recipe == null) continue;
            buf.writeByte(recipe.index);
            NetworkUtils.writeItemStack(buf, recipe.recipeResult);
            buf.writeInt(recipe.timesUsed);
            buf.writeBoolean(recipe.isRecipeLocked());
            buf.writeBoolean(recipe.matrixProtected);
        }
    }

    public void readRecipes(PacketBuffer buf) {
        Arrays.fill(memorizedRecipes, null);
        int size = buf.readByte();
        for (int i = 0; i < size; i++) {
            int index = buf.readByte();
            if (index < 0 || index >= memorizedRecipes.length) {
                NetworkUtils.readItemStack(buf);
                buf.readInt();
                buf.readBoolean();
                buf.readBoolean();
                continue;
            }
            memorizedRecipes[index] = new MemorizedRecipe(index);

            memorizedRecipes[index].recipeResult = NetworkUtils.readItemStack(buf);
            memorizedRecipes[index].timesUsed = buf.readInt();
            memorizedRecipes[index].recipeLocked = buf.readBoolean();
            memorizedRecipes[index].matrixProtected = buf.readBoolean();
        }
        normalizeRecipeBuckets();
        invalidateRecipeCache();
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) {
        if (id == UPDATE_RECIPES) {
            syncToClient(UPDATE_RECIPES, this::writeRecipes);
        } else if (id == MOUSE_CLICK) {
            // read mouse data
            int index = buf.readByte();
            var data = MouseData.readPacket(buf);
            var recipe = getRecipeAtIndex(index);
            if (recipe == null) return;

            if (data.shift && data.mouseButton == 0) {
                if (toggleRecipeLock(index)) {
                    syncRecipesToClient();
                }
            } else if (data.mouseButton == 0) {
                loadRecipe(index);
            } else if (data.mouseButton == 1 && !recipe.isRecipeLocked()) {
                if (removeRecipe(index) != null) {
                    syncRecipesToClient();
                }
            }
        }
    }

    public static class MemorizedRecipe {

        private final ItemStackHandler craftingMatrix = new ItemStackHandler(9);
        private ItemStack recipeResult = ItemStack.EMPTY;
        private boolean recipeLocked = false;
        /**
         * 保护标志：当配方从锁定区解锁到临时区时设置为 true，
         * 防止自动记忆覆盖 craftingMatrix 中的原始数据。
         * 在配方被手动加载或重新锁定时清除。
         */
        private boolean matrixProtected = false;
        public int timesUsed = 0;
        public int index;

        private MemorizedRecipe(int index) {
            this.index = index;
        }

        /**
         * 创建一个虚拟的 MemorizedRecipe，用于合成链求解。
         * 虚拟配方的 index 为 -1，不属于任何记忆槽位。
         */
        public static MemorizedRecipe createVirtual(IInventory craftingGrid, ItemStack result) {
            MemorizedRecipe recipe = new MemorizedRecipe(-1);
            recipe.recipeResult = result.copy();
            copyInventoryItems(craftingGrid, recipe.craftingMatrix);
            return recipe;
        }

        private NBTTagCompound serializeNBT() {
            NBTTagCompound result = new NBTTagCompound();
            result.setTag("Result", recipeResult.serializeNBT());
            result.setTag("Matrix", craftingMatrix.serializeNBT());
            result.setBoolean("Locked", recipeLocked);
            result.setBoolean("MatrixProtected", matrixProtected);
            result.setInteger("TimesUsed", timesUsed);
            return result;
        }

        private static MemorizedRecipe deserializeNBT(NBTTagCompound tagCompound, int index) {
            MemorizedRecipe recipe = new MemorizedRecipe(index);
            recipe.recipeResult = new ItemStack(tagCompound.getCompoundTag("Result"));
            recipe.craftingMatrix.deserializeNBT(tagCompound.getCompoundTag("Matrix"));
            recipe.recipeLocked = tagCompound.getBoolean("Locked");
            recipe.matrixProtected = tagCompound.getBoolean("MatrixProtected");
            recipe.timesUsed = tagCompound.getInteger("TimesUsed");
            return recipe;
        }

        private void writeToBuffer(PacketBuffer buffer) {
            buffer.writeByte(this.index);
            buffer.writeInt(this.timesUsed);
            buffer.writeBoolean(this.recipeLocked);
            buffer.writeBoolean(this.matrixProtected);
            NetworkUtils.writeItemStack(buffer, this.recipeResult);
        }

        private static @NotNull MemorizedRecipe fromBuffer(PacketBuffer buffer) {
            var recipe = new MemorizedRecipe(buffer.readByte());
            recipe.timesUsed = buffer.readInt();
            recipe.recipeLocked = buffer.readBoolean();
            recipe.matrixProtected = buffer.readBoolean();
            recipe.recipeResult = NetworkUtils.readItemStack(buffer);
            return recipe;
        }

        private void initialize(ItemStack recipeResult) {
            this.recipeResult = recipeResult.copy();
            for (int i = 0; i < this.craftingMatrix.getSlots(); i++) {
                this.craftingMatrix.setStackInSlot(i, ItemStack.EMPTY);
            }
            this.recipeLocked = false;
            this.matrixProtected = false;
            this.timesUsed = 0;
        }

        private void updateCraftingMatrix(IItemHandler craftingGrid) {
            // do not modify crafting grid for locked or protected recipes
            if (!recipeLocked && !matrixProtected) {
                copyInventoryItems(craftingGrid, craftingMatrix);
            }
        }

        private void updateCraftingMatrix(IInventory craftingGrid) {
            if (!recipeLocked && !matrixProtected) {
                copyInventoryItems(craftingGrid, craftingMatrix);
            }
        }

        public ItemStack getRecipeResult() {
            return recipeResult;
        }

        /** 获取合成网格中指定槽位的物品 */
        public ItemStack getCraftingMatrixSlot(int slot) {
            return craftingMatrix.getStackInSlot(slot);
        }

        /** 获取合成网格 handler */
        public ItemStackHandler getCraftingMatrix() {
            return craftingMatrix;
        }

        public boolean isRecipeLocked() {
            return recipeLocked;
        }

        public void setRecipeLocked(boolean recipeLocked) {
            this.recipeLocked = recipeLocked;
        }

        public MemorizedRecipe copy() {
            var recipe = new MemorizedRecipe(this.index);
            recipe.initialize(this.recipeResult);
            recipe.updateCraftingMatrix(this.craftingMatrix);
            recipe.recipeLocked = this.recipeLocked;
            recipe.matrixProtected = this.matrixProtected;
            recipe.timesUsed = this.timesUsed;
            return recipe;
        }

        @Override
        public String toString() {
            return String.format("MemorizedRecipe{%dx %s, locked: %s, times used: %d}",
                    getRecipeResult().getCount(),
                    getRecipeResult().getDisplayName(),
                    recipeLocked,
                    timesUsed);
        }
    }
}
