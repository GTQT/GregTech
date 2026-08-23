package gregtech.common.blocks.bedrock;

import gregtech.common.creativetab.GTCreativeTabs;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 基岩流体泉：自然生成在 Y=0 基岩层的喷口方块，替换基岩生成。
 * 周期性地在自身正上方放置一格所属矿脉的实体流体（见 TileEntityBedrockFluidSpring）。
 */
public class BlockBedrockFluidSpring extends Block implements ITileEntityProvider {

    public BlockBedrockFluidSpring() {
        super(Material.ROCK);
        setTranslationKey("bedrock_fluid_spring");
        setCreativeTab(GTCreativeTabs.TAB_GREGTECH);
        setBlockUnbreakable();
        setResistance(6000000.0F);
        setSoundType(SoundType.STONE);
    }

    @Override
    public boolean canEntityDestroy(@NotNull IBlockState state, @NotNull IBlockAccess world, @NotNull BlockPos pos,
                                    @NotNull Entity entity) {
        return false;
    }

    @Override
    public boolean hasTileEntity(@NotNull IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(@NotNull World world, int meta) {
        return new TileEntityBedrockFluidSpring();
    }
}
