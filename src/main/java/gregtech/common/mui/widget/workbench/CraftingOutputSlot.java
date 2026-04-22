package gregtech.common.mui.widget.workbench;

import gregtech.api.util.ItemStackHashStrategy;
import gregtech.client.utils.RenderUtil;
import gregtech.common.metatileentities.workbench.CraftingChainSolver;
import gregtech.common.metatileentities.workbench.CraftingRecipeLogic;
import gregtech.common.metatileentities.workbench.CraftingRecipeMemory;
import gregtech.common.metatileentities.workbench.MetaTileEntityWorkbench;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerIngredientProvider;
import com.cleanroommc.modularui.network.NetworkUtils;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CraftingOutputSlot extends Widget<CraftingOutputSlot> implements Interactable,
                                                                              RecipeViewerIngredientProvider {

    private static final int MOUSE_CLICK = 2;
    private static final int SYNC_STACK = 5;
    private static final int SYNC_CHAIN_MISSING = 6;
    private final CraftingSlotSH syncHandler;

    public CraftingOutputSlot(IntSyncValue amountCrafted, MetaTileEntityWorkbench workbench) {
        this.syncHandler = new CraftingSlotSH(amountCrafted, workbench);
        setSyncHandler(this.syncHandler);
        tooltipAutoUpdate(true);
        tooltipBuilder(tooltip -> {
            if (!isSynced()) return;
            ItemStack stack = this.syncHandler.getOutputStack();
            if (stack.isEmpty()) return;
            tooltip.addFromItem(stack);
            // 合成链缺失材料提示
            var missing = this.syncHandler.getClientMissingItems();
            if (!missing.isEmpty()) {
                tooltip.addLine(IKey.EMPTY);
                tooltip.addLine(IKey.str(TextFormatting.GOLD +
                        net.minecraft.client.resources.I18n.format("gregtech.workbench.chain_missing_header")));
                for (var entry : missing.entrySet()) {
                    String itemName = entry.getKey().getDisplayName();
                    int count = entry.getValue();
                    tooltip.addLine(IKey.str(TextFormatting.RED + "  " + itemName + " x" + count));
                }
            }
        });
    }

    @Override
    public boolean isValidSyncHandler(SyncHandler syncHandler) {
        return syncHandler instanceof CraftingSlotSH;
    }

    @Override
    public @NotNull Result onMousePressed(int mouseButton) {
        MouseData mouseData = MouseData.create(mouseButton);
        // if there's a valid recipe, then the output slot should not be empty
        if (!getIngredient().isEmpty())
            this.syncHandler.syncToServer(MOUSE_CLICK, mouseData::writeToPacket);
        return Result.SUCCESS;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        ItemStack itemstack = this.syncHandler.getOutputStack();
        RenderUtil.drawItemStack(itemstack, 1, 1, true);
        RenderUtil.handleSlotOverlay(this, widgetTheme);
    }

    @Override
    public void drawForeground(ModularGuiContext context) {
        RichTooltip tooltip = getTooltip();
        if (tooltip != null && isHoveringFor(tooltip.getShowUpTimer())) {
            tooltip.draw(getContext(), this.syncHandler.getOutputStack());
        }
    }

    @Override
    public @NotNull ItemStack getIngredient() {
        return this.syncHandler.getOutputStack();
    }

    protected static class CraftingSlotSH extends SyncHandler {

        private final CraftingRecipeLogic recipeLogic;
        private final CraftingOutputMS slot;
        private final CraftingChainSolver chainSolver = new CraftingChainSolver();

        private final List<ModularSlot> shiftClickSlots = new ArrayList<>();

        // ==================== 合成链缺失材料同步 ====================
        private static final int CHAIN_SOLVE_INTERVAL = 10;
        private final ItemStackHashStrategy missingStrategy = ItemStackHashStrategy.builder()
                .compareItem(true)
                .compareMetadata(true)
                .build();
        /** 服务端：上一次同步的缺失材料缓存，用于变化检测 */
        private Map<ItemStack, Integer> cachedMissingItems = new LinkedHashMap<>();
        /** 客户端：从服务端同步过来的缺失材料列表 */
        @SideOnly(Side.CLIENT)
        private Map<ItemStack, Integer> clientMissingItems;
        /** 节流计数器：距离上次求解的 tick 数 */
        private int tickSinceLastSolve = CHAIN_SOLVE_INTERVAL;
        /** 上次求解时的配方版本号 */
        private int lastRecipeVersion = -1;

        public CraftingSlotSH(IntSyncValue amountCrafted, MetaTileEntityWorkbench workbench) {
            this.slot = new CraftingOutputMS(amountCrafted, workbench);
            this.recipeLogic = slot.recipeLogic;
        }

        @Override
        public void init(String key, PanelSyncManager syncManager) {
            super.init(key, syncManager);
            getSyncManager().getSlotGroups().stream()
                    .filter(SlotGroup::allowShiftTransfer)
                    .sorted(Comparator.comparingInt(SlotGroup::getShiftClickPriority))
                    .collect(Collectors.toList())
                    .forEach(slotGroup -> {
                        for (Slot slot : slotGroup.getSlots()) {
                            if (slot instanceof ModularSlot modularSlot) {
                                this.shiftClickSlots.add(modularSlot);
                            }
                        }
                    });
        }

        private static final int MAX_SHIFT_CRAFT = 64;

        @Override
        public void detectAndSendChanges(boolean init) {
            // 节流：配方版本变化时立即求解，否则最多每 CHAIN_SOLVE_INTERVAL tick 求解一次
            int currentVersion = this.slot.recipeMemory.getRecipeVersion();
            boolean versionChanged = currentVersion != lastRecipeVersion;
            tickSinceLastSolve++;

            if (!init && !versionChanged && tickSinceLastSolve < CHAIN_SOLVE_INTERVAL) {
                return;
            }

            lastRecipeVersion = currentVersion;
            tickSinceLastSolve = 0;

            Map<ItemStack, Integer> currentMissing = solveChainMissingItems();
            if (init || !missingMapsEqual(cachedMissingItems, currentMissing)) {
                cachedMissingItems = currentMissing;
                syncToClient(SYNC_CHAIN_MISSING, buf -> {
                    buf.writeVarInt(currentMissing.size());
                    for (var entry : currentMissing.entrySet()) {
                        NetworkUtils.writeItemStack(buf, entry.getKey());
                        buf.writeVarInt(entry.getValue());
                    }
                });
            }
        }

        /**
         * 求解合成链并返回缺失材料 Map（不执行任何合成）。
         */
        private Map<ItemStack, Integer> solveChainMissingItems() {
            if (!recipeLogic.isRecipeValid()) return Collections.emptyMap();

            var allRecipes = this.slot.recipeMemory.getAllRecipes();
            if (allRecipes.isEmpty()) return Collections.emptyMap();

            var currentRecipe = createCurrentRecipe();
            if (currentRecipe == null) return Collections.emptyMap();

            var result = chainSolver.solve(
                    currentRecipe, allRecipes,
                    recipeLogic.getAvailableHandlers(),
                    getSyncManager().getPlayer().world,
                    recipeLogic::countItemInInventory);

            return result.missingItems;
        }

        /**
         * 比较两个缺失材料 Map 是否相同（忽略 Map 实例引用）。
         */
        private boolean missingMapsEqual(Map<ItemStack, Integer> a, Map<ItemStack, Integer> b) {
            if (a.size() != b.size()) return false;
            for (var entryA : a.entrySet()) {
                boolean found = false;
                for (var entryB : b.entrySet()) {
                    if (missingStrategy.equals(entryA.getKey(), entryB.getKey())) {
                        if (entryA.getValue().equals(entryB.getValue())) {
                            found = true;
                        }
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        }

        /**
         * 获取客户端缓存的合成链缺失材料（仅客户端调用）。
         */
        @SideOnly(Side.CLIENT)
        public Map<ItemStack, Integer> getClientMissingItems() {
            return clientMissingItems != null ? clientMissingItems : Collections.emptyMap();
        }

        @Override
        public void readOnServer(int id, PacketBuffer buf) {
            if (id == MOUSE_CLICK) {
                EntityPlayer player = getSyncManager().getPlayer();
                ForgeHooks.setCraftingPlayer(player);
                try {
                    var data = MouseData.readPacket(buf);

                    if (recipeLogic.isRecipeValid()) {
                        ItemStack outputStack = getOutputStack();
                        boolean hasSpace;
                        if (data.shift) {
                            hasSpace = quickTransfer(getOutputStack(), true);
                        } else {
                            hasSpace = this.slot.canTakeStack(player);
                        }
                        if (hasSpace) {
                            var chainResult = solveChainResult();
                            if (chainResult == null || !chainResult.canExecute) {
                                return;
                            }

                            var chainSteps = solveChainDependencies(chainResult);
                            if (!executeChainSteps(chainSteps)) {
                                return;
                            }

                            if (recipeLogic.performRecipe()) {
                                handleItemCraft(outputStack, player);

                                if (data.shift) {
                                    ItemStack finalStack = outputStack.copy();
                                    int crafted = 1;
                                    while (crafted < MAX_SHIFT_CRAFT &&
                                            quickTransfer(finalStack, true) &&
                                            canStack(finalStack, outputStack)) {
                                        // 每次循环重新求解合成链，避免中间产品过度产出
                                        var loopChainResult = solveChainResult();
                                        if (loopChainResult == null || !loopChainResult.canExecute) break;
                                        var loopChainSteps = solveChainDependencies(loopChainResult);
                                        if (!executeChainSteps(loopChainSteps) ||
                                                !recipeLogic.performRecipe()) break;
                                        finalStack.setCount(finalStack.getCount() + outputStack.getCount());
                                        handleItemCraft(outputStack, player);
                                        crafted++;
                                    }
                                    quickTransfer(finalStack, false);
                                } else {
                                    syncToClient(SYNC_STACK, this::syncCursorStack);
                                }
                            }
                        }
                    }
                } finally {
                    ForgeHooks.setCraftingPlayer(null);
                }
            }
        }

        /**
         * 求解合成链，返回前置步骤列表（不包含目标配方本身）。
         * 此方法只做求解，不执行任何合成，结果可在 shift 连续合成中复用。
         */
        @Nullable
        private CraftingChainSolver.ChainResult solveChainResult() {
            var currentRecipe = createCurrentRecipe();
            if (currentRecipe == null) return null;
            return chainSolver.solve(
                    currentRecipe, this.slot.recipeMemory.getAllRecipes(),
                    recipeLogic.getAvailableHandlers(),
                    getSyncManager().getPlayer().world,
                    recipeLogic::countItemInInventory);
        }

        private List<CraftingChainSolver.ChainStep> solveChainDependencies(CraftingChainSolver.ChainResult result) {
            if (result.steps.size() <= 1) return Collections.emptyList();

            // Exclude the last step (the target recipe itself), keep only dependency steps.
            return result.steps.subList(0, result.steps.size() - 1);
        }
        /**
         * 执行已求解的合成链前置步骤。
         */
        private boolean executeChainSteps(List<CraftingChainSolver.ChainStep> chainSteps) {
            for (var step : chainSteps) {
                for (int t = 0; t < step.count; t++) {
                    if (!recipeLogic.executeChainStep(step)) {
                        return false;
                    }
                }
            }
            return true;
        }
        /**
         * 根据当前合成网格内容创建一个虚拟 MemorizedRecipe。
         */
        @Nullable
        private CraftingRecipeMemory.MemorizedRecipe createCurrentRecipe() {
            ItemStack outputStack = getOutputStack();
            if (outputStack.isEmpty()) return null;

            var recipe = CraftingRecipeMemory.MemorizedRecipe.createVirtual(
                    recipeLogic.getCraftingMatrix(), outputStack);
            return recipe;
        }

        private static boolean canStack(ItemStack a, ItemStack b) {
            return ItemHandlerHelper.canItemStacksStackRelaxed(a, b) &&
                    a.getCount() + b.getCount() < b.getMaxStackSize();
        }

        private boolean insertStack(ItemStack fromStack, ModularSlot toSlot, boolean simulate) {
            ItemStack toStack = toSlot.getStack().copy();
            if (ItemHandlerHelper.canItemStacksStack(fromStack, toStack)) {
                int combined = toStack.getCount() + fromStack.getCount();
                int maxSize = Math.min(toSlot.getSlotStackLimit(), fromStack.getMaxStackSize());

                // we can fit all of toStack
                if (combined <= maxSize) {
                    if (simulate) return true;
                    fromStack.setCount(0);
                    toStack.setCount(combined);
                    toSlot.putStack(toStack);
                } else if (toStack.getCount() < maxSize) {
                    if (simulate) return true;
                    // we can fit some of toStack, but not all
                    fromStack.shrink(maxSize - toStack.getCount());
                    toStack.setCount(maxSize);
                    toSlot.putStack(toStack);
                }

                return fromStack.isEmpty();
            } else if (toStack.isEmpty()) {
                if (simulate) return true;
                int maxSize = Math.min(toSlot.getSlotStackLimit(), fromStack.getCount());
                toSlot.putStack(fromStack.splitStack(maxSize));
                return fromStack.isEmpty();
            }
            return false;
        }

        public boolean quickTransfer(ItemStack fromStack, boolean simulate) {
            List<ModularSlot> emptySlots = new ArrayList<>();
            for (ModularSlot toSlot : this.shiftClickSlots) {
                if (toSlot.isEnabled() && toSlot.isItemValid(fromStack)) {
                    if (toSlot.getStack().isEmpty()) {
                        emptySlots.add(toSlot);
                        continue;
                    }

                    if (insertStack(fromStack, toSlot, simulate)) {
                        if (simulate || fromStack.isEmpty()) return true;
                    }
                }
            }
            for (ModularSlot emptySlot : emptySlots) {
                if (insertStack(fromStack, emptySlot, simulate)) {
                    if (simulate || fromStack.isEmpty()) return true;
                }
            }
            return false;
        }

        @Override
        public void readOnClient(int id, PacketBuffer buf) {
            if (id == SYNC_STACK && buf.readBoolean()) {
                getSyncManager().setCursorItem(NetworkUtils.readItemStack(buf));
            } else if (id == SYNC_CHAIN_MISSING) {
                int size = buf.readVarInt();
                Map<ItemStack, Integer> missing = new LinkedHashMap<>();
                for (int i = 0; i < size; i++) {
                    ItemStack stack = NetworkUtils.readItemStack(buf);
                    int count = buf.readVarInt();
                    missing.put(stack, count);
                }
                clientMissingItems = missing;
            }
        }

        private void syncCursorStack(PacketBuffer buf) {
            ItemStack curStack = getSyncManager().getCursorItem();
            ItemStack outStack = this.slot.getStack();
            if (this.slot.canTakeStack(getSyncManager().getPlayer())) {
                ItemStack toSync = outStack.copy();
                int combined = curStack.getCount() + outStack.getCount();
                // clamp to max stack size
                toSync.setCount(Math.min(combined, outStack.getMaxStackSize()));
                buf.writeBoolean(true);
                NetworkUtils.writeItemStack(buf, toSync);
            } else {
                buf.writeBoolean(false);
            }
        }

        public ItemStack getOutputStack() {
            return slot.getStack();
        }

        public void handleItemCraft(ItemStack craftedStack, EntityPlayer player) {
            craftedStack.onCrafting(player.world, player, 1);

            var inventoryCrafting = recipeLogic.getCraftingMatrix();

            // if we're not simulated, fire the event, unlock recipe and add crafted items, and play sounds
            FMLCommonHandler.instance().firePlayerCraftingEvent(player, craftedStack, inventoryCrafting);

            var cachedRecipe = recipeLogic.getCachedRecipe();
            if (cachedRecipe != null) {
                if (!cachedRecipe.isDynamic()) {
                    player.unlockRecipes(Lists.newArrayList(cachedRecipe));
                }
                ItemStack resultStack = cachedRecipe.getCraftingResult(inventoryCrafting);
                this.slot.notifyRecipePerformed(resultStack);
            }
        }
    }

    protected static class CraftingOutputMS extends ModularSlot {

        private final IntSyncValue amountCrafted;
        private final CraftingRecipeLogic recipeLogic;
        private final CraftingRecipeMemory recipeMemory;
        private final IItemHandler craftingGrid;

        public CraftingOutputMS(IntSyncValue amountCrafted, MetaTileEntityWorkbench workbench) {
            super(new InventoryWrapper(
                    workbench.getCraftingRecipeLogic().getCraftingResultInventory(),
                    workbench.getCraftingRecipeLogic()), 0);
            this.amountCrafted = amountCrafted;
            this.recipeLogic = workbench.getCraftingRecipeLogic();
            this.recipeMemory = workbench.getRecipeMemory();
            this.craftingGrid = workbench.getCraftingGrid();
        }

        @Override
        public boolean canTakeStack(EntityPlayer playerIn) {
            ItemStack curStack = playerIn.inventory.getItemStack();
            if (curStack.isEmpty()) return true;

            ItemStack outStack = getStack();
            if (ItemHandlerHelper.canItemStacksStack(curStack, outStack)) {
                int combined = curStack.getCount() + outStack.getCount();
                return combined <= outStack.getMaxStackSize();
            } else {
                return false;
            }
        }

        public void notifyRecipePerformed(ItemStack stack) {
            this.amountCrafted.setValue(this.amountCrafted.getValue() + stack.getCount(), true, true);
            this.recipeMemory.notifyRecipePerformed(this.craftingGrid, stack);
        }

        @Override
        public void putStack(@NotNull ItemStack stack) {
            super.putStack(getStack());
        }

        @Override
        public @NotNull ItemStack decrStackSize(int amount) {
            return getStack();
        }
    }

    private static class InventoryWrapper implements IItemHandlerModifiable {

        private final IInventory inventory;
        private final CraftingRecipeLogic recipeLogic;

        private InventoryWrapper(IInventory inventory, CraftingRecipeLogic recipeLogic) {
            this.inventory = inventory;
            this.recipeLogic = recipeLogic;
        }

        @Override
        public int getSlots() {
            return inventory.getSizeInventory();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot).copy();
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getInventoryStackLimit();
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            if (!recipeLogic.isRecipeValid()) {
                inventory.setInventorySlotContents(slot, ItemStack.EMPTY);
            }

            if (!stack.isEmpty())
                inventory.setInventorySlotContents(slot, stack);
        }
    }
}
