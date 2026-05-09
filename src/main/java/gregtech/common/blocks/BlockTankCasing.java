package gregtech.common.blocks;

import gregtech.api.block.IStateHarvestLevel;
import gregtech.api.block.IStateSoundType;
import gregtech.api.block.VariantBlock;
import gregtech.api.items.toolitem.ToolClasses;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving.SpawnPlacementType;
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

    public enum TankCasingType implements IStringSerializable, IStateHarvestLevel, IStateSoundType {

        BRONZE("bronze", 1),
        GOLD("gold", 1),
        COPPER("copper", 1),
        IRON("iron", 1),
        LEAD("lead", 1),
        CHROME("chrome", 2),
        ALUMINIUM("aluminium", 1),
        STAINLESS_STEEL("stainless_steel", 2),
        TITANIUM("titanium", 2),
        TUNGSTEN("tungsten", 3),
        TUNGSTENSTEEL("tungstensteel", 3),
        IRIDIUM("iridium", 3),
        RHODIUM_PLATED_PALLADIUM("rhodium_plated_palladium", 3),
        NAQUADAH_ALLOY("naquadah_alloy", 4),
        DARMSTADTIUM("darmstadtium", 4),
        NEUTRONIUM("neutronium", 4);

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
