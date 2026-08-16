package gregtech.integration.tconstruct;

import gregtech.api.GregTechAPI;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.util.GTUtility;
import gregtech.common.items.MetaItems;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.tools.ToolPart;
import slimeknights.tconstruct.tools.TinkerTools;

import static gregtech.api.GTValues.LV;
import static gregtech.api.GTValues.VA;

/**
 * Registers GT machine recipes for TiC tool parts:
 * forming-press recipes stamp the GT shape-extruder molds from TiC casts,
 * extruder recipes stamp material into tool parts using those molds.
 */
public final class MachineRecipes {

    /** One tool part: the TiC cast type, the GT mold item, the TiC part and its ingot cost. */
    private static final class PartDefinition {
        final String castType;
        final MetaItem<?>.MetaValueItem mold;
        final ToolPart part;
        final int ingots;
        final int duration;

        PartDefinition(String castType, MetaItem<?>.MetaValueItem mold, ToolPart part, int ingots, int duration) {
            this.castType = castType;
            this.mold = mold;
            this.part = part;
            this.ingots = ingots;
            this.duration = duration;
        }
    }

    private static final PartDefinition[] PARTS = {
            new PartDefinition("pick_head", TicMetaItem.SHAPE_EXTRUDER_PICKAXE, TinkerTools.pickHead, 2, 900),
            new PartDefinition("arrow_head", TicMetaItem.SHAPE_EXTRUDER_ARROWHEAD, TinkerTools.arrowHead, 2, 400),
            new PartDefinition("axe_head", TicMetaItem.SHAPE_EXTRUDER_AXE, TinkerTools.axeHead, 2, 900),
            new PartDefinition("large_sword_blade", TicMetaItem.SHAPE_EXTRUDER_BEHEADER, TinkerTools.largeSwordBlade, 8, 1200),
            new PartDefinition("binding", TicMetaItem.SHAPE_EXTRUDER_BINDING, TinkerTools.binding, 1, 600),
            new PartDefinition("bow_limb", TicMetaItem.SHAPE_EXTRUDER_BOWLIMB, TinkerTools.bowLimb, 3, 600),
            new PartDefinition("cross_guard", TicMetaItem.SHAPE_EXTRUDER_CROSSGUARD, TinkerTools.crossGuard, 1, 600),
            new PartDefinition("excavator_head", TicMetaItem.SHAPE_EXTRUDER_EXCAVATOR, TinkerTools.excavatorHead, 8, 1200),
            new PartDefinition("pan_head", TicMetaItem.SHAPE_EXTRUDER_FRYPAN, TinkerTools.panHead, 3, 1200),
            new PartDefinition("hand_guard", TicMetaItem.SHAPE_EXTRUDER_GUARD, TinkerTools.handGuard, 1, 600),
            new PartDefinition("hammer_head", TicMetaItem.SHAPE_EXTRUDER_HAMMER, TinkerTools.hammerHead, 8, 1200),
            new PartDefinition("kama_head", TicMetaItem.SHAPE_EXTRUDER_KAMA, TinkerTools.kamaHead, 2, 900),
            new PartDefinition("knife_blade", TicMetaItem.SHAPE_EXTRUDER_KNIFEBLADE, TinkerTools.knifeBlade, 1, 700),
            new PartDefinition("large_plate", TicMetaItem.SHAPE_EXTRUDER_LARGEPLATE, TinkerTools.largePlate, 8, 1200),
            new PartDefinition("broad_axe_head", TicMetaItem.SHAPE_EXTRUDER_LUMBERAXE, TinkerTools.broadAxeHead, 8, 1200),
            new PartDefinition("scythe_head", TicMetaItem.SHAPE_EXTRUDER_SCYTHE, TinkerTools.scytheHead, 8, 1200),
            new PartDefinition("sharpening_kit", TicMetaItem.SHAPE_EXTRUDER_SHARPENINGKIT, TinkerTools.sharpeningKit, 2, 900),
            new PartDefinition("shovel_head", TicMetaItem.SHAPE_EXTRUDER_SHOVEL, TinkerTools.shovelHead, 2, 900),
            new PartDefinition("sign_head", TicMetaItem.SHAPE_EXTRUDER_SIGN, TinkerTools.signHead, 3, 1200),
            new PartDefinition("sword_blade", TicMetaItem.SHAPE_EXTRUDER_SWORDBLADE, TinkerTools.swordBlade, 2, 900),
            new PartDefinition("tool_rod", TicMetaItem.SHAPE_EXTRUDER_TOOLROD, TinkerTools.toolRod, 1, 600),
            new PartDefinition("tough_binding", TicMetaItem.SHAPE_EXTRUDER_TOUGHBINDING, TinkerTools.toughBinding, 3, 900),
            new PartDefinition("tough_tool_rod", TicMetaItem.SHAPE_EXTRUDER_TOUGHTOOLROD, TinkerTools.toughToolRod, 3, 900),
            new PartDefinition("wide_guard", TicMetaItem.SHAPE_EXTRUDER_WIDEGUARD, TinkerTools.wideGuard, 1, 600),
    };

