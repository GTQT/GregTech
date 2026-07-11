package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.unification.material.Material;
import gregtech.api.util.BlockInfo;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Direct element for frame blocks and framed pipes.
 */
public class FrameElement implements ITypedStructureElement<Object> {

    private final Material[] frameMaterials;
    private final IBlockState[] frameStates;
    private final StructureElementPreview preview;

    public FrameElement(Material... frameMaterials) {
        this.frameMaterials = Arrays.stream(frameMaterials)
                .filter(Objects::nonNull)
                .toArray(Material[]::new);
        this.frameStates = Arrays.stream(this.frameMaterials)
                .map(material -> MetaBlocks.FRAMES.get(material).getBlock(material))
                .toArray(IBlockState[]::new);
        this.preview = StructureElementPreview.of(this::getCandidates);
    }

    @Override
    public boolean check(@NotNull StructureEvaluationContext<Object> context) {
        if (ArrayUtils.contains(frameStates, context.getBlockState())) {
            return true;
        }
        return isFramePipe(context.getTileEntity());
    }

    @Override
    public BlockInfo[] getCandidates() {
        return Arrays.stream(frameStates)
                .map(state -> new BlockInfo(state, null))
                .toArray(BlockInfo[]::new);
    }

    @Override
    public StructureElementPreview getPreview() {
        return preview;
    }

    @Override
    public boolean placeBlock(@NotNull StructureEvaluationContext<Object> context,
                              @NotNull EntityPlayer player) {
        World world = context.getWorld();
        if (world == null) {
            return false;
        }
        if (frameStates.length == 0) {
            return false;
        }
        world.setBlockState(context.getPos(), frameStates[0]);
        return true;
    }

    private boolean isFramePipe(TileEntity tileEntity) {
        if (!(tileEntity instanceof IPipeTile<?, ?>)) {
            return false;
        }
        return ArrayUtils.contains(frameMaterials, ((IPipeTile<?, ?>) tileEntity).getFrameMaterial());
    }
}
