package gregtech.common.terminal2;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.terminal2.ITerminalApp;
import gregtech.api.terminal2.Terminal2Theme;
import gregtech.common.items.behaviors.TerminalStorageProvider;

import net.minecraftforge.items.ItemStackHandler;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * A chest's worth of slots that live on the terminal item itself, so whatever is put in it travels with
 * that terminal. The contents are stored in the item's NBT by {@link TerminalStorageProvider}.
 */
public class StorageApp implements ITerminalApp {

    private static final String SLOT_GROUP = "terminal_storage";
    private static final int SLOT_SIZE = 18;

    @Override
    public IWidget buildWidgets(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings,
                                ModularPanel panel) {
        var root = new ParentWidget<>()
                .sizeRel(1.0F)
                .background(Terminal2Theme.COLOR_BACKGROUND_1);

        // Mirrors the toolbelt: if the capability is somehow missing, show an empty grid rather than crashing.
        var capabilityHandler = TerminalStorageProvider.getHandler(guiData.getUsedItemStack());
        final ItemStackHandler handler = capabilityHandler != null ? capabilityHandler :
                new ItemStackHandler(TerminalStorageProvider.SLOT_COUNT);

        var group = new SlotGroup(SLOT_GROUP, TerminalStorageProvider.ROW_SIZE);
        guiSyncManager.registerSlotGroup(group);

        List<ItemSlot> slots = new ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            slots.add(new ItemSlot());
        }

        var grid = new Grid()
                .margin(0)
                .minElementMargin(0)
                .coverChildren()
                .mapTo(group.getRowSize(), slots, (index, slot) -> slot
                        .slot(SyncHandlers.itemSlot(handler, index)
                                .slotGroup(group))
                        .background(GTGuiTextures.SLOT)
                        .debugName(SLOT_GROUP + "_slot_" + index))
                .debugName(SLOT_GROUP);

        return root.child(grid.center());
    }

    @Override
    public IDrawable getIcon() {
        return GTGuiTextures.SLOT;
    }
}
