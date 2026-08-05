package gregtech.api.mui;

import gregtech.api.GTValues;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.ColorType;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * GT MUI textures.<br>
 * Marked experimental as some of these textures may disappear or be renamed at some point
 * while MUI port is still ongoing. When MUI port is done, this annotation will be removed.
 */
// TODO ^
@SuppressWarnings("unused")
@ApiStatus.Experimental
public class GTGuiTextures {

    /** Keys used for GT assets registered for use in Themes */
    public static class IDs {

        public static final String STANDARD_BACKGROUND = id("standard_bg");
        public static final String COVER_BACKGROUND = id("cover_bg");
        public static final String BRONZE_BACKGROUND = id("bronze_bg");
        public static final String STEEL_BACKGROUND = id("steel_bg");
        public static final String PRIMITIVE_BACKGROUND = id("primitive_bg");

        public static final String STANDARD_SLOT = id("standard_slot");
        public static final String DARK_SLOT = id("dark_slot");
        public static final String BRONZE_SLOT = id("bronze_slot");
        public static final String STEEL_SLOT = id("steel_slot");
        public static final String PRIMITIVE_SLOT = id("primitive_slot");

        public static final String STANDARD_FLUID_SLOT = id("standard_fluid_slot");

        public static final String STANDARD_BUTTON = id("standard_button");
        public static final String BRONZE_BUTTON = id("bronze_button");
        public static final String STEEL_BUTTON = id("steel_button");
        public static final String BRONZE_BUTTON_SELECTED = id("bronze_button_sel");
        public static final String STEEL_BUTTON_SELECTED = id("steel_button_sel");

        public static final String DISPLAY = id("display");
        public static final String DISPLAY_BRONZE = id("display_bronze");
        public static final String DISPLAY_STEEL = id("display_steel");

        private static String id(String path) {
            return "gregtech:" + path;
        }
    }

    // ICONS
    /** @apiNote You may want {@link GTGuiTextures#getLogo} instead. */
    public static final UITexture GREGTECH_LOGO = fullImage("textures/gui/icon/gregtech_logo.png");
    /** @apiNote You may want {@link GTGuiTextures#getLogo} instead. */
    public static final UITexture GREGTECH_LOGO_XMAS = fullImage("textures/gui/icon/gregtech_logo_xmas.png");
    public static final UITexture GREGTECH_LOGO_DARK = fullImage("textures/gui/icon/gregtech_logo_dark.png");
    public static final IDrawable GREGTECH_LOGO_BLINKING_YELLOW = animated(
            "textures/gui/icon/gregtech_logo_blinking_yellow.png", 17, 34, 60);
    public static final IDrawable GREGTECH_LOGO_BLINKING_RED = animated(
            "textures/gui/icon/gregtech_logo_blinking_red.png", 17, 34, 36);
    public static final UITexture GREGTECH_LOGO_BRONZE = fullImage("textures/gui/base/logo_bronze");
    public static final UITexture GREGTECH_LOGO_STEEL = fullImage("textures/gui/base/logo_steel");

    public static final UITexture INDICATOR_NO_ENERGY = fullImage("textures/gui/base/indicator_no_energy.png");
    public static final UITexture INDICATOR_NO_STEAM_BRONZE = fullImage(
            "textures/gui/base/indicator_no_steam_bronze.png");
    public static final UITexture INDICATOR_NO_STEAM_STEEL = fullImage(
            "textures/gui/base/indicator_no_steam_steel.png");
    public static final UITexture TANK_ICON = fullImage("textures/gui/base/tank_icon.png");

    // STEAM GAUGE
    public static final UITexture STEAM_GAUGE_BG_BRONZE = fullImage("textures/gui/background/steam_dial_bronze.png");
    public static final UITexture STEAM_GAUGE_BG_STEEL = fullImage("textures/gui/background/steam_dial_steel.png");

    // BACKGROUNDS
    public static final UITexture BACKGROUND = UITexture.builder()
            .location(GTValues.MODID, "textures/gui/base/background.png")
            .imageSize(16, 16)
            .adaptable(3)
            .name(IDs.STANDARD_BACKGROUND)
            .canApplyTheme()
            .build();

    public static final UITexture BACKGROUND_POPUP = UITexture.builder()
            .location(GTValues.MODID, "textures/gui/base/background_popup.png")
            .imageSize(16, 16)
            .adaptable(4)
            .name(IDs.COVER_BACKGROUND)
            .canApplyTheme()
            .build();

    public static final UITexture BACKGROUND_BRONZE = UITexture.builder()
            .location(GTValues.MODID, "textures/gui/base/background_bronze.png")
            .imageSize(16, 16)
            .adaptable(3)
            .name(IDs.BRONZE_BACKGROUND)
            .build();

    public static final UITexture BACKGROUND_STEEL = UITexture.builder()
            .location(GTValues.MODID, "textures/gui/base/background_steel.png")
            .imageSize(16, 16)
            .adaptable(3)
            .name(IDs.STEEL_BACKGROUND)
            .build();

    // todo move to textures/gui/base
    public static final UITexture BACKGROUND_PRIMITIVE = UITexture.builder()
            // main color 826b51
            .location(GTValues.MODID, "textures/gui/primitive/primitive_background.png")
            .imageSize(16, 16)
            .adaptable(3)
            .name(IDs.PRIMITIVE_BACKGROUND)
            .build();

    // todo clipboard backgrounds, may deserve some redoing

    // DISPLAYS
    public static final UITexture DISPLAY = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/base/display.png")
            .name(IDs.DISPLAY)
            .imageSize(143, 75)
            .adaptable(2)
            .canApplyTheme()
            .build();

    public static final UITexture DISPLAY_BRONZE = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/base/display_bronze.png")
            .name(IDs.DISPLAY_BRONZE)
            .imageSize(143, 75)
            .adaptable(2)
            .build();

    public static final UITexture DISPLAY_STEEL = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/base/display_steel.png")
            .name(IDs.DISPLAY_STEEL)
            .imageSize(143, 75)
            .adaptable(2)
            .build();

    // todo primitive display?

    // FUSION
    public static final UITexture FUSION_REACTOR_MK1_TITLE = fullImage(
            "textures/gui/widget/fusion_reactor_mk1_title.png");
    public static final UITexture FUSION_REACTOR_MK2_TITLE = fullImage(
            "textures/gui/widget/fusion_reactor_mk2_title.png");
    public static final UITexture FUSION_REACTOR_MK3_TITLE = fullImage(
            "textures/gui/widget/fusion_reactor_mk3_title.png");
    public static final UITexture FUSION_REACTOR_MK4_TITLE = fullImage(
            "textures/gui/widget/fusion_reactor_mk4_title.png");
    public static final UITexture FUSION_REACTOR_MK5_TITLE = fullImage(
            "textures/gui/widget/fusion_reactor_mk5_title.png");
    public static final UITexture FUSION_REACTOR_MK6_TITLE = fullImage(
            "textures/gui/widget/fusion_reactor_mk6_title.png");

    public static final UITexture FUSION_DIAGRAM = fullImage("textures/gui/widget/fusion_reactor_diagram.png");
    public static final UITexture FUSION_LEGEND = fullImage("textures/gui/widget/fusion_reactor_legend.png");
    public static final UITexture FUSION_PROGRESS = fullImage("textures/gui/progress_bar/fusion_diagram/stitched.png");

    // SLOTS
    public static final UITexture SLOT = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/base/slot.png")
            .imageSize(18, 18)
            .adaptable(1)
            .name(IDs.STANDARD_SLOT)
            .canApplyTheme()
            .build();

    public static final UITexture SLOT_DARK = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/base/slot_dark.png")
            .imageSize(18, 18)
            .adaptable(1)
            .name(IDs.DARK_SLOT)
            .canApplyTheme()
            .build();

    public static final UITexture SLOT_BRONZE = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/base/slot_bronze.png")
            .imageSize(18, 18)
            .adaptable(1)
            .name(IDs.BRONZE_SLOT)
            .build();

    public static final UITexture SLOT_STEEL = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/base/slot_steel.png")
            .imageSize(18, 18)
            .adaptable(1)
            .name(IDs.STEEL_SLOT)
            .build();

    // todo move to textures/gui/base
    public static final UITexture SLOT_PRIMITIVE = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/primitive/primitive_slot.png")
            .imageSize(18, 18)
            .adaptable(1)
            .name(IDs.PRIMITIVE_SLOT)
            .build();

    public static final UITexture FLUID_SLOT = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/base/fluid_slot.png")
            .imageSize(18, 18)
            .adaptable(1)
            .name(IDs.STANDARD_FLUID_SLOT)
            .canApplyTheme()
            .build();

