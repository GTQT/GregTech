package gregtech.common.metatileentities.registration;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.capability.FeCompat;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.material.Materials;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.ConfigHolder;
import gregtech.common.metatileentities.MetaTileEntityClipboard;
import gregtech.common.metatileentities.converter.MetaTileEntityConverter;
import gregtech.common.metatileentities.electric.DisposableBatteryType;
import gregtech.common.metatileentities.electric.MetaTileEntityAlarm;
import gregtech.common.metatileentities.electric.MetaTileEntityBatteryBuffer;
import gregtech.common.metatileentities.electric.MetaTileEntityBlockBreaker;
import gregtech.common.metatileentities.electric.MetaTileEntityCharger;
import gregtech.common.metatileentities.electric.MetaTileEntityDiode;
import gregtech.common.metatileentities.electric.MetaTileEntityDisposableBatteryBase;
import gregtech.common.metatileentities.electric.MetaTileEntityFisher;
import gregtech.common.metatileentities.electric.MetaTileEntityHull;
import gregtech.common.metatileentities.electric.MetaTileEntityItemCollector;
import gregtech.common.metatileentities.electric.MetaTileEntityMagicEnergyAbsorber;
import gregtech.common.metatileentities.electric.MetaTileEntityPump;
import gregtech.common.metatileentities.electric.MetaTileEntityTeleporter;
import gregtech.common.metatileentities.electric.MetaTileEntityTransformer;
import gregtech.common.metatileentities.electric.MetaTileEntityWorldAccelerator;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityCentralMonitor;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityMonitorScreen;
import gregtech.common.metatileentities.storage.MetaTileEntityBuffer;
import gregtech.common.metatileentities.storage.MetaTileEntityCrate;
import gregtech.common.metatileentities.storage.MetaTileEntityCreativeChest;
import gregtech.common.metatileentities.storage.MetaTileEntityCreativeEnergy;
import gregtech.common.metatileentities.storage.MetaTileEntityCreativeTank;
import gregtech.common.metatileentities.storage.MetaTileEntityDrum;
import gregtech.common.metatileentities.storage.MetaTileEntityLockedSafe;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumChest;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumExtender;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumProxy;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumStorageController;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumTank;
import gregtech.common.metatileentities.workbench.MetaTileEntityWorkbench;
import gregtech.common.pipelike.fluidpipe.longdistance.MetaTileEntityLDFluidEndpoint;
import gregtech.common.pipelike.itempipe.longdistance.MetaTileEntityLDItemEndpoint;

import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.common.metatileentities.MetaTileEntities.*;

/**
 * Registration for infrastructure and non-processing single-block machines:
 * <ul>
 *   <li>Locked Safe (ID 0)</li>
 *   <li>Hulls, Transformers, Diodes, Battery Buffers, Chargers (IDs 665-999)</li>
 *   <li>Pumps, Block Breakers, Fishers, Wind Generators, etc.</li>
 *   <li>Tool Caster, Bath Condenser (IDs 745-755)</li>
 *   <li>Energy Converters (IDs 1790-1849)</li>
 *   <li>Disposable Battery Blocks (IDs 2105+)</li>
 *   <li>Storage: Quantum Chests/Tanks, Drums, Crates (IDs 2020-2080)</li>
 *   <li>Misc: Creative machines, Workbench, Clipboard, Monitor, LD endpoints (IDs 2000+)</li>
 * </ul>
 */
public final class InfrastructureRegistration {

    private InfrastructureRegistration() {}

    public static void init() {
        registerMisc();
        registerHullsAndTransformers();
        registerElectricUtilities();
        registerEnergyConverters();
        registerBatteryBlocks();
        registerStorage();
        registerCreativeAndTools();
    }

    // ---- Locked Safe and misc starting IDs ----

    private static void registerMisc() {
        LOCKED_SAFE = registerMetaTileEntity(0, new MetaTileEntityLockedSafe(gregtechId("locked_safe")));
    }

    // ---- Hulls, Transformers, Diodes, Battery Buffers ----

