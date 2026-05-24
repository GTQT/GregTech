package gregtech.api.pattern.casing;

import gregtech.api.GregTechAPI;
import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.util.CasingTier;
import gregtech.api.util.GlassTier;

/**
 * Centralized registration of commonly-used ICasingGroup instances.
 * These groups are lazily initialized from runtime registries (e.g. {@link GregTechAPI#HEATING_COILS}).
 *
 * <p>Usage:
 * <pre>{@code
 * DeclarativePatternBuilder.start()
 *     .tieredCasing('C', GTCasingGroups.heatingCoils())
 *         .withChannel(GTStructureChannels.HEATING_COIL)
 *     .build();
 * }</pre>
 *
 * <p>In formStructure, retrieve the matched casing:
 * <pre>{@code
 * ICasing matched = GTStructureChannels.HEATING_COIL.getMatchedCasing(context);
 * IHeatingCoilBlockStats stats = matched.getPayloadAs(IHeatingCoilBlockStats.class);
 * }</pre>
 */
public final class GTCasingGroups {

    private static ICasingGroup heatingCoilGroup;
    private static ICasingGroup machineCasingGroup;
    private static ICasingGroup borosilicateGlassGroup;

    private GTCasingGroups() {}

    public static ICasingGroup heatingCoils() {
        if (heatingCoilGroup == null) {
            heatingCoilGroup = CasingDefinition.fromMap("heating_coils", true,
                    GTStructureChannels.HEATING_COIL,
                    GregTechAPI.HEATING_COILS,
                    IHeatingCoilBlockStats::getTier,
                    IHeatingCoilBlockStats::getName);
        }
        return heatingCoilGroup;
    }

    public static ICasingGroup machineCasings() {
        if (machineCasingGroup == null) {
            machineCasingGroup = CasingDefinition.fromIterable("machine_casings", true,
                    GTStructureChannels.MACHINE_CASING,
                    CasingTier.getCasingList(),
                    CasingTier.CasingTierEntry::getState,
                    CasingTier.CasingTierEntry::getTier,
                    CasingTier.CasingTierEntry::getTranslationKey,
                    CasingTier.CasingTierEntry::getPayload);
        }
        return machineCasingGroup;
    }

    public static ICasingGroup borosilicateGlasses() {
        if (borosilicateGlassGroup == null) {
            borosilicateGlassGroup = CasingDefinition.fromIterable("borosilicate_glasses", true,
                    GTStructureChannels.BOROSILICATE_GLASS,
                    GlassTier.getGlassList(),
                    GlassTier.GlassTierEntry::getState,
                    GlassTier.GlassTierEntry::getTier,
                    GlassTier.GlassTierEntry::getTranslationKey);
        }
        return borosilicateGlassGroup;
    }

    public static void invalidateCache() {
        heatingCoilGroup = null;
        machineCasingGroup = null;
        borosilicateGlassGroup = null;
    }
}