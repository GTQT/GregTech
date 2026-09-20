package gregtech.client.renderer.pipe;

import gregtech.api.pipenet.block.BlockPipe;
import gregtech.api.pipenet.block.IPipeType;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.unification.material.Material;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.utils.BloomEffectUtil;
import gregtech.common.ConfigHolder;
import gregtech.common.pipelike.fiber.FiberColor;
import gregtech.common.pipelike.fiber.FiberPipeType;
import gregtech.common.pipelike.fiber.tile.TileEntityFiberPipe;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.uv.IconTransformation;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Renders fiber optic cable.
 * <p>
 * The sprites are greyscale copies of the laser pipe set, so the pipe body is tinted in real time
 * with the colour the line is currently carrying — the tint follows the light, not the material.
 * While the line is dark it renders neutral. The emissive overlay stays untinted so it still reads
 * as light.
 */
public class FiberPipeRenderer extends PipeRenderer {

    public static final FiberPipeRenderer INSTANCE = new FiberPipeRenderer();

    private boolean active = false;
    private int carriedColor = 0xFFFFFF;

    private FiberPipeRenderer() {
        super("gt_fiber_pipe", GTUtility.gregtechId("fiber_pipe"));
    }

    @Override
    public void registerIcons(TextureMap map) {
        // sprites are registered centrally in Textures
    }

    @Override
    public void buildRenderer(PipeRenderContext renderContext, BlockPipe<?, ?, ?> blockPipe,
                              @Nullable IPipeTile<?, ?> pipeTile, IPipeType<?> pipeType, @Nullable Material material) {
        if (pipeType instanceof FiberPipeType) {
            // paint, when applied, wins over the carried colour
            carriedColor = 0xFFFFFF;
            if (pipeTile instanceof TileEntityFiberPipe fiber) {
                FiberColor carried = fiber.getTransmittedColor();
                if (carried != null) carriedColor = carried.getRgb();
            }
            int rgb = pipeTile != null && pipeTile.isPainted() ? pipeTile.getPaintingColor() : carriedColor;
            ColourMultiplier bodyColor = new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(rgb));

            renderContext.addOpenFaceRender(false, new IconTransformation(Textures.FIBER_PIPE_IN), bodyColor)
                    .addSideRender(false, new IconTransformation(Textures.FIBER_PIPE_SIDE), bodyColor);
            if (pipeTile != null && pipeTile.isPainted()) {
                renderContext.addSideRender(new IconTransformation(Textures.FIBER_PIPE_OVERLAY));
            }

            active = !ConfigHolder.client.preventAnimatedCables && pipeTile instanceof TileEntityFiberPipe fiber &&
                    fiber.isActive();
        }
    }

    @Override
    protected void renderOtherLayers(BlockRenderLayer layer, CCRenderState renderState,
                                     PipeRenderContext renderContext) {
        if (active && layer == BloomEffectUtil.getEffectiveBloomLayer() &&
                (renderContext.getConnections() & 0b111111) != 0) {
            Cuboid6 innerCuboid = BlockPipe.getSideBox(null, renderContext.getPipeThickness());
            for (EnumFacing side : EnumFacing.VALUES) {
                if ((renderContext.getConnections() & (1 << side.getIndex())) == 0) {
                    int oppositeIndex = side.getOpposite().getIndex();
                    if ((renderContext.getConnections() & (1 << oppositeIndex)) <= 0 ||
                            (renderContext.getConnections() & 0b111111 & ~(1 << oppositeIndex)) != 0) {
                        IVertexOperation[] ops = renderContext.getBaseVertexOperation();
                        ops = ArrayUtils.addAll(ops,
                                new IconTransformation(Textures.FIBER_PIPE_OVERLAY_EMISSIVE));
                        renderFace(renderState, ops, side, innerCuboid);
                    }
                } else {
                    Cuboid6 sideCuboid = BlockPipe.getSideBox(side, renderContext.getPipeThickness());
                    for (EnumFacing connectionSide : EnumFacing.VALUES) {
                        if (connectionSide.getAxis() != side.getAxis()) {
                            IVertexOperation[] ops = renderContext.getBaseVertexOperation();
                            ops = ArrayUtils.addAll(ops,
                                    new IconTransformation(Textures.FIBER_PIPE_OVERLAY_EMISSIVE));
                            renderFace(renderState, ops, connectionSide, sideCuboid);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected boolean canRenderInLayer(BlockRenderLayer layer) {
        return super.canRenderInLayer(layer) || layer == BloomEffectUtil.getEffectiveBloomLayer();
    }

    @Override
    public TextureAtlasSprite getParticleTexture(IPipeType<?> pipeType, @Nullable Material material) {
        return Textures.FIBER_PIPE_SIDE;
    }
}
