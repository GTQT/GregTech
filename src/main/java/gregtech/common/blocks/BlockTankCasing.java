package gregtech.common.blocks;

import gregtech.api.block.IStateHarvestLevel;
import gregtech.api.block.IStateSoundType;
import gregtech.api.block.VariantBlock;
import gregtech.api.items.toolitem.ToolClasses;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving.SpawnPlacementType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import org.jetbrains.annotations.NotNull;

/**
 * Tank casing blocks for multiblock tanks.
 * Each variant corresponds to a material type used for tank construction.
 * Wood and Steel are excluded as they use existing casing blocks.
 */
public class BlockTankCasing extends VariantBlock<BlockTankCasing.TankCasingType> {

    public BlockTankCasing() {
        super(Material.IRON);
        setTranslationKey("tank_casing");
        setHardness(4.0f);
        setResistance(8.0f);
        setSoundType(SoundType.METAL);
        setDefaultState(getState(TankCasingType.BRONZE));
    }

    @Override
    public boolean canCreatureSpawn(@NotNull IBlockState state, @NotNull IBlockAccess world, @NotNull BlockPos pos,
                                    @NotNull SpawnPlacementType type) {
        return false;
    }

    @NotNull
    public IBlockState getState(@NotNull String name) {
        return getState(getType(name));
    }

    @NotNull
    public ItemStack getItemVariant(@NotNull String name) {
        return getItemVariant(getType(name));
    }

    @NotNull
    public ItemStack getItemVariant(@NotNull String name, int amount) {
        return getItemVariant(getType(name), amount);
    }

    public int getVariantIndex(@NotNull String name) {
        return VARIANT.getIndexOf(getType(name));
    }

    @NotNull
    private TankCasingType getType(@NotNull String name) {
        for (TankCasingType type : TankCasingType.values()) {
            if (type.getName().equals(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown tank casing: " + name);
    }

    public enum TankCasingType implements IStringSerializable, IStateHarvestLevel, IStateSoundType {

        BRONZE("bronze", 1),
        ALUMINIUM("aluminium", 1),
        CHROME("chrome", 2),
        STAINLESS_STEEL("stainless_steel", 2),
        TITANIUM("titanium", 2);

        private final String name;
        private final int harvestLevel;
        private final SoundType soundType;

        TankCasingType(String name, int harvestLevel, SoundType soundType) {
            this.name = name;
            this.harvestLevel = harvestLevel;
            this.soundType = soundType;
        }

        TankCasingType(String name, int harvestLevel) {
            this(name, harvestLevel, SoundType.METAL);
        }

        @NotNull
        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public int getHarvestLevel(IBlockState state) {
            return harvestLevel;
        }

        @Override
        public String getHarvestTool(IBlockState state) {
            return ToolClasses.WRENCH;
        }

        @NotNull
        @Override
        public SoundType getSoundType(@NotNull IBlockState state) {
            return soundType;
        }
    }
}
