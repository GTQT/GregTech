package gregtech.api.util;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;

import org.jetbrains.annotations.NotNull;

/**
 * Preview candidate whose machine front must point at an exposed side of the
 * declared structure. The preview assembler resolves that side from the
 * template, never from live-world air blocks.
 */
public final class ExteriorFacingBlockInfo extends BlockInfo {

    public ExteriorFacingBlockInfo(@NotNull IBlockState blockState, @NotNull TileEntity tileEntity) {
        super(blockState, tileEntity);
    }
}
