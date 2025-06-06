package gregtech.common.items;

import gregtech.api.GTValues;
import gregtech.api.items.toolitem.*;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Materials;
import gregtech.client.renderer.handler.ToolbeltRenderer;
import gregtech.common.items.tool.*;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.init.Enchantments;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ToolItems {

    private static final List<IGTTool> TOOLS = new ArrayList<>();
    public static IGTTool SWORD;
    public static IGTTool PICKAXE;
    public static IGTTool SHOVEL;
    public static IGTTool AXE;
    public static IGTTool HOE;
    public static IGTTool SAW;
    public static IGTTool HARD_HAMMER;
    public static IGTTool SOFT_MALLET;
    public static IGTTool MINING_HAMMER;
    public static IGTTool SPADE;
    public static IGTTool WRENCH;
    public static IGTTool CHOOCHER;
    public static IGTTool FILE;
    public static IGTTool CROWBAR;
    public static IGTTool SCREWDRIVER;
    public static IGTTool MORTAR;
    public static IGTTool WIRE_CUTTER;
    public static IGTTool SCYTHE;
    public static IGTTool KNIFE;
    public static IGTTool BUTCHERY_KNIFE;
    public static IGTTool DRILL_LV;
    public static IGTTool DRILL_MV;
    public static IGTTool DRILL_HV;
    public static IGTTool DRILL_EV;
    public static IGTTool DRILL_IV;
    public static IGTTool CHAINSAW_LV;
    public static IGTTool WRENCH_LV;
    public static IGTTool WRENCH_HV;
    public static IGTTool WRENCH_IV;
    public static IGTTool BUZZSAW;
    public static IGTTool SCREWDRIVER_LV;
    public static IGTTool PLUNGER;
    public static IGTTool WIRECUTTER_LV;
    public static IGTTool WIRECUTTER_HV;
    public static IGTTool WIRECUTTER_IV;
    public static IGTTool HARD_HAMMER_LV;
    public static IGTTool HARD_HAMMER_MV;
    public static IGTTool HARD_HAMMER_HV;
    public static IGTTool HARD_HAMMER_EV;
    public static IGTTool HARD_HAMMER_IV;
    public static ItemGTToolbelt TOOLBELT;

    public static IGTTool ONCE_WRENCH;
    public static IGTTool ONCE_HARD_HAMMER;
    public static IGTTool ONCE_WIRE_CUTTER;
    public static IGTTool ONCE_SAW;
    public static IGTTool ONCE_FILE;
    public static IGTTool ONCE_SCREWDRIVER;
    public static IGTTool ONCE_MORTAR;
    private ToolItems() {/**/}

    public static List<IGTTool> getAllTools() {
        return TOOLS;
    }

    public static void init() {
        TOOLBELT = (ItemGTToolbelt) register(new ItemGTToolbelt(GTValues.MODID, "toolbelt",
                null, OpenGUIBehavior.INSTANCE));
        SWORD = register(ItemGTSword.Builder.of(GTValues.MODID, "sword")
                .toolStats(b -> b.attacking()
                        .attackDamage(3.0F).attackSpeed(-2.4F))
                .toolClasses(ToolClasses.SWORD)
                .oreDict(ToolOreDict.toolSword));
        PICKAXE = register(ItemGTTool.Builder.of(GTValues.MODID, "pickaxe")
                .toolStats(b -> b.blockBreaking().attackDamage(1.0F).attackSpeed(-2.8F)
                        .behaviors(TorchPlaceBehavior.INSTANCE))
                .toolClasses(ToolClasses.PICKAXE)
                .oreDict(ToolOreDict.toolPickaxe));
        SHOVEL = register(ItemGTTool.Builder.of(GTValues.MODID, "shovel")
                .toolStats(b -> b.blockBreaking().attackDamage(1.5F).attackSpeed(-3.0F)
                        .behaviors(GrassPathBehavior.INSTANCE))
                .toolClasses(ToolClasses.SHOVEL)
                .oreDict(ToolOreDict.toolShovel));
        AXE = register(ItemGTAxe.Builder.of(GTValues.MODID, "axe")
                .toolStats(b -> b.blockBreaking()
                        .attackDamage(5.0F).attackSpeed(-3.2F).baseEfficiency(2.0F)
                        .behaviors(DisableShieldBehavior.INSTANCE, TreeFellingBehavior.INSTANCE))
                .toolClasses(ToolClasses.AXE)
                .oreDict(ToolOreDict.toolAxe));
        HOE = register(ItemGTHoe.Builder.of(GTValues.MODID, "hoe")
                .toolStats(b -> b.cannotAttack().attackSpeed(-1.0F))
                .toolClasses(ToolClasses.HOE)
                .oreDict(ToolOreDict.toolHoe));
        SAW = register(ItemGTTool.Builder.of(GTValues.MODID, "saw")
                .toolStats(b -> b.crafting().damagePerCraftingAction(2)
                        .attackDamage(-1.0F).attackSpeed(-2.6F)
                        .behaviors(HarvestIceBehavior.INSTANCE))
                .oreDict(ToolOreDict.toolSaw)
                .secondaryOreDicts("craftingToolSaw")
                .symbol('s')
                .toolClasses(ToolClasses.SAW));
        HARD_HAMMER = register(ItemGTTool.Builder.of(GTValues.MODID, "hammer")
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(2)
                        .attackDamage(1.0F).attackSpeed(-2.8F)
                        .behaviors(new EntityDamageBehavior(2.0F, EntityGolem.class)))
                .oreDict(ToolOreDict.toolHammer)
                .secondaryOreDicts("craftingToolHardHammer")
                .sound(SoundEvents.BLOCK_ANVIL_LAND)
                .symbol('h')
                .toolClasses(ToolClasses.PICKAXE, ToolClasses.HARD_HAMMER));
        SOFT_MALLET = register(ItemGTTool.Builder.of(GTValues.MODID, "mallet")
                .toolStats(b -> b.crafting().cannotAttack().attackSpeed(-2.4F))
                .oreDict(ToolOreDict.toolMallet)
                .secondaryOreDicts("craftingToolSoftHammer")
                .sound(GTSoundEvents.SOFT_MALLET_TOOL)
                .symbol('r')
                .toolClasses(ToolClasses.SOFT_MALLET)
                .markerItem(() -> SOFT_MALLET.get(Materials.Wood)));
        MINING_HAMMER = register(ItemGTTool.Builder.of(GTValues.MODID, "mining_hammer")
                .toolStats(b -> b.blockBreaking().aoe(1, 1, 0)
                        .efficiencyMultiplier(0.4F).attackDamage(1.5F).attackSpeed(-3.2F)
                        .durabilityMultiplier(3.0F)
                        .behaviors(TorchPlaceBehavior.INSTANCE))
                .toolClasses(ToolClasses.PICKAXE)
                .oreDict(ToolOreDict.toolMiningHammer));
        SPADE = register(ItemGTTool.Builder.of(GTValues.MODID, "spade")
                .toolStats(b -> b.blockBreaking().aoe(1, 1, 0)
                        .efficiencyMultiplier(0.4F).attackDamage(1.5F).attackSpeed(-3.2F)
                        .durabilityMultiplier(3.0F)
                        .behaviors(GrassPathBehavior.INSTANCE))
                .toolClasses(ToolClasses.SHOVEL)
                .oreDict(ToolOreDict.toolSpade));
        WRENCH = register(ItemGTTool.Builder.of(GTValues.MODID, "wrench")
                .toolStats(b -> b.blockBreaking().crafting().sneakBypassUse()
                        .attackDamage(1.0F).attackSpeed(-2.8F)
                        .behaviors(BlockRotatingBehavior.INSTANCE, new EntityDamageBehavior(3.0F, EntityGolem.class)))
                .sound(GTSoundEvents.WRENCH_TOOL, true)
                .oreDict(ToolOreDict.toolWrench)
                .secondaryOreDicts("craftingToolWrench")
                .symbol('w')
                .toolClasses(ToolClasses.WRENCH));

        CHOOCHER = register(ItemGTTool.Builder.of(GTValues.MODID, "choocher")
                .toolStats(b -> b.blockBreaking().crafting().sneakBypassUse()
                        .attackDamage(1.0F).attackSpeed(-2.8F)
                        .behaviors(BlockRotatingBehavior.INSTANCE, new EntityDamageBehavior(3.0F, EntityGolem.class)))
                .sound(GTSoundEvents.WRENCH_TOOL, true)
                .secondaryOreDicts(ToolOreDict.toolPickaxe, ToolOreDict.toolShovel)
                .toolClasses(ToolClasses.WRENCH,ToolClasses.HARD_HAMMER));

        FILE = register(ItemGTTool.Builder.of(GTValues.MODID, "file")
                .toolStats(b -> b.crafting().damagePerCraftingAction(4)
                        .cannotAttack().attackSpeed(-2.4F))
                .sound(GTSoundEvents.FILE_TOOL)
                .oreDict(ToolOreDict.toolFile)
                .secondaryOreDicts("craftingToolFile")
                .symbol('f')
                .toolClasses(ToolClasses.FILE));
        CROWBAR = register(ItemGTTool.Builder.of(GTValues.MODID, "crowbar")
                .toolStats(b -> b.blockBreaking().crafting()
                        .attackDamage(2.0F).attackSpeed(-2.4F)
                        .sneakBypassUse().behaviors(RotateRailBehavior.INSTANCE))
                .sound(SoundEvents.ENTITY_ITEM_BREAK)
                .oreDict(ToolOreDict.toolCrowbar)
                .secondaryOreDicts("craftingToolCrowbar")
                .symbol('c')
                .toolClasses(ToolClasses.CROWBAR));
        SCREWDRIVER = register(ItemGTTool.Builder.of(GTValues.MODID, "screwdriver")
                .toolStats(b -> b.crafting().damagePerCraftingAction(4).sneakBypassUse()
                        .attackDamage(-1.0F).attackSpeed(3.0F)
                        .behaviors(new EntityDamageBehavior(3.0F, EntitySpider.class)))
                .sound(GTSoundEvents.SCREWDRIVER_TOOL)
                .oreDict(ToolOreDict.toolScrewdriver)
                .secondaryOreDicts("craftingToolScrewdriver")
                .symbol('d')
                .toolClasses(ToolClasses.SCREWDRIVER));
        MORTAR = register(ItemGTTool.Builder.of(GTValues.MODID, "mortar")
                .toolStats(b -> b.crafting().damagePerCraftingAction(2)
                        .cannotAttack().attackSpeed(-2.4F))
                .sound(GTSoundEvents.MORTAR_TOOL)
                .oreDict(ToolOreDict.toolMortar)
                .secondaryOreDicts("craftingToolMortar")
                .symbol('m')
                .toolClasses(ToolClasses.MORTAR));
        WIRE_CUTTER = register(ItemGTTool.Builder.of(GTValues.MODID, "wire_cutter")
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(4)
                        .attackDamage(-1.0F).attackSpeed(-2.4F))
                .sound(GTSoundEvents.WIRECUTTER_TOOL, true)
                .oreDict(ToolOreDict.toolWireCutter)
                .secondaryOreDicts("craftingToolWireCutter")
                .symbol('x')
                .toolClasses(ToolClasses.WIRE_CUTTER));
        SCYTHE = register(ItemGTSword.Builder.of(GTValues.MODID, "scythe")
                .toolStats(b -> b.blockBreaking().attacking()
                        .attackDamage(5.0F).attackSpeed(-3.0F).durabilityMultiplier(3.0F)
                        .aoe(2, 2, 2)
                        .behaviors(HoeGroundBehavior.INSTANCE, HarvestCropsBehavior.INSTANCE)
                        .canApplyEnchantment(EnumEnchantmentType.DIGGER))
                .oreDict(ToolOreDict.toolScythe)
                .toolClasses(ToolClasses.SCYTHE, ToolClasses.HOE));
        KNIFE = register(ItemGTSword.Builder.of(GTValues.MODID, "knife")
                .toolStats(b -> b.crafting().attacking().attackSpeed(3.0F))
                .oreDict(ToolOreDict.toolKnife)
                .secondaryOreDicts("craftingToolKnife")
                .symbol('k')
                .toolClasses(ToolClasses.KNIFE, ToolClasses.SWORD));
        BUTCHERY_KNIFE = register(ItemGTSword.Builder.of(GTValues.MODID, "butchery_knife")
                .toolStats(b -> b.attacking()
                        .attackDamage(1.5F).attackSpeed(-1.3F).defaultEnchantment(Enchantments.LOOTING, 3))
                .oreDict(ToolOreDict.toolButcheryKnife)
                .secondaryOreDicts("craftingToolButcheryKnife")
                .toolClasses(ToolClasses.BUTCHERY_KNIFE));
        DRILL_LV = register(ItemGTTool.Builder.of(GTValues.MODID, "drill_lv")
                .toolStats(b -> b.blockBreaking().aoe(1, 1, 0)
                        .attackDamage(1.0F).attackSpeed(-3.2F).durabilityMultiplier(3.0F)
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_LV)
                        .behaviors(TorchPlaceBehavior.INSTANCE))
                .oreDict(ToolOreDict.toolDrill)
                .secondaryOreDicts(ToolOreDict.toolPickaxe, ToolOreDict.toolShovel)
                .sound(GTSoundEvents.DRILL_TOOL, true)
                .toolClasses(ToolClasses.DRILL)
                .electric(GTValues.LV));
        DRILL_MV = register(ItemGTTool.Builder.of(GTValues.MODID, "drill_mv")
                .toolStats(b -> b.blockBreaking().aoe(1, 1, 2)
                        .attackDamage(1.0F).attackSpeed(-3.2F).durabilityMultiplier(4.0F)
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_MV)
                        .behaviors(TorchPlaceBehavior.INSTANCE))
                .oreDict(ToolOreDict.toolDrill)
                .secondaryOreDicts(ToolOreDict.toolPickaxe, ToolOreDict.toolShovel)
                .sound(GTSoundEvents.DRILL_TOOL, true)
                .toolClasses(ToolClasses.DRILL)
                .electric(GTValues.MV));
        DRILL_HV = register(ItemGTTool.Builder.of(GTValues.MODID, "drill_hv")
                .toolStats(b -> b.blockBreaking().aoe(2, 2, 4)
                        .attackDamage(1.0F).attackSpeed(-3.2F).durabilityMultiplier(5.0F)
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_HV)
                        .behaviors(TorchPlaceBehavior.INSTANCE))
                .oreDict(ToolOreDict.toolDrill)
                .secondaryOreDicts(ToolOreDict.toolPickaxe, ToolOreDict.toolShovel)
                .sound(GTSoundEvents.DRILL_TOOL, true)
                .toolClasses(ToolClasses.DRILL)
                .electric(GTValues.HV));
        DRILL_EV = register(ItemGTTool.Builder.of(GTValues.MODID, "drill_ev")
                .toolStats(b -> b.blockBreaking().aoe(3, 3, 6)
                        .attackDamage(1.0F).attackSpeed(-3.2F).durabilityMultiplier(6.0F)
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_EV)
                        .behaviors(TorchPlaceBehavior.INSTANCE))
                .oreDict(ToolOreDict.toolDrill)
                .secondaryOreDicts(ToolOreDict.toolPickaxe, ToolOreDict.toolShovel)
                .sound(GTSoundEvents.DRILL_TOOL, true)
                .toolClasses(ToolClasses.DRILL)
                .electric(GTValues.EV));
        DRILL_IV = register(ItemGTTool.Builder.of(GTValues.MODID, "drill_iv")
                .toolStats(b -> b.blockBreaking().aoe(4, 4, 8)
                        .attackDamage(1.0F).attackSpeed(-3.2F).durabilityMultiplier(7.0F)
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_IV)
                        .behaviors(TorchPlaceBehavior.INSTANCE))
                .oreDict(ToolOreDict.toolDrill)
                .secondaryOreDicts(ToolOreDict.toolPickaxe, ToolOreDict.toolShovel)
                .sound(GTSoundEvents.DRILL_TOOL, true)
                .toolClasses(ToolClasses.DRILL)
                .electric(GTValues.IV));
        CHAINSAW_LV = register(ItemGTAxe.Builder.of(GTValues.MODID, "chainsaw_lv")
                .toolStats(b -> b.blockBreaking()
                        .efficiencyMultiplier(2.0F)
                        .attackDamage(5.0F).attackSpeed(-3.2F)
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_LV)
                        .behaviors(HarvestIceBehavior.INSTANCE, DisableShieldBehavior.INSTANCE,
                                TreeFellingBehavior.INSTANCE))
                .oreDict(ToolOreDict.toolAxe)
                .secondaryOreDicts(ToolOreDict.toolChainsaw)
                .sound(GTSoundEvents.CHAINSAW_TOOL, true)
                .toolClasses(ToolClasses.AXE)
                .electric(GTValues.LV));
        WRENCH_LV = register(ItemGTTool.Builder.of(GTValues.MODID, "wrench_lv")
                .toolStats(b -> b.blockBreaking().crafting().sneakBypassUse()
                        .efficiencyMultiplier(2.0F)
                        .attackDamage(1.0F).attackSpeed(-2.8F)
                        .behaviors(BlockRotatingBehavior.INSTANCE, new EntityDamageBehavior(3.0F, EntityGolem.class))
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_LV))
                .sound(GTSoundEvents.WRENCH_TOOL, true)
                .oreDict(ToolOreDict.toolWrench)
                .secondaryOreDicts("craftingToolWrench")
                .toolClasses(ToolClasses.WRENCH)
                .electric(GTValues.LV));
        WRENCH_HV = register(ItemGTTool.Builder.of(GTValues.MODID, "wrench_hv")
                .toolStats(b -> b.blockBreaking().crafting().sneakBypassUse()
                        .efficiencyMultiplier(3.0F)
                        .attackDamage(1.0F).attackSpeed(-2.8F)
                        .behaviors(BlockRotatingBehavior.INSTANCE, new EntityDamageBehavior(3.0F, EntityGolem.class))
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_HV))
                .sound(GTSoundEvents.WRENCH_TOOL, true)
                .oreDict(ToolOreDict.toolWrench)
                .secondaryOreDicts("craftingToolWrench")
                .toolClasses(ToolClasses.WRENCH)
                .electric(GTValues.HV));
        WRENCH_IV = register(ItemGTTool.Builder.of(GTValues.MODID, "wrench_iv")
                .toolStats(b -> b.blockBreaking().crafting().sneakBypassUse()
                        .efficiencyMultiplier(4.0F)
                        .attackDamage(1.0F).attackSpeed(-2.8F)
                        .behaviors(BlockRotatingBehavior.INSTANCE, new EntityDamageBehavior(3.0F, EntityGolem.class))
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_IV))
                .sound(GTSoundEvents.WRENCH_TOOL, true)
                .oreDict(ToolOreDict.toolWrench)
                .secondaryOreDicts("craftingToolWrench")
                .toolClasses(ToolClasses.WRENCH)
                .electric(GTValues.IV));
        BUZZSAW = register(ItemGTTool.Builder.of(GTValues.MODID, "buzzsaw")
                .toolStats(b -> b.crafting().attackDamage(1.5F).attackSpeed(-3.2F)
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_LV))
                .sound(GTSoundEvents.CHAINSAW_TOOL, true)
                .oreDict(ToolOreDict.toolSaw)
                .secondaryOreDicts("craftingToolSaw")
                .secondaryOreDicts(ToolOreDict.toolBuzzsaw)
                .toolClasses(ToolClasses.SAW)
                .electric(GTValues.LV));
        SCREWDRIVER_LV = register(ItemGTTool.Builder.of(GTValues.MODID, "screwdriver_lv")
                .toolStats(b -> b.crafting().sneakBypassUse()
                        .attackDamage(-1.0F).attackSpeed(3.0F)
                        .behaviors(new EntityDamageBehavior(3.0F, EntitySpider.class))
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_LV))
                .sound(GTSoundEvents.SCREWDRIVER_TOOL)
                .oreDict(ToolOreDict.toolScrewdriver)
                .secondaryOreDicts("craftingToolScrewdriver")
                .toolClasses(ToolClasses.SCREWDRIVER)
                .electric(GTValues.LV));
        PLUNGER = register(ItemGTTool.Builder.of(GTValues.MODID, "plunger")
                .toolStats(b -> b.cannotAttack().attackSpeed(-2.4F).sneakBypassUse()
                        .behaviors(PlungerBehavior.INSTANCE))
                .sound(GTSoundEvents.PLUNGER_TOOL)
                .oreDict(ToolOreDict.toolPlunger)
                .toolClasses(ToolClasses.PLUNGER)
                .markerItem(() -> PLUNGER.get(Materials.Rubber)));
        WIRECUTTER_LV = register(ItemGTTool.Builder.of(GTValues.MODID, "wire_cutter_lv")
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(4)
                        .efficiencyMultiplier(2.0F)
                        .attackDamage(-1.0F).attackSpeed(-2.4F)
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_LV))
                .sound(GTSoundEvents.WIRECUTTER_TOOL, true)
                .oreDict(ToolOreDict.toolWireCutter)
                .secondaryOreDicts("craftingToolWireCutter")
                .toolClasses(ToolClasses.WIRE_CUTTER)
                .electric(GTValues.LV));
        WIRECUTTER_HV = register(ItemGTTool.Builder.of(GTValues.MODID, "wire_cutter_hv")
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(4)
                        .efficiencyMultiplier(3.0F)
                        .attackDamage(-1.0F).attackSpeed(-2.4F)
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_LV))
                .sound(GTSoundEvents.WIRECUTTER_TOOL, true)
                .oreDict(ToolOreDict.toolWireCutter)
                .secondaryOreDicts("craftingToolWireCutter")
                .toolClasses(ToolClasses.WIRE_CUTTER)
                .electric(GTValues.HV));
        WIRECUTTER_IV = register(ItemGTTool.Builder.of(GTValues.MODID, "wire_cutter_iv")
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(4)
                        .efficiencyMultiplier(4.0F)
                        .attackDamage(-1.0F).attackSpeed(-2.4F)
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_LV))
                .sound(GTSoundEvents.WIRECUTTER_TOOL, true)
                .oreDict(ToolOreDict.toolWireCutter)
                .secondaryOreDicts("craftingToolWireCutter")
                .toolClasses(ToolClasses.WIRE_CUTTER)
                .electric(GTValues.IV));
        //风钻
        HARD_HAMMER_LV = register(ItemGTTool.Builder.of(GTValues.MODID, "hammer_drill_lv")
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(2)
                        .attackDamage(1.0F).attackSpeed(3.2F).aoe(1, 1, 2)
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_LV)
                        .defaultEnchantment(Enchantments.FORTUNE,1)
                        .behaviors(TorchPlaceBehavior.INSTANCE))
                .sound(SoundEvents.BLOCK_ANVIL_LAND)
                .oreDict(ToolOreDict.toolHammerDrill)
                .electric(GTValues.LV)
                .toolClasses(ToolClasses.PICKAXE, ToolClasses.HARD_HAMMER));

        HARD_HAMMER_MV = register(ItemGTTool.Builder.of(GTValues.MODID, "hammer_drill_mv")
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(2)
                        .attackDamage(5.0F).attackSpeed(3.6F).aoe(2, 2, 4)
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_MV)
                        .defaultEnchantment(Enchantments.FORTUNE,1)
                        .behaviors(TorchPlaceBehavior.INSTANCE))
                .sound(SoundEvents.BLOCK_ANVIL_LAND)
                .oreDict(ToolOreDict.toolHammerDrill)
                .electric(GTValues.MV)
                .toolClasses(ToolClasses.PICKAXE, ToolClasses.HARD_HAMMER));

        HARD_HAMMER_HV = register(ItemGTTool.Builder.of(GTValues.MODID, "hammer_drill_hv")
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(2)
                        .attackDamage(7.0F).attackSpeed(4.2F).aoe(3, 3, 6)
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_HV)
                        .defaultEnchantment(Enchantments.FORTUNE,2)
                        .behaviors(TorchPlaceBehavior.INSTANCE))
                .sound(SoundEvents.BLOCK_ANVIL_LAND)
                .oreDict(ToolOreDict.toolHammerDrill)
                .electric(GTValues.HV)
                .toolClasses(ToolClasses.PICKAXE, ToolClasses.HARD_HAMMER));

        HARD_HAMMER_EV = register(ItemGTTool.Builder.of(GTValues.MODID, "hammer_drill_ev")
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(2)
                        .attackDamage(9.0F).attackSpeed(4.8F).aoe(4, 4, 8)
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_EV)
                        .defaultEnchantment(Enchantments.FORTUNE,2)
                        .behaviors(TorchPlaceBehavior.INSTANCE))
                .sound(SoundEvents.BLOCK_ANVIL_LAND)
                .oreDict(ToolOreDict.toolHammerDrill)
                .electric(GTValues.EV)
                .toolClasses(ToolClasses.PICKAXE, ToolClasses.HARD_HAMMER));

        HARD_HAMMER_IV = register(ItemGTTool.Builder.of(GTValues.MODID, "hammer_drill_iv")
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(2)
                        .attackDamage(11.0F).attackSpeed(5.6F).aoe(9, 9, 0)
                        .brokenStack(ToolHelper.SUPPLY_POWER_UNIT_IV)
                        .defaultEnchantment(Enchantments.FORTUNE,3)
                        .behaviors(TorchPlaceBehavior.INSTANCE))
                .sound(SoundEvents.BLOCK_ANVIL_LAND)
                .oreDict(ToolOreDict.toolHammerDrill)
                .electric(GTValues.IV)
                .toolClasses(ToolClasses.PICKAXE, ToolClasses.HARD_HAMMER, ToolClasses.SHOVEL));

        ONCE_WRENCH = register(ItemGTTool.Builder.of(GTValues.MODID, "once_wrench")
                .oreDict(ToolOreDict.toolWrench)
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(1).baseDurability(0).durabilityMultiplier(0))
                .secondaryOreDicts("craftingToolWrench")
                .toolClasses(ToolClasses.WRENCH));

        ONCE_HARD_HAMMER = register(ItemGTTool.Builder.of(GTValues.MODID, "once_hammer")
                .oreDict(ToolOreDict.toolWrench)
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(1).baseDurability(0).durabilityMultiplier(0))
                .secondaryOreDicts("craftingToolHardHammer")
                .toolClasses(ToolClasses.HARD_HAMMER));

        ONCE_WIRE_CUTTER = register(ItemGTTool.Builder.of(GTValues.MODID, "once_wire_cutter")
                .oreDict(ToolOreDict.toolWrench)
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(1).baseDurability(0).durabilityMultiplier(0))
                .secondaryOreDicts("craftingToolWireCutter")
                .toolClasses(ToolClasses.WIRE_CUTTER));

        ONCE_SAW= register(ItemGTTool.Builder.of(GTValues.MODID, "once_saw")
                .oreDict(ToolOreDict.toolWrench)
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(1).baseDurability(0).durabilityMultiplier(0))
                .secondaryOreDicts("craftingToolSaw")
                .toolClasses(ToolClasses.SAW));

        ONCE_FILE= register(ItemGTTool.Builder.of(GTValues.MODID, "once_file")
                .oreDict(ToolOreDict.toolWrench)
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(1).baseDurability(0).durabilityMultiplier(0))
                .secondaryOreDicts("craftingToolFile")
                .toolClasses(ToolClasses.FILE));

        ONCE_SCREWDRIVER= register(ItemGTTool.Builder.of(GTValues.MODID, "once_screwdriver")
                .oreDict(ToolOreDict.toolWrench)
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(1).baseDurability(0).durabilityMultiplier(0))
                .secondaryOreDicts("craftingToolScrewdriver")
                .toolClasses(ToolClasses.SCREWDRIVER));

        ONCE_MORTAR= register(ItemGTTool.Builder.of(GTValues.MODID, "once_mortar")
                .oreDict(ToolOreDict.toolWrench)
                .toolStats(b -> b.blockBreaking().crafting().damagePerCraftingAction(1).baseDurability(0).durabilityMultiplier(0))
                .secondaryOreDicts("craftingToolMortar")
                .toolClasses(ToolClasses.MORTAR));
    }

    public static IGTTool register(@NotNull ToolBuilder<?> builder) {
        IGTTool tool = builder.build();
        TOOLS.add(tool);
        return tool;
    }

    public static IGTTool register(@NotNull IGTTool tool) {
        TOOLS.add(tool);
        return tool;
    }

    public static void registerModels() {
        TOOLS.forEach(tool -> ModelLoader.setCustomModelResourceLocation(tool.get(), 0, tool.getModelLocation()));
    }

    @SideOnly(Side.CLIENT)
    public static void registerBakedModels(ModelBakeEvent event) {
        ModelResourceLocation loc = TOOLBELT.getModelLocation();
        event.getModelRegistry().putObject(loc, new ToolbeltRenderer(event.getModelRegistry().getObject(loc)));
    }

    public static void registerColors() {
        TOOLS.forEach(
                tool -> Minecraft.getMinecraft().getItemColors().registerItemColorHandler(tool::getColor, tool.get()));
    }

    public static void registerOreDict() {
        TOOLS.forEach(tool -> {
            final ItemStack stack = new ItemStack(tool.get(), 1, GTValues.W);
            if (tool.getOreDictName() != null) {
                OreDictUnifier.registerOre(stack, tool.getOreDictName());
            }
            tool.getSecondaryOreDicts().forEach(oreDict -> {
                OreDictUnifier.registerOre(stack, oreDict);
            });
        });
    }
}