    private static void registerHullsAndTransformers() {
        // World Accelerators, IDs 665-679
        if (ConfigHolder.machines.enableWorldAccelerators) {
            for (int i = 0; i < WORLD_ACCELERATOR.length; i++) {
                WORLD_ACCELERATOR[i] = registerMetaTileEntity(665 + i,
                        new MetaTileEntityWorldAccelerator(
                                gregtechId("world_accelerator." + GTValues.VN[i].toLowerCase()), i + 1));
            }
        }

        // Teleporter IDs 680-694
        for (int i = 0; i < TELEPORTER.length; i++){
            TELEPORTER[i] = registerMetaTileEntity(680 + i,
                    new MetaTileEntityTeleporter(
                            gregtechId("teleporter." + GTValues.VN[i].toLowerCase()), i + 1));
        }

        // Charger IDs 695-709
        for (int i = 0; i < CHARGER.length; i++) {
            String chargerId = "charger." + GTValues.VN[i].toLowerCase();
            MetaTileEntityCharger charger = new MetaTileEntityCharger(gregtechId(chargerId), i, 4);
            CHARGER[i] = registerMetaTileEntity(695 + i, charger);
        }

        // Buffers, IDs 930-934
        BUFFER[0] = registerMetaTileEntity(930, new MetaTileEntityBuffer(gregtechId("buffer.lv"), 1));
        BUFFER[1] = registerMetaTileEntity(931, new MetaTileEntityBuffer(gregtechId("buffer.mv"), 2));
        BUFFER[2] = registerMetaTileEntity(932, new MetaTileEntityBuffer(gregtechId("buffer.hv"), 3));
        BUFFER[3] = registerMetaTileEntity(933, new MetaTileEntityBuffer(gregtechId("buffer.ev"), 4));
        BUFFER[4] = registerMetaTileEntity(934, new MetaTileEntityBuffer(gregtechId("buffer.iv"), 5));

        // Hulls, IDs 985-999
        int endPos = GregTechAPI.isHighTier() ? HULL.length : Math.min(HULL.length - 1, GTValues.UV + 2);
        for (int i = 0; i < endPos; i++) {
            HULL[i] = new MetaTileEntityHull(gregtechId("hull." + GTValues.VN[i].toLowerCase()), i);
            registerMetaTileEntity(985 + i, HULL[i]);
        }

        // Transformer, IDs 1420-1464
        endPos = GregTechAPI.isHighTier() ? TRANSFORMER.length - 1 : Math.min(TRANSFORMER.length - 1, GTValues.UV);
        for (int i = 0; i <= endPos; i++) {
            // 1A <-> 4A
            MetaTileEntityTransformer transformer = new MetaTileEntityTransformer(
                    gregtechId("transformer." + GTValues.VN[i].toLowerCase()), i);
            TRANSFORMER[i] = registerMetaTileEntity(1420 + (i), transformer);
            // 2A <-> 8A and 4A <-> 16A
            MetaTileEntityTransformer adjustableTransformer = new MetaTileEntityTransformer(
                    gregtechId("transformer.hi_amp." + GTValues.VN[i].toLowerCase()), i, 2, 4);
            HI_AMP_TRANSFORMER[i] = registerMetaTileEntity(1450 + i, adjustableTransformer);
            // 16A <-> 64A (can do other amperages because of legacy compat)
            adjustableTransformer = new MetaTileEntityTransformer(
                    gregtechId("transformer.adjustable." + GTValues.VN[i].toLowerCase()), i, 1, 2, 4, 16);
            POWER_TRANSFORMER[i] = registerMetaTileEntity(1435 + (i), adjustableTransformer);
        }

        // Diode, IDs 1465-1479
        endPos = GregTechAPI.isHighTier() ? DIODES.length - 1 : Math.min(DIODES.length - 1, GTValues.UV + 2);
        for (int i = 0; i < endPos; i++) {
            String diodeId = "diode." + GTValues.VN[i].toLowerCase();
            MetaTileEntityDiode diode = new MetaTileEntityDiode(gregtechId(diodeId), i, 16);
            DIODES[i] = registerMetaTileEntity(1465 + i, diode);
        }

        // Battery Buffer, IDs 1480-1524
        endPos = GregTechAPI.isHighTier() ? BATTERY_BUFFER[0].length : GTValues.UHV + 1;
        int[] batteryBufferSlots = new int[] { 4, 8, 16 };
        for (int slot = 0; slot < batteryBufferSlots.length; slot++) {
            BATTERY_BUFFER[slot] = new MetaTileEntityBatteryBuffer[endPos];
            for (int i = 0; i < endPos; i++) {
                String bufferId = "battery_buffer." + GTValues.VN[i].toLowerCase() + "." + batteryBufferSlots[slot];
                MetaTileEntityBatteryBuffer batteryBuffer = new MetaTileEntityBatteryBuffer(gregtechId(bufferId), i,
                        batteryBufferSlots[slot]);
                BATTERY_BUFFER[slot][i] = registerMetaTileEntity(1480 + BATTERY_BUFFER[slot].length * slot + i,
                        batteryBuffer);
            }
        }
    }

