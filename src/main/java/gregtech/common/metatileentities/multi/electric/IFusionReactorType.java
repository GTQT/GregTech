package gregtech.common.metatileentities.multi.electric;

import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.block.state.IBlockState;

import com.cleanroommc.modularui.api.drawable.IDrawable;

public interface IFusionReactorType {

    String getName();

    int getTier();

    IBlockState getCasingState();

    IBlockState getGlassState();

    IBlockState getCoilState();

    ICubeRenderer getBaseRenderer();

    IDrawable getUITitle();

    int getEnergyMultiplier();

}
