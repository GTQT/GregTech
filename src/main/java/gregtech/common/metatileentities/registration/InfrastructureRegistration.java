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
import gregtech.common.metatileentities.electric.MetaTileEntityProgrammingProvider;
import gregtech.common.metatileentities.electric.MetaTileEntityPump;
import gregtech.common.metatileentities.electric.MetaTileEntityTeleporter;
import gregtech.common.metatileentities.electric.MetaTileEntityTransformer;
import gregtech.common.metatileentities.electric.MetaTileEntityWorldAccelerator;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityCentralMonitor;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityMonitorScreen;
import gregtech.common.metatileentities.primitive.MetaTileEntityCharcoalPileIgniter;
import gregtech.common.metatileentities.storage.MetaTileEntityBuffer;
import gregtech.common.metatileentities.storage.MetaTileEntityCrate;
import gregtech.common.metatileentities.storage.MetaTileEntityCreativeChest;
import gregtech.common.metatileentities.storage.MetaTileEntityCreativeEnergy;
import gregtech.common.metatileentities.storage.MetaTileEntityCreativeTank;
import gregtech.common.metatileentities.storage.MetaTileEntityDrum;
import gregtech.common.metatileentities.storage.MetaTileEntityLockedSafe;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumChest;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumExtender;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumMultiTank;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumProxy;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumStorageController;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumTank;
import gregtech.common.metatileentities.store.MetaTileEntityHugeBuffer;
import gregtech.common.metatileentities.workbench.MetaTileEntityWorkbench;
import gregtech.common.pipelike.fluidpipe.longdistance.MetaTileEntityLDFluidEndpoint;
import gregtech.common.pipelike.itempipe.longdistance.MetaTileEntityLDItemEndpoint;

import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.common.metatileentities.MetaTileEntities.*;

/**
 * Registration for infrastructure and non-processing single-block machines. All IDs start from 4000, organized in
 * ascending order:
 *
 * <pre>
 *   4000-4019  基础杂项 (Basic Misc)
 *   4020-4114  电力设施 & 实用设备 (Electric Utilities & Utility Equipment)
 *   4125-4244  船壳 / 变压器 / 二极管 / 电池缓存 (Hulls / Transformers / Diodes / Battery Buffers)
 *   4250-4329  能源转换器 (Energy Converters)
 *   4330-4339  一次性电池 (Disposable Battery Blocks)
 *   4340-4465  存储 (Storage — Quantum, Drums, Crates, Huge Buffers)
 * </pre>
 */
public final class InfrastructureRegistration {

    private InfrastructureRegistration() {}

    // ==================== init: 严格按 ID 升序调用 ====================

    public static void init() {
        // 4000-4019
        register4000_BasicMisc();
        // 4020-4114
        register4020_ElectricAndUtility();
        // 4125-4244
        register4125_HullsTransformersBatteries();
        // 4250-4329
        register4250_EnergyConverters();
        // 4330-4339
        register4330_BatteryBlocks();
        // 4340-4465
        register4340_Storage();
    }

    // ======================== 4000-4019: 基础杂项 ========================

    private static void register4000_BasicMisc() {
        // 4000
        LOCKED_SAFE = registerMetaTileEntity(4000,
                new MetaTileEntityLockedSafe(gregtechId("locked_safe")));
        // 4001
        CHARCOAL_PILE_IGNITER = registerMetaTileEntity(4001,
                new MetaTileEntityCharcoalPileIgniter(gregtechId("charcoal_pile")));
        // 4002
        WORKBENCH = registerMetaTileEntity(4002,
                new MetaTileEntityWorkbench(gregtechId("workbench")));
        // 4003
        CREATIVE_ENERGY = registerMetaTileEntity(4003,
                new MetaTileEntityCreativeEnergy());
        // 4004
        CREATIVE_CHEST = registerMetaTileEntity(4004,
                new MetaTileEntityCreativeChest(gregtechId("creative_chest")));
        // 4005
        CREATIVE_TANK = registerMetaTileEntity(4005,
                new MetaTileEntityCreativeTank(gregtechId("creative_tank")));
        // 4006
        LONG_DIST_ITEM_ENDPOINT = registerMetaTileEntity(4006,
                new MetaTileEntityLDItemEndpoint(gregtechId("ld_item_endpoint")));
        // 4007
        LONG_DIST_FLUID_ENDPOINT = registerMetaTileEntity(4007,
                new MetaTileEntityLDFluidEndpoint(gregtechId("ld_fluid_endpoint")));
        // 4008
        ALARM = registerMetaTileEntity(4008,
                new MetaTileEntityAlarm(gregtechId("alarm")));
        // 4009
        CLIPBOARD_TILE = registerMetaTileEntity(4009,
                new MetaTileEntityClipboard(gregtechId("clipboard")));
        // 4010
        CENTRAL_MONITOR = registerMetaTileEntity(4010,
                new MetaTileEntityCentralMonitor(gregtechId("central_monitor")));
        // 4011
        MONITOR_SCREEN = registerMetaTileEntity(4011,
                new MetaTileEntityMonitorScreen(gregtechId("monitor_screen")));
        // 4012
        PROGRAMMING_PROVIDER = registerMetaTileEntity(4012,
                new MetaTileEntityProgrammingProvider(gregtechId("programming_provider"), GTValues.HV));
    }

