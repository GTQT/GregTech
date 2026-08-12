package gregtech.api.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Radiation debuff mechanics: when a player carries radioactive items without
 * sufficient protection, one random missing debuff (out of six) is applied.
 * Duration and potency scale with the radiation strength of the source.
 * <p>
 * Called from {@code MetaPrefixItem#onUpdate} next to the radiation damage
 * branch; the per-tick debounce keeps multiple carried stacks from stacking
 * several debuffs in the same tick.
 */
public final class RadiationEffectUtil {

    /** The six possible radiation debuffs. */
    private static final Potion[] RADIATION_EFFECTS = {
            MobEffects.SLOWNESS,
            MobEffects.MINING_FATIGUE,
            MobEffects.NAUSEA,
            MobEffects.WEAKNESS,
            MobEffects.HUNGER,
            MobEffects.POISON
    };

    /** Radiation strength below which no debuff is applied. */
    private static final float MIN_STRENGTH = 0.5f;

    private static final Random RANDOM = new Random();
    private static long lastAppliedTick = -1;

    private RadiationEffectUtil() {}

    /**
     * Applies a random missing radiation debuff to the player, if any.
     *
     * @param player    the affected player
     * @param radiation strength of the radiation source (radioactivity or
     *                   fission decay damage)
     */
    public static void applyDebuff(EntityPlayer player, float radiation) {
        if (radiation < MIN_STRENGTH) return;
        if (player.isCreative() || player.isSpectator()) return;
        if (player.world.isRemote) return;

        // Sufficient armor protection makes the player immune to debuffs
        float resistance = EntityDamageUtil.getArmorResistance(player,
                EntityDamageUtil.ResistanceType.RADIATION);
        if (resistance <= 0.01f) return;

        // Debounce: only one debuff per tick, no matter how many stacks are carried
        long worldTime = player.world.getTotalWorldTime();
        if (worldTime == lastAppliedTick) return;

        // Collect the debuffs the player does not currently have
        List<Potion> missing = new ArrayList<>();
        for (Potion effect : RADIATION_EFFECTS) {
            if (player.getActivePotionEffect(effect) == null) {
                missing.add(effect);
            }
        }
        if (missing.isEmpty()) return;

        // Grade by radiation strength: tier 1..4 → duration and potency
        int tier = getTier(radiation);
        int duration = tier * 20 * 20; // 1..4 → 20s..80s
        int amplifier = tier >= 4 ? 2 : tier >= 3 ? 1 : 0;

        Potion chosen = missing.get(RANDOM.nextInt(missing.size()));
        player.addPotionEffect(new PotionEffect(chosen, duration, amplifier, false, true));
        lastAppliedTick = worldTime;
    }

    /**
     * Maps radiation strength to a 1..4 tier:
     * <1.0 → 1, 1.0-2.9 → 2, 3.0-4.9 → 3, ≥5.0 → 4
     */
    private static int getTier(float radiation) {
        if (radiation >= 5.0f) return 4;
        if (radiation >= 3.0f) return 3;
        if (radiation >= 1.0f) return 2;
        return 1;
    }
}
