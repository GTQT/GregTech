package gregtech.mixins.ae2;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;

import appeng.helpers.DualityInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = DualityInterface.class, remap = false)
public abstract class MixinDualityInterface {

    /**
     * @author GregTech
     * @reason 注入 GT 机器名获取逻辑
     */
    @Overwrite
    protected String getGTMachineName(TileEntity directedTile, Block directedBlock) {
        if (directedTile instanceof IGregTechTileEntity igtte) {
            MetaTileEntity mte = igtte.getMetaTileEntity();
            if (mte != null) {
                if (mte instanceof MetaTileEntityMultiblockPart part) {
                    var controller = part.getController();
                    if (controller != null) {
                        return controller.getMetaFullName();
                    }
                }
                return mte.getMetaFullName();
            }
        }
        return null;
    }
}
