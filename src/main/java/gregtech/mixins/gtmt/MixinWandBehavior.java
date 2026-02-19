package gregtech.mixins.gtmt;

import com.github.gtexpert.gtmt.integration.bbw.tools.WandBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = WandBehavior.class)
public abstract class MixinWandBehavior {
    @ModifyArg(
            method = "onItemUse",
            at = @At(
                    value = "INVOKE",
                    target = "Lgregtech/api/items/toolitem/ToolHelper;damageItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;I)V",
                    remap = false
            ),
            index = 2,
            remap = false
    )
    private int setDamageToOne(int originalDamage) {
        return 1;
    }
}
