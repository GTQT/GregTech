package gregtech.integration.chisel.loaders;

import gregtech.api.GTValues;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.metatileentities.electric.MetaTileEntityAutoChisel;

import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.common.metatileentities.MetaTileEntities.registerMetaTileEntities;

public class ChiselMachineRegistration {

    public static MetaTileEntityAutoChisel[] AUTO_CHISEL = new MetaTileEntityAutoChisel[GTValues.V.length - 1];

    public static void register() {
        // Auto Chisel IDs 4550-4565
        registerMetaTileEntities(AUTO_CHISEL, 4550, "auto_chisel",
                (tier, voltageName) -> new MetaTileEntityAutoChisel(
                        gregtechId(String.format("auto_chisel.%s", voltageName)),
                        RecipeMaps.AUTO_CHISEL_RECIPES,
                        Textures.AUTO_CHISEL_OVERLAY,
                        tier,
                        true,
                        GTUtility.defaultTankSizeFunction));
    }
}
