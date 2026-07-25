package gregtech.common.blocks;

import gregtech.api.block.IStateHarvestLevel;
import gregtech.api.block.VariantBlock;
import gregtech.api.items.toolitem.ToolClasses;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.IStringSerializable;
import org.jetbrains.annotations.NotNull;

public class BlockNuclearReactorCasing extends VariantBlock<BlockNuclearReactorCasing.NuclearReactorType> {

    public BlockNuclearReactorCasing() {
        super(Material.IRON);
        setTranslationKey("nuclear_reactor_casing");
        setHardness(10.0f);
        setResistance(10.0f);
        setSoundType(SoundType.METAL);
        setDefaultState(getState(NuclearReactorType.NUCLEAR_REACTOR_CASING));
    }

    public enum NuclearReactorType implements IStringSerializable, IStateHarvestLevel {

        NUCLEAR_REACTOR_CASING("nuclear_reactor_casing", 3);

        private final String name;
        private final int harvestLevel;

        NuclearReactorType(String name, int harvestLevel) {
            this.name = name;
            this.harvestLevel = harvestLevel;
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
    }
}
