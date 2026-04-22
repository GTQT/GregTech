package gregtech.mixins.ae2;

import gregtech.api.metatileentity.MetaTileEntity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.util.AETrack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = AETrack.class, remap = false)
public abstract class MixinAETrack {

    /**
     * @author GregTech
     * @reason 注入 GT 样板提供者追踪逻辑
     */
    @Overwrite
    protected static void trackGTProvider(ICraftingProvider provider, EntityPlayer player) {
        if (provider instanceof MetaTileEntity metaTileEntity) {
            BlockPos blockPos = metaTileEntity.getPos();
            player.sendMessage(new TextComponentTranslation("[合成追踪]正在追踪位于 X:" + blockPos.getX() + " Y:"
                    + blockPos.getY() + " Z:" + blockPos.getZ() + " 的样板总成"));
            AETrack.showPos(blockPos, player);
        }
    }
}
