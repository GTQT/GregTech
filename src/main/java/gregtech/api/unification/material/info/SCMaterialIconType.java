package gregtech.api.unification.material.info;

import gregtech.api.unification.material.info.MaterialIconType;

/**
 * Copyright (C) SymmetricDevs 2025
 * 由 MeowmelMuku 于 2025 修改。
 * 修改内容：添加靶丸。
 * 此文件遵循 GPL-3.0 许可证，详情请见项目根目录的 LICENSE 文件。
 */
public class SCMaterialIconType {

    // Nuclear MaterialIcontypes
    public static final MaterialIconType fuelRod = new MaterialIconType("fuelRod");
    public static final MaterialIconType fuelRodDepleted = new MaterialIconType("fuelRodDepleted");
    public static final MaterialIconType fuelRodHotDepleted = new MaterialIconType("fuelRodHotDepleted");
    public static final MaterialIconType fuelPelletRaw = new MaterialIconType("fuelPelletRaw");
    public static final MaterialIconType fuelPellet = new MaterialIconType("fuelPellet");
    public static final MaterialIconType fuelPelletDepleted = new MaterialIconType("fuelPelletDepleted");

    public static final MaterialIconType dustSpentFuel = new MaterialIconType("dustSpentFuel");
    public static final MaterialIconType dustBredFuel = new MaterialIconType("dustBredFuel");
    public static final MaterialIconType dustFissionByproduct = new MaterialIconType("dustFissionByproduct");

    //Additions Nuclear stuff, introduced by MeowmelMuku
    public static final MaterialIconType fuelPebble = new MaterialIconType("fuelPebble");
    public static final MaterialIconType fuelPebbleDepleted = new MaterialIconType("fuelPebbleDepleted");
}
