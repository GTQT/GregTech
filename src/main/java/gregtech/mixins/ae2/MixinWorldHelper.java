package gregtech.mixins.ae2;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import appeng.util.WorldHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = WorldHelper.class, remap = false)
public abstract class MixinWorldHelper {

    /**
     * @author GregTech
     * @reason 注入 GT MetaTileEntity 获取逻辑
     */
    @Overwrite
    public static Object getMetaTileEntity(IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof IGregTechTileEntity igtte) {
            return igtte.getMetaTileEntity();
        }
        return null;
    }
}
