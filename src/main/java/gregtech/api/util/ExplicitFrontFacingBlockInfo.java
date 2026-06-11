package gregtech.api.util;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * BlockInfo variant for machine candidates whose preview/build facing is part of
 * the structure contract.
 */
public class ExplicitFrontFacingBlockInfo extends BlockInfo {

    @NotNull
    private final Function<MultiblockControllerBase, EnumFacing> frontFacingResolver;

    public ExplicitFrontFacingBlockInfo(@NotNull IBlockState blockState, @NotNull TileEntity tileEntity,
                                        @NotNull EnumFacing frontFacing) {
        this(blockState, tileEntity, controller -> frontFacing);
    }

    public ExplicitFrontFacingBlockInfo(@NotNull IBlockState blockState, @NotNull TileEntity tileEntity,
                                        @NotNull Function<MultiblockControllerBase, EnumFacing> frontFacingResolver) {
        super(blockState, tileEntity);
        this.frontFacingResolver = frontFacingResolver;
    }

    @NotNull
    public EnumFacing getFrontFacing(@NotNull MultiblockControllerBase controller) {
        return frontFacingResolver.apply(controller);
    }
}
