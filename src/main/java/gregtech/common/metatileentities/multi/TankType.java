package gregtech.common.metatileentities.multi;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.BlockSteamCasing;
import gregtech.common.blocks.MetaBlocks;

import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;

import java.util.function.Supplier;

public enum TankType {

    WOOD("wood", true, 250_000,
            MetaBlocks.STEAM_CASING.getState(BlockSteamCasing.SteamCasingType.WOOD_WALL),
            () -> MetaTileEntities.WOODEN_TANK_VALVE,
            Textures.WOOD_WALL,
            SoundType.WOOD),

    BRONZE("bronze", false, 1_000_000,
            MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.BRONZE_BRICKS),
            () -> MetaTileEntities.BRONZE_TANK_VALVE,
            Textures.BRONZE_PLATED_BRICKS,
            SoundType.METAL),

    STEEL("steel", false, 4_000_000,
            MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.STEEL_SOLID),
            () -> MetaTileEntities.STEEL_TANK_VALVE,
            Textures.SOLID_STEEL_CASING,
            SoundType.METAL);

    private final String name;
    private final boolean isWood;
    private final int capacity;
    private final IBlockState casingState;
    private final Supplier<MetaTileEntity> valveSupplier;
    private final ICubeRenderer baseTexture;
    private final SoundType soundType;

    TankType(String name, boolean isWood, int capacity, IBlockState casingState,
             Supplier<MetaTileEntity> valveSupplier, ICubeRenderer baseTexture, SoundType soundType) {
        this.name = name;
        this.isWood = isWood;
        this.capacity = capacity;
        this.casingState = casingState;
        this.valveSupplier = valveSupplier;
        this.baseTexture = baseTexture;
        this.soundType = soundType;
    }

    public String getName() {
        return name;
    }

    public boolean isWood() {
        return isWood;
    }

    public int getCapacity() {
        return capacity;
    }

    public IBlockState getCasingState() {
        return casingState;
    }

    public MetaTileEntity getValve() {
        return valveSupplier.get();
    }

    public ICubeRenderer getBaseTexture() {
        return baseTexture;
    }

    public SoundType getSoundType() {
        return soundType;
    }
}
