package gregtech.common.pipelike.fiber;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.items.toolitem.ToolClasses;
import gregtech.api.items.toolitem.ToolHelper;
import gregtech.api.pipenet.block.BlockPipe;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.pipenet.tile.TileEntityPipeBase;
import gregtech.client.renderer.pipe.FiberPipeRenderer;
import gregtech.client.utils.BloomEffectUtil;
import gregtech.common.creativetab.GTCreativeTabs;
import gregtech.common.pipelike.fiber.net.WorldFiberPipeNet;
import gregtech.common.pipelike.fiber.tile.TileEntityFiberPipe;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The fiber optic cable block: a single block with no material variants, carrying color and
 * intensity along a single non-branching line with no attenuation.
 */
public class BlockFiberPipe extends BlockPipe<FiberPipeType, FiberPipeProperties, WorldFiberPipeNet> {

    private final FiberPipeType pipeType;
    private final FiberPipeProperties properties;

    public BlockFiberPipe(@NotNull FiberPipeType pipeType) {
        this.pipeType = pipeType;
        this.properties = FiberPipeProperties.INSTANCE;
        setCreativeTab(GTCreativeTabs.TAB_GREGTECH_PIPES);
        setHarvestLevel(ToolClasses.WIRE_CUTTER, 1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected Pair<TextureAtlasSprite, Integer> getParticleTexture(World world, BlockPos blockPos) {
        return FiberPipeRenderer.INSTANCE.getParticleTexture((TileEntityFiberPipe) world.getTileEntity(blockPos));
    }

    @Override
    public Class<FiberPipeType> getPipeTypeClass() {
        return FiberPipeType.class;
    }

    @Override
    public WorldFiberPipeNet getWorldPipeNet(World world) {
        return WorldFiberPipeNet.getWorldPipeNet(world);
    }

    @Override
    public TileEntityPipeBase<FiberPipeType, FiberPipeProperties> createNewTileEntity(boolean supportsTicking) {
        return new TileEntityFiberPipe();
    }

    @Override
    public FiberPipeProperties createProperties(IPipeTile<FiberPipeType, FiberPipeProperties> pipeTile) {
        FiberPipeType type = pipeTile.getPipeType();
        return type == null ? getFallbackType() : type.modifyProperties(properties);
    }

    @Override
    public FiberPipeProperties createItemProperties(ItemStack itemStack) {
        if (itemStack.getItem() instanceof ItemBlockFiberPipe pipe) {
            return ((BlockFiberPipe) pipe.getBlock()).properties;
        }
        return null;
    }

    @Override
    public ItemStack getDropItem(IPipeTile<FiberPipeType, FiberPipeProperties> pipeTile) {
        return new ItemStack(this, 1, pipeType.ordinal());
    }

    @Override
    protected FiberPipeProperties getFallbackType() {
        return FiberPipeProperties.INSTANCE;
    }

    @Override
    public FiberPipeType getItemPipeType(ItemStack itemStack) {
        if (itemStack.getItem() instanceof ItemBlockFiberPipe pipe) {
            return ((BlockFiberPipe) pipe.getBlock()).pipeType;
        }
        return null;
    }

    @Override
    public void setTileEntityData(TileEntityPipeBase<FiberPipeType, FiberPipeProperties> pipeTile,
                                  ItemStack itemStack) {
        pipeTile.setPipeData(this, pipeType);
    }

    @Override
    public void getSubBlocks(@NotNull CreativeTabs itemIn, @NotNull NonNullList<ItemStack> items) {
        items.add(new ItemStack(this, 1, this.pipeType.ordinal()));
    }

    @Override
    public boolean isPipeTool(@NotNull ItemStack stack) {
        return ToolHelper.isTool(stack, ToolClasses.WIRE_CUTTER);
    }

    @Override
    public boolean canPipesConnect(IPipeTile<FiberPipeType, FiberPipeProperties> selfTile, EnumFacing side,
                                   IPipeTile<FiberPipeType, FiberPipeProperties> sideTile) {
        return selfTile instanceof TileEntityFiberPipe && sideTile instanceof TileEntityFiberPipe;
    }

    @Override
    public boolean canPipeConnectToBlock(IPipeTile<FiberPipeType, FiberPipeProperties> selfTile, EnumFacing side,
                                         @Nullable TileEntity tile) {
        if (tile == null) return false;
        EnumFacing capSide = side.getOpposite();
        return tile.hasCapability(GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_SOURCE, capSide) ||
                tile.hasCapability(GregtechTileCapabilities.CAPABILITY_FIBER_LIGHT_RECEIVER, capSide);
    }

    @Override
    public boolean isHoldingPipe(EntityPlayer player) {
        if (player == null) return false;
        ItemStack stack = player.getHeldItemMainhand();
        return stack != ItemStack.EMPTY && stack.getItem() instanceof ItemBlockFiberPipe;
    }

    @Override
    @NotNull
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("deprecation")
    public EnumBlockRenderType getRenderType(@NotNull IBlockState state) {
        return FiberPipeRenderer.INSTANCE.getBlockRenderType();
    }

    @Override
    public boolean canRenderInLayer(@NotNull IBlockState state, @NotNull BlockRenderLayer layer) {
        if (layer == BlockRenderLayer.SOLID || layer == BlockRenderLayer.CUTOUT) return true;
        return layer == BloomEffectUtil.getEffectiveBloomLayer();
    }
}
