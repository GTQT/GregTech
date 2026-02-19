package gregtech.mixins.gtmt;

import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.integration.gtmt.ChiselRecipeMaps;

import com.github.gtexpert.gtmt.integration.chisel.metatileentities.ChiselMetaTileEntities;
import com.github.gtexpert.gtmt.integration.chisel.metatileentities.MetaTileEntityAutoChisel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import static com.github.gtexpert.gtmt.integration.chisel.metatileentities.ChiselMetaTileEntities.AUTO_CHISEL;
import static gregtech.api.util.GTUtility.gregtechId;
import static gregtech.common.metatileentities.MetaTileEntities.registerMetaTileEntities;

@Mixin(ChiselMetaTileEntities.class)
public class MixinChiselMetaTileEntities {

    /**
     * @author MeowmelMuku
     * @reason 修复与ceu290的兼容性
     */
    @Overwrite(remap = false)
    public static void init() {
        // Auto Chisel 29000
        registerMetaTileEntities(AUTO_CHISEL, 29000, "auto_chisel",
                (tier, voltageName) -> new MetaTileEntityAutoChisel(
                        gregtechId(String.format("%s.%s", "auto_chisel", voltageName)),
                        ChiselRecipeMaps.AUTO_CHISEL_RECIPES,
                        Textures.AUTOCLAVE_OVERLAY,
                        tier, true, GTUtility.defaultTankSizeFunction));
    }

}
