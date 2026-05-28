package gregtech.common.covers;

import gregtech.api.GTValues;
import gregtech.api.cover.CoverBase;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverableView;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;

public class CoverDrain extends CoverBase implements ITickable {

    private final FluidStack waterStack;

    public CoverDrain(@NotNull CoverDefinition definition, @NotNull CoverableView coverableView,
                      @NotNull EnumFacing attachedSide, int transferRate) {
        super(definition, coverableView, attachedSide);
        this.waterStack = new FluidStack(FluidRegistry.WATER, transferRate);
    }

    @Override
    public boolean canAttach(@NotNull CoverableView coverable, @NotNull EnumFacing side) {
        return coverable.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
    }

    @Override
    public void renderCover(@NotNull CCRenderState renderState, @NotNull Matrix4 translation,
                            @NotNull IVertexOperation[] pipeline, @NotNull Cuboid6 plateBox,
                            @NotNull BlockRenderLayer renderLayer) {
        Textures.DRAIN_OVERLAY.renderSided(getAttachedSide(), plateBox, renderState, pipeline, translation);
    }

    @Override
    public void update() {
        if (getWorld().isRemote || getOffsetTimer() % GTValues.SECOND != 0) return;

        IBlockState neighborBlock = getWorld().getBlockState(getPos().offset(getAttachedSide()));

        if (getTileEntityHere() == null) return;

        IFluidHandler fluidHandler = getCoverableView()
                .getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, getAttachedSide());
        if (fluidHandler == null) return;

        if (neighborBlock.getBlock() == Blocks.WATER || neighborBlock.getBlock() == Blocks.FLOWING_WATER) {
            fluidHandler.fill(this.waterStack.copy(), true);
        }
    }
}

