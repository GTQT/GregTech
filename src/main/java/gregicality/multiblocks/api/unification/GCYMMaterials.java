package gregicality.multiblocks.api.unification;

import gregtech.api.unification.material.Material;
import gregtech.api.util.GTLog;

public final class GCYMMaterials {

    /*
     * First Degree Materials 3000-3019
     */
    public static Material Stellite;
    public static Material WatertightSteel;
    public static Material MaragingSteel250;
    public static Material MaragingSteel300;
    public static Material MaragingSteel350;
    public static Material HastelloyC276;
    public static Material HastelloyX;
    public static Material HastelloyN;
    public static Material HastelloyW;
    public static Material Trinaquadalloy;
    public static Material Zeron100;
    public static Material TitaniumCarbide;
    public static Material TantalumCarbide;
    public static Material MolybdenumDisilicide;

    /*
     * Second Degree Materials 3020-3039
     */
    public static Material HSLASteel;
    public static Material TitaniumTungstenCarbide;
    public static Material IncoloyMA956;

    private GCYMMaterials() {
    }

    /*
     * High Degree Materials 3040-3059
     */

    /*
     * Gregicality: 3000-19999
     */
    public static void init() {
        // First Degree 0-
        GTLog.logger.info("GCYM: Registering First Degree Materials");
        GCYMFirstDegreeMaterials.init();

        // Second Degree 100-
        GCYMSecondDegreeMaterials.init();

        // Flags
        GCYMMaterialFlagAddition.init();
    }
}
