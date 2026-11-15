package gregtech.api.pattern.casing;

import gregtech.api.GregTechAPI;
import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.util.CasingTier;
import gregtech.api.util.GlassTier;

import net.minecraft.util.IStringSerializable;

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
            heatingCoils = CasingDefinition.fromMap("heating_coil", true,
                    GregTechAPI.HEATING_COILS,
                    IHeatingCoilBlockStats::getTier,
                    // NOTE: do NOT use IHeatingCoilBlockStats::getName here. In production jars the
                    // coil implementations have their getName renamed to func_176610_l by reobf,
                    // while this mod interface keeps declaring "getName", so that method reference
                    // resolves to an abstract method and throws AbstractMethodError at load time.
                    // Calling through IStringSerializable keeps the reference remappable.
                    stats -> ((IStringSerializable) stats).getName());
        }
        return heatingCoils;
    }

    public static CasingRegistration machineCasings() {
        if (machineCasings == null) {
            machineCasings = CasingDefinition.fromIterable("machine_casing", true,
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
            borosilicateGlasses = CasingDefinition.fromIterable("borosilicate_glass", true,
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