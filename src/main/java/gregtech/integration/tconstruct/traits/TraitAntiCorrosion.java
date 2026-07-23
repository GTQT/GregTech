package gregtech.integration.tconstruct.traits;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import slimeknights.tconstruct.library.traits.AbstractTrait;

/**
 * Each durability-damage event has a {@value #NEGATE_CHANCE} (15 %) chance of being negated entirely.
 *
 * <p>
 * Assigned to non-unbreakable materials with a tool durability ≥ 2000.
 */
public class TraitAntiCorrosion extends AbstractTrait {

    private static final float NEGATE_CHANCE = 0.15f;

    public TraitAntiCorrosion() {
        super("gregtech_anti_corrosion", 0x00CED1);
    }

    @Override
    public int onToolDamage(ItemStack tool, int damage, int newDamage, EntityLivingBase entity) {
        if (random.nextFloat() < NEGATE_CHANCE) {
            newDamage = 0;
        }
        return super.onToolDamage(tool, damage, newDamage, entity);
    }
}
