package gregtech.client.renderer.textures;

import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.client.renderer.texture.cube.SimpleCubeRenderer;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;

public class SCTextures {

    public static OrientedOverlayRenderer FISSION_REACTOR_OVERLAY;
    public static OrientedOverlayRenderer SPENT_FUEL_POOL_OVERLAY;
    public static OrientedOverlayRenderer GAS_CENTRIFUGE_OVERLAY;
    public static OrientedOverlayRenderer HEAT_EXCHANGER_OVERLAY;
    public static OrientedOverlayRenderer NUCLEAR_REACTOR_OVERLAY;
    public static OrientedOverlayRenderer NUCLEAR_REACTOR_EXTEND_OVERLAY;

    public static SimpleOverlayRenderer FISSION_REACTOR_TEXTURE;
    public static SimpleOverlayRenderer CONTROL_ROD;
    public static SimpleOverlayRenderer CONTROL_ROD_MODERATED;
    public static SimpleOverlayRenderer MODERATOR_PORT;
    public static SimpleOverlayRenderer NUCLEAR_REACTOR_CASING;

    public static OrientedOverlayRenderer RTG_OVERLAY=new OrientedOverlayRenderer("overlay/rtg");

    public static void preInit() {
        FISSION_REACTOR_OVERLAY = new OrientedOverlayRenderer("multiblock/fission_reactor");
        SPENT_FUEL_POOL_OVERLAY = new OrientedOverlayRenderer("multiblock/spent_fuel_pool");
        GAS_CENTRIFUGE_OVERLAY = new OrientedOverlayRenderer("multiblock/gas_centrifuge");
        HEAT_EXCHANGER_OVERLAY = new OrientedOverlayRenderer("multiblock/heat_exchanger");
        NUCLEAR_REACTOR_OVERLAY = new OrientedOverlayRenderer("multiblock/nuclear_reactor");
        NUCLEAR_REACTOR_EXTEND_OVERLAY=new OrientedOverlayRenderer("multiblock/nuclear_reactor_extend");

        FISSION_REACTOR_TEXTURE = new SimpleOverlayRenderer("casings/fission/reactor_vessel");
        CONTROL_ROD = new SimpleOverlayRenderer("overlay/machine/overlay_control_rod");
        CONTROL_ROD_MODERATED = new SimpleOverlayRenderer("overlay/machine/overlay_control_rod_moderated");
        MODERATOR_PORT = new SimpleOverlayRenderer("overlay/machine/overlay_moderator_port");
        NUCLEAR_REACTOR_CASING = new SimpleOverlayRenderer("casings/nuclear/nuclear_reactor_casing");
    }
}
