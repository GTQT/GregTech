package gregtech.integration.binnies.extratrees;

import gregtech.api.GTValues;

import net.minecraftforge.common.config.Config;

@Config.LangKey("gregtech.config.extratrees")
@Config(modid = GTValues.MODID, name = GTValues.MODID + "/extratrees_integration", category = "Extra Trees(Binnie's Mods)")
public class ExtraTreesConfig {

    //开启林业木材加工
    @Config.Comment({
            "Enable GregTech Wooden Crafting Table.",
            "Requirements: Extra Trees module",
            "Default: true"
    })
    @Config.RequiresMcRestart
    public static boolean enableGTWoodenCraftingTable = true;
}
