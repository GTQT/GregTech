/*
 * Inspired by Susy-Core DimensionBreathabilityHandler
 * (https://github.com/SymmetricDevs/Susy-Core, LGPLv3)
 */
package gregtech.api.util;

import gregtech.common.EventHandlers;
import net.minecraft.entity.player.EntityPlayer;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Applies environmental damage in specific dimensions.
 * <p>
 * Armor resistance, damage delivery and armor wear all come from {@link Hazard},
 * so a dimension hazard behaves exactly like any other source of the same hazard
 * type — there is no separate damage path to keep in sync.
 * <p>
 * Addons can register hazards via {@link #registerHazard(int, Hazard, float)}.
 */
public class DimensionHazardHandler {

    private static final Map<Integer, DimensionHazard> DIMENSION_HAZARDS = new HashMap<>();

    static {
        // Nether: heat damage, 2.0 per second (applied every 20 ticks)
        DIMENSION_HAZARDS.put(-1, new DimensionHazard(Hazard.HEAT, 2.0f));
    }

    /**
     * Register a dimension hazard. Call from your mod's init phase.
     *
     * @param dimId  dimension ID
     * @param hazard the hazard to inflict
     * @param damage base damage per second (applied every 20 ticks)
     */
    public static void registerHazard(int dimId, @NotNull Hazard hazard, float damage) {
        DIMENSION_HAZARDS.put(dimId, new DimensionHazard(hazard, damage));
    }

    /**
     * Called every player tick from {@link EventHandlers#onPlayerTick}.
     * Applies damage every 20 ticks (once per second).
     */
    public static void onPlayerTick(EntityPlayer player) {
        if (player.isCreative() || player.isSpectator()) return;
        if (player.world.isRemote) return;
        if (player.ticksExisted % 20 != 0) return;

        DimensionHazard hazard = DIMENSION_HAZARDS.get(player.dimension);
        if (hazard == null) return;

        hazard.hazard().applyTo(player, hazard.baseDamage());
    }
}