    // ---- Electric utility machines (non-processing) ----

    private static void registerElectricUtilities() {
        // Fishers, IDs 710-724
        for (int i = 0; i < FISHER.length; i++) {
            FISHER[i] = registerMetaTileEntity(710 + i,
                    new MetaTileEntityFisher(gregtechId("fisher." + GTValues.VN[i + 1].toLowerCase()), i + 1));
        }

        // Pumps, IDs 725-739
        for (int i = 0; i < PUMP.length; i++) {
            PUMP[i] = registerMetaTileEntity(725 + i,
                    new MetaTileEntityPump(gregtechId("pump." + GTValues.VN[i + 1].toLowerCase()), i + 1));
        }

        // Block Breakers, IDs 740-745
        for (int i = 0; i < BLOCK_BREAKER.length; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            BLOCK_BREAKER[i] = new MetaTileEntityBlockBreaker(gregtechId("block_breaker." + voltageName), i + 1);
            registerMetaTileEntity(740 + i, BLOCK_BREAKER[i]);
        }

        // Tool Caster IDs 745-750
        for (int i = 0; i < TOOL_CASTER.length; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            TOOL_CASTER[i] = new SimpleMachineMetaTileEntity(gregtechId("tool_caster." + voltageName), RecipeMaps.TOOL_CASTER_RECIPES, Textures.TOOL_CASTER_OVERLAY, i+1,true);
            registerMetaTileEntity(745 + i, TOOL_CASTER[i]);
        }

        // Bath Condenser IDs 750-755
        for (int i = 0; i < BATH_CONDENSER.length; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            BATH_CONDENSER[i] = new SimpleMachineMetaTileEntity(gregtechId("bath_condenser." + voltageName), RecipeMaps.BATH_CONDENSER_RECIPES, Textures.BATH_CONDENSER_OVERLAY, i+1,true);
            registerMetaTileEntity(750 + i, BATH_CONDENSER[i]);
        }

        // Magic Energy Absorber, IDs 975-979
        for (int i = 0; i < MAGIC_ENERGY_ABSORBER.length; i++){
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            MAGIC_ENERGY_ABSORBER[i] = registerMetaTileEntity(975 + i,
                    new MetaTileEntityMagicEnergyAbsorber(gregtechId("magic_energy_absorber." + voltageName),i + 1));
        }

        // Item Collector, IDs 980-984
        for (int i = 0; i < ITEM_COLLECTOR.length; i++){
            String  voltageName = GTValues.VN[i + 1].toLowerCase();
            ITEM_COLLECTOR[i] = registerMetaTileEntity(980 + i,
                    new MetaTileEntityItemCollector(gregtechId("item_collector." + voltageName),i + 1,
                            (int) (Math.pow(2, i)*8)));
        }
    }

    // ---- Energy Converters ----

    private static void registerEnergyConverters() {
        // Energy Converter, IDs 1790-1849
        int endPos = GregTechAPI.isHighTier() ? ENERGY_CONVERTER[0].length : GTValues.UHV + 1;
        int[] amps = { 1, 4, 8, 16 };
        for (int i = 0; i < endPos; i++) {
            for (int j = 0; j < 4; j++) {
                long eu = amps[j] * GTValues.V[i];
                long euToFe = FeCompat.toFeLong(eu, FeCompat.ratio(false));
                long feToEu = FeCompat.toEu(Integer.MAX_VALUE, FeCompat.ratio(true));
                if (euToFe > Integer.MAX_VALUE || feToEu < eu) continue;

                String id = "energy_converter." + GTValues.VN[i].toLowerCase() + "." + amps[j];
                MetaTileEntityConverter converter = new MetaTileEntityConverter(gregtechId(id), i, amps[j]);
                ENERGY_CONVERTER[j][i] = registerMetaTileEntity(1790 + j + i * 4, converter);
            }
        }
    }

