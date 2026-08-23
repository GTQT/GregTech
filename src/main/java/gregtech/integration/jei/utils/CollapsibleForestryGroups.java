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
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import mezz.jei.api.ICollapsibleGroupRegistry;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
        buildCrateGroup(registry);
        buildCanGroup(registry);
        buildCapsuleGroup(registry);
        buildRefractoryGroup(registry);
        buildSaplingGroup(registry);
        buildPollenGroup(registry);
        buildButterflyGroup(registry);
        buildSerumGroup(registry);
        buildCaterpillarGroup(registry);
        buildCocoonGroup(registry);
        buildLogGroup(registry);
        buildPlankGroup(registry);
        buildSlabGroup(registry);
        buildFenceGroup(registry);
        buildStairGroup(registry);
        buildDoorGroup(registry);
        buildLeafGroup(registry);
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
        addGroup(registry, "electrodes", ForestryModule.ELECTRODE_APATITE, ForestryModule.ELECTRODE_BLAZE,
                ForestryModule.ELECTRODE_BRONZE, ForestryModule.ELECTRODE_COPPER,
                ForestryModule.ELECTRODE_DIAMOND, ForestryModule.ELECTRODE_EMERALD,
                ForestryModule.ELECTRODE_ENDER, ForestryModule.ELECTRODE_GOLD,
                ForestryModule.ELECTRODE_IRON, ForestryModule.ELECTRODE_LAPIS,
                ForestryModule.ELECTRODE_OBSIDIAN, ForestryModule.ELECTRODE_ORCHID,
                ForestryModule.ELECTRODE_RUBBER, ForestryModule.ELECTRODE_TIN);
    }

    /** Forestry crates ({@code forestry:crated.*}) — one item per crated item, all sharing the prefix. */
    private static void buildCrateGroup(ICollapsibleGroupRegistry registry) {
        buildPrefixGroup(registry, "forestry_crates", "crated");
    }

    /** Forestry cans ({@code forestry:can}) — one stack per fluid, distinguished by NBT. */
    private static void buildCanGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "forestry_cans", getSubItems("forestry:can"));
    }

    /** Forestry capsules ({@code forestry:capsule}) — one stack per fluid, distinguished by NBT. */
    private static void buildCapsuleGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "forestry_capsules", getSubItems("forestry:capsule"));
    }

    /** Forestry refractory capsules ({@code forestry:refractory}) — one stack per fluid, distinguished by NBT. */
    private static void buildRefractoryGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "forestry_refractories", getSubItems("forestry:refractory"));
    }

    /** Forestry tree saplings ({@code forestry:sapling}) — one stack per tree species, distinguished by NBT. */
    private static void buildSaplingGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "forestry_saplings", getSubItems("forestry:sapling"));
    }

    /** Forestry fertile pollen ({@code forestry:pollen_fertile}) — one stack per tree species, distinguished by NBT. */
    private static void buildPollenGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "forestry_pollen", getSubItems("forestry:pollen_fertile"));
    }

    /** Forestry butterflies ({@code forestry:butterfly_ge}) — one stack per species, distinguished by NBT. */
    private static void buildButterflyGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "forestry_butterflies", getSubItems("forestry:butterfly_ge"));
    }

    /** Forestry butterfly serums ({@code forestry:serum_ge}) — one stack per species, distinguished by NBT. */
    private static void buildSerumGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "forestry_serums", getSubItems("forestry:serum_ge"));
    }

    /** Forestry caterpillars ({@code forestry:caterpillar_ge}) — one stack per species, distinguished by NBT. */
    private static void buildCaterpillarGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "forestry_caterpillars", getSubItems("forestry:caterpillar_ge"));
    }

    /** Forestry cocoons ({@code forestry:cocoon_ge}) — one stack per species, distinguished by NBT. */
    private static void buildCocoonGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "forestry_cocoons", getSubItems("forestry:cocoon_ge"));
    }

    /** Forestry logs ({@code forestry:logs.*}). */
    private static void buildLogGroup(ICollapsibleGroupRegistry registry) {
        buildPrefixGroup(registry, "forestry_logs", "logs");
    }

    /** Forestry planks ({@code forestry:planks.*}). */
    private static void buildPlankGroup(ICollapsibleGroupRegistry registry) {
        buildPrefixGroup(registry, "forestry_planks", "planks");
    }

    /** Forestry wooden slabs ({@code forestry:slabs.*}). */
    private static void buildSlabGroup(ICollapsibleGroupRegistry registry) {
        buildPrefixGroup(registry, "forestry_slabs", "slabs");
    }

    /** Forestry wooden fences ({@code forestry:fences.*}). */
    private static void buildFenceGroup(ICollapsibleGroupRegistry registry) {
        buildPrefixGroup(registry, "forestry_fences", "fences");
    }

    /** Forestry wooden stairs ({@code forestry:stairs.*}). */
    private static void buildStairGroup(ICollapsibleGroupRegistry registry) {
        buildPrefixGroup(registry, "forestry_stairs", "stairs");
    }

    /** Forestry wooden doors ({@code forestry:doors.*}). */
    private static void buildDoorGroup(ICollapsibleGroupRegistry registry) {
        buildPrefixGroup(registry, "forestry_doors", "doors");
    }

    /** Forestry leaves ({@code forestry:leaves.*}). */
    private static void buildLeafGroup(ICollapsibleGroupRegistry registry) {
        buildPrefixGroup(registry, "forestry_leaves", "leaves");
    }

    /**
     * Collects all Forestry items whose registry path starts with {@code prefix}
     * (e.g. {@code logs.0}, {@code stairs.larch}, {@code crated.xxx}) and folds them into one group.
     */
    private static void buildPrefixGroup(ICollapsibleGroupRegistry registry, String id, String prefix) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            ResourceLocation rl = item.getRegistryName();
            if (rl == null || !"forestry".equals(rl.getNamespace())) continue;
            if (!rl.getPath().startsWith(prefix)) continue;
            stacks.addAll(getSubItems(item));
        }
        addGroup(registry, id, stacks);
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
