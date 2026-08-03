package gregtech.integration.jei.utils;

import gregtech.api.GregTechAPI;
import gregtech.api.items.materialitem.MetaPrefixItem;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.MetaItem.MetaValueItem;
import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.util.Mods;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockCompressed;
import gregtech.common.blocks.BlockFrame;
import gregtech.common.blocks.BlockLamp;
import gregtech.common.blocks.BlockLeanOre;
import gregtech.common.blocks.BlockOre;
import gregtech.common.blocks.BlockSheet;
import gregtech.common.blocks.StoneVariantBlock;
import gregtech.common.items.MetaItems;
import gregtech.common.items.ToolItems;
import gregtech.common.pipelike.cable.BlockCable;
import gregtech.common.pipelike.fluidpipe.BlockFluidPipe;
import gregtech.common.pipelike.heat.BlockHeatConductor;
import gregtech.common.pipelike.itempipe.BlockItemPipe;
import gregtech.integration.forestry.ForestryModule;
import gregtech.integration.forestry.bees.GTCombType;
import gregtech.integration.forestry.bees.GTDropType;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mezz.jei.api.ICollapsibleGroupRegistry;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static gregtech.common.blocks.MetaBlocks.*;

/**
 * Builds JEI collapsible groups for GregTech's variant-heavy items so that
 * dusts, plates, cables, pipes, ores, etc. each fold into a single entry
 * in the JEI item list.
 */
@ApiStatus.Internal
public final class CollapsibleItemGroups {

    private CollapsibleItemGroups() {}

    /** Registers all GTCEu collapsible groups when enabled in config. */
    public static void registerGroups(ICollapsibleGroupRegistry registry) {
        if (!ConfigHolder.client.collapseGTItems) return;

        buildPrefixGroups(registry);
        buildCableGroup(registry);
        buildHeatConductorGroup(registry);
        buildItemPipeGroup(registry);
        buildFluidPipeGroup(registry);
        buildOreGroup(registry);
        buildStoneVariantGroup(registry);
        buildWarningSignGroup(registry);
        buildMetalSheetGroup(registry);
        buildPanellingGroup(registry);
        buildFrameGroup(registry);
        buildLampGroup(registry);
        buildStorageBlockGroup(registry);
        buildFluidCellGroup(registry);
        buildMoldGroup(registry);
        buildExtruderShapeGroup(registry);
        buildGlassLensGroup(registry);
        buildChemicalDyesGroup(registry);
        buildCastingMoldGroup(registry);
        buildDisposableToolGroup(registry);
        buildWirelessCoverGroup(registry);
        buildFluidBucketGroup(registry);
        buildBeeCombGroup(registry);
        buildBeeDropGroup(registry);
        buildForestryBeeGroup(registry);
        buildElectrodeGroup(registry);
        buildToolGroups(registry);
    }

    /** One group per {@link OrePrefix} (dusts, plates, ingots, etc.). */
    private static void buildPrefixGroups(ICollapsibleGroupRegistry registry) {
        Map<OrePrefix, List<ItemStack>> buckets = new Object2ObjectOpenHashMap<>();
        for (MetaItem<?> metaItem : MetaItems.ITEMS) {
            if (!(metaItem instanceof MetaPrefixItem prefixItem)) continue;
            OrePrefix prefix = prefixItem.getOrePrefix();
            for (MetaValueItem valueItem : metaItem.getAllItems()) {
                //noinspection ConstantValue
                if (valueItem == null) continue;
                buckets.computeIfAbsent(prefix, k -> new ArrayList<>()).add(valueItem.getStackForm());
            }
        }
        for (Map.Entry<OrePrefix, List<ItemStack>> entry : buckets.entrySet()) {
            addGroup(registry, "oreprefix." + entry.getKey().name(), entry.getValue());
        }
    }

    /** All GT cables and wires (every insulation × material). */
    private static void buildCableGroup(ICollapsibleGroupRegistry registry) {
        List<ItemStack> stacks = new ArrayList<>();
        for (BlockCable[] blocks : CABLES.values()) {
            for (BlockCable block : blocks) {
                addSubBlocks(stacks, block);
            }
        }
        addGroup(registry, "cables", stacks);
    }

