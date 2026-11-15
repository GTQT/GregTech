package gregtech.api.worldgen.generator;

import gregtech.api.util.Mods;

import net.minecraft.world.World;

import xy177.farmersfuturedelight.core.FFDHeightHooks;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tells GregTech where the vertical bounds of a world are.
 *
 * <p>Vanilla (and most modded world types) use Y=0..255. Farmer's Future Delight's "Caves &amp; Cliffs" world type
 * ({@code ffd_cac}) extends the overworld to Y=-64..319, and its height hooks also extend any overworld when the
 * Cave Biomes API or Depths Update is installed. The canonical answer therefore lives in Farmer's Future Delight's
 * {@code FFDHeightHooks}.</p>
 *
 * <p>Farmer's Future Delight is a compile-only optional dependency, so the hook is called directly, but only after
 * {@link Mods.FarmersFutureDelight} reports the mod as loaded. The actual reference sits in {@link FFDHeightAccess}
 * so that loading this class never loads a Farmer's Future Delight class when the mod is absent.</p>
 */
public final class WorldHeightCompat {

    private static final String FFD_WORLD_TYPE = "ffd_cac";

    public static final int VANILLA_MIN_Y = 0;
    public static final int VANILLA_MAX_Y_EXCLUSIVE = 256;
    public static final int FFD_MIN_Y = -64;
    public static final int FFD_MAX_Y_EXCLUSIVE = 320;

    private static final Map<World, Boolean> EXTENDED = Collections.synchronizedMap(new WeakHashMap<>());

    private static final AutoCloseable NO_OP_SCOPE = () -> {};

    private WorldHeightCompat() {}

    /**
     * @return {@code true} when the world generates blocks outside of Y=0..255 (Farmer's Future Delight height)
     */
    public static boolean isExtended(World world) {
        if (world == null || world.isRemote) {
            return false;
        }
        // Farmer's Future Delight only extends the overworld; the nether/end of the same save share the world type
        // name but keep their vanilla heights.
        if (world.provider == null || world.provider.getDimension() != 0) {
            return false;
        }
        Boolean cached = EXTENDED.get(world);
        if (cached != null) {
            return cached;
        }
        boolean extended = computeExtended(world);
        // Only positive results are cached. Farmer's Future Delight runs other mods' world generators inside a
        // "legacy height" scope, where its hooks report Y=0..255 even for extended worlds; a negative result may
        // therefore be an artefact of that scope rather than a property of the world.
        if (extended) {
            EXTENDED.put(world, Boolean.TRUE);
        }
        return extended;
    }

    /**
     * Enters Farmer's Future Delight's "extended height" worldgen scope for the duration of the returned handle.
     *
     * <p>Farmer's Future Delight wraps the Forge world-generator bridge in a legacy height scope, which makes
     * {@code World.setBlockState} ignore blocks below Y=0 and makes its height hooks report Y=0..255. A mod that
     * understands the extended height range must opt back into it; this is what Farmer's Future Delight's own
     * world generator does. When Farmer's Future Delight is absent, or the world is not extended, this returns a
     * no-op handle.</p>
     *
     * @param world the world which is about to be generated in
     * @return a handle that restores the previous scope when closed
     */
    public static AutoCloseable enterExtendedWorldgen(World world) {
        if (!Mods.FarmersFutureDelight.isModLoaded() || !isExtended(world)) {
            return NO_OP_SCOPE;
        }
        try {
            AutoCloseable scope = FFDHeightAccess.enterExtended(world);
            return scope != null ? scope : NO_OP_SCOPE;
        } catch (Throwable ignored) {
            return NO_OP_SCOPE;
        }
    }

    /**
     * @return lowest block Y that can be generated in the given world
     */
    public static int minY(World world) {
        return isExtended(world) ? FFD_MIN_Y : VANILLA_MIN_Y;
    }

    /**
     * @return exclusive upper bound of the block Y range of the given world
     */
    public static int maxYExclusive(World world) {
        return isExtended(world) ? FFD_MAX_Y_EXCLUSIVE : VANILLA_MAX_Y_EXCLUSIVE;
    }

    /**
     * Maps a height registered for the vanilla overworld (Y=0..255) into Farmer's Future Delight's extended
     * overworld (Y=-64..319), preserving the relative position inside the world's vertical span. Vein definitions
     * use this to derive their {@code deepYields} range when none was specified explicitly.
     *
     * @param y a height limit written for a normal overworld
     * @return the same height limit expressed in the extended overworld
     */
    public static int scaleToExtendedHeight(int y) {
        if (y == Integer.MIN_VALUE || y == Integer.MAX_VALUE) {
            return y;
        }
        float factor = (float) (FFD_MAX_Y_EXCLUSIVE - FFD_MIN_Y) / (float) VANILLA_MAX_Y_EXCLUSIVE;
        int mapped = FFD_MIN_Y + Math.round(y * factor);
        return Math.max(FFD_MIN_Y, Math.min(FFD_MAX_Y_EXCLUSIVE - 1, mapped));
    }

    private static boolean computeExtended(World world) {
        if (!Mods.FarmersFutureDelight.isModLoaded()) {
            return false;
        }
        // The world type name is the one signal that does not depend on Farmer's Future Delight's thread-local
        // worldgen height scope (see isExtended), so it is checked first.
        net.minecraft.world.storage.WorldInfo info = world.getWorldInfo();
        if (info != null && info.getTerrainType() != null
                && FFD_WORLD_TYPE.equals(info.getTerrainType().getName())) {
            return true;
        }
        Boolean extended = FFDHeightAccess.isExtended(world);
        // A failure inside the hook must never stop world generation.
        return Boolean.TRUE.equals(extended);
    }
}

/**
 * Holder for the direct Farmer's Future Delight height hook. This class is only loaded when the mod is present.
 */
final class FFDHeightAccess {

    private FFDHeightAccess() {}

    /**
     * @return whether the world uses Farmer's Future Delight's extended height, or {@code null} if the hook could not
     *         be evaluated
     */
    static Boolean isExtended(World world) {
        try {
            // usesExtendedHeight is private; minY is the public view of the same decision (-64 only for extended
            // overworlds). It also covers height extensions granted by the Cave Biomes API / Depths Update.
            return FFDHeightHooks.minY(world) < 0;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * @return a handle that restores the previous worldgen height scope, or {@code null} if the hook failed
     */
    static AutoCloseable enterExtended(World world) {
        try {
            return FFDHeightHooks.enterExtendedWorldgenHeight();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
