/*
 * Inspired by Susy-Core DimensionBreathabilityHandler
 * (https://github.com/SymmetricDevs/Susy-Core, LGPLv3)
 */
package gregtech.common;

import gregtech.api.damagesources.DamageSources;
import gregtech.api.items.armor.ArmorMetaItem;
import gregtech.api.util.EntityDamageUtil;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Applies environmental damage in specific dimensions.
 * Armor heat/radiation/poison resistance reduces the damage via the chest slot,
 * following the same pattern as {@code MetaPrefixItem}.
 * <p>
 * Addons can register hazards via {@link #registerHazard(int, DamageSource, float, HazardType)}.
 */
public class DimensionHazardHandler {

    public enum HazardType {
        HEAT, RADIATION, POISON
    }

    public static class Hazard {
        public final DamageSource damageSource;
        public final float baseDamage;
        public final HazardType type;

        public Hazard(DamageSource source, float damage, HazardType type) {
            this.damageSource = source;
            this.baseDamage = damage;
            this.type = type;
        }
    }

    private static final Map<Integer, Hazard> DIMENSION_HAZARDS = new HashMap<>();

    static {
        // Nether: heat damage, 2.0 per second (applied every 20 ticks)
        DIMENSION_HAZARDS.put(-1, new Hazard(DamageSources.getHeatDamage(), 2.0f, HazardType.HEAT));
    }

    /**
     * Register a dimension hazard. Call from your mod's init phase.
     *
     * @param dimId  dimension ID
     * @param source the damage source to use
     * @param damage base damage per second (applied every 20 ticks)
     * @param type   which armor resistance to check
     */
    public static void registerHazard(int dimId, DamageSource source, float damage, HazardType type) {
        DIMENSION_HAZARDS.put(dimId, new Hazard(source, damage, type));
    }

    /**
     * Called every player tick from {@link EventHandlers#onPlayerTick}.
     * Applies damage every 20 ticks (once per second).
     */
    public static void onPlayerTick(EntityPlayer player) {
        if (player.isCreative() || player.isSpectator()) return;
        if (player.world.isRemote) return;
        if (player.ticksExisted % 20 != 0) return;

        Hazard hazard = DIMENSION_HAZARDS.get(player.dimension);
        if (hazard == null) return;

        float damage = hazard.baseDamage;
        ItemStack chest = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (!chest.isEmpty() && chest.getItem() instanceof ArmorMetaItem) {
            ArmorMetaItem<?>.ArmorMetaValueItem meta = ((ArmorMetaItem<?>) chest.getItem()).getItem(chest);
            if (meta != null) {
                switch (hazard.type) {
                    case HEAT -> damage *= meta.getArmorLogic().getHeatResistance();
                    case RADIATION -> damage *= meta.getArmorLogic().getRadiationResistance();
                    case POISON -> damage *= meta.getArmorLogic().getPoisonResistance();
                }
            }
        }

        if (damage > 0.0f) {
            player.attackEntityFrom(hazard.damageSource.setDamageBypassesArmor(), damage);
            EntityDamageUtil.damageArmorForHazard(player, hazard.damageSource, damage);
        }
    }
}
