package gregtech.api.mui.sync.appeng;

import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.mui.GTByteBufAdapters;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;
import gregtech.api.util.GTUtility;
import gregtech.api.util.JEIUtil;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEItemList;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.ExportOnlyAEItemSlot;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.slot.IConfigurableSlot;

import gtqt.common.items.GTQTMetaItems;
import gtqt.common.items.behaviors.ProgrammableCircuit;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.cleanroommc.modularui.utils.serialization.IByteBufAdapter;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;

public class AEItemSyncHandler extends AESyncHandler<IAEItemStack> {

    protected final ExportOnlyAEItemList itemList;

    public AEItemSyncHandler(ExportOnlyAEItemList itemList, @Nullable Runnable dirtyNotifier,
                             @NotNull IntConsumer circuitChangeConsumer) {
        super(itemList.getInventory(), itemList.isStocking(), dirtyNotifier, circuitChangeConsumer);
        this.itemList = itemList;
    }

    @Override
    protected @NotNull IConfigurableSlot<IAEItemStack> @NotNull [] initializeCache() {
        // noinspection unchecked
        IConfigurableSlot<IAEItemStack>[] cache = new IConfigurableSlot[slots.length];
        for (int index = 0; index < slots.length; index++) {
            cache[index] = new ExportOnlyAEItemSlot();
        }
        return cache;
    }

    @Override
    protected @NotNull IByteBufAdapter<IAEItemStack> initializeByteBufAdapter() {
        return GTByteBufAdapters.AE_ITEM_STACK;
    }

    @Override
    public boolean isStackValidForSlot(int index, @Nullable IAEItemStack stack) {
        if (stack == null || stack.getDefinition().isEmpty()) return true;
        if (!isStocking) return true;
        return !itemList.hasStackInConfig(stack.getDefinition(), true);
    }

    @Override
    public IRecipeTransferError receiveRecipe(@NotNull IRecipeLayout recipeLayout, boolean maxTransfer,
                                              boolean simulate) {
        if (simulate) return null;

        Int2ObjectMap<ItemStack> originalItemInputs = JEIUtil.getDisplayedInputItemStacks(recipeLayout.getItemStacks(),
                false, true);
        List<ItemStack> itemInputs = new ArrayList<>(originalItemInputs.values());
        GTUtility.collapseItemList(itemInputs);

        int circuitValue = GhostCircuitItemStackHandler.NO_CONFIG;
        Iterator<ItemStack> inputsIterator = itemInputs.iterator();
        while (inputsIterator.hasNext()) {
            ItemStack stack = inputsIterator.next();
            if (stack == null) continue;
            if (IntCircuitIngredient.isIntegratedCircuit(stack)) {
                // 检查玩家是否持有可编程工具箱 — 如果持有，将集成电路包装为可编程电路
                // 移植自 PH-Mod 的 MixinPatternEncodingCiruitSpecialTreatment
                if (hasToolkitInInventory()) {
                    int config = IntCircuitIngredient.getCircuitConfiguration(stack);
                    ItemStack circuitStack = GTQTMetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(1);
                    ItemStack intCircuit = IntCircuitIngredient.getIntegratedCircuit(config);
                    ProgrammableCircuit.wrap(intCircuit, circuitStack);
                    // 替换原位置的集成电路为可编程电路
                    inputsIterator.remove();
                    itemInputs.add(circuitStack);
                } else {
                    circuitValue = IntCircuitIngredient.getCircuitConfiguration(stack);
                    inputsIterator.remove();
                }
                break;
            }
        }
        ghostCircuitConfig.accept(circuitValue);

        int lastSlotIndex;
        for (lastSlotIndex = 0; lastSlotIndex < itemInputs.size(); lastSlotIndex++) {
            ItemStack newConfig = itemInputs.get(lastSlotIndex);
            setConfig(lastSlotIndex, newConfig);
        }
        clearConfigFrom(lastSlotIndex);

        return null;
    }

    @SideOnly(Side.CLIENT)
    public void setConfig(int index, @Nullable ItemStack stack) {
        setConfig(index, stack == null ? null : AEItemStack.fromItemStack(stack));
    }

    /**
     * 检查当前打开 GUI 的玩家物品栏中是否持有可编程工具箱。
     * 移植自 PH-Mod 的样板编码虚拟电路自动包装功能。
     *
     * @return 如果玩家物品栏中有可编程工具箱则返回 true
     */
    private boolean hasToolkitInInventory() {
        EntityPlayer player = getSyncManager().getPlayer();
        if (player == null) return false;

        ItemStack toolkitStack = GTQTMetaItems.PROGRAMMING_TOOLKIT.getStackForm();
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack invStack = player.inventory.getStackInSlot(i);
            if (!invStack.isEmpty() && invStack.getItem() == toolkitStack.getItem()
                    && invStack.getMetadata() == toolkitStack.getMetadata()) {
                return true;
            }
        }
        return false;
    }
}