    // ======================== 4020-4114: 电力设施 & 实用设备 ========================

    private static void register4020_ElectricAndUtility() {
        // ---- 4020-4033: World Accelerators ----
        if (ConfigHolder.machines.enableWorldAccelerators) {
            for (int i = 0; i < WORLD_ACCELERATOR.length; i++) {
                WORLD_ACCELERATOR[i] = registerMetaTileEntity(4020 + i,
                        new MetaTileEntityWorldAccelerator(
                                gregtechId("world_accelerator." + GTValues.VN[i].toLowerCase()), i + 1));
            }
        }

        // ---- 4035-4048: Teleporters ----
        for (int i = 0; i < TELEPORTER.length; i++) {
            TELEPORTER[i] = registerMetaTileEntity(4035 + i,
                    new MetaTileEntityTeleporter(
                            gregtechId("teleporter." + GTValues.VN[i].toLowerCase()), i + 1));
        }

        // ---- 4050-4064: Chargers ----
        for (int i = 0; i < CHARGER.length; i++) {
            String chargerId = "charger." + GTValues.VN[i].toLowerCase();
            MetaTileEntityCharger charger = new MetaTileEntityCharger(gregtechId(chargerId), i, 4);
            CHARGER[i] = registerMetaTileEntity(4050 + i, charger);
        }

        // ---- 4065-4073: Pumps ----
        for (int i = 0; i < PUMP.length; i++) {
            PUMP[i] = registerMetaTileEntity(4065 + i,
                    new MetaTileEntityPump(gregtechId("pump." + GTValues.VN[i + 1].toLowerCase()), i + 1));
        }

        // ---- 4075-4078: Fishers ----
        for (int i = 0; i < FISHER.length; i++) {
            FISHER[i] = registerMetaTileEntity(4075 + i,
                    new MetaTileEntityFisher(gregtechId("fisher." + GTValues.VN[i + 1].toLowerCase()), i + 1));
        }

        // ---- 4080-4083: Block Breakers ----
        for (int i = 0; i < BLOCK_BREAKER.length; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            BLOCK_BREAKER[i] = new MetaTileEntityBlockBreaker(gregtechId("block_breaker." + voltageName), i + 1);
            registerMetaTileEntity(4080 + i, BLOCK_BREAKER[i]);
        }

        // ---- 4085-4089: Magic Energy Absorbers ----
        for (int i = 0; i < MAGIC_ENERGY_ABSORBER.length; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            MAGIC_ENERGY_ABSORBER[i] = registerMetaTileEntity(4085 + i,
                    new MetaTileEntityMagicEnergyAbsorber(gregtechId("magic_energy_absorber." + voltageName), i + 1));
        }

        // ---- 4090-4094: Item Collectors ----
        for (int i = 0; i < ITEM_COLLECTOR.length; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            ITEM_COLLECTOR[i] = registerMetaTileEntity(4090 + i,
                    new MetaTileEntityItemCollector(gregtechId("item_collector." + voltageName), i + 1,
                            (int) (Math.pow(2, i) * 8)));
        }

        // ---- 4095-4099: Buffers ----
        BUFFER[0] = registerMetaTileEntity(4095, new MetaTileEntityBuffer(gregtechId("buffer.lv"), 1));
        BUFFER[1] = registerMetaTileEntity(4096, new MetaTileEntityBuffer(gregtechId("buffer.mv"), 2));
        BUFFER[2] = registerMetaTileEntity(4097, new MetaTileEntityBuffer(gregtechId("buffer.hv"), 3));
        BUFFER[3] = registerMetaTileEntity(4098, new MetaTileEntityBuffer(gregtechId("buffer.ev"), 4));
        BUFFER[4] = registerMetaTileEntity(4099, new MetaTileEntityBuffer(gregtechId("buffer.iv"), 5));

        // ---- 4100-4104: Tool Casters ----
        for (int i = 0; i < TOOL_CASTER.length; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            TOOL_CASTER[i] = new SimpleMachineMetaTileEntity(gregtechId("tool_caster." + voltageName),
                    RecipeMaps.TOOL_CASTER_RECIPES, Textures.TOOL_CASTER_OVERLAY, i + 1, true);
            registerMetaTileEntity(4100 + i, TOOL_CASTER[i]);
        }

        // ---- 4105-4109: Bath Condensers ----
        for (int i = 0; i < BATH_CONDENSER.length; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            BATH_CONDENSER[i] = new SimpleMachineMetaTileEntity(gregtechId("bath_condenser." + voltageName),
                    RecipeMaps.BATH_CONDENSER_RECIPES, Textures.BATH_CONDENSER_OVERLAY, i + 1, true);
            registerMetaTileEntity(4105 + i, BATH_CONDENSER[i]);
        }
    }

