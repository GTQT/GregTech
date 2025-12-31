package gregtech.common.pipelike.heat;

import gregtech.api.items.toolitem.ToolClasses;
import gregtech.api.items.toolitem.ToolHelper;
import gregtech.api.pipenet.block.material.BlockMaterialPipe;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.pipenet.tile.TileEntityPipeBase;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.properties.HeatConductorProperties;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.material.registry.MaterialRegistry;
import gregtech.client.renderer.pipe.HeatConductorRenderer;
import gregtech.client.renderer.pipe.PipeRenderer;
import gregtech.common.creativetab.GTCreativeTabs;
import gregtech.common.pipelike.heat.net.WorldHNet;
import gregtech.common.pipelike.heat.tile.TileEntityHeatConductor;
import gregtech.common.pipelike.heat.tile.TileEntityHeatConductorTickable;

import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import static gregtech.api.capability.GregtechCapabilities.CAPABILITY_HEAT_CONTAINER;

public class BlockHeatConductor extends BlockMaterialPipe<HeatConductorType, HeatConductorProperties, WorldHNet>
        implements ITileEntityProvider {

    public BlockHeatConductor(HeatConductorType pipeType, MaterialRegistry registry) {
        super(pipeType, registry);
        setCreativeTab(GTCreativeTabs.TAB_GREGTECH_PIPES);
        setHarvestLevel(ToolClasses.WRENCH, 1);
    }

    @Override
    public boolean isValidPipeMaterial(Material material) {
        return super.isValidPipeMaterial(material) && material.hasProperty(PropertyKey.INGOT);
    }

    @Override
    public @NotNull PipeRenderer getPipeRenderer() {
        return HeatConductorRenderer.INSTANCE;
    }

    @Override
    public Class<HeatConductorType> getPipeTypeClass() {
        return HeatConductorType.class;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return HeatConductorRenderer.INSTANCE.getBlockRenderType();
    }

    @Override
    public WorldHNet getWorldPipeNet(World world) {
        return WorldHNet.getWorldHNet(world);
    }

    @Override
    protected boolean isPipeTool(@NotNull ItemStack stack) {
        return ToolHelper.isTool(stack, ToolClasses.WRENCH);
    }

    @Override
    public int getLightValue(@NotNull IBlockState state, IBlockAccess world, @NotNull BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityHeatConductor heatConductor) {
            int temp = heatConductor.getTemperature();
            // 管道发光逻辑，温度越高光越亮
            if (temp >= 2000) {
                return 15;
            }
            if (temp > 500) {
                return (temp - 500) * 15 / 1500;
            }
        }
        return 0;
    }

    @Override
    public boolean canPipesConnect(IPipeTile<HeatConductorType, HeatConductorProperties> selfTile, EnumFacing side,
                                   IPipeTile<HeatConductorType, HeatConductorProperties> sideTile) {
        return selfTile instanceof TileEntityHeatConductor && sideTile instanceof TileEntityHeatConductor;
    }

    @Override
    public boolean canPipeConnectToBlock(IPipeTile<HeatConductorType, HeatConductorProperties> selfTile, EnumFacing side,
                                         TileEntity tile) {
        return tile != null &&
                tile.getCapability(CAPABILITY_HEAT_CONTAINER, side.getOpposite()) != null;
    }

    @Override
    public boolean isHoldingPipe(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        ItemStack stack = player.getHeldItemMainhand();
        return stack != ItemStack.EMPTY && stack.getItem() instanceof ItemBlockHeatConductor;
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityHeatConductor();
    }

    @Override
    public TileEntityPipeBase<HeatConductorType, HeatConductorProperties> createNewTileEntity(boolean supportsTicking) {
        return supportsTicking ? new TileEntityHeatConductorTickable() : new TileEntityHeatConductor();
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected Pair<TextureAtlasSprite, Integer> getParticleTexture(World world, BlockPos blockPos) {
        return HeatConductorRenderer.INSTANCE.getParticleTexture((TileEntityHeatConductor) world.getTileEntity(blockPos));
    }
}