    // HPCA Component icons
    public static final UITexture BLANK_TRANSPARENT = fullImage("textures/gui/base/blank_transparent.png");
    public static final UITexture HPCA_COMPONENT_OUTLINE = fullImage("textures/gui/widget/hpca/component_outline.png");
    public static final UITexture HPCA_ICON_EMPTY_COMPONENT = fullImage("textures/gui/widget/hpca/empty_component.png");
    public static final UITexture HPCA_ICON_ADVANCED_COMPUTATION_COMPONENT = fullImage(
            "textures/gui/widget/hpca/advanced_computation_component.png");
    public static final UITexture HPCA_ICON_BRIDGE_COMPONENT = fullImage(
            "textures/gui/widget/hpca/bridge_component.png");
    public static final UITexture HPCA_ICON_COMPUTATION_COMPONENT = fullImage(
            "textures/gui/widget/hpca/computation_component.png");
    public static final UITexture HPCA_ICON_ACTIVE_COOLER_COMPONENT = fullImage(
            "textures/gui/widget/hpca/active_cooler_component.png");
    public static final UITexture HPCA_ICON_HEAT_SINK_COMPONENT = fullImage(
            "textures/gui/widget/hpca/heat_sink_component.png");
    public static final UITexture HPCA_ICON_DAMAGED_ADVANCED_COMPUTATION_COMPONENT = fullImage(
            "textures/gui/widget/hpca/damaged_advanced_computation_component.png");
    public static final UITexture HPCA_ICON_DAMAGED_COMPUTATION_COMPONENT = fullImage(
            "textures/gui/widget/hpca/damaged_computation_component.png");

    public static final UITexture[] BUTTON_BLACKLIST = slice("textures/gui/widget/button_blacklist.png",
            16, 32, 16, 16, ColorType.DEFAULT);
    public static final UITexture[] BUTTON_IGNORE_DAMAGE = slice("textures/gui/widget/button_filter_damage.png",
            16, 32, 16, 16, ColorType.DEFAULT);
    public static final UITexture[] BUTTON_IGNORE_NBT = slice("textures/gui/widget/button_filter_nbt.png",
            16, 32, 16, 16, ColorType.DEFAULT);

    public static final UITexture[] BUTTON_CASE_SENSITIVE = slice(
            "textures/gui/widget/ore_filter/button_case_sensitive.png",
            16, 32, 16, 16, ColorType.DEFAULT);

    public static final UITexture[] BUTTON_MATCH_ALL = slice("textures/gui/widget/ore_filter/button_match_all.png",
            16, 32, 16, 16, ColorType.DEFAULT);
    public static final UITexture BUTTON_LOCK = fullImage("textures/gui/widget/button_lock.png");

    public static final UITexture OREDICT_ERROR = fullImage("textures/gui/widget/ore_filter/error.png");
    public static final UITexture OREDICT_INFO = fullImage("textures/gui/widget/ore_filter/info.png");
    public static final UITexture OREDICT_MATCH = fullImage("textures/gui/widget/ore_filter/match.png");
    public static final UITexture OREDICT_NO_MATCH = fullImage("textures/gui/widget/ore_filter/no_match.png");
    public static final UITexture OREDICT_SUCCESS = fullImage("textures/gui/widget/ore_filter/success.png");
    public static final UITexture OREDICT_WAITING = fullImage("textures/gui/widget/ore_filter/waiting.png");
    public static final UITexture OREDICT_WARN = fullImage("textures/gui/widget/ore_filter/warn.png");

    public static final IDrawable PLUS = IKey.str("+").asIcon().marginLeft(1);
    public static final IDrawable MINUS = IKey.str("-").asIcon().marginLeft(1);

    public static final UITexture[] MANUAL_IO_OVERLAY_IN = slice("textures/gui/overlay/manual_io_overlay_in.png",
            18, 18 * 3, 18, 18, ColorType.DEFAULT);
    public static final UITexture[] MANUAL_IO_OVERLAY_OUT = slice("textures/gui/overlay/manual_io_overlay_out.png",
            18, 18 * 3, 18, 18, ColorType.DEFAULT);
    public static final UITexture[] CONVEYOR_MODE_OVERLAY = slice("textures/gui/overlay/conveyor_mode_overlay.png",
            18, 18 * 2, 18, 18, ColorType.DEFAULT);

    public static final UITexture[] TRANSFER_MODE_OVERLAY = slice("textures/gui/overlay/transfer_mode_overlay.png",
            18, 18 * 4, 18, 18, ColorType.DEFAULT);

    public static final UITexture[] FLUID_TRANSFER_MODE_OVERLAY = slice(
            "textures/gui/overlay/fluid_transfer_mode_overlay.png",
            18, 18 * 4, 18, 18, ColorType.DEFAULT);

    public static final UITexture[] DISTRIBUTION_MODE_OVERLAY = slice(
            "textures/gui/widget/button_distribution_mode.png",
            16, 48, 16, 16, ColorType.DEFAULT);

    public static final UITexture[] VOIDING_MODE_OVERLAY = slice(
            "textures/gui/overlay/voiding_mode_overlay.png",
            16, 32, 16, 16, ColorType.DEFAULT);

    public static final UITexture[] FILTER_MODE_OVERLAY = slice(
            "textures/gui/overlay/filter_mode_overlay.png",
            16, 48, 16, 16, ColorType.DEFAULT);

    public static final UITexture[] PRIVATE_MODE_BUTTON = slice(
            "textures/gui/widget/button_public_private.png",
            18, 36, 18, 18, ColorType.DEFAULT);

    public static final UITexture MENU_OVERLAY = fullImage("textures/gui/overlay/menu_overlay.png");
    public static final UITexture BUTTON_EXPORT_FACE = fullImage("textures/gui/overlay/button_export_face.png");
    public static final UITexture BUTTON_OVERCLOCK = fullImage("textures/gui/widget/button_overclock.png");
    public static final UITexture RECIPE_LOCK = fullImage("textures/gui/widget/lock.png");
    public static final UITexture RECIPE_LOCK_WHITE = fullImage("textures/gui/widget/lock_white.png");
    public static final UITexture PRIMITIVE_FURNACE_OVERLAY = fullImage(
            "textures/gui/primitive/overlay_primitive_furnace.png");
    public static final UITexture PRIMITIVE_DUST_OVERLAY = fullImage(
            "textures/gui/primitive/overlay_primitive_dust.png");
    public static final UITexture PRIMITIVE_INGOT_OVERLAY = fullImage(
            "textures/gui/primitive/overlay_primitive_ingot.png");
    public static final UITexture PRIMITIVE_LARGE_FLUID_TANK = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/primitive/primitive_large_fluid_tank.png")
            .fullImage()
            .adaptable(2)
            .build();
    public static final UITexture PRIMITIVE_LARGE_FLUID_TANK_OVERLAY = fullImage(
            "textures/gui/primitive/primitive_large_fluid_tank_overlay.png");
    public static final UITexture PRIMITIVE_BLAST_FURNACE_PROGRESS_BAR = fullImage(
            "textures/gui/primitive/progress_bar_primitive_blast_furnace.png");

    public static final UITexture FLUID_VOID_OVERLAY = fullImage("textures/gui/widget/button_fluid_void.png",
            ColorType.DEFAULT);
    public static final UITexture ITEM_VOID_OVERLAY = fullImage("textures/gui/widget/button_item_void.png",
            ColorType.DEFAULT);
    public static final UITexture FLUID_LOCK_OVERLAY = fullImage("textures/gui/widget/button_lock.png",
            ColorType.DEFAULT);

    // todo bronze/steel/primitive fluid slots?
    public static final UITexture TERMINAL_FRAME = fullImage("textures/gui/terminal/terminal_frame.png");
    public static final UITexture HOME_BUTTON = fullImage("textures/gui/terminal/home_button.png");
    public static final UITexture HOME_BUTTON_HOVER = fullImage("textures/gui/terminal/home_button_hover.png");
    public static final UITexture CAPES_APP_ICON = fullImage("textures/gui/terminal/capes/icon.png");


