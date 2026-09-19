package gregtech.common.pipelike.optical;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.items.toolitem.ToolClasses;
import gregtech.api.items.toolitem.ToolHelper;
import gregtech.api.pipenet.block.material.BlockMaterialPipe;
import gregtech.api.pipenet.block.material.IMaterialPipeTile;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.pipenet.tile.TileEntityPipeBase;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.OpticalCableProperties;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.material.registry.MaterialRegistry;
import gregtech.client.renderer.pipe.OpticalPipeRenderer;
import gregtech.client.renderer.pipe.PipeRenderer;
import gregtech.common.creativetab.GTCreativeTabs;
import gregtech.common.pipelike.optical.net.WorldOpticalPipeNet;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BlockOpticalPipe extends BlockMaterialPipe<OpticalPipeType, OpticalCableProperties, WorldOpticalPipeNet>
        implements ITileEntityProvider {

    public BlockOpticalPipe(OpticalPipeType pipeType, MaterialRegistry registry) {
        super(pipeType, registry);
        setCreativeTab(GTCreativeTabs.TAB_GREGTECH_PIPES);
        setHarvestLevel(ToolClasses.WIRE_CUTTER, 1);
    }

    @Override
    public boolean isValidPipeMaterial(Material material) {
        return super.isValidPipeMaterial(material) && material.hasProperty(PropertyKey.OPTICAL_CABLE);
    }

    @Override
    public Class<OpticalPipeType> getPipeTypeClass() {
        return OpticalPipeType.class;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    public PipeRenderer getPipeRenderer() {
        return OpticalPipeRenderer.INSTANCE;
    }

    @Override
    public WorldOpticalPipeNet getWorldPipeNet(World world) {
        return WorldOpticalPipeNet.getWorldPipeNet(world);
    }

    @Override
    public boolean isPipeTool(@NotNull ItemStack stack) {
        return ToolHelper.isTool(stack, ToolClasses.WIRE_CUTTER);
    }

    @Override
    public boolean canPipesConnect(IPipeTile<OpticalPipeType, OpticalCableProperties> selfTile, EnumFacing side,
                                   IPipeTile<OpticalPipeType, OpticalCableProperties> sideTile) {
        if (!(selfTile instanceof TileEntityOpticalPipe) || !(sideTile instanceof TileEntityOpticalPipe)) {
            return false;
        }
        // cables of different materials can never be spliced together
        Material selfMaterial = ((IMaterialPipeTile<OpticalPipeType, OpticalCableProperties>) selfTile)
                .getPipeMaterial();
        Material sideMaterial = ((IMaterialPipeTile<OpticalPipeType, OpticalCableProperties>) sideTile)
                .getPipeMaterial();
        return selfMaterial != null && selfMaterial == sideMaterial;
    }

    @Override
    public boolean canPipeConnectToBlock(IPipeTile<OpticalPipeType, OpticalCableProperties> selfTile, EnumFacing side,
                                         @Nullable TileEntity tile) {
        if (tile == null) return false;
        if (tile.hasCapability(GregtechTileCapabilities.CAPABILITY_DATA_ACCESS, side.getOpposite())) return true;
        return tile.hasCapability(GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER, side.getOpposite());
    }

    @Override
    public boolean isHoldingPipe(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        ItemStack stack = player.getHeldItemMainhand();
        return stack != ItemStack.EMPTY && stack.getItem() instanceof ItemBlockOpticalPipe;
    }

    @Override
    public TileEntity createNewTileEntity(@NotNull World worldIn, int meta) {
        return new TileEntityOpticalPipe();
    }

    @Override
    public TileEntityPipeBase<OpticalPipeType, OpticalCableProperties> createNewTileEntity(boolean supportsTicking) {
        return new TileEntityOpticalPipe();
    }

    @NotNull
    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("deprecation")
    public EnumBlockRenderType getRenderType(@NotNull IBlockState state) {
        return OpticalPipeRenderer.INSTANCE.getBlockRenderType();
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected Pair<TextureAtlasSprite, Integer> getParticleTexture(@NotNull World world, BlockPos blockPos) {
        return OpticalPipeRenderer.INSTANCE.getParticleTexture(getPipeTileEntity(world, blockPos));
    }
}