    /** All GT item pipes (every type × material). */
    private static void buildItemPipeGroup(ICollapsibleGroupRegistry registry) {
        List<ItemStack> stacks = new ArrayList<>();
        for (BlockItemPipe[] blocks : ITEM_PIPES.values()) {
            for (BlockItemPipe block : blocks) {
                addSubBlocks(stacks, block);
            }
        }
        addGroup(registry, "item_pipes", stacks);
    }

    /** All GT fluid pipes (every type × material). */
    private static void buildFluidPipeGroup(ICollapsibleGroupRegistry registry) {
        List<ItemStack> stacks = new ArrayList<>();
        for (BlockFluidPipe[] blocks : FLUID_PIPES.values()) {
            for (BlockFluidPipe block : blocks) {
                addSubBlocks(stacks, block);
            }
        }
        addGroup(registry, "fluid_pipes", stacks);
    }

    /** All GT ores and lean ores (material × stone type). */
    private static void buildOreGroup(ICollapsibleGroupRegistry registry) {
        List<ItemStack> stacks = new ArrayList<>();
        for (BlockOre block : ORES) {
            addSubBlocks(stacks, block);
        }
        for (BlockLeanOre block : LEAN_ORES) {
            addSubBlocks(stacks, block);
        }
        addGroup(registry, "ores", stacks);
    }

    /** All GT frame boxes. */
    private static void buildFrameGroup(ICollapsibleGroupRegistry registry) {
        List<ItemStack> stacks = new ArrayList<>();
        for (BlockFrame block : FRAME_BLOCKS) {
            for (Material material : block.getVariantProperty().getAllowedValues()) {
                if (material == Materials.NULL) continue;
                stacks.add(block.getItem(material));
            }
        }
        addGroup(registry, "frames", stacks);
    }

    /** All GT lamps, both normal and borderless. */
    private static void buildLampGroup(ICollapsibleGroupRegistry registry) {
        List<ItemStack> stacks = new ArrayList<>();
        for (BlockLamp block : LAMPS.values()) {
            addSubBlocks(stacks, block);
        }
        for (BlockLamp block : BORDERLESS_LAMPS.values()) {
            addSubBlocks(stacks, block);
        }
        addGroup(registry, "lamps", stacks);
    }

    /** All GT compressed / material storage blocks. */
    private static void buildStorageBlockGroup(ICollapsibleGroupRegistry registry) {
        List<ItemStack> stacks = new ArrayList<>();
        for (BlockCompressed block : COMPRESSED_BLOCKS) {
            for (Material material : block.getVariantProperty().getAllowedValues()) {
                if (material == Materials.NULL) continue;
                stacks.add(block.getItem(material));
            }
        }
        addGroup(registry, "storage_blocks", stacks);
    }

