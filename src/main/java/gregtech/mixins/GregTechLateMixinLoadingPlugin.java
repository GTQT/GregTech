package gregtech.mixins;

import gregtech.api.util.Mods;

import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.ArrayList;
import java.util.List;

public class GregTechLateMixinLoadingPlugin implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        List<String> configs = new ArrayList<>();

        configs.add("mixins.gregtech.theoneprobe.json");
        configs.add("mixins.gregtech.jei.json");
        configs.add("mixins.gregtech.ctm.json");
        configs.add("mixins.gregtech.ccl.json");
        configs.add("mixins.gregtech.littletiles.json");
        configs.add("mixins.gregtech.vintagium.json");
        configs.add("mixins.gregtech.mui2.json");
        configs.add("mixins.gregtech.nothirium.json");
        configs.add("mixins.gregtech.forestry.json");
        configs.add("mixins.gregtech.gtmt.json");
        configs.add("mixins.gregtech.ae2.json");
        configs.add("mixins.gregtech.ae2fc.json");
        return configs;
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        return switch (mixinConfig) {
            case "mixins.gregtech.theoneprobe.json" -> Mods.TheOneProbe.isModLoaded();
            case "mixins.gregtech.jei.json" -> Mods.JustEnoughItems.isModLoaded();
            case "mixin.gregtech.ctm.json" -> Mods.CTM.isModLoaded();
            case "mixins.gregtech.littletiles.json" -> Mods.LittleTiles.isModLoaded();
            case "mixins.gregtech.vintagium.json" -> Mods.Vintagium.isModLoaded();
            case "mixins.gregtech.nothirium.json" -> Mods.Nothirium.isModLoaded();
            case "mixins.gregtech.forestry.json" -> Mods.Forestry.isModLoaded();
            case "mixins.gregtech.gtmt.json" -> Mods.GTMT.isModLoaded();
            case "mixins.gregtech.ae2.json" -> Mods.AppliedEnergistics2.isModLoaded();
            case "mixins.gregtech.ae2fc.json" -> Mods.AE2FluidCraft.isModLoaded();
            default -> true;
        };
    }
}
