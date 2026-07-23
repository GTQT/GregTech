package gregtech.integration.tconstruct.traits;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import slimeknights.tconstruct.library.traits.AbstractTrait;

/**
 * Deals bonus damage against armored targets.
 *
 * <p>
 * Assigned to materials whose GT harvest level is ≥ 5 (Cobalt tier and above). The rationale is that super-hard
 * materials can physically pierce armour plating.
 */
public class TraitPiercer extends AbstractTrait {

    private static final float BONUS_MULTIPLIER = 0.5f;

    public TraitPiercer() {
        super("gregtech_piercer", 0xA9A9A9);
    }

    @Override
    public float damage(ItemStack tool, EntityLivingBase player, EntityLivingBase target,
                        float damage, float newDamage, boolean isCritical) {
        if (target.getTotalArmorValue() > 0) {
            return newDamage + damage * BONUS_MULTIPLIER;
        }
        return newDamage;
    }
}
