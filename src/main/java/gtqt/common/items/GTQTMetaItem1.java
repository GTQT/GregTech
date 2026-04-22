package gtqt.common.items;

import gregtech.api.GTValues;
import gregtech.api.items.metaitem.FilteredFluidStats;
import gregtech.api.items.metaitem.StandardMetaItem;
import gregtech.api.items.metaitem.stats.ItemFluidContainer;
import gregtech.api.unification.material.MarkerMaterials;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.unification.stack.RecyclingData;
import gregtech.common.creativetab.GTCreativeTabs;

import gtqt.common.GTQTCommonProxy;
import gtqt.common.items.behaviors.OrderBehavior;
import gtqt.common.items.behaviors.ProgrammableCircuit;
import gtqt.common.items.behaviors.ProgrammingToolkit;
import gtqt.common.items.behaviors.VeinScanBehavior;
import gtqt.common.items.behaviors.WindRotorBehavior;

import static gregtech.api.GTValues.M;
import static gregtech.api.unification.material.Materials.*;

public class GTQTMetaItem1 extends StandardMetaItem {

    public GTQTMetaItem1() {
        this.setRegistryName("gtqt_meta_item_0");
    }

    public void registerSubItems() {
        GTQTMetaItems.ORDER = addItem(0, "order").addComponents(new OrderBehavior());


        GTQTMetaItems.COVER_PROGRAMMABLE_CIRCUIT = addItem(1, "cover.programmable_circuit").setCreativeTabs(
                GTQTCommonProxy.GTQTCore_PC);
        // 通用可编程电路（通过 NBT 包裹任意物品）
        GTQTMetaItems.PROGRAMMABLE_CIRCUIT = this.addItem(20, "programmable_circuit")
                .addComponents(new ProgrammableCircuit())
                .addOreDict("oreProgrammableCircuit")
                .setCreativeTabs(GTQTCommonProxy.GTQTCore_PC);
        // 可编程工具箱（右键打开 GUI，将物品包裹到可编程电路中）
        GTQTMetaItems.PROGRAMMING_TOOLKIT = this.addItem(53, "programming_toolkit")
                .addComponents(new ProgrammingToolkit())
                .setMaxStackSize(1)
                .setCreativeTabs(GTQTCommonProxy.GTQTCore_PC);

        //  General Circuits
        GTQTMetaItems.GENERAL_CIRCUIT_ULV = this.addItem(70, "general_circuit.ulv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.ULV)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GTQTMetaItems.GENERAL_CIRCUIT_LV = this.addItem(71, "general_circuit.lv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.LV)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GTQTMetaItems.GENERAL_CIRCUIT_MV = this.addItem(72, "general_circuit.mv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.MV)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GTQTMetaItems.GENERAL_CIRCUIT_HV = this.addItem(73, "general_circuit.hv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.HV)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GTQTMetaItems.GENERAL_CIRCUIT_EV = this.addItem(74, "general_circuit.ev")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.EV)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GTQTMetaItems.GENERAL_CIRCUIT_IV = this.addItem(75, "general_circuit.iv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.IV)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GTQTMetaItems.GENERAL_CIRCUIT_LuV = this.addItem(76, "general_circuit.luv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.LuV)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GTQTMetaItems.GENERAL_CIRCUIT_ZPM = this.addItem(77, "general_circuit.zpm")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.ZPM)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GTQTMetaItems.GENERAL_CIRCUIT_UV = this.addItem(78, "general_circuit.uv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.UV)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GTQTMetaItems.GENERAL_CIRCUIT_UHV = this.addItem(79, "general_circuit.uhv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.UHV)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GTQTMetaItems.GENERAL_CIRCUIT_UEV = this.addItem(80, "general_circuit.uev")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.UEV)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GTQTMetaItems.GENERAL_CIRCUIT_UIV = this.addItem(81, "general_circuit.uiv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.UIV)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GTQTMetaItems.GENERAL_CIRCUIT_UXV = this.addItem(82, "general_circuit.uxv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.UXV)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GTQTMetaItems.GENERAL_CIRCUIT_OpV = this.addItem(83, "general_circuit.opv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.OpV)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GTQTMetaItems.GENERAL_CIRCUIT_MAX = this.addItem(84, "general_circuit.max")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.MAX)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        //流体单元90
        GTQTMetaItems.WOODEN_BUCKET = this.addItem(90, "wooden_bucket").addComponents(
                        new FilteredFluidStats(1000, Wood.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(), true,
                                false, false, false, true), new ItemFluidContainer()).setMaxStackSize(1)
                .setRecyclingData(new RecyclingData(new MaterialStack(Wood, M * 8))) // ingot * 8
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.FLUID_CELL_CHROME = this.addItem(91, "large_fluid_cell.chrome").addComponents(
                        new FilteredFluidStats(2_048_000, Chrome.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(),
                                true, true, false, false, true), new ItemFluidContainer()).setMaxStackSize(32)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Chrome, M * 8))) // ingot * 8
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.FLUID_CELL_IRIDIUM = this.addItem(92, "large_fluid_cell.iridium").addComponents(
                        new FilteredFluidStats(8_192_000, Iridium.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(),
                                true, true, true, false, true), new ItemFluidContainer()).setMaxStackSize(32)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Iridium, M * 8))) // ingot * 8
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.FLUID_CELL_NAQUADAH_ALLOY = this.addItem(93, "large_fluid_cell.naquadah_alloy").addComponents(
                        new FilteredFluidStats(32_768_000,
                                NaquadahAlloy.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(), true, true, true,
                                true, true), new ItemFluidContainer()).setMaxStackSize(32)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.NaquadahAlloy, M * 8))) // ingot * 8
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.FLUID_CELL_NEUTRONIUM = this.addItem(94, "large_fluid_cell.neutronium").addComponents(
                        new FilteredFluidStats(131_072_000,
                                Neutronium.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(), true, true, true, true,
                                true), new ItemFluidContainer()).setMaxStackSize(32)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Neutronium, M * 8))) // ingot * 8
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        //风力发电机转子
        GTQTMetaItems.WIND_ROTOR_STEEL = this.addItem(380, "wind_rotor.steel").setMaxStackSize(1)
                .addComponents(new WindRotorBehavior(2400000, 1, Steel));
        GTQTMetaItems.WIND_ROTOR_ALUMINIUM = this.addItem(381, "wind_rotor.aluminium").setMaxStackSize(1)
                .addComponents(new WindRotorBehavior(4800000, 2, Aluminium));
        GTQTMetaItems.WIND_ROTOR_STAINLESSSTEEL = this.addItem(382, "wind_rotor.stainlesssteel").setMaxStackSize(1)
                .addComponents(new WindRotorBehavior(9600000, 3, StainlessSteel));
        GTQTMetaItems.WIND_ROTOR_TITANIUM = this.addItem(383, "wind_rotor.titanium").setMaxStackSize(1)
                .addComponents(new WindRotorBehavior(19200000, 4, Titanium));
        GTQTMetaItems.WIND_ROTOR_TUNGSTENSTEEL = this.addItem(384, "wind_rotor.tungstensteel").setMaxStackSize(1)
                .addComponents(new WindRotorBehavior(38400000, 5, TungstenSteel));
        GTQTMetaItems.WIND_ROTOR_RHODIUMPLATEDPALLADIUM = this.addItem(385, "wind_rotor.rhodiumplatedpalladium")
                .setMaxStackSize(1).addComponents(new WindRotorBehavior(76800000, 6, RhodiumPlatedPalladium));

        // 51-70: Vanadium Steel Molds & Extruders.
        GTQTMetaItems.CASTING_MOLD_EMPTY = addItem(200, "shape.mold.vanadium_steel.empty").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.CASTING_MOLD_SAW = addItem(201, "shape.mold.vanadium_steel.saw").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.CASTING_MOLD_HARD_HAMMER = addItem(202, "shape.mold.vanadium_steel.hard_hammer").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.CASTING_MOLD_SOFT_MALLET = addItem(203, "shape.mold.vanadium_steel.soft_mallet").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.CASTING_MOLD_WRENCH = addItem(204, "shape.mold.vanadium_steel.wrench").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.CASTING_MOLD_FILE = addItem(205, "shape.mold.vanadium_steel.file").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.CASTING_MOLD_CROWBAR = addItem(206, "shape.mold.vanadium_steel.crowbar").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.CASTING_MOLD_SCREWDRIVER = addItem(207, "shape.mold.vanadium_steel.screwdriver").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.CASTING_MOLD_MORTAR = addItem(208, "shape.mold.vanadium_steel.mortar").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.CASTING_MOLD_WIRE_CUTTER = addItem(209, "shape.mold.vanadium_steel.wire_cutter").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.CASTING_MOLD_KNIFE = addItem(210, "shape.mold.vanadium_steel.knife").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.CASTING_MOLD_BUTCHERY_KNIFE = addItem(211,
                "shape.mold.vanadium_steel.butchery_knife").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.CASTING_MOLD_ROLLING_PIN = addItem(212, "shape.mold.vanadium_steel.rolling_pin").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        // 601-700: Tools.
        GTQTMetaItems.DISPOSABLE_SAW = addItem(220, "tool.disposable.saw").addOreDict("toolSaw")
                .addOreDict("craftingToolSaw").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.DISPOSABLE_HARD_HAMMER = addItem(221, "tool.disposable.hard_hammer").addOreDict("toolHammer")
                .addOreDict("craftingToolHardHammer").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.DISPOSABLE_SOFT_MALLET = addItem(222, "tool.disposable.soft_mallet").addOreDict("toolMallet")
                .addOreDict("craftingToolSoftHammer").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.DISPOSABLE_WRENCH = addItem(223, "tool.disposable.wrench").addOreDict("toolWrench")
                .addOreDict("craftingToolWrench").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.DISPOSABLE_FILE = addItem(224, "tool.disposable.file").addOreDict("toolFile")
                .addOreDict("craftingToolFile").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.DISPOSABLE_CROWBAR = addItem(225, "tool.disposable.crowbar").addOreDict("toolCrowbar")
                .addOreDict("craftingToolCrowbar").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.DISPOSABLE_SCREWDRIVER = addItem(226, "tool.disposable.screwdriver").addOreDict("toolScrewdriver")
                .addOreDict("craftingToolScrewdriver").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.DISPOSABLE_MORTAR = addItem(227, "tool.disposable.mortar").addOreDict("toolMortar")
                .addOreDict("craftingToolMortar").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.DISPOSABLE_WIRE_CUTTER = addItem(228, "tool.disposable.wire_cutter").addOreDict("toolWireCutter")
                .addOreDict("craftingToolWireCutter").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.DISPOSABLE_KNIFE = addItem(229, "tool.disposable.knife").addOreDict("toolKnife")
                .addOreDict("craftingToolKnife").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.DISPOSABLE_BUTCHERY_KNIFE = addItem(230, "tool.disposable.butchery_knife").addOreDict(
                        "toolButcheryKnife").addOreDict("craftingToolButcheryKnife")
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.DISPOSABLE_ROLLING_PIN = addItem(231, "tool.disposable.rolling_pin").addOreDict("toolRollingPin")
                .addOreDict("craftingToolRollingPin").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        GTQTMetaItems.VEIN_SCANNER = addItem(232,"tool.scanner").addComponents(new VeinScanBehavior());
    }
}
