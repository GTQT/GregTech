package gregtech.common.items;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.capability.impl.CommonFluidFilters;
import gregtech.api.items.metaitem.ElectricStats;
import gregtech.api.items.metaitem.FilteredFluidStats;
import gregtech.api.items.metaitem.FoodStats;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.MusicDiscStats;
import gregtech.api.items.metaitem.StandardMetaItem;
import gregtech.api.items.metaitem.stats.IItemComponent;
import gregtech.api.items.metaitem.stats.IItemContainerItemProvider;
import gregtech.api.items.metaitem.stats.ItemFluidContainer;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.MarkerMaterial;
import gregtech.api.unification.material.MarkerMaterials;
import gregtech.api.unification.material.MarkerMaterials.Component;
import gregtech.api.unification.material.MarkerMaterials.Tier;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.unification.stack.RecyclingData;
import gregtech.api.util.GTUtility;
import gregtech.api.util.RandomPotionEffect;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.covers.filter.IFilter;
import gregtech.common.covers.filter.OreDictionaryItemFilter;
import gregtech.common.covers.filter.SimpleFluidFilter;
import gregtech.common.covers.filter.SimpleItemFilter;
import gregtech.common.covers.filter.SmartItemFilter;
import gregtech.common.creativetab.GTCreativeTabs;
import gregtech.common.entities.GTBoatEntity.GTBoatType;
import gregtech.common.items.behaviors.ClipboardBehavior;
import gregtech.common.items.behaviors.DataItemBehavior;
import gregtech.common.items.behaviors.DoorBehavior;
import gregtech.common.items.behaviors.DynamiteBehaviour;
import gregtech.common.items.behaviors.FacadeItem;
import gregtech.common.items.behaviors.FertilizerBehavior;
import gregtech.common.items.behaviors.FoamSprayerBehavior;
import gregtech.common.items.behaviors.GTBoatBehavior;
import gregtech.common.items.behaviors.IntCircuitBehaviour;
import gregtech.common.items.behaviors.ItemMagnetBehavior;
import gregtech.common.items.behaviors.LighterBehaviour;
import gregtech.common.items.behaviors.MiningLaserBehavior;
import gregtech.common.items.behaviors.MultiblockRemovalBehavior;
import gregtech.common.items.behaviors.NanoSaberBehavior;
import gregtech.common.items.behaviors.PipeNetPainterBehavior;
import gregtech.common.items.behaviors.ProgrammableCircuit;
import gregtech.common.items.behaviors.ProgrammingToolkit;
import gregtech.common.items.behaviors.ProspectorScannerBehavior;
import gregtech.common.items.behaviors.ScrapBoxBehavior;
import gregtech.common.items.behaviors.StructureProjectorBehavior;
import gregtech.common.items.behaviors.Terminal2Behavior;
import gregtech.common.items.behaviors.TooltipBehavior;
import gregtech.common.items.behaviors.TricorderBehavior;
import gregtech.common.items.behaviors.TurbineRotorBehavior;
import gregtech.common.items.behaviors.VajraBehavior;
import gregtech.common.items.behaviors.VeinScanBehavior;
import gregtech.common.items.behaviors.filter.OreDictFilterUIManager;
import gregtech.common.items.behaviors.filter.SimpleFilterUIManager;
import gregtech.common.items.behaviors.filter.SimpleFluidFilterUIManager;
import gregtech.common.items.behaviors.filter.SmartFilterUIManager;
import gregtech.common.items.behaviors.monitorplugin.AdvancedMonitorPluginBehavior;
import gregtech.common.items.behaviors.monitorplugin.FakeGuiPluginBehavior;
import gregtech.common.items.behaviors.monitorplugin.OnlinePicPluginBehavior;
import gregtech.common.items.behaviors.monitorplugin.TextPluginBehavior;
import gregtech.common.items.behaviors.spray.CreativeSprayBehavior;
import gregtech.common.items.behaviors.spray.DurabilitySprayBehavior;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import static gregtech.api.GTValues.M;
import static gregtech.api.GTValues.MAX;
import static gregtech.api.unification.material.Materials.Steel;
import static gregtech.api.util.DyeUtil.getOredictColorName;
import static gregtech.common.items.MetaItems.*;

public final class GregTechMetaItem extends StandardMetaItem {

    @Override
    public void getSubItems(@NotNull CreativeTabs tab, @NotNull NonNullList<ItemStack> subItems) {
        if (!isInCreativeTab(tab)) return;
        for (MetaItem<?>.MetaValueItem item : metaItems.values()) {
            if (!item.isInCreativeTab(tab)) continue;
            item.getSubItemHandler().getSubItems(item.getStackForm(), tab, subItems);
        }
    }

