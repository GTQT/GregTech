package gregtech.api.terminal2;

import gregtech.api.util.FileUtility;
import gregtech.api.util.GTUtility;
import gregtech.common.ConfigHolder;
import gregtech.common.terminal2.AppStoreApp;
import gregtech.common.terminal2.CapeSelectorApp;
import gregtech.common.terminal2.SettingsApp;
import gregtech.common.terminal2.StorageApp;
import gregtech.common.terminal2.TeleportApp;
import gregtech.common.terminal2.game.maze.MazeApp;
import gregtech.common.terminal2.game.minesweeper.MinesweeperApp;
import gregtech.common.terminal2.game.pong.PongApp;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class Terminal2 {

    public static final int SCREEN_WIDTH = 340, SCREEN_HEIGHT = 240;
    public static final Map<ResourceLocation, ITerminalApp> appMap = new LinkedHashMap<>();
    public static final ResourceLocation HOME_ID = GTUtility.gregtechId("home");

    @SideOnly(Side.CLIENT)
    public static File TERMINAL_PATH;

    /**
     * The page switcher of the currently open terminal, wired up by the terminal item. Apps that want to jump to another
     * app — the app store, for instance — call {@link #openApp(ResourceLocation)} rather than reaching into the terminal
     * item's UI themselves.
     */
    private static Consumer<ResourceLocation> pageSwitcher;

    /**
     * Runs the page switch on the next client tick, because a click handler may not restructure the widget tree it is
     * being called from.
     */
    private static ResourceLocation pendingApp;

    public static void init() {
        if (FMLCommonHandler.instance().getSide().isClient()) {
            TERMINAL_PATH = new File(Loader.instance().getConfigDir(), ConfigHolder.client.terminalRootPath);
            FileUtility.extractJarFiles("/assets/gregtech/terminal", TERMINAL_PATH, false);
            Terminal2Theme.init();
        }
        registerApp(GTUtility.gregtechId("settings"), new SettingsApp());
        registerApp(GTUtility.gregtechId("capes"), new CapeSelectorApp());
        registerApp(GTUtility.gregtechId("store"), new AppStoreApp());
        registerApp(GTUtility.gregtechId("storage"), new StorageApp());

        // Games. Client side only in effect, but registered on both sides so the page list stays in step.
        registerApp(GTUtility.gregtechId("minesweeper"), new MinesweeperApp());
        registerApp(GTUtility.gregtechId("maze"), new MazeApp());
        registerApp(GTUtility.gregtechId("pong"), new PongApp());

        registerApp(GTUtility.gregtechId("teleporter"), new TeleportApp());

        /*
         * TODO potential apps to create/port:
         * guide/tutorial app using mui2 rich text and markup files of some sort
         * recipe chart (if anyone actually wants to port it)
         */
    }

    /**
     * Register a terminal app. Call this during initialization.
     *
     * @param id A unique identifier for your app. This is used to determine the lang key for the app name tooltip.
     *           <p>
     *           e.g. <code>gregtech:capes</code> -> <code>terminal.app.gregtech.capes</code>
     */
    public static void registerApp(ResourceLocation id, ITerminalApp app) {
        if (appMap.containsKey(id) || HOME_ID.equals(id)) {
            throw new AssertionError("A terminal app with id " + id + " already exists!");
        }
        appMap.put(id, app);
    }

    /**
     * Switches the open terminal over to the given app, as if its icon on the home screen had been clicked. Does nothing
     * if that app is not registered, or if no terminal is open.
     */
    public static void openApp(ResourceLocation id) {
        if (!appMap.containsKey(id)) {
            throw new AssertionError("No terminal app with id " + id + " is registered!");
        }
        pendingApp = id;
    }

    /** Wired up by the terminal item, cleared when its UI closes. */
    public static void setPageSwitcher(@Nullable Consumer<ResourceLocation> switcher) {
        pageSwitcher = switcher;
        pendingApp = null;
    }

    /**
     * Called once per client tick while a terminal is open; performs any page switch queued by {@link #openApp}.
     */
    public static void tickPageSwitch() {
        if (pendingApp == null) return;
        ResourceLocation id = pendingApp;
        pendingApp = null;
        if (pageSwitcher != null) {
            pageSwitcher.accept(id);
        }
    }
}
