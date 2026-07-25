package gregtech.api.unification.material;

import gregtech.api.unification.material.Material;
import gregtech.common.materials.ElementMaterials;
import gregtech.common.materials.FirstDegreeMaterials;
import gregtech.common.materials.SecondDegreeMaterials;
import gregtech.common.materials.UnknownCompositionMaterials;

/**
 * Copyright (C) SymmetricDevs 2025
 * 由 MeowmelMuku 于 2025 修改。
 * 修改内容：添加靶丸。
 * 此文件遵循 GPL-3.0 许可证，详情请见项目根目录的 LICENSE 文件。
 */
public class SCMaterials {
    public static Material Radium225;
    public static Material Radium226;

    public static Material Protactinium231;
    public static Material Protactinium233;

    public static Material Thorium228;
    public static Material Thorium229;
    public static Material Thorium230;
    public static Material Thorium232;
    public static Material Thorium233;

    /* public static Material Neptunium = Materials.Neptunium; */
    public static Material Neptunium235;
    public static Material Neptunium236;
    public static Material Neptunium237;
    public static Material Neptunium238;
    public static Material Neptunium239;

    public static Material Plutonium236;
    public static Material Plutonium237;
    public static Material Plutonium238;
    /* public static Material Plutonium239 = Materials.Plutonium239; */
    public static Material Plutonium240;
    /* public static Material Plutonium241 = Materials.Plutonium241; */
    public static Material Plutonium242;
    public static Material Plutonium243;
    public static Material Plutonium244;

    public static Material Uranium232;
    public static Material Uranium233;
    public static Material Uranium234;
    /* public static Material Uranium235 = Materials.Uranium235; */
    public static Material Uranium236;
    public static Material Uranium237;
    /* public static Material Uranium238 = Materials.Uranium238; */
    public static Material Uranium239;

    public static Material Americium240;
    public static Material Americium241;
    public static Material Americium242;
    public static Material Americium243;

    public static Material Curium242;
    public static Material Curium243;
    public static Material Curium244;
    public static Material Curium245;
    public static Material Curium246;
    public static Material Curium247;
    public static Material Curium248;
    public static Material Curium250;

    public static Material Berkelium249;

    public static Material Californium249;
    public static Material Californium252;



    public static Material HighEnrichedUraniumHexafluoride;
    /* public static Material LowEnrichedUraniumHexafluoride = Materials.EnrichedUraniumHexafluoride; */

    public static Material LowEnrichedUraniumDioxide;

    public static Material HighEnrichedUraniumDioxide;
    public static Material DepletedUraniumDioxide;
    public static Material FissilePlutoniumDioxide;
    public static Material Inconel;
    public static Material BoronTrioxide;
    public static Material BoronCarbide;
    public static Material HeavyWater;
    public static Material HighPressureHeavyWater;

    public static Material Corium;
    public static Material SpentUraniumFuelSolution;
    public static Material RadonRichGasMixture;

    public static Material LEU235;
    public static Material HEU235;
    public static Material LowGradeMOX;
    public static Material HighGradeMOX;
    public static Material FBR;
    public static Material THOR;

    public static void register() {
        /*
         * Ranges 1-499
         */
        ElementMaterials.register();

        /*
         * Ranges 500-999
         */
        FirstDegreeMaterials.register();

        /*
         * Ranges 1000-1499
         */
        SecondDegreeMaterials.register();

        /*
         * Ranges 1500-1999
         */
        UnknownCompositionMaterials.register();
    }
}
