package gregtech.api.bridge.impl;

import gregtech.api.bridge.IGTItemHelper;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import gtqt.common.items.GTQTMetaItems;
import gtqt.common.items.behaviors.ProgrammableCircuit;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

public class GTItemHelperImpl implements IGTItemHelper {

    @Override
    public boolean isIntegratedCircuit(ItemStack stack) {
        return IntCircuitIngredient.isIntegratedCircuit(stack);
    }

    @Override
    public int getCircuitConfiguration(ItemStack stack) {
        return IntCircuitIngredient.getCircuitConfiguration(stack);
    }

    @Override
    public ItemStack getIntegratedCircuit(int config) {
        return IntCircuitIngredient.getIntegratedCircuit(config);
    }

    @Nullable
    @Override
    public ItemStack getProgrammableCircuitStack() {
        if (GTQTMetaItems.PROGRAMMABLE_CIRCUIT != null) {
            return GTQTMetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(1);
        }
        return null;
    }

    @Nullable
    @Override
    public ItemStack wrapAsProgrammableCircuit(ItemStack source) {
        if (GTQTMetaItems.PROGRAMMABLE_CIRCUIT == null) {
            return null;
        }
        ItemStack programmable = GTQTMetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(1);
        ProgrammableCircuit.wrap(source, programmable);
        return programmable;
    }

    @Override
    public boolean isGTDamageableItem(Item item) {
        return item instanceof IGTTool;
    }

    @Override
    public void findMetaItem(String itemId, BiConsumer<Item, Integer> consumer) {
        for (MetaItem<?> metaItem : MetaItem.getMetaItems()) {
            MetaItem<?>.MetaValueItem valueItem = metaItem.getItem(itemId);
            if (valueItem != null) {
                ItemStack stack = valueItem.getStackForm();
                consumer.accept(stack.getItem(), stack.getItemDamage());
                return;
            }
        }
    }
}
