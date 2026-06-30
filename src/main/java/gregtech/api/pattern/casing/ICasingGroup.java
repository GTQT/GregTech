package gregtech.api.pattern.casing;

import java.util.List;

/**
 * Represents a group of casings that share the same structural role but differ in tier.
 * For example, all heating coils form a group where each coil type is a different tier.
 *
 * <p>Casing groups can optionally enforce uniform tier — all blocks of this group
 * in a formed structure must be of the same tier.
 *
 * @see ICasing for individual casing definitions
 * @see CasingDefinition for pre-defined casing groups
 */
public interface ICasingGroup {

    /**
     * @return the unique identifier for this casing group (e.g. "heating_coils", "solid_casings")
     */
    String getGroupId();

    /**
     * @return the translation key for the group name (e.g. "gregtech.casing_group.heating_coils")
     */
    String getTranslationKey();

    /**
     * @return all casings in this group, ordered by tier (ascending)
     */
    List<ICasing> getCasings();

    /**
     * @return true if all casings of this group in a structure must be of the same tier
     */
    boolean requiresUniformTier();

    /**
     * Get the tier channel name emitted into typed structure channel metadata.
     * This is the key used to store/retrieve the detected tier during pattern matching.
     *
     * @return the tier channel name (defaults to groupId)
     */
    default String getTierChannel() {
        return getGroupId();
    }
}
