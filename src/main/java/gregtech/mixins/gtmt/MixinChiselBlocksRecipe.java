package gregtech.mixins.gtmt;

import gregtech.api.recipes.GTRecipeHandler;
import gregtech.api.recipes.ModHandler;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.stack.UnificationEntry;
import gregtech.api.util.Mods;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockLamp;
import gregtech.common.blocks.MetaBlocks;
import gregtech.integration.gtmt.ChiselRecipeMaps;
import gregtech.loaders.recipe.MetaTileEntityLoader;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;

import com.github.gtexpert.gtmt.api.util.ModUtility;
import com.github.gtexpert.gtmt.integration.chisel.ChiselConfigHolder;
import com.github.gtexpert.gtmt.integration.chisel.ChiselUtil;
import com.github.gtexpert.gtmt.integration.chisel.recipes.ChiselBlocksRecipe;
import com.google.common.base.CaseFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import team.chisel.api.carving.ICarvingGroup;
import team.chisel.common.carving.Carving;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static com.github.gtexpert.gtmt.api.util.Mods.Names.PROJECT_RED_ILLUMINATION;
import static com.github.gtexpert.gtmt.api.util.Mods.ProjectRedIllumination;
import static com.github.gtexpert.gtmt.integration.chisel.metatileentities.ChiselMetaTileEntities.AUTO_CHISEL;
import static gregtech.api.GTValues.ULV;
import static gregtech.api.GTValues.VH;
import static gregtech.api.unification.ore.OrePrefix.toolHeadBuzzSaw;
import static gregtech.loaders.recipe.CraftingComponent.*;

@Mixin(ChiselBlocksRecipe.class)
public class MixinChiselBlocksRecipe {

    @Shadow(remap = false)
    private static List<ItemStack> getUniqueStacks(List<ItemStack> stacks){
        return null;
    }

    @Shadow(remap = false)
    private static String itemKey(ItemStack stack) {
        return "13+78=91";
    }

    /**
     * @author MeowmelMuku
     * @reason 修复与ceu290的兼容性
     */
    @Overwrite(remap = false)
    public static void registerAutoChiselRecipe() {
        // GregTech IMC creates OreDict-based groups (e.g. "blockCoalCoke") that overlap
        // with Chisel's built-in groups (e.g. "block_coal_coke"), so track globally to
        // avoid registering duplicate recipes across groups.
        Set<String> registeredRecipes = new HashSet<>();

        for (String groupName : Carving.chisel.getSortedGroupNames()) {
            ICarvingGroup group = Carving.chisel.getGroup(groupName);
            if (group == null) continue;

            List<ItemStack> stacks = getUniqueStacks(Carving.chisel.getItemsForChiseling(group));

            for (ItemStack target : stacks) {
                for (ItemStack input : stacks) {
                    if (input.isItemEqual(target)) continue;

                    String key = itemKey(input) + ">" + itemKey(target);
                    if (!registeredRecipes.add(key)) continue;

                    ChiselRecipeMaps.AUTO_CHISEL_RECIPES.recipeBuilder()
                            .inputs(input.copy())
                            .notConsumable(target.copy())
                            .outputs(target.copy())
                            .duration(10).EUt(VH[ULV])
                            .buildAndRegister();
                }
            }
        }
    }

