package gtqt.common.items.behaviors;

import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

import gtqt.common.items.GTQTMetaItems;

import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 可编程工具箱行为类。
 * 右键打开 GUI，玩家放入任意物品后，输出槽会自动生成包裹了该物品的可编程电路。
 */
public class ProgrammingToolkit implements ItemUIFactory, IItemBehaviour {

    public ProgrammingToolkit() {
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (!world.isRemote) {
            MetaItemGuiFactory.open(player, hand);
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, heldItem);
    }

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        var panel = GTGuis.createPanel(guiData.getUsedItemStack(), 176, 120);

        // 输入槽 - 玩家放入要包裹的物品
        ItemStackHandler inputHandler = new ItemStackHandler(1);
        // 输出槽 - 显示包裹后的可编程电路（无限供应，取出不减少）
        ItemStackHandler outputHandler = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return false;
            }

            @NotNull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                // 无限供应：返回一份副本，不修改输出槽内容
                ItemStack outputStack = getStackInSlot(0);
                if (outputStack.isEmpty()) return ItemStack.EMPTY;
                ItemStack extracted = outputStack.copy();
                extracted.setCount(Math.min(amount, 64));
                return extracted;
            }
        };

        return panel
                .child(IKey.str(I18n.format("metaitem.programming_toolkit.gui_title"))
                        .asWidget().pos(5, 5))
                // 输入槽标签
                .child(IKey.str(I18n.format("metaitem.programming_toolkit.input"))
                        .asWidget().pos(30, 30))
                // 输入槽
                .child(new ItemSlot()
                        .slot(SyncHandlers.itemSlot(inputHandler, 0)
                                .changeListener((newItem, onlyAmountChanged, client, init) -> {
                                    if (!client) {
                                        updateOutput(inputHandler, outputHandler);
                                    }
                                })
                                .accessibility(true, true))
                        .background(GTGuiTextures.SLOT)
                        .pos(35, 45))
                // 箭头标签
                .child(IKey.str("→").asWidget().pos(62, 48))
                // 输出槽标签
                .child(IKey.str(I18n.format("metaitem.programming_toolkit.output"))
                        .asWidget().pos(80, 30))
                // 输出槽
                .child(new ItemSlot()
                        .slot(SyncHandlers.itemSlot(outputHandler, 0)
                                .accessibility(false, true))
                        .background(GTGuiTextures.SLOT)
                        .pos(85, 45))
                // 提示信息
                .child(IKey.str(I18n.format("metaitem.programming_toolkit.hint"))
                        .asWidget().pos(5, 75));
    }

    /**
     * 根据输入槽的物品更新输出槽。
     * 输入为空时输出空白可编程电路，输入有物品时输出包裹了该物品的可编程电路。
     */
    private static void updateOutput(ItemStackHandler inputHandler, ItemStackHandler outputHandler) {
        ItemStack inputStack = inputHandler.getStackInSlot(0);
        if (inputStack.isEmpty()) {
            // 输入为空 → 输出空白可编程电路
            outputHandler.setStackInSlot(0, GTQTMetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(64));
            return;
        }

        // 创建可编程电路并包裹输入的物品，输出槽显示64个
        ItemStack circuitStack = GTQTMetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(64);
        ProgrammableCircuit.wrap(inputStack, circuitStack);
        circuitStack.setCount(64);
        outputHandler.setStackInSlot(0, circuitStack);
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        lines.add(I18n.format("metaitem.programming_toolkit.tooltip.1"));
        lines.add(I18n.format("metaitem.programming_toolkit.tooltip.2"));
    }
}
