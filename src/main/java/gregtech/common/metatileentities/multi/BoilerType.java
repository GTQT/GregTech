package gregtech.common.metatileentities.multi;

import gregtech.api.mui.GTGuiTheme;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.block.state.IBlockState;

import static gregtech.common.blocks.BlockBoilerCasing.BoilerCasingType.*;
import static gregtech.common.blocks.BlockFireboxCasing.FireboxCasingType.*;
import static gregtech.common.blocks.BlockMetalCasing.MetalCasingType.*;
import static gregtech.common.blocks.MetaBlocks.*;

public enum BoilerType implements IBoilerType {

    BRONZE("bronze", 800, 1200,
            METAL_CASING.getState(BRONZE_BRICKS),
            BOILER_FIREBOX_CASING.getState(BRONZE_FIREBOX),
            BOILER_CASING.getState(BRONZE_PIPE),
            Textures.BRONZE_PLATED_BRICKS,
            Textures.BRONZE_FIREBOX,
            Textures.BRONZE_FIREBOX_ACTIVE,
            Textures.LARGE_BRONZE_BOILER,
            0.01,
            GTGuiTheme.BRONZE),

    STEEL("steel", 1800, 1800,
            METAL_CASING.getState(STEEL_SOLID),
            BOILER_FIREBOX_CASING.getState(STEEL_FIREBOX),
            BOILER_CASING.getState(STEEL_PIPE),
            Textures.SOLID_STEEL_CASING,
            Textures.STEEL_FIREBOX,
            Textures.STEEL_FIREBOX_ACTIVE,
            Textures.LARGE_STEEL_BOILER,
            0.012,
            GTGuiTheme.STEEL),

    TITANIUM("titanium", 3200, 2400,
            METAL_CASING.getState(TITANIUM_STABLE),
            BOILER_FIREBOX_CASING.getState(TITANIUM_FIREBOX),
            BOILER_CASING.getState(TITANIUM_PIPE),
            Textures.STABLE_TITANIUM_CASING,
            Textures.TITANIUM_FIREBOX,
            Textures.TITANIUM_FIREBOX_ACTIVE,
            Textures.LARGE_TITANIUM_BOILER,
            0.015,
            GTGuiTheme.STANDARD),

    TUNGSTENSTEEL("tungstensteel", 6400, 3000,
            METAL_CASING.getState(TUNGSTENSTEEL_ROBUST),
            BOILER_FIREBOX_CASING.getState(TUNGSTENSTEEL_FIREBOX),
            BOILER_CASING.getState(TUNGSTENSTEEL_PIPE),
            Textures.ROBUST_TUNGSTENSTEEL_CASING,
            Textures.TUNGSTENSTEEL_FIREBOX,
            Textures.TUNGSTENSTEEL_FIREBOX_ACTIVE,
            Textures.LARGE_TUNGSTENSTEEL_BOILER,
            0.02,
            GTGuiTheme.STANDARD);

    private final String name;
    public final IBlockState casingState;
    public final IBlockState fireboxState;
    public final IBlockState pipeState;
    public final ICubeRenderer casingRenderer;
    public final ICubeRenderer fireboxIdleRenderer;
    public final ICubeRenderer fireboxActiveRenderer;
    public final ICubeRenderer frontOverlay;
    private final int steamPerTick;
    private final int ticksToBoiling;
    private final double pollutionAmount;
    private final GTGuiTheme uiTheme;

    BoilerType(String name, int steamPerTick, int ticksToBoiling,
               IBlockState casingState, IBlockState fireboxState, IBlockState pipeState,
               ICubeRenderer casingRenderer, ICubeRenderer fireboxIdleRenderer,
               ICubeRenderer fireboxActiveRenderer, ICubeRenderer frontOverlay,
               double pollutionAmount, GTGuiTheme uiTheme) {
        this.name = name;
        this.steamPerTick = steamPerTick;
        this.ticksToBoiling = ticksToBoiling;
        this.casingState = casingState;
        this.fireboxState = fireboxState;
        this.pipeState = pipeState;
        this.casingRenderer = casingRenderer;
        this.fireboxIdleRenderer = fireboxIdleRenderer;
        this.fireboxActiveRenderer = fireboxActiveRenderer;
        this.frontOverlay = frontOverlay;
        this.pollutionAmount = pollutionAmount;
        this.uiTheme = uiTheme;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int steamPerTick() {return steamPerTick;}

    @Override
    public int getTicksToBoiling() {return ticksToBoiling;}

    @Override
    public IBlockState getCasingState() {return casingState;}

    @Override
    public IBlockState getFireboxState() {return fireboxState;}

    @Override
    public IBlockState getPipeState() {return pipeState;}

    @Override
    public ICubeRenderer getCasingRenderer() {return casingRenderer;}

    @Override
    public ICubeRenderer getFireboxIdleRenderer() {return fireboxIdleRenderer;}

    @Override
    public ICubeRenderer getFireboxActiveRenderer() {return fireboxActiveRenderer;}

    @Override
    public ICubeRenderer getFrontOverlay() {return frontOverlay;}

    @Override
    public double getPollutionAmount() {return pollutionAmount;}

    @Override
    public GTGuiTheme getUITheme() {return uiTheme;}

    @Override
    public int runtimeBoost(int ticks) {
        return switch (this) {
            case BRONZE -> ticks * 2;
            case STEEL -> ticks * 150 / 100;
            case TITANIUM -> ticks * 120 / 100;
            case TUNGSTENSTEEL -> ticks;
        };
    }
}
