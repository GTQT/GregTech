package gregtech.common.metatileentities;

import gregtech.common.metatileentities.multiblock.generator.MetaTileEntitySteamEngine;
import gregtech.common.metatileentities.multiblock.standard.*;
import gregtech.common.metatileentities.multiblockpart.MetaTileEntityParallelHatch;
import gregtech.common.metatileentities.multiblockpart.MetaTileEntityTieredHatch;
import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;

import static gregtech.api.util.GCYMUtil.gcymId;
import static gregtech.api.GTValues.MAX;
import static gregtech.common.metatileentities.MetaTileEntities.registerMetaTileEntity;

public final class GCYMMetaTileEntities {

    public static MetaTileEntityLargeMacerator LARGE_MACERATOR;
    public static MetaTileEntityAlloyBlastSmelter ALLOY_BLAST_SMELTER;
    public static MetaTileEntityLargeArcFurnace LARGE_ARC_FURNACE;
    public static MetaTileEntityLargeAssembler LARGE_ASSEMBLER;
    public static MetaTileEntityLargeAutoclave LARGE_AUTOCLAVE;
    public static MetaTileEntityLargeBender LARGE_BENDER;
    public static MetaTileEntityLargeBrewery LARGE_BREWERY;
    public static MetaTileEntityLargeCentrifuge LARGE_CENTRIFUGE;
    public static MetaTileEntityLargeChemicalBath LARGE_CHEMICAL_BATH;
    public static MetaTileEntityLargeExtractor LARGE_EXTRACTOR;
    public static MetaTileEntityLargeCutter LARGE_CUTTER;
    public static MetaTileEntityLargeDistillery LARGE_DISTILLERY;
    public static MetaTileEntityLargeElectrolyzer LARGE_ELECTROLYZER;
    public static MetaTileEntityLargePolarizer LARGE_POLARIZER;
    public static MetaTileEntityLargeExtruder LARGE_EXTRUDER;
    public static MetaTileEntityLargeSolidifier LARGE_SOLIDIFIER;
    public static MetaTileEntityLargeMixer LARGE_MIXER;
    public static MetaTileEntityLargePackager LARGE_PACKAGER;
    public static MetaTileEntityLargeEngraver LARGE_ENGRAVER;
    public static MetaTileEntityLargeSifter LARGE_SIFTER;
    public static MetaTileEntityLargeWiremill LARGE_WIREMILL;
    public static MetaTileEntityElectricImplosionCompressor ELECTRIC_IMPLOSION_COMPRESSOR;
    public static MetaTileEntityLargeMassFabricator LARGE_MASS_FABRICATOR;
    public static MetaTileEntityLargeReplicator LARGE_REPLICATOR;
    public static MetaTileEntityLargeCircuitAssembler LARGE_CIRCUIT_ASSEMBLER;
    public static MetaTileEntityLargeChemicalComplex LARGE_CHEMICAL_COMPLEX;
    public static MetaTileEntityLargePyrolyser LARGE_PYROLYSER;
    public static MetaTileEntityLargeThermalCentrifuge LARGE_THERMAL_CENTRIFUGE;
    public static MetaTileEntityLargeSonicator LARGE_SONICATOR;
    public static MetaTileEntityLargeDesulfurization LARGE_DESULFURIZER;
    public static MetaTileEntityLargePolymerization LARGE_POLYMERIZATION;
    public static MetaTileEntityLargeRockBreaker LARGE_ROCK_BREAKER;
    public static MetaTileEntityLargeGasCollector LARGE_GAS_COLLECTOR;

    public static MetaTileEntityMegaBlastFurnace MEGA_BLAST_FURNACE;
    public static MetaTileEntityMegaVacuumFreezer MEGA_VACUUM_FREEZER;
    public static MetaTileEntityMegaAlloyBlastSmelter MEGA_ALLOY_BLAST_SMELTER;
    public static MetaTileEntityMegaChemicalReactor MEGA_CHEMICAL_REACTOR;
    public static MetaTileEntityMegaCrackingUnit MEGA_CRACKING_UNIT;
    public static MetaTileEntitySteamEngine STEAM_ENGINE;


