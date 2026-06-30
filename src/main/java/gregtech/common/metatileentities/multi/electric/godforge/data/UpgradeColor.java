package gregtech.common.metatileentities.multi.electric.godforge.data;

import gregtech.api.mui.GTGuiTextures;

import com.cleanroommc.modularui.drawable.UITexture;

public enum UpgradeColor {

    BLUE(
            GTGuiTextures.BACKGROUND_GLOW_BLUE,
            GTGuiTextures.PICTURE_OVERLAY_BLUE,
            GTGuiTextures.PICTURE_UPGRADE_CONNECTOR_BLUE_OPAQUE,
            GTGuiTextures.PICTURE_UPGRADE_CONNECTOR_BLUE),

    PURPLE(
            GTGuiTextures.BACKGROUND_GLOW_PURPLE,
            GTGuiTextures.PICTURE_OVERLAY_PURPLE,
            GTGuiTextures.PICTURE_UPGRADE_CONNECTOR_PURPLE_OPAQUE,
            GTGuiTextures.PICTURE_UPGRADE_CONNECTOR_PURPLE),

    ORANGE(
            GTGuiTextures.BACKGROUND_GLOW_ORANGE,
            GTGuiTextures.PICTURE_OVERLAY_ORANGE,
            GTGuiTextures.PICTURE_UPGRADE_CONNECTOR_ORANGE_OPAQUE,
            GTGuiTextures.PICTURE_UPGRADE_CONNECTOR_ORANGE),

    GREEN(
            GTGuiTextures.BACKGROUND_GLOW_GREEN,
            GTGuiTextures.PICTURE_OVERLAY_GREEN,
            GTGuiTextures.PICTURE_UPGRADE_CONNECTOR_GREEN_OPAQUE,
            GTGuiTextures.PICTURE_UPGRADE_CONNECTOR_GREEN),

    RED(
            GTGuiTextures.BACKGROUND_GLOW_RED,
            GTGuiTextures.PICTURE_OVERLAY_RED,
            GTGuiTextures.PICTURE_UPGRADE_CONNECTOR_RED_OPAQUE,
            GTGuiTextures.PICTURE_UPGRADE_CONNECTOR_RED);

    public static final UpgradeColor[] VALUES = values();

    private final UITexture background;
    private final UITexture overlay;
    private final UITexture opaqueConnector;
    private final UITexture connector;

    UpgradeColor(UITexture background, UITexture overlay, UITexture opaqueConnector, UITexture connector) {
        this.background = background;
        this.overlay = overlay;
        this.opaqueConnector = opaqueConnector;
        this.connector = connector;
    }

    public UITexture getBackground() {
        return background;
    }

    public UITexture getOverlay() {
        return overlay;
    }

    public UITexture getOpaqueConnector() {
        return opaqueConnector;
    }

    public UITexture getConnector() {
        return connector;
    }
}
