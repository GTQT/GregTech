package gregtech.common.item.behaviors;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.common.metatileentities.multi.electric.generator.nuclearReactor.NuclearAbility;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

public class NuclearUpdateBehavior implements IItemBehaviour {

    private NuclearAbility ability;

    public NuclearUpdateBehavior(NuclearAbility ability) {
        this.ability = ability;
    }

    public NuclearAbility getAbility() {
        return ability;
    }

    @Nullable
    public static NuclearUpdateBehavior getInstanceFor(ItemStack itemStack) {
        if (!(itemStack.getItem() instanceof MetaItem)) return null;
        MetaItem<?>.MetaValueItem valueItem = ((MetaItem<?>) itemStack.getItem()).getItem(itemStack);
        if (valueItem == null) return null;
        for (IItemBehaviour behaviour : valueItem.getBehaviours()) {
            if (behaviour instanceof NuclearUpdateBehavior) {
                return (NuclearUpdateBehavior) behaviour;
            }
        }

        return null;
    }

}