    /**
     * @author MeowmelMuku
     * @reason 修复与ceu290的兼容性
     */
    @Overwrite(remap = false)
    public static void init() {
        // Bookshelf
        GTRecipeHandler.removeRecipesByInputs(RecipeMaps.ASSEMBLER_RECIPES, new ItemStack(Blocks.PLANKS, 6, 0),
                new ItemStack(Items.BOOK, 3));
        String[] bookshelf = new String[] { "oak", "spruce", "birch", "jungle", "acacia", "darkoak" };
        for (int i = 0; i < bookshelf.length; i++) {
            ChiselUtil.addGroup("bookshelf" + bookshelf[i].toUpperCase());
            RecipeMaps.ASSEMBLER_RECIPES.recipeBuilder()
                    .inputs(new ItemStack(Blocks.PLANKS, 6, i))
                    .inputs(new ItemStack(Items.BOOK, 3))
                    .outputs(ModUtility.getModItem(Mods.Names.CHISEL, "bookshelf_" + bookshelf[i]))
                    .duration(100).EUt(VH[ULV])
                    .buildAndRegister();
        }

        // Material Blocks
        if (ConfigHolder.recipes.disableManualCompression) {
            Arrays.asList("charcoal_uncraft", "diamond", "emerald", "redstone", "coal", "uncraft_blocksilver",
                    "uncraft_blocklead", "uncraft_blocktin", "uncraft_blocksteel", "uncraft_blockplatinum",
                    "uncraft_blockiron", "uncraft_blockaluminium", "uncraft_blockcobalt", "uncraft_blocknickel",
                    "uncraft_blockelectrum", "uncraft_blockuranium", "uncraft_blockcopper", "uncraft_blockbronze",
                    "uncraft_blockinvar", "uncraft_blockgold").forEach(
                    block -> ModHandler
                            .removeRecipeByName(Mods.Chisel.getResource(block)));
        }

        // Glass Panes
        if (ConfigHolder.recipes.hardGlassRecipes) {
            Arrays.asList("glass/terrain-glassbubble", "glass/terrain-glassnoborder", "glass/terrain-glassshale",
                    "glass/terrain-glass-thingrid", "glass/chinese", "glass/japanese", "glass/terrain-glassdungeon",
                    "glass/terrain-glasslight", "glass/terrain-glass-ornatesteel", "glass/terrain-glass-screen",
                    "glass/terrain-glass-steelframe", "glass/terrain-glassstone", "glass/terrain-glassstreak",
                    "glass/terrain-glass-thickgrid", "glass/a1-glasswindow-ironfencemodern", "glass/chrono",
                    "glass/chinese2", "glass/japanese2").forEach(
                    block -> ModHandler
                            .removeRecipeByName(Mods.Chisel.getResource(block)));
        }

        // Auto Chisel
        ModHandler.removeRecipeByName(Mods.Chisel.getResource("autochisel"));
        ModHandler.addShapelessRecipe("normal_auto_chisel",
                ModUtility.getModItem(Mods.Names.CHISEL, "auto_chisel", 1),
                AUTO_CHISEL[2].getStackForm());
        ModHandler.addShapelessRecipe("ceu_auto_chisel", AUTO_CHISEL[2].getStackForm(),
                ModUtility.getModItem(Mods.Names.CHISEL, "auto_chisel", 1));
        MetaTileEntityLoader.registerMachineRecipe(true, AUTO_CHISEL,
                "BSB", "THT", "MCM",
                'B', new UnificationEntry(toolHeadBuzzSaw, Materials.Invar),
                'S', SENSOR,
                'T', "craftChisel",
                'H', HULL,
                'M', MOTOR,
                'C', CIRCUIT);

        // Lamp
        if (ChiselConfigHolder.hardLedRecipes) {
            if (ProjectRedIllumination.isModLoaded()) {
                IntStream.range(0, 32)
                        .mapToObj(i -> ModUtility.getModItem(PROJECT_RED_ILLUMINATION, "lamp", 1, i))
                        .forEach(ModHandler::removeRecipeByOutput);
            }

            int i = 0;
            while (i < Materials.CHEMICAL_DYES.length) {
                EnumDyeColor color = EnumDyeColor.byMetadata(i);
                EnumDyeColor dyeColor = EnumDyeColor.values()[i];
                String colorName = dyeColor.toString().equals("silver") ?
                        "LightGray" :
                        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, dyeColor.getName());
                BlockLamp lamp = MetaBlocks.LAMPS.get(color);

                ChiselUtil.addGroup("lamp" + colorName);
                {
                    int lampMeta = 0;
                    while (lampMeta < lamp.getItemMetadataStates()) {
                        if (ProjectRedIllumination.isModLoaded()) {
                            ChiselUtil.addVariation("lamp" + colorName,
                                    ModUtility.getModItem(PROJECT_RED_ILLUMINATION, "lamp", 1, i));
                            ChiselUtil.addVariation("lamp" + colorName,
                                    ModUtility.getModItem(PROJECT_RED_ILLUMINATION, "lamp", 1, i + 16));
                        }
                        ChiselUtil.addVariation("lamp" + colorName, new ItemStack(lamp, 1, lampMeta));
                        lampMeta++;
                    }
                }

                lamp = MetaBlocks.BORDERLESS_LAMPS.get(color);
                ChiselUtil.addGroup("lampBorderless" + colorName);
                int lampMeta = 0;
                while (lampMeta < lamp.getItemMetadataStates()) {
                    ChiselUtil.addVariation("lampBorderless" + colorName, new ItemStack(lamp, 1, lampMeta));
                    lampMeta++;
                }
                i++;
            }
        }
    }
}
