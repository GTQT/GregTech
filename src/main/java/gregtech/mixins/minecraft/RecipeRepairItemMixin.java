package gregtech.mixins.minecraft;

import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.items.toolitem.ItemGTToolbelt;
import gregtech.api.items.toolitem.ToolHelper;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.RecipeRepairItem;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.ForgeEventFactory;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Mixin(RecipeRepairItem.class)
public class RecipeRepairItemMixin {

    // GT tools of different types share the same Item, so the vanilla equality check in matches()
    // cannot tell them apart; returning null makes the comparison pass for tool-only pairs,
    // while tool + non-tool pairs still fail it
    @Redirect(method = "matches",
              at = @At(value = "INVOKE",
                       ordinal = 0,
                       target = "Lnet/minecraft/item/ItemStack;getItem()Lnet/minecraft/item/Item;"))
    private Item gregtech$bypassToolItemCheckFirst(ItemStack instance) {
        return instance.getItem() instanceof IGTTool ? null : instance.getItem();
    }

    @Redirect(method = "matches",
              at = @At(value = "INVOKE",
                       ordinal = 1,
                       target = "Lnet/minecraft/item/ItemStack;getItem()Lnet/minecraft/item/Item;"))
    private Item gregtech$bypassToolItemCheckSecond(ItemStack instance) {
        return instance.getItem() instanceof IGTTool ? null : instance.getItem();
    }

    @ModifyReturnValue(method = "matches", at = @At(value = "RETURN", ordinal = 1), remap = false)
    private boolean gregtech$matches(boolean matched, @Local List<ItemStack> list) {
        if (!matched) return false; // list size is not two

        ItemStack firstStack = list.get(0);
        ItemStack secondStack = list.get(1);

        // only two identical, non-toolbelt GT tools can be repaired
        if (!(firstStack.getItem() instanceof IGTTool first) || firstStack.getItem() instanceof ItemGTToolbelt)
            return false;
        if (!(secondStack.getItem() instanceof IGTTool second) || secondStack.getItem() instanceof ItemGTToolbelt)
            return false;

        // must be same material
        if (!Objects.equals(first.getToolMaterial(firstStack), second.getToolMaterial(secondStack)))
            return false;

        // must not be electric
        if (first.isElectric() || second.isElectric())
            return false;

        // must share at least one tool class
        return !Collections.disjoint(first.getToolClasses(firstStack), second.getToolClasses(secondStack));
    }

    @Unique
    private static boolean gregtech$isElectricTool(ItemStack stack) {
        return stack.getItem() instanceof IGTTool tool && tool.isElectric();
    }

    // defensive: matches() already rejects electric tools, but getCraftingResult() may be queried directly
    @Inject(method = "getCraftingResult(Lnet/minecraft/inventory/InventoryCrafting;)Lnet/minecraft/item/ItemStack;",
            at = @At(value = "INVOKE_ASSIGN",
                     target = "Ljava/util/List;get(I)Ljava/lang/Object;",
                     ordinal = 0),
            cancellable = true)
    private void gregtech$rejectElectricTools(InventoryCrafting inv, CallbackInfoReturnable<ItemStack> cir,
                                              @Local(ordinal = 0) ItemStack itemstack,
                                              @Local(ordinal = 1) ItemStack itemstack1) {
        if (gregtech$isElectricTool(itemstack) || gregtech$isElectricTool(itemstack1)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @ModifyReturnValue(method = "getCraftingResult", at = @At(value = "RETURN", ordinal = 1))
    private ItemStack gregtech$repairTools(ItemStack originalResult, InventoryCrafting inv,
                                           @Local(ordinal = 3) int i1,
                                           @Local(ordinal = 0) ItemStack itemstack2,
                                           @Local(ordinal = 1) ItemStack itemstack3) {
        if (itemstack2.getItem() instanceof IGTTool first && itemstack3.getItem() instanceof IGTTool second) {
            // do not allow repairing tools if both are at full durability
            if (itemstack2.getItemDamage() == 0 && itemstack3.getItemDamage() == 0) {
                return ItemStack.EMPTY;
            }
            // defensive: matches() already enforces these, but the crafting result may be queried directly
            if (!Objects.equals(first.getToolMaterial(itemstack2), second.getToolMaterial(itemstack3)))
                return ItemStack.EMPTY;
            if (first.isElectric() || second.isElectric())
                return ItemStack.EMPTY;

            ItemStack output = first.get(first.getToolMaterial(itemstack2));
            NBTTagCompound outputTag = ToolHelper.getToolTag(output);
            outputTag.setInteger(ToolHelper.DURABILITY_KEY, i1);
            return output;
        }

        return originalResult;
    }

    @WrapOperation(method = "getRemainingItems",
                   at = @At(value = "INVOKE",
                            target = "Lnet/minecraft/util/NonNullList;set(ILjava/lang/Object;)Ljava/lang/Object;"))
    private Object gregtech$consumeToolsOnRepair(NonNullList<Object> instance, int index, Object newValue,
                                                 Operation<Object> original,
                                                 @Local(ordinal = 0) ItemStack itemstack) {
        if (itemstack.getItem() instanceof IGTTool) {
            // consume the tool instead of keeping it, firing the destroy event for e.g. enchantment refunds
            ForgeEventFactory.onPlayerDestroyItem(ForgeHooks.getCraftingPlayer(), itemstack, null);
            return instance.get(index);
        }
        return original.call(instance, index, newValue);
    }
}
