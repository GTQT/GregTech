package gregtech.common.items;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.StandardMetaItem;

import gregtech.common.creativetab.GTCreativeTabs;

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

    public OrbItems() {
        super();
        setCreativeTab(GTCreativeTabs.TAB_GREGTECH_ORB);
    }

    // ==================== Display Items ====================

    public static MetaItem<?>.MetaValueItem DISPLAY_OVERWORLD;
    public static MetaItem<?>.MetaValueItem DISPLAY_NETHER;
    public static MetaItem<?>.MetaValueItem DISPLAY_TWILIGHT_FOREST;
    public static MetaItem<?>.MetaValueItem DISPLAY_UNDERGROUND;
    public static MetaItem<?>.MetaValueItem DISPLAY_ALFHEIM;
    public static MetaItem<?>.MetaValueItem DISPLAY_END;
    public static MetaItem<?>.MetaValueItem DISPLAY_MOON;
    public static MetaItem<?>.MetaValueItem DISPLAY_DEIMOS;
    public static MetaItem<?>.MetaValueItem DISPLAY_MARS;
    public static MetaItem<?>.MetaValueItem DISPLAY_PHOBOS;
    public static MetaItem<?>.MetaValueItem DISPLAY_ASTEROIDS;
    public static MetaItem<?>.MetaValueItem DISPLAY_CALLISTO;
    public static MetaItem<?>.MetaValueItem DISPLAY_CERES;
    public static MetaItem<?>.MetaValueItem DISPLAY_EUROPA;
    public static MetaItem<?>.MetaValueItem DISPLAY_GANYMEDE;
    public static MetaItem<?>.MetaValueItem DISPLAY_ROSS128B;
    public static MetaItem<?>.MetaValueItem DISPLAY_IO;
    public static MetaItem<?>.MetaValueItem DISPLAY_MERCURY;
    public static MetaItem<?>.MetaValueItem DISPLAY_VENUS;
    public static MetaItem<?>.MetaValueItem DISPLAY_ENCELADUS;
    public static MetaItem<?>.MetaValueItem DISPLAY_MIRANDA;
    public static MetaItem<?>.MetaValueItem DISPLAY_OBERON;
    public static MetaItem<?>.MetaValueItem DISPLAY_TITAN;
    public static MetaItem<?>.MetaValueItem DISPLAY_ROSS128BA;
    public static MetaItem<?>.MetaValueItem DISPLAY_PROTEUS;
    public static MetaItem<?>.MetaValueItem DISPLAY_TRITON;
    public static MetaItem<?>.MetaValueItem DISPLAY_HAUMEA;
    public static MetaItem<?>.MetaValueItem DISPLAY_KUIPER_BELT;
    public static MetaItem<?>.MetaValueItem DISPLAY_MAKEMAKE;
    public static MetaItem<?>.MetaValueItem DISPLAY_PLUTO;
    public static MetaItem<?>.MetaValueItem DISPLAY_BARNARD_C;
    public static MetaItem<?>.MetaValueItem DISPLAY_BARNARD_E;
    public static MetaItem<?>.MetaValueItem DISPLAY_BARNARD_F;
    public static MetaItem<?>.MetaValueItem DISPLAY_CENTAURI_A;
    public static MetaItem<?>.MetaValueItem DISPLAY_TCETI_E;
    public static MetaItem<?>.MetaValueItem DISPLAY_VEGA_B;
    public static MetaItem<?>.MetaValueItem DISPLAY_ANUBIS;
    public static MetaItem<?>.MetaValueItem DISPLAY_HORUS;
    public static MetaItem<?>.MetaValueItem DISPLAY_MAAHES;
    public static MetaItem<?>.MetaValueItem DISPLAY_MEHEN_BELT;
    public static MetaItem<?>.MetaValueItem DISPLAY_NEPER;
    public static MetaItem<?>.MetaValueItem DISPLAY_SETH;

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

        // T0: vanilla
        DISPLAY_OVERWORLD = addItem(id++, "display.overworld");
        DISPLAY_NETHER = addItem(id++, "display.nether");
        DISPLAY_TWILIGHT_FOREST = addItem(id++, "display.twilight_forest");
        DISPLAY_UNDERGROUND = addItem(id++, "display.underground");
        DISPLAY_ALFHEIM = addItem(id++, "display.alfheim");
        DISPLAY_END = addItem(id++, "display.end");

        // T1-T2
        DISPLAY_MOON = addItem(id++, "display.moon");
        DISPLAY_DEIMOS = addItem(id++, "display.deimos");
        DISPLAY_MARS = addItem(id++, "display.mars");
        DISPLAY_PHOBOS = addItem(id++, "display.phobos");

        // T3
        DISPLAY_ASTEROIDS = addItem(id++, "display.asteroids");
        DISPLAY_CALLISTO = addItem(id++, "display.callisto");
        DISPLAY_CERES = addItem(id++, "display.ceres");
        DISPLAY_EUROPA = addItem(id++, "display.europa");
        DISPLAY_GANYMEDE = addItem(id++, "display.ganymede");
        DISPLAY_ROSS128B = addItem(id++, "display.ross128b");

        // T4
        DISPLAY_IO = addItem(id++, "display.io");
        DISPLAY_MERCURY = addItem(id++, "display.mercury");
        DISPLAY_VENUS = addItem(id++, "display.venus");

        // T5
        DISPLAY_ENCELADUS = addItem(id++, "display.enceladus");
        DISPLAY_MIRANDA = addItem(id++, "display.miranda");
        DISPLAY_OBERON = addItem(id++, "display.oberon");
        DISPLAY_TITAN = addItem(id++, "display.titan");
        DISPLAY_ROSS128BA = addItem(id++, "display.ross128ba");

        // T6
        DISPLAY_PROTEUS = addItem(id++, "display.proteus");
        DISPLAY_TRITON = addItem(id++, "display.triton");

        // T7
        DISPLAY_HAUMEA = addItem(id++, "display.haumea");
        DISPLAY_KUIPER_BELT = addItem(id++, "display.kuiper_belt");
        DISPLAY_MAKEMAKE = addItem(id++, "display.makemake");
        DISPLAY_PLUTO = addItem(id++, "display.pluto");

        // T8
        DISPLAY_BARNARD_C = addItem(id++, "display.barnard_c");
        DISPLAY_BARNARD_E = addItem(id++, "display.barnard_e");
        DISPLAY_BARNARD_F = addItem(id++, "display.barnard_f");
        DISPLAY_CENTAURI_A = addItem(id++, "display.centauri_a");
        DISPLAY_TCETI_E = addItem(id++, "display.tceti_e");
        DISPLAY_VEGA_B = addItem(id++, "display.vega_b");

        // T9
        DISPLAY_ANUBIS = addItem(id++, "display.anubis");
        DISPLAY_HORUS = addItem(id++, "display.horus");
        DISPLAY_MAAHES = addItem(id++, "display.maahes");
        DISPLAY_MEHEN_BELT = addItem(id++, "display.mehen_belt");
        DISPLAY_NEPER = addItem(id++, "display.neper");
        DISPLAY_SETH = addItem(id, "display.seth");

        // ========== Default vanilla dimension mappings ==========
        // Other dimension IDs (Moon, Mars, etc.) depend on installed mods
        // and should be mapped via postInit scripts using OrbItems.setDisplayItem()
        setDisplayItem(0, DISPLAY_OVERWORLD);
        setDisplayItem(-1, DISPLAY_NETHER);
        setDisplayItem(1, DISPLAY_END);
    }
}
