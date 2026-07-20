package gregtech.mixins.tic;

import gregtech.api.items.toolitem.ItemGTTool;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.tools.DualToolHarvestUtils;
import slimeknights.tconstruct.library.tools.TinkerToolCore;

/**
 * Replicates the hand-swap logic from {@code ToolCore.onBlockStartBreak} for GT tools.
 */
@Mixin(value = ItemGTTool.class, remap = false)
public class MixinItemGTTool {

    @Inject(method = "onBlockStartBreak", at = @At("HEAD"), cancellable = true)
    private void gtDualToolHarvest(ItemStack stack, BlockPos pos, EntityPlayer player,
                                   CallbackInfoReturnable<Boolean> cir) {
        ItemStack offhand = player.getHeldItemOffhand();
        if (offhand.isEmpty() || !(offhand.getItem() instanceof TinkerToolCore)) return;

        if (!DualToolHarvestUtils.shouldUseOffhand(player, pos, stack)) return;

        player.setHeldItem(EnumHand.MAIN_HAND, offhand);
        player.setHeldItem(EnumHand.OFF_HAND, stack);

        NBTTagCompound tag = offhand.hasTagCompound() ? offhand.getTagCompound() : new NBTTagCompound();
        tag.setLong("SwitchedHand", player.getEntityWorld().getTotalWorldTime());
        offhand.setTagCompound(tag);

        cir.setReturnValue(false);
    }

    @Inject(method = "getHarvestLevel", at = @At("RETURN"), cancellable = true)
    private void elevateHarvestLevelFromOffHand(ItemStack stack, String toolClass,
                                                @NotNull EntityPlayer player, @NotNull IBlockState state,
                                                CallbackInfoReturnable<Integer> cir) {
        if (player == null || state == null) return;
        ItemStack offhand = player.getHeldItemOffhand();
        if (offhand.isEmpty() || !(offhand.getItem() instanceof TinkerToolCore)) return;
        int currentLevel = cir.getReturnValue();
        int ticLevel = offhand.getItem().getHarvestLevel(offhand, toolClass, null, state);
        if (ticLevel > currentLevel) {
            cir.setReturnValue(ticLevel);
        }
    }
}
