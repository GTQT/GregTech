package gregtech.common.items.behaviors;

import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.items.metaitem.stats.IItemContainerItemProvider;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

import gregtech.common.items.MetaItems;

import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Single-mode Programming Toolkit (always-on normal + append-empty behavior).
 */
public class ProgrammingToolkit implements ItemUIFactory, IItemBehaviour, IItemContainerItemProvider {

    public ProgrammingToolkit() {
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (!world.isRemote) {
            MetaItemGuiFactory.open(player, hand);
        }
        return success(heldItem);
    }

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        ModularPanel panel = GTGuis.createPanel(guiData.getUsedItemStack(), 176, 166);

        ItemStackHandler outputHandler = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return false;
            }

            @NotNull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack outputStack = getStackInSlot(0);
                if (outputStack.isEmpty()) return ItemStack.EMPTY;
                ItemStack extracted = outputStack.copy();
                extracted.setCount(Math.min(amount, 64));
                return extracted;
            }
        };
        ItemStackHandler inputHandler = new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                updateOutput(this, outputHandler);
            }
        };

        updateOutput(inputHandler, outputHandler);
        guiSyncManager.onCommonTick(() -> updateOutput(inputHandler, outputHandler));
        guiSyncManager.addCloseListener(player -> onGuiClosed(player, inputHandler));

        return panel
                .child(IKey.str(I18n.format("metaitem.programming_toolkit.gui_title"))
                        .asWidget().pos(5, 5))
                .child(IKey.str(I18n.format("metaitem.programming_toolkit.input"))
                        .asWidget().pos(30, 30))
                .child(new ItemSlot()
                        .slot(SyncHandlers.itemSlot(inputHandler, 0)
                                .changeListener((newItem, onlyAmountChanged, client, init) ->
                                        updateOutput(inputHandler, outputHandler))
                                .accessibility(true, true))
                        .background(GTGuiTextures.SLOT)
                        .tooltip(t -> t.addLine(IKey.lang("metaitem.programming_toolkit.hint")))
                        .pos(35, 45))
                .child(IKey.str("->").asWidget().pos(62, 48))
                .child(IKey.str(I18n.format("metaitem.programming_toolkit.output"))
                        .asWidget().pos(80, 30))
                .child(new ItemSlot()
                        .slot(SyncHandlers.itemSlot(outputHandler, 0)
                                .accessibility(false, true))
                        .background(GTGuiTextures.SLOT)
                        .pos(85, 45))
                .child(IKey.str(I18n.format("metaitem.programming_toolkit.hint"))
                        .asWidget().pos(5, 75))
                .bindPlayerInventory();
    }

    private static void updateOutput(ItemStackHandler inputHandler, ItemStackHandler outputHandler) {
        if (MetaItems.PROGRAMMABLE_CIRCUIT == null) {
            outputHandler.setStackInSlot(0, ItemStack.EMPTY);
            return;
        }

        ItemStack circuitStack = MetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(64);
        ItemStack normalizedInput = normalizeInput(inputHandler.getStackInSlot(0));
        ProgrammableCircuit.wrap(normalizedInput, circuitStack);
        circuitStack.setCount(64);
        outputHandler.setStackInSlot(0, circuitStack);
    }

    private static void onGuiClosed(@NotNull EntityPlayer player, @NotNull ItemStackHandler inputHandler) {
        if (player.world.isRemote) return;

        ItemStack input = inputHandler.getStackInSlot(0);
        if (input.isEmpty()) return;

        player.dropItem(input.copy(), false);
        inputHandler.setStackInSlot(0, ItemStack.EMPTY);
    }

    @NotNull
    private static ItemStack normalizeInput(@NotNull ItemStack inputStack) {
        if (inputStack.isEmpty()) return ItemStack.EMPTY;

        ItemStack normalized = inputStack.copy();
        normalized.setCount(1);

        if (MetaItems.PROGRAMMABLE_CIRCUIT != null && MetaItems.PROGRAMMABLE_CIRCUIT.isItemEqual(normalized)) {
            return ProgrammableCircuit.getWrappedItem(normalized)
                    .map(ItemStack::copy)
                    .orElse(ItemStack.EMPTY);
        }
        return normalized;
    }

    public static boolean isToolkitInInventory(@NotNull EntityPlayer player) {
        if (MetaItems.PROGRAMMING_TOOLKIT == null) return false;

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack invStack = player.inventory.getStackInSlot(i);
            if (!invStack.isEmpty() && MetaItems.PROGRAMMING_TOOLKIT.isItemEqual(invStack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        lines.add(I18n.format("metaitem.programming_toolkit.tooltip.1"));
        lines.add(I18n.format("metaitem.programming_toolkit.tooltip.2"));
    }

    @Override
    public ItemStack getContainerItem(ItemStack itemStack) {
        ItemStack container = itemStack.copy();
        container.setCount(1);
        return container;
    }
}
