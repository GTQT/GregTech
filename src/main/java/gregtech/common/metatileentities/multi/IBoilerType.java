package gregtech.common.metatileentities.multi;

import gregtech.api.mui.GTGuiTheme;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.block.state.IBlockState;

public interface IBoilerType {

    String getName();

    int steamPerTick();
    int getTicksToBoiling();
    int runtimeBoost(int ticks);

    double getPollutionAmount();

    GTGuiTheme getUITheme();

    IBlockState getCasingState();
    IBlockState getFireboxState();
    IBlockState getPipeState();

    ICubeRenderer getCasingRenderer();
    ICubeRenderer getFireboxIdleRenderer();
    ICubeRenderer getFireboxActiveRenderer();
    ICubeRenderer getFrontOverlay();
}
