package gregtech.integration.tconstruct.traits;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerEvent;

import slimeknights.tconstruct.library.traits.AbstractTrait;

/**
 * Grants bonus mining speed and bonus damage during the night.
 *
 * <p>
 * Assigned to materials whose direct composition contains a precious metal (Gold, Platinum, Palladium, or Iridium). The
 * night-themed bonus reflects the lustrous, moonlight-reflecting nature of these metals.
 */
public class TraitMoonlit extends AbstractTrait {

    private static final float NIGHT_SPEED_BONUS = 0.5f;  // +50% mining speed
    private static final float NIGHT_DAMAGE_BONUS = 3.0f;  // +3 flat damage

    public TraitMoonlit() {
        super("gregtech_moonlit", 0xE8D44D);
    }

    private static boolean isNight(World world) {
        return !world.isDaytime();
    }

    @Override
    public void miningSpeed(ItemStack tool, PlayerEvent.BreakSpeed event) {
        if (isNight(event.getEntityPlayer().world)) {
            event.setNewSpeed(event.getNewSpeed() * (1.0f + NIGHT_SPEED_BONUS));
        }
    }

    @Override
    public float damage(ItemStack tool, EntityLivingBase player, EntityLivingBase target,
                        float damage, float newDamage, boolean isCritical) {
        if (isNight(player.world)) {
            return newDamage + NIGHT_DAMAGE_BONUS;
        }
        return newDamage;
    }
}
