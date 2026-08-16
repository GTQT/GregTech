package gregtech.integration.tconstruct;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.unification.stack.RecyclingData;

import static gregtech.api.GTValues.M;
import static gregtech.integration.tconstruct.TiCModule.ticMetaItem;

public class TicMetaItem {

    public static final MetaItem<?>.MetaValueItem[] SHAPE_EXTRUDERS = new MetaItem.MetaValueItem[24];
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_SWORDBLADE;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_BEHEADER;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_ARROWHEAD;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_BINDING;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_TOUGHBINDING;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_GUARD;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_WIDEGUARD;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_CROSSGUARD;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_KNIFEBLADE;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_TOOLROD;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_TOUGHTOOLROD;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_LARGEPLATE;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_SHARPENINGKIT;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_BOWLIMB;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_HAMMER;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_KAMA;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_AXE;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_LUMBERAXE;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_PICKAXE;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_SHOVEL;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_EXCAVATOR;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_SIGN;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_SCYTHE;
    public static MetaItem<?>.MetaValueItem SHAPE_EXTRUDER_FRYPAN;

    public static void registerSubItems() {
        SHAPE_EXTRUDERS[0] = SHAPE_EXTRUDER_SWORDBLADE = ticMetaItem.addItem(1, "shape.extruder.swordblade")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[1] = SHAPE_EXTRUDER_BEHEADER = ticMetaItem.addItem(2, "shape.extruder.beheader")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[2] = SHAPE_EXTRUDER_ARROWHEAD = ticMetaItem.addItem(3, "shape.extruder.arrowhead")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[3] = SHAPE_EXTRUDER_BINDING = ticMetaItem.addItem(4, "shape.extruder.binding")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[4] = SHAPE_EXTRUDER_TOUGHBINDING = ticMetaItem.addItem(5, "shape.extruder.toughbinding")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[5] = SHAPE_EXTRUDER_GUARD = ticMetaItem.addItem(6, "shape.extruder.guard")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[6] = SHAPE_EXTRUDER_WIDEGUARD = ticMetaItem.addItem(7, "shape.extruder.wideguard")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[7] = SHAPE_EXTRUDER_CROSSGUARD = ticMetaItem.addItem(8, "shape.extruder.crossguard")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[8] = SHAPE_EXTRUDER_KNIFEBLADE = ticMetaItem.addItem(9, "shape.extruder.knifeblade")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[9] = SHAPE_EXTRUDER_TOOLROD = ticMetaItem.addItem(10, "shape.extruder.toolrod")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[10] = SHAPE_EXTRUDER_TOUGHTOOLROD = ticMetaItem.addItem(11, "shape.extruder.toughtoolrod")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[11] = SHAPE_EXTRUDER_LARGEPLATE = ticMetaItem.addItem(12, "shape.extruder.largeplate")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[12] = SHAPE_EXTRUDER_SHARPENINGKIT = ticMetaItem.addItem(13, "shape.extruder.sharpeningkit")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[13] = SHAPE_EXTRUDER_BOWLIMB = ticMetaItem.addItem(14, "shape.extruder.bowlimb")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[14] = SHAPE_EXTRUDER_HAMMER = ticMetaItem.addItem(15, "shape.extruder.hammerhead")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[15] = SHAPE_EXTRUDER_KAMA = ticMetaItem.addItem(16, "shape.extruder.kama")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[16] = SHAPE_EXTRUDER_AXE = ticMetaItem.addItem(17, "shape.extruder.axehead")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[17] = SHAPE_EXTRUDER_LUMBERAXE = ticMetaItem.addItem(18, "shape.extruder.lumberaxe")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[18] = SHAPE_EXTRUDER_PICKAXE = ticMetaItem.addItem(19, "shape.extruder.pickaxehead")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[19] = SHAPE_EXTRUDER_SHOVEL =ticMetaItem. addItem(20, "shape.extruder.shovelhead")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[20] = SHAPE_EXTRUDER_EXCAVATOR = ticMetaItem.addItem(21, "shape.extruder.excavator")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[21] = SHAPE_EXTRUDER_SIGN = ticMetaItem.addItem(22, "shape.extruder.sign")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[22] = SHAPE_EXTRUDER_SCYTHE = ticMetaItem.addItem(23, "shape.extruder.scythehead")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[23] = SHAPE_EXTRUDER_FRYPAN = ticMetaItem.addItem(24, "shape.extruder.pan")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
    }
}