    /** Empty GT fluid cells. */
    private static void buildFluidCellGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "fluid_cells", new MetaValueItem[]{
                MetaItems.FLUID_CELL,
                MetaItems.FLUID_CELL_UNIVERSAL,
                MetaItems.FLUID_CELL_LARGE_STEEL,
                MetaItems.FLUID_CELL_LARGE_ALUMINIUM,
                MetaItems.FLUID_CELL_LARGE_STAINLESS_STEEL,
                MetaItems.FLUID_CELL_LARGE_TITANIUM,
                MetaItems.FLUID_CELL_LARGE_TUNGSTEN_STEEL,
                MetaItems.FLUID_CELL_GLASS_VIAL,
        });
    }

    /** GT fluid-solidifier molds. */
    private static void buildMoldGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "molds", MetaItems.SHAPE_MOLDS);
    }

    /** GT extruder shapes. */
    private static void buildExtruderShapeGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "extruder_shapes", MetaItems.SHAPE_EXTRUDERS);
    }

    /** GT glass lenses. */
    private static void buildGlassLensGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "glass_lens", MetaItems.GLASS_LENSES.values().toArray(new MetaValueItem[0]));
    }

    /** GT chemical dyes. */
    private static void buildChemicalDyesGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "chemical_dyes", MetaItems.DYE_ONLY_ITEMS);
    }

    /** All Forge universal buckets ({@code forge:bucketfilled}) — NBT distinguishes the fluid. */
    private static void buildFluidBucketGroup(ICollapsibleGroupRegistry registry) {
        Item bucket = Item.getByNameOrId("forge:bucketfilled");
        if (bucket == null) return;
        List<ItemStack> stacks = new ArrayList<>();
        NonNullList<ItemStack> sub = NonNullList.create();
        bucket.getSubItems(CreativeTabs.SEARCH, sub);
        stacks.addAll(sub);
        addGroup(registry, "fluid_buckets", stacks);
    }

    /** All GT heat conductors (every type × material). */
    private static void buildHeatConductorGroup(ICollapsibleGroupRegistry registry) {
        List<ItemStack> stacks = new ArrayList<>();
        for (BlockHeatConductor[] blocks : HEAT_CONDUCTOR.values()) {
            for (BlockHeatConductor block : blocks) {
                addSubBlocks(stacks, block);
            }
        }
        addGroup(registry, "heat_conductors", stacks);
    }

    /** All GT stone variant blocks (smooth, cobble, bricks, etc. × stone type). */
    private static void buildStoneVariantGroup(ICollapsibleGroupRegistry registry) {
        List<ItemStack> stacks = new ArrayList<>();
        for (StoneVariantBlock block : STONE_BLOCKS.values()) {
            addSubBlocks(stacks, block);
        }
        addGroup(registry, "stone_variants", stacks);
    }

    /** GT warning signs (normal + variant 1). */
    private static void buildWarningSignGroup(ICollapsibleGroupRegistry registry) {
        List<ItemStack> stacks = new ArrayList<>();
        addSubBlocks(stacks, WARNING_SIGN);
        addSubBlocks(stacks, WARNING_SIGN_1);
        addGroup(registry, "warning_signs", stacks);
    }

    /** GT metal sheets (colored, large colored, and material-based sheets). */
    private static void buildMetalSheetGroup(ICollapsibleGroupRegistry registry) {
        List<ItemStack> stacks = new ArrayList<>();
        addSubBlocks(stacks, METAL_SHEET);
        addSubBlocks(stacks, LARGE_METAL_SHEET);
        for (BlockSheet block : SHEET_BLOCKS) {
            addSubBlocks(stacks, block);
        }
        addGroup(registry, "metal_sheets", stacks);
    }

    /** GT panelling variants. */
    private static void buildPanellingGroup(ICollapsibleGroupRegistry registry) {
        List<ItemStack> stacks = new ArrayList<>();
        addSubBlocks(stacks, PANELLING);
        addGroup(registry, "panelling", stacks);
    }

    /** GT bee combs ({@link GTCombType}). Only active when Forestry is loaded. */
    private static void buildBeeCombGroup(ICollapsibleGroupRegistry registry) {
        if (ForestryModule.COMBS == null) return;
        List<ItemStack> stacks = new ArrayList<>();
        for (GTCombType type : GTCombType.VALUES) {
            if (!type.showInList) continue;
            stacks.add(new ItemStack(ForestryModule.COMBS, 1, type.ordinal()));
        }
        addGroup(registry, "bee_combs", stacks);
    }

    /** GT bee produce drops ({@link GTDropType}). Only active when Forestry is loaded. */
    private static void buildBeeDropGroup(ICollapsibleGroupRegistry registry) {
        if (ForestryModule.DROPS == null) return;
        List<ItemStack> stacks = new ArrayList<>();
        for (GTDropType type : GTDropType.VALUES) {
            stacks.add(new ItemStack(ForestryModule.DROPS, 1, type.ordinal()));
        }
        addGroup(registry, "bee_drops", stacks);
    }

    /** Forestry bee items (drone, princess, queen, larvae) — each meta is a different species. */
    private static void buildForestryBeeGroup(ICollapsibleGroupRegistry registry) {
        if (!Mods.Forestry.isModLoaded()) return;
        List<ItemStack> stacks = new ArrayList<>();
        // Forestry bee items only return sub-items when tab == Tabs.tabApiculture
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

    /** GT Forestry electrodes. Only active when Forestry is loaded. */
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

    /** GT casting molds (empty, saw, hammer, wrench, etc.). */
    private static void buildCastingMoldGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "casting_molds", new MetaValueItem[]{
                MetaItems.CASTING_MOLD_EMPTY, MetaItems.CASTING_MOLD_SAW,
                MetaItems.CASTING_MOLD_HARD_HAMMER, MetaItems.CASTING_MOLD_SOFT_MALLET,
                MetaItems.CASTING_MOLD_WRENCH, MetaItems.CASTING_MOLD_FILE,
                MetaItems.CASTING_MOLD_CROWBAR, MetaItems.CASTING_MOLD_SCREWDRIVER,
                MetaItems.CASTING_MOLD_MORTAR, MetaItems.CASTING_MOLD_WIRE_CUTTER,
                MetaItems.CASTING_MOLD_KNIFE, MetaItems.CASTING_MOLD_BUTCHERY_KNIFE,
                MetaItems.CASTING_MOLD_ROLLING_PIN,
        });
    }

    /** GT disposable tools (saw, hammer, wrench, etc.). */
    private static void buildDisposableToolGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "disposable_tools", new MetaValueItem[]{
                MetaItems.DISPOSABLE_SAW, MetaItems.DISPOSABLE_HARD_HAMMER,
                MetaItems.DISPOSABLE_SOFT_MALLET, MetaItems.DISPOSABLE_WRENCH,
                MetaItems.DISPOSABLE_FILE, MetaItems.DISPOSABLE_CROWBAR,
                MetaItems.DISPOSABLE_SCREWDRIVER, MetaItems.DISPOSABLE_MORTAR,
                MetaItems.DISPOSABLE_WIRE_CUTTER, MetaItems.DISPOSABLE_KNIFE,
                MetaItems.DISPOSABLE_BUTCHERY_KNIFE, MetaItems.DISPOSABLE_ROLLING_PIN,
        });
    }

    /** GT wireless energy covers (all voltage tiers, input + output). */
    private static void buildWirelessCoverGroup(ICollapsibleGroupRegistry registry) {
        addGroup(registry, "wireless_covers", new MetaValueItem[]{
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_ULV, MetaItems.WIRELESS_ENERGY_COVER_INPUT_LV,
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_MV, MetaItems.WIRELESS_ENERGY_COVER_INPUT_HV,
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_EV, MetaItems.WIRELESS_ENERGY_COVER_INPUT_IV,
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_LUV, MetaItems.WIRELESS_ENERGY_COVER_INPUT_ZPM,
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_UV, MetaItems.WIRELESS_ENERGY_COVER_INPUT_UHV,
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_UEV, MetaItems.WIRELESS_ENERGY_COVER_INPUT_UIV,
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_UXV, MetaItems.WIRELESS_ENERGY_COVER_INPUT_OPV,
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_MAX,
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_ULV, MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_LV,
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_MV, MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_HV,
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_EV, MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_IV,
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_LUV, MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_ZPM,
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UV, MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UHV,
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UEV, MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UIV,
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UXV, MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_OPV,
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_MAX,
        });
    }

    /** One group per GT tool family (wrench, hammer, screwdriver, etc.) — each material variant folded together. */
    private static void buildToolGroups(ICollapsibleGroupRegistry registry) {
        Collection<Material> materials = GregTechAPI.materialManager.getRegisteredMaterials();
        for (IGTTool tool : ToolItems.getAllTools()) {
            List<ItemStack> stacks = new ArrayList<>();
            boolean electric = tool.isElectric();
            for (Material material : materials) {
                if (!material.hasProperty(PropertyKey.TOOL)) continue;
                stacks.add(electric ? tool.get(material, Long.MAX_VALUE) : tool.get(material));
            }
            if (stacks.size() < 2) continue;
            addGroup(registry, "tool." + tool.getToolId(), "tool." + tool.getToolId(), stacks);
        }
    }

    private static void addSubBlocks(List<ItemStack> out, Block block) {
        NonNullList<ItemStack> sub = NonNullList.create();
        block.getSubBlocks(CreativeTabs.SEARCH, sub);
        out.addAll(sub);
    }

    private static void addGroup(ICollapsibleGroupRegistry registry,
                                 String id,
                                 MetaValueItem... items) {
        List<ItemStack> stacks = new ArrayList<>(items.length);
        for (MetaValueItem item : items) {
            //noinspection ConstantValue
            if (item == null) continue;
            stacks.add(item.getStackForm());
        }
        addGroup(registry, id, id, stacks);
    }

    private static void addGroup(ICollapsibleGroupRegistry registry,
                                 String id,
                                 Collection<ItemStack> stacks) {
        addGroup(registry, id, id, stacks);
    }

    private static void addGroup(ICollapsibleGroupRegistry registry,
                                 String id,
                                 String displayNameKey,
                                 Collection<ItemStack> stacks) {
        if (stacks.size() < 2) return;
        var builder = registry.newGroup("gregtech:" + id, "gregtech.jei.group." + displayNameKey);
        for (ItemStack stack : stacks) {
            builder.add(stack);
        }
        builder.build();
    }
}