    private MachineRecipes() {}

    /**
     * Called during material registration to add machine recipes for all GT tool materials.
     */
    public static void register() {
        registerMoldRecipes();

        for (Material gtMaterial : GregTechAPI.materialManager.getRegisteredMaterials()) {
            if (!gtMaterial.hasProperty(PropertyKey.TOOL)) continue;
            if (!gtMaterial.hasProperty(PropertyKey.INGOT) && !gtMaterial.hasProperty(PropertyKey.GEM)) continue;

            slimeknights.tconstruct.library.materials.Material ticMaterial = getTicMaterial(gtMaterial);
            if (ticMaterial == null) continue;

            ItemStack representative = getRepresentativeItem(gtMaterial);
            if (representative.isEmpty()) continue;

            int eut = VA[getMachineTier(gtMaterial)];
            for (PartDefinition part : PARTS) {
                registerExtruderRecipe(ticMaterial, part, representative, eut);
            }
        }
    }

    /** Registers forming-press recipes that stamp the GT molds from TiC casts. */
    private static void registerMoldRecipes() {
        for (PartDefinition part : PARTS) {
            ItemStack cast = getCast(part.castType);
            ItemStack mold = part.mold.getStackForm();
            ItemStack blank = MetaItems.SHAPE_EMPTY.getStackForm();

            // blank mold + cast = shape-extruder mold
            RecipeMaps.FORMING_PRESS_RECIPES.recipeBuilder()
                    .inputs(blank)
                    .notConsumable(cast)
                    .outputs(mold)
                    .duration(240).EUt(VA[LV]).buildAndRegister();

            // mold copy
            RecipeMaps.FORMING_PRESS_RECIPES.recipeBuilder()
                    .inputs(mold)
                    .notConsumable(mold)
                    .outputs(mold)
                    .duration(120).EUt(VA[LV]).buildAndRegister();

            // cast copy
            RecipeMaps.FORMING_PRESS_RECIPES.recipeBuilder()
                    .inputs(cast)
                    .notConsumable(cast)
                    .outputs(cast)
                    .duration(120).EUt(VA[LV]).buildAndRegister();
        }
    }

    /**
     * Returns the GT representative item (ingot, or gem) for the material. TiC's own
     * {@code Material.getRepresentativeItem()} is not usable here: it is only populated during TiC's post-init, after
     * this module's recipe registration has already run.
     */
    private static ItemStack getRepresentativeItem(Material material) {
        if (material.hasProperty(PropertyKey.INGOT)) {
            return OreDictUnifier.get(OrePrefix.ingot, material);
        }
        return OreDictUnifier.get(OrePrefix.gem, material);
    }

    /** Registers an extruder recipe that stamps material into a tool part using the mold. */
    private static void registerExtruderRecipe(slimeknights.tconstruct.library.materials.Material ticMaterial,
                                               PartDefinition part, ItemStack representative, int eut) {
        RecipeMaps.EXTRUDER_RECIPES.recipeBuilder()
                .inputs(GTUtility.copy(part.ingots, representative))
                .notConsumable(part.mold.getStackForm())
                .outputs(part.part.getItemstackWithMaterial(ticMaterial))
                .duration(part.duration).EUt(eut).buildAndRegister();
    }

    /**
     * Returns the TiC material registered for this GT material: the TiC-builtin material when one exists under the
     * same name (merged), or the GT-registered material otherwise.
     */
    private static slimeknights.tconstruct.library.materials.Material getTicMaterial(Material gtMaterial) {
        slimeknights.tconstruct.library.materials.Material existing = TinkerRegistry
                .getMaterial(gtMaterial.getName());
        if (!existing.identifier.equals(
                slimeknights.tconstruct.library.materials.Material.UNKNOWN.identifier)) {
            return existing;
        }
        slimeknights.tconstruct.library.materials.Material registered = TinkerRegistry
                .getMaterial(gregtech.api.GTValues.MODID + "." + gtMaterial.getName());
        if (!registered.identifier.equals(
                slimeknights.tconstruct.library.materials.Material.UNKNOWN.identifier)) {
            return registered;
        }
        return null;
    }

    /** Derives a machine voltage tier from the GT blast temperature, matching TiCSmeltery's four tiers. */
    private static int getMachineTier(Material material) {
        int blastTemp = material.getBlastTemperature();
        if (blastTemp >= 5000) return 3;
        if (blastTemp >= 2500) return 2;
        if (blastTemp >= 1000) return 1;
        return 0;
    }

    /** Builds a TiC cast ItemStack carrying the part-type NBT of the given cast type. */
    private static ItemStack getCast(String castType) {
        ItemStack cast = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct", "cast")));
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("PartType", "tconstruct:" + castType);
        cast.setTagCompound(tag);
        return cast;
    }
}
