package gregtech.integration.tconstruct.traits;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import slimeknights.tconstruct.library.traits.AbstractTrait;

/**
 * Overrides every durability-damage event to 0, making the tool truly indestructible.
 *
 * <p>
 * Assigned to GT materials whose {@code ToolProperty#isUnbreakable} flag is set.
 */
public class TraitUnbreakable extends AbstractTrait {

    public TraitUnbreakable() {
        super("gregtech_unbreakable", 0xFFFFFF);
    }

    @Override
    public int onToolDamage(ItemStack tool, int damage, int newDamage, EntityLivingBase entity) {
        return super.onToolDamage(tool, damage, 0, entity);
    }
}
