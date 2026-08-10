package gregtech.api.mattermanipulator;

import java.util.Optional;

/**
 * A GregTech endpoint that can be configured to share a source endpoint during
 * a target-native smart-copy operation.
 */
public interface ISmartCopyLinkable {

    /** Returns the configured root source, when this endpoint is linked. */
    Optional<SmartCopyLink> getSmartCopyLink();

    /**
     * Configures this endpoint to share {@code source}.
     *
     * @return {@code true} when the implementation accepted the source
     */
    boolean setSmartCopyLink(SmartCopyLink source);

    /** Removes any configured smart-copy source. */
    void clearSmartCopyLink();
}