    public static MetaTileEntityParallelHatch[] PARALLEL_HATCH = new MetaTileEntityParallelHatch[GTValues.V.length - 1];
    public static MetaTileEntityTieredHatch[] TIERED_HATCH = new MetaTileEntityTieredHatch[GTValues.V.length];
    public static MetaTileEntityParallelHatch CREATIVE_PARALLEL_HATCH;

    private GCYMMetaTileEntities() {
    }

    public static void init() {
        // Multiblocks
        LARGE_MACERATOR = registerMetaTileEntity(1, new MetaTileEntityLargeMacerator(gcymId("large_macerator")));
        ALLOY_BLAST_SMELTER = registerMetaTileEntity(2, new MetaTileEntityAlloyBlastSmelter(gcymId("alloy_blast_smelter")));
        LARGE_ARC_FURNACE = registerMetaTileEntity(3, new MetaTileEntityLargeArcFurnace(gcymId("large_arc_furnace")));
        LARGE_ASSEMBLER = registerMetaTileEntity(4, new MetaTileEntityLargeAssembler(gcymId("large_assembler")));
        LARGE_AUTOCLAVE = registerMetaTileEntity(5, new MetaTileEntityLargeAutoclave(gcymId("large_autoclave")));
        LARGE_BENDER = registerMetaTileEntity(6, new MetaTileEntityLargeBender(gcymId("large_bender")));
        LARGE_BREWERY = registerMetaTileEntity(7, new MetaTileEntityLargeBrewery(gcymId("large_brewer")));
        LARGE_CENTRIFUGE = registerMetaTileEntity(8, new MetaTileEntityLargeCentrifuge(gcymId("large_centrifuge")));
        LARGE_CHEMICAL_BATH = registerMetaTileEntity(9, new MetaTileEntityLargeChemicalBath(gcymId("large_chemical_bath")));
        LARGE_EXTRACTOR = registerMetaTileEntity(10, new MetaTileEntityLargeExtractor(gcymId("large_extractor")));
        LARGE_CUTTER = registerMetaTileEntity(11, new MetaTileEntityLargeCutter(gcymId("large_cutter")));
        LARGE_DISTILLERY = registerMetaTileEntity(12, new MetaTileEntityLargeDistillery(gcymId("large_distillery")));
        LARGE_ELECTROLYZER = registerMetaTileEntity(13, new MetaTileEntityLargeElectrolyzer(gcymId("large_electrolyzer")));
        LARGE_POLARIZER = registerMetaTileEntity(14, new MetaTileEntityLargePolarizer(gcymId("large_polarizer")));
        LARGE_EXTRUDER = registerMetaTileEntity(15, new MetaTileEntityLargeExtruder(gcymId("large_extruder")));
        LARGE_SOLIDIFIER = registerMetaTileEntity(16, new MetaTileEntityLargeSolidifier(gcymId("large_solidifier")));
        LARGE_MIXER = registerMetaTileEntity(17, new MetaTileEntityLargeMixer(gcymId("large_mixer")));
        LARGE_PACKAGER = registerMetaTileEntity(18, new MetaTileEntityLargePackager(gcymId("large_packager")));
        LARGE_ENGRAVER = registerMetaTileEntity(19, new MetaTileEntityLargeEngraver(gcymId("large_engraver")));
        LARGE_SIFTER = registerMetaTileEntity(20, new MetaTileEntityLargeSifter(gcymId("large_sifter")));
        LARGE_WIREMILL = registerMetaTileEntity(21, new MetaTileEntityLargeWiremill(gcymId("large_wiremill")));
        ELECTRIC_IMPLOSION_COMPRESSOR = registerMetaTileEntity(22, new MetaTileEntityElectricImplosionCompressor(gcymId("electric_implosion_compressor")));
        LARGE_MASS_FABRICATOR = registerMetaTileEntity(23, new MetaTileEntityLargeMassFabricator(gcymId("large_mass_fabricator")));
        LARGE_REPLICATOR = registerMetaTileEntity(24, new MetaTileEntityLargeReplicator(gcymId("large_replicator")));
        LARGE_CIRCUIT_ASSEMBLER = registerMetaTileEntity(25, new MetaTileEntityLargeCircuitAssembler(gcymId("large_circuit_assembler")));
        LARGE_CHEMICAL_COMPLEX = registerMetaTileEntity(26, new MetaTileEntityLargeChemicalComplex(gcymId("large_chemical_complex")));
        LARGE_PYROLYSER = registerMetaTileEntity(27, new MetaTileEntityLargePyrolyser(gcymId("large_pyrolyser")));
        LARGE_THERMAL_CENTRIFUGE = registerMetaTileEntity(28, new MetaTileEntityLargeThermalCentrifuge(gcymId("large_thermal_centrifuge")));
        LARGE_SONICATOR = registerMetaTileEntity(29, new MetaTileEntityLargeSonicator(gcymId("large_sonicator")));
        LARGE_DESULFURIZER = registerMetaTileEntity(30, new MetaTileEntityLargeDesulfurization(gcymId("large_desulfurizer")));
        LARGE_POLYMERIZATION = registerMetaTileEntity(31, new MetaTileEntityLargePolymerization(gcymId("large_polymerization")));
        LARGE_ROCK_BREAKER = registerMetaTileEntity(32, new MetaTileEntityLargeRockBreaker(gcymId("large_rock_breaker")));
        LARGE_GAS_COLLECTOR = registerMetaTileEntity(33, new MetaTileEntityLargeGasCollector(gcymId("large_gas_collector")));

        //巨型设备
        MEGA_BLAST_FURNACE = registerMetaTileEntity(40, new MetaTileEntityMegaBlastFurnace(gcymId("mega_blast_furnace")));
        MEGA_VACUUM_FREEZER = registerMetaTileEntity(41, new MetaTileEntityMegaVacuumFreezer(gcymId("mega_vacuum_freezer")));
        MEGA_ALLOY_BLAST_SMELTER = registerMetaTileEntity(42, new MetaTileEntityMegaAlloyBlastSmelter(gcymId("mega_alloy_blast_smelter")));
        MEGA_CHEMICAL_REACTOR = registerMetaTileEntity(43, new MetaTileEntityMegaChemicalReactor(gcymId("mega_chemical_reactor")));
        MEGA_CRACKING_UNIT = registerMetaTileEntity(44, new MetaTileEntityMegaCrackingUnit(gcymId("mega_cracking_unit")));
        //发电机
        STEAM_ENGINE = registerMetaTileEntity(50, new MetaTileEntitySteamEngine(gcymId("steam_engine")));


        for (int i = 0; i < PARALLEL_HATCH.length; i++) {
            int tier = i + 1;
            PARALLEL_HATCH[i] = registerMetaTileEntity(60 + i, new MetaTileEntityParallelHatch(
                    gcymId(String.format("parallel_hatch.%s", GTValues.VN[tier])), tier));
        }

        for (int i = 0; i < TIERED_HATCH.length; i++) {
            if (!GregTechAPI.isHighTier() && i > GTValues.UHV)
                break;
            TIERED_HATCH[i] = registerMetaTileEntity(75 + i,
                    new MetaTileEntityTieredHatch(gcymId(String.format("tiered_hatch.%s", GTValues.VN[i])), i));
        }

        // 创造并行仓
        CREATIVE_PARALLEL_HATCH = registerMetaTileEntity(100, new MetaTileEntityParallelHatch(gcymId("creative_parallel_hatch"), MAX, Integer.MAX_VALUE));
    }
}
