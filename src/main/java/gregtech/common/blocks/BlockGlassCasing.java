package gregtech.common.blocks;

import gregtech.api.GTValues;
import gregtech.api.block.IGlassTier;
import gregtech.api.block.VariantActiveBlock;
import gregtech.api.items.toolitem.ToolClasses;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BlockGlassCasing extends VariantActiveBlock<BlockGlassCasing.CasingType> {

    public BlockGlassCasing() {
        super(Material.GLASS);
        setTranslationKey("transparent_casing");
        setHardness(5.0F);
        setResistance(5.0F);
        setSoundType(SoundType.GLASS);
        setHarvestLevel(ToolClasses.PICKAXE, 1);
        setDefaultState(getState(CasingType.TEMPERED_GLASS));
        this.useNeighborBrightness = true;
    }

    @Override
    public boolean canCreatureSpawn(@NotNull IBlockState state, @NotNull IBlockAccess world, @NotNull BlockPos pos,
                                    @NotNull EntityLiving.SpawnPlacementType type) {
        return false;
    }

    @Override
    @NotNull
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public boolean canRenderInLayer(@NotNull IBlockState state, @NotNull BlockRenderLayer layer) {
        return getState(state) == CasingType.TEMPERED_GLASS ? layer == BlockRenderLayer.TRANSLUCENT :
                super.canRenderInLayer(state, layer);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isOpaqueCube(@NotNull IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isFullCube(@NotNull IBlockState state) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("deprecation")
    public boolean shouldSideBeRendered(@NotNull IBlockState state, IBlockAccess world, BlockPos pos,
                                        @NotNull EnumFacing side) {
        IBlockState sideState = world.getBlockState(pos.offset(side));

        return sideState.getBlock() == this ?
                getState(sideState) != getState(state) :
                super.shouldSideBeRendered(state, world, pos, side);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@NotNull ItemStack stack, @Nullable World world,
                               @NotNull List<String> tooltip, @NotNull ITooltipFlag flag) {
        super.addInformation(stack, world, tooltip, flag);
        tooltip.add(I18n.format("gregtech.glass_tier.tooltip",
                GTValues.VNF[getState(getStateFromMeta(stack.getMetadata())).getGlassTier()]));
    }

    public enum CasingType implements IStringSerializable, IGlassTier {

        TEMPERED_GLASS("tempered_glass", GTValues.MV),
        FUSION_GLASS("fusion_glass", GTValues.UV),
        LAMINATED_GLASS("laminated_glass", GTValues.IV),
        CLEANROOM_GLASS("cleanroom_glass", GTValues.HV);

        private final String name;
        private final int glassTier;

        CasingType(String name, int glassTier) {
            this.name = name;
            this.glassTier = glassTier;
        }

        @Override
        public int getGlassTier() {
            return glassTier;
        }

        @Override
        @NotNull
        public String getName() {
            return this.name;
        }
    }
}
