package gregtech.common.materials;

import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.FissionFuelProperty;
import gregtech.api.unification.material.properties.SCPropertyKey;

import static gregtech.api.unification.material.Materials.*;
import static gregtech.api.unification.material.info.MaterialFlags.DISABLE_DECOMPOSITION;
import static gregtech.api.unification.material.info.MaterialIconSet.METALLIC;
import static gregtech.api.unification.material.info.MaterialIconSet.SHINY;
import static gregtech.api.unification.material.properties.PropertyKey.FISSION_FUEL;
import static gregtech.api.unification.material.SCMaterials.*;
import static gregtech.api.util.SCUtility.scId;

/*
 * Ranges 25000-25499
 */
public class SecondDegreeMaterials {
    private static int startId = 25000;

    public static int getStartID() {
        return startId++;
    }

    public static void register() {
        LEU235 = new Material.Builder(getStartID(), scId("leu_235"))
                .dust(3)
                .color(0x232323).iconSet(METALLIC)
                .flags(DISABLE_DECOMPOSITION)
                .components(HighEnrichedUraniumDioxide, 1, DepletedUraniumDioxide, 19)
                .build()
                .setFormula("UO2", true);

        LEU235.setProperty(FISSION_FUEL,
                FissionFuelProperty.builder(LEU235.getRegistryName(), 1500, 75000, 3.5)
                        .fastNeutronCaptureCrossSection(0.4)
                        .slowNeutronCaptureCrossSection(1.8)
                        .slowNeutronFissionCrossSection(1.8)
                        .requiredNeutrons(1)
                        .releasedNeutrons(2.5)
                        .releasedHeatEnergy(0.025)
                        .decayRate(0.025)
                        .build());

        HEU235 = new Material.Builder(getStartID(), scId("heu_235"))
                .dust(3)
                .color(0x424845).iconSet(METALLIC)
                .flags(DISABLE_DECOMPOSITION)
                .components(HighEnrichedUraniumDioxide, 1, DepletedUraniumDioxide, 4)
                .build()
                .setFormula("UO2", true);

        HEU235.setProperty(FISSION_FUEL,
                FissionFuelProperty.builder(HEU235.getRegistryName(), 1800, 60000, 2.5)
                        .fastNeutronCaptureCrossSection(0.3)
                        .slowNeutronCaptureCrossSection(2.2)
                        .slowNeutronFissionCrossSection(2.2)
                        .requiredNeutrons(1)
                        .releasedNeutrons(2.5)
                        .releasedHeatEnergy(0.025)
                        .decayRate(0.05)
                        .build());

        LowGradeMOX = new Material.Builder(getStartID(), scId("low_grade_mox"))
                .dust(3)
                .color(0x62C032).iconSet(METALLIC)
                .flags(DISABLE_DECOMPOSITION)
                .components(FissilePlutoniumDioxide, 1, Uraninite, 19)
                .build()
                .setFormula("(U,Pu)O2", true);

        LowGradeMOX.setProperty(FISSION_FUEL,
                FissionFuelProperty.builder(LowGradeMOX.getRegistryName(), 1600, 50000, 1.5)
                        .fastNeutronCaptureCrossSection(0.5)
                        .slowNeutronCaptureCrossSection(2)
                        .slowNeutronFissionCrossSection(2)
                        .requiredNeutrons(1)
                        .releasedNeutrons(2.60)
                        .releasedHeatEnergy(0.052)
                        .decayRate(0.1)
                        .build());

        HighGradeMOX = new Material.Builder(getStartID(), scId("high_grade_mox"))
                .dust(3)
                .color(0x7EA432).iconSet(METALLIC)
                .flags(DISABLE_DECOMPOSITION)
                .components(FissilePlutoniumDioxide, 1, Uraninite, 4)
                .build()
                .setFormula("(U,Pu)O2", true);


        HighGradeMOX.setProperty(FISSION_FUEL,
                FissionFuelProperty.builder(HighGradeMOX.getRegistryName(), 2000, 80000, 1)
                        .fastNeutronCaptureCrossSection(0.5)
                        .slowNeutronCaptureCrossSection(2.4)
                        .slowNeutronFissionCrossSection(2.4)
                        .requiredNeutrons(1)
                        .releasedNeutrons(2.80)
                        .releasedHeatEnergy(0.056)
                        .decayRate(0.2)
                        .build());

        // 钚燃料体系
        FBR = new Material.Builder(getStartID(), scId("fbr"))
                .dust(4)  // 提高处理等级
                .color(0x8A795D).iconSet(SHINY)
                .flags(DISABLE_DECOMPOSITION)
                .components(Plutonium, 1, Oxygen, 2)
                .build()
                .setFormula("PuO2", true);

        FBR.setProperty(FISSION_FUEL,
                FissionFuelProperty.builder(FBR.getRegistryName(), 2200, 90000, 0.8)
                        .fastNeutronCaptureCrossSection(0.5)
                        .slowNeutronCaptureCrossSection(1.8)
                        .slowNeutronFissionCrossSection(2.8)
                        .requiredNeutrons(1)
                        .releasedNeutrons(3.0)
                        .releasedHeatEnergy(0.060)
                        .decayRate(0.25)
                        .build());

        // 钍燃料体系
        THOR = new Material.Builder(getStartID(), scId("thor"))
                .dust(3)
                .color(0x5F4B32).iconSet(METALLIC)
                .flags(DISABLE_DECOMPOSITION)
                .components(Thorium, 1, Oxygen, 2)
                .build()
                .setFormula("ThO2", true);

        THOR.setProperty(FISSION_FUEL,
                FissionFuelProperty.builder(THOR.getRegistryName(), 1800, 100000, 2.0)
                        .fastNeutronCaptureCrossSection(0.5)
                        .slowNeutronCaptureCrossSection(3.2)
                        .slowNeutronFissionCrossSection(0.8)
                        .requiredNeutrons(1)
                        .releasedNeutrons(2.2)
                        .releasedHeatEnergy(0.060)
                        .decayRate(0.12)
                        .build());
    }
}