    @Override
    public void registerSubItems() {
        // Credits: ID 0-10
        CREDIT_COPPER = addItem(0, "credit.copper");
        CREDIT_CUPRONICKEL = addItem(1, "credit.cupronickel");
        CREDIT_SILVER = addItem(2, "credit.silver").setRarity(EnumRarity.UNCOMMON);
        CREDIT_GOLD = addItem(3, "credit.gold").setRarity(EnumRarity.UNCOMMON);
        CREDIT_PLATINUM = addItem(4, "credit.platinum").setRarity(EnumRarity.RARE);
        CREDIT_OSMIUM = addItem(5, "credit.osmium").setRarity(EnumRarity.RARE);
        CREDIT_NAQUADAH = addItem(6, "credit.naquadah").setRarity(EnumRarity.EPIC);
        CREDIT_NEUTRONIUM = addItem(7, "credit.neutronium").setRarity(EnumRarity.EPIC);

        COIN_GOLD_ANCIENT = addItem(8, "coin.gold.ancient")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Gold, M / 4)))
                .setRarity(EnumRarity.RARE);
        COIN_DOGE = addItem(9, "coin.doge")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Brass, M / 4)))
                .setRarity(EnumRarity.EPIC);
        COIN_CHOCOLATE = addItem(10, "coin.chocolate")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Gold, M / 4)))
                .addComponents(new FoodStats(1, 0.1F, false, true, OreDictUnifier.get(OrePrefix.foil, Materials.Gold),
                        new RandomPotionEffect(MobEffects.SPEED, 200, 1, 10)));

        // Solidifier Shapes: ID 11-30
        SHAPE_EMPTY = addItem(11, "shape.empty")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[0] = SHAPE_MOLD_PLATE = addItem(12, "shape.mold.plate")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[1] = SHAPE_MOLD_GEAR = addItem(13, "shape.mold.gear")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[2] = SHAPE_MOLD_CREDIT = addItem(14, "shape.mold.credit")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[3] = SHAPE_MOLD_BOTTLE = addItem(15, "shape.mold.bottle")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[4] = SHAPE_MOLD_INGOT = addItem(16, "shape.mold.ingot")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[5] = SHAPE_MOLD_BALL = addItem(17, "shape.mold.ball")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[6] = SHAPE_MOLD_BLOCK = addItem(18, "shape.mold.block")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[7] = SHAPE_MOLD_NUGGET = addItem(19, "shape.mold.nugget")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[8] = SHAPE_MOLD_CYLINDER = addItem(20, "shape.mold.cylinder")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[9] = SHAPE_MOLD_ANVIL = addItem(21, "shape.mold.anvil")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[10] = SHAPE_MOLD_NAME = addItem(22, "shape.mold.name")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[11] = SHAPE_MOLD_GEAR_SMALL = addItem(23, "shape.mold.gear.small")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[12] = SHAPE_MOLD_ROTOR = addItem(24, "shape.mold.rotor")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[13] =SHAPE_MOLD_ROD = addItem(25, "shape.mold.rod")
                .setRecyclingData(new RecyclingData(new MaterialStack(Steel, M * 4)));
        SHAPE_MOLDS[14] =SHAPE_MOLD_BOLT = addItem(26, "shape.mold.bolt")
                .setRecyclingData(new RecyclingData(new MaterialStack(Steel, M * 4)));
        SHAPE_MOLDS[15] =SHAPE_MOLD_ROUND = addItem(27, "shape.mold.round")
                .setRecyclingData(new RecyclingData(new MaterialStack(Steel, M * 4)));
        SHAPE_MOLDS[16] =SHAPE_MOLD_SCREW = addItem(28, "shape.mold.screw")
                .setRecyclingData(new RecyclingData(new MaterialStack(Steel, M * 4)));
        SHAPE_MOLDS[17] =SHAPE_MOLD_RING = addItem(29, "shape.mold.ring")
                .setRecyclingData(new RecyclingData(new MaterialStack(Steel, M * 4)));
        SHAPE_MOLDS[18] =SHAPE_MOLD_ROD_LONG = addItem(30, "shape.mold.rod_long")
                .setRecyclingData(new RecyclingData(new MaterialStack(Steel, M * 4)));
        SHAPE_MOLDS[19] =SHAPE_MOLD_TURBINE_BLADE = addItem(31, "shape.mold.turbine_blade")
                .setRecyclingData(new RecyclingData(new MaterialStack(Steel, M * 4)));
        SHAPE_MOLDS[20] =SHAPE_MOLD_DRILL_HEAD = addItem(32, "shape.mold.drill_head")
                .setRecyclingData(new RecyclingData(new MaterialStack(Steel, M * 4)));
        SHAPE_MOLDS[21] = SHAPE_MOLD_PIPE_TINY = addItem(33, "shape.mold.pipe.tiny")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[22] = SHAPE_MOLD_PIPE_SMALL = addItem(34, "shape.mold.pipe.small")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[23] = SHAPE_MOLD_PIPE_NORMAL = addItem(35, "shape.mold.pipe.normal")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[24] = SHAPE_MOLD_PIPE_LARGE = addItem(36, "shape.mold.pipe.large")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_MOLDS[25] = SHAPE_MOLD_PIPE_HUGE = addItem(37, "shape.mold.pipe.huge")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));

        // Extruder Shapes: ID 40-66
        SHAPE_EXTRUDERS[0] = SHAPE_EXTRUDER_PLATE = addItem(40, "shape.extruder.plate")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[1] = SHAPE_EXTRUDER_ROD = addItem(41, "shape.extruder.rod")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[2] = SHAPE_EXTRUDER_BOLT = addItem(42, "shape.extruder.bolt")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[3] = SHAPE_EXTRUDER_RING = addItem(43, "shape.extruder.ring")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[4] = SHAPE_EXTRUDER_CELL = addItem(44, "shape.extruder.cell")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[5] = SHAPE_EXTRUDER_INGOT = addItem(45, "shape.extruder.ingot")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[6] = SHAPE_EXTRUDER_WIRE = addItem(46, "shape.extruder.wire")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[7] = SHAPE_EXTRUDER_PIPE_TINY = addItem(47, "shape.extruder.pipe.tiny")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[8] = SHAPE_EXTRUDER_PIPE_SMALL = addItem(48, "shape.extruder.pipe.small")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[9] = SHAPE_EXTRUDER_PIPE_NORMAL = addItem(49, "shape.extruder.pipe.normal")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[10] = SHAPE_EXTRUDER_PIPE_LARGE = addItem(50, "shape.extruder.pipe.large")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[11] = SHAPE_EXTRUDER_PIPE_HUGE = addItem(51, "shape.extruder.pipe.huge")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[12] = SHAPE_EXTRUDER_BLOCK = addItem(52, "shape.extruder.block")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        // Extruder Shapes index 13-20 (inclusive), id 53-58 (inclusive) are unused
        SHAPE_EXTRUDERS[21] = SHAPE_EXTRUDER_GEAR = addItem(59, "shape.extruder.gear")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[22] = SHAPE_EXTRUDER_BOTTLE = addItem(60, "shape.extruder.bottle")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[23] = SHAPE_EXTRUDER_FOIL = addItem(61, "shape.extruder.foil")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[24] = SHAPE_EXTRUDER_GEAR_SMALL = addItem(62, "shape.extruder.gear_small")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[25] = SHAPE_EXTRUDER_ROD_LONG = addItem(63, "shape.extruder.rod_long")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[26] = SHAPE_EXTRUDER_ROTOR = addItem(64, "shape.extruder.rotor")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4)));
        SHAPE_EXTRUDERS[27] =SHAPE_EXTRUDER_TURBINE_BLADE = addItem(65, "shape.extruder.turbine_blade")
                .setRecyclingData(new RecyclingData(new MaterialStack(Steel, M * 4)));
        SHAPE_EXTRUDERS[28] =SHAPE_EXTRUDER_DRILL_HEAD = addItem(66, "shape.extruder.drill_head")
                .setRecyclingData(new RecyclingData(new MaterialStack(Steel, M * 4)));

        // Fluid Cells: ID 78-88
        FLUID_CELL = addItem(75, "fluid_cell")
                .addComponents(new FilteredFluidStats(1000, 1800, true, false, false, false, false),
                        new ItemFluidContainer(true))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        FLUID_CELL_UNIVERSAL = addItem(76, "fluid_cell.universal")
                .addComponents(new FilteredFluidStats(1000, 1800, true, false, false, false, true),
                        new ItemFluidContainer(true))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        FLUID_CELL_LARGE_STEEL = addItem(77, "large_fluid_cell.steel")
                .addComponents(new FilteredFluidStats(8000,
                        Materials.Steel.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(), true, false,
                        false, false, true),
                        new ItemFluidContainer(true))
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 4))) // ingot * 4
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        FLUID_CELL_LARGE_ALUMINIUM = addItem(78, "large_fluid_cell.aluminium")
                .addComponents(new FilteredFluidStats(32000,
                        Materials.Aluminium.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(), true, false,
                        false, false, true),
                        new ItemFluidContainer(true))
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Aluminium, M * 4))) // ingot * 4
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        FLUID_CELL_LARGE_STAINLESS_STEEL = addItem(79, "large_fluid_cell.stainless_steel")
                .addComponents(new FilteredFluidStats(64000,
                        Materials.StainlessSteel.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(), true,
                        true, true, false, true),
                        new ItemFluidContainer(true))
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.StainlessSteel, M * 6))) // ingot * 6
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        FLUID_CELL_LARGE_TITANIUM = addItem(80, "large_fluid_cell.titanium")
                .addComponents(new FilteredFluidStats(128000,
                        Materials.Titanium.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(), true, true,
                        false, false, true),
                        new ItemFluidContainer(true))
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Titanium, M * 6))) // ingot * 6
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        FLUID_CELL_CHROME = addItem(81, "large_fluid_cell.chrome")
                .addComponents(new FilteredFluidStats(2_048_000,
                        Materials.Chrome.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(),
                        true, true, false, false, true),
                        new ItemFluidContainer(true))
                .setMaxStackSize(32)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Chrome, M * 8)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        FLUID_CELL_LARGE_TUNGSTEN_STEEL = addItem(82, "large_fluid_cell.tungstensteel")
                .addComponents(new FilteredFluidStats(512000,
                        Materials.TungstenSteel.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(), true,
                        true, false, false, true),
                        new ItemFluidContainer(true))
                .setMaxStackSize(32)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.TungstenSteel, M * 8))) // ingot * 8
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        FLUID_CELL_IRIDIUM = addItem(83, "large_fluid_cell.iridium")
                .addComponents(new FilteredFluidStats(8_192_000,
                        Materials.Iridium.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(),
                        true, true, true, false, true),
                        new ItemFluidContainer(true))
                .setMaxStackSize(32)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Iridium, M * 8)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        FLUID_CELL_NAQUADAH_ALLOY = addItem(84, "large_fluid_cell.naquadah_alloy")
                .addComponents(new FilteredFluidStats(32_768_000,
                        Materials.NaquadahAlloy.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(),
                        true, true, true, true, true),
                        new ItemFluidContainer(true))
                .setMaxStackSize(32)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.NaquadahAlloy, M * 8)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        FLUID_CELL_GLASS_VIAL = addItem(85, "fluid_cell.glass_vial")
                .addComponents(new FilteredFluidStats(1000, 1200, false, true, false, false, true),
                        new ItemFluidContainer())
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Glass, M / 4))) // small dust
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        FLUID_CELL_NEUTRONIUM = addItem(86, "large_fluid_cell.neutronium")
                .addComponents(new FilteredFluidStats(131_072_000,
                        Materials.Neutronium.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(),
                        true, true, true, true, true),
                        new ItemFluidContainer(true))
                .setMaxStackSize(32)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Neutronium, M * 8)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        WOODEN_BUCKET = addItem(89, "wooden_bucket")
                .addComponents(new FilteredFluidStats(1000,
                        Materials.Wood.getProperty(PropertyKey.FLUID_PIPE).getMaxFluidTemperature(),
                        true, false, false, false, true),
                        new ItemFluidContainer())
                .setMaxStackSize(1)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Wood, M * 8)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        // Spray Cans: ID 90-110
        SPRAY_EMPTY = addItem(91, "spray.empty");

        // out of registry order so it can reference the Empty Spray Can
        SPRAY_SOLVENT = addItem(90, "spray.solvent").setMaxStackSize(1)
                .addComponents(new DurabilitySprayBehavior(SPRAY_EMPTY.getStackForm(), 1024, null))
                .addComponents(new PipeNetPainterBehavior(1024, SPRAY_EMPTY.getStackForm(), -1))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        for (EnumDyeColor color : EnumDyeColor.values()) {
            SPRAY_CAN_DYES.put(color, addItem(92 + color.ordinal(), "spray.can.dyes." + color.getName())
                    .setMaxStackSize(1)
                    .addComponents(new DurabilitySprayBehavior(SPRAY_EMPTY.getStackForm(), 512, color))
                    .addComponents(new PipeNetPainterBehavior(512, SPRAY_EMPTY.getStackForm(), color.ordinal()))
                    .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS));
        }

        SPRAY_CREATIVE = addItem(110, "spray.creative")
                .addComponents(new CreativeSprayBehavior()).setRarity(EnumRarity.EPIC);

        // Voltage Coils: ID 96-110
        VOLTAGE_COIL_ULV = addItem(111, "voltage_coil.ulv").setTier(0).setRecyclingData(new RecyclingData(
                new MaterialStack(Materials.Lead, M * 2), new MaterialStack(Materials.IronMagnetic, M / 2)));
        VOLTAGE_COIL_LV = addItem(112, "voltage_coil.lv").setTier(1).setRecyclingData(new RecyclingData(
                new MaterialStack(Materials.Steel, M * 2), new MaterialStack(Materials.IronMagnetic, M / 2)));
        VOLTAGE_COIL_MV = addItem(113, "voltage_coil.mv").setTier(2).setRecyclingData(new RecyclingData(
                new MaterialStack(Materials.Aluminium, M * 2), new MaterialStack(Materials.SteelMagnetic, M / 2)));
        VOLTAGE_COIL_HV = addItem(114, "voltage_coil.hv").setTier(3).setRecyclingData(new RecyclingData(
                new MaterialStack(Materials.BlackSteel, M * 2), new MaterialStack(Materials.SteelMagnetic, M / 2)));
        VOLTAGE_COIL_EV = addItem(115, "voltage_coil.ev").setTier(4).setRecyclingData(new RecyclingData(
                new MaterialStack(Materials.Platinum, M * 2), new MaterialStack(Materials.NeodymiumMagnetic, M / 2)));
        VOLTAGE_COIL_IV = addItem(116, "voltage_coil.iv").setTier(5).setRecyclingData(new RecyclingData(
                new MaterialStack(Materials.Iridium, M * 2), new MaterialStack(Materials.NeodymiumMagnetic, M / 2)));
        VOLTAGE_COIL_LuV = addItem(117, "voltage_coil.luv").setTier(6).setRecyclingData(new RecyclingData(
                new MaterialStack(Materials.Osmiridium, M * 2), new MaterialStack(Materials.SamariumMagnetic, M / 2)));
        VOLTAGE_COIL_ZPM = addItem(118, "voltage_coil.zpm").setTier(7).setRecyclingData(new RecyclingData(
                new MaterialStack(Materials.Europium, M * 2), new MaterialStack(Materials.SamariumMagnetic, M / 2)));
        VOLTAGE_COIL_UV = addItem(119, "voltage_coil.uv").setTier(8).setRecyclingData(new RecyclingData(
                new MaterialStack(Materials.Tritanium, M * 2), new MaterialStack(Materials.SamariumMagnetic, M / 2)));

        //120 UHV
        //121 UEV
        //122 UIV
        //123 UXV
        //124 OpV
        //125 MAX

        // Motors: ID 127-140
        ELECTRIC_MOTOR_LV = addItem(127, "electric.motor.lv").setTier(1);
        ELECTRIC_MOTOR_MV = addItem(128, "electric.motor.mv").setTier(2);
        ELECTRIC_MOTOR_HV = addItem(129, "electric.motor.hv").setTier(3);
        ELECTRIC_MOTOR_EV = addItem(130, "electric.motor.ev").setTier(4);
        ELECTRIC_MOTOR_IV = addItem(131, "electric.motor.iv").setTier(5);
        ELECTRIC_MOTOR_LuV = addItem(132, "electric.motor.luv").setTier(6);
        ELECTRIC_MOTOR_ZPM = addItem(133, "electric.motor.zpm").setTier(7);
        ELECTRIC_MOTOR_UV = addItem(134, "electric.motor.uv").setTier(8);
        ELECTRIC_MOTOR_UHV = addItem(135, "electric.motor.uhv").setTier(9).setInvisibleIf(!GregTechAPI.isHighTier());
        ELECTRIC_MOTOR_UEV = addItem(136, "electric.motor.uev").setTier(10).setInvisibleIf(!GregTechAPI.isHighTier());
        ELECTRIC_MOTOR_UIV = addItem(137, "electric.motor.uiv").setTier(11).setInvisibleIf(!GregTechAPI.isHighTier());
        ELECTRIC_MOTOR_UXV = addItem(138, "electric.motor.uxv").setTier(12).setInvisibleIf(!GregTechAPI.isHighTier());
        ELECTRIC_MOTOR_OpV = addItem(139, "electric.motor.opv").setTier(13).setInvisibleIf(!GregTechAPI.isHighTier());
        ELECTRIC_MOTOR_MAX = addItem(140, "electric.motor.max").setTier(14).setInvisibleIf(!GregTechAPI.isHighTier());

        // Pumps: ID 141-155
        ELECTRIC_PUMP_LV = addItem(142, "electric.pump.lv").setTier(1).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.electric.pump.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 / 20));
        }));
        ELECTRIC_PUMP_MV = addItem(143, "electric.pump.mv").setTier(2).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.electric.pump.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 4 / 20));
        }));
        ELECTRIC_PUMP_HV = addItem(144, "electric.pump.hv").setTier(3).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.electric.pump.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 16 / 20));
        }));
        ELECTRIC_PUMP_EV = addItem(145, "electric.pump.ev").setTier(4).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.electric.pump.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 / 20));
        }));
        ELECTRIC_PUMP_IV = addItem(146, "electric.pump.iv").setTier(5).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.electric.pump.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 4 / 20));
        }));
        ELECTRIC_PUMP_LuV = addItem(147, "electric.pump.luv").setTier(6).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.electric.pump.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 16 / 20));
        }));
        ELECTRIC_PUMP_ZPM = addItem(148, "electric.pump.zpm").setTier(7).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.electric.pump.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 / 20));
        }));
        ELECTRIC_PUMP_UV = addItem(149, "electric.pump.uv").setTier(8).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.electric.pump.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 * 4 / 20));
        }));
        ELECTRIC_PUMP_UHV = addItem(150, "electric.pump.uhv").setTier(9).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.electric.pump.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 * 4 / 20));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        ELECTRIC_PUMP_UEV = addItem(151, "electric.pump.uev").setTier(10).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.electric.pump.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 * 4 / 20));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        ELECTRIC_PUMP_UIV = addItem(152, "electric.pump.uiv").setTier(11).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.electric.pump.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 * 4 / 20));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        ELECTRIC_PUMP_UXV = addItem(153, "electric.pump.uxv").setTier(12).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.electric.pump.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 * 4 / 20));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        ELECTRIC_PUMP_OpV = addItem(154, "electric.pump.opv").setTier(13).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.electric.pump.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 * 4 / 20));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        ELECTRIC_PUMP_MAX = addItem(155, "electric.pump.max").setTier(14).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.electric.pump.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 * 4 / 20));
        })).setInvisibleIf(!GregTechAPI.isHighTier());

        // Conveyors: ID 156-170
        CONVEYOR_MODULE_LV = addItem(157, "conveyor.module.lv").setTier(1).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.conveyor.module.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate", 8));
        }));
        CONVEYOR_MODULE_MV = addItem(158, "conveyor.module.mv").setTier(2).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.conveyor.module.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate", 32));
        }));
        CONVEYOR_MODULE_HV = addItem(159, "conveyor.module.hv").setTier(3).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.conveyor.module.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate", 64));
        }));
        CONVEYOR_MODULE_EV = addItem(160, "conveyor.module.ev").setTier(4).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.conveyor.module.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 3));
        }));
        CONVEYOR_MODULE_IV = addItem(161, "conveyor.module.iv").setTier(5).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.conveyor.module.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 8));
        }));
        CONVEYOR_MODULE_LuV = addItem(162, "conveyor.module.luv").setTier(6).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.conveyor.module.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        }));
        CONVEYOR_MODULE_ZPM = addItem(163, "conveyor.module.zpm").setTier(7).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.conveyor.module.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        }));
        CONVEYOR_MODULE_UV = addItem(164, "conveyor.module.uv").setTier(8).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.conveyor.module.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        }));
        CONVEYOR_MODULE_UHV = addItem(165, "conveyor.module.uhv").setTier(9).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.conveyor.module.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        CONVEYOR_MODULE_UEV = addItem(166, "conveyor.module.uev").setTier(10).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.conveyor.module.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        CONVEYOR_MODULE_UIV = addItem(167, "conveyor.module.uiv").setTier(11).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.conveyor.module.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        CONVEYOR_MODULE_UXV = addItem(168, "conveyor.module.uxv").setTier(12).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.conveyor.module.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        CONVEYOR_MODULE_OpV = addItem(169, "conveyor.module.opv").setTier(13).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.conveyor.module.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        CONVEYOR_MODULE_MAX = addItem(170, "conveyor.module.max").setTier(14).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.conveyor.module.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        })).setInvisibleIf(!GregTechAPI.isHighTier());

        // Pistons: ID 171-185
        ELECTRIC_PISTON_LV = addItem(172, "electric.piston.lv").setTier(1);
        ELECTRIC_PISTON_MV = addItem(173, "electric.piston.mv").setTier(2);
        ELECTRIC_PISTON_HV = addItem(174, "electric.piston.hv").setTier(3);
        ELECTRIC_PISTON_EV = addItem(175, "electric.piston.ev").setTier(4);
        ELECTRIC_PISTON_IV = addItem(176, "electric.piston.iv").setTier(5);
        ELECTRIC_PISTON_LuV = addItem(177, "electric.piston.luv").setTier(6);
        ELECTRIC_PISTON_ZPM = addItem(178, "electric.piston.zpm").setTier(7);
        ELECTRIC_PISTON_UV = addItem(179, "electric.piston.uv").setTier(8);
        ELECTRIC_PISTON_UHV = addItem(180, "electric.piston.uhv").setTier(9).setInvisibleIf(!GregTechAPI.isHighTier());
        ELECTRIC_PISTON_UEV = addItem(181, "electric.piston.uev").setTier(10).setInvisibleIf(!GregTechAPI.isHighTier());
        ELECTRIC_PISTON_UIV = addItem(182, "electric.piston.uiv").setTier(11).setInvisibleIf(!GregTechAPI.isHighTier());
        ELECTRIC_PISTON_UXV = addItem(183, "electric.piston.uxv").setTier(12).setInvisibleIf(!GregTechAPI.isHighTier());
        ELECTRIC_PISTON_OpV = addItem(184, "electric.piston.opv").setTier(13).setInvisibleIf(!GregTechAPI.isHighTier());
        ELECTRIC_PISTON_MAX = addItem(185, "electric.piston.max").setTier(14).setInvisibleIf(!GregTechAPI.isHighTier());

        // Robot Arms: ID 186-200
        ROBOT_ARM_LV = addItem(187, "robot.arm.lv").setTier(1).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.robot.arm.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate", 8));
        }));
        ROBOT_ARM_MV = addItem(188, "robot.arm.mv").setTier(2).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.robot.arm.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate", 32));
        }));
        ROBOT_ARM_HV = addItem(189, "robot.arm.hv").setTier(3).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.robot.arm.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate", 64));
        }));
        ROBOT_ARM_EV = addItem(190, "robot.arm.ev").setTier(4).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.robot.arm.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 3));
        }));
        ROBOT_ARM_IV = addItem(191, "robot.arm.iv").setTier(5).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.robot.arm.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 8));
        }));
        ROBOT_ARM_LuV = addItem(192, "robot.arm.luv").setTier(6).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.robot.arm.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        }));
        ROBOT_ARM_ZPM = addItem(193, "robot.arm.zpm").setTier(7).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.robot.arm.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        }));
        ROBOT_ARM_UV = addItem(194, "robot.arm.uv").setTier(8).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.robot.arm.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        }));
        ROBOT_ARM_UHV = addItem(195, "robot.arm.uhv").setTier(9).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.robot.arm.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        ROBOT_ARM_UEV = addItem(196, "robot.arm.uev").setTier(10).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.robot.arm.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        ROBOT_ARM_UIV = addItem(197, "robot.arm.uiv").setTier(11).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.robot.arm.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        ROBOT_ARM_UXV = addItem(198, "robot.arm.uxv").setTier(12).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.robot.arm.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        ROBOT_ARM_OpV = addItem(199, "robot.arm.opv").setTier(13).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.robot.arm.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        ROBOT_ARM_MAX = addItem(200, "robot.arm.max").setTier(14).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.robot.arm.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.item_transfer_rate_stacks", 16));
        })).setInvisibleIf(!GregTechAPI.isHighTier());

        // Field Generators: ID 201-215
        FIELD_GENERATOR_LV = addItem(202, "field.generator.lv").setTier(1);
        FIELD_GENERATOR_MV = addItem(203, "field.generator.mv").setTier(2);
        FIELD_GENERATOR_HV = addItem(204, "field.generator.hv").setTier(3);
        FIELD_GENERATOR_EV = addItem(205, "field.generator.ev").setTier(4);
        FIELD_GENERATOR_IV = addItem(206, "field.generator.iv").setTier(5);
        FIELD_GENERATOR_LuV = addItem(207, "field.generator.luv").setTier(6);
        FIELD_GENERATOR_ZPM = addItem(208, "field.generator.zpm").setTier(7);
        FIELD_GENERATOR_UV = addItem(209, "field.generator.uv").setTier(8);
        FIELD_GENERATOR_UHV = addItem(210, "field.generator.uhv").setTier(9).setInvisibleIf(!GregTechAPI.isHighTier());
        FIELD_GENERATOR_UEV = addItem(211, "field.generator.uev").setTier(10).setInvisibleIf(!GregTechAPI.isHighTier());
        FIELD_GENERATOR_UIV = addItem(212, "field.generator.uiv").setTier(11).setInvisibleIf(!GregTechAPI.isHighTier());
        FIELD_GENERATOR_UXV = addItem(213, "field.generator.uxv").setTier(12).setInvisibleIf(!GregTechAPI.isHighTier());
        FIELD_GENERATOR_OpV = addItem(214, "field.generator.opv").setTier(13).setInvisibleIf(!GregTechAPI.isHighTier());
        FIELD_GENERATOR_MAX = addItem(215, "field.generator.max").setTier(14).setInvisibleIf(!GregTechAPI.isHighTier());

        // Emitters: ID 216-230
        EMITTER_LV = addItem(217, "emitter.lv").setTier(1);
        EMITTER_MV = addItem(218, "emitter.mv").setTier(2);
        EMITTER_HV = addItem(219, "emitter.hv").setTier(3);
        EMITTER_EV = addItem(220, "emitter.ev").setTier(4);
        EMITTER_IV = addItem(221, "emitter.iv").setTier(5);
        EMITTER_LuV = addItem(222, "emitter.luv").setTier(6);
        EMITTER_ZPM = addItem(223, "emitter.zpm").setTier(7);
        EMITTER_UV = addItem(224, "emitter.uv").setTier(8);
        EMITTER_UHV = addItem(225, "emitter.uhv").setTier(9).setInvisibleIf(!GregTechAPI.isHighTier());
        EMITTER_UEV = addItem(226, "emitter.uev").setTier(10).setInvisibleIf(!GregTechAPI.isHighTier());
        EMITTER_UIV = addItem(227, "emitter.uiv").setTier(11).setInvisibleIf(!GregTechAPI.isHighTier());
        EMITTER_UXV = addItem(228, "emitter.uxv").setTier(12).setInvisibleIf(!GregTechAPI.isHighTier());
        EMITTER_OpV = addItem(229, "emitter.opv").setTier(13).setInvisibleIf(!GregTechAPI.isHighTier());
        EMITTER_MAX = addItem(230, "emitter.max").setTier(14).setInvisibleIf(!GregTechAPI.isHighTier());

        // Sensors: ID 231-245
        SENSOR_LV = addItem(232, "sensor.lv").setTier(1);
        SENSOR_MV = addItem(233, "sensor.mv").setTier(2);
        SENSOR_HV = addItem(234, "sensor.hv").setTier(3);
        SENSOR_EV = addItem(235, "sensor.ev").setTier(4);
        SENSOR_IV = addItem(236, "sensor.iv").setTier(5);
        SENSOR_LuV = addItem(237, "sensor.luv").setTier(6);
        SENSOR_ZPM = addItem(238, "sensor.zpm").setTier(7);
        SENSOR_UV = addItem(239, "sensor.uv").setTier(8);
        SENSOR_UHV = addItem(240, "sensor.uhv").setTier(9).setInvisibleIf(!GregTechAPI.isHighTier());
        SENSOR_UEV = addItem(241, "sensor.uev").setTier(10).setInvisibleIf(!GregTechAPI.isHighTier());
        SENSOR_UIV = addItem(242, "sensor.uiv").setTier(11).setInvisibleIf(!GregTechAPI.isHighTier());
        SENSOR_UXV = addItem(243, "sensor.uxv").setTier(12).setInvisibleIf(!GregTechAPI.isHighTier());
        SENSOR_OpV = addItem(244, "sensor.opv").setTier(13).setInvisibleIf(!GregTechAPI.isHighTier());
        SENSOR_MAX = addItem(245, "sensor.max").setTier(14).setInvisibleIf(!GregTechAPI.isHighTier());

        // Fluid Regulators: ID 246-260
        FLUID_REGULATOR_LV = addItem(247, "fluid.regulator.lv").setTier(1).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.fluid.regulator.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 / 20));
        }));
        FLUID_REGULATOR_MV = addItem(248, "fluid.regulator.mv").setTier(2).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.fluid.regulator.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 4 / 20));
        }));
        FLUID_REGULATOR_HV = addItem(249, "fluid.regulator.hv").setTier(3).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.fluid.regulator.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 16 / 20));
        }));
        FLUID_REGULATOR_EV = addItem(250, "fluid.regulator.ev").setTier(4).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.fluid.regulator.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 / 20));
        }));
        FLUID_REGULATOR_IV = addItem(251, "fluid.regulator.iv").setTier(5).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.fluid.regulator.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 4 / 20));
        }));
        FLUID_REGULATOR_LuV = addItem(252, "fluid.regulator.luv").setTier(6).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.fluid.regulator.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 16 / 20));
        }));
        FLUID_REGULATOR_ZPM = addItem(253, "fluid.regulator.zpm").setTier(7).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.fluid.regulator.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 / 20));
        }));
        FLUID_REGULATOR_UV = addItem(254, "fluid.regulator.uv").setTier(8).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.fluid.regulator.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 * 4 / 20));
        }));
        FLUID_REGULATOR_UHV = addItem(255, "fluid.regulator.uhv").setTier(9).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.fluid.regulator.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 * 16 / 20));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        FLUID_REGULATOR_UEV = addItem(256, "fluid.regulator.uev").setTier(10).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.fluid.regulator.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 * 64 / 20));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        FLUID_REGULATOR_UIV = addItem(257, "fluid.regulator.uiv").setTier(11).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.fluid.regulator.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 * 64 * 4 / 20));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        FLUID_REGULATOR_UXV = addItem(258, "fluid.regulator.uxv").setTier(12).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.fluid.regulator.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 * 64 * 4 / 20));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        FLUID_REGULATOR_OpV = addItem(259, "fluid.regulator.opv").setTier(13).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.fluid.regulator.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 * 64 * 4 / 20));
        })).setInvisibleIf(!GregTechAPI.isHighTier());
        FLUID_REGULATOR_MAX = addItem(260, "fluid.regulator.max").setTier(14).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.fluid.regulator.tooltip"));
            lines.add(I18n.format("gregtech.universal.tooltip.fluid_transfer_rate", 1280 * 64 * 64 * 64 * 4 / 20));
        })).setInvisibleIf(!GregTechAPI.isHighTier());


        // Data Items: ID 261-265
        TOOL_DATA_STICK = addItem(261, "tool.datastick").addComponents(new DataItemBehavior()).setRarity(EnumRarity.UNCOMMON);
        TOOL_DATA_ORB = addItem(262, "tool.dataorb").addComponents(new DataItemBehavior()).setRarity(EnumRarity.RARE);
        TOOL_DATA_MODULE = addItem(263, "tool.datamodule").addComponents(new DataItemBehavior(true)).setRarity(EnumRarity.EPIC);

        // Special Machine Components: ID 266-280
        COMPONENT_GRINDER_DIAMOND = addItem(266, "component.grinder.diamond")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M * 8),
                        new MaterialStack(Materials.Diamond, M * 5)));
        COMPONENT_GRINDER_TUNGSTEN = addItem(267, "component.grinder.tungsten")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Tungsten, M * 4),
                        new MaterialStack(Materials.VanadiumSteel, M * 8), new MaterialStack(Materials.Diamond, M)));

        IRON_MINECART_WHEELS = addItem(268, "minecart_wheels.iron")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Iron, M)));
        STEEL_MINECART_WHEELS = addItem(269, "minecart_wheels.steel")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Steel, M)));

        // Special Eyes/Stars: ID 281-289
        QUANTUM_EYE = addItem(281, "quantumeye").setRarity(EnumRarity.UNCOMMON);
        QUANTUM_STAR = addItem(282, "quantumstar").setRarity(EnumRarity.RARE);
        GRAVI_STAR = addItem(283, "gravistar").setRarity(EnumRarity.EPIC);

        // Filters: ID 290-300
        FLUID_FILTER = addItem(290, "fluid_filter")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Zinc, M * 2)))
                .addComponents(new SimpleFluidFilterUIManager(), IFilter.factory(SimpleFluidFilter::new));
        ITEM_FILTER = addItem(291, "item_filter")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Zinc, M * 2),
                        new MaterialStack(Materials.Steel, M)))
                .addComponents(new SimpleFilterUIManager(), IFilter.factory(SimpleItemFilter::new));
        ORE_DICTIONARY_FILTER = addItem(292, "ore_dictionary_filter")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Zinc, M * 2)))
                .addComponents(new OreDictFilterUIManager(), IFilter.factory(OreDictionaryItemFilter::new));
        SMART_FILTER = addItem(293, "smart_item_filter")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Zinc, M * 3 / 2)))
                .addComponents(new SmartFilterUIManager(), IFilter.factory(SmartItemFilter::new));

        // Functional Covers: ID 295-330
        COVER_DRAIN = addItem(295, "cover.drain")
                .addComponents(new TooltipBehavior(lines -> {
                    lines.add(I18n.format("metaitem.cover.drain.tooltip.1"));
                    lines.add(I18n.format("metaitem.cover.drain.tooltip.2", 100));
                }));

        COVER_AIR_VENT = addItem(296, "cover.air_vent")
                .addComponents(new TooltipBehavior(lines -> {
                    lines.add(I18n.format("metaitem.cover.air_vent.tooltip.1"));
                    lines.add(I18n.format("metaitem.cover.air_vent.tooltip.2", 100));
                }));

        COVER_PROGRAMMABLE_CIRCUIT = addItem(297, "cover.programmable_circuit")
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_PROGRAMMABLE);

        COVER_MACHINE_CONTROLLER = addItem(301, "cover.controller");
        COVER_ACTIVITY_DETECTOR = addItem(302, "cover.activity.detector");
        COVER_ACTIVITY_DETECTOR_ADVANCED = addItem(303, "cover.activity.detector_advanced");
        COVER_FLUID_DETECTOR = addItem(304, "cover.fluid.detector");
        COVER_ITEM_DETECTOR = addItem(305, "cover.item.detector");
        COVER_ENERGY_DETECTOR = addItem(306, "cover.energy.detector");
        COVER_SCREEN = addItem(307, "cover.screen");
        COVER_ENDER_ITEM_LINK = addItem(308, "cover.ender_item_link");
        COVER_ENDER_FLUID_LINK = addItem(309, "cover.ender_fluid_link");
        COVER_SHUTTER = addItem(310, "cover.shutter");
        COVER_DIGITAL_INTERFACE = addItem(312, "cover.digital");
        COVER_DIGITAL_INTERFACE_WIRELESS = addItem(313, "cover.digital.wireless");
        COVER_FLUID_VOIDING = addItem(314, "cover.fluid.voiding");
        COVER_FLUID_VOIDING_ADVANCED = addItem(315, "cover.fluid.voiding.advanced");
        COVER_ITEM_VOIDING = addItem(316, "cover.item.voiding");
        COVER_ITEM_VOIDING_ADVANCED = addItem(317, "cover.item.voiding.advanced");
        COVER_ENERGY_DETECTOR_ADVANCED = addItem(318, "cover.energy.detector.advanced");
        COVER_FLUID_DETECTOR_ADVANCED = addItem(319, "cover.fluid.detector.advanced");
        COVER_ITEM_DETECTOR_ADVANCED = addItem(320, "cover.item.detector.advanced");
        COVER_MAINTENANCE_DETECTOR = addItem(321, "cover.maintenance.detector");

        COVER_STORAGE = addItem(322, "cover.storage");
        COVER_STORAGE_MEDIUM = addItem(323, "cover.storage_medium");
        COVER_STORAGE_LARGE = addItem(324, "cover.storage_large");
        COVER_STORAGE_HUGE = addItem(325, "cover.storage_huge");

        COVER_INFINITE_WATER_LV = addItem(326, "cover.infinite_water.lv").setTier(1).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.infinite_water.tooltip.1"));
            lines.add(I18n.format("gregtech.universal.tooltip.produces_fluid", 250 / 20));
        }));
        COVER_INFINITE_WATER_MV = addItem(327, "cover.infinite_water.mv").setTier(2).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.infinite_water.tooltip.1"));
            lines.add(I18n.format("gregtech.universal.tooltip.produces_fluid", 1000 / 20));
        }));
        COVER_INFINITE_WATER_HV = addItem(328, "cover.infinite_water.hv").setTier(3).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.infinite_water.tooltip.1"));
            lines.add(I18n.format("gregtech.universal.tooltip.produces_fluid", 4000 / 20));
        }));
        COVER_INFINITE_WATER_EV = addItem(329, "cover.infinite_water.ev").setTier(4).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.infinite_water.tooltip.1"));
            lines.add(I18n.format("gregtech.universal.tooltip.produces_fluid", 16000 / 20));
        }));

        COVER_FACADE = addItem(330, "cover.facade").addComponents(new FacadeItem()).disableModelLoading();

        // Solar Panels: ID 331-346
        COVER_SOLAR_PANEL = addItem(331, "cover.solar.panel").addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", 1, GTValues.VNF[GTValues.ULV]));
        }));
        COVER_SOLAR_PANEL_ULV = addItem(332, "cover.solar.panel.ulv").setTier(0).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", GTValues.V[GTValues.ULV],
                    GTValues.VNF[GTValues.ULV]));
        }));
        COVER_SOLAR_PANEL_LV = addItem(333, "cover.solar.panel.lv").setTier(1).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", GTValues.V[GTValues.LV],
                    GTValues.VNF[GTValues.LV]));
        }));
        COVER_SOLAR_PANEL_MV = addItem(334, "cover.solar.panel.mv").setTier(2).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", GTValues.V[GTValues.MV],
                    GTValues.VNF[GTValues.MV]));
        }));
        COVER_SOLAR_PANEL_HV = addItem(335, "cover.solar.panel.hv").setTier(3).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", GTValues.V[GTValues.HV],
                    GTValues.VNF[GTValues.HV]));
        }));
        COVER_SOLAR_PANEL_EV = addItem(336, "cover.solar.panel.ev").setTier(4).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", GTValues.V[GTValues.EV],
                    GTValues.VNF[GTValues.EV]));
        }));
        COVER_SOLAR_PANEL_IV = addItem(337, "cover.solar.panel.iv").setTier(5).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", GTValues.V[GTValues.IV],
                    GTValues.VNF[GTValues.IV]));
        }));
        COVER_SOLAR_PANEL_LUV = addItem(338, "cover.solar.panel.luv").setTier(6).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", GTValues.V[GTValues.LuV],
                    GTValues.VNF[GTValues.LuV]));
        }));
        COVER_SOLAR_PANEL_ZPM = addItem(339, "cover.solar.panel.zpm").setTier(7).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", GTValues.V[GTValues.ZPM],
                    GTValues.VNF[GTValues.ZPM]));
        }));
        COVER_SOLAR_PANEL_UV = addItem(340, "cover.solar.panel.uv").setTier(8).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", GTValues.V[GTValues.UV],
                    GTValues.VNF[GTValues.UV]));
        }));

        COVER_SOLAR_PANEL_UHV = addItem(341, "cover.solar.panel.uhv").setTier(9).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", GTValues.V[GTValues.UHV],
                    GTValues.VNF[GTValues.UHV]));
        }));
        COVER_SOLAR_PANEL_UEV = addItem(342, "cover.solar.panel.uev").setTier(10).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", GTValues.V[GTValues.UEV],
                    GTValues.VNF[GTValues.UEV]));
        }));
        COVER_SOLAR_PANEL_UIV = addItem(343, "cover.solar.panel.uiv").setTier(11).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", GTValues.V[GTValues.UIV],
                    GTValues.VNF[GTValues.UIV]));
        }));
        COVER_SOLAR_PANEL_UXV = addItem(344, "cover.solar.panel.uxv").setTier(12).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", GTValues.V[GTValues.UXV],
                    GTValues.VNF[GTValues.UXV]));
        }));
        COVER_SOLAR_PANEL_OPV = addItem(345, "cover.solar.panel.opv").setTier(13).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", GTValues.V[GTValues.OpV],
                    GTValues.VNF[GTValues.OpV]));
        }));
        COVER_SOLAR_PANEL_MAX = addItem(346, "cover.solar.panel.max").setTier(14).addComponents(new TooltipBehavior(lines -> {
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.1"));
            lines.add(I18n.format("metaitem.cover.solar.panel.tooltip.2"));
            lines.add(I18n.format("gregtech.universal.tooltip.voltage_out", GTValues.V[MAX],
                    GTValues.VNF[GTValues.MAX]));
        }));

        if (!ConfigHolder.machines.enableHighTierSolars) {
            COVER_SOLAR_PANEL_UHV.setInvisible();
            COVER_SOLAR_PANEL_UEV.setInvisible();
            COVER_SOLAR_PANEL_UIV.setInvisible();
            COVER_SOLAR_PANEL_UXV.setInvisible();
            COVER_SOLAR_PANEL_OPV.setInvisible();
            COVER_SOLAR_PANEL_MAX.setInvisible();
        }

        // Early Game Brick Related: ID 347-360
        IItemContainerItemProvider selfContainerItemProvider = itemStack -> itemStack;
        WOODEN_FORM_EMPTY = addItem(347, "wooden_form.empty");
        WOODEN_FORM_BRICK = addItem(348, "wooden_form.brick").addComponents(selfContainerItemProvider);
        COMPRESSED_CLAY = addItem(349, "compressed.clay")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Clay, M)));
        COMPRESSED_COKE_CLAY = addItem(350, "compressed.coke_clay")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Clay, M)));
        COMPRESSED_FIRECLAY = addItem(351, "compressed.fireclay")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Fireclay, M)));
        FIRECLAY_BRICK = addItem(352, "brick.fireclay")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Fireclay, M)));
        COKE_OVEN_BRICK = addItem(353, "brick.coke")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Clay, M)));

        if (!ConfigHolder.recipes.harderBrickRecipes)
            COMPRESSED_CLAY.setInvisible();

        // Boules: ID 361-370
        SILICON_BOULE = addItem(361, "boule.silicon");
        PHOSPHORUS_BOULE = addItem(362, "boule.phosphorus");
        NAQUADAH_BOULE = addItem(363, "boule.naquadah");
        NEUTRONIUM_BOULE = addItem(364, "boule.neutronium");

        // Boule-Direct Wafers: ID 371-380
        SILICON_WAFER = addItem(371, "wafer.silicon");
        PHOSPHORUS_WAFER = addItem(372, "wafer.phosphorus");
        NAQUADAH_WAFER = addItem(373, "wafer.naquadah");
        NEUTRONIUM_WAFER = addItem(374, "wafer.neutronium");

        // Unfinished Circuit Boards: ID 381-400
        COATED_BOARD = addItem(381, "board.coated").setTier(1);
        PHENOLIC_BOARD = addItem(382, "board.phenolic").setTier(2);
        PLASTIC_BOARD = addItem(383, "board.plastic").setTier(3);
        EPOXY_BOARD = addItem(384, "board.epoxy").setTier(4);
        FIBER_BOARD = addItem(385, "board.fiber_reinforced").setTier(5);
        MULTILAYER_FIBER_BOARD = addItem(386, "board.multilayer.fiber_reinforced").setTier(6);
        WETWARE_BOARD = addItem(387, "board.wetware").setTier(7);

        // Finished Circuit Boards: ID 401-420
        BASIC_CIRCUIT_BOARD = addItem(401, "circuit_board.basic").setTier(1);
        GOOD_CIRCUIT_BOARD = addItem(402, "circuit_board.good").setTier(2);
        PLASTIC_CIRCUIT_BOARD = addItem(403, "circuit_board.plastic").setTier(3);
        ADVANCED_CIRCUIT_BOARD = addItem(404, "circuit_board.advanced").setTier(4);
        EXTREME_CIRCUIT_BOARD = addItem(405, "circuit_board.extreme").setTier(5);
        ELITE_CIRCUIT_BOARD = addItem(406, "circuit_board.elite").setTier(6);
        WETWARE_CIRCUIT_BOARD = addItem(407, "circuit_board.wetware").setTier(7);

        // Dyes: ID 421-436
        for (int i = 0; i < EnumDyeColor.values().length; i++) {
            EnumDyeColor dyeColor = EnumDyeColor.values()[i];
            DYE_ONLY_ITEMS[i] = addItem(421 + i, "dye." + dyeColor.getName()).addOreDict(getOredictColorName(dyeColor));
        }

        // Plant/Rubber Related: ID 438-445
        STICKY_RESIN = addItem(438, "rubber_drop").setBurnValue(200);
        PLANT_BALL = addItem(439, "plant_ball").setBurnValue(75);
        BIO_CHAFF = addItem(440, "bio_chaff").setBurnValue(200);

        // Power Units: ID 446-459
        POWER_UNIT_LV = addItem(446, "power_unit.lv").setTier(1)
                .addComponents(ElectricStats.createElectricItem(100000L, GTValues.LV)).setMaxStackSize(8);
        POWER_UNIT_MV = addItem(447, "power_unit.mv").setTier(2)
                .addComponents(ElectricStats.createElectricItem(400000L, GTValues.MV)).setMaxStackSize(8);
        POWER_UNIT_HV = addItem(448, "power_unit.hv").setTier(3)
                .addComponents(ElectricStats.createElectricItem(1600000L, GTValues.HV)).setMaxStackSize(8);
        POWER_UNIT_EV = addItem(449, "power_unit.ev").setTier(4)
                .addComponents(ElectricStats.createElectricItem(6400000L, GTValues.EV)).setMaxStackSize(8);
        POWER_UNIT_IV = addItem(450, "power_unit.iv").setTier(5)
                .addComponents(ElectricStats.createElectricItem(25600000L, GTValues.IV)).setMaxStackSize(8);

        // Usable Items: ID 455-490
        TOOL_MATCHES = addItem(455, "tool.matches")
                .addComponents(new LighterBehaviour(false, false, false))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        TOOL_MATCHBOX = addItem(456, "tool.matchbox")
                .addComponents(new LighterBehaviour(false, true, false, Items.PAPER, 16))
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        TOOL_LIGHTER_INVAR = addItem(457, "tool.lighter.invar")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Invar, M * 2)))
                .addComponents(new LighterBehaviour(GTUtility.gregtechId("lighter_open"), true, true, true))
                .addComponents(new FilteredFluidStats(100, true, CommonFluidFilters.LIGHTER_FUEL))
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        TOOL_LIGHTER_PLATINUM = addItem(458, "tool.lighter.platinum")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Platinum, M * 2)))
                .addComponents(new LighterBehaviour(GTUtility.gregtechId("lighter_open"), true, true, true))
                .addComponents(new FilteredFluidStats(1000, true, CommonFluidFilters.LIGHTER_FUEL))
                .setMaxStackSize(1)
                .setRarity(EnumRarity.UNCOMMON)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        BOTTLE_PURPLE_DRINK = addItem(459, "bottle.purple.drink").addComponents(new FoodStats(8, 0.2F, true, true,
                new ItemStack(Items.GLASS_BOTTLE), new RandomPotionEffect(MobEffects.HASTE, 800, 1, 90)));

        DYNAMITE = addItem(460, "dynamite")
                .addComponents(new DynamiteBehaviour())
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        INTEGRATED_CIRCUIT = addItem(461, "circuit.integrated").addComponents(new IntCircuitBehaviour())
                .setModelAmount(33);
        FOAM_SPRAYER = addItem(462, "foam_sprayer").addComponents(new FoamSprayerBehavior())
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        NANO_SABER = addItem(463, "nano_saber").addComponents(ElectricStats.createElectricItem(4_000_000L, GTValues.HV))
                .addComponents(new NanoSaberBehavior())
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        NANO_SABER.getMetaItem().addPropertyOverride(NanoSaberBehavior.OVERRIDE_KEY_LOCATION,
                (stack, worldIn, entityIn) -> NanoSaberBehavior.isItemActive(stack) ? 1.0f : 0.0f);

        CLIPBOARD = addItem(464, "clipboard")
                .addComponents(new ClipboardBehavior())
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        TERMINAL = addItem(465, "terminal")
                .addComponents(new Terminal2Behavior())
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        PROGRAMMABLE_CIRCUIT = addItem(466, "programmable_circuit")
                .addComponents(new ProgrammableCircuit()).addOreDict("oreProgrammableCircuit")
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_PROGRAMMABLE);

        PROGRAMMING_TOOLKIT = addItem(467, "programming_toolkit")
                .addComponents(new ProgrammingToolkit()).setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_PROGRAMMABLE);

        VEIN_SCANNER = addItem(468, "tool.scanner")
                .addComponents(new VeinScanBehavior())
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        TRICORDER_SCANNER = addItem(469, "tricorder_scanner")
                .addComponents(ElectricStats.createElectricItem(100_000L, GTValues.MV), new TricorderBehavior(2))
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        DEBUG_SCANNER = addItem(470, "debug_scanner")
                .addComponents(new TricorderBehavior(3))
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        ITEM_MAGNET_LV = addItem(471, "item_magnet.lv").setTier(1)
                .addComponents(ElectricStats.createElectricItem(100_000L, GTValues.LV), new ItemMagnetBehavior(8))
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        ITEM_MAGNET_HV = addItem(472, "item_magnet.hv").setTier(3)
                .addComponents(ElectricStats.createElectricItem(1_600_000L, GTValues.HV), new ItemMagnetBehavior(32))
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        RUBBER_WOOD_BOAT = addItem(473, "rubber_wood_boat")
                .addComponents(new GTBoatBehavior(GTBoatType.RUBBER_WOOD_BOAT)).setMaxStackSize(1).setBurnValue(400);
        TREATED_WOOD_BOAT = addItem(474, "treated_wood_boat")
                .addComponents(new GTBoatBehavior(GTBoatType.TREATED_WOOD_BOAT)).setMaxStackSize(1).setBurnValue(400);
        RUBBER_WOOD_DOOR = addItem(475, "rubber_wood_door").addComponents(new DoorBehavior(MetaBlocks.RUBBER_WOOD_DOOR))
                .setBurnValue(200).setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_DECORATIONS);
        TREATED_WOOD_DOOR = addItem(476, "treated_wood_door")
                .addComponents(new DoorBehavior(MetaBlocks.TREATED_WOOD_DOOR)).setBurnValue(200)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_DECORATIONS);

        //480
        PROSPECTOR_LV = addItem(480, "prospector.lv").setTier(1)
                .addComponents(ElectricStats.createElectricItem(100_000L, GTValues.LV),
                        new ProspectorScannerBehavior(2, GTValues.LV))
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        PROSPECTOR_MV = addItem(481, "prospector.mv").setTier(2)
                .addComponents(ElectricStats.createElectricItem(500_000L, GTValues.MV),
                        new ProspectorScannerBehavior(2, GTValues.MV))
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        PROSPECTOR_HV = addItem(482, "prospector.hv").setTier(3)
                .addComponents(ElectricStats.createElectricItem(1_000_000L, GTValues.HV),
                        new ProspectorScannerBehavior(3, GTValues.HV))
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        PROSPECTOR_EV = addItem(483, "prospector.ev").setTier(4)
                .addComponents(ElectricStats.createElectricItem(5_000_000L, GTValues.EV),
                        new ProspectorScannerBehavior(3, GTValues.EV))
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        PROSPECTOR_IV = addItem(484, "prospector.iv").setTier(5)
                .addComponents(ElectricStats.createElectricItem(10_000_000L, GTValues.IV),
                        new ProspectorScannerBehavior(4, GTValues.IV))
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        PROSPECTOR_LUV = addItem(485, "prospector.luv").setTier(6)
                .addComponents(ElectricStats.createElectricItem(50_000_000L, GTValues.LuV),
                        new ProspectorScannerBehavior(4, GTValues.LuV))
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        PROSPECTOR_ZPM = addItem(486, "prospector.zpm").setTier(7)
                .addComponents(ElectricStats.createElectricItem(100_000_000L, GTValues.ZPM),
                        new ProspectorScannerBehavior(5, GTValues.ZPM))
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        PROSPECTOR_UV = addItem(487, "prospector.uv").setTier(8)
                .addComponents(ElectricStats.createElectricItem(500_000_000L, GTValues.UV),
                        new ProspectorScannerBehavior(5, GTValues.UV))
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        PROSPECTOR_UHV = addItem(488, "prospector.uhv").setTier(9)
                .addComponents(ElectricStats.createElectricItem(1_000_000_000L, GTValues.UHV),
                        new ProspectorScannerBehavior(6, GTValues.UHV))
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        // Misc Crafting Items: ID 491-515
        ENERGIUM_DUST = addItem(491, "energium_dust");
        ENGRAVED_LAPOTRON_CHIP = addItem(492, "engraved.lapotron_chip");
        // Free ID: 493, 494, 495, 496
        NEUTRON_REFLECTOR = addItem(497, "neutron_reflector");
        GELLED_TOLUENE = addItem(498, "gelled_toluene");
        CARBON_FIBERS = addItem(499, "carbon.fibers");
        CARBON_MESH = addItem(500, "carbon.mesh");
        CARBON_FIBER_PLATE = addItem(501, "carbon.plate");
        DUCT_TAPE = addItem(502, "duct_tape");
        WIRELESS = addItem(503, "wireless");
        CAMERA = addItem(504, "camera");
        BASIC_TAPE = addItem(505, "basic_tape");

        // Tool
        MINING_LASER = addItem(510, "mining_laser")
                .addComponents(ElectricStats.createElectricItem(10_000_000L, GTValues.IV),
                        new MiningLaserBehavior())
                .setMaxStackSize(1)
                .setTier(5)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        VAJRA_HV = addItem(511, "vajra_hv")
                .addComponents(ElectricStats.createElectricItem(1_000_000L, GTValues.HV),
                        new VajraBehavior(GTValues.HV))
                .setMaxStackSize(1)
                .setTier(3)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        VAJRA_EV = addItem(512, "vajra_ev")
                .addComponents(ElectricStats.createElectricItem(5_000_000L, GTValues.EV),
                        new VajraBehavior(GTValues.EV))
                .setMaxStackSize(1)
                .setTier(4)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        VAJRA_IV = addItem(513, "vajra_iv")
                .addComponents(ElectricStats.createElectricItem(10_000_000L, GTValues.IV),
                        new VajraBehavior(GTValues.IV))
                .setMaxStackSize(1)
                .setTier(5)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        // Circuit Components: ID 516-565
        VACUUM_TUBE = addItem(516, "circuit.vacuum_tube").setUnificationData(OrePrefix.circuit, Tier.ULV);
        GLASS_TUBE = addItem(517, "component.glass.tube")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Glass, M)));
        TRANSISTOR = addItem(518, "component.transistor").setUnificationData(OrePrefix.component, Component.Transistor);
        RESISTOR = addItem(519, "component.resistor").setUnificationData(OrePrefix.component, Component.Resistor);
        CAPACITOR = addItem(520, "component.capacitor").setUnificationData(OrePrefix.component, Component.Capacitor);
        DIODE = addItem(521, "component.diode").setUnificationData(OrePrefix.component, Component.Diode);
        INDUCTOR = addItem(522, "component.inductor").setUnificationData(OrePrefix.component, Component.Inductor);
        SMD_TRANSISTOR = addItem(523, "component.smd.transistor").setUnificationData(OrePrefix.component,
                Component.Transistor).setTier(3);
        SMD_RESISTOR = addItem(524, "component.smd.resistor").setUnificationData(OrePrefix.component,
                Component.Resistor).setTier(3);
        SMD_CAPACITOR = addItem(525, "component.smd.capacitor").setUnificationData(OrePrefix.component,
                Component.Capacitor).setTier(3);
        SMD_DIODE = addItem(526, "component.smd.diode").setUnificationData(OrePrefix.component, Component.Diode).setTier(3);
        SMD_INDUCTOR = addItem(527, "component.smd.inductor").setUnificationData(OrePrefix.component,
                Component.Inductor).setTier(3);
        ADVANCED_SMD_TRANSISTOR = addItem(528, "component.advanced_smd.transistor").setTier(5);
        ADVANCED_SMD_RESISTOR = addItem(529, "component.advanced_smd.resistor").setTier(5);
        ADVANCED_SMD_CAPACITOR = addItem(530, "component.advanced_smd.capacitor").setTier(5);
        ADVANCED_SMD_DIODE = addItem(531, "component.advanced_smd.diode").setTier(5);
        ADVANCED_SMD_INDUCTOR = addItem(532, "component.advanced_smd.inductor").setTier(5);

        // Engraved and Complex Wafers: ID 566-590
        CENTRAL_PROCESSING_UNIT_WAFER = addItem(566, "wafer.central_processing_unit");
        RANDOM_ACCESS_MEMORY_WAFER = addItem(567, "wafer.random_access_memory");
        INTEGRATED_LOGIC_CIRCUIT_WAFER = addItem(568, "wafer.integrated_logic_circuit");
        NANO_CENTRAL_PROCESSING_UNIT_WAFER = addItem(569, "wafer.nano_central_processing_unit");
        QUBIT_CENTRAL_PROCESSING_UNIT_WAFER = addItem(570, "wafer.qbit_central_processing_unit");
        SIMPLE_SYSTEM_ON_CHIP_WAFER = addItem(571, "wafer.simple_system_on_chip");
        SYSTEM_ON_CHIP_WAFER = addItem(572, "wafer.system_on_chip");
        ADVANCED_SYSTEM_ON_CHIP_WAFER = addItem(573, "wafer.advanced_system_on_chip");
        HIGHLY_ADVANCED_SOC_WAFER = addItem(574, "wafer.highly_advanced_system_on_chip");
        NAND_MEMORY_CHIP_WAFER = addItem(575, "wafer.nand_memory_chip");
        NOR_MEMORY_CHIP_WAFER = addItem(576, "wafer.nor_memory_chip");
        ULTRA_LOW_POWER_INTEGRATED_CIRCUIT_WAFER = addItem(577, "wafer.ultra_low_power_integrated_circuit");
        LOW_POWER_INTEGRATED_CIRCUIT_WAFER = addItem(578, "wafer.low_power_integrated_circuit");
        POWER_INTEGRATED_CIRCUIT_WAFER = addItem(579, "wafer.power_integrated_circuit");
        HIGH_POWER_INTEGRATED_CIRCUIT_WAFER = addItem(580, "wafer.high_power_integrated_circuit");
        ULTRA_HIGH_POWER_INTEGRATED_CIRCUIT_WAFER = addItem(581, "wafer.ultra_high_power_integrated_circuit");

        // Engraved and Complex Cut Wafers: ID 591-615
        CENTRAL_PROCESSING_UNIT = addItem(591, "plate.central_processing_unit");
        RANDOM_ACCESS_MEMORY = addItem(592, "plate.random_access_memory");
        INTEGRATED_LOGIC_CIRCUIT = addItem(593, "plate.integrated_logic_circuit");
        NANO_CENTRAL_PROCESSING_UNIT = addItem(594, "plate.nano_central_processing_unit");
        QUBIT_CENTRAL_PROCESSING_UNIT = addItem(595, "plate.qbit_central_processing_unit");
        SIMPLE_SYSTEM_ON_CHIP = addItem(596, "plate.simple_system_on_chip");
        SYSTEM_ON_CHIP = addItem(597, "plate.system_on_chip");
        ADVANCED_SYSTEM_ON_CHIP = addItem(598, "plate.advanced_system_on_chip");
        HIGHLY_ADVANCED_SOC = addItem(599, "plate.highly_advanced_system_on_chip");
        NAND_MEMORY_CHIP = addItem(600, "plate.nand_memory_chip");
        NOR_MEMORY_CHIP = addItem(601, "plate.nor_memory_chip");
        ULTRA_LOW_POWER_INTEGRATED_CIRCUIT = addItem(602, "plate.ultra_low_power_integrated_circuit");
        LOW_POWER_INTEGRATED_CIRCUIT = addItem(603, "plate.low_power_integrated_circuit");
        POWER_INTEGRATED_CIRCUIT = addItem(604, "plate.power_integrated_circuit");
        HIGH_POWER_INTEGRATED_CIRCUIT = addItem(605, "plate.high_power_integrated_circuit");
        ULTRA_HIGH_POWER_INTEGRATED_CIRCUIT = addItem(606, "plate.ultra_high_power_integrated_circuit");

        // ???: ID 616-620

        // Circuits: ID 621-700

        // T1: Electronic
        ELECTRONIC_CIRCUIT_LV = addItem(621, "circuit.electronic").setUnificationData(OrePrefix.circuit, Tier.LV).setTier(1);
        ELECTRONIC_CIRCUIT_MV = addItem(622, "circuit.good_electronic").setUnificationData(OrePrefix.circuit, Tier.MV).setTier(1);

        // T2: Integrated
        INTEGRATED_CIRCUIT_LV = addItem(623, "circuit.basic_integrated").setUnificationData(OrePrefix.circuit, Tier.LV).setTier(2);
        INTEGRATED_CIRCUIT_MV = addItem(624, "circuit.good_integrated").setUnificationData(OrePrefix.circuit, Tier.MV).setTier(2);
        INTEGRATED_CIRCUIT_HV = addItem(625, "circuit.advanced_integrated").setUnificationData(OrePrefix.circuit,
                Tier.HV).setTier(2);

        // Misc Unlocks
        NAND_CHIP_ULV = addItem(626, "circuit.nand_chip").setUnificationData(OrePrefix.circuit, Tier.ULV).setTier(2);
        MICROPROCESSOR_LV = addItem(627, "circuit.microprocessor").setUnificationData(OrePrefix.circuit, Tier.LV).setTier(2);

        // T3: Processor
        PROCESSOR_MV = addItem(628, "circuit.processor").setUnificationData(OrePrefix.circuit, Tier.MV).setTier(3);
        PROCESSOR_ASSEMBLY_HV = addItem(629, "circuit.assembly").setUnificationData(OrePrefix.circuit, Tier.HV).setTier(3);
        WORKSTATION_EV = addItem(630, "circuit.workstation").setUnificationData(OrePrefix.circuit, Tier.EV).setTier(3);
        MAINFRAME_IV = addItem(631, "circuit.mainframe").setUnificationData(OrePrefix.circuit, Tier.IV).setTier(3);

        // T4: Nano
        NANO_PROCESSOR_HV = addItem(632, "circuit.nano_processor").setUnificationData(OrePrefix.circuit, Tier.HV).setTier(4);
        NANO_PROCESSOR_ASSEMBLY_EV = addItem(633, "circuit.nano_assembly").setUnificationData(OrePrefix.circuit,
                Tier.EV).setTier(4);
        NANO_COMPUTER_IV = addItem(634, "circuit.nano_computer").setUnificationData(OrePrefix.circuit, Tier.IV).setTier(4);
        NANO_MAINFRAME_LUV = addItem(635, "circuit.nano_mainframe").setUnificationData(OrePrefix.circuit, Tier.LuV).setTier(4);

        // T5: Quantum
        QUANTUM_PROCESSOR_EV = addItem(636, "circuit.quantum_processor").setUnificationData(OrePrefix.circuit, Tier.EV).setTier(5);
        QUANTUM_ASSEMBLY_IV = addItem(637, "circuit.quantum_assembly").setUnificationData(OrePrefix.circuit, Tier.IV).setTier(5);
        QUANTUM_COMPUTER_LUV = addItem(638, "circuit.quantum_computer").setUnificationData(OrePrefix.circuit, Tier.LuV).setTier(5);
        QUANTUM_MAINFRAME_ZPM = addItem(639, "circuit.quantum_mainframe").setUnificationData(OrePrefix.circuit,
                Tier.ZPM).setTier(5);

        // T6: Crystal
        CRYSTAL_PROCESSOR_IV = addItem(640, "circuit.crystal_processor").setUnificationData(OrePrefix.circuit, Tier.IV).setTier(6);
        CRYSTAL_ASSEMBLY_LUV = addItem(641, "circuit.crystal_assembly").setUnificationData(OrePrefix.circuit, Tier.LuV).setTier(6);
        CRYSTAL_COMPUTER_ZPM = addItem(642, "circuit.crystal_computer").setUnificationData(OrePrefix.circuit, Tier.ZPM).setTier(6);
        CRYSTAL_MAINFRAME_UV = addItem(643, "circuit.crystal_mainframe").setUnificationData(OrePrefix.circuit, Tier.UV).setTier(6);

        // T7: Wetware
        WETWARE_PROCESSOR_LUV = addItem(644, "circuit.wetware_processor").setUnificationData(OrePrefix.circuit,
                Tier.LuV).setTier(7);
        WETWARE_PROCESSOR_ASSEMBLY_ZPM = addItem(645, "circuit.wetware_assembly").setUnificationData(OrePrefix.circuit,
                Tier.ZPM).setTier(7);
        WETWARE_SUPER_COMPUTER_UV = addItem(646, "circuit.wetware_computer").setUnificationData(OrePrefix.circuit,
                Tier.UV).setTier(7);
        WETWARE_MAINFRAME_UHV = addItem(647, "circuit.wetware_mainframe").setUnificationData(OrePrefix.circuit,
                Tier.UHV).setTier(7);

        // General Circuits: ID 648-662
        GENERAL_CIRCUIT_ULV = addItem(648, "general_circuit.ulv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.ULV).setTier(0)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GENERAL_CIRCUIT_LV = addItem(649, "general_circuit.lv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.LV).setTier(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GENERAL_CIRCUIT_MV = addItem(650, "general_circuit.mv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.MV).setTier(2)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GENERAL_CIRCUIT_HV = addItem(651, "general_circuit.hv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.HV).setTier(3)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GENERAL_CIRCUIT_EV = addItem(652, "general_circuit.ev")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.EV).setTier(4)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GENERAL_CIRCUIT_IV = addItem(653, "general_circuit.iv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.IV).setTier(5)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GENERAL_CIRCUIT_LuV = addItem(654, "general_circuit.luv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.LuV).setTier(6)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GENERAL_CIRCUIT_ZPM = addItem(655, "general_circuit.zpm")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.ZPM).setTier(7)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GENERAL_CIRCUIT_UV = addItem(656, "general_circuit.uv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.UV).setTier(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GENERAL_CIRCUIT_UHV = addItem(657, "general_circuit.uhv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.UHV).setTier(9)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GENERAL_CIRCUIT_UEV = addItem(658, "general_circuit.uev")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.UEV).setTier(10)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GENERAL_CIRCUIT_UIV = addItem(659, "general_circuit.uiv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.UIV).setTier(11)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GENERAL_CIRCUIT_UXV = addItem(660, "general_circuit.uxv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.UXV).setTier(12)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GENERAL_CIRCUIT_OpV = addItem(661, "general_circuit.opv")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.OpV).setTier(13)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        GENERAL_CIRCUIT_MAX = addItem(662, "general_circuit.max")
                .setUnificationData(OrePrefix.circuit, MarkerMaterials.Tier.MAX).setTier(14)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        // T8: Bioware

        // T9: Optical

        // T10: Exotic

        // T11: Cosmic

        // T12: Supra-Causal

        // T13: ???

        // Crystal Circuit Components: ID 701-705
        RAW_CRYSTAL_CHIP = addItem(701, "crystal.raw");
        RAW_CRYSTAL_CHIP_PART = addItem(702, "crystal.raw_chip");
        ENGRAVED_CRYSTAL_CHIP = addItem(703, "engraved.crystal_chip");
        CRYSTAL_CENTRAL_PROCESSING_UNIT = addItem(704, "crystal.central_processing_unit");
        CRYSTAL_SYSTEM_ON_CHIP = addItem(705, "crystal.system_on_chip");

        // Wetware Circuit Components: ID 706-710
        NEURO_PROCESSOR = addItem(708, "processor.neuro");
        STEM_CELLS = addItem(709, "stem_cells");
        PETRI_DISH = addItem(710, "petri_dish");

        // Turbine Rotors: ID 711-715
        TURBINE_ROTOR = addItem(711, "turbine_rotor").addComponents(new TurbineRotorBehavior());

        // Battery Hulls: ID 716-730
        BATTERY_HULL_LV = addItem(717, "battery.hull.lv").setTier(1)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.BatteryAlloy, M))); // plate
        BATTERY_HULL_MV = addItem(718, "battery.hull.mv").setTier(2)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.BatteryAlloy, M * 3))); // plate * 3
        BATTERY_HULL_HV = addItem(719, "battery.hull.hv").setTier(3)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.BatteryAlloy, M * 9))); // plate * 9
        BATTERY_HULL_SMALL_VANADIUM = addItem(720, "battery.hull.ev").setTier(4)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.RedSteel, M * 2)));
        BATTERY_HULL_MEDIUM_VANADIUM = addItem(721, "battery.hull.iv").setTier(5)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.RoseGold, M * 6)));
        BATTERY_HULL_LARGE_VANADIUM = addItem(722, "battery.hull.luv").setTier(6)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.BlueSteel, M * 18)));
        BATTERY_HULL_MEDIUM_NAQUADRIA = addItem(723, "battery.hull.zpm").setTier(7)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Europium, M * 6)));
        BATTERY_HULL_LARGE_NAQUADRIA = addItem(724, "battery.hull.uv").setTier(8)
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.Americium, M * 18)));

        // Disposable Battery Block shells: ID 725+
        DISPOSABLE_BATTERY_SHELL_LV = addItem(725, "disposable.battery.shell.lv");
        DISPOSABLE_BATTERY_SHELL_MV = addItem(726, "disposable.battery.shell.mv");
        DISPOSABLE_BATTERY_SHELL_HV = addItem(727, "disposable.battery.shell.hv");
        DISPOSABLE_BATTERY_SHELL_EV = addItem(728, "disposable.battery.shell.ev");
        DISPOSABLE_BATTERY_SHELL_IV = addItem(729, "disposable.battery.shell.iv");

        // Batteries: 731-775
        BATTERY_ULV_TANTALUM = addItem(731, "battery.re.ulv.tantalum")
                .addComponents(ElectricStats.createRechargeableBattery(1000, GTValues.ULV))
                .setUnificationData(OrePrefix.battery, Tier.ULV).setTier(0).setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        BATTERY_LV_SODIUM = addItem(732, "battery.re.lv.sodium")
                .addComponents(ElectricStats.createRechargeableBattery(80000, GTValues.LV))
                .setUnificationData(OrePrefix.battery, Tier.LV).setTier(1).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        BATTERY_MV_SODIUM = addItem(733, "battery.re.mv.sodium")
                .addComponents(ElectricStats.createRechargeableBattery(360000, GTValues.MV))
                .setUnificationData(OrePrefix.battery, Tier.MV).setTier(2).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        BATTERY_HV_SODIUM = addItem(734, "battery.re.hv.sodium")
                .addComponents(ElectricStats.createRechargeableBattery(1200000, GTValues.HV))
                .setUnificationData(OrePrefix.battery, Tier.HV).setTier(3).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        BATTERY_LV_LITHIUM = addItem(735, "battery.re.lv.lithium")
                .addComponents(ElectricStats.createRechargeableBattery(120000, GTValues.LV))
                .setUnificationData(OrePrefix.battery, Tier.LV).setTier(1).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        BATTERY_MV_LITHIUM = addItem(736, "battery.re.mv.lithium")
                .addComponents(ElectricStats.createRechargeableBattery(420000, GTValues.MV))
                .setUnificationData(OrePrefix.battery, Tier.MV).setTier(2).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        BATTERY_HV_LITHIUM = addItem(737, "battery.re.hv.lithium")
                .addComponents(ElectricStats.createRechargeableBattery(1800000, GTValues.HV))
                .setUnificationData(OrePrefix.battery, Tier.HV).setTier(3).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        BATTERY_LV_CADMIUM = addItem(738, "battery.re.lv.cadmium")
                .addComponents(ElectricStats.createRechargeableBattery(100000, GTValues.LV))
                .setUnificationData(OrePrefix.battery, Tier.LV).setTier(1).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        BATTERY_MV_CADMIUM = addItem(739, "battery.re.mv.cadmium")
                .addComponents(ElectricStats.createRechargeableBattery(400000, GTValues.MV))
                .setUnificationData(OrePrefix.battery, Tier.MV).setTier(2).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        BATTERY_HV_CADMIUM = addItem(740, "battery.re.hv.cadmium")
                .addComponents(ElectricStats.createRechargeableBattery(1600000, GTValues.HV))
                .setUnificationData(OrePrefix.battery, Tier.HV).setTier(3).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        ENERGIUM_CRYSTAL = addItem(741, "energy_crystal")
                .addComponents(ElectricStats.createRechargeableBattery(6_400_000L, GTValues.HV))
                .setUnificationData(OrePrefix.battery, Tier.HV).setTier(3).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        LAPOTRON_CRYSTAL = addItem(742, "lapotron_crystal")
                .addComponents(ElectricStats.createRechargeableBattery(25_000_000L, GTValues.EV))
                .setUnificationData(OrePrefix.battery, Tier.EV).setTier(4).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        BATTERY_EV_VANADIUM = addItem(743, "battery.ev.vanadium")
                .addComponents(ElectricStats.createRechargeableBattery(10_240_000L, GTValues.EV))
                .setUnificationData(OrePrefix.battery, Tier.EV).setTier(4).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        BATTERY_IV_VANADIUM = addItem(744, "battery.iv.vanadium")
                .addComponents(ElectricStats.createRechargeableBattery(40_960_000L, GTValues.IV))
                .setUnificationData(OrePrefix.battery, Tier.IV).setTier(5).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        BATTERY_LUV_VANADIUM = addItem(745, "battery.luv.vanadium")
                .addComponents(ElectricStats.createRechargeableBattery(163_840_000L, GTValues.LuV))
                .setUnificationData(OrePrefix.battery, Tier.LuV).setTier(6).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        BATTERY_ZPM_NAQUADRIA = addItem(746, "battery.zpm.naquadria")
                .addComponents(ElectricStats.createRechargeableBattery(655_360_000L, GTValues.ZPM))
                .setUnificationData(OrePrefix.battery, Tier.ZPM).setTier(7).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        BATTERY_UV_NAQUADRIA = addItem(747, "battery.uv.naquadria")
                .addComponents(ElectricStats.createRechargeableBattery(2_621_440_000L, GTValues.UV))
                .setUnificationData(OrePrefix.battery, Tier.UV).setTier(8).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        ENERGY_LAPOTRONIC_ORB = addItem(748, "energy.lapotronic_orb")
                .addComponents(ElectricStats.createRechargeableBattery(250_000_000L, GTValues.IV))
                .setUnificationData(OrePrefix.battery, Tier.IV).setTier(5).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        ENERGY_LAPOTRONIC_ORB_CLUSTER = addItem(749, "energy.lapotronic_orb_cluster")
                .addComponents(ElectricStats.createRechargeableBattery(1_000_000_000L, GTValues.LuV))
                .setUnificationData(OrePrefix.battery, Tier.LuV).setTier(6).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        ENERGY_MODULE = addItem(750, "energy.module")
                .addComponents(
                        new IItemComponent[] { ElectricStats.createRechargeableBattery(4_000_000_000L, GTValues.ZPM) })
                .setUnificationData(OrePrefix.battery, Tier.ZPM).setTier(7).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        ENERGY_CLUSTER = addItem(751, "energy.cluster")
                .addComponents(
                        new IItemComponent[] { ElectricStats.createRechargeableBattery(20_000_000_000L, GTValues.UV) })
                .setUnificationData(OrePrefix.battery, Tier.UV).setTier(8).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        //特殊
        ZERO_POINT_MODULE = addItem(752, "zero_point_module")
                .addComponents(ElectricStats.createBattery(2000000000000L, GTValues.ZPM, true)).setModelAmount(8).setTier(7)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        // UHV级电池 (9) - 量子真空电池
        QUANTUM_CORE = addItem(753, "uhv.battery")
                .addComponents(ElectricStats.createRechargeableBattery(320_000_000_000L, GTValues.UHV))
                .setUnificationData(OrePrefix.battery, Tier.UHV).setTier(9).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        // UEV级电池 (10) - 引力奇点单元
        SINGULARITY_CELL = addItem(754, "uev.battery")
                .addComponents(ElectricStats.createRechargeableBattery(5_120_000_000_000L, GTValues.UEV))
                .setUnificationData(OrePrefix.battery, Tier.UEV).setTier(10).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        // UIV级电池 (11) - 时空晶体矩阵
        CHRONO_MATRIX = addItem(755, "uiv.battery")
                .addComponents(ElectricStats.createRechargeableBattery(81_920_000_000_000L, GTValues.UIV))
                .setUnificationData(OrePrefix.battery, Tier.UIV).setTier(11).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        // UXV级电池 (12) - 超光速粒子反应堆
        TACHYON_REACTOR = addItem(756, "uxv.battery")
                .addComponents(ElectricStats.createRechargeableBattery(1_310_720_000_000_000L, GTValues.UXV))
                .setUnificationData(OrePrefix.battery, Tier.UXV).setTier(12).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        // OPV级电池 (13) - 宇宙弦能量体
        COSMIC_STRING = addItem(757, "opv.battery")
                .addComponents(ElectricStats.createRechargeableBattery(20_971_520_000_000_000L, GTValues.OpV))
                .setUnificationData(OrePrefix.battery, Tier.OpV).setTier(13).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        // MAX级电池 (14) - 创世之源
        ULTIMATE_BATTERY = addItem(758, "max.battery")
                .addComponents(ElectricStats.createRechargeableBattery(Long.MAX_VALUE, GTValues.MAX))
                .setUnificationData(OrePrefix.battery, Tier.MAX).setTier(14).setModelAmount(8)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);


        POWER_THRUSTER = addItem(776, "power_thruster").setRarity(EnumRarity.UNCOMMON);
        POWER_THRUSTER_ADVANCED = addItem(777, "power_thruster_advanced").setRarity(EnumRarity.RARE);
        GRAVITATION_ENGINE = addItem(778, "gravitation_engine").setRarity(EnumRarity.EPIC);

        // Plugins: 780-799
        PLUGIN_ADVANCED_MONITOR = addItem(780, "plugin.advanced_monitor")
                .addComponents(new AdvancedMonitorPluginBehavior());
        PLUGIN_FAKE_GUI = addItem(781, "plugin.fake_gui").addComponents(new FakeGuiPluginBehavior());
        PLUGIN_ONLINE_PIC = addItem(782, "plugin.online_pic").addComponents(new OnlinePicPluginBehavior());
        PLUGIN_TEXT = addItem(783, "plugin.text").addComponents(new TextPluginBehavior());

        // Disposable Battery Block shells (A5+): ID 785+
        DISPOSABLE_BATTERY_SHELL_LUV = addItem(785, "disposable.battery.shell.luv");
        DISPOSABLE_BATTERY_SHELL_ZPM = addItem(787, "disposable.battery.shell.zpm");
        DISPOSABLE_BATTERY_SHELL_UV = addItem(788, "disposable.battery.shell.uv");

        // Exchange membranes & components: ID 786, 789+
        // Carbon nanotube film — conductive current collector for high-tier batteries
        CARBON_NANOTUBE_FILM = addItem(786, "component.carbon_nanotube_film");
        // Proton Exchange Membrane — basic polymer membrane for LV/MV/HV batteries
        PROTON_EXCHANGE_MEMBRANE = addItem(789, "component.proton_exchange_membrane");
        // Ceramic Exchange Membrane — high-temp ceramic composite for EV/IV/LuV batteries
        CERAMIC_EXCHANGE_MEMBRANE = addItem(790, "component.ceramic_exchange_membrane");
        // Graphene Exchange Membrane — advanced graphene-enhanced membrane for ZPM/UV batteries
        GRAPHENE_EXCHANGE_MEMBRANE = addItem(791, "component.graphene_exchange_membrane");

        // Records: 800-819
        SUS_RECORD = addItem(800, "record.sus").addComponents(new MusicDiscStats(GTSoundEvents.SUS_RECORD))
                .setRarity(EnumRarity.RARE).setMaxStackSize(1).setInvisible();

        // Dyed Glass Lenses: 820-840
        for (int i = 0; i < MarkerMaterials.Color.VALUES.length; i++) {
            MarkerMaterial color = MarkerMaterials.Color.VALUES[i];
            if (color != MarkerMaterials.Color.White) {
                GLASS_LENSES.put(color, addItem(820 + i, String.format("glass_lens.%s", color.toString())));
            }
        }

        //UU: 850
        SCRAP = addItem(850, "scrap");
        SCRAP_BOX = addItem(851, "scrap_box").addComponents(new ScrapBoxBehavior());
        UU_MATER = addItem(852, "uu_matter");

        // Misc 1000+
        NAN_CERTIFICATE = addItem(1000, "nan.certificate").setRarity(EnumRarity.EPIC);
        FERTILIZER = addItem(1001, "fertilizer").addComponents(new FertilizerBehavior());
        BLACKLIGHT = addItem(1002, "blacklight");

        LOGO = addItem(1003, "logo").setInvisible();
        LOGO.getMetaItem().addPropertyOverride(new ResourceLocation("xmas"), (s, w, e) -> GTValues.XMAS.get() ? 1 : 0);

        MULTIBLOCK_REMOVER = addItem(1005, "tool.multiblock_remover").addComponents(new MultiblockRemovalBehavior())
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MULTIBLOCK_PREVIEW = addItem(1006, "tool.mutliblock_preview").addComponents(new StructureProjectorBehavior())
                .setMaxStackSize(1)
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        // Forge of the Gods - Stellar Fuel catalyst for battery startup
        STELLAR_FUEL = addItem(1007, "stellar_fuel").setRarity(EnumRarity.EPIC);

        registerWirelessCoverInput(1100);
        registerWirelessCoverOutput(1115);

        // Disposable Tools (A6+): ID 2000
        MetaItems.DISPOSABLE_SAW = addItem(2000, "tool.disposable.saw").addOreDict("toolSaw")
                .addOreDict("craftingToolSaw").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.DISPOSABLE_HARD_HAMMER = addItem(2001, "tool.disposable.hard_hammer").addOreDict("toolHammer")
                .addOreDict("craftingToolHardHammer").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.DISPOSABLE_SOFT_MALLET = addItem(2002, "tool.disposable.soft_mallet").addOreDict("toolMallet")
                .addOreDict("craftingToolSoftHammer").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.DISPOSABLE_WRENCH = addItem(2003, "tool.disposable.wrench").addOreDict("toolWrench")
                .addOreDict("craftingToolWrench").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.DISPOSABLE_FILE = addItem(2004, "tool.disposable.file").addOreDict("toolFile")
                .addOreDict("craftingToolFile").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.DISPOSABLE_CROWBAR = addItem(2005, "tool.disposable.crowbar").addOreDict("toolCrowbar")
                .addOreDict("craftingToolCrowbar").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.DISPOSABLE_SCREWDRIVER = addItem(2006, "tool.disposable.screwdriver").addOreDict("toolScrewdriver")
                .addOreDict("craftingToolScrewdriver").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.DISPOSABLE_MORTAR = addItem(2007, "tool.disposable.mortar").addOreDict("toolMortar")
                .addOreDict("craftingToolMortar").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.DISPOSABLE_WIRE_CUTTER = addItem(2008, "tool.disposable.wire_cutter").addOreDict("toolWireCutter")
                .addOreDict("craftingToolWireCutter").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.DISPOSABLE_KNIFE = addItem(2009, "tool.disposable.knife").addOreDict("toolKnife")
                .addOreDict("craftingToolKnife").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.DISPOSABLE_BUTCHERY_KNIFE = addItem(2010, "tool.disposable.butchery_knife")
                .addOreDict("toolButcheryKnife").addOreDict("craftingToolButcheryKnife")
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.DISPOSABLE_ROLLING_PIN = addItem(2011, "tool.disposable.rolling_pin").addOreDict("toolRollingPin")
                .addOreDict("craftingToolRollingPin").setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);

        MetaItems.CASTING_MOLD_EMPTY = addItem(2020, "shape.mold.vanadium_steel.empty")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.CASTING_MOLD_SAW = addItem(2021, "shape.mold.vanadium_steel.saw")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.CASTING_MOLD_HARD_HAMMER = addItem(2022, "shape.mold.vanadium_steel.hard_hammer")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.CASTING_MOLD_SOFT_MALLET = addItem(2023, "shape.mold.vanadium_steel.soft_mallet")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.CASTING_MOLD_WRENCH = addItem(2024, "shape.mold.vanadium_steel.wrench")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.CASTING_MOLD_FILE = addItem(2025, "shape.mold.vanadium_steel.file")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.CASTING_MOLD_CROWBAR = addItem(2026, "shape.mold.vanadium_steel.crowbar")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.CASTING_MOLD_SCREWDRIVER = addItem(2027, "shape.mold.vanadium_steel.screwdriver")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.CASTING_MOLD_MORTAR = addItem(2028, "shape.mold.vanadium_steel.mortar")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.CASTING_MOLD_WIRE_CUTTER = addItem(2029, "shape.mold.vanadium_steel.wire_cutter")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.CASTING_MOLD_KNIFE = addItem(2030, "shape.mold.vanadium_steel.knife")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.CASTING_MOLD_BUTCHERY_KNIFE = addItem(2031, "shape.mold.vanadium_steel.butchery_knife")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.CASTING_MOLD_ROLLING_PIN = addItem(2032, "shape.mold.vanadium_steel.rolling_pin")
                .setRecyclingData(new RecyclingData(new MaterialStack(Materials.VanadiumSteel, GTValues.M * 4)))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);


    }

    private void registerWirelessCoverInput(int baseId) {
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_ULV = addItem(baseId++, "wireless_energy_cover_input.ulv")
                .setTier(0).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_LV = addItem(baseId++, "wireless_energy_cover_input.lv")
                .setTier(1).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_MV = addItem(baseId++, "wireless_energy_cover_input.mv").setTier(2)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_HV = addItem(baseId++, "wireless_energy_cover_input.hv").setTier(3)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_EV = addItem(baseId++, "wireless_energy_cover_input.ev").setTier(4)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_IV = addItem(baseId++, "wireless_energy_cover_input.iv").setTier(5)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_LUV = addItem(baseId++, "wireless_energy_cover_input.luv").setTier(6)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_ZPM = addItem(baseId++, "wireless_energy_cover_input.zpm").setTier(7)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_UV = addItem(baseId++, "wireless_energy_cover_input.uv").setTier(8)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_UHV = addItem(baseId++, "wireless_energy_cover_input.uhv").setTier(9)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_UEV = addItem(baseId++, "wireless_energy_cover_input.uev").setTier(10)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_UIV = addItem(baseId++, "wireless_energy_cover_input.uiv").setTier(11)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_UXV = addItem(baseId++, "wireless_energy_cover_input.uxv").setTier(12)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_OPV = addItem(baseId++, "wireless_energy_cover_input.opv").setTier(13)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_INPUT_MAX = addItem(baseId, "wireless_energy_cover_input.max").setTier(14)
                .addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_input.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
    }

    private void registerWirelessCoverOutput(int baseId) {
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_ULV = addItem(baseId++, "wireless_energy_cover_output.ulv")
                .setTier(0).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_LV = addItem(baseId++, "wireless_energy_cover_output.lv")
                .setTier(1).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_MV = addItem(baseId++, "wireless_energy_cover_output.mv")
                .setTier(2).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_HV = addItem(baseId++, "wireless_energy_cover_output.hv")
                .setTier(3).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_EV = addItem(baseId++, "wireless_energy_cover_output.ev")
                .setTier(4).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_IV = addItem(baseId++, "wireless_energy_cover_output.iv")
                .setTier(5).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_LUV = addItem(baseId++, "wireless_energy_cover_output.luv")
                .setTier(6).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_ZPM = addItem(baseId++, "wireless_energy_cover_output.zpm")
                .setTier(7).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UV = addItem(baseId++, "wireless_energy_cover_output.uv")
                .setTier(8).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UHV = addItem(baseId++, "wireless_energy_cover_output.uhv")
                .setTier(9).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UEV = addItem(baseId++, "wireless_energy_cover_output.uev")
                .setTier(10).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UIV = addItem(baseId++, "wireless_energy_cover_output.uiv")
                .setTier(11).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UXV = addItem(baseId++, "wireless_energy_cover_output.uxv")
                .setTier(12).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_OPV = addItem(baseId++, "wireless_energy_cover_output.opv")
                .setTier(13).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
        MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_MAX = addItem(baseId, "wireless_energy_cover_output.max")
                .setTier(14).addComponents(new TooltipBehavior(lines -> lines.add(I18n.format("metaitem.wireless_energy_cover_output.tooltip"))))
                .setCreativeTabs(GTCreativeTabs.TAB_GREGTECH_TOOLS);
    }
}
