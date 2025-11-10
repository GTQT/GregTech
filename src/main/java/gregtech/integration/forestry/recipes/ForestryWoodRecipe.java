package gregtech.integration.forestry.recipes;

import gregtech.api.recipes.ModHandler;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.Mods;
import gregtech.common.ConfigHolder;
import gregtech.loaders.recipe.WoodTypeEntry;

import net.minecraft.util.ResourceLocation;

import forestry.api.arboriculture.EnumForestryWoodType;
import forestry.api.arboriculture.EnumVanillaWoodType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static gregtech.api.GTValues.MODID;
import static gregtech.loaders.recipe.WoodRecipeLoader.registerWoodTypeRecipe;
import static gregtech.loaders.recipe.WoodRecipeLoader.registerWoodUnificationInfo;

public class ForestryWoodRecipe {

    private static final String mcModId = Mods.Names.FORESTRY;
    private static List<WoodTypeEntry> DEFAULT_ENTRIES;
    private static List<WoodTypeEntry> FIREPROOF_ENTRIES;

    private static void initEntries() {
        DEFAULT_ENTRIES = new ArrayList<>();
        FIREPROOF_ENTRIES = new ArrayList<>();

        int plankId = -1, logId = -1, slabId = -1;
        List<String> woodNames = Arrays.stream(EnumForestryWoodType.values()).map(Enum::name)
                .collect(Collectors.toList());
        for (int i = 0; i < woodNames.size(); i++) {
            String woodName = woodNames.get(i).toLowerCase();
            int plankMeta = i % 16;
            int logMeta = i % 4;
            int slabMeta = i % 8;
            if (plankMeta == 0) plankId++;
            if (logMeta == 0) logId++;
            if (slabMeta == 0) slabId++;
            DEFAULT_ENTRIES.add(getEntry(woodName, plankMeta, logMeta, slabMeta, plankId, logId, slabId, ""));
            FIREPROOF_ENTRIES
                    .add(getEntry(woodName, plankMeta, logMeta, slabMeta, plankId, logId, slabId, "fireproof."));
        }

        plankId = -1;
        logId = -1;
        slabId = -1;
        List<String> vanillaWoodNames = Arrays.stream(EnumVanillaWoodType.values()).map(Enum::name)
                .collect(Collectors.toList());
        for (int i = 0; i < vanillaWoodNames.size(); i++) {
            int plankMeta = i % 16;
            int logMeta = i % 4;
            int slabMeta = i % 8;
            if (plankMeta == 0) plankId++;
            if (logMeta == 0) logId++;
            if (slabMeta == 0) slabId++;
            FIREPROOF_ENTRIES.add(getEntry(vanillaWoodNames.get(i).toLowerCase(), plankMeta, logMeta, slabMeta, plankId,
                    logId, slabId, "vanilla.fireproof."));
        }
    }

    private static WoodTypeEntry getEntry(String woodName, int plankMeta, int logMeta, int slabMeta, int plankId,
                                          int logId, int slabId, String separator) {
        return new WoodTypeEntry.Builder(mcModId, woodName)
                .log(Mods.Forestry.getItem("logs." + separator + logId, logMeta, 1)).removeCharcoalRecipe()
                .planks(Mods.Forestry.getItem("planks." + separator + plankId, plankMeta, 1), null)
                .door(Mods.Forestry.getItem("doors." + separator + woodName), null)
                .slab(Mods.Forestry.getItem("slabs." + separator + slabId, slabMeta, 1), null)
                .fence(Mods.Forestry.getItem("fences." + separator + plankId, plankMeta, 1), null)
                .fenceGate(Mods.Forestry.getItem("fence.gates." + separator + woodName), null)
                .stairs(Mods.Forestry.getItem("stairs." + separator + woodName), null).addStairsRecipe()
                .registerAllUnificationInfo()
                .build();
    }

    public static void init() {
        initEntries();
        String[] types = { "planks_", "slabs_", "fences_", "fence_gates_", "stairs_" };
        for (WoodTypeEntry entry : DEFAULT_ENTRIES) {
            for (String type : types) {
                ModHandler.removeRecipeByName(Mods.Forestry.getResource(type + entry.woodName));
            }

            // only for normal woods
            ModHandler.removeRecipeByName(Mods.Forestry.getResource("doors_" + entry.woodName));

            registerWoodTypeRecipe(true,entry);
            registerWoodUnificationInfo(entry);
        }

        for (WoodTypeEntry entry : FIREPROOF_ENTRIES) {
            for (String type : types) {
                ModHandler.removeRecipeByName(Mods.Forestry.getResource("fireproof_" + type + entry.woodName));
            }

            registerWoodTypeRecipe(true,entry);
            registerWoodUnificationInfo(entry);
        }

        // Fireproof for vanilla wood
        String[] vanillaWoodNames = { "oak", "spruce", "birch", "jungle", "acacia", "dark_oak" };
        for (String woodName : vanillaWoodNames) {
            ModHandler.removeRecipeByName(new ResourceLocation(MODID, woodName + "_planks_saw"));
        }

        if (!Mods.ForestryCharcoal.isModLoaded()) return;
        if (!ConfigHolder.recipes.harderCharcoalRecipe) return;
        ModHandler.removeRecipeByName(Mods.Forestry.getResource("wood_pile"));

        RecipeMaps.COMPRESSOR_RECIPES.recipeBuilder()
                .input("logWood", 4)
                .outputs(Mods.Forestry.getItem("wood_pile"))
                .duration(300).EUt(2)
                .buildAndRegister();
    }
}
