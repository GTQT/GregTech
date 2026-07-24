package gregtech.common.items;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.StandardMetaItem;

import net.minecraft.item.ItemStack;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/**
 * Registry for planet/dimension display items used in JEI ore/fluid vein pages.
 * <p>
 * Each {@link MetaValueItem} represents a celestial body (planet, moon, etc.)
 * and can be mapped to a dimension ID via {@link #setDisplayItem(int, MetaValueItem)}.
 * <p>
 * The bidirectional mapping supports:
 * <ul>
 *   <li><b>Rendering:</b> dimId → ItemStack (show planet icon on vein JEI page)</li>
 *   <li><b>Lookup:</b> ItemStack → dimId (click planet icon → show veins in that dimension)</li>
 * </ul>
 */
public class OrbItems extends StandardMetaItem {

    // ==================== Display Items ====================

    public static MetaItem<?>.MetaValueItem DISPLAY_OVERWORLD;
    public static MetaItem<?>.MetaValueItem DISPLAY_NETHER;
    public static MetaItem<?>.MetaValueItem DISPLAY_END;
    public static MetaItem<?>.MetaValueItem DISPLAY_END_MAIN_ISLAND;
    public static MetaItem<?>.MetaValueItem DISPLAY_END_OUTER_ISLANDS;
    public static MetaItem<?>.MetaValueItem DISPLAY_MOON;
    public static MetaItem<?>.MetaValueItem DISPLAY_MERCURY;
    public static MetaItem<?>.MetaValueItem DISPLAY_MARS;
    public static MetaItem<?>.MetaValueItem DISPLAY_DEIMOS;
    public static MetaItem<?>.MetaValueItem DISPLAY_PHOBOS;
    public static MetaItem<?>.MetaValueItem DISPLAY_PLUTO;
    public static MetaItem<?>.MetaValueItem DISPLAY_TITAN;
    public static MetaItem<?>.MetaValueItem DISPLAY_DEEP_DARK;

    // ==================== Registry ====================

    /** dimId → MetaValueItem (used for rendering in JEI) */
    private static final Int2ObjectMap<MetaItem<?>.MetaValueItem> DIM_TO_ITEM = new Int2ObjectOpenHashMap<>();

    /** MetaValueItem → dimId (used for reverse lookup when clicking a planet icon) */
    private static final Object2IntMap<MetaItem<?>.MetaValueItem> ITEM_TO_DIM = new Object2IntOpenHashMap<>();

    static {
        ITEM_TO_DIM.defaultReturnValue(Integer.MIN_VALUE);
    }

    /**
     * Register a planet display item with its dimension ID.
     * Call this from postInit (e.g., via Groovy/CraftTweaker scripts or addon mods)
     * to match each display item to a dimension.
     *
     * @param dimId the dimension ID
     * @param item  the MetaValueItem representing the planet / dimension
     */
    public static void setDisplayItem(int dimId, MetaItem<?>.MetaValueItem item) {
        DIM_TO_ITEM.put(dimId, item);
        ITEM_TO_DIM.put(item, dimId);
    }

    /**
     * Get the display {@link ItemStack} for a given dimension ID.
     *
     * @param dimId the dimension ID
     * @return the display ItemStack, or {@link ItemStack#EMPTY} if not registered
     */
    public static ItemStack getDisplayItem(int dimId) {
        MetaItem<?>.MetaValueItem item = DIM_TO_ITEM.get(dimId);
        if (item == null) return ItemStack.EMPTY;
        return item.getStackForm();
    }

    /**
     * Get the dimension ID associated with a display item.
     *
     * @param stack the ItemStack to look up
     * @return the dimension ID, or {@code null} if this item is not a registered display item
     */
    public static Integer getDimension(ItemStack stack) {
        if (stack.isEmpty()) return null;

        if (!(stack.getItem() instanceof MetaItem<?> metaItem)) return null;
        MetaItem<?>.MetaValueItem mvi = metaItem.getItem(stack);
        if (mvi == null) return null;

        int dimId = ITEM_TO_DIM.getInt(mvi);
        return dimId == Integer.MIN_VALUE ? null : dimId;
    }

    @Override
    protected String formatModelPath(MetaItem<?>.MetaValueItem metaValueItem) {
        String name = metaValueItem.unlocalizedName;
        // display.overworld → metaitems/display/overworld
        if (name.startsWith("display.")) {
            return "metaitems/display/" + name.substring("display.".length());
        }
        return super.formatModelPath(metaValueItem);
    }

    @Override
    public void registerSubItems() {
        int id = 0;

        // Vanilla dimensions
        DISPLAY_OVERWORLD = addItem(id++, "display.overworld");
        DISPLAY_NETHER = addItem(id++, "display.nether");
        DISPLAY_END = addItem(id++, "display.end");
        DISPLAY_END_MAIN_ISLAND = addItem(id++, "display.end_main_island");
        DISPLAY_END_OUTER_ISLANDS = addItem(id++, "display.end_outer_islands");

        // Solar system bodies
        DISPLAY_MOON = addItem(id++, "display.moon");
        DISPLAY_MERCURY = addItem(id++, "display.mercury");
        DISPLAY_MARS = addItem(id++, "display.mars");
        DISPLAY_DEIMOS = addItem(id++, "display.deimos");
        DISPLAY_PHOBOS = addItem(id++, "display.phobos");
        DISPLAY_PLUTO = addItem(id++, "display.pluto");
        DISPLAY_TITAN = addItem(id++, "display.titan");

        // Modded dimensions
        DISPLAY_DEEP_DARK = addItem(id++, "display.deep_dark");

        // ========== Default vanilla dimension mappings ==========
        // Other dimension IDs (Moon, Mars, etc.) depend on installed mods
        // and should be mapped via postInit scripts using OrbItems.setDisplayItem()
        setDisplayItem(0, DISPLAY_OVERWORLD);
        setDisplayItem(-1, DISPLAY_NETHER);
        setDisplayItem(1, DISPLAY_END);
    }
}
