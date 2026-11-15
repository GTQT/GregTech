package gregtech.mixins.minecraft;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 防止 {@link BlockFluidBase#getFogColor} 无限递归：Forge 的实现里，眼睛不在流体内部时会委托给
 * {@code pos.down(densityDir)} 的方块——正密度液体向上委托、负密度气体向下委托，于是"气体紧贴液体上方"时
 * 两个方块会互相委托（液体找上面的气体，气体找下面的液体）。只要眼睛不在其中任一方块内部，递归就不会自行终止，
 * 最终 StackOverflowError，表现为客户端崩溃/卡死。
 * <p>
 * 这里只加一层"雾色计算链"重入保护：重入时直接返回调用方给的 {@code originalColor}。不再 Overwrite
 * {@code getFilledPercentage}——它是 Forge 公共 API（浸没判定、流体 capability 填充量、其它 mod 的渲染高度都
 * 会调用），全局替换成"计算雾色期间返回 ±1"的伪造值会污染这些调用方，而它本身并不会调用 {@code getFogColor}，
 * 根本不在递归环上。
 */
@Mixin(BlockFluidBase.class)
public abstract class BlockFluidBaseMixin extends Block implements IFluidBlock {

    // 雾色计算的递归标记：仅在当前线程的 getFogColor 调用链上为 true
    private static final ThreadLocal<Boolean> isCalculatingFogColor = ThreadLocal.withInitial(() -> false);

    @Shadow(remap = false)
    protected int densityDir;

    protected BlockFluidBaseMixin(Material materialIn, MapColor color) {
        super(materialIn, color);
    }

    @Shadow(remap = false)
    protected abstract boolean isWithinFluid(IBlockAccess world, BlockPos pos, Vec3d vec);

    /**
     * @reason 液体/气体相邻时 Forge 的雾色委托会形成无终止递归，重入时返回调用方原值即可终止
     * @author MeowmelMuku
     */
    @Overwrite(remap = false)
    @SideOnly(Side.CLIENT)
    public Vec3d getFogColor(World world, BlockPos pos, IBlockState state, Entity entity, Vec3d originalColor,
                             float partialTicks) {
        // 已经在计算雾色：说明是递归进来的，直接交还给调用方
        if (isCalculatingFogColor.get()) {
            return originalColor;
        }

        try {
            isCalculatingFogColor.set(true);

            if (!isWithinFluid(world, pos, ActiveRenderInfo.projectViewFromEntity(entity, partialTicks))) {
                BlockPos otherPos = pos.down(densityDir);
                IBlockState otherState = world.getBlockState(otherPos);
                return otherState.getBlock()
                        .getFogColor(world, otherPos, otherState, entity, originalColor, partialTicks);
            }

            if (getFluid() != null) {
                int color = getFluid().getColor();
                float red = (color >> 16 & 0xFF) / 255.0F;
                float green = (color >> 8 & 0xFF) / 255.0F;
                float blue = (color & 0xFF) / 255.0F;
                return new Vec3d(red, green, blue);
            }

            return super.getFogColor(world, pos, state, entity, originalColor, partialTicks);
        } finally {
            isCalculatingFogColor.set(false);
        }
    }
}