    // SLOT OVERLAYS
    public static final UITexture ATOMIC_OVERLAY_1 = fullImage("textures/gui/overlay/atomic_overlay_1.png",
            ColorType.DEFAULT);
    public static final UITexture ATOMIC_OVERLAY_2 = fullImage("textures/gui/overlay/atomic_overlay_2.png",
            ColorType.DEFAULT);
    public static final UITexture ARROW_INPUT_OVERLAY = fullImage("textures/gui/overlay/arrow_input_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture ARROW_OUTPUT_OVERLAY = fullImage("textures/gui/overlay/arrow_output_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture BATTERY_OVERLAY = fullImage("textures/gui/overlay/battery_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture BEAKER_OVERLAY_1 = fullImage("textures/gui/overlay/beaker_overlay_1.png",
            ColorType.DEFAULT);
    public static final UITexture BEAKER_OVERLAY_2 = fullImage("textures/gui/overlay/beaker_overlay_2.png",
            ColorType.DEFAULT);
    public static final UITexture BEAKER_OVERLAY_3 = fullImage("textures/gui/overlay/beaker_overlay_3.png",
            ColorType.DEFAULT);
    public static final UITexture BEAKER_OVERLAY_4 = fullImage("textures/gui/overlay/beaker_overlay_4.png",
            ColorType.DEFAULT);
    public static final UITexture BENDER_OVERLAY = fullImage("textures/gui/overlay/bender_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture BOX_OVERLAY = fullImage("textures/gui/overlay/box_overlay.png", ColorType.DEFAULT);
    public static final UITexture BOXED_OVERLAY = fullImage("textures/gui/overlay/boxed_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture BREWER_OVERLAY = fullImage("textures/gui/overlay/brewer_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture CANNER_OVERLAY = fullImage("textures/gui/overlay/canner_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture CHARGER_OVERLAY = fullImage("textures/gui/overlay/charger_slot_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture CANISTER_OVERLAY = fullImage("textures/gui/overlay/canister_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture CANISTER_OVERLAY_BRONZE = fullImage(
            "textures/gui/overlay/canister_overlay_bronze.png");
    public static final UITexture CANISTER_OVERLAY_STEEL = fullImage("textures/gui/overlay/canister_overlay_steel.png");
    public static final UITexture CENTRIFUGE_OVERLAY = fullImage("textures/gui/overlay/centrifuge_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture CIRCUIT_OVERLAY = fullImage("textures/gui/overlay/circuit_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture COAL_OVERLAY_BRONZE = fullImage("textures/gui/overlay/coal_overlay_bronze.png");
    public static final UITexture COAL_OVERLAY_STEEL = fullImage("textures/gui/overlay/coal_overlay_steel.png");
    public static final UITexture COMPRESSOR_OVERLAY = fullImage("textures/gui/overlay/compressor_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture COMPRESSOR_OVERLAY_BRONZE = fullImage(
            "textures/gui/overlay/compressor_overlay_bronze.png");
    public static final UITexture COMPRESSOR_OVERLAY_STEEL = fullImage(
            "textures/gui/overlay/compressor_overlay_steel.png");
    public static final UITexture CRACKING_OVERLAY_1 = fullImage("textures/gui/overlay/cracking_overlay_1.png",
            ColorType.DEFAULT);
    public static final UITexture CRACKING_OVERLAY_2 = fullImage("textures/gui/overlay/cracking_overlay_2.png",
            ColorType.DEFAULT);
    public static final UITexture CRUSHED_ORE_OVERLAY = fullImage("textures/gui/overlay/crushed_ore_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture CRUSHED_ORE_OVERLAY_BRONZE = fullImage(
            "textures/gui/overlay/crushed_ore_overlay_bronze.png");
    public static final UITexture CRUSHED_ORE_OVERLAY_STEEL = fullImage(
            "textures/gui/overlay/crushed_ore_overlay_steel.png");
    public static final UITexture CRYSTAL_OVERLAY = fullImage("textures/gui/overlay/crystal_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture CUTTER_OVERLAY = fullImage("textures/gui/overlay/cutter_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture DARK_CANISTER_OVERLAY = fullImage("textures/gui/overlay/dark_canister_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture DUST_OVERLAY = fullImage("textures/gui/overlay/dust_overlay.png", ColorType.DEFAULT);
    public static final UITexture DUST_OVERLAY_BRONZE = fullImage("textures/gui/overlay/dust_overlay_bronze.png");
    public static final UITexture DUST_OVERLAY_STEEL = fullImage("textures/gui/overlay/dust_overlay_steel.png");
    public static final UITexture EXTRACTOR_OVERLAY = fullImage("textures/gui/overlay/extractor_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture EXTRACTOR_OVERLAY_BRONZE = fullImage(
            "textures/gui/overlay/extractor_overlay_bronze.png");
    public static final UITexture EXTRACTOR_OVERLAY_STEEL = fullImage(
            "textures/gui/overlay/extractor_overlay_steel.png");
    public static final UITexture FILTER_SLOT_OVERLAY = fullImage("textures/gui/overlay/filter_slot_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture FILTER_SETTINGS_OVERLAY = fullImage(
            "textures/gui/overlay/filter_settings_overlay.png");
    public static final UITexture OVERLAY_THREAD = fullImage(
            "textures/gui/overlay/thread_overlay.png");
    public static final UITexture OVERLAY_PARALLEL = fullImage(
            "textures/gui/overlay/parallel_overlay.png");

    public static final UITexture FURNACE_OVERLAY_1 = fullImage("textures/gui/overlay/furnace_overlay_1.png",
            ColorType.DEFAULT);
    public static final UITexture FURNACE_OVERLAY_2 = fullImage("textures/gui/overlay/furnace_overlay_2.png",
            ColorType.DEFAULT);
    public static final UITexture FURNACE_OVERLAY_BRONZE = fullImage("textures/gui/overlay/furnace_overlay_bronze.png");
    public static final UITexture FURNACE_OVERLAY_STEEL = fullImage("textures/gui/overlay/furnace_overlay_steel.png");
    public static final UITexture HAMMER_OVERLAY = fullImage("textures/gui/overlay/hammer_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture HAMMER_OVERLAY_BRONZE = fullImage("textures/gui/overlay/hammer_overlay_bronze.png");
    public static final UITexture HAMMER_OVERLAY_STEEL = fullImage("textures/gui/overlay/hammer_overlay_steel.png");
    public static final UITexture HEATING_OVERLAY_1 = fullImage("textures/gui/overlay/heating_overlay_1.png",
            ColorType.DEFAULT);
    public static final UITexture HEATING_OVERLAY_2 = fullImage("textures/gui/overlay/heating_overlay_2.png",
            ColorType.DEFAULT);
    public static final UITexture IMPLOSION_OVERLAY_1 = fullImage("textures/gui/overlay/implosion_overlay_1.png",
            ColorType.DEFAULT);
    public static final UITexture IMPLOSION_OVERLAY_2 = fullImage("textures/gui/overlay/implosion_overlay_2.png",
            ColorType.DEFAULT);
    public static final UITexture IN_SLOT_OVERLAY = fullImage("textures/gui/overlay/in_slot_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture IN_SLOT_OVERLAY_BRONZE = fullImage("textures/gui/overlay/in_slot_overlay_bronze.png");
    public static final UITexture IN_SLOT_OVERLAY_STEEL = fullImage("textures/gui/overlay/in_slot_overlay_steel.png");
    public static final UITexture INGOT_OVERLAY = fullImage("textures/gui/overlay/ingot_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture INT_CIRCUIT_OVERLAY = fullImage("textures/gui/overlay/int_circuit_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture LENS_OVERLAY = fullImage("textures/gui/overlay/lens_overlay.png", ColorType.DEFAULT);
    public static final UITexture LIGHTNING_OVERLAY_1 = fullImage("textures/gui/overlay/lightning_overlay_1.png",
            ColorType.DEFAULT);
    public static final UITexture LIGHTNING_OVERLAY_2 = fullImage("textures/gui/overlay/lightning_overlay_2.png",
            ColorType.DEFAULT);
    public static final UITexture MOLD_OVERLAY = fullImage("textures/gui/overlay/mold_overlay.png", ColorType.DEFAULT);
    public static final UITexture MOLECULAR_OVERLAY_1 = fullImage("textures/gui/overlay/molecular_overlay_1.png",
            ColorType.DEFAULT);
    public static final UITexture MOLECULAR_OVERLAY_2 = fullImage("textures/gui/overlay/molecular_overlay_2.png",
            ColorType.DEFAULT);
    public static final UITexture MOLECULAR_OVERLAY_3 = fullImage("textures/gui/overlay/molecular_overlay_3.png",
            ColorType.DEFAULT);
    public static final UITexture MOLECULAR_OVERLAY_4 = fullImage("textures/gui/overlay/molecular_overlay_4.png",
            ColorType.DEFAULT);
    public static final UITexture OUT_SLOT_OVERLAY = fullImage("textures/gui/overlay/out_slot_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture OUT_SLOT_OVERLAY_BRONZE = fullImage(
            "textures/gui/overlay/out_slot_overlay_bronze.png");
    public static final UITexture OUT_SLOT_OVERLAY_STEEL = fullImage("textures/gui/overlay/out_slot_overlay_steel.png");
    public static final UITexture PAPER_OVERLAY = fullImage("textures/gui/overlay/paper_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture PRINTED_PAPER_OVERLAY = fullImage("textures/gui/overlay/printed_paper_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture PIPE_OVERLAY_2 = fullImage("textures/gui/overlay/pipe_overlay_2.png",
            ColorType.DEFAULT);
    public static final UITexture PIPE_OVERLAY_1 = fullImage("textures/gui/overlay/pipe_overlay_1.png",
            ColorType.DEFAULT);
    public static final UITexture PRESS_OVERLAY_1 = fullImage("textures/gui/overlay/press_overlay_1.png",
            ColorType.DEFAULT);
    public static final UITexture PRESS_OVERLAY_2 = fullImage("textures/gui/overlay/press_overlay_2.png",
            ColorType.DEFAULT);
    public static final UITexture PRESS_OVERLAY_3 = fullImage("textures/gui/overlay/press_overlay_3.png",
            ColorType.DEFAULT);
    public static final UITexture PRESS_OVERLAY_4 = fullImage("textures/gui/overlay/press_overlay_4.png",
            ColorType.DEFAULT);
    public static final UITexture SAWBLADE_OVERLAY = fullImage("textures/gui/overlay/sawblade_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture SOLIDIFIER_OVERLAY = fullImage("textures/gui/overlay/solidifier_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture STRING_SLOT_OVERLAY = fullImage("textures/gui/overlay/string_slot_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture STRING_SLOT_OVERLAY_2 = fullImage("textures/gui/overlay/string_slot_overlay_2.png",
            ColorType.DEFAULT);
    public static final UITexture TOOL_SLOT_OVERLAY = fullImage("textures/gui/overlay/tool_slot_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture TURBINE_OVERLAY = fullImage("textures/gui/overlay/turbine_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture VIAL_OVERLAY_1 = fullImage("textures/gui/overlay/vial_overlay_1.png",
            ColorType.DEFAULT);
    public static final UITexture VIAL_OVERLAY_2 = fullImage("textures/gui/overlay/vial_overlay_2.png",
            ColorType.DEFAULT);
    public static final UITexture WIREMILL_OVERLAY = fullImage("textures/gui/overlay/wiremill_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture POSITIVE_MATTER_OVERLAY = fullImage(
            "textures/gui/overlay/positive_matter_overlay.png", ColorType.DEFAULT);
    public static final UITexture NEUTRAL_MATTER_OVERLAY = fullImage("textures/gui/overlay/neutral_matter_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture DATA_ORB_OVERLAY = fullImage("textures/gui/overlay/data_orb_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture SCANNER_OVERLAY = fullImage("textures/gui/overlay/scanner_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture DUCT_TAPE_OVERLAY = fullImage("textures/gui/overlay/duct_tape_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture RESEARCH_STATION_OVERLAY = fullImage(
            "textures/gui/overlay/research_station_overlay.png", ColorType.DEFAULT);
    public static final UITexture DISH_OVERLAY = fullImage("textures/gui/overlay/dish_overlay.png",
            ColorType.DEFAULT);
    public static final UITexture FOIL_OVERLAY = fullImage("textures/gui/overlay/foil_overlay.png",
            ColorType.DEFAULT);


    // Texture Areas
    public static final UITexture[] BUTTON_FLUID = slice("textures/blocks/cover/cover_interface_fluid_button.png", 18,
            36, null);
    public static final UITexture[] BUTTON_ITEM = slice("textures/blocks/cover/cover_interface_item_button.png", 18, 36,
            null);
    public static final UITexture[] BUTTON_ENERGY = slice("textures/blocks/cover/cover_interface_energy_button.png", 18,
            36, null);
    public static final UITexture[] BUTTON_MACHINE = slice("textures/blocks/cover/cover_interface_machine_button.png",
            18, 36, null);
    public static final UITexture[] BUTTON_INTERFACE = slice(
            "textures/blocks/cover/cover_interface_computer_button.png", 18, 36, null);
    public static final UITexture COVER_INTERFACE_MACHINE_ON_PROXY = fullImage(
            "textures/blocks/cover/cover_interface_machine_on_proxy.png");
    public static final UITexture COVER_INTERFACE_MACHINE_OFF_PROXY = fullImage(
            "textures/blocks/cover/cover_interface_machine_off_proxy.png");

    // BUTTONS

    public static final UITexture BUTTON = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/widget/button.png")
            .imageSize(18, 18)
            .adaptable(2)
            .name(IDs.STANDARD_BUTTON)
            .canApplyTheme()
            .build();

    public static final UITexture BUTTON_BRONZE = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/widget/buttons.png")
            .imageSize(54, 36)
            .xy(18, 0, 18, 18)
            .adaptable(2)
            .name(IDs.BRONZE_BUTTON)
            .build();

    public static final UITexture BUTTON_BRONZE_SELECTED = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/widget/buttons.png")
            .imageSize(54, 36)
            .xy(18, 18, 18, 18)
            .adaptable(2)
            .name(IDs.BRONZE_BUTTON_SELECTED)
            .build();

    public static final UITexture BUTTON_STEEL = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/widget/buttons.png")
            .imageSize(54, 36)
            .xy(36, 0, 18, 18)
            .adaptable(2)
            .name(IDs.STEEL_BUTTON)
            .build();

    public static final UITexture BUTTON_STEEL_SELECTED = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/widget/buttons.png")
            .imageSize(54, 36)
            .xy(36, 18, 18, 18)
            .adaptable(2)
            .name(IDs.STEEL_BUTTON_SELECTED)
            .build();

    public static final UITexture MC_BUTTON = new UITexture.Builder()
            .location("modularui", "gui/widgets/mc_button.png") // todo
            .imageSize(16, 32)
            .uv(0.0f, 0.0f, 1.0f, 0.5f)
            .adaptable(2)
            .build();

    public static final UITexture MC_BUTTON_DISABLED = new UITexture.Builder()
            .location("modularui", "gui/widgets/mc_button_disabled.png") // todo
            .imageSize(16, 16)
            .adaptable(2)
            .build();

    // BUTTON OVERLAYS
    public static final UITexture PROXY_OVERLAY = fullImage("textures/gui/widget/proxy_overlay.png");
    public static final UITexture EXPORT_OVERLAY = fullImage("textures/gui/widget/pattern_export.png");
    public static final UITexture BUTTON_DUAL_OUTPUT = fullImage("textures/gui/widget/button_dual_output_overlay.png");
    public static final UITexture BUTTON_DUAL_COLLAPSE = fullImage("textures/gui/widget/button_dual_collapse_overlay.png");
    public static final UITexture PATTERN_OVERLAY = fullImage("textures/gui/overlay/pattern_overlay.png",  ColorType.DEFAULT);
    public static final UITexture EXTRA_SLOT_OVERLAY = fullImage("textures/gui/overlay/extra_slot.png",  ColorType.DEFAULT);

    public static final UITexture BUTTON_ITEM_OUTPUT = fullImage("textures/gui/widget/button_item_output_overlay.png");
    public static final UITexture BUTTON_FLUID_OUTPUT = fullImage(
            "textures/gui/widget/button_fluid_output_overlay.png");
    public static final UITexture BUTTON_AUTO_COLLAPSE = fullImage(
            "textures/gui/widget/button_auto_collapse_overlay.png");
    public static final UITexture BUTTON_X = fullImage("textures/gui/widget/button_x_overlay.png", ColorType.DEFAULT);
    public static final UITexture BUTTON_CLEAR_GRID = fullImage("textures/gui/widget/button_clear_grid.png",
            ColorType.DEFAULT);

    public static final UITexture BUTTON_CROSS = fullImage("textures/gui/widget/button_cross.png");
    public static final UITexture BUTTON_REDSTONE_ON = fullImage("textures/gui/widget/button_redstone_on.png");
    public static final UITexture BUTTON_REDSTONE_OFF = fullImage("textures/gui/widget/button_redstone_off.png");

    /**
     * 0 = OFF <br />
     * 1 = ON <br />
     */
    public static final UITexture[] BUTTON_POWER = slice("textures/gui/widget/button_power.png", 18, 36, 18, 18,
            ColorType.DEFAULT);
    public static final UITexture[] SOUND_STATE = slice("textures/gui/widget/button_sound_state.png", 18, 36, 18, 18,
            ColorType.DEFAULT);

    public static final UITexture BUTTON_POWER_DETAIL = fullImage("textures/gui/widget/button_power_detail.png");

    /**
     * 0 = DISABLED <br />
     * 1 = ITEM VOID <br />
     * 2 = FLUID VOID <br />
     * 3 = VOID BOTH <br />
     **/
    public static final UITexture[] MULTIBLOCK_VOID = slice("textures/gui/widget/button_void_multiblock.png", 18, 72,
            18, 18, ColorType.DEFAULT);

    public static final UITexture OVERLAY_VOID_NONE = fullImage("textures/gui/widget/button_void_none.png");

    /**
     * 0 = DISABLED <br />
     * 1 = ENABLED <br />
     */
    public static final UITexture[] OVERLAY_DISTINCT_BUSES = slice("textures/gui/widget/button_distinct_buses.png", 18,
            36, 18, 18);

    public static final UITexture[] OVERLAY_ITEM_EXPORT = slice("textures/gui/widget/button_item_export.png",
            18, 36, 18, 18);

    public static final UITexture[] OVERLAY_FLUID_EXPORT = slice("textures/gui/widget/button_fluid_export.png",
            18, 36, 18, 18);

    public static final UITexture[] OVERLAY_ITEM_EXPORT_IMPORT = slice("textures/gui/widget/overlay_item_export_import.png",
            18, 36, 18, 18);

    public static final UITexture[] OVERLAY_FLUID_EXPORT_IMPORT = slice("textures/gui/widget/overlay_fluid_export_import.png",
            18, 36, 18, 18);

    public static final UITexture[] OVERLAY_BATCH = slice("textures/gui/widget/button_batch.png",
            18, 36, 18, 18);
    public static final UITexture[] OVERLAY_RECIPE_LOCK = slice("textures/gui/widget/button_recipe_lock.png",
            18, 36, 18, 18);
    public static final UITexture[] OVERLAY_LACK_ENERGY = slice("textures/gui/widget/button_lack_energy.png",
            18, 36, 18, 18);
    public static final UITexture[] OVERLAY_OVER_FLOW = slice("textures/gui/widget/button_over_flow.png",
            18, 36, 18, 18);
    public static final UITexture BUTTON_STRUCTURE = fullImage("textures/gui/widget/button_structure.png",  ColorType.DEFAULT);

    public static final UITexture OVERLAY_NO_FLEX = fullImage("textures/gui/widget/button_no_flex.png");
    public static final UITexture BUTTON_MULTI_MAP = fullImage("textures/gui/widget/button_multi_map.png",
            ColorType.DEFAULT);
    public static final UITexture BUTTON_MINER_MODES = fullImage("textures/gui/widget/button_miner_modes.png",
            ColorType.DEFAULT);

    // PROGRESS BARS
    public static final UITexture PROGRESS_BAR_ARC_FURNACE = progressBar(
            "textures/gui/progress_bar/progress_bar_arc_furnace.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_ARROW = progressBar("textures/gui/progress_bar/progress_bar_arrow.png",
            ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_ARROW_BRONZE = progressBar(
            "textures/gui/progress_bar/progress_bar_arrow_bronze.png");
    public static final UITexture PROGRESS_BAR_ARROW_STEEL = progressBar(
            "textures/gui/progress_bar/progress_bar_arrow_steel.png");
    public static final UITexture PROGRESS_BAR_ARROW_MULTIPLE = progressBar(
            "textures/gui/progress_bar/progress_bar_arrow_multiple.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_BATH = progressBar("textures/gui/progress_bar/progress_bar_bath.png",
            ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_BENDING = progressBar(
            "textures/gui/progress_bar/progress_bar_bending.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_CANNER = progressBar("textures/gui/progress_bar/progress_bar_canner.png",
            ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_CIRCUIT = progressBar(
            "textures/gui/progress_bar/progress_bar_circuit.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_CIRCUIT_ASSEMBLER = progressBar(
            "textures/gui/progress_bar/progress_bar_circuit_assembler.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_COMPRESS = progressBar(
            "textures/gui/progress_bar/progress_bar_compress.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_COMPRESS_BRONZE = progressBar(
            "textures/gui/progress_bar/progress_bar_compress_bronze.png");
    public static final UITexture PROGRESS_BAR_COMPRESS_STEEL = progressBar(
            "textures/gui/progress_bar/progress_bar_compress_steel.png");
    public static final UITexture PROGRESS_BAR_CRACKING = progressBar(
            "textures/gui/progress_bar/progress_bar_cracking.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_CRACKING_INPUT = progressBar(
            "textures/gui/progress_bar/progress_bar_cracking_2.png", 21, 38, ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_CRYSTALLIZATION = progressBar(
            "textures/gui/progress_bar/progress_bar_crystallization.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_EXTRACT = progressBar(
            "textures/gui/progress_bar/progress_bar_extract.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_EXTRACT_BRONZE = progressBar(
            "textures/gui/progress_bar/progress_bar_extract_bronze.png");
    public static final UITexture PROGRESS_BAR_EXTRACT_STEEL = progressBar(
            "textures/gui/progress_bar/progress_bar_extract_steel.png");
    public static final UITexture PROGRESS_BAR_EXTRUDER = progressBar(
            "textures/gui/progress_bar/progress_bar_extruder.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_FUSION = progressBar("textures/gui/progress_bar/progress_bar_fusion.png",
            ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_GAS_COLLECTOR = progressBar(
            "textures/gui/progress_bar/progress_bar_gas_collector.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_HAMMER = progressBar("textures/gui/progress_bar/progress_bar_hammer.png",
            ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_HAMMER_BRONZE = progressBar(
            "textures/gui/progress_bar/progress_bar_hammer_bronze.png");
    public static final UITexture PROGRESS_BAR_HAMMER_STEEL = progressBar(
            "textures/gui/progress_bar/progress_bar_hammer_steel.png");
    public static final UITexture PROGRESS_BAR_HAMMER_BASE = fullImage(
            "textures/gui/progress_bar/progress_bar_hammer_base.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_HAMMER_BASE_BRONZE = fullImage(
            "textures/gui/progress_bar/progress_bar_hammer_base_bronze.png");
    public static final UITexture PROGRESS_BAR_HAMMER_BASE_STEEL = fullImage(
            "textures/gui/progress_bar/progress_bar_hammer_base_steel.png");
    public static final UITexture PROGRESS_BAR_LATHE = progressBar("textures/gui/progress_bar/progress_bar_lathe.png",
            ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_LATHE_BASE = fullImage(
            "textures/gui/progress_bar/progress_bar_lathe_base.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_MACERATE = progressBar(
            "textures/gui/progress_bar/progress_bar_macerate.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_MACERATE_BRONZE = progressBar(
            "textures/gui/progress_bar/progress_bar_macerate_bronze.png");
    public static final UITexture PROGRESS_BAR_MACERATE_STEEL = progressBar(
            "textures/gui/progress_bar/progress_bar_macerate_steel.png");
    public static final UITexture PROGRESS_BAR_MAGNET = progressBar("textures/gui/progress_bar/progress_bar_magnet.png",
            ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_MASS_FAB = progressBar(
            "textures/gui/progress_bar/progress_bar_mass_fab.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_MIXER = progressBar("textures/gui/progress_bar/progress_bar_mixer.png",
            ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_PACKER = progressBar("textures/gui/progress_bar/progress_bar_packer.png",
            ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_RECYCLER = progressBar(
            "textures/gui/progress_bar/progress_bar_recycler.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_REPLICATOR = progressBar(
            "textures/gui/progress_bar/progress_bar_replicator.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_SIFT = progressBar("textures/gui/progress_bar/progress_bar_sift.png",
            ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_SLICE = progressBar("textures/gui/progress_bar/progress_bar_slice.png",
            ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_UNPACKER = progressBar(
            "textures/gui/progress_bar/progress_bar_unpacker.png", ColorType.DEFAULT);
    public static final UITexture PROGRESS_BAR_WIREMILL = progressBar(
            "textures/gui/progress_bar/progress_bar_wiremill.png", ColorType.DEFAULT);

    public static final UITexture PROGRESS_BAR_CHISEL = progressBar(
            "textures/gui/progress_bar/progress_bar_chisel.png", ColorType.DEFAULT);

    // more custom progress bars
    // MULTIBLOCK BARS
    // todo these boiler empty bars can probably be replaced by using a resized steam slot texture
    public static final UITexture PROGRESS_BAR_BOILER_EMPTY_BRONZE = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/progress_bar/progress_bar_boiler_empty_bronze.png")
            .imageSize(10, 54)
            .adaptable(1)
            .build();
    public static final UITexture PROGRESS_BAR_BOILER_EMPTY_STEEL = new UITexture.Builder()
            .location(GTValues.MODID, "textures/gui/progress_bar/progress_bar_boiler_empty_steel.png")
            .imageSize(10, 54)
            .adaptable(1)
            .build();
    public static final UITexture PROGRESS_BAR_BOILER_FUEL_BRONZE = progressBar(
            "textures/gui/progress_bar/progress_bar_boiler_fuel_bronze.png", 18, 36);
    public static final UITexture PROGRESS_BAR_BOILER_FUEL_STEEL = progressBar(
            "textures/gui/progress_bar/progress_bar_boiler_fuel_steel.png", 18, 36);
    public static final UITexture PROGRESS_BAR_BOILER_HEAT = progressBar(
            "textures/gui/progress_bar/progress_bar_boiler_heat.png");
    public static final UITexture PROGRESS_BAR_ASSEMBLY_LINE = progressBar(
            "textures/gui/progress_bar/progress_bar_assembly_line.png", 54, 144);
    public static final UITexture PROGRESS_BAR_ASSEMBLY_LINE_ARROW = progressBar(
            "textures/gui/progress_bar/progress_bar_assembly_line_arrow.png", 10, 36);
    public static final UITexture PROGRESS_BAR_COKE_OVEN = progressBar(
            "textures/gui/progress_bar/progress_bar_coke_oven.png", 36, 36);
    public static final UITexture PROGRESS_BAR_DISTILLATION_TOWER = progressBar(
            "textures/gui/progress_bar/progress_bar_distillation_tower.png", 66, 116);
    public static final UITexture PROGRESS_BAR_SOLAR_BRONZE = progressBar(
            "textures/gui/progress_bar/progress_bar_solar_bronze.png", 10, 20);
    public static final UITexture PROGRESS_BAR_SOLAR_STEEL = progressBar(
            "textures/gui/progress_bar/progress_bar_solar_steel.png", 10, 20);
    public static final UITexture PROGRESS_BAR_RESEARCH_STATION_1 = progressBar(
            "textures/gui/progress_bar/progress_bar_research_station_1.png", 54, 10);
    public static final UITexture PROGRESS_BAR_RESEARCH_STATION_2 = progressBar(
            "textures/gui/progress_bar/progress_bar_research_station_2.png", 10, 36);
    public static final UITexture PROGRESS_BAR_RESEARCH_STATION_BASE = fullImage(
            "textures/gui/progress_bar/progress_bar_research_station_base.png");
    public static final UITexture PROGRESS_BAR_FUSION_ENERGY = progressBar(
            "textures/gui/progress_bar/progress_bar_fusion_energy.png", 94, 14);
    public static final UITexture PROGRESS_BAR_FUSION_HEAT = progressBar(
            "textures/gui/progress_bar/progress_bar_fusion_heat.png", 94, 14);
    public static final UITexture PROGRESS_BAR_MULTI_ENERGY_YELLOW = progressBar(
            "textures/gui/progress_bar/progress_bar_multi_energy_yellow.png", 190, 14);
    public static final UITexture PROGRESS_BAR_HPCA_COMPUTATION = progressBar(
            "textures/gui/progress_bar/progress_bar_hpca_computation.png", 94, 14);
    public static final UITexture PROGRESS_BAR_LCE_FUEL = progressBar(
            "textures/gui/progress_bar/progress_bar_lce_fuel.png", 62, 14);
    public static final UITexture PROGRESS_BAR_LCE_LUBRICANT = progressBar(
            "textures/gui/progress_bar/progress_bar_lce_lubricant.png", 62, 14);
    public static final UITexture PROGRESS_BAR_LCE_OXYGEN = progressBar(
            "textures/gui/progress_bar/progress_bar_lce_oxygen.png", 62, 14);
    public static final UITexture PROGRESS_BAR_TURBINE_ROTOR_SPEED = progressBar(
            "textures/gui/progress_bar/progress_bar_turbine_rotor_speed.png", 62, 14);
    public static final UITexture PROGRESS_BAR_TURBINE_ROTOR_DURABILITY = progressBar(
            "textures/gui/progress_bar/progress_bar_turbine_rotor_durability.png", 62, 14);
    public static final UITexture PROGRESS_BAR_FLUID_RIG_DEPLETION = progressBar(
            "textures/gui/progress_bar/progress_bar_fluid_rig_depletion.png", 190, 14);
    public static final UITexture PROGRESS_BAR_HEAT_TEMP = progressBar(
            "textures/gui/progress_bar/progress_bar_heat_temp.png", 190, 14);
    // MISC
    // ME hatch/bus
    public static final UITexture NUMBER_BACKGROUND = fullImage("textures/gui/widget/number_background.png");
    public static final UITexture CONFIG_ARROW = fullImage("textures/gui/widget/config_arrow.png");
    public static final UITexture CONFIG_ARROW_DARK = fullImage("textures/gui/widget/config_arrow_dark.png");
    public static final UITexture SELECT_BOX = fullImage("textures/gui/widget/select_box.png");
    public static final UITexture BUTTON_AUTO_PULL = fullImage("textures/gui/widget/button_me_auto_pull.png");
    public static final UITexture ARROW_DOUBLE = fullImage("textures/gui/widget/arrow_double.png");
    public static final UITexture ARROW_OPPOSITE = fullImage("textures/gui/widget/opposite_arrows.png");
    public static final UITexture[] AUTO_PULL = slice("textures/gui/widget/button_me_auto_pull.png",
            16, 32, 16, 16, ColorType.DEFAULT);

    public static final UITexture RGB_GRADIENT = fullImage("textures/gui/widget/rgb_gradient.png");

    public static final UITexture MAINTENANCE_ICON = fullImage("textures/gui/widget/button_maintenance.png",
            ColorType.DEFAULT);

    public static final UITexture SPEAKER_ICON = fullImage("textures/gui/widget/speaker.png");
    public static void init() {/**/}

    public static UITexture fullImage(String path) {
        return fullImage(path, null);
    }

    public static UITexture fullImage(String path, @Nullable ColorType colorType) {
        return UITexture.fullImage(GTValues.MODID, path, colorType);
    }

    @SuppressWarnings("SameParameterValue")
    public static UITexture[] slice(String path, int imageWidth, int imageHeight, int sliceWidth, int sliceHeight) {
        return slice(path, imageWidth, imageHeight, sliceWidth, sliceHeight, null);
    }

    @SuppressWarnings("SameParameterValue")
    public static UITexture[] slice(String path, int imageWidth, int imageHeight, int sliceWidth, int sliceHeight,
                                     @Nullable ColorType colorType) {
        if (imageWidth % sliceWidth != 0 || imageHeight % sliceHeight != 0)
            throw new IllegalArgumentException("Slice height and slice width must divide the image evenly!");

        int countX = imageWidth / sliceWidth;
        int countY = imageHeight / sliceHeight;
        UITexture[] slices = new UITexture[countX * countY];

        for (int indexX = 0; indexX < countX; indexX++) {
            for (int indexY = 0; indexY < countY; indexY++) {
                slices[(indexX * countX) + indexY] = UITexture.builder()
                        .location(GTValues.MODID, path)
                        .colorType(colorType)
                        .imageSize(imageWidth, imageHeight)
                        .xy(indexX * sliceWidth, indexY * sliceHeight, sliceWidth, sliceHeight)
                        .build();
            }
        }
        return slices;
    }

    public static UITexture[] slice(String path, int imageWidth, int imageHeight) {
        return slice(path, imageWidth, imageHeight, null);
    }

    public static UITexture[] slice(String path, int imageWidth, int imageHeight, @Nullable ColorType colorType) {
        int sliceSize = Math.min(imageWidth, imageHeight);
        return slice(path, imageWidth, imageHeight, sliceSize, sliceSize, colorType);
    }

    public static IDrawable animated(String path, int imageWidth, int imageHeight, int rate) {
        return dynamic(slice(path, imageWidth, imageHeight, null), rate);
    }

    public static IDrawable animated(String path, int imageWidth, int imageHeight, @Nullable ColorType colorType,
                                      int rate) {
        return dynamic(slice(path, imageWidth, imageHeight, colorType), rate);
    }

    public static UITexture progressBar(String path) {
        return progressBar(path, 20, 40);
    }

    public static UITexture progressBar(String path, @Nullable ColorType colorType) {
        return progressBar(path, 20, 40, colorType);
    }

    public static UITexture progressBar(String path, int width, int height) {
        return progressBar(path, width, height, null);
    }

    public static UITexture progressBar(String path, int width, int height, @Nullable ColorType colorType) {
        return new UITexture.Builder()
                .location(GTValues.MODID, path)
                .imageSize(width, height)
                .colorType(colorType)
                .build();
    }

    // todo steam logos? multi indicator blinking logos?
    public static @NotNull UITexture getLogo(GTGuiTheme theme) {
        if (theme != null) {
            UITexture logo = theme.getLogo();
            if (logo != null) return logo;
        }
        return GTValues.XMAS.get() ? GREGTECH_LOGO_XMAS : GREGTECH_LOGO;
    }

    public static IDrawable dynamic(UITexture[] textures, int rate) {
        return new IDrawable() {

            int tick = 0;
            int index = 0;

            @Override
            public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
                int a = tick++ % rate; // this makes rate per frame ?
                if (a == 0) index++;
                textures[index % textures.length].draw(context, x, y, width, height, widgetTheme);
            }
        };
    }

    // region Godforge Textures

    public static final UITexture BACKGROUND_GLOW_ORANGE = fullImage("textures/gui/background/orange_glow");
    public static final UITexture BACKGROUND_GLOW_PURPLE = fullImage("textures/gui/background/purple_glow");
    public static final UITexture BACKGROUND_GLOW_BLUE = fullImage("textures/gui/background/blue_glow");
    public static final UITexture BACKGROUND_GLOW_GREEN = fullImage("textures/gui/background/green_glow");
    public static final UITexture BACKGROUND_GLOW_RED = fullImage("textures/gui/background/red_glow");
    public static final UITexture BACKGROUND_GLOW_WHITE = fullImage("textures/gui/background/white_glow");
    public static final UITexture BACKGROUND_GLOW_WHITE_HALF = fullImage("textures/gui/background/white_glow_half");
    public static final UITexture BACKGROUND_GLOW_RAINBOW = fullImage("textures/gui/background/rainbow_glow");
    public static final UITexture BACKGROUND_SPACE = fullImage("textures/gui/background/space");

    public static final UITexture PROGRESSBAR_GODFORGE_PLASMA = progressBar(
        "textures/gui/progressbar/godforge_plasma.png", 20, 40);
    public static final UITexture PROGRESSBAR_GODFORGE_MILESTONE_BACKGROUND = progressBar(
        "textures/gui/progressbar/godforge_progressbar_background.png", 20, 40);
    public static final UITexture PROGRESSBAR_GODFORGE_MILESTONE_BLUE = progressBar(
        "textures/gui/progressbar/godforge_progressbar_blue.png", 20, 40);
    public static final UITexture PROGRESSBAR_GODFORGE_MILESTONE_RED = progressBar(
        "textures/gui/progressbar/godforge_progressbar_red.png", 20, 40);
    public static final UITexture PROGRESSBAR_GODFORGE_MILESTONE_PURPLE = progressBar(
        "textures/gui/progressbar/godforge_progressbar_purple.png", 20, 40);
    public static final UITexture PROGRESSBAR_GODFORGE_MILESTONE_RAINBOW = progressBar(
        "textures/gui/progressbar/godforge_progressbar_rainbow.png", 20, 40);
    public static final UITexture PROGRESSBAR_GODFORGE_MILESTONE_BLUE_INVERTED = progressBar(
        "textures/gui/progressbar/godforge_progressbar_blue_inverted.png", 20, 40);
    public static final UITexture PROGRESSBAR_GODFORGE_MILESTONE_RED_INVERTED = progressBar(
        "textures/gui/progressbar/godforge_progressbar_red_inverted.png", 20, 40);
    public static final UITexture PROGRESSBAR_GODFORGE_MILESTONE_PURPLE_INVERTED = progressBar(
        "textures/gui/progressbar/godforge_progressbar_purple_inverted.png", 20, 40);
    public static final UITexture PROGRESSBAR_GODFORGE_MILESTONE_RAINBOW_INVERTED = progressBar(
        "textures/gui/progressbar/godforge_progressbar_rainbow_inverted.png", 20, 40);

    public static final UITexture TT_OVERLAY_BUTTON_POWER_SWITCH_ON = fullImage(
        "textures/gui/overlay_button/power_switch_on");
    public static final UITexture TT_OVERLAY_BUTTON_POWER_SWITCH_OFF = fullImage(
        "textures/gui/overlay_button/power_switch_off");
    public static final UITexture TT_OVERLAY_BUTTON_POWER_SWITCH_DISABLED = fullImage(
        "textures/gui/overlay_button/power_switch_disabled");
    public static final UITexture TT_OVERLAY_BUTTON_ARROW_BLUE_UP = fullImage(
        "textures/gui/overlay_button/arrow_blue_up");
    public static final UITexture TT_OVERLAY_BUTTON_BATTERY_ON = fullImage(
        "textures/gui/overlay_button/battery_on");
    public static final UITexture TT_OVERLAY_BUTTON_BATTERY_OFF = fullImage(
        "textures/gui/overlay_button/battery_off");
    public static final UITexture TT_OVERLAY_BUTTON_FLAG = fullImage("textures/gui/overlay_button/flag");
    public static final UITexture TT_OVERLAY_BUTTON_HEART = fullImage("textures/gui/overlay_button/heart");
    public static final UITexture TT_OVERLAY_BUTTON_RAINBOW_SPIRAL = fullImage(
        "textures/gui/overlay_button/rainbow_spiral");
    public static final UITexture TT_OVERLAY_BUTTON_STATISTICS = fullImage(
        "textures/gui/overlay_button/statistics");
    public static final UITexture TT_OVERLAY_BUTTON_HEAT_ON = fullImage(
        "textures/gui/overlay_button/heat_on");
    public static final UITexture TT_OVERLAY_BUTTON_HEAT_OFF = fullImage(
        "textures/gui/overlay_button/heat_off");
    public static final UITexture TT_OVERLAY_BUTTON_INPUT_SEPARATION = fullImage(
        "textures/gui/overlay_button/input_separation_on");
    public static final UITexture TT_OVERLAY_BUTTON_INPUT_SEPARATION_OFF = fullImage(
        "textures/gui/overlay_button/input_separation_off");
    public static final UITexture TT_OVERLAY_BUTTON_BATCH_MODE = fullImage(
        "textures/gui/overlay_button/batch_mode_on");
    public static final UITexture TT_OVERLAY_BUTTON_BATCH_MODE_OFF = fullImage(
        "textures/gui/overlay_button/batch_mode_off");
    public static final UITexture TT_OVERLAY_BUTTON_LOAF_MODE = fullImage(
        "textures/gui/overlay_button/loaf_mode_on");
    public static final UITexture TT_OVERLAY_BUTTON_LOAF_MODE_OFF = fullImage(
        "textures/gui/overlay_button/loaf_mode_off");
    public static final UITexture TT_OVERLAY_BUTTON_RECIPE_LOCKED = fullImage(
        "textures/gui/overlay_button/recipe_locked");
    public static final UITexture TT_OVERLAY_BUTTON_RECIPE_UNLOCKED = fullImage(
        "textures/gui/overlay_button/recipe_unlocked");
    public static final UITexture TT_OVERLAY_BUTTON_VOIDING_OFF = fullImage(
        "textures/gui/overlay_button/voiding_disabled");
    public static final UITexture TT_OVERLAY_BUTTON_VOIDING_ITEMS = fullImage(
        "textures/gui/overlay_button/voiding_items");
    public static final UITexture TT_OVERLAY_BUTTON_VOIDING_FLUIDS = fullImage(
        "textures/gui/overlay_button/voiding_fluids");
    public static final UITexture TT_OVERLAY_BUTTON_VOIDING_BOTH = fullImage(
        "textures/gui/overlay_button/voiding_both");
    public static final UITexture TT_OVERLAY_BUTTON_STRUCTURE_CHECK = fullImage(
        "textures/gui/overlay_button/structure_check_on");
    public static final UITexture TT_OVERLAY_BUTTON_STRUCTURE_CHECK_OFF = fullImage(
        "textures/gui/overlay_button/structure_check_off");
    public static final UITexture TT_OVERLAY_BUTTON_FURNACE_MODE = fullImage(
        "textures/gui/overlay_button/furnace_mode_on");
    public static final UITexture TT_OVERLAY_BUTTON_FURNACE_MODE_OFF = fullImage(
        "textures/gui/overlay_button/furnace_mode_off");
    public static final UITexture TT_OVERLAY_BUTTON_POWER_PANEL = fullImage(
        "textures/gui/overlay_button/power_panel");
    public static final UITexture TT_OVERLAY_CYCLIC_BLUE = fullImage("textures/gui/overlay_button/cyclic_blue");
    public static final UITexture TT_OVERLAY_EJECTION_LOCKED = fullImage(
        "textures/gui/overlay_button/eject_disabled");
    public static final UITexture TT_OVERLAY_EJECTION_ON = fullImage("textures/gui/overlay_button/eject");

    public static final UITexture TT_BUTTON_CELESTIAL_32x32 = fullImage("textures/gui/button/celestial");

    public static final UITexture PICTURE_GODFORGE_LOGO = fullImage("textures/gui/picture/gorge_logo");
    public static final UITexture PICTURE_GODFORGE_MILESTONE_CHARGE = fullImage(
        "textures/gui/picture/milestone_charge");
    public static final UITexture PICTURE_GODFORGE_MILESTONE_CONVERSION = fullImage(
        "textures/gui/picture/milestone_conversion");
    public static final UITexture PICTURE_GODFORGE_MILESTONE_CATALYST = fullImage(
        "textures/gui/picture/milestone_catalyst");
    public static final UITexture PICTURE_GODFORGE_MILESTONE_COMPOSITION = fullImage(
        "textures/gui/picture/milestone_composition");
    public static final UITexture PICTURE_GODFORGE_THANKS = fullImage(
        "textures/gui/picture/thanks");
    public static final UITexture PICTURE_GODFORGE_MILESTONE_CHARGE_GLOW = fullImage(
        "textures/gui/picture/milestone_charge_glow");
    public static final UITexture PICTURE_GODFORGE_MILESTONE_CONVERSION_GLOW = fullImage(
        "textures/gui/picture/milestone_conversion_glow");
    public static final UITexture PICTURE_GODFORGE_MILESTONE_CATALYST_GLOW = fullImage(
        "textures/gui/picture/milestone_catalyst_glow");
    public static final UITexture PICTURE_GODFORGE_MILESTONE_COMPOSITION_GLOW = fullImage(
        "textures/gui/picture/milestone_composition_glow");

    public static final UITexture CLOSE_BUTTON_HOLLOW = fullImage("textures/gui/button/transparent_x_10x10");
    public static final UITexture GODFORGE_SOUND_ON = fullImage("textures/gui/overlay_button/sound_on");
    public static final UITexture GODFORGE_SOUND_OFF = fullImage("textures/gui/overlay_button/sound_off");

    public static final UITexture BUTTON_OUTLINE_HOLLOW = fullImage("textures/gui/button/transparent_16x16");
    public static final UITexture BUTTON_OUTLINE_HOLLOW_PRESSED = fullImage("textures/gui/button/transparent_pressed_16x16");
    public static final UITexture BUTTON_STANDARD = BUTTON_OUTLINE_HOLLOW;
    public static final UITexture BUTTON_STANDARD_PRESSED = BUTTON_OUTLINE_HOLLOW_PRESSED;
    public static final UITexture UNSELECTED_OPTION = fullImage("textures/gui/picture/unselected_option");
    public static final UITexture BACKGROUND_TEXT_FIELD = fullImage("textures/gui/background/screen_blue");
    public static final UITexture PICTURE_INFO = fullImage("textures/gui/picture/information_round");
    public static final UITexture BACKGROUND_STAR = fullImage("textures/gui/background/star");
    public static final UITexture BACKGROUND_STANDARD = UITexture.builder()
        .location(GTValues.MODID, "gui/background/singleblock_default")
        .imageSize(176, 166)
        .adaptable(4)
        .canApplyTheme()
        .build();
    public static final UITexture BUTTON_SPACE_32x16 = fullImage("textures/gui/button/standard_16x16");
    public static final UITexture BUTTON_SPACE_PRESSED_32x16 = fullImage("textures/gui/button/standard_light_16x16");
    public static final UITexture BUTTON_BOXED_CHECKMARK_18x18 = fullImage("textures/gui/button/boxed_checkmark");
    public static final UITexture BUTTON_BOXED_EXCLAMATION_POINT_18x18 = fullImage(
        "textures/gui/button/boxed_exclamation_point");
    public static final UITexture BUTTON_STANDARD_DISABLED = UITexture.builder()
        .location(GTValues.MODID, "gui/button/standard_disabled")
        .imageSize(18, 18)
        .adaptable(1)
        .canApplyTheme()
        .build();

    public static final UITexture PICTURE_UPGRADE_CONNECTOR_BLUE = fullImage(
        "textures/gui/picture/connector_blue");
    public static final UITexture PICTURE_UPGRADE_CONNECTOR_BLUE_OPAQUE = fullImage(
        "textures/gui/picture/connector_blue_opaque");
    public static final UITexture PICTURE_UPGRADE_CONNECTOR_RED = fullImage(
        "textures/gui/picture/connector_red");
    public static final UITexture PICTURE_UPGRADE_CONNECTOR_RED_OPAQUE = fullImage(
        "textures/gui/picture/connector_red_opaque");
    public static final UITexture PICTURE_UPGRADE_CONNECTOR_PURPLE = fullImage(
        "textures/gui/picture/connector_purple");
    public static final UITexture PICTURE_UPGRADE_CONNECTOR_PURPLE_OPAQUE = fullImage(
        "textures/gui/picture/connector_purple_opaque");
    public static final UITexture PICTURE_UPGRADE_CONNECTOR_ORANGE = fullImage(
        "textures/gui/picture/connector_orange");
    public static final UITexture PICTURE_UPGRADE_CONNECTOR_ORANGE_OPAQUE = fullImage(
        "textures/gui/picture/connector_orange_opaque");
    public static final UITexture PICTURE_UPGRADE_CONNECTOR_GREEN = fullImage(
        "textures/gui/picture/connector_green");
    public static final UITexture PICTURE_UPGRADE_CONNECTOR_GREEN_OPAQUE = fullImage(
        "textures/gui/picture/connector_green_opaque");
    public static final UITexture PICTURE_UPGRADE_CONNECTOR_RAINBOW = fullImage(
        "textures/gui/picture/rainbow_square");
    public static final UITexture PICTURE_UPGRADE_CONNECTOR_RAINBOW_OPAQUE = fullImage(
        "textures/gui/picture/rainbow_square");

    public static final UITexture PICTURE_OVERLAY_BLUE = UITexture.builder()
        .location(GTValues.MODID, "gui/picture/overlay_blue")
        .nonOpaque()
        .build();
    public static final UITexture PICTURE_OVERLAY_GREEN = UITexture.builder()
        .location(GTValues.MODID, "gui/picture/overlay_green")
        .nonOpaque()
        .build();
    public static final UITexture PICTURE_OVERLAY_ORANGE = UITexture.builder()
        .location(GTValues.MODID, "gui/picture/overlay_orange")
        .nonOpaque()
        .build();
    public static final UITexture PICTURE_OVERLAY_PURPLE = UITexture.builder()
        .location(GTValues.MODID, "gui/picture/overlay_purple")
        .nonOpaque()
        .build();
    public static final UITexture PICTURE_OVERLAY_RED = UITexture.builder()
        .location(GTValues.MODID, "gui/picture/overlay_red")
        .nonOpaque()
        .build();
    public static final UITexture SLOT_OUTLINE_GREEN = PICTURE_OVERLAY_GREEN;

    public static final UITexture GREEN_CHECKMARK_11x9 = fullImage("textures/gui/picture/green_checkmark");
    public static final UITexture OVERLAY_BUTTON_CHECKMARK = GREEN_CHECKMARK_11x9;
    public static final UITexture OVERLAY_BUTTON_CROSS = BUTTON_CROSS;

    public static final UITexture PROGRESSBAR_GODFORGE_MILESTONE_ORANGE = PROGRESSBAR_GODFORGE_MILESTONE_RED;
    public static final UITexture PROGRESSBAR_GODFORGE_MILESTONE_GREEN = PROGRESSBAR_GODFORGE_MILESTONE_BLUE;
    public static final UITexture PROGRESSBAR_GODFORGE_MILESTONE_ORANGE_INVERTED = PROGRESSBAR_GODFORGE_MILESTONE_RED_INVERTED;
    public static final UITexture PROGRESSBAR_GODFORGE_MILESTONE_GREEN_INVERTED = PROGRESSBAR_GODFORGE_MILESTONE_BLUE_INVERTED;

    // endregion
}
