package gregtech.client.renderer.pipe;

import gregtech.api.pipenet.block.BlockPipe;
import gregtech.api.pipenet.block.IPipeType;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.unification.material.Material;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.ConfigHolder;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;

import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.uv.IconTransformation;
import org.jetbrains.annotations.Nullable;

/**
 * Renders optical cables.
 * <p>
 * The sprites are pure greyscale, exactly like {@code blocks/cable/wire} and the insulation set, so
 * the pipe body is tinted with the cable material's colour through a {@link ColourMultiplier} —
 * this is what makes Glass, Borosilicate Glass, Nether Quartz and Diamond cables visually distinct.
 * The overlay sprites stay untinted white so the flow highlight still reads as light rather than as
 * the material.
 */
public final class OpticalPipeRenderer extends PipeRenderer {

    public static final OpticalPipeRenderer INSTANCE = new OpticalPipeRenderer();

    private OpticalPipeRenderer() {
        super("gt_optical_pipe", GTUtility.gregtechId("optical_pipe"));
    }

    @Override
    public void registerIcons(TextureMap map) {
        // sprites are registered centrally in Textures
    }

    @Override
    public void buildRenderer(PipeRenderContext renderContext, BlockPipe<?, ?, ?> blockPipe,
                              @Nullable IPipeTile<?, ?> pipeTile, IPipeType<?> pipeType, @Nullable Material material) {
        if (material == null) {
            return;
        }

        // material colour, or the painting colour when the cable has been sprayed
        int rgb = material.getMaterialRGB();
        if (pipeTile != null && pipeTile.getPaintingColor() != pipeTile.getDefaultPaintingColor()) {
            rgb = pipeTile.getPaintingColor();
        }
        ColourMultiplier bodyColor = new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(rgb));

        renderContext.addOpenFaceRender(false, new IconTransformation(Textures.OPTICAL_PIPE_IN), bodyColor)
                .addSideRender(false, new IconTransformation(Textures.OPTICAL_PIPE_SIDE), bodyColor);

        IVertexOperation overlay;
        if (ConfigHolder.client.preventAnimatedCables) {
            overlay = new IconTransformation(Textures.OPTICAL_PIPE_SIDE_OVERLAY);
        } else if (pipeTile instanceof TileEntityOpticalPipe opticalPipe && opticalPipe.isActive()) {
            overlay = new IconTransformation(Textures.OPTICAL_PIPE_SIDE_OVERLAY_ACTIVE);
        } else {
            overlay = new IconTransformation(Textures.OPTICAL_PIPE_SIDE_OVERLAY);
        }
        renderContext.addSideRender(false, overlay);
    }

    @Override
    public TextureAtlasSprite getParticleTexture(IPipeType<?> pipeType, @Nullable Material material) {
        // the end cap sprite is mostly transparent and has a dark core, so use the shell instead
        return Textures.OPTICAL_PIPE_SIDE;
    }
}
