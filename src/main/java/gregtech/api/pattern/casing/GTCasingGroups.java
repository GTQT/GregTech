package gregtech.api.pattern.casing;

import gregtech.api.GregTechAPI;
import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.util.CasingTier;
import gregtech.api.util.GlassTier;

/**
 * Centralized registration of commonly-used ICasingGroup instances.
 * Each registration auto-creates a {@link StructureChannel} with the same name as the group.
 *
 * <p>Usage:
 * <pre>{@code
 * DeclarativePatternBuilder.start()
 *     .tieredCasing('C', GTCasingGroups.heatingCoils().group())
 *     .build();
 *
 * // In formStructure:
 * ICasing matched = GTCasingGroups.heatingCoils().channel().getMatchedCasing(context);
 * IHeatingCoilBlockStats stats = matched.getPayloadAs(IHeatingCoilBlockStats.class);
 * }</pre>
 */
public final class GTCasingGroups {

    private static CasingRegistration heatingCoils;
    private static CasingRegistration machineCasings;
    private static CasingRegistration borosilicateGlasses;

    private GTCasingGroups() {}

    public static CasingRegistration heatingCoils() {
        if (heatingCoils == null) {
            heatingCoils = CasingDefinition.fromMap("heating_coils", true,
                    GregTechAPI.HEATING_COILS,
                    IHeatingCoilBlockStats::getTier,
                    IHeatingCoilBlockStats::getName);
        }
        return heatingCoils;
    }

    public static CasingRegistration machineCasings() {
        if (machineCasings == null) {
            machineCasings = CasingDefinition.fromIterable("machine_casings", true,
                    CasingTier.getCasingList(),
                    CasingTier.CasingTierEntry::getState,
                    CasingTier.CasingTierEntry::getTier,
                    CasingTier.CasingTierEntry::getTranslationKey,
                    CasingTier.CasingTierEntry::getPayload);
        }
        return machineCasings;
    }

    public static CasingRegistration borosilicateGlasses() {
        if (borosilicateGlasses == null) {
            borosilicateGlasses = CasingDefinition.fromIterable("borosilicate_glasses", true,
                    GlassTier.getGlassList(),
                    GlassTier.GlassTierEntry::getState,
                    GlassTier.GlassTierEntry::getTier,
                    GlassTier.GlassTierEntry::getTranslationKey);
        }
        return borosilicateGlasses;
    }

    public static void invalidateCache() {
        heatingCoils = null;
        machineCasings = null;
        borosilicateGlasses = null;
    }
}