    // ---- Disposable Battery Blocks ----

    private static void registerBatteryBlocks() {
        // Disposable Battery Blocks (A-series), IDs 2105+
        ZINC_MANGANESE_CELL = registerMetaTileEntity(2105,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("zinc_manganese_cell"), DisposableBatteryType.ZINC_MANGANESE));
        LITHIUM_MANGANESE_CELL = registerMetaTileEntity(2106,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("lithium_manganese_cell"), DisposableBatteryType.LITHIUM_MANGANESE));
        NICKEL_CADMIUM_CELL = registerMetaTileEntity(2107,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("nickel_cadmium_cell"), DisposableBatteryType.NICKEL_CADMIUM));
        LEAD_ACID_BATTERY = registerMetaTileEntity(2108,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("lead_acid_battery"), DisposableBatteryType.LEAD_ACID));
        VANADIUM_FLOW_CELL = registerMetaTileEntity(2109,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("vanadium_flow_cell"), DisposableBatteryType.VANADIUM_FLOW));
        LFP_BATTERY = registerMetaTileEntity(2110,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("lfp_battery"), DisposableBatteryType.LFP));
        LCO_BATTERY = registerMetaTileEntity(2111,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("lco_battery"), DisposableBatteryType.LCO));
        NMC_BATTERY = registerMetaTileEntity(2112,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("nmc_battery"), DisposableBatteryType.NMC));
    }

    // ---- Storage: Quantum, Drums, Crates ----

    private static void registerStorage() {
        // Quantum Storage Network 2020-
        QUANTUM_STORAGE_CONTROLLER = registerMetaTileEntity(2020,
                new MetaTileEntityQuantumStorageController(gregtechId("quantum_storage_controller")));
        QUANTUM_STORAGE_PROXY = registerMetaTileEntity(2021,
                new MetaTileEntityQuantumProxy(gregtechId("quantum_storage_proxy")));
        QUANTUM_STORAGE_EXTENDER = registerMetaTileEntity(2022,
                new MetaTileEntityQuantumExtender(gregtechId("quantum_storage_extender")));

        // Super / Quantum Chests, IDs 2030-2040
        for (int i = 0; i < 5; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            QUANTUM_CHEST[i] = new MetaTileEntityQuantumChest(gregtechId("super_chest." + voltageName), i + 1,
                    4000000L * (int) Math.pow(2, i));
            registerMetaTileEntity(2030 + i, QUANTUM_CHEST[i]);
        }

        for (int i = 5; i < QUANTUM_CHEST.length; i++) {
            String voltageName = GTValues.VN[i].toLowerCase();
            long capacity = i == GTValues.UHV ? Integer.MAX_VALUE : 4000000L * (int) Math.pow(2, i);
            QUANTUM_CHEST[i] = new MetaTileEntityQuantumChest(gregtechId("quantum_chest." + voltageName), i, capacity);
            registerMetaTileEntity(2030 + i, QUANTUM_CHEST[i]);
        }

        // Super / Quantum Tanks, IDs 2040-2050
        for (int i = 0; i < 5; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            QUANTUM_TANK[i] = new MetaTileEntityQuantumTank(gregtechId("super_tank." + voltageName), i + 1,
                    4000000 * (int) Math.pow(2, i));
            registerMetaTileEntity(2040 + i, QUANTUM_TANK[i]);
        }

        for (int i = 5; i < QUANTUM_TANK.length; i++) {
            String voltageName = GTValues.VN[i].toLowerCase();
            int capacity = i == GTValues.UHV ? Integer.MAX_VALUE : 4000000 * (int) Math.pow(2, i);
            QUANTUM_TANK[i] = new MetaTileEntityQuantumTank(gregtechId("quantum_tank." + voltageName), i, capacity);
            registerMetaTileEntity(2040 + i, QUANTUM_TANK[i]);
        }


        // Drums, IDs 2060-2077
        WOODEN_DRUM = registerMetaTileEntity(2060,
                new MetaTileEntityDrum(gregtechId("drum.wood"), Materials.Wood, 16000));
        BRONZE_DRUM = registerMetaTileEntity(2061,
                new MetaTileEntityDrum(gregtechId("drum.bronze"), Materials.Bronze, 24000));
        GOLD_DRUM = registerMetaTileEntity(2062,
                new MetaTileEntityDrum(gregtechId("drum.gold"), Materials.Gold, 32000));
        COPPER_DRUM = registerMetaTileEntity(2063,
                new MetaTileEntityDrum(gregtechId("drum.copper"), Materials.Copper, 40000));
        IRON_DRUM = registerMetaTileEntity(2064,
                new MetaTileEntityDrum(gregtechId("drum.iron"), Materials.Iron, 48000));
        LEAD_DRUM = registerMetaTileEntity(2065,
                new MetaTileEntityDrum(gregtechId("drum.lead"), Materials.Lead, 56000));
        STEEL_DRUM = registerMetaTileEntity(2066,
                new MetaTileEntityDrum(gregtechId("drum.steel"), Materials.Steel, 64000));
        CHROME_DRUM = registerMetaTileEntity(2067,
                new MetaTileEntityDrum(gregtechId("drum.chrome"), Materials.Chrome, 96000));
        ALUMINIUM_DRUM = registerMetaTileEntity(2068,
                new MetaTileEntityDrum(gregtechId("drum.aluminium"), Materials.Aluminium, 128000));
        STAINLESS_STEEL_DRUM = registerMetaTileEntity(2069,
                new MetaTileEntityDrum(gregtechId("drum.stainless_steel"), Materials.StainlessSteel, 256000));
        TITANIUM_DRUM = registerMetaTileEntity(2070,
                new MetaTileEntityDrum(gregtechId("drum.titanium"), Materials.Titanium, 512000));
        TUNGSTEN_DRUM = registerMetaTileEntity(2071,
                new MetaTileEntityDrum(gregtechId("drum.tungsten"), Materials.Tungsten, 768000));
        TUNGSTENSTEEL_DRUM = registerMetaTileEntity(2072,
                new MetaTileEntityDrum(gregtechId("drum.tungstensteel"), Materials.TungstenSteel, 1024000));
        IRIDIUM_DRUM = registerMetaTileEntity(2073,
                new MetaTileEntityDrum(gregtechId("drum.iridium"), Materials.Iridium, 1536000));
        RHODIUM_PLATED_PALLADIUM_DRUM = registerMetaTileEntity(2074,
                new MetaTileEntityDrum(gregtechId("drum.rhodium_plated_palladium"), Materials.RhodiumPlatedPalladium, 2048000));
        NAQUADAH_ALLOY_DRUM = registerMetaTileEntity(2075,
                new MetaTileEntityDrum(gregtechId("drum.naquadah_alloy"), Materials.NaquadahAlloy, 4096000));
        DARMSTADTIUM_DRUM = registerMetaTileEntity(2076,
                new MetaTileEntityDrum(gregtechId("drum.darmstadtium"), Materials.Darmstadtium, 8192000));
        NEUTRONIUM_DRUM = registerMetaTileEntity(2077,
                new MetaTileEntityDrum(gregtechId("drum.neutronium"), Materials.Neutronium, 16384000));

        // Crates, IDs 2080-2095
        WOODEN_CRATE = registerMetaTileEntity(2080,
                new MetaTileEntityCrate(gregtechId("crate.wood"), Materials.Wood, 27, 9));
        COPPER_CRATE = registerMetaTileEntity(2081,
                new MetaTileEntityCrate(gregtechId("crate.copper"), Materials.Copper, 36, 9));
        IRON_CRATE = registerMetaTileEntity(2082,
                new MetaTileEntityCrate(gregtechId("crate.iron"), Materials.Iron, 45, 9));
        BRONZE_CRATE = registerMetaTileEntity(2083,
                new MetaTileEntityCrate(gregtechId("crate.bronze"), Materials.Bronze, 54, 9));
        SILVER_CRATE = registerMetaTileEntity(2084,
                new MetaTileEntityCrate(gregtechId("crate.silver"), Materials.Silver, 63, 9));
        STEEL_CRATE = registerMetaTileEntity(2085,
                new MetaTileEntityCrate(gregtechId("crate.steel"), Materials.Steel, 72, 9));
        GOLD_CRATE = registerMetaTileEntity(2086,
                new MetaTileEntityCrate(gregtechId("crate.gold"), Materials.Gold, 81, 9));
        ALUMINIUM_CRATE = registerMetaTileEntity(2087,
                new MetaTileEntityCrate(gregtechId("crate.aluminium"), Materials.Aluminium, 90, 10));
        DIAMOND_CRATE = registerMetaTileEntity(2088,
                new MetaTileEntityCrate(gregtechId("crate.diamond"), Materials.Diamond, 100, 10));
        STAINLESS_STEEL_CRATE = registerMetaTileEntity(2089,
                new MetaTileEntityCrate(gregtechId("crate.stainless_steel"), Materials.StainlessSteel, 108, 12));
        TITANIUM_CRATE = registerMetaTileEntity(2090,
                new MetaTileEntityCrate(gregtechId("crate.titanium"), Materials.Titanium, 126, 14));
        TUNGSTENSTEEL_CRATE = registerMetaTileEntity(2091,
                new MetaTileEntityCrate(gregtechId("crate.tungstensteel"), Materials.TungstenSteel, 144, 16));
        RHODIUM_PLATED_PALLADIUM_CRATE = registerMetaTileEntity(2092,
                new MetaTileEntityCrate(gregtechId("crate.rhodium_plated_palladium"), Materials.RhodiumPlatedPalladium, 162, 18));
        NAQUADAH_ALLOY_CRATE = registerMetaTileEntity(2093,
                new MetaTileEntityCrate(gregtechId("crate.naquadah_alloy"), Materials.NaquadahAlloy, 180, 20));
        DARMSTADTIUM_CRATE = registerMetaTileEntity(2094,
                new MetaTileEntityCrate(gregtechId("crate.darmstadtium"), Materials.Darmstadtium, 198, 22));
        NEUTRONIUM_CRATE = registerMetaTileEntity(2095,
                new MetaTileEntityCrate(gregtechId("crate.neutronium"), Materials.Neutronium, 216, 24));
    }

    // ---- Creative machines, Workbench, Clipboard, Monitor, LD endpoints, Alarm ----

    private static void registerCreativeAndTools() {
        WORKBENCH = registerMetaTileEntity(2000, new MetaTileEntityWorkbench(gregtechId("workbench")));

        CREATIVE_ENERGY = registerMetaTileEntity(2003, new MetaTileEntityCreativeEnergy());

        CREATIVE_CHEST = registerMetaTileEntity(2015, new MetaTileEntityCreativeChest(gregtechId("creative_chest")));
        CREATIVE_TANK = registerMetaTileEntity(2016, new MetaTileEntityCreativeTank(gregtechId("creative_tank")));

        LONG_DIST_ITEM_ENDPOINT = registerMetaTileEntity(2017,
                new MetaTileEntityLDItemEndpoint(gregtechId("ld_item_endpoint")));
        LONG_DIST_FLUID_ENDPOINT = registerMetaTileEntity(2018,
                new MetaTileEntityLDFluidEndpoint(gregtechId("ld_fluid_endpoint")));

        ALARM = registerMetaTileEntity(2019, new MetaTileEntityAlarm(gregtechId("alarm")));

        CLIPBOARD_TILE = registerMetaTileEntity(2055, new MetaTileEntityClipboard(gregtechId("clipboard")));

        MONITOR_SCREEN = registerMetaTileEntity(2056, new MetaTileEntityMonitorScreen(gregtechId("monitor_screen")));
        CENTRAL_MONITOR = registerMetaTileEntity(1029, new MetaTileEntityCentralMonitor(gregtechId("central_monitor")));
    }
}
