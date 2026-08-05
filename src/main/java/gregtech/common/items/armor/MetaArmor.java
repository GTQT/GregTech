package gregtech.common.items.armor;

import gregtech.api.items.armor.ArmorMetaItem;
import gregtech.common.ConfigHolder;
import gregtech.common.items.MetaItems;

import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumRarity;

public class MetaArmor extends ArmorMetaItem<ArmorMetaItem<?>.ArmorMetaValueItem> {

    @Override
    public void registerSubItems() {
        MetaItems.NIGHTVISION_GOGGLES = addItem(1, "nightvision_goggles").setArmorLogic(new NightvisionGoggles(2,
                80_000L * (long) Math.max(1, Math.pow(1, ConfigHolder.tools.voltageTierNightVision - 1)),
                ConfigHolder.tools.voltageTierNightVision, EntityEquipmentSlot.HEAD));

        MetaItems.SEMIFLUID_JETPACK = addItem(2, "liquid_fuel_jetpack").setArmorLogic(new PowerlessJetpack());
        MetaItems.ELECTRIC_JETPACK = addItem(3, "electric_jetpack").setArmorLogic(new Jetpack(30,
                1_000_000L * (long) Math.max(1, Math.pow(4, ConfigHolder.tools.voltageTierImpeller - 2)),
                ConfigHolder.tools.voltageTierImpeller)).setModelAmount(8).setRarity(EnumRarity.UNCOMMON);
        MetaItems.ELECTRIC_JETPACK_ADVANCED = addItem(4, "advanced_electric_jetpack")
                .setArmorLogic(new AdvancedJetpack(512,
                        6_400_000L * (long) Math.max(1, Math.pow(4, ConfigHolder.tools.voltageTierAdvImpeller - 4)),
                        ConfigHolder.tools.voltageTierAdvImpeller))
                .setRarity(EnumRarity.RARE);

        MetaItems.HAZMAT_HELMET = addItem(5, "hazmat.helmet")
                .setArmorLogic(new HazmatSuit(EntityEquipmentSlot.HEAD, 165));
        MetaItems.HAZMAT_CHESTPLATE = addItem(6, "hazmat.chestplate")
                .setArmorLogic(new HazmatSuit(EntityEquipmentSlot.CHEST, 240));
        MetaItems.HAZMAT_LEGGINGS = addItem(7, "hazmat.leggings")
                .setArmorLogic(new HazmatSuit(EntityEquipmentSlot.LEGS, 225));
        MetaItems.HAZMAT_BOOTS = addItem(8, "hazmat.boots")
                .setArmorLogic(new HazmatSuit(EntityEquipmentSlot.FEET, 195));

        int energyPerUse = 512;
        int tier = ConfigHolder.tools.voltageTierNanoSuit;
        long maxCapacity = 6_400_000L * (long) Math.max(1, Math.pow(4, tier - 3));
        MetaItems.NANO_HELMET = addItem(20, "nms.helmet")
                .setArmorLogic(new NanoMuscleSuite(EntityEquipmentSlot.HEAD, energyPerUse, maxCapacity, tier))
                .setRarity(EnumRarity.UNCOMMON);
        MetaItems.NANO_CHESTPLATE = addItem(21, "nms.chestplate")
                .setArmorLogic(new NanoMuscleSuite(EntityEquipmentSlot.CHEST, energyPerUse, maxCapacity, tier))
                .setRarity(EnumRarity.UNCOMMON);
        MetaItems.NANO_LEGGINGS = addItem(22, "nms.leggings")
                .setArmorLogic(new NanoMuscleSuite(EntityEquipmentSlot.LEGS, energyPerUse, maxCapacity, tier))
                .setRarity(EnumRarity.UNCOMMON);
        MetaItems.NANO_BOOTS = addItem(23, "nms.boots")
                .setArmorLogic(new NanoMuscleSuite(EntityEquipmentSlot.FEET, energyPerUse, maxCapacity, tier))
                .setRarity(EnumRarity.UNCOMMON);
        MetaItems.NANO_CHESTPLATE_ADVANCED = addItem(30, "nms.advanced_chestplate")
                .setArmorLogic(new AdvancedNanoMuscleSuite(energyPerUse,
                        12_800_000L * (long) Math.max(1, Math.pow(4, ConfigHolder.tools.voltageTierAdvNanoSuit - 3)),
                        ConfigHolder.tools.voltageTierAdvNanoSuit))
                .setRarity(EnumRarity.RARE);

        energyPerUse = 8192;
        tier = ConfigHolder.tools.voltageTierQuarkTech;
        maxCapacity = 100_000_000L * (long) Math.max(1, Math.pow(4, tier - 5));
        MetaItems.QUANTUM_HELMET = addItem(40, "qts.helmet")
                .setArmorLogic(new QuarkTechSuite(EntityEquipmentSlot.HEAD, energyPerUse, maxCapacity, tier))
                .setRarity(EnumRarity.RARE);
        MetaItems.QUANTUM_CHESTPLATE = addItem(41, "qts.chestplate")
                .setArmorLogic(new QuarkTechSuite(EntityEquipmentSlot.CHEST, energyPerUse, maxCapacity, tier))
                .setRarity(EnumRarity.RARE);
        MetaItems.QUANTUM_LEGGINGS = addItem(42, "qts.leggings")
                .setArmorLogic(new QuarkTechSuite(EntityEquipmentSlot.LEGS, energyPerUse, maxCapacity, tier))
                .setRarity(EnumRarity.RARE);
        MetaItems.QUANTUM_BOOTS = addItem(43, "qts.boots")
                .setArmorLogic(new QuarkTechSuite(EntityEquipmentSlot.FEET, energyPerUse, maxCapacity, tier))
                .setRarity(EnumRarity.RARE);
        MetaItems.QUANTUM_CHESTPLATE_ADVANCED = addItem(50, "qts.advanced_chestplate")
                .setArmorLogic(new AdvancedQuarkTechSuite(energyPerUse,
                        1_000_000_000L *
                                (long) Math.max(1, Math.pow(4, ConfigHolder.tools.voltageTierAdvQuarkTech - 6)),
                        ConfigHolder.tools.voltageTierAdvQuarkTech))
                .setRarity(EnumRarity.EPIC);

        MetaItems.SIMPLE_GAS_MASK = addItem(60, "simple_gas_mask")
                .setArmorLogic(new SimpleGasMask());

        MetaItems.GAS_MASK = addItem(61, "gas_mask")
                .setArmorLogic(new BreathingApparatus(EntityEquipmentSlot.HEAD, 300, 0));
        MetaItems.GAS_TANK = addItem(62, "gas_tank")
                .setArmorLogic(new BreathingApparatus(EntityEquipmentSlot.CHEST, 500, 1200));
        MetaItems.REBREATHER_TANK = addItem(63, "rebreather_tank")
                .setArmorLogic(new BreathingApparatus(EntityEquipmentSlot.CHEST, 800, 6000));
        MetaItems.FILTERED_TANK = addItem(64, "filtered_tank")
                .setArmorLogic(new BreathingApparatus(EntityEquipmentSlot.CHEST, 1000, 24000));
        
        MetaItems.ASBESTOS_MASK = addItem(65, "asbestos.mask")
                .setArmorLogic(new AsbestosSuit(EntityEquipmentSlot.HEAD, 250));
        MetaItems.ASBESTOS_CHESTPLATE = addItem(66, "asbestos.chestplate")
                .setArmorLogic(new AsbestosSuit(EntityEquipmentSlot.CHEST, 400));
        MetaItems.ASBESTOS_LEGGINGS = addItem(67, "asbestos.leggings")
                .setArmorLogic(new AsbestosSuit(EntityEquipmentSlot.LEGS, 350));
        MetaItems.ASBESTOS_BOOTS = addItem(68, "asbestos.boots")
                .setArmorLogic(new AsbestosSuit(EntityEquipmentSlot.FEET, 300));

        MetaItems.NOMEX_MASK = addItem(69, "nomex.mask")
                .setArmorLogic(new NomexSuit(EntityEquipmentSlot.HEAD, 700));
        MetaItems.NOMEX_CHESTPLATE = addItem(70, "nomex.chestplate")
                .setArmorLogic(new NomexSuit(EntityEquipmentSlot.CHEST, 1000));
        MetaItems.NOMEX_LEGGINGS = addItem(71, "nomex.leggings")
                .setArmorLogic(new NomexSuit(EntityEquipmentSlot.LEGS, 900));
        MetaItems.NOMEX_BOOTS = addItem(72, "nomex.boots")
                .setArmorLogic(new NomexSuit(EntityEquipmentSlot.FEET, 850));
    }
}
