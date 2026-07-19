package gregtech.api.pattern;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.ExteriorFacingBlockInfo;
import gregtech.api.util.RelativeDirection;

import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Resolves the explicit exterior-facing declaration carried by preview
 * candidates. This only considers the assembled template shape; it never
 * inspects the world around a projected or previewed structure.
 */
final class PreviewFacingResolver {

    private PreviewFacingResolver() {}

    static void orientExteriorFacingMetaTileEntities(Map<BlockPos, BlockInfo> blocks) {
        blocks.forEach((pos, info) -> {
            if (!(info instanceof ExteriorFacingBlockInfo) ||
                    !(info.getTileEntity() instanceof MetaTileEntityHolder holder)) {
                return;
            }
            MetaTileEntity metaTileEntity = holder.getMetaTileEntity();
            EnumFacing facing = findExposedFacing(pos, blocks, metaTileEntity);
            if (facing != null) {
                metaTileEntity.setFrontFacing(facing);
            }
        });
    }

    @Nullable
    private static EnumFacing findExposedFacing(BlockPos pos, Map<BlockPos, BlockInfo> blocks,
                                                @Nullable MetaTileEntity metaTileEntity) {
        if (metaTileEntity == null) {
            return null;
        }
        for (EnumFacing facing : RelativeDirection.ALL_FACINGS) {
            if (metaTileEntity.isValidFrontFacing(facing) && !isOccupied(blocks.get(pos.offset(facing)))) {
                return facing;
            }
        }
        return null;
    }

    private static boolean isOccupied(@Nullable BlockInfo info) {
        return info != null && info != BlockInfo.EMPTY && info.getBlockState() != null &&
                info.getBlockState().getBlock() != Blocks.AIR;
    }
}
