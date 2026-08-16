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
        configs.add("mixins.gregtech.vintagium.json");
        configs.add("mixins.gregtech.mui2.json");
        configs.add("mixins.gregtech.nothirium.json");
        configs.add("mixins.gregtech.forestry.json");
        configs.add("mixins.gregtech.betterbuilderswands.json");
        configs.add("mixins.gregtech.chisel.json");
        configs.add("mixins.gregtech.tconstruct.json");
        return configs;
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        return switch (mixinConfig) {
            case "mixins.gregtech.theoneprobe.json" -> Mods.TheOneProbe.isModLoaded();
            case "mixins.gregtech.jei.json" -> Mods.JustEnoughItems.isModLoaded();
            case "mixins.gregtech.ctm.json" -> Mods.CTM.isModLoaded();
            case "mixins.gregtech.vintagium.json" -> Mods.Vintagium.isModLoaded();
            case "mixins.gregtech.nothirium.json" -> Mods.Nothirium.isModLoaded();
            case "mixins.gregtech.forestry.json" -> Mods.Forestry.isModLoaded();
            case "mixins.gregtech.betterbuilderswands.json" -> Mods.BetterBuildersWands.isModLoaded();
            case "mixins.gregtech.chisel.json" -> Mods.Chisel.isModLoaded();
            case "mixins.gregtech.tconstruct.json" -> Mods.TinkersConstruct.isModLoaded();
            default -> true;
        };
    }
}
