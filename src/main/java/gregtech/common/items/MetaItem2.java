package gregtech.common.items;

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
import gregtech.common.items.behaviors.TooltipBehavior;

import net.minecraft.client.resources.I18n;

import gregtech.common.items.behaviors.OrderBehavior;
import gregtech.common.items.behaviors.ProgrammableCircuit;
import gregtech.common.items.behaviors.ProgrammingToolkit;
import gregtech.common.items.behaviors.VeinScanBehavior;

import static gregtech.api.GTValues.M;
import static gregtech.api.unification.material.Materials.*;

public class MetaItem2 extends StandardMetaItem {

    public MetaItem2() {
        this.setRegistryName("gtqt_meta_item_0");
    }

    public void registerSubItems() {
        MetaItems.ORDER = addItem(0, "order").addComponents(new OrderBehavior());

        MetaItems.COVER_PROGRAMMABLE_CIRCUIT = addItem(1, "cover.programmable_circuit").setCreativeTabs(
                GTCreativeTabs.TAB_GREGTECH_PROGRAMMABLE);

        // 通用可编程电路（通过 NBT 包裹任意物品）
        MetaItems.PROGRAMMABLE_CIRCUIT = this.addItem(2, "programmable_circuit")
                .addComponents(new ProgrammableCircuit()).addOreDict("oreProgrammableCircuit")
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_PROGRAMMABLE);

