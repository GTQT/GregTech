package gregtech.mixins.appeng;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import appeng.fluids.helper.DualityFluidInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DualityFluidInterface.class, remap = false)
public class DualityFluidInterfaceMixin {

    @Redirect(
            method = "getTermName",
            at = @At(
                    value = "INVOKE",
                    target = "Lgregtech/api/metatileentity/MetaTileEntity;getMetaFullName()Ljava/lang/String;"
            )
    )
    private String redirectGetMetaFullName(MetaTileEntity metaTileEntity) {
        // 检查是否为多方块部件
        if (metaTileEntity instanceof MetaTileEntityMultiblockPart part) {
            if (part.getController() != null) {
                return part.getController().getMetaFullName();
            }
        }
        // 否则返回原名称
        return metaTileEntity.getMetaFullName();
    }
}
