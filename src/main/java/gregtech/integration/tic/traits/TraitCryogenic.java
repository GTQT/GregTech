package gregtech.integration.tic.traits;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;

import slimeknights.tconstruct.library.traits.AbstractTrait;

/**
 * Applies Slowness I for 2 seconds to the target on a successful hit.
 *
 * <p>
 * Assigned to materials with a blast furnace temperature in [1750, 2500) K (i.e. those that require vacuum-freezer
 * processing).
 */
public class TraitCryogenic extends AbstractTrait {

    public TraitCryogenic() {
        super("gregtech_cryogenic", 0x7EC8E3);
    }

    @Override
    public void afterHit(ItemStack tool, EntityLivingBase player, EntityLivingBase target,
                         float damageDealt, boolean wasCritical, boolean wasHit) {
        if (wasHit && target.isEntityAlive()) {
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 40, 0));
        }
    }
}
