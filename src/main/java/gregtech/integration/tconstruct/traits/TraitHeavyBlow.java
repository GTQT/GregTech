package gregtech.integration.tconstruct.traits;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import slimeknights.tconstruct.library.traits.AbstractTrait;

/**
 * Multiplies the base knockback by (1 + {@value #KNOCKBACK_BONUS}), effectively +50 %.
 *
 * <p>
 * Assigned to materials with an attack damage value ≥ 10.
 */
public class TraitHeavyBlow extends AbstractTrait {

    private static final float KNOCKBACK_BONUS = 0.5f;

    public TraitHeavyBlow() {
        super("gregtech_heavy_blow", 0x555555);
    }

    @Override
    public float knockBack(ItemStack tool, EntityLivingBase player, EntityLivingBase target,
                           float damage, float knockback, float newKnockback, boolean isCritical) {
        return super.knockBack(tool, player, target, damage, knockback,
                newKnockback + knockback * KNOCKBACK_BONUS, isCritical);
    }
}
