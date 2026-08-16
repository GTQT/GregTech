package gregtech.integration.jei.utils;

import gregtech.api.items.metaitem.MetaItem.MetaValueItem;
import gregtech.integration.tconstruct.TicMetaItem;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import mezz.jei.api.ICollapsibleGroupRegistry;

import org.jetbrains.annotations.ApiStatus;

import slimeknights.tconstruct.library.tinkering.MaterialItem;
import slimeknights.tconstruct.library.tools.ToolCore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Tinkers' Construct-related JEI collapsible groups, separated from {@link CollapsibleItemGroups}
 * so that {@code NoClassDefFoundError} is avoided when Tinkers' Construct is not loaded.
 */
@ApiStatus.Internal
public final class CollapsibleTicGroups {

    private CollapsibleTicGroups() {}

    public static void registerGroups(ICollapsibleGroupRegistry registry) {
        buildTicExtruderShapeGroup(registry);
        buildCastGroup(registry);
        buildClayCastGroup(registry);
        buildPatternGroup(registry);
        buildToolTableGroup(registry);
        buildToolForgeGroup(registry);
        buildCustomCastGroup(registry);
        buildToolPartGroups(registry);
        buildToolGroups(registry);
    }

    /** TiC tool part extruder shapes — folds the TicMetaItem molds into one entry. */
    private static void buildTicExtruderShapeGroup(ICollapsibleGroupRegistry registry) {
        List<ItemStack> stacks = new ArrayList<>(TicMetaItem.SHAPE_EXTRUDERS.length);
        for (MetaValueItem item : TicMetaItem.SHAPE_EXTRUDERS) {
            if (item == null) continue;
            stacks.add(item.getStackForm());
        }
        addGroup(registry, "tic_extruder_shapes", stacks);
    }

    /** TiC metal casts — one stack per registered part type, distinguished by NBT. */
    private static void buildCastGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "tic_casts", getSubItems("tconstruct:cast"));
    }

    /** TiC clay casts — one stack per registered part type, distinguished by NBT. */
    private static void buildClayCastGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "tic_clay_casts", getSubItems("tconstruct:clay_cast"));
    }

    /** TiC patterns — one stack per registered part type, distinguished by NBT. */
    private static void buildPatternGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "tic_patterns", getSubItems("tconstruct:pattern"));
    }

    /** TiC tool tables (tool station, tool table, ...) — texture variant in NBT. */
    private static void buildToolTableGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "tic_tool_tables", getSubItems("tconstruct:tooltables"));
    }

    /** TiC tool forge — texture variant in NBT. */
    private static void buildToolForgeGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "tic_tool_forge", getSubItems("tconstruct:toolforge"));
    }

    /** TiC custom casts — one stack per size (meta 0-4). */
    private static void buildCustomCastGroup(ICollapsibleGroupRegistry registry) {
        Item item = Item.getByNameOrId("tconstruct:cast_custom");
        if (item == null) return;
        addGroup(registry, "tic_cast_custom", getSubItems(item));
    }

    /**
     * TiC tool parts (shards, heads, rods, bowstrings, ...) — one group per item, material variants distinguished
     * by NBT. All items whose class extends {@link MaterialItem} carry material NBT; their display name uses TiC's
     * own lang key ({@code item.tconstruct.<part>.name}).
     */
    private static void buildToolPartGroups(ICollapsibleGroupRegistry registry) {
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation rl = item.getRegistryName();
            if (rl == null || !"tconstruct".equals(rl.getNamespace())) continue;
            if (!(item instanceof MaterialItem)) continue;
            addGroup(registry, "tic_part_" + rl.getPath(), item.getTranslationKey() + ".name", getSubItems(item));
        }
    }

    /**
     * TiC finished tools (pickaxe, hatchet, sword, ...) — one group per item, material combinations distinguished
     * by {@code TinkerData.Materials} NBT. All items whose class extends {@link ToolCore} are finished tools.
     */
    private static void buildToolGroups(ICollapsibleGroupRegistry registry) {
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation rl = item.getRegistryName();
            if (rl == null || !"tconstruct".equals(rl.getNamespace())) continue;
            if (!(item instanceof ToolCore)) continue;
            addGroup(registry, "tic_tool_" + rl.getPath(), item.getTranslationKey() + ".name", getSubItems(item));
        }
    }

    /** Collects all sub-items (NBT/meta variants) of the given item via the search creative tab. */
    private static List<ItemStack> getSubItems(String itemId) {
        Item item = Item.getByNameOrId(itemId);
        if (item == null) return Collections.emptyList();
        return getSubItems(item);
    }

    private static List<ItemStack> getSubItems(Item item) {
        NonNullList<ItemStack> sub = NonNullList.create();
        item.getSubItems(CreativeTabs.SEARCH, sub);
        return sub;
    }

    private static void addGroup(ICollapsibleGroupRegistry registry,
                                 String id,
                                 Collection<ItemStack> stacks) {
        addGroup(registry, id, "gregtech.jei.group." + id, stacks);
    }

    private static void addGroup(ICollapsibleGroupRegistry registry,
                                 String id,
                                 String displayNameKey,
                                 Collection<ItemStack> stacks) {
        if (stacks.size() < 2) return;
        var builder = registry.newGroup("gregtech:" + id, displayNameKey);
        for (ItemStack stack : stacks) {
            builder.add(stack);
        }
        builder.build();
    }
}
