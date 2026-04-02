package gregtech.client.renderer.handler;

import gregtech.api.block.machines.BlockMachine;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.pipenet.block.BlockPipe;
import gregtech.api.pipenet.block.material.TileEntityMaterialPipeBase;
import gregtech.api.util.GTUtility;
import gregtech.common.blocks.BlockCompressed;
import gregtech.common.blocks.BlockFrame;
import gregtech.common.pipelike.cable.tile.TileEntityCable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class BlockHighlightRenderer {

    private static final int[] TIER_COLORS = {
            0x555555, 0xAAAAAA, 0x55FFFF, 0xFFAA00,
            0xAA00AA, 0x5555FF, 0xFF55FF, 0xFF5555,
            0x00AAAA, 0xAA0000, 0x00AA00, 0x006600,
            0xFFFF55, 0x5555FF, 0xFF5555,
    };

    private BlockHighlightRenderer() {}

    public static boolean handleMaterialHighlight(DrawBlockHighlightEvent event) {
        if (event.getTarget().typeOfHit != RayTraceResult.Type.BLOCK) return false;

        BlockPos pos = event.getTarget().getBlockPos();
        if (pos == null) return false;

        EntityPlayer player = event.getPlayer();
        World world = player.world;
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        int color = -1;
        if (block instanceof BlockCompressed compressed) {
            color = compressed.getGtMaterial(state).getMaterialRGB();
        } else if (block instanceof BlockFrame frame) {
            color = frame.getGtMaterial(state).getMaterialRGB();
        }

        if (color == -1) return false;

        event.setCanceled(true);
        drawColoredOutline(event, player, state, world, pos, color, 0.65f);
        return true;
    }

    public static boolean handleMachineHighlight(DrawBlockHighlightEvent event) {
        if (event.isCanceled()) return false;
        if (event.getTarget().typeOfHit != RayTraceResult.Type.BLOCK) return false;

        BlockPos pos = event.getTarget().getBlockPos();
        if (pos == null) return false;

        EntityPlayer player = event.getPlayer();
        World world = player.world;
        IBlockState state = world.getBlockState(pos);

        if (!(state.getBlock() instanceof BlockMachine) && !(state.getBlock() instanceof BlockPipe)) return false;

        int color;
        if (state.getBlock() instanceof BlockMachine) {
            color = getMachineColor(world, pos);
        } else {
            color = getCableColor(world, pos);
        }
        event.setCanceled(true);
        drawColoredOutline(event, player, state, world, pos, color, 0.5f);
        return true;
    }

    private static void drawColoredOutline(DrawBlockHighlightEvent event, EntityPlayer player,
                                           IBlockState state, World world, BlockPos pos,
                                           int color, float alpha) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float partialTicks = event.getPartialTicks();
        double dx = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double dy = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double dz = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        AxisAlignedBB aabb = state.getSelectedBoundingBox(world, pos).grow(0.002).offset(-dx, -dy, -dz);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.glLineWidth(2.5f);
        GlStateManager.disableTexture2D();
        GlStateManager.depthMask(false);

        RenderGlobal.drawSelectionBoundingBox(aabb, r, g, b, alpha);

        GlStateManager.depthMask(true);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static int getMachineColor(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof MetaTileEntityHolder holder)) return 0x808080;
        MetaTileEntity mte = holder.getMetaTileEntity();
        if (mte == null) return 0x808080;
        if (mte instanceof ITieredMetaTileEntity tiered) {
            int tier = tiered.getTier();
            if (tier >= 0 && tier < TIER_COLORS.length) return TIER_COLORS[tier];
        }
        return 0x808080;
    }

    public static int getCableColor(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityCable cable) {
            long voltage = cable.getMaxVoltage();
            int tier = GTUtility.getTierByVoltage(voltage);
            if (tier >= 0 && tier < TIER_COLORS.length) return TIER_COLORS[tier];
        }
        if (te instanceof TileEntityMaterialPipeBase<?, ?>materialPipe) {
            return materialPipe.getPipeMaterial().getMaterialRGB();
        }
        return 0x808080;
    }
}
