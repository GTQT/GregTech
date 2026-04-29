package gregtech.mixins.ae2;

import gregtech.api.items.metaitem.MetaItem;

import net.minecraft.item.ItemStack;

import appeng.helpers.NonBlockingItems;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = NonBlockingItems.class, remap = false)
public abstract class MixinNonBlockingItems {

    /**
     * @author GregTech
     * @reason 注入 GT MetaItem 查找逻辑
     */
    @Overwrite
    protected boolean lookupGTMetaItem(String modid, String[] modItemMeta, String rawEntry) {
        boolean found = false;
        for (MetaItem<?> metaItem : MetaItem.getMetaItems()) {
            MetaItem<?>.MetaValueItem metaItem2 = metaItem.getItem(modItemMeta[1]);
            if (metaItem.getItem(modItemMeta[1]) != null) {
                found = true;
                ItemStack itemStack = metaItem2.getStackForm();
                NonBlockingItems.NON_BLOCKING_MAP.get(modid).putIfAbsent(itemStack.getItem(), new IntOpenHashSet());
                NonBlockingItems.NON_BLOCKING_MAP.get(modid).computeIfPresent(itemStack.getItem(), (item, intSet) -> {
                    intSet.add(itemStack.getItemDamage());
                    return intSet;
                });
            } else {
                ItemStack itemStack = GameRegistry.makeItemStack(modItemMeta[0] + ":" + modItemMeta[1],
                        modItemMeta.length == 3 ? Integer.parseInt(modItemMeta[2]) : 0, 1, null);
                if (!itemStack.isEmpty()) {
                    NonBlockingItems.NON_BLOCKING_MAP.get(modid).putIfAbsent(itemStack.getItem(), new IntOpenHashSet());
                    NonBlockingItems.NON_BLOCKING_MAP.get(modid).computeIfPresent(itemStack.getItem(),
                            (item, intSet) -> {
                                intSet.add(itemStack.getItemDamage());
                                return intSet;
                            });
                }
            }
        }
        return found;
    }
}
