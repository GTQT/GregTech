package gregtech.common.metatileentities.workbench;

import gregtech.api.capability.impl.ItemHandlerList;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Contract shared by everything that hosts the crafting station (workbench) GUI.
 * <p>
 * It is implemented by {@link MetaTileEntityWorkbench} and by
 * {@code gregtech.common.covers.CoverCraftingTable}, so that both open the exact same GUI
 * built by {@code gregtech.common.mui.widget.workbench.WorkbenchUI}.
 */
public interface IWorkbenchHolder {

    /**
     * @return the crafting logic driving the 3x3 grid and the crafting output
     */
    CraftingRecipeLogic getCraftingRecipeLogic();

    /**
     * @return the temporary/locked recipe memory
     */
    CraftingRecipeMemory getRecipeMemory();

    /**
     * @return the 3x3 crafting grid, one item per slot
     */
    ItemStackHandler getCraftingGrid();

    /**
     * @return the 2x9 inventory used as extra ingredient storage
     */
    ItemStackHandler getInternalInventory();

    /**
     * @return the 9 single item slots holding the tools used by the recipes
     */
    ItemStackHandler getToolInventory();

    /**
     * @return the inventories found around the workbench
     */
    ItemHandlerList getConnectedInventory();

    /**
     * @return every inventory the workbench may pull crafting ingredients from: the internal inventory,
     *         the tool inventory and all connected inventories
     */
    ItemHandlerList getAvailableHandlers();

    /**
     * Marks the connected inventory cache as dirty and rebuilds it.
     */
    void refreshInventoryCache();

    int getItemsCrafted();

    void setItemsCrafted(int itemsCrafted);

    /**
     * @return the item used as the "workbench" page tab icon
     */
    ItemStack getWorkbenchIcon();

    /**
     * @return the name of the main ModularUI panel
     */
    String getWorkbenchPanelName();

    /**
     * @return the translation key used as the GUI title
     */
    String getWorkbenchTitleKey();
}