    // ======================== 4125-4244: 船壳 / 变压器 / 二极管 / 电池缓存 ========================

    private static void register4125_HullsTransformersBatteries() {
        // ---- 4125-4139: Hulls ----
        int endPos = GregTechAPI.isHighTier() ? HULL.length : Math.min(HULL.length - 1, GTValues.UV + 2);
        for (int i = 0; i < endPos; i++) {
            HULL[i] = new MetaTileEntityHull(gregtechId("hull." + GTValues.VN[i].toLowerCase()), i);
            registerMetaTileEntity(4125 + i, HULL[i]);
        }

        // ---- 4140-4153: Transformers (1A <-> 4A) ----
        endPos = GregTechAPI.isHighTier() ? TRANSFORMER.length - 1 : Math.min(TRANSFORMER.length - 1, GTValues.UV);
        for (int i = 0; i <= endPos; i++) {
            MetaTileEntityTransformer transformer = new MetaTileEntityTransformer(
                    gregtechId("transformer." + GTValues.VN[i].toLowerCase()), i);
            TRANSFORMER[i] = registerMetaTileEntity(4140 + i, transformer);
        }

        // ---- 4155-4168: Power Transformers (16A <-> 64A, adjustable) ----
        for (int i = 0; i <= endPos; i++) {
            MetaTileEntityTransformer adjustableTransformer = new MetaTileEntityTransformer(
                    gregtechId("transformer.adjustable." + GTValues.VN[i].toLowerCase()), i, 1, 2, 4, 16);
            POWER_TRANSFORMER[i] = registerMetaTileEntity(4155 + i, adjustableTransformer);
        }

        // ---- 4170-4183: Hi-Amp Transformers (2A <-> 8A, 4A <-> 16A) ----
        for (int i = 0; i <= endPos; i++) {
            MetaTileEntityTransformer adjustableTransformer = new MetaTileEntityTransformer(
                    gregtechId("transformer.hi_amp." + GTValues.VN[i].toLowerCase()), i, 2, 4);
            HI_AMP_TRANSFORMER[i] = registerMetaTileEntity(4170 + i, adjustableTransformer);
        }

        // ---- 4185-4199: Diodes ----
        endPos = GregTechAPI.isHighTier() ? DIODES.length - 1 : Math.min(DIODES.length - 1, GTValues.UV + 2);
        for (int i = 0; i < endPos; i++) {
            String diodeId = "diode." + GTValues.VN[i].toLowerCase();
            MetaTileEntityDiode diode = new MetaTileEntityDiode(gregtechId(diodeId), i, 16);
            DIODES[i] = registerMetaTileEntity(4185 + i, diode);
        }

        // ---- 4200-4244: Battery Buffers ----
        endPos = GregTechAPI.isHighTier() ? BATTERY_BUFFER[0].length : GTValues.UHV + 1;
        int[] batteryBufferSlots = new int[] { 4, 8, 16 };
        for (int slot = 0; slot < batteryBufferSlots.length; slot++) {
            BATTERY_BUFFER[slot] = new MetaTileEntityBatteryBuffer[endPos];
            for (int i = 0; i < endPos; i++) {
                String bufferId = "battery_buffer." + GTValues.VN[i].toLowerCase() + "." + batteryBufferSlots[slot];
                MetaTileEntityBatteryBuffer batteryBuffer = new MetaTileEntityBatteryBuffer(gregtechId(bufferId), i,
                        batteryBufferSlots[slot]);
                BATTERY_BUFFER[slot][i] = registerMetaTileEntity(4200 + BATTERY_BUFFER[slot].length * slot + i,
                        batteryBuffer);
            }
        }
    }

