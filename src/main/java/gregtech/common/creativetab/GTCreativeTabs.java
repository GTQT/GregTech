package gregtech.common.creativetab;

import gregtech.api.GTValues;
import gregtech.api.creativetab.BaseCreativeTab;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.common.blocks.BlockWarningSign;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.items.MetaItems;
import gregtech.common.items.OrbItems;
import gregtech.common.items.ToolItems;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

public final class GTCreativeTabs {

    public static final BaseCreativeTab TAB_GREGTECH = new BaseCreativeTab(GTValues.MODID + ".main",
            () -> MetaItems.LOGO.getStackForm(), false);
    public static final BaseCreativeTab TAB_GREGTECH_MACHINES = new BaseCreativeTab(GTValues.MODID + ".machines",
            () -> MetaTileEntities.ELECTRIC_FURNACE[1].getStackForm(), false);
    public static final BaseCreativeTab TAB_GREGTECH_MULTIBLOCKS = new BaseCreativeTab(GTValues.MODID + ".multiblocks",
            () -> MetaTileEntities.ELECTRIC_BLAST_FURNACE.getStackForm(), false);
    public static final BaseCreativeTab TAB_GREGTECH_MULTIBLOCK_PARTS = new BaseCreativeTab(GTValues.MODID + ".multiblock_parts",
            () -> MetaTileEntities.MAINTENANCE_HATCH.getStackForm(), false);
    public static final BaseCreativeTab TAB_GREGTECH_CABLES = new BaseCreativeTab(GTValues.MODID + ".cables",
            () -> OreDictUnifier.get(OrePrefix.cableGtDouble, Materials.TungstenSteel), false);
    public static final BaseCreativeTab TAB_GREGTECH_PIPES = new BaseCreativeTab(GTValues.MODID + ".pipes",
            () -> OreDictUnifier.get(OrePrefix.pipeNormalFluid, Materials.TungstenSteel), false);
    public static final BaseCreativeTab TAB_GREGTECH_TOOLS = new BaseCreativeTab(GTValues.MODID + ".tools",
            () -> ToolItems.HARD_HAMMER.get(Materials.TungstenSteel), false);
    public static final BaseCreativeTab TAB_GREGTECH_MATERIALS = new BaseCreativeTab(GTValues.MODID + ".materials",
            () -> OreDictUnifier.get(OrePrefix.ingot, Materials.TungstenSteel), false);
    public static final BaseCreativeTab TAB_GREGTECH_ORES = new BaseCreativeTab(GTValues.MODID + ".ores",
            () -> new ItemStack(Blocks.IRON_ORE), false);
    public static final BaseCreativeTab TAB_GREGTECH_DECORATIONS = new BaseCreativeTab(GTValues.MODID + ".decorations",
            () -> MetaBlocks.WARNING_SIGN.getItemVariant(BlockWarningSign.SignType.YELLOW_STRIPES), false);
    public static final BaseCreativeTab TAB_GREGTECH_PROGRAMMABLE = new BaseCreativeTab(GTValues.MODID + ".programmable",
            () -> MetaItems.INTEGRATED_CIRCUIT.getStackForm(), false);
    public static final BaseCreativeTab TAB_GREGTECH_ORB = new BaseCreativeTab(GTValues.MODID + ".orb",
            () -> OrbItems.DISPLAY_OVERWORLD.getStackForm(), false);
    public static final BaseCreativeTab TAB_GREGTECH_ARMOR = new BaseCreativeTab(GTValues.MODID + ".armor",
            () -> MetaItems.QUANTUM_HELMET.getStackForm(), false);
    public static final BaseCreativeTab TAB_GREGTECH_NUCLEAR = new BaseCreativeTab(GTValues.MODID + ".nuclear",
            () -> MetaItems.FUEL_ROD_NAQUADAH_4X.getStackForm(), false);

    private GTCreativeTabs() {}
}