        // 可编程工具箱（右键打开 GUI，将物品包裹到可编程电路中）
        MetaItems.PROGRAMMING_TOOLKIT = this.addItem(3, "programming_toolkit")
                .addComponents(new ProgrammingToolkit()).setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_PROGRAMMABLE);

        // Wireless Energy Covers - Input
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_ULV = this.addItem(10, "wireless_energy_cover_input.ulv")
                .setTier(0).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_LV = this.addItem(11, "wireless_energy_cover_input.lv")
                .setTier(1).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_MV = this.addItem(12, "wireless_energy_cover_input.mv").setTier(2)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_HV = this.addItem(13, "wireless_energy_cover_input.hv").setTier(3)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_EV = this.addItem(14, "wireless_energy_cover_input.ev").setTier(4)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_IV = this.addItem(15, "wireless_energy_cover_input.iv").setTier(5)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_LUV = this.addItem(16, "wireless_energy_cover_input.luv").setTier(6)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_ZPM = this.addItem(17, "wireless_energy_cover_input.zpm").setTier(7)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_UV = this.addItem(18, "wireless_energy_cover_input.uv").setTier(8)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_UHV = this.addItem(19, "wireless_energy_cover_input.uhv").setTier(9)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_UEV = this.addItem(20, "wireless_energy_cover_input.uev").setTier(10)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_UIV = this.addItem(21, "wireless_energy_cover_input.uiv").setTier(11)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_UXV = this.addItem(22, "wireless_energy_cover_input.uxv").setTier(12)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_OPV = this.addItem(23, "wireless_energy_cover_input.opv").setTier(13)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_MAX = this.addItem(24, "wireless_energy_cover_input.max").setTier(14)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        // Wireless Energy Covers - Output
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_ULV = this.addItem(25, "wireless_energy_cover_output.ulv")
                .setTier(0).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_LV = this.addItem(26, "wireless_energy_cover_output.lv")
                .setTier(1).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_MV = this.addItem(27, "wireless_energy_cover_output.mv")
                .setTier(2).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_HV = this.addItem(28, "wireless_energy_cover_output.hv")
                .setTier(3).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_EV = this.addItem(29, "wireless_energy_cover_output.ev")
                .setTier(4).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_IV = this.addItem(30, "wireless_energy_cover_output.iv")
                .setTier(5).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_LUV = this.addItem(31, "wireless_energy_cover_output.luv")
                .setTier(6).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_ZPM = this.addItem(32, "wireless_energy_cover_output.zpm")
                .setTier(7).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UV = this.addItem(33, "wireless_energy_cover_output.uv")
                .setTier(8).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UHV = this.addItem(34, "wireless_energy_cover_output.uhv")
                .setTier(9).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UEV = this.addItem(35, "wireless_energy_cover_output.uev")
                .setTier(10).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UIV = this.addItem(36, "wireless_energy_cover_output.uiv")
                .setTier(11).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UXV = this.addItem(37, "wireless_energy_cover_output.uxv")
                .setTier(12).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_OPV = this.addItem(38, "wireless_energy_cover_output.opv")
                .setTier(13).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_MAX = this.addItem(39, "wireless_energy_cover_output.max")
                .setTier(14).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        //  General Circuits
        MetaItems.GENERAL_CIRCUIT_ULV = this.addItem(70, "general_circuit.ulv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.ULV).setTier(0)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.GENERAL_CIRCUIT_LV = this.addItem(71, "general_circuit.lv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.LV).setTier(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.GENERAL_CIRCUIT_MV = this.addItem(72, "general_circuit.mv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.MV).setTier(2)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.GENERAL_CIRCUIT_HV = this.addItem(73, "general_circuit.hv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.HV).setTier(3)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.GENERAL_CIRCUIT_EV = this.addItem(74, "general_circuit.ev")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.EV).setTier(4)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.GENERAL_CIRCUIT_IV = this.addItem(75, "general_circuit.iv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.IV).setTier(5)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.GENERAL_CIRCUIT_LuV = this.addItem(76, "general_circuit.luv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.LuV).setTier(6)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.GENERAL_CIRCUIT_ZPM = this.addItem(77, "general_circuit.zpm")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.ZPM).setTier(7)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.GENERAL_CIRCUIT_UV = this.addItem(78, "general_circuit.uv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.UV).setTier(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.GENERAL_CIRCUIT_UHV = this.addItem(79, "general_circuit.uhv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.UHV).setTier(9)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.GENERAL_CIRCUIT_UEV = this.addItem(80, "general_circuit.uev")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.UEV).setTier(10)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.GENERAL_CIRCUIT_UIV = this.addItem(81, "general_circuit.uiv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.UIV).setTier(11)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.GENERAL_CIRCUIT_UXV = this.addItem(82, "general_circuit.uxv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.UXV).setTier(12)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.GENERAL_CIRCUIT_OpV = this.addItem(83, "general_circuit.opv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.OpV).setTier(13)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.GENERAL_CIRCUIT_MAX = this.addItem(84, "general_circuit.max")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.MAX).setTier(14)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        //流体单元90
        MetaItems.WOODEN_BUCKET = this.addItem(90, "wooden_bucket").addComponents(
                        new FilteredFluidStats(1000, Wood.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(), true,
                                false, false, false, true), new ItemFluidContainer()).setMaxStackSize(1)
                .setRecyclingData(new RecyclingData(new MaterialStack(Wood, M * 8))) // ingot * 8
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.FLUID_CELL_CHROME = this.addItem(91, "large_fluid_cell.chrome").addComponents(
                        new FilteredFluidStats(2_048_000, Chrome.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(),
                                true, true, false, false, true), new ItemFluidContainer()).setMaxStackSize(32)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Chrome, M * 8))) // ingot * 8
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.FLUID_CELL_IRIDIUM = this.addItem(92, "large_fluid_cell.iridium").addComponents(
                        new FilteredFluidStats(8_192_000, Iridium.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(),
                                true, true, true, false, true), new ItemFluidContainer()).setMaxStackSize(32)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Iridium, M * 8))) // ingot * 8
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.FLUID_CELL_NAQUADAH_ALLOY = this.addItem(93, "large_fluid_cell.naquadah_alloy").addComponents(
                        new FilteredFluidStats(32_768_000,
                                NaquadahAlloy.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(), true, true, true,
                                true, true), new ItemFluidContainer()).setMaxStackSize(32)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.NaquadahAlloy, M * 8))) // ingot * 8
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.FLUID_CELL_NEUTRONIUM = this.addItem(94, "large_fluid_cell.neutronium").addComponents(
                        new FilteredFluidStats(131_072_000,
                                Neutronium.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(), true, true, true, true,
                                true), new ItemFluidContainer()).setMaxStackSize(32)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Neutronium, M * 8))) // ingot * 8
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        // 200-: Vanadium Steel Molds & Extruders.
        MetaItems.CASTING_MOLD_EMPTY = addItem(200, "shape.mold.vanadium_steel.empty").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.CASTING_MOLD_SAW = addItem(201, "shape.mold.vanadium_steel.saw").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.CASTING_MOLD_HARD_HAMMER = addItem(202, "shape.mold.vanadium_steel.hard_hammer").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.CASTING_MOLD_SOFT_MALLET = addItem(203, "shape.mold.vanadium_steel.soft_mallet").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.CASTING_MOLD_WRENCH = addItem(204, "shape.mold.vanadium_steel.wrench").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.CASTING_MOLD_FILE = addItem(205, "shape.mold.vanadium_steel.file").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.CASTING_MOLD_CROWBAR = addItem(206, "shape.mold.vanadium_steel.crowbar").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.CASTING_MOLD_SCREWDRIVER = addItem(207, "shape.mold.vanadium_steel.screwdriver").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.CASTING_MOLD_MORTAR = addItem(208, "shape.mold.vanadium_steel.mortar").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.CASTING_MOLD_WIRE_CUTTER = addItem(209, "shape.mold.vanadium_steel.wire_cutter").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.CASTING_MOLD_KNIFE = addItem(210, "shape.mold.vanadium_steel.knife").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.CASTING_MOLD_BUTCHERY_KNIFE = addItem(211,
                "shape.mold.vanadium_steel.butchery_knife").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.CASTING_MOLD_ROLLING_PIN = addItem(212, "shape.mold.vanadium_steel.rolling_pin").setRecyclingData(
                        new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        // 601-700: Tools.
        MetaItems.DISPOSABLE_SAW = addItem(220, "tool.disposable.saw").addOreDict("toolSaw")
                .addOreDict("craftingToolSaw").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.DISPOSABLE_HARD_HAMMER = addItem(221, "tool.disposable.hard_hammer").addOreDict("toolHammer")
                .addOreDict("craftingToolHardHammer").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.DISPOSABLE_SOFT_MALLET = addItem(222, "tool.disposable.soft_mallet").addOreDict("toolMallet")
                .addOreDict("craftingToolSoftHammer").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.DISPOSABLE_WRENCH = addItem(223, "tool.disposable.wrench").addOreDict("toolWrench")
                .addOreDict("craftingToolWrench").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.DISPOSABLE_FILE = addItem(224, "tool.disposable.file").addOreDict("toolFile")
                .addOreDict("craftingToolFile").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.DISPOSABLE_CROWBAR = addItem(225, "tool.disposable.crowbar").addOreDict("toolCrowbar")
                .addOreDict("craftingToolCrowbar").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.DISPOSABLE_SCREWDRIVER = addItem(226, "tool.disposable.screwdriver").addOreDict("toolScrewdriver")
                .addOreDict("craftingToolScrewdriver").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.DISPOSABLE_MORTAR = addItem(227, "tool.disposable.mortar").addOreDict("toolMortar")
                .addOreDict("craftingToolMortar").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.DISPOSABLE_WIRE_CUTTER = addItem(228, "tool.disposable.wire_cutter").addOreDict("toolWireCutter")
                .addOreDict("craftingToolWireCutter").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.DISPOSABLE_KNIFE = addItem(229, "tool.disposable.knife").addOreDict("toolKnife")
                .addOreDict("craftingToolKnife").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.DISPOSABLE_BUTCHERY_KNIFE = addItem(230, "tool.disposable.butchery_knife").addOreDict(
                        "toolButcheryKnife").addOreDict("craftingToolButcheryKnife")
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.DISPOSABLE_ROLLING_PIN = addItem(231, "tool.disposable.rolling_pin").addOreDict("toolRollingPin")
                .addOreDict("craftingToolRollingPin").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.VEIN_SCANNER = addItem(232, "tool.scanner").addComponents(new VeinScanBehavior());
    }
}
