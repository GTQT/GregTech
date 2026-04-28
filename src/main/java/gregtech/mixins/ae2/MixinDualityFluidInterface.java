package gregtech.mixins.ae2;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.fluids.helper.DualityFluidInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = DualityFluidInterface.class, remap = false)
public abstract class MixinDualityFluidInterface {

    /**
     * @author GregTech
     * @reason Inject GT machine name retrieval logic
     */
    @Overwrite
    protected String getGTMachineName(World world, BlockPos pos, Block block) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof IGregTechTileEntity igtte) {
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