    // ======================== 4250-4329: 能源转换器 ========================

    private static void register4250_EnergyConverters() {
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
                ENERGY_CONVERTER[j][i] = registerMetaTileEntity(4250 + j + i * 4, converter);
            }
        }
    }

    // ======================== 4330-4339: 一次性电池 ========================

    private static void register4330_BatteryBlocks() {
        ZINC_MANGANESE_CELL = registerMetaTileEntity(4330,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("zinc_manganese_cell"), DisposableBatteryType.ZINC_MANGANESE));
        LITHIUM_MANGANESE_CELL = registerMetaTileEntity(4331,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("lithium_manganese_cell"), DisposableBatteryType.LITHIUM_MANGANESE));
        NICKEL_CADMIUM_CELL = registerMetaTileEntity(4332,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("nickel_cadmium_cell"), DisposableBatteryType.NICKEL_CADMIUM));
        LEAD_ACID_BATTERY = registerMetaTileEntity(4333,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("lead_acid_battery"), DisposableBatteryType.LEAD_ACID));
        VANADIUM_FLOW_CELL = registerMetaTileEntity(4334,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("vanadium_flow_cell"), DisposableBatteryType.VANADIUM_FLOW));
        LFP_BATTERY = registerMetaTileEntity(4335,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("lfp_battery"), DisposableBatteryType.LFP));
        LCO_BATTERY = registerMetaTileEntity(4336,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("lco_battery"), DisposableBatteryType.LCO));
        NMC_BATTERY = registerMetaTileEntity(4337,
                new MetaTileEntityDisposableBatteryBase(
                        gregtechId("nmc_battery"), DisposableBatteryType.NMC));
    }

    // ======================== 4340-4465: 存储 ========================

    private static void register4340_Storage() {
        // ---- 4340-4342: Quantum Storage Network ----
        QUANTUM_STORAGE_CONTROLLER = registerMetaTileEntity(4340,
                new MetaTileEntityQuantumStorageController(gregtechId("quantum_storage_controller")));
        QUANTUM_STORAGE_PROXY = registerMetaTileEntity(4341,
                new MetaTileEntityQuantumProxy(gregtechId("quantum_storage_proxy")));
        QUANTUM_STORAGE_EXTENDER = registerMetaTileEntity(4342,
                new MetaTileEntityQuantumExtender(gregtechId("quantum_storage_extender")));

        // ---- 4350-4359: Quantum Chests ----
        for (int i = 0; i < 5; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            QUANTUM_CHEST[i] = new MetaTileEntityQuantumChest(gregtechId("super_chest." + voltageName), i + 1,
                    4000000L * (int) Math.pow(2, i));
            registerMetaTileEntity(4350 + i, QUANTUM_CHEST[i]);
        }
        for (int i = 5; i < QUANTUM_CHEST.length; i++) {
            String voltageName = GTValues.VN[i].toLowerCase();
            long capacity = i == GTValues.UHV ? Integer.MAX_VALUE : 4000000L * (int) Math.pow(2, i);
            QUANTUM_CHEST[i] = new MetaTileEntityQuantumChest(gregtechId("quantum_chest." + voltageName), i, capacity);
            registerMetaTileEntity(4350 + i, QUANTUM_CHEST[i]);
        }

        // ---- 4360-4369: Quantum Tanks ----
        for (int i = 0; i < 5; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            QUANTUM_TANK[i] = new MetaTileEntityQuantumTank(gregtechId("super_tank." + voltageName), i + 1,
                    4000000 * (int) Math.pow(2, i));
            registerMetaTileEntity(4360 + i, QUANTUM_TANK[i]);
        }
        for (int i = 5; i < QUANTUM_TANK.length; i++) {
            String voltageName = GTValues.VN[i].toLowerCase();
            int capacity = i == GTValues.UHV ? Integer.MAX_VALUE : 4000000 * (int) Math.pow(2, i);
            QUANTUM_TANK[i] = new MetaTileEntityQuantumTank(gregtechId("quantum_tank." + voltageName), i, capacity);
            registerMetaTileEntity(4360 + i, QUANTUM_TANK[i]);
        }

        // ---- 4370-4379: Multi Quantum Tanks ----
        for (int i = 0; i < 5; i++) {
            String voltageName = GTValues.VN[i + 1].toLowerCase();
            MULTI_QUANTUM_TANK[i] = new MetaTileEntityQuantumMultiTank(gregtechId("multi_super_tank." + voltageName),
                    i + 1, 4000000 * (int) Math.pow(2, i));
            registerMetaTileEntity(4370 + i, MULTI_QUANTUM_TANK[i]);
        }
        for (int i = 5; i < MULTI_QUANTUM_TANK.length; i++) {
            String voltageName = GTValues.VN[i].toLowerCase();
            int capacity = i == GTValues.UHV ? Integer.MAX_VALUE : 4000000 * (int) Math.pow(2, i);
            MULTI_QUANTUM_TANK[i] = new MetaTileEntityQuantumMultiTank(gregtechId("multi_quantum_tank." + voltageName),
                    i, capacity);
            registerMetaTileEntity(4370 + i, MULTI_QUANTUM_TANK[i]);
        }

        // ---- 4380-4384: Huge Buffers ----
        HUGE_BUFFER[0] = registerMetaTileEntity(4380, new MetaTileEntityHugeBuffer(gregtechId("huge_buffer.lv"), 1));
        HUGE_BUFFER[1] = registerMetaTileEntity(4381, new MetaTileEntityHugeBuffer(gregtechId("huge_buffer.mv"), 2));
        HUGE_BUFFER[2] = registerMetaTileEntity(4382, new MetaTileEntityHugeBuffer(gregtechId("huge_buffer.hv"), 3));
        HUGE_BUFFER[3] = registerMetaTileEntity(4383, new MetaTileEntityHugeBuffer(gregtechId("huge_buffer.ev"), 4));
        HUGE_BUFFER[4] = registerMetaTileEntity(4384, new MetaTileEntityHugeBuffer(gregtechId("huge_buffer.iv"), 5));

        // ---- 4430-4447: Drums ----
        WOODEN_DRUM = registerMetaTileEntity(4430,
                new MetaTileEntityDrum(gregtechId("drum.wood"), Materials.Wood, 16000));
        BRONZE_DRUM = registerMetaTileEntity(4431,
                new MetaTileEntityDrum(gregtechId("drum.bronze"), Materials.Bronze, 24000));
        GOLD_DRUM = registerMetaTileEntity(4432,
                new MetaTileEntityDrum(gregtechId("drum.gold"), Materials.Gold, 32000));
        COPPER_DRUM = registerMetaTileEntity(4433,
                new MetaTileEntityDrum(gregtechId("drum.copper"), Materials.Copper, 40000));
        IRON_DRUM = registerMetaTileEntity(4434,
                new MetaTileEntityDrum(gregtechId("drum.iron"), Materials.Iron, 48000));
        LEAD_DRUM = registerMetaTileEntity(4435,
                new MetaTileEntityDrum(gregtechId("drum.lead"), Materials.Lead, 56000));
        STEEL_DRUM = registerMetaTileEntity(4436,
                new MetaTileEntityDrum(gregtechId("drum.steel"), Materials.Steel, 64000));
        CHROME_DRUM = registerMetaTileEntity(4437,
                new MetaTileEntityDrum(gregtechId("drum.chrome"), Materials.Chrome, 96000));
        ALUMINIUM_DRUM = registerMetaTileEntity(4438,
                new MetaTileEntityDrum(gregtechId("drum.aluminium"), Materials.Aluminium, 128000));
        STAINLESS_STEEL_DRUM = registerMetaTileEntity(4439,
                new MetaTileEntityDrum(gregtechId("drum.stainless_steel"), Materials.StainlessSteel, 256000));
        TITANIUM_DRUM = registerMetaTileEntity(4440,
                new MetaTileEntityDrum(gregtechId("drum.titanium"), Materials.Titanium, 512000));
        TUNGSTEN_DRUM = registerMetaTileEntity(4441,
                new MetaTileEntityDrum(gregtechId("drum.tungsten"), Materials.Tungsten, 768000));
        TUNGSTENSTEEL_DRUM = registerMetaTileEntity(4442,
                new MetaTileEntityDrum(gregtechId("drum.tungstensteel"), Materials.TungstenSteel, 1024000));
        IRIDIUM_DRUM = registerMetaTileEntity(4443,
                new MetaTileEntityDrum(gregtechId("drum.iridium"), Materials.Iridium, 1536000));
        RHODIUM_PLATED_PALLADIUM_DRUM = registerMetaTileEntity(4444,
                new MetaTileEntityDrum(gregtechId("drum.rhodium_plated_palladium"), Materials.RhodiumPlatedPalladium,
                        2048000));
        NAQUADAH_ALLOY_DRUM = registerMetaTileEntity(4445,
                new MetaTileEntityDrum(gregtechId("drum.naquadah_alloy"), Materials.NaquadahAlloy, 4096000));
        DARMSTADTIUM_DRUM = registerMetaTileEntity(4446,
                new MetaTileEntityDrum(gregtechId("drum.darmstadtium"), Materials.Darmstadtium, 8192000));
        NEUTRONIUM_DRUM = registerMetaTileEntity(4447,
                new MetaTileEntityDrum(gregtechId("drum.neutronium"), Materials.Neutronium, 16384000));

        // ---- 4450-4465: Crates ----
        WOODEN_CRATE = registerMetaTileEntity(4450,
                new MetaTileEntityCrate(gregtechId("crate.wood"), Materials.Wood, 27, 9));
        COPPER_CRATE = registerMetaTileEntity(4451,
                new MetaTileEntityCrate(gregtechId("crate.copper"), Materials.Copper, 36, 9));
        IRON_CRATE = registerMetaTileEntity(4452,
                new MetaTileEntityCrate(gregtechId("crate.iron"), Materials.Iron, 45, 9));
        BRONZE_CRATE = registerMetaTileEntity(4453,
                new MetaTileEntityCrate(gregtechId("crate.bronze"), Materials.Bronze, 54, 9));
        SILVER_CRATE = registerMetaTileEntity(4454,
                new MetaTileEntityCrate(gregtechId("crate.silver"), Materials.Silver, 63, 9));
        STEEL_CRATE = registerMetaTileEntity(4455,
                new MetaTileEntityCrate(gregtechId("crate.steel"), Materials.Steel, 72, 9));
        GOLD_CRATE = registerMetaTileEntity(4456,
                new MetaTileEntityCrate(gregtechId("crate.gold"), Materials.Gold, 81, 9));
        ALUMINIUM_CRATE = registerMetaTileEntity(4457,
                new MetaTileEntityCrate(gregtechId("crate.aluminium"), Materials.Aluminium, 90, 10));
        DIAMOND_CRATE = registerMetaTileEntity(4458,
                new MetaTileEntityCrate(gregtechId("crate.diamond"), Materials.Diamond, 100, 10));
        STAINLESS_STEEL_CRATE = registerMetaTileEntity(4459,
                new MetaTileEntityCrate(gregtechId("crate.stainless_steel"), Materials.StainlessSteel, 108, 12));
        TITANIUM_CRATE = registerMetaTileEntity(4460,
                new MetaTileEntityCrate(gregtechId("crate.titanium"), Materials.Titanium, 126, 14));
        TUNGSTENSTEEL_CRATE = registerMetaTileEntity(4461,
                new MetaTileEntityCrate(gregtechId("crate.tungstensteel"), Materials.TungstenSteel, 144, 16));
        RHODIUM_PLATED_PALLADIUM_CRATE = registerMetaTileEntity(4462,
                new MetaTileEntityCrate(gregtechId("crate.rhodium_plated_palladium"), Materials.RhodiumPlatedPalladium,
                        162, 18));
        NAQUADAH_ALLOY_CRATE = registerMetaTileEntity(4463,
                new MetaTileEntityCrate(gregtechId("crate.naquadah_alloy"), Materials.NaquadahAlloy, 180, 20));
        DARMSTADTIUM_CRATE = registerMetaTileEntity(4464,
                new MetaTileEntityCrate(gregtechId("crate.darmstadtium"), Materials.Darmstadtium, 198, 22));
        NEUTRONIUM_CRATE = registerMetaTileEntity(4465,
                new MetaTileEntityCrate(gregtechId("crate.neutronium"), Materials.Neutronium, 216, 24));
    }
}
