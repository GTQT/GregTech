package gregtech.api.block;

/** Glass tier shared by casing blocks and addon structure requirements. */
public interface IGlassTier {

    int getGlassTier();

    default boolean isOpticalGlass() {
        return false;
    }
}
