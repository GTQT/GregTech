package gregtech.integration.jei.utils;

import gregtech.api.items.metaitem.MetaItem.MetaValueItem;
import gregtech.api.util.Mods;
import gregtech.integration.forestry.ForestryModule;
import gregtech.integration.forestry.bees.GTCombType;
import gregtech.integration.forestry.bees.GTDropType;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import mezz.jei.api.ICollapsibleGroupRegistry;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Forestry-related JEI collapsible groups, separated from {@link CollapsibleItemGroups}
 * so that {@code NoClassDefFoundError} is avoided when Forestry is not loaded.
 */
@ApiStatus.Internal
public final class CollapsibleForestryGroups {

    private CollapsibleForestryGroups() {}

    public static void registerGroups(ICollapsibleGroupRegistry registry) {
        buildBeeCombGroup(registry);
        buildBeeDropGroup(registry);
        buildForestryBeeGroup(registry);
        buildElectrodeGroup(registry);
    }

    /** GT bee combs ({@link GTCombType}). */
    private static void buildBeeCombGroup(ICollapsibleGroupRegistry registry) {
        if (ForestryModule.COMBS == null) return;
        List<ItemStack> stacks = new ArrayList<>();
        for (GTCombType type : GTCombType.VALUES) {
            if (!type.showInList) continue;
            stacks.add(new ItemStack(ForestryModule.COMBS, 1, type.ordinal()));
        }
        addGroup(registry, "bee_combs", stacks);
    }

    /** GT bee produce drops ({@link GTDropType}). */
    private static void buildBeeDropGroup(ICollapsibleGroupRegistry registry) {
        if (ForestryModule.DROPS == null) return;
        List<ItemStack> stacks = new ArrayList<>();
        for (GTDropType type : GTDropType.VALUES) {
            stacks.add(new ItemStack(ForestryModule.DROPS, 1, type.ordinal()));
        }
        addGroup(registry, "bee_drops", stacks);
    }

    /** Forestry bee items (drone, princess, queen, larvae). */
    private static void buildForestryBeeGroup(ICollapsibleGroupRegistry registry) {
        if (!Mods.Forestry.isModLoaded()) return;
        List<ItemStack> stacks = new ArrayList<>();
        CreativeTabs tab = forestry.api.core.Tabs.tabApiculture;
        String[] beeIds = { "forestry:bee_drone_ge", "forestry:bee_princess_ge",
                "forestry:bee_queen_ge", "forestry:bee_larvae_ge" };
        for (String id : beeIds) {
            Item item = Item.getByNameOrId(id);
            if (item == null) continue;
            NonNullList<ItemStack> sub = NonNullList.create();
            item.getSubItems(tab, sub);
            stacks.addAll(sub);
        }
        addGroup(registry, "forestry_bees", stacks);
    }

    /** GT Forestry electrodes. */
    private static void buildElectrodeGroup(ICollapsibleGroupRegistry registry) {
        if (ForestryModule.ELECTRODE_APATITE == null) return;
        addGroup(registry, "electrodes", new MetaValueItem[]{
                ForestryModule.ELECTRODE_APATITE, ForestryModule.ELECTRODE_BLAZE,
                ForestryModule.ELECTRODE_BRONZE, ForestryModule.ELECTRODE_COPPER,
                ForestryModule.ELECTRODE_DIAMOND, ForestryModule.ELECTRODE_EMERALD,
                ForestryModule.ELECTRODE_ENDER, ForestryModule.ELECTRODE_GOLD,
                ForestryModule.ELECTRODE_IRON, ForestryModule.ELECTRODE_LAPIS,
                ForestryModule.ELECTRODE_OBSIDIAN, ForestryModule.ELECTRODE_ORCHID,
                ForestryModule.ELECTRODE_RUBBER, ForestryModule.ELECTRODE_TIN,
        });
    }

    private static void addGroup(ICollapsibleGroupRegistry registry,
                                 String id,
                                 MetaValueItem... items) {
        List<ItemStack> stacks = new ArrayList<>(items.length);
        for (MetaValueItem item : items) {
            if (item == null) continue;
            stacks.add(item.getStackForm());
        }
        addGroup(registry, id, stacks);
    }

    private static void addGroup(ICollapsibleGroupRegistry registry,
                                 String id,
                                 Collection<ItemStack> stacks) {
        if (stacks.size() < 2) return;
        var builder = registry.newGroup("gregtech:" + id, "gregtech.jei.group." + id);
        for (ItemStack stack : stacks) {
            builder.add(stack);
        }
        builder.build();
    }
}
