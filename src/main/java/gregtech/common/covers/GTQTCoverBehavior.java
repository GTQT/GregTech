package gregtech.common.covers;

import gregtech.api.GTValues;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.items.behavior.CoverItemBehavior;
import gregtech.api.items.metaitem.MetaItem;

import net.minecraft.util.ResourceLocation;

import gregtech.common.items.MetaItems;

import static gregtech.api.util.GTUtility.gregtechId;

public class GTQTCoverBehavior {

    public static void init() {

        registerBehavior(gregtechId("programmable_circuit_cover"), MetaItems.COVER_PROGRAMMABLE_CIRCUIT,
                CoverProgrammableHatch::new);

        // Wireless Energy Covers - Input
        registerBehavior(gregtechId("wireless_energy_cover_input.ulv"),
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_ULV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.ULV, true));
        registerBehavior(gregtechId("wireless_energy_cover_input.lv"),
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_LV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.LV, true));
        registerBehavior(gregtechId("wireless_energy_cover_input.mv"),
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_MV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.MV, true));
        registerBehavior(gregtechId("wireless_energy_cover_input.hv"),
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_HV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.HV, true));
        registerBehavior(gregtechId("wireless_energy_cover_input.ev"),
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_EV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.EV, true));
        registerBehavior(gregtechId("wireless_energy_cover_input.iv"),
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_IV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.IV, true));
        registerBehavior(gregtechId("wireless_energy_cover_input.luv"),
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_LUV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.LuV, true));
        registerBehavior(gregtechId("wireless_energy_cover_input.zpm"),
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_ZPM,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.ZPM, true));
        registerBehavior(gregtechId("wireless_energy_cover_input.uv"),
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_UV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.UV, true));
        registerBehavior(gregtechId("wireless_energy_cover_input.uhv"),
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_UHV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.UHV, true));
        registerBehavior(gregtechId("wireless_energy_cover_input.uev"),
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_UEV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.UEV, true));
        registerBehavior(gregtechId("wireless_energy_cover_input.uiv"),
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_UIV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.UIV, true));
        registerBehavior(gregtechId("wireless_energy_cover_input.uxv"),
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_UXV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.UXV, true));
        registerBehavior(gregtechId("wireless_energy_cover_input.opv"),
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_OPV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.OpV, true));
        registerBehavior(gregtechId("wireless_energy_cover_input.max"),
                MetaItems.WIRELESS_ENERGY_COVER_INPUT_MAX,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.MAX, true));

        // Wireless Energy Covers - Output
        registerBehavior(gregtechId("wireless_energy_cover_output.ulv"),
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_ULV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.ULV, false));
        registerBehavior(gregtechId("wireless_energy_cover_output.lv"),
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_LV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.LV, false));
        registerBehavior(gregtechId("wireless_energy_cover_output.mv"),
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_MV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.MV, false));
        registerBehavior(gregtechId("wireless_energy_cover_output.hv"),
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_HV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.HV, false));
        registerBehavior(gregtechId("wireless_energy_cover_output.ev"),
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_EV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.EV, false));
        registerBehavior(gregtechId("wireless_energy_cover_output.iv"),
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_IV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.IV, false));
        registerBehavior(gregtechId("wireless_energy_cover_output.luv"),
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_LUV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.LuV, false));
        registerBehavior(gregtechId("wireless_energy_cover_output.zpm"),
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_ZPM,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.ZPM, false));
        registerBehavior(gregtechId("wireless_energy_cover_output.uv"),
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.UV, false));
        registerBehavior(gregtechId("wireless_energy_cover_output.uhv"),
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UHV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.UHV, false));
        registerBehavior(gregtechId("wireless_energy_cover_output.uev"),
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UEV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.UEV, false));
        registerBehavior(gregtechId("wireless_energy_cover_output.uiv"),
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UIV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.UIV, false));
        registerBehavior(gregtechId("wireless_energy_cover_output.uxv"),
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_UXV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.UXV, false));
        registerBehavior(gregtechId("wireless_energy_cover_output.opv"),
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_OPV,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.OpV, false));
        registerBehavior(gregtechId("wireless_energy_cover_output.max"),
                MetaItems.WIRELESS_ENERGY_COVER_OUTPUT_MAX,
                (def, view, side) -> new WirelessEnergyCover(def, view, side, GTValues.MAX, false));
    }

    @SuppressWarnings("rawtypes")
    public static void registerBehavior(ResourceLocation coverId,
                                        MetaItem.MetaValueItem placerItem,
                                        CoverDefinition.CoverCreator behaviorCreator) {
        CoverDefinition coverDefinition = gregtech.common.covers.CoverBehaviors.registerCover(coverId, placerItem.getStackForm(), behaviorCreator);
        placerItem.addComponents(new CoverItemBehavior(coverDefinition));
    }
}